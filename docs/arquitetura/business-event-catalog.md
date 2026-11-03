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

Nenhum evento de negócio registrado até esta onda.

Nenhum evento de negócio publicado em produção até esta onda (P1.38): a outbox da P1.9 não tem publicador de produção — o único tipo que aparece no código é billing.invoice.issued, só nos testes da P1.9, sem carga estável e sem publicador, e por isso NÃO está registrado como evento (ver a seção de lacunas do catálogo gerado). Toda tarefa que passar a publicar eventos declara o evento aqui antes de publicar: nome, versão, esquema da carga (só identificadores e códigos, sem dado pessoal), permissão exigida de quem recebe e consumidores declarados. Mudança incompatível de carga exige nova versão do evento; a versão antiga continua declarada.

## Consumidores declarados (só declarados, não construídos)

- Central de avisos (`P3.36`) <!-- consumer: P3.36 -->
- Notificações ao consumidor (`P3.5`) <!-- consumer: P3.5 -->
- Notificações ao consumidor (`ED-41`) <!-- consumer: ED-41 -->
- Webhooks de saída (`P2.26`) <!-- consumer: P2.26 -->
- Minhas tarefas (`P2.28`) <!-- consumer: P2.28 -->
- Alertas do tenant (`ED-07`) <!-- consumer: ED-07 -->

## Lacunas (tipos fora do catálogo)

- `billing.invoice.issued` <!-- gap: billing.invoice.issued --> — Aparece só nos testes da P1.9 (tipo de exemplo no contrato da fila e nos ITs da outbox), com cargas inconsistentes entre testes e sem nenhum publicador em código de produção. Não é um evento de negócio declarado: a tarefa que criar o publicador de faturamento deve declará-lo aqui primeiro, com esquema estável da carga, e só então publicar.
