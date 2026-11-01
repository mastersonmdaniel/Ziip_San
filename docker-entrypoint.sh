#!/bin/bash
# Entrypoint da imagem ZIIPSAN (P0.5): UMA imagem, tres modos.
#
#   docker run ziipsan-app:<tag> api      # API (padrao; nao migra na subida, P0.6)
#   docker run ziipsan-app:<tag> worker   # perfil worker (hoje: mesmo processo da api;
#                                          # modo reservado para jobs em background)
#   docker run ziipsan-app:<tag> migrate  # job de migracao (perfil migrate, P0.6)
#
# Nenhum segredo aqui: tudo vem do ambiente em runtime. Portas NAO sao publicadas pela
# imagem: quem roda publica so em 127.0.0.1 (ou usa --network=host com porta propria).
set -eu

MODE="${1:-api}"

case "$MODE" in
  api)
    exec java ${JAVA_OPTS:-} -jar /app/app.jar
    ;;
  worker)
    exec java ${JAVA_OPTS:-} -jar /app/app.jar --spring.profiles.active=worker
    ;;
  migrate)
    # P0.6: o perfil migrate liga migratorDataSource/DatabaseMigrator/
    # TenantProvisioningService e o job sai apos migrar (MigrateExitRunner).
    # Na P0.5 o perfil ainda nao existe: o container migra na subida (flag abaixo)
    # e fica no ar — verifique pelo log e pare o container.
    exec java ${JAVA_OPTS:-} -jar /app/app.jar \
      --spring.profiles.active=migrate \
      --ziipsan.db.migrate-on-startup=true
    ;;
  *)
    echo "modo desconhecido: '$MODE' (esperado: api, worker ou migrate)" >&2
    exit 2
    ;;
esac
