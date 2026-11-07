#!/bin/bash
# P2.9 -- Coleta de uso de recursos durante a carga (o "uso de recursos" do
# registro): amostra `docker stats` dos containers do ambiente a cada
# INTERVALO_SEG e grava CSV (epoch, container, cpu_pct, mem_usada, mem_pct).
# Sem Docker, cai para /proc (cpu/mem do host). Uso:
#   coletar-recursos.sh <dir-saida> <duracao-seg> [intervalo-seg]
set -uo pipefail
OUT="${1:?dir de saida}"; DUR="${2:?duracao em seg}"; INT="${3:-10}"
mkdir -p "$OUT"
CSV="$OUT/recursos.csv"
echo "epoch,container,cpu_pct,mem,mem_pct" > "$CSV"
FIM=$(( $(date +%s) + DUR ))
while [ "$(date +%s)" -lt "$FIM" ]; do
  if command -v docker >/dev/null 2>&1 && docker stats --no-stream >/dev/null 2>&1; then
    docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}' 2>/dev/null \
      | while IFS= read -r linha; do echo "$(date +%s),$linha"; done >> "$CSV"
  else
    read -r _ u n s i _ < /proc/stat
    echo "$(date +%s),host,$(( (u+n+s) * 100 / (u+n+s+i+1) ))%,$(free -m | awk '/^Mem:/{print $3"M"}')," >> "$CSV"
  fi
  sleep "$INT"
done
echo "recursos em $CSV" >&2
