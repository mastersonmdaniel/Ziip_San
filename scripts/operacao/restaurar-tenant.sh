#!/bin/bash
# P3.33: restauracao seletiva de UM tenant a um instante anterior, sem
# afetar os demais tenants do cluster. Tres fases explicitas:
#
#   scripts/operacao/restaurar-tenant.sh extrair  <schema> <instante-alvo ISO>
#   scripts/operacao/restaurar-tenant.sh comparar <schema> <diretorio-da-extracao>
#   scripts/operacao/restaurar-tenant.sh aplicar  <schema> <diretorio-da-extracao> <REPLACE|MERGE>
#
# Pre-condicao da fase `extrair`: o PITR da P0.28 ja foi restaurado na
# INSTANCIA LATERAL (roteiro de scripts/local-pitr.sh: PostgreSQL
# descartavel, NUNCA o banco de producao), no container
# ${ZIIPSAN_LATERAL_CONTAINER:-ziipsan-pitr-lateral}, parado exatamente
# no instante-alvo. Este script nao faz PITR: ele extrai DA lateral.
#
# extrair:  pg_dump -n <schema> da lateral + fotografia por tabela
#           (contagem e SHA-256 do conteudo ordenado) em fotografia.tsv.
# comparar: fotografia do estado ATUAL no mesmo formato e diff entre as
#           duas — a saida (comparacao.txt) e o que o tenant aprova e o
#           que a classe RestoreDiff classifica em Java.
# aplicar:  exige, TODOS:
#           - consentimento do tenant em arquivo (CONSENTIMENTO_JSON):
#             JSON nos moldes do ADR-0020 — o toJson de RestoreConsent
#             (consentId, tenantId, mode, targetInstant, grantedAt,
#             expiresAt, revokedAt; NAO ha campo de schema nesse JSON:
#             o vinculo schema<->tenant e conferido pelo tenantId do
#             catalogo) — com tenantId, targetInstant e mode iguais aos
#             desta execucao, grantedAt ja vigente, expiresAt no futuro
#             e sem revogacao — conferido aqui e, na integracao, pelo
#             portao TenantRestorePolicy (papel PLATFORM_OWNER e
#             step-up sao da camada de API/plataforma, nao de um shell);
#           - confirmacao explicita APLICAR_RESTAURACAO=<schema> no
#             ambiente (nenhuma aplicacao acontece por acidente);
#           - fotografia pre-restauracao do schema atual (a rede de
#             seguranca: o "antes" fica em pre-restauracao.dump).
#           REPLACE: drop do schema e pg_restore da extracao.
#           MERGE: NAO automatizado por este script — a mesclagem e
#           tabela a tabela, a partir de comparacao.txt, com comandos
#           revisados um a um (o script para e aponta o runbook).
#           Ao fim, registra a aplicacao em public.audit_log (a trilha
#           existente — a P3.33 nao tem migration, plano v3.0 §3).
#
# ISOLAMENTO: todo comando e -n <schema> ou qualificado pelo schema;
# nenhum outro schema e lido para escrita nem alterado. A prova durante
# o ensaio e do verificar-isolamento-tenant.sh.
set -euo pipefail

PROD="${ZIIPSAN_PG_CONTAINER:-ziipsan-postgres-primary}"
LATERAL="${ZIIPSAN_LATERAL_CONTAINER:-ziipsan-pitr-lateral}"
FASE="${1:?uso: restaurar-tenant.sh extrair|comparar|aplicar ...}"
SCHEMA="${2:?schema do tenant e obrigatorio}"
case "$SCHEMA" in
  *[!a-z0-9_]*|'') echo "ERRO: schema invalido: '$SCHEMA'" >&2; exit 1 ;;
  [0-9]*) echo "ERRO: schema invalido (comeca com digito): '$SCHEMA'" >&2; exit 1 ;;
esac

psql_em() { podman exec "$1" psql -U postgres -d ziipsan -Atc "$2" </dev/null; }

# O instante-alvo entra em grep, em nome de diretorio e, na fase aplicar,
# interpolado no INSERT da trilha: qualquer caractere fora do alfabeto
# ISO-8601 (aspas, espaco, ';') e recusado antes de qualquer uso.
validar_instante() {
  case "$1" in
    *[!0-9TZ:.+-]*|'') echo "ERRO: instante-alvo invalido (esperado ISO-8601, ex.: 2027-02-01T12:00:00Z): '$1'" >&2; exit 1 ;;
  esac
}

fotografia() { # $1=container $2=arquivo de saida
  local container="$1" saida="$2" tabela
  : > "$saida"
  while IFS= read -r tabela; do
    [ -n "$tabela" ] || continue
    local contagem hash
    contagem="$(psql_em "$container" "SELECT count(*) FROM \"$SCHEMA\".\"$tabela\"")"
    # SHA-256 do conteudo canonico: linhas em texto, ordenadas, com
    # COALESCE para a tabela vazia (hash da cadeia vazia). O MESMO
    # comando dos dois lados — e o hash que RestoreDiff.TableSnapshot
    # exige (64 hex minusculos) na integracao.
    hash="$(psql_em "$container" \
      "SELECT encode(sha256(convert_to(coalesce(string_agg(linha, E'\n' ORDER BY linha), ''), 'UTF8')), 'hex') FROM (SELECT t::text AS linha FROM \"$SCHEMA\".\"$tabela\" t) s")"
    printf '%s\t%s\t%s\n' "$tabela" "$contagem" "$hash" >> "$saida"
  done <<EOF
$(psql_em "$container" "SELECT tablename FROM pg_tables WHERE schemaname = '$SCHEMA' ORDER BY tablename")
EOF
}

case "$FASE" in
  extrair)
    ALVO="${3:?instante-alvo ISO e obrigatorio (ex.: 2027-02-01T12:00:00Z)}"
    validar_instante "$ALVO"
    DEST="${ZIIPSAN_RESTAURACAO_DIR:-/var/tmp/ziipsan-restauracao}/$SCHEMA/$(date +%Y%m%d-%H%M%S)"
    mkdir -p "$DEST"
    echo "$ALVO" > "$DEST/instante-alvo.txt"
    podman exec "$LATERAL" pg_dump -U postgres -d ziipsan -Fc -n "$SCHEMA" -f /tmp/extracao-tenant.dump
    podman cp "$LATERAL:/tmp/extracao-tenant.dump" "$DEST/tenant.dump"
    (cd "$DEST" && sha256sum tenant.dump > SHA256SUMS)
    fotografia "$LATERAL" "$DEST/fotografia-extraida.tsv"
    echo "extracao em $DEST (instante-alvo $ALVO). Proxima fase: comparar."
    ;;
  comparar)
    DIR="${3:?diretorio da extracao e obrigatorio}"
    [ -f "$DIR/fotografia-extraida.tsv" ] || { echo "ERRO: fotografia-extraida.tsv ausente em $DIR" >&2; exit 1; }
    fotografia "$PROD" "$DIR/fotografia-atual.tsv"
    # diff completo: tabelas iguais somem; o restante e a comparacao
    # apresentada ao tenant e classificada por RestoreDiff na integracao.
    diff -u "$DIR/fotografia-atual.tsv" "$DIR/fotografia-extraida.tsv" > "$DIR/comparacao.txt" || true
    echo "comparacao em $DIR/comparacao.txt (atual x extraida). Revise COM o tenant antes de aplicar."
    ;;
  aplicar)
    DIR="${3:?diretorio da extracao e obrigatorio}"
    MODO="${4:?modo e obrigatorio: REPLACE ou MERGE (o consentido pelo tenant)}"
    [ "$MODO" = "REPLACE" ] || {
      echo "MERGE nao e automatizado: mesclagem e tabela a tabela a partir de $DIR/comparacao.txt, com comandos revisados (runbook RB-07 §6). Nada foi aplicado." >&2; exit 1; }
    [ "${APLICAR_RESTAURACAO:-}" = "$SCHEMA" ] || {
      echo "ERRO: confirmacao explicita ausente — rode com APLICAR_RESTAURACAO=$SCHEMA no ambiente" >&2; exit 1; }
    CONSENTIMENTO="${CONSENTIMENTO_JSON:?CONSENTIMENTO_JSON (aprovacao do tenant, moldes do ADR-0020) e obrigatorio}"
    [ -f "$CONSENTIMENTO" ] || { echo "ERRO: arquivo de consentimento nao encontrado: $CONSENTIMENTO" >&2; exit 1; }
    [ -f "$DIR/comparacao.txt" ] || { echo "ERRO: comparacao.txt ausente em $DIR — a fase comparar (apresentada ao tenant) precede a aplicacao" >&2; exit 1; }
    ALVO="$(cat "$DIR/instante-alvo.txt")"
    validar_instante "$ALVO"
    grep -Eq "\"targetInstant\": *\"$ALVO\"" "$CONSENTIMENTO" || { echo "ERRO: instante-alvo do consentimento difere do da extracao ($ALVO)" >&2; exit 1; }
    grep -Eq "\"mode\": *\"$MODO\"" "$CONSENTIMENTO" || { echo "ERRO: modo do consentimento difere do pedido ($MODO)" >&2; exit 1; }
    # Vigencia do consentimento (ADR-0020): revogado, ainda nao vigente
    # ou expirado NAO aplica — os campos sao os do toJson de
    # RestoreConsent (revokedAt sai como null JSON quando nao revogado).
    if grep -Eq '"revokedAt": *"[^"]' "$CONSENTIMENTO"; then
      echo "ERRO: consentimento revogado — aplicacao recusada" >&2; exit 1; fi
    CONCEDIDO_EM="$(grep -oE '"grantedAt": *"[^"]*"' "$CONSENTIMENTO" | head -n 1 | cut -d'"' -f4)"
    EXPIRA_EM="$(grep -oE '"expiresAt": *"[^"]*"' "$CONSENTIMENTO" | head -n 1 | cut -d'"' -f4)"
    [ -n "$CONCEDIDO_EM" ] && [ -n "$EXPIRA_EM" ] || { echo "ERRO: consentimento sem grantedAt/expiresAt" >&2; exit 1; }
    AGORA_S="$(date -u +%s)"
    CONCEDIDO_S="$(date -u -d "$CONCEDIDO_EM" +%s 2>/dev/null)" || { echo "ERRO: grantedAt ilegivel no consentimento: $CONCEDIDO_EM" >&2; exit 1; }
    EXPIRA_S="$(date -u -d "$EXPIRA_EM" +%s 2>/dev/null)" || { echo "ERRO: expiresAt ilegivel no consentimento: $EXPIRA_EM" >&2; exit 1; }
    [ "$AGORA_S" -ge "$CONCEDIDO_S" ] || { echo "ERRO: consentimento ainda nao vigente (grantedAt $CONCEDIDO_EM)" >&2; exit 1; }
    [ "$AGORA_S" -lt "$EXPIRA_S" ] || { echo "ERRO: consentimento expirado em $EXPIRA_EM — aplicacao recusada" >&2; exit 1; }
    TENANT_ID="$(psql_em "$PROD" "SELECT id FROM public.tenant_catalog WHERE schema_name = '$SCHEMA'")"
    [ -n "$TENANT_ID" ] || { echo "ERRO: tenant nao encontrado no catalogo para $SCHEMA" >&2; exit 1; }
    grep -Eq "\"tenantId\": *\"$TENANT_ID\"" "$CONSENTIMENTO" || { echo "ERRO: tenantId do consentimento difere do catalogo" >&2; exit 1; }
    (cd "$DIR" && sha256sum -c SHA256SUMS) || { echo "ERRO: hash da extracao divergente — aplicacao abortada" >&2; exit 1; }

    INICIO="$(date +%s)"
    # Rede de seguranca: o estado atual ANTES da substituicao.
    podman exec "$PROD" pg_dump -U postgres -d ziipsan -Fc -n "$SCHEMA" -f /tmp/pre-restauracao.dump
    podman cp "$PROD:/tmp/pre-restauracao.dump" "$DIR/pre-restauracao.dump"
    podman exec "$PROD" psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan -c "DROP SCHEMA \"$SCHEMA\" CASCADE"
    # O dump entra no container por `podman cp` e o pg_restore le o
    # ARQUIVO la dentro — o mesmo padrao da extracao (dump em /tmp +
    # cp). Redirecionar o dump do host para o stdin de um `podman exec`
    # SEM -i nao entrega byte algum ao pg_restore: ele falharia (ou
    # restauraria nada) DEPOIS do DROP SCHEMA acima.
    podman cp "$DIR/tenant.dump" "$PROD:/tmp/restauracao-tenant.dump"
    podman exec "$PROD" pg_restore --exit-on-error -U postgres -d ziipsan /tmp/restauracao-tenant.dump
    fotografia "$PROD" "$DIR/fotografia-pos.tsv"
    diff -q "$DIR/fotografia-extraida.tsv" "$DIR/fotografia-pos.tsv" >/dev/null \
      || { echo "ERRO: fotografia pos-restauracao difere da extraida — ver $DIR/fotografia-pos.tsv; o antes esta em pre-restauracao.dump" >&2; exit 1; }
    # Trilha na auditoria existente (sem migration — plano v3.0 §3).
    RESTORE_ID="$(cat /proc/sys/kernel/random/uuid)"
    podman exec "$PROD" psql -v ON_ERROR_STOP=1 -U postgres -d ziipsan -c \
      "INSERT INTO public.audit_log (tenant_id, entity_type, entity_id, action, metadata) VALUES ('$TENANT_ID', 'TENANT_RESTORE', '$RESTORE_ID', 'IMPORT', '{\"task\":\"P3.33\",\"action\":\"RESTORE_APPLIED\",\"mode\":\"$MODO\",\"targetInstant\":\"$ALVO\"}'::jsonb)"
    echo "restauracao REPLACE de $SCHEMA para $ALVO aplicada e auditada (restore_id $RESTORE_ID)."
    echo "RTO desta restauracao: $(( $(date +%s) - INICIO ))s (registrar no Registro do Ensaio/operacao — RNF novo da P3.33, sem meta antes da LC.3)"
    ;;
  *)
    echo "ERRO: fase desconhecida: $FASE (use extrair, comparar ou aplicar)" >&2; exit 1 ;;
esac
