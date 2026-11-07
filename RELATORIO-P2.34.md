# RELATÓRIO — P2.34 · Cashback de CBS e IBS na fatura

Repo: `mastersonmdaniel/Ziip_San`, branch `main` (HEAD inclui a P2.5). Entrega **local, sem push e sem escrita no GitHub**, em `~/workspace/ziipsan-producao/p2.34/entrega/` (caminhos do repo). Espelho/proveniência em `~/workspace/ziipsan-producao/p2.34/espelho/`.

## Limite de acesso (honesto)

- A primeira (e única) chamada ao GitHub MCP retornou **HTTP 429**; pelo fluxo obrigatório, **parei de chamar o GitHub** e trabalhei só com os pacotes locais: entrega da P2.5 (`BillingParameters`, `Money`, V4_4), espelho da V4_2 (lista oficial/índice cego/acesso), entrega da P2.6 (`NfageEmissionService`, padrão de condicionalidade ao MOC, `BillEntity` com `fiscalDocumentId`) e a entrega da P3.1 (catálogo de modos de split que aguardava esta tarefa). A **V4_5 (P2.4) não foi vista** — como nas entregas anteriores; por isso nenhum nome de tabela/coluna dela é afirmado: o código recebe os valores de CBS/IBS já resolvidos pelo chamador e a migration **não toca** na tabela de alíquotas.
- Sem Java/Maven nesta VM: **nada foi compilado nem executado**. A conferência foi leitura de padrões, balanceamento sintático de todos os arquivos, diff programático do `BillEntity` (acréscimo mínimo único) e recálculo independente (script) de todos os valores fixados como literais nos testes.
- **Revisão cruzada:** o fluxo pedia revisão por subagente via spawn, mas esta sessão é profundidade 2/2 (sem spawn). Fiz a revisão contra a spec item a item nesta própria frente (checklist no fim) e **recomendo ao agente pai rodar a revisão cruzada independente antes do push**, como nas demais tarefas.

## Arquivos criados (27)

Módulo `commercial/ziipsan-billing/`:

| Arquivo | O que é |
|---|---|
| `src/main/resources/db/migration/tenant/V4_12__cashback_cbs_ibs.sql` | **Migration reservada à P2.34 no mapa do plano (§3)** — confirmada na fonte: "`V4_12` \| billing \| cashback de CBS e IBS \| P2.34 \| reservada, P2" |
| `.../billing/parameters/TaxProfile.java` | Leitura tipada do `tax.profile` (Json do core; ausência = `MissingParameterException`) |
| `.../billing/cashback/CashbackVigencia.java` | Fatos normativos: 100% CBS / 20% IBS; CBS desde 01/2027; IBS desde 01/2029 |
| `.../billing/cashback/CashbackCalculator.java` | Cálculo puro por domicílio/período sobre valores resolvidos da P2.4 |
| `.../billing/cashback/CashbackService.java` | Concessão: perfil → lista (acesso registrado) → devolução única → tax_event_date → gravação |
| `.../billing/cashback/CashbackStore.java` | Porta de persistência (sem implementação, como as portas da P2.6) |
| `.../billing/cashback/TaxEventDateResolver.java` | Porta do `tax_event_date` + `PendingConfirmation` (recusa explícita até a conferência) |
| `.../billing/cashback/CashbackCompetenceReport.java` | Relatório por competência + efeito tributário p/ reequilíbrio |
| `.../billing/cashback/CashbackOpenPoints.java` | Pontos em aberto isolados (autarquia; ativação LC 227/2026) |
| `.../billing/domain/Cashback{ListImport,ListEntry,ListAccess,Return,TaxEffect}Entity.java` | Entidades das 5 tabelas da V4_12, sem `@Builder`, sobre `TenantAwareEntity` |
| `.../billing/repository/Cashback*Repository.java` (5) | Repositórios Spring Data no padrão do módulo |
| `.../billing/domain/BillEntity.java` | Arquivo existente com **acréscimo mínimo único** (diff conferido): campo `taxEventDate` (V4_12) sobre a base da entrega da P2.6 |
| Testes (billing) | `CashbackCalculatorTest` (7), `CashbackServiceTest` (6), `TaxProfileTest` (4), `CashbackCompetenceReportTest` (2), `CashbackMigrationConsistencyTest` (4) |

Módulo `commercial/ziipsan-fiscal/`:

| Arquivo | O que é |
|---|---|
| `.../fiscal/nfage/NfageCashbackReflection.java` | DTO/ponto de integração do reflexo na NFAg-e, **condicional ao MOC definitivo** |
| `.../fiscal/nfage/NfageCashbackReflectionTest.java` | 3 testes da condicionalidade e das validações |

## A V4_12 (o que a migration faz)

1. **`cashback_list_imports`** — importação da lista da RFB com **origem, data e versão** (`source_origin`, `source_reference`, `imported_on`, `list_version`, `legal_basis`), contagem e situação; proveniência sem default (NOT NULL).
2. **`cashback_list_entries`** — domicílio elegível por período de aferição, identificado **só por índice cego** (`domicile_key_hash`, HMAC-SHA-256, privacidade como na V4_2); `assessment_month` com CHECK de dia 1; FK à importação e à unidade localizada.
3. **`cashback_list_accesses`** — **cada acesso registrado** (quem, quando, tipo IMPORT/ELIGIBILITY_CHECK/REPORT/RETURN_GRANT, finalidade), o padrão G2 do aceite.
4. **`cashback_returns`** — a devolução como crédito na fatura, com a garantia central do aceite na constraint **`uq_cashback_return_domicile_period` UNIQUE (tenant_id, domicile_key_hash, assessment_month)**: uma única devolução por domicílio no período. CHECKs: total = CBS + IBS; devolução ≤ tributo; valores ≥ 0. `bill_id` sem FK composta (bills particionada — mesmo desenho da V4_7 para pagamentos).
5. **`bills.tax_event_date`** — coluna separada de `due_date`/`issued_on`, com COMMENT declarando a condicionalidade. **Aviso de integração (mesmo achado da P2.6/V4_7):** a V4_14 recria `bills` com lista explícita de colunas — `tax_event_date` precisa ser carregado para a recriação ou aplicado depois dela (declarado no SQL).
6. **`cashback_tax_effects`** — efeito tributário agregado por competência (único por tenant+competência), o registro para o pleito de reequilíbrio (arts. 373 a 377); a simulação é da P2.29.
7. **`tax.profile` no catálogo global** — `parameter_definitions` (OBJECT, **classe b**, aprovação **JUR**, sem padrão de fábrica) + `parameter_legal_limits` ALLOWED_VALUES com o catálogo de `tax_event_date_rule` e `split_payment_mode`.
8. Isolamento (`register_tenant_table` ×5) e redação de auditoria OMIT dos índices cegos (mesma razão da V4_2). **Nenhum dado de negócio, lista ou alíquota é semeado.**

## Cobertura do acceptance, item a item

1. **"Cashback como CRÉDITO NA FATURA para os domicílios da lista da RFB (Decreto 12.955/2026 art. 511; LC 214/2025 arts. 112–118), 100% CBS e 20% IBS"** — ✅ `CashbackService` concede a devolução ligada à conta do comando (a fiação que a transforma em linha de crédito da conta, pelo mecanismo de créditos da P2.5, é da integração — declarado no javadoc); percentuais em `CashbackVigencia`/`CashbackCalculator`, com dispositivos citados.
2. **"Cálculo por domicílio e por período, UMA ÚNICA devolução por domicílio no período, inclusive com mais de uma unidade (teste obrigatório)"** — ✅ chave do cálculo e da gravação é (domicílio, período), nunca a unidade; garantia dupla: serviço devolve a existente (`JA_DEVOLVIDO`) + UNIQUE no banco. Teste `eligibleWithTwoUnitsSingleReturn`: duas unidades/contas distintas → 1 salvamento, mesma devolução.
3. **"Vigências: CBS desde 01/2027, IBS desde 01/2029"** — ✅ constantes `CBS_FROM`/`IBS_FROM` + testes de fronteira dia a dia no mês (12/2026→0; 01/2027→só CBS; 12/2028→só CBS; 01/2029→ambos). Nenhuma outra data foi criada.
4. **"Lista importada com origem, data e versão; cada acesso registrado como no G2"** — ✅ tabelas `cashback_list_imports/entries/accesses`; o serviço registra o acesso em **toda** consulta (elegível ou não — provado no teste do não elegível); domicílio só por índice cego, como na lista da tarifa social (V4_2).
5. **"Reflexo na NFAg-e nos campos do leiaute vigente, CONDICIONAIS AO MOC DEFINITIVO"** — ⚠️ **CONDICIONADO (por desenho, como manda a spec):** `NfageCashbackReflection` é o ponto de integração/DTO: carrega os valores ligados à `versaoLeiaute`, declara `aplicavelAoLeiaute=false` e a condicionalidade no payload interno (JSON do core, **não** é XML nem campo de leiaute). **Nenhum campo de leiaute foi inventado.** Quando o MOC definitivo definir os campos, a integração mapeia o DTO e vira a chave — teste cobre os dois estados.
6. **"Relatório por competência (valor por domicílio e total)"** — ✅ `CashbackCompetenceReport.relatorio`: linhas por domicílio ordenadas + `totalCbs/totalIbs/totalGeral`; teste com somas à mão (40,00 + 8,67 = **48,67**).
7. **"`tax.profile` (classe b, aprovação JUR) com `cashback_enabled`, `tax_event_date_rule`, `split_payment_mode`; alíquotas na tabela com vigência da P2.4 (NÃO duplicar)"** — ✅ `TaxProfile` + registro na V4_12 (classe b, JUR, sem default). Alíquotas: **nenhuma tabela criada nem valor fixado** — o teste de migration afirma a ausência de `tax_rates` e o calculador recebe os valores de tributo como entrada resolvida. `split_payment_mode`: catálogo = os 3 modos que a **P3.1 já implementou** aguardando esta definição (`SEGREGATED_AT_SETTLEMENT`, `SIMPLIFIED_SEGREGATION`, `NO_SEGREGATION`) — alinhamento declarado no código, na migration e aqui; a conciliação continua da P3.1 (item 11).
8. **"`tax_event_date` separado de `due_on`/`issued_on`, pela regra da LC 227/2026 art. 10 §3º, texto conferido ANTES da ativação"** — ⚠️ **ATIVAÇÃO CONDICIONADA (declarada):** coluna própria na conta + campo na entidade + porta `TaxEventDateResolver` parametrizada por `tax.profile.tax_event_date_rule` (`CONTINUOUS_OPERATION`, o nome do dispositivo citado — **o texto da LC 227/2026 não foi inventado nem presumido**). A implementação entregue, `PendingConfirmation`, **recusa com erro explícito**; o serviço resolve a data **antes** de gravar, logo **sem a conferência nenhuma devolução é concedida** (teste `pendingTaxEventActivationBlocksGrant`: exceção, 0 salvamentos). O relatório por competência carrega a nota da pendência em toda emissão.
9. **"Efeito tributário por competência registrado para o pleito de reequilíbrio (LC 214/2025 arts. 373–377); simulação é da P2.29"** — ✅ `CashbackCompetenceReport.efeitosPorCompetencia` + tabela `cashback_tax_effects` (só o registro; nenhuma simulação aqui).
10. **"ITEM A CONFIRMAR POR PARECER TRIBUTÁRIO: condição de contribuinte IBS/CBS de prestador autarquia — não decidir"** — ✅ **ISOLADO E NÃO DECIDIDO:** constante `CashbackOpenPoints.AUTARQUIA_CONTRIBUINTE_PENDENTE`, citada no relatório por competência e declarada na V4_12; **nenhum ramo de código, CHECK ou coluna depende dela** — não bloqueia nenhum outro item.
11. **"Split payment fica na conciliação da P3.1 — só o parâmetro existe aqui"** — ✅ só o subcampo `split_payment_mode` (validado contra o catálogo da P3.1); nenhuma lógica de segregação/conciliação foi escrita nesta tarefa.
12. **Testes exigidos (elegível, não elegível, elegível com duas unidades)** — ✅ os três em `CashbackServiceTest`, mais: perfil desligado, período pré-vigência, bloqueio da ativação pendente, fronteiras/arredondamento do cálculo (20% de 33,33 = **6,67** HALF_UP, conferido por script), perfil (ausente/subcampo/modo), relatório/efeito e consistência da migration. **Total: 26 testes.**

## Casos calculados à mão (conferidos por script independente)

- 01/2029, CBS 30,00 / IBS 10,00 → 30,00 + 2,00 = **32,00** (caso central dos testes de serviço).
- 01/2027, CBS 50,00 / IBS 50,00 → **50,00 + 0,00** (IBS fora da vigência).
- 20% de 33,33 = 6,666 → **6,67** (HALF_UP, padrão `Money.percentOf` do módulo).
- Relatório 02/2029: A 32,00 + B 16,67 = **48,67** (CBS 40,00; IBS 8,67).

## Pendências e riscos para a CI

- **Implementação da porta `CashbackStore` (JPA)** é da integração, como as portas da P2.6; sem ela o serviço não roda contra o banco (as entidades/repositórios estão prontos).
- **Fiação do crédito na conta:** transformar a devolução `GRANTED` em linha de crédito da fatura (mecanismo da P2.5) e gravar `bills.tax_event_date` a partir da devolução são da integração p2b.
- **V4_14 × V4_12:** a recriação de `bills` na V4_14 precisa carregar `tax_event_date` (declarado no SQL e aqui) — mesmo risco já registrado para as colunas da V4_7.
- **Ativação do `tax_event_date`:** bloqueada de propósito até a conferência do texto da LC 227/2026 (art. 10, §3º) e a implementação do resolvedor real; com `PendingConfirmation`, qualquer concessão em ambiente integrado **falha explicitamente** — comportamento esperado, não defeito.
- **MOC definitivo da NFAg-e:** o reflexo do cashback só vira campo de leiaute quando o MOC o definir (item 5).
- **Parecer tributário (autarquia):** pendente e isolado (item 10); não impede push nem CI.
- Testes de migration leem a V4_12 do classpath de recursos do billing (padrão Maven do módulo); os testes do fiscal usam só `ziipsan-core` (dependência já existente no pom).
- A CI total (`scripts/ci.sh`) precisa rodar no PC do dono, como nas demais tarefas.

## Checklist da revisão contra a spec (auto-revisão; revisão cruzada independente recomendada ao pai)

- [x] Nenhuma asserção afrouxada; nenhum teste de outra tarefa alterado.
- [x] Nenhuma alíquota, data ou texto jurídico inventado além do citado na spec (100%/20%; 01/2027; 01/2029; dispositivos citados verbatim).
- [x] Ausência de dado = erro explícito (perfil, subcampos, valores de tributo, versão de leiaute, hash do domicílio).
- [x] JSON só pela classe `Json` do core (`TaxProfile`, `NfageCashbackReflection`).
- [x] Migration somente a reservada (V4_12); tabela de alíquotas da P2.4 não duplicada (com teste).
- [x] Condicionalidades declaradas no código, na migration e neste relatório (MOC; LC 227/2026; autarquia).
