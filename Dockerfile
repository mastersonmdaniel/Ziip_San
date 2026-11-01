# ZIIPSAN — imagem unica api/worker/migrate (P0.5).
#
# Multi-stage: build (Maven, sem testes — os testes rodam no CI) + runtime (JRE enxuto).
# UMA imagem, TRES modos, selecionados pelo entrypoint (docker-entrypoint.sh):
#   api     (padrao)  — processo da API, sem migrar na subida (a migracao e um job separado, P0.6)
#   worker            — mesmo processo, perfil "worker" (modo reservado para jobs em background;
#                       hoje nao ha beans exclusivos de worker: o processo sobe igual a api.
#                       O modo existe para o deploy ja separar replicas de worker.)
#   migrate           — perfil "migrate" + migrate-on-startup=true (P0.6: o job migra e sai;
#                       na P0.5, sem o perfil, o container migra na subida e FICA no ar —
#                       verifique pelo log e pare o container; nao o use como job ainda).
#
# Seguranca:
#   - usuario nao root (ziipsan, UID 10001); o jar e o SBOM pertencem a ele;
#   - NENHUM segredo embutido: senhas, URLs e chaves chegam por variavel de ambiente em
#     runtime (sem elas, a subida falha listando o que falta — ver application.yml);
#   - tag FIXA, nunca :latest (scripts/build-image.sh marca versao + sha do git).
#   - healthcheck em /actuator/health (saude publica, sem token).
#
# SBOM: gerado pelo plugin CycloneDX no build da app (target/bom.json) e copiado para
# /app/sbom/bom.json dentro da imagem. A imagem LEVA os modulos fora do scan de componentes
# da app — quality, losses, gis, mtr, lgpd, billing, metering, collection — como dependencias
# do jar (migrations e entidades JPA de todos os modulos), sem mudar o scanBasePackages do
# ZiipsanApplication (que continua sendo app/core/platform/registry). Eles aparecem no SBOM
# como dependencias; nao sao componentes Spring ativos. Ver docs/arquitetura/IMAGEM.md.
#
# Banco: esta imagem NAO leva PostgreSQL. O banco vem do ambiente (compose PG15-3.4 hoje;
# PG17 + PostGIS 3.7.0 local via PGDG/ci-linux ate existir a tag postgis:17-3.7 — P0.4).
# syntax=docker/dockerfile:1
# Nomes totalmente qualificados (docker.io): o Podman desta VM nao tem
# unqualified-search registries em /etc/containers/registries.conf e nomes curtos falham.
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml lombok.config ./
COPY shared/ ./shared/
COPY platform/ ./platform/
COPY intelligence/ ./intelligence/
COPY grs/ ./grs/
COPY compliance/ ./compliance/
COPY commercial/ ./commercial/
COPY operations/ ./operations/
RUN mvn -B -DskipTests package -pl platform/ziipsan-app -am
# O jar repackageado (sem o sufixo -original) vira /tmp/app.jar, nome estavel para o runtime.
RUN cp "$(ls platform/ziipsan-app/target/ziipsan-app-*.jar | grep -v original | head -1)" /tmp/app.jar

FROM docker.io/library/eclipse-temurin:21-jre
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd -r ziipsan -g 10001 \
  && useradd -r -u 10001 -g ziipsan ziipsan
WORKDIR /app
COPY --from=build --chown=ziipsan:ziipsan /tmp/app.jar /app/app.jar
COPY --from=build --chown=ziipsan:ziipsan /src/platform/ziipsan-app/target/bom.json /app/sbom/bom.json
COPY --chown=ziipsan:ziipsan docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
USER ziipsan
EXPOSE 8090
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f "http://localhost:${ZIIPSAN_PORT:-8090}/actuator/health" || exit 1
ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["api"]
