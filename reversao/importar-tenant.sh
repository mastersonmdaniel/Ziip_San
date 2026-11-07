#!/usr/bin/env bash
# ED-04 -- importa a exportacao de UM tenant sintetico no pacote de reversao.
#
# Formato esperado da exportacao (o mesmo da saida da P3.16, alimentado pela
# copia logica por tenant da P3.33):
#   <dir>/manifesto.json   manifesto da exportacao
#   <dir>/SHA256SUMS       hashes de todos os arquivos, conferidos aqui
#   <dir>/banco/tenant.sql OU <dir>/banco/tenant.dump   pg_dump do schema do tenant
#   <dir>/banco/catalogo.sql   linha do tenant em public.tenant_catalog (sem segredos)
#
# Banco e usuario sao os nomes reais fixos do ambiente: servico
# postgres-primary, psql -U postgres -d ziipsan (POSTGRES_USER e
# POSTGRES_DB do compose). Nao ha variavel para eles neste pacote.
set -euo pipefail
cd "$(dirname "$0")"
EXPORT_DIR="${1:?uso: ./importar-tenant.sh <diretorio-da-exportacao>}"
[ -f .env.reversao ] || { echo "ERRO: .env.reversao nao encontrado"; exit 1; }

# Le REVERSAO_TENANT_SCHEMA do .env.reversao SEM executar o arquivo (sem
# source: valores com espacos ou $ nao sao expandidos por este script).
REVERSAO_TENANT_SCHEMA="$(grep -E '^REVERSAO_TENANT_SCHEMA=' .env.reversao 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r' || true)"
: "${REVERSAO_TENANT_SCHEMA:?defina REVERSAO_TENANT_SCHEMA no .env.reversao}"
# O schema e interpolado nas consultas abaixo: so aceita identificador
# simples (letra minuscula ou _, seguido de minusculas/digitos/_).
case "$REVERSAO_TENANT_SCHEMA" in
  *[!a-z0-9_]*|'') echo "ERRO: REVERSAO_TENANT_SCHEMA invalido (esperado identificador simples em minusculas): '$REVERSAO_TENANT_SCHEMA'"; exit 1 ;;
esac
case "$REVERSAO_TENANT_SCHEMA" in
  [0-9]*) echo "ERRO: REVERSAO_TENANT_SCHEMA invalido (nao pode comecar com digito): '$REVERSAO_TENANT_SCHEMA'"; exit 1 ;;
esac

[ -f "$EXPORT_DIR/manifesto.json" ] || { echo "ERRO: manifesto.json ausente na exportacao"; exit 1; }
[ -f "$EXPORT_DIR/SHA256SUMS" ] || { echo "ERRO: SHA256SUMS ausente na exportacao"; exit 1; }
(cd "$EXPORT_DIR" && sha256sum -c SHA256SUMS) || { echo "ERRO: hash divergente na exportacao; importacao abortada"; exit 1; }

export COMPOSE_FILE=docker-compose.reversao.yml
COMPOSE="docker compose --env-file .env.reversao"

# Aborta se o schema do tenant ja existir: reversao nao sobrescreve em silencio.
EXISTS=$($COMPOSE exec -T postgres-primary psql -U postgres -d ziipsan -Atc "SELECT count(*) FROM information_schema.schemata WHERE schema_name = '$REVERSAO_TENANT_SCHEMA'")
[ "$EXISTS" = "0" ] || { echo "ERRO: schema $REVERSAO_TENANT_SCHEMA ja existe; abortando (ver criterios de abortar do runbook)"; exit 1; }

if [ -f "$EXPORT_DIR/banco/tenant.sql" ]; then
  $COMPOSE exec -T postgres-primary psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan < "$EXPORT_DIR/banco/tenant.sql"
elif [ -f "$EXPORT_DIR/banco/tenant.dump" ]; then
  # --exit-on-error: o pg_restore para no primeiro erro, como o ramo .sql
  # para com ON_ERROR_STOP (fecha o restante do P7 da revisao da ED-04).
  $COMPOSE exec -T postgres-primary pg_restore --exit-on-error -U postgres -d ziipsan < "$EXPORT_DIR/banco/tenant.dump"
else
  echo "ERRO: nem banco/tenant.sql nem banco/tenant.dump na exportacao"; exit 1
fi

[ -f "$EXPORT_DIR/banco/catalogo.sql" ] || { echo "ERRO: banco/catalogo.sql ausente (linha do tenant no catalogo)"; exit 1; }
$COMPOSE exec -T postgres-primary psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan < "$EXPORT_DIR/banco/catalogo.sql"

# Registra o ambiente de destino na linha do tenant (ED-04, ADR-0024).
# Confere a contagem: UPDATE de 0 linhas = catalogo.sql nao criou a linha deste
# tenant (ou schema_name divergente) — aborta em vez de declarar sucesso falso.
SAIDA_UPDATE=$($COMPOSE exec -T postgres-primary psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan -c "UPDATE public.tenant_catalog SET environment = 'REVERSAO', updated_at = NOW() WHERE schema_name = '$REVERSAO_TENANT_SCHEMA'")
[ "$SAIDA_UPDATE" = "UPDATE 1" ] || { echo "ERRO: registro de ambiente afetou '$SAIDA_UPDATE' (esperado 'UPDATE 1'); importacao nao confirmada"; exit 1; }

echo "Tenant $REVERSAO_TENANT_SCHEMA importado. Rode ./testes-fumaca.sh."
