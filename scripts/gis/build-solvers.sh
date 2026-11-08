#!/usr/bin/env bash
# ED-63/OPE-35 — constrói os solvers oficiais a partir da fonte, com versão fixada.
# EPANET 2.2 (OpenWaterAnalytics/EPANET, tag v2.2, licença MIT) e
# SWMM 5.2.4 (USEPA/Stormwater-Management-Model, tag v5.2.4, domínio público).
# Uso no CI: build-solvers.sh <dir-destino>  → gera <destino>/runepanet e <destino>/runswmm
# e grava SOLVERS-MANIFEST.txt com versões, URLs, hashes dos tarballs e licenças.
set -euo pipefail
DEST="${1:?destino obrigatorio}"
mkdir -p "$DEST/src" "$DEST/bin"
cd "$DEST/src"
curl -fsSL -o epanet.tar.gz https://codeload.github.com/OpenWaterAnalytics/EPANET/tar.gz/refs/tags/v2.2
curl -fsSL -o swmm.tar.gz https://codeload.github.com/USEPA/Stormwater-Management-Model/tar.gz/refs/tags/v5.2.4
sha256sum epanet.tar.gz swmm.tar.gz > "$DEST/SOLVERS-MANIFEST.txt"
tar xzf epanet.tar.gz && tar xzf swmm.tar.gz
cmake -S EPANET-2.2 -B epanet-build -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build epanet-build -j4 >/dev/null
cmake -S Stormwater-Management-Model-5.2.4 -B swmm-build -DCMAKE_BUILD_TYPE=Release >/dev/null
cmake --build swmm-build -j4 >/dev/null
cp epanet-build/bin/runepanet "$DEST/bin/runepanet"
cp swmm-build/bin/Release/runswmm "$DEST/bin/runswmm"
{
  echo "EPANET 2.2 (tag v2.2) - MIT License - https://github.com/OpenWaterAnalytics/EPANET"
  echo "SWMM 5.2.4 (tag v5.2.4) - Public Domain - https://github.com/USEPA/Stormwater-Management-Model"
} >> "$DEST/SOLVERS-MANIFEST.txt"
echo "OK: $DEST/bin/runepanet e $DEST/bin/runswmm"
