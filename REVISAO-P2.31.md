# REVISÃO CRUZADA — P2.31 (Perfis regulatórios das agências de SC)

Revisor independente, 07/11/2026. Somente leitura (GitHub via MCP + espelhos
locais) e este relatório. **Nenhum push, nenhuma correção de código.**
Método: leitura integral dos 16 arquivos da entrega; confronto valor a valor
(amostragem completa das três ERIs lidas e total dos perfis NC) com o mapa de
ERIs (`docs/normas/partes/04`, §4, §5, §6, anexos A, B e apêndice B do anexo F),
`PARAMETROS-DO-TENANT.md`, V3 do core, V1_5 real, V4_4 da P2.5 (já pushada),
`BillingParameters`/`PartialPeriodRule`/`AfterCapRule` da P2.5,
`ParameterLimits`/`ParameterVersionRule`/`Json` do core,
`TenantParameterService` e, via MCP na main (HEAD `0fbcd22`): listagem real de
migrations do platform, `PlatformConsoleRules` real e o precedente GIS.
O gerador Python foi executado nesta VM (ver item 3). **Não houve HTTP 429.**

**VEREDITO GERAL: APROVADO COM RESSALVAS — NÃO SUBIR sem corrigir o problema
GRAVE nº 1** (formato de duas chaves do billing). A procedência dos valores —
o coração da tarefa — está correta: nenhum valor inventado, nenhum artigo
errado encontrado na amostragem, e os "sem valor de fábrica" são genuínos.

Contagem de problemas: **1 GRAVE, 3 MÉDIOS, 5 BAIXOS.**

---

## 1. Procedência dos valores — **OK** (com 1 ressalva BAIXA)

Amostragem conferida contra os anexos, artigo por artigo:

- **ARIS** (anexo A, §3): multa/juros/IPCA art. 108; média art. 83; ciclo e 6
  datas art. 84 e §4º; 5/10 dias úteis art. 98 §1º; aviso 30 e teto 120 dias
  art. 153 §§1º/5º e §2º; divergência art. 158; corridos art. 171; religação
  arts. 160/161/25 III; vazamento 70% art. 89; resposta 15 dias art. 23 —
  **todos batem**, inclusive os §§.
- **ARESC** (anexo B §2): encargos art. 104; média e saldo parcelado em 3×
  art. 90 §4º; ciclo art. 91; datas art. 103; antecedência art. 100 §1º; aviso
  art. 79 (e ausência do teto de 120 dias como `CONFIRMED_ABSENT`, com lastro
  no anexo B §7 item 5); divergência art. 85; prioritário 65 art. 127 §3º;
  úteis art. 153; religação arts. 87/88/79 §§6º-7º com compensação sobre a
  **primeira** fatura; vazamento art. 97 — **todos batem**.
- **AGIR** (anexo B §1): encargos art. 129; média art. 102; ciclo art. 103;
  datas art. 128; antecedência art. 118 p.u.; aviso e compensação (sobre a
  **última** fatura) art. 143; divergências arts. 148 e 127 §7º; ausência do
  teto com o art. 47 §1º IV citado corretamente como condicionante de
  ligação/religação — **todos batem**. Prioritário 60 sem artigo discriminado,
  com a ressalva escrita na nota da chave (quadro §6/anexo B §7 item 13) —
  honestidade correta, não é artigo inventado.
- **Convergentes de SC** (§6 item 3) presentes e testados para ARIS/ARESC/AGIR:
  30 dias, 6 datas, 5/10 d.u., 27–33, 2%, 0,033%, IPCA, 48h/4h/4h, média 6×3.
  Diferenças por perfil (§6 item 4) todas corretas, inclusive
  `rec.undue_cut_compensation` com as três fórmulas distintas.
- **Erro inverso (fonte tinha, frente não carregou): NÃO encontrado.**
  - CISAM-MO RN 01/2017: a fonte (apêndice B, anexo F) só confirma aviso 30
    (art. 104), corte (art. 107), ligação 10/15 d.u., resposta 15 d.u. e
    encargos remetidos à lei municipal — a carga confirma exatamente isso
    (ligação sem chave nos grupos, declarado em nota) e o resto é
    `REQUIRES_TENANT_VALUE`. Religação 12/24/120h é da RN 05/2026 na fonte, e
    a frente a pôs **só** no perfil de 2026 — correto, sem retroagir.
  - AGR-Tubarão: **17** chaves `REQUIRES_TENANT_VALUE` conferidas uma a uma —
    a fonte confirma só ligação 5 d.u. (sem chave; em nota) e reparo/religação
    24h (carregado em `rec.sla_hours`), o resto é NC expresso (apêndice B:
    "aviso, dias vedados, juros, multa e correção: não confirmados").
  - CISAM-SUL: **16** chaves sem fábrica (ver BAIXO nº 4: o relatório diz 15);
    carregados só os dois valores do quadro §6 (mínimo 4 datas; prioritário
    60), ambos sem artigo — declarado. Juros/multa remetem à lei municipal e
    ao contrato no §6 da camada — coerente com o `REQUIRES`.
- Ressalva BAIXA (não é erro de valor): bases de contagem marcadas por
  inferência da regra geral — `meter.reading_cycle_days` da ARESC com
  `BUSINESS_DAYS` (o ciclo 27–33 é duração, não prazo contado pelo art. 153) e
  `sac.response_days` da ARESC sob `BUSINESS_DAYS` incluindo a resposta
  escrita de 30 dias, que a fonte não marca como úteis. Não muda valor; se a
  curadoria quiser precisão, a base do ciclo devia ser `CALENDAR_DAYS` ou
  `NOT_APPLICABLE` nos três perfis lidos.

## 2. V1_13, EXCLUDE e slot — **OK**

- O defeito existia de fato: a V1_5 real (espelho e main) traz
  `EXCLUDE (tenant_id, agency_code, daterange)` — dois municípios da mesma
  agência no mesmo tenant eram impossíveis. A correção é mínima e correta:
  mesmo nome de constraint, acrescido só de
  `COALESCE(municipality_ibge, '') WITH =`. A semântica da P0.19
  (proposta/aprovação/aceite em `parameter_versions`/`parameter_acceptances`)
  não é tocada; as 4 tabelas novas seguem o padrão (`register_tenant_table`,
  UNIQUE (tenant_id, id), FKs compostas válidas contra os UNIQUEs da V1_5).
- Slot: listagem real da main via MCP = V1_1–V1_7, V1_9–V1_12, V1_16–V1_18 —
  **V1_13 livre**. O §3 do plano v3.0 (linha 389) reserva a V1_13 exatamente
  para a P2.31 com este conteúdo, e a R__ repetível idem; V1_14 = P3.34 e
  V1_15 = P3.36 no plano (a V1_15 da P3.36 existe no pacote local ainda não
  pushado — sem colisão com a V1_13). O cabeçalho da migration declara tudo
  isso corretamente.
- As 15 definições: a conta fecha **só com o precedente real** — das 24
  chaves dos perfis, 7 já estão na V3 do core (`meter.average_months`,
  `meter.max_estimated_cycles`, `bill.due_date_options`, `fee.fine_pct`,
  `fee.interest_mode`, `fee.interest_daily_pct`, `ret.billing_history_months`)
  e 2 foram registradas pela V4_4 §7 da P2.5 já pushada
  (`meter.after_cap_rule` §7.2 e `bill.min_days_delivery_to_due` §7.3,
  conferido no arquivo). Restam exatamente as 15 da V1_13. Precedente do
  INSERT em `public.parameter_definitions` a partir de migration de tenant
  com `ON CONFLICT DO NOTHING`: real, é o §7 da V4_4.

## 3. R__ gerada e determinismo — **OK**

- Rodei o gerador nesta VM sobre o `ScRegulatoryProfiles.java` entregue:
  saída `7 perfis, 167 valores, 7 divergencias` e **`diff` byte a byte igual**
  à R__ entregue. Contagens recontadas por fora: 167 = 6 perfis × 24 valores
  + CISAM-MO RN 01/2017 × 23 (sem `reg.ana_nr_adoption`, correto: os atos são
  de 2025/2026); divergências 1+1+2+0+1+1+1 = 7.
- O placeholder `${tenant_id}` segue o precedente do GIS de verdade:
  `R__gis_factory_catalog.sql` (P1.27, na main) abre com a mesma linha
  `SELECT set_config('app.current_tenant', '${tenant_id}', true);` e usa o
  mesmo placeholder nas linhas. O risco nº 4 do relatório da frente (migrador
  não substituir) é, portanto, menor do que o declarado — o precedente já
  roda em produção de provisionamento. Guarda de contagem no fim: presente,
  no modelo da P1.34.

## 4. Guarda de carga e divergências — **OK com ressalva MÉDIA**

- As 7 divergências batem com a spec e com a fonte, e **todas** se fundam em
  lei federal: corte 12h de sexta × Lei 14.015/2020 em ARIS (158), ARESC (85),
  AGIR (148), CISAM-MO RN 01/2017 (art. 107 — fonte confirma) e RN 05/2026
  (manhã de sexta aberta — fonte confirma) e CISAM-SUL (§6, camada: "idem");
  tarifa social AGIR art. 127 §7º × Lei 14.898/2024 com `RULE_REJECTED` e
  nenhum valor substituto. A spec nomeia 4; as 3 a mais têm lastro no mapa —
  não são divergência inventada.
- Como VALOR, a recusa funciona: `validateProfile` varre todos os valores
  (inclusive não-CONFIRMED) à procura dos marcadores, e
  `cut.forbidden_window` sem `FEDERAL_FLOOR_14_015` é recusada em qualquer
  caminho. O valor efetivo carregado nos 7 perfis é o piso federal.
- **MÉDIO — a afirmação do relatório ("a guarda recusa qualquer tentativa…
  inclusive em valor informado pelo tenant na ativação") é mais forte que o
  código.** Em `RegulatoryActivationRule.planVersions`
  (linhas 87–97), `assertValueLoadable` só é chamado para chaves
  `REQUIRES_TENANT_VALUE`. Valor do tenant para chave `CONFIRMED` ou
  `CONFIRMED_ABSENT` é **silenciosamente ignorado** (a versão planejada usa o
  valor de fábrica / nenhum). O efeito é seguro — regra proibida nunca vigora
  e o tenant não consegue injetar a janela local — mas o contrato é silencioso
  onde o desenho promete recusa nomeada, e um tenant que informe, p. ex.,
  `cut.max_debt_age_days` para a ARESC (ausência confirmada) não recebe nem
  versão nem erro. Correção sugerida abaixo.

## 5. Simulação sem gravar — **OK na não-persistência; MÉDIO no cálculo**

- "Sem DataSource por construção": **verdade**. `RegulatorySimulation` não
  importa `DataSource`/`Connection`/repositório; só recebe
  `Map<String,String>` + perfil + conta sintética. O teste estrutural
  (`simulationHasNoPersistenceSurface`) confere campos e parâmetros — é um
  teste honesto do que promete.
- Mostra diferença por chave (todas, com ato/artigo/confirmação), pendências
  do tenant, divergências e efeito na conta-tipo. Os números dos testes
  conferem à mão: R$ 200 × (2% + 0,033%×10) = R$ 4,66; média de 6 meses sobre
  [10..20] = 15; fator 40/30 = 1,3333.
- **MÉDIO — é reimplementação, não o cálculo da P2.5.** `charges()`,
  `average()` e o `dayFactor` são cópias simplificadas locais: o
  `PartialPeriodRule` real (billing) distingue primeira/final/regular e
  normaliza o consumo medido de ciclo regular fora da faixa pela média diária
  — a simulação devolve só o fator, sem esse efeito; os encargos ignoram
  `fee.interest_mode` e o índice de correção. O javadoc declara ("espelha",
  "o cálculo autoritativo continua sendo o do billing") e a dependência
  platform→billing não existe no módulo — mas o acceptance pede a simulação
  "sobre a massa sintética" com efeito na conta-tipo, e quem ler o resultado
  como prévia fiel da P2.5 será enganado nos casos fora da faixa. Aceitável
  como está **se** a tela rotular como estimativa; registrar como dívida
  conhecida, não como equivalência.

## 6. Envelope de ativação — **OK** (1 ressalva BAIXA)

- Proponente ≠ aprovador garantido **no serviço**, em três camadas:
  `RegulatoryProfileActivationService.approve` chama
  `RegulatoryActivationRule.validateApproval` → delega a
  `ParameterVersionRule.validateApproval` do core (reuso, não duplicação;
  confere também efeito só futuro); CHECK `approved_by <> proposed_by` na
  tabela de ativações da V1_13; e os CHECKs da V1_5 em cada
  `parameter_versions`. Não é só teste.
- Hash: `RegulatoryActivationRule.contentHash` **delega** a
  `PlatformConsoleRules.contentHash` — conferido contra a classe real na main
  via MCP (assinatura `(String, String, LocalDate, LocalDate)`, SHA-256 sobre
  chave/valor/vigência) e contra o uso idêntico no `TenantParameterService`,
  incluindo a canonização `Json.readTree(value).toString()` antes do hash.
  Reuso verdadeiro, sem segunda fórmula.
- BAIXO: `accept()` nunca chama `validateAcceptanceHash` — copia o hash da
  própria versão para o aceite (a regra pura só é exercida em teste). É o
  mesmo padrão do `TenantParameterService.accept` da P1.6, logo é precedente
  e não regressão; fica registrado porque o relatório da frente fala em
  "hash conferido", e o que há é hash **registrado**, não conferido contra
  uma entrada externa.

## 7. Testes por perfil (41) — **OK com ressalvas BAIXAS**

- Contagem confere: 10 + 11 + 6 + 7 + 4 + 3 = **41 testes** em 6 classes,
  pacote novo, nenhuma asserção existente tocada.
- Por perfil: `ScRegulatoryProfilesTest` tem um bloco por perfil cobrindo
  convergentes, diferenças, divergências, adoção e fato NR 11. Média/teto/
  ajuste por dias/vencimentos estão em `RegulatorySimulationTest`, mas só
  exercitados sobre ARIS/ARESC/AGIR (+ mínimo do CISAM-SUL); para AGR e
  CISAM-MO essas chaves são `REQUIRES_TENANT_VALUE` e o que se testa é a
  pendência/fallback — coerente com a spec ("teste por perfil" sobre o que o
  perfil carrega), mas a frase do relatório "por perfil" deve ser lida assim.
  Limites RAS-16: `RegulatoryLoadGuardTest` nomeia um teste por chave/limite
  (multa, juros, modo, aviso, retenção, média, ciclos, regra posterior,
  janela, tarifa social, piso de concessionária) — cobertura real.
  Período parcial (JOR-11/RAS-24): exercido via fator de dias e diffs de
  vencimento; a classe autoritativa `PartialPeriodRule` não é chamada (ver
  item 5) — declarado pela frente, não escondido.
- Troca CISAM-MO: `RegulatoryProfileSwitchTest` usa `Clock.fixed` de verdade
  (19/05/2027 → RN 01/2017; 20/05/2027 → RN 05/2026) e a data/art. 163 batem
  com a fonte (apêndice B do anexo F: RN 05/2026 publicada em 05/10/2026, em
  vigor a partir de 20/05/2027 revogando a RN 01/2017; a frente atribui a
  vigência ao art. 163 conforme o acceptance — o mapa não numera o artigo da
  vigência, só confirma data e revogação; a atribuição vem da spec, não é
  invenção da frente).
- BAIXO: `RegulatoryCatalogConsistencyTest.versionedMigrationShape`, no
  segundo loop, só assevera que toda `definitionKey` não está em branco —
  não prova o que o nome sugere (que toda chave tem definição). A prova real
  das definições é a lista fixa de 15 chaves no mesmo teste + a conta do
  item 2 desta revisão.

## 8. `reg.ana_nr_adoption`, DAD-28 e fato NR 11 — **fato OK; adoção OK; ligação NÃO VERIFICÁVEL / MÉDIA**

- Os atos de adoção conferem com o §5 do mapa (parágrafo de atos e anexo F
  linha "SC | …"), amostrados em todos os perfis: ARIS NR 11 parcial
  (RN 19/2019, 45/2025, 47/2025, 55/2026) e NR 13 parcial (RN 56/2026); ARESC
  NR 3/5/8 pelas Res. 433/432/430 de 2026 (+431 na NR 8); AGIR RN 018–025/2025-26;
  CISAM-MO NR 3–8 pelas RN 01–06/2025, NR 11 pela RN 05/2026, NR 13 pela
  RN 04/2026; AGR-Tubarão NR 7/8/9 pelas Res. 044/045+048/049; CISAM-SUL com
  status honestos de lista ANA 2025 (`ATENDIDA_LISTA_ANA_2025`,
  `DESOBRIGADA_LISTA_ANA_2025` para a NR 6) em vez de ato inventado. Nenhum
  ato sem lastro encontrado.
- Fato NR 11: presente nos 7 perfis como `reg.normative_facts` com data
  2027-05-20 e fonte "Res. ANA 230/2024, art. 74, §3º" — bate com o mapa
  (§3 achado 2 e anexo F §2: o texto do ato prevalece sobre a notícia de
  27/05/2027). Testado em todos os perfis.
- **MÉDIO/NÃO VERIFICÁVEL — a "ligação" com o catálogo da P1.34 (DAD-28) não
  existe em código.** `RegulatoryProfileActivationService.accept` (linhas
  299–306) apenas copia o JSON de adoção para `regulatory_profiles` — nenhuma
  consulta, validação ou junção com `cert_standard`. E a chave usada no JSON
  é `NR11`, `NR3`…, enquanto a P1.34 define `cert_standard.code = catalog_key`
  do JSON de normas (formato slug, ex. os códigos ABNT da nota da P1.34); se
  os códigos reais das NR da ANA no catálogo são slugs, `NR11` **não casa**
  com nenhum `code`. Não pude confirmar o formato real: o espelho
  `fontes/R__norm_catalog-head.txt` está **vazio (0 bytes)** e a
  `R__norm_catalog.sql` tem 1.475.532 bytes — o MCP recusou a leitura
  integral ("too large to display"). A adoção como **dado** está certa; a
  ligação como **mecanismo** está só declarada em comentário. Antes de
  prometer DAD-28, conferir 2–3 `code` de NR da ANA no catálogo e, se forem
  slugs, ou mapear NR→code na carga, ou rebaixar a alegação para "mapa
  declarativo por número de NR".

## 9. Java, fronteiras e forma — **GRAVE no formato de 2 chaves; resto OK**

- **GRAVE nº 1 — o formato gravado de `bill.due_date_options` e de
  `bill.min_days_delivery_to_due` é incompatível com o consumidor real da
  P2.5, e a ativação grava esse formato em `parameter_versions`.**
  - `bill.due_date_options` é `LIST_INT` na V3 e
    `BillingParameters.dueDateOptions()` (P2.5, linhas 159–170) exige array
    JSON e lança erro se não for (`"esperado array de dias"`, linha 163). O
    perfil carrega **objeto** `{"minCount":6}` (ScRegulatoryProfiles.java
    linhas 80, 129, 171; `{"minCount":4}` no CISAM-SUL, linha 340).
  - `bill.min_days_delivery_to_due` é OBJECT na V4_4 §7.3 no formato
    `{"default": 5, "<categoria>": 7}` (javadoc de `BillingParameters`,
    linha 38; leitura com fallback `"default"`, linhas 175–190). O perfil
    carrega `{"general":5,"publicCategory":10,"terminationOnRequest":1,
    "unit":"BUSINESS_DAYS"}` (linhas 81, 130, 172) — sem `"default"`, a
    leitura da P2.5 lança `MissingParameterException` para qualquer
    categoria.
  - Agravante: a nota da chave diz "os dias concretos são escolha do tenant
    na ativação", mas `planVersions` não tem caminho para transformar
    `minCount` + escolha do tenant em lista de dias — para chave `CONFIRMED`
    o valor do tenant é ignorado (item 4). Resultado: tenant que ativar
    ARIS/ARESC/AGIR passa a ter versões aceitas que o billing não consegue
    ler, justamente nas chaves de RAS-24 que a tarefa devia pré-carregar.
    Os demais formatos conferem com os consumidores existentes (ints de
    média/ciclos/aviso/prioritário, enum de `meter.after_cap_rule` igual ao
    `AfterCapRule` da P2.5, percentuais numéricos, `ret` = 120).
- **MÉDIO (mesma família, sem consumidor ainda):** `reg.normative_facts` é
  registrada na V1_13 como `OBJECT` (linha 205), mas o valor construído em
  `RegulatoryProfileDef` (linhas 246–254) é um **array** JSON. Nenhum
  consumidor atual quebra (o console não valida tipo contra o catálogo),
  mas o catálogo passa a mentir sobre a própria carga; corrigir o tipo ou o
  formato junto com o GRAVE.
- JSON só via `Json` do core: **OK**. Não há `ObjectMapper` nem serialização
  manual nas classes novas; os imports de `tools.jackson.databind.JsonNode`
  são só o tipo de retorno de `Json.readTree` — o mesmo padrão do
  `TenantParameterService` e do `BillingParameters` reais. A canonização por
  `JsonNode.toString()` é a do console (item 6).
- Padrão do módulo: **OK** — pacote novo `platform.regulatory`, serviço JDBC
  espelhando o `TenantParameterService` (DataSource/schema/tenantId/Clock,
  `inTx` com `set_config` local, 409 em 23P01/23505), regras puras separadas
  do JDBC, reuso de `ParameterLimits`/`ParameterVersionRule` sem duplicar
  trava.
- Fronteiras: **OK** — nada de P3.4/P3.28 implementado (só notas de consumo
  futuro e a divergência estadual da ARESC registrada para a P3.4/RAS-12);
  nenhum perfil fora de SC (`all()` = 7 perfis, 6 ERIs de SC).

---

## Correções ordenadas por severidade

1. **[GRAVE] Formato das chaves de vencimento (item 9).** Decidir o contrato
   antes do push: ou o perfil passa a carregar `bill.due_date_options` como
   lista de dias (e o `minCount` vira metadado do catálogo do perfil, não o
   valor da versão), com `planVersions` exigindo/compondo os dias concretos
   do tenant na ativação; e `bill.min_days_delivery_to_due` no formato da
   P2.5 (`{"default":5, "PUBLIC":10, …}` — mapear `general→default` e a
   categoria pública para a chave de categoria real do billing, levando a
   unidade para a `deadline_basis`/nota). Acrescentar teste que leia os
   valores planejados **com o parser da P2.5** (ou uma réplica fiel dele),
   para o formato nunca mais divergir em silêncio.
2. **[MÉDIO] DAD-28 (item 8).** Conferir os `code` reais das NR da ANA em
   `cert_standard` (a R__ tem 1,47 MB: ler por download/grep local, não pelo
   MCP) e ou implementar a ligação (mapa NR→code validado na ativação), ou
   corrigir relatório/comentários para "mapa declarativo, ligação pendente".
   Repovoar o espelho `R__norm_catalog-head.txt`, que está vazio.
3. **[MÉDIO] Contrato do valor do tenant na ativação (item 4).** Recusar
   nomeando a chave (em vez de ignorar em silêncio) quando o tenant informar
   valor para chave `CONFIRMED`/`CONFIRMED_ABSENT` divergente do perfil — ou,
   se a intenção é ignorar, declarar isso no relatório e na mensagem da API.
4. **[MÉDIO] `reg.normative_facts`: tipo × formato (item 9).** Alinhar a
   definição da V1_13 com o valor (array) ou embrulhar os fatos em objeto.
5. **[MÉDIO] Simulação (item 5).** Rotular o resultado como estimativa com
   fator de dias simplificado, ou aproximar o `dayFactor` da semântica do
   `PartialPeriodRule` (primeira/final × regular; normalização do consumo).
   Não bloquear o push por isto se o rótulo for honesto.
6. **[BAIXO] Relatório da frente:** CISAM-SUL tem **16** chaves sem fábrica,
   não 15 (contadas no Java e na R__). Ajustar a seção "Sem valor de fábrica".
7. **[BAIXO] Teste de consistência:** trocar o segundo loop vazio de
   `versionedMigrationShape` por asserção real (toda chave dos perfis ∈
   V3 ∪ V4_4 ∪ V1_13), nem que seja como lista fixa comentada.
8. **[BAIXO] Bases de contagem inferidas (item 1):** revisar
   `BUSINESS_DAYS` no ciclo de leitura da ARESC e na resposta escrita de
   30 dias — precisão documental, sem pressa.

## O que NÃO deve ser mexido

- Os **valores, artigos e marcas de confirmação** de `ScRegulatoryProfiles.java`
  — a procedência está correta e completa contra o mapa; qualquer "completar"
  os NC com números plausíveis destruiria a propriedade central da tarefa.
- A **correção do EXCLUDE** na V1_13 e o desenho catálogo R__ → envelope →
  `regulatory_profiles` (a spec pede pré-carga na base da P0.19; a linha só
  nasce no aceite — desenho correto e declarado).
- O **gerador e a R__** como par: determinismo byte a byte provado; não
  editar a R__ à mão em nenhuma correção — regenerar sempre (a correção nº 1
  muda a fonte Java e exige regeneração + guarda de contagem coerente).
- A **delegação** de hash a `PlatformConsoleRules` e de aprovação a
  `ParameterVersionRule` — reuso verificado contra as classes reais.
- As **7 divergências** como estão: nem remover as 3 além da spec (têm lastro
  federal e no mapa), nem promovê-las a valor.
- Os **status de adoção do CISAM-SUL** (lista ANA 2025, sem ato): não trocar
  por `ADOPTED` retroativamente.

## Riscos que esta revisão não cobre

- Compilação e CI: sem JDK/Maven/PostgreSQL nesta VM, como a frente declarou.
  Os riscos JDBC/RLS/Flyway listados no relatório da frente (itens 1–6 dele)
  continuam válidos e só a CI total no PC do dono os prova — em especial o
  `enablesRowLevelSecurityOnEveryTable` sobre as 4 tabelas novas.
- A leitura integral da `R__norm_catalog.sql` (item 8) ficou impossível pelas
  vias disponíveis nesta revisão (MCP recusa arquivo de 1,47 MB; espelho
  vazio) — é a única verificação declarada NÃO VERIFICÁVEL, e é justamente a
  que a correção nº 2 precisa resolver.
