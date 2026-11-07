# P2.31 — Perfis regulatórios das agências de SC: carga, simulação e teste

Execução local, 07/11/2026. Base: HEAD `0fbcd22` de `mastersonmdaniel/Ziip_San`
(conferido nas listagens via MCP). **Nenhum push foi feito** — o pacote aguarda
push pelo agente principal e a prova é a CI total no PC do dono. Nada compilou
nesta VM (sem JDK/Maven/PostgreSQL): toda a conferência foi estática, contra os
espelhos das P0.19/P0.20/P1.34/P2.5 e contra o mapa de ERIs lido do repo.

## Onde vive o quê (pergunta prévia do encargo)

- **`regulatory_profiles` (P0.19)**: migration de tenant **`V1_5`** do módulo
  **platform** (`platform/ziipsan-platform/.../db/migration/tenant/`), ao lado de
  `parameter_versions` (EXCLUDE de vigência, proponente ≠ aprovador) e
  `parameter_acceptances` (aceite formal com hash). O catálogo global de
  definições/limites é o **core `V3`** (`public.parameter_definitions`,
  `public.parameter_legal_limits`).
- **Serviço de parâmetros (P0.20/P2.5)**: limites puros em
  `shared/ziipsan-core` — `ParameterLimits` (um método por chave, RAS-16) e
  `ParameterVersionRule` (vigência/aprovação/aceite); serviço JDBC do console em
  `platform/.../console/TenantParameterService` (P1.6), com hash canônico de
  `PlatformConsoleRules.contentHash`. A P2.31 **reutiliza os três**, sem
  duplicar trava.
- **Catálogo de normas (P1.34)**: tabelas do core `V9_8`
  (`platform.cert_standard*`), carga repetível `R__norm_catalog.sql` gerada por
  script a partir de fonte única. A P2.31 segue o mesmo desenho (ver abaixo) e
  liga `reg.ana_nr_adoption` a esse catálogo por código/ato (DAD-28), sem FK
  entre schemas (precedente da V9_8).

## Decisões de migration (com evidência)

- **Série do módulo dono**: platform, tenant. O plano v3.0 §3 reserva para a
  P2.31 a **`V1_13`** (platform: "colunas de origem em `regulatory_profiles` e
  nos valores do perfil, simulação antes da ativação e troca na data de
  vigência do ato") e a **`R__regulatory_profile_catalog.sql`** (platform,
  repetível). Conferida a listagem real do repo via MCP: tenant do platform tem
  V1_1–V1_7, V1_9–V1_12, V1_16–V1_18 — **V1_13 livre**, sem renumeração.
- **Carga repetível, não versionada**: o conteúdo é catálogo de fábrica por
  upsert de chave estável, como a P1.34 (`R__norm_catalog`) e o catálogo GIS
  (`R__gis_factory_catalog`, tenant). A R__ é **gerada** por
  `scripts/regulatory/generate-regulatory-profile-catalog.py` a partir de
  `ScRegulatoryProfiles.java` (fonte única; determinismo verificado por
  regeneração + `diff`), com guarda de contagem no fim (7 perfis, 167 valores,
  7 divergências) que falha alto se divergir — precedente da P1.34.
- **Correção necessária encontrada (declarada)**: o EXCLUDE da V1_5 sobre
  `regulatory_profiles` era por (tenant, agência, vigência) e **impediria dois
  municípios da mesma agência no mesmo tenant** — exatamente o caso CASAN do
  acceptance. A V1_13 o substitui por um EXCLUDE que inclui
  `COALESCE(municipality_ibge, '')`. Sem essa correção, o item "perfil
  identificado por agência e município" é impossível no banco.
- A V1_13 também registra em `public.parameter_definitions` as **15 chaves**
  que os perfis usam e que ainda não estavam no catálogo global (precedente:
  V4_4 §7 da P2.5 registra definições a partir de migration de tenant, com
  `ON CONFLICT DO NOTHING`): `reg.regulator`, `reg.general_conditions_act`,
  `reg.ana_nr_adoption`, `reg.normative_facts`, `meter.reading_cycle_days`,
  `fee.correction_index`, `cut.forbidden_window`, `cut.max_debt_age_days`,
  `cut.min_notice_days`, `rec.sla_hours`, `rec.undue_cut_compensation`,
  `sac.response_days`, `sac.priority_min_age_years`,
  `sac.deadline_counting_rule`, `leak.max_discount_pct_water`. Sem elas, o
  `TenantParameterService` recusaria as versões da ativação ("chave sem
  definição no catálogo é recusada").

## Pacote (17 arquivos, caminhos do repo — 15 originais + 2 das correções pós-revisão)

Módulo `platform/ziipsan-platform`, pacote novo `br.com.ziipsan.platform.regulatory`:

| Arquivo | Papel |
|---|---|
| `.../migration/tenant/V1_13__regulatory_profile_origin.sql` | Colunas de origem/município, EXCLUDE corrigido, 4 tabelas novas (`regulatory_profile_catalog`, `..._values`, `..._divergences`, `..._activations`), `regulatory_activation_id` em `parameter_versions`, 15 definições |
| `.../migration/tenant/R__regulatory_profile_catalog.sql` | Carga gerada: 7 perfis, 167 valores, 7 divergências |
| `regulatory/RegulatoryProfileDef.java` | Modelo do perfil: valor com ato/artigo/vigência/base de contagem/marca de confirmação; divergência; adoção; fato normativo |
| `regulatory/ScRegulatoryProfiles.java` | Os 7 perfis (fonte única da carga) + resolução por data (`versionOn`) |
| `regulatory/RegulatoryLoadGuard.java` | Guarda de carga: limites P0.20/RAS-16 por chave + recusa estrutural de regra contrária a lei federal |
| `regulatory/RegulatorySimulation.java` | Simulação pré-ativação pura, sem persistência por construção |
| `regulatory/RegulatoryActivationRule.java` | Regras puras do envelope (planejamento, hash = `PlatformConsoleRules`, proponente ≠ aprovador) |
| `regulatory/RegulatoryProfileActivationService.java` | JDBC: propor → aprovar (outra pessoa) → aceitar (aceites + nasce o `regulatory_profiles` do município) |
| `scripts/regulatory/generate-regulatory-profile-catalog.py` | Gerador da R__ |
| `regulatory/AnaNormCatalogLink.java` | **(pós-revisão)** Mapa NR → `cert_standard.code` verificado da P1.34 e validação da ligação DAD-28 |
| 7 classes de teste | Ver "Testes" abaixo (a 7ª, `RegulatoryBillingContractTest`, é pós-revisão) |

## Cobertura do acceptance, item a item

1. **Perfis pré-carregados na `regulatory_profiles` da P0.19** — o desenho é:
   catálogo de fábrica (R__) → ativação pelo envelope → linha em
   `regulatory_profiles`. Perfis: **ARIS** (RN 19/2019 compilada, com RN
   20/2019, 38/2023, 40/2024 e 55/2026), **ARESC** (Res. 046/2016 consolidada,
   com Res. 139/2019 e 259/2023), **AGIR** (RN 001/2013), **AGR-Tubarão**
   (Res. 007/2013 e 040/2024), **CISAM Meio-Oeste** (RN 01/2017 e RN 05/2026,
   dois perfis por vigência) e **CISAM-SUL** (RN 03/2017). ✔
2. **Perfil por agência e município, com data de início** — `municipality_ibge`
   + EXCLUDE corrigido na V1_13; a ativação é por município (ou tenant
   inteiro, com município nulo); o caso CASAN (ARESC/ARIS/AGIR/CISAM-SUL) é o
   motivo declarado da correção. ✔
3. **Cada chave de G1, G5, G6 e G9 e das bifurcações com valor, artigo,
   vigência e marca de confirmação** — 22 chaves por perfil no modelo
   `ProfileValue` (24 com as derivadas `reg.ana_nr_adoption` e
   `reg.normative_facts`): G1 (`reg.*`, `meter.average_*`, `meter.after_cap_rule`,
   `meter.reading_cycle_days`, `bill.due_date_options`,
   `bill.min_days_delivery_to_due`), G5 (`fee.fine_pct`, `fee.interest_mode`,
   `fee.interest_daily_pct`, `fee.correction_index`), G6
   (`ret.billing_history_months`), G9 (`cut.min_notice_days`,
   `cut.max_debt_age_days`, `cut.forbidden_window`, `rec.sla_hours`,
   `sac.response_days`, `sac.priority_min_age_years`) e bifurcações
   (`sac.deadline_counting_rule`, `rec.undue_cut_compensation`,
   `leak.max_discount_pct_water`, `meter.after_cap_rule`). Teste
   `provenanceAndDeadlineBasisOnEveryValue` confere ato em todo valor
   confirmado e base de contagem em todos. ✔ (ressalva: prazos de ligação —
   ex. rurais da ARESC, 4/6 d.u. — não têm chave nesses grupos; ficam
   registrados na nota do perfil, declarados, não inventados como chave.)
4. **Valor não confirmado sem fábrica, exigindo o tenant** — marca
   `REQUIRES_TENANT_VALUE` com `value` NULL (CHECK na V1_13 impede valor com
   essa marca) e a ativação **recusa** proposta sem o valor do tenant,
   nomeando as chaves (`RegulatoryActivationRule.planVersions`). Lista
   completa na seção "Sem valor de fábrica" abaixo. ✔
5. **Prazos marcados corridos/úteis** — `deadline_basis` por valor:
   ARIS `CALENDAR_DAYS` (art. 171), ARESC `BUSINESS_DAYS` (art. 153), AGIR
   `NOT_DEFINED` (RN 001/2013 não define; a contagem vira valor do tenant).
   ✔ testado.
6. **Convergentes de SC carregados e testados** — aviso de corte 30 dias;
   6 datas de vencimento (CISAM-SUL: mínimo de 4); vencimento mínimo de
   5 dias úteis (10 para a categoria pública); ciclo de 27 a 33 dias; multa
   2%; juros até 0,033%/dia; correção IPCA; religação 48 h / urgência 4 h /
   indevido 4 h; média de 6 meses por até 3 ciclos. Presentes e testados
   para ARIS, ARESC e AGIR (as três com texto lido); no CISAM-SUL só o que
   o quadro de SC confirma (item 4 datas); nos demais perfis, ver a seção de
   sem-fábrica — o acceptance manda carregar os convergentes **e** manda
   não inventar o não confirmado; a segunda regra prevalece onde o mapa
   marca NC. ✔ com a ressalva declarada.
7. **Diferenças por perfil** — regra depois da média: ARIS/AGIR
   `BILL_MINIMUM` (consumo mínimo), ARESC `SETTLE_BALANCE_IN_3_INSTALLMENTS`
   (saldo parcelado em 3 vezes, art. 90 §4º) — os mesmos nomes de enum da
   P2.5 (`AfterCapRule`); teto de 120 dias **só ARIS** (art. 153 §2º), com
   ARESC/AGIR como `CONFIRMED_ABSENT` (ausência confirmada, sem versão na
   ativação); `rec.undue_cut_compensation`: ARIS religação 4 h sem ônus,
   AGIR maior entre 2× urgência e 20% da **última** fatura, ARESC idem sobre
   a **primeira fatura após a religação**; prioritário: **60** AGIR e
   CISAM-SUL, **65** ARESC (art. 127 §3º). ✔ testado por perfil.
8. **Regra contrária a lei federal recusada na carga e exibida como
   divergência** — tabela `regulatory_profile_divergences` + modelo:
   corte até 12 h de sexta (ARIS art. 158; AGIR art. 148; ARESC art. 85;
   CISAM-MO RN 01/2017 art. 107 e RN 05/2026 na manhã de sexta; CISAM-SUL
   pelo quadro §6) com resolução `FEDERAL_FLOOR_APPLIED` e valor efetivo
   `cut.forbidden_window = FEDERAL_FLOOR_14_015`; perda da tarifa social
   após dois cortes (AGIR, art. 127 §7º) com resolução `RULE_REJECTED` pela
   Lei 14.898/2024 — não gera valor em chave alguma. A guarda recusa
   qualquer tentativa de carregar essas regras como valor (marcadores +
   janela sem o piso), inclusive em valor informado pelo tenant na
   ativação. `divergencesOf` expõe a lista para a tela. ✔ testado.
9. **Simulação antes de ativar, sobre massa sintética, sem gravar** —
   `RegulatorySimulation.simulate`: diff de cada chave (vigente × perfil,
   com artigo), efeito na conta-tipo (multa+juros: R$ 4,66 sobre R$ 200 com
   10 dias de atraso nos valores convergentes), média (6 meses × vigente),
   disparo do teto de ciclos e fator de ajuste por dias. A classe não tem
   `DataSource`/conexão — teste estrutural garante a ausência de superfície
   de persistência. ✔ testado.
10. **Ativação pelo envelope da P0.19, proponente ≠ aprovador** —
    `RegulatoryProfileActivationService`: proposta grava ativação +
    `parameter_versions` PROPOSED ligadas por `regulatory_activation_id`;
    aprovação reusa `ParameterVersionRule.validateApproval` (outra pessoa,
    efeito só futuro) e os CHECKs da V1_5; aceite grava
    `parameter_acceptances` por versão (hash de `PlatformConsoleRules`, o
    mesmo do console) e só então cria o `regulatory_profiles`, encerrando o
    anterior da mesma agência **e município**. ✔ regras puras testadas; o
    JDBC segue o padrão do `TenantParameterService` e só a CI com banco o
    prova (risco declarado abaixo).
11. **Teste automatizado por perfil: média, teto de ciclos, ajuste por dias,
    vencimentos e período parcial da P2.5 (RAS-14, RAS-24, JOR-11) e limites
    da P0.20 (RAS-16)** — `ScRegulatoryProfilesTest` por perfil;
    `RegulatorySimulationTest` (média de 6 meses = 15 sobre a massa, teto
    dispara no 3º ciclo, fator 1,3333 para ciclo de 40 dias sobre
    referência de 30 — o mesmo fator da regra proporcional da P2.5 — e
    diffs de vencimento); `RegulatoryLoadGuardTest` nomeado por chave/limite
    (multa, juros, aviso, retenção, média, ciclos, regra posterior, janela,
    tarifa social, piso de concessionária). O período parcial em si é
    exercido sobre a faixa do perfil (27–33) via fator de dias; a classe
    autoritativa `PartialPeriodRule` é do billing e o platform não depende
    dele — dependência inversa não foi criada por esta tarefa. ✔
12. **Troca de perfil na vigência do ato, com relógio simulado** —
    `RegulatoryProfileSwitchTest`: `Clock.fixed` em 19/05/2027 resolve
    `CISAM_MO_RN01_2017`; em 20/05/2027, `CISAM_MO_RN05_2026` (art. 163 da
    RN 05/2026, conforme o acceptance; o mapa confirma publicação em
    05/10/2026, vigor a partir de 20/05/2027 e revogação da RN 01/2017). A
    troca muda a regra viva (contagem indefinida → dias úteis; adoção nasce
    com NR 11 e NR 13). ✔
13. **`reg.ana_nr_adoption` preenchida e ligada ao catálogo da P1.34
    (DAD-28)** — mapa NR → {status, atos} por perfil, dos atos do mapa §5 e
    anexos A/B/F (ex.: ARIS NR 11 parcial — RN 19/2019, 45/2025, 47/2025,
    55/2026; ARESC NR 3/5/8 pelas Res. 433/432/430 de 2026; CISAM-MO NR 11
    pela RN 05/2026 e NR 13 pela RN 04/2026; AGR-Tubarão NR 7/8/9). A
    ligação é por código de norma do catálogo (`cert_standard.code`) no
    momento da ativação, sem FK entre schemas (precedente V9_8/P1.34);
    status honestos: `NO_ACT_LOCATED` onde não há ato, e no CISAM-SUL
    `ATENDIDA_LISTA_ANA_2025`/`DESOBRIGADA_LISTA_ANA_2025` porque só há a
    lista positiva, sem ato individual localizado. No perfil RN 01/2017 do
    CISAM-MO a chave **não** é carregada (atos são de 2025/2026). ✔
14. **Verificação da NR 11 pela ANA a partir de 20/05/2027 como fato
    normativo** — valor `reg.normative_facts` em todos os 7 perfis:
    `{code: ANA_NR11_VERIFICATION_FROM, date: 2027-05-20, source: Res. ANA
    230/2024, art. 74, §3º}` (o texto do ato prevalece sobre a notícia da
    ANA que dizia 27/05/2027 — mapa §3, achado 2). ✔ testado em todos.
15. **P3.4 e P3.28 apenas consomem** — nada implementado nelas; as
    vedações estaduais (Lei SC 16.673/2015, art. 21 p.u.) constam da
    divergência da ARESC para a P3.4 (RAS-12), e `leak.max_discount_pct_water`
    (70% nas três ERIs lidas) está carregado para a P3.28. ✔ fronteira
    respeitada.
16. **Perfis fora de SC** — não feitos (ED-97, RAS-31). ✔

## Procedência dos valores (resumo por perfil)

Fonte única: **mapa de ERIs** (`docs/normas/partes/04-mapa-de-eris.md`),
§4 (quadro de bifurcações), §5 (adoção), §6 (perfil de SC) e anexos **A**
(ARIS, tabela de parâmetros com artigo por linha), **B** (AGIR §1, ARESC
§2, demais SC §3, adoção §5, camada federal/estadual §6) e **F** (matriz
ERI × NR e apêndice B do CISAM-MO/AGR-Tubarão), mais
**PARAMETROS-DO-TENANT** (grupos e classes) e os limites da **P0.20**.
Cada linha da R__ carrega `source_act` e `source_article` — a procedência
completa está na própria carga e em `ScRegulatoryProfiles.java`, não só
neste relatório. Exemplos-âncora: ARIS art. 108 (multa/juros/IPCA), art. 83
(média), art. 84 (ciclo e vencimentos), art. 98 §1º (5/10 dias úteis),
art. 153 (aviso 30 e teto 120), art. 158 (divergência), art. 171
(corridos); ARESC art. 104, art. 90 (média e parcelamento), art. 100 §1º,
art. 103, art. 79 (aviso; ausência do teto), art. 85 (divergência),
art. 127 §3º (65 anos), art. 153 (úteis); AGIR art. 129, art. 102, art. 103,
art. 118 p.u., art. 128, art. 143 (aviso e compensação), art. 148 e
art. 127 §7º (divergências); CISAM-MO RN 01/2017 art. 104 (aviso) e
art. 107 (divergência).

## Sem valor de fábrica (e por quê)

- **AGR-Tubarão — 17 chaves** (`fee.*` ×4, `meter.*` ×4,
  `bill.due_date_options`, `bill.min_days_delivery_to_due`,
  `cut.min_notice_days`, `cut.max_debt_age_days`,
  `rec.undue_cut_compensation`, `sac.*` ×3, `leak.*`, contagem):
  condições gerais comerciais **não localizadas** (vivem no contrato
  038/2012 e no regulamento da concessionária; mapa, pendência 25).
  Confirmados e carregados: `rec.sla_hours` parcial (religação 24 h,
  Res. 007/2013), adoção NR 7/8/9, pisos federais. A data exata da
  Res. 007/2013 também não consta do mapa: `valid_from` do perfil é nulo
  e a vigência é definida na ativação (declarado na nota do perfil).
- **CISAM-SUL — 16 chaves**: texto da RN 03/2017 **só digitalizado**, com
  reconhecimento imperfeito (mapa, pendência 24). Carregados só os dois
  valores que o quadro de SC do mapa (§6) confirma para o CISAM-SUL —
  mínimo de **4** datas e prioritário aos **60** — ambos com artigo
  declarado como não localizado. Juros/multa remetem à legislação
  municipal e ao contrato (§6). A faixa de religação "24 h a 120 h" (§4.1)
  não discrimina normal/urgência/indevida: fica na nota de
  `rec.sla_hours`, que exige o valor do tenant.
- **CISAM Meio-Oeste (dois perfis) — encargos e medição**: RN 01/2017 e
  RN 05/2026 remetem juros, multa e correção **à legislação municipal, sem
  percentuais** (confirmada a remissão, não o valor); média, ciclos,
  vencimentos e vazamento não constam dos trechos lidos no mapa.
  Carregados: aviso de 30 dias (art. 104 na RN 01/2017; confirmado sem
  artigo discriminado na RN 05/2026), contagem em dias úteis (RN 05/2026),
  religação 12/24/120 h como conjunto confirmado **sem mapear**
  normal/urgência/indevida (o mapa não discrimina; o tenant declara o
  mapeamento na ativação), adoção completa no perfil de 2026.
- **Pontuais**: idade do prioritário da **ARIS** (o art. 24 prevê o
  atendimento, sem idade nos trechos lidos); contagem da **AGIR**
  (`NOT_DEFINED`); artigos não discriminados no mapa para prioritário da
  AGIR/CISAM-SUL e religação do CISAM-MO — valor carregado do quadro §6
  com a ressalva escrita na nota da chave, nunca com artigo inventado.

## Testes (6 classes, pacote `platform.regulatory`)

- `ScRegulatoryProfilesTest` (10 testes): um bloco por perfil — convergentes,
  diferenças, divergências, adoção, fato NR 11, procedência e bases.
- `RegulatoryLoadGuardTest` (11 testes): recusas por chave/limite (RAS-16)
  e as duas recusas estruturais (Lei 14.015/2020; Lei 14.898/2024).
- `RegulatorySimulationTest` (6 testes): conta-tipo, média/teto, fator de
  dias, diffs de vencimento, fallback da AGR e não-persistência.
- `RegulatoryActivationRuleTest` (7 testes): envelope completo, separação
  proponente/aprovador, hash igual ao do console, aceite com hash.
- `RegulatoryProfileSwitchTest` (4 testes): troca do CISAM-MO com relógio
  simulado em 19/20/05/2027 e fronteiras de vigência.
- `RegulatoryCatalogConsistencyTest` (3 testes): R__ × catálogo Java
  (conteúdo, NULLs onde não há fábrica, contagens da guarda), ausência das
  regras recusadas como valor e forma da V1_13.

Nenhuma asserção de teste existente foi tocada (pacote novo); JSON só via
`Json` do core nas classes novas.

## Riscos de CI (honestos)

1. **Sem compilação local**: sintaxe conferida estaticamente (balanço,
   imports contra os espelhos, assinaturas de `ParameterLimits`,
   `ParameterVersionRule`, `PlatformConsoleRules.contentHash`,
   `ApiException` e `Json` copiados dos arquivos reais). Erro de API fora
   do espelho só aparece no `mvn` da CI.
2. **JDBC sem banco aqui**: `RegulatoryProfileActivationService` segue o
   padrão do `TenantParameterService`, mas inserts/updates (inclusive o
   `valid_to = ?::date - 1` do encerramento e o insert em
   `regulatory_profiles` com `?::jsonb` nulo) só são provados com
   PostgreSQL na CI do dono. O teste `enablesRowLevelSecurityOnEveryTable`
   do platform deve cobrir as 4 tabelas novas (registradas via
   `register_tenant_table`, como a V1_5 manda).
3. **EXCLUDE de `parameter_versions`**: propor a ativação com vigência que
   sobreponha versão existente da mesma chave devolve 409 (semântica atual
   da V1_5, espelhada) — o fluxo de tela precisa encerrar a vigência
   anterior antes, como já faz para parâmetros avulsos.
4. **`R__` e o placeholder `${tenant_id}`**: segue o precedente do GIS
   (`R__gis_factory_catalog.sql`); se o migrador do platform não substituir
   placeholders nas repetíveis do tenant, a carga falha na CI — é o
   primeiro ponto a olhar se a rodada quebrar na P2.31.
5. **Guarda de contagem da R__**: se a curadoria futura alterar
   `ScRegulatoryProfiles.java` sem regenerar a R__ (ou vice-versa), o
   `RegulatoryCatalogConsistencyTest` e a guarda SQL falham — comportamento
   desejado, mas exige regenerar pelo script no mesmo commit.
6. **Jackson 3 (`tools.jackson`)**: `JsonNode.toString()` é usado como
   forma canônica nos testes de consistência; se a serialização de
   decimais mudar de escala (`2.00` → `2`), o teste de consistência acusa
   antes da carga — conferir na primeira rodada.

## Correções pós-revisão (07/11/2026)

A revisão cruzada (`REVISAO-P2.31.md`) aprovou com ressalvas e ordenou 8
correções (1 GRAVE, 3 MÉDIOS, 5 BAIXOS — a contagem da revisão). Todas
foram aplicadas sobre a fonte (a V1_13 e a R__ ainda NÃO tinham sido
aplicadas/pushadas; a R__ foi REGENERADA pelo script, com determinismo
reconferido por dupla geração + `diff` byte a byte: saída idêntica,
7 perfis, 167 valores, 7 divergências). Nenhum valor mudou de
procedência: formato mudou, artigo não — cada valor continua com o ato e
o artigo que a revisão já conferiu. Nenhuma asserção foi afrouxada: os
41 testes originais estão preservados (3 métodos tiveram asserções
atualizadas onde elas codificavam o formato antigo recusado — ver item
1 — trocadas por asserções do novo contrato, sem perda de cobertura) e
9 testes novos somam (50 no total).

1. **[GRAVE] Formato das chaves de vencimento × consumidor da P2.5.**
   - `bill.min_days_delivery_to_due` (ARIS/ARESC/AGIR): de
     `{"general":5,"publicCategory":10,"terminationOnRequest":1,"unit":"BUSINESS_DAYS"}`
     para **`{"default":5,"PUBLIC":10}`**, o formato exato de
     `BillingParameters.minDaysDeliveryToDue` (mapa por categoria com
     fallback `"default"`; sem ele a leitura lançava
     `MissingParameterException`). `general→default`; `PUBLIC` é a chave
     da categoria Pública (o billing trata as chaves de categoria como
     vocabulário opaco do chamador — os testes da P2.5 usam `RES`/`COM`
     no mesmo estilo); a unidade saiu do objeto e vive no
     `deadline_basis` (`BUSINESS_DAYS`); o 1 dia útil em desligamento a
     pedido (ARIS art. 98 §1º; ARESC art. 100 §1º; AGIR art. 118 p.u. —
     reconferido no mapa, linhas dos anexos A e B §1/§2) não é categoria
     de consumo e ficou registrado na nota da chave, com o artigo.
     Arquivos: `ScRegulatoryProfiles.java` + R__ regenerada.
   - `bill.due_date_options`: a norma só confirma o NÚMERO mínimo de
     datas (6; CISAM-SUL 4) — os dias concretos são escolha do tenant e
     nenhuma lista foi inventada. Desenho corrigido: o
     `{"minCount":N}` do perfil é agora, estruturalmente, **requisito de
     catálogo, nunca valor de versão** (`RegulatoryLoadGuard.TENANT_COMPLETED_KEYS`);
     `planVersions` exige a lista concreta do tenant na ativação,
     valida-a (array de inteiros 1..31 distintos, via
     `ParameterLimits` do core, e contagem ≥ mínimo confirmado) e planeja
     ESSA lista como versão — `LIST_INT` legível pelo billing em qualquer
     estado válido. Arquivos: `RegulatoryLoadGuard.java` (novo
     `dueDateDays`, ramo de array em `assertValueLoadable` — que também
     corrige a recusa indevida de lista válida nas chaves
     `REQUIRES_TENANT_VALUE` desta chave), `RegulatoryActivationRule.java`,
     notas da chave em `ScRegulatoryProfiles.java` + R__ regenerada.
   - **Auditoria completa do catálogo** (não só as duas chaves): as 24
     chaves × 7 perfis foram conferidas contra o tipo/formato de cada
     definição (core V3, V4_4 §7 e V1_13). Fora as duas do GRAVE e o
     `reg.normative_facts` (item 4), todos os demais formatos já casavam
     com os consumidores (INT/PERCENT numéricos, ENUM textuais idênticos
     ao `AfterCapRule`, OBJECTs como objetos). A auditoria virou teste
     permanente (abaixo).
   - **Teste de contrato novo** (`RegulatoryBillingContractTest`, 8
     testes): interpreta os valores de fábrica e TODAS as versões
     planejadas dos 7 perfis com uma **réplica fiel** de
     `BillingParameters.dueDateOptions`/`minDaysDeliveryToDue`. O
     platform não pode reusar a classe real — o pom do platform depende
     só de `ziipsan-core`/`ziipsan-security` (conferido no repo);
     depender de `ziipsan-billing` inverteria a camada. A réplica reusa
     as peças comuns do core (`Json`, `ParameterLimits`,
     `MissingParameterException`) e um teste prova que ela recusa os
     formatos antigos exatamente como o original (réplica não-vacua).
2. **[MÉDIO] DAD-28 — ligação implementada e VERIFICADA.** A fonte real
   foi encontrada no repo (a R__ de 1,47 MB não foi necessária): o
   gerador da P1.34 (`scripts/normas/generate-norm-catalog-load.py`,
   lido via MCP) define `code = catalog_key = id` slug do
   `catalogo-normas.json`, e o arquivo de catálogo da área 02 (ANA),
   gerado da mesma fonte (`docs/normas/catalogo/02-agencia-nacional-de-aguas-e-saneamento-basico-ana-.md`,
   lido via MCP no HEAD `0fbcd22`), lista os IDs um a um — ex.: NR 11 =
   `ana-nr-11-res-230-2024` (Res. ANA 230/2024), NR 13 =
   `ana-nr-13-res-271-2025`, NR 3 = `ana-nr-3-res-161-2023`, NR 1 =
   `ana-nr-1-res-79-2021`. Implementação: nova classe
   `AnaNormCatalogLink` com o mapa NR → code das 16 NR; cada entrada do
   JSON de `reg.ana_nr_adoption` ganha o campo `catalogCode`
   (`RegulatoryProfileDef` + espelho no gerador Python); e
   `RegulatoryProfileActivationService.accept` valida a ligação em
   código — mapa completo (`validateAdoptionLinkage`) e existência de
   cada código adotado em `platform.cert_standard` (schema fixo da core
   V9_8; precedente `CertificationService`), recusando o aceite com os
   códigos faltantes nomeados. Os ATOS de adoção não foram tocados
   (a revisão confirmou que já conferiam com o §5 do mapa). Resíduo
   declarado: a existência em `cert_standard` só é provada com banco na
   CI (a carga da P1.34 precisa ter rodado naquele banco — se não rodou,
   o aceite recusa nomeando o código, que é o comportamento desenhado);
   o espelho local `R__norm_catalog-head.txt` (0 bytes) é arquivo do
   espelho desta VM, não do repo, e não foi alterado.
3. **[MÉDIO] Valor do tenant para chave CONFIRMED/CONFIRMED_ABSENT.**
   `planVersions` agora RECUSA explicitamente, nomeando a chave e o
   motivo (o valor — ou a ausência — vem do perfil; divergir é pela
   proposta avulsa da P0.19). Antes ignorava em silêncio. A única exceção
   é o completamento desenhado de `bill.due_date_options` (item 1), que
   não é sobreposição de valor confirmado: é o fornecimento dos dias que
   o perfil, por desenho, não tem. Testes novos cobrem as duas recusas
   (inclusive valor igual ao de fábrica) em
   `RegulatoryBillingContractTest`.
4. **[MÉDIO] `reg.normative_facts`: tipo × formato.** A definição na
   V1_13 passou de `OBJECT` para **`TABLE`** — o vocabulário correto do
   catálogo para array de objetos (precedente: `bill.auto_review_value_bands`,
   TABLE na V4_4), com a descrição registrando a correção. O valor (array
   de `{code, date, source}`) não mudou. Arquivo:
   `V1_13__regulatory_profile_origin.sql` + asserção nova em
   `RegulatoryCatalogConsistencyTest`.
5. **[MÉDIO] Simulação — limitação declarada, sem dependência invertida.**
   Avaliadas as dependências: reusar `PartialPeriodRule`/`BillingParameters`
   exigiria platform → billing, que a arquitetura não admite (fundação ×
   comercial; a regra pura vive no billing e não há módulo comum abaixo
   dos dois sem mover a P2.5). Fica a reimplementação, agora rotulada
   sem margem de dúvida: javadoc da classe declara ESTIMATIVA e as três
   divergências concretas (primeira/final × regular, normalização do
   consumo fora da faixa, `fee.interest_mode`/correção ignorados), e o
   resultado carrega `notice` = `RegulatorySimulation.ESTIMATE_NOTICE`
   para a tela exibir. Arquivo: `RegulatorySimulation.java` + teste novo
   `resultIsLabeledAsEstimate`. A simulação também passou a listar
   `bill.due_date_options` entre as pendências do tenant nos perfis em
   que ela é completada na ativação (a asserção de pendências da ARIS em
   `RegulatorySimulationTest` foi atualizada para as duas pendências
   reais: vencimentos e idade prioritária).
6. **[BAIXO] Contagem do CISAM-SUL.** Corrigida neste relatório: são
   **16** chaves sem fábrica (recontadas no Java e na R__), não 15.
7. **[BAIXO] Teste de consistência.** O segundo loop de
   `versionedMigrationShape` (que só provava chave não-branca) virou
   asserção real: toda chave de todo perfil ∈ V3 ∪ V4_4 ∪ V1_13, como
   conjuntos fixos comentados com a fonte de cada conjunto (24 chaves).
   Arquivo: `RegulatoryCatalogConsistencyTest.java`.
8. **[BAIXO] Bases de contagem inferidas (ARESC).**
   `meter.reading_cycle_days`: `BUSINESS_DAYS` → `CALENDAR_DAYS` (a faixa
   27–33 é duração do ciclo, não prazo contado pelo art. 153), com nota.
   `sac.response_days`: `BUSINESS_DAYS` → `NOT_DEFINED`, porque o objeto
   tem bases mistas nomeadas nos próprios campos (`phoneBusinessDays` =
   10 úteis; `writtenDays` = 30 dias que a fonte não marca como úteis) —
   nota da chave explica. Arquivo: `ScRegulatoryProfiles.java` + R__
   regenerada.

**Arquivos alterados nas correções:** `ScRegulatoryProfiles.java`,
`RegulatoryProfileDef.java`, `RegulatoryLoadGuard.java`,
`RegulatoryActivationRule.java`, `RegulatorySimulation.java`,
`RegulatoryProfileActivationService.java`, **novo**
`AnaNormCatalogLink.java`, `V1_13__regulatory_profile_origin.sql`,
`R__regulatory_profile_catalog.sql` (regenerada),
`generate-regulatory-profile-catalog.py` (mapa NR→code espelhado),
testes `ScRegulatoryProfilesTest`, `RegulatoryActivationRuleTest`,
`RegulatorySimulationTest`, `RegulatoryCatalogConsistencyTest` e
**novo** `RegulatoryBillingContractTest.java`, e este relatório.

**Risco novo declarado (soma-se à lista acima):** o `accept` agora
consulta `platform.cert_standard` — num banco onde a carga repetível da
P1.34 ainda não rodou, o aceite de perfil com adoção é recusado
nomeando os códigos (comportamento desenhado do DAD-28, mas é o
primeiro ponto a olhar se a CI quebrar no aceite). A compilação segue
sem prova local (sem JDK nesta VM): as APIs novas usam só
`Json`/`ParameterLimits`/`MissingParameterException` do core e JDBC no
padrão já conferido do `CertificationService`/`TenantParameterService`.
