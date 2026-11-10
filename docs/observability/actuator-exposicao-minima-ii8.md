# Actuator/Micrometer — exposição mínima declarada (II-8)

**Pacote:** II-8 da Onda II do PDCA-01 (decisão 64 do dono).
**Base lida e verificada:** clone em `1d755053` — `platform/ziipsan-app`
(P0.8: Actuator + Micrometer) e a entrega da LC.7 (observabilidade
gerenciada de produção: OTel, sondas, página de status). Este documento
é ADITIVO sobre os dois: não substitui `metrics-catalog.md` (P0.8) nem
os documentos da LC.7; fixa o contrato de exposição do go-live.

## 1. O que já existe na base (P0.8) — verificado, não refeito

| Item | Estado na base |
|---|---|
| Dependências | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` + `micrometer-tracing-bridge-brave` em `platform/ziipsan-app/pom.xml` — **nenhuma dependência nova é necessária**; este pacote não altera pom nenhum |
| Exposição web | `health,metrics,prometheus` (`application.yml`, `management.endpoints.web.exposure.include`) |
| Health | `show-details: never`; probes ligadas |
| Autenticação | `SecurityConfig`: `/actuator/health` público; `/actuator/metrics` e `/actuator/prometheus` autenticados |
| Logs | JSON estruturado (logstash; ECS em prod) com `trace_id` correlacionado via MDC (`requestId`, `tenant`) |
| Tracing | amostragem 1.0 fora de prod, 0.1 em prod (`application-prod.yml`) |

## 2. Contrato de exposição do go-live (o que este pacote fixa)

Endpoints expostos — **só estes três**:

| Endpoint | Acesso | Para quê |
|---|---|---|
| `GET /actuator/health` (+ `/liveness`, `/readiness` das probes) | público, sem detalhes | sonda de disponibilidade (LC.7: sondas sintéticas e página de status) |
| `GET /actuator/metrics` | autenticado | leitura pontual de medidores (RED/USE) |
| `GET /actuator/prometheus` | autenticado | scrape do registry Prometheus |

**NÃO expostos** (ausentes do `include`; o smoke da II-8 verifica a
ausência): `env`, `beans`, `configprops`, `heapdump`, `threaddump`,
`loggers`, `mappings`, `scheduledtasks`, `caches`, `sessions`,
`shutdown` (este último nem com exposição — nunca habilitar),
`info` (não incluído nesta fase; a versão pública viaja nos cabeçalhos
`Ziip-Version` da P0.39).

O perfil `application-golive.yml` entregue neste pacote congela esta
exposição para a prova de release: qualquer alteração futura da base
aparece como divergência contra o perfil.

## 3. Como se verifica

1. **Smoke isolado da II-8** (rota declarada no RELATORIO-II-8): app
   mínima com as mesmas versões do Boot da base + o perfil `golive`,
   assertando o índice do Actuator (só os 3 links) e 404 nos endpoints
   não expostos.
2. **No reactor completo** (quando a CI fechar — P-II8-CI): o IT da
   P0.8, `VersionAndObservabilityIntegrationTest`, já prova health
   pública com cabeçalhos de versão, Prometheus autenticado com
   `http_server_requests`/`hikaricp`/contador de negócio e metrics a
   exigir autenticação. Este pacote não duplica esse IT.
3. **Scrape real em ambiente de go-live**: pendente do ambiente (LC.7);
   é lá que o orçamento de séries deixa de ser estimativa (ver
   `metrics-budget.yml`).

## 4. Fronteiras

- Nenhum endpoint novo, nenhum dashboard (decisão 57), nenhum go-live
  real (acto externo da trilha LC).
- Segredos nunca em endpoint: `env`/`configprops` fora da exposição é
  também a garantia de que nenhum segredo vaza por esta via.
