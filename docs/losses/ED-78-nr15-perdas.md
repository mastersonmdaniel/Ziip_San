# ED-78 — Balanço de perdas e plano de gestão pela NR 15

Módulo `intelligence/ziipsan-losses`, pacote novo
`br.com.ziipsan.intelligence.losses.nr15`: a SAÍDA da NR 15/ANA
(Res. ANA 275/2025) sobre o módulo `losses` da ED-85 (OPE-01), sem
cálculo próprio de balanço.

## O que a entrega faz

- **Catálogo da norma** (`Nr15NormCatalog`): NR 15 ligada ao
  `cert_standard.code` da P1.34 (`ana-nr-15-res-275-2025`, via
  `AnaNormCatalogLink` da P2.31); a verificação da adoção pela ANA
  a partir de maio de 2028 é registrada como DATA DE NORMA —
  20/05/2028, art. 14, parágrafo único da NR 15 — sem condicionar
  comportamento.
- **Aplicabilidade** (`Nr15Applicability` sobre
  `RegulatoryProfilePort`): o tenant só é aplicável quando o perfil
  regulatório (P2.31) declara a adoção da NR 15 pela sua agência
  COM ato publicado. Hoje a NR 15 consta `NO_ACT_LOCATED` nos
  perfis ARIS/AGIR da P2.31: nenhum tenant deles é aplicável até a
  ERI publicar o ato — fail-closed, com motivo nomeado.
- **Balanço hídrico padronizado anual** (base SINISA; art. 4º e
  Anexo I): soma das fotos mensais por DMC da ED-85
  (`loss_monthly_balances`, via `DmcBalancePort`/
  `DmcBalanceSnapshot`), exigindo os 12 meses por DMC (Anexo I,
  obs. 4). Perdas aparentes/reais desconhecidas num mês tornam o
  ano desconhecido (null), nunca zero. O volume produzido é LIDO DA
  P4.2 (`ProducedVolumePort`, variável `AGUA.VOLUME_PRODUZIDO`); a
  divergência contra a entrada somada fica gravada no balanço e
  bloqueia a declaração. A matriz do Anexo I apresenta volumes em
  1.000 m³/ano (obs. 1) — conversão de apresentação apenas.
- **Fechamento SINISA** (`SinisaClosureEquations`): as 5 equações
  da obs. 3 do Anexo I, transcritas do DOU, verificadas sobre
  valores GTA declarados. O significado componente a componente
  dos códigos GTA é `[SEM FONTE]` nesta VM (matriz gráfica ilegível
  e glossário não carregado) — pendência P-ED78-02.
- **Declaração anual** (`AnnualDeclaration`/
  `Nr15DeclarationService`): DRAFT → SUBMITTED → PUBLISHED, com
  portões de aplicabilidade, reconciliação com a ED-85 e volume
  produzido reconciliado. Publicação com canal (site do prestador
  ou da ERI — art. 12, parágrafo único; art. 14, III) e referência
  declarada.
- **Indicadores por período** (`Nr15Indicators`): índice de perdas
  por ligação segregado em reais e aparentes (NR 9 nível I-01, via
  art. 5º, I, a), água não faturada %, hidrometração (SINISA
  IAG1003) e macromedição (NR 9 II-02) das contagens da ED-17, e
  **IVI e ILI por período** — um só indicador (IVI é o nome em
  português do ILI da IWA, razão CARL/UARL), agregado das fotos da
  ED-85 por ΣCARL/ΣUARL com a UARL recuperada de cada ILI gravado;
  ausente se algum DMC com perda real não tem ILI. O índice de
  intermitência (NR 9 I-04) é da ED-92/ED-77 e não é duplicado.
- **Plano de Gestão de Redução e Controle de Perdas**
  (`LossManagementPlan`/`Nr15PlanService`): por município (art.
  8º), com o conteúdo mínimo do art. 9º — diagnóstico (a
  declaração), acompanhamento de perdas aparentes e reais sobre a
  linha de base do diagnóstico (periodicidade DECLARADA pelo
  prestador; a NR a remete ao ato da ERI), evolução da
  hidrometração e da macromedição, ações com cronograma
  físico-financeiro e justificativa técnico-econômica, indicadores
  complementares, estrutura operacional e custos. Convergência dos
  acompanhamentos só contra alvo DECLARADO (ato da ERI/prestador);
  sem alvo, é desconhecida. Revisão versionada e ligada (art. 9º,
  §1º).

## O que a entrega NÃO faz

- Não calcula balanço: toda componente derivada é lida da ED-85.
- Não fixa limiar, meta ou tolerância de norma: os únicos números
  de norma são os fatos do catálogo (datas, códigos, equações). A
  tolerância de 0,01 m³ da reconciliação é escolha aritmética
  declarada, não limiar da Resolução.
- Não presume a fórmula do glossário SINISA para IAG1003/II-02
  além do significado literal dos nomes citados na NR 15
  (`[SEM FONTE]`, P-ED78-02).

## Banco

`db/pendente/ED-78__nr15_outputs.sql` (sem número; faixa V22_x do
losses): `nr15_annual_balances`, `nr15_annual_declarations`,
`nr15_management_plans`, `nr15_plan_actions`,
`nr15_plan_metering_evolution`, `nr15_plan_declared_targets`,
`nr15_plan_monitoring` — todas por tenant, com
`UNIQUE (tenant_id, id)` nos pais de cada FK composta e nenhuma
linha semeada.

## Adendo de correção — 08/11/2026 (ED-78-CORR-1, sobre a revisão ED-78-REV-1)

Correções documentais das ressalvas R1–R5 da revisão independente
(APROVADO COM RESSALVAS, nenhuma bloqueante). Nenhuma fórmula,
convenção ou comportamento foi alterado neste adendo; os itens
abaixo são registros e pendências nomeadas.

- **R1 — ANF% com entrada líquida zero = 0,0 (não null).** É a
  convenção herdada verbatim da ED-85 (`nrwPct` idêntico), mantida
  de propósito: mudar só na ED-78 divergiria da fonte do número.
  Registrado também nos Javadocs de `Nr15BalanceAssembler` e
  `Nr15Indicators`. A decisão de virar null (ou não) é **conjunta
  com a ED-85** — pendência **P-ED78-08**.
- **R2 — ILI de período ausente quando um DMC tem perda real zero
  ou negativa.** Com perda real zero, a ED-85 grava ILI = 0 e a
  inversão UARL = CARL/ILI é 0/0 (indefinida); com perda real
  negativa, o ILI gravado é ≤ 0. Nos dois casos o período inteiro
  fica sem ILI (null honesto), mesmo que outros DMCs fossem
  computáveis — agregar o subconjunto o apresentaria como sistema.
  O caso e o porquê estão agora documentados no Javadoc de
  `Nr15Indicators.aggregateIli`. Mudança futura de comportamento
  só com decisão conjunta com a ED-85 — pendência **P-ED78-09**.
- **R3 (observação, só registro) — Aplicabilidade decide só por
  atos, não pelo `status`.** `Nr15Applicability` considera o tenant
  aplicável quando a adoção da NR 15 tem ao menos um ato publicado,
  qualquer que seja o `status` declarado; um status inconsistente
  com os atos não muda o resultado. É o **espelho fiel** do
  critério da P2.31 (`AnaNormCatalogLink.adoptedCatalogCodes`
  filtra só por atos não vazios) e, nos perfis reais conferidos
  (ARIS/AGIR), status e atos são coerentes. Endurecer aqui
  divergiria da dependência — nenhum código foi alterado.
- **R4 — O Plano não tem ciclo de publicação guardado.** A
  declaração anual tem máquina DRAFT→SUBMITTED→PUBLISHED com canal
  e referência obrigatórios; o `LossManagementPlan` é record
  aberto e pode nascer `PUBLISHED` sem rastro, e a tabela
  `nr15_management_plans` não tem colunas/CHECK de publicação do
  plano. Nenhum ciclo novo foi inventado nesta correção: o gap e o
  tratamento esperado (na fiação, ou o Plano ganha o mesmo portão
  da declaração, ou seu status fica restrito a DRAFT/SUBMITTED até
  lá) estão registrados na pendência **P-ED78-05** (adendo de
  08/11/2026 no RELATORIO-ED-78) e no Javadoc do record.
- **R5 (observação, só registro) — Fronteira das equações GTA.**
  `SinisaClosureEquations` executa aritmética de fechamento
  (inclusive a subtração do GTA1218) sobre valores GTA
  **declarados**, como conferência do formulário SINISA; nenhuma
  saída da NR 15 deriva dela, portanto não viola o "sem cálculo
  próprio de balanço". Registra-se ainda que o script de coerência
  bloqueia um único padrão textual (`netInput − authorized`) e não
  deve ser lido como prova geral da fronteira — a prova é o
  inventário aritmético da revisão. Nenhum código foi alterado.
