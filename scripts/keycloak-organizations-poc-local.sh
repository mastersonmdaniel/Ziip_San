#!/bin/bash
# PoC Keycloak Organizations (P0.38) — variante sem contêiner, contra Keycloak local.
# Uso: KC_HOME=<dir do keycloak-26.8.0> KC_POC_PORT=18081 bash scripts/keycloak-organizations-poc-local.sh
# O script sobe o Keycloak em start-dev (só para a PoC, nunca para produção), cria o
# realm "poc" com Organizations ligado, cria a organização sintética "poc-saneamento"
# com domínio "poc.example.invalid" e confere a listagem via Admin API. O passo manual
# do tema (abrir o login, digitar usuario@poc.example.invalid e verificar se a marca
# da organização aparece antes da senha) continua sendo o ponto a provar — ver o doc.
set -euo pipefail
KC_HOME="${KC_HOME:?defina KC_HOME para o diretorio do Keycloak 26.8.0}"
KC_PORT="${KC_POC_PORT:-18081}"
ADMIN_PASSWORD="${KC_POC_ADMIN_PASSWORD:-poc-admin-sintetico}"
export JAVA_HOME="${JAVA_HOME:-/opt/toolchain/jdk-21.0.13+9}"
"$KC_HOME/bin/kc.sh" start-dev --http-port="$KC_PORT" \
  --bootstrap-admin-username=admin --bootstrap-admin-password="$ADMIN_PASSWORD" \
  >"${KC_POC_LOG:-/tmp/kc-poc.log}" 2>&1 &
KC_PID=$!
trap 'kill $KC_PID 2>/dev/null || true' EXIT
READY=0
for _ in $(seq 1 240); do
  # start-dev não expõe /health/ready (404): o probe é /realms/master (200 quando sobe).
  if curl -fsS "http://127.0.0.1:${KC_PORT}/realms/master" >/dev/null 2>&1; then
    READY=1
    break
  fi
  sleep 3
done
if [ "$READY" != "1" ]; then
  echo "Keycloak nao ficou pronto em 12 min (ver ${KC_POC_LOG:-/tmp/kc-poc.log})" >&2
  exit 3
fi
TOKEN="$(curl -fsS -X POST "http://127.0.0.1:${KC_PORT}/realms/master/protocol/openid-connect/token" \
  -d "username=admin&password=${ADMIN_PASSWORD}&grant_type=password&client_id=admin-cli" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")"
curl -fsS -X POST "http://127.0.0.1:${KC_PORT}/admin/realms" \
  -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
  -d '{"realm":"poc","enabled":true,"organizationsEnabled":true}' >/dev/null
curl -fsS -X POST "http://127.0.0.1:${KC_PORT}/admin/realms/poc/organizations" \
  -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
  -d '{"name":"poc-saneamento","alias":"poc-saneamento","domains":[{"name":"poc.example.invalid"}]}' >/dev/null
curl -fsS "http://127.0.0.1:${KC_PORT}/admin/realms/poc/organizations" \
  -H "Authorization: Bearer ${TOKEN}"
echo
echo "PoC: organizacao criada. Falta o passo manual do tema (ver docs/arquitetura/keycloak-organizations-poc.md §3)."
