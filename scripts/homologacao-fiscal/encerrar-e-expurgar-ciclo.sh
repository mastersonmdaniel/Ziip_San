#!/usr/bin/env bash
# P3.7 — Encerra um ciclo e APAGA os XMLs assinados da area separada, com
# exclusao registrada (ciclo, data, quantidade, hash do lote, resultado no
# diario).
#
# Uso:  ZIIPSAN_HOMOLOGACAO_AREA=... ZIIPSAN_HOMOLOGACAO_REGISTROS=/pasta/de/registros \
#       RESULTADO_DIARIO="DIARIO-DE-BORDO.md: <referencia da entrada com o resultado>" \
#       ./encerrar-e-expurgar-ciclo.sh 2026-11-ciclo-01
#
# Pre-condicoes (o script confere e recusa sem elas):
#   - o resultado da rodada esta registrado no diario (RESULTADO_DIARIO);
#   - a pasta do ciclo existe e contem XMLs alem do manifesto;
#   - a pasta do ciclo SO contem os XMLs e o manifesto (arquivo estranho
#     recusa ANTES de qualquer registro ou apagamento — sem estado parcial).
# O registro de exclusao fica FORA da area separada (nos registros), porque a
# area e apagada. O hash do lote usa o mesmo algoritmo de Sha256.doLote:
# hashes individuais ordenados, um por linha, com quebra de linha final.
#
# Esquema do registro (correcao D7 da revisao): EXATAMENTE os 5 campos do
# record Java AreaSeparadaHomologacao.RegistroExclusao (ciclo, dataExclusao,
# quantidade, hashDoLote, resultadoDiario), com as strings escapadas para
# JSON — um RESULTADO_DIARIO com aspas ou quebra de linha NAO quebra mais o
# registro (o printf antigo quebrava).
set -euo pipefail

# Escapa uma string para JSON (aspas, barra invertida e controles usuais).
json_escape() {
  local s="$1"
  s=${s//\\/\\\\}
  s=${s//\"/\\\"}
  s=${s//$'\n'/\\n}
  s=${s//$'\r'/\\r}
  s=${s//$'\t'/\\t}
  printf '%s' "$s"
}

CICLO="${1:?uso: encerrar-e-expurgar-ciclo.sh <id-do-ciclo>}"
case "$CICLO" in
  *[!A-Za-z0-9._-]*) echo "RECUSADO: id de ciclo com caracteres nao admitidos: $CICLO" >&2; exit 1 ;;
esac
AREA="${ZIIPSAN_HOMOLOGACAO_AREA:?defina ZIIPSAN_HOMOLOGACAO_AREA}"
REGISTROS="${ZIIPSAN_HOMOLOGACAO_REGISTROS:?defina ZIIPSAN_HOMOLOGACAO_REGISTROS (fora da area separada)}"
RESULTADO="${RESULTADO_DIARIO:?registre o resultado no diario e informe RESULTADO_DIARIO}"
PASTA="$AREA/$CICLO"
[ -d "$PASTA" ] || { echo "RECUSADO: ciclo nao encontrado: $PASTA" >&2; exit 1; }

mapfile -t XMLS < <(find "$PASTA" -type f -name '*.xml' | sort)
QTD="${#XMLS[@]}"
[ "$QTD" -gt 0 ] || { echo "RECUSADO: nenhum XML na area do ciclo $CICLO (nada a expurgar)" >&2; exit 1; }

# Nada alem dos XMLs e do manifesto pode estar na pasta do ciclo: qualquer
# outro arquivo/subpasta recusa ANTES de gravar o registro (o rmdir antigo
# falhava DEPOIS do registro, deixando estado parcial).
mapfile -t ESTRANHOS < <(find "$PASTA" -mindepth 1 ! -type f ! -name 'ciclo.json' -o -type f ! -name '*.xml' ! -name 'ciclo.json' | sort)
if [ "${#ESTRANHOS[@]}" -gt 0 ]; then
  echo "RECUSADO: a pasta do ciclo contem itens alem dos XMLs e do manifesto (nada foi apagado nem registrado):" >&2
  printf '  %s\n' "${ESTRANHOS[@]}" >&2
  exit 1
fi

# hash do lote (mesmo algoritmo do codigo Java)
HASH_LOTE="$(
  for f in "${XMLS[@]}"; do sha256sum "$f" | awk '{print $1}'; done \
    | sort | awk '{printf "%s\n", $0}' | sha256sum | awk '{print $1}'
)"
DATA="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
mkdir -p "$REGISTROS"
REGISTRO="$REGISTROS/exclusao-registrada-$CICLO.json"
[ -e "$REGISTRO" ] && { echo "RECUSADO: ja existe exclusao registrada deste ciclo: $REGISTRO" >&2; exit 1; }
printf '{\n  "ciclo": "%s",\n  "dataExclusao": "%s",\n  "quantidade": %s,\n  "hashDoLote": "%s",\n  "resultadoDiario": "%s"\n}\n' \
  "$(json_escape "$CICLO")" "$DATA" "$QTD" "$HASH_LOTE" "$(json_escape "$RESULTADO")" > "$REGISTRO"

rm -f "${XMLS[@]}" "$PASTA/ciclo.json"
rmdir "$PASTA"
echo "Ciclo $CICLO expurgado: $QTD XML(s) apagados. Exclusao registrada em $REGISTRO (hash do lote: $HASH_LOTE)."
echo "Proximo passo obrigatorio: rodar varredura-pos-ciclo.sh."
