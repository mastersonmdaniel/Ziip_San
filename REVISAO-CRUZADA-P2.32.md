# Revisão cruzada independente da P2.32 — parecer do revisor

Revisor distinto do autor, 07/11/2026. Escopo: os 19 arquivos da entrega
contra o acceptance integral da P2.32 (tarefas-P2.json), o pacote da P2.31
(ScRegulatoryProfiles + R__regulatory_profile_catalog), o pacote da P2.5
(HolderNameDisplay, BenefitExpiryRule, BillingParameters, V4_4), o espelho
da P2.30 (TemplateBlock/TemplateEngine) e PARAMETROS-DO-TENANT.md.
Conferência estática (sem JDK nesta VM, como o autor declarou).
**Nenhum push foi feito.**

## Veredito geral

**APROVADA COM CORREÇÕES APLICADAS.** A estrutura é fiel à spec e nada de
conteúdo jurídico foi inventado nos pontos verificados. Foram achados
**1 defeito de dado real** (classes de parâmetro erradas, com o CHECK do
banco proibindo os valores certos), **3 defeitos de teste/prova** (asserção
sempre-verdadeira na contagem, nome de teste afirmando uma injeção que não
existia, faltante-por-perfil afirmado sem teste) e **2 defeitos menores**
(campo morto no contexto de emissão; `norm_key` 120 × 160). Todos
corrigidos nesta revisão e registrados abaixo. Restam apenas os pontos de
integração já declarados pelo autor, mais dois apontamentos novos.

---

## Item 1 — Campos por lei e por ERI × P2.31 — **OK** (com 1 observação)

Conferido artigo a artigo contra `ScRegulatoryProfiles` (P2.31):

| Perfil | P2.32 alega | P2.31 (fonte) | Confere |
|---|---|---|---|
| ARIS | encargos art. 108; religação arts. 160/161; corte art. 153 | fee.* art. 108; rec `arts. 160, 161 e 25, III`; cut `art. 153, §§1º e 5º` | ✔ subconjunto fiel, nada inventado |
| ARESC | encargos art. 104; religação arts. 87/88; corte art. 79 | fee art. 104; rec `arts. 87, 88 e 79, §§6º/7º`; cut `art. 79, §§…` | ✔ idem |
| AGIR | encargos art. 129; religação arts. 151/152; corte art. 143 | fee art. 129; rec `arts. 151, 152 e 143…`; cut `art. 143, §§…` | ✔ idem |
| CISAM-MO RN 01/2017 | só corte, art. 104 | cut confirmado art. 104; fee/rec = REQUIRES_TENANT_VALUE | ✔ coerente: sem artigo confirmado, sem campo |
| CISAM-MO RN 05/2026 | só corte, "artigo não discriminado" | cut 30 dias confirmado, artigo null no mapa | ✔ honestidade preservada na referência |
| AGR-Tubarão / CISAM-SUL | só base federal | ver observação | ✔ conservador, sem invenção |

Base federal confere com o acceptance (Lei 11.445 art. 39; CDC art. 42-A/
Lei 12.039/2009; Decreto 5.440/2005; Lei 12.007/2009). Decreto e Lei
12.007/2009 vêm sem número de artigo — o acceptance também não os dá; a
referência descritiva é honesta, não é artigo inventado.

**Observação (não é defeito):** a regra implícita da entrega é "campo de
perfil só entra com ato **e artigo** confirmados na P2.31". Por ela, a
AGR-Tubarão fica sem campo de religação embora a P2.31 confirme
`rec.sla_hours = 24h` (Res. 007/2013, sem artigo e sem discriminação
normal/urgência) — o mesmo tratamento dado à RN 05/2026. É omissão
conservadora e consistente, mas o RELATORIO diz "texto não confirmado"
para a AGR, o que é impreciso: o **valor** foi confirmado, falta o artigo.
Recomendo registrar essa regra no javadoc de `RequiredBillContent` numa
próxima passada; não bloqueia.

**Observação 2:** `holder.printed_name` entra na base comum com
`origin = FEDERAL`, mas sua fonte é a regra da P2.5, não lei federal.
Está declarado no javadoc; a modelagem é uma simplificação aceitável
(campo obrigatório para todos os perfis), não uma atribuição falsa —
a `sourceNorm` cita a P2.5 corretamente.

## Item 2 — Aprovação do `bill.invoice_template` — **OK, com defeito de teste corrigido**

A regra está certa: faltante recusa nomeando campo + norma + referência
(`InvoiceTemplateContentRule.contentProblems`); o bloco do CDC art. 42-A
presente mas fora do conjunto travado recusa ("fora do bloco travado");
perfil desconhecido é erro explícito; `lockedFieldKeys == null` é tratado.

**[DEFEITO DE TESTE — CORRIGIDO]** O RELATORIO afirmava "teste de conteúdo
por perfil (completo aprova nos 7 × faltante recusa)", mas o faltante só
era testado para **um** campo da ARIS (e o caso do bloco travado na ARESC).
Correção aplicada em `InvoiceTemplateContentRuleTest`: novo teste em laço
remove **cada** campo de ERI de **cada** perfil e exige recusa nomeando
campo e norma; novo teste de faltante federal (qualidade da água /
Decreto 5.440/2005). Com isso a afirmação do relatório passa a ser verdade.
As asserções existentes são reais (completude via `doesNotThrowAnyException`
por perfil com `.as(profile)`, recusa com `hasMessageContaining` de chave
e norma) — nenhuma frouxa encontrada no restante do arquivo.

## Item 3 — Motor de mensagens — **defeito de teste corrigido; prova OK**

- **Ordem travada no código: OK de fato.** O comparador é por grupo
  (`!legal`) e só depois por `order` — uma não-legal de ordem menor não
  passa uma legal. Não é convenção.
- **[DEFEITO DE TESTE — CORRIGIDO]** O teste chamava-se "legais antes das
  demais, mesmo com slot de ordem menor **injetado** no catálogo" — nenhuma
  injeção existia: `select()` lê o catálogo estático e nele os slots já
  nascem com ordem 110–130 > 10–40 das legais; o teste passaria mesmo com
  ordenação simples por `order`, sem trava nenhuma. Correção aplicada:
  extraído `LegalMessageEngine.orderForPrint(List)` (usado por `select`),
  novo teste com definições sintéticas (legal ordem 99 × não-legal ordem 1,
  nas duas ordens de entrada) provando a trava, e o teste antigo renomeado
  para o que ele de fato prova.
- **Prova de emissão: OK e completa.** `EmissionProof` registra mensagem
  (chave + norma + posição) × `billId` × `billVersionNo` ×
  `billTemplateVersionId` × `contentCatalogVersion` ×
  `messageCatalogVersion` × data — exatamente o que o acceptance pede
  ("qual mensagem saiu em qual versão da conta"), e o teste afirma todos
  os campos com valores distintos. A entidade e a tabela da V4_10 espelham
  todos esses campos.
- **Apontamento aberto (menor, integração):** nenhum componente desta
  entrega **persiste** a prova (conversão `EmissionProof` → entidades +
  `save` no repositório). Existem objeto, entidade, tabela e repositório;
  falta o gravador. Quem chama `emissionProof` (ciclo P2.5/P2.8) precisa
  gravá-la, ou cria-se um `EmissionProofRecorder` na integração. O
  RELATORIO diz "prova persistível" — é verdade no sentido estrito, mas
  convém não ler como "persistida".

## Item 4 — Tarifa social, piso de 3 meses — **cálculo OK; classe errada (ver defeito nº 1)**

Os cálculos à mão estão **certos** e o teste os prova de verdade:
`01/04/2026 + 3m = 01/07/2026` (não é depois da perda → cumpre);
`02/04/2026 + 3m = 02/07/2026` (depois → não cumpre); e o caso do
parâmetro 2 é uma prova real do piso — sem o `Math.max(…, 3)`,
`01/05 + 2m = 01/07` cumpriria e o teste esperaria `false`.
O disparo em janela (primeiro aviso ≤ emissão ≤ perda) também está
testado nos dois lados.

**[DEFEITO MENOR — CORRIGIDO]** `EmissionContext.socialLossNoticeMonths`
era um campo **morto**: declarado, exigido no construtor, nunca lido por
nenhum método (`fires` não chama `lossNoticeLeadTimeMet`). Correção
aditiva: nova sobrecarga `lossNoticeLeadTimeMet(EmissionContext)` que
consome o campo, com teste (cumpre em 01/04, não cumpre em 02/04,
vacuidade sem perda programada).

## Item 5 — RAS-28 — **OK, íntegro**

Verificado por busca textual em toda a entrega:
`taxes.approximate_total_disclosure` aparece **somente** como constante em
`PendingTaxDisclosure` (e, por referência à constante, na regra e no
teste). **Não** está em `RequiredBillContent`, **não** está em nenhuma das
duas R__, **não** está na V4_10 (o teste de consistência afirma as três
ausências). A regra **recusa** modelo que contenha a chave enquanto
`LEGAL_OPINION_CONFIRMED = false`, com teste dedicado. Nenhum texto,
alíquota ou fonte de alíquotas foi declarado — nenhum conteúdo de parecer
inventado. A pendência está isolada exatamente como a spec manda.

## Item 6 — Slots ED-88 / ED-25 / P3.5 — **OK**

Os três slots nascem `slotOnly = true`, `legal = false`, com
`activatingTask` declarada (a validação do record exige a tarefa em slot).
`fires()` para `SLOT_ACTIVATION` exige a chave no conjunto
`activatedSlotKeys` do contexto, cujo padrão é vazio — sem ativação
explícita do chamador, não emitem; o teste `inactiveSlotsDoNotEmit`
confirma conjunto vazio → seleção vazia (e também que nenhuma mensagem de
fábrica dispara por aproximação). Quando ativados, entram depois das
legais (grupo não-legal), provado no teste de ordem.
**Nota:** a ativação é uma afirmação do chamador — não há nesta entrega
(nem cabia nela) um registro de que a tarefa dona concluiu; a integração
deve garantir que só o fluxo da tarefa dona popula `activatedSlotKeys`.

## Item 7 — Migrations, consistência e assinaturas — **defeito de dado corrigido; demais OK**

- **V4_10: OK.** O mapa do plano v3.0 §3 (linha da tabela de migrations)
  reserva literalmente `V4_10` ao billing para "conteúdo mínimo da conta
  … e mensagens legais emitidas por conta, com prova de emissão — P2.32".
  Estava livre: nenhum outro pacote da produção usa V4_10 e o billing
  entregue ocupa V4_3/V4_4 (P2.5) etc. As duas R__ são exatamente a
  "migration repetível R__ do conteúdo mínimo" do mapa. Precedentes
  conferidos: `uuid_generate_v4()`, `register_tenant_table(...)` e leitura
  de migration via `getResourceAsStream("/db/migration/tenant/…")` são o
  padrão da P2.5.
- **[DEFEITO REAL Nº 1 — CORRIGIDO] Classes de parâmetro erradas, e o
  banco proibia as certas.** `LegalMessageDef.ParameterClass` era truncado
  em `{A, B}` "de PARAMETROS-DO-TENANT" — mas o ADR-0015 define classes
  **a–d**, e os parâmetros governantes reais são: `social.loss_notice_months`
  = **classe c** (PARAMETROS-DO-TENANT: "≥ 3 … só amplia | c") e
  `benefit.expiry_notice_days` = **classe d** (BillingParameters e catálogo
  da V4_4 da P2.5). O código gravava A para o aviso de perda e B para o de
  benefício — e o próprio javadoc de `LegalMessageCatalog` dizia "(classe
  d)" do benefício, contradição interna que denuncia o erro; o comentário
  do motor chamava o piso de "classe a". Para piorar, o CHECK da V4_10 só
  admitia `('A','B')`: corrigir o Java sem corrigir o banco quebraria a
  carga. **Correções aplicadas:** enum → `{A, B, C, D}`; social → `C`;
  benefício → `D`; R__ de mensagens com `C`/`D`; CHECK da V4_10 →
  `('A','B','C','D')`; comentários "classe a" → classe c; frase "classes
  a/b" do RELATORIO corrigida com nota. Permanecem `A` a declaração anual
  e o bloco de qualidade (deveres legais incondicionais, sem parâmetro —
  classe a = travado, correto) e `B` nos slots (placeholder: a classe
  definitiva é da tarefa que ativar). **Tensão a ciência do dono:** o
  acceptance da P2.32 traz o parêntese "(classes a e b)", que conflita com
  PARAMETROS-DO-TENANT e com a P2.5; prevaleceram as fontes primárias dos
  parâmetros. Se o parêntese era intencional noutro sentido, é decisão de
  plano, não desta revisão.
- **[DEFEITO DE TESTE — CORRIGIDO] O teste de consistência não conferia
  quase nada.** Em `RequiredContentCatalogConsistencyTest`:
  (a) o contador `rows` era incrementado e **nunca afirmado**; a
  "contagem" era afirmada pelo texto do comentário `"16 linhas"` —
  asserção sempre-verdadeira enquanto o comentário existisse;
  (b) campos só eram conferidos pela chave solta, sem par perfil×campo nem
  norma/artigo; (c) mensagens só pela chave — foi assim que a classe
  errada do item acima passou. **Correção aplicada:** contagem real de
  tuplas (16 e 7, verificadas contra os SQL), pares
  `(profile_key, field_key, norma, referência)` e `(NULL, campo, norma)`
  para os federais, e linha completa de mensagem (chave, norma, **classe**,
  disparo, ordem, flags). Contagens conferidas à mão nesta revisão:
  5 federais + 3+3+3+1+1+0+0 = 16 ✔; 7 mensagens ✔.
- **[DEFEITO MENOR — CORRIGIDO]** `BillMessageEmissionEntity.norm_key`
  tinha `length = 120`; a V4_10 define `VARCHAR(160)`. Corrigido para 160.
- **JSON via `Json` do core: OK (vacuo declarado com honestidade).**
  Nenhuma classe nova serializa JSON (busca por `Json`/`ObjectMapper`/
  `Gson` na entrega: zero ocorrências) — a autorrevisão declara exatamente
  isso. O único JSON no caminho é o de `HolderNameDisplay` (P2.5), que já
  usa `Json` do core.
- **Assinaturas × espelhos: OK.** `HolderNameDisplay.fromParameterValue/
  printedName` e `BenefitExpiryRule.noticeWindowOpen(date, validUntil,
  days)` chamados com as assinaturas exatas da P2.5 (o teste do nome com
  valor JSON entre aspas, `"USER_HOLDER_AND_OWNER"`, está correto contra
  a implementação real); `TemplateBlock(id, Kind, Origin, text)` e
  `locked()` idênticos ao espelho da P2.30; entidade estende
  `TenantAwareEntity`, repositório `JpaRepository<…, UUID>` com finder por
  nome derivado — padrão das entidades da P2.5. `ON CONFLICT DO NOTHING`
  das R__ é sustentado pelos índices únicos criados na V4_10 (a
  autorrevisão já tinha corrigido a duplicação; conferido).
- **Risco que subsiste (já declarado pelo autor, confirmado):** as R__
  usam `${tenant_id}` sem o `set_config('app.current_tenant', …)` que a
  R__ da P2.31 usa — primeiro ponto a olhar na CI, como o relatório diz.

## Item 8 — Fiação pendente com a P2.30 — **OK, declaração verdadeira**

Conferido no espelho real: `TemplateEngine.validationProblems` recusa
**qualquer** bloco `Origin.SYSTEM` (regra 3), sem exceção. A entrega
**não** finge resolver isso: `LegalTemplateBlocks` declara o ponto de
integração no javadoc ("a regra 3 precisa passar a aceitar bloco SYSTEM
cujo id esteja no conjunto devolvido por esta ponte para o tipo FATURA"),
e o RELATORIO o lista como risco 2, com o comportamento seguro descrito
corretamente (enquanto isso, a trava efetiva é a regra de conteúdo do
billing). A P2.30, por sua vez, reservou os blocos SYSTEM explicitamente
à P2.32 no javadoc de `TemplateBlock` — a ponte usa exatamente
`Kind.LOCKED + Origin.SYSTEM + id = chave do campo`, como reservado.
**Veredito: declaração correta e completa; nada a corrigir aqui.**

---

## Resumo das correções aplicadas nesta revisão (7 arquivos)

1. `LegalMessageDef.java` — enum `{A,B}` → `{A,B,C,D}` + javadoc das classes (ADR-0015).
2. `LegalMessageCatalog.java` — social `A → C`; benefício `B → D`; javadoc do social cita classe c.
3. `LegalMessageEngine.java` — comentário "classe a" → classe c; `orderForPrint` extraído; sobrecarga `lossNoticeLeadTimeMet(EmissionContext)` consumindo o campo morto.
4. `V4_10…sql` — CHECK de `parameter_class` admite `C`/`D`.
5. `R__legal_message_catalog.sql` — linhas do social (`C`) e do benefício (`D`).
6. `BillMessageEmissionEntity.java` — `norm_key` 120 → 160.
7. Testes — `LegalMessageEngineTest` (prova sintética da ordem travada; antecedência via contexto; teste antigo renomeado), `InvoiceTemplateContentRuleTest` (faltante de ERI por perfil em laço + faltante federal), `RequiredContentCatalogConsistencyTest` (contagem real, pares perfil×campo, classe/disparo/ordem/flags por mensagem). E a frase "classes a/b" do `RELATORIO-P2.32.md`, corrigida com nota.

Nenhuma asserção existente foi afrouxada; todos os testes novos afirmam
mais do que os que substituem/complementam. Balanço de chaves/parênteses
dos 16 `.java` conferido por script após as edições (único desvio: um `(`
dentro da string-marcador do contador de linhas — literal, inócuo).

## Defeitos abertos / o que a integração precisa fechar

1. **Fiação P2.30 (já declarada):** `TemplateEngine` aceitar blocos SYSTEM
   vindos de `LegalTemplateBlocks` para o tipo fatura.
2. **Gravador da prova de emissão (novo, item 3):** converter
   `EmissionProof` em `BillMessageEmissionEntity` e salvar via
   `BillMessageEmissionRepository` no fluxo emissor — hoje nada persiste.
3. **CI (já declarados pelo autor, confirmados):** R__ com `${tenant_id}`
   sem `set_config`; registrar `BillMessageEmissionEntity` nas guardas de
   consistência de migrations do billing, se a CI exigir; confirmar que
   `@Service` resolve no classpath do billing (o módulo usa
   `@Repository`/Spring Data, então o risco é baixo).
4. **Ciência do dono/plano:** o parêntese "(classes a e b)" do acceptance
   conflita com as classes reais dos parâmetros (c e d) — corrigido a
   favor de PARAMETROS-DO-TENANT/P2.5; se houver intenção diversa, é
   ajuste de redação do plano.
5. **Menor, sem bloqueio:** documentar em `RequiredBillContent` a regra
   "campo de perfil exige ato + artigo confirmados" (caso AGR/religação).
