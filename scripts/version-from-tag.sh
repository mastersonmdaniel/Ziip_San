#!/bin/bash
# P0.39: deriva a versao do Maven (-Drevision) a partir da tag git.
#
#   scripts/version-from-tag.sh            # imprime a versao (ex.: 1.2.0)
#   mvn -Drevision="$(scripts/version-from-tag.sh)" clean verify
#
# Regras (deterministicas: dois builds do mesmo commit dao a mesma versao):
#   - tag exata no HEAD (ex.: v1.2.0) -> 1.2.0 (o 'v' e removido);
#   - sem tag exata -> 0.0.0-SNAPSHOT (build de desenvolvimento, nunca uma versao
#     inventada a partir do hash: o hash varia por commit, mas a versao de dev e fixa).
# Tags devem ser SemVer (vMAJOR.MINOR.PATCH); qualquer outra tag e ignorada e cai
# para 0.0.0-SNAPSHOT, em vez de propagar um valor invalido para o build.
set -euo pipefail
TAG="$(git describe --tags --exact-match HEAD 2>/dev/null || true)"
if [[ "$TAG" =~ ^v?([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  echo "${BASH_REMATCH[1]}"
else
  echo "0.0.0-SNAPSHOT"
fi
