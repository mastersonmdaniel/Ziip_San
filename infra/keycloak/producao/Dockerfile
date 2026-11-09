# LC.6 — Imagem de produção do Keycloak (AWS)
# Produtor: sessão 92031a19-4d6d-4174-92b7-164897d85503 (papel: Produtor)
#
# Derivação (fontes locais lidas):
# - Aceite LC.6 (fonte única): "Keycloak de produção com a configuração do
#   modo de produção da P0.29 reaplicada na AWS: start --optimized, TLS,
#   sslRequired=all e 2 réplicas; derrubada de uma réplica sem perda de
#   sessão (teste); tema da P0.35 na imagem".
# - Aceite P0.29: Keycloak local com `start --optimized`, TLS e
#   `sslRequired=all`, duas réplicas, PostgreSQL 17 da P0.4.
# - Espelho local do modo de produção (docker-compose.yml conferido em
#   pacote local, serviço keycloak/keycloak-2): imagem
#   quay.io/keycloak/keycloak:26.8.0 (tag exata), comando
#   `kc.sh build && exec kc.sh start --optimized --import-realm`,
#   KC_CACHE=ispn, KC_CACHE_STACK=jdbc-ping, KC_HEALTH_ENABLED=true,
#   certificado em /opt/keycloak/certs/tls.crt|tls.key.
# - Aceite P0.35: realm com loginTheme, emailTheme e displayName gerados
#   do pacote de marca (P0.34, ZIIPSAN_BRAND); "o tema entra na imagem de
#   produção do Keycloak da P0.29".
#
# [SEM FONTE] Tema da P0.35: nenhum arquivo de tema (login/e-mail) existe
# nos pacotes locais conferidos nesta produção — o diretório de tema é
# entrada de build nomeada (pendência P-LC6-02), montado em
# /opt/keycloak/themes/ziipsan na etapa de build a partir do pacote de
# marca. Este Dockerfile NÃO embute tema inventado: sem o diretório de
# tema no contexto de build, o build deve falhar (ver README do pacote).
# Nenhum segredo entra na imagem: credenciais e certificado são injetados
# em tempo de execução (variáveis/segredos da implantação, ver
# keycloak-producao.env.example e valores-implantacao.yaml).

FROM quay.io/keycloak/keycloak:26.8.0 AS builder

# Tema da P0.35 (login + e-mail), gerado do pacote de marca.
# Contexto de build esperado: themes/ziipsan/ ao lado deste Dockerfile.
COPY themes/ziipsan/ /opt/keycloak/themes/ziipsan/

# Build otimizado do Keycloak (mesma etapa do `kc.sh build` local):
# congela as opções de build (banco, cache, health/metrics) para que a
# partida seja `start --optimized`, sem reconfiguração em runtime.
ENV KC_DB=postgres \
    KC_CACHE=ispn \
    KC_CACHE_STACK=jdbc-ping \
    KC_HEALTH_ENABLED=true \
    KC_METRICS_ENABLED=true
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.8.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/

# Partida de produção (aceite LC.6/P0.29): start --optimized.
# O realm é importado na primeira subida a partir do volume/ConfigMap de
# importação montado pela implantação (realm-producao.json deste pacote,
# com sslRequired=all e os temas da P0.35 referenciados).
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
