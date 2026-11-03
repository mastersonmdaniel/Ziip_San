#!/bin/sh
# Gera a userlist do PgBouncer NA SUBIDA, a partir das senhas do ambiente (.env).
# Nada de senha e versionado, nem vai para a imagem: o arquivo nasce aqui, com 0600.
set -eu
: "${ZIIPSAN_APP_PASSWORD:?defina ZIIPSAN_APP_PASSWORD}"
: "${ZIIPSAN_MIGRATOR_PASSWORD:?defina ZIIPSAN_MIGRATOR_PASSWORD}"
umask 077
printf '"ziipsan_app" "%s"\n"ziipsan_migrator" "%s"\n' \
  "$ZIIPSAN_APP_PASSWORD" "$ZIIPSAN_MIGRATOR_PASSWORD" > /etc/pgbouncer/userlist.txt
chmod 600 /etc/pgbouncer/userlist.txt
exec pgbouncer /etc/pgbouncer/pgbouncer.ini
