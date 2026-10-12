# 18e — Aderência do Ziip SAN aos editais: faturamento, fiscal, arrecadação, cobrança e contabilidade/TCE

Data: 12/10/2026. Produto Ziip SAN (código interno SIGSAN, renomeado `ziipsan` na P0.33).

Escopo: requisitos dos editais de Videira/SC (VISAN, PE 06/2026: `18a`, IDs `TR-`; `18b`, IDs `ED-`) e de São Ludgero/SC
(SAMAE, PE 2/2026 "SIGCOS": `18c`, IDs `SG-`) que pertencem às áreas de faturamento (tarifas, estrutura tarifária,
tarifa social, ciclo e grupos, cálculo, contas, 2ª via, revisão e refaturamento, serviços avulsos, débito automático),
fiscal (retenções, NFAg-e, NFS-e, CBS/IBS), arrecadação (bancos, FEBRABAN, código de barras, Pix, cartão, conciliação,
baixa), cobrança (aviso, corte, religação, parcelamento, negociação, SPC/SERASA, protesto, dívida ativa) e integração
contábil e TCE/SC (e-Sfinge).

Fontes do estado do produto, lidas nesta data: `commercial/sigsan-billing` (motor de tarifa e conta, `V4_1`),
`commercial/sigsan-collection` (código de barras, retorno FEBRABAN 150, classificação de pagamentos, acréscimos, `V6_1`),
`commercial/sigsan-registry` (`V3_1`), `operations/sigsan-metering` (`V5_1`), os módulos vazios
`commercial/sigsan-accounting`, `commercial/sigsan-dunning` e `commercial/sigsan-portal`, a descrição em texto de
`platform/sigsan-platform/.../module/ModuleRegistry.java`, o plano `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` v2.2,
`docs/arquitetura/PARAMETROS-DO-TENANT.md`, `FISCAL-PORTE-JAVA.md`, os ADRs 0001–0022 e os levantamentos `02`, `03`,
`08` e `11`.

## Convenções

- **Uma linha por requisito.** Requisitos equivalentes dos dois editais (ou repetidos em telas diferentes do mesmo
  edital, como 2ª via no portal, no totem e no app) dividem a linha; todos os IDs aparecem na primeira coluna e cada ID
  é contado uma vez nos totais.
- **POC?** `sim` quando o requisito pontua na prova de conceito de algum edital. Entre parênteses, o código por edital
  (Videira/São Ludgero): `S` = citado no roteiro do Anexo II do TR; `a` = avaliável a critério da comissão (itens 5.1 a
  23.1, TR 3.2.8/3.2.9), sem pontuação própria; `n` = fora da POC; `C` = item crítico/eliminatório da POC do SG
  (Edital 7.7.9); `E` = essencial, conta nos 90% da POC; `A` = avaliável (seções 2.2, 2.23, 2.25); `–` = o edital não
  tem o requisito. No TR vale ainda a regra do item 4.19: 90% das funcionalidades de 5.1 a 23.1 nativas.
- **Código hoje.** `Atende` = o operador consegue executar o requisito no produto. `Parcial` = existe regra pura,
  tabela ou campo que cobre o núcleo do requisito, mas falta serviço, API, tela, lote ou parte da regra. `Não atende`
  = nada no código. Como faturamento e arrecadação **não têm serviço, API nem lote** (plano, §1; READMEs dos módulos) e
  cobrança, contabilidade e portal são módulos vazios, nenhum requisito desta área está em `Atende`.
- **Plano.** Tarefa e fase do plano v2.2. `Implícito em Px.y` = cabe no escopo da tarefa, mas o plano não o nomeia
  (risco de ficar de fora). `Em parte` = uma parte está planejada e outra não. `Não planejado` = nenhuma tarefa cobre.
  Fases: P0 13/10–27/11/2026; P1 30/11/2026–29/01/2027; P2 01/02–26/03/2027; P3 29/03–28/05/2027; go-live
  01/06–30/07/2027; P4 02/08–17/12/2027; 2028 sob demanda.
- **Esforço** (régua do plano, §4): `P` até 2 dias, `M` até 1 semana, `G` mais de 1 semana, para uma pessoa com agentes.
- **Ação** remete às entregas propostas `E1`–`E17` da seção 3 e às decisões `D1`–`D5` da seção 4.

## 1. Resumo

### 1.1 Quantidade de requisitos da área

| Edital | Requisitos da área | Na matriz principal (2.1–2.20) | Relatórios da área (2.21) |
|---|---|---|---|
| Videira/SC — TR (`18a`) | 412 | 325 | 87 |
| Videira/SC — edital e ETP (`18b`) | 3 | 3 | 0 |
| São Ludgero/SC — SIGCOS (`18c`) | 406 | 317 | 89 |
| **Total** | **821** | **645** | **176** |

A matriz tem 377 linhas. Painéis de BI e telas gerais de atendimento que só consomem dados desta área ficaram fora
da contagem e estão listados na seção 2.22.

### 1.2 Situação no código hoje (por requisito)

| Situação | Videira (TR + ED) | São Ludgero (SG) | Total |
|---|---|---|---|
| Atende | 0 | 0 | 0 |
| Parcial | 70 | 68 | 138 |
| Não atende | 345 | 338 | 683 |

### 1.3 Situação no plano (por requisito)

| Situação | Videira (TR + ED) | São Ludgero (SG) | Total |
|---|---|---|---|
| Planejado | 50 | 50 | 100 |
| Em parte | 91 | 102 | 193 |
| Implícito | 60 | 63 | 123 |
| Não planejado | 214 | 191 | 405 |

### 1.4 Leitura

1. **O que existe é o núcleo de cálculo.** Tarifa por faixa e categoria com vigência, rateio por dias, esgoto, tarifa
   social por bandas, débitos e créditos entre contas, retenção de tributos, multa e juros com trava de 2%, correção
   por índice, código de barras FEBRABAN (arrecadação e boleto), leitura do retorno FEBRABAN 150 e classificação de
   pagamentos em 15 situações. Por isso a maior parte dos itens de estrutura tarifária, cálculo e arrecadação está em
   `Parcial`. Nada disso é operável: não há serviço, API, tela nem lote.
2. **Retificação e manutenção de conta não estão no plano.** Os estados existem (`RECTIFIED`, `CANCELLED`, motivo,
   vencimento original), as operações não, e nenhuma tarefa as nomeia. São itens críticos da POC do SG e do roteiro
   do TR (Anexo II f e o).
3. **Dívida ativa, contabilidade, e-Sfinge, débito automático, cartão, protesto, revisão de consumo, fraude e
   situações especiais de faturamento não estão no plano.** SPC/SERASA está em 2028, sob demanda. No SG, cobrança
   (2.15, inclusive negativação, dívida ativa e protesto) é crítica e eliminatória; contabilidade (2.16) é essencial,
   com 2.16.18 crítico. No TR, o roteiro da POC cobra débito automático FEBRABAN (h), dívida ativa com inscrição, CDA
   e parcelamento (k) e contabilidade (l). Com o plano atual, o produto seria reprovado nas duas POCs.
4. **Calendário.** Mesmo o que está planejado chega tarde para editais deste tipo: arrecadação e Pix na P3
   (mai/2027), cobrança na P4 (dez/2027), negativação em 2028. Os dois editais pedem implantação completa em 60 a 90
   dias e POC em até 5 dias úteis após a adjudicação (TR 3.1.1 e 3.2; SG Edital 7). Disputar editais equivalentes
   antes de 2028 exige repriorizar as entregas da seção 3.
5. **Três requisitos colidem com norma e precisam de esclarecimento, não de código:** perda de benefício por faturas
   em atraso (TR-286: a Lei 14.898/2024 proíbe condicionar a tarifa social à adimplência); dias e horários de geração
   das ordens de corte (TR-415, SG-398: só dentro das vedações da Lei 14.015/2020, travadas no produto); débito em
   conta com recebedor pessoa jurídica (TR-339, SG-327: desde 13/10/2025 a autorização segue o regulamento do Pix
   Automático, Res. CMN 5.251/2025; relatório `11`, R15).

## 2. Matriz de aderência

Colunas: IDs · Requisito (resumo fiel) · POC? · Código hoje (evidência) · Plano · Lacuna / ação · Esforço.

### 2.1 Estrutura tarifária e parâmetros de faturamento

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-274 · SG-264 | Calcular água, serviço básico, esgoto (SG: e resíduos), parcelamentos e serviços, integrado à arrecadação e à contabilidade | sim (a/C) | **Parcial** — `BillCalculator` soma água, esgoto, débitos, créditos e retenções; sem resíduos, sem ligação com arrecadação e contabilidade, sem serviço | Em parte: P2.5 (ciclo e conta); contabilidade não planejada | Ciclo na P2.5; eventos contábeis na E12 | G |
| TR-283 · SG-273 | O usuário define e atualiza a estrutura tarifária (caput) | sim (a/C) | **Parcial** — `tariffs`, `tariff_versions`, `tariff_category_rules`, `tariff_blocks` (`V4_1`); sem API nem tela | P1.6 (assistente: tarifa com simulação aprovada); P0.19 (aprovação) | CRUD de tarifa com aprovação dupla e simulação de impacto | M |
| TR-284 · SG-274 | Cadastro de categoria de tarifa | sim (S/C) | **Parcial** — `categories`/`subcategories` (`V3_1`, com API do cadastro) e regra por categoria em `tariff_category_rules` (sem API) | P1.6; P1.3 | Regra por categoria na tela de tarifa | P |
| TR-285 · SG-275 | Tabela tarifária com vigência de água, esgoto e serviços | sim (S/C) | **Parcial** — `tariff_versions` com vigência e `EXCLUDE` contra sobreposição; serviços sem tabela | Em parte: P1.6; preços de serviço não planejados | Tela de versões; tabela de serviços na E5 | M |
| TR-287 · SG-276 · SG-142 | Cadastro de tipo de tarifa (classificação de cada tarifa) | sim (S/C) | **Não atende** — `tariffs` só tem código e nome | Implícito em P1.6 | Catálogo `tariff_type` em migration do billing | P |
| SG-097 | Cadastro de tarifas (catálogo do 2.8.10) | sim (–/C) | **Parcial** — mesmas tabelas do `V4_1`, sem tela | P1.6 | Idem TR-283 | P |
| TR-288 · SG-277 | Tarifa por categoria e tipo: descrição, % de esgoto sobre a água e tabela vigente | sim (S/C) | **Parcial** — `SewerParameters` aplica `pcesgoto`/`pccoleta`, mas como entrada da ligação, não gravada na tarifa | Implícito em P2.5 | Persistir % de esgoto por tarifa e categoria, com exceção por ligação | P |
| TR-289 · SG-278 | Faixas por tarifa: número, limite inferior e superior em m³, valor | sim (S/C) | **Parcial** — `tariff_blocks` e faixas progressivas no `TariffEngine`; sem tela | P1.6 | Tela de faixas; modo "bloco direto" só se a agência exigir (hoje recusado pelo `CHECK`) | P |
| TR-290 · SG-279 | Nova tabela tarifária a partir de uma existente | sim (a/C) | **Não atende** | Não planejado | Ação "copiar versão" com nova vigência (E2) | P |
| TR-291 · SG-280 | Ratear o consumo igualmente entre categorias quando há duas ou mais | sim (S/C) | **Parcial** — `TariffEngine` calcula por categoria e economia (`EconomyGroup`); `Apportionment` reparte centavos por maior resto | P2.5 | Teste com o caso de economias mistas do roteiro | P |
| TR-292 · SG-281 | Cálculo da fatura (água, esgoto; SG: resíduos sólidos) pela faixa e número de economias | sim (S/C) | **Parcial** — `TariffEngine` + `BillCalculator` cobrem água e esgoto, com rateio por vigência e `tariff_snapshot`; resíduos não existem (`service_kind` só `WATER`/`SEWER`) | Em parte: P2.5; resíduos não planejados | Ciclo na P2.5; resíduos na E15 | M |
| TR-286 | Quantidade de faturas em atraso que faz o usuário perder benefício (ex.: tarifa social) | não (a/–) | **Não atende** — por desenho, o motor não recebe situação de pagamento | Não planejado (vedado para tarifa social, Lei 14.898) | Esclarecimento ao órgão; admitir só para benefício local não social, com norma citada | P |
| TR-132 | Tipo de usuário Padrão/Baixa Renda para enquadramento na tarifa social | não (a/–) | **Parcial** — economias sociais (`EconomyGroup.socialEconomies`) e `tariff_social_bands`; sem enquadramento por CadÚnico/BPC | P1.5 (R3) | Concluir R3 | M |
| TR-295 · SG-284 | Gestão paramétrica das situações de faturamento (caput) | sim (a/C) | **Não atende** — não há tabela de parâmetros de faturamento (README do billing) | P0.19 (parâmetros do tenant) | Registrar as chaves das linhas abaixo no catálogo de parâmetros | M |
| TR-296 · SG-285 | Percentual de multa por atraso | sim (a/C) | **Parcial** — `collection_policies.fine_percent`, `FineRule` com trava de 2%; `LateFeePolicy` duplicada no billing | P0.20; P2.5 | Unificar as duas políticas (ADR-0015) | P |
| TR-297 · SG-286 | Percentual de juros diários | sim (a/C) | **Parcial** — `InterestRule` (juros simples pro rata die), `interest_*` em `collection_policies` | P0.20; P2.5 | Idem | P |
| TR-298 · SG-287 | Valor mínimo de fatura emitida individualmente | sim (a/C) | **Não atende** — `BillGenerationRule` não considera valor mínimo | Implícito em P2.5 | Parâmetro de valor mínimo e regra de arrasto | P |
| TR-299 · SG-288 | Valor mínimo de fatura no processamento do grupo | sim (a/C) | **Não atende** | Implícito em P2.5 | Idem, por ciclo | P |
| TR-300 · SG-289 | Dias mínimos para a primeira cobrança após ligação nova ou religação | sim (a/C) | **Não atende** | Implícito em P2.5 | Parâmetro e regra de geração | P |
| TR-301 · SG-290 | Mês/ano a partir do qual o histórico de leitura e consumo sai na fatura | sim (a/C) | **Não atende** | Implícito em P2.5 (`V4_4`, modelos de conta) | Parâmetro do modelo de conta | P |
| TR-302 · SG-291 | Cobrar ou não taxa de entrega para endereço alternativo | sim (a/C) | **Não atende** — o cadastro não tem endereço de entrega | Não planejado | Endereço de entrega (R2) e lançamento da taxa (E2) | P |
| TR-306 · SG-295 | Emitir mensagem de excesso de consumo quando acima da média | sim (a/C) | **Parcial** — catálogo `consumption_anomalies` (`V5_1`) guarda a mensagem na conta por tipo, inclusive consumo alto | Implícito em P2.5 | Levar a mensagem ao modelo de conta | P |

### 2.2 Ciclo, emissão e envio de documentos

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-275 · SG-265 | Cadastro de grupos de faturamento | sim (a/C) | **Não atende** — só rotas de leitura (`reading_routes`, `V3_1`) | P2.5 (`V4_3`, ciclo) | Grupo com rotas, vencimentos e cronograma | M |
| SG-134 | Cadastro de tipo de grupo de faturamento | sim (–/C) | **Não atende** | Implícito em P2.5 | Catálogo junto do grupo | P |
| TR-277 · SG-267 | Cronograma mensal por grupo e localidade (massa de leitura, leitura, processamento, entrega, vencimento) | sim (a/C) | **Não atende** | P2.5 (ciclo) com o painel de fechamento N6 | Cronograma com estados e trava de fechamento | M |
| TR-278 · SG-268 | Adequar-se ao calendário e à forma de faturamento atuais do órgão | sim (a/C) | **Não atende** | Implícito em P1.1 (discovery) e P2.5 | Levantar no discovery do piloto | P |
| TR-279 · SG-269 | Gestão de feriados nacionais, estaduais, municipais e pontos facultativos | sim (a/C) | **Parcial** — tabela `holidays` (`V6_1`) e `BusinessCalendar`; sem ponto facultativo nem tela | P0.19 (`holidays` passa ao `V1_5`) | Tipo "ponto facultativo" | P |
| TR-280 · SG-270 | Mensagens na conta: excesso de consumo, débitos vencidos, quitação anual, valor zero, débito automático | sim (S/C) | **Não atende** — só a mensagem por anomalia de consumo | Implícito em P2.5 (`V4_4`) | Cadastro de mensagens por tipo e vigência | M |
| TR-281 · SG-271 | Agendar faturamento em lote; arquivo para gráfica e arquivo de débito em conta por grupo e rota | sim (a/C) | **Não atende** | Em parte: P2.1 (lote) e P2.8 (gráfica); débito em conta não planejado | Arquivo de débito depende da D2 (E8) | G |
| TR-541 · TR-542 | Selecionar várias faturas para impressão ou envio por e-mail | não (a/–) | **Não atende** — não há PDF nem e-mail | Implícito em P2.17 (PDF) e P3.5 (notificações) | Ação em lote no atendimento | P |
| TR-570 · SG-546 | Enviar uma ou várias faturas por e-mail | sim (a/C) | **Não atende** | Implícito em P3.5 | Envio pela outbox com PDF anexo | P |
| TR-571 · SG-547 | Enviar valor, vencimento e código de barras por SMS (SG: e WhatsApp) | sim (a/C) | **Não atende** | Implícito em P3.5 | Canal SMS/WhatsApp do omnichannel | P |
| TR-206 | Enviar faturas e comunicado de débito por e-mail (módulo de leitura) | sim (S/–) | **Não atende** | Implícito em P3.5 | Idem | P |
| TR-567 · SG-544 | 2ª via com as mesmas características da original (atendimento) | sim (a/C) | **Não atende** — sem gerador de PDF; `tariff_snapshot` permite reproduzir o cálculo | Em parte: P2.17 (PDF) e P3.3 (portal); backoffice não citado | 2ª via no backoffice | M |
| TR-568 · SG-550 | Fatura espelho / 2ª via de fatura quitada, sem código de barras, com mensagem de quitação | sim (a/C) | **Não atende** | Em parte: P3.20 (conta paga com quitação no portal) | Estender ao backoffice | P |
| TR-554 · SG-530 | Pesquisar faturas da unidade por situação (pendente, quitada, cancelada, parcelada, em processo, em dívida ativa) | sim (a/C) | **Parcial** — `BillStatus` com 14 estados; faltam "em processo" e "em dívida ativa"; sem API | Implícito em P2.5 | API de consulta e estados que faltam | P |
| TR-564 · SG-541 | Emitir fatura antes do processamento do grupo, com cálculo prévio para confirmação | sim (a/C) | **Não atende** — o motor calcula conta avulsa, não há fluxo | Implícito em P2.5 | Emissão individual fora do lote | M |
| TR-565 · SG-542 | Emitir fatura que não foi gerada no processamento do grupo | sim (a/C) | **Não atende** — o estado `PROCESSING_ERROR` admite reprocessar | Implícito em P2.5 | Idem | P |
| TR-569 · SG-545 · TR-843 · SG-608 · SG-620 · SG-642 · SG-682 · SG-722 | Simular o valor da fatura sem emitir (atendimento, portal, totem, app, chatbot e IA) | sim (S/E) | **Parcial** — `TariffEngine` é função pura e serve de simulador | Em parte: P1.5 (simulador N4); canais não citados | Expor no atendimento e nos canais | M |
| SG-110 | Cadastro de motivo de emissão de fatura | sim (–/C) | **Não atende** | Não planejado | E1 | P |
| SG-116 | Cadastro de motivo de situação de fatura | sim (–/C) | **Não atende** | Não planejado | E1 | P |
| SG-129 | Cadastro de tipo de consumo faturado | sim (–/C) | **Parcial** — `consumption_type` em `V5_1` (real, média, informado etc.) como enum fixo | Implícito em P2.5 | Catálogo descritivo sobre o enum | P |

### 2.3 Retificação e manutenção da conta

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-307 · SG-296 | Retificações com estorno e reemissão por processo paramétrico e autorização (caput) | sim (a/C) | **Parcial** — estados `RECTIFIED` e `CANCELLED_BY_RECTIFICATION`, `origin_bill_id`, `rectification_count`; nenhuma operação ("só os estados existem", README do billing) | Não planejado | E1 | G |
| TR-308 · SG-297 | Incluir, manter e excluir serviços a faturar com rastreio, competência original e motivo | sim (a/C) | **Parcial** — `debit_receivables` (tipo, parcelas, cancelamento) e `bill_debits`; sem motivo nem fluxo | Não planejado | E1 | M |
| TR-309 · SG-298 · TR-555 · SG-531 | Cancelar fatura com motivo, detalhe e anotação (usuário, data, parecer) | sim (S/C) | **Parcial** — estado `CANCELLED` e `cancel_reason`; sem operação; auditoria genérica por gatilho | Não planejado | E1 | M |
| TR-310 · SG-299 · TR-556 · SG-532 | Alterar vencimento com motivo e anotação | sim (S/C) | **Parcial** — `due_date`, `original_due_date`, `due_change_count`; sem operação | Não planejado | E1 | P |
| TR-311 · SG-300 · TR-557 · SG-533 | Alterar a leitura da fatura com motivo e anotação | sim (S/C) | **Não atende** — o motor recalcula, não há fluxo de retificação | Não planejado | E1 | M |
| TR-312 · SG-301 · TR-558 · SG-534 | Alterar o consumo faturado com motivo e anotação | sim (S/C) | **Não atende** | Não planejado | E1 | M |
| TR-313 · SG-302 · TR-560 · SG-536 | Recalcular a fatura pela situação cadastral atual | sim (a/C) | **Não atende** | Não planejado | E1 | M |
| TR-559 · SG-535 · TR-561 · SG-537 | Alterar e atualizar serviços faturados numa fatura, com anotação | sim (a/C) | **Parcial** — `bill_debits` | Não planejado | E1 | P |
| TR-562 · SG-538 | Retirar a cobrança de água ou de esgoto de uma fatura, com anotação | sim (a/C) | **Não atende** | Não planejado | E1 | P |
| TR-314 | Lançar crédito numa fatura existente; a sobra vira desconto na próxima | não (a/–) | **Parcial** — `CreditApplier` e `credit_receivables.residual_amount` fazem a regra; não há operação de lançamento | Não planejado | E1 | P |
| TR-316 · SG-304 · SG-540 | Situação "pagamento a confirmar" quando o banco remeterá o pagamento em data futura | sim (a/C) | **Não atende** | Não planejado | E1 | P |
| TR-317 · SG-305 · TR-573 · SG-549 | Fatura em processo administrativo ou judicial: não gera aviso nem corte | sim (a/C) | **Parcial** — `bills.review_reason`/`reviewed_on`, `debit_receivables.under_review` | P4.1 (contestação bloqueia corte) | Antecipar o bloqueio para a P3.4, junto do corte | P |
| TR-609 · SG-589 | Serviço faturado: ver, alterar vencimento, reimprimir e cancelar o documento de cobrança | sim (a/C) | **Não atende** | Não planejado | E1 e E5 | P |

### 2.4 Situações especiais de faturamento

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-276 · SG-266 · TR-323 · SG-310 · TR-587 · SG-564 | Unidades isentas; registrar e excluir isenção de água, esgoto (SG: e resíduos) ou serviço | sim (a/C) | **Não atende** — não há campo de isenção | Não planejado | E2 | P |
| TR-294 · SG-283 | Cobrança a quem não tem imóvel (circo, parque, vistoria, parecer, limpeza de fossa) | sim (S/C) | **Não atende** — `debit_receivables.unit_id` é obrigatório | Em parte: P3.6 (só a NFS-e avulsa) | Guia avulsa por cliente (E2) | M |
| TR-337 | Faturas de serviços desvinculadas da fatura de água e esgoto | não (a/–) | **Não atende** — o único documento é a conta | Não planejado | E2 | M |
| SG-324 | Fatura de resíduos sólidos desvinculada da de água e esgoto | sim (–/C) | **Não atende** | Não planejado | E15 | M |
| SG-325 | Carnê de resíduos sólidos com parcelas mensais ajustáveis ou parcela anual | sim (–/C) | **Não atende** | Não planejado | E15 | M |
| TR-318 · SG-306 | Faturas agrupadas para órgãos públicos ou particulares | sim (a/C) | **Não atende** | Não planejado | E2 | M |
| TR-319 | Fatura única do órgão centralizador: um código de barras quita todas as faturas vinculadas | não (a/–) | **Não atende** — `payments.document_type` é polimórfico, mas não há documento agrupador | Não planejado | E2 com quitação em cascata (E7) | M |
| TR-618 · SG-599 · TR-619 · SG-600 | Tela do órgão centralizador (faturas, unidades); reimprimir e alterar vencimento da fatura centralizada | sim (a/C) | **Não atende** | Não planejado | E2 | M |
| TR-320 · SG-307 | Conta final (antecipa a leitura; reúne débitos vencidos e a vencer; SG: no desligamento) | sim (a/C) | **Não atende** | Não planejado | E2 | M |
| TR-324 · SG-311 | Contrato especial de fornecimento: consumo fixo, mínimo e máximo, valor fixo | sim (a/C) | **Não atende** — `NonMeteredConsumption` cobre consumo fixado, não contrato | Não planejado | E2 | M |
| SG-183 | Faturas de condomínio com medição individualizada pela regra do órgão | sim (–/C) | **Parcial** — `CondominiumApportionment` (billing e metering) rateia o consumo macro/micro; rateio de valor fora do cálculo | Implícito em P2.5 | Fechar o rateio de valor | M |
| TR-326 · SG-313 | Gestão de imóveis alugados (caput) | sim (a/C) | **Parcial** — vínculo temporal `unit_customers` (proprietário, usuário, responsável; início e fim) | Em parte: P1.3 (cadastro) | E2 | M |
| TR-327 · SG-314 | Contrato de locação com vigência e responsável; ao fim, a responsabilidade volta ao proprietário | sim (a/C) | **Parcial** — `unit_customers` com datas; sem contrato nem troca automática | Não planejado | E2 | M |
| TR-328 · SG-315 | Inquilino negocia só os débitos do seu período; SG: transferência automática ao fim | sim (a/C) | **Não atende** — a conta não grava o responsável da competência | Não planejado | Gravar o responsável na conta (E2) | M |
| TR-329 · SG-316 | Prorrogação do contrato de locação | sim (a/C) | **Não atende** | Não planejado | E2 | P |
| TR-330 · SG-317 | Histórico de responsáveis e contratos da ligação | sim (a/C) | **Parcial** — `unit_customers` guarda o histórico | P1.3 | Tela de histórico | P |
| TR-143 | Cadastro próprio por usuário, cobrança por CPF | não (a/–) | **Parcial** — `customers` com documento cifrado e índice cego | P1.3 | — | P |
| TR-585 · SG-562 | Atualizar categoria, tipo de tarifa e economias da unidade | sim (a/C) | **Parcial** — `unit_subcategories` com economias, na API do cadastro; tipo de tarifa não existe | P1.3 | Ligar a unidade ao tipo de tarifa | P |
| TR-588 · SG-565 | Dia de vencimento alternativo, entre as opções do grupo | sim (a/C) | **Parcial** — `consumer_units.due_day`, `customers.due_day`, `DueDateCalculator`; opções por grupo não existem | P2.5 (parâmetro `bill.due_date_options`) | Opções por grupo | P |
| TR-777 · SG-1004 | Emitir termo de adesão ao serviço no cadastro da unidade | sim (a/C) | **Não atende** | Não planejado | Modelo de documento (E1) | P |

### 2.5 Revisão de consumo e desconto por vazamento

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-245 · SG-242 | Geração e gestão de revisão de consumo (caput) | sim (S/C) | **Não atende** | Não planejado (a P3.4 traz só o protocolo) | E3 | G |
| TR-246 · SG-243 | Processo on-line ou presencial, pedido pelo usuário ou pelo operador, com protocolo | sim (S/C) | **Não atende** | Em parte: protocolo na P3.4; processo não planejado | E3 | M |
| TR-247 · SG-244 | Formulário de adesão com dados mínimos (caput) | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-248 · SG-245 | Controle por ano/número | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-249 · SG-246 | Mês/ano de referência da revisão | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-250 · SG-247 | Cadastro do usuário e matrícula | sim (a/C) | **Parcial** — dado existe no cadastro; formulário não | Não planejado | E3 | P |
| TR-251 · SG-248 | Endereço completo | sim (a/C) | **Parcial** — idem | Não planejado | E3 | P |
| TR-252 | Número de economias | não (a/–) | **Parcial** — idem (`unit_subcategories`) | Não planejado | E3 | P |
| TR-253 · SG-249 | Data da solicitação | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-254 · SG-250 | Nome do proponente | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-255 · SG-251 | Quantidade de moradores | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-256 · SG-252 | Informação de piscina | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-257 · SG-253 | Máquina de lavar roupas | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-258 · SG-254 | Caixa d'água | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-259 · SG-255 | Fonte alternativa de água | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-260 · SG-256 | Descrição da ocorrência | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-261 | Leituras e consumos dos últimos meses | não (a/–) | **Parcial** — `meter_readings` e `consumption_records` (`V5_1`) | Não planejado | E3 | P |
| TR-262 · SG-257 | Classificar a revisão por fase; cada etapa gera registro consultável | sim (a/C) | **Não atende** | Não planejado | E3 | M |
| TR-263 · SG-258 | Formulário impresso com os dados da revisão e campos adicionáveis | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-264 | Formulário: local do vazamento | não (a/–) | **Não atende** | Não planejado | E3 | P |
| TR-265 | Formulário: materiais substituídos ou reparados | não (a/–) | **Não atende** | Não planejado | E3 | P |
| TR-266 | Formulário: indícios de que o vazamento foi resolvido | não (a/–) | **Não atende** | Não planejado | E3 | P |
| TR-267 | Formulário: método de detecção do vazamento | não (a/–) | **Não atende** | Não planejado | E3 | P |
| TR-268 | Formulário: anexos (fotos) | não (a/–) | **Não atende** — há pipeline de arquivos só para suporte (P1.20) | Não planejado | E3 | P |
| TR-269 · SG-259 | Revisão em dispositivo móvel, sem papel, como as OS | sim (a/C) | **Não atende** | Não planejado | E3 com o app de campo | M |
| TR-270 · SG-260 | Agenda de visitas técnicas da revisão | sim (a/C) | **Não atende** | Não planejado | E3 | M |
| TR-271 · SG-261 | Adequar-se aos processos atuais sem perdas nem custos ao órgão | sim (a/C) | **Não atende** | Não planejado | E3, a partir do discovery | P |
| TR-272 · SG-262 | Alterar as faturas revisadas dentro do processo, conforme permissão | sim (a/C) | **Não atende** | Não planejado | E3 sobre a E1 | M |
| TR-273 · SG-263 | Enviar ao usuário a fatura alterada e o laudo (TR: e-mail/WhatsApp e consulta no app; SG: e-mail) | sim (a/C) | **Não atende** | Não planejado | E3 com P3.5 | P |
| TR-303 · SG-292 | Percentual máximo de desconto por vazamento de água | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-304 · SG-293 | Percentual máximo de desconto por vazamento de esgoto | sim (a/C) | **Não atende** | Não planejado | E3 | P |
| TR-305 · SG-294 | Regra do desconto: percentual ou média dos últimos seis meses | sim (a/C) | **Não atende** — a média existe na medição (`AverageConsumption`) | Não planejado | E3 | P |
| TR-315 · SG-303 · TR-563 · SG-539 | Recalcular fatura por anormalidade ou vazamento oculto pelos parâmetros, com desconto calculado e impresso | sim (S/C) | **Não atende** — a medição detecta anomalias (`ConsumptionAnomalyKind`), não há recálculo com desconto | Não planejado | E3 | M |

### 2.6 Controle de fraudes

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-331 · SG-318 | Gestão do controle de fraudes (caput) | sim (a/C) | **Não atende** | Não planejado | E4 | M |
| TR-332 · SG-319 | Tipos de fraude e graus (leve a gravíssimo) | sim (a/C) | **Não atende** | Não planejado | E4 | P |
| TR-333 · SG-320 | Tabela de multa por tipo e grau de fraude | sim (a/C) | **Não atende** | Não planejado | E4 | P |
| TR-334 · SG-321 | OS automática de fiscalização para ligações cortadas há N dias | sim (a/C) | **Não atende** — não há módulo de OS | Não planejado | E4 sobre a OS da P3.4 | M |
| TR-335 · SG-322 | No encerramento da OS: tipo de fraude, fotos, boletim de ocorrência e multa lançada na próxima fatura | sim (a/C) | **Não atende** — `debit_receivables` receberia o lançamento | Não planejado | E4 | M |
| TR-336 · SG-323 | Relatório de fraudes por período (quantidade, usuários, valores) | sim (a/C) | **Não atende** | Não planejado | E4 | P |
| TR-325 · SG-312 | OS automática de fiscalização de consumo por critério do usuário | sim (a/C) | **Não atende** | Não planejado | E4 | M |

### 2.7 Serviços e materiais faturáveis

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-500 · SG-453 | Tipo de faturamento do serviço (obrigatório, opcional, variável, não faturável) | sim (S/C) | **Não atende** — não há catálogo de serviços | Não planejado (a P3.4 é OS mínima de corte) | E5 | P |
| TR-503 · SG-456 | Número máximo de parcelas do serviço | sim (S/C) | **Não atende** — `Installments` divide, sem parâmetro por serviço | Não planejado | E5 | P |
| TR-504 · SG-457 | Cobrança de juros no serviço | sim (S/C) | **Não atende** | Não planejado | E5 | P |
| SG-458 | Incidência de imposto no serviço | sim (–/C) | **Não atende** | Implícito em P3.6 (NFS-e de serviços avulsos) | E5 | P |
| TR-505 · SG-459 | Cobrança de parcela à vista no serviço | sim (S/C) | **Não atende** | Não planejado | E5 | P |
| TR-506 · SG-460 | Percentual mínimo da parcela à vista do serviço | sim (S/C) | **Não atende** | Não planejado | E5 | P |
| TR-512 · SG-466 | Rubricas contábeis do serviço (TR: várias por serviço) | sim (S/C) | **Não atende** | Não planejado | E5 com E12 | P |
| TR-530 · SG-487 | Tabela de preços de serviços com vigência | sim (a/C) | **Não atende** | Não planejado | E5 | M |
| TR-535 · SG-497 | Faturamento de materiais usados nos serviços (caput) | sim (a/C) | **Não atende** | Não planejado | E5 | M |
| TR-536 · SG-498 | Definir quais serviços cobram materiais | sim (a/C) | **Não atende** | Não planejado | E5 | P |
| TR-537 · SG-499 | Tabela de preços de materiais com vigência | sim (a/C) | **Não atende** | Não planejado | E5 | P |
| TR-538 · SG-500 | Cobrar materiais na fatura conforme o encerramento da OS | sim (a/C) | **Não atende** — `debit_receivables` receberia o lançamento; falta a origem | Não planejado | E5 | P |
| SG-358 | Parcelar no carnê a parcela à vista de um serviço | sim (–/C) | **Não atende** | Não planejado | E5 com E9 | P |

### 2.8 Leitura e emissão simultânea (parte de faturamento)

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-221 · SG-220 | Faturamento em campo on-line e off-line em dispositivo móvel | sim (a/C) | **Não atende** | Em parte: P4.4 (app de leitura off-line), sem emissão | E6 | G |
| TR-223 · SG-222 | Calcular a fatura em campo (água, esgoto; SG: resíduos), com dedução de impostos | sim (S/C) | **Parcial** — motor em Java puro, sem I/O, reutilizável; não há app | Em parte: P4.4 | E6 | G |
| TR-224 · SG-223 | Impressão térmica em bobina branca, com leiaute desenhado na impressão | sim (S/C) | **Não atende** | Não planejado | E6 | M |
| TR-225 · SG-224 | Fatura em campo com código de barras FEBRABAN e dados mínimos | sim (S/C) | **Parcial** — `CollectionBarcode` gera as 44 posições e a linha digitável; não há documento | Em parte: conta impressa na P2.8/P2.17; em campo não | E6 | M |
| TR-226 · SG-225 | QR Code Pix na fatura de campo e nos documentos de pagamento, sem pagamento em duplicidade (SG: QR obrigatório, Pix como integração futura) | sim (a/C) | **Não atende** — Pix não implementado (README da collection) | Em parte: P3.2 (Pix Cobrança); em campo não | E6 sobre a P3.2 | M |
| TR-227 · SG-226 | Comunicado de débito após a fatura, com alerta de corte e faturas pendentes | sim (a/C) | **Não atende** | Não planejado | E6 | M |
| TR-228 · SG-227 | Comunicado de excesso de consumo após a fatura (SG: com retenção) | sim (a/C) | **Parcial** — `ConsumptionCalculator` detecta consumo alto; não há comunicado | Não planejado | E6 | P |
| TR-242 · SG-240 | Corrigir leitura em campo e reimprimir a fatura | sim (a/C) | **Não atende** | Não planejado | E6 | P |
| TR-244 · SG-241 | Comunicado de vencimento de benefício (tarifa social, ligação provisória) | sim (a/C) | **Não atende** | Em parte: P1.5 (aviso na conta antes da perda, `social.loss_notice_months`); em campo não | E6 | P |
| TR-207 | Imprimir o comunicado de débito a qualquer momento após a entrega em campo | não (a/–) | **Não atende** | Não planejado | E6 | P |

### 2.9 Fiscal

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-282 · SG-272 | Cadastro de impostos com tabela de vigência de alíquotas | sim (a/C) | **Parcial** — `tax_withholding_rates` com vigência e `EXCLUDE`, só para retenção; CBS/IBS não | P2.4 (CBS/IBS com vigência, `V4_5`) | Tributos destacados além dos retidos | M |
| TR-293 · SG-282 | Estratificar retenções na fatura de entes federais; TR: mensagem de imunidade recíproca (CF art. 150, VI, "a") nas faturas de órgãos públicos | sim (a/C) | **Parcial** — `TaxWithholding` e `bill_taxes` (ordem, base, alíquota, valor); falta perfil de retenção por cliente-órgão e a mensagem | Em parte: P1.16 e P2.5 (tributo na conta) | Perfil "órgão retentor" no cliente; mensagem no modelo de conta | P |
| TR-1074 · SG-1282 | Adequações legais sem custo; nesta área: NFAg-e (modelo 75), NFS-e de serviços, destaque de CBS/IBS, cashback e split payment | sim (n/C) | **Não atende** — `commercial/sigsan-fiscal` ainda não existe | Em parte: P0.32, P1.8, P2.4, P2.6, P3.6; cashback e split payment não planejados | Ver quadro 2.9.1 | G |

#### 2.9.1 Obrigações fiscais que os editais exigem só por via da cláusula de adequação legal

Não contadas à parte (já estão em TR-1074 e SG-1282). Fonte: relatório `11`.

| Obrigação | Base e data | Código hoje | Plano |
|---|---|---|---|
| NFAg-e (modelo 75), DANFAG com QR Code | Ato Conjunto RFB/CGIBS 4/2026; obrigatória em 01/12/2026, a confirmar no DOU | não existe | P2.6 sobre o motor portado (ADR-0003) |
| NFS-e Nacional para serviços (religação, ligação nova, vistoria) | LC 214/2025; campos IBS/CBS desde 01/10/2026 | não existe | P1.8 (motor), P3.6 (avulsa) |
| Destaque de CBS e IBS na conta | EC 132; CBS plena e IBS 0,05% + 0,05% em 01/01/2027 | não existe | P2.4 |
| Cashback de CBS/IBS para famílias do CadÚnico | LC 214 arts. 112–118; início não confirmado | não existe | não planejado |
| Split payment na liquidação de boleto e Pix | LC 214 arts. 31–36; data de operação não confirmada | não existe | não planejado |

### 2.10 Arrecadação

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-338 · SG-326 | Integração com agentes arrecadadores: recepção, tratamento, baixa e lançamentos contábeis (caput) | sim (a/C) | **Parcial** — parser e validador FEBRABAN 150, classificador e tabelas `V6_1`; nenhum serviço grava `payments` nem baixa a conta | Em parte: P3.1; contabilidade não planejada | P3.1 e E12 | G |
| TR-340 · SG-328 | Importar o retorno FEBRABAN e vincular cada pagamento ao documento (débito em conta ou pagamento comum, incluindo QR Code e Pix), sem perder registro | sim (S/C) | **Parcial** — `Febraban150ReturnParser`/`Validator` (A/G/Z, NSA, trailer, coerência com o código de barras), `bank_return_files` idempotente por hash e NSA; sem CNAB 240 nem Pix | P3.1, P3.2 | Serviço de importação; CNAB 240 conforme a pendência G4 | M |
| TR-341 · SG-329 | Baixa manual (confirmação de pagamento) com os dados do pagamento | sim (a/C) | **Parcial** — `payments` aceita pagamento sem arquivo de retorno; não há operação | P3.1 (ajuste com aprovação dupla, N3) | — | P |
| TR-342 · SG-330 | Identificar e corrigir pagamentos não vinculados a matrícula ou documento | sim (S/C) | **Parcial** — `PaymentClassifier` marca `DOC_NOT_FOUND`; coluna `identified`; não há fila de correção | P3.1 | Fila de exceções com sugestão de vínculo | M |
| TR-343 · SG-331 | Pagamento em duplicidade gera ocorrência e crédito nas próximas faturas | sim (a/C) | **Parcial** — situação `DUPLICATE`, `duplicate_winner`, `adjustment_kind = CREDIT_TO_REALIZE`; falta criar o `credit_receivables` | P3.1 | Ligar o ajuste ao crédito a realizar | P |
| TR-344 · SG-332 | Pagamento com valor diferente gera crédito ou débito da diferença | sim (a/C) | **Parcial** — `EXCESS`/`AMOUNT_MISMATCH`, tolerâncias por tenant, `adjustment_kind` | P3.1 | Idem, com débito a cobrar | P |
| TR-345 · SG-333 | Pagamento de documento com várias faturas (aviso de corte, 2ª via agrupada, termo de confissão) quita todas | sim (a/C) | **Não atende** — não há documento agrupador | Não planejado | E7 | M |
| TR-346 · SG-334 | Pagamento em atraso gera juros, multa e correção conforme regulamento ou agência | sim (a/C) | **Parcial** — `SurchargeCalculator` (multa com trava, juros pro rata die, correção por índice, carência em dias úteis); falta lançar na próxima conta | P2.5, P3.1 | Lançamento como débito a cobrar | P |
| TR-347 · SG-335 | Cadastro de agentes, agências, convênio com vigência e tarifa bancária | sim (a/C) | **Parcial** — `collection_agreements` com vigência e leiaute do campo livre; sem agência nem tarifa | P3.1 (parâmetros G4) | Agência e tarifa por convênio | P |
| SG-131 | Cadastro de tipo de convênio bancário | sim (–/C) | **Parcial** — `document_kind` (`COLLECTION`, `BANK_SLIP`) | P3.1 | Catálogo descritivo | P |
| TR-348 · SG-336 | Estorno de pagamento indevido | sim (a/C) | **Não atende** — há situação de devolução, não a operação | P3.1 (N3 cita estorno) | — | P |
| SG-111 | Cadastro de motivo de estorno de pagamento | sim (–/C) | **Não atende** | Implícito em P3.1 | Catálogo | P |
| TR-349 · SG-337 | Transferência de pagamento entre unidades | sim (a/C) | **Não atende** | Implícito em P3.1 | Operação com aprovação dupla | P |
| SG-119 | Cadastro de motivo de transferência de pagamento | sim (–/C) | **Não atende** | Implícito em P3.1 | Catálogo | P |
| TR-350 · SG-338 | Fechamento mensal agendado da arrecadação, com posição congelada (pendentes, pagos, parcelados, cancelados, emissões manuais) | sim (a/C) | **Não atende** | Não planejado | E7 com E12 | M |
| TR-351 · SG-339 | Integração com VAN, com busca automática e intervalo parametrizável (SG: condicional à contratação) | sim (a/C) | **Não atende** | Não planejado | E7 | M |
| TR-352 · SG-340 | Registrar pagamento por depósito bancário | sim (a/C) | **Não atende** | Não planejado | E7 | P |
| TR-353 · SG-341 | Tratar depósito bancário não identificado | sim (a/C) | **Não atende** | Não planejado | E7 | P |
| TR-374 | Parcelamento por cartão de crédito ou débito com integração on-line com a operadora | não (a/–) | **Não atende** — só o estado `CREDIT_CARD` em `BillStatus` | Não planejado | E14 (D3) | M |

### 2.11 Débito automático

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-339 · SG-327 | Gerar automaticamente o arquivo de débito em conta no padrão FEBRABAN e enviar aos bancos | sim (S/C) | **Não atende** — o README da collection exclui o débito automático (registros B/C/E/F) "como padrão" (relatório `11`, R15) | Não planejado (P4.5 traz o Pix Automático) | E8 (D2) | M |
| TR-321 · SG-308 · TR-566 · SG-543 | Reenviar uma fatura específica para débito em conta | sim (a/C) | **Não atende** | Não planejado | E8 | P |
| SG-115 | Cadastro de motivo de reenvio de fatura para débito em conta | sim (–/C) | **Não atende** | Não planejado | E8 | P |
| TR-322 · SG-309 | Tratar cadastro de débito em conta não identificado e associá-lo a uma unidade | sim (a/C) | **Não atende** | Não planejado | E8 | P |
| SG-122 | Cadastro de ocorrência de débito em conta | sim (–/C) | **Não atende** | Não planejado | E8 | P |
| TR-583 · SG-560 | Cadastrar (SG) e excluir a unidade como optante de débito em conta | sim (a/C) | **Não atende** | Não planejado | E8 | P |
| TR-354 · SG-342 | Mais de um convênio de débito em conta no mesmo banco | sim (a/C) | **Não atende** — `collection_agreements` aceita vários convênios por banco, mas não há tipo "débito automático" | Não planejado | E8 | P |
| SG-748 | Pedir débito em conta pelo app | sim (–/C) | **Não atende** | Não planejado | E8 com o app | P |

### 2.12 Parcelamento e negociação

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-355 · SG-343 | Recuperação de receita integrada à arrecadação (caput) | sim (a/C) | **Não atende** — `commercial/sigsan-dunning` vazio | P4.1 (R8) | E9 antecipa | G |
| TR-356 · SG-344 | Política de negociação paramétrica, com autorizações, única para todos os usuários | sim (a/C) | **Não atende** | P4.1 | Política com vigência no envelope de parâmetros (ADR-0002) | M |
| TR-357 · SG-345 | Valor mínimo para parcelar e número máximo de parcelas | sim (a/C) | **Não atende** | Implícito em P4.1 | Parâmetro da política | P |
| TR-358 · SG-346 | Percentual mínimo da entrada | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-359 · SG-347 | Valor mínimo da parcela | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-360 · SG-348 | Entrada obrigatória ou não | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-361 · SG-349 | Prazo máximo para pagar a entrada | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-362 · SG-350 | Dias para cancelar automaticamente o parcelamento sem entrada paga | sim (a/C) | **Não atende** | Implícito em P4.1 | Rotina diária no lote | P |
| TR-363 | Desfazer o parcelamento por entrada ou N parcelas não pagas, com prazos parametrizáveis | não (a/–) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-364 · SG-351 | Desconto máximo no parcelamento para tarifa social e outros benefícios | sim (a/C) | **Não atende** | Implícito em P4.1 | Parâmetro da política | P |
| TR-365 · SG-352 | Responsável pelo órgão impresso no termo de negociação | sim (a/C) | **Não atende** | Implícito em P4.1 | Modelo do termo | P |
| TR-366 · SG-353 | Percentual de juros (TR) ou de multa (SG) do parcelamento | sim (a/C) | **Parcial** — `InterestRule`/`FineRule` calculam; a política de parcelamento não existe | Implícito em P4.1 | Parâmetro da política | P |
| TR-367 · SG-354 | Quantidade máxima de reparcelamentos por usuário | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-368 · SG-355 | Parcelas máximas no reparcelamento de quem tem infração | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-369 · SG-356 | Parcelas máximas no reparcelamento de quem não tem infração | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-370 · SG-357 | Entrada mínima no reparcelamento de quem tem infração | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-371 | Parcelas na fatura mensal ou em carnê (boleto) | não (a/–) | **Parcial** — parcelas na conta via `debit_receivables`; carnê não existe | Em parte: P4.1 (parcelas na conta); carnê não citado | Carnê sobre `BankSlipBarcode` (E9) | M |
| TR-372 | Relatório de acompanhamento dos parcelamentos por carnê | não (a/–) | **Não atende** | Não planejado | E9 | P |
| TR-373 | Controle da negociação pelo usuário do sistema que a fez | não (a/–) | **Parcial** — `created_by` e auditoria em toda tabela de tenant | Implícito em P4.1 | — | P |
| TR-375 · SG-359 · SG-360 | Parcelar débitos pendentes (faturas e serviços) conforme a política, com juros e multa do regulamento ou da agência | sim (S/C) | **Parcial** — encargos (`SurchargeCalculator`) e divisão em parcelas (`Installments`, centavos na última); sem política nem fluxo | P4.1 | E9 antecipa | G |
| TR-376 · SG-361 | Juros, multa e correção nas parcelas a vencer | sim (a/C) | **Parcial** — `SurchargeCalculator` | P4.1 | — | P |
| TR-377 · SG-362 | Escolher as faturas (vencidas ou não) e serviços que entram no parcelamento | sim (S/C) | **Não atende** | P4.1 | — | M |
| TR-378 · SG-363 | Transferir débito de outra unidade a partir do parcelamento | sim (a/C) | **Não atende** | Implícito em P4.1 | — | M |
| TR-379 · SG-364 | Termo impresso para assinatura, com cobrança da entrada; reemissão com os dados originais | sim (a/C) | **Não atende** | Implícito em P4.1 ("demonstrativo") | Modelo do termo | M |
| TR-778 · SG-1005 | Termo de confissão de dívida emitido automaticamente no parcelamento | sim (a/C) | **Não atende** | Implícito em P4.1 | Idem | P |
| TR-380 · SG-365 | Faturas parceladas passam de pendentes a parceladas | sim (a/C) | **Parcial** — estados `INSTALLMENT` e `INSTALLMENT_DOWN_PAYMENT` em `BillStatus`; situação `DOC_PARCELED` no pagamento | P4.1 | — | P |
| TR-381 · SG-366 | Cobrar as parcelas automaticamente nas próximas faturas | sim (a/C) | **Parcial** — `debit_receivables` e `bill_debits` levam parcelas às contas seguintes | P4.1 | — | P |
| TR-779 · SG-1006 | Parcelar faturas e dívidas em faturas de parcelamento ou em lançamentos nas faturas normais | sim (a/C) | **Parcial** — idem; fatura de parcelamento avulsa não existe | P4.1 | — | M |
| TR-382 · SG-367 | Emitir faturas avulsas de parcelamento | sim (a/C) | **Não atende** | Implícito em P4.1 | Guia avulsa (E2) | M |
| TR-383 · SG-368 | Cancelar um parcelamento | sim (a/C) | **Não atende** — `debit_receivables` aceita `CANCELLED` | Implícito em P4.1 | Desfazimento com reabertura das faturas | M |
| TR-384 · SG-369 | Reparcelamento com os mesmos recursos (caput) | sim (a/C) | **Não atende** | Implícito em P4.1 | — | M |
| TR-385 · SG-370 | Saldo do parcelamento em aberto trazido a valor presente e incorporado | sim (a/C) | **Parcial** — `SurchargeCalculator` atualiza valores; saldo de parcelamento não existe | Implícito em P4.1 | — | P |
| TR-386 · SG-371 | Incluir novas faturas e serviços no reparcelamento | sim (a/C) | **Não atende** | Implícito em P4.1 | — | P |
| TR-387 · SG-372 | Recalcular multas e juros a valor presente | sim (a/C) | **Parcial** — `SurchargeCalculator` | Implícito em P4.1 | — | P |
| TR-388 · SG-373 | Antecipar parcelas, descontando juros e multa | sim (a/C) | **Não atende** | Implícito em P4.1 | — | P |
| TR-389 · SG-374 | Simular parcelamento ou reparcelamento sem efetivar | sim (a/C) | **Não atende** | Implícito em P4.1 | Simulação reaproveitada nos canais | P |
| SG-127 | Cadastro de tipo de cobrança | sim (–/C) | **Não atende** | Implícito em P4.1 | Catálogo | P |

### 2.13 Cartas, notificações, corte e religação

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-390 · SG-375 | Cartas de cobrança com filtros (imóvel, rotas, referência, valor, quantidade de faturas, meses de atraso) | sim (a/C) | **Não atende** | Não planejado (a P3.5 é o envio com prova, não a seleção) | E9 | M |
| TR-391 · SG-376 | Geração paramétrica das notificações de corte (caput) | sim (a/C) | **Não atende** | P3.4 (aviso com prova e bloqueio; parâmetros G9 `cut.*`) | — | M |
| TR-392 · SG-377 | Número mínimo de faturas vencidas para notificar | sim (a/C) | **Não atende** | Implícito em P3.4 | Parâmetro G9 | P |
| TR-393 · SG-378 | Valor mínimo da dívida para notificar | sim (a/C) | **Não atende** | P3.4 (`cut.min_debt_amount`) | — | P |
| TR-394 · SG-379 | Dias mínimos de atraso para notificar | sim (a/C) | **Não atende** | P3.4 (`cut.min_overdue_days`) | — | P |
| TR-395 · SG-380 | Notificar débitos só de outros serviços, sem água | sim (a/C) | **Não atende** | Implícito em P3.4 (`cut.third_party_debt_blocks`, padrão "não") | Respeitar a norma da agência | P |
| TR-396 · SG-381 | Dias para o vencimento da notificação de corte | sim (a/C) | **Não atende** | P3.4 (`cut.min_notice_days`, piso de 30 dias, Lei 11.445 art. 40 §2º) | O piso legal é travado | P |
| TR-397 · SG-382 | Mensagem impressa na notificação | sim (a/C) | **Não atende** | Implícito em P3.4 | Modelo de aviso | P |
| TR-398 · SG-383 | Cobrar ou não pela emissão da notificação, e o valor | sim (a/C) | **Não atende** | Não planejado | Conferir a norma da agência antes de permitir | P |
| TR-399 · SG-384 | Notificar quem tem cobranças diversas atrasadas, não só faturas | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-400 · SG-385 | Permitir uma ou mais notificações pendentes por usuário | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-401 · SG-386 | Notificação para unidades com ligação já cortada | sim (S/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-402 · SG-387 | Imprimir ou não o código de barras FEBRABAN na notificação | sim (a/C) | **Parcial** — `CollectionBarcode` | Implícito em P3.4 | Notificação como documento pagável (E7) | P |
| TR-403 | Avisar pendências por SMS, WhatsApp ou e-mail antes do aviso de corte | não (a/–) | **Não atende** | Implícito em P3.5 | — | P |
| TR-404 · SG-388 | Geração automática da notificação junto da massa de leitura, para imprimir com a fatura em campo | sim (a/C) | **Não atende** | Não planejado | E6 | M |
| TR-405 | Consultar notificação por vários filtros e reimprimir com os dados originais | sim (S/–) | **Não atende** | Implícito em P3.4 | — | P |
| SG-389 | Alterar o vencimento de notificação pendente, gerando OS de registro | sim (–/C) | **Não atende** | Não planejado | E9 | P |
| TR-406 · SG-390 | Cancelar notificação pendente, gerando OS de registro (usuário, data, parecer) | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-407 · SG-391 | Ciclo do corte: notificação e desdobramento em OS de corte | sim (a/C) | **Não atende** | P3.4 (`V8_1`, `V9_1`) | — | M |
| TR-408 · SG-392 | Políticas de geração das OS de corte (caput) | sim (a/C) | **Não atende** | P3.4 | — | M |
| TR-409 · SG-393 | Prioridade das OS de corte (localização, vencimento, maior valor, tarifa, categoria, perfil do devedor) | sim (a/C) | **Não atende** | Não planejado | E9 | P |
| TR-410 · SG-394 | Quantidade máxima de OS de corte por dia | sim (a/C) | **Não atende** | Não planejado | E9 | P |
| TR-411 · SG-395 | Valor mínimo da notificação para gerar OS de corte | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-412 · SG-396 | Código do serviço gerado para a ordem de corte | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-413 | Filtrar e ordenar ordens de corte abertas | não (a/–) | **Não atende** | Implícito em P3.4 | — | P |
| TR-414 · SG-397 | Mensagem de comunicação do corte impressa na OS | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-415 · SG-398 | Dias da semana e horários de geração das ordens de 1º, 2º e 3º nível | sim (a/C) | **Não atende** | P3.4 (vedações de dia e janela `cut.business_hours`) | Aceitar só dentro das vedações da Lei 14.015, travadas | P |
| TR-416 · SG-399 | Geração automática das OS de corte pelas regras | sim (a/C) | **Não atende** | P3.4 | — | M |
| TR-417 · SG-400 | Ao menos dois níveis de corte sucessivos (cavalete, ramal), com calendário por nível | sim (a/C) | **Não atende** | Não planejado | E9 | M |
| TR-418 · SG-401 | Bloquear notificação ou ordem de corte da unidade por motivo (tutela judicial, serviço essencial), com OS de registro | sim (a/C) | **Não atende** | P3.4 (bloqueio; classes protegidas `cut.protected_classes`) | — | P |
| TR-590 · SG-567 · SG-571 | Alterar a permissão de gerar notificação e ordem de corte da unidade | sim (a/C) | **Não atende** | Implícito em P3.4 | — | P |
| TR-593 · SG-570 | Histórico de cortes e religações da unidade | sim (a/C) | **Não atende** | P3.4 (`V9_1`) | — | P |
| TR-529 · SG-486 | Ordens de corte de certos clientes só para equipes especiais | sim (a/C) | **Não atende** | Não planejado | E9 | P |
| TR-539 · SG-501 | Registrar a execução por nível de corte (cavalete, ramal, supressão) | sim (a/C) | **Não atende** | P3.4 | — | M |
| TR-540 · SG-502 | Registrar a execução da religação | sim (a/C) | **Não atende** | P3.4 (parâmetros `rec.*`) | — | M |
| SG-503 | Registrar corte de ligação de esgoto | sim (–/C) | **Não atende** | Não planejado | E9; `cut.sewer_allowed` tem padrão "não" (Lei 11.445 art. 40) | P |
| SG-504 | Registrar religação de ligação de esgoto | sim (–/C) | **Não atende** | Não planejado | E9 | P |
| SG-736 | Pedir religação de água pelo app | sim (–/C) | **Não atende** | Não planejado | Canal do app | P |
| TR-581 · SG-558 | Comunicado de cobrança de esgoto com texto parametrizado | sim (a/C) | **Não atende** | Não planejado | E9 | P |

### 2.14 Negativação (SPC/SERASA) e protesto

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| SG-402 | Política de geração automática do arquivo de negativação no SPC e SERASA, atualizável | sim (–/C) | **Não atende** — só a descrição em `ModuleRegistry` | Não planejado até 2028 ("sob demanda"; a P4.1 é "sem negativação") | E10 (D4) | G |
| SG-403 | Critério: número mínimo de faturas ou parcelas vencidas | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-404 | Critério: valor mínimo da dívida | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-405 | Critério: dias mínimos de atraso | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-406 | Critério: ciclo, bairro, localidade | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-407 | Arquivos de negativação automáticos integrados ao SPC e ao SERASA (caput) | sim (–/C) | **Não atende** | Não planejado | E10 | M |
| SG-408 | Movimento de inadimplência instantâneo; controle de envio, retorno e pagamento | sim (–/C) | **Não atende** | Não planejado | E10 | M |
| SG-409 | Acompanhar inclusões, cancelamentos e exclusões no SPC e SERASA | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-410 | Excluir o registro automaticamente após quitação, renegociação ou parcelamento | sim (–/C) | **Não atende** | Não planejado | E10 (baixa em até 5 dias úteis, Súmula 548 do STJ) | P |
| SG-411 | Simular um lote de negativação, com desfazer ou efetivar | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| SG-548 | Excluir uma fatura registrada no SPC ou SERASA (atendimento) | sim (–/C) | **Não atende** | Não planejado | E10 | P |
| TR-572 | Excluir uma fatura registrada em cartório de protesto ou outro meio de cobrança legal | não (a/–) | **Não atende** | Não planejado | E10 | P |
| SG-424 | Notificação extrajudicial e envio automático para protesto das faturas em dívida ativa | sim (–/C) | **Não atende** | Não planejado | E10 (protesto de CDA: Lei 9.492/1997, alterada pela Lei 12.767/2012) | M |
| SG-425 | Integração com o sistema de cobrança do cartório: envio e baixa automática | sim (–/C) | **Não atende** | Não planejado | E10 | M |

### 2.15 Dívida ativa

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-419 | Módulo de controle de dívida ativa; módulo de notificações (caput) | não (a/–) | **Não atende** — só a descrição em `ModuleRegistry` | Não planejado | E11 (D1) | G |
| SG-412 | Processo administrativo com passos obrigatórios e facultativos até a cobrança | sim (–/C) | **Não atende** | Não planejado | E11 | M |
| TR-420 | Selecionar contribuintes em débito, individual ou coletivamente, para notificar | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-421 | Simular a notificação com crítica cadastral | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-422 | Filtros da simulação e da notificação (imóvel, vencimento, valor, setor, grupo, rota, categoria, esfera) | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-423 | Exportar a simulação em CSV, com ou sem acréscimos | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-424 | Notificação com código de barras, agrupando as contas | não (a/–) | **Parcial** — `CollectionBarcode`; não há documento agrupador | Não planejado | E11 com E7 | P |
| TR-425 | Manter as notificações e registrar o status da entrega | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-426 · SG-415 | Aviso/notificação de débito com proprietário, base legal, valor inscrito e atualização (juros, multa, correção) | sim (a/C) | **Parcial** — atualização por `SurchargeCalculator`; documento não existe | Não planejado | E11 | M |
| TR-427 | Aviso de débito em moeda corrente ou índice | não (a/–) | **Parcial** — `CorrectionRule` e `surcharge_indexes` | Não planejado | E11 | P |
| TR-428 | Atualizar por índice as parcelas já geradas e reemiti-las | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-429 | Tela externa para o contribuinte consultar e imprimir o extrato de débitos | não (a/–) | **Não atende** | Não planejado | E11 com o portal (P3.3) | P |
| TR-430 | Situações da notificação: gerada, entregue, dívida ativa, certidão, execução, cancelada, negociada (confirmada ou não), paga | não (a/–) | **Não atende** | Não planejado | E11 | M |
| TR-431 | Ignorar a notificação quando o cadastro está incompleto | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-432 | Imprimir as notificações | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-433 · SG-416 | Registrar a entrega da notificação (pessoalmente, edital ou AR; SG: data de recebimento) | sim (a/C) | **Não atende** | Não planejado | E11 | P |
| TR-434 | Cancelar notificações de dívida ativa | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-435 | Extrato de débito recalcula os dias excedentes quando pago depois da data de cálculo | não (a/–) | **Parcial** — `SurchargeCalculator` com data de avaliação explícita | Não planejado | E11 | P |
| TR-436 | Módulo de inscrição (caput) | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-437 · SG-413 | Inscrição em dívida ativa: individual por contas selecionadas (TR); de todos os débitos vencidos no período (SG) | sim (S/C) | **Não atende** — `BillStatus` não tem "inscrita" | Não planejado | E11 | M |
| TR-438 | Congelamento periódico e inscrição automática após o prazo da notificação | sim (S/–) | **Não atende** | Não planejado | E11 | M |
| TR-439 | Módulo de execução (caput) | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-440 | Filtrar e gerar certidões de dívida ativa, separadas por tarifas e taxas | não (a/–) | **Não atende** | Não planejado | E11 | M |
| TR-441 | Imprimir as certidões | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-442 | Execução fiscal das dívidas | não (a/–) | **Não atende** | Não planejado | E11 (D1) | M |
| TR-443 · SG-414 | Livro da dívida ativa: número, páginas, ordem cronológica, localização de cada débito | sim (a/C) | **Não atende** | Não planejado | E11 | M |
| TR-444 | Guias de honorários, custas processuais e outros serviços | não (a/–) | **Não atende** | Não planejado | E11 com E2 | P |
| TR-445 · SG-417 | CDA e petição pelos modelos do órgão (SG: certidão após a notificação), com bloqueio das contas | sim (S/C) | **Não atende** | Não planejado | E11 | M |
| SG-426 | Gerar livro, notificação e certidão em etapas separadas, conforme a necessidade | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| SG-418 | Manipular lançamentos inscritos (baixa, cancelamento, reabertura) | sim (–/C) | **Não atende** | Não planejado | E11 | M |
| SG-423 | Excluir uma fatura da dívida ativa individualmente | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| SG-428 | Dívidas ajuizadas não entram nos livros seguintes; acompanhamento delas | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| TR-446 | Módulo de gestão da dívida ativa (caput) | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-447 | Separar a receita de dívida ativa na contabilidade | não (a/–) | **Não atende** | Não planejado | E11 com E12 | P |
| TR-448 | Bloquear parcelamento conjunto de contas inscritas e não inscritas | sim (S/–) | **Não atende** | Não planejado | E11 com E9 | P |
| SG-419 | Parcelar débitos inscritos em dívida ativa | sim (–/C) | **Não atende** | Não planejado | E11 com E9 | M |
| SG-420 | Termo de confissão de dívida ativa para pagamento das faturas inscritas | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| SG-427 | Atualizar a dívida ativa parcelada a partir das parcelas da certidão | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| TR-449 | Avisar, na consulta da ligação, que há contas em execução fiscal | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-450 | Saldo mensal da dívida ativa e sua composição (inscrições, cancelamentos, parcelamentos, reaberturas, receita), com fechamento controlado | não (a/–) | **Não atende** | Não planejado | E11 | M |
| TR-451 | Relatório analítico do saldo da dívida ativa | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-452 | Auditoria de todas as alterações no módulo | não (a/–) | **Parcial** — `audit_log` por gatilho em toda tabela registrada com `register_tenant_table` | Não planejado | Vem pronto quando o módulo existir | P |
| TR-453 | Cadastro de telas, grupos e usuários | não (a/–) | **Parcial** — papéis no Keycloak; gestão de usuários por tenant na P0.13 | P0.13 | Permissões do módulo | P |
| TR-454 | Configurar impontualidade, tipos de débito, livro e certidões | não (a/–) | **Parcial** — impontualidade em `collection_policies` | Não planejado | E11 | P |
| TR-455 | Relatório de parcelamento da dívida ativa | sim (S/–) | **Não atende** | Não planejado | E11 | P |
| TR-456 | Imprimir o livro da dívida ativa | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-457 | Eventos da dívida ativa congelados; relatório ou consulta entre duas datas (caput) | não (a/–) | **Não atende** | Não planejado | E11 | M |
| TR-458 | Saldo inicial na data inicial | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-459 | Soma de acréscimos e diminuições no intervalo | não (a/–) | **Não atende** | Não planejado | E11 | P |
| TR-460 | Saldo final na data final | não (a/–) | **Não atende** | Não planejado | E11 | P |
| SG-421 | Relatório de valores inscritos por período | sim (–/C) | **Não atende** | Não planejado | E11 | P |
| SG-422 | Relatório de pagamentos de lançamentos inscritos | sim (–/C) | **Não atende** | Não planejado | E11 | P |

### 2.16 Contabilidade

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-461 · SG-429 | Módulo de contabilidade integrado a faturamento e arrecadação, com lançamentos por evento conforme o plano de contas (caput) | sim (a/E) | **Não atende** — `commercial/sigsan-accounting` vazio; `ModuleRegistry` só descreve | Não planejado | E12 (D5) | G |
| TR-462 · SG-430 | Cadastro do plano de contas (ativo, passivo, receitas) | sim (S/E) | **Não atende** | Não planejado | E12 | M |
| TR-463 · SG-431 | Contabilização ativa: lançamento parametrizado gerado na mesma transação (faturamento, cancelamento, refaturamento, parcelamento, recebimento) | sim (S/E) | **Não atende** | Não planejado | E12 (outbox na mesma transação, ADR-0014) | G |
| TR-464 · SG-432 | Fechamento contábil mensal agendado, com débito e crédito e posição congelada | sim (a/E) | **Não atende** | Não planejado | E12 sobre o lote da P2.1 | M |
| TR-465 · SG-433 | Contas a receber de dívida ativa contabilizadas à parte | sim (a/E) | **Não atende** | Não planejado | E12 com E11 | M |
| SG-434 | Registro contábil automático dos tributos retidos, pela arrecadação | sim (–/E) | **Não atende** — retenção existe em `bill_taxes` | Não planejado | E12 | P |
| TR-466 · SG-435 | Provisão para devedores duvidosos sem afetar o saldo comercial | sim (a/E) | **Não atende** | Não planejado | E12 | M |
| TR-467 · SG-436 | Histórico textual dos lançamentos pré-configurável | sim (a/E) | **Não atende** | Não planejado | E12 | P |
| TR-468 · SG-437 · ED-078 | Integração diária automática dos lançamentos com o sistema contábil existente do órgão | sim (a/E) | **Não atende** | Não planejado | E12 (arquivo ou API do sistema contábil do cliente) | M |
| TR-469 · SG-438 | Bloquear serviço operacional sem conta contábil configurada | sim (a/E) | **Não atende** | Não planejado | E12 com E5 | P |
| TR-470 · SG-439 | Consultas e relatórios de controle com rastreio analítico; a contratada explica divergências de saldo | sim (a/E) | **Não atende** | Não planejado | E12 | M |
| TR-471 · SG-440 | Relatório sintético da contabilização do faturamento por conta contábil | sim (a/E) | **Não atende** | Não planejado | E12 | P |
| TR-472 · SG-441 | Relatório de parcelamentos (usuário, valor, faturas originais, parcelas, juros a faturar) | sim (a/E) | **Não atende** | Não planejado | E12 com E9 | P |
| TR-473 · SG-442 | Relatório analítico da provisão de devedores duvidosos | sim (a/E) | **Não atende** | Não planejado | E12 | P |
| TR-474 · SG-443 | Posição das contas a receber por usuário, documento a documento (saldo contábil e financeiro) | sim (a/E) | **Não atende** | Não planejado | E12 | M |
| TR-475 · SG-444 | Resumo dos lançamentos: saldos consolidados por conta (anterior, faturado, pago, parcelado, cancelado, devolvido, imposto, atual) | sim (a/E) | **Não atende** | Não planejado | E12 | M |
| SG-445 | Relatório mensal de unidades PJ com dedução de impostos retidos na fatura | sim (–/E) | **Não atende** — dados em `bill_taxes` | Não planejado | E12 | P |
| SG-446 · SG-447 | Importar e exportar lançamentos contábeis por arquivo ou integração direta | sim (–/C) | **Não atende** | Não planejado | E12 | M |
| TR-155 | Integração entre o cadastro técnico (ativos) e o sistema contábil | não (a/–) | **Não atende** | Não planejado | E12, depois do cadastro técnico | M |

### 2.17 TCE/SC e-Sfinge

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-476 · ED-077 | Integração com os órgãos fiscalizadores, atuais e futuros, em especial o TCE/SC pelo e-Sfinge | não (a/–) | **Não atende** — só a descrição em `ModuleRegistry` | Não planejado | E13 (D5) | G |
| TR-477 | Envio dos dados tributários ao e-Sfinge conforme a legislação do TCE/SC (caput dos registros) | não (a/–) | **Não atende** | Não planejado | E13 | M |
| TR-478 | Registro Tributário.estorno | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-479 | Registro Tributário.diário_arrecadação | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-480 | Registro Tributário.crédito_tributário_baixa | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-481 | Registro Tributário.crédito_tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-482 | Registro Tributário.contas_receber_trib_itens | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-483 | Registro Tributário.contas_receber_tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-484 | Integração com o TCE/SC para os envios abaixo (caput) | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-485 | Cadastro de contribuinte | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-486 | Cadastro imobiliário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-487 | Cadastro de propriedade imobiliária | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-488 | Lançamento de crédito tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-489 | Baixa de crédito tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-490 | Diário de arrecadação tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-491 | Estorno de diário de arrecadação tributário | não (a/–) | **Não atende** | Não planejado | E13 | P |
| TR-492 | Tempestividade das remessas; dados idênticos aos dos sistemas de origem | não (a/–) | **Não atende** | Não planejado | E13 (gerar a remessa do mesmo fechamento congelado da E7/E12) | M |
| TR-493 | Implementar as regras de consistência (CONs) do TCE/SC; vedado alterar dado contábil fora das normas | não (a/–) | **Não atende** | Não planejado | E13 | M |
| TR-494 | Resolver problemas de remessa com rapidez; a contratada responde pelas penalidades do TCE | não (a/–) | **Não atende** | Não planejado | Cláusula de risco; ver D5 | P |
| ED-019 | Habilitação: atestado com relatórios de envio de dados ao e-Sfinge do TCE/SC | não (habilitação) | **Não atende** — sem cliente em produção | Não planejado | Depende da E13 e de um cliente em SC; impugnar por restringir a competição (`18b`, B2) | G |

### 2.18 Autoatendimento: funções desta área nos canais

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-660 · TR-840 · TR-858 · SG-605 · SG-617 · SG-641 · SG-673 · SG-717 · SG-730 | 2ª via de fatura no portal, totem, app, omnichannel e IA (completa ou simplificada; várias de uma vez) | sim (S/C) | **Não atende** — `commercial/sigsan-portal` vazio | Em parte: P3.3 (portal); totem, app e omnichannel não citados | Mesma API de 2ª via para todos os canais | M |
| SG-731 | 2ª via de boleto de cobrança no app | sim (–/C) | **Não atende** | Implícito em P3.3 | — | P |
| TR-859 | QR Code Pix de uma fatura específica no omnichannel | sim (S/–) | **Não atende** | P3.2, P3.3 | — | P |
| TR-860 · SG-640 | Extrato de débitos e pendências da unidade (SG: inclui cobrança de lixo) | sim (S/C) | **Não atende** | Implícito em P3.3 e P3.20 | — | P |
| SG-718 | Débitos do responsável (CPF/CNPJ), inclusive os negativados, com 2ª via | sim (–/C) | **Não atende** | Em parte: P3.20 (vários imóveis por conta); negativação não planejada | — | P |
| TR-842 · TR-864 · SG-607 · SG-619 · SG-644 · SG-677 · SG-745 | Simular e pedir parcelamento nos canais digitais (portal, totem, app, omnichannel, IA) | sim (S/E) | **Não atende** | Em parte: P4.1 (parcelamento); canais não citados | E9 com a API dos canais | M |
| TR-844 · SG-609 · SG-621 · SG-723 | Declaração (certidão) anual de quitação de débitos | sim (S/E) | **Não atende** | Não planejado | E17; obrigação legal do prestador (Lei 12.007/2009) | P |
| SG-724 | Certidão negativa de débitos | sim (–/C) | **Não atende** | Não planejado | E17 | P |
| TR-849 · TR-863 · SG-613 · SG-624 · SG-766 | Consultar a tabela tarifária e de serviços vigente | sim (a/E) | **Parcial** — tabelas de tarifa existem; serviços não | Não planejado | E17 | P |
| SG-716 | Histórico de faturas dos últimos 12 meses (usuário autenticado) | sim (–/C) | **Não atende** | P3.20 (120 meses) | — | P |
| SG-747 | Pedir prazo de pagamento | sim (–/C) | **Não atende** | Não planejado | E17 sobre a E1 | P |
| SG-749 | Alterar o formato de entrega da fatura | sim (–/C) | **Não atende** | Não planejado | E17 | P |
| SG-750 | Contestar fatura | sim (–/C) | **Não atende** | Em parte: P4.1 (contestação bloqueia corte); canal não citado | E17 | P |
| SG-755 | Faturamento por autoleitura | sim (–/C) | **Não atende** — `meter_readings.source` não tem autoleitura | Não planejado | E17 com a medição | M |
| SG-764 | Copiar o código de barras no app | sim (–/C) | **Parcial** — linha digitável gerada por `CollectionBarcode` | Não planejado | E17 | P |
| SG-765 | Compartilhar o PDF das faturas e documentos | sim (–/C) | **Não atende** | Não planejado | E17 | P |
| SG-774 | Notificação push sobre emissão de documentos de cobrança | sim (–/C) | **Não atende** | Não planejado | E17 | P |

### 2.19 a 2.20

As seções 2.19 (catálogos) e 2.20 (documentos) foram incorporadas às tabelas de cada tema: catálogos do SG 2.8.10
(SG-097, SG-110, SG-111, SG-115, SG-116, SG-119, SG-122, SG-127, SG-129, SG-131, SG-134, SG-142) e termos de adesão e de
confissão (TR-777, SG-1004, TR-778, SG-1005) aparecem junto do processo a que pertencem.

### 2.21 Relatórios da área

Uma linha por tema; todos os relatórios de um tema têm a mesma situação, porque dependem do mesmo módulo. Cada ID é
contado uma vez. A POC do TR pede 13 relatórios escolhidos pela comissão (Anexo II r); no SG os relatórios da seção 2.28
são críticos.

| IDs | Requisito | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|
| TR-666 a TR-680 · TR-770 · SG-892 a SG-906 · SG-997 | Arrecadação e retorno bancário: quitadas (SG: com impostos), status da importação (detalhado e resumido), comparação, extrato, não identificados, remessa, sucesso, diário, compatibilidade, transferidos, validação do arquivo, depósitos não identificados, duplicidade, por data de remessa, pagamento, crédito e banco | sim (S/C) | **Não atende** — dados em `payments` e `bank_return_files`, sem consulta | Em parte: P3.1 (dados), P3.15 (aba Arrecadação da Central), P4.3 (relatórios em réplica) | Entregar com a P3.1 os relatórios exigidos na POC | M |
| TR-733 a TR-735 · SG-960 a SG-962 | Débito em conta: faturas enviadas, ocorrências de rejeição do banco, cadastros por instituição | sim (a/C) | **Não atende** | Não planejado | E8 | P |
| TR-697 · TR-708 · TR-709 · TR-729 · TR-730 · TR-736 a TR-746 · TR-751 a TR-755 · TR-760 · TR-765 · TR-766 · TR-768 · TR-769 · TR-771 · TR-834 · SG-924 · SG-935 · SG-936 · SG-956 · SG-957 · SG-963 a SG-973 · SG-978 a SG-982 · SG-987 · SG-992 · SG-993 · SG-995 · SG-996 · SG-998 · SG-1061 | Faturamento: previsão e projeção de vencidas, acompanhamento diário, centralizadas, endereço alternativo, vencidas por dia, alteradas por reajuste, valores por situação, 2ª vias, canceladas, faturamento sucinto, detalhado e líquido por categoria, serviços faturados, por faixa e tipo de consumo, manuais, unidades sem faturamento, regras diferenciadas e contratos, painéis do faturamento por grupo e de faturamento bruto, líquido e inadimplência, status das faturas, situação de documentos, relatório parametrizável | sim (a/C) | **Não atende** | Em parte: P2.11 (aba Faturamento da Central), P4.3 | Entregar na P2.5 os de fechamento do ciclo; os demais na P4.3 | G |
| TR-694 a TR-696 · TR-758 · SG-921 a SG-923 · SG-985 | Isenção e tarifa social: listagem com tarifa social e com isenção, cadastros vencidos, acompanhamento, isentas com volume | sim (a/C) | **Não atende** | Em parte: P1.5 (relatório anual da tarifa social, `social.annual_report`) | Com R3 e E2 | P |
| TR-699 a TR-701 · TR-704 a TR-707 · TR-710 a TR-714 · TR-716 · TR-732 · TR-763 · TR-805 a TR-807 · SG-926 a SG-928 · SG-931 a SG-934 · SG-937 a SG-941 · SG-943 · SG-959 · SG-990 · SG-1032 a SG-1034 | Cobrança e corte: informes de débito e protocolos, 1º nível de corte, cronograma e desempenho da cobrança, grandes devedores, pendências, aptos ao corte, baixados por informe, pagamentos posteriores, certidão de pendências, notificações de não débito, painel dos informes, cortadas sem religação, cortes realizados e a realizar | sim (a/C) | **Não atende** | Em parte: P3.4 (dados de aviso e OS), P4.1, P4.3 | Junto da E9 | M |
| TR-715 · SG-942 | Inclusões e exclusões em empresas de cobrança | sim (a/C) | **Não atende** | Não planejado (negativação em 2028) | E10 | P |
| TR-702 · TR-703 · SG-929 · SG-930 | Dívida ativa: faturas com baixa de documentos de dívida ativa; acompanhamento dos documentos | sim (a/C) | **Não atende** | Não planejado | E11 | P |
| TR-727 · TR-772 · SG-954 · SG-999 | Parcelamentos: status contábil; sintético por usuário (dívida, parcelas, valores) | sim (S/C) | **Não atende** | P4.1 | — | P |
| TR-665 · TR-717 a TR-724 · TR-726 · TR-728 · TR-750 · SG-891 · SG-944 a SG-951 · SG-953 · SG-955 · SG-977 · SG-1059 | Contábil e contas a receber: compensações, lançamentos por processo, faturamento por conta contábil, saldo por conta, realocação de documentos, diário, razão, pendências e quitação por rubrica, devolução de adiantamentos, abatimentos do fechamento, faturamento com visão contábil, valores a receber por tipo de unidade | sim (S/C) | **Não atende** | Não planejado | E12 | M |
| SG-974 | Impostos faturados de clientes federais | sim (–/C) | **Não atende** — dados em `bill_taxes` | Implícito em P2.4 | — | P |
| TR-761 · SG-988 | Unidades com registro de fraude | sim (a/C) | **Não atende** | Não planejado | E4 | P |

### 2.22 Fronteira com outras áreas (não contados)

- Painéis de BI de arrecadação, cobrança e faturamento: TR 22.6.12 a 22.6.15 (TR-909 a TR-967) e indicadores (TR-1002);
  equivalentes na seção 2.29 do SG. Dependem dos dados das seções 2.10 a 2.16; plano P4.3.
- Telas gerais de atendimento que mostram faturas e parcelamentos: TR-552, TR-603, SG-528, SG-582.
- Leitura com efeito no faturamento: leitura de ligação cortada com emissão de fatura (TR-222, SG-221), fontes
  alternativas para o esgoto (TR-186, SG-188), leitura de imóvel isento (TR-187, SG-189), relatórios de leitura
  (TR-790, TR-794, TR-800).
- Qualidade da água impressa na fatura (TR-1008): depende do modelo de conta da P2.5.
- Gerador de relatórios (SG-1062, TR-664) e relatórios de "perdas obtidas" (TR-725, SG-952), de sentido incerto.

## 3. Requisitos não planejados agrupados em entregas

Fase sugerida considerando o caminho crítico do plano (o go-live do piloto não depende destas entregas). Para disputar
editais como estes antes de 2028, E1, E2, E7, E8, E9, E11 e E12 teriam de estar prontas antes da licitação; a seção 4
pede as decisões que destravam essa ordem.

| Entrega | Requisitos cobertos | Fase sugerida | Esforço | Dependências |
|---|---|---|---|---|
| **E1 — Manutenção e retificação de conta** (cancelar, alterar vencimento, leitura, consumo e serviços, recalcular por cadastro, retirar água ou esgoto, crédito avulso, "pagamento a confirmar", motivos, anotação, 2ª via no backoffice, termo de adesão) | TR-307 a TR-314, TR-316, TR-555 a TR-562, TR-609, TR-777; SG-296 a SG-302, SG-304, SG-531 a SG-538, SG-540, SG-589, SG-1004, SG-110, SG-116 | P2, junto da P2.5 | G | P2.5 (conta persistida), P1.11 (idempotência), protocolo da P3.4 para a anotação |
| **E2 — Situações especiais de faturamento** (isenção, copiar tabela, guia avulsa a não vinculado, faturas desvinculadas, agrupadas e centralizadora com código único, conta final, contrato especial, locação com responsável na conta, taxa de entrega) | TR-276, TR-290, TR-294, TR-302, TR-318 a TR-320, TR-323, TR-324, TR-327 a TR-329, TR-337, TR-587, TR-618, TR-619; SG-266, SG-279, SG-283, SG-291, SG-306, SG-307, SG-310, SG-311, SG-314 a SG-316, SG-564, SG-599, SG-600 | P2 (isenção, copiar tabela) e P3 (demais) | G | P2.5, P1.3, E7 (documento agrupador) |
| **E3 — Revisão de consumo e desconto por vazamento** (processo com protocolo e fases, formulário, agenda, móvel, alteração das faturas, laudo, parâmetros de desconto, recálculo por anormalidade) | TR-245 a TR-273, TR-303 a TR-305, TR-315, TR-563; SG-242 a SG-263, SG-292 a SG-294, SG-303, SG-539 | P3, junto da P3.4 | G | P3.4 (protocolo e OS), E1, P3.5 |
| **E4 — Controle de fraudes e fiscalização de consumo** | TR-325, TR-331 a TR-336; SG-312, SG-318 a SG-323; relatórios TR-761, SG-988 | P4 | M | OS da P3.4, E1 |
| **E5 — Serviços e materiais faturáveis** (catálogo de serviços com parâmetros de cobrança, tabelas de preço de serviços e materiais com vigência, rubrica contábil, cobrança no encerramento da OS) | TR-500, TR-503 a TR-506, TR-512, TR-530, TR-535 a TR-538; SG-453, SG-456, SG-457, SG-459, SG-460, SG-466, SG-487, SG-497 a SG-500, SG-358 | P3, junto da P3.4 e da P3.6 | M | P3.4, P3.6 |
| **E6 — Emissão simultânea em campo** (cálculo off-line no app, impressão térmica, código de barras e QR Pix, comunicados de débito e de excesso, reimpressão, aviso de corte na massa de leitura) | TR-207, TR-224, TR-227, TR-228, TR-242, TR-404 e as partes em campo de TR-221, TR-223, TR-225, TR-226, TR-244; SG-223, SG-226, SG-227, SG-240, SG-388 e as partes de SG-220, SG-222, SG-224, SG-225, SG-241 | P4, ampliando a P4.4 | G | P2.5, P3.2, decisão de impressora (fora desta área) |
| **E7 — Arrecadação ampliada** (documento agrupador com quitação em cascata, fechamento mensal congelado, depósito identificado e não identificado, VAN) | TR-345, TR-350 a TR-353; SG-333, SG-338 a SG-341 | P3, junto da P3.1 | M | P3.1, P2.1 |
| **E8 — Débito automático** (arquivo de débito, optantes, reenvio, não identificados, vários convênios, pedido pelo app, relatórios) | TR-339, TR-321, TR-322, TR-354, TR-566, TR-583, TR-733 a TR-735; SG-327, SG-308, SG-309, SG-342, SG-543, SG-560, SG-748, SG-115, SG-122, SG-960 a SG-962 | P3 (ver D2) | M | P3.1; D2 |
| **E9 — Complementos de cobrança não citados no plano** (cartas de cobrança, prioridade e cota diária de OS de corte, níveis sucessivos, equipes especiais, corte e religação de esgoto, comunicado de esgoto, alteração de vencimento do aviso, carnê) | TR-372, TR-390, TR-409, TR-410, TR-417, TR-529, TR-581; SG-375, SG-389, SG-393, SG-394, SG-400, SG-486, SG-503, SG-504, SG-558 | P3 (corte) e P4 (cobrança) | M | P3.4, P4.1 |
| **E10 — Negativação e protesto** | SG-402 a SG-411, SG-424, SG-425, SG-548, TR-572; relatórios TR-715, SG-942 | P4 (ver D4) | G | P4.1, E11 (protesto de CDA), contrato do tenant com birô e cartório |
| **E11 — Dívida ativa** | TR-419 a TR-460 (exceto os já planejados TR-453), SG-412 a SG-423, SG-426 a SG-428; relatórios TR-702, TR-703, SG-929, SG-930 | P3/P4 (ver D1) | G | E1, E7, E9, E12 |
| **E12 — Contabilização por eventos e integração contábil** | TR-155, TR-461 a TR-475, TR-447; SG-429 a SG-447; ED-078; relatórios contábeis do 2.21 | P3/P4 (ver D5) | G | P2.5, P3.1, outbox (P1.9), lote (P2.1) |
| **E13 — Remessa e-Sfinge (TCE/SC)** | TR-476 a TR-494; ED-019, ED-077 | Depois da E12 e da E11 (ver D5) | G | E11, E12, leiaute oficial do TCE/SC |
| **E14 — Cartão de crédito e débito** | TR-374 | P4 (ver D3) | M | P3.1, contrato do tenant com credenciadora |
| **E15 — Resíduos sólidos na conta** (serviço adicional no cálculo, fatura desvinculada, carnê mensal ou anual, isenção e cálculo em campo) | SG-324, SG-325 e as partes de resíduos de SG-264, SG-281, SG-222, SG-310 | P3 | M | P2.5; decisão comercial de atender editais com resíduos |
| **E16 — Relatórios da área** | seção 2.21 | Os da POC com cada módulo; os demais na P4.3 | G | Módulos de origem; réplica de leitura (P4.3) |
| **E17 — Autoatendimento financeiro** (declaração anual de quitação, certidão negativa, tabela tarifária, prazo de pagamento, formato de entrega, contestação, autoleitura, código de barras e PDF no app, push) | TR-844, TR-849, TR-863; SG-609, SG-613, SG-621, SG-624, SG-723, SG-724, SG-747, SG-749, SG-750, SG-755, SG-764 a SG-766, SG-774 | P3, junto da P3.3 e P3.20 | M | P3.3, E1 |

**Itens implícitos que precisam virar tarefa nomeada** (estão no escopo de uma tarefa, mas o plano não os cita, e a
POC os cobra): parâmetros de geração da conta na P2.5 (valor mínimo, dias após ligação nova, histórico na fatura,
mensagens, tipo de tarifa, emissão individual e reprocessamento); parâmetros da política de parcelamento na P4.1;
parâmetros da notificação e da OS de corte na P3.4; envios por e-mail, SMS e WhatsApp na P3.5.

## 4. Decisões do dono do produto

### D1 — Dívida ativa no produto

Os dois editais cobram dívida ativa na POC (TR Anexo II k: inscrição, CDA e parcelamento; SG 2.15.3.19, crítico). O
prestador é autarquia; a inscrição e a CDA seguem a Lei 6.830/1980 e a Lei 4.320/1964 (art. 39).

| Opção | Conteúdo | Esforço |
|---|---|---|
| A | Módulo completo: notificação, inscrição, livro, CDA, petição, execução fiscal, saldo mensal e contabilização própria | G+ |
| **B (recomendada)** | Núcleo no produto: notificação com entrega registrada, inscrição (individual e periódica), livro, CDA, parcelamento separado, saldo mensal congelado e relatórios; a execução fiscal sai como CDA e petição em modelo do órgão e arquivo para a procuradoria ou o sistema tributário do município | G |
| C | Fora do produto; não disputar editais que exigem dívida ativa | — |

Recomendação: B, em P3/P4 (E11), porque é o mínimo que passa nas duas POCs sem assumir o processo judicial.

### D2 — Débito em conta: FEBRABAN 150, Pix Automático ou ambos

O TR cobra débito automático FEBRABAN na POC (Anexo II h) e o SG o trata como crítico (SG-327). Desde 13/10/2025 a
autorização com recebedor pessoa jurídica segue o regulamento do Pix Automático (Res. CMN 5.251/2025), e o regime atual
é revogado em 01/07/2027 (Res. CMN 5.299/2026). O plano exclui o FEBRABAN 150 e põe o Pix Automático na P4.5.

| Opção | Conteúdo | Esforço |
|---|---|---|
| A | Só Pix Automático, antecipado para a P3; FEBRABAN fora | M |
| **B (recomendada)** | Pix Automático para autorizações novas e FEBRABAN 150 (registros B, C, E, F) para as autorizações legadas migradas e para o banco depositário que é também destinatário, com data de desligamento | M + M |
| C | Só FEBRABAN 150 | M |

Recomendação: B (E8), em P3, porque A reprova na POC e C contraria a regra vigente para autorizações novas.

### D3 — Cartão de crédito e débito no parcelamento

O TR pede parcelamento por cartão com integração on-line com a operadora (TR-374). O ADR-0019 trata só da cobrança do
SaaS, não do consumidor do tenant.

| Opção | Conteúdo | Esforço |
|---|---|---|
| **A (recomendada)** | Integração com credenciadora ou subcredenciadora por API (link de pagamento ou checkout), contratada pelo tenant e configurada como parâmetro G4; o produto registra a operação, baixa as faturas e concilia pelo extrato da credenciadora | M |
| B | Maquininha/TEF da operadora do órgão no balcão, com conciliação por arquivo da operadora (modelo do GSAN) | M |
| C | Não ofertar; pedir esclarecimento ou impugnar o item | — |

Recomendação: A (E14), em P4, com B só se o piloto exigir atendimento presencial com cartão.

### D4 — Negativação (SPC/SERASA) e protesto

O SG trata negativação e protesto como críticos (SG-402 a SG-411, SG-424, SG-425); o plano deixa a negativação para
2028 sob demanda e não cita o protesto.

| Opção | Conteúdo | Esforço |
|---|---|---|
| A | Manter em 2028 | — |
| **B (recomendada)** | Antecipar para a P4: negativação com um birô por contrato do tenant (notificação prévia, CDC art. 43 §2º; baixa em 5 dias úteis, Súmula 548; teto de 5 anos já previsto em `ret.negative_listing_max_years`) e protesto de CDA e faturas pela central de remessa dos cartórios, começando em modo assistido (arquivo gerado, envio pelo tenant) | G |
| C | Só modo assistido, sem integração | M |

Recomendação: B (E10), com o modo assistido como primeiro passo.

### D5 — Contabilidade e e-Sfinge

Os dois editais pedem módulo contábil com plano de contas, contabilização por evento e fechamento mensal, e também
integração diária com o sistema contábil que o órgão já usa (TR-468, SG-437). O TR exige ainda a remessa tributária ao
e-Sfinge com as regras de consistência e transfere à contratada as penalidades do TCE por atraso ou erro (TR-494); o
edital exige atestado de envio ao e-Sfinge (ED-019).

| Opção | Conteúdo | Esforço |
|---|---|---|
| **A (recomendada)** | Razão auxiliar comercial no produto (plano de contas parametrizado, regras evento → lançamento, PDD, retidos, fechamento congelado, relatórios) exportado diariamente ao sistema contábil do cliente; remessa e-Sfinge gerada pelo produto com as CONs e enviada pelo cliente, como no ADR-0004 | G + G |
| B | Contabilidade pública completa no produto e envio direto ao TCE pelo produto | G++ |
| C | Só exportação de lançamentos; não disputar editais de SC que exigem e-Sfinge | M |

Recomendação: A (E12 e E13), em P3/P4. O envio assistido limita a responsabilidade do TR-494 ao conteúdo da remessa;
mesmo assim, a cláusula deve ser objeto de pedido de esclarecimento, e o atestado do ED-019 só existe depois de um
cliente em SC operando.
