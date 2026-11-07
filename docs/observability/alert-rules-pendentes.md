# Regras de alerta PENDENTES de modelagem — PROPOSTA (P3.11, pós-revisão)

> Este arquivo **não** é um catálogo executável: nenhuma regra abaixo é
> carregada por `AlertRuleCatalog`, inserida em `platform.alert_rule` ou
> prometida como alerta distinguível nos exercícios de falha. As regras
> foram **retiradas** de `docs/observability/alert-rules.json` na correção
> pós-revisão (P-1/P-2 da `REVISAO-P3.11.md`) porque as métricas que usavam
> não existem ou não distinguem o cenário. Os runbooks correspondentes
> (RB-01, RB-03..RB-06) são mantidos — runbook sem regra executável é
> preparação, não instrumentação.
>
> Catálogo executável vigente: apenas as regras cujas `metric_key`
> pertencem às 8 chaves fechadas de `platform.metric_definition` (V9_1):
> `platform.tenants.total`, `platform.tenants.active`,
> `platform.outbox.pending`, `platform.outbox.dead`,
> `platform.outbox.oldest_pending_age_s`, `platform.alerts.open`,
> `platform.onboarding.pending_steps`,
> `platform.collector.last_run_age_s`.

## 1. Burn rate da disponibilidade — `api.availability.burn_fast` / `api.availability.burn_slow` (RB-01)

- **Forma retirada:** `metric_key: "http.server.availability"`,
  thresholds 14,4 / 6 aplicados diretamente sobre a métrica.
- **Por que saiu:** `http.server.availability` e `http.server.errors`
  **não existem** — nem no catálogo/orçamento da P0.8, nem em
  `metric_definition` (V9_1). Inserir a regra no banco violaria a FK
  `alert_rule.metric_key → metric_definition(metric_key)`. Além disso, o
  threshold pressupõe uma série de burn já calculada, e ninguém está
  definido como calculista/publicador dessa série.
- **Base real que existe:** a métrica HTTP real da P0.8 é
  `http.server.requests` (timer com histograma, componente de 3.000
  séries no orçamento do serviço `ziipsan-app` em `metrics-budget.yml`,
  exposta em `/actuator/metrics` e `/actuator/prometheus`
  **autenticados**).
- **Derivação proposta (pendente de decisão de modelagem):** o SLI de
  disponibilidade seria **derivado** de `http.server.requests` pela tag
  de status — eventos totais e eventos ruins contados a partir dela e
  entregues ao `BurnRateCalculator` (que calcula burn/orçamento a partir
  de contagens, não de chave de métrica). A definição exata de evento
  ruim é a definição de SLI marcada como PROPOSTA no `slo-catalog.md`.
- **O que a decisão precisa definir (dono/integração, não esta tarefa):**
  (a) se nasce uma chave nova em `metric_definition` para a série
  derivada/de burn — isso exige migration Flyway, e **nenhuma versão
  está reservada para a P3.11** (ver RELATORIO-P3.11.md) — e a
  atualização do `metrics-budget.yml`, cuja regra (P0.8) exige
  atualização **antes** do merge de medidor novo; ou (b) se o burn é
  avaliado fora de `alert_rule`. Sem essa decisão, a regra não volta ao
  catálogo executável.

## 2. Perda do PSP — `psp.unavailable` (RB-03)

- **Forma retirada:** `metric_key: "platform.alerts.open" > 0` (genérica,
  auto-referente: conta alertas abertos de qualquer regra, inclusive
  desta — dispara junto com todos os outros cenários e não distingue
  nenhum).
- **Por que saiu:** não existe métrica real de saúde/disponibilidade do
  PSP nem na V9_1 nem na P0.8.
- **Pendente:** métrica própria e distinguível do PSP, a definir quando
  houver fonte real (integração do PSP / P3.15 / LC.7, conforme a
  revisão). Nome de chave **não** é proposto aqui para não inventar
  instrumentação.

## 3. Indisponibilidade da SEFAZ — `sefaz.unavailable` (RB-04)

- **Forma retirada:** `metric_key: "platform.alerts.open" > 0` (idem §2).
- **Por que saiu:** não existe métrica real de disponibilidade da SEFAZ
  nem na V9_1 nem na P0.8.
- **Pendente:** métrica própria e distinguível da SEFAZ, a definir quando
  houver fonte real (fiscal / P3.15 / LC.7). Sem nome de chave proposto,
  pelo mesmo motivo do §2.

## 4. Certificado vencido/a vencer — `certificate.expiring` (RB-05)

- **Forma retirada:** `metric_key: "platform.alerts.open" > 0` (idem §2).
- **Por que saiu:** não existe métrica real de validade de certificado
  (dias para vencer, por certificado) nem na V9_1 nem na P0.8.
- **Pendente:** métrica própria e distinguível de validade de
  certificado, a definir quando houver fonte real (cofre / P3.15 /
  LC.7). Sem nome de chave proposto.

## 5. Réplica do banco derrubada — `db.replica.down` (RB-06)

- **Forma retirada:** `metric_key: "platform.alerts.open" > 0` (idem §2).
- **Por que saiu:** não existe métrica real de estado/atraso de réplica
  nem na V9_1 nem na P0.8.
- **Pendente:** métrica própria e distinguível de réplica (estado ou
  atraso de reaplicação), a definir quando houver fonte real
  (infraestrutura / P3.15 / LC.7). Sem nome de chave proposto.

## Efeito nos exercícios de falha

Enquanto a modelagem não existir, os cenários de réplica, PSP, SEFAZ e
certificado do `programa-exercicios-de-falha.md` **não têm alerta
esperado distinguível** e o exercício deles não comprova disparo de
alerta cenário a cenário — apenas o runbook pode ser exercitado. O
cenário de fila parada segue comprovável pelas duas regras executáveis
(`central.collector.stale` e `platform.outbox.oldest_pending_age_s.high`).
