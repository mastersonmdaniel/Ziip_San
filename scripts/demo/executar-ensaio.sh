#!/bin/bash
# P3.23 -- Ensaio cronometrado de POC: abre e fecha o cronometro.
#
# A demonstracao de POC e executada por pessoas (roteiros da P1.25); este
# script so garante que o TEMPO DE PREPARO e medido de verdade e que o
# resultado por item vira registro avaliado pela regua do aceite:
#
#   bash scripts/demo/executar-ensaio.sh iniciar
#   # ... preparar o ambiente (recriar-demo.sh) e executar o roteiro,
#   #     preenchendo docs/demo/resultados-ensaio (uma linha por item) ...
#   bash scripts/demo/executar-ensaio.sh concluir resultados.csv [feriados.txt]
#
# "iniciar" grava o instante (ISO) num marcador local nao versionado.
# "concluir" chama avaliar-ensaio.py com inicio=marcador e fim=agora e
# grava resultado-ensaio.json + cobertura-poc.csv na pasta de saida.
# Sem marcador, "concluir" PARA: ensaio sem inicio medido nao e ensaio
# cronometrado.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
MARCADOR="${ENSAIO_MARCADOR:-$DIR/.ensaio-inicio.local}"
ACAO="${1:-}"

case "$ACAO" in
  iniciar)
    date --iso-8601=seconds > "$MARCADOR"
    echo "cronometro aberto em $(cat "$MARCADOR") (marcador: $MARCADOR)"
    echo "orcamentos do aceite: 5 e 7 dias uteis de preparo (avaliar-ensaio.py confere os dois)"
    ;;
  concluir)
    RESULTADOS="${2:-}"
    [ -n "$RESULTADOS" ] || { echo "uso: executar-ensaio.sh concluir <resultados.csv> [feriados.txt]" >&2; exit 2; }
    [ -f "$MARCADOR" ] || { echo "ERRO: sem marcador de inicio -- rode 'executar-ensaio.sh iniciar' antes de preparar" >&2; exit 2; }
    INICIO="$(cat "$MARCADOR")"
    FIM="$(date --iso-8601=seconds)"
    STAMP="$(date +%Y%m%d-%H%M%S)"
    OUT="${ENSAIO_OUT_DIR:-$DIR/saidas/ensaio-$STAMP}"
    echo "ensaio: inicio=$INICIO fim=$FIM saida=$OUT"
    EXTRA=()
    [ -n "${3:-}" ] && EXTRA=(--feriados "$3")
    python3 "$DIR/avaliar-ensaio.py" --resultados "$RESULTADOS" \
      --inicio "$INICIO" --fim "$FIM" "${EXTRA[@]}" --saida "$OUT"
    ;;
  *)
    echo "uso: executar-ensaio.sh iniciar | concluir <resultados.csv> [feriados.txt]" >&2
    exit 2
    ;;
esac
