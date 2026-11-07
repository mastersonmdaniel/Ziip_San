# P3.1 — Revisão cruzada independente (R5a: retorno e conciliação N3)

Revisor: agente independente (primeira revisão do pacote; o executor não pôde
revisar o próprio trabalho). Data: 07/11/2026. Revisão **estática** — esta VM
não tem JDK/Maven/Docker; a prova final continua sendo a CI total no PC do dono.
APIs do core conferidas contra o espelho real em
`~/workspace/ziipsan-producao/p2.5/espelho/` (`Json`, `DocumentNumber`,
`TenantAwareEntity`, `MissingParameterException`) e o padrão de parâmetros
contra `BillingParameters` (P2.5). **Nenhum push feito.**

## Veredito: LIBERA COM CORREÇÕES APLICADAS

O pacote cobre o acceptance no nível de regras puras + entidades + migration,
no mesmo padrão das tarefas irmãs (P2.5/P3.6). Um defeito real de runtime foi
corrigido nesta revisão (payload JSONB sem `@JdbcTypeCode`) e três testes de
fronteira foram acrescentados. Restam **pendências de integração já declaradas
pelo executor e confirmadas aqui** (fiação transacional, diff do módulo real,
modos do split) que não são defeito deste pacote, mas **impedem considerar a
P3.1 "gravando efeito" em produção até a integração** — ver tabela e pendências.

## Tabela item a item do acceptance

| # | Item do acceptance | Situação | Evidência / observação do revisor |
|---|---|---|---|
| 1 | Migration única `V6_2` (tenant, ziipsan-collection) | ✅ COBERTO | Único `.sql` do pacote é a V6_2; nenhuma outra migration presente. |
| 2 | Retorno idempotente por NSA + hash; reprocessamento não duplica baixa (teste) | ✅ COBERTO (regra) / fiação pendente | Índice único `(tenant_id, nsa, file_hash)` na V6_2 + `ReturnFileProcessor` devolve `REPROCESSADO_SEM_EFEITO` sem chamar `saveNewFile`; teste `reprocessamentoNaoDuplicaBaixa` conta gravações no store falso (1 em 2 processamentos) — teste não circular. O adaptador JPA da porta e o tratamento da violação do índice único em concorrência são fiação de integração. |
| 3 | Aprovação dupla nas classificações manuais | ✅ COBERTO (regra) | `DoubleApprovalRule`: 2 aprovadores distintos, solicitante não aprova (1ª **e** 2ª aprovação — teste da 2ª acrescentado nesta revisão); CHECKs espelhados no banco (`first/second <> requested_by`, `second <> first`, `second` exige `first`). A ligação regra→entidade/status é fiação (não há serviço que transicione PROPOSTA→APROVADA→APLICADA). Caixa com delegação (P2.28) corretamente NÃO implementada. |
| 4 | Classificação grava o efeito: baixa da versão vigente da conta | ⚠️ PARCIAL | `PaymentClassifier` decide `SETTLEMENT` com `settledAmount` = valor devido da versão vigente; entidades/repositórios existem. **Quem grava a baixa na conta do billing não existe no pacote** (decisão pura, sem aplicador transacional) — declarado pelo executor como fiação de integração. Aceito no padrão do projeto, mas o item só fecha na integração p2b/coorte da P3.1. |
| 5 | Débito/crédito de ajuste na conta seguinte dentro de `pay.adjust_tolerance` | ✅ COBERTO (regra) | `NEXT_BILL_DEBIT`/`NEXT_BILL_CREDIT` com residual calculado à mão nos testes (96→débito 4,00; 103→crédito 3,00; tolerância 5,00). Fronteira inclusiva (diferença == tolerância) conferida e **teste de fronteira acrescentado nesta revisão** (95,00 e 105,00). Fora da tolerância → `NONE_PENDING_MANUAL` + aprovação dupla. A criação efetiva do débito/crédito da P2.5 é fiação. |
| 6 | Aviso de duplicidade (NR 11, art. 37); devolução pela P3.29 | ✅ COBERTO | Situação `DUPLICATE_PAYMENT`, `notifyUserOfDuplicate=true`, `refundable=true`, efeito `DUPLICATE_REFUND_HANDOFF`. A devolução em si **não** foi implementada — fronteira da P3.29 respeitada, sem invasão. |
| 7 | Sem acréscimo sobre conta em revisão aberta | ✅ COBERTO | `billUnderOpenReview` zera os acréscimos no devido; teste dedicado (100,00 + 4,00 ignorados → baixa de 100,00) + teste de contraste sem revisão (devido 106,00). |
| 8 | Transferência por OS `PAYMENT_TRANSFER` com aprovação dupla | ✅ COBERTO (regra) | Situação/efeito `PAYMENT_TRANSFER`/`TRANSFER`, sempre `requiresManualApproval=true`, auto-transferência recusada, campos `target_bill_id`/`service_order_id` na classificação. Abertura da OS real é fiação. Ver observação O2 (ordem de precedência). |
| 9 | Conta prescrita: recebimento válido, sem devolução (CC art. 882), conta paga + evento de recuperação p/ ED-32 | ✅ COBERTO (regra) | `PRESCRIBED_BILL_PAYMENT`/`CREDIT_RECOVERY`, `refundable=false`, `emitsCreditRecoveryEvent=true`, tabela/entidade `payment_events`. A emissão efetiva do evento é fiação. Ver observação O3 (pagamento parcial de prescrita). |
| 10 | Colunas FEBRABAN 150 fora de `payments`, na tabela de detalhe; sem uso por Pix/cartão | ✅ COBERTO | As 6 colunas (`capture_form`, `payment_form`, `collecting_branch`, `authentication`, `line_number`, `barcode`) só existem em `return_file_details`; V6_2 faz `DROP COLUMN IF EXISTS` das 6 em `payments`; entidade `PaymentEntity` não as declara; guarda textual testa os três fatos. Pix/cartão nunca geram detalhe (só o processador de retorno cria detalhes). Histórico das colunas em linhas antigas não é copiado — decisão documentada e honesta (não há arquivo de retorno ao qual associar); se existir dado real, a cópia é passo operacional **antes** da migration. |
| 11 | `payments.collection_month` (dia 1) e `fiscal_document_id` com FK | ⚠️ PARCIAL | Tabela nova: `collection_month DATE NOT NULL` + CHECK de dia 1 + `collectionMonthOf` testado; FK composta `fk_payments_fiscal_document → fiscal_documents (tenant_id, id)`. **Duas ressalvas:** (a) a FK presume UNIQUE `(tenant_id, id)` em `fiscal_documents` (V4_5) — padrão do projeto, **não conferível** nos espelhos locais (a V4_5 não está espelhada); se a UNIQUE não existir, a migration falha na criação da FK; (b) em tabela `payments` **preexistente**, a coluna nasce anulável e o CHECK admite NULL, divergindo da entidade (`nullable=false`) — backfill é operacional, já registrado pelo executor. |
| 12 | Split payment (LC 214/2025, LC 227/2026): líquido × tributo segregado, 1 teste por modo da P2.4 | ⚠️ PARCIAL | `SplitPaymentReconciler` com 3 modos e 1 teste por modo + divergência, valores calculados à mão (bruto 1.000,00). **Verificação do revisor nas fontes:** a P2.4 **não enumera modos** (seu acceptance remete o split à P3.1) e `tax.profile.split_payment_mode` é da P2.34, ainda não executada — **não existe catálogo de modos para conferir**. Os 3 modos são invenção declarada desta implementação; a integração deve mapear o parâmetro da P2.34 → enum recusando valor desconhecido. Item não pode ser marcado coberto enquanto a P2.34 não existir. |
| 13 | Retenção pagador público (IN RFB 1.234/2012), retido = crédito do tenant, ligada por parâmetro pós-PD-10; teste desligado E ligado | ✅ COBERTO | `WithholdingRule`: desligado + retenção informada = erro explícito citando PD-10; ligado + público = baixa pelo bruto, líquido = bruto − retido, crédito = retido (1.000,00/65,00/935,00 à mão); ligado + privado = erro; tabela `withholding_credits`. `pay.withholding_enabled` sem padrão presumido: ausência = `MissingParameterException` (teste). |
| 14 | CNPJ alfanumérico da P1.39 nos retornos (teste) | ✅ COBERTO | `DocumentNumber.isValidCnpj` (API real conferida no espelho do core) no cabeçalho e nas linhas, antes de gravar; arquivo inteiro recusado se inválido. **Revisor recalculou os DVs à mão:** `12ABC34501DE35` (exemplo oficial) é válido de fato (DV1=3, DV2=5); o "inválido" do teste `11222333000182` é inválido de fato (DV2 correto seria 1) — o defeito que o executor pegou na autorrevisão era real e a troca está certa. |
| 15 | Um teste por situação de classificação | ✅ COBERTO | 8 situações do enum × 8 testes nomeados por situação em `PaymentClassifierTest` (+ exata com acréscimo, revisão aberta, auto-transferência e, nesta revisão, fronteira da tolerância). Totais conferidos: 43 `@Test` após as correções (eram 40). |
| 16 | Fora de escopo: Pix Automático/híbrida (P3.2), caixa c/ delegação (P2.28), GL.2 | ✅ RESPEITADO | Nenhum código de Pix Automático, cobrança híbrida ou delegação no pacote (busca textual negativa); GL.2 é ensaio, corretamente fora de código. Nenhuma invasão de escopo encontrada. |

## Correções aplicadas nesta revisão

| # | Arquivo | Era | Ficou |
|---|---|---|---|
| C1 | `domain/PaymentEventEntity.java` | `payload` era `String` com `columnDefinition = "jsonb"` **sem** `@JdbcTypeCode` — divergência do padrão do projeto (billing usa `@JdbcTypeCode(SqlTypes.JSON)` em toda String JSONB e tem guarda textual disso na P2.5); sem a anotação, o Hibernate liga a String como VARCHAR e o PostgreSQL recusa o INSERT em coluna `jsonb` em runtime — o evento `CREDIT_RECOVERY` da conta prescrita falharia ao gravar. | Adicionados `import org.hibernate.annotations.JdbcTypeCode` / `org.hibernate.type.SqlTypes` e `@JdbcTypeCode(SqlTypes.JSON)` sobre `payload`, idêntico a `BillEntity` (P2.5). |
| C2 | `persistence/CollectionMigrationConsistencyTest.java` | Sem guarda para o defeito C1. | Novo teste `payloadJsonbDoEventoUsaJdbcTypeCodeJson` (reflexão sobre a entidade, no padrão da guarda do billing) provando C1. |
| C3 | `retorno/PaymentClassifierTest.java` | A fronteira exata de `pay.adjust_tolerance` (diferença == tolerância) não tinha teste — é justamente a linha onde um `>` viraria `>=` sem ninguém perceber. | Novo teste `diferencaExatamenteNaToleranciaAindaEstaDentroDela`: pago 95,00 → débito 5,00; pago 105,00 → crédito 5,00 (calculados à mão). A implementação já estava certa (inclusiva); o teste trava a semântica. |
| C4 | `retorno/DoubleApprovalRuleTest.java` | Só testava o solicitante como **primeiro** aprovador. | Novo teste `solicitanteTambemNaoPodeSerOSegundoAprovador`. A implementação já recusava (a checagem é sobre o novo aprovador, em qualquer estágio); o teste trava a semântica. |

Nenhuma asserção existente foi alterada ou afrouxada. Chaves/parênteses
rebalanceados após as edições; contagem de testes: 40 → 43.

## Defeitos e observações (sem correção — decisão/registro)

- **D1 (risco de migration, pendência de integração):** os seis
  `ALTER TABLE … ADD CONSTRAINT uq_*_tenant_id` da V6_2 são incondicionais,
  enquanto a FK e o CHECK de `payments` usam `DROP CONSTRAINT IF EXISTS`
  antes. Se a tabela `payments` (ou outra das seis) já existir da V6_1 com
  UNIQUE de mesmo nome, a V6_2 falha com "constraint already exists". O padrão
  é o mesmo da V4_4 do billing (ADD sem DROP em tabela preexistente), por isso
  **não reescrevi**; mas o diff obrigatório da V6_1 (pendência P1) deve
  confirmar os nomes antes do push. Se já existirem, a correção é trocar esses
  seis ADDs por DROP+ADD como nos outros dois casos do próprio arquivo.
- **D2 (menor):** `SplitPaymentReconciler` não recusa tributo segregado maior
  que o bruto (`expectedNet` pode ficar negativo em dado impossível). Registrar
  para a fiação validar na entrada; não alterei a regra pura.
- **D3 (menor):** `SplitPaymentReconcilerTest.modoAusenteEErroExplicito` aceita
  `NullPointerException` (de `Objects.requireNonNull` com mensagem) como "erro
  explícito". Funciona e a mensagem nomeia o campo, mas o padrão do projeto
  preferiria `IllegalArgumentException`. Não é circular nem afrouxado; fica o
  registro.
- **O1:** `PaymentClassifier` trata `accruedCharges == null` como zero quando
  não há revisão aberta. É presunção silenciosa de "sem acréscimo" — aceitável
  como "não informado", mas a fiação deve passar o valor calculado ou zero
  explícito, nunca null por esquecimento.
- **O2:** No `PaymentClassifier`, a transferência (intenção do operador) tem
  precedência sobre conta PAGA/PRESCRITA: classificar como transferência uma
  conta já paga devolve `TRANSFER`, não `DUPLICATE_PAYMENT`. É defensável
  (intenção explícita + aprovação dupla), mas a fiação deve estar ciente de
  que a duplicidade não será sinalizada nesse caminho.
- **O3:** Conta prescrita paga **a menor** devolve `settledAmount = paidAmount`
  com efeito `CREDIT_RECOVERY` (conta dada como paga pelo valor recebido). O
  acceptance não detalha pagamento parcial de prescrita; registrado para a
  integração confirmar a semântica contábil com a ED-32.
- **O4:** `payments.bill_id`/`unit_id` não têm FK para `bills` (billing). O
  acceptance só exige FK para `fiscal_document_id`, então não é defeito contra
  a spec; fica o registro de assimetria para a integração decidir.
- **O5:** `return_files.status = REPROCESSADO_SEM_EFEITO` existe no CHECK mas
  um reprocessamento, por definição, não grava linha nova — o valor é inalcançável
  via `ReturnFileProcessor`. Cosmético, sem efeito.
- **Padrões do projeto — conformes:** JSON só via `Json` do core
  (`CollectionParameters` usa `Json.readTree`; nenhum `ObjectMapper`/Jackson
  solto/Gson no pacote — o import de `tools.jackson.databind.JsonNode` é o
  mesmo de `BillingParameters`); ausência de parâmetro = `MissingParameterException`
  sem padrão presumido; nenhum dado semeado na V6_2 (`INSERT` ausente, guarda
  testa); entidades sem `@Builder`, herdando `TenantAwareEntity` como no padrão;
  `uuid_generate_v4()`, `register_tenant_table` e `ADD CONSTRAINT uq_*` seguem
  as migrations irmãs; testes com valores calculados à mão e duplos independentes
  (FakeStore contador, DVs de CNPJ recalculados pelo revisor) — **nenhum teste
  circular encontrado** além das guardas textuais, que são o padrão estabelecido
  do projeto; imports/assinaturas conferidos contra o espelho do core
  (`DocumentNumber.isValidCnpj/normalize`, `MissingParameterException(key, detail)`,
  `Json.readTree`) — todos batem. Risco residual declarado: `JsonNode.decimalValue()/
  booleanValue()` (Jackson 3) — `booleanValue()` já é usado pelo `BillingParameters`
  entregue; `decimalValue()` só a CI confirma.

## Pendências (bloqueiam o fechamento em produção, não este pacote)

1. **Diff obrigatório do `ziipsan-collection` real** antes do push (pom, V6_1,
   `payments` preexistente, UNIQUE `(tenant_id, id)` de `fiscal_documents` da
   V4_5, nomes das constraints do D1) — o espelho do módulo não foi lido
   (HTTP 429 na execução; limitação honestamente registrada pelo executor e
   confirmada por esta revisão: não há espelho local do módulo).
2. **Fiação transacional:** adaptador JPA de `ReturnFileStore` (com tradução da
   violação do índice único de idempotência) e aplicador da
   `ClassificationDecision` (baixa no billing, débito/crédito a realizar da
   P2.5, aviso de duplicidade, emissão do `CREDIT_RECOVERY`, abertura da OS
   `PAYMENT_TRANSFER`, transição de status da classificação com a aprovação
   dupla). Sem ela, os itens 2/4/5/8/9 estão no nível de regra.
3. **Modos do split (item 12):** mapear `tax.profile.split_payment_mode` da
   P2.34 para o enum quando ela existir; até lá o item fica parcial por
   impossibilidade de fonte, não por omissão do pacote.
4. **Backfill operacional** de `collection_month` e cópia do histórico FEBRABAN
   150, se houver linhas preexistentes de `payments` com esses dados.
5. **Primeira rodada da CI da camada** para fechar os riscos de compilação que
   revisão estática não elimina (assinaturas Jackson 3 e JPA do módulo real).
