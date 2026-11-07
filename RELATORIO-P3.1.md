# P3.1 — Relatório de entrega (R5a: retorno e conciliação N3)

Execução de 07/11/2026. Base de referência: HEAD `0fbcd22` conferido via MCP
(mais novo que o `2ba51f8` da tarefa — pushes da Onda 1). **Nenhum push foi
feito**; o pacote aguarda push pelo agente principal. **Nenhum teste rodou
nesta VM (sem JDK/Maven/Docker — confirmado: não há `java`/`javac`)**; toda a
conferência foi estática. A prova é a CI total no PC do dono.

## Limitação do espelho (sem esconder)

O MCP retornou **HTTP 429** ao listar `commercial/ziipsan-collection`; conforme
a instrução da tarefa, **parei todas as chamadas ao GitHub** nesta tentativa.
Confirmado antes do 429: o módulo de pagamentos/retorno é
`commercial/ziipsan-collection` (irmãos: billing, fiscal, registry) e o HEAD é
`0fbcd22`. A entrega foi construída sobre os espelhos/pacotes locais:
billing completo (pacote p2.5), core (`Json`, `DocumentNumber` da P1.39,
`TenantAwareEntity`, `MissingParameterException`) e o estilo do pacote p3.6
(serviço puro + portas, entidade JPA, migration, guarda textual).

**Consequência prática:** o conteúdo atual do `ziipsan-collection` no repo
(pom, migration V6_1 e eventuais entidades/testes de `payments` pré-existentes)
**não foi lido**. Na integração, antes do push, é obrigatório diffar:
(1) se já existir entidade/mapping de `payments`, fundir em vez de duplicar;
(2) o `pom.xml` do módulo (esta entrega **não inclui pom** de propósito — não
usa dependência nova além de core + JPA, o padrão dos módulos irmãos);
(3) a FK para `fiscal_documents` presume UNIQUE composta `(tenant_id, id)`
na tabela da V4_5 (padrão do projeto, não conferido no arquivo real).

## Arquivos entregues (27)

Módulo `commercial/ziipsan-collection`, pacote `br.com.ziipsan.commercial.collection`:

- **Migration (só a reservada):**
  `src/main/resources/db/migration/tenant/V6_2__payment_return_and_reconciliation.sql`
- **domain/**: `PaymentEntity`, `ReturnFileEntity`, `ReturnFileDetailEntity`,
  `PaymentClassificationEntity`, `WithholdingCreditEntity`, `PaymentEventEntity`
- **repository/**: um `JpaRepository` por entidade (6)
- **parameters/**: `CollectionParameters` (`pay.adjust_tolerance`,
  `pay.withholding_enabled`; JSON só via `Json` do core; ausência =
  `MissingParameterException`, sem padrão presumido)
- **retorno/**: `ReturnFileProcessor` (ingestão idempotente + CNPJ P1.39),
  `PaymentSituation` (8 situações), `PaymentClassifier` (situação → efeito),
  `DoubleApprovalRule` (aprovação dupla), `SplitPaymentReconciler`
  (conciliação do split), `WithholdingRule` (retenção IN RFB 1.234/2012)
- **Testes (7 classes, 40 casos):** `ReturnFileProcessorTest` (6),
  `PaymentClassifierTest` (11), `DoubleApprovalRuleTest` (3),
  `SplitPaymentReconcilerTest` (5), `WithholdingRuleTest` (5),
  `CollectionParametersTest` (4), `CollectionMigrationConsistencyTest` (6 —
  guarda textual da V6_2, no padrão das guardas dos outros módulos)

## Acceptance — item a item

| # | Item | Situação |
|---|---|---|
| 1 | Migration `V6_2`, só essa | ✅ entregue |
| 2 | Retorno idempotente por NSA + hash; reprocessamento não duplica baixa (teste) | ✅ chave `(tenant, nsa, sha256)` na V6_2 (índice único) e no `ReturnFileProcessor`; teste com store falso contando gravações (1 gravação em 2 processamentos) |
| 3 | Aprovação dupla nas classificações manuais | ✅ `DoubleApprovalRule` (2 aprovadores distintos; solicitante não aprova) + CHECKs no banco; testes dos 3 casos |
| 4 | Classificação grava o efeito: baixa da versão vigente | ✅ decisão `SETTLEMENT` com `settledAmount`; a **gravação** transacional na conta do billing é fiação de integração (ver Pendentes) |
| 5 | Débito a cobrar / crédito a realizar de ajuste na conta seguinte dentro de `pay.adjust_tolerance` | ✅ `NEXT_BILL_DEBIT`/`NEXT_BILL_CREDIT` com residual calculado; fora da tolerância → pendente manual (decisão documentada no código) |
| 6 | Aviso de duplicidade (NR 11, art. 37); devolução pela P3.29 | ✅ situação `DUPLICATE_PAYMENT` com `notifyUserOfDuplicate` e efeito `DUPLICATE_REFUND_HANDOFF` (a devolução em si é da P3.29 — fronteira respeitada) |
| 7 | Sem acréscimo sobre conta em revisão aberta | ✅ acréscimos ignorados no devido quando `billUnderOpenReview`; teste dedicado |
| 8 | Transferência por OS `PAYMENT_TRANSFER` com aprovação dupla | ✅ situação/efeito `PAYMENT_TRANSFER`/`TRANSFER`, sempre manual; o vínculo com a OS real é fiação (campo `service_order_id` na classificação) |
| 9 | Conta prescrita: recebimento válido, sem devolução (CC art. 882), conta paga + evento p/ ED-32 | ✅ `PRESCRIBED_BILL_PAYMENT`/`CREDIT_RECOVERY`, `refundable=false`, tabela `payment_events` + entidade |
| 10 | Colunas FEBRABAN 150 fora de `payments`, na tabela de detalhe; sem uso por Pix/cartão | ✅ as 6 colunas só existem em `return_file_details`; a V6_2 as remove de `payments` se preexistirem (DROP IF EXISTS); Pix/cartão nunca geram detalhe; guarda textual testa isso |
| 11 | `payments.collection_month` (dia 1, ≠ competência) e `fiscal_document_id` com FK | ✅ colunas + CHECK de dia 1 + FK composta p/ `fiscal_documents (tenant_id, id)` (presumida — ver Limitação); `collectionMonthOf` testado |
| 12 | Split payment: líquido × tributo segregado, por modo da P2.4, 1 teste/modo | ⚠️ **parcial** — reconciliador com 3 modos e 1 teste por modo + divergência; os **códigos dos modos são desta implementação**: o catálogo de `tax.profile.split_payment_mode` é da P2.34 (não executada) e a P2.4 não enumera os modos nas fontes locais; a fiação deve mapear o parâmetro → enum recusando valor desconhecido |
| 13 | Retenção pagador público (IN RFB 1.234/2012), retido = crédito do tenant, parâmetro só pós-PD-10; teste desligado e ligado | ✅ `WithholdingRule` (desligado + retenção informada = erro explícito; ligado + público = crédito; ligado + privado = erro) + tabela `withholding_credits`; parâmetro sem padrão presumido |
| 14 | CNPJ alfanumérico da P1.39 nos retornos (teste) | ✅ `DocumentNumber.isValidCnpj` no cabeçalho e nas linhas; teste com `12.ABC.345/01DE-35` (exemplo oficial) e recusas |
| 15 | Um teste por situação de classificação | ✅ 8 situações, 8+ testes nomeados por situação, valores calculados à mão (conta 100,00, tolerância 5,00) |
| 16 | Fronteiras: P3.2 (Pix Automático/híbrida), P2.28 (caixa c/ delegação), GL.2 (fecha conta a conta) | ✅ **não implementadas**, só referenciadas: a aprovação dupla local espelha a regra da P2.28 sem substituí-la; nada de Pix/híbrida; GL.2 é ensaio, fora de código |

## Revisão cruzada

A tarefa pedia um subagente revisor; **esta sessão é depth 2/2 sem permissão
de spawn** — a revisão foi feita pelo próprio executor, item a item contra o
acceptance e os padrões do módulo, e **pegou um defeito real**: o CNPJ usado
como "inválido" no teste (`11.222.333/0001-81`) é na verdade válido; trocado
por `-82` (DV errado), com comentário no teste. Checagens estáticas executadas:
chaves balanceadas em todos os `.java`, situações do enum × CHECKs da V6_2,
imports restritos aos pacotes do projeto/JDK/JPA/Lombok/Jackson, assinatura de
`MissingParameterException(key, detail)` conferida contra o core. **Recomendo
revisão cruzada de verdade pelo agente principal antes do push**, como nas
demais tarefas.

## Pendente / incerto (sem esconder nada)

1. **Fiação transacional (integração):** adaptador JPA da porta
   `ReturnFileStore`; serviço que aplica a `ClassificationDecision` (baixa na
   conta do billing, criação do débito/crédito a realizar da P2.5, aviso ao
   usuário, emissão do `CREDIT_RECOVERY`, abertura da OS `PAYMENT_TRANSFER`);
   bean/registro dos serviços. As regras estão puras e testadas; a escrita
   cruzada entre módulos não foi inventada aqui.
2. **Diff obrigatório do `ziipsan-collection` real** (ver Limitação): pom,
   V6_1, `payments` pré-existente e testes do módulo não foram lidos (429).
3. **Histórico das colunas FEBRABAN** em linhas antigas de `payments` (se
   houver): a V6_2 remove as colunas sem copiar valores — não há arquivo de
   retorno ao qual associá-los no modelo atual; se o tenant tiver esse dado,
   a cópia é etapa operacional anterior à migration.
4. **Modos do split** (item 12): nomes/valores a confirmar contra
   `tax.profile` da P2.34 na integração.
5. **`collection_month` em tabela preexistente** nasce anulável (ADD COLUMN);
   o NOT NULL vale para a tabela criada pela V6_2. Backfill de linhas antigas
   é operacional, não inventado na migration.
6. **Risco de CI:** sem compilação local, restam riscos de assinatura/import
   (ex.: `JsonNode.decimalValue()/booleanValue()` do Jackson 3) que só o
   `mvn clean verify` revela — esperar a primeira rodada da camada para isso.
