# Observabilidade mínima — P0.8

## O que está ligado

| Item | Como | Onde |
|---|---|---|
| Métricas HTTP | `http.server.requests` (timer, histograma de percentis) | `/actuator/metrics`, `/actuator/prometheus` (autenticados) |
| Métricas do Hikari | `hikaricp.*` (2 pools: app, migrador) | idem |
| Traces | Brave via Micrometer Tracing; amostragem 100% fora de prod, 10% em prod | `management.tracing.sampling.probability` |
| `tenant_id` no trace | `JwtTenantFilter` marca o span corrente com `tenant_id` | `platform/.../security/JwtTenantFilter.java` |
| Logs JSON | formato estruturado JSON (logstash no padrão, ECS em prod) | `application.yml` / `application-prod.yml` |
| `X-Request-ID` | aceito do cliente se bem formado, senão gerado; devolvido na resposta e no MDC (`requestId`) | `RequestIdFilter` (já existia) |
| `pg_stat_statements` | extensão criada pelo init + `shared_preload_libraries` no servidor | `security.sql`, `docker-compose.yml`, `ci-linux.sh` |

## A regra do rótulo de tenant (imposta por código, não por convenção)

- **Latência por rota NÃO leva rótulo de tenant.** `TenantTagGuard` (um `MeterFilter`)
  remove as tags `tenant`/`tenant_id` de qualquer medidor fora da allowlist abaixo, antes
  do registro. Um `.tag("tenant_id", ...)` esquecido num timer não cria série por tenant.
- **Rótulo de tenant só existe num contador agregado por grupo de rota**, e num conjunto
  curto e listado de métricas de negócio — que, nesta fase, tem exatamente UM item:

| Métrica | Tags | Quem alimenta |
|---|---|---|
| `ziipsan_business_requests_total` | `route_group` (1º segmento após `/api/v1`), `tenant` (UUID ou `anonymous`) | `BusinessMetricsInterceptor` (MVC, após o tenant estar resolvido) |

Nenhuma outra métrica pode carregar tenant. Grupo novo ou tenant além do teto do orçamento
exige atualizar `metrics-budget.yml` — o CI barra a soma acima do orçamento.

## Visão por tenant

- **Traces:** filtrar por `tenant_id` (tag do span) + `trace_id` correlacionado ao log
  via MDC (`tenant`, `requestId`).
- **Resumos `metric_point` da Central:** NÃO implementados aqui. A Central (P1.17) é quem
  vai coletar e resumir por tenant; até lá, a visão por tenant é pelos traces e pelo
  contador de negócio acima. Não há endpoint de métricas por tenant nesta fase.

## Telemetria de produto com privacidade (P4.8)

**Só declarada aqui, não implementada.** Nenhum evento de produto, nenhum identificador
de usuário e nenhum dado pessoal entra nas métricas desta fase. O contador de negócio
mede volume por grupo/tenant para operação, não comportamento de usuário. A P4.8 definirá
o que (e com que consentimento/anonymização) será telemetria de produto.

## Orçamento de séries

Ver `metrics-budget.yml` (serviço `ziipsan-app`, teto 10.000 séries estimadas) e
`scripts/check-metrics-budget.py` (job `observability-budget` do CI). As estimativas são
de planejamento até haver scrape real (ver o cabeçalho do arquivo de orçamento).
