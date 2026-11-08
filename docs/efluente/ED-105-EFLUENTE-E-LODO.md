# ED-105 — Efluente da ETE e lodo de ETA e ETE

Corretor: sessão ED-105-CORR-1 — agente de correção (distinto do produtor e do revisor)
(documento regularizado na correção D1, 08/11/2026, sobre o pacote real em disco)

Obrigação legal (classe `REGULATORY`, CONAMA 430/2011 e CONAMA
498/2020) de todo tenant com ETA ou ETE — JOR-16 e OPE-25.
Módulo `shared/ziipsan-core`, pacote `br.com.ziipsan.core.effluent`.
Núcleo puro Java: persistência, controllers, telas e fiação das
portas são da integração (pendência P-ED105-01).

## Desenho

| Peça | Papel |
|---|---|
| `RegulatoryObligation` | Portão JOR-16: a obrigação existe sse o tenant tem ETA ou ETE. ISO 14001 (ED-70), pacote de esgoto e pacote de água bruta são campos lidos e **ignorados** na decisão — a independência é testada (32 combinações na revisão), não só declarada. |
| `EffluentStandard` / `EffluentCatalog` | Catálogo de padrão de efluente versionado, federal + estadual, com vigência (inclusive futura). Sem limite de fábrica e **sem nenhuma linha semeada**: tudo é carga revisada pelo responsável técnico; o SQL nasce vazio. Vigências sobrepostas do mesmo parâmetro/esfera são recusadas na carga do núcleo **e no banco** (exclusion constraint `daterange` + `btree_gist` em `effluent_standards` — correção D3). **Norma não publicada não entra**: a guarda é `UNPUBLISHED` recusado na construção (Java) e CHECK só-`PUBLISHED` (SQL) — a revisão da CONAMA 430, aprovada em 02/09/2026 sem publicação, só entra após publicar, como nova versão com data de efeito. É o catálogo que a ED-83 consome. Nenhum artigo de norma nem URL consta do pacote. |
| `EffluentConformity` | Veredito contra a esfera declarada pelo chamador (federal/estadual). Sem padrão revisado vigente: `INDETERMINATE`, nunca presumido. A regra de prevalência entre esferas não tem fonte — pendência P-ED105-06. |
| `EffluentMonitoringPlan` / `EffluentMonitoring` | Automonitoramento da ETE: plano de coleta com quantidades **do tenant** (nenhuma frequência de fábrica), cobertura planejado × coletado, e registro de análise somente sobre amostra com recebimento provado pela cadeia de custódia da ED-50 (porta `EffluentLabPort`). |
| `BodEfficiency` | Eficiência de remoção de DBO por ETE/período: médias dos pares afluente/efluente do automonitoramento; eficiência negativa é reportada, não escondida; período sem par recusa. |
| `SludgeRecord` | Geração do resíduo do próprio tenant (lodo de ETA/ETE, gradeamento, desarenação): quantidade por pesagem ou estimativa de volume (método é parte do registro), armazenamento temporário, saída com transportador/destino/documento, fechamento. A unidade geradora é validada pelo `UnitKind` da ED-87 (ETA/ETE; gradeamento/desarenação só de ETE). |
| `SludgeService` / `MtrLink` | Destinação validada contra `waste.sludge_destination_catalog`; MTR do gerador: origem em `waste.mtr_exempt_origins` → isento; senão item **manual** na agenda da ED-98 via porta (RAS-17) e vínculo `PENDING_MANUAL` → `EMITTED_MANUALLY`. `MtrLink.linkAutomatic` é apenas o gancho futuro da ED-104: existe no núcleo, **nunca é invocado pelo fluxo desta tarefa** (o `SludgeService` não o chama; é exercitado só nos testes do próprio vínculo) e vínculo `EXEMPT` o recusa. |
| `SeptageReception` | Recepção de lodo de fossa **na ETE** com volume, unidade, origem (código + descrição) e transportador. Entrada ≠ geração própria. Desde a correção D4 o registro recebe o `receivingUnitKind` (`StationUnit.UnitKind` da ED-87) e **recusa unidade receptora que não seja WWTP**, como o `SludgeRecord` já fazia. |
| `DiaryEffluentFacts` | Leitura do diário da ED-87 sobre as classes reais (`ShiftLog`): "volumes e paradas lidos do diário" vale **com a ressalva declarada no próprio Javadoc** — as leituras `FLOW` são devolvidas **como lançadas, sem integrar vazão em volume** (o diário não fixa intervalo de integração; volume derivado seria número inventado) e **paradas = equipamentos `OUT_OF_SERVICE`** do diário (+ ocorrências contadas). |
| `SludgeAspectSummary` | Fronteira de leitura da ED-70: totais de geração por tipo/unidade de medida, saídas e pendentes de destinação. A ED-70 lê; não registra. |
| `EffluentParameters` | `waste.sludge_destination_catalog` (obrigatório, não vazio) e `waste.mtr_exempt_origins` (ausência = erro explícito; `[]` = nenhuma isenta), validados sobre `MissingParameterException` real do core. Ausência de parâmetro nunca vira fallback silencioso. |

## SQL

`db/pendente/ED-105__effluent_sludge.sql` — 8 tabelas
(`effluent_catalog_versions`, `effluent_standards`,
`effluent_monitoring_plans`, `effluent_monitoring_plan_lines`,
`effluent_bod_assessments`, `sludge_records`,
`septage_receptions`, `sludge_mtr_links`), sem número Flyway,
**zero seeds**, FKs compostas `(tenant_id, …)`, CHECKs espelhando
o núcleo e, desde a correção D3, exclusion constraint de vigência
em `effluent_standards`. Reaplicação idempotente (`IF NOT EXISTS`
+ `CREATE EXTENSION IF NOT EXISTS btree_gist`).

## Fronteiras duras respeitadas

- **ED-80** continua dona de Declaração de Carga Poluidora,
  outorga, licença, biossólido e reúso: nada disso é implementado
  nesta entrega (só ausência/negativa).
- **ED-104** é dona do MTR automático: aqui só o item manual da
  **ED-98** e a costura de ligação (`MtrLink.linkAutomatic`, nunca
  invocada neste fluxo).
- **ED-50** é dona do laboratório e da cadeia de custódia: a
  ED-105 consome a prova de recebimento por porta e compila as
  classes reais da ED-50 no harness; nenhuma amostra/custódia é
  duplicada.
- **ED-87** é dona do diário e do cadastro de unidades: a ED-105
  só os lê (classes reais `ShiftLog`, `DiaryReading`,
  `StationUnit`).
- **P3.9** intocada; item 32 (arquitetura tarifária) não
  executado — nenhum cálculo de cobrança é introduzido. Os limites
  novos da CONAMA em revisão **não entram**.

## Fiação (integração)

| Porta | Quem implementa |
|---|---|
| `EffluentPorts.EffluentLabPort` | Adaptador sobre a ED-50: prova de recebimento da amostra (`LabReceipt.receivedByLab`). |
| `EffluentPorts.ManualAgendaPort` | Agenda da ED-98: cria o item manual de emissão do MTR e devolve a referência. |

Pendências nomeadas: ver `RELATORIO-ED-105.md` (P-ED105-01 a 07).
