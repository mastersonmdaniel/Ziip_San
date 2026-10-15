# 20b — GSAN: catálogo de regras de micromedição, consumo e faturamento, cruzado com o Ziip SAN

Data: 14/10/2026. Fonte GSAN: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN\src\gcom` (somente leitura,
clean-room; arquivos em ISO-8859-1). Ponto de partida: `docs/levantamento/02-gsan-leitura-consumo-faturamento.md`
(11/10/2026), cujo conteúdo não é repetido. Projeto cruzado: `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` v2.13,
`docs/arquitetura/PARAMETROS-DO-TENANT.md`, `docs/arquitetura/FISCAL-PORTE-JAVA.md`, ADRs 0002, 0015 e 0031,
`docs/levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md` e `18e`, `docs/levantamento/08-regulatorio-saneamento-e-tarifas.md`
(b.1, d.1) e o código de `commercial/sigsan-billing` (`V4_1`) e `operations/sigsan-metering` (`V5_1`).

**Convenções.** [FATO] = visto no código ou no mapeamento citado; [INFERÊNCIA] = dedução do analista. Caminhos de
arquivo e nomes de método aparecem só como evidência; nenhum código, SQL, texto de tela, mensagem ou leiaute foi
copiado. Valores monetários de exemplo são fictícios. **Natureza**: LEGAL (lei ou norma de agência), POLÍTICA (escolha
do prestador; vira parâmetro do tenant), GSAN (particularidade do legado; não absorver), PRÁTICA (boa prática de
operação). **Situação no Ziip SAN**: COBERTA, PARCIAL, AUSENTE. Classes de parâmetro a/b/c/d conforme
`PARAMETROS-DO-TENANT.md` §2.

**Diretrizes do dono do produto aplicadas (14/10/2026).**
1. *Bifurcação vira parâmetro.* Onde lei, norma de agência ou prática legítima admitem caminhos alternativos, ou
   normas de mesma hierarquia divergem, o produto oferece todos como configuração do tenant, com o caminho mais
   protetivo ao usuário como padrão de fábrica. Essas bifurcações estão na seção 5, não nas perguntas.
2. *Trava legal.* Onde lei federal proíbe ou fixa limite (Lei 14.898/2024, Lei 14.015/2020, CDC), o caminho fica
   travado (classe a ou c), mesmo que a norma da agência seja mais permissiva.
3. *A unidade consumidora é o pilar.* Tudo se liga a `consumer_units`; toda solicitação ou alteração de cliente ou de
   unidade, inclusive cadastral, gera uma OS, ainda que interna e sem ida a campo. Onde o GSAN altera dado de
   faturamento direto, o catálogo indica a OS tipada correspondente (seção 2.9).

---

## 1. Resumo executivo

1. No GSAN a conta não se edita: toda correção gera conta nova ligada à anterior, com motivo de catálogo, e os
   pagamentos, itens de fatura e débito automático migram para ela. A P2.20 do plano segue o mesmo princípio.
2. Débito a cobrar e crédito a realizar são o saldo do cliente entre contas. O Ziip tem as tabelas, mas sem catálogo
   de origem e tipo, sem repartição por categoria, sem parcela bônus e sem regra para o que acontece com prestações
   já cobradas quando a conta é cancelada; o GSAN também não devolve essas prestações automaticamente.
3. O bônus de tarifa social do GSAN exige conta anterior paga em dia, consumo até 10 m³ e ausência de contas vencidas.
   As três condições contrariam a Lei 14.898/2024 e ficam travadas fora do produto.
4. A revisão de conta é estado ortogonal (motivo + data) que retém impressão, acréscimos, cobrança e débito automático.
   No Ziip ela existe só como coluna em `bills`, sem efeito em cobrança, arrecadação e emissão.
5. Situação especial de faturamento mistura paralisação de leitura, paralisação de faturamento (inclusive só de
   esgoto), consumo e volume fixos e crédito compensatório (nitrato: metade do valor da água). O Ziip cobre só o
   consumo fixo e a pausa, no motor puro.
6. Faltam no Ziip o cronograma com atividades e precedência, o encerramento do mês, a fatura agrupadora do órgão
   centralizador, a guia do usuário avulso e o auto de infração ligado ao débito a cobrar.
7. Pela diretriz 3, cada alteração direta de dado de faturamento do GSAN vira OS interna tipada no Ziip; isso cria
   dependência da P2.20 (fase P2) com o núcleo de OS, hoje só na P3.4: é a lacuna estrutural mais grave.
8. Foram identificadas 31 bifurcações que viram parâmetro do tenant (seção 5), 10 travas legais e 6 perguntas que não
   são bifurcação (seção 8).
9. As variações por empresa do GSAN (sete classes `*SEJB`) concentram-se em emissão, mensagem, qualidade da água,
   segunda via, revisão e acréscimos: tudo vira parâmetro, modelo de conta ou ponto de extensão, nunca fork.
10. As lacunas por impacto estão na seção 7.

---

## 2. Catálogo de regras

Colunas: **Regra** | **Evidência** (GSAN) | **Nat.** | **Ziip** | **Recomendação** (tarefa do plano, fase P1–P3 ou onda
A/B/C da trilha de editais; OS tipada quando houver alteração de dado).

### 2.1 Tarifa social, bônus e parcela bônus

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-01 | Bônus social gerado por rota no ciclo, como crédito a realizar de 1 prestação, tipo "desconto tarifa social", repartido por categoria [FATO] | `faturamento/ControladorFaturamento.java` `gerarBonusTarifaSocial` (UC0926); `batch/faturamento/BatchGerarBonusTarifaSocialMDB.java` | GSAN | COBERTA por decisão (`sigsan-billing/README.md`: não implementado) | Manter fora; o desconto social é calculado na tarifa (`SocialTariffRule`, P1.5), nunca como crédito posterior |
| FAT-02 | Elegibilidade ao bônus: perfil de imóvel "tarifa social", só subcategoria residencial 1, ligação de água ligada [FATO] | idem | GSAN | COBERTA (P1.5 usa CadÚnico/BPC) | Trava (classe a): elegibilidade só pelos critérios da Lei 14.898 e da ERI, nunca por perfil manual |
| FAT-03 | Bônus negado se a conta anterior não foi paga até o vencimento mais a carência de acréscimos, se houver conta anterior vencida e não paga ou se o consumo passou de 10 m³ [FATO] | idem (consumo comparado a constante 10) | GSAN, vedado | COBERTA (motor não recebe adimplência) | Trava (classe a): concessão independe de adimplência; teto de consumo que retira o benefício só se for ampliação do piso federal, nunca redução (classe c) |
| FAT-04 | Valor do bônus = tarifa mínima residencial da tarifa normal vigente × percentual do sistema [FATO] | `calcularValorBonusSocial`; `SistemaParametro.percentualBonusSocial` | GSAN | COBERTA | Não absorver; percentuais sociais ficam em `social.variable_bands` e `social.fixed_part_discount_pct` |
| FAT-05 | Critérios locais de exclusão por área construída máxima e consumo de energia máximo [FATO, existência dos parâmetros] | `SistemaParametro.areaMaximaTarifaSocial`, `consumoEnergiaMaximoTarifaSocial` | GSAN/LEGAL | AUSENTE (correto) | Trava (classe c): critério local só amplia o universo de beneficiários; critério que exclui quem a Lei 14.898 inclui é recusado |
| FAT-06 | Reprocessar o bônus apaga o crédito do mês ainda sem prestação realizada e gera de novo [FATO] | `apagarDadosBonusSocialInicioBatch` | PRÁTICA | COBERTA (idempotência por chave) | Nenhuma |
| FAT-07 | Anormalidade de leitura marcada como causa de perda do benefício social [FATO] | `micromedicao/LeituraAnormalidade.hbm.xml` (`ltan_icperdatarifasocial`) | LEGAL (perda só após aviso ≥ 3 meses) | PARCIAL | P1.5: a marca abre OS interna `SOCIAL_TARIFF_IRREGULARITY_NOTICE` e só conta prazo de aviso (`social.loss_notice_months`, classe c) |
| FAT-08 | Parcela bônus em parcelamento especial: conta do mês paga até 2 dias após o vencimento soma 1 ao contador; na terceira consecutiva o contador zera e o débito e o crédito do parcelamento ganham 1 parcela bônus; atraso ou falta de pagamento zera o contador [FATO] | `ControladorFaturamentoFINAL.atualizaQtdeParcelaPagaConsecutivaEParcelaBonus`; `DebitoACobrar` e `CreditoARealizar` (`*_nnparcelabonus`) | POLÍTICA comercial | AUSENTE | P4.1 (parcelamento), parâmetros `installment.bonus_*` (seção 5); campo `bonus_installments` em `debit_receivables`/`credit_receivables` desde a P2.5 |
| FAT-09 | Parcelas do crédito podem ser antecipadas [FATO, coluna] | `CreditoARealizar.hbm.xml` (`crac_nnparcelasantecipadas`) | PRÁTICA | AUSENTE | P2.20: OS `CREDIT_ANTICIPATION` aplica o saldo na próxima conta |

### 2.2 Créditos a realizar

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-10 | Origem do crédito em catálogo fixo de 16 itens: pagamento em duplicidade ou excesso, devolução de tarifa de água ou de esgoto, serviço indireto pago indevidamente, devolução de juros de parcelamento, desconto no parcelamento, valor cobrado indevidamente, descontos condicionais e incondicionais, ajuste para zerar conta, recuperação de crédito de conta cancelada ou parcelada, créditos anteriores de curto e longo prazo [FATO] | `faturamento/credito/CreditoOrigem.java` | PRÁTICA (contábil) | PARCIAL (`credit_receivables.origin` livre) | P2.5: enum de origem no produto (classe a: a origem determina a conta contábil) |
| FAT-11 | Tipo do crédito com indicador de geração automática e valor limite por tipo [FATO] | `CreditoTipo.hbm.xml` (`crti_icgeracaoautomatica`, `crti_vllimite`) | POLÍTICA | AUSENTE | P2.5: `credit_types` por tenant com `max_amount`; acima do limite, OS `CREDIT_ISSUE` com aprovação dupla |
| FAT-12 | Crédito repartido por categoria e economias (mesma regra do débito, diferença na primeira categoria) [FATO] | `CreditoARealizarCategoria`; UC0185 | PRÁTICA | AUSENTE | P2.5: `credit_receivable_categories`; usar `Apportionment` (maior resto) em vez do "resto na primeira" |
| FAT-13 | Crédito com referência contábil e referência de cobrança próprias, distintas da referência do crédito [FATO] | `CreditoARealizar.hbm.xml` (`crar_amreferenciacontabil`, `crar_amcobrancacredito`) | PRÁTICA | AUSENTE | P2.6 (fechamento contábil): `accounting_month` em débitos e créditos |
| FAT-14 | Cancelar crédito: sem prestação realizada e no mês contábil corrente, o registro some; senão vira CANCELADO com mês contábil ajustado; recusado se o imóvel está em cobrança administrativa [FATO] | `ControladorFaturamentoFINAL.cancelarCreditoARealizar` | PRÁTICA | PARCIAL (`status CANCELLED`) | P2.20: nunca apagar; sempre CANCELLED com OS `CREDIT_CANCELLATION`, motivo e aprovação; bloqueio por cobrança administrativa vira validação |
| FAT-15 | Crédito compensatório por água fora do padrão (nitrato): metade do valor da água da conta; no pré-faturamento nasce zerado e é atualizado no retorno [FATO] | `faturamento/UC0876GerarCreditoSituacaoEspecialFaturamento.java` | POLÍTICA/LEGAL (ERI) | AUSENTE | ED-22 e P4 qualidade: parâmetro `quality.noncompliance_compensation` (seção 5) |
| FAT-16 | Resíduo do crédito que a conta não comporta passa ao mês seguinte; o concedido no mês fica registrado [FATO, doc 02] | `FaturamentoUtil.java` | PRÁTICA | COBERTA (`CreditApplier`) | Nenhuma |

### 2.3 Débitos a cobrar

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-20 | Tipo de débito com vigência de valor e faixas de valor [FATO] | `faturamento/debito/DebitoTipoVigencia.hbm.xml`, `DebitoFaixaValore.hbm.xml` | POLÍTICA | AUSENTE | ED-21/ED-22 (tabela de serviços com vigência, como a tarifa) |
| FAT-21 | Cancelar débito com financiamento exige permissão especial; débito sem prestação cobrada pode ser removido [FATO] | `ControladorFaturamentoFINAL.cancelarDebitoACobrar` | PRÁTICA | PARCIAL (`status CANCELLED`) | P2.20: OS `DEBIT_CANCELLATION`; sempre CANCELLED, nunca apagar; permissão `billing.cancel-financed-debit` |
| FAT-22 | Débito em revisão não entra na conta [FATO, doc 02] | `ControladorDebitoACobrar` | PRÁTICA | COBERTA (`under_review`) | Ligar a OS `DEBIT_REVIEW` (entrada e saída) |
| FAT-23 | O tipo de débito diz se ele, sozinho, justifica gerar conta [FATO, doc 02] | `DebitoTipo` (`dbtp_icgeracaoconta`) | PRÁTICA | PARCIAL (por débito) | P2.5: mover para `debit_types.generates_bill` |
| FAT-24 | Débito nasce com referência ao protocolo (RA) e à OS de origem [FATO, doc 02] | `DebitoACobrar.hbm.xml` | PRÁTICA | AUSENTE | P2.5: `service_order_id` obrigatório para débito de serviço (diretriz 3) |
| FAT-25 | Auto de infração: situação com indicador "gera débito" e janela de recurso; débito em N parcelas; item marcado como multa ou como consumo retroativo; prazo de recurso e meses máximos de consumo retroativo são parâmetros do sistema [FATO] | `faturamento/autoinfracao/AutosInfracao.hbm.xml`, `AutoInfracaoSituacao.hbm.xml`, `AutosInfracaoDebitoACobrar.hbm.xml`; `SistemaParametro.numeroDiasPrazoRecursoAutoInfracao`, `numeroMaximoMesesCalculoConsumoAutoInfracao` | LEGAL (devido processo) + POLÍTICA | AUSENTE | ED-24: OS `INFRACTION_NOTICE` com prazo de defesa; débito só depois da decisão (seção 5, `infraction.charge_timing`) |

### 2.4 Revisão de conta

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-30 | Motivo de revisão em catálogo; entrada e saída registradas na conta [FATO] | `faturamento/conta/ContaMotivoRevisao.hbm.xml`; `colocarRevisaoConta`/`retirarRevisaoConta` | PRÁTICA | PARCIAL (`review_reason`, `reviewed_on`) | P2.20: `bill_reviews` (abertura, encerramento, motivo, OS `BILL_REVIEW`) |
| FAT-31 | Revisão automática por faixa de valor da conta [FATO] | `faturamento/ContaRevisaoFaixaValor.hbm.xml` (início e fim da faixa) | POLÍTICA | AUSENTE | P2.5: `bill.auto_review_value_bands` (seção 5) |
| FAT-32 | Revisão automática por estouro e baixo consumo, com impressão retida [FATO, doc 02]; variante de empresa redefine o motivo [FATO] | `ControladorFaturamentoCOMPESASEJB.determinarContaMotivoRevisao` | POLÍTICA | PARCIAL (metering detecta; billing não retém) | P2.5: `bill.hold_on_anomaly` (seção 5) |
| FAT-33 | Conta em revisão por antiguidade não pode ser retificada [FATO] | `controladores/ControladorRetificarConta.validarContaParaRetificacao` | GSAN | AUSENTE | Não absorver: revisão não deve impedir correção a favor do usuário |
| FAT-34 | Colocar em revisão conta vencida há mais de N dias exige permissão especial [FATO] | `gui/faturamento/conta/ColocarRevisaoContaAction.java`; `SistemaParametro.numeroDiasRevisaoComPermEspecial` | GSAN | AUSENTE | Não absorver contra o usuário: contestação do usuário é aceita em qualquer prazo até a prescrição (classe a); o limite vale só para revisão aberta pelo prestador; prazo de análise em `bill.review_max_days` |
| FAT-35 | Conta em revisão fica fora de acréscimo, cobrança, negativação e débito automático [FATO, doc 02] | `ControladorCobranca`; `ContaTipo` | LEGAL (contestação suspende cobrança, NR 11 arts. 37–38) | AUSENTE | P3.1, P3.4, P4.1 filtram contas com revisão aberta (trava, classe a) |

### 2.5 Cancelamento, retificação, alteração de vencimento e inclusão

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-40 | Cancelamento só de conta normal, incluída ou retificada já contabilizada; incluída ou retificada ainda não contabilizada é removida e a anterior restabelecida [FATO] | `ControladorFaturamentoFINAL.cancelarConta` | PRÁTICA | PARCIAL (estados) | P2.20: nunca remover; sempre nova versão; OS `BILL_CANCELLATION` |
| FAT-41 | Cancelamento por prescrição vai a estado próprio se a conta pode prescrever; senão é recusado [FATO] | idem | LEGAL (prescrição) | PARCIAL (`PRESCRIBED_DEBT`) | P4 (dívida ativa, ED-31): `ret.prescription_years` já previsto |
| FAT-42 | Cancelar exige protocolo de atendimento, salvo permissão especial; o protocolo fica gravado na conta [FATO] | idem | PRÁTICA | AUSENTE | Diretriz 3: toda operação nasce de OS; a permissão "sem protocolo" deixa de existir |
| FAT-43 | Cancelamento atualiza itens de negativação ligados à conta [FATO] | idem (`getControladorSpcSerasa`) | LEGAL (CDC art. 43) | AUSENTE | ED-30: evento `bill.cancelled` baixa a negativação no prazo legal |
| FAT-44 | O cancelamento não devolve as prestações de débito cobradas nem os créditos realizados [FATO: o método não os toca]; a recuperação se faz por crédito de origem "recuperação de crédito de conta cancelada" ou por inclusão de conta [INFERÊNCIA, pela origem 12] | `cancelarConta`; `CreditoOrigem` | GSAN | AUSENTE | P2.20: trava (classe a) – cancelar devolve créditos realizados ao saldo; débitos cobrados voltam a cobrar só se o motivo do cancelamento não os abranger (seção 4.4) |
| FAT-45 | Retificação recusada para conta prescrita, para conta com emissão em campo pendente e com data de vencimento nova anterior a hoje [FATO] | `ControladorRetificarConta.validarContaParaRetificacao`, `validarRetificarContasPrescritas`, `verificarContaParaRetificacao` | PRÁTICA | AUSENTE | P2.20 e ED-19 (fila de divergências do ADR-0031) |
| FAT-46 | Retificação por alteração da leitura faturada só se a leitura da conta coincide com a da medição e o arquivo de leitura da referência seguinte ainda não foi gerado; regrava leitura, consumo histórico e média [FATO] | `validarLeiturasRetificarConta`, `corrigirLeiturasEConsumos`, `atualizarMediaConsumoHistoricoAoRetificarConta` | PRÁTICA | AUSENTE | P2.20 + metering: OS `READING_CORRECTION` regrava `meter_readings`/`consumption_records` com nova versão e recalcula a média; se o pacote seguinte já saiu, gera ajuste na referência seguinte |
| FAT-47 | Motivo de retificação define quais colunas da conta podem mudar e se mexe em consumo; limite de ocorrências do mesmo motivo no ano [FATO] | `ContaMotivoRetificacaoColuna.hbm.xml`; `ContaMotivoRetificacao.hbm.xml` (`cmrt_iccompetenciaconsumo`, `cmrt_nnocorrmesmomotivonoano`) | PRÁTICA | AUSENTE | P2.20: `bill_rectification_reasons` com `editable_fields` |
| FAT-48 | Na retificação, pagamentos, item da fatura agrupadora, débito automático e cliente da conta migram para a conta nova; protocolo aberto sem OS é encerrado [FATO] | `atualizarPagamentoContaRetificada`, `atualizarFaturaItemContaRetificada`, `atualizarDebitoAutomaticoMovimentoContaRetificada`, `encerrarRA` | PRÁTICA | AUSENTE | P2.20; P3.1 (pagamento segue a versão vigente da conta) |
| FAT-49 | Valor antes e depois do refaturamento guardado [FATO] | `faturamento/conta/Refaturamento.hbm.xml` | PRÁTICA | PARCIAL (versões da conta) | P2.20: trilha por campo já prevista |
| FAT-50 | Limites de retificação: quantidade máxima, meses de antiguidade, retificação a menor e "norma de retificação" [FATO, parâmetros] | `SistemaParametro.qtdMaxContasRetificadas`, `numeroMesesRetificarConta`, `indicadorRetificacaoValorMenor`, `indicadorNormaRetificacao` | POLÍTICA | AUSENTE | Seção 5: limites só contra o usuário; a favor do usuário, sem limite além da prescrição (classe a) |
| FAT-51 | Retificação em lote por grupo, por cliente, por coleção e por consumo; contas em contrato de parcelamento ficam fora; retificação de contas pagas sem débito e crédito [FATO] | `retificarConjuntoConta*`, `obterColecaoSemContasEmContratoParcelamento*`, `retificarContasPagasSemDebitoCredito` | PRÁTICA | AUSENTE | P2.20 (lote com aprovação dupla e OS-mãe com OS-filhas, `parent_order_id`) |
| FAT-52 | Alteração de vencimento: limite de vezes, meses mínimos, dias após o vencimento e indicador de limite [FATO, parâmetros] | `SistemaParametro.numeroLimiteAlteracaoVencimento`, `numeroMesesMinimoAlteracaoVencimento`, `numeroDiasAlteracaoVencimentoPosterior`, `indicadorLimiteAlteracaoVencimento` | POLÍTICA | PARCIAL (`due_change_count`) | P2.20: OS `BILL_DUE_DATE_CHANGE`; seção 5 |
| FAT-53 | Inclusão manual de conta com motivo (transferência de débito, recuperação de crédito, cancelamento de parcelamento) [FATO, doc 02] | `ContaMotivoInclusao.hbm.xml` | PRÁTICA | PARCIAL (`ADDED`) | P2.20: OS `BILL_ISSUE_OUT_OF_CYCLE` |

### 2.6 Situação especial de faturamento

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-60 | Tipo de situação com sete indicadores: paralisa faturamento, paralisa leitura, vale para água, vale para esgoto, paralisa só o faturamento de esgoto, exige consumo informado, exige volume informado [FATO] | `faturamento/FaturamentoSituacaoTipo.hbm.xml` | POLÍTICA | PARCIAL (`SpecialSituation.pausesBilling` e consumos fixos) | ED-22: `special_billing_situation_types` por tenant com os sete indicadores |
| FAT-61 | Histórico por unidade: competência inicial e final, competência de retirada, consumo de água e volume de esgoto fixos, medidos e não medidos, observação de inclusão e de retirada [FATO] | `FaturamentoSituacaoHistorico.hbm.xml` | PRÁTICA | AUSENTE | ED-22: OS `SPECIAL_SITUATION_SET` e `SPECIAL_SITUATION_REMOVE`; a tabela guarda a OS |
| FAT-62 | Motivo em catálogo; comando em lote por faixa de localização, rota e categoria [FATO] | `FaturamentoSituacaoMotivo`, `FaturamentoSituacaoComando` | PRÁTICA | AUSENTE | ED-22: OS-mãe de lote com OS-filhas por unidade |
| FAT-63 | O sistema cria sozinho situação especial "paralisar leitura e faturar taxa mínima" para unidade com hidrômetro retirado ou consumo até 10 m³ por vários meses, com fim em referência + N meses [FATO] | `ControladorFaturamento.java` (uso de `numeroMesesReinicioSitEspFaturamento`; motivo de consumo baixo por vários meses) | POLÍTICA (critério) / GSAN (constante) | AUSENTE | ED-22: regra configurável que abre OS `SPECIAL_SITUATION_SET` gerada pelo sistema, com aviso ao usuário; `special_situation.max_months` (seção 5) |
| FAT-64 | Paralisado não gera conta; o consumo continua calculado e entra na média [FATO, doc 02] | `determinarFaturamentoImovel` | PRÁTICA | COBERTA (`BillGenerationRule`, metering) | Nenhuma |
| FAT-65 | Isenção de água, esgoto ou serviço por unidade [INFERÊNCIA: obtida por situação "paralisar faturamento"; não há entidade de isenção] | `FaturamentoSituacaoTipo` | POLÍTICA/LEGAL (lei municipal) | AUSENTE | ED-22: isenção como tipo de situação com base legal obrigatória e vigência |

### 2.7 Esgoto, consumo mínimo, rateio e grandes consumidores

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-70 | Ligação de esgoto guarda percentual de esgoto (valor), percentual de coleta (volume), percentual alternativo até um consumo, volume mínimo de esgoto e caixa de gordura [FATO] | `atendimentopublico/ligacaoesgoto/LigacaoEsgoto.hbm.xml` | POLÍTICA | PARCIAL (`SewerParameters` só como entrada) | ED-13: colunas em `service_connections` (esgoto); alteração por OS `SEWER_PARAMETERS_CHANGE` |
| FAT-71 | Esgoto sem água: volume pelo mínimo da ligação ou pelo não medido; poço medido soma ao consumo de água [FATO, doc 02] | `determinarDadosFaturamentoEsgoto` | POLÍTICA | COBERTA no cálculo | Seção 5 (`sewer.no_water_volume_rule`) |
| FAT-72 | Situação especial pode paralisar só o esgoto e fixar volume de esgoto medido e não medido [FATO] | FAT-60/61 | POLÍTICA | AUSENTE | ED-22 |
| FAT-73 | Atividade de faturamento atualiza a ligação de esgoto por rota em variante de empresa [FATO] | `ControladorFaturamentoCOMPESASEJB.atualizarLigacaoEsgotoPorRota` | GSAN | AUSENTE | Não absorver: mudança de ligação só por OS |
| FAT-74 | Consumo mínimo da ligação fixado manualmente sobrepõe o da tarifa [FATO, doc 02] | `ligacao_agua.lagu_nnconsumominimoagua` | POLÍTICA | PARCIAL (entrada da cascata) | ED-13: OS `CONNECTION_MINIMUM_CONSUMPTION_CHANGE` com motivo e vigência |
| FAT-75 | Indicador de tarifa por categoria e atualização tarifária na correção de débitos [FATO, parâmetros] | `SistemaParametro.indicadorTarifaCategoria`, `indicadorAtualizacaoTarifaria` | POLÍTICA | PARCIAL (`fee.correction_*`) | Seção 5 |
| FAT-76 | Perfil do imóvel: normal, grande, grande no mês, corporativo, tarifa social, programa social local, grande telemedido, corporativo telemedido, cadastro provisório; indicadores de gerar dados de leitura, bloquear retificação, grande consumidor e bloquear dados sociais [FATO] | `cadastro/imovel/ImovelPerfil.java` e `.hbm.xml` | POLÍTICA | AUSENTE | P1.3/ED-12: `unit_profiles` por tenant; "tarifa social" não é perfil (é enquadramento P1.5); alteração por OS `UNIT_PROFILE_CHANGE` |
| FAT-77 | Grande usuário definido por consumo mínimo e por número mínimo de economias [FATO, parâmetros] | `SistemaParametro.menorConsumoGrandeUsuario`, `menorEconomiasGrandeUsuario` | POLÍTICA (NR 11 arts. 17–20: contrato específico) | AUSENTE | ED-17/ED-22: `large_user.min_m3`, `large_user.min_economies` (classe d) |
| FAT-78 | Rateio de condomínio com incremento e decremento máximos e tolerância percentual [FATO, parâmetros] | `SistemaParametro.incrementoMaximoConsumoRateio`, `decrementoMaximoConsumoRateio`, `percentualToleranciaRateio` | POLÍTICA | PARCIAL (`CondominiumApportionment`) | ED-15; seção 5 (`condominium.apportionment_mode`) |

### 2.8 Ciclo, cobrança agrupada, emissão, mensagens, qualidade da água e segunda via

| # | Regra | Evidência | Nat. | Ziip | Recomendação |
|---|---|---|---|---|---|
| FAT-80 | Dez atividades de catálogo: pré-faturar (0), gerar arquivo de leitura (1), efetuar leitura (2), registrar leituras e anormalidades (3), gerar fiscalização (4), faturar grupo (5), distribuir contas (6), transmitir arquivo (7), simular (8), consistir leituras e calcular consumos (9); cada uma com ordem, obrigatoriedade, repetição e comando [FATO] | `faturamento/FaturamentoAtividade.java` e `.hbm.xml` | PRÁTICA | AUSENTE | P2.5: `billing_activity_catalog` do produto (classe a) e `billing_cycle_activities` por grupo |
| FAT-81 | Grupo com referência corrente, dia de vencimento e indicador de vencimento no mês da fatura [FATO] | `FaturamentoGrupo.hbm.xml` | POLÍTICA | AUSENTE | P2.5 (`V4_3`) |
| FAT-82 | Valor mínimo de emissão de conta [FATO, parâmetro] | `SistemaParametro.valorMinimoEmissaoConta` | POLÍTICA | AUSENTE (editais TR-298/299) | Seção 5 (`bill.min_issue_amount`) |
| FAT-83 | Fatura agrupadora por cliente e referência, com número sequencial, vencimento, validade, valor e taxa; itens com valor da conta e imposto [FATO] | `faturamento/conta/Fatura.hbm.xml`, `FaturaItem.hbm.xml`; `ControladorFaturamentoCAERSEJB.emitirFaturaClienteResponsavel` | PRÁTICA | AUSENTE | ED-22 + ED-26 (quitação em cascata); vínculo unidade × órgão por OS `CENTRAL_BILLING_LINK` |
| FAT-84 | Emissão separada para órgão público em variante de empresa [FATO] | `ControladorFaturamentoCAERNSEJB.emitirContasOrgaoPublico` | POLÍTICA | AUSENTE | ED-22 (modelo de conta por perfil) |
| FAT-85 | Faturamento antecipado por indicador do sistema; referência seguinte faturada no mesmo lote para órgão público [FATO] | `SistemaParametro.indicadorFaturamentoAntecipado`; `faturarImovelAntecipado` | POLÍTICA | AUSENTE | Seção 5 (`bill.advance_billing`) |
| FAT-86 | Impostos retidos: base inclui débitos e exclui créditos; alíquota e descrição de alíquota também no parâmetro do sistema [FATO] | `SistemaParametro.valorAliquotaImposto`, `descricaoAliquotaImposto`; `GerarImpostosDeduzidosContaHelper` | LEGAL (retenção na fonte por órgão público) | PARCIAL (`TaxWithholding`; sujeito passivo não modelado) | P1.16/P2.4: `customers.tax_withholding_profile`; alteração por OS `CUSTOMER_TAX_PROFILE_CHANGE` |
| FAT-87 | Mensagem da conta em três partes: se há impostos retidos, as linhas vão para eles; se há débito, mensagem de débito; senão busca a mensagem da referência do nível mais específico (setor, localidade, gerência, grupo) [FATO parcial; ordem exata INFERÊNCIA] | `ControladorFaturamentoFINAL.obterMensagemConta3Partes`; `conta/ContaMensagem.hbm.xml` | PRÁTICA + LEGAL (mensagens obrigatórias, `08` R-12) | PARCIAL (`consumption_anomalies.bill_message`) | P2.8: mensagens legais sempre impressas (classe a), antes das demais |
| FAT-88 | Qualidade da água por referência e área: turbidez, cloro, pH, cor, flúor, ferro, coliformes totais, fecais e termotolerantes, nitrato, alcalinidade, com amostras exigidas, analisadas e conformes [FATO]; variantes por face de quadra e por localidade [FATO] | `faturamento/QualidadeAgua.hbm.xml`; `ControladorFaturamentoCOSANPASEJB.obterDadosQualidadeAguaCosanpa`; `ControladorFaturamentoCAEMASEJB.obterDadosQualidadeAguaCAEMA` | LEGAL (Decreto 5.440/2005) | AUSENTE | P2.8 e P4 qualidade; seção 8, Q4 |
| FAT-89 | Segunda via: valor do parâmetro do sistema; taxa de emissão gerada como débito a cobrar com base na tarifa mínima normal ou social conforme o perfil [FATO, estrutura]; cinco empresas redefinem a emissão [FATO] | `SistemaParametro.valorSegundaVia`; `gerarDebitoACobrarTaxaEmissaoConta`; `ControladorFaturamento*SEJB.emitir2ViaContas` | POLÍTICA | PARCIAL (P3.3) | Seção 5 (`bill.second_copy_fee`) |
| FAT-90 | Envio de conta por e-mail com antecedência em dias e mensagem em Braille [FATO, parâmetros] | `SistemaParametro.qtdeDiasEnvioEmailConta`, `mensagemContaBraile` | PRÁTICA + LEGAL (acessibilidade, Lei 13.146) | AUSENTE | P2.8/P3.5: Braille e formato acessível sob pedido por OS `ACCESSIBLE_BILL_REQUEST` |
| FAT-91 | Contas em contrato de parcelamento podem ser bloqueadas para manutenção e para geração de débitos [FATO, parâmetros] | `SistemaParametro.indicadorBloqueioContasContratoParcel*` | POLÍTICA | AUSENTE | P4.1 |
| FAT-92 | Variação de consumo por dias e percentual sobre a média para índice [FATO, parâmetros] | `SistemaParametro.numeroDiasVariacaoConsumo`, `percentualMediaIndice` | POLÍTICA | PARCIAL (`ConsumptionPolicy`) | Metering: confirmar mapeamento na P2.5 |

### 2.9 OS tipadas para alterações que o GSAN faz direto (diretriz 3)

Toda linha abaixo é uma OS em `service_orders` (já existe em `platform/.../V1_1__create_tenant_schema.sql`, com
`consumer_unit_id` e `parent_order_id`), de tipo interno sem ida a campo, salvo indicação. A OS carrega motivo de
catálogo, anotação, solicitante, aprovação conforme parâmetro e o efeito (nova versão do dado).

| Tipo de OS (código) | Origem no GSAN | Efeito no Ziip | Campo? |
|---|---|---|---|
| `BILL_RECTIFICATION` | `retificarConta` | nova versão da conta; migra pagamento e fatura agrupadora | não |
| `BILL_CANCELLATION` | `cancelarConta` | conta CANCELLED; devolve créditos (FAT-44) | não |
| `BILL_DUE_DATE_CHANGE` | `alterarVencimentoConta` | novo vencimento, original preservado | não |
| `BILL_REVIEW` (abrir/encerrar) | `colocarRevisaoConta` | suspende cobrança e acréscimos (FAT-35) | não |
| `BILL_ISSUE_OUT_OF_CYCLE` | `inserirConta` | conta ADDED | não |
| `READING_CORRECTION` | retificação por leitura | nova leitura e consumo; média recalculada | opcional (releitura) |
| `CONSUMPTION_REVIEW` (vazamento) | revisão de consumo dos editais | desconto conforme `leak.discount_rule` | sim (vistoria, ED-23) |
| `SPECIAL_SITUATION_SET` / `_REMOVE` | `FaturamentoSituacaoHistorico` | situação vigente na unidade | não |
| `CONNECTION_MINIMUM_CONSUMPTION_CHANGE` | consumo mínimo fixado | nova vigência na ligação | não |
| `SEWER_PARAMETERS_CHANGE` | `LigacaoEsgoto` | percentuais e volume mínimo com vigência | opcional |
| `UNIT_PROFILE_CHANGE` | `ImovelPerfil` | perfil com vigência | não |
| `ECONOMIES_CATEGORY_CHANGE` | cadastro de economias | economias por categoria com vigência | opcional (vistoria) |
| `CENTRAL_BILLING_LINK` | cliente responsável / fatura | unidade ligada ao órgão centralizador | não |
| `CUSTOMER_TAX_PROFILE_CHANGE` | cliente com retenção | perfil de retenção com vigência | não |
| `CREDIT_ISSUE` / `CREDIT_CANCELLATION` / `CREDIT_ANTICIPATION` | `CreditoARealizar` manual | crédito com origem e tipo | não |
| `DEBIT_ISSUE` / `DEBIT_CANCELLATION` / `DEBIT_REVIEW` | `DebitoACobrar` manual | débito com tipo e OS de origem | não |
| `SOCIAL_TARIFF_IRREGULARITY_NOTICE` | perda por anormalidade | inicia contagem do aviso de 3 meses | não |
| `INFRACTION_NOTICE` | `AutosInfracao` | prazo de defesa; débito após decisão | sim (fiscalização) |
| `ALTERNATIVE_DUE_DAY` | vencimento alternativo | `consumer_units.due_day` com vigência | não |
| `DELIVERY_ADDRESS_CHANGE` | endereço de entrega | endereço de entrega e taxa, se houver | não |
| `ACCESSIBLE_BILL_REQUEST` | conta em Braille | formato acessível nas contas seguintes | não |
| `FINAL_BILL` | conta final (editais) | leitura antecipada e conta final | sim |

---

## 3. Ciclos de vida inferidos

### 3.1 Conta

```
                    faturar grupo / emitir em campo               pagamento conciliado
 (sem conta) ──────────────────────────────────▶ NORMAL ─────────────────────────────▶ PAGA
                                                 │
   OS BILL_REVIEW (abrir/encerrar)                ├── revisão aberta (estado ortogonal): retida na impressão,
                                                 │   sem acréscimo, cobrança, negativação, corte, débito automático
   OS BILL_RECTIFICATION                          ├── CANCELADA_POR_RETIFICAÇÃO + nova conta RETIFICADA
                                                 │     (pagamento, item de fatura e débito automático migram)
   OS BILL_CANCELLATION                           ├── CANCELADA (motivo) │ DÉBITO PRESCRITO (motivo prescrição)
   parcelamento (P4.1)                           ├── PARCELADA │ ENTRADA DE PARCELAMENTO
   arrasto de saldo                              ├── ARRASTADA
   OS BILL_DUE_DATE_CHANGE                        └── NORMAL (vencimento novo; original e contagem preservados)
 PRÉ-FATURADA (campo) ── sincronização e recálculo (ADR-0031) ──▶ NORMAL │ fila de divergências
 ERRO DE PROCESSAMENTO ── reprocessar ──▶ NORMAL
```

No GSAN, conta incluída ou retificada ainda não contabilizada é apagada ao cancelar [FATO]; no Ziip, nenhuma conta é
apagada (P2.20). Retificar exige: não prescrita, sem emissão em campo pendente, vencimento novo ≥ hoje; leitura só se o
pacote da referência seguinte não saiu [FATO]. Retificação e cancelamento disparam, no Ziip, cancelamento ou
substituição da NFAg-e conforme o MOC (`FISCAL-PORTE-JAVA.md` §4, a confirmar).

### 3.2 Débito a cobrar e crédito a realizar

```
 criado (OS de origem) ─▶ NORMAL ─(prestação cobrada em conta)─▶ NORMAL (n cobradas) ─(última)─▶ quitado/histórico
        │                     ├─ revisão (débito) ─▶ fora da conta até encerrar
        │                     ├─ parcela bônus (parcelamento) ─▶ prestações devidas diminuem
        │                     └─ OS de cancelamento ─▶ CANCELADO (GSAN apaga se nada foi cobrado; Ziip nunca apaga)
 crédito: resíduo não comportado pela conta ─▶ mês seguinte; conta cancelada ─▶ crédito devolvido ao saldo (Ziip)
```

### 3.3 Situação especial

```
 OS SPECIAL_SITUATION_SET (tipo, motivo, [início, fim], consumo/volume fixos) ─▶ VIGENTE na competência
   ─▶ fim atingido │ OS SPECIAL_SITUATION_REMOVE (competência de retirada, observação) ─▶ ENCERRADA
   ─▶ prazo máximo (`special_situation.max_months`) ─▶ reavaliação obrigatória por OS (não reinício silencioso)
```

### 3.4 Ciclo mensal e dependências [INFERÊNCIA: ordens e precedentes não estão no repositório; derivados dos ids e chamadas]

```
 pré-faturar (0, só emissão em campo)
   └▶ gerar pacote de leitura (1) ─▶ efetuar leitura (2) ─▶ registrar leituras (3) ─▶ gerar fiscalização (4, opcional)
                                                             └▶ consistir e calcular consumos (9) ─▶ rateio
 débitos periódicos (acréscimos, taxas, créditos de situação especial) ─▶ simular (8, repetível)
   ─▶ faturar grupo (5, por rota, idempotente) ─▶ distribuir contas (6) ─▶ transmitir arquivo (7)
 todos os grupos faturados ─▶ encerrar o mês ─▶ resumo contábil e lançamentos à ERI ─▶ avança referência
```

Efeitos em arrecadação e cobrança: a referência de arrecadação é independente da de faturamento [FATO,
`anoMesArrecadacao`]; acréscimos e cobrança só alcançam contas vivas fora de revisão; pagamento de conta retificada
passa à conta nova [FATO]; diferença entre pago e retificado vira crédito ou débito (seção 4.5).

---

## 4. Pseudocódigo das regras novas (em palavras próprias)

### 4.1 Bônus social do GSAN (documentado para rejeição)

```
para cada unidade da rota:
   apagar crédito social da referência anterior ainda não realizado
   exigir perfil social, subcategoria residencial 1, água ligada
   escolher a conta de referência (ref-1; ou ref-2 se o vencimento ainda não passou ou passou há até 5 dias)
   se a conta não foi cancelada:
       exigir pagamento até o vencimento + carência de acréscimos (dias úteis do município)
       exigir consumo de água ≤ 10 m³ e nenhuma conta anterior vencida e não paga
   gerar crédito de 1 prestação = tarifa mínima residencial × percentual do bônus, repartido por categoria
Ziip: REJEITADO (Lei 14.898: concessão não depende de adimplência; piso não reduzido por teto de consumo)
```

### 4.2 Parcela bônus de parcelamento

```
para cada parcelamento especial em situação normal e ainda dentro de (mês do parcelamento + n + 2):
   conta = conta da unidade na referência de arrecadação − 1
   se não paga, ou paga depois de vencimento + tolerância: contador ← 0
   senão se contador = (meta − 1): contador ← 0 ; débito.bônus += 1 ; crédito.bônus += 1
   senão: contador += 1
prestação regular = TRUNC2(total / n) ; prestações devidas = n − bônus
última devida = total − prestação × (devidas − 1) − valorAbonado        # Ziip: abono explícito como crédito,
                                                                         # nunca valor que some sem lançamento
```

### 4.3 Crédito compensatório por não conformidade da água (nitrato)

```
se a unidade está em situação especial de não conformidade na referência:
   no pré-faturamento: criar crédito com valor 0 e estado pré-faturado
   no faturamento: valor = valor da água da conta × percentual de compensação (GSAN: 50%)
   repartir por categoria ; 1 prestação ; aplicar na própria conta (CreditApplier)
```

### 4.4 Cancelamento de conta (regra proposta para o Ziip)

```
pré: OS BILL_CANCELLATION aprovada ; conta viva ; não prescrita (salvo motivo prescrição)
conta.status ← CANCELLED (ou PRESCRIBED_DEBT) ; nova linha de trilha
para cada crédito realizado na conta: crédito.residual += valor realizado          # trava (classe a)
para cada prestação de débito cobrada:
   se o motivo abrange o débito (ex.: serviço não prestado): cancelar o débito por OS DEBIT_CANCELLATION
   senão: débito.installments_done −= 1                                            # volta a cobrar
emitir eventos: negativação (baixa), NFAg-e (cancelamento conforme MOC), cobrança (sai da carteira)
```

### 4.5 Retificação de conta já paga

```
nova = recalcular com as versões de parâmetro da competência (ADR-0002, princípio 7)
diferença = valor pago − nova.total
se diferença > 0: tratar conforme `bill.overpayment_refund_mode` (padrão: escolha do usuário)
   e, se a cobrança original foi indevida sem engano justificável, aplicar `bill.undue_charge_double_refund`
se diferença < 0: débito a cobrar do saldo, sem multa nem juros sobre a diferença (classe a)
```

### 4.6 Esgoto completo (o que o documento 02 não fecha)

```
se a situação especial vigente paralisa esgoto: volume = 0 ; valor = 0
senão se fixa volume de esgoto: volume = volume fixo (medido ou não medido)
senão: volume = SewageVolumeCalculator (água, poço, mínimo de esgoto, coleta)
       se não há água nem poço: volume = regra `sewer.no_water_volume_rule`
pct = pct de esgoto da ligação ; se pct alternativo e volume/economia ≤ limite: pct = MIN(pct, alternativo)
valor esgoto = tarifa(volume) × pct/100 (fator conforme `SewerFactorPolicy`) ; desconto social conforme `social.sewer_discount`
```

### 4.7 Revisão automática na geração da conta

```
se anormalidade de consumo com retenção (estouro, baixo consumo) ou total ∈ faixa de valor configurada:
   abrir revisão automática (OS BILL_REVIEW gerada pelo sistema)
   aplicar `bill.hold_on_anomaly`: reter e analisar até `bill.review_max_days`; vencido o prazo sem análise,
   emitir pela regra mais favorável ao usuário (média) com aviso, e a diferença só por retificação posterior
```

---

## 5. Parâmetros de faturamento e medição

### 5.1 Bifurcações que viram parâmetro do tenant (diretriz 1)

Padrão de fábrica = caminho mais protetivo ao usuário. "Em PT?" = já existe em `PARAMETROS-DO-TENANT.md`.

| Chave | Opções | Padrão de fábrica | Cl. | Base | Em PT? |
|---|---|---|---|---|---|
| `bill.review_effects` | `SUSPEND_ALL` (cobrança, acréscimos, negativação, corte, débito automático); `SUSPEND_DUNNING_ONLY` | `SUSPEND_ALL` | b | NR 11 arts. 37–38 | não |
| `bill.hold_on_anomaly` | `HOLD_AND_REVIEW`; `ISSUE_AVERAGE_THEN_ADJUST`; `ISSUE_MEASURED_WITH_NOTICE` | `HOLD_AND_REVIEW` com teto `bill.review_max_days` | b | prática; ERI | não |
| `bill.review_max_days` | dias | prazo da ERI | c (teto da ERI) | ERI | não |
| `bill.auto_review_value_bands` | faixas de valor por categoria | sem faixas | d | prática | não |
| `bill.overpayment_refund_mode` | `CUSTOMER_CHOICE`; `CREDIT_NEXT_BILL`; `CASH_REFUND` | `CUSTOMER_CHOICE` | b | CDC art. 42; NR 11 | não |
| `bill.undue_charge_double_refund` | ligado; desligado com norma citada | ligado | b | CDC art. 42 p.u. (aplicação a tarifa: G5/E-02) | não |
| `bill.rectification_limits_against_user` | quantidade por ano, meses de antiguidade | sem limite próprio; prescrição | d | prática | não |
| `bill.due_change_max_per_year` | inteiro ou sem limite | sem limite | d | Lei 8.987 art. 7º-A (direito de escolha) | não |
| `bill.due_change_min_days_before_due` | dias | 0 | d | prática | não |
| `bill.min_issue_amount`, `bill.below_min_rule` | valor; `CARRY_FORWARD` (máx. meses) ou `ISSUE_ANYWAY` | `CARRY_FORWARD`, 3 meses, sem encargo sobre o arrastado | d | editais TR-298/299 | não |
| `bill.validity_months` | meses | sem padrão | d | prática | não |
| `bill.mail_extra_days` | dias | sem padrão | d | prática | não |
| `bill.second_copy_fee` | valor, por canal | 0; canal digital sempre 0 | b | prática; `08` R-11 | não |
| `bill.delivery_address_fee` | valor | 0 | b | editais TR-302 | não |
| `bill.advance_billing` | desligado; por pedido do órgão (OS) | desligado | d | orçamento público | não |
| `bill.cut_connection_rule` | `NO_CHARGE`; `BASIC_FEE` (se a ERI previr) | `NO_CHARGE` | b | NR 13 art. 10 | não |
| `bill.inactive_economy_rule` | `NO_CHARGE`; `BASIC_FEE` (se a ERI previr) | `NO_CHARGE` | b | NR 13 art. 10 | não |
| `tariff.fixed_part_model` | `BASIC_FEE`; `MINIMUM_CONSUMPTION` | `BASIC_FEE`; `MINIMUM_CONSUMPTION` só com `reg.contract_pre_nr13` | c | NR 13 arts. 8º–9º | parcial (`reg.contract_pre_nr13`) |
| `sewer.no_water_volume_rule` | `CONNECTION_MINIMUM`; `NON_METERED_ESTIMATE`; `FIXED_VOLUME`; `LOWER_OF` | `LOWER_OF` | b | ERI | não |
| `sewer.alternative_pct_enabled` | ligado/desligado | ligado onde a ERI previr | d | ERI | não |
| `condominium.apportionment_mode` | `COMMON_AREA_TO_CONDOMINIUM`; `EQUAL_PER_ECONOMY`; `UNMETERED_ONLY`; `NONE` | `COMMON_AREA_TO_CONDOMINIUM` | b | Lei 13.312; NR 11 arts. 28–30 | não |
| `condominium.max_increment_m3`, `condominium.tolerance_pct` | inteiros | sem padrão | d | prática | não |
| `special_situation.max_months` | meses | 12, com reavaliação por OS | b | prática | não |
| `quality.noncompliance_compensation` | `CREDIT_PCT_OF_WATER` (%); `NONE` (só se a ERI não previr) | `CREDIT_PCT_OF_WATER`, % a informar | b | ERI; CDC art. 20 | não |
| `leak.discount_rule` | `AVERAGE_6M`; `PCT_OF_EXCESS`; `MOST_FAVORABLE` | `MOST_FAVORABLE` | b | editais TR-303–305; ERI | não |
| `leak.max_discount_pct_water`, `leak.max_discount_pct_sewer` | % | 100 | b | editais | não |
| `infraction.charge_timing` | `AFTER_DECISION`; `ON_ISSUE_SUSPENDED_BY_APPEAL` | `AFTER_DECISION` | b | devido processo | não |
| `infraction.appeal_days`, `infraction.max_retroactive_months` | inteiros | ERI | c (prazo mínimo e teto da ERI) | ERI | não |
| `installment.bonus_enabled`, `installment.bonus_streak` | ligado/desligado; nº de pagamentos em dia | desligado; meta 3 | d | política comercial | não |
| `credit.type_limits` | valor máximo por tipo | sem limite; acima exige aprovação dupla | d | prática | não |
| `large_user.min_m3`, `large_user.min_economies` | inteiros | sem padrão | d | NR 11 | não |

### 5.2 Travas legais (diretriz 2)

| Regra | Classe | Fonte | Recusa |
|---|---|---|---|
| Benefício social sem condição de adimplência | a | Lei 14.898 | qualquer regra que leia pagamento para conceder ou manter |
| Piso social (50% até 15 m³; a conferir no DOU) | c | Lei 14.898/NR 13 | teto de consumo, área ou energia que exclua beneficiário |
| Perda do benefício só após aviso ≥ 3 meses na fatura | c | Lei 14.898 | perda imediata por anormalidade (FAT-07) |
| Vedações de corte por inadimplência; conta com revisão aberta não motiva corte | a | Lei 14.015; NR 11 arts. 37–38 | OS de corte baseada em conta em revisão (FAT-35) |
| Multa ≤ 2% sem aceite reforçado | b/c | CDC art. 52 §1º; ADR-0015 | multa diária do `LateFeePolicy` sem trava |
| Mensagens legais sempre impressas | a | Lei 14.898; NR 13; Lei 11.445 | mensagem local que as desloque |
| Créditos devolvidos ao cancelar conta | a | CDC art. 42 | crédito realizado perdido com a conta cancelada |
| Diferença de retificação sem encargo | a | CDC | multa ou juros sobre diferença apurada depois |
| Nenhuma conta apagada | a | NR 11 art. 22 (120 meses) | remoção física (GSAN, FAT-40) |
| Contestação do usuário aceita até a prescrição | a | NR 11 arts. 37–38; CDC art. 42 | prazo que recuse revisão pedida pelo usuário (FAT-34) |

### 5.3 Parâmetros do GSAN sem bifurcação (estado do ciclo ou parâmetro simples)

| Parâmetro GSAN | Destino no Ziip | Cl. | Em PT? |
|---|---|---|---|
| `anoMesFaturamento`, `anoMesArrecadacao` | estado do ciclo (`billing_cycles`), não parâmetro | — | — |
| `mesesMediaConsumo`, `numeroMesesMaximoCalculoMedia` | `meter.average_months`, `meter.max_estimated_cycles` | d | sim |
| `numeroMinimoDiasEmissaoVencimento` | `bill.min_days_delivery_to_due` | d | sim |
| `diasVencimentoAlternativo`, `ultimoDiaVencimentoAlternativo` | `bill.due_date_options` | c | sim |
| `numeroDiasCalculoAcrescimos` | `fee.grace_business_days` | d | sim |
| `indicadorAtualizacaoTarifaria`, `numeroMesesCalculoCorrecao` | `fee.correction_*` | d | sim |
| `indicadorFaixaFalsa`, `percentualFaixaFalsa`, `percentualFiscalizacaoLeitura` | `reading.false_range_pct`, `reading.audit_sample_pct` | d | não |
| `numeroVezesSuspendeLeitura`, `numeroMesesLeituraSuspensa` | colunas de `reading_anomalies` (`V5_1`) | d | não |
| `codigoTipoCalculoNaoMedido`, `indicadorNaoMedidoTarifa` | `consumption.non_metered_mode` | d | não |
| `numeroMaximoParcelaCredito` | `credit.max_installments` | d | não |
| `qtdeDiasEnvioEmailConta` | `bill.email_days_before_due` | d | não |
| `percentualBonusSocial`, `areaMaximaTarifaSocial`, `consumoEnergiaMaximoTarifaSocial` | não absorver (5.2) | — | — |

---

## 6. Melhores práticas (manter como conceito, não replicar)

1. Três camadas de dado (lido, faturado, cobrado), cada correção regravando a camada certa com nova versão.
2. Conta imutável; correção por nova conta ligada à anterior, com migração explícita de pagamento, fatura agrupadora e
   débito automático.
3. Motivo de retificação que declara quais campos pode alterar e se mexe em consumo; limite de uso por motivo.
4. Revisão como estado ortogonal com efeitos declarados em emissão, cobrança, arrecadação e negativação.
5. Débitos e créditos com origem tipada, mês contábil próprio e repartição por categoria, para que o encerramento
   feche sem conciliação manual.
6. Catálogo de atividades com ordem, obrigatoriedade, repetição e comando por rota, reexecutável e idempotente.
7. Simulação do faturamento com o mesmo motor antes do lote real.
8. Qualidade da água por área e referência com amostras exigidas, analisadas e conformes, ligada à compensação.
9. Lote sempre como OS-mãe com OS-filhas por unidade, para trilha individual.

Não replicar: forks por empresa (`*SEJB`), remoção física de conta e de crédito, permissão de cancelar sem protocolo,
bônus condicionado a pagamento, origem de crédito errada para atender um caso (o bônus social grava origem "devolução
de juros de parcelamento"), constante de consumo no código (10 m³), atualização de ligação dentro do lote de faturamento.

---

## 7. Lacunas do Ziip SAN por impacto

| # | Lacuna | Impacto | Tarefa afetada | Proposta |
|---|---|---|---|---|
| L1 | P2.20 (P2) exige OS tipada (diretriz 3), mas o núcleo de OS está na P3.4 e a OS completa na ED-36 | P2.20 não cumpre a diretriz ou atrasa | P2.20, P3.4, ED-36 | antecipar para o P2 um núcleo de OS interna (tipo, motivo, unidade, aprovação, efeito) sobre `service_orders`; seção 8, Q1 |
| L2 | Revisão de conta sem efeito em cobrança, acréscimos, negativação, corte e débito automático | cobrança e corte de conta contestada (viola NR 11 e Lei 14.015 por via indireta) | P2.5, P3.1, P3.4, P4.1 | `bill_reviews` + filtro obrigatório nos três módulos |
| L3 | Cancelamento e retificação sem regra para prestações cobradas, créditos realizados e pagamento já feito | saldo do cliente errado; crédito perdido | P2.20 | seção 4.4 e 4.5; parâmetros `bill.overpayment_refund_mode` e `bill.undue_charge_double_refund` |
| L4 | Cronograma, catálogo de atividades, encerramento do mês e mês contábil de débitos e créditos ausentes | ciclo sem trava de fechamento; contabilidade e ERI sem resumo | P2.5, P2.6 | `billing_activity_catalog`, `billing_cycle_activities`, `accounting_month` |
| L5 | Situação especial, isenção, fatura agrupadora, guia avulsa e auto de infração ausentes | POC-51 e SAMAE 2.13 falham | ED-22, ED-24, ED-26 | FAT-60 a 65, 83, 25 |
| L6 | `debit_receivables`/`credit_receivables` sem tipo, origem, categoria, OS de origem e bônus | fechamento contábil manual; débito de serviço sem rastreio | P2.5 | FAT-10 a 13, 20, 24 |
| L7 | Parâmetros da ligação de esgoto e consumo mínimo fixado só como entrada do motor | valor de esgoto não reproduzível depois | ED-13, P2.5 | colunas com vigência em `service_connections` |
| L8 | Duas políticas de encargos (billing sem trava de 2%) | multa acima do limite em conta | P2.5 | unificar (ADR-0015, já registrado) |
| L9 | Mensagens legais sem prioridade garantida e qualidade da água sem fonte antes do P4 | descumprimento de R-12 e do Decreto 5.440 | P2.8 | FAT-87, FAT-88; Q4 |
| L10 | Perfil de unidade e grande usuário ausentes | regra de leitura, revisão e contrato específico sem base | P1.3, ED-12, ED-17 | `unit_profiles`, `large_user.*` |

---

## 8. Perguntas ao dono do produto (só o que não é bifurcação)

**Q1. Como cumprir a diretriz da OS na P2.20, que vem antes da P3.4?**
(a) antecipar para o P2 um núcleo de OS interna sem campo (tipos, motivo, aprovação, efeito), e a P3.4 e a ED-36
acrescentam campo e agenda; (b) a P2.20 grava registro de solicitação próprio e migra para OS na P3.4;
(c) a P2.20 passa a depender da P3.4 e sai do P2.

**Q2. O que é "unidade consumidora" quando não há imóvel (usuário avulso, evento, limpeza de fossa)?**
(a) unidade consumidora sem ligação, de tipo "avulsa", para manter o pilar único; (b) cobrança por cliente, fora de
`consumer_units`, como exceção registrada; (c) unidade avulsa só para quem tem recorrência, cliente para o resto.

**Q3. Parcela bônus em parcelamento entra no escopo do produto?**
(a) não; (b) sim, só no parcelamento da P4.1, como política opcional do tenant; (c) sim, já no modelo da P2.5,
também para débitos de serviço.

**Q4. Qual a fonte dos dados de qualidade da água na conta antes do módulo de qualidade (P4)?**
(a) importação mensal por arquivo por sistema de abastecimento já na P2.8; (b) digitação mensal no backoffice;
(c) só a partir do P4, com aviso de "dados em implantação" no modelo de conta.

**Q5. Fatura agrupadora do órgão centralizador e documento fiscal:**
(a) NFAg-e por unidade e fatura agrupadora só como documento de cobrança; (b) NFAg-e única por fatura agrupadora, se
o MOC permitir; (c) decidir depois do MOC, mantendo (a) até lá.

**Q6. Emissão em campo e conta pré-faturada que não volta da rota:**
(a) cancelar e refaturar no servidor com a leitura disponível; (b) manter a pré-faturada e emitir 2ª via no servidor;
(c) tratar como divergência na fila do ADR-0031, com decisão humana.

---

## 9. Limites

- Leitura clean-room, sem execução do GSAN. Métodos longos (`cancelarConta`, `retificarConta`, mensagens da conta)
  foram lidos por trechos; afirmações sobre fluxo completo estão marcadas [INFERÊNCIA].
- As três leituras paralelas planejadas não produziram resultado; a leitura foi refeita de forma dirigida. Não foram
  lidos por inteiro: `encerrarFaturamentoMes`, `faturarImovelAntecipado`, o tratamento de contas pré-faturadas não
  devolvidas e o uso efetivo de vários parâmetros listados em 2.5 e 5.3 (a existência é [FATO], o uso não foi
  conferido).
- Dados de semente (ordem e precedência de atividades, motivos, tipos de situação, perfis) não estão no repositório;
  a seção 3.4 é inferida.
- Bases legais citadas vêm dos relatórios `08`, `11`, `13` e `14` do projeto e não são parecer jurídico; o piso
  social depende de conferência no DOU (ADR-0015).
- Licença do GSAN não confirmada; o documento registra regras de domínio, não artefatos.
