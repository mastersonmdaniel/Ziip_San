#!/usr/bin/env bash
# ED-04 -- testes de fumaca do pacote de reversao (versionavel, sem banco de
# producao, sem dado real: tenant sintetico apenas).
# Falha (exit != 0) em qualquer item; nenhum item e pulado em silencio.
# Sem `|| echo 000` nas chamadas curl (correcao do P6 da revisao): com a
# porta morta, o curl -w ja imprime 000 e um echo a mais geraria "000000",
# que passaria no teste != "000" com o servico fora do ar.
set -uo pipefail
cd "$(dirname "$0")"
[ -f .env.reversao ] || { echo "ERRO: .env.reversao nao encontrado"; exit 1; }

# Le as variaveis necessarias do .env.reversao SEM executar o arquivo
# (sem source: valores com espacos ou $ nao sao expandidos por este script).
ler_var() {
  grep -E "^$1=" .env.reversao 2>/dev/null | head -n 1 | cut -d= -f2- | tr -d '\r' || true
}
REVERSAO_TENANT_SCHEMA="$(ler_var REVERSAO_TENANT_SCHEMA)"
: "${REVERSAO_TENANT_SCHEMA:?defina REVERSAO_TENANT_SCHEMA no .env.reversao}"
# O schema e interpolado na consulta do item 2: valida como identificador
# simples, com a mesma regra do importar-tenant.sh (fecha o residuo do P12).
case "$REVERSAO_TENANT_SCHEMA" in
  *[!a-z0-9_]*|'') echo "ERRO: REVERSAO_TENANT_SCHEMA invalido (esperado identificador simples em minusculas): '$REVERSAO_TENANT_SCHEMA'"; exit 1 ;;
esac
case "$REVERSAO_TENANT_SCHEMA" in
  [0-9]*) echo "ERRO: REVERSAO_TENANT_SCHEMA invalido (nao pode comecar com digito): '$REVERSAO_TENANT_SCHEMA'"; exit 1 ;;
esac
REVERSAO_API_PORT="$(ler_var REVERSAO_API_PORT)"; REVERSAO_API_PORT="${REVERSAO_API_PORT:-8090}"
REVERSAO_KC_PORT="$(ler_var REVERSAO_KC_PORT)"; REVERSAO_KC_PORT="${REVERSAO_KC_PORT:-8080}"

export COMPOSE_FILE=docker-compose.reversao.yml
COMPOSE="docker compose --env-file .env.reversao"
FALHAS=0
ok()   { echo "OK   - $1"; }
fail() { echo "FALHA - $1"; FALHAS=$((FALHAS+1)); }

# 1. Banco responde (servico postgres-primary, nomes reais fixos).
$COMPOSE exec -T postgres-primary pg_isready -U postgres -d ziipsan >/dev/null 2>&1 \
  && ok "banco responde (pg_isready)" || fail "banco nao responde"

# 2. Tenant importado esta no catalogo, com modo de isolamento e ambiente (core V7 + registro da ED-04).
LINHA=$($COMPOSE exec -T postgres-primary psql -U postgres -d ziipsan -Atc "SELECT isolation_mode || '|' || environment FROM public.tenant_catalog WHERE schema_name = '$REVERSAO_TENANT_SCHEMA'" 2>/dev/null || true)
case "$LINHA" in
  SCHEMA\|REVERSAO|SHARED\|REVERSAO|DEDICATED\|REVERSAO) ok "catalogo: tenant com isolation_mode e environment=REVERSAO ($LINHA)" ;;
  *) fail "catalogo sem tenant importado com modo de isolamento valido e ambiente REVERSAO (lido: '${LINHA:-vazio}')" ;;
esac

# 3. API responde no endpoint de versao/ambiente que ja existe no produto
#    (GET /api/v1/about, lido do registro de implantacao da core V5_1).
COD=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${REVERSAO_API_PORT}/api/v1/about" || true)
[ "$COD" = "200" ] && ok "api: GET /api/v1/about = 200" || fail "api: GET /api/v1/about = $COD (esperado 200)"

# 4. Keycloak responde com o realm ziipsan importado (mais forte que
#    "qualquer resposta": prova que a importacao do realm aconteceu).
CODKC=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${REVERSAO_KC_PORT}/realms/ziipsan" || true)
[ "$CODKC" = "200" ] && ok "keycloak: GET /realms/ziipsan = 200" || fail "keycloak: GET /realms/ziipsan = $CODKC (esperado 200)"

# 5. Cofre proprio responde (qualquer codigo HTTP != 000: cofre selado ou
#    nao inicializado responde 4xx/5xx, mas responde).
CODVAULT=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8200/v1/sys/health" || true)
[ "$CODVAULT" != "000" ] && ok "cofre responde (HTTP $CODVAULT)" || fail "cofre nao responde"

# 6. Armazenamento compativel com S3 responde (qualquer codigo != 000).
CODS3=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:9000/" || true)
[ "$CODS3" != "000" ] && ok "s3 responde (HTTP $CODS3)" || fail "s3 nao responde"

# 7. Fiscal desligado no pacote (regra da P0.27 para ambientes locais).
$COMPOSE exec -T api printenv ZIIPSAN_FISCAL_PRODUCTION_ALLOWED 2>/dev/null | grep -qx "false" \
  && ok "ziipsan.fiscal.production-allowed = false na api" || fail "flag fiscal de producao nao esta false na api"

echo "-----"
if [ "$FALHAS" -eq 0 ]; then echo "FUMACA VERDE ($REVERSAO_TENANT_SCHEMA)"; else echo "FUMACA COM $FALHAS FALHA(S)"; exit 1; fi
