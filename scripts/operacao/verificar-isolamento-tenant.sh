#!/bin/bash
# P3.33: prova de isolamento DURANTE a restauracao seletiva — os demais
# tenants seguem lendo e gravando sem alteracao. Usado pelo ensaio com
# tenant sintetico (ROTEIRO-ENSAIO-RESTAURACAO-TENANT.md) sobre um
# tenant-testemunha SINTETICO (nunca sobre tenant real fora do ensaio).
#
#   verificar-isolamento-tenant.sh registrar <schema-testemunha> <arquivo>
#   verificar-isolamento-tenant.sh sondar    <schema-testemunha>
#   verificar-isolamento-tenant.sh conferir  <schema-testemunha> <arquivo>
#
# registrar: fotografia (contagem + SHA-256 por tabela, mesmo comando do
#   restaurar-tenant.sh) das tabelas do schema-testemunha, EXCETO a
#   tabela-sonda restore_isolation_probe.
# sondar: le, grava, atualiza e apaga uma linha na tabela-sonda do
#   tenant-testemunha — prova leitura E escrita durante a restauracao.
#   A tabela-sonda e criada pelo roteiro do ensaio no tenant sintetico:
#     CREATE TABLE <schema>.restore_isolation_probe (
#       id int PRIMARY KEY, note text NOT NULL, updated_at timestamptz NOT NULL DEFAULT now());
# conferir: refaz a fotografia e difere da registrada — divergencia em
#   qualquer tabela (fora a sonda) reprova o isolamento.
set -euo pipefail

PROD="${ZIIPSAN_PG_CONTAINER:-ziipsan-postgres-primary}"
MODO="${1:?uso: verificar-isolamento-tenant.sh registrar|sondar|conferir <schema> [arquivo]}"
SCHEMA="${2:?schema do tenant-testemunha e obrigatorio}"
case "$SCHEMA" in
  *[!a-z0-9_]*|'') echo "ERRO: schema invalido: '$SCHEMA'" >&2; exit 1 ;;
  [0-9]*) echo "ERRO: schema invalido (comeca com digito): '$SCHEMA'" >&2; exit 1 ;;
esac

psql_prod() { podman exec "$PROD" psql -U postgres -d ziipsan -Atc "$1" </dev/null; }

fotografia() {
  local tabela contagem hash
  while IFS= read -r tabela; do
    [ -n "$tabela" ] || continue
    contagem="$(psql_prod "SELECT count(*) FROM \"$SCHEMA\".\"$tabela\"")"
    hash="$(psql_prod "SELECT encode(sha256(convert_to(coalesce(string_agg(linha, E'\n' ORDER BY linha), ''), 'UTF8')), 'hex') FROM (SELECT t::text AS linha FROM \"$SCHEMA\".\"$tabela\" t) s")"
    printf '%s\t%s\t%s\n' "$tabela" "$contagem" "$hash"
  done <<EOF
$(psql_prod "SELECT tablename FROM pg_tables WHERE schemaname = '$SCHEMA' AND tablename <> 'restore_isolation_probe' ORDER BY tablename")
EOF
}

case "$MODO" in
  registrar)
    ARQ="${3:?arquivo da fotografia e obrigatorio}"
    fotografia > "$ARQ"
    echo "fotografia do tenant-testemunha $SCHEMA registrada em $ARQ"
    ;;
  sondar)
    psql_prod "INSERT INTO \"$SCHEMA\".restore_isolation_probe (id, note) VALUES (1, 'sonda-p3.33') ON CONFLICT (id) DO UPDATE SET note = 'sonda-p3.33', updated_at = now()" >/dev/null
    LIDO="$(psql_prod "SELECT note FROM \"$SCHEMA\".restore_isolation_probe WHERE id = 1")"
    [ "$LIDO" = "sonda-p3.33" ] || { echo "ERRO: sonda de leitura/escrita falhou no tenant-testemunha $SCHEMA" >&2; exit 1; }
    psql_prod "DELETE FROM \"$SCHEMA\".restore_isolation_probe WHERE id = 1" >/dev/null
    echo "sonda OK: $SCHEMA leu, gravou, atualizou e apagou durante a restauracao"
    ;;
  conferir)
    ARQ="${3:?arquivo da fotografia registrada e obrigatorio}"
    ATUAL="$(mktemp)"
    fotografia > "$ATUAL"
    if diff -u "$ARQ" "$ATUAL"; then
      echo "isolamento OK: nenhuma tabela do tenant-testemunha $SCHEMA mudou"
      rm -f "$ATUAL"
    else
      echo "ERRO: ISOLAMENTO REPROVADO — tabelas do tenant-testemunha mudaram (diff acima); fotografia atual em $ATUAL" >&2
      exit 1
    fi
    ;;
  *)
    echo "ERRO: modo desconhecido: $MODO" >&2; exit 1 ;;
esac
