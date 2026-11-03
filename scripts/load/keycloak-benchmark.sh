#!/bin/bash
# Cenário do Keycloak com o kit de benchmark oficial (P0.23) — roteiro, não execução.
#
# O kit oficial é o keycloak/keycloak-benchmark (dataset + Gatling). Executá-lo exige:
#   1. Keycloak 26.8.0 em modo produção (start --optimized, 2 réplicas — RNF-17), não
#      start-dev (o compose local usa start-dev; ver P0.29);
#   2. O build do kit (Maven + Gatling) e o dataset gerado para o realm do portal;
#   3. A meta de pico do portal no vencimento e na emissão da conta registrada (a meta
#      numérica em si não está no plano: é pendência do dono/da P0.29 registrar o pico
#      esperado antes de comparar — sem meta, o kit mede mas não aprova nem reprova).
#
# Nesta onda o kit NÃO foi executado (sem Keycloak em produção nesta VM; ver README).
# O cenário reduzido executável é scripts/load/keycloak-token.js (token endpoint local).
# Quando o ambiente da P0.29 existir, os passos são:
#   git clone https://github.com/keycloak/keycloak-benchmark.git
#   cd keycloak-benchmark/benchmark
#   mvn clean install -DskipTests
#   # gerar dataset para o realm do portal e rodar os cenários de login, refresh e
#   # client_credentials, com o número de usuários do dataset registrado no relatório.
#   # Comparar com ARQUITETURA-ALVO §6 (Keycloak 2 × 1 vCPU / 2 GB) e RNF-17.
set -euo pipefail
echo "Kit oficial do Keycloak: NAO executado nesta onda (ver scripts/load/README.md)."
echo "Cenario reduzido: scripts/load/keycloak-token.js"
exit 2
