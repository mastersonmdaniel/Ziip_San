#!/bin/bash
# P0.29: certificado TLS LOCAL do Keycloak (autoassinado, so para localhost).
# Gera infra/keycloak/certs/tls.crt + tls.key quando ainda nao existem; nunca
# sobrescreve um par existente e nunca versiona nada (pasta ignorada pelo git).
# Uso: scripts/local-tls.sh  (scripts/local-env.sh ja chama este script)
set -euo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$SRC/infra/keycloak/certs"
mkdir -p "$DIR"
if [ -f "$DIR/tls.crt" ] && [ -f "$DIR/tls.key" ]; then
  echo "certificado local ja existe em $DIR (nada feito)"
  exit 0
fi
openssl req -x509 -newkey rsa:2048 -keyout "$DIR/tls.key" -out "$DIR/tls.crt" \
  -days 365 -nodes -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" 2>/dev/null
chmod 600 "$DIR/tls.key"
echo "certificado local gerado em $DIR (autoassinado, valido 365 dias, so localhost)"
