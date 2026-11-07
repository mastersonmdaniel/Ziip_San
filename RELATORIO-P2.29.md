# Relatório — P2.29 Gestão tarifária: reajuste, revisão, cópia de versão e simulação de impacto

**Módulo:** `commercial/ziipsan-billing` · **Tamanho:** G · **Deps:** P2.5 e P1.6 (entregues; P2.5 na main, HEAD conferido `0fbcd22`)
**Estado:** implementação completa local, sem push (por instrução da tarefa). Nada compila nesta VM (sem JDK/Maven/PostgreSQL); a prova é a CI total no PC do dono.

---

## 1. Migration e reserva no §3

- **Reserva no §3 do plano v3.0:** `V4_9` | billing | "versões da estrutura tarifária (reajuste por índice, revisão por ato da agência, cópia de versão) e simulações de impacto, que não alteram a base" | P2.29 | reservada, P2. A linha da tarefa no §4 do plano confirma: "Migration: `V4_9` (mapa do §3)".
- **Conferência no repo real (MCP, HEAD `0fbcd22`):** `commercial/ziipsan-billing/src/main/resources/db/migration/tenant/` contém exatamente `V4_1, V4_2, V4_3, V4_4, V4_6, V4_13, V4_14, V4_15`. **O slot `V4_9` está livre** — sem divergência entre a reserva e o repo; nenhum número foi inventado nem renumerado.
- **Entregue:** `V4_9__tariff_readjust_review_and_simulation.sql`:
  1. `ALTER TABLE tariff_versions` com 11 colunas novas, todas anuláveis (versões pré-P2.29 não são reclassificadas): `change_type` (`INITIAL/READJUSTMENT/REVIEW/COPY`), `source_version_id` (FK composta para a própria tabela), `base_date`, `index_code`, `index_pct`, `factor_x_pct`, `applied_pct` (NUMERIC(9,4)), `readjust_formula` (`INDEX_ONLY/INDEX_MINUS_FACTOR_X`), `approval_mode` (`EXPRESS/TACIT`), `approved_on`, `calculation_method` (TEXT).
  2. Trava de publicação também no banco, escopada às linhas novas de reajuste/revisão: `chk_tariff_versions_publication_30d` (`valid_from >= published_on + 30`). Linhas legadas (`change_type NULL`) e cópias em elaboração ficam fora — endurecer o passado quebraria dado admitido pela V4_1.
  3. `tariff_impact_simulations`: registro **explícito** de simulação já calculada (snapshots JSON da massa e do relatório), tabela própria, FK só para `tariff_versions`, registrada com `register_tenant_table` (RLS). Não é base de faturamento e o simulador nunca escreve nela.
  4. Catálogo de parâmetros (`public.parameter_definitions` + `public.parameter_legal_limits`, precedente da seção 7 da V4_4): ver item 10 abaixo.
  5. Nenhum dado de negócio semeado (guarda em teste).
- **Decisão de desenho central:** reajuste/revisão/cópia **são novas linhas de `tariff_versions`** — não há tabela nova de versão. Assim a vigência sem sobreposição continua imposta pela **mesma** `EXCLUDE` da V4_1 (`excl_tariff_versions_no_overlap`), que é o que o aceite pede; uma estrutura paralela criaria um segundo caminho de vigência que o `TariffEngine`/`TariffSchedule` não lê.

## 2. Arquivos (23 arquivos de código/migration + este relatório, pelos caminhos do repo)

**Migration (1)**
- `commercial/ziipsan-billing/src/main/resources/db/migration/tenant/V4_9__tariff_readjust_review_and_simulation.sql`

**Domínio tarifário — `billing/tariff/` (11 novos)**
- `ReadjustFormula.java` — enum `INDEX_ONLY` / `INDEX_MINUS_FACTOR_X` com `appliedPct(índice, fator X)` validado (fator X com fórmula por índice = incoerência recusada; paramétrica sem fator X = recusada; índice ≤ −100% recusado).
- `ReadjustmentCalculator.java` — aplica o percentual a todos os preços (parcela fixa e faixas), HALF_UP a 4 casas; percentuais sociais, consumo mínimo e fator de economias **não** são reajustados (não são preços).
- `TariffVersionCopier.java` — cópia profunda da estrutura com id/vigência novos.
- `TariffComparison.java` — vigente × proposta regra a regra e faixa a faixa (absoluto e %); regra só de um lado aparece como acrescentada/removida.
- `TariffPublicationRule.java` — trava dos 30 dias: `effectiveMinDays = max(parâmetro, 30)`; o parâmetro nunca reduz o piso.
- `TariffApprovalRule.java` — homologação expressa (data do ato) e tácita (só com previsão na norma + prazo; data = publicação + prazo).
- `VersionChangeMetadata.java` — record dos fatos por versão com invariantes por tipo (reajuste exige data-base/índice/fórmula e confere `appliedPct` contra a fórmula; revisão exige ato; cópia/reajuste/revisão exigem origem; tácita exige data de consumação).
- `TariffManagementService.java` — propostas de reajuste, revisão e cópia com todas as travas (publicação, sobreposição espelho da EXCLUDE, metadado) e o método de cálculo determinístico do reajuste.
- `ImpactSimulator.java` — simulação pura sobre o mesmo `TariffEngine` da P2.5 (ver item 8).
- `TariffHistory.java` — janela pública do histórico com piso travado de 5 anos e método obrigatório por versão.
- `TariffVersionChangeType.java` — enum da origem da versão.

**Parâmetros — `billing/parameters/` (1 novo)**
- `TariffReadjustParameters.java` — leitura tipada via `Json` do core, no padrão de `BillingParameters` (P2.5): objeto `bill.tariff_readjust` ampliado + `tariff.readjust_formula`, `tariff.tacit_approval_days` (ausente = não configurado), `tariff.history_years_public` (piso 5 na leitura).

**Persistência (3)**
- `domain/TariffVersionEntity.java` — **reescreve o arquivo existente** acrescentando os 11 campos da V4_9 (arquivo-base conferido idêntico entre espelho P2.5 e main via MCP).
- `domain/TariffImpactSimulationEntity.java` (novo) e `repository/TariffImpactSimulationRepository.java` (novo).

**Testes (7 novos, padrão do módulo: JUnit 5 + AssertJ, casos calculados à mão)**
- `tariff/ReadjustmentCalculatorTest.java` (7 testes), `tariff/TariffPublicationAndApprovalTest.java` (9), `tariff/TariffComparisonAndCopyTest.java` (6), `tariff/TariffManagementServiceTest.java` (13), `tariff/ImpactSimulatorTest.java` (8), `parameters/TariffReadjustParametersTest.java` (6), `persistence/TariffManagementMigrationConsistencyTest.java` (6). Total: **55 testes** (49 da entrega original + 6 acrescentados nas correções pós-revisão, ver §6; nenhuma asserção dos 49 foi alterada ou afrouxada — só as chamadas de `proposeReview`/`ReadjustmentRequest` ganharam os fatos da homologação tácita que o serviço passou a exigir).

**Espelho:** `~/workspace/ziipsan-producao/p2.29/espelho/` (cópia do espelho do billing da P2.5 + `_main-check/` com os 3 arquivos relidos da main via MCP — `TariffVersionEntity`, `TariffVersion`, `BillingParameters` — todos **idênticos** ao espelho/entrega da P2.5, sem drift — e o `PARAMETROS-DO-TENANT.md` da main).

## 3. Cobertura do acceptance, item a item

| # | Item do aceite | Como foi atendido | Prova (teste) |
|---|---|---|---|
| 1 | Migration própria do billing na faixa `V4_x`, reserva no §3 | `V4_9`, reserva e slot livre conferidos (§1) | `TariffManagementMigrationConsistencyTest` |
| 2 | Reajuste por índice (IPCA ou fórmula paramétrica com fator X, NR 10 e NR 6 da ANA) a partir da data-base, gerando nova versão | `ReadjustFormula` + `ReadjustmentCalculator` + `TariffManagementService.proposeReadjustment` (data-base no metadado e no método) | `ReadjustmentCalculatorTest` (índice 5%; paramétrica 6,25−1,25=5%; deflação −2%; arredondamento 4,4550×1,05=4,6778) |
| 3 | Revisão por ato da agência, gerando nova versão | `proposeReview` com os preços do ato; sem ato, recusada (`VersionChangeMetadata`) | `TariffManagementServiceTest.reviewByAgencyAct` |
| 4 | Vigência sem sobreposição (`EXCLUDE` da `V4_1`) | Novas versões são linhas de `tariff_versions` (a EXCLUDE original impõe); espelho em domínio `validateNoOverlap` | `TariffManagementServiceTest.overlappingValidityIsRefused` + guarda de migration |
| 5 | Ato, índice e data de aplicação guardados por tenant (ARESC e AGIR por prestador) | Colunas da V4_9 em `tariff_versions` (tabela de tenant, RLS): `regulatory_act` (já existia), `index_code/index_pct/factor_x_pct/applied_pct/base_date/published_on/valid_from`; entidade estendida | Guarda entidade×SQL na consistência de migration |
| 6 | Publicação ≥ 30 dias, travada; reajuste com publicação em menos de 30 dias é recusado (teste) | `TariffPublicationRule` (piso travado `max(parâmetro,30)`) + CHECK no banco + recusa na leitura do parâmetro | `TariffPublicationAndApprovalTest` (29 dias recusado; 30 aceito; parâmetro 10 não afrouxa; 45 vale) e `TariffManagementServiceTest.readjustmentPublishedTooLateIsRefused` (12 dias) |
| 7 | Homologação tácita só quando a norma da agência a prevê | `TariffApprovalRule.tacitApprovalDate`: exige `tacitApprovalAllowed` (fato da norma, no parâmetro) + `tariff.tacit_approval_days` ≥ 1 + prazo decorrido — **imposto dentro de `TariffManagementService.propose*`** (correção pós-revisão, §6.1): o serviço recebe os dois fatos e recusa `TACIT` sem eles ou com data divergente da consumação | `TariffPublicationAndApprovalTest` (sem previsão → recusado; antes do prazo → recusado; no dia → data exata) + `TariffManagementServiceTest` (recusa no nível do serviço: sem previsão, sem prazo, data fabricada antes/depois da consumação, e na revisão) |
| 8 | Cópia de estrutura existente como ponto de partida | `TariffVersionCopier` + `proposeCopy` (tipo `COPY`, origem registrada) | `TariffComparisonAndCopyTest.copyReproducesTheStructure` |
| 9 | Comparação vigente × proposta | `TariffComparison` (parcela fixa e faixas, absoluto/%, regras só de um lado) | `TariffComparisonAndCopyTest` (+10,00% em fixa e faixas) |
| 10 | Simulação antes da vigência sobre a base real, em leitura que não grava: receita por categoria e faixa, conta-tipo por categoria/perfil, efeito na tarifa social e no subsídio | `ImpactSimulator` puro (sem repositório, sem entidade): `revenueByCategory`, `revenueByBlock`, `typicalBills`, `socialImpact` (desconto = subsídio implícito), `units`, totais | `ImpactSimulatorTest` com massa à mão (totais 580,60 → 638,66; RES/faixas/social conferidos linha a linha) |
| 11 | Teste de que a simulação não altera a base | Massa e estruturas fotografadas antes/depois; repetição produz relatório idêntico; preços da vigente intactos | `ImpactSimulatorTest.simulationDoesNotMutateAnything` |
| 12 | Teste de que a simulação reproduz a conta da P2.5 para a mesma massa | Proposta = vigente ⇒ resultado por unidade igual ao `BillCalculator.calculate` (P2.5), com água (112,00) e com esgoto (201,60) | `ImpactSimulatorTest.simulationReproducesTheP25BillForTheSameMass` |
| 13 | Histórico de 5 anos com o método de cálculo mantido (NR 13, arts. 61 e 62) | `calculation_method` por versão (método determinístico gerado no reajuste) + `TariffHistory.publicHistory` (janela ≥ 5, piso travado; versão da janela sem método é recusada) | `TariffManagementServiceTest` (janela exclui 2019; 3 anos recusado; sem método recusado) |
| 14 | Publicação do histórico no portal pela ED-38 | **Não feito (fronteira expressa):** esta tarefa só monta o histórico (`TariffHistory.HistoryEntry` é a entrada que a ED-38 consome) | — |
| 15 | `bill.tariff_readjust` ampliado com fórmula, índice, fator X, regra de homologação tácita e prazo de publicação (classe d, aprovação REG; mínimo de 30 dias travado), chaves novas `tariff.readjust_formula`, `tariff.tacit_approval_days`, `tariff.history_years_public` | Registrado na V4_9: `bill.tariff_readjust` OBJECT classe **d** REG com os 9 campos (o objeto **não existia** no catálogo da core V3 — só no doc `PARAMETROS-DO-TENANT` G1; registrado já ampliado, declarado no cabeçalho da migration); 3 chaves novas idem; limites `FLOOR 30`, `FLOOR 5`, `ALLOWED_VALUES`, `MIN_VALUE 1`; leitura em `TariffReadjustParameters` recusando `publicationMinDays < 30` | `TariffReadjustParametersTest` + guarda de catálogo na consistência |
| 16 | Metodologia da revisão periódica só com a norma da ANA aprovada | **Não feito (fronteira expressa):** nenhum campo de receita requerida/base de remuneração; a revisão implementada é a por ato. Declarado no cabeçalho da V4_9 e no javadoc de `VersionChangeMetadata` | — |
| 17 | Fronteira com a P1.6 (COE-19): tarifa com simulação é desta tarefa | Nenhum código no console; gestão e simulação vivem no billing (`tariff/`) | — (ausência deliberada) |
| 18 | ED-91 usa as versões com vigência no mecanismo de contingência | Nenhuma regra própria criada; as versões produzidas aqui são as linhas comuns de `tariff_versions` que a ED-91 consumirá | — (fronteira respeitada) |

## 4. Regras invioláveis — como foram seguidas

- **Nenhuma asserção afrouxada:** na entrega original nenhum teste existente foi tocado e os 49 testes novos só acrescentavam. Nas correções pós-revisão (§6), os testes desta própria entrega foram ajustados apenas nas **chamadas** (`ReadjustmentRequest`/`proposeReview` ganharam os fatos da homologação tácita) e 6 testes novos foram acrescentados (55 no total) — nenhuma asserção dos 49 foi removida, enfraquecida ou teve valor esperado alterado; os números conferidos na revisão (IPCA 5%, 6,25−1,25=5,00, simulação 580,60→638,66, P2.5 112,00/201,60) permanecem intocados.
- **Nenhum dado inventado:** nenhum índice, percentual, ato ou prazo é semeado — a migration só registra catálogo de parâmetro (guarda em teste: INSERTs só em `public.parameter_definitions`/`parameter_legal_limits`). Valores de teste são fictícios declarados (tarifa T1 do fixture existente).
- **Parâmetros no padrão existente:** leitura tipada via classe `Json` do core, `MissingParameterException` para ausência, classes d/REG conforme o aceite, piso de 30 dias travado em três camadas (leitura do parâmetro, domínio, CHECK no banco).
- **JSON só via `Json` do core:** `TariffReadjustParameters` usa `Json.readTree`; os snapshots da tabela de simulações são documentados como serializados pela mesma classe (a serialização em si é do chamador que registrar a simulação).

## 5. Pendências honestas e riscos de CI

1. **Sem compilação local** (VM sem JDK/Maven/PostgreSQL): as verificações foram estáticas (ASCII da migration, balanço de chaves, conferência de acessores contra o espelho — ex.: `SewerCharge.charge()`, `BillInput`/`BillCalculator` da P2.5, padrão `columnDefinition = "text"` do `BillEntity`). A prova real é a CI total no PC do dono, como nas demais tarefas.
2. **Risco — `MigrationConsistencyTest` existente (V4_1) — PENDÊNCIA DA CAMADA DE INTEGRAÇÃO (detalhe técnico confirmado na revisão):** ele compara as colunas mapeadas de `TariffVersionEntity` contra o CREATE TABLE da V4_1. A entidade ganhou 11 campos que só existem pela V4_9. As fusões que o `load()` daquele teste já faz são **V4_6 e V4_14** (no mapa de `bills`/`bill_items`) — citação corrigida pós-revisão: a V4_4 **não** é carregada ali. A integração desta tarefa deve estender aquele teste para fundir os ALTERs da V4_9 no mapa de `tariff_versions`, e a fusão **não é trivial**: (i) a regex de ALTER do precedente (V4_6) só aceita tipos `UUID|VARCHAR(n)`, enquanto a V4_9 usa `DATE`, `NUMERIC(9,4)` e `TEXT` — o parser precisa ser ampliado para esses tipos; (ii) o `typeMatches` do teste não tem ramo para `TEXT` em campo `String` (só aceita `VARCHAR` com comprimento igual ou `JSONB` com `@JdbcTypeCode`) — sem estender o `typeMatches`, a guarda continua vermelha em `calculation_method` mesmo com o mapa fundido. **Esse ajuste é da camada de integração, não foi feito aqui** para não editar teste de outra tarefa nesta entrega; sem ele, `mappedColumnsMatchTheSchema(TariffVersionEntity)` falha na CI. É o principal ponto de atenção da integração, e a CI total deve rodar **depois** dessa fusão.
3. **Risco — CHECK de publicação no banco:** `chk_tariff_versions_publication_30d` só atinge linhas com `change_type IN ('READJUSTMENT','REVIEW')` e `published_on` não nulo; fixtures antigas não preenchem `change_type`, logo não são afetadas. Se algum teste de integração futuro gravar reajuste com publicação recente sem passar pelo domínio, o banco recusa (comportamento desejado, mas pode surpreender).
4. **Versões legadas no histórico:** `TariffHistory` recusa versão da janela sem método de cálculo. Versões pré-P2.29 não têm metadado: o chamador precisa declarar o método delas ao montar `VersionWithMetadata` (o método do motor de faixas), ou o histórico falha de forma explícita — comportamento deliberado (transparência não publica versão sem método), mas é trabalho da camada que montar a leitura/ED-38.
5. **`bill.tariff_readjust` no catálogo:** registrado a partir da migration de tenant (precedente V4_4). Se a core V3 ou outra migration posterior tentar registrar a mesma chave, o `ON CONFLICT DO NOTHING` dos dois lados evita duplicidade; vale conferir na integração se nenhuma tarefa P1.6 pendente registra a chave com outro formato de objeto — o formato aqui é o do doc `PARAMETROS-DO-TENANT` ampliado pelo aceite.
6. **Simulação e esgoto na receita por categoria:** a receita por categoria/faixa soma água + esgoto (o esgoto usa a mesma estrutura, escalado pelo fator), como documentado no `ImpactSimulator`; quem precisar separar água de esgoto usa `units` (que traz água+esgoto por unidade) — separação por serviço no agregado fica como evolução, não foi inventada quebra nova.
7. **Fiação de aplicação (fora do aceite):** não há endpoint nem tela nesta entrega — o aceite da P2.29 é de domínio/migration/parâmetros, como o da P2.5; a camada de aplicação que persiste `Proposal` e registra simulações consome `TariffManagementService`/`TariffVersionEntity` sem regra nova.

## 6. Correções pós-revisão

Revisão cruzada (`REVISAO-P2.29.md`): **APROVADA COM RESSALVAS** — 0 críticas · 0 altas · 2 médias · 3 baixas. Correções aplicadas nesta camada (sem push; a V4_9 ainda não foi aplicada, logo ajuste nela era permitido):

### 6.1 [MÉDIA — entrega] Homologação tácita imposta dentro do fluxo de proposta — **FEITO**

- **Problema:** `TariffApprovalRule.tacitApprovalDate` estava correta isolada, mas `proposeReadjustment`/`proposeReview` recebiam `approvalMode`/`approvedOn` livres e nunca chamavam a regra; existia caminho para registrar homologação tácita fabricada (sem previsão normativa e sem prazo decorrido).
- **Correção:** `TariffManagementService` ganhou a conferência `validateApproval(...)`, chamada dentro de `proposeReadjustment` e `proposeReview` (na mesma fronteira do piso de 30 dias): para `TACIT`, chama `TariffApprovalRule.tacitApprovalDate` com os dois fatos — previsão na norma (`tacitApprovalAllowed`) e prazo (`tacitApprovalDays`) — usando a data informada como data da consulta, e ainda exige que ela seja **exatamente** a data da consumação devolvida pela regra (publicação + prazo); qualquer divergência é recusada como homologação fabricada. O metadado grava a data **validada**, não a livre. Para `EXPRESS`, a data informada é validada por `expressApprovalDate` (não antecede a publicação); proposta expressa ainda sem ato (`approvedOn == null`) continua representável (estágio de proposta, sem homologação alegada — decisão registrada). Data sem modo passou a ser recusada.
- **Arquivos:** `tariff/TariffManagementService.java` (`ReadjustmentRequest` ganhou `tacitApprovalAllowed`/`tacitApprovalDays`; `proposeReview` ganhou os mesmos dois parâmetros; novo `validateApproval`), `tariff/VersionChangeMetadata.java` (javadoc da invariante atualizado).
- **Testes novos no nível do serviço** (`TariffManagementServiceTest`, asserções dos existentes intocadas — só as chamadas ganharam os fatos): `tacitApprovalWithoutNormProvisionIsRefusedByTheService` (sem previsão, mesmo com a data "certa"), `tacitApprovalWithoutConfiguredTermIsRefusedByTheService` (sem prazo), `tacitApprovalWithFabricatedDateIsRefusedByTheService` (antes do prazo e depois da consumação), `reviewTacitApprovalWithoutNormProvisionIsRefusedByTheService` (mesma trava na revisão). O teste `tacitApprovalInTheFlow` agora passa pelos fatos no pedido e prova o caminho feliz pelo serviço.

### 6.2 [MÉDIA — integração] `MigrationConsistencyTest` — **MANTIDO COMO PENDÊNCIA DE INTEGRAÇÃO (não editado)**

- O teste **não foi editado** nesta entrega (é teste de outra tarefa/camada), como a revisão confirmou ser o certo. A pendência está registrada com o detalhe técnico completo no §5.2 deste relatório: fundir os ALTERs da V4_9 no mapa de `tariff_versions`; estender a regex de ALTER (hoje só `UUID|VARCHAR(n)`) para `DATE`/`NUMERIC(p,s)`/`TEXT`; estender o `typeMatches` com o ramo `TEXT` para campo `String` (senão `calculation_method` continua vermelho); fusões existentes no espelho são V4_6/V4_14 (a citação cosmética "V4_4" no §5.2 foi corrigida). CI total roda depois dessa fusão.

### 6.3 [BAIXA] `published_on` nulo fora do serviço — **DECISÃO REGISTRADA, CHECK NÃO APERTADO**

- Não foi exigido `published_on NOT NULL` na V4_9: isso conflitaria com fluxos legítimos de rascunho/elaboração que gravam a versão antes da publicação do ato. Fica registrado (decisão 7 no cabeçalho da V4_9) que o serviço de domínio é o caminho de escrita dessas linhas e recusa proposta sem publicação; escrita direta por SQL/entidade fora do serviço é o único caminho sem a trava, a ser fechado por migration futura só se nenhum fluxo de rascunho depender da isenção.
- **Arquivo:** `V4_9__tariff_readjust_review_and_simulation.sql` (só comentário de decisão; nenhum CHECK ou coluna alterado).

### 6.4 [BAIXA] `TariffComparison` emparelhava faixas por posição — **FEITO**

- Emparelhamento passou a ser por `toM3` (faixa aberta = `toM3` nulo); faixa só de um lado entra com o preço do outro lado nulo (acrescentada/removida). Revisão que insere/remove faixa intermediária não desalinha mais os deltas seguintes.
- **Arquivos:** `tariff/TariffComparison.java`; teste novo `TariffComparisonAndCopyTest.comparisonPairsBlocksByToM3NotByPosition` (insere faixa até 35 → 5,00 e confere a de 50 comparada com 6,60, +10,00%).

### 6.5 [BAIXA] Guarda de fórmula nula — **FEITO**

- `formula == null` agora é recusado com `TariffConfigurationException` ("a fórmula não se presume") em `TariffManagementService.proposeReadjustment`, `ReadjustmentCalculator.apply` e `ReadjustmentCalculator.appliedPct` — antes era `NullPointerException`.
- **Arquivos:** `tariff/TariffManagementService.java`, `tariff/ReadjustmentCalculator.java`; teste novo `TariffManagementServiceTest.nullFormulaIsRefusedCleanly`.
- Cosmético da mesma correção: `TariffReadjustParametersTest.publicationDaysBelowTheFloorAreRefused` passou a usar **10 dias** (o número do aceite) em vez de 20 — a asserção é a mesma, só o valor de entrada mudou para o caso canônico já provado na camada de domínio.

### Resumo das correções

| Item da revisão | Severidade | Estado |
|---|---|---|
| 1. Homologação tácita fora do fluxo de proposta | MÉDIA (entrega) | **Corrigido** (§6.1) + 4 testes de serviço |
| 2. Fusão da V4_9 no `MigrationConsistencyTest` | MÉDIA (integração) | **Pendência de integração** registrada com detalhe técnico (§6.2/§5.2); teste não editado |
| 3. `published_on` nulo fora do serviço | BAIXA (decisão) | **Decisão registrada** na V4_9 (decisão 7); CHECK não apertado de propósito (§6.3) |
| 4. `TariffComparison` por posição | BAIXA | **Corrigido** — emparelhamento por `toM3` + 1 teste (§6.4) |
| 5. `formula == null` → NPE | BAIXA | **Corrigido** — `TariffConfigurationException` + 1 teste; cosmético 20→10 dias (§6.5) |

Total de testes da entrega após as correções: **55** (49 originais com asserções intocadas + 6 novos). Nenhum cálculo foi tocado: IPCA 5%, 6,25−1,25=5,00, simulação 580,60→638,66 e reprodução da P2.5 (112,00/201,60) seguem como a revisão os recomputou. Sem compilação local (VM sem JDK/Maven/PostgreSQL): a prova continua sendo a CI total no PC do dono, após a fusão do item 6.2.
