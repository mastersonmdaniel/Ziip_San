#!/usr/bin/env bash
# ED-04 -- para o pacote de reversao. SEM -v por padrao: os volumes sao o
# dado revertido do tenant; apagar volume e decisao registrada no ensaio.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env.reversao ] || { echo "ERRO: .env.reversao nao encontrado (copie de .env.reversao.example e preencha fora do repositorio)"; exit 1; }
export COMPOSE_FILE=docker-compose.reversao.yml
docker compose --env-file .env.reversao down
echo "Pacote parado (volumes preservados: postgres-primary-data, postgres-wal-archive, vault-data, minio-data)."
