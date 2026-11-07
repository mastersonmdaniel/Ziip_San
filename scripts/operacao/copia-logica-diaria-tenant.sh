#!/bin/bash
# P3.33: copia logica diaria do schema de CADA tenant (pg_dump -n), com
# manifesto, hash e expiracao pelo RNF-43 (ate 35 dias).
#
#   scripts/operacao/copia-logica-diaria-tenant.sh [schema]
#
# Sem argumento, copia todos os tenants ACTIVE do catalogo
# (public.tenant_catalog). Com um schema, copia so ele.
#
# Destino: ${ZIIPSAN_COPIAS_DIR:-/var/tmp/ziipsan-copias}/<schema>/<AAAA-MM-DD>/
#   tenant.dump    pg_dump em formato custom, SOMENTE o schema do tenant (-n)
#   manifesto.json manifesto da classe DailyTenantCopy (Java): tenant, schema,
#                  tenantKeyId (public.tenant_key da P0.28), captura, expiracao
#                  (captura + 35 dias), hash, tamanho, consumidores
#   SHA256SUMS     hash do dump, no formato que a ED-04 (importar-tenant.sh)
#                  e a P3.16 ja consomem
#
# IMUTABILIDADE: copia existente para o mesmo tenant/dia NUNCA e
# sobrescrita (o script aborta). Em producao o diretorio e substituido
# pelo armazenamento com retencao imutavel (Object Lock / cofre da LC.3);
# a regra de nao sobrescrever e a mesma nos dois casos.
#
# CRYPTO-SHREDDING: escopo da LC.5 — este script NAO destroi chave. O
# manifesto carrega o tenantKeyId para a destruicao da DEK alcanca-lo.
#
# Pre-requisitos: ambiente local no ar (scripts/local-env.sh) com o
# container ziipsan-postgres-primary (mesmo padrao de
# scripts/local-backup.sh da P0.28) e `date` GNU (para +35 dias).
set -euo pipefail

CONTAINER="${ZIIPSAN_PG_CONTAINER:-ziipsan-postgres-primary}"
DEST_ROOT="${ZIIPSAN_COPIAS_DIR:-/var/tmp/ziipsan-copias}"
DIA="$(date +%F)"
CAPTURED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
EXPIRES_AT="$(date -u -d "+35 days" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null)" \
  || { echo "ERRO: 'date -d' (GNU) indisponivel; expiracao do RNF-43 nao calculada — abortando" >&2; exit 1; }

psql_prod() { podman exec "$CONTAINER" psql -U postgres -d ziipsan -Atc "$1" </dev/null; }

validar_schema() {
  case "$1" in
    *[!a-z0-9_]*|'') echo "ERRO: schema invalido (identificador simples em minusculas): '$1'" >&2; return 1 ;;
    [0-9]*) echo "ERRO: schema invalido (nao pode comecar com digito): '$1'" >&2; return 1 ;;
  esac
}

copiar_um() {
  local schema="$1"
  validar_schema "$schema" || return 1
  local tenant_id tenant_key_id
  tenant_id="$(psql_prod "SELECT id FROM public.tenant_catalog WHERE schema_name = '$schema' AND status = 'ACTIVE'")"
  [ -n "$tenant_id" ] || { echo "ERRO: tenant ACTIVE nao encontrado no catalogo para o schema '$schema'" >&2; return 1; }
  # A referencia da chave (P0.28, core V6): sem ela o manifesto e recusado
  # pela classe DailyTenantCopy — aqui, sem ela a copia nem e gerada.
  tenant_key_id="$(psql_prod "SELECT id FROM public.tenant_key WHERE tenant_id = '$tenant_id' AND status = 'ACTIVE' ORDER BY key_version DESC LIMIT 1")"
  [ -n "$tenant_key_id" ] || { echo "ERRO: tenant_key (P0.28) nao encontrada para o tenant $tenant_id — copia nao gerada" >&2; return 1; }

  local dest="$DEST_ROOT/$schema/$DIA"
  [ ! -e "$dest/tenant.dump" ] || { echo "ERRO: copia de $schema de $DIA ja existe e e imutavel: $dest" >&2; return 1; }
  mkdir -p "$dest"

  # `|| return 1` explicito (nao depender do set -e): quando copiar_um e
  # chamada sob `||` no laco de todos os tenants, o set -e nao vale no
  # corpo — sem a guarda, uma falha do pg_dump faria o `podman cp`
  # copiar o dump ANTERIOR que ficou em /tmp no container.
  podman exec "$CONTAINER" pg_dump -U postgres -d ziipsan -Fc -n "$schema" -f /tmp/copia-tenant.dump </dev/null || return 1
  podman cp "$CONTAINER:/tmp/copia-tenant.dump" "$dest/tenant.dump" </dev/null || return 1
  local sha size
  sha="$(sha256sum "$dest/tenant.dump" | cut -d' ' -f1)"
  size="$(stat -c %s "$dest/tenant.dump")"
  (cd "$dest" && sha256sum tenant.dump > SHA256SUMS)
  # Manifesto nos campos de DailyTenantCopy.CopyManifest.manifestJson().
  printf '{\n  "tenantId": "%s",\n  "schemaName": "%s",\n  "tenantKeyId": "%s",\n  "capturedAt": "%s",\n  "expiresAt": "%s",\n  "retentionRule": "RNF-43 (expiracao em ate 35 dias)",\n  "storageKey": "copias/%s/%s/tenant.dump",\n  "sha256": "%s",\n  "sizeBytes": %s,\n  "format": "pg_dump -n <schema> (formato custom)",\n  "consumers": ["P3.33-restauracao", "P3.16-exportacao", "ED-04-reversao"],\n  "cryptoShredding": "PENDENTE-LC.5 (destruicao da DEK alcanca esta copia por tenantKeyId)"\n}\n' \
    "$tenant_id" "$schema" "$tenant_key_id" "$CAPTURED_AT" "$EXPIRES_AT" "$schema" "$DIA" "$sha" "$size" \
    > "$dest/manifesto.json"
  echo "copia de $schema ($tenant_id) em $dest — expira em $EXPIRES_AT (RNF-43)"
}

if [ "${1:-}" != "" ]; then
  copiar_um "$1"
else
  # A falha de UM tenant (sem chave, schema invalido, copia ja existente)
  # NAO pode abortar a copia dos demais: registra, segue e sai com erro
  # no fim — o job falha alto, mas nenhum tenant fica sem copia por
  # causa de outro.
  FALHAS=0
  while IFS= read -r schema; do
    [ -n "$schema" ] || continue
    copiar_um "$schema" || FALHAS=$((FALHAS + 1))
  done <<EOF
$(psql_prod "SELECT schema_name FROM public.tenant_catalog WHERE status = 'ACTIVE' ORDER BY schema_name")
EOF
  [ "$FALHAS" -eq 0 ] || { echo "ERRO: $FALHAS tenant(s) sem copia nesta execucao (ver erros acima)" >&2; exit 1; }
fi
echo "copia logica diaria concluida (inicio: $CAPTURED_AT)"
