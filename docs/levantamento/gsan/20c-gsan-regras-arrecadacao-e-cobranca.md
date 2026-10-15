# 20c — GSAN: catálogo de regras de arrecadação, cobrança, parcelamento, negativação e contábil

Levantamento clean-room, somente leitura, da fonte `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN` (código em `src/gcom/<pacote>`, mapeamentos `*.hbm.xml` ao lado das entidades), cruzado com o Ziip SAN: plano v2.13, `arquitetura/PARAMETROS-DO-TENANT.md`, ADRs 0002, 0015, 0019, 0028 e 0032, relatórios `11` e `18`, código em `commercial/`. Data: 14/10/2026. Continua o relatório `03-gsan-arrecadacao-cobranca-batch.md` (11/10/2026) e não repete o que ele descreve; quando uma regra já está lá, este documento cita a seção e acrescenta só o que é novo.

Convenções:

- **[FATO]** lido no código ou no mapeamento; **[INFERÊNCIA]** dedução não confirmada; **[EXT]** norma externa, a validar com o jurídico antes de virar requisito.
- Nenhum trecho de código, SQL, JSP, texto de tela, `.jrxml` ou leiaute posicional foi copiado. Caminhos e nomes de método aparecem só como evidência. Valores em exemplos são fictícios.
- **Natureza:** LEGAL (lei ou norma impõe); POLÍTICA (vira parâmetro do tenant); GSAN (comportamento do legado que não deve ser absorvido); PRÁTICA (boa prática a manter no produto).
- **Situação no Ziip SAN:** COBERTA (tarefa, ADR ou código já trata); PARCIAL; AUSENTE.
- **Diretrizes do dono do produto aplicadas (14/10/2026):** (1) onde lei, norma de agência ou prática legítima admite caminhos diferentes, ou normas de mesma hierarquia divergem, o produto oferece todos como parâmetro do tenant e o padrão de fábrica é o caminho mais protetivo ao usuário; onde lei federal proíbe expressamente, o caminho fica travado (classe a), ainda que a agência seja mais permissiva. Essas bifurcações aparecem como **parâmetro proposto** (seção 5.2), não como pergunta. (2) A unidade consumidora (`consumer_units`) é o pilar: todo pedido ou alteração de cliente ou de unidade gera OS, ainda que interna; parcelamento, transferência de débito, devolução, situação especial de cobrança e afins pedidos no atendimento entram como OS tipada (seção 2.4).
- Classes de parâmetro conforme `PARAMETROS-DO-TENANT.md` §2: **a** travado; **b** padrão de fábrica com aceite; **c** só ampliável no sentido protetivo; **d** livre.

## 1. Resumo executivo

1. O GSAN trata pagamento, documento de cobrança, parcelamento e negativação como quatro máquinas de estado ligadas por eventos: o pagamento classificado suspende o documento de cobrança, a entrada paga consolida o parcelamento, o parcelamento exclui a negativação. O Ziip SAN implementou só a primeira (classificador puro com 15 situações), ainda sem gravar o efeito na conta.
2. A classificação é mensal e em lote; a diferença dentro da tolerância de ajuste vira débito a cobrar ou crédito a realizar na conta seguinte. O Ziip SAN já calcula o tipo e o valor do ajuste, mas não cria o `debit_receivables`/`credit_receivables` nem baixa a conta (P3.1).
3. A ação de cobrança do GSAN é um funil de elegibilidade com 27 motivos de recusa, ação predecessora com prazo mínimo e validade, e cota por atividade. Nada disso tem código no Ziip SAN (`sigsan-dunning` vazio); o plano cobre em P3.4, P4.1 e ED-29.
4. No corte, o GSAN não tem vedação de dia nem prova de aviso; o Ziip SAN já trava ambos (G9, classe a). Falta absorver a mecânica: ação gera OS, encerramento da OS muda a situação da ligação, pagamento dispara religação.
5. O parcelamento é a regra mais densa (perfil por resolução, descontos em cascata, entrada por precedência, juros, desfazer por entrada não paga, cancelar por prestações em atraso, reparcelar). O Ziip SAN tem só a divisão em parcelas e o débito a cobrar no billing.
6. A negativação do GSAN não registra prova do aviso prévio nem expira em 5 anos; o Ziip SAN exige os dois (ED-30, `ret.negative_listing_max_years`), sem código.
7. Os acréscimos do Ziip SAN cobrem multa, juros simples por dia e correção com carência em dias úteis; faltam as isenções do GSAN (débito automático, situação especial, indicador da ação) e os modos alternativos de multa e juros, que viram parâmetro.
8. O contábil do GSAN lança por resumo mensal e reprocessa apagando; PDD por meta de baixa. O Ziip SAN não tem módulo; ED-32 pede lançamento por fato com fechamento congelado, mais exigente que o GSAN.
9. Pedidos de atendimento que no GSAN são telas soltas (parcelar, transferir pagamento, devolver, incluir situação especial) passam a OS tipada sobre a unidade consumidora; os editais já pedem "OS de registro" em vários deles.
10. Lacunas mais graves (seção 7): efeito contábil e comercial da classificação; funil de elegibilidade com motivos de não geração; parcelamento com desfazer e reparcelamento; negativação com aviso provado; contábil por evento.

## 2. Catálogo de regras

Evidências relativas a `src/gcom/`. Abreviações: `CA` = `arrecadacao/ControladorArrecadacao.java`; `CC` = `cobranca/ControladorCobranca.java`; `SP` = `cadastro/sistemaparametro/SistemaParametro.java`; `CF` = `financeiro/ControladorFinanceiro.java`; `CSS` = `spcserasa/ControladorSpcSerasaSEJB.java`.

### 2.1 Arrecadação (ARR)

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| ARR-01 | A classificação compara, por documento (conta, guia de pagamento, débito a cobrar), a soma dos pagamentos ativos com o valor do documento; dentro da tolerância de classificação, todos ficam classificados e o documento fica pago [FATO] | `CA` `classificarPagamentosDevolucoes`; relatório 03 §2.5 | POLÍTICA | COBERTA: `PaymentClassifier`, `pay.classify_tolerance` | Manter; P3.1 grava a baixa da conta (`BillStatus.PAID`) na mesma transação da classificação |
| ARR-02 | Diferença dentro da tolerância de ajuste gera crédito a realizar (pagou a maior) ou débito a cobrar (pagou a menor) na conta seguinte, com a conta original retificada e competência contábil no mês seguinte ao da arrecadação [FATO] | `CA`, processo "diferença de dois reais"; 03 §2.5 | POLÍTICA | PARCIAL: `adjustment_kind` e `adjustment_amount` calculados; nada cria `debit_receivables`/`credit_receivables` | P3.1: criar o débito ou crédito com `idempotency_key` derivada do pagamento; sem retificar a conta paga (o ajuste é lançamento novo, ver FIN-10) |
| ARR-03 | Em duplicidade, um pagamento de valor igual ao documento vence e os demais viram duplicidade com excedente igual ao seu valor integral [FATO] | `CA`; 03 §2.5 | POLÍTICA | COBERTA: `pay.duplicate_winner` | Manter; padrão de fábrica proposto `OLDEST_PAYMENT` (a data de quitação mais antiga protege o usuário contra acréscimo) |
| ARR-04 | Pagamento de documento inexistente, cancelado, parcelado, prescrito ou com erro recebe situação própria e não se perde: fica fora da baixa, entra no resumo como não classificado e aguarda tratamento manual ou reaproveitamento [FATO] | `arrecadacao/pagamento/PagamentoSituacao.java`; `DadosPagamentosNaoClassificados` | PRÁTICA | PARCIAL: situações existem; não há fila de exceções (TR-342) | P3.1: fila de exceções com sugestão de vínculo; cada tratamento é OS interna `PAYMENT_IDENTIFICATION` (seção 2.4) |
| ARR-05 | Pagamento de conta prescrita é aceito e não gera devolução [INFERÊNCIA a partir da situação dedicada] | `PagamentoSituacao` (documento inexistente por débito prescrito) | LEGAL [EXT] (CC art. 882: não se repete o que se pagou para solver dívida prescrita) | PARCIAL: `DOC_PRESCRIBED` existe sem destino definido | P3.1: tratar `DOC_PRESCRIBED` como recebimento válido, com a conta marcada paga e lançamento de recuperação (FIN-08) |
| ARR-06 | Documento ainda no ciclo contábil aberto fica "a contabilizar" e só é classificado depois do fechamento do faturamento [FATO] | `CA`; 03 §2.5 | GSAN | COBERTA em excesso: `TO_ACCOUNT` reproduz a regra | Rever na ED-32: com lançamento por fato a espera é desnecessária; manter o estado só enquanto o contábil for por resumo |
| ARR-07 | Pagamento em duplicidade precisa ser comunicado ao usuário [EXT] | — (o GSAN não comunica) | LEGAL [EXT] (NR 11 art. 37) | AUSENTE (relatório 11, R12) | P3.1: evento de duplicidade gera aviso no canal do usuário e abre a escolha da forma de devolução (ARR-08) |
| ARR-08 | Devolução de duplicidade ou excesso é feita por guia de devolução casada com o pagamento de origem; sem pagamento duplicado, sem guia ou com valor divergente, a devolução fica pendente com motivo [FATO] | `arrecadacao/Devolucao.hbm.xml`, `GuiaDevolucao`, `DevolucaoSituacao`; 03 §2.5 | PRÁTICA + POLÍTICA (forma) | AUSENTE: não há entidade de devolução | Nova tarefa no P3, junto da P3.1: `refunds` ligada ao pagamento; forma em `pay.refund_mode` (seção 5.2); pedido no atendimento é OS `REFUND_REQUEST` |
| ARR-09 | Cobrança indevida é devolvida em dobro, salvo engano justificável [EXT] | — | LEGAL [EXT] (CDC art. 42 par. único) | AUSENTE (relatório 11, R21) | Junto de ARR-08: `refund_kind` (`DUPLICATE_PAYMENT`, `UNDUE_CHARGE`) e justificativa obrigatória para afastar o dobro |
| ARR-10 | Transferência de pagamento entre documentos ou imóveis é manual, desfaz a classificação de origem e reclassifica no destino, com trilha [INFERÊNCIA pelo relatório de transferência] | `reports/relatorioTransferenciaPagamento*` | PRÁTICA | AUSENTE | P3.1: OS `PAYMENT_TRANSFER` com aprovação dupla (já travada para ajuste de conciliação, G4) e dois eventos em `payment_status_events` |
| ARR-11 | Guia de pagamento é documento avulso por tipo de débito (serviço, entrada de parcelamento, auto de infração), com prestações e vencimento; guia vencida e não paga é cancelada em lote [FATO pelo processo de cancelamento de guias não pagas] | `faturamento/GuiaPagamento*.hbm.xml`; 03 §5.3 | POLÍTICA (prazo) | PARCIAL: `pay.document_kind_by_debt` escolhe o tipo de documento; não há guia | ED-26 ou P3.1: `payment_slips` com `pay.unpaid_slip_cancel_days`; cancelar a guia não apaga o débito de origem |
| ARR-12 | Acima de um valor, a guia e o demonstrativo de parcelamento saem como boleto de cobrança em vez de documento de arrecadação [FATO pelos parâmetros] | `SP` `valorGuiaFichaComp`, `valorDemonstrativoParcelamentoFichaComp` | POLÍTICA | PARCIAL: `pay.document_kind_by_debt` | Acrescentar faixa de valor à chave existente |
| ARR-13 | Arquivo de retorno: cabeçalho, sequência de arquivo, convênio, banco, versão e totais validados; linha original guardada; registros não aceitos geram relatório e acerto manual [FATO] | `CA` `validarArquivoMovimentoArrecadador`; 03 §2.3 | PRÁTICA | COBERTA para FEBRABAN 150 (`Febraban150Validator`, `bank_return_files`); CNAB 240 AUSENTE | P3.1 conforme o plano |
| ARR-14 | O valor realizado do aviso bancário vem do arquivo ou do somatório calculado, conforme indicador do sistema [FATO pelo parâmetro] | `SP` `indicadorValorMovimentoArrecadador`; 03 §2.6 | POLÍTICA | AUSENTE | P3.1: `pay.notice_amount_source` (seção 5.2) |
| ARR-15 | Aviso bancário por arrecadador, forma e data de crédito; fechado quando as diferenças de arrecadação e de devolução se compensam pelos acertos; diferença residual acumulada para o mês seguinte [FATO] | `arrecadacao/aviso`; 03 §2.6 | PRÁTICA | AUSENTE (plano: P3.1, "conciliação N3") | P3.1: `bank_notices`, `bank_notice_adjustments`, `accumulated_differences`; acerto com aprovação dupla (classe a) |
| ARR-16 | Tarifa bancária e dias de float por convênio e forma de arrecadação, com valor fixo ou percentual [FATO] | `ArrecadadorContratoTarifa.hbm.xml` | POLÍTICA | AUSENTE: `collection_agreements` não tem tarifa nem float | P3.1: `collection_agreement_fees` (forma, valor, percentual, dias de float) |
| ARR-17 | Débito automático: opção do cliente registrada com o banco, envio por conta do grupo de faturamento, retorno com código; débito efetuado vira pagamento; rejeição devolve a conta à cobrança comum [FATO para o ciclo; INFERÊNCIA para o destino da rejeitada] | `arrecadacao/debitoautomatico`; 03 §2.7 | LEGAL [EXT] (Res. CMN 5.251/2025) + POLÍTICA | COBERTA no plano (P3.24, ED-27); código AUSENTE | Manter o recorte do plano; inclusão e exclusão de optante são OS `AUTO_DEBIT_ENROLLMENT` |
| ARR-18 | Pagamento por débito automático não gera acréscimo, mesmo efetuado depois do vencimento [FATO] | `CC` `calcularAcrescimoPorImpontualidade`; 03 §3.5 | PRÁTICA | AUSENTE em `SurchargeCalculator` | P3.24/ED-27: isenção pela forma de arrecadação (COB-17) |
| ARR-19 | Documento agrupador (fatura do cliente responsável, extrato de débito, documento de cobrança) quita os itens em cascata; pagamento divergente do agrupador cai em valor não confere [FATO para os tipos; INFERÊNCIA para a cascata] | `cobranca/DocumentoTipo.java`; 03 §2.2 | PRÁTICA | COBERTA no plano (ED-26); código AUSENTE | ED-26: quitação parcial em ordem configurável (`pay.partial_settlement_order`, seção 5.2) |
| ARR-20 | Extrato de débito com código de barras vale por número de dias; usuário com permissão especial emite com validade maior [FATO pelos parâmetros] | `SP` `numeroDiasValidadeExtrato`, `numeroDiasValidadeExtratoPermissaoEspecial` | POLÍTICA | AUSENTE | P3.3 e ED-26: `pay.statement_validity_days`; pagamento após a validade é recalculado pela data real (TR-435) |
| ARR-21 | Pagamento não identificado é associado a um cliente fictício para poder abrir OS de identificação [FATO pelo parâmetro] | `SP` `clienteFicticioParaAssociarOsPagamentosNaoIdentificados` | GSAN | — | Não absorver o cliente fictício; OS interna sem unidade, que recebe a unidade quando o pagamento é identificado |
| ARR-22 | Conta paga antes de ser retificada recebe motivo de retificação próprio, para que a retificação não anule o pagamento [FATO pelo parâmetro; INFERÊNCIA para o efeito] | `SP` `contaMotivoRetificacaoPagamentoAntecipado` | PRÁTICA | PARCIAL: P2.20 cria versão da conta e prevê "pagamento a confirmar" | P2.20: retificação de conta paga gera ajuste (crédito ou débito), nunca reabre a quitação |
| ARR-23 | Encerramento mensal: exige resumo da arrecadação do mês, avança a referência de arrecadação e move pagamentos, devoluções e documentos quitados para histórico; não classificados seguem pendentes [FATO] | `CA` `encerrarArrecadacaoMes`, `gerarHistoricoParaEncerrarArrecadacaoMes`; 03 §2.8 | PRÁTICA (fechamento) + GSAN (tabelas de histórico) | COBERTA no plano (ED-26, fechamento congelado); código AUSENTE | `period_close` por domínio e referência; sem tabelas `*_historico`: imutabilidade por estado e RLS |
| ARR-24 | O resumo separa recebimentos classificados no mês, de meses anteriores classificados no mês, não classificados, devoluções, baixa de não classificados, perdas e recuperação de crédito [FATO] | `arrecadacao/RecebimentoTipo.java`; 03 §2.8 | PRÁTICA | AUSENTE | ED-32: os mesmos grupos como tipos de evento contábil |
| ARR-25 | Pagamento de conta já baixada como perda ou cancelada é reaproveitado como recuperação de crédito [FATO] | `PagamentoSituacao` 15 e 16; `RecebimentosClassificadosRecuperacaoCredito` | PRÁTICA | PARCIAL: situações existem, fluxo não | ED-32, com FIN-07 |
| ARR-26 | Cartão: conciliação pelo extrato da operadora, com rejeições registradas [FATO] | `PagamentoCartaoDebito`, `MovimentoCartaoRejeita` | PRÁTICA | COBERTA no plano (ED-28, credenciadora do tenant) | Manter; nenhum dado de cartão no produto |

### 2.2 Cobrança, corte, parcelamento e negativação (COB)

**Ação de cobrança, documento e corte**

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| COB-01 | A cobrança se organiza em grupo, cronograma mensal, ação e atividade (simular, emitir, encerrar, encerrar OS); o comando eventual roda a mesma ação fora do cronograma, com filtros próprios de localização, valor e consumo [FATO] | `cobranca/CobrancaAcaoAtividadeComando.hbm.xml`, `CobrancaAcaoCronograma`; 03 §3.1 | PRÁTICA | AUSENTE (`sigsan-dunning` vazio) | P4.1 com ED-29; a simulação é obrigatória antes da emissão, como no GSAN |
| COB-02 | O critério de cobrança define, por perfil do imóvel e categoria, faixas de valor e de quantidade de contas, mínimos próprios para optante de débito automático e para débito de inquilino, e se aceita débito só do mês, só antigo ou em revisão [FATO] | `CobrancaCriterio.hbm.xml`, `CobrancaCriterioLinha.hbm.xml` | POLÍTICA | PARCIAL: `cut.min_overdue_days`, `cut.min_debt_amount`, `cut.max_debt_age_days` no catálogo; sem código | P4.1: `dunning_criteria` com linhas por perfil e categoria, versionado com vigência (ADR-0002) |
| COB-03 | A ação exige documento válido da ação predecessora, emitido há pelo menos N dias e ainda dentro da validade; para ações de campo, exige a OS da predecessora executada no prazo [FATO pelos motivos 3 e 4 e pelos campos da ação] | `CobrancaAcao.hbm.xml` (predecessora, dias mínimos, validade); `MotivoNaoGeracaoDocCobranca` | LEGAL [EXT] para corte (aviso de 30 dias, Lei 11.445 art. 40 §2º) + POLÍTICA | PARCIAL: `cut.min_notice_days` (classe c, piso 30) e `cut.notice_validity_days`; P3.4 bloqueia a OS | P3.4: a OS de corte só nasce de aviso válido com prova de entrega (G9); as demais predecessoras ficam na P4.1 |
| COB-04 | Todo imóvel avaliado e recusado recebe motivo de não geração (27 motivos), com relatório analítico e sintético [FATO] | `cobranca/MotivoNaoGeracaoDocCobranca.java`; 03 §3.3 | PRÁTICA | AUSENTE | P4.1: `dunning_skips` com catálogo próprio (seção 4.3); mesmo padrão para negativação (COB-40) |
| COB-05 | Não se gera documento quando já existe documento válido da mesma ação ou de ação sucessora [FATO pelos motivos 5 e 6] | `MotivoNaoGeracaoDocCobranca` | PRÁTICA | AUSENTE | P4.1; o limite de documentos pendentes por unidade é parâmetro (TR-400: `dun.max_pending_notices`) |
| COB-06 | A ação exige situação de ligação de água e de esgoto compatível (aviso e corte para ligado; recorte para cortado; supressão para cortado há tempo) [FATO pelos motivos 7 e 8] | `CobrancaAcao.hbm.xml`; 03 §3.2 | PRÁTICA | AUSENTE | ED-29: níveis sucessivos (cavalete, ramal, supressão) com calendário por nível (TR-417) |
| COB-07 | O documento de cobrança reúne contas, guias, débitos a cobrar e créditos a realizar com acréscimos calculados na emissão; vencimento igual à emissão mais os dias da ação; a ação pode gerar taxa de emissão, cobrada como débito a cobrar [FATO pelos campos; INFERÊNCIA para o destino da taxa] | `CobrancaDocumento.hbm.xml`, `CobrancaDocumentoItem.hbm.xml`; `SP` `numeroDiasVencimentoCobranca` | POLÍTICA | AUSENTE | P4.1: `dunning_documents` e itens; taxa de notificação em `dun.notice_fee` (seção 5.2), padrão sem cobrança |
| COB-08 | Pagamento, parcelamento ou cancelamento dos itens depois da emissão suspendem a ação; o documento vencido sem execução é cancelado por prazo; registra-se se o evento ocorreu antes ou depois da data-limite [FATO pela situação da ação] | `CobrancaAcaoSituacao`; 03 §3.2 | PRÁTICA | AUSENTE | P4.1: evento de pagamento classificado (P3.1) consome o documento; ver máquina 3.2 |
| COB-09 | A ação de campo gera OS do tipo de serviço configurado; o encerramento da OS de corte atualiza a situação da ligação e o histórico; há prazos para encerrar a atividade e a OS [FATO pelos campos; INFERÊNCIA para a atualização] | `CobrancaAcao` (tipo de serviço, dias de encerramento); `SP` `numeroDiasEncerramentoOrdemServico` | PRÁTICA | PARCIAL: `service_orders` existe sem tipo de serviço nem efeito na ligação | P3.4: OS de corte tipada; ED-13 grava a situação da ligação a partir do encerramento |
| COB-10 | O GSAN não veda o início do corte em sexta, fim de semana, feriado ou véspera, nem exige prova do aviso [FATO por ausência nos filtros e motivos] | `CobrancaAcao`, `MotivoNaoGeracaoDocCobranca` | GSAN | COBERTA: G9 classe a; P3.4 recusa a OS | Não absorver; manter a trava (Lei 13.460 art. 6º p.u.; Lei 8.987 art. 6º §4º) [EXT] |
| COB-11 | Quantidade máxima de documentos por atividade de cronograma, metas por unidade e valor-limite de prioridade ordenam e cortam a seleção [FATO pelos campos e pelo motivo 24] | `CobrancaAcaoAtividadeCronograma` (quantidade máxima); `CobrancaCriterio` (limite de prioridade); `MetasArrecadacao` | POLÍTICA | AUSENTE | ED-29: `cut.daily_quota` e `cut.priority_order` (seção 5.2); quem fica fora da cota recebe motivo |
| COB-12 | Religação: pagamento ou parcelamento do débito gera OS de religação; ligação cortada que volta a registrar consumo é tratada como religada sem OS; a taxa de religação é débito a cobrar [FATO pelos processos 1387 e 1357; INFERÊNCIA para o gatilho] | 03 §5.3; `atendimentopublico/ordemservico` | LEGAL [EXT] (sem aviso, sem taxa: Lei 13.460 art. 6º p.u.) + POLÍTICA | PARCIAL: `rec.sla_hours`, `rec.urgent_fee`, `rec.requires_settlement` no catálogo | P3.4: religação automática pelo evento de pagamento; condição em `rec.release_condition` (seção 5.2); religação à revelia abre OS de fiscalização |
| COB-13 | Ligação cortada continua sujeita a cobrança mínima (percentual da tarifa mínima) [FATO pelo processo 1354] | 03 §5.3 | POLÍTICA [EXT] (depende da ERI) | AUSENTE | P2.5: `bill.cut_connection_charge` (seção 5.2), padrão sem cobrança |
| COB-14 | Situação especial de cobrança por imóvel, com tipo (paralisa ações, arrasto, acréscimos, corte, supressão ou pagamento), vigência por referência, motivo e histórico; inclusão individual ou por comando em lote [FATO] | `CobrancaSituacaoTipo`, `CobrancaSituacaoHistorico`, `CobrancaSituacaoComando`; 03 §3.4 | POLÍTICA | AUSENTE; editais pedem bloqueio por tutela judicial e serviço essencial com OS de registro (TR-418, SG-401) | P4.1: `unit_collection_holds` com efeitos combináveis; todo pedido é OS `COLLECTION_HOLD` |
| COB-15 | Conta em revisão (contestada) fica fora da cobrança enquanto a revisão durar, salvo critério em contrário [FATO pelo indicador do critério e pelo motivo 16] | `CobrancaCriterio`; `SP` `numeroDiasRevisaoComPermEspecial` | LEGAL [EXT] + POLÍTICA | COBERTA no plano (P4.1, "contestação bloqueia corte") | Travar (classe a): conta contestada não entra em aviso de corte nem em negativação; o critério do GSAN que permite incluí-la não é absorvido |
| COB-16 | Acréscimos: carência em dias úteis com feriados do município; multa diária com teto ou fixa; juros por dia com teto ou por mês; correção pela razão de fatores mensais [FATO] | `CC` `calcularAcrescimoPorImpontualidade`; `IndicesAcrescimosImpontualidade`; 03 §3.5 | POLÍTICA [EXT] (normas de agência divergem) | PARCIAL: `SurchargeCalculator` faz juros simples por dia, multa única e correção; não faz multa diária nem juros por mês | P2.5 ou P3.1: ampliar `fee.fine_mode` e `fee.interest_mode` (seção 5.2); o teto de 2% da multa segue classe b do ADR-0015 |
| COB-17 | Isenção de acréscimos: pagamento por débito automático, imóvel em situação especial que paralisa acréscimos, ação ou forma de arrecadação com indicador desligado [FATO para os dois primeiros; INFERÊNCIA para o terceiro] | `CC`; `CobrancaSituacaoTipo` (paralisa acréscimos) | POLÍTICA | AUSENTE | P3.1: `fee.exemptions` (seção 5.2); atraso imputável ao banco ou ao prestador também isenta (padrão protetivo) |
| COB-18 | Acréscimos de conta paga em atraso são lançados como débito a cobrar na conta seguinte [FATO pelo processo 188] | 03 §5.3 | POLÍTICA | PARCIAL: `LateFeeCalculator` no billing; `fee.no_carry_over` no catálogo | P2.5: `fee.charge_moment` (seção 5.2); unificar `LateFeePolicy` e `SurchargeCalculator` (já apontado no G5) |
| COB-19 | Arrasto: débito vencido e não pago é trazido para a conta seguinte, que passa a cobrá-lo, e a conta de origem fica "arrastada" [FATO pela situação 6 e pelos tipos de financiamento de arrasto] | `DebitoCreditoSituacao`; `FinanciamentoTipo` 5 a 7 | POLÍTICA [EXT] | PARCIAL: `BillStatus.CARRIED_FORWARD` existe | P2.5: `bill.carry_over_unpaid` (seção 5.2), padrão desligado |
| COB-20 | Prescrição por esfera de poder, com referência-limite em tabela, executada em lote por situação de cobrança; a conta vira prescrita e o lançamento contábil distingue se já estava baixada como perda [FATO] | `cobranca/Prescricao`; 03 §3.9 | LEGAL [EXT] (prazo decenal, CC art. 205, a confirmar) + POLÍTICA | PARCIAL: `ret.prescription_years` e `BillStatus.PRESCRIBED_DEBT` | P4.1: lote de prescrição com data calculada pelo vencimento, não por referência digitada; pagamento posterior segue ARR-05 |
| COB-21 | Cobrança administrativa e judicial são só marcadores de situação (judicial exige advogado); não há processo [FATO] | `CobrancaSituacao` 4 e 5 | GSAN (lacuna) | COBERTA no plano por ED-31 (dívida ativa, opção B da D1) | ED-31; débito ajuizado sai das ações automáticas e da negativação (classe a) |
| COB-22 | Cobrança por resultado: contas enviadas a empresa terceirizada com percentual de remuneração; pagamento atribuído à empresa dentro de janela e com acordo válido; retirada por pagamento, parcelamento ou prazo; arquivos de ida e de volta [FATO] | `EmpresaCobrancaConta`, `ComandoEmpresaCobrancaConta`; 03 §3.8 | POLÍTICA | AUSENTE e fora do plano | Onda C sob demanda; exige contrato de operador (LGPD [EXT]) e envio mínimo de dados; remuneração calculada pelo evento de pagamento |
| COB-23 | Auto de infração tem prazo de recurso e número máximo de parcelas; vencido o prazo sem recurso, gera o débito [FATO] | `SP` `numeroDiasPrazoRecursoAutoInfracao`, `numeroMaximoParcelasAutoInfracao`; 03 §3.9 | LEGAL [EXT] (contraditório) + POLÍTICA | PARCIAL: ED-24 (fraudes) no plano | ED-24: o débito só nasce depois do prazo ou do julgamento; recurso é OS `INFRACTION_APPEAL` |
| COB-24 | Certidão negativa de débitos com opção de efeito positivo (débito parcelado em dia não impede) e regra para débito a cobrar não vencido; débito só conta depois de N dias do vencimento [FATO pelos parâmetros] | `SP` `indicadorCertidaoNegativaEfeitoPositivo`, `indicadorDebitoACobrarValidoCertidaoNegativa`, `numeroDiasVencimentoDebitoGeracaoCertidaoNegativaDebitos` | POLÍTICA | AUSENTE (ED-38 cita certidão de quitação anual, não a negativa) | P3.22 ou ED-38: `cert.*` (seção 5.2), padrão protetivo |
| COB-25 | Transferência de titularidade com débito pendente é permitida ou bloqueada por indicador do sistema [FATO pelo parâmetro] | `SP` `indicadorTransferenciaComDebito` | LEGAL [EXT] (débito de tarifa é obrigação pessoal do usuário, não do imóvel, a confirmar) + POLÍTICA | AUSENTE | P1.3 ou ED-35: travar (classe a) a recusa de transferência por débito do titular anterior, se o jurídico confirmar; transferência de débito entre imóveis é OS `DEBT_TRANSFER` com aceite do novo devedor |

**Parcelamento**

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| COB-26 | O perfil de parcelamento é escolhido pela resolução vigente, situação da ligação, perfil do imóvel, subcategoria e categoria; sem perfil, não há parcelamento [FATO] | `cobranca/parcelamento/ParcelamentoPerfil.hbm.xml`; 03 §3.6 | POLÍTICA | AUSENTE | P4.1: `installment_policies` versionada (TR-356); sem perfil, a recusa tem motivo, como em COB-04 |
| COB-27 | Descontos em sequência (antiguidade, faixa de referência, acréscimos, inatividade da ligação, sanções, tarifa social, pagamento à vista), cada um sobre a base já descontada ou por item [FATO] | 03 §3.6 | POLÍTICA | AUSENTE | P4.1: a ordem dos descontos é dado da política, não código; demonstrativo mostra cada desconto (TR-364) |
| COB-28 | Entrada mínima por percentual do débito, por percentual da tarifa mínima, por faixa de valor, por saldo de reparcelamento ou pela última conta; usuário com permissão especial reduz [FATO; precedência exata em 4.2] | `ParcelamentoQuantidadePrestacao`, `ParcelamentoFaixaValor`; 03 §3.6 | POLÍTICA | AUSENTE | P4.1: entrada obrigatória ou não (TR-360) em `inst.down_payment_rule`; redução por alçada com motivo |
| COB-29 | Prestação mínima igual a percentual da tarifa mínima; opções com prestação menor são descartadas [FATO] | `ParcelamentoPerfil` | POLÍTICA | AUSENTE | P4.1: `inst.min_installment_amount` (TR-359) |
| COB-30 | Juros do parcelamento por taxa associada à quantidade de prestações; Tabela Price ou montante capitalizado dividido por n, conforme indicador [FATO] | `SP` `indicadorTabelaPrice`; 03 §3.6 | POLÍTICA + GSAN (o montante capitalizado dividido por n cobra juros sobre o saldo inteiro sem amortizar) | AUSENTE | P4.1: `inst.interest_method` (seção 5.2) sem a opção do montante dividido por n |
| COB-31 | Juros do parcelamento são débito a cobrar próprio, separado do principal; o principal é dividido em água, esgoto e serviço, com curto e longo prazo para o contábil [FATO] | `FinanciamentoTipo` 2 a 4 e 8; 03 §3.6 | PRÁTICA | PARCIAL: `debit_receivables` com `typeCode` e `Installments` | P4.1: tipos de débito do parcelamento separados por natureza e prazo; base de FIN-03 |
| COB-32 | Conclusão: contas passam a parceladas, nasce a guia ou a conta de entrada com vencimento em N dias, e o parcelamento pode exigir confirmação antes de valer [FATO] | `SP` `numeroDiasVencimentoEntradaParcelamento`, `indicadorParcelamentoConfirmado` | POLÍTICA | PARCIAL: `BillStatus.INSTALLMENT` e `INSTALLMENT_DOWN_PAYMENT` | P4.1: `inst.down_payment_due_days`, `inst.requires_confirmation`; o termo é emitido na conclusão (TR-379, TR-778) |
| COB-33 | Entrada não paga no prazo desfaz o parcelamento: contas voltam à situação anterior, débitos e créditos gerados são cancelados, motivo registrado [FATO] | `ControladorCobranca` `desfazerParcelamentosPorEntradaNaoPaga`; 03 §3.6 | POLÍTICA | AUSENTE | P4.1: `inst.unpaid_down_payment_action` e `inst.down_payment_grace_days` (seção 5.2; TR-362, TR-363) |
| COB-34 | Prestações em atraso acima de um limite cancelam o parcelamento; o saldo volta como conta incluída, com novos acréscimos [FATO para a conta incluída; INFERÊNCIA para o limite de 3] | `ControladorParcelamento.cancelarParcelamentos`; `ConstantesSistema` | POLÍTICA | AUSENTE | P4.1: `inst.cancel_after_overdue` e `inst.discount_on_cancel` (seção 5.2) |
| COB-35 | Reparcelamento: saldo de parcelamento ativo entra no novo; entrada mínima própria sobre esse saldo; limite de reparcelamentos; resolução alternativa para quem tem prestações em atraso [FATO] | `ParcelamentoQuantidadeReparcelamento`, `ResolucaoDiretoria`; 03 §3.6 | POLÍTICA | AUSENTE | P4.1: TR-367 a TR-370 e TR-384 a TR-387; o saldo é trazido a valor presente sem os juros futuros |
| COB-36 | Parcela bônus: as últimas prestações são perdoadas a quem paga um número de prestações consecutivas em dia [INFERÊNCIA; regra exata não lida] | `DebitoACobrar.numeroParcelaBonus`; processo de parcelas consecutivas | POLÍTICA | AUSENTE | P4.1: `inst.bonus_installments`, desligado por padrão até haver regra do tenant |
| COB-37 | Antecipação de prestações com redução proporcional dos juros [EXT]; o GSAN não tem fluxo próprio [INFERÊNCIA] | — | LEGAL [EXT] (CDC art. 52 §2º) | AUSENTE (TR-388) | P4.1: travar (classe a) o abatimento proporcional |
| COB-38 | Contrato de parcelamento por cliente (grandes clientes e órgãos), com prestações próprias, vencimentos informados, renegociação de contrato anterior e bloqueios sobre as contas incluídas [FATO] | `ContratoParcelamento*`; `SP` `indicadorBloqueio*ContratoParcel*`, `numeroMaximoParcelasContratosParcelamento` | POLÍTICA | AUSENTE | ED-22 (órgão centralizador) com P4.1 |
| COB-39 | Bloqueios para parcelar: situação especial com bloqueio, cobrança judicial, conta em revisão, parcelamento em andamento quando a resolução não permite [FATO pelos indicadores] | `CobrancaSituacao`; `ResolucaoDiretoria` | POLÍTICA | AUSENTE; TR-448 (inscrito e não inscrito juntos) | P4.1: toda recusa com motivo; inscrito em dívida ativa segue a ED-31 |

**Negativação**

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| COB-40 | Critério de negativação: faixa de valor e de quantidade, referência e vencimento dos débitos, tipos de cliente e de documento, titularidade da conta em nome do cliente, exclusão de baixa renda e de órgão público salvo marcação, exclusão de quem já foi enviado [FATO] | `NegativacaoCriterio`; `CSS` `verificarCriteriodeNegativacaoParaImovel`; 03 §3.7 | POLÍTICA + LEGAL (titularidade) | AUSENTE; ED-30 | ED-30: critério versionado; motivos de não inclusão como em COB-04 |
| COB-41 | O GSAN registra "carta enviada" como situação de cobrança, sem prova de entrega nem prazo mínimo entre carta e inclusão [FATO] | `CobrancaSituacao` 13 e 14 | GSAN | COBERTA no plano: ED-30 recusa inclusão sem aviso com entrega registrada | Travar (classe a) aviso prévio por escrito com prova (CDC art. 43 §2º [EXT]); prazo em `neg.notice_min_days` (classe c) |
| COB-42 | Inclusão confirmada na data de envio mais o prazo do contrato do birô mais um dia [FATO] | `CSS`; 03 §3.7 | PRÁTICA | AUSENTE | ED-30 |
| COB-43 | Exclusão por pagamento ou renegociação, marcada na hora e enviada no movimento seguinte; o item guarda a situação do débito depois da exclusão [FATO] | `CSS` `excluirNegativacaoOnLine`; `NegativadorExclusaoMotivo` | LEGAL [EXT] (baixa em 5 dias úteis, Súmula 548 STJ) | AUSENTE; ED-30 exige baixa no prazo legal | Travar o teto de 5 dias úteis; `neg.exclusion_business_days` classe c, só reduz; pedido no atendimento é OS `NEGATIVE_LISTING_EXCLUSION` (SG-548) |
| COB-44 | Não há expiração automática da inclusão [FATO por ausência] | `CSS` | GSAN | COBERTA no catálogo: `ret.negative_listing_max_years` | Travar 5 anos (CDC art. 43 §1º [EXT]) e excluir por lote diário |
| COB-45 | Contrato do birô com tarifa por inclusão, quantidade contratada e enviada, prazo de inclusão e sequência de arquivo [FATO] | `NegativadorContrato.hbm.xml` | POLÍTICA | AUSENTE | ED-30: ao atingir a quantidade contratada, o comando para com aviso |
| COB-46 | Negativação e protesto são canais alternativos ou cumulativos de cobrança extrajudicial; o GSAN só tem negativação [FATO por ausência de protesto] | 03 §3.9 | POLÍTICA [EXT] | COBERTA no plano (ED-30, D4 opção B) | `neg.channels` (seção 5.2), padrão desligado |

### 2.3 Financeiro e contábil (FIN)

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| FIN-01 | Lançamento contábil gerado a partir de resumo mensal por localidade, categoria, tipo e item de lançamento, com conta débito, conta crédito e histórico parametrizados por essa chave [FATO] | `financeiro/ArrecadacaoContabilParametros.hbm.xml`, `LancamentoContabilItem`; 03 §4.2 | PRÁTICA (regra evento → lançamento) + GSAN (lote mensal sobre resumo) | AUSENTE (`sigsan-accounting` vazio); ED-32 no plano, opção A da D5 | ED-32: a regra fica; o gatilho passa a ser o fato (TR-463), na mesma transação do evento comercial |
| FIN-02 | Quatro origens de lançamento: faturamento, arrecadação, devedores duvidosos e aviso bancário [FATO] | `LancamentoOrigem` | PRÁTICA | AUSENTE | ED-32; acrescentar dívida ativa como origem (TR-465) |
| FIN-03 | Eventos de faturamento contabilizados: emissão por natureza (água, esgoto, serviços), inclusão, cancelamento, retificação pela diferença, parcelamento realizado e cancelado (curto e longo prazo), prestações cobradas, juros de parcelamento, descontos, impostos retidos, prescrição [FATO pelos tipos de lançamento] | `LancamentoTipo.java`; 03 §4.1 | PRÁTICA | AUSENTE | ED-32: catálogo de eventos com nomes próprios; teste de que todo evento comercial tem regra (TR-469: sem conta configurada, a operação é recusada) |
| FIN-04 | Eventos de arrecadação: classificados, não classificados, duplicidade, valor não confere, devoluções, guia de devolução não informada, recuperação de crédito, descontos à vista, bônus de parcelamento, recebimentos não identificados [FATO] | `LancamentoTipo.java`; `RecebimentoTipo` | PRÁTICA | AUSENTE | ED-32 com ARR-24 |
| FIN-05 | Contas a receber contábil por curto e longo prazo e por esfera (particular e público), movimentadas mês a mês [FATO pela entidade; INFERÊNCIA para a fórmula de saldo] | `ContaAReceberContabil`; processo 847 | PRÁTICA | AUSENTE | ED-32: saldo por conta (anterior, faturado, pago, parcelado, cancelado, devolvido, imposto, atual), como pede TR-475 |
| FIN-06 | PDD por meta de baixa definida no mês, com tolerância de 10% sobre a meta, aplicada quadra a quadra [FATO] | `CF` `gerarResumoDevedoresDuvidosos`; 03 §4.3 | GSAN (método) + POLÍTICA (escolha do método) | AUSENTE | ED-32: `acc.doubtful_method` (seção 5.2); provisão separada do saldo comercial (TR-466) |
| FIN-07 | Baixa como perda não extingue a dívida comercial; pagamento posterior vira recuperação de crédito [FATO] | `PagamentoSituacao` 15 e 16; `RecebimentoTipo` | PRÁTICA | AUSENTE | ED-32 |
| FIN-08 | Prescrição gera lançamento de cancelamento por prescrição, distinto conforme a conta já estivesse ou não fora da inadimplência por perda [FATO pelos tipos de lançamento] | `LancamentoTipo`; 03 §3.9 | PRÁTICA | AUSENTE | ED-32 com COB-20 |
| FIN-09 | Retenções de órgãos públicos (IR, Cofins, CSLL, PIS) deduzidas da fatura e lançadas como impostos retidos [FATO pelos itens] | `LancamentoItem`; 03 §4.1 | LEGAL [EXT] (IN RFB 1.234/2012) | PARCIAL: `bill_taxes`, `TaxWithholding` no billing | ED-32: lançamento automático na arrecadação (SG-434) e relatório de retidos por cliente (SG-445) |
| FIN-10 | Reprocessamento apaga resumos e lançamentos e gera de novo [FATO] | processos de apagar e atualizar resumo; 03 §4.3 | GSAN | COBERTA no plano: fechamento congelado (ED-26, ED-32) | Período fechado é imutável; correção por estorno no período aberto, com referência ao lançamento original |
| FIN-11 | Exportação ao sistema contábil por arquivo delimitado com conta, centro de custo, valores a débito e a crédito e histórico; variante por empresa [FATO] | `CF` `gerarIntegracaoContabilidade`; 03 §4.2 | POLÍTICA (formato) | AUSENTE; ED-32 e ED-62 (Betha, IPM, Fiorilli) | `acc.export_target` com adaptadores; exportação diária idempotente por período e sequência |
| FIN-12 | O aviso bancário tem lançamento próprio, que liga disponibilidade, arrecadação a classificar, tarifas e deduções [FATO pela origem 4] | `LancamentoOrigem`; processo 1291 | PRÁTICA | AUSENTE | ED-32 com ARR-15 e ARR-16 |
| FIN-13 | Receita a faturar: volume consumido e não faturado no fim do mês é apurado para competência [FATO pelo processo 898] | `ValorVolumesConsumidosNaoFaturado` | PRÁTICA [EXT] (regime de competência) | AUSENTE | ED-32 |
| FIN-14 | Envelhecimento de documentos a receber por faixa de dias, faixa de valor, categoria, esfera e perfil [FATO] | `DocumentosAReceberResumo`, `FaixaDocumentosAReceber` | PRÁTICA | AUSENTE | P3.22 (relatório) e ED-32 (base da matriz de perda, FIN-06) |
| FIN-15 | O GSAN não separa a dívida ativa no contábil [FATO por ausência] | — | GSAN (lacuna) | COBERTA no plano (TR-447, TR-465; ED-31 e ED-32) | Origem e contas próprias para inscrição, baixa e parcelamento de dívida ativa |

### 2.4 Pedidos de atendimento como OS tipada

Diretriz do dono do produto: todo pedido ou alteração de cliente ou de unidade consumidora gera OS, ainda que interna. No GSAN esses pedidos são telas de manutenção que gravam direto nas tabelas, com trilha só no histórico da entidade [FATO pelo padrão `Manter*`/`Inserir*` em `gcom/`]. Os editais já pedem "OS de registro" em parte deles (SG-389, SG-390, TR-418). Tipos propostos, todos com `consumer_unit_id` (exceto onde indicado), motivo de catálogo, anotação, aprovação conforme alçada e vínculo com o protocolo (P3.4):

| Tipo de OS (código) | Origem no GSAN | Efeito no encerramento | Aprovação | Tarefa |
|---|---|---|---|---|
| `INSTALLMENT_REQUEST` | efetuar parcelamento | cria o parcelamento a partir da simulação aprovada e emite o termo | alçada por desconto e por redução de entrada | P4.1 |
| `INSTALLMENT_CANCEL` | cancelar ou desfazer parcelamento a pedido | aplica COB-33 ou COB-34 com motivo de pedido do usuário | simples | P4.1 |
| `DEBT_TRANSFER` | transferir débito entre imóveis | move débitos com aceite do novo devedor; nunca de inquilino para proprietário sem aceite | dupla | P4.1 (TR-378) |
| `PAYMENT_TRANSFER` | transferência de pagamento | reclassifica o pagamento (ARR-10) | dupla | P3.1 |
| `PAYMENT_IDENTIFICATION` (interna; sem unidade até a identificação) | cliente fictício para pagamento não identificado | vincula pagamento a unidade e documento | simples | P3.1 |
| `REFUND_REQUEST` | guia de devolução | gera crédito ou devolução bancária (ARR-08, ARR-09) | dupla acima de alçada | P3.1 |
| `COLLECTION_HOLD` | situação especial de cobrança | inclui ou retira a suspensão com vigência e efeitos (COB-14) | dupla para corte e negativação | P4.1 |
| `NOTICE_DUE_DATE_CHANGE` / `NOTICE_CANCEL` | alterar ou cancelar documento de cobrança | altera vencimento ou cancela aviso pendente (SG-389, SG-390) | simples | P4.1 |
| `AUTO_DEBIT_ENROLLMENT` | manter débito automático | inclui ou exclui optante (legado) ou autorização Pix Automático | simples | P3.24, P4.5 |
| `NEGATIVE_LISTING_EXCLUSION` | excluir negativação | marca exclusão e entra no movimento seguinte (COB-43) | simples | ED-30 |
| `INFRACTION_APPEAL` | recurso de auto de infração | suspende o débito até o julgamento (COB-23) | julgamento | ED-24 |
| `RECONNECTION` | religação por pagamento | OS de campo gerada pelo evento, não pelo atendente (COB-12) | automática | P3.4 |

Regra transversal proposta (PRÁTICA): a OS é o registro da intenção e da decisão; o efeito financeiro só acontece no encerramento aprovado, com `idempotency_key` derivada da OS, de modo que reabrir ou reenviar a OS não duplica débito, crédito ou parcelamento.

## 3. Máquinas de estado inferidas

Nomes de estado em inglês, próprios do Ziip SAN; a numeração do GSAN não é reutilizada. Onde o Ziip SAN acrescenta estado que o GSAN não tem, a linha diz "novo".

### 3.1 Pagamento

Base: `PagamentoSituacao` [FATO], com situação atual e anterior gravadas a cada mudança [FATO]; transições pelo classificador [FATO] e pelos fluxos de devolução, transferência e recuperação [INFERÊNCIA pela existência das situações e dos relatórios].

```
OPEN ──classificação──> CLASSIFIED | DUPLICATE | EXCESS | AMOUNT_MISMATCH | DOC_NOT_FOUND
                        | DOC_CANCELED | DOC_PARCELED | DOC_PRESCRIBED | DOC_PROCESSING_ERROR | TO_ACCOUNT
TO_ACCOUNT ──fechamento do faturamento──> reclassificação (volta ao funil)
AMOUNT_MISMATCH ──diferença dentro da tolerância de ajuste──> CLASSIFIED (+ débito ou crédito de ajuste)
AMOUNT_MISMATCH | DOC_NOT_FOUND ──OS PAYMENT_IDENTIFICATION ou PAYMENT_TRANSFER──> reclassificação
DUPLICATE | EXCESS ──excedente registrado──> TO_WRITE_DOWN ──devolução concluída──> DUPLICATE_REFUNDED
DUPLICATE ──reaproveitado em débito do mesmo usuário──> CREDIT_RECOVERY_DUPLICATE
DOC_CANCELED ──reaproveitado──> CREDIT_RECOVERY_CANCELED
DOC_PRESCRIBED ──(novo) recebimento válido──> CLASSIFIED com evento de recuperação (ARR-05)
qualquer ──estorno por transferência (novo)──> OPEN no destino, com evento de origem preservado
```

Invariantes propostas: toda transição grava `payment_status_events` (já existe); `DUPLICATE_REFUNDED` e `CREDIT_RECOVERY_*` são finais; nenhuma transição em período fechado (ARR-23).

### 3.2 Documento de cobrança

Base: `CobrancaAcaoSituacao` e `CobrancaDebitoSituacao` [FATO]; motivo de não entrega no documento [FATO].

```
(seleção) ──recusado──> registro em dunning_skips com motivo (não há documento)
ISSUED ──(novo) entrega registrada com prova──> DELIVERED
ISSUED ──não entregue (motivo)──> NOT_DELIVERED ──reemissão──> ISSUED (novo documento, predecessor = anterior)
DELIVERED ──itens pagos antes do prazo──> SUSPENDED_BY_PAYMENT
DELIVERED ──itens parcelados──> SUSPENDED_BY_INSTALLMENT
DELIVERED ──itens cancelados ou retificados para zero──> SUSPENDED_BY_CANCELLATION
DELIVERED ──prazo vencido, ação de campo──> OS gerada ──OS executada──> EXECUTED
                                                       └─OS de fiscalização──> INSPECTED
ISSUED | DELIVERED ──validade expirada sem execução──> EXPIRED
ISSUED | DELIVERED ──OS NOTICE_CANCEL──> CANCELED
```

O GSAN distingue suspensão "antes" e "depois" da data-limite [FATO]; no Ziip SAN isso vira atributo do evento (data do pagamento contra a data-limite), não estado. Só `DELIVERED` com prova habilita a OS de corte (G9, classe a).

### 3.3 Parcelamento

Base: `ParcelamentoSituacao` (normal, desfeito, concluído, cancelado) [FATO]; confirmação por indicador do sistema [FATO]; reparcelamento [FATO para a existência; INFERÊNCIA para o estado do parcelamento substituído].

```
(simulação, sem persistir) ──OS INSTALLMENT_REQUEST aprovada──> PENDING_CONFIRMATION (se inst.requires_confirmation)
PENDING_CONFIRMATION ──termo aceito──> ACTIVE
ACTIVE ──entrada não paga no prazo + carência──> UNDONE (contas voltam ao estado anterior)    [se inst.unpaid_down_payment_action = UNDO]
ACTIVE ──entrada não paga──> ACTIVE com entrada lançada na próxima conta                       [se KEEP_AND_CHARGE_NEXT_BILL]
ACTIVE ──prestações em atraso ≥ inst.cancel_after_overdue──> CANCELED (saldo vira débito, regra de desconto em inst.discount_on_cancel)
ACTIVE ──novo parcelamento incorpora o saldo──> (novo) REPLACED, com vínculo ao sucessor
ACTIVE ──última prestação paga ou bônus aplicado──> CONCLUDED
ACTIVE ──antecipação total──> CONCLUDED (juros futuros abatidos, COB-37)
ACTIVE ──OS INSTALLMENT_CANCEL──> CANCELED (motivo: pedido do usuário)
```

`UNDONE`, `CANCELED`, `REPLACED` e `CONCLUDED` são finais. O estado das contas parceladas acompanha: `INSTALLMENT` enquanto ativo; volta ao anterior em `UNDONE`; em `CANCELED` e `REPLACED` permanece `INSTALLMENT`, porque o saldo passou a outro instrumento.

### 3.4 Negativação

Base: situação de cobrança do imóvel (em análise, carta enviada, negativado) [FATO]; `NegativacaoImoveis` com data de confirmação e exclusão [FATO]; registro do movimento com aceite ou rejeição e motivo [FATO].

```
(comando, simulação opcional) ──critério atendido──> SELECTED      ──não atendido──> registro de recusa com motivo
SELECTED ──aviso prévio emitido──> NOTICE_SENT
NOTICE_SENT ──(novo) entrega comprovada e prazo neg.notice_min_days decorrido──> READY
NOTICE_SENT ──pagamento, parcelamento ou contestação no prazo──> DROPPED
READY ──movimento enviado──> SENT ──retorno rejeitado──> REJECTED (motivo do birô; reenvio só por novo comando)
SENT ──retorno aceito──> ACCEPTED ──envio + prazo do contrato + 1 dia──> CONFIRMED
CONFIRMED ──pagamento, parcelamento com entrada paga, contestação, decisão judicial ou (novo) 5 anos──> EXCLUSION_PENDING
EXCLUSION_PENDING ──movimento de exclusão enviado no prazo de neg.exclusion_business_days──> EXCLUDED
```

O GSAN não tem `READY` nem a expiração de 5 anos [FATO por ausência]; o Ziip SAN trava as duas (ED-30, `ret.negative_listing_max_years`). Parcelamento de débito negativado que for desfeito ou cancelado não renegativa automaticamente: exige novo comando com novo aviso [proposta].

## 4. Pseudocódigo

Escrito para o Ziip SAN, a partir das regras do GSAN, sem seguir a estrutura do código-fonte. Onde a regra é proposta e não vem do GSAN, está marcado.

### 4.1 Classificação completa do período

O relatório 03 §2.5 descreve o ramo da conta. Aqui entram a ordem dos documentos, a devolução, o ajuste e a recuperação.

```
classificar(tenant, referência_arrecadação):
  exigir período de arrecadação aberto                                   # ARR-23
  para cada unidade com pagamentos OPEN ou pendentes (partição por localidade):
    para cada tipo de documento na ordem: conta, guia de pagamento, débito a cobrar avulso, documento agrupador
      para cada documento com pagamentos ativos (exclui DUPLICATE_REFUNDED e CREDIT_RECOVERY_*):
        resultado ← classificador(documento, pagamentos, política vigente na data do pagamento)
        se documento agrupador: distribuir o valor nos itens conforme pay.partial_settlement_order   # ARR-19
        gravar situação nova e anterior de cada pagamento (+ evento)
        se resultado quita o documento: marcar pago na mesma transação; publicar evento "documento quitado"
        se ajuste ≠ NONE: criar débito a cobrar ou crédito a realizar com chave = (pagamento, "AJUSTE")   # ARR-02
        se DUPLICATE ou EXCESS: registrar excedente; publicar "pagamento a maior" (aviso ao usuário, ARR-07)
        se DOC_PRESCRIBED: tratar como recebimento válido e lançar recuperação                        # ARR-05
        se DOC_CANCELED e documento baixado como perda: CREDIT_RECOVERY_CANCELED                         # ARR-25
  para cada devolução pendente:
    sem pagamento de origem em DUPLICATE ou EXCESS   → pendente "sem duplicidade"
    sem instrumento de devolução (crédito ou transferência) → pendente "sem guia"
    valor da devolução ≠ excedente disponível        → pendente "valor não confere"
    senão                                             → CLASSIFIED; pagamento de origem → DUPLICATE_REFUNDED
  pagamentos que continuam DOC_NOT_FOUND ou AMOUNT_MISMATCH: abrir OS interna PAYMENT_IDENTIFICATION (uma por pagamento, idempotente)
  efeitos do evento "documento quitado" (assíncronos, pelo outbox, ADR-0014):
    suspender documentos de cobrança que contêm o item (COB-08)
    se a unidade está cortada por esse débito: gerar OS RECONNECTION conforme rec.release_condition (COB-12)
    se o débito está negativado: marcar exclusão pendente (COB-43)
    se o item é prestação de parcelamento: atualizar contagem de prestações pagas e bônus (COB-36)
```

Diferenças deliberadas em relação ao GSAN: classificação contínua (por arquivo recebido) além da mensal, porque o efeito sobre corte e negativação não pode esperar o fim do mês; tolerâncias e vencedor da duplicidade vindos da política vigente; nada é apagado para reprocessar.

### 4.2 Parcelamento: simular, concluir, acompanhar, desfazer, cancelar, reparcelar

```
simular(unidade, itens escolhidos, data_base):                           # TR-377, TR-389
  recusar com motivo se: hold que bloqueia parcelamento; débito ajuizado ou inscrito misturado com não inscrito;
                         conta contestada entre os itens; limite de reparcelamentos atingido     # COB-39
  política ← installment_policy vigente (resolução × situação da ligação × perfil × categoria)  # COB-26
  para cada item: principal + acréscimos na data_base (SurchargeCalculator)
  aplicar descontos na ordem definida pela política, registrando cada um                        # COB-27
  saldo_reparcelado ← saldo presente de parcelamentos ativos incluídos (sem juros futuros)      # COB-35
  para n em 1..máximo da política:
    entrada_mínima ← maior entre as regras de entrada configuradas que se aplicam              # COB-28 (proposta: "maior entre", ver nota)
    entrada_mínima ← max(entrada_mínima, saldo_reparcelado × percentual de reparcelamento)
    a_parcelar ← total_descontado − entrada
    prestação ← conforme inst.interest_method (NONE: a_parcelar / n; SIMPLE; PRICE)             # COB-30
    descartar n se prestação < inst.min_installment_amount                                       # COB-29
  devolver opções com demonstrativo completo (valor total, encargos, número e valor das prestações)  # [EXT] informação prévia

concluir(opção escolhida, OS INSTALLMENT_REQUEST aprovada):
  chave ← OS; se já concluído com essa chave, devolver o existente
  contas incluídas → INSTALLMENT; parcelamentos incorporados → REPLACED
  entrada → guia ou conta de entrada, vencimento em inst.down_payment_due_days
  prestações → débitos a cobrar por natureza (água, esgoto, serviço) e juros em débito próprio  # COB-31
  descontos → registrados no parcelamento (não como crédito solto)
  termo de parcelamento e confissão de dívida emitidos; estado ACTIVE ou PENDING_CONFIRMATION   # COB-32

lote diário:
  entrada vencida há mais de inst.down_payment_grace_days e não paga:
     UNDO → contas voltam ao estado anterior, débitos e juros do parcelamento cancelados, REPLACED reativados
     KEEP_AND_CHARGE_NEXT_BILL → entrada lançada na próxima conta como débito a cobrar
  prestações vencidas e não pagas ≥ inst.cancel_after_overdue:
     CANCELED; saldo das prestações restantes em débito novo com acréscimos desde o vencimento de cada prestação;
     descontos tratados por inst.discount_on_cancel (proporcional às prestações pagas, por padrão)
  pagamento consecutivo em dia atingiu a regra de bônus: perdoar as prestações finais (lançamento contábil próprio)
```

Nota sobre a entrada: o GSAN aplica as regras de entrada mínima em sequência, cada uma sobrescrevendo a anterior quando configurada (03 §3.6), e combina o resultado com a entrada informada pelo usuário numa rotina cuja precedência exata não foi lida. A proposta "maior entre as regras aplicáveis" é previsível e auditável; a redução abaixo dela só por alçada registrada na OS.

### 4.3 Elegibilidade de uma unidade para uma ação de cobrança

Agrupa os 27 motivos do GSAN em verificações ordenadas da mais barata para a mais cara; a primeira que falhar grava o motivo e encerra a avaliação da unidade.

```
avaliar(unidade, ação, data_referência):
  1  unidade inexistente, excluída ou inativa                    → UNIT_INVALID
  2  hold de cobrança vigente que paralisa esta ação             → COLLECTION_HOLD
  3  situação de cobrança incompatível (ajuizada, inscrita, com terceirizada, em análise de negativação)
                                                                 → COLLECTION_STATUS
  4  situação de ligação de água ou de esgoto fora do exigido   → CONNECTION_STATUS
  5  classe protegida sem regra própria cumprida (cut.protected_classes)        → PROTECTED_CLASS (novo)
  6  já existe documento válido desta ação ou de sucessora        → ALREADY_NOTIFIED
  7  predecessora exigida sem documento entregue há ≥ dias mínimos e dentro da validade,
     ou sem OS da predecessora executada no prazo                 → PREDECESSOR_MISSING
  8  débito elegível ← contas vencidas há ≥ cut.min_overdue_days, não contestadas, não parceladas,
     não prescritas, idade ≤ cut.max_debt_age_days; + guias e débitos a cobrar se a ação os cobra
     − créditos a realizar se a ação os abate
     sem débito elegível                                          → NO_DEBT
  9  débito só do mês ou só antigo, quando o critério não aceita  → DEBT_AGE_RULE
  10 linha do critério para perfil e categoria inexistente        → NO_CRITERIA
  11 valor ou quantidade de contas fora da faixa (com mínimos próprios para optante e inquilino)
                                                                 → OUT_OF_RANGE
  12 débito de terceiro como único motivo, se cut.third_party_debt_blocks = false → THIRD_PARTY_DEBT (novo)
  13 consumo médio ou situação de fiscalização fora do critério   → CONSUMPTION_RULE
  14 para ação de campo: data prevista em dia vedado ou fora de cut.business_hours → FORBIDDEN_DATE (novo, classe a)
  elegível → entra na fila da atividade, ordenada por cut.priority_order
  ao emitir: se a fila excede cut.daily_quota ou a meta da unidade → QUOTA_EXCEEDED para o excedente
```

Diferenças em relação ao GSAN: os passos 5, 12 e 14 não existem lá; o passo 8 exclui sempre a conta contestada (COB-15), enquanto o GSAN deixa o critério decidir.

## 5. Parâmetros

### 5.1 Parâmetros do GSAN neste domínio

Origem: atributos de `SistemaParametro` [FATO pelos nomes no mapeamento], campos de entidades de configuração [FATO] e constantes no código [FATO]. "Existe" indica se o Ziip SAN já tem a chave em `PARAMETROS-DO-TENANT.md` ou no código.

| Parâmetro no GSAN | Significado | Classe | Chave no Ziip SAN | Existe |
|---|---|---|---|---|
| constante 0,03 | tolerância de classificação | d | `pay.classify_tolerance` | sim |
| constante 2,00 | tolerância de ajuste | d | `pay.adjust_tolerance` | sim |
| regra fixa no código | vencedor da duplicidade | d | `pay.duplicate_winner` | sim |
| `codigoEmpresaFebraban` | código da empresa no código de barras | d | `pay.febraban_company_code` | sim (sem coluna própria) |
| `numeroLayoutFebraban` | versão de leiaute aceita | d | `pay.agreements` (`accepted_return_versions`) | sim |
| `indicadorValorMovimentoArrecadador` | valor do aviso: informado ou calculado | d | `pay.notice_amount_source` | não |
| tarifa e float do contrato do arrecadador | custo e prazo de repasse por forma | d | `pay.agreement_fees` | não |
| `valorGuiaFichaComp`, `valorDemonstrativoParcelamentoFichaComp` | valor a partir do qual o documento vira boleto | d | `pay.document_kind_by_debt` | parcial |
| `numeroDiasValidadeExtrato` (e com permissão especial) | validade do extrato com código de barras | d | `pay.statement_validity_days` | não |
| prazo do cancelamento de guia não paga | dias até cancelar guia vencida | d | `pay.unpaid_slip_cancel_days` | não |
| `numeroDiasCalculoAcrescimos` | carência em dias úteis | d | `fee.grace_business_days` | sim |
| `numeroMesesCalculoCorrecao` | meses mínimos para correção | d | `fee.correction_*` | sim |
| índices mensais de acréscimo (multa, juros, fator, limites, indicadores de mensal) | modo e taxas de acréscimo | b (multa), d (demais) | `fee.fine_pct`, `fee.interest_*`, `surcharge_indexes` | parcial (faltam modos, 5.2) |
| `indicadorAtualizacaoTarifaria` | correção pela tarifa em vez de índice | d | `fee.correction_mode` com opção `TARIFF` | não |
| `numeroDiasVencimentoCobranca` | vencimento do documento de cobrança e base do desfazer | d | `dun.notice_due_days` | não |
| dias mínimos da predecessora, validade e vencimento da ação | encadeamento das ações | c (aviso de corte), d (demais) | `cut.min_notice_days`, `cut.notice_validity_days`; `dun.action_timing` | parcial |
| quantidade máxima de documentos da atividade; metas | cota | d | `cut.daily_quota`, `dun.unit_targets` | não |
| linhas do critério de cobrança | elegibilidade por perfil e categoria | d | `dun.criteria`; `cut.min_overdue_days`, `cut.min_debt_amount`, `cut.max_debt_age_days` | parcial |
| `numeroDiasEncerramentoOrdemServico`, `numeroDiasEncerramentoOSSeletiva` | prazo para encerrar OS de cobrança | d | `dun.wo_close_days` | não |
| tipos de situação especial de cobrança | catálogo de suspensões | d | `dun.hold_types` | não |
| perfil, resolução, faixas e quantidades de prestação | política de parcelamento | d | `inst.policy` (tabelas versionadas) | não |
| `numeroDiasVencimentoEntradaParcelamento` | vencimento da entrada | d | `inst.down_payment_due_days` | não |
| `indicadorParcelamentoConfirmado` | parcelamento exige confirmação | b | `inst.requires_confirmation` | não |
| `indicadorTabelaPrice` | método de juros | b | `inst.interest_method` | não |
| constante de 3 contas | prestações em atraso que cancelam | c | `inst.cancel_after_overdue` | não |
| `numeroMaximoParcelasFinanciamento`, `percentualTaxaJurosFinanciamento` | serviços financiados na conta | d | `inst.service_max_installments`, `inst.service_interest_pct` | não |
| `numeroMaximoParcelaCredito` | crédito a realizar em até N parcelas | d | `bill.credit_max_installments` | não |
| `numeroMaximoParcelasContratosParcelamento`, indicadores de bloqueio | contrato por cliente | d | `inst.customer_contract_*` | não |
| `numeroDiasPrazoRecursoAutoInfracao`, `numeroMaximoParcelasAutoInfracao` | recurso e parcelas do auto | c (prazo), d (parcelas) | `inf.appeal_days`, `inf.max_installments` | não |
| `indicadorCertidaoNegativaEfeitoPositivo` e afins | regras da certidão negativa | b | `cert.*` | não |
| prazo de inclusão do contrato do birô; `codigoPeriodicidadeNegativacao` | confirmação e agenda da negativação | d | `neg.bureau_contracts`, `neg.schedule` | não |
| tabela de prescrição por esfera de poder | prazo de prescrição | c | `ret.prescription_years` | parcial (sem esfera) |
| `indicadorTransferenciaComDebito` | transferência de titularidade com débito | a ou b (5.2) | `unit.transfer_with_previous_debt` | não |
| meta de baixa e itens da PDD | provisão | d | `acc.doubtful_*` | não |
| parâmetros contábeis por chave | conta débito, crédito, histórico | d | `acc.posting_rules` | não |
| remuneração e janela da cobrança por resultado | terceirizada | d | `dun.outsourced_*` | não |
| `indicadorExibeMensagemNaoReceberPagamento`, `clienteFicticioParaAssociarOsPagamentosNaoIdentificados`, `clienteResponsavelNegativacao` | mecanismos internos do GSAN | — | não absorver | — |

### 5.2 Bifurcações convertidas em parâmetro proposto

Diretriz de 14/10/2026: todos os caminhos legítimos ficam configuráveis; o padrão de fábrica é o mais protetivo ao usuário; proibição expressa em lei federal fica travada. Chaves novas, a incluir em `PARAMETROS-DO-TENANT.md` no grupo indicado.

| Chave (grupo) | Opções | Padrão de fábrica | Classe | Fundamento da bifurcação |
|---|---|---|---|---|
| `fee.fine_mode` (G5) | `ONE_TIME`, `DAILY_CAPPED` (percentual diário até o teto de `fee.fine_pct`) | `DAILY_CAPPED` | b | GSAN tem os dois; normas de agência divergem; o diário com teto nunca supera o fixo |
| `fee.interest_mode` (G5, amplia a chave atual) | `SIMPLE_PER_DAY`, `SIMPLE_PER_DAY_CAPPED`, `MONTHLY_BASIS_30`, `CALENDAR_MONTH` | `SIMPLE_PER_DAY` | b | agências divergem (por dia com teto, como a ARIS, contra mensal base 30); mês cheio cobra o mês inteiro no primeiro dia; capitalização segue travada |
| `fee.correction_mode` (G5) | `NONE`, `BY_INDEX`, `TARIFF` | `NONE` | d | GSAN corrige por índice ou pela tarifa; ERI ou contrato decide |
| `fee.charge_moment` (G5) | `NEXT_BILL`, `AT_PAYMENT_DOCUMENT` | `NEXT_BILL` | b | GSAN lança na conta seguinte; documento de arrecadação não carrega valor variável |
| `fee.exemptions` (G5) | conjunto: `AUTO_DEBIT`, `BANK_DELAY`, `PROVIDER_FAULT`, `COLLECTION_HOLD` | todas ligadas | c | GSAN isenta débito automático e situação especial; o tenant só acrescenta isenção |
| `bill.carry_over_unpaid` (P2.5) | `OFF`, `ON` | `OFF` | b | arrasto do GSAN; agências que vedam acúmulo (relação com `fee.no_carry_over`) |
| `bill.cut_connection_charge` (P2.5) | `NONE`, `MIN_TARIFF_PCT`, `AVAILABILITY_FEE` | `NONE` | b | cobrança de ligação cortada varia por ERI |
| `pay.refund_mode` (G4) | `USER_CHOICE`, `CREDIT_NEXT_BILL`, `BANK_TRANSFER` | `USER_CHOICE` | b | GSAN aceita crédito em conta ou guia de devolução |
| `pay.partial_settlement_order` (G4) | `OLDEST_DUE_FIRST`, `USER_CHOICE`, `REJECT_PARTIAL` | `OLDEST_DUE_FIRST` | b | quitação de documento agrupador pago a menor; o mais antigo primeiro reduz acréscimo e risco de corte |
| `pay.duplicate_winner` (G4, chave existente) | `OLDEST_PAYMENT`, `NEWEST_PAYMENT` | `OLDEST_PAYMENT` | d | hoje sem sugestão; propõe-se o padrão |
| `dun.notice_fee` (novo grupo de cobrança) | `NONE`, `FIXED` (valor) | `NONE` | b | GSAN permite taxa de emissão; editais pedem a opção (TR-398) |
| `rec.release_condition` (G9, substitui `rec.requires_settlement`) | `MOTIVATING_DEBT_PAID_OR_AGREED`, `DOWN_PAYMENT_PAID`, `FULL_SETTLEMENT` | `MOTIVATING_DEBT_PAID_OR_AGREED` | b | condição de religação varia por ERI |
| `inst.interest_method` (novo grupo de parcelamento) | `NONE`, `SIMPLE`, `PRICE` | `NONE` | b | GSAN escolhe Price ou capitalizado; o capitalizado dividido por n não é oferecido |
| `inst.down_payment_rule` | `NONE`, `MIN_PCT`, `MIN_PCT_OR_MIN_TARIFF`, `LAST_BILL` | `NONE` | b | entrada obrigatória ou não (TR-360) |
| `inst.unpaid_down_payment_action` | `KEEP_AND_CHARGE_NEXT_BILL`, `UNDO` | `KEEP_AND_CHARGE_NEXT_BILL` | b | GSAN tem os dois caminhos (desfazer e incluir a entrada em conta); manter o acordo preserva os descontos |
| `inst.down_payment_grace_days` | inteiro | piso de fábrica a definir | c | prazo para desfazer (TR-362) |
| `inst.cancel_after_overdue` | inteiro (prestações) | 3 | c | constante do GSAN; o tenant só amplia |
| `inst.discount_on_cancel` | `KEEP_PROPORTIONAL`, `REVOKE_ALL` | `KEEP_PROPORTIONAL` | b | perda dos descontos no cancelamento |
| `inst.requires_confirmation` | `SIGNED_TERM`, `DOWN_PAYMENT`, `NONE` | `SIGNED_TERM` | b | GSAN tem indicador de confirmação; o termo aceito protege contra acordo não desejado |
| `inst.bonus_installments` | desligado ou regra (prestações consecutivas, prestações perdoadas) | desligado | d | parcela bônus do GSAN |
| `neg.channels` (novo grupo de negativação) | `NONE`, `BUREAU`, `PROTEST`, `BOTH` | `NONE` | b | negativação e protesto são alternativos ou cumulativos (D4) |
| `neg.prior_notice_by` | `PROVIDER_AND_BUREAU`, `BUREAU` | `PROVIDER_AND_BUREAU` | c | Súmula 359 atribui ao birô [EXT]; o aviso do prestador é proteção adicional |
| `neg.notice_min_days` | inteiro | piso a definir com o jurídico | c | intervalo entre aviso entregue e inclusão |
| `neg.exclusion_business_days` | inteiro | 5 | c (teto legal, só reduz) | Súmula 548 [EXT] |
| `neg.excluded_profiles` | conjunto: `LOW_INCOME`, `PUBLIC_BODY`, `PROTECTED_CLASSES` | todos excluídos | c | GSAN exclui baixa renda e órgão público salvo marcação |
| `cert.positive_effect` | `ON`, `OFF` | `ON` | b | certidão com débito parcelado em dia |
| `unit.transfer_with_previous_debt` | `ALLOW_DEBT_STAYS_WITH_HOLDER`, `BLOCK` | `ALLOW_DEBT_STAYS_WITH_HOLDER` | a se o jurídico confirmar a natureza pessoal do débito [EXT]; senão b | indicador do GSAN |
| `acc.doubtful_method` (contábil) | `TARGET_WRITE_OFF`, `AGING_MATRIX`, `EXPECTED_LOSS` | sem padrão: escolha do contador do tenant | d | não afeta o usuário; GSAN usa meta, CPC 48 pede perda esperada [EXT] |

Travados (classe a), sem configuração: vedações de dia e horário do corte por inadimplência e aviso com prova (G9); religação sem taxa quando faltou aviso; juros sem capitalização; abatimento proporcional na antecipação (COB-37); conta contestada fora de aviso de corte e de negativação (COB-15); débito ajuizado ou inscrito fora das ações automáticas (COB-21); exclusão da negativação em até 5 dias úteis e expiração em 5 anos (COB-43, COB-44); o prestador não recusa pagamento (o GSAN tem indicador para exibir "não receber pagamento"; não absorver).

## 6. Melhores práticas

### 6.1 Manter (absorver como conceito)

1. Motivo registrado para toda unidade avaliada e recusada, na cobrança e na negativação, com relatório analítico e sintético (COB-04).
2. Linha original do arquivo bancário guardada junto do registro interpretado (ARR-13).
3. Situação atual e anterior em todo objeto com estado, com trilha de transições (seção 3).
4. Simulação obrigatória antes de emitir documento de cobrança, comando de negativação e parcelamento.
5. Política de parcelamento como norma versionada com vigência (resolução), separada do código (COB-26).
6. Débito a cobrar e crédito a realizar como mecanismo único para levar ajustes, acréscimos, prestações e devoluções à conta seguinte (ARR-02, COB-18, COB-31).
7. Separação do principal por natureza (água, esgoto, serviço) e por prazo, e dos juros do parcelamento em débito próprio, para o contábil (COB-31, FIN-03).
8. Aviso bancário conciliado com acertos e diferença acumulada, em vez de aceitar o arquivo como verdade (ARR-15).
9. Recuperação de crédito como evento próprio, distinto de recebimento comum (ARR-25, FIN-07).
10. Exclusão de negativação marcada no momento do pagamento, sem esperar o lote (COB-43).
11. Regra contábil por chave de evento com conta débito, conta crédito e histórico configuráveis (FIN-01).
12. Isenção de acréscimo quando o atraso não é do usuário, como no débito automático (ARR-18).

### 6.2 Não replicar

1. Tolerâncias, prazos e limites fixos no código (0,03; 2,00; 3 prestações; 10% da PDD).
2. Cliente fictício para pagamento não identificado (ARR-21) e cliente responsável fixo para a negativação.
3. Reprocessamento que apaga e regera resumos e lançamentos (FIN-10); tabelas de histórico para "fechar" o mês (ARR-23).
4. Juros do parcelamento pelo montante capitalizado dividido por n (COB-30).
5. Arredondamentos diferentes por módulo (`HALF_DOWN` no parcelamento, `HALF_UP` nos acréscimos; 03 §9); no Ziip SAN a mesma divergência já aparece entre billing e collection e está em §2.4 do plano.
6. Identificadores numéricos com colisão e regras por subclasse de empresa (03 §9).
7. "Carta enviada" sem prova de entrega como condição para negativar (COB-41) e ausência de vedação de dia no corte (COB-10).
8. Critério que permite cobrar ou negativar conta contestada (COB-15).
9. Indicador para não receber pagamento.
10. Telas de manutenção que alteram débito, pagamento ou situação de cobrança sem OS, só com histórico da entidade (seção 2.4).
11. Ajuste de valor por retificação da conta já paga (ARR-02): no Ziip SAN o ajuste é lançamento novo e a conta paga não muda.

## 7. Lacunas do Ziip SAN por impacto

| # | Lacuna | Impacto | Tarefa afetada | Regras |
|---|---|---|---|---|
| 1 | Classificação sem efeito: não baixa a conta, não cria débito ou crédito de ajuste, não avisa duplicidade, não há devolução nem fila de exceções | Alto: o paralelo conta a conta (GL.2) não fecha sem baixa e ajuste; NR 11 art. 37 [EXT] | P3.1 (ampliar) e nova tarefa de devolução no P3 | ARR-02, 04, 05, 07, 08, 09 |
| 2 | Funil de elegibilidade, documento de cobrança e motivos de não geração inexistentes; `service_orders` sem tipo de serviço nem efeito na ligação | Alto: sem o funil, a P3.4 bloqueia a OS de corte mas não tem de onde gerá-la; editais (TR-390 a TR-418) | P3.4, P4.1, ED-29, ED-36 | COB-01 a 09, 11 |
| 3 | Parcelamento: política, simulação, termo, entrada, desfazer, cancelar, reparcelar, antecipar | Alto: 35 requisitos de edital (TR-355 a TR-389); parcelamentos ativos do piloto precisam ser cobrados no paralelo | P4.1; P1.1 e P1.2 para a migração dos parcelamentos ativos | COB-26 a 39 |
| 4 | Negativação com aviso provado, exclusão em 5 dias úteis, expiração de 5 anos e protesto | Alto: risco CDC e LGPD [EXT]; SAMAE 2.15 crítico | ED-30 | COB-40 a 46 |
| 5 | Contábil por evento, PDD, contas a receber, fechamento congelado | Alto para editais (POC-37 a 40, 66 a 68) | ED-32 | FIN-01 a 15 |
| 6 | OS tipada para pedidos de atendimento (diretriz de 14/10/2026) | Médio-alto: transversal; sem ela, alterações de débito ficam sem trilha de decisão | P3.4, ED-36, P4.1 | seção 2.4 |
| 7 | Situação especial de cobrança e bloqueio por tutela judicial ou serviço essencial | Médio-alto: corte indevido de unidade protegida por decisão judicial | P4.1 (antecipar a parte de bloqueio de corte para P3.4) | COB-14, 21 |
| 8 | Modos de multa e juros, isenções e momento da cobrança dos acréscimos; duas implementações (billing e collection) | Médio: divergência de valor no paralelo | P2.5, P3.1 | COB-16 a 19 |
| 9 | Aviso bancário, tarifa e float por convênio | Médio: conciliação N3 da P3.1 depende disso | P3.1 | ARR-14 a 16 |
| 10 | Guia de pagamento e extrato com validade | Médio | P3.1, P3.3, ED-26 | ARR-11, 12, 20 |
| 11 | Religação disparada pelo pagamento e condição de religação | Médio | P3.4 | COB-12 |
| 12 | Prescrição em lote e tratamento de pagamento de dívida prescrita | Baixo-médio | P4.1 | ARR-05, COB-20, FIN-08 |
| 13 | Certidão negativa de débitos | Baixo | P3.22 ou ED-38 | COB-24 |
| 14 | Cobrança por resultado (terceirizada) | Baixo (sem requisito de edital identificado) | onda C, sob demanda | COB-22 |

## 8. Perguntas ao dono do produto

Só o que não é bifurcação; as bifurcações estão na seção 5.2 como parâmetro proposto.

1. **Efeito da classificação e devolução.** A lacuna 1 cabe na P3.1 ou pede tarefa própria?
   (a) Ampliar a P3.1 com baixa da conta, ajuste e fila de exceções, e criar tarefa nova no P3 só para devolução;
   (b) ampliar a P3.1 com tudo, inclusive devolução;
   (c) manter a P3.1 como está e levar baixa, ajuste e devolução à ED-26.
2. **Parcelamentos ativos do piloto.** Como tratá-los no paralelo da homologação local, antes da P4.1?
   (a) Migrar só os débitos a cobrar remanescentes (prestações a vencer), cobrados pela conta, sem política de parcelamento no P3;
   (b) migrar o parcelamento completo (termo, entrada, situação) e antecipar o núcleo da P4.1 para o P3;
   (c) excluir do paralelo as unidades com parcelamento ativo.
3. **Funil de cobrança antes da P4.1.** A P3.4 bloqueia a OS de corte, mas a geração do aviso fica na P4.1.
   (a) Antecipar para o P3 o funil mínimo do aviso de corte (critério, predecessora, motivos de não geração, cota);
   (b) no P3, aviso e OS de corte só manuais, com as travas da P3.4; funil na P4.1;
   (c) manter tudo na P4.1 e não usar corte no paralelo.
4. **OS tipadas da seção 2.4.** Onde nasce o catálogo de tipos?
   (a) Na P3.4, junto do protocolo, com os tipos desta seção já cadastrados e efeitos implementados à medida que cada módulo chega;
   (b) na ED-36 (OS completa no backoffice);
   (c) cada módulo cria os seus tipos na própria tarefa.
5. **Cobrança por resultado.** Entra no produto?
   (a) Onda C, sob demanda de cliente;
   (b) fora do escopo até aparecer em edital;
   (c) onda B, junto da ED-30.
6. **Parcela bônus.** O levantamento do legado do piloto (P1.1) deve procurar essa regra?
   (a) Sim, e o parâmetro `inst.bonus_installments` só é implementado se o piloto usar;
   (b) implementar o parâmetro genérico de qualquer forma, desligado;
   (c) não oferecer.

## 9. Limites

1. Leitura por amostragem dirigida dos controladores (dezenas de milhares de linhas cada), com apoio de subagentes de exploração; regras marcadas [FATO] foram vistas no código ou no mapeamento, mas a ordem exata dos ramos em rotinas longas pode não estar completa. A precedência entre as regras de entrada do parcelamento e a regra da parcela bônus continuam não confirmadas.
2. O banco do GSAN (cargas de situação, motivos, processos e parâmetros) não está no repositório; valores de parâmetros e a ordem real dos lotes não foram vistos, só os nomes.
3. Versão da fonte de 2018; as regras podem ter mudado nas companhias que ainda usam o GSAN.
4. Licença do GSAN não confirmada (relatório 03 §9); este documento registra conceitos e regras em redação própria, sem código, SQL, texto ou leiaute.
5. Todo item [EXT] (Lei 11.445, Lei 13.460, Lei 8.987, CDC arts. 42, 43 e 52, CC arts. 205 e 882, Súmulas 359 e 548 do STJ, NR 11 da ANA, CPC 48, IN RFB 1.234/2012) depende de validação jurídica antes de virar requisito ou trava; os pisos e tetos marcados "a definir" seguem o fluxo do ADR-0015.
6. As chaves de parâmetro da seção 5.2 são propostas; a inclusão em `PARAMETROS-DO-TENANT.md` e a classe final dependem do aceite do dono do produto, conforme o ADR-0002.
7. A situação do Ziip SAN foi conferida no código de `commercial/sigsan-collection` e `commercial/sigsan-billing` em 14/10/2026; `sigsan-dunning` e `sigsan-accounting` estão vazios. Mudanças posteriores a essa data não estão refletidas.
8. Relatórios `.jrxml`, telas JSP e o pacote `batch` só foram usados como evidência de existência de funcionalidade, não como fonte de regra.
