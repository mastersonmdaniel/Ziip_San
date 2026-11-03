# P0.28: PgBouncer em modo transaction, construido sobre Debian estavel (sem tag de
# imagem de PgBouncer inventada: o pacote vem do apt do Debian bookworm).
FROM debian:bookworm-slim
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends pgbouncer \
    && rm -rf /var/lib/apt/lists/*
COPY pgbouncer.ini /etc/pgbouncer/pgbouncer.ini
COPY docker-entrypoint.sh /usr/local/bin/pgbouncer-entrypoint.sh
RUN chmod +x /usr/local/bin/pgbouncer-entrypoint.sh
USER postgres
ENTRYPOINT ["/usr/local/bin/pgbouncer-entrypoint.sh"]
