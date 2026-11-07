# ED-17 — Contrato de ingestão: telemetria e supervisório (REST e arquivo)

Contrato publicado da entrega ED-17. Implementação no módulo
`operations/ziipsan-metering`, pacotes `telemetry` e `supervisory`.
Nenhum broker em nenhum trecho: REST e arquivo, exceção controlada ao
ADR-0010 conforme o ADR-0046 (decisão PD-04).

> **Estado da PD-04/ADR-0046:** o texto do aceite da ED-17 (redação do
> plano) diz "mantido como pendência no §2.4 enquanto não aceito". A tabela
> de decisões do próprio plano v3.0 registra a PD-04 como **aceita em
> 29/10/2026** (ADR-0046 aceito). A implementação não muda nos dois casos —
> é REST e arquivo, sem broker — mas a divergência entre a redação do
> aceite e a tabela de decisões fica registrada aqui e no relatório da
> entrega, para confirmação do dono.

## 1. Telemetria (AMR e curva horária)

Camada de API pública da P2.26: credencial de integração **por tenant**
(cliente OAuth 2.0 da `V7_1`), erros em `application/problem+json`
(RFC 9457), rotas sob `/api/v1` (P0.10).

| Operação | Rota | Escopo (P2.26/ADR-0040) | Núcleo |
|---|---|---|---|
| Enviar leituras AMR | `POST /api/v1/integrations/telemetry/readings` | `telemetry:write` | `TelemetryIngestionService.ingestReadings` |
| Enviar amostras horárias | `POST /api/v1/integrations/telemetry/hourly` | `telemetry:write` | `TelemetryIngestionService.ingestHourly` |
| Importar arquivo AMR | `POST /api/v1/integrations/telemetry/imports` (multipart) | `telemetry:write` | `TelemetryIngestionService.importAmrFile`, leiaute `AMR_CSV_V1` |
| Ler curva horária | `GET /api/v1/integrations/telemetry/hourly-profile` | `telemetry:read` | repositório da curva; exibição ao usuário só com `meter.hourly_profile_visible_to_customer` |

Regras do contrato:

- **Idempotência:** a leitura AMR traz `externalRef` (chave única por
  tenant e mês); a amostra horária é única por ponto, canal e hora.
  Reenvio devolve os contadores de duplicadas, sem criar linha.
- **VEE:** leitura AMR entra em `telemetry_readings` e passa por
  validação/estimativa na entrada; só promovida vira `meter_readings`
  com origem `TELEMETRY`.
- **Dado pessoal:** a curva horária só é aceita de ponto com base legal
  registrada (`measuring_points.legal_basis`); a amostra não carrega dado
  do titular (minimização).

## 2. Supervisório (somente leitura)

Modelo de zonas e conduítes da IEC 62443: o **agente** roda na zona do
cliente, lê do historiador (OPC UA, MQTT, SQL somente leitura ou CSV
agendado), guarda localmente o que não conseguiu enviar e reenvia quando o
enlace volta, por **HTTPS de saída, sem nenhuma porta de entrada** na rede
do cliente. O Ziip SAN **nunca** escreve em CLP, RTU ou IHM.

| Operação | Rota | Escopo | Núcleo |
|---|---|---|---|
| Enviar envelope de amostras | `POST /api/v1/integrations/supervisory/samples` | `supervisory:write` | `SupervisoryIngestionService.ingest` |

Envelope (esquema publicado, versão no campo `schemaVersion`):

```json
{
  "schemaVersion": "1",
  "agentId": "agente-eta-central",
  "stationCode": "ETA-CENTRAL",
  "accessMode": "READ_ONLY",
  "envelopeId": "env-2026-11-07-0001",
  "sentAt": "2026-11-07T10:15:00-03:00",
  "samples": [
    {
      "sampleKey": "ETA-CENTRAL:NIVEL-01:2026-11-07T10:00:00-03:00",
      "tagCode": "NIVEL-01",
      "sampledAt": "2026-11-07T10:00:00-03:00",
      "numericValue": 3.42,
      "textValue": null,
      "unitOfMeasure": "m",
      "quality": "GOOD",
      "operation": "SAMPLE"
    }
  ],
  "signature": "<HMAC-SHA-256 hex da forma canônica>"
}
```

Regras do contrato:

- `accessMode` diferente de `READ_ONLY`, ou qualquer amostra com
  `operation` diferente de `SAMPLE`, derruba o envelope inteiro sem gravar
  nada (`supervisory-write-attempt`).
- **Assinatura:** HMAC-SHA-256 sobre a forma canônica de
  `SupervisorySignature` (cabeçalho e amostras, na ordem), com o segredo
  do agente por tenant, guardado no cofre.
- **Idempotência por amostra:** `sampleKey` estável no agente; o reenvio
  (store-and-forward) devolve as amostras como duplicadas, sem segunda
  linha — coberto por teste de reenvio.
- **MQTT aparece só dentro da rede do cliente**, como uma das leituras que
  o agente faz do historiador; nenhum broker é exposto ao Ziip SAN nem
  atravessa o conduíte — o conduíte é só esta chamada HTTPS.

## 3. Retenção e agregados

Calculados por `RetentionPolicy` dos parâmetros do tenant e executados
por `TelemetryRollupService`: `tele.retention_raw_days` (série bruta),
`tele.retention_hourly_months` (série horária, por mês de partição),
`ami.retention_days` (curva horária como dado pessoal — vale o corte mais
restritivo dos dois para a curva) e `tele.rollup_policy` (agregados na
tabela de resumo `telemetry_hourly_daily_summary`, por dia civil no fuso
do ponto). O descarte físico da partição vencida (DROP PARTITION) é DDL
do migrador, como a criação das partições. Quem agenda a execução é o
worker da P1.9 com o tenant explícito (fiação da aplicação).

Alertas ao consumidor (contínuo 24h e pico): emitidos uma vez por
avaliação sobre dados novos; a contenção de repetição ao mesmo
consumidor é do canal da P3.5 (supressão e preferências do tenant). Os
alarmes do medidor, persistidos, são deduplicados pelo alarme aberto do
mesmo tipo no ponto.

## 4. Fora deste contrato

Diário de operação de estações (ED-87), gestão de pressão (ED-86),
contratos de grandes usuários (ED-22) e analítica de perdas aparentes
(ED-95) consomem estes dados, mas não fazem parte desta entrega.
