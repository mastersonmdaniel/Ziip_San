# Contrato do pacote de vertical (P0.47)

Contrato executável: `packages/sanitation/vertical.json`, validado por
`packages/sanitation/vertical.schema.json` e, no build, por
`VerticalManifestLoader`/`VerticalManifestValidator` (módulo `ziipsan-platform`) e pelo
teste de fronteira `VerticalBoundaryTest` (desde 10/11/2026 no módulo `ziipsan-app`; decisões 66 + 71). O mapa do produto é
`docs/arquitetura/product-map.md`, gerado por `scripts/generate-product-map.py` e
conferido no CI por `VerticalManifestTest` (marcadores `<!-- module: <id> -->`).

## 1. Regra das abas da ficha da unidade

Cada tarefa que cria uma fonte da ficha da unidade (contas, débitos, OS, atendimentos,
documentos, qualidade) acrescenta a aba correspondente ao manifesto do pacote, no campo
`unitSheetTabs` do módulo. A aplicação da regra no frontend é da P1.12, não desta onda.

Abas refletidas no manifesto nesta onda (fontes que já existem como módulo): contas
(`billing`), débitos (`collection`), documentos (`mtr`), qualidade (`quality`). O
`registry` fornece a própria ficha, não uma aba. OS e atendimentos ainda não têm módulo
nem aba: entram com as tarefas que os criarem, junto com a aba.

## 2. Renomeação para `platform/*` e `packages/sanitation/*` — NÃO APLICADA (trabalho restante)

A P0.47 deveria aplicar os nomes definitivos de módulo conforme "a tabela única de
módulos de PLATAFORMA-E-VERTICAIS". Verificação feita nesta onda: **essa tabela não
existe no documento** — `PLATAFORMA-E-VERTICAIS.md` só fixa os padrões `platform/*` e
`packages/sanitation/*`, sem mapear nenhum módulo atual para o nome definitivo. Sem a
tabela, renomear seria adivinhar o destino de 13 módulos Maven, dos pacotes Java, dos
artefatos e das migrations — e a instrução da onda é explícita: não quebrar o build
pela renomeação.

Decisão da frente (registrada, não inventada como decisão do dono): entregar
`vertical.json` + registro + teste de fronteira + mapa **sem renomear os módulos
existentes**, e deixar a renomeação como trabalho restante, condicionado a existir a
tabela única. Proposta de classificação **para revisão do coordenador/dono, não
aplicada**:

| Classificação proposta | Módulos atuais |
|---|---|
| Núcleo → `platform/*` | `shared/ziipsan-core`, `shared/ziipsan-security`, `platform/ziipsan-platform`, `platform/ziipsan-app` (tenant, autenticação, BFF, parâmetros, auditoria — fronteira §2) |
| Pacote → `packages/sanitation/*` | `commercial/ziipsan-registry`, `commercial/ziipsan-billing`, `commercial/ziipsan-collection`, `commercial/ziipsan-fiscal`, `operations/ziipsan-metering`, `intelligence/ziipsan-gis`, `intelligence/ziipsan-quality`, `intelligence/ziipsan-losses`, `grs/ziipsan-mtr`, `compliance/ziipsan-lgpd` |

Pontos que a tabela única precisa resolver antes da renomeação (não resolvidos aqui):
o destino do `ziipsan-app` (raiz de composição, não só plataforma); o nome definitivo
do `ziipsan-core` dentro de `platform/*`; e se `packages/sanitation/*` carrega um
artefato agregador ou só os módulos. Até lá, o campo `mavenModule` do manifesto usa o
caminho atual, e o validador não exige o padrão novo.

## 3. Lista de exceções da fronteira (versionada) — regra redefinida (decisões 66 + 71, 10/11/2026)

Regra redefinida pelo dono (decisões 66 e 71): o núcleo é puro **excepto** as
ocorrências listadas e justificadas uma a uma nas tabelas 3.1 e 3.2 abaixo.
O teste de fronteira `VerticalBoundaryTest` (desde 10/11/2026 no módulo
`ziipsan-app`, pacote `br.com.ziipsan.app.vertical`, por força da decisão 66)
**lê estas tabelas mecanicamente** em cada corrida: entrada malformada
(número de colunas errado, célula vazia, termo fora da lista de proibidos,
par duplicado) **falha o teste**. O casamento é **exacto por par**: na
varredura, por (ficheiro, termo) — um ficheiro exceptuado para um termo
continua a falhar por qualquer outro termo proibido; no ArchUnit, por
(classe origem, classe destino) — qualquer outra dependência da mesma
classe continua a falhar. Fora destas listas, a regra mantém a força
original integral.

Termos proibidos no código do núcleo (`platform/ziipsan-platform`,
`shared/ziipsan-core`, `shared/ziipsan-security`, só `src/main/java`) — os 8
do teste, em minúsculas: `hidrômetro`, `hidrometro`, `esgoto`, `fatura`,
`saneamento`, `água`, `ligação de água`, `ligacao de agua`. A comparação é
por substring sobre o conteúdo em minúsculas, como no teste original: parte
das ocorrências de `fatura` é a substring em `faturamento`/identificadores
desse domínio, o que a linha de cada excepção mostra ao citar o uso real.

Baseline das excepções: corrida do teste ainda no platform sobre a base
`9128ef4d` (log integral no pacote `correcao-vbt-app-regra`, 2026-11-10) —
90 pares (ficheiro, termo) em 83 ficheiros e 42 violações ArchUnit, todas
na regra do pacote `core` e todas cobertas pelos 7 pares de 3.2. A
atribuição de cada ficheiro é o commit de criação do ficheiro no histórico
(`git log --diff-filter=A`), citado na justificação: o termo já nasce com o
ficheiro nesse commit, introduzido pela frente/tarefa do assunto do commit.

### 3.1 Exceções da varredura de termos

| Ficheiro | Termo | Justificativa |
|---|---|---|
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/BillingCycleSnapshot.java | fatura | Introduzido com o ficheiro no commit `11f2b47a` (2026-11-07, "feat(p2-11): Central, abas Faturamento e Fiscal"). Uso no ficheiro: `* Instantâneo AGREGADO do ciclo de faturamento de um tenant, como a` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/CentralAlertSignal.java | fatura | Introduzido com o ficheiro no commit `11f2b47a` (2026-11-07, "feat(p2-11): Central, abas Faturamento e Fiscal"). Uso no ficheiro: `* grupo de faturamento do item obrigatório vencido mais antigo quando a` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/CentralBillingFiscalAlerts.java | fatura | Introduzido com o ficheiro no commit `11f2b47a` (2026-11-07, "feat(p2-11): Central, abas Faturamento e Fiscal"). Uso no ficheiro: `* Regras de alerta das abas Faturamento e Fiscal da Central (P2.11,` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/CentralBillingFiscalService.java | fatura | Introduzido com o ficheiro no commit `11f2b47a` (2026-11-07, "feat(p2-11): Central, abas Faturamento e Fiscal"). Uso no ficheiro: `* Visões das abas Faturamento e Fiscal da Central (P2.11) sobre os` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/CentralTab.java | fatura | Introduzido com o ficheiro no commit `11f2b47a` (2026-11-07, "feat(p2-11): Central, abas Faturamento e Fiscal"). Uso no ficheiro: `* {@link #FATURAMENTO} e {@link #FISCAL} — e só elas são desta tarefa.` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/central/complete/CentralCompleteTabs.java | fatura | Introduzido com o ficheiro no commit `c2e0395a` (2026-11-07, "feat(p3-15): Central Ziip completa (parte 1/3)"). Uso no ficheiro: `* P2.11, Faturamento e Fiscal (registro próprio em` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/clientapp/ConsumerRealtimePolicy.java | fatura | Introduzido com o ficheiro no commit `11f01e69` (2026-11-08, "feat(lote final): ED-39, ED-40 (parte 52/59)"). Uso no ficheiro: `*       o app mostra o consumo diario/faturado, nao a curva;</li>` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/ConsumptionGoal.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `* do ciclo de faturamento). Nenhum valor de meta tem fabrica nem sugestao` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/ConsumptionPorts.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `* FATURADO do periodo (billing); a telemetria da ED-17, QUANDO HOUVER` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/EngagementParameters.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `*   <li>{@code bill.invoice_template} — modelo de fatura aprovado pela` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/GoalProgress.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `* definitivo com o periodo fechado (consumo faturado conhecido); em periodo` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/GoalService.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `* periodo; senao, o consumo faturado; nenhuma das duas = {@code NO_DATA}.` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/engagement/InvoiceMessageService.java | fatura | Introduzido com o ficheiro no commit `adf13dc1` (2026-11-08, "feat(ed-101): metas de consumo, comparacao com pares e gamificacao (SQL pendente)"). Uso no ficheiro: `* integracao com o conteudo minimo da fatura da P2.32): {@code {period}},` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/NotificationDispatchService.java | fatura | Introduzido com o ficheiro no commit `0aaf91a1` (2026-11-07, "feat(p3-5): notificacoes com prova de entrega (parte 1/2)"). Uso no ficheiro: `* Com disparo, avisa ANTES da fatura pelos canais de {@code alert.channels}` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/BillEmailPorts.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* Portas da fatura por e-mail (ED-41), agrupadas como em` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/BillEmailRule.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* Regra pura do PDF anexo da fatura por e-mail (aceite da ED-41; ADR-0028,` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/BillEmailService.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* Fatura por e-mail e comunicado de débito (ED-41; POC-20 do roteiro` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/MultichannelDispatchService.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `*   <li>IN_APP volta à central da P3.36; EMAIL volta à P3.5 (a fatura por` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/MultichannelParameters.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `*       adota PDF anexo como padrão da fatura por e-mail (ADR-0028, l).` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/Samae219Coverage.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `new Entry("Fatura por e-mail com aviso e link autenticado ao portal (POC-20)",` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/notification/multichannel/UsageRecord.java | fatura | Introduzido com o ficheiro no commit `1c3ef0ae` (2026-11-08, "feat(ed-41): notificacoes multicanal e fatura por e-mail (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* quem mede (faturamento, atendimento, telemetria…); {@code referenceId} é` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/observability/BillingBatchReleaseProof.java | fatura | Introduzido com o ficheiro no commit `613bcc97` (2026-11-10, "feat(observability): II-8 observabilidade de go-live (Actuator/Micrometer, RED/USE, SLOs e"). Uso no ficheiro: `* II-8: harness do lote de faturação como PROVA DE RELEASE.` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/observability/MetricLabelPolicy.java | hidrometro | Introduzido com o ficheiro no commit `613bcc97` (2026-11-10, "feat(observability): II-8 observabilidade de go-live (Actuator/Micrometer, RED/USE, SLOs e"). Uso no ficheiro: `"meter_id", "hidrometro_id", "service_order_id", "os_id",` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/observability/RedUseMetrics.java | fatura | Introduzido com o ficheiro no commit `613bcc97` (2026-11-10, "feat(observability): II-8 observabilidade de go-live (Actuator/Micrometer, RED/USE, SLOs e"). Uso no ficheiro: `* faturação e sincronização de campo. (O RED do HTTP já existe desde a` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/GuestPaymentService.java | fatura | Introduzido com o ficheiro no commit `114aab19` (2026-11-07, "feat(p3-3): portal do cliente, 2a via, Pix e protocolo (parte 1/3)"). Uso no ficheiro: `* vinculacao impresso e recebe SOMENTE a fatura em aberto daquele codigo.` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/LinkingCode.java | fatura | Introduzido com o ficheiro no commit `114aab19` (2026-11-07, "feat(p3-3): portal do cliente, 2a via, Pix e protocolo (parte 1/3)"). Uso no ficheiro: `* Codigo de vinculacao impresso na fatura (ADR-0043): alta entropia` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/StaticFallbackService.java | fatura | Introduzido com o ficheiro no commit `cf7631d4` (2026-11-07, "feat(p3-3): portal do cliente, 2a via, Pix e protocolo (parte 2/3)"). Uso no ficheiro: `* <p>Esta classe nao depende de nenhuma API de faturamento em tempo de` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/consumer/CentralizerViewService.java | fatura | Introduzido com o ficheiro no commit `764cd483` (2026-11-08, "feat(lote final): ED-22, ED-34 (parte 5/59)"). Uso no ficheiro: `* das unidades vinculadas, exportacao e fatura agrupadora da` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/consumer/ConsumerAccessGuard.java | fatura | Introduzido com o ficheiro no commit `2003909f` (2026-11-08, "feat(lote final): P3.8, P3.20 (parte 13/59)"). Uso no ficheiro: `* O caminho de CONVIDADO da P3.3 (codigo -> fatura em aberto, 5 campos)` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/consumer/ConsumerLinkService.java | fatura | Introduzido com o ficheiro no commit `2003909f` (2026-11-08, "feat(lote final): P3.8, P3.20 (parte 13/59)"). Uso no ficheiro: `* conta (o da fatura, P3.3/{@link LinkingCode}) MAIS o CPF ou CNPJ do` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/consumer/HolderChangeService.java | fatura | Introduzido com o ficheiro no commit `2003909f` (2026-11-08, "feat(lote final): P3.8, P3.20 (parte 13/59)"). Uso no ficheiro: `* novo (geracao e impressao sao da fatura/P3.3) e o antigo nao vincula` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/selfcare/AnnualSettlementCertificate.java | fatura | Introduzido com o ficheiro no commit `61a13cee` (2026-11-08, "feat(lote final): ED-37, ED-38 (parte 47/59)"). Uso no ficheiro: `* porta, do historico da P3.20) E todas as faturas estao quitadas;` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/selfcare/BillExplanation.java | esgoto | Introduzido com o ficheiro no commit `61a13cee` (2026-11-08, "feat(lote final): ED-37, ED-38 (parte 47/59)"). Uso no ficheiro: `* <p>Conciliacao: a soma dos componentes gravados (agua + esgoto +` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/selfcare/BillExplanation.java | fatura | Introduzido com o ficheiro no commit `61a13cee` (2026-11-08, "feat(lote final): ED-37, ED-38 (parte 47/59)"). Uso no ficheiro: `/** Calculo gravado da conta (espelho dos valores persistidos no faturamento). */` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/selfcare/Poc69SelfCareService.java | fatura | Introduzido com o ficheiro no commit `61a13cee` (2026-11-08, "feat(lote final): ED-37, ED-38 (parte 47/59)"). Uso no ficheiro: `* faturas pendentes e quitadas do ultimo ano, segunda via (pendente e` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/BillDocumentHash.java | fatura | Introduzido com o ficheiro no commit `f4f626c3` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `* Hash do documento da fatura (P3.35): SHA-256 sobre a forma canonica dos` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/EmailSenderPolicy.java | fatura | Introduzido com o ficheiro no commit `f4f626c3` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `*       verificacao de fatura nao protege o canal de e-mail;</li>` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/FraudProtectionTexts.java | fatura | Introduzido com o ficheiro no commit `f4f626c3` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `+ "e-mail ou rede social. Pague somente pela fatura emitida, pelo portal oficial "` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/FraudReportService.java | fatura | Introduzido com o ficheiro no commit `f4f626c3` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `* telemetria de requisicoes, nao este servico. (Na verificacao de fatura,` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/PublicBillVerificationService.java | fatura | Introduzido com o ficheiro no commit `54df34bf` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `* Verificacao publica de autenticidade da fatura (P3.35, PLT-11;` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/portal/verify/VerificationCode.java | fatura | Introduzido com o ficheiro no commit `54df34bf` (2026-11-07, "feat(p3-35): protecao contra cobranca falsa: autenticidade publica da fatura e canais ofic"). Uso no ficheiro: `* Codigo publico de verificacao da fatura (P3.35, PLT-11): o codigo curto` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/regulatory/AnaNormCatalogLink.java | saneamento | Introduzido com o ficheiro no commit `53eb609b` (2026-11-07, "feat(p2-31): perfil regulatorio e origem (parte 1/5)"). Uso no ficheiro: `* ({@code docs/normas/catalogo/02-agencia-nacional-de-aguas-e-saneamento-basico-ana-.md},` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/regulatory/ScRegulatoryProfiles.java | fatura | Introduzido com o ficheiro no commit `f8dc28b4` (2026-11-07, "feat(p2-31): perfil regulatorio e origem (parte 3/5)"). Uso no ficheiro: `.value("meter.after_cap_rule", "\"BILL_MINIMUM\"", "RN 19/2019", "art. 83, §§1º, 2º, 4º e 5º", DeadlineBasis.NOT_APPLICABLE, Confi` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/regulatory/ScRegulatoryProfiles.java | saneamento | Introduzido com o ficheiro no commit `f8dc28b4` (2026-11-07, "feat(p2-31): perfil regulatorio e origem (parte 3/5)"). Uso no ficheiro: `return RegulatoryProfileDef.builder("ARIS_RN19_2019", "ARIS", "ARIS - Agência Reguladora Intermunicipal de Saneamento", "RN 19/201` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/securityreview/SecurityBaseline.java | fatura | Introduzido com o ficheiro no commit `d13cfb8c` (2026-11-08, "feat(lote final): P2.24, P3.8 (parte 12/59)"). Uso no ficheiro: `new ComponentLevel("Cobranca e faturamento (billing, metering, fiscal)", Level.L2, "P2.5; P2.6; P3.6"),` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/securityreview/SecurityCase.java | fatura | Introduzido com o ficheiro no commit `d13cfb8c` (2026-11-08, "feat(lote final): P2.24, P3.8 (parte 12/59)"). Uso no ficheiro: `"HomologationSecurityPortalTest: inexistente, sem fatura e sufixo errado sao indistintos; 429 no limite; sem sessao"),` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/telephony/UraQueryService.java | fatura | Introduzido com o ficheiro no commit `109bbef1` (2026-11-08, "feat(ed-44): telefonia mínima (SQL pendente)"). Uso no ficheiro: `* identificou ou nao, a unidade e o resumo de faturas abertas da ficha 360.` |
| platform/ziipsan-platform/src/main/java/br/com/ziipsan/platform/templates/LegalTemplateBlocks.java | fatura | Introduzido com o ficheiro no commit `e95c4857` (2026-11-07, "feat(p2-32): conteudo minimo da fatura por lei e por agencia (parte 2/2)"). Uso no ficheiro: `* fatura entra no modelo como blocos de origem SYSTEM e travados` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/agenda/AgendaEventTypes.java | fatura | Introduzido com o ficheiro no commit `038298ec` (2026-11-07, "feat(p3-39): inicio por perfil: pagina inicial e agenda (parte 2/2)"). Uso no ficheiro: `* cronograma de faturamento da P2.5 e as auditorias da P3.31.` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/apparentloss/FraudRules.java | hidrometro | Introduzido com o ficheiro no commit `e27e9612` (2026-11-08, "feat(ED-95): perdas aparentes e analítica de fraude (SQL pendente) (parte 1/2)"). Uso no ficheiro: `*       fluxo no periodo e a leitura do hidrometro nao avancou nas` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/apparentloss/LossCoverage.java | hidrometro | Introduzido com o ficheiro no commit `e27e9612` (2026-11-08, "feat(ED-95): perdas aparentes e analítica de fraude (SQL pendente) (parte 1/2)"). Uso no ficheiro: `"fatos de troca de hidrometro e de dimensionamento consumidos no retrato UnitFacts",` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/apparentloss/UnitFacts.java | hidrometro | Introduzido com o ficheiro no commit `e27e9612` (2026-11-08, "feat(ED-95): perdas aparentes e analítica de fraude (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* m3 e leituras absolutas do hidrometro. Campos de telemetria sao` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/chemicals/ChemCoverage.java | fatura | Introduzido com o ficheiro no commit `cc1b4569` (2026-11-08, "feat(ed-89): insumos químicos de tratamento e reservatórios (SQL pendente) — parte 1/2"). Uso no ficheiro: `"a ED-89 nao e consumidora de conta: nenhum preco, tarifa ou item de fatura e criado aqui; o produto usado na limpeza e REFERENCIA` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/chemicals/ChemReports.java | fatura | Introduzido com o ficheiro no commit `cc1b4569` (2026-11-08, "feat(ed-89): insumos químicos de tratamento e reservatórios (SQL pendente) — parte 1/2"). Uso no ficheiro: `* faturado por limpeza de reservatorio (ficha de perdas). Soma` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/chemicals/ReservoirCleaning.java | fatura | Introduzido com o ficheiro no commit `5b21abd0` (2026-11-08, "feat(ed-89): insumos químicos de tratamento e reservatórios (SQL pendente) — parte 2/2"). Uso no ficheiro: `* o balanco operacional da ED-85 como volume autorizado nao faturado` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/cutoff/CutoffExecutionRule.java | esgoto | Introduzido com o ficheiro no commit `d17204ce` (2026-11-07, "feat(p3-4): corte e religacao como efeitos de ordem de servico (parte 1/5)"). Uso no ficheiro: `"Interrupcao de esgoto nao permitida (cut.sewer_allowed; Lei 11.445/2007, art. 40, V).");` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/cutoff/CutoffOrderTypes.java | ligacao de agua | Introduzido com o ficheiro no commit `80c93f48` (2026-11-07, "feat(p3-4): corte e religacao como efeitos de ordem de servico (parte 2/5)"). Uso no ficheiro: `RECONNECTION_TYPE_CODE, "Religacao de agua", "RECONNECTION", RECONNECT_EFFECT_CODE, EFFECT_SCHEMA,` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/cutoff/UndueCutCompensationRule.java | fatura | Introduzido com o ficheiro no commit `82f99930` (2026-11-07, "feat(p3-4): corte e religacao como efeitos de ordem de servico (parte 3/5)"). Uso no ficheiro: `throw new IllegalStateException("a formula exige a ultima fatura: sem ela, sem credito calculado");` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/effluent/RegulatoryObligation.java | esgoto | Introduzido com o ficheiro no commit `2f70e4e5` (2026-11-08, "feat(ED-105): efluente da ETE e lodo de ETA e ETE (SQL pendente) (parte 1/2)"). Uso no ficheiro: `*       do pacote de esgoto e agua bruta: os campos dessas ligacoes` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/fieldos/IdentityScan.java | hidrometro | Introduzido com o ficheiro no commit `f0c5c214` (2026-11-08, "feat(ed-20): OS em campo e acompanhamento de campo (SQL pendente)"). Uso no ficheiro: `* codigo de barras ou NFC do HIDROMETRO, do LACRE e da PLACA DO ATIVO,` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/incident/IncidentIndicators.java | esgoto | Introduzido com o ficheiro no commit `52833b00` (2026-11-08, "feat(ed-92): ocorrencia de rede como entidade (SQL pendente)"). Uso no ficheiro: `*       contagem de OVERFLOW e o denominador (km de esgoto dos` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/incident/NetworkIncident.java | esgoto | Introduzido com o ficheiro no commit `52833b00` (2026-11-08, "feat(ed-92): ocorrencia de rede como entidade (SQL pendente)"). Uso no ficheiro: `* Extensao de rede no esgoto (km) alimenta o denominador do` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/incident/OutageCreditRule.java | fatura | Introduzido com o ficheiro no commit `52833b00` (2026-11-08, "feat(ed-92): ocorrencia de rede como entidade (SQL pendente)"). Uso no ficheiro: `* Credito na fatura por interrupcao prolongada (RAS-29): regra` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inspection/CutConsumptionRule.java | ligacao de agua | Introduzido com o ficheiro no commit `7a2bd972` (2026-11-08, "feat(lote final): ED-24 (parte 44/59)"). Uso no ficheiro: `/** Situacao da ligacao de agua para esta regra (espelha o fato do cadastro/corte; nao redefine o dominio deles). */` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inspection/FraudRecord.java | hidrometro | Introduzido com o ficheiro no commit `7a2bd972` (2026-11-08, "feat(lote final): ED-24 (parte 44/59)"). Uso no ficheiro: `* partir do vocabulario do dominio (desvio, violacao de hidrometro,` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inspection/InfractionLifecycle.java | fatura | Introduzido com o ficheiro no commit `7a2bd972` (2026-11-08, "feat(lote final): ED-24 (parte 44/59)"). Uso no ficheiro: `* (catalogo {@code ReceivableSourceType} do faturamento, P2.5), com` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inspection/InfractionService.java | fatura | Introduzido com o ficheiro no commit `7a2bd972` (2026-11-08, "feat(lote final): ED-24 (parte 44/59)"). Uso no ficheiro: `* P2.30, OS no motor da P1.31 e debito no faturamento (recebivel de fonte` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inventory/InventoryItem.java | hidrometro | Introduzido com o ficheiro no commit `f1fa4217` (2026-11-08, "feat(ed-58): almoxarifado — materiais e estoque para a OS, com alcada e baixa automatica ("). Uso no ficheiro: `* PROPRIO do almoxarifado (materiais de rede, hidrometros como item` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/inventory/SerializedMeterTracking.java | hidrometro | Introduzido com o ficheiro no commit `f1fa4217` (2026-11-08, "feat(ed-58): almoxarifado — materiais e estoque para a OS, com alcada e baixa automatica ("). Uso no ficheiro: `* Rastreio do hidrometro SERIALIZADO no almoxarifado (ED-58): numero de` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticChargeCalculator.java | esgoto | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `* Cobranca complementar do esgoto nao domestico (aceite da ED-83,` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticChargeCalculator.java | fatura | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `* P2.5 o compoe na fatura como os demais componentes.` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticChargeModel.java | esgoto | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `* Modelo da cobranca complementar do esgoto nao domestico, conforme o` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticChargeModel.java | fatura | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `*       volume faturavel de efluente e o volume de agua multiplicado` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticParameters.java | esgoto | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `*       complementar do esgoto nao domestico (classe d):` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/nondomestic/NonDomesticService.java | esgoto | Introduzido com o ficheiro no commit `af046054` (2026-11-08, "feat(ED-83): esgoto nao domestico e carga poluidora do usuario (parte 1/2)"). Uso no ficheiro: `* Servico do esgoto nao domestico (ED-83): amarra o cadastro, a` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/RegulatoryContract.java | esgoto | Introduzido com o ficheiro no commit `5c9c8275` (2026-11-08, "feat(ed-96): instrumento contratual regulatório do prestador (SQL pendente)"). Uso no ficheiro: `* por servico — agua e esgoto sob o mesmo instrumento sao DOIS` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/RegulatoryContract.java | saneamento | Introduzido com o ficheiro no commit `5c9c8275` (2026-11-08, "feat(ed-96): instrumento contratual regulatório do prestador (SQL pendente)"). Uso no ficheiro: `* Servico prestado sob o instrumento. Componentes do saneamento` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/agenda/MunicipalObligationKinds.java | fatura | Introduzido com o ficheiro no commit `0bfee565` (2026-11-09, "feat(mm-06): agenda regulatoria municipal sobre a ED-98 (5 ficheiros)"). Uso no ficheiro: `* {@code ANNUAL_DISCHARGE_DECLARATION} (fatura do tenant),` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/agenda/ObligationCalendarService.java | fatura | Introduzido com o ficheiro no commit `02090935` (2026-11-09, "feat(core): ED-98 — agenda de obrigacoes regulatorias e de controle do tenant (SQL pendent"). Uso no ficheiro: `// Aceite: fatura de maio. O DIA exato nao tem fonte (D5: a` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/regulatory/agenda/ObligationRule.java | fatura | Introduzido com o ficheiro no commit `02090935` (2026-11-09, "feat(core): ED-98 — agenda de obrigacoes regulatorias e de controle do tenant (SQL pendent"). Uso no ficheiro: `* declaracao anual de quitacao na fatura de MAIO (mes conhecido, dia` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sac/Customer360View.java | fatura | Introduzido com o ficheiro no commit `db752384` (2026-11-08, "feat(lote final): ED-35, ED-69 (parte 36/59)"). Uso no ficheiro: `* Visao 360 do atendimento (ED-35): faturas, consumos, servicos (protocolos/OS)` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sac/NewConnectionWorkflowPort.java | esgoto | Introduzido com o ficheiro no commit `db752384` (2026-11-08, "feat(lote final): ED-35, ED-69 (parte 36/59)"). Uso no ficheiro: `* vistoria \u2192 execucao \u2192 [esgoto simultaneo, se houver rede e o parametro` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sac/kiosk/KioskTerminal.java | fatura | Introduzido com o ficheiro no commit `11f01e69` (2026-11-08, "feat(lote final): ED-39, ED-40 (parte 52/59)"). Uso no ficheiro: `* do portal (faturas, protocolos, dados do cliente) NUNCA sao acoes do` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sanitation/SanitationAspectCatalog.java | esgoto | Introduzido com o ficheiro no commit `e14ee3d5` (2026-11-08, "feat(ED-70): pacote ISO 14001 (SQL pendente)"). Uso no ficheiro: `/** Estacao de tratamento de esgoto (ETE). */` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sanitation/SanitationAspectCatalog.java | saneamento | Introduzido com o ficheiro no commit `e14ee3d5` (2026-11-08, "feat(ED-70): pacote ISO 14001 (SQL pendente)"). Uso no ficheiro: `* (templates) de aspecto nas tres areas do saneamento —, nao dado` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/sewerraw/IntakeRegistrationService.java | esgoto | Introduzido com o ficheiro no commit `386cec3a` (2026-11-08, "feat(ED-80): pacote de esgoto e água bruta (SQL pendente)"). Uso no ficheiro: `*       ABASTECIMENTO (captacao nao se liga a sistema de esgoto);</li>` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/watersystems/AnnualReportIssuanceService.java | fatura | Introduzido com o ficheiro no commit `1d9a9471` (2026-11-08, "feat(ED-79): sistemas, PSA, relatório anual e Carta de Serviços (SQL pendente) (parte 1/2)"). Uso no ficheiro: `* As mensagens legais DA FATURA seguem o mesmo motor: quando a fatura` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/watersystems/Ed79Coverage.java | esgoto | Introduzido com o ficheiro no commit `1d9a9471` (2026-11-08, "feat(ED-79): sistemas, PSA, relatório anual e Carta de Serviços (SQL pendente) (parte 1/2)"). Uso no ficheiro: `"entidade de sistema de abastecimento e de esgoto com componentes, populacao atendida, manancial e tratamento",` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/watersystems/Ed79Coverage.java | fatura | Introduzido com o ficheiro no commit `1d9a9471` (2026-11-08, "feat(ED-79): sistemas, PSA, relatório anual e Carta de Serviços (SQL pendente) (parte 1/2)"). Uso no ficheiro: `"envio do relatorio e mensagens legais da fatura pelo motor de mensagens legais com prova de emissao da P2.32 (CAD-96, RAS-04), se` |
| shared/ziipsan-core/src/main/java/br/com/ziipsan/core/watersystems/WaterSystem.java | esgoto | Introduzido com o ficheiro no commit `497c0914` (2026-11-08, "feat(ED-79): sistemas, PSA, relatório anual e Carta de Serviços (SQL pendente) (parte 2/2)"). Uso no ficheiro: `* Sistema de abastecimento (SAA) e sistema de esgoto (SES) — ED-79.` |

Nota de falso positivo, mantida da redação original: a palavra isolada
`ligação` NÃO é termo proibido. Ela ocorre no núcleo com o sentido de vínculo (ex.:
`JwtTenantFilter`, "sem ligação com o usuário autenticado"). Só a expressão
`ligação de água` é termo de pacote.

### 3.2 Exceções ArchUnit (classe origem → classe destino)

A regra ArchUnit original mantém-se: nenhuma classe de `core`, `platform` ou
`security` pode depender de classe de `br.com.ziipsan.commercial.fiscal..`
nem de qualquer pacote `..sanitation..`. As 42 violações do baseline são
todas internas ao próprio core: o catálogo de fábrica da ED-70 vive no core
num pacote chamado `br.com.ziipsan.core.sanitation` — o padrão
`..sanitation..` casa o nome do pacote do próprio núcleo, sem haver
dependência em código de pacote (não há nenhuma violação para
`commercial.fiscal` nem para pacote de vertical externo). Pares exactos
exceptuados:

| Classe origem | Classe destino | Justificativa |
|---|---|---|
| br.com.ziipsan.core.certification.env14001.EnvironmentalAspect | br.com.ziipsan.core.sanitation.SanitationAspectCatalog | ED-70 (commit `e14ee3d5`, 2026-11-08, "feat(ED-70): pacote ISO 14001"): o aspecto ambiental da certificação ISO 14001 no core resolve templates chamando `SanitationAspectCatalog.byCode(...)` — o catálogo é código do próprio core (pacote `br.com.ziipsan.core.sanitation`), não código de pacote externo; o padrão `..sanitation..` casa o nome do pacote do próprio núcleo. |
| br.com.ziipsan.core.certification.env14001.EnvironmentalAspect | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectTemplate | ED-70 (commit `e14ee3d5`): `EnvironmentalAspect` lê `area()`, `code()`, `aspectLabel()`, `impactLabel()` e `sludgeLinked()` de `AspectTemplate`, tipo aninhado do catálogo de fábrica do próprio core (ver linha anterior). |
| br.com.ziipsan.core.certification.env14001.EnvironmentalAspect | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectArea | ED-70 (commit `e14ee3d5`): o campo/parâmetro `area` de `EnvironmentalAspect` é tipado pelo enum aninhado `AspectArea` do catálogo de fábrica do próprio core (ver linhas anteriores). |
| br.com.ziipsan.core.sanitation.SanitationAspectCatalog | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectTemplate | ED-70 (commit `e14ee3d5`): dependência intra-classe do catálogo — `SanitationAspectCatalog` (core) declara/retorna os seus próprios `AspectTemplate` aninhados (`all()`, `byCode`, `byArea`, `sludgeTemplates`, campo `TEMPLATES`). Não é dependência do núcleo num pacote externo: origem e destino vivem no mesmo ficheiro do core. |
| br.com.ziipsan.core.sanitation.SanitationAspectCatalog | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectArea | ED-70 (commit `e14ee3d5`): dependência intra-classe do catálogo — métodos do catálogo recebem/devolvem o enum aninhado `AspectArea` e o inicializador estático lê as constantes `WTP`/`WWTP`/`NETWORK` desse enum. Origem e destino no mesmo ficheiro do core. |
| br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectTemplate | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectArea | ED-70 (commit `e14ee3d5`): o record aninhado `AspectTemplate` guarda a sua `area` no enum irmão `AspectArea` — tipos aninhados da mesma classe do core, no mesmo ficheiro. |
| br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectArea | br.com.ziipsan.core.sanitation.SanitationAspectCatalog$AspectArea | ED-70 (commit `e14ee3d5`): auto-referência do enum `AspectArea` — o tipo do campo `$VALUES` e o tipo de retorno de `values()`/`$values()` são o array do próprio enum, e `values()` chama `clone()` desse array. Não há dependência noutra classe. |

## 4. Pacote de exemplo

Só em teste (PLATAFORMA-E-VERTICAIS §6): o contrato de manifesto é exercitado por
manifestos sintéticos montados em `VerticalManifestTest` (validador rejeita rota
duplicada e manifesto sem `certification`). Não há pacote `example-vertical`
versionado no repositório — o teste sintético cumpre o papel sem carregar conteúdo
regulatório fictício.

## 5. P1.11 e P1.13 (frente plat-web): escrita condicional, idempotência e fronteiras de módulos

P1.11, superfície v0 — só o recurso de usuários (`/api/v1/users`, o único PUT
da app): GET único devolve `ETag: "v<version>"` (o `@Version` do `BaseEntity`);
PUT sem `If-Match` é 428 (`precondition-required`), com versão diferente da
atual é 412 (`precondition-failed`); `If-Match: *` aceita qualquer versão
existente. POST de criação com `Idempotency-Key` devolve a resposta gravada
(201, mesmo corpo) sem reexecutar, com escopo por tenant; sem a chave, o
comportamento é o de antes, e erro nunca é gravado. Componentes reutilizáveis
por outras frentes: `IdempotencyStore` e `ConditionalWrite` (módulo
`ziipsan-platform`, pacote `web`), e os tipos `ApiException.PreconditionRequired`
/ `PreconditionFailed`. Limites da v0: o store é em memória, por processo
(entre réplicas a mesma chave pode executar duas vezes); a generalização do
PUT condicional para os recursos do cadastro é das frentes donas de cada
controller, não desta. CORS: `Idempotency-Key`/`If-Match` liberados e `ETag`
exposto, sem os quais o navegador não usaria o contrato.

P1.13, suíte `ModuleBoundaryTest` (ArchUnit da P0.3, Surefire): núcleo só
depende de si; `platform` só do núcleo; domínio só do núcleo, de si e da
própria família (`commercial`, `intelligence`, `operations`, `grs`,
`compliance`); ninguém depende da `app`; nenhum nome de classe simples se
repete entre `billing` e `metering` (as duplicatas citadas na P1.13, ver
P0.25, não existem hoje — o teste falha se voltarem). A duplicata conhecida
`DmaDistrictRepository` (gis × losses) fica fora desta suíte de propósito:
ela é da P1.26, que a remove. A prova de violação proposital aplica a mesma
forma de regra ao par real gis → losses e exige `AssertionError`.

## 6. P1.38 — catálogo único de eventos de negócio

A seção `events` do `vertical.json` é o catálogo único: cada evento tem nome,
versão, esquema da carga (só identificadores e códigos, sem dado pessoal),
permissão exigida de quem recebe (dentro do catálogo da P0.13) e consumidores
declarados. Os consumidores declarados do pacote são `P3.36` (central de
avisos), `P3.5` e `ED-41` (notificações ao consumidor), `P2.26` (webhooks de
saída), `P2.28` (minhas tarefas) e `ED-07` (alertas do tenant) — só declarados,
não construídos nesta onda. O documento `docs/arquitetura/business-event-catalog.md`
é gerado por `scripts/generate-business-event-catalog.py` e conferido no CI por
`BusinessEventCatalogDocTest` (marcadores por evento, consumidor e lacuna).

Publicação: toda tarefa que publica eventos usa `BusinessEventPublisher`
(pacote `platform.infra.events`), que valida catálogo, versão e carga antes
de escrever na outbox da P1.9, com o tipo versionado (`nome@vN`); a
`PostgresMessageQueue` construída com catálogo aplica a mesma validação a
qualquer mensagem de evento, e recusa nome catalogado sem versão. Tipos que
não são eventos passam como transporte. Mudança incompatível de carga exige
nova versão do evento; a carga da versão nova é recusada na versão antiga.

Estado nesta onda: nenhum evento de negócio em produção (a outbox da P1.9 não
tem publicador em código de produção). O único tipo que aparece no código,
`billing.invoice.issued`, existe só nos testes da P1.9 e está registrado como
lacuna (`eventGaps`), não como evento — a tarefa que criar o publicador deve
declará-lo primeiro.
