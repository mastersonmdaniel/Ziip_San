#!/bin/sh
# Gera a userlist do PgBouncer NA SUBIDA, a partir das senhas do ambiente (.env).
# Nada de senha e versionado, nem vai para a imagem: o arquivo nasce aqui, com 0600.
set -eu
: "${ZIIPSAN_APP_PASSWORD:?defina ZIIPSAN_APP_PASSWORD}"
: "${ZIIPSAN_MIGRATOR_PASSWORD:?defina ZIIPSAN_MIGRATOR_PASSWORD}"
umask 077
printf '"ziipsan_app" "%s"\n"ziipsan_migrator" "%s"\n' \
  "$ZIIPSAN_APP_PASSWORD" "$ZIIPSAN_MIGRATOR_PASSWORD" > /etc/pgbouncer/userlist.txt
# P2.2: pools por papel sao OPCIONAIS aqui (as senhas nao sao exigidas, para
# nenhum .env existente deixar de subir): cada papel so entra na userlist se
# a sua senha foi informada. Sem a linha, o PgBouncer recusa o papel — o que
# e o comportamento certo, e nao um pool silenciosamente sem autenticacao.
# Cada usuario e um pool distinto no PgBouncer em modo transaction (o pool e
# por par usuario/banco): e essa separacao que isola lote e ingestao da API.
if [ -n "${ZIIPSAN_BATCH_PASSWORD:-}" ]; then
  printf '"ziipsan_batch" "%s"\n' "$ZIIPSAN_BATCH_PASSWORD" >> /etc/pgbouncer/userlist.txt
fi
if [ -n "${ZIIPSAN_INGEST_PASSWORD:-}" ]; then
  printf '"ziipsan_ingest" "%s"\n' "$ZIIPSAN_INGEST_PASSWORD" >> /etc/pgbouncer/userlist.txt
fi
chmod 600 /etc/pgbouncer/userlist.txt
exec pgbouncer /etc/pgbouncer/pgbouncer.ini
