#!/bin/bash
# Cria ou atualiza um usuario de DESENVOLVIMENTO no realm sigsan do Keycloak do docker-compose.
#
# Roda o kcadm.sh dentro do container (docker exec): nao precisa de Keycloak CLI na maquina nem da
# porta de administracao exposta. O realm nao traz usuario nenhum (infra/keycloak/realms); cada
# desenvolvedor cria o seu com este script.
#
# Uso (Git Bash):
#   KEYCLOAK_ADMIN_PASSWORD=... SIGSAN_DEV_PASSWORD=... SIGSAN_DEV_TENANT_ID=<uuid> \
#     scripts/kc-dev-user.sh <usuario> [PAPEL ...]
#
#   PAPEL: ADMIN, MANAGER, OPERATOR, VIEWER, registry.reveal-document. Sem papel nenhum o usuario
#   entra, mas o backend nega tudo. Papeis sao acrescentados; os que o usuario ja tem ficam.
#
# Variaveis:
#   KEYCLOAK_ADMIN_PASSWORD  senha do admin de bootstrap (a mesma do .env). Obrigatoria.
#   SIGSAN_DEV_PASSWORD      senha do usuario. Obrigatoria e sem padrao: senha conhecida de todos
#                            acaba em producao. Precisa cumprir a politica do realm (12+ caracteres,
#                            maiuscula, minuscula e digito).
#   SIGSAN_DEV_TENANT_ID     UUID do tenant gravado no atributo tenant_id (vira a claim do token).
#   SIGSAN_DEV_EMAIL         padrao: <usuario>@sigsan.invalid
#   SIGSAN_DEV_FIRST_NAME    padrao: <usuario>
#   SIGSAN_DEV_LAST_NAME     padrao: Desenvolvimento
#   SIGSAN_KC_CONTAINER      padrao: sigsan-keycloak
#   SIGSAN_KC_ADMIN_USER     padrao: admin
#
# As senhas vao para o container por variavel de ambiente (docker exec -e NOME, sem valor na linha
# de comando), entao nao aparecem na lista de processos da maquina nem no historico do shell.
source "$(dirname "$0")/lib.sh"

usage() { sed -n '2,30p' "$0" >&2; exit 2; }

[ "$#" -ge 1 ] || usage
DEV_USERNAME="$1"
shift

ALLOWED_ROLES=" ADMIN MANAGER OPERATOR VIEWER registry.reveal-document "
for role in "$@"; do
  case "$ALLOWED_ROLES" in
    *" $role "*) ;;
    *) die "papel desconhecido: $role (validos:$ALLOWED_ROLES)" ;;
  esac
done

[[ "$DEV_USERNAME" =~ ^[A-Za-z0-9._-]{3,64}$ ]] || die "usuario invalido: use 3 a 64 caracteres [A-Za-z0-9._-]"
[ -n "${KEYCLOAK_ADMIN_PASSWORD:-}" ] || die "defina KEYCLOAK_ADMIN_PASSWORD (a senha do admin do .env)"
[ -n "${SIGSAN_DEV_PASSWORD:-}" ] || die "defina SIGSAN_DEV_PASSWORD (nao existe senha padrao)"
[ -n "${SIGSAN_DEV_TENANT_ID:-}" ] || die "defina SIGSAN_DEV_TENANT_ID (UUID do tenant)"
[[ "$SIGSAN_DEV_TENANT_ID" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] \
  || die "SIGSAN_DEV_TENANT_ID nao e um UUID"

KC_CONTAINER="${SIGSAN_KC_CONTAINER:-sigsan-keycloak}"
docker inspect -f '{{.State.Running}}' "$KC_CONTAINER" 2>/dev/null | grep -q true \
  || die "container $KC_CONTAINER nao esta rodando (docker compose up -d keycloak)"

export KC_ADMIN_USER="${SIGSAN_KC_ADMIN_USER:-admin}"
export KC_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD"
export DEV_USERNAME
export DEV_PASSWORD="$SIGSAN_DEV_PASSWORD"
export DEV_TENANT_ID="$SIGSAN_DEV_TENANT_ID"
export DEV_EMAIL="${SIGSAN_DEV_EMAIL:-$DEV_USERNAME@sigsan.invalid}"
export DEV_FIRST_NAME="${SIGSAN_DEV_FIRST_NAME:-$DEV_USERNAME}"
export DEV_LAST_NAME="${SIGSAN_DEV_LAST_NAME:-Desenvolvimento}"
export DEV_ROLES="$*"

# O script abaixo roda DENTRO do container (bash da imagem do Keycloak). O arquivo de credenciais
# do kcadm fica em /tmp e e apagado ao sair, para nao deixar token de admin no container.
MSYS_NO_PATHCONV=1 docker exec -i \
  -e KC_ADMIN_USER -e KC_ADMIN_PASSWORD -e DEV_USERNAME -e DEV_PASSWORD -e DEV_TENANT_ID \
  -e DEV_EMAIL -e DEV_FIRST_NAME -e DEV_LAST_NAME -e DEV_ROLES \
  "$KC_CONTAINER" bash -s <<'EOF'
set -euo pipefail
KCADM=/opt/keycloak/bin/kcadm.sh
CFG="$(mktemp /tmp/kcadm.XXXXXX)"
trap 'rm -f "$CFG"' EXIT
kc() { "$KCADM" "$@" --config "$CFG"; }

kc config credentials --server http://localhost:8080 --realm master \
  --user "$KC_ADMIN_USER" --password "$KC_ADMIN_PASSWORD" >/dev/null

id="$(kc get users -r sigsan -q username="$DEV_USERNAME" -q exact=true --fields id --format csv --noquotes)"
if [ -z "$id" ]; then
  # Nasce desabilitado e so e habilitado depois que a senha passa pela politica do realm: assim uma
  # senha recusada nao deixa um usuario ativo sem senha.
  kc create users -r sigsan -s username="$DEV_USERNAME" -s enabled=false -s emailVerified=true \
    -s email="$DEV_EMAIL" -s firstName="$DEV_FIRST_NAME" -s lastName="$DEV_LAST_NAME" \
    -s "attributes.tenant_id=[\"$DEV_TENANT_ID\"]" >/dev/null
  id="$(kc get users -r sigsan -q username="$DEV_USERNAME" -q exact=true --fields id --format csv --noquotes)"
  echo "usuario $DEV_USERNAME criado"
else
  kc update "users/$id" -r sigsan -s emailVerified=true \
    -s email="$DEV_EMAIL" -s firstName="$DEV_FIRST_NAME" -s lastName="$DEV_LAST_NAME" \
    -s "attributes.tenant_id=[\"$DEV_TENANT_ID\"]"
  echo "usuario $DEV_USERNAME atualizado"
fi

# Rodar de novo com a mesma senha esbarra no historico de senhas (passwordHistory): nao e erro.
if ! out="$(kc set-password -r sigsan --username "$DEV_USERNAME" --new-password "$DEV_PASSWORD" 2>&1)"; then
  case "$out" in
    *invalidPasswordHistoryMessage*) echo "senha mantida (igual a uma das ultimas)" ;;
    *) echo "$out" >&2; echo "ERRO: senha recusada (usuario recem-criado continua desabilitado)" >&2; exit 1 ;;
  esac
fi
kc update "users/$id" -r sigsan -s enabled=true

if [ -n "$DEV_ROLES" ]; then
  args=()
  for role in $DEV_ROLES; do args+=(--rolename "$role"); done
  kc add-roles -r sigsan --uusername "$DEV_USERNAME" "${args[@]}"
fi

echo "papeis de realm: $(kc get-roles -r sigsan --uusername "$DEV_USERNAME" --effective --fields name --format csv --noquotes | tr '\n' ' ')"
EOF
