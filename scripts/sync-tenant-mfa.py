#!/usr/bin/env python3
"""Sincroniza o parametro security.mfa_required -> atributo mfa_required do usuario no Keycloak (P0.15).

Veiculo FORA do modulo app, de proposito: o ArchUnit proibe org.keycloak.* na app
(P0.25 apagou o keycloak-admin-client) e a app nunca fala com o Admin API. Este
script e o veiculo: le public.tenant_settings via psql e atualiza os usuarios via
Admin REST do Keycloak, com client_credentials de um client de servico.

Chave e formato (fonte no codigo: MfaPolicy, platform):
  tenant_settings.setting_key = "security.mfa_required", setting_value JSONB booleano.
  Ausente = false. Valor invalido (nao booleano) NAO e sincronizado: o tenant e
  pulado com aviso, para nao apagar um true por dado quebrado.

Atributo no Keycloak: mfa_required = "true"/"false" (lista de um elemento, como
o Keycloak guarda atributos). Usuarios de plataforma (ziipsan_user_type=platform)
NAO sao tocados aqui: o atributo deles e manual, no cadastro pelo console.

Lag fail-open (documentado, nao escondido): entre a mudanca do parametro e a
proxima execucao deste script, o login usa o atributo ANTIGO. Atributo ausente
vale como nao-exigente: o risco e um tenant recem-exigente ficar sem MFA ate a
proxima sincronizacao. Rode o script logo apos mudar o parametro (ou por cron);
a P0.19 dara a isto um workflow de aceite (parameter_acceptances ainda nao existe).

Registro de aceite (lacuna P0.19): ate la, cada mudanca aplicada sai como uma
linha JSON {"event": "mfa_attribute_sync", ...} no stdout (redirecione para o
arquivo de auditoria, ou SYNC_AUDIT_FILE). Nao ha workflow de aceite inventado.

Uso:
  python3 scripts/sync-tenant-mfa.py [--dry-run | --apply]
Ambiente: PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD (libpq padrao),
  KC_URL (ex.: http://localhost:8080), KC_SYNC_CLIENT_ID, KC_SYNC_CLIENT_SECRET.
  A conexao PG precisa enxergar TODOS os tenants (role com BYPASSRLS ou superuser
  de manutencao): a RLS de tenant_settings esconderia as linhas dos outros tenants.
  O client de servico precisa de manage-users no realm ziipsan (criar no console;
  nunca commitar o segredo).
Sem dependencias alem da stdlib (psql + curl via subprocess/urllib).
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.request
from urllib.error import HTTPError

PARAMETER_KEY = "security.mfa_required"
USER_ATTRIBUTE = "mfa_required"
TENANT_ATTRIBUTE = "tenant_id"
REALM = "ziipsan"


def parse_bool_setting(value_text):
    """Texto JSONB de setting_value -> True/False, ou None se nao for booleano."""
    try:
        value = json.loads(value_text)
    except (ValueError, TypeError):
        return None
    return value if isinstance(value, bool) else None


def load_settings(rows):
    """Linhas (tenant_id, setting_value::text) -> (settings, invalidos, avisos).

    settings: tenant -> bool (so linhas validas). invalidos: tenants com valor
    quebrado, que o plano pula sem tocar (nunca apaga um true por dado quebrado).
    """
    settings, invalid, warnings = {}, [], []
    for tenant_id, value_text in rows:
        parsed = parse_bool_setting(value_text)
        if parsed is None:
            invalid.append(tenant_id)
            warnings.append(f"tenant {tenant_id}: valor invalido para {PARAMETER_KEY}: {value_text!r} (pulado)")
        else:
            settings[tenant_id] = parsed
    return settings, invalid, warnings


def kc_users_for_tenant(users, tenant_id):
    """Usuarios do Keycloak cujo atributo tenant_id contem o tenant (filtro local:
    a busca por atributo na Admin API varia por versao; listar e filtrar aqui
    funciona em qualquer Keycloak 26 e basta na escala do piloto)."""
    out = []
    for user in users:
        attrs = user.get("attributes") or {}
        if tenant_id in (attrs.get(TENANT_ATTRIBUTE) or []):
            out.append(user)
    return out


def needs_update(user, desired):
    """True se o atributo mfa_required do usuario difere do desejado."""
    attrs = user.get("attributes") or {}
    return (attrs.get(USER_ATTRIBUTE) or []) != ["true" if desired else "false"]


def updated_attributes(user, desired):
    """Copia dos atributos do usuario com mfa_required ajustado."""
    attrs = dict(user.get("attributes") or {})
    attrs[USER_ATTRIBUTE] = ["true" if desired else "false"]
    return attrs


def plan_updates(tenant_ids, settings, invalid, users):
    """Plano puro (testavel sem PG nem Keycloak): lista de dicts com tenant_id,
    user_id, username, de, para. Tenants invalidos sao pulados; tenant sem linha
    no parametro vale false (limpa atributo obsoleto)."""
    invalid_set = set(invalid)
    plan = []
    for tenant_id in tenant_ids:
        if tenant_id in invalid_set:
            continue
        desired = settings.get(tenant_id, False)
        for user in kc_users_for_tenant(users, tenant_id):
            if needs_update(user, desired):
                current = (user.get("attributes") or {}).get(USER_ATTRIBUTE) or []
                plan.append({
                    "tenant_id": tenant_id,
                    "user_id": user.get("id"),
                    "username": user.get("username"),
                    "from": current[0] if current else None,
                    "to": "true" if desired else "false",
                    "desired": desired,
                })
    return plan


def psql_query(sql):
    """Roda psql (libpq via ambiente) e devolve linhas (sem cabecalho)."""
    result = subprocess.run(
        ["psql", "-t", "-A", "-F", "|", "-c", sql],
        capture_output=True, text=True, check=True)
    rows = []
    for line in result.stdout.splitlines():
        if not line.strip():
            continue
        tenant_id, _, value = line.partition("|")
        rows.append((tenant_id.strip(), value.strip()))
    return rows


def kc_token(kc_url, client_id, client_secret):
    data = f"grant_type=client_credentials&client_id={client_id}&client_secret={client_secret}".encode()
    req = urllib.request.Request(
        f"{kc_url}/realms/{REALM}/protocol/openid-connect/token", data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)["access_token"]


def kc_get_json(url, token):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.load(resp)


def kc_put_json(url, token, payload):
    req = urllib.request.Request(
        url, data=json.dumps(payload).encode(), method="PUT",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30):
            pass
    except HTTPError as e:
        raise RuntimeError(f"PUT {url} -> HTTP {e.code}") from e


def audit_line(entry, audit_file):
    line = json.dumps(entry, ensure_ascii=False)
    print(line)
    if audit_file:
        with open(audit_file, "a", encoding="utf-8") as fh:
            fh.write(line + "\n")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Sincroniza security.mfa_required -> atributo Keycloak.")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--dry-run", action="store_true", default=True, help="só mostra (padrão)")
    group.add_argument("--apply", action="store_true", help="aplica de verdade")
    args = parser.parse_args(argv)

    kc_url = os.environ.get("KC_URL")
    client_id = os.environ.get("KC_SYNC_CLIENT_ID")
    client_secret = os.environ.get("KC_SYNC_CLIENT_SECRET")
    missing = [n for n, v in [("KC_URL", kc_url), ("KC_SYNC_CLIENT_ID", client_id),
                              ("KC_SYNC_CLIENT_SECRET", client_secret)] if not v]
    if missing:
        print(f"faltam variaveis de ambiente: {', '.join(missing)}", file=sys.stderr)
        return 2

    tenant_ids = [t for t, _ in psql_query("SELECT id::text FROM public.tenant_catalog")]
    setting_rows = psql_query(
        f"SELECT tenant_id::text, setting_value::text FROM public.tenant_settings "
        f"WHERE setting_key = '{PARAMETER_KEY}'")
    settings, invalid, warnings = load_settings(setting_rows)
    for warning in warnings:
        print(f"AVISO: {warning}", file=sys.stderr)

    token = kc_token(kc_url, client_id, client_secret)
    users, first = [], 0
    while True:
        page = kc_get_json(
            f"{kc_url}/admin/realms/{REALM}/users?first={first}&max=100", token)
        if not page:
            break
        users.extend(page)
        first += len(page)

    plan = plan_updates(tenant_ids, settings, invalid, users)
    audit_file = os.environ.get("SYNC_AUDIT_FILE")
    for item in plan:
        if args.apply:
            user = kc_get_json(f"{kc_url}/admin/realms/{REALM}/users/{item['user_id']}", token)
            user["attributes"] = updated_attributes(user, item["desired"])
            kc_put_json(f"{kc_url}/admin/realms/{REALM}/users/{item['user_id']}", token, user)
            audit_line({"event": "mfa_attribute_sync", **item}, audit_file)
        else:
            print(f"DRY tenant={item['tenant_id']} user={item['username']} "
                  f"mfa_required {item['from']} -> {item['to']}")
    print(f"{'aplicadas' if args.apply else 'planejadas'}: {len(plan)} atualizacoes; "
          f"tenants: {len(tenant_ids)}; invalidos pulados: {len(invalid)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
