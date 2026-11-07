#!/usr/bin/env bash
# P3.7 — Abre um ciclo de homologacao fiscal local criando a pasta do ciclo
# SOMENTE dentro da area separada.
#
# Uso:  ZIIPSAN_HOMOLOGACAO_AREA=/caminho/fora-do-repo/area-separada \
#       ./iniciar-ciclo.sh 2026-11-ciclo-01
#
# A area separada NUNCA pode ser: dentro do repositorio, dentro da pasta de
# backups locais (P0.28) ou dentro do armazenamento imutavel (P2.18). O script
# recusa esses destinos. Nenhum XML assinado e gravado por este script: ele so
# prepara a pasta e o manifesto do ciclo.
#
# Endurecimento (correcao D5 da revisao): a area e CANONICALIZADA (caminho
# absoluto, links resolvidos) e a recusa roda sobre o caminho canonico em
# minusculas, sem sensibilidade a caixa — "backups/area" (relativo),
# ".../Backups/area" (maiúsculo) e variantes com "." ou links sao recusados
# como os originais.
set -euo pipefail

CICLO="${1:?uso: iniciar-ciclo.sh <id-do-ciclo>}"
case "$CICLO" in
  *[!A-Za-z0-9._-]*) echo "RECUSADO: id de ciclo com caracteres nao admitidos: $CICLO" >&2; exit 1 ;;
esac
AREA="${ZIIPSAN_HOMOLOGACAO_AREA:?defina ZIIPSAN_HOMOLOGACAO_AREA (area separada, fora do repo e dos backups)}"

# Recusa sobre o caminho em minusculas (insensivel a caixa): os mesmos
# destinos proibidos da versao original, agora inescapaveis por caixa ou por
# caminho relativo — repositorio, backups, imutavel. A conferencia roda DUAS
# vezes: antes de criar qualquer pasta (sobre o caminho canonicalizado
# logicamente) e depois (sobre o caminho real, com links resolvidos).
recusar_se_proibida() {
  case "$1" in
    *"/ziip_san"*|*"/ziip-san"*|*"/backups"*|*"/backup"*|*"imutavel"*|*"immutable"*|*"/.git"*)
      echo "RECUSADO: a area separada nao pode ficar no repositorio, nos backups locais nem no armazenamento imutavel: $2" >&2; exit 1 ;;
  esac
}
AREA_CANDIDATA="$(realpath -m -- "$AREA" 2>/dev/null || printf '%s' "$AREA")"
recusar_se_proibida "$(printf '%s' "$AREA_CANDIDATA" | tr '[:upper:]' '[:lower:]')" "$AREA_CANDIDATA"

mkdir -p "$AREA"
AREA_REAL="$(cd "$AREA" && pwd -P)"
recusar_se_proibida "$(printf '%s' "$AREA_REAL" | tr '[:upper:]' '[:lower:]')" "$AREA_REAL"

DESTINO="$AREA_REAL/$CICLO"
if [ -e "$DESTINO" ]; then
  echo "RECUSADO: o ciclo ja existe (sem sobrescrita): $DESTINO" >&2; exit 1
fi
mkdir -p "$DESTINO"
printf '{\n  "ciclo": "%s",\n  "situacao": "ABERTO",\n  "tpAmb": 2\n}\n' "$CICLO" > "$DESTINO/ciclo.json"
echo "Ciclo $CICLO aberto em $DESTINO (tpAmb=2). XMLs assinados da homologacao so podem ser gravados nesta pasta."
