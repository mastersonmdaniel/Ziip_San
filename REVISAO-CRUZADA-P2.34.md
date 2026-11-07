# REVISÃO CRUZADA INDEPENDENTE — P2.34 · Cashback de CBS e IBS na fatura

Revisor: frente independente (não implementou a entrega). Base conferida: acceptance integral da P2.34 em `tarefas-P2.json`, mapa de migrations do plano v3.0 §3, e os pacotes locais P2.5 (`Money`, `BillingParameters`, V4_4, espelho V4_2/V4_14), P2.6 (`BillEntity`, `NfageEmissionService`) e P3.1 (`SplitPaymentReconciler`). **Sem push.** Sem Java/Maven nesta VM: nada foi compilado/executado — conferência por leitura, diff programático e recálculo independente (script Decimal).

## Veredito geral

**APROVADA COM 1 DEFEITO REAL CORRIGIDO** (incompatibilidade porta × migration que impediria a gravação) **e 2 reforços de teste**. Nenhuma data, alíquota ou percentual inventado; nenhum condicionado disfarçado de implementado. Restam apenas as pendências de integração já declaradas pela própria entrega (porta JPA, fiação do crédito, V4_14, ativação LC 227/2026, MOC).

---

## Item 1 — Percentuais e vigências — **OK** (com imprecisão de relatório, ver item 8)

- `CashbackVigencia`: `CBS_RETURN_PCT=100`, `IBS_RETURN_PCT=20`, `CBS_FROM=2027-01`, `IBS_FROM=2029-01`, dispositivos citados = os do acceptance (LC 214/2025 arts. 112–118; Decreto 12.955/2026 art. 511). Nenhuma outra data/percentual no código (grep conferido).
- Fronteira é inclusiva e correta: `!periodo.isBefore(FROM)` → 12/2026 sem nada, 01/2027 só CBS, 01/2029 ambos. Testes existentes cobriam 12/2026, 01/2027, 06/2028 e 01/2029.
- **Reforço aplicado:** o RELATÓRIO afirmava teste em "12/2028→só CBS", mas nenhum teste exercitava 12/2028 (havia 06/2028). Acrescentei `ibsBoundaryEve` (12/2028 → CBS 30,00 + IBS 0,00, flags conferidas) a `CashbackCalculatorTest`. A lógica já estava certa; a declaração passou a ser verdadeira.

## Item 2 — Devolução única por domicílio no período — **OK**

- V4_12: `uq_cashback_return_domicile_period UNIQUE (tenant_id, domicile_key_hash, assessment_month)` existe exatamente assim em `cashback_returns`, mais o teste de migration que o afirma por string.
- Defesa em profundidade real: serviço consulta `buscarDevolucao` antes de gravar e devolve `JA_DEVOLVIDO`.
- Teste `eligibleWithTwoUnitsSingleReturn` **não é frouxo**: duas unidades e duas contas distintas; afirma status CONCEDIDO→JA_DEVOLVIDO, **mesmo id** de devolução, `billId` da devolução = conta A (a primeira), `salvamentos == 1` e `devolucoes.size() == 1`.

## Item 3 — Recálculo dos valores — **OK**

Recalculado independentemente (Decimal, HALF_UP, mesma fórmula de `Money.percentOf` da P2.5: `amount × pct / 100`, uma divisão, um arredondamento, escala 2):

- Elegível 01/2029: CBS 30,00 ×100% = 30,00; IBS 10,00 ×20% = 2,00; **total 32,00** ✓ (código e testes batem; CBS a 100% via `percentOf` é exata).
- Arredondamento: 20% de 33,33 = 6,666 → **6,67** HALF_UP ✓.
- Relatório 02/2029: A 32,00 + B 16,67 = **48,67**; totalCbs 40,00, totalIbs 8,67 ✓. `CashbackCompetenceReport` soma as parcelas gravadas e deriva `totalGeral = totalCbs + totalIbs` — equivalente ao somatório dos totais porque o CHECK da V4_12 impõe `total = cbs + ibs` por linha.
- Observação (não é defeito): o fixture de `CashbackCompetenceReportTest` constrói devoluções já gravadas (tax = return), ou seja, testa só a agregação — correto para a classe, mas quem lê não deve tomá-lo como prova do cálculo de 20% (essa prova está no `CashbackCalculatorTest`).

## Item 4 — Lista da RFB (G2) e índice cego — **OK com ressalva declarada**

- `cashback_list_imports` com `source_origin`, `source_reference`, `imported_on`, `list_version`, `legal_basis` — proveniência NOT NULL, sem default ✓. Acessos em `cashback_list_accesses` (quem/quando/tipo/finalidade), tipos IMPORT/ELIGIBILITY_CHECK/REPORT/RETURN_GRANT.
- Ordem no `CashbackService.conceder`: consulta → **registra acesso** → demais passos. Logo o acesso é registrado nos caminhos **não-elegível** (provado em teste), **já-devolvido**, **sem-devolução-na-vigência** e **erro de ativação** (`PendingConfirmation` lança depois do registro). **Reforço aplicado:** o teste `pendingTaxEventActivationBlocksGrant` agora também afirma `acessos == 1`, tornando o G2-no-erro provado, não só deduzível.
- Caminhos sem registro são os em que **não há acesso à lista**: perfil desligado (retorna antes da consulta; teste afirma `acessos` vazio — correto, nada foi acessado) e comando inválido (validação prévia). Se `buscarEntrada` em si falhar (erro de banco), não há como registrar pela mesma porta — limitação inerente ao desenho de porta, igual ao padrão da P2.6; registro fica a cargo da implementação JPA transacional na integração.
- Domicílio **só por índice cego**: `domicile_key_hash VARCHAR(64)` em entries/accesses/returns; nenhum CPF/documento/nome em claro em nenhuma tabela da V4_12 (grep conferido — "documento" só aparece em comentário sobre a fonte); redação de auditoria OMIT nos três hashes, mesmo precedente da V4_2 (`family_code_hash`).

## Item 5 — `tax.profile` e não-duplicação de alíquotas — **OK**

- V4_12 registra `tax.profile` como `OBJECT`, classe **b**, aprovação **JUR**, `factory_default NULL` ✓. `TaxProfile` lê os três subcampos (`cashback_enabled`, `tax_event_date_rule`, `split_payment_mode`) via `Json` do core, no mesmo contrato de `BillingParameters.fromValues` da P2.5.
- Ausência de perfil = `MissingParameterException`; subcampo ausente/fora do catálogo = `IllegalArgumentException`. **Nenhum default silencioso** em nenhum caminho (o `cashback_enabled=false` é valor explícito do perfil, não default — e gera `NAO_HABILITADO`, não concessão).
- Catálogo de split idêntico ao da P3.1, conferido contra `SplitPaymentReconciler.SplitPaymentMode`: `SEGREGATED_AT_SETTLEMENT`, `SIMPLIFIED_SEGREGATION`, `NO_SEGREGATION` ✓.
- Não-duplicação: a V4_12 não cria nem altera tabela de alíquotas e não semeia valor; o calculador recebe CBS/IBS já resolvidos. O teste `taxRateTableNotDuplicated` é **real** (lê o SQL do classpath e nega CREATE de `tax_rates`/`fiscal_documents` e INSERTs de negócio) — raso por natureza (string match), mas a ausência foi confirmada também por leitura integral da migration nesta revisão.

## Item 6 — `tax_event_date` e a porta `PendingConfirmation` — **OK**; diff mínimo **confirmado**

- Diff programático `BillEntity` (base P2.6) × entrega: **um único acréscimo** — comentário + `@Column(name="tax_event_date") LocalDate taxEventDate`, inserido antes de `fiscal_document_id`. Coluna separada de `due_date`/`issued_on` também na V4_12 (`ALTER TABLE bills ADD COLUMN`). (A spec escreve `due_on`; a coluna real do schema é `due_date` — a entrega usa o nome real, corretamente.)
- Bypass: **não há** caminho na entrega que grave devolução sem passar pelo resolvedor — `salvarDevolucao` só é chamado depois de `taxEventDateResolver.resolver(...)`, e `PendingConfirmation` lança `IllegalStateException` com mensagem explícita (teste prova 0 salvamentos). O dublê de teste que devolve o fim do período está declarado como dublê no javadoc do teste. Ressalva de integração (não é defeito da entrega): `CashbackReturnRepository` é JPA genérico, como todos os repositórios do módulo — a integração não deve gravar `CashbackReturnEntity` por fora do `CashbackService`.

## Item 7 — Condicionados honestos — **OK**

- `NfageCashbackReflection`: nenhum nome de campo de leiaute inventado; payload é JSON interno via `Json.write` do core, com `condicionalidade = "CONDICIONAL AO MOC DEFINITIVO (P2.34)"` e `aplicavelAoLeiaute` derivado de `mocDefineCampos`; valida total = CBS+IBS e versão de leiaute obrigatória. Testes cobrem os dois estados do MOC.
- Autarquia: `CashbackOpenPoints.AUTARQUIA_CONTRIBUINTE_PENDENTE` é citado **apenas** como nota textual no relatório por competência (grep conferido: definição + `CashbackCompetenceReport`). Nenhum ramo, CHECK ou coluna depende dele.
- Efeito por competência: `efeitosPorCompetencia` + `cashback_tax_effects` (UNIQUE por tenant+competência) registram somente a agregação; **nenhuma simulação tarifária** (nada de tarifa/reequilíbrio calculado — P2.29 preservada).

## Item 8 — Migration, V4_14, JSON e assinaturas — **DEFEITO CORRIGIDO** + declarações conferidas

- **V4_12 é a reservada à P2.34**: confirmado no mapa do plano §3 (PLANO-v3.0 e PDCA R0–R9): "`V4_12` | billing | cashback de CBS e IBS | P2.34 | reservada, P2" ✓.
- **Observação V4_14 correta e declarada**: conferi a V4_14 no espelho da P2.5 — ela renomeia `bills` e a recria com `CREATE TABLE bills (...)` e `INSERT INTO bills (lista explícita)` **sem** `tax_event_date` (nem `fixed_part_value`/`fiscal_document_id`, mesmo problema já conhecido). Como V4_12 < V4_14 na ordem Flyway, em instalação nova o `ALTER` da V4_12 é anterior à recriação: sem carregar a coluna para a V4_14 (ou reaplicar depois), ela se perde. O aviso está no SQL da V4_12 e no relatório — declaração **verdadeira e necessária**; o fechamento é da integração.
- JSON só via `Json` do core: `TaxProfile` (`Json.readTree`) e `NfageCashbackReflection` (`Json.write`) — mesmos métodos usados por P2.5/P2.6 ✓. Nenhum outro parser na entrega.
- Assinaturas coerentes com P2.5/P2.6/P3.1: `Money.percentOf(amount, pct, HALF_UP)` idêntico ao da P2.5; entidades sem `@Builder` sobre `TenantAwareEntity`, repositórios Spring Data no padrão do módulo; porta sem implementação como as da P2.6.

### Defeito real corrigido nesta revisão (o único)

**Porta × migration incompatíveis — a devolução não poderia ser gravada.** `cashback_returns.list_import_id` e `list_entry_id` são `NOT NULL` com FK na V4_12 (e existem em `CashbackReturnEntity`), mas o record `CashbackStore.DevolucaoRegistrada` **não tinha esses campos** e o `CashbackService` não os passava — apesar de ter a `EntradaLista` (com `importId`/`entryId`) em mãos. A afirmação do javadoc da porta ("espelham as colunas da V4_12") era, nesse ponto, **falsa**. Correção aplicada (4 arquivos):

1. `CashbackStore.DevolucaoRegistrada`: + `listImportId`, `listEntryId` (com nota da correção no javadoc).
2. `CashbackService.conceder`: passa `entrada.importId()`/`entrada.entryId()` da entrada elegível ao salvar.
3. `CashbackServiceTest`: `LojaMemoria` propaga os campos; teste do elegível afirma que a devolução gravada carrega import/entry da entrada consultada.
4. `CashbackCompetenceReportTest`: fixture ajustada à nova assinatura.

Balanceamento sintático de todos os `.java` reverificado após as edições; total de testes: 26 → **27** (novo `ibsBoundaryEve`).

---

## Defeitos abertos

Nenhum defeito de código aberto nesta entrega. Declarações do RELATÓRIO conferidas: verdadeiras, com a única imprecisão do "12/2028" (item 1), sanada pelo reforço de teste — o relatório em si não foi alterado por esta revisão.

## O que a integração precisa fechar (já declarado pela entrega; confirmado)

1. Implementação JPA da porta `CashbackStore` sobre as 5 tabelas (com registro de acesso transacional junto à consulta).
2. Fiação do crédito: devolução `GRANTED` → linha de crédito da fatura (mecanismo de créditos da P2.5) e gravação de `bills.tax_event_date` a partir da devolução — sempre via `CashbackService`, nunca direto no repositório.
3. **V4_14 × V4_12**: carregar `tax_event_date` na recriação de `bills` da V4_14 (lista explícita de colunas) ou reaplicar o `ALTER` depois dela — mesmo tratamento devido às colunas da V4_4/V4_7.
4. Ativação do `tax_event_date`: conferir o texto da LC 227/2026 (art. 10, §3º) e implementar o resolvedor real; até lá, `PendingConfirmation` bloqueia qualquer concessão — comportamento esperado.
5. MOC definitivo da NFAg-e: mapear `NfageCashbackReflection` aos campos do leiaute quando existirem e virar `mocDefineCampos`.
6. Parecer tributário da autarquia: segue pendente e isolado; não bloqueia push nem CI.
7. CI total (`scripts/ci.sh`) no PC do dono, como nas demais tarefas.
