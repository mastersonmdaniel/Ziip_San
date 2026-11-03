#!/bin/bash
# PoC Keycloak Organizations (P0.38) — roteiro executável, NÃO executado nesta onda.
# Exige: podman (ou docker) + curl + python3. Nada real: realm e domínio sintéticos.
# Passos:
#   1. Sobe Keycloak 26.8.0 isolado (porta própria 18081, fora do compose do projeto).
#   2. Obtém token do admin de bootstrap e cria o realm "poc" (realm de PoC, não o ziipsan).
#   3. Liga Organizations no realm (Realm Settings) via Admin API.
#   4. Cria a organização "poc-saneamento" com domínio "poc.example.invalid".
#   5. Confere via Admin API: GET /admin/realms/poc/organizations retorna a organização.
#   6. (Manual, no navegador) Abrir o login do realm poc, digitar usuario@poc.example.invalid
#      e verificar se o tema exibe a marca da organização antes da senha — ESTE é o ponto
#      a provar; sem ele, a recomendação de não adotar se mantém (ver doc da PoC).
#   7. Derruba o contêiner e apaga o volume (nada persiste).
set -euo pipefail
RUNTIME="${CONTAINER_RUNTIME:-podman}"
if ! command -v "$RUNTIME" >/dev/null 2>&1; then
  echo "Sem runtime de conteiner ($RUNTIME): PoC nao executavel aqui." >&2
  exit 2
fi
KC_PORT="${KC_POC_PORT:-18081}"
ADMIN_PASSWORD="${KC_POC_ADMIN_PASSWORD:-poc-admin-sintetico}"
"$RUNTIME" run -d --name ziipsan-kc-poc --rm -p "127.0.0.1:${KC_PORT}:8080" \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  quay.io/keycloak/keycloak:26.8.0 start-dev >/dev/null
trap '"$RUNTIME" rm -f ziipsan-kc-poc >/dev/null 2>&1 || true' EXIT
for _ in $(seq 1 60); do
  # start-dev não expõe /health/ready (404): o probe é /realms/master (200 quando sobe).
  curl -fsS "http://127.0.0.1:${KC_PORT}/realms/master" >/dev/null 2>&1 && break
  sleep 2
done
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
