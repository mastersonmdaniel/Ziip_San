# Integração — ED-101 Metas de consumo, comparação com pares e gamificação

Backend entregue em `platform/ziipsan-platform`, pacote
`br.com.ziipsan.platform.engagement`. Este documento registra as fiações —
nenhuma tela, endpoint ou agendamento é fingido como entregue.

## O que consome de quem

| Origem | Consumo | Como |
|---|---|---|
| ED-38 (CAD-102) | Comparação com pares | `PeerComparison` + porta `SelfCareParameters` da própria ED-38, compilados juntos nesta entrega. Chaves `bill.peer_comparison`/`bill.peer_min_units` e o piso 5 (classe c, O9-3) são REUTILIZADOS — a ED-101 não cria segundo piso; o mínimo efetivo é o maior entre `eng.peer_group_min_size` e o mínimo da ED-38 |
| ED-41 | Lembretes da meta | Porta `ReminderPorts.ReminderPort`; o adaptador chama o `MultichannelDispatchService` da ED-41 (opt-in por canal/finalidade, teto e modelos da P2.30 valem os da ED-41) |
| ED-17 | Telemetria | Porta `ConsumptionPorts.TelemetryConsumptionPort`: o adaptador agrega a série horária da ED-17 por unidade/período. A curva horária em si segue a regra de visibilidade da ED-17 (`meter.hourly_profile_visible_to_customer`); aqui só viaja o total mensal da própria unidade |
| ED-39 / P3.3 | Telas | A definição da meta e a leitura do relatório acontecem no app (ED-39) e no portal (P3.3): esta entrega é a exposição de backend (serviços + regras). Endpoints REST e telas = pendência P-ED101-01 |
| Billing | Consumo faturado e modelo da conta | Porta `BilledConsumptionPort`; mensagem impressa só do texto da versão vigente de `bill.invoice_template` (catálogo core V3, tipo VERSION, modelo aprovado pela agência/ERI; aprovação por conteúdo e conteúdo mínimo na P2.32), com as variáveis declaradas em `InvoiceMessageService` |

## Pendências de fiação (nomeadas no relatório)

1. **P-ED101-01 — Endpoints e telas** no app (ED-39) e no portal (P3.3):
   adesão/revogação, definição da meta, progresso, relatório de pares,
   marcos. Backend entregue; nenhuma tela foi desenhada nesta tarefa.
2. **P-ED101-02 — Persistência JDBC/JPA** das tabelas do SQL pendente
   (`db/pendente/ED-101__engagement.sql`) e numeração Flyway pela
   sequência global na camada de integração.
3. **P-ED101-03 — Critério de semelhança do grupo de pares [A DEFINIR]:**
   nenhuma fonte enumera os atributos do perfil semelhante. A integração
   define o critério com o cadastro (P1.3) e o aceite do tenant e monta a
   `PeerCohort`; o serviço só consome a coorte pronta e impõe o mínimo.
4. **P-ED101-04 — Periodicidade/agendamento [A DEFINIR]:** o aceite diz
   "relatório periódico" sem fixar frequência nem quem agenda. O serviço
   gera o relatório de um período sob demanda; o agendamento (job por
   ciclo de faturamento) é da integração.
5. **P-ED101-05 — Evento do catálogo para lembretes de meta:** o disparo
   da ED-41 exige evento do catálogo da P1.38; registrar o(s) evento(s)
   de meta/relatório no catálogo e ligar o adaptador da
   `ReminderPort` a ele. Nenhum nome de evento foi inventado aqui.
6. **P-ED101-06 — Registro dos parâmetros** `eng.peer_report_enabled`,
   `eng.peer_group_min_size` e `eng.gamification_milestones` (proposto)
   no catálogo de parâmetros do tenant (P0.19). (`bill.invoice_template`
   já está registrada no catálogo core V3 — não duplicar; a fiação aqui é
   só resolver a versão vigente do modelo no billing/P2.5.)
7. **P-ED101-07 — Marcos do tenant:** os códigos/limiares de
   `eng.gamification_milestones` são definição do tenant na ativação;
   sem eles, a gamificação responde `MILESTONES_NOT_CONFIGURED`.

## Privacidade (regras, não detalhes)

- Comparação nunca expõe dado individual: a coorte entra só como valores
  para o cálculo da ED-38, o relatório guarda/publica só média, mediana,
  posição e o consumo do próprio usuário; o schema nem tem coluna para
  consumo de terceiro.
- Grupo abaixo do mínimo efetivo não gera comparação (teste do aceite).
- Gamificação não tem pontos, ranking nem qualquer dado de outro usuário.
- Lembretes e mensagem impressa carregam só dados do próprio usuário e
  agregados já publicáveis do relatório.
