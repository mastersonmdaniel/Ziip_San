#!/bin/bash
# Passa arquivos .inp pelo EPANET de verdade (EPANET 2.2 empacotado no WNTR) e imprime as pressoes.
#
# Texto que "parece um .inp" nao prova nada: o parser do EPANET e rigido (palavra-chave errada,
# tempo sem unidade, no inexistente). Este script e a verificacao externa do InpWriter.
#
# Uso:
#   scripts/build.sh -pl intelligence/sigsan-gis -am test -Dtest=InpWriterTest -Dsurefire.failIfNoSpecifiedTests=false
#   scripts/validate-inp.sh                      # valida as amostras geradas pelo teste
#   scripts/validate-inp.sh caminho/arquivo.inp  # valida um arquivo especifico
#
# Requer Docker e rede (instala o wntr no container, que e descartavel).
set -u

BUILD_DIR="${SIGSAN_BUILD_DIR:-$(cygpath -u "$LOCALAPPDATA")/sigsan-build}"
SAMPLES="$BUILD_DIR/sigsan/intelligence/sigsan-gis/target/epanet-samples"

if [ $# -gt 0 ]; then
  TARGET_DIR="$(cd "$(dirname "$1")" && pwd)"
  FILES="$(basename "$1")"
else
  TARGET_DIR="$SAMPLES"
  FILES="$(ls "$SAMPLES"/*.inp 2>/dev/null | xargs -n1 basename | tr '\n' ' ')"
fi
[ -z "$FILES" ] && { echo "nenhum .inp encontrado em $TARGET_DIR"; exit 2; }

MSYS_NO_PATHCONV=1 docker run --rm -v "$(cygpath -m "$TARGET_DIR"):/inp:ro" python:3.12-slim bash -c '
set -e
pip install -q wntr >/dev/null 2>&1 || { echo "falha ao instalar wntr"; exit 3; }
cd /tmp
python - '"$FILES"' <<PY
import sys, wntr
status = 0
for name in sys.argv[1:]:
    path = "/inp/" + name
    try:
        wn = wntr.network.WaterNetworkModel(path)
        res = wntr.sim.EpanetSimulator(wn).run_sim()
        p = res.node["pressure"]
        print("OK   %s: %d nos, %d ligacoes, pressao min=%.2f max=%.2f m" % (
            name, len(wn.node_name_list), len(wn.link_name_list), p.min().min(), p.max().max()))
        bad = int((p < -0.01).sum().sum())
        if bad:
            print("     AVISO: %d leituras com pressao negativa" % bad)
    except Exception as ex:
        status = 1
        print("FALHA %s: %s" % (name, ex))
sys.exit(status)
PY
'
