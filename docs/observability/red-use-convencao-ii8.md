# Convenção RED/USE + regra de cardinalidade (II-8)

**Pacote:** II-8 da Onda II do PDCA-01 (decisão 64). **Base:** P0.8
(`metrics-catalog.md`, `TenantTagGuard`, orçamento de séries) e P3.11
(SLOs/burn). Este documento estende os dois para os processos de
negócio do go-live; a imposição por código vive em
`br.com.ziipsan.platform.observability` (`MetricLabelPolicy`,
`CardinalityGuard`, `RedUseMetrics`).

## 1. RED — por rota/serviço (tráfego)

| Sinal | Métrica | Origem |
|---|---|---|
| **R**ate | `http.server.requests` (contador por `method`/`uri`/`status`) + `ziipsan_business_requests_total{route_group, tenant}` | P0.8 |
| **E**rrors | derivável de `http.server.requests` por classe de `status`/`outcome` (5xx = erro; definição do SLI em `slo-go-live-ii8.md`) | P0.8/P3.11 |
| **D**uration | `http.server.requests` como timer com histograma de percentis (`percentiles-histogram` ligado na base) | P0.8 |

RED de processo de negócio (novo neste pacote, via `RedUseMetrics`):

| Processo | Rate | Errors | Duration |
|---|---|---|---|
| Lote de faturação | `ziipsan_billing_batch_items_total{outcome}` | outcome=`FAILURE` do mesmo contador | `ziipsan_billing_batch_duration_seconds` (timer, sem rótulos) |
| Sync de campo | `ziipsan_field_sync_items_total{outcome}` | outcome=`FAILURE` do mesmo contador | [A DEFINIR] — nasce quando o sync tiver duração medida por execução; não se inventa timer sem consumidor |

## 2. USE — por recurso

| Sinal | Métricas (já expostas pelo Actuator na base) |
|---|---|
| **U**tilization | `system.cpu.usage`, `process.cpu.usage`, `jvm.memory.used`/`jvm.memory.max`, `hikaricp_connections_active`/`hikaricp_connections` |
| **S**aturation | `hikaricp_connections_pending`, `tomcat_threads_busy`/`tomcat_threads_config_max`, filas do lote (contagem de itens pendentes é métrica de processo, não USE de runtime — entra quando houver fonte; [A DEFINIR]) |
| **E**rrors | `hikaricp_connections_timeout_total`, erros 5xx do RED, `logback_events_total{level="error"}` |

## 3. Regra de cardinalidade (verificável — `MetricLabelPolicy`)

1. **PROIBIDOS SEMPRE** (recusa na registação, `CardinalityGuard`):
   `uc_id`, `consumer_unit_id`, `consumer_unit`, `unit_id`,
   `customer_id`, `cliente_id`, `user_id`, `usuario_id`, `bill_id`,
   `conta_id`, `reading_id`, `leitura_id`, `meter_id`,
   `hidrometro_id`, `service_order_id`, `os_id`, `route_id`,
   `rota_id`, `address_id`, `endereco_id`, `cpf`, `cnpj`, `email`,
   `telefone`, `phone`, `name`, `nome`, `trace_id`, `request_id`,
   `span_id`, `ip`, `url`, `path`.
   **Regra-mãe do PDCA-01: `uc_id` NUNCA é rótulo.** Detalhe por UC é
   por log/trace correlacionado (`trace_id` no MDC), nunca por série.
2. **RESTRITOS**: `tenant`, `tenant_id` — só em
   `ziipsan_business_requests_total` (allowlist herdada da P0.8;
   imposição: `TenantTagGuard`).
3. **PERMITIDOS** (vocabulário fechado): `method`, `uri` (sempre o
   template da rota, nunca o caminho concreto), `status`, `outcome`,
   `exception`, `route_group`, `error`, `pool`, `resource`, `state`,
   `probe_key`, `service`, `le`, `quantile`.
4. Rótulo fora das três listas = violação. Vocabulário novo exige
   revisão desta política **e** do orçamento de séries no mesmo pacote.

## 4. Orçamento de séries — impacto deste pacote

Séries novas estimadas (planeamento, como todo o `metrics-budget.yml`
até haver scrape real): itens do lote 2 (`outcome` × 2) + timer de
duração ≈ 8 (count/sum/max + buckets sem rótulos) + itens de sync 2 =
**≈ 12 séries**, registadas como componente `ii8-golive` na alteração
aditiva ao `metrics-budget.yml` deste pacote. Total estimado do
serviço passa de 7.540 para ≈ 7.552 — longe do teto de 10.000. O
guarda-chuva continua o mesmo: série por UC/tenant fora da allowlist
estouraria o orçamento em silêncio; é exactamente o que a guarda
recusa.
