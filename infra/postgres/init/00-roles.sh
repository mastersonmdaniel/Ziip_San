#!/bin/bash
# Init do PostgreSQL do ZIIPSAN (docker-entrypoint-initdb.d). Roda uma unica vez, com o diretorio
# de dados vazio, como POSTGRES_USER (o superusuario de bootstrap, "postgres").
#
# As senhas das roles da aplicacao vem do ambiente do container, nunca do repositorio:
#   ZIIPSAN_APP_PASSWORD        role ziipsan_app (a aplicacao)
#   ZIIPSAN_MIGRATOR_PASSWORD   role ziipsan_migrator (migrations)
#   KEYCLOAK_DB_PASSWORD       opcional: cria role e banco proprios do Keycloak
#
# O SQL fica em sql/ (subdiretorio) para o entrypoint nao executa-lo sozinho: ele precisa das
# variaveis psql passadas aqui.
#
# Sem set -u: o entrypoint do postgres faz "source" deste arquivo quando ele nao e executavel, e
# set -u vazaria para o resto do entrypoint.
set -e

: "${ZIIPSAN_APP_PASSWORD:?defina ZIIPSAN_APP_PASSWORD (senha da role ziipsan_app)}"
: "${ZIIPSAN_MIGRATOR_PASSWORD:?defina ZIIPSAN_MIGRATOR_PASSWORD (senha da role ziipsan_migrator)}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_pw="$ZIIPSAN_APP_PASSWORD" \
  -v migrator_pw="$ZIIPSAN_MIGRATOR_PASSWORD" \
  -f /docker-entrypoint-initdb.d/sql/security.sql

# Keycloak com banco e role proprios: antes ele usava o banco ziipsan com a role da aplicacao, e
# suas tabelas ficavam em public ao lado de audit_log.
if [ -n "${KEYCLOAK_DB_PASSWORD:-}" ]; then
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v kc_pw="$KEYCLOAK_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE keycloak LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'kc_pw')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'keycloak') \gexec
SELECT 'CREATE DATABASE keycloak OWNER keycloak'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'keycloak') \gexec
REVOKE ALL ON DATABASE keycloak FROM PUBLIC;
SQL
fi
