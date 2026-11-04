# Catálogo de eventos de negócio — pacote sanitation (gerado, P1.38)

> Gerado por `scripts/generate-business-event-catalog.py` a partir de
> `packages/sanitation/vertical.json`. Não edite à mão: regenere.
> Regra: a carga de um evento carrega só identificadores e códigos,
> sem dado pessoal; mudança incompatível de carga exige nova versão
> do evento. Publicação fora do catálogo, em versão não declarada ou
> com carga fora do esquema é recusada no caminho de publicação
> (`BusinessEventPublisher` e a outbox validada da P1.9).

Pacote: **sanitation** — versão 0.1.0 (plataforma mínima 1.0.0-SNAPSHOT)

## Eventos

### `support.ticket.created` v1 <!-- event: support.ticket.created@v1 -->

- Descrição: Chamado de suporte aberto pelo tenant (P1.23). A carga leva só identificadores e códigos; o e-mail de aviso leva protocolo, estado e link, sem conteúdo nem anexo (ADR-0020 decisão 5).
- Permissão exigida de quem recebe: `support:queue`
- Consumidores declarados: P1.17
- Tarefa de origem: P1.23
- Carga (só identificadores e códigos):
  - `ticketId` (string, obrigatório)
  - `ticketNumber` (string, obrigatório)
  - `tenantId` (string, obrigatório)
  - `status` (string, obrigatório)
  - `category` (string, obrigatório)
  - `severity` (string, obrigatório)

### `support.ticket.message` v1 <!-- event: support.ticket.message@v1 -->

- Descrição: Mensagem nova num chamado de suporte (P1.23). Sem corpo na carga: o consumidor busca a conversa pela API, com a visibilidade devida (nota interna nunca vai ao tenant).
- Permissão exigida de quem recebe: `support:queue`
- Consumidores declarados: P1.17
- Tarefa de origem: P1.23
- Carga (só identificadores e códigos):
  - `ticketId` (string, obrigatório)
  - `ticketNumber` (string, obrigatório)
  - `tenantId` (string, obrigatório)
  - `messageId` (string, obrigatório)
  - `authorType` (string, obrigatório)
  - `visibility` (string, obrigatório)

### `support.ticket.status` v1 <!-- event: support.ticket.status@v1 -->

- Descrição: Estado do chamado alterado pelo agente ou pelo solicitante (P1.23). A carga leva só os estados anterior e novo, em código.
- Permissão exigida de quem recebe: `support:queue`
- Consumidores declarados: P1.17
- Tarefa de origem: P1.23
- Carga (só identificadores e códigos):
  - `ticketId` (string, obrigatório)
  - `ticketNumber` (string, obrigatório)
  - `tenantId` (string, obrigatório)
  - `fromStatus` (string, obrigatório)
  - `toStatus` (string, obrigatório)

## Consumidores declarados (só declarados, não construídos)

- Central Ziip — fila de chamados (`P1.17`) <!-- consumer: P1.17 -->
- Central de avisos (`P3.36`) <!-- consumer: P3.36 -->
- Notificações ao consumidor (`P3.5`) <!-- consumer: P3.5 -->
- Notificações ao consumidor (`ED-41`) <!-- consumer: ED-41 -->
- Webhooks de saída (`P2.26`) <!-- consumer: P2.26 -->
- Minhas tarefas (`P2.28`) <!-- consumer: P2.28 -->
- Alertas do tenant (`ED-07`) <!-- consumer: ED-07 -->

## Lacunas (tipos fora do catálogo)

- `billing.invoice.issued` <!-- gap: billing.invoice.issued --> — Aparece só nos testes da P1.9 (tipo de exemplo no contrato da fila e nos ITs da outbox), com cargas inconsistentes entre testes e sem nenhum publicador em código de produção. Não é um evento de negócio declarado: a tarefa que criar o publicador de faturamento deve declará-lo aqui primeiro, com esquema estável da carga, e só então publicar.
