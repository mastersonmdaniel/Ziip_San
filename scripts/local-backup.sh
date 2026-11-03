#!/bin/bash
# P0.28: backup local + arquivamento de WAL (base do PITR).
#
#   scripts/local-backup.sh <destino>   # pg_dump do banco ziipsan + lista do WAL
#
# Pre-requisitos: ambiente local no ar (scripts/local-env.sh dev) e pg_dump/pg_basebackup
# no host, ou o container do PostgreSQL. O WAL e arquivado pelo proprio PostgreSQL
# (archive_command do compose, volume postgres-wal-archive): este script apenas
# verifica que o arquivamento esta ativo e registra quantos segmentos existem.
# RPO de referencia: 15 min; RTO de referencia: 4 h (sem meta na P0.28 — a meta vale
# na LC.3). Tempos medidos vao para o relatorio do ensaio, nao para este script.
set -euo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${1:-/var/tmp/ziipsan-backup-$(date +%Y%m%d-%H%M%S)}"
mkdir -p "$DEST"
START=$(date +%s)
echo "backup em $DEST"
if command -v podman >/dev/null 2>&1; then
  podman exec ziipsan-postgres-primary pg_dump -U postgres -d ziipsan -Fc -f /tmp/ziipsan.dump \
    && podman cp ziipsan-postgres-primary:/tmp/ziipsan.dump "$DEST/ziipsan.dump"
  WAL_COUNT=$(podman exec ziipsan-postgres-primary sh -c 'ls /var/lib/postgresql/wal-archive 2>/dev/null | wc -l')
  podman exec ziipsan-postgres-primary psql -U postgres -d ziipsan -tAc \
    "SELECT pg_current_wal_lsn()" > "$DEST/wal-lsn.txt"
else
  echo "ERRO: podman nao encontrado (nesta VM o docker daemon e inutilizavel)" >&2; exit 1
fi
echo "segmentos WAL arquivados: $WAL_COUNT"
echo "tempo: $(( $(date +%s) - START ))s"
