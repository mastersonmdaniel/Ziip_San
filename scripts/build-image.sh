#!/bin/bash
# Constroi a imagem ZIIPSAN com tag FIXA (nunca :latest): versao do pom + sha curto do git.
#   scripts/build-image.sh            # podman se existir, senao docker
#   CONTAINER_RUNTIME=docker scripts/build-image.sh
#
# Nesta VM o daemon Docker nao funciona (pull EOF + runc setns); o caminho verificado e
# Podman + crun + --cgroups=disabled + --network=host (ver STATUS.md, turno 3). As flags
# Podman so sao passadas quando o runtime e podman.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SRC"

RUNTIME="${CONTAINER_RUNTIME:-}"
if [ -z "$RUNTIME" ]; then
  if command -v podman >/dev/null 2>&1; then
    RUNTIME=podman
  elif command -v docker >/dev/null 2>&1; then
    RUNTIME=docker
  else
    echo "ERRO: nem podman nem docker encontrados" >&2
    exit 1
  fi
fi

VERSION="$(grep -m1 '<version>' pom.xml | sed -e 's/.*<version>//' -e 's/<\/version>.*//')"
SHA="$(git rev-parse --short HEAD)"
TAG="ziipsan-app:${VERSION}-${SHA}"
echo "runtime: $RUNTIME, tag: $TAG"

if [ "$RUNTIME" = "podman" ]; then
  # --cgroups=disabled e --network=host sao flags de `podman run`, nao de `build`
  # (o build as rejeita). --isolation=chroot: o build roda os passos RUN sem o
  # runtime OCI (crun), que nesta VM falha sem systemd ("cannot open sd-bus").
  # O `podman run` continua precisando de --cgroups=disabled --network=host.
  podman build --isolation=chroot --storage-driver=vfs \
    -t "$TAG" -f Dockerfile .
else
  docker build -t "$TAG" -f Dockerfile .
fi
echo "imagem pronta: $TAG (sem :latest)"
