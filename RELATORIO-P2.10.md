# P2.10 — Importador v1 com 120 meses — Relatório da frente de implementação

- Repo: `mastersonmdaniel/Ziip_San`, HEAD conferido via MCP: `0fbcd22` (já inclui a P2.5
  publicada: pushes `5c24d7f`…`0fbcd22`).
- Entrega **local, sem push**: código em `entrega/` (caminhos do repo), espelho parcial em
  `espelho/`.
- Aceite (JSON `tarefas-P2.json`, id P2.10; deps P1.2 ✔, P1.41 ✔, P2.5 ✔):
  "`V14_2`; importador da v1 com 120 meses de histórico, construído sobre o framework
  declarativo de migração da P1.2 (PLT-16); saldo por matrícula igual ao do legado;
  prestações a vencer dos parcelamentos ativos migradas como débitos a cobrar com origem
  parcelamento (`source_type`) e referência ao acordo do legado (`legacy_reference`), nas
  colunas de origem da P2.5 (DAD-20), cobradas uma a uma na conta, e saldo por matrícula
  conferido com elas incluídas; unidade sem usuário vigente importada na situação da P1.41
  (CAD-68); simular, desfazer, reparcelar e antecipar parcelamento continuam na P4.1 (fora
  de escopo)".

## ⚠️ ESPELHO REMOTO INTERROMPIDO POR HTTP 429 (declarado, sem contorno)

A listagem via MCP confirmou o desenho (pacote `legacy` da P1.2 com 21 arquivos; no tenant
do registry só existem `V14_1` e `V14_4` — **V14_2 livre, módulo confirmado:
`commercial/ziipsan-registry`**). Na sequência de leituras o conector devolveu **HTTP 429**;
a parada foi respeitada (nenhuma nova chamada ao GitHub). O trabalho seguiu sobre os
espelhos locais, que cobrem o essencial:

- `LegacyImportService.java` integral (espelho da ED-57 + cópia em `espelho/` desta frente)
  e `LegacyImportServiceTest.java` (assinaturas de `LegacyMapping`, `StagedUnit`,
  `StagedInstallment`, `CsvLegacyReader`, fakes) — a API pública do framework da P1.2;
- `V14_1__legacy_import.sql` integral (schema do staging, inclusive a tabela de prestações
  que a V14_1 "apenas encena para a carga da P2.10 — a P2.10 é dona da virada para APPLIED");
- P2.5 local integral (`p2.5/entrega/`): `DebitReceivable`, `ReceivableSourceType`,
  `ReceivableChecks`, V4_4 (colunas e CHECKs de `debit_receivables`) e V4_1 (colunas-base);
- plano v3.0 local (`ziipsan-retomada/fontes/`): aceite literal da P2.10, da P1.41 (texto da
  CAD-68) e mapa §3 (`V14_1, V14_2 | importação de legado | … histórico de 120 meses |
  P1.2, P2.10`).

**Não lidos** (429): os corpos de `LegacyMapping`, `StagedUnit`, `StagedInstallment`,
`LegacyStagingStore`, `JdbcLegacyStagingStore`, `JdbcLegacyUnitApplier` e os JSON de
mapeamento `piloto-v1.json`/`gsan-v1.json`. Tudo o que esta frente usa dessas classes são
assinaturas exercidas pelo `LegacyImportService` e pelo teste da P1.2 (ver Pendências).

## Arquivos criados (entrega/)

| Arquivo | O quê |
|---|---|
| `commercial/ziipsan-registry/src/main/resources/db/migration/tenant/V14_2__legacy_import_v1_history.sql` | Migration reservada: staging do histórico, histórico aplicado, acordos migrados, vínculos prestação→débito e resíduo→débito, situações de unidade |
| `commercial/ziipsan-registry/src/main/resources/legacy/mappings/v1-history-v1.json` | Declaração de mapeamento da seção de histórico da v1 (dado, não código) |
| `…/registry/legacy/V1HistoryMapping.java` | Leitor/validador da declaração de histórico (vocabulário fechado de alvos/transformações) |
| `…/registry/legacy/V1HistoryRecord.java` | Linha de histórico encenada/aplicada (janela de 120 meses) |
| `…/registry/legacy/V1MigrationStore.java` + `InMemoryV1MigrationStore.java` | Porta de persistência das tabelas da V14_2 + implementação em memória p/ testes (padrão da P1.2) |
| `…/registry/legacy/V1BalanceConference.java` | Conferência de saldo por matrícula com as prestações incluídas (regra pura) |
| `…/registry/legacy/V1UnitSituation.java` | Regra pura CAD-68: sem usuário vigente → `VACANT`; com → `OCCUPIED` |
| `…/registry/legacy/LegacyReferences.java` | **(Correção A4)** Construção das `legacy_reference` com teto de 100 caracteres (V4_4): forma intacta quando cabe; compactação com hash curto quando não cabe |
| `…/registry/legacy/InstallmentDebitGateway.java` | Porta dos débitos da migração (prestação um a um + resíduo), com os campos DAD-20; o record `InstallmentDebit` ganhou o campo `installments` (validado = 1) na correção A6 |
| `…/registry/legacy/JdbcInstallmentDebitGateway.java` | Gateway JDBC sobre `debit_receivables` (V4_1 + V4_4), resolvendo `debit_type_id` pelo catálogo do tenant |
| `…/registry/legacy/UnitSituationGateway.java` + `JdbcUnitSituationGateway.java` | Porta + JDBC que grava `occupancy_status` em `consumer_units` |
| `…/registry/legacy/V1ImportService.java` | Motor da P2.10 sobre o framework da P1.2: carga do histórico, simulação, aplicação |
| `…/legacy/V1ImportServiceTest.java` | Testes do motor em memória (histórico 120 meses, débitos, saldo, CAD-68, idempotência) |
| `…/legacy/V1HistoryMappingTest.java` | Testes da declaração de mapeamento |

Nenhum arquivo existente foi alterado. Nenhum JSON foi escrito à mão em código Java (a
única declaração JSON é o arquivo de mapeamento, lido como recurso).

## Como cada item do aceite foi atendido

1. **Migration `V14_2`** — só o que a P2.10 precisa, no histórico de tenant do registry
   (mesma série da V14_1/V14_4): `legacy_import_staging_history`,
   `legacy_import_history`, `legacy_import_agreements`,
   `legacy_import_installment_debits`, `legacy_import_residual_debits`,
   `legacy_import_unit_situations` — todas novas, com `register_tenant_table`, índices
   únicos de idempotência e grants no precedente da V14_1. Nenhuma coluna nova em tabela
   existente: as colunas de origem dos débitos são da V4_4 da P2.5 e são só consumidas.

2. **Importador da v1 com 120 meses sobre o framework declarativo da P1.2 (PLT-16)** —
   unidades e prestações continuam encenadas pelo `LegacyImportService` da P1.2 (carga,
   ensaio, conciliação, OS de lote e aplicação das unidades são dele; esta frente não
   reimplementa nada disso). O histórico entra pela declaração
   `legacy/mappings/v1-history-v1.json` + `V1HistoryMapping`: legado novo = declaração
   nova, provado no `V1HistoryMappingTest`. **Decisão declarada (correção A1 da revisão
   cruzada)**: a seção de histórico é um mecanismo declarativo IRMÃO do framework da
   P1.2, NÃO uma extensão dele — ela não estende `LegacyMapping`/`LegacyTransform`, e o
   `FieldSpec` entregue tem apenas origem (`source`), destino (`target`), transformação
   (`transform`) e obrigatoriedade (`required`): **não tem campo de validação** (o
   `FieldMapping` da P1.2 tem). Motivo: estender o framework exigiria alterar arquivo
   existente da P1.2, o que esta frente não faz. A compatibilidade do vocabulário de
   transformações desta seção com o do framework fica como **conferência pendente de
   integração** — os corpos de `LegacyMapping`/`LegacyTransform` não foram lidos por
   causa do HTTP 429 (ver aviso no topo). A janela é aplicada na carga: competência fora dos
   120 meses até o mês de corte (`HISTORY_OUT_OF_WINDOW`), depois do corte
   (`HISTORY_AFTER_CUTOVER`), duplicada (`DUPLICATE_HISTORY_MONTH`) ou sem unidade no lote
   (`REFERENCE_UNKNOWN`) é recusada e volta no relatório da carga, sem encenar.

3. **Saldo por matrícula igual ao do legado, conferido com as prestações incluídas** —
   `V1BalanceConference` (regra pura) decompõe, por matrícula: saldo declarado do legado
   (o `balance_cents` encenado pela P1.2) = Σ prestações a vencer + resíduo. A aplicação só
   gera débitos de matrícula que confere; divergências têm código próprio
   (`BALANCE_MISSING`, `BALANCE_NEGATIVE`, `INSTALLMENTS_EXCEED_BALANCE`) e a matrícula
   divergente não gera débito nenhum. **Massa dos testes é sintética declarada**: saldos
   escritos como constantes no teste (LEG-1: 1.234,56 = 40.000 de prestações + 83.456 de
   resíduo; LEG-2: 100,00 de resíduo; total criado = total declarado = 133.456 centavos) —
   nenhum número se apresenta como legado real.

4. **Prestações a vencer → débitos a cobrar, uma a uma, nas colunas da P2.5 (DAD-20)** —
   cada prestação encenada vira UM `debit_receivables` com `installments = 1`,
   `source_type = 'INSTALLMENT_AGREEMENT'`, `legacy_reference` = referência do acordo do
   legado e meses contábil/de arrecadação = mês do vencimento — ou seja, cada prestação
   entra na conta do seu mês, uma a uma, pelo motor de conta da P2.5 que consome
   `debit_receivables`. O resíduo do saldo vira um débito `LEGACY_IMPORT` com
   `legacy_reference` construída por `LegacyReferences` a partir da chave da carga da
   unidade (≤100 caracteres — correção A4, ver seção de correções), fechando a
   igualdade do item 3.
   **Decisão declarada**: o domínio da P2.5 exige `source_id` em `INSTALLMENT_AGREEMENT`
   (CHECK `chk_drec_source_id` + `ReceivableChecks`) e o legado só tem a referência
   textual do acordo; a V14_2 cria `legacy_import_agreements` — a identidade migrada do
   acordo (UUID estável por lote+referência) que o `source_id` referencia, sem inventar
   operação de parcelamento nenhuma (P4.1). `financing_term` = total de prestações do
   acordo **dentro da unidade em processamento** (o `termByAgreement` de
   `V1ImportService.apply` é calculado por unidade, não no lote — texto alinhado ao
   código na correção A8). **Limitação conhecida (A8)**: se um mesmo acordo estiver
   presente em duas unidades, o prazo fica subcontado em cada uma; corrigir exige
   contagem no lote inteiro antes da aplicação — registrado como pendência conhecida,
   não corrigido nesta frente. O vínculo prestação→débito fica em `legacy_import_installment_debits`
   (prova um a um + idempotência).

5. **Unidade sem usuário vigente na situação da P1.41 (CAD-68)** — `V1UnitSituation`:
   sem nome de titular no staging ou com documento de espécie `NONE` → `VACANT`; com
   usuário vigente → `OCCUPIED`. É exatamente o texto da CAD-68 ("`VACANT` só sem usuário
   vigente"; "unidades carregadas sem titular pela P1.2 recebem `VACANT`"). A aplicação
   grava a `occupancy_status` via `JdbcUnitSituationGateway` e registra a decisão por lote
   em `legacy_import_unit_situations`. O parâmetro `registry.vacant_unit_holder` (padrão:
   sem titular, fatura retida) é do cadastro/P1.41 e não é duplicado aqui. Teste dedicado
   com `StagedUnit` construída sem titular e com titular.

6. **Simular antes de aplicar / idempotência (coerência com o framework)** —
   `V1ImportService.simulate` devolve conferência de saldo, contagens de histórico a
   aplicar/recusado, prestações a converter e unidades VACANT/OCCUPIED **sem chamar
   nenhum gateway** (teste prova os fakes vazios depois da simulação) — o ensaio da P1.2
   (`dryRun`) continua sendo o das unidades. Reaplicação é no-op total (vínculos da V14_2
   + histórico já aplicado + situação já registrada), com teste. Pré-condição declarada: a
   aplicação da P2.10 roda DEPOIS da aplicação das unidades pela P1.2 — unidade não
   aplicada não recebe débito, histórico nem situação.

7. **Fora de escopo respeitado** — nenhuma operação de parcelamento (simular, desfazer,
   reparcelar, antecipar) foi criada; o acordo migrado é só identidade + débitos. É a P4.1.

## Suposições declaradas

1. **`debit_types` é catálogo do tenant sem códigos semeados pela P2.5** (conferido na
   V4_4: a migration cria o catálogo, não itens). Por isso os códigos do tipo de débito de
   prestação e de resíduo são **configuração da aplicação** (`ApplyConfig`), resolvidos
   para `debit_type_id` pelo gateway; código ausente/inativo no catálogo aborta com erro
   explícito antes de qualquer INSERT. Os testes usam os códigos fictícios declarados
   `PARCELAMENTO_MIGRADO`/`SALDO_MIGRADO`. A integração/dono deve cadastrar os itens reais
   no catálogo do tenant piloto e passar os códigos escolhidos.
2. **Saldo do legado inclui as prestações a vencer** — é a leitura do aceite ("saldo
   conferido com elas incluídas"): o `balance_cents` encenado é o saldo total da
   matrícula. Se um legado declarar saldo SEM as prestações, a conferência reprova a
   matrícula (`INSTALLMENTS_EXCEED_BALANCE` ou resíduo divergente) em vez de ajustar —
   divergência se resolve na carga, não no motor.
3. **Histórico aplicado vive nas tabelas da V14_2** (`legacy_import_history`), que é o
   que o mapa §3 reserva para a V14_2 ("histórico de 120 meses" na linha de importação).
   A projeção desse histórico em contas (`bills`) e consumo (`consumption_records`) NÃO
   foi feita: reconstituir contas históricas particionadas é outro desenho (a P2.5,
   DAD-14, reconstitui 120 meses a partir de consumo versionado + contas) e misturá-lo
   aqui inventaria regra de faturamento retroativo. Fica como pendência de integração
   decidida pelo dono (ver Pendências).
4. Mês contábil/de arrecadação da prestação = mês do vencimento (prestação sem
   vencimento encenado cai no mês de corte); resíduo = mês de corte nos dois meses.
5. `legacy_reference` do resíduo = `<batchId>:<legacyKey>` quando cabe — referência da
   carga, única por unidade e lote, conferível contra a linhagem da P1.2. **Correção
   A4**: essa forma natural chega a 137 caracteres (UUID de 36 + `:` + `legacy_key` de
   até 100 na V14_1) e estoura a `VARCHAR(100)` da V4_4; quando não cabe,
   `LegacyReferences.residualReference` compacta para
   `<batchId>:<54 chars da chave>#<hash8 do SHA-256 da referência completa>` (= 100
   caracteres), mantendo o lote integral no prefixo e a unicidade pelo hash.

## Pendências honestas (integração)

1. **Compilação não executada** (sem JDK nesta VM, como instruído) e pontos a confirmar
   contra os fontes não lidos por causa do 429, todos usados apenas em assinaturas que o
   `LegacyImportService`/teste da P1.2 exercem: acessor `StagedUnit.appliedUnitId()`,
   `holderDocumentKind()`, construtores de `StagedUnit`/`StagedInstallment` (ordem dos
   argumentos conforme `LegacyImportService.mapUnitRow`/`loadInstallments`), métodos de
   `LegacyStagingStore` (`listUnits`, `listInstallments`, `findBatchById`,
   `insertLineage`) e existência de `InMemoryLegacyStagingStore` e dos recursos
   `/legacy/mappings/piloto-v1.json` no classpath de teste (o teste da P2.10 os reutiliza
   como o da P1.2). A CI total é quem prova.
2. **Implementação JDBC do `V1MigrationStore`** (sobre as tabelas da V14_2) e a fiação
   dos gateways JDBC (DataSource/schema do tenant, `@Component`/configuração) ficam para
   a integração, no precedente das frentes anteriores (o motor e as portas estão prontos
   e testados em memória; os dois gateways JDBC desta entrega já são código real contra
   `debit_receivables`/`consumer_units`).
3. **(A5) Virada `STAGED → APPLIED` das prestações no staging da P1.2 — NÃO EXECUTADA
   nesta entrega.** A V14_1 a reserva à P2.10, mas ela **não está implementada**: o que
   existe é o registro da conversão pelo vínculo `legacy_import_installment_debits`
   (fonte da verdade da conversão). Atualizar a coluna `status` de
   `legacy_import_staging_installments` fica para a implementação JDBC do
   `V1MigrationStore` na integração (item 2), na mesma transação do vínculo —
   declarado aqui, com todas as letras, para não passar por feito.
4. **Projeção do histórico em contas/consumo** (suposição 3) e o cadastro dos itens de
   `debit_types` do piloto (suposição 1): decisões/ações do dono na integração.
5. `consumer_units.occupancy_status` e `updated_by`: coluna da P1.41 (V3_8 do cadastro,
   conforme o aceite dela); o gateway JDBC a referencia — se o nome da tabela/coluna
   divergir no fonte da P1.41, o ajuste é só neste gateway.
6. **(A7) Store JDBC real de `legacy_import_agreements`** — não implementado: o
   `agreementId` só existe no `InMemoryV1MigrationStore` (UUID estável por
   lote+referência em memória). A implementação JDBC do `V1MigrationStore` (item 2)
   deve persistir/ler a identidade do acordo na tabela da V14_2.
7. **(A9) Chave de histórico já aplicado sem `tenantId`** — a porta
   `V1MigrationStore.historyAlreadyApplied(unitLegacyKey, referenceMonth)` e a
   implementação em memória chaveiam só por (unidade, mês). A implementação JDBC deve
   incluir o `tenantId` nessa chave, para um tenant nunca enxergar o histórico
   aplicado de outro.

## Correções pós-revisão (frente de correção)

A revisão cruzada aprovou a entrega **com correções**; esta seção registra o que foi
aplicado, item a item. Nenhum push foi feito e nenhum build foi rodado (sem JDK nesta
VM, como instruído) — a prova continua sendo a CI total na integração.

- **A4 (código, obrigatória) — estouro de `legacy_reference` no resíduo: CORRIGIDA.**
  A forma `batchId + ":" + legacyKey` chega a 137 caracteres e estoura a
  `VARCHAR(100)` de `debit_receivables.legacy_reference` (V4_4 da P2.5). Novo
  `LegacyReferences`: referência que cabe vai intacta; a que não cabe é compactada
  para `<batchId>:<54 chars da chave>#<hash8 SHA-256 da referência completa>`
  (exatamente 100), com o lote integral no prefixo (rastreável pela linhagem da
  P1.2) e unicidade pelo hash. `V1ImportService` passou a usar a função no resíduo.
  **Verificação das prestações**: a `legacy_reference` delas é a referência pura do
  acordo, cuja coluna de origem (`legacy_agreement_ref`, V14_1/V14_2) já é
  `VARCHAR(100)` — o estouro **não existe** por essa via (cabe exatamente no pior
  caso); mesmo assim o gateway JDBC passou a gravar via
  `LegacyReferences.installmentReference`, como defesa caso a origem mude. Teste novo
  com chave legada de 100 caracteres prova referência ≤100, prefixo do lote e débito
  de resíduo criado; teste unitário da função prova forma curta intacta, forma longa
  com 100 caracteres e não-colisão entre chaves de mesmo prefixo.
- **A2 (testes) — divergência em nível de aplicação + `BALANCE_MISSING`/`BALANCE_NEGATIVE`:
  CORRIGIDA.** Novo `divergentMatriculaCreatesNoDebitOnApply` executa `apply()` com
  prestações (40.000) maiores que o saldo (30.000) e asserta ZERO débitos nos dois
  gateways (prestações e resíduos vazios, nenhum vínculo criado). Novos
  `conferenceMissingAndNegativeBalance` (regra pura: códigos e valores exatos) e
  `missingOrNegativeBalanceCreatesNoDebitOnApply` (aplicação: as duas matrículas
  puladas, zero débitos, códigos `BALANCE_MISSING` e `BALANCE_NEGATIVE` no relatório).
- **A3 (teste) — `HISTORY_AFTER_CUTOVER`: CORRIGIDA.** O fixture de histórico ganhou a
  linha `LEG-1;11/2026` (depois do corte 10/2026), recusada com
  `HISTORY_AFTER_CUTOVER`. Contagens recalculadas e declaradas no teste: recusadas
  3 → **4**; aceitos **continuam 121** (120 meses da janela 11/2016–10/2026 de LEG-1
  + 1 linha válida de LEG-2) — a linha nova é recusa, não aceito. Nenhuma asserção
  existente foi afrouxada.
- **A6 (testes) — `installments=1` e VACANT ponta-a-ponta: CORRIGIDA.** (i) O record
  `InstallmentDebit` não tinha campo de parcelas: ganhou `installments`, validado
  como exatamente 1 no construtor compacto (débito agrupado é recusado na porta);
  `V1ImportService` passa `1` e o `JdbcInstallmentDebitGateway` grava o campo do
  record na coluna `installments` (valor continua 1). O fake dos testes captura o
  campo (`installmentCounts`) e o teste das prestações asserta `== 1` em todos os
  débitos. (ii) Novo `vacantAppliedEndToEnd`: `apply()` sobre unidade aplicada sem
  titular grava `VACANT` pelo gateway de situação (e `OCCUPIED` para a unidade com
  titular, no mesmo lote) — antes só a regra pura era exercida e só `OCCUPIED` ia
  ponta-a-ponta. Os testes de aplicação novos usam fixture manual
  (`appliedUnit`/`stage` sobre o staging em memória), só com assinaturas que a P1.2
  e esta entrega já exercem.
- **A1 (relatório) — CORRIGIDA** no item 2 do aceite: retirada a afirmação de
  equivalência com o framework ("campo de origem, transformação, validação e
  destino, como no framework") e declarada a decisão: mecanismo declarativo irmão,
  `FieldSpec` sem campo de validação, motivo (não alterar arquivo existente da
  P1.2) e a compatibilidade do vocabulário de transformações como conferência
  pendente de integração (corpos de `LegacyMapping`/`LegacyTransform` não lidos —
  HTTP 429).
- **A8 (relatório) — CORRIGIDA** no item 4 do aceite: o texto agora diz que o
  `financing_term` conta as prestações do acordo **dentro da unidade em
  processamento** (como o código faz), com a limitação registrada como pendência
  conhecida (acordo em duas unidades subconta o prazo em cada uma); comentário
  equivalente foi acrescentado ao código, junto do `termByAgreement`.
- **A5, A7, A9 — NÃO implementadas, por instrução da revisão**: apenas declaradas
  com clareza nas Pendências honestas (itens 3, 6 e 7): a virada `STAGED → APPLIED`
  das prestações (A5) fica para a implementação JDBC do `V1MigrationStore` na
  integração, junto do store JDBC real de `legacy_import_agreements` (A7) e da
  inclusão do `tenantId` na chave de histórico já aplicado do store JDBC (A9).

**Asserções existentes**: nenhuma foi afrouxada, removida ou relaxada. As mudanças
em testes preexistentes são apenas: (a) acréscimos de asserções (`installments == 1`
no teste das prestações); (b) o fixture de histórico ganhou UMA linha (a pós-corte
da A3), com a contagem de recusadas recalculada de 3 para 4 e a de aceitos
confirmada em 121 — ajuste declarado no próprio teste. Todo o resto é teste novo.
