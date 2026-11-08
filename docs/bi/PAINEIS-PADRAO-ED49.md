# ED-49 — Sete painéis padrão: integração com o Superset

Os sete painéis (arrecadação POC-77, atendimento POC-78, cobrança
POC-79, faturamento POC-80, medição, operacional, indicadores
estratégicos POC-81) são dashboards do Apache Superset da ED-47
(ADR-0027), montados sobre a réplica, com a role de banco do tenant.

## Regra central (JOR-19)

Indicador, meta e semáforo exibidos são LIDOS do registro único da
P3.41 (`IndicatorRegistryService`). Nenhum painel guarda fórmula:
`StandardPanelService` não conhece expressão nenhuma; a apuração —
inclusive o valor congelado de período fechado e o semáforo
`NO_TARGET` sem meta — é a do registro. O teste central da entrega
compara, painel a painel, o valor exibido com o valor apurado
diretamente no registro.

## Ligações (nada presumido)

Nenhuma fonte lista quais indicadores compõem cada painel. A ligação
painel → indicador é DECLARADA por tenant na tabela
`panel_indicator_bindings` (SQL pendente `ED-49__standard_panels.sql`,
sem número Flyway — a faixa `V15_x` aguarda atribuição) e validada
contra o registro na montagem: código inexistente falha nomeado
(`INDICATOR_NOT_FOUND` do registro). `StandardPanelCatalog.unboundPanels`
nomeia os painéis ainda sem ligação.

## Painel estratégico (GES-29 + ED-62)

- Benchmark: `BenchmarkBase.compare` da ED-48 sobre a base pública
  versionada importada na integração. O recorte do tenant (porte,
  região, natureza jurídica) é declarado em `panel_benchmark_slices`
  `[A DEFINIR]` por tenant; sem base ou sem recorte, a seção é a
  pendência nomeada `benchmark_sinisa` — nunca a mediana de outro
  recorte.
- Compras/RH/orçamento: leitura do ERP da ED-62 adaptada para
  `ErpIndicatorsPort` (o reporting não depende do integration). Sem
  adaptador ou sem leitura na competência, a seção é a pendência
  nomeada `indicadores_erp_ed62` — nunca zero.

## Fronteira OPE-22

O console operacional em tempo quase real NÃO é um painel desta
entrega: fica fora do BI, na ED-93. O painel operacional da ED-49 é BI
periódico sobre a réplica. `RealtimeConsoleBoundary.requirePeriodicOnly`
recusa nomeada (`OPE22_REALTIME_CONSOLE_OUT_OF_BI`) qualquer pedido de
tempo quase real dirigido a um painel padrão, e `PanelDefinition`
recusa nascer como console em tempo real.

## Fiação pendente (integração)

1. Criar os 7 dashboards no Superset a partir de
   `PanelSupersetManifest.manifestJson()` (slugs são PROPOSTOS) e
   conferir todas as fontes com `ReplicaOnlyGuard` da ED-47 antes de
   habilitar (`PanelSupersetManifest.requireReplicaOnly`).
2. Adaptador JDBC das tabelas do SQL pendente + número Flyway da faixa
   `V15_x`; adaptador de `ErpIndicatorsPort` sobre a ED-62 integrada;
   importação da base pública de benchmark do ciclo (ED-48).
3. Ensaio POC-77 a POC-81 no ambiente do piloto (VISAN/SAMAE): esta
   entrega prova a regra e o transporte; o ensaio com o roteiro real é
   externo, como nas frentes anteriores.
