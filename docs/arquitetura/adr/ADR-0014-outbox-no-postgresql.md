# ADR-0014 — Trabalho assíncrono por outbox no PostgreSQL

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto
- **Afeta:** revisão 17 (achado X4); `docs/arquitetura/ARQUITETURA-ALVO.md` §2
- **Ajuste de 12/10/2026 (plano v2.2), antes de qualquer implementação:** nomes de coluna e de estado em inglês, por
  decisão do dono do produto (plano §8, Idioma); a decisão não muda.

## Contexto

Não há trabalho assíncrono nem infraestrutura de lote (X4). Faturamento, emissão fiscal, conciliação Pix, notificações
e provisionamento precisam de entrega garantida, retomada e isolamento entre tenants. A hospedagem está pendente
(ADR-0011) e Kafka e Redis estão congelados (ADR-0010).

## Decisão

1. Tabela `outbox` no mesmo banco do tenant (uma por shard), com `tenant_id`, `queue`, `type`, `dedup_key`,
   `payload` sem dado pessoal (só identificadores), `status`, `attempts`, `available_at`, `locked_until` e
   `last_error`. `UNIQUE (tenant_id, queue, dedup_key)`; partição diária.
2. A mensagem é gravada na mesma transação do registro de negócio, com RLS `WITH CHECK` no `tenant_id`. Só a role
   `sigsan_worker` lê linhas de todos os tenants, e só nesta tabela; o handler processa com o contexto do tenant da
   mensagem.
3. Workers do processo `worker` tomam mensagens com `FOR UPDATE SKIP LOCKED` e prazo de posse; posse vencida volta à
   fila. Entrega ao menos uma vez; todo handler é idempotente.
4. Fila justa: a seleção percorre os tenants com pendência e respeita o limite de concorrência de cada um (RNF-25).
5. Backoff exponencial com jitter; após o limite de tentativas, estado `DEAD` (mensagem morta), visível no console com
   reprocessamento.
6. O código usa as interfaces `MessagePublisher` e `MessageHandler`; o adaptador PostgreSQL é o único inicial. Uma
   fila gerenciada entra depois como transporte, com a outbox mantida como ponto de gravação transacional.

## Consequências

- Nenhum componente novo em produção; transação única entre negócio e mensagem.
- Carga de escrita e de vacuum no banco principal; a partição diária permite descartar por `DROP` em vez de `DELETE`.
- Gatilho para rever: mais de 500 mensagens por segundo sustentadas por shard ou RNF-09 violado por capacidade da
  fila.

## Alternativas consideradas

- **Kafka ou RabbitMQ.** Rejeitada: componente a operar sem volume que o justifique (ADR-0010).
- **SQS ou Service Bus.** Rejeitada agora: depende do ADR-0011 e não grava na transação do negócio sem outbox.
- **Só `LISTEN/NOTIFY`.** Rejeitada como fila, por não ser durável; pode servir para acordar workers.
- **Bibliotecas sobre PostgreSQL (db-scheduler, JobRunr).** Admitidas como implementação do adaptador se atenderem à
  RLS e à fila justa; avaliar na P1.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Tabela, interfaces, adaptador PostgreSQL e processo `worker` com pool e role próprios | P1 |
| Uso no provisionamento de tenant e nas notificações do console v0 | P1 |
| Fila justa, métricas de profundidade e idade, autoscaling por fila | P2 |
| Faturamento particionado por rota e emissão fiscal sobre a outbox | P2 |
| Caixa de entrada `pix_inbox` e conciliação | P3 |
