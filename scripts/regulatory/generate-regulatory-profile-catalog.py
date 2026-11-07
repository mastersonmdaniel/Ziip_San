#!/usr/bin/env python3
"""Gera a R__regulatory_profile_catalog.sql (P2.31) a partir da fonte Java.

Fonte unica dos valores: ScRegulatoryProfiles.java (modulo platform), que por
sua vez cita o mapa de ERIs (docs/normas/partes/04) e o PARAMETROS-DO-TENANT.
Precedente: scripts/normas/generate-norm-catalog-load.py da P1.34 -- a R__
NAO se edita a mao; regenere com este script.

Uso:
    python3 scripts/regulatory/generate-regulatory-profile-catalog.py \
        [caminho de ScRegulatoryProfiles.java] [caminho de saida da R__]

Sem argumentos, usa os caminhos do repositorio a partir da raiz.
"""
import json
import re
import sys
from pathlib import Path

JAVA_DEFAULT = ("platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/"
                "regulatory/ScRegulatoryProfiles.java")
SQL_DEFAULT = ("platform/ziipsan-platform/src/main/resources/db/migration/tenant/"
               "R__regulatory_profile_catalog.sql")


def split_args(text):
    """Divide a lista de argumentos no nivel superior, respeitando aspas Java
    e a profundidade de parenteses (LocalDate.of(2019, 3, 27) e um so
    argumento)."""
    args, cur, i, depth = [], [], 0, 0
    while i < len(text):
        ch = text[i]
        if ch == '"':
            cur.append(ch)
            i += 1
            while i < len(text):
                cur.append(text[i])
                if text[i] == "\\":
                    i += 1
                    if i < len(text):
                        cur.append(text[i])
                elif text[i] == '"':
                    break
                i += 1
        elif ch == "(":
            depth += 1
            cur.append(ch)
        elif ch == ")":
            depth -= 1
            cur.append(ch)
        elif ch == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
        else:
            cur.append(ch)
        i += 1
    if cur:
        args.append("".join(cur).strip())
    return args


def java_string(token):
    if token == "null":
        return None
    token = token.strip()
    assert token.startswith('"') and token.endswith('"'), token
    return json.loads(token)  # escapes Java de string simples == escapes JSON


def java_date(token):
    if token == "null":
        return None
    m = re.fullmatch(r"LocalDate\.of\((\d+),\s*(\d+),\s*(\d+)\)", token.strip())
    assert m, token
    return "%04d-%02d-%02d" % (int(m.group(1)), int(m.group(2)), int(m.group(3)))


def sql_text(value):
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'"


def sql_json(value):
    if value is None:
        return "NULL"
    return "'" + value.replace("'", "''") + "'::jsonb"


def parse(path):
    profiles = []
    current = None
    for raw in Path(path).read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        m = re.search(r'RegulatoryProfileDef\.builder\((.*)\)$', line)
        if m:
            a = split_args(m.group(1))
            current = {
                "profile_key": java_string(a[0]), "agency_code": java_string(a[1]),
                "agency_name": java_string(a[2]), "act": java_string(a[3]),
                "valid_from": java_date(a[4]), "valid_to": java_date(a[5]),
                "values": [], "divergences": [], "adoption": [], "facts": [], "notes": None,
            }
            profiles.append(current)
            continue
        if current is None:
            continue
        m = re.match(r"\.value\((.*)\)$", line)
        if m:
            a = split_args(m.group(1))
            current["values"].append({
                "key": java_string(a[0]), "value": java_string(a[1]),
                "act": java_string(a[2]), "article": java_string(a[3]),
                "basis": a[4].split(".")[-1], "confirmation": a[5].split(".")[-1],
                "note": java_string(a[6]),
            })
            continue
        m = re.match(r"\.adoption\((.*)\)$", line)
        if m:
            a = split_args(m.group(1))
            acts = java_string(a[2])
            current["adoption"].append({
                "nr": java_string(a[0]), "status": java_string(a[1]),
                "acts": [s.strip() for s in acts.split(";")] if acts else [],
            })
            continue
        m = re.match(r"\.fact\((.*)\)$", line)
        if m:
            a = split_args(m.group(1))
            current["facts"].append({
                "code": java_string(a[0]), "date": java_date(a[1]),
                "source": java_string(a[2]),
            })
            continue
        m = re.match(r"\.divergence\((.*)\)$", line)
        if m:
            a = split_args(m.group(1))
            current["divergences"].append({
                "rule_code": java_string(a[0]), "local_rule": java_string(a[1]),
                "local_source": java_string(a[2]), "federal_norm": java_string(a[3]),
                "resolution": a[4].split(".")[-1], "note": java_string(a[5]),
            })
            continue
        m = re.match(r"\.note\((.*)\)$", line)
        if m:
            current["notes"] = java_string(split_args(m.group(1))[0])
            continue
    return profiles


# NR -> cert_standard.code da P1.34 (DAD-28). ESPELHO EXATO de
# AnaNormCatalogLink.CATALOG_CODE_BY_NR (Java): code = catalog_key = id
# slug do JSON de normas, lido do catalogo da area 02 (ANA) gerado da
# fonte da P1.34. Se um lado mudar sem o outro, o
# RegulatoryCatalogConsistencyTest acusa (a R__ diverge do catalogo Java).
NR_CATALOG_CODES = {
    "NR1": "ana-nr-1-res-79-2021",
    "NR2": "ana-nr-2-res-106-2021",
    "NR3": "ana-nr-3-res-161-2023",
    "NR4": "ana-nr-4-res-177-2024",
    "NR5": "ana-nr-5-res-178-2024",
    "NR6": "ana-nr-6-res-183-2024",
    "NR7": "ana-nr-7-res-187-2024",
    "NR8": "ana-nr-8-res-192-2024",
    "NR9": "ana-nr-9-res-211-2024",
    "NR10": "ana-nr-10-res-228-2024",
    "NR11": "ana-nr-11-res-230-2024",
    "NR12": "ana-nr-12-res-245-2025",
    "NR13": "ana-nr-13-res-271-2025",
    "NR14": "ana-nr-14-res-276-2025",
    "NR15": "ana-nr-15-res-275-2025",
    "NR16": "ana-nr-16-res-302-2026",
}


def derived_values(profile):
    """Os mesmos valores derivados do Builder Java: reg.ana_nr_adoption e
    reg.normative_facts, construidos dos campos estruturados."""
    out = []
    if profile["adoption"]:
        adoption = {}
        for entry in profile["adoption"]:
            item = {"status": entry["status"], "acts": entry["acts"]}
            code = NR_CATALOG_CODES.get(entry["nr"])
            if code is not None:
                item["catalogCode"] = code
            adoption[entry["nr"]] = item
        out.append({
            "key": "reg.ana_nr_adoption",
            "value": json.dumps(adoption, ensure_ascii=False, separators=(",", ":")),
            "act": "Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F",
            "article": None, "basis": "NOT_APPLICABLE", "confirmation": "CONFIRMED",
            "note": "Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas "
                    "da P1.34 pelo cert_standard.code em catalogCode (DAD-28), "
                    "validado no aceite da ativação.",
        })
    if profile["facts"]:
        facts = [{"code": f["code"], "date": f["date"], "source": f["source"]}
                 for f in profile["facts"]]
        out.append({
            "key": "reg.normative_facts",
            "value": json.dumps(facts, ensure_ascii=False, separators=(",", ":")),
            "act": "Res. ANA 230/2024 (NR 11)", "article": "art. 74, §3º",
            "basis": "NOT_APPLICABLE", "confirmation": "CONFIRMED", "note": None,
        })
    return out


def main():
    java_path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(JAVA_DEFAULT)
    sql_path = Path(sys.argv[2]) if len(sys.argv) > 2 else Path(SQL_DEFAULT)
    profiles = parse(java_path)
    assert len(profiles) == 7, f"esperados 7 perfis, lidos {len(profiles)}"

    total_values = 0
    total_divergences = 0
    body = []
    for p in profiles:
        values = p["values"] + derived_values(p)
        total_values += len(values)
        total_divergences += len(p["divergences"])
        body.append(
            "INSERT INTO regulatory_profile_catalog\n"
            "    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act,"
            " valid_from, valid_to, notes)\nVALUES\n"
            f"    ('${{tenant_id}}', {sql_text(p['profile_key'])}, {sql_text(p['agency_code'])},"
            f" {sql_text(p['agency_name'])}, {sql_text(p['act'])},"
            f" {sql_text(p['valid_from'])}::date, {sql_text(p['valid_to'])}::date,"
            f" {sql_text(p['notes'])})\n"
            "ON CONFLICT (tenant_id, profile_key) DO UPDATE SET\n"
            "    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,\n"
            "    general_conditions_act = EXCLUDED.general_conditions_act,\n"
            "    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,\n"
            "    notes = EXCLUDED.notes, updated_at = NOW();\n")
        rows = []
        for v in values:
            rows.append(
                f"    ('${{tenant_id}}', {sql_text(p['profile_key'])}, {sql_text(v['key'])},"
                f" {sql_json(v['value'])}, {sql_text(v['act'])}, {sql_text(v['article'])},"
                f" {sql_text(v['basis'])}, {sql_text(v['confirmation'])}, {sql_text(v['note'])})")
        body.append(
            "INSERT INTO regulatory_profile_values\n"
            "    (tenant_id, profile_key, definition_key, value, source_act, source_article,"
            " deadline_basis, confirmation, note)\nVALUES\n"
            + ",\n".join(rows) + "\n"
            "ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET\n"
            "    value = EXCLUDED.value, source_act = EXCLUDED.source_act,\n"
            "    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,\n"
            "    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();\n")
        if p["divergences"]:
            rows = []
            for d in p["divergences"]:
                rows.append(
                    f"    ('${{tenant_id}}', {sql_text(p['profile_key'])},"
                    f" {sql_text(d['rule_code'])}, {sql_text(d['local_rule'])},"
                    f" {sql_text(d['local_source'])}, {sql_text(d['federal_norm'])},"
                    f" {sql_text(d['resolution'])}, {sql_text(d['note'])})")
            body.append(
                "INSERT INTO regulatory_profile_divergences\n"
                "    (tenant_id, profile_key, rule_code, local_rule, local_source,"
                " federal_norm, resolution, note)\nVALUES\n"
                + ",\n".join(rows) + "\n"
                "ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET\n"
                "    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,\n"
                "    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,\n"
                "    note = EXCLUDED.note, updated_at = NOW();\n")

    header = f"""-- Perfis regulatorios de fabrica das ERIs de SC (P2.31) -- CARGA REPETIVEL.
--
-- GERADO por scripts/regulatory/generate-regulatory-profile-catalog.py a
-- partir de ScRegulatoryProfiles.java (modulo platform). NAO EDITE A MAO:
-- regenere. Precedente: a carga do catalogo de normas da P1.34
-- (R__norm_catalog.sql, core) e o catalogo de fabrica do GIS (R__ tenant).
--
-- Fonte de cada valor: o mapa de ERIs (docs/normas/partes/04, §4, §5, §6 e
-- anexos A, B e F) e o PARAMETROS-DO-TENANT (G1, G5, G6, G9 e bifurcacoes).
-- Valor com confirmation = REQUIRES_TENANT_VALUE tem value NULL por desenho:
-- o texto da norma nao foi confirmado na leitura (AGR-Tubarao sem condicoes
-- gerais comerciais localizadas; CISAM-SUL com texto so digitalizado;
-- CISAM Meio-Oeste com juros/multa remetidos a legislacao municipal) e a
-- ativacao exige o valor informado pelo tenant. NENHUM valor e inventado.
--
-- Regra local contraria a lei federal NAO entra como valor: entra em
-- regulatory_profile_divergences, e o valor efetivo e o piso federal
-- (corte ate 12 h de sexta -> Lei 14.015/2020; perda da tarifa social apos
-- dois cortes na AGIR -> recusada pela Lei 14.898/2024).
--
-- Idempotencia: upsert pela chave estavel (profile_key, definition_key);
-- reaplicar nunca apaga linha nem toca em regulatory_profiles,
-- regulatory_profile_activations ou parameter_versions do tenant.
-- Contagens esperadas desta versao: {len(profiles)} perfis,
-- {total_values} valores, {total_divergences} divergencias (guarda no fim).

SELECT set_config('app.current_tenant', '${{tenant_id}}', true);

"""
    guard = f"""
-- =======================================================================
-- Guarda de contagem (precedente da P1.34): falha alto se a carga divergir
-- =======================================================================

DO $$
DECLARE
    v_profiles INT;
    v_values INT;
    v_divergences INT;
BEGIN
    SELECT COUNT(*) INTO v_profiles FROM regulatory_profile_catalog
        WHERE tenant_id = '${{tenant_id}}'::uuid;
    SELECT COUNT(*) INTO v_values FROM regulatory_profile_values
        WHERE tenant_id = '${{tenant_id}}'::uuid;
    SELECT COUNT(*) INTO v_divergences FROM regulatory_profile_divergences
        WHERE tenant_id = '${{tenant_id}}'::uuid;
    IF v_profiles <> {len(profiles)} OR v_values <> {total_values}
            OR v_divergences <> {total_divergences} THEN
        RAISE EXCEPTION 'Carga P2.31 divergente: % perfis, % valores, % divergencias (esperados {len(profiles)}/{total_values}/{total_divergences})',
            v_profiles, v_values, v_divergences;
    END IF;
END $$;
"""
    sql_path.parent.mkdir(parents=True, exist_ok=True)
    sql_path.write_text(header + "\n".join(body) + guard, encoding="utf-8")
    print(f"{len(profiles)} perfis, {total_values} valores, "
          f"{total_divergences} divergencias -> {sql_path}")


if __name__ == "__main__":
    main()
