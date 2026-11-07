# RB-07 — Ciclo de faturamento atrasado (Central, P2.11)

- **Regra:** `central.billing.cycle_overdue` (`billing.cycle.overdue_items > 0`, TENANT, WARNING, owner: platform)
- **Sinal:** item obrigatório do cronograma do ciclo (P2.5) com `planned_on` anterior a hoje e não realizado (PENDING/COMMANDED). O sinal carrega o `groupId` e o detalhe nomeia a atividade vencida mais antiga, o grupo dela e a data prevista (correção pós-revisão M-2).

## Procedimento

1. Ler no sinal/detalhe do alerta a atividade vencida mais antiga e o grupo (o sinal já os traz — `groupId` + detalhe); na aba **Faturamento** da Central, abrir o tenant do alerta e conferir o item nesse grupo.
2. Conferir no tenant se há causa operacional registrada (ex.: rota de leitura não transmitida — o faturamento é recusado nessa condição pela P2.5) ou se é atraso de comando.
3. Escalar ao responsável pelo tenant com a referência, a atividade e a data prevista; registrar o andamento no alerta (ACK na aba Alertas).
4. O alerta fecha quando o item é realizado ou cancelado no cronograma; não fechar manualmente sem a realização — a próxima coleta reabriria o sinal.

## Não fazer

- Não comandar atividade fora da precedência do catálogo do produto (a P2.5 recusa).
- Não alterar `planned_on` para "curar" o alerta: a data prevista é o fato do cronograma.
