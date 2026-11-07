#!/bin/bash
# P3.33: remocao por execucao — desfaz UMA operacao em massa aplicada,
# identificada pelo run_id, e nenhuma outra.
#
#   scripts/operacao/desfazer-lote.sh <run_id> <diretorio-da-execucao>
#
# Contrato da execucao (gravado na APLICACAO, nao na reversao):
#   <dir>/run.json       {"runId": "...", "tenantId": "...", "status": "APPLIED", ...}
#                        (o JSON da classe BulkOperation.BulkRun; quando a
#                        execucao gerou efeito por OS, a integracao
#                        acrescenta "businessEffectOrderId" a este arquivo,
#                        a partir do InverseOrderRequest da correcao)
#   <dir>/reverter.sql   a imagem-anterior DESTA execucao: os comandos
#                        que devolvem exatamente as linhas que ela tocou
#                        (UPDATEs com os valores anteriores / DELETEs das
#                        linhas que ela inseriu), gerados na aplicacao.
#
# O script: (1) confere que o run_id pedido e o run_id do run.json —
# divergencia aborta ANTES de qualquer comando (a regra
# run-id-mismatch da classe BulkOperation); (2) confere status APPLIED;
# (3) aplica reverter.sql numa UNICA transacao (psql -1 com
# ON_ERROR_STOP); (4) registra a reversao em public.audit_log.
#
# EFEITO DE NEGOCIO POR OS: se a execucao gerou efeito por OS, a remocao
# de linhas NAO basta — a correcao e a OS inversa do ADR-0039
# (BulkOperation.InverseOrderRequest), aberta na camada de OS. Este
# script recusa quando o run.json declara "businessEffectOrderId".
set -euo pipefail

PROD="${ZIIPSAN_PG_CONTAINER:-ziipsan-postgres-primary}"
RUN_ID="${1:?uso: desfazer-lote.sh <run_id> <diretorio-da-execucao>}"
DIR="${2:?diretorio da execucao e obrigatorio}"
case "$RUN_ID" in
  *[!a-f0-9-]*|'') echo "ERRO: run_id invalido (esperado UUID): '$RUN_ID'" >&2; exit 1 ;;
esac
[ -f "$DIR/run.json" ] || { echo "ERRO: run.json ausente em $DIR" >&2; exit 1; }
[ -f "$DIR/reverter.sql" ] || { echo "ERRO: reverter.sql (imagem-anterior da execucao) ausente em $DIR" >&2; exit 1; }

grep -Eq "\"runId\": *\"$RUN_ID\"" "$DIR/run.json" || {
  echo "ERRO: run-id-mismatch — o run_id pedido nao e o da execucao em $DIR; nada foi desfeito" >&2; exit 1; }
grep -Eq "\"status\": *\"APPLIED\"" "$DIR/run.json" || {
  echo "ERRO: so execucao APPLIED pode ser desfeita (ver status em $DIR/run.json)" >&2; exit 1; }
if grep -Eq "\"businessEffectOrderId\": *\"" "$DIR/run.json"; then
  echo "ERRO: a execucao tem efeito de negocio por OS — a correcao e a OS inversa (ADR-0039), nao a remocao de linhas. Nada foi desfeito." >&2
  exit 1
fi
TENANT_ID="$(grep -oE '"tenantId": *"[^"]*"' "$DIR/run.json" | head -n 1 | cut -d'"' -f4)"
[ -n "$TENANT_ID" ] || { echo "ERRO: tenantId ausente no run.json" >&2; exit 1; }
# O tenantId e interpolado no INSERT da trilha: so UUID entra.
case "$TENANT_ID" in
  *[!a-f0-9-]*|'') echo "ERRO: tenantId invalido (esperado UUID) no run.json: '$TENANT_ID'" >&2; exit 1 ;;
esac

podman exec -i "$PROD" psql -1 -v ON_ERROR_STOP=1 -U postgres -d ziipsan < "$DIR/reverter.sql"
podman exec "$PROD" psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan -c \
  "INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, metadata) VALUES ('$TENANT_ID', 'BULK_RUN', '$RUN_ID', 'DELETE', '{\"task\":\"P3.33\",\"action\":\"BULK_REVERTED\",\"runId\":\"$RUN_ID\"}'::jsonb)"
echo "execucao $RUN_ID desfeita (remocao por execucao) e registrada na trilha."
