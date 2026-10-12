# 03 — GSAN: arrecadação, cobrança, parcelamento, financeiro e batch

Levantamento de domínio (somente leitura) para enriquecer o SIGSAN.
Fonte: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN` (último commit visto: 30/08/2018, branch de recadastramento).
Data do levantamento: 11/10/2026.

Convenções deste documento:

- **(fato)** = li o arquivo/classe citado. **[INF]** = inferência minha, não confirmada no código.
- **[EXT]** = conhecimento externo ao repositório (legislação, padrões FEBRABAN/BCB). Não vem do GSAN e precisa de validação jurídica/bancária antes de virar requisito.
- Caminhos relativos a `GSAN/`. O código Java fica em `src/gcom/<pacote>/` (a pasta `gcom/` na raiz é só o webapp JSP/Struts). Os mapeamentos Hibernate são `*.hbm.xml` ao lado de cada entidade.
- Escopo lido: `src/gcom/arrecadacao`, `cobranca`, `financeiro`, `spcserasa`, `batch`, `faturamento/autoinfracao`, `descriptors/`, `reports/`. Os controladores são gigantes (`ControladorArrecadacao` ~50 mil linhas, `ControladorCobranca` ~62 mil, `ControladorBatchSEJB` ~6 mil, `ControladorSpcSerasaSEJB` ~14 mil); li as regras centrais por amostragem dirigida (comentários de caso de uso `[UCxxxx]`, métodos de cálculo e classificação), **não** linha a linha. Onde isso limita a certeza, está marcado.
- Encoding misto: parte dos `.java` é latin1 (acentos viram `?`/`�` se lidos como UTF-8). Relevante se alguém for reler as fontes.

---

## 0. Resumo executivo

1. **O GSAN é um motor de arrecadação/cobrança maduro, com regras de negócio ricas, mas enterradas em classes de 50–60 mil linhas.** O valor para o SIGSAN está nos **catálogos de estados, nas fórmulas e nas regras de tolerância**, não na arquitetura (EJB 2 + Struts 1 + JMS/JBoss 4, Hibernate, procedimentos serializados em `bytea`).
2. **Pagamento tem máquina de estados com 14 valores** (`PagamentoSituacao`, §2.5) e duas tolerâncias codificadas: **R$ 0,03** para classificar conta como paga e **R$ 2,00** para reclassificar "valor não confere" e gerar débito/crédito de ajuste. Essas duas constantes são regra de negócio que o SIGSAN deve **parametrizar por tenant**.
3. **Layouts bancários suportados (fato por nome de classes/campos):** FEBRABAN 150 posições de arrecadação (registros A/G/Z), débito automático FEBRABAN 150 (A/B/C/E/F), retorno de cobrança CNAB 240 (registros 0/1/3T/3U/5/9), retorno de cobrança de carteira registrada tipo "7" (CNAB 400, estilo Banco do Brasil **[INF]**), e arquivo de cartão (header/tipo1/tipo2/trailer). **Não há PIX, nem CNAB 444, nem integração NPC/DDA.**
4. **As variantes por empresa (CAEMA, CAERN, CAER, COSAMA, COSANPA, JUAZEIRO; COMPESA é stub vazio) existem quase só para ler os códigos de barras e tipos de pagamento do sistema legado de cada companhia** durante a migração para o GSAN (tipo de pagamento `0`, `06`, `07`, `08`, `99` etc.). O mecanismo é um *lookup* JNDI trocando `GCOM` pelo nome da empresa (`ServiceLocator.getLocalHomePorEmpresa`). No SIGSAN isso vira **estratégia por tenant**, não fork de classe.
5. **Acréscimos por impontualidade** (multa, juros de mora, correção monetária) são calculados por **índices mensais parametrizáveis** com limites (§3.5). Há **um bug confirmado** (retorno de `subtrairNumeroDiasDeUmaData` descartado) e o cálculo mistura dias úteis (carência) com dias corridos (multa/juros diários).
6. **Parcelamento é o módulo mais denso:** perfil × resolução de diretoria (RD) × faixas de desconto (antiguidade, inatividade, faixa de referência, acréscimos, sanções, à vista, tarifa social) × entrada mínima × quantidade de prestações × taxa de juros (composto simples ou Tabela Price por parâmetro) × parcelas bônus. Rotinas batch desfazem parcelamento por entrada não paga (§3.6).
7. **Negativação (SPC/Serasa)** é um ciclo completo com contrato, comando, critério, movimento (arquivo de remessa), retorno, confirmação (data de envio + prazo do contrato + 1 dia), exclusão automática por pagamento/renegociação e resumo diário (§3.7). É a área de **maior risco LGPD/CDC** a absorver.
8. **O motor batch** (`batch.processo` → `processo_iniciado` → `funcionalidade_iniciada` → `unidade_iniciada`) tem ideias boas e diretamente aproveitáveis: **ordem por sequencial, bloqueio por predecessor, unidade de processamento (localidade/rota/setor) com retomada que pula unidades concluídas, reinício total e continuação** (§5). Tem também defeitos que não devem ser copiados (tarefa Java serializada em `bytea`, uma funcionalidade por vez no cluster, `marcarProcessosInterrompidos` cancela tudo no boot).
9. **Contabilidade:** o GSAN não tem razão contábil; tem **lançamentos por evento** (faturamento, arrecadação, devedores duvidosos, aviso bancário) a partir de **resumos** e exporta um **TXT** para o sistema contábil da companhia (há variante CAERN). A "PDD" é por **meta de baixa** (valor a baixar + 10 % de tolerância), não por perda esperada (§4).
10. **Licença:** não há arquivo `LICENSE` na raiz nem cabeçalho de licença nos `.java` que abri. O README diz "software livre … Ministério das Cidades … Portal do Software Público Brasileiro". O SPB costuma usar GPL **[EXT]**, o que é copyleft. **Tratar tudo como não copiável**; usar só conceitos, catálogos de estados e fórmulas reescritas (§9).

---

## 1. Mapa do território

| Pasta | O que há | Tamanho/observação |
|---|---|---|
| `src/gcom/arrecadacao/` | Entidades de arrecadador, contrato, movimento, pagamento, aviso bancário, devolução, débito automático, cartão, resumos; `ControladorArrecadacao` + 7 subclasses por empresa | schema `arrecadacao.*` |
| `src/gcom/arrecadacao/bean/` | ~45 *helpers* de layout de arquivo (`RegistroHelperCodigoA/G/Z/B/C/E/F/W/X/0/1/3T/3U/5/9`, `RegistroFichaCompensacao*`, `RegistroCartaoCredito*`) | são os layouts de arquivo |
| `src/gcom/cobranca/` | Ação, atividade, critério, cronograma, comando, documento, situação especial, parcelamento, contrato de parcelamento por cliente, cobrança por resultado, negativador (entidades), prescrição | schema `cobranca.*` |
| `src/gcom/spcserasa/` | `ControladorSpcSerasaSEJB` (ciclo de negativação) + repositório | usa entidades de `cobranca/` |
| `src/gcom/financeiro/` | Contas contábeis, lançamentos, resumos, devedores duvidosos, contas a receber contábil | schema `financeiro.*` |
| `src/gcom/faturamento/autoinfracao/` | `AutosInfracao`, `AutoInfracaoSituacao` | schema `faturamento.*` |
| `src/gcom/batch/` | Motor batch: 8 entidades de controle + `Tarefa*`/`*MDB` por área (arrecadação, cobrança, faturamento, financeiro, gerencial, micromedição, cadastro, atendimento público) | schema `batch.*` |
| `descriptors/` | 239 pastas de descritores EJB (`ejb-jar.xml` + `jboss.xml`): ~160 são `batch*` (uma fila JMS/MDB por tarefa) e o resto são o mesmo controlador instanciado por empresa (`arrecadacaoCAEMA`, `cobrancaCAERN` …) | **não é** configuração de negócio; é *deployment* JBoss |
| `reports/` | 500 `.jrxml` (JasperReports), inclusive subrelatórios | §6 |
| `lib/quartz/` | `quartz-all-1.5.2.jar` | agendador |
| `lib/codigo-barras/` | `codigo-barra.jar` (terceiro, licença desconhecida) | não reutilizar |
| `scripts/`, `build.xml`, `documents/recadastramento` | build Ant, material de recadastramento | fora da fatia |

**Não está no repositório:** o DDL/seed do banco (README manda baixar `gsan-migracoes`, outro repositório, com MyBatis Migration). Logo **não vi** a carga de `processo_funcionalidade` (a ordem real do fechamento), nem os valores de `PAGAMENTO_SITUACAO` etc. como dados; só as constantes Java que os referenciam.

---

## 2. Arrecadação

### 2.1 Entidades principais (fato, `*.hbm.xml`)

| Entidade (tabela) | Campos que importam | Observação |
|---|---|---|
| `Arrecadador` (`arrecadacao.arrecadador`) | `arrc_cdagente` (código do agente = código do banco), `arrc_nninscricaoestadual`, FK cliente e imóvel | o arrecadador **é** um cliente do cadastro |
| `ArrecadadorContrato` (`arrecadador_contrato`) | nº contrato, `cdconvenio`, datas início/fim/encerramento, `iccobrancaiss`, **NSA por tipo de arquivo** (`nnnsaretornocdbarras`, `nnnsaretornodebaut`, `nnnsaenviodebaut`, `nnnsaretornofichacomp`), `nntamidentificarimv` (8 ou 9 dígitos), contas de depósito (tarifa e arrecadação), motivo de cancelamento | NSA = número sequencial de arquivo; ver validação em 2.3 |
| `ArrecadadorContratoTarifa` (`arrecadador_contrato_tar`, PK composta contrato+forma) | `vltarifa`, `pctarifa`, **`nndiafloat`**, conta de depósito | tarifa bancária por forma de arrecadação **e dias de float** |
| `ArrecadacaoForma` (`arrecadacao_forma`) | `cdarrecadacaoforma`, descrição | ids usados em código: 1 guichê/caixa, 13 débito automático, 14 depósito, 16 ficha de compensação, 17 cartão de crédito, 19 cartão de débito |
| `ArrecadadorMovimento` (`arrecadador_movimento`) | `dtgeracao`, `nnnsa`, `cdconvenio`, `nmempresa`, `cdbanco`, `nnversaolayout`, `cdremessa`, `nnregistrosmovimento`, `vltotalmovimento` | cabeçalho do arquivo recebido |
| `ArrecadadorMovimentoItem` (`arrecadador_mov_item`) | **`cnregistro` (linha crua)**, `dsocorrencia`, `icaceitacao` (1 aceito, 2 não aceito), `vldocumento`, FKs opcionais para imóvel, conta, guia, fatura, documento de cobrança, tipo de registro | **guarda a linha original** (boa prática de auditoria) |
| `RegistroCodigo` (`registro_codigo`) | `cdregistrocodigo` (A, B, C, E, F, G, X, W, K, 6, 7, 15…) | catálogo de tipos de registro |
| `Pagamento` (`arrecadacao.pagamento`) | `vlpagamento`, **`vlexcedente`**, `amreferenciapagamento`, `dtpagamento`, `amreferenciaarrecadacao`, `icexpurgado`, `tmprocessamento`, `indicadorClassificadoRecuperacaoCredito`; FKs: cliente, imóvel, localidade, forma, tipo de documento (+agregador), **situação atual e anterior**, item de movimento, conta, guia, fatura, documento de cobrança, aviso bancário, débito a cobrar, tipo de débito, pagamento cartão | **FK por tipo de documento** (conta/guia/fatura/doc. cobrança/débito a cobrar): polimorfismo manual |
| `PagamentoHistorico` | cópia para o histórico no encerramento | |
| `AvisoBancario` (`aviso_bancario`) | `vlarrecadacaocalculado`, `vlarrecadacaoinformado`, `vldevolucaocalculado`, `vldevolucaoinformado`, `vlrealizado`, `vlcontabilizado`, `dtprevista`, `dtrealizada`, `dtlancamento`, `nnsequencial`, `iccreditodebito`, ref. de arrecadação, conta bancária, arrecadador, forma | extrato de crédito do banco, base da conciliação |
| `AvisoAcerto` / `AvisoDeducoes` | acertos (crédito/débito, `icarrecadacaodevolucao`); deduções por tipo `DeducaoTipo` (1 CPMF, 2 tarifa, 3 encontro de contas) | |
| `Devolucao` / `GuiaDevolucao` / `DevolucaoSituacao` | valor, datas, situação atual e anterior, FKs: guia de devolução, aviso, tipo de débito, crédito a realizar, item de movimento | devolução de duplicidade/excesso/cobrança indevida |
| `DiferencaAcumulada` | `vldiferencaacumulada` por ref. de arrecadação e aviso | diferença de conciliação carregada mês a mês |
| `DebitoAutomatico` / `DebitoAutomaticoMovimento` / `DebitoAutomaticoRetornoCodigo` | opção do cliente (agência, id do cliente no banco, datas de opção/inclusão/exclusão); movimento por conta (vencimento, valor, envio, retorno, NSA envio/retorno, código de retorno) | |
| `PagamentoCartaoDebito(+Item)`, `MovimentoCartaoRejeita`, `GuiaPagamentoParcelamentoCartao`, `ParcelamentoPagamentoCartaoCredito` | conciliação de operadora de cartão | |
| `BoletoInfo` (`boleto_info`) | `idconv`, `reftran`, CPF/CNPJ, nome, endereço, `tpduplicata`, `tppagamento` (2 boleto, 21 segunda via), valor, vencimento, `urlretorno`, `mensagemloja`, `linkboleto`, indicadores gerado pelo GSAN / registrado no banco / excluído | integração com formulário web do banco (ver §9) |
| `ArrecadacaoDadosDiarios` | dados diários de arrecadação por data × arrecadador × forma × perfil × categoria × esfera × situação de ligação × localidade/setor/quadra, `qtpagamentos`, `vlpagamentos`, `nndiafloat`, `vltarifa` | base dos relatórios de acompanhamento e do **float** |
| `ResumoArrecadacao` | valor por tipo/item de lançamento × localidade × gerência × categoria × tipo de recebimento (`RecebimentoTipo`) | base contábil (§4) |
| `DadosPagamentosNaoClassificados`, `DadosDocumentosNaoIdentificados` | fotografia mensal do que ficou sem classificar | |
| `MetasArrecadacao` | metas de faturamento/pendência/arrecadação por unidade | |

### 2.2 Documentos que um pagamento pode quitar

`DocumentoTipo` (ids em `cobranca/DocumentoTipo.java`): 1 conta; 2 entrada de parcelamento; 3 documento de cobrança; 5 fatura do cliente responsável; 6 débito a cobrar; 7 guia de pagamento; 8 devolução de valor; 10 crédito a realizar; 14 extrato de débito; 34 extrato de contrato de parcelamento; mais os tipos de carta/ordem de corte/fiscalização (11, 12, 13, 16, 17, 18, 21–25, 27–33). Há **colisão de constantes** no próprio arquivo (ex.: `ORDEM_FISCALIZACAO_SUPRIMIDO = 16` e `ORDEM_FISCALIZACAO = 16`; `ORDEM_FISCALIZACAO_CORTADO = 17` e `VISITA_COBRANCA = 17`); não reproduzir esses números.

### 2.3 Layouts de arquivo bancário e variantes por empresa

Registros reconhecidos (fato por nome de classe e campos de `arrecadacao/bean/`; o significado do formato é **[INF]** pelos campos):

| Registro | Classe | Campos (resumo) | Padrão provável |
|---|---|---|---|
| A | `RegistroHelperCodigoA` | código remessa, convênio, nome empresa, banco, data geração, NSA, versão do layout, tipo de movimento ("DEBITO AUTOMATICO" ou "CODIGO DE BARRAS"), id do banco | FEBRABAN 150, header |
| G | `RegistroHelperCodigoG` (+ `RegistroHelperCodigoBarras*`) | id agência/conta, data pagamento, **data prevista de crédito**, **código de barras**, valor recebido, **valor da tarifa**, nº sequencial, agência arrecadadora, **forma de arrecadação**, **autenticação**, forma de pagamento | FEBRABAN 150, detalhe de arrecadação |
| Z | `RegistroHelperCodigoZ` | total de registros e valor total do arquivo | FEBRABAN 150, trailer (usado para validar total) |
| B, C, E, F | `RegistroHelperCodigoB/C/E/F` | B: opção/exclusão de débito automático; C: ocorrência do cadastro (devolução do banco); E: débito (envio); F: retorno (`codigoRetorno`) | FEBRABAN 150 débito automático |
| X | `RegistroHelperCodigoX` | cadastro de agências (código, nome, endereço, CEP, situação) | arquivo de agências do banco |
| W | `RegistroHelperCodigoW` | segmento de cobrança 240 (nosso número, vencimento, valor pago, data de efetivação) | **[INF]** |
| 0, 1, 3T, 3U, 5, 9 | `RegistroHelperCodigo0/1/3T/3U/5/9` | header de arquivo/lote, segmentos T (nosso número, carteira, vencimento, valor, tarifa) e U (juros/multa, desconto, abatimento, IOF, valor pago, valor líquido, datas de ocorrência e crédito), trailers | **CNAB 240 retorno de cobrança** |
| Tipo 7 | `RegistroFichaCompensacaoTipo7Helper` (+ header/trailer) | prefixo de agência, convênio de cobrança, nosso número, carteira, variação, comando, datas de liquidação/crédito, valor recebido, juros de mora, código do banco recebedor, **canal de pagamento** | **CNAB 400 retorno**, estilo Banco do Brasil convênio de 7 posições **[INF]** (campos "variação de carteira", "conta caução") |
| Cartão | `RegistroCartaoCredito{Header,Tipo1,Tipo2,Trailler}Helper` | estabelecimento, resumo de operação, plano, valores bruto/comissão/rejeitado/líquido, banco/agência/conta | extrato de operadora (estilo Redecard EEVC) **[INF]** |

**Validação de arquivo recebido** (`validarArquivoMovimentoArrecadador`, `validarArquivoMovimentoArrecadadorFichaCompensacao`; fato pelos códigos de erro):

- arquivo sem header → rejeita;
- código de remessa inválido; banco do arquivo ≠ arrecadador selecionado; versão de layout > `SistemaParametro.numeroLayoutFebraban`; tipo de movimento não selecionado/ incompatível;
- **NSA fora de sequência** (compara com o NSA guardado no contrato; incrementa ao aceitar); existe pesquisa do maior NSA já processado por (data de geração, banco);
- lote não corresponde ao header (CNAB 240);
- total de registros/valor do trailer Z confere com o somatório;
- id da empresa no código de barras ≠ `SistemaParametro.codigoEmpresaFebraban` → pagamento tratado como não aceito.

**O que diferencia as variantes por empresa** (fato: métodos sobrescritos em `ControladorArrecadacao<EMPRESA>SEJB.java`; a UF é **[INF]** pelas siglas):

| Empresa (UF **[INF]**) | Linhas | O que sobrescreve | Leitura |
|---|---|---|---|
| CAEMA (MA) | 2.471 | distribuição do código de barras; processamento de **conta, guia, cliente responsável, documento de cobrança tipo 5 e campanha** "LEGADO"; `obterIdentificacaoPagamento`; registro de movimento por tipo de pagamento | códigos legados `06` fatura, `07` conta, `08` guia, `99` campanha/aviso de débito, `0` negociação à vista (`ConstantesSistema` linhas 198–212) |
| CAERN (RN) | 736 | tipo de pagamento `0` = conta legado: campos 0–6 matrícula, 6–9 referência, 9–10 e 10–11 dígitos | só conta legado |
| CAER (RR) | 5.499 | o maior: **identificação por tipo de documento** (campo `G05.7.1`; constantes `CODIGO_TIPO_PAGAMENTO_*_CAER`: `01` conta/2ª via, `02` fatura, `04` reaviso de débitos, `06` guia de pagamento cliente, `07` fatura cliente responsável, `08` doc. de cobrança cliente responsável, `09` guia de pagamento imóvel, `10` doc. de cobrança imóvel; o comentário do código diz "reaviso = 3", divergindo da constante), `consultarItensMovimentoArrecadador`, `criarRegistroTipoE`, **`mandaArquivoLeituraEmail`** (retorno por e-mail), `pesquisarGuiaPagamentoRelatorio` | layout próprio de identificação de 24 posições |
| COSAMA (AM) | 963 | conta, guia, **extrato** legados (`processarPagamentosCodigoBarrasExtrato_COSAMA_LEGADO`, `...ExtratoPorConta`) | códigos legados 1, 2, 3, 30 |
| COSANPA (PA) | 1.972 | conta, guia, doc. cobrança, **fatura** legados; `recuperarMatriculaImovel`; **`gerarArquivoRegistrosTipoC`**, `gerarArquivoTxt`, `criarRegistroTipoE` | também **gera** arquivos (devolutiva de débito automático/ocorrências) |
| JUAZEIRO | 583 | conta e guia legados | códigos 0 e 1 |
| COMPESA (PE) | 26 | nada (stub) | sem customização |

Conclusão (**[INF]** forte): as 6 variantes **não** representam regras de arrecadação diferentes; representam **migração de convênios já impressos** (boletos de contas antigas continuam sendo pagos nos bancos meses depois). Para o SIGSAN: uma tabela `barcode_layout` (por tenant e por `tipo_pagamento`/faixa de datas de vigência) que descreve onde estão localidade, matrícula, referência e DV, em vez de código por empresa.

### 2.4 Código de barras de arrecadação (convênio) e ficha de compensação

**Convênio/arrecadação, 44 posições (fato, `distribuirDadosCodigoBarras`, `ControladorArrecadacao` ~7900–8300; posições 0-based):**

| Posição | Campo |
|---|---|
| 0 | produto |
| 1 | segmento |
| 2 | identificação de valor real/efetivo |
| 3 | DV geral |
| 4–14 | valor (11 dígitos). Se a linha tiver mais de 44 posições, o valor efetivamente arrecadado vem em 44–55 |
| 15–18 | identificação da empresa (4 dígitos), comparada com `SistemaParametro.codigoEmpresaFebraban` |
| 19–42 | campo livre (24) |
| 43 | **tipo de pagamento** |

Tipos de pagamento (posição 43; `ConstantesSistema` 136–235): 3 conta; 4 guia (imóvel); 1 guia com matrícula; 9 guia com cliente; 6 guia cliente; 5 documento de cobrança/extrato; 8 documento de cobrança novo; 7 fatura do cliente responsável; 0 legado da empresa.
Campo livre de **conta** (tipo 3): `[0,3)` localidade; `[3,11)` ou `[3,12)` matrícula (8 ou 9 dígitos, indicado na posição 13); `[14,20)` referência (MMAAAA); `[20]` DV módulo 10 da conta; `[21,24)` livre. Os outros tipos reorganizam o mesmo campo (tipo de débito + ano para guia; sequência + tipo de documento para cobrança).

**Ficha de compensação / boleto (fato, `util/CodigoBarras.java`):** monta 44 posições = banco(3) + moeda(1) + DV(1) + **fator de vencimento(4)** + valor(10) + `000000` + **nosso número sem DV (17)** + carteira(2); DV geral por **módulo 11** (0, 10 e 11 viram 1); linha digitável em 5 campos com DV módulo 10 nos três primeiros. O **fator de vencimento usa base 07/10/1997**. A estrutura "6 zeros + 17 de nosso número + 2 de carteira" é o formato de **convênio de 7 posições do BB [INF]**. `BoletoInfo` + `formatarLinkBB` mostram que a emissão de boleto "registrado" era delegada a um **formulário web do banco** passando dados por *query string*, com o código de convênio **fixo no código**.

**Lacuna regulatória [EXT]:** o fator de vencimento FEBRABAN reinicia em 1000 a partir de 22/02/2025 (a base de 1997 esgotou em 9999). Usar a base antiga estraga todo boleto com vencimento a partir dessa data.

### 2.5 Estados e classificação

`PagamentoSituacao` (fato, `arrecadacao/pagamento/PagamentoSituacao.java`):

| Id | Constante | Significado operacional |
|---|---|---|
| 0 | PAGAMENTO_CLASSIFICADO | casado com documento, valor ok |
| 1 | PAGAMENTO_EM_DUPLICIDADE | segundo pagamento do mesmo documento (vira devolução/crédito) |
| 2 | DOCUMENTO_INEXISTENTE (= FATURA_INEXISTENTE) | não achou o documento |
| 3 | VALOR_EM_EXCESSO | excedente |
| 4 | VALOR_A_BAIXAR | excedente aguardando baixa |
| 5 | VALOR_NAO_CONFERE | valor diferente do documento |
| 7 | MOVIMENTO_ABERTO | |
| 9 | DUPLICIDADE_EXCESSO_DEVOLVIDO | já devolvido |
| 10 | DOCUMENTO_A_CONTABILIZAR | documento ainda no ciclo contábil aberto |
| 11 | DOC_INEXISTENTE_DEBITO_PRESCRITO | conta prescrita |
| 12 | DOC_INEXISTENTE_CONTA_PARCELADA | conta já parcelada |
| 13 | DOC_INEXISTENTE_CONTA_CANCELADA | conta cancelada |
| 14 | DOC_INEXISTENTE_ERRO_PROCESSAMENTO | |
| 15, 16 | CLASSIFICADO_RECUPERACAO_CREDITO_CANCELADO / _DUPLICIDADE | pagamento reaproveitado como recuperação de crédito |

`DevolucaoSituacao`: 1 classificada; 2 outros valores; 3 pagamento em duplicidade não encontrado; 4 guia de devolução não informada; 5 valor não confere.
`DebitoCreditoSituacao` da conta: 0 normal, 1 retificada, 2 incluída, 3 cancelada, 4 cancelada por retificação, 5 parcelada, 6 arrastada, 7 entrada de parcelamento, 8 débito prescrito, 9 pré-faturada, 10 cartão de crédito, 11 erro de processamento, 12 débito prescrito (contas incluídas), 13 paga.

**Classificação mensal** (`classificarPagamentosDevolucoes`, UC0300; fato pelos comentários e trechos lidos; ordem exata dos ramos de `processarPagamentoAMenorOUAMaior` **não conferida linha a linha**):

```
para cada localidade (páginas de 500 imóveis):
  para cada imóvel e referência de pagamento com pagamentos de CONTA até a ref. de arrecadação:
    conta = CONTA ?? CONTA_HISTORICO ?? (nenhuma → DOCUMENTO_INEXISTENTE)
    conforme conta.situação:
        PRESCRITA(8,12) → pagamento 11 ; CANCELADA(3) → 13 ; PARCELADA(5) → 12 ; ERRO(11) → 14
        NORMAL/INCLUIDA/RETIFICADA:
           se conta.refContabil >= refFaturamentoAtual: pagamento → DOCUMENTO_A_CONTABILIZAR(10)  # ciclo aberto
           senão:
              total = Σ valor − Σ excedente dos pagamentos em VALOR_A_BAIXAR
              se |total − valorConta| <= 0,03:  todos CLASSIFICADO(0); conta fica PAGA(13)
              senão a maior/menor:
                  total < valorConta → VALOR_NAO_CONFERE(5)
                  total > valorConta → o pagamento de valor igual ao do documento e data mais antiga
                                       fica CLASSIFICADO; os demais EM_DUPLICIDADE(1) com valorExcedente = valor;
                                       pagamentos de valor diferente → VALOR_NAO_CONFERE(5)
  repete o raciocínio para GUIA DE PAGAMENTO e DÉBITO A COBRAR (referência contábil ≥ faturamento → 10)
  devoluções: casa duplicidade/excesso com guia de devolução
       sem pagamento duplicado → 3 ; sem guia → 4 ; valor ≠ → 5 ; ok → CLASSIFICADA(1)
  reclassificarPagamentosValorNaoConfere: |valorPagamento − valorDocumento| <= 2,00 e imóvel conhecido → CLASSIFICADO(0)

processo seguinte PROCESSAR_PAGAMENTOS_COM_DIFERENCA_DE_DOIS_REAIS:
  para pagamento CLASSIFICADO de conta com |dif| <= 2,00:
     dif > 0 (pagou a maior) → crédito a realizar (tipo PAGAMENTO_NAO_CONFERE) + retifica a conta
     dif < 0 (pagou a menor) → débito a cobrar (tipo VALOR_NAO_CONFERE) + retifica a conta
     refContabil = refArrecadação + 1
```

Regras que o SIGSAN deve **parametrizar** em vez de fixar: tolerância de classificação (0,03), tolerância de ajuste (2,00), política de duplicidade (qual pagamento "ganha"), referência de corte contábil.

### 2.6 Aviso bancário, conciliação e float

- Cada arquivo aceito gera um **aviso bancário** por (arrecadador, forma, data prevista de crédito): `valorArrecadacaoInformado` (do trailer/banco), `valorArrecadacaoCalculado` (soma dos pagamentos classificados), idem devolução. `valorRealizado` = informado (se aceito) ou calculado (se não aceito). Referência de arrecadação = maior entre o mês da data de crédito e a referência corrente (fato, `gerarOcorrenciaAvisoBancario`).
- **Aberto/Fechado** (fato, `obterSituacaoAvisoBancario`):
  `arrecFinal = (calculado − informado) − (acertosCrédito − acertosDébito)`; `devolFinal` análoga; **fechado se `arrecFinal == devolFinal`**.
- **Acréscimo ao fechamento:** `DiferencaAcumulada` carrega a diferença para o mês seguinte (batch `ATUALIZAR_DIFERENCA_ACUMULADA_NO_MES`, 1119).
- **Float e tarifa:** `ArrecadadorContratoTarifa.nndiafloat` + `vltarifa`/`pctarifa` alimentam `ArrecadacaoDadosDiarios` (`nndiafloat`, `vltarifa`) e os relatórios "Dados diários … agente". A **data prevista de crédito** vem no registro G.
- Pagamento por **débito automático em atraso não gera acréscimo** (regra explícita no cálculo, §3.5).

### 2.7 Débito automático

Fluxo (fato por classes e batch `BATCH_ATUALIZAR_CODIGO_DEBITO_AUTOMATICO`, `gerarMovimentoDebitoAutomaticoBanco`):
opção do cliente (`DebitoAutomatico`, registrada com o banco via registro B/C) → por grupo de faturamento gera-se o movimento de envio (registro E, por conta, `DebitoAutomaticoMovimento` com NSA de envio) → banco responde com registro F (`codigoRetorno`; `DebitoAutomaticoRetornoCodigo`: `0` debitado, `31` debitado em data diferente da informada, demais = motivos de rejeição) → sucesso gera pagamento (forma 13). `CobrancaCriterioLinha` tem limites mínimos próprios para **cobrar imóvel com débito automático** (`vlmindebdebaut`, `qtmincontasdebaut`).

### 2.8 Encerramento do mês de arrecadação

`encerrarArrecadacaoMes` (UC0276) soma pagamentos e devoluções por **tipo de recebimento** (`RecebimentoTipo`, 18 valores: classificados, não classificados, devoluções, arrecadação líquida, meses anteriores classificados no mês, baixa de não classificados, valores contabilizados como perdas, recuperação de crédito…) e por **item de lançamento contábil** × localidade × categoria, escrevendo `ResumoArrecadacao`. Há sequências de totalização por faixa de `LancamentoTipo` (800–1099, 1100, 1200–1599, 1600, 1700–1999, 2000, 2100–2399, 2400, 2460) que montam subtotais (receita bruta → líquida → arrecadação líquida). Depois `gerarHistoricoParaEncerrarArrecadacaoMes` move contas, guias, débitos a cobrar, créditos a realizar, pagamentos e devoluções para tabelas `*_historico`, com **pré-condição** "existe resumo da arrecadação para o ano/mês" (senão aborta).

---

## 3. Cobrança

### 3.1 Modelo (fato, `cobranca/*.hbm.xml`)

```
CobrancaGrupo (cbgr) ──< CobrancaGrupoCronogramaMes (cbcm: ref AAAAMM)
                              └──< CobrancaAcaoCronograma (cbcr) >── CobrancaAcao (cbac)
                                          └──< CobrancaAcaoAtividadeCronograma (caac: dtprevista, comando, realizacao,
                                                 qtdocumentos, vldocumentos, qtmaximadocumentos) >── CobrancaAtividade (cbat)
CobrancaAcaoAtividadeComando (cacm)  = comando EVENTUAL (fora do cronograma), com filtros de localização/valor/consumo/fiscalização
CobrancaCriterio (cbct) ──< CobrancaCriterioLinha (cbcl: perfil do imóvel × categoria → faixa de valor e de qtd. de contas)
CobrancaDocumento (cbdo) ──< CobrancaDocumentoItem (cdit: conta | guia | débito a cobrar | crédito a realizar | prestação de contrato)
CobrancaSituacao / CobrancaSituacaoTipo / CobrancaSituacaoMotivo / CobrancaSituacaoHistorico / CobrancaSituacaoComando (situação especial por imóvel)
```

- **Ação** (`cobranca_acao`): ordem de realização, obrigatoriedade, repetição, **ação predecessora** (e alternativa), `nndiasminacaoprecedente`, `nndiasvalidade`, `nndiasvencimento`, `nndiasencatividade`, `nndiasencosatividade`, situação de ligação água/esgoto exigida, tipo de documento gerado, **tipo de serviço** (gera OS), critério, indicadores (suspensão de abastecimento, geração de taxa, acréscimo por impontualidade, cobra débito a cobrar/crédito a realizar, usa metas, usa ordenamento, boletim, nota promissória), `qtdiaslimitepagamento` (remuneração de terceiro).
- **Atividade** (`cobranca_atividade`): ids em código: 1 simular, 2 emitir, 3 encerrar, 5 encerrar OS; cada uma com predecessora, `nndiasexecucao`, vinculada a um `Processo` batch.
- **Critério** (`cobranca_criterio` + linhas): vigência; flags de emissão para imóvel em paralisação, em situação de cobrança, com débito só do mês / só de contas antigas / em revisão / de inquilino; `vllimiteprioridade`; **percentuais mínimos de valor/quantidade pagos de parcelado/cancelado**; cada linha: perfil do imóvel × categoria com valor mínimo/máximo do débito, qtd. mínima/máxima de contas, mínimos específicos para débito automático, mínimo do mês, qtd. mínima de contas em parcelamento.
- **Documento** (`cobranca_documento`): valores (documento, desconto, taxa, acréscimos, impostos), emissão, sequência, forma de emissão (`DocumentoEmissaoForma`: 1 cronograma, 2 eventual, 3 individual, 4 impressão simultânea), situação da ação (`CobrancaAcaoSituacao`), situação do débito (`CobrancaDebitoSituacao`), antes/depois do limite, documento predecessor, motivo de não entrega, RD, perfil, categoria, esfera de poder, fiscalização.

### 3.2 Ações de cobrança (catálogo, fato `CobrancaAcao.java`)

| Id | Ação | Id | Ação |
|---|---|---|---|
| 1 | Aviso de corte | 15 | Carta de cobrança — parcelamento |
| 2 | Corte administrativo | 16 / 17 / 18 | Fiscalização: factível / potencial / ligado |
| 3 | Corte físico | 20 | Fiscalização: ligado à revelia |
| 4 | Supressão parcial | 21–25 | Variantes "à revelia" (aviso, corte, carta) |
| 5 | Supressão total | 26 | Fiscalização total |
| 6 | Aviso de tamponamento | 27 | Corte físico (público) |
| 7 | Tamponamento de esgoto | 29 | Recorte |
| 8 / 9 | Fiscalização: suprimido / cortado | 30 | Inspeção de ligações |
| 10 / 11 | Carta de cobrança: suprimido / cortado | 33 | Carta de final de ano |
| 12 / 13 | Carta tarifa social: ligado / cortado | 34 | Visita de cobrança |
| 14 | Carta de cobrança: ligado | 35 | Fiscalização de situação do esgoto |

`CobrancaAcaoSituacao` (do documento/ação): 1 pendente, 2 executada, 3 cancelada, 4 cancelada por prazo, 5 fiscalizada, 6 enviados, 7/8/9 suspensa por pagamento/parcelamento/cancelamento (geral, até, após a data de emissão).

### 3.3 Por que um imóvel não gera documento (`MotivoNaoGeracaoDocCobranca`, 27 motivos, fato)

1 imóvel (MARIO — nome truncado no código), 2 imóvel excluído, 3 sem documento precedente válido, 4 sem OS da ação precedente executada no prazo, 5 já existe documento válido de ação sucessora, 6 já existe documento válido do mesmo tipo, 7/8 situação de ligação de água/esgoto inválida, 9 situação especial de cobrança, 10 situação de cobrança não permitida, 11 critério não permite situação de cobrança, 12 sem critério para o perfil, 13 sem débitos, 14/15/16 critério não permite débito só do mês / só antigo / mês e antigo, 17 valor fora da faixa, 18 qtd. de itens fora da faixa, 19/20 abaixo do mínimo para débito automático, 21/22 abaixo do mínimo (inquilino), 23 qtd. de contas parceladas abaixo do mínimo, 24 excedeu metas, 25 consumo médio fora do intervalo, 26 situação de fiscalização não atende, 27 qtd. de contas inválida para parcelamento.
**Isso é um catálogo excelente para auditoria de dunning:** o SIGSAN deve registrar o motivo de **não** geração (há relatórios analítico e sintético dele em `reports/`).

### 3.4 Situação especial de cobrança

`CobrancaSituacaoTipo` (fato): 1 cobrança por empresa terceirizada; 5 paralisar ações de cobrança; 6 paralisar arrasto; 7 paralisar arrasto e todas as ações; 8 paralisar ações **e acréscimos por impontualidade**; 9 paralisar ordens de corte e supressão; 10 paralisar supressão total e parcial; 11 suspender pagamento da conta. `CobrancaSituacao` (cobrança do imóvel) tem 1 empresa terceirizada, 2 cheque devolvido, 4 cobrança administrativa, 5 em cobrança judicial, 11/12 negativado automaticamente no SPC/Serasa, 13/14 carta enviada ao SPC/Serasa, 15/16/17 em análise para negativação (geral/SPC/Serasa), com flags `bloqueio de parcelamento / inclusão / retirada`, `prescreve particular`, `exige advogado`, `não incluir em cobrança por resultado`.

### 3.5 Acréscimos por impontualidade (fórmulas)

`IndicesAcrescimosImpontualidade` (`cobranca.indices_acresc_impont`): por **mês de referência** `pcmulta`, `pcjurosmora`, `nnfatoratlzcmonetaria`, `pclimitemulta`, `pclimitejuros`, `icmultamensal`, `icjurosmensal`. Parâmetros globais em `SistemaParametro`: `numeroDiasCalculoAcrescimos` (carência em **dias úteis**), `numeroDiasVencimentoCobranca`, `numeroMesesCalculoCorrecao`, `indicadorAtualizacaoTarifaria`, `indicadorTabelaPrice`. Pseudocódigo (fato, `calcularAcrescimoPorImpontualidade`, ControladorCobranca ~5107):

```
acrescimos(refDebito, venc, dtPgto, valor, multasJaCobradas, calcularMulta, refArrecadacao, idConta, arrecadacaoAtiva):
  se a conta foi paga por DÉBITO AUTOMÁTICO → tudo 0
  dtPgto ← dtPgto ?? hoje                         # BUG: o código chama subtrairNumeroDiasDeUmaData e DESCARTA o retorno
  diasUteis ← diasUteisEntre(venc, dtPgto, feriados do MUNICÍPIO da conta)
  se diasUteis <= numeroDiasCalculoAcrescimos → tudo 0 (carência)
  idx ← indice[AAAAMM(venc) − 1] ?? menorIndice(refDebito) ?? menorIndice() ?? zeros (mensal=SIM)
  dias ← diasCorridos(venc, dtPgto)
  # multa
  se calcularMulta:
      pct ← idx.multaMensal ? idx.pcMulta : min(idx.pcMulta × dias, idx.limiteMulta)
      multa ← (valor − multasJaCobradas) × pct / 100
  # juros e correção só se, em modo arrecadação, o vencimento NÃO é posterior à referência de arrecadação
  se idx.jurosMensal:
      meses ← diferençaDeMeses(dtPgto, venc);  juros ← meses <= 0 ? 0 : valor × meses × pcJuros / 100   # linear, sem capitalização
  senão:
      pct ← min(idx.pcJuros × dias, idx.limiteJuros);  juros ← valor × pct / 100
  se !indicadorAtualizacaoTarifaria:
      se numeroMesesCalculoCorrecao é nulo OU diffMeses(venc, dtPgto) > numeroMesesCalculoCorrecao:
          fi ← idx.fator ; ff ← fator[AAAAMM(dtPgto)] ?? menorIndice(...)
          se fi ≠ 0 e ff ≠ 0:  correção ← valor × (ff / fi) − valor
  senão: correção ← atualizaçãoTarifária(conta)      # não lida
  arredondar cada parcela a 2 casas HALF_UP
```

Observações para o SIGSAN: (a) a **multa pode ser diária com teto** ou fixa mensal; (b) juros são **lineares**; (c) a correção usa **razão de dois fatores mensais**; (d) a carência é em **dias úteis municipais** (UC0747), mas multa/juros diários usam **dias corridos**; (e) o limite legal de multa [EXT] para consumidor é 2 %; o campo `pclimitemulta` permite impor, mas nada o força.

### 3.6 Parcelamento de débitos

**Entidades (fato, `cobranca/parcelamento/*.hbm.xml`):**

| Entidade | Papel |
|---|---|
| `Parcelamento` (`cobranca.parcelamento`) | cabeçalho: tipo (`POR_IMOVEL=1`), situação, imóvel, cliente, RD, perfil, forma de cobrança, valores: conta, serviços a cobrar, atualização monetária, juros de mora, multa, **débito atualizado**, desconto faixa de referência, desconto de acréscimos, desconto de antiguidade, de inatividade, de sanção, de tarifa social, **entrada**, juros do parcelamento, nº de prestações, **valor da prestação**, taxa de juros, crédito a realizar, parcelamentos a cobrar, guia de pagamento (entrada), flags (restabelecimento, contas em revisão, guias, crédito, confirmação), **nº de parcelas pagas consecutivas**, data/motivo/usuário de cancelamento. Constantes de arredondamento: **2 casas, `ROUND_HALF_DOWN`** |
| `ParcelamentoSituacao` | 1 normal, 2 desfeito, 3 concluído, 4 cancelado |
| `ParcelamentoItem` | itens parcelados: conta, guia, débito a cobrar, crédito a realizar |
| `ResolucaoDiretoria` | norma interna com vigência e flags: parcelamento único, uso livre, desconto por faixa de referência, desconto de sanções, parcelas em atraso, parcelamento em andamento, negociação só à vista, desconto só em conta à vista, loja virtual, `pcdoacao`, RD alternativa para parcelas em atraso/em andamento |
| `ParcelamentoPerfil` (por RD × subcategoria × situação do imóvel × perfil × categoria) | percentuais de desconto sobre **multa, juros de mora e atualização monetária**; % da tarifa mínima como prestação mínima; consumo mínimo; variação de consumo médio; limites de referência (inferior/superior); desconto tarifa social, sanções, **à vista** (acréscimos e total, com data-limite); entrada mínima; máximo de reparcelamentos |
| `ParcelamentoQuantidadePrestacao` | por perfil: máx. de prestações, **taxa de juros**, % mínimo de entrada, % da tarifa mínima como entrada, % do valor reparcelado, prestação especial, % de entrada sugerida, fator de quantidade de prestações, flag "média do valor das contas", flag "valor da última conta em atraso" |
| `ParcelamentoFaixaValor` | faixa de valor do débito → % de entrada |
| `ParcelamentoFaixaDesconto` | **faixa de referência da conta (min–max AAAAMM) → % de desconto** |
| `ParcelamentoDescontoAntiguidade` | meses mínimos de débito → % de desconto com/sem restabelecimento, com **motivo de revisão** (contas com esse motivo não entram) |
| `ParcelamentoDescontoInatividade` | meses máximos de inatividade da ligação suprimida → % com/sem restabelecimento |
| `ParcelamentoQuantidadeReparcelamento` | quantidade máxima de reparcelamentos |
| `ParcelamentoMotivoDesfazer` / `ParcelamentoMotivoCancelamento` | `ENTRADA_NAO_PAGA = 4` etc. |
| `ContratoParcelamento` (+ `Item`, `RD`, `QuantidadePrestacoes`, `PrestacaoContratoParcelamento`) | **parcelamento por cliente** (grandes clientes/órgãos): contrato com data, faixa de débitos (referência e vencimento), flags (com acréscimos, com juros, permite informar valor da parcela), nº/valor das prestações, taxa de juros, contrato anterior (renegociação), prestações com vencimento e pagamento |
| `GuiaPagamentoParcelamentoCartao`, `ParcelamentoPagamentoCartaoCredito` | entrada/parcelas em cartão |

**Fluxo de simulação** (`obterOpcoesDeParcelamento`, UC de negociação; fato pelos comentários numerados e trechos de código; ordem **[INF]** nos pontos marcados):

```
perfil ← obterPerfilParcelamento(imóvel, situação de ligação, perfil do imóvel, RD)
débitos ← contas, guias, débitos a cobrar (valorTotalComBonus) , créditos a realizar
para cada conta: calcula multa + juros + correção (§3.5) em dtPgto = hoje
   (1) desconto por ANTIGUIDADE: percentual conforme meses de débito e restabelecimento (sim/não),
       aplicado separadamente sobre conta, multa, juros e correção; contas com motivo de revisão ficam fora
   (2) desconto por FAIXA de referência da conta: ParcelamentoFaixaDesconto
   (3) desconto dos ACRÉSCIMOS: perfil.pcDescontoMulta / JurosMora / AtualizaçãoMonetária
       (calculado por item, alteração "Felipe Santos" no código)
   (4) se ligação de água SUPRIMIDA: desconto por INATIVIDADE (meses desde a supressão) e versão à vista
   (5) RD especial: desconto de SANÇÕES (% sobre sanções) e tarifa social
valorDebitoDesconto ← débitoAtualizado − descAntiguidade − descFaixa − descAcréscimos − descInatividade − descSanções ...
descontoAVista ← regra própria (pcDescontoAcrescimosPagamentoAVista, pcDescontoTotalPagamentoAVista, até dataLimite)
para cada ParcelamentoQuantidadePrestacao do perfil:
   entradaMínima ← aplica em sequência, cada regra SOBRESCREVENDO a anterior quando configurada:
        valorDebitoDesconto × pcMinimoEntrada/100
        tarifaMínimaÁgua × pcTarifaMinimaImovel/100
        valorDebitoDesconto × pcPorFaixaDeValor (ParcelamentoFaixaValor)
        média(valor das contas + acréscimos) se flag "média"        # o código tem um teste que nunca é verdadeiro (qtd > qtd)
   entradaReparcelamento ← (Σ parcelas vencidas + a vencer de parcelamento ativo) × pcValorReparcelado/100
   entradaÚltimaConta ← valor da última conta do imóvel, se flag "última conta em atraso"
   entrada ← obterValorEntradaParcelamento(entradaInformada, entradaMínima, entradaReparcelamento,
                                            usuário, entradaSugerida, entradaÚltimaConta)   # combinação não lida (lacuna)
   a_parcelar ← valorDebitoDesconto − entrada
   para n = 1 .. maxPrestações (e prestação especial só com permissão):
        i ← taxaJuros/100
        se i > 0:
            se sistema.TabelaPrice: prestação ← Price(i, n, valorDebitoDesconto, entrada)
            senão:  prestação ← (a_parcelar × (1+i)^n) / n         # juros COMPOSTOS capitalizados, dividido por n (não é Price)
        senão:  prestação ← a_parcelar / n
        prestaçãoMínima ← tarifaMínimaÁgua × perfil.pcTarifaMinimaPrestacao/100
        descarta n se prestação < prestaçãoMínima (exceto com permissão especial) ou prestação <= 0
```

**Conclusão/pós-parcelamento:**
- `concluirParcelamentoDebitos` (UC) cria: **guia de pagamento da entrada** (vencimento controlado por parâmetro) ou **conta de entrada** (`DebitoCreditoSituacao.ENTRADA_DE_PARCELAMENTO=7`); **débitos a cobrar** `PARCELAMENTO_AGUA/ESGOTO/SERVICO` (curto e longo prazo: `ParcelamentoGrupo` 2–5) e `JUROS_PARCELAMENTO`; créditos a realizar para descontos; marca as contas como PARCELADA(5).
- **Parcelas bônus:** `DebitoACobrar.numeroParcelaBonus` e `getValorTotalComBonus()`/`getNumeroPrestacaoCobradasMaisBonus()` mostram que o parcelamento pode ter **parcelas finais perdoadas** ("bônus"). O batch `ATUALIZA_QUANTIDADE_PARCELA_PAGA_CONSECUTIVA_PARCELA_BONUS` (1254/895) atualiza `parc_nnparcelaspagasconsecs` e o relatório de negativação acompanha parcelamento. A **regra exata do bônus não foi lida** (**lacuna**): pelo nome e uso, **[INF]** o cliente que paga N parcelas consecutivas em dia ganha o perdão das últimas; precisa ser levantada com o time antes de implementar.
- **Desfazer por entrada não paga** (`desfazerParcelamentosPorEntradaNaoPaga`, `...SemAnoMesReferencia`, UC fato): parcelamento NORMAL cuja guia de entrada está vencida e sem pagamento até `hoje − numeroDiasVencimentoCobranca` → situação DESFEITO com motivo 4; contas voltam ao estado anterior; batch `INCLUIR_DEBITO_A_COBRAR_ENTRADA_PARCELAMENTO_NAO_PAGA` (1118) transforma a entrada não paga em **débito a cobrar** na próxima conta.
- **Cancelar parcelamento** (`ControladorParcelamento.cancelarParcelamentos`): gera **conta incluída** com os débitos, `CANCELAMENTO_PARCELAMENTO=11`, novos acréscimos e débitos a cobrar de **juros de parcelamento**; `QTD_CONTAS_CANCELAMENTO_PARCELAMENTO = 3` (constante em `ConstantesSistema` — parece o gatilho de parcelas em atraso, **[INF]**).
- `emitirParcelamentoEmAtraso` (cobrança): documento para cliente com parcelas em atraso.

### 3.7 Negativação (SPC / Serasa)

Entidades (fato): `Negativador` (SPC=1, Serasa=2; é um cliente), `NegativadorContrato` (contrato/convênio, **NSA de envio e retorno**, valor da tarifa por inclusão, inclusões contratadas/enviadas, `nnprazoinclusao`, tamanho do registro), `NegativacaoComando` (simulação ou real, baixa renda, órgão público, conta no nome do cliente, previsão e realização), `NegativacaoCriterio` (setor, referência e vencimento do débito, valor e qtd. mín/máx., qtd. máxima de inclusões, **tipos de cliente, de CPF/CNPJ, perfis, subcategorias, situações de ligação, situações de cobrança e especial**, flags: imóvel em paralisação, em situação de cobrança, conta em revisão, guia de pagamento, carta de parcelamento, parcelamento em atraso, `nndiasretorno`), `NegativacaoImoveis` (inclusão, excluído, data de confirmação), `NegativadorMovimento` / `NegativadorMovimentoReg` / `NegativadorMovimentoRegItem` (arquivo de remessa; registro por imóvel/cliente; item por conta/guia/débito com valor, pago, cancelado, **situação do débito após exclusão**), `NegativadorMovimentoRegRetMot` / `NegativadorRetornoMotivo` (motivos de rejeição do retorno), `NegativadorExclusaoMotivo` ("pagamento da dívida", "renegociação da dívida", "motivo não identificado"), `ResumoNegativacao`.

Ciclo (fato, `ControladorSpcSerasaSEJB` + batch `spcserasa/`):

1. **Comando** (simulação opcional; `NegativadorResultadoSimulacao`).
2. **Executar comando** (`EXECUTAR_COMANDO_DE_NEGATIVACAO`, 876): para cada imóvel/rota verifica o critério (`verificarCriteriodeNegativacaoParaImovel`, ~400 linhas): baixa renda; órgão público não negativa se o comando não o inclui; exclui quem foi **enviado e aceito há N dias** ou rejeitado por motivos escolhidos; situação especial e de cobrança só as selecionadas (considera histórico de retirada); débitos por faixa; **titularidade** (CPF/CNPJ válido, conta em nome do cliente — `TitularidadeCpfCnpjNegativacaoHelper`); cria registro no movimento (layout SPC: header/detalhe consumidor/detalhe SPC/trailer; layout Serasa: header com CNPJ da instituição, contato, remessa, `logon`, detalhe consumidor); grava situação de cobrança "em análise para negativação" no imóvel.
3. **Envio** (arquivo por e-mail/FTP **[INF]**; `ngcn_dsemailenvioarquivo`) e **retorno** (`GERAR_MOVIMENTO_RETORNO_NEGATIVACAO`, 892): registros aceitos/rejeitados com motivo.
4. **Confirmar negativação** (`DETERMINAR_CONFIRMACAO_DA_NEGATIVACAO`, UC1005): data de confirmação = `dtprocessamentoenvio + prazo de inclusão do contrato + 1 dia`; atualiza a situação de cobrança do imóvel (de "carta enviada" para "negativado automaticamente"), `NegativacaoImoveis` e o registro.
5. **Exclusão**: online (ao pagar/renegociar, `excluirNegativacaoOnLine`) ou por movimento (`GERAR_MOVIMENTO_EXCLUSAO_NEGATIVACAO`, 854/1169); o item guarda a situação do débito após exclusão.
6. **Acompanhamento**: pagamento do parcelamento de débito negativado (`ACOMPANHAR_PAGAMENTO_DO_PARCELAMENTO`), resumo diário e resumo da negativação por localização/perfil/situação (`GERAR_RESUMO_DIARIO_NEGATIVACAO` 875, `GERAR_RESUMO_NEGATIVACAO` 1253, `ATUALIZAR_NUMERO_EXECUCAO_RESUMO_NEGATIVACAO` 1168).

O GSAN prevê a **carta ao devedor antes da negativação** (situações 13/14 "carta enviada", critério "recebimento de carta de parcelamento + dias de atraso"), alinhada à exigência de **notificação prévia [EXT]**. Não vi como o SIGSAN deve provar o recebimento (AR, SMS, e-mail) — falta definir.

### 3.8 Cobrança por resultado (empresa terceirizada)

Fato por entidades e batch: `ComandoEmpresaCobrancaConta` (empresa, faixa de setores/localidades/quadras, referência e vencimento das contas, valor mínimo/máximo, perfil residencial/comercial/industrial/público, qtd. de contas/clientes, tipo de serviço, **telemarketing**, **só com CPF/CNPJ**, débito pretérito, dias de ciclo), `EmpresaCobrancaConta` (conta enviada: **`pcempresaconta` — % de remuneração da empresa**, `icpagamentovalido`, data de envio e de retirada, OS), `EmpresaCobrancaContaPagamentos` (pagamento atribuível à empresa, parcela atual/total, desconto), `NegociacaoCobrancaEmpresa` + `NegociacaoContaCobrancaEmpresa` (acordo feito pela empresa → parcelamento/documento/guia). Batch: gerar movimento, gerar arquivo texto de contas para a empresa, atualizar pagamentos, gerar arquivo de pagamentos/relatório, encerrar comando (prazo vencido ou cancelamento, motivos 1/2), receber arquivo TXT de encerramento de OS.

### 3.9 Auto de infração, prescrição, dívida ativa e protesto

- **Auto de infração** (fato): `AutosInfracao` (imóvel, **OS de fiscalização**, funcionário, situação de fiscalização, tipo de débito, parcelas, datas de emissão/início/término do recurso, ref. gerada) + `AutosInfracaoDebitoACobrar` (`icmultaautoinfracao`). Situações: 1 em prazo de recurso, 2 prazo vencido, 3 recurso em análise, 4 julgado procedente, 5 improcedente, 6 cancelado; flags `gerar débito`, `data início/término de recurso`. Batch `ATUALIZAR_AUTOS_INFRACAO_PRAZO_RECURSO_VENCIDO` (1208) muda 1→2 e gera o débito.
- **Prescrição** (fato): `cobranca.prescricao` (ref. AAAAMM por **esfera de poder**), batch `GERAR_PRESCREVER_DEBITOS_DE_IMOVEIS` (1350, por situação de cobrança escolhida), `PRESCREVER_DEBITOS_IMOVEIS_PUBLICOS_AUTOMATICO/MANUAL` (239/240, por esfera). Conta vira DEBITO_PRESCRITO(8/12); pagamentos posteriores caem em `DOCUMENTO_INEXISTENTE_DEBITO_PRESCRITO(11)`; há tipos de lançamento contábil de "cancelamento por prescrição" já excluído ou não da inadimplência (72–80).
- **Dívida ativa / protesto / cobrança judicial:** **não há** módulo. Só a situação de cobrança `EM_COBRANCA_JUDICIAL=5` e o flag "exige advogado". Protesto de CDA (Lei 9.492/1997 art. 1º par. único, [EXT]) **fica de fora do GSAN**; o SIGSAN pode tratá-lo como lacuna ou integração futura (CRA/IEPTB).
- **Devedores duvidosos:** §4.

---

## 4. Financeiro / contábil

### 4.1 Modelo (fato, `financeiro/*.hbm.xml`)

| Entidade | Papel |
|---|---|
| `ContaContabil` (`cnct_*`) | plano de contas: prefixo, número, dígito, terceiros, flag linha, flag centro de custo |
| `LancamentoOrigem` | 1 faturamento, 2 arrecadação, 3 devedores duvidosos, 4 aviso bancário; guarda **cartão, folha, versão, nº de histórico débito/crédito** (herança do formato de integração de um sistema contábil legado) |
| `LancamentoTipo` / `LancamentoItem` / `LancamentoItemContabil` / `LancamentoTipoItem` | árvore de **tipos** (ids até 107) × **itens** (ids até 132) × **itens contábeis** (15); `lctp_cdtipocontabil`, flags total/resumido/impressão |
| `LancamentoContabil` + `LancamentoContabilItem` | `lcnb_amlancamento`, origem, tipo, localidade, tipo de recebimento; item com `icdebitocredito`, `vllancamento`, `dshistorico`, conta contábil, `cdterceiro` — **partida dobrada de fato** (débito/crédito por linha) |
| `ArrecadacaoContabilParametros`, `DevedoresDuvidososContabilParametro` | **parametrização conta débito / conta crédito / texto de histórico** por (tipo de lançamento × item × item contábil × categoria) |
| `ResumoFaturamento`, `ResumoArrecadacao`, `ResumoReceita`, `ResumoDevedoresDuvidosos`, `LancamentoResumo*` | resumos mensais que alimentam lançamentos |
| `ContaAReceberContabil` | saldo contábil de contas a receber (curto/longo prazo, particular/público) |
| `DocumentosAReceberResumo` + `FaixaDocumentosAReceber` | aging (dias vencidos × faixa de valor × categoria × esfera × perfil) |
| `ValorVolumesConsumidosNaoFaturado` | receita a faturar (volume medido não faturado) |
| `ParametrosDevedoresDuvidosos` + `Item` | PDD |
| `FinanciamentoTipo` | 1 serviço normal, 2/3/4 parcelamento água/esgoto/serviço, 5/6/7 arrasto, 8 juros de parcelamento, 9 entrada de parcelamento, 10 doações, 11 cancelamento de parcelamento |

**Tipos de lançamento relevantes (fato, `LancamentoTipo.java`):** água, esgoto, financiamentos incluídos/cancelados (curto e longo prazo), faturamento adicional por guia, receita bruta/líquida, cancelamentos/inclusões por refaturamento, parcelamentos realizados/cancelados/cobrados/transferidos, débitos anteriores cobrados, devoluções em conta, valores cobrados indevidamente, impostos deduzidos/retidos, total cobrado, guias de pagamento, débitos a cobrar, **pagamento em duplicidade, documento inexistente, valor não confere, guia de devolução não informada**, recebimentos classificados/não classificados, arrecadação líquida, valores contabilizados como perdas, doações, créditos por cobrança indevida, descontos incondicionais, **cancelamento por prescrição**, descontos pagamento à vista, **bônus concedidos no parcelamento**, campanha da criança, recebimentos não identificados, aviso bancário, **recuperação de crédito**.
**Itens com impostos** (fato, `LancamentoItem`): `IMPOSTO_RENDA`, `COFINS`, `CSLL`, `PIS_PASEP` (retenção na fonte de órgãos públicos **[INF/EXT]**).

### 4.2 Lançamento por evento

Fluxo (fato, batch `financeiro/` + `gerarLancamentosContabeisFaturamento/Arrecadacao/DevedoresDuvidosos/AvisoBancario`): o resumo do mês (`ResumoFaturamento`/`ResumoArrecadacao`/`ResumoDevedoresDuvidosos`) por localidade × item × categoria é transformado em `LancamentoContabilItem` conforme a tabela de parâmetros contábeis (conta débito, conta crédito, histórico). Depois `gerarIntegracaoContabilidade` (UC0469) gera um **TXT delimitado por `;`** (ano, conta contábil de 8, centro de custo de 10, localidade de 50, …, moeda BRL, valor débito, valor crédito, histórico montado como "VALOR FATURAMENTO DO MES DE <mês>/<ano>") e existe **variante CAERN** (`gerarIntegracaoContabilidadeCaern`).

### 4.3 Devedores duvidosos (PDD)

Fato (`gerarResumoDevedoresDuvidosos`, `ControladorFinanceiro` ~5979):

```
param ← ParametrosDevedoresDuvidosos[refContabil]    # valorABaixar (meta), valorBaixado (acumulado)
itens ← ParametrosDevedoresDuvidososItem (situação de cobrança, valorLimite, nº de meses)
para cada localidade, para cada quadra:
    valorQuadra ← Σ contas que casam com itens (situação de cobrança, idade ≥ nº de meses, valor ≥ limite)
    se param.valorBaixado + valorQuadra <= param.valorABaixar × 1,10:     # tolerância de 10 %
        param.valorBaixado += valorQuadra
        marca as contas com refContabilDevedoresDuvidosos = refContabil
        acumula ResumoDevedoresDuvidosos por (lançamento tipo/item/item contábil/categoria)
inserir resumos com valor > 0
```

Pontos: (a) é **meta de baixa top-down** (alguém decide quanto baixar no mês), não perda esperada por faixa de atraso; (b) o filtro pela quadra limita estouro; (c) há batch para **apagar/atualizar** resumo (reprocessamento).
**Nota [EXT]:** concessionárias regidas por CPC 48 (IFRS 9) calculam perda esperada por matriz de provisão; o SIGSAN deve **oferecer os dois modos** (meta de baixa e matriz de aging).

### 4.4 Outros resumos

`GERAR_CONTAS_A_RECEBER_CONTABIL` (847), `GERAR_RESUMO_RECEITA` (1310), `GERAR_RESUMO_DOCUMENTOS_A_RECEBER` (1298), `GERAR_VALOR_VOLUMES_CONSUMIDOS_NAO_FATURADOS` (898), `GERAR_DADOS_RECEITAS_A_FATURAR_RESUMO` (16025), relatório `relatorioContasBaixadasContabilmente`. Os resumos gerenciais do GSAN (`gerencial*`, "BIG") ficam em **outro banco** (README: bancos comercial e gerencial) e são recalculados por mês.

---

## 5. Motor batch

### 5.1 Modelo de controle (fato, `batch/*.hbm.xml`, schema `batch`)

| Tabela | Papel | Colunas relevantes |
|---|---|---|
| `processo` | catálogo de processos (~165 constantes em `Processo.java`: relatórios `GERAR_RELATORIO_*` 4–268 e 1000–1001, `FATURAR_GRUPO_FATURAMENTO=2`, `ENCERRAR_ARRECADACAO_MES=50`, `GERAR_RESUMO_DEVEDORES_DUVIDOSOS=79`, `EMITIR_BOLETOS=181`, `ENCERRAR_COMANDO_DE_COBRANCA_RESULTADO=506`, `ATUALIZACAO_CADASTRAL=507` …) | tipo, **`icautorizacao`**, `limite`, `nmarquivobatch`, `prioridade` |
| `processo_tipo` | 1 faturamento comandado, 2 cobrança comandado, 3 mensal, 4 eventual, 5 relatório, 6 semanal, 7 diário | |
| `processo_funcionalidade` | passos do processo: processo × funcionalidade × **`nnsequencialexecucao`** × **unidade de processamento** | **a ordem real do fechamento está aqui — carga de dados não está no repositório** |
| `processo_iniciado` | execução: agendamento, início, término, comando, **`nngrupo`**, usuário, **processo iniciado precedente**, situação | 1 execução = 1 instância |
| `funcionalidade_iniciada` | passo executado: início/fim, **`fuin_parametros` = tarefa Java serializada**, `dserro`, situação | |
| `unidade_iniciada` | unidade processada: tipo de unidade, **código real da unidade** (id da localidade/rota/setor…), situação | **granularidade de retomada** |
| `relatorio_gerado` | PDF resultante (bytea) + nº de páginas | relatórios assíncronos guardados em tabela |

Estados (fato): processo `1 em processamento, 2 concluído, 3 em espera, 4 agendado, 5 início a comandar, 6 concluído com erro, 7 execução cancelada, 8 aguardando autorização`; funcionalidade `1/2/3/4/5/6/7` análogos; unidade `1 em processamento, 2 concluída, 3 em espera, 4 concluída com erro`. Unidades de processamento: rota, conta, atividade de cronograma de faturamento por rota, relatório, funcionalidade, **localidade**, grupo de cobrança no mês, **setor comercial**, marca de hidrômetro, atividade de cobrança (cronograma/comando), comando de empresa de cobrança, negativador movimento reg, unidade de negócio, hidrômetro, empresa, quadra, grupo de faturamento, referência (22 tipos).

### 5.2 Como executa

1. **Quartz 1.5.2** (`AgendadorTarefas.initAgendador`, no boot): marca processos interrompidos; agenda `VerificadorProcessosIniciados` **a cada minuto** e a integração UPA por hora. (Scheduler padrão `StdSchedulerFactory`; não vi `quartz.properties` — jobstore **[INF]** em memória.)
2. `inserirProcessoIniciado` cria o `processo_iniciado` em estado **EM_ESPERA** (ou AGUARDANDO_AUTORIZACAO se o processo exige autorização) e, para cada `processo_funcionalidade` ativo, uma `funcionalidade_iniciada` cuja **tarefa** (`TarefaBatch*` com parâmetros) é **serializada em bytes**. Os parâmetros dependem do `SistemaParametro` (ref. de faturamento/arrecadação) e de consultas ("localidades com pagamentos ou devoluções").
3. `verificarProcessosIniciados` (a cada minuto): seleciona funcionalidades prontas; **só entra a funcionalidade cuja sequência não tem nenhuma pendente anterior** (`pesquisarQuantidadeFuncionaldadesIniciadasForaOrdemExecucao == 0`); **dispara só a primeira** (`break`), então **uma funcionalidade por vez**; muda processo para EM_PROCESSAMENTO e chama `tarefa.agendarTarefaBatch()`.
4. A tarefa roda como job Quartz; `executar()` normalmente **faz fan-out**: para cada unidade (ex.: cada localidade) envia uma **mensagem JMS** (`enviarMensagemControladorBatch`) a uma **fila própria da tarefa** (`BatchEncerrarArrecadacaoMesMDB` etc.). Cada MDB (`MessageDrivenBean`) chama o controlador (resolvido **por empresa**, §2.3) em transação própria (`NotSupported` no MDB, `RequiresNew`/`Required` no método). Há **160 descritores `batch*`**, ~155 classes `*MDB` e ~150 `TarefaBatch*` em `src/gcom/batch/` (contagem por arquivo; cadastro e faturamento somam 37 cada, cobrança 37 MDBs/35 tarefas, gerencial 27/26, arrecadação 11/12, financeiro 12/12).
5. Cada unidade chama `iniciarUnidadeProcessamentoBatch(funcionalidadeIniciada, tipoUnidade, codigoReal)` e `encerrarUnidadeProcessamentoBatch(erro?)`. **Idempotência/retomada:** se a unidade já consta como CONCLUIDA na lista `unidadesJaExecutadas` da tarefa, o início lança "Unidade já executada", que o encerramento trata como sucesso (pula). Se a funcionalidade já está marcada **concluída com erro**, as unidades restantes **se recusam a executar** ("processo está concluído com erro").
6. `encerrarFuncionalidadesIniciadas` / `encerrarProcessosIniciados` fecham os estados quando todas as unidades terminam (com ou sem erro).
7. **Reinício** (`reiniciarFuncionalidadesIniciadas`): apaga as unidades, zera `unidadesJaExecutadas` e volta a funcionalidade para EM_ESPERA (do zero). **Continuação** (`continuarFuncionalidadesIniciadas`, fato): só aceita funcionalidade em CONCLUIDA_COM_ERRO ou EXECUCAO_CANCELADA (e nunca relatório); copia as unidades já iniciadas para `unidadesJaExecutadas` dentro da tarefa serializada, apaga as linhas de `unidade_iniciada` e recoloca em EM_ESPERA, de modo que só as unidades pendentes executam. Após queda do servidor, **todo processo em execução vira EXECUCAO_CANCELADA** no boot.
8. Relatórios usam o mesmo motor (`ProcessoTipo.RELATORIO`), com fila de funcionalidades "agendadas" e `deletarRelatoriosBatchDataExpiracao`.

### 5.3 Inventário do que roda (agrupado; ids de `Funcionalidade.java`)

| Área | Tarefas (id) |
|---|---|
| **Faturamento / leitura** | pré-faturar grupo (0), gerar dados para leitura (185), arquivo texto para leiturista (804), consistir leituras e calcular consumos (52), efetuar rateio de consumo (186), gerar bônus tarifa social (1234), **faturar grupo (63)**, taxa de entrega em outro endereço (187), **débitos a cobrar de acréscimos por impontualidade (188)**, emitir contas (64) / órgão público (794) / extrato condomínio (611) / fatura cliente responsável (629) / contas em braille (1470), emitir boletos (1219), e-mail da conta (1297), crédito de situação especial de faturamento (1183), suspender leitura (1529/1530), retificar conjunto de contas (1160), religar cortados com consumo real (1387), verificar faturamento de cortados (1357), taxa % tarifa mínima de cortado (1354), **encerrar faturamento do mês (552)**, histórico p/ encerrar faturamento (647), histórico de conta (653), declaração de quitação anual (1303/1305) |
| **Arrecadação** | classificar pagamentos e devoluções (545), dados diários (457), processar diferença de R$ 2 (16004), cancelar guias não pagas (5007), inserir pagamentos de faturas especiais (1265), atualizar diferença acumulada (1119), dados de pagamentos não classificados (16041) e documentos não identificados (16042), resumo de arrecadação (694), lançamentos contábeis de arrecadação (700) e de aviso bancário (1291), BIG (relatório gerencial), histórico p/ encerrar arrecadação (642), **encerrar arrecadação do mês (546)**, atualizar ligação de água "ligado análise" → "ligado" (1082) |
| **Cobrança** | gerar atividade de ação de cobrança (539), emitir documento de cobrança (1312), atualizar comando/atividade (1311), encerrar atividade (457 — **mesmo id de "dados diários"**), processar encerramento de OS de ação (1428/1522), resumos de ações (576, 672, 750, 751, 1056), resumo de pendência (582, 1257, 1304), resumo situação especial (598), resumo de indicadores (907), prescrever débitos (1350/1404), desfazer parcelamento por entrada não paga (652/16044), incluir débito de entrada não paga (1118), cancelar parcelamentos (16040), cartas de final de ano (1255) e campanha solidariedade (1216), visita de cobrança, **cobrança por resultado** (1154, 1158, 1159, 1189, 1244, 1474, 1476, 1495, 1500, 16039, 16045), **negativação** (854, 875, 876, 892, 1168, 1169, 1253, 1254, 1302) |
| **Financeiro** | lançamentos contábeis faturamento (681)/arrecadação (700)/aviso bancário (1291)/devedores duvidosos (755), resumo faturamento (1103, 695), contas a receber contábil (847), resumo devedores duvidosos (714, 1406 apagar, 1407 atualizar), resumo de receita (1310), documentos a receber (1298), volumes não faturados (898) |
| **Gerencial / micromedição / cadastro** | ~25 resumos (ligações/economias, consumo de água, anormalidades de leitura, hidrômetros, histograma, metas, parcelamento, refaturamento, registro de atendimento, indicadores de micromedição/comercialização/faturamento; versões `POR_ANO`), tarifa social (bônus/retirada/carta), atualização cadastral (tabelas temporárias, arquivo, diferença), alterar inscrição do imóvel, atualizar conjunto de hidrômetros, movimento de hidrômetro, programação de roteiro de acompanhamento de serviço, OS de fiscalização/inspeção por anormalidade |

**Ordem típica do fechamento** (**[INF]** a partir de dependências no código, não de `processo_funcionalidade`):

```
MENSAL FATURAMENTO (por grupo de faturamento, referência AAAAMM)
  gerar dados/arquivo de leitura → registrar leituras (online) → consistir leituras e calcular consumos
  → rateio de consumo → bônus tarifa social → FATURAR GRUPO → débitos de acréscimos e taxas
  → emitir contas (+ órgão público, braille, e-mail) → histórico das contas
  → [fechamento] histórico p/ encerrar faturamento → ENCERRAR FATURAMENTO (avança AnoMesFaturamento)
  → resumos (faturamento, ligações, consumo, pendência, indicadores) → contas a receber contábil
  → lançamentos contábeis faturamento → devedores duvidosos (apagar → gerar → atualizar → lançamentos)

MENSAL ARRECADAÇÃO
  (online) registrar movimentos dos arrecadadores → CLASSIFICAR PAGAMENTOS/DEVOLUÇÕES
  → dados diários → diferença de R$ 2 → cancelar guias não pagas → diferença acumulada
  → dados de não classificados/não identificados → RESUMO DE ARRECADAÇÃO     (pré-condição do histórico)
  → lançamentos contábeis arrecadação e aviso bancário
  → histórico p/ encerrar arrecadação → ENCERRAR ARRECADAÇÃO (avança AnoMesArrecadacao)

COBRANÇA (por cronograma/ grupo / eventual)
  gerar atividade (seleciona imóveis por critério) → emitir documentos/OS → (executa em campo) → encerrar OS → resumos
NEGATIVAÇÃO: executar comando → envio → retorno → confirmar → exclusão/ acompanhamento
```

### 5.4 Mapeamento para Spring Batch + Quartz no SIGSAN (multi-tenant, idempotente)

| GSAN | SIGSAN (proposta) |
|---|---|
| `processo` / `processo_funcionalidade` (ordem por sequencial) | `Job` Spring Batch com `Step` em sequência; definição **em código** versionada (não em tabela editável), mais tabela `job_definition` só para ligar/desligar e agendar por tenant |
| `processo_iniciado` | `JobExecution` com **parâmetros de identidade** `tenant_id`, `job`, `referencia (AAAAMM)`, `grupo`: mesma identidade = mesma `JobInstance` ⇒ **não roda duas vezes e permite *restart*** |
| `funcionalidade_iniciada` + tarefa serializada | `StepExecution`; parâmetros em **JSON** (`JobParameters`/`ExecutionContext`), **nunca** bytes de objeto Java |
| `unidade_iniciada` (localidade/rota/setor) | **Partitioned step**: partição = unidade (localidade, setor, rota). Registrar em `unit_run(job_execution_id, unit_type, unit_key, status, error)` para **pular concluídas no *restart*** e permitir relatório "por unidade" como no GSAN |
| Filas JMS por tarefa + MDB | Kafka (já na stack) **ou** *remote partitioning* do Spring Batch; ou executor local para o MVP |
| `VerificadorProcessosIniciados` a cada minuto | Quartz (JDBC job store, **clusterizado**) disparando por **tenant × job × calendário**; execução de usuário vira *event* (REST → `JobLauncher` assíncrono) |
| `permiteIniciarProcesso`, `limite`, autorização | `job_definition.requires_approval` + papel RBAC; limite de execuções concorrentes por tenant |
| `marcarProcessosInterrompidos` cancela tudo no boot | Spring Batch marca `FAILED/UNKNOWN`; recuperação por *restart* com `JobExplorer` |
| `relatorio_gerado.bytea` | PDF em SeaweedFS/S3 + metadados (a stack já tem) |
| Resolução de controlador por empresa (`GCOM`→`CAEMA`) | `Strategy` por tenant (`TenantFeatureConfig`) selecionada em runtime, **sem subclasses por empresa** |

**Regras de projeto (lições do GSAN + contexto do SIGSAN):**

1. **Multi-tenant e RLS:** todo `ItemReader/Writer` roda **dentro de transação com `set_config('app.current_tenant', …, true)`** (o Diário já provou que a GUC é local à transação). Um `ChunkListener`/`TransactionSynchronization` deve reaplicar a GUC a cada *chunk*; **jamais** usar uma conexão com a role migradora para lógica de negócio. Testar com o mesmo estilo de `TenantRlsIsolationTest`.
2. **Idempotência por construção:** resumos mensais com **chave natural** + `INSERT … ON CONFLICT DO UPDATE` (o GSAN apaga e reinsere, por isso precisa dos batches "apagar resumo…"); lançamentos contábeis com **chave (origem, período, tipo, item, localidade, categoria)** única; ações financeiras (gerar débito de ajuste, criar guia) com `idempotency_key` derivado do documento de origem.
3. **Período fechado é imutável:** `period_close(tenant, domínio, referencia, status)`; qualquer job de classificação/lançamento recusa período fechado (equivale ao `AnoMesArrecadacao` avançando).
4. **Precondições explícitas** (como "existe resumo de arrecadação para o mês"), verificadas no `JobExecutionListener`, **com mensagem de negócio**.
5. **Uma execução por (job, referência, grupo) por vez**, mas **vários tenants em paralelo** (o GSAN só roda uma funcionalidade por vez em toda a instância).
6. **Observabilidade:** métricas por unidade (duração, linhas), *dead-letter* por unidade e relatório de **motivos de rejeição** (como o catálogo de "motivo de não geração").
7. **Reinício vs continuação:** manter os dois (limpar unidades × só pendentes), expostos na API de operação.

---

## 6. Catálogo de relatórios (`reports/*.jrxml`, 500 arquivos)

Agrupamento por prefixo (contagens de arquivos, incluem subrelatórios):

| Área | Prefixos (n) | Exemplos |
|---|---|---|
| **Cadastros ("Manter…")** | `relatorioManter*` (85) | arrecadador, agência bancária, contrato do arrecadador, forma de arrecadação, aviso bancário (+acertos/deduções), débito automático, movimento de arrecadadores, perfil de parcelamento, resolução de diretoria, critério/cronograma de cobrança, negativador (+contrato, motivos), guia de devolução, mensagem de conta |
| **Segunda via, contas e faturas** | `relatorio2Via*` (7), `relatorioConta*` (22), `relatorioFaturas*`, `relatorioDetailFatura*` | segunda via com boleto bancário, versão CAERN, "tipo 2", contas canceladas/retificadas/retidas/em revisão/emitidas, faturas agrupadas |
| **Débitos e extratos** | `relatorioExtratoDebito*` (10), `relatorioConsultarDebitos*` (6), `relatorioGerarRelacaoDebitos*` (12), `relatorioDebito*`, `relatorioGerarCurvaABCDebitos`, `relatorioValorDebitoConsultarImovel` | extrato com boleto, relação de débitos (contas/guias/tipo de débito/totais), curva ABC |
| **Arrecadação** | `relatorioDadosDiarios*` (13), `relatorioResumoArrecadacao`, `relatorioAnaliseArrecadacao`, `relatorioAnaliseAvisosBancarios`, `relatorioAvisoBancarioPorContaCorrente`, `relatorioPagamentos*`, `relatorioDevolucoes`, `relatorioGuiaDevolucao`, `relatorioDevolucaoPagamentosDuplicidade`, `relatorioDocumentosNaoAceitos`, `relatorioTransferenciaPagamento`, `relatorioAcompanhamentoMovimentoArrecadadores(+PorNSA)`, `relatorioRegistrarMovimentoArrecadadores`, `relatorioMovimentoDebitoAutomaticoBanco`, `relatorioAnalisePagamentoCartaoDebito`, `relatorioComparativoFaturamentoArrecadacaoExpurgo` | |
| **Cobrança** | `relatorioAcompanhamentoAcoesCobranca`, `relatorioComandoDocumentoCobranca`, `relatorioComandos…`, `relatorioFiltrarDocumentoCobranca`, `relatorioMotivoNaoGeracaoDocumentoCobranca*`, `relatorioNotificacaoDebito*` (5), `relatorioReavisoDeDebito*`, `relatorioOrdemCorte*`, `relatorioOrdemFiscalizacao*`, `relatorioVisitaCobranca*`, `relatorioReligacaoClientesInadiplentes*` (5), `relatorioImoveisFaturasAtraso*` (8), `relatorioImoveisComAcordo`, `relatorioGuiaPagamentoEmAtraso`, `relatorioSupressoesReligacoesReestabelecimentos`, `relatorioRotasOnlinePorEmpresa`, `relatorioPagamentosContasCobrancaEmpresa*`, `relatorioResumoAcoesCobrancaEventuais`, `relatorioResumoCobrancaSituacaoEspecial` | |
| **Parcelamento** | `relatorioParcelamento*` (9, por empresa: CAEMA/CAER/CAERN/COSANPA/JUAZEIRO), `relatorioRelacaoParcelamento*` (4), `relatorioEfetuarAnaliseParcelamento*`, `relatorioEmitirContratoParcelamentoPorCliente*`, `relatorioEmitirExtratoContratoParcelamentoCliente*`, `relatorioEmitirComprovantePagamentoContratoParcelamento`, `relatorioDocumentosParcelamentoLojaVirtual` | |
| **Negativação** | `relatorioAcompanhamentoClientesNegativados*` (5), `relatorioNegativacoesExcluidas`, `relatorioNegativadorResultadoSimulacao`, `relatorioAcessoSPC` | |
| **Financeiro/contábil** | `relatorioResumoDevedoresDuvidosos`, `relatorioSaldoContasAReceberContabil`, `relatorioEvolucaoContasAReceberContabil`, `relatorioParametrosContabeis{Arrecadacao,ContasAReceber,Faturamento}`, `relatorioContasBaixadasContabilmente`, `relatorioResumoReceita*`, `relatorioReceitasAFaturar*`, `relatorioDocumentosAReceber`, `relatorioVolumesConsumidosNaoFaturados`, `relatorioAnalisePerdasCreditos`, `relatorioJurosMultasDebitosCancelados*` | |
| **Certidões e declarações** | `relatorioCertidaoNegativa*` (5; CAEMA), `relatorioDeclaracaoQuitacaoAnualDebitos*` (3; CAEMA/CAERN), `relatorioContratoPrestacaoServico*` | |
| **Faturamento, consumo e leitura** | `relatorioResumoFaturamento*`, `relatorioPosicaoFaturamento`, `relatorioAnaliseFaturamento*`, `relatorioAcompanhamentoFaturamento`, `relatorioHistoricoFaturamentoImovel*`, `relatorioAnormalidade*`, `relatorioHistograma*` (7), `relatorioImoveis*` | fora da minha fatia (outros levantamentos) |
| **Cadastro, atendimento, OS, qualidade** | `relatorioDadosCadastrais*`, `relatorioConsultarRegistroAtendimento*`, `relatorioOrdemServico*`, `relatorioQualidadeAgua*`, `relatorioBoletim*` | fora da fatia |

Observação: várias famílias existem **uma vez por empresa** (CAEMA/CAER/CAERN/COSANPA/JUAZEIRO/CAEMA etc.), porque o layout impresso é da companhia, não a regra. No SIGSAN: **um relatório com *template* por tenant**.

### 6.1 Os ~20 mais relevantes para um MVP

Campos entre parênteses vêm dos `<field>` do `.jrxml` (fato). Entrega de uma linha por relatório:

| # | Relatório | O que entrega |
|---|---|---|
| 1 | `relatorio2ViaConta` (+`BoletoBancario`) | segunda via da conta com consumo, leituras, histórico de consumo, vencimento, código de barras/boleto |
| 2 | `relatorioExtratoDebito` (+`Cliente`, `BoletoBancario`) | extrato dos débitos do imóvel/cliente (contas, serviços, guias) com valor de acréscimos e código de barras para quitar |
| 3 | `relatorioEmitirGuiaPagamento` (+`FichaCompensacao`, `Parcelamento`) | guia de pagamento (serviço, entrada de parcelamento) com ficha de compensação |
| 4 | `relatorioGerarRelacaoDebitos` (+Contas/Guias/Resumido/Totais) | relação de débitos por imóvel/localidade com situação de ligação e consumo médio |
| 5 | `relatorioImoveisFaturasAtrasoDescritasCliente` / `…AgrupadasLocalizacao` | imóveis com faturas em atraso, valor sem e com encargos, quantidade de faturas |
| 6 | `relatorioGerarCurvaABCDebitos` | curva ABC de devedores por faixa de valor e categoria |
| 7 | `relatorioAcompanhamentoAcoesCobranca` | quantidade e valor por ação × situação × empresa × gerência/unidade/localidade, por período |
| 8 | `relatorioDocumentosAReceber` | aging de documentos a receber por categoria, gerência, unidade, localidade, faixa |
| 9 | `relatorioResumoArrecadacao` | arrecadação por tipo de lançamento/recebimento/item contábil, por gerência/unidade/município, mês |
| 10 | `relatorioDadosDiariosArrecadacaoArrecadador` (+Forma, Categoria, Localidade…) | arrecadação diária por arrecadador/forma/categoria com quantidade, valor, float e tarifa |
| 11 | `relatorioAcompanhamentoMovimentoArrecadadores` (+`PorNSA`) | acompanhamento dia a dia dos arquivos recebidos por banco e forma (valor do dia e acumulado) |
| 12 | `relatorioAnaliseAvisosBancarios` | conciliação: arrecadação calculada × informada, devolução, acertos, deduções, diferença e diferença acumulada |
| 13 | `relatorioPagamentos` / `relatorioDevolucoes` | listagem de pagamentos/devoluções com tipo de débito, situação atual, arrecadador, valor da guia |
| 14 | `relatorioDocumentosNaoAceitos` | registros de arquivo bancário rejeitados/não aceitos (tratamento de exceção) |
| 15 | `relatorioRelacaoParcelamento(Analitico)` / `relatorioParcelamento` | parcelamentos por período/situação/perfil com valor, entrada, prestações; termo de parcelamento |
| 16 | `relatorioNotificacaoDebito` / `relatorioReavisoDeDebito` | carta de notificação de débito com código de barras para pagamento |
| 17 | `relatorioOrdemCorte` / `relatorioReligacaoClientesInadiplentes*` | ordens de corte/supressão e de religação de inadimplentes |
| 18 | `relatorioAcompanhamentoClientesNegativados` (+Sintético) | negativados: valor negativado/pago/parcelado/cancelado/pendente, motivos de rejeição, dados do parcelamento |
| 19 | `relatorioResumoDevedoresDuvidosos` + `relatorioSaldoContasAReceberContabil` | PDD e saldo contábil de contas a receber por gerência/unidade/categoria |
| 20 | `relatorioDeclaracaoQuitacaoAnualDebitos` / `relatorioCertidaoNegativa` | declaração anual de quitação e certidão negativa de débitos (exigência regulatória, §7) |

---

## 7. Matriz regulatória e bancária (Brasil)

Legenda "No GSAN": **S** = coberto (fato), **P** = parcial, **N** = ausente. "Fonte" distingue o que vi no código de **[EXT]** (precisa validar).

| Tema | Requisito (resumo) | No GSAN | Fonte / evidência | Falta para o SIGSAN |
|---|---|---|---|---|
| Arrecadação com código de barras de convênio (FEBRABAN) | 44 posições, produto 8, DV, valor, id da empresa, campo livre | **S** | `distribuirDadosCodigoBarras*` | DV módulo 10/11 correto (testes dourados), identificação de empresa por tenant |
| Arquivo de retorno de arrecadação FEBRABAN 150 (A/G/Z) | NSA sequencial, trailer com total, data de crédito, forma, tarifa | **S** | `RegistroHelperCodigoA/G/Z`, `validarArquivoMovimentoArrecadador` | amostras reais dos bancos (BB, CAIXA, Bradesco, Itaú, Santander, Sicredi) |
| Débito automático FEBRABAN 150 | opção (B), ocorrência (C), envio (E), retorno (F) | **S** | `RegistroHelperCodigoB/C/E/F` | autorização formal do cliente (consentimento), reapresentação |
| CNAB 240 retorno de cobrança | segmentos T/U | **S** | `RegistroHelperCodigo3T/3U` | mapeamento dos códigos de ocorrência por banco |
| CNAB 400 retorno (carteira registrada) | tipo 7 | **P** | `RegistroFichaCompensacaoTipo7Helper` | **[INF]** só um banco |
| **CNAB 444 / boleto padrão novo** | layout unificado FEBRABAN **[EXT]** | **N** | — | decidir se vale suportar; muitos bancos já migram |
| Boleto bancário, ficha de compensação | 44 posições, linha digitável 47, nosso número, carteira | **S** | `util/CodigoBarras.java` | **fator de vencimento com novo ciclo a partir de 22/02/2025 [EXT]** |
| Boleto registrado / **NPC (CIP) / DDA** | registro online na plataforma de cobrança; DDA; boleto sem registro não é compensado **[EXT]** | **P/N** | `BoletoInfo` (formulário do BB) | **gateway de registro**; nunca passar PII em query string |
| **PIX** | QR dinâmico (cob/cobv), **boleto híbrido (BolePix)**, baixa por webhook, conciliação por `txid`/`endToEndId` **[EXT]** | **N** | `grep` por "pix" = 0 resultados | módulo novo: PSP adapter, webhook assinado, conciliação, devolução (`DevolucaoSituacao`) |
| Cartão de crédito/débito | parcelamento da entrada, extrato de operadora | **P** | `RegistroCartaoCredito*`, `PagamentoCartaoDebito` | gateway/adquirência moderna (tokenização, PCI); **não armazenar PAN** |
| Acréscimos por impontualidade | multa, juros de mora, correção | **S** | `IndicesAcrescimosImpontualidade` | travas legais **[EXT]**: multa ≤ 2 % (CDC art. 52 §1º), juros moratórios conforme contrato/lei; **parametrizar e auditar** |
| Notificação prévia à negativação | CDC art. 43 §2º; Súmula 359 STJ **[EXT]** | **P** | cartas ao SPC/Serasa, critério de recebimento de carta | **prova de envio/recebimento**, prazo mínimo, registro de comunicação |
| Prazo máximo de inscrição | 5 anos (CDC art. 43 §1º) **[EXT]** | **N** (não vi) | — | job de expiração automática |
| Exclusão da negativação ao pagar | imediata **[EXT]** | **S** | `excluirNegativacaoOnLine` | SLA e auditoria |
| LGPD na negativação e na cobrança | base legal (execução de contrato/legítimo interesse), minimização, direitos do titular **[EXT]** | **N** | — | **integrar `compliance/lgpd`**: registro de operação de tratamento, retenção, direito de acesso |
| **Corte por inadimplência** | notificação formal prévia; regras especiais p/ saúde, educação e baixa renda (Lei 11.445/2007 art. 40, redação da Lei 14.026/2020) **[EXT]** | **P** | ações de aviso de corte, carta tarifa social, paralisações | calendário de prazos legais por tipo de usuário, bloqueio de OS de corte para classes protegidas |
| **Declaração anual de quitação de débitos** | Lei 12.007/2009 **[EXT]** | **S** | `relatorioDeclaracaoQuitacaoAnualDebitos`, batch 1303/1305 | emitir até 31/05 do ano seguinte por tenant; PDF e portal |
| Prescrição de débitos | prazos por natureza (tarifa de água/esgoto) **[EXT]** — validar com jurídico | **S** | `Prescricao`, batches 1350/1404 | parametrizar prazo por tenant e esfera, trilha de decisão |
| Parcelamento de débitos | juros, entrada, desconto, resoluções internas | **S** | §3.6 | RD versionada com vigência; limites legais |
| Superendividamento | Lei 14.181/2021 **[EXT]** | **N** | — | estudo; pode afetar renegociação de consumidor pessoa física |
| Contabilidade | partida dobrada, plano de contas, períodos | **P** | `LancamentoContabil(Item)` | razão e fechamento; exportação SPED/ECD **[EXT]** é do sistema contábil da companhia |
| Retenções na fonte (órgãos públicos) | IR, CSLL, COFINS, PIS (Lei 10.833/2003 art. 30 e 34 **[EXT]**) | **P** | itens `IMPOSTO_RENDA/COFINS/CSLL/PIS_PASEP`, `ContaImpostosDeduzidos` | calcular/reter; DIRF/EFD-Reinf **[EXT]** |
| PDD / perdas de crédito | CPC 48 **[EXT]** | **P** | PDD por meta de baixa | matriz de aging |
| **Reforma tributária (IBS/CBS)** | EC 132/2023 e LC 214/2025 **[EXT]** | **N** | — | monitorar (há skill `monitor-regulatorio` no ambiente) |
| Tarifa social | Lei 12.212/2010 **[EXT]** | **P** | batches de bônus/retirada/carta | fora desta fatia |

---

## 8. Esboço de modelo de dados para o SIGSAN

Premissa do SIGSAN (Diário): PostgreSQL, **um schema por tenant**, `tenant_id UUID` em toda tabela, RLS por `app.current_tenant`, `BaseEntity/TenantAwareEntity` (`created_at, updated_at, version, created_by, updated_by`). **Dinheiro: `NUMERIC(14,2)`; referências: `INT` AAAAMM com `CHECK`; polimorfismo de documento: `doc_type` + `doc_id` (sem FK dura, com `CHECK` do tipo e índice).**

**Nota de nomenclatura (confirmar):** no pedido, `collection` e `dunning` são módulos separados. Assumi **`collection` = arrecadação/recebimentos** e **`dunning` = cobrança de inadimplência (ações, negativação, parcelamento)**. Se `collection` for "cobrança", inverter.

### 8.1 `commercial/sigsan-collection` (arrecadação)

```
payment_agent(id, tenant_id, bank_code, name, customer_id, active)            -- arrecadador
agent_contract(id, agent_id, contract_no, convenio, start, end, closed_at,
               iss_charged bool, parcel_id_digits smallint(8|9),
               nsa_barcode_return int, nsa_debit_return int, nsa_debit_send int, nsa_ficha_return int,
               fee_account_id, deposit_account_id)
agent_contract_fee(contract_id, channel, fee_value, fee_pct, float_days)       -- PK (contract_id, channel)
collection_channel(code, name)       -- GUICHE, DEBITO_AUTOMATICO, DEPOSITO, FICHA, CARTAO_CRED, CARTAO_DEB, PIX, ...
barcode_layout(id, tenant_id, payment_type, valid_from, valid_to, fields jsonb)  -- substitui forks por empresa
bank(code,name), bank_branch(id,bank_code,branch_code,...), bank_account(id,branch_id,number,gl_account_id)

remittance_file(id, agent_id, layout, nsa, issued_at, record_count, total_amount, status, sha256, storage_uri)
remittance_item(id, file_id, line_no, raw_line text, accepted bool, error_code, error_msg,
                doc_type, doc_id, unit_id, amount)                              -- guarda a linha crua

payment(id, tenant_id, amount, excess_amount, paid_on, ref_payment int, ref_collection int,
        channel, agent_id, notice_id, remittance_item_id, unit_id,
        doc_type, doc_id, aggregator_doc_type,
        status smallint, previous_status smallint, expunged bool, processed_at, idempotency_key unique)
payment_status_event(payment_id, from_status, to_status, reason, job_run_id, at)   -- trilha da classificação
refund(id, amount, refund_on, ref_collection, status, notice_id, payment_id, refund_voucher_id)
refund_voucher(id, issued_on, valid_until, amount, reason)
bank_notice(id, agent_id, channel, bank_account_id, expected_on, settled_on, ref_collection,
            amount_collected_calc, amount_collected_inf, refund_calc, refund_inf,
            settled_amount, posted_amount, credit_debit char, seq int, remittance_id)
bank_notice_adjustment(id, notice_id, credit_debit, kind('ARREC'|'DEVOL'), amount, on)
bank_notice_deduction(notice_id, kind('CPMF'|'TARIFA'|'ENCONTRO'), amount)
accumulated_difference(notice_id, ref_collection, amount)
auto_debit_enrollment(id, unit_id, branch_id, bank_customer_ref, opted_on, included_on, excluded_on)
auto_debit_movement(id, enrollment_id, invoice_id, due_on, amount, sent_at, nsa_send, returned_at, nsa_return, return_code)
daily_collection(day, agent_id, channel, category, profile, unit/locality, qty_payments, amount, float_days, fee)
collection_tolerance(tenant_id, name, value)   -- 'CLASSIFY_TOLERANCE'=0.03 ; 'ADJUST_TOLERANCE'=2.00
```

Máquina de estados `payment.status`: manter os 14 valores do §2.5 como **enum nomeado** (`CLASSIFIED, DUPLICATE, DOC_NOT_FOUND, EXCESS, TO_WRITE_DOWN, AMOUNT_MISMATCH, OPEN, DUPLICATE_REFUNDED, TO_ACCOUNT, DOC_PRESCRIBED, DOC_PARCELED, DOC_CANCELED, DOC_PROCESSING_ERROR, CREDIT_RECOVERY_CANCELED, CREDIT_RECOVERY_DUPLICATE`), **sem reutilizar os números do GSAN**.

### 8.2 `commercial/sigsan-dunning`

```
dunning_group(id, name, auto_exec, owner_email)
dunning_cycle(id, group_id, ref int)
dunning_action(id, name, order_no, mandatory, repeatable, predecessor_id, alt_predecessor_id,
               min_days_after_predecessor, validity_days, due_days, closing_days, service_type_id,
               doc_type, criteria_id, requires_water_status, requires_sewer_status, flags jsonb)
dunning_activity(id, name, kind('SIMULATE'|'ISSUE'|'CLOSE'|'CLOSE_WO'), predecessor_id, exec_days)
dunning_schedule(id, cycle_id, action_id, activity_id, planned_on, commanded_at, done_at, doc_qty, doc_amount, max_docs)
dunning_command(id, action_id, activity_id, filters jsonb, requested_by, commanded_at, done_at, doc_qty, amount)  -- eventual
dunning_criteria(id, name, valid_from, flags jsonb, min_paid_pct_parceled, ...), dunning_criteria_line(criteria_id, profile, category, min/max amount, min/max invoices, ...)
dunning_document(id, number, action_id, schedule_id|command_id, unit_id, issued_at, amount, discount, fee, surcharge,
                 doc_type, action_status, debt_status, predecessor_id, not_delivered_reason, rd_id, ...)
dunning_document_item(document_id, doc_type, doc_id, amount, surcharge)
dunning_skip_reason(code, name)  ;  dunning_skip(unit_id, action_id, schedule_id, reason_code, at)   -- por que NÃO gerou
collection_special_status(id, type, name, blocks_actions, blocks_surcharge, blocks_wo, ...)  ;  unit_special_status(unit_id, status_id, from_ref, to_ref, by, note)
surcharge_index(ref int pk, fine_pct, interest_pct, monetary_factor, fine_cap_pct, interest_cap_pct, fine_monthly bool, interest_monthly bool)
dunning_param(tenant_id, grace_business_days, correction_months, tariff_update bool, price_table bool, ...)

settlement_rd(id, number, subject, valid_from, valid_to, flags jsonb)             -- "resolução de diretoria"
settlement_profile(id, rd_id, subcategory, unit_status, unit_profile, category, disc_fine_pct, disc_interest_pct,
                   disc_monetary_pct, min_installment_pct_of_min_tariff, entry_min flag, max_resettlements, cash_disc_pct, cash_until, ...)
settlement_installment_rule(id, profile_id, max_installments, interest_rate, min_entry_pct, entry_pct_of_min_tariff,
                            suggested_entry_pct, resettle_pct, special_max_installments, use_avg_invoice, use_last_invoice)
settlement_value_band(rule_id, up_to_amount, entry_pct)
settlement_ref_discount(ref_min, ref_max, discount_pct)
settlement_age_discount(profile_id, min_months, pct_no_reconnect, pct_reconnect, revision_reason_id)
settlement_idle_discount(profile_id, max_idle_months, pct_no_reconnect, pct_reconnect)
settlement(id, unit_id, customer_id, rd_id, profile_id, status('NORMAL','UNDONE','DONE','CANCELED'), created_at,
           debt_updated, fine, interest, monetary, disc_age, disc_idle, disc_ref, disc_surcharge, disc_sanction, disc_social_tariff,
           entry, installment_count, installment_value, interest_rate, paid_consecutive, canceled_at, cancel_reason_id)
settlement_item(settlement_id, doc_type, doc_id)
customer_settlement_contract(...)   -- contrato por cliente (grandes clientes), com prestações próprias
infraction_notice(id, unit_id, work_order_id, status, issued_on, appeal_start, appeal_end, debt_type_id, installments)
prescription_rule(tenant_id, sphere, ref_limit)  ;  prescription_run(id, ref, sphere, run_at, count, amount)

credit_bureau(id, code, customer_id)  ;  bureau_contract(id, bureau_id, number, convenio, nsa_send, nsa_return, fee_per_inclusion,
                                                           contracted, sent, inclusion_deadline_days, record_size)
bureau_command(id, bureau_id, simulation bool, low_income bool, public_body bool, planned_on, run_at, count, amount)
bureau_criteria(id, command_id, filters jsonb, min_amount, max_amount, max_inclusions, ...)
bureau_movement(id, bureau_id, command_id, seq, sent_at, returned_at, record_count, total)
bureau_record(id, movement_id, unit_id, customer_id, doc_hash, accepted bool, return_reason_id, definitive bool)
bureau_record_item(record_id, doc_type, doc_id, amount, paid, canceled, status_after_exclusion)
bureau_notice_proof(record_id, channel, sent_at, delivered_at, evidence_uri)    -- NOVO: prova da notificação prévia
outsourced_command(...), outsourced_invoice(command_id, invoice_id, fee_pct, sent_on, removed_on, payment_valid)
```

### 8.3 `commercial/sigsan-accounting`

```
gl_account(id, code, name, is_line, cost_center_required, group_id)
posting_origin(id, code)            -- BILLING, COLLECTION, DOUBTFUL, BANK_NOTICE
posting_type(id, name, parent_id, accounting_code, total bool, summarized bool, printable bool)
posting_item(id, name, accounting_item bool)  ;  posting_accounting_item(id, name, seq)
posting_rule(tenant_id, origin, posting_type, posting_item, accounting_item, category,
             debit_account_id, credit_account_id, history_template)          -- = tabelas de parâmetros contábeis do GSAN
period_summary_billing/collection/doubtful(ref, locality, category, type, item, accounting_item, amount)  -- views materializadas
journal_entry(id, origin, ref, locality, receipt_type, created_by, job_run_id)  unique(tenant,origin,ref,locality)
journal_line(entry_id, debit_credit, account_id, amount, history, third_party, posted_on)
accounting_export(id, system('TXT'|'SAP'|'TOTVS'|...), ref, file_uri, status, checksum)
receivable_snapshot(ref, locality, category, type, item, amount, public/private, short/long term)
doubtful_param(ref, amount_to_write_off, amount_written_off, processed_at)  ;  doubtful_param_item(param_id, collection_status_id, limit_amount, months)
doubtful_summary(ref, locality, category, type, item, amount)
period_close(domain, ref, closed_at, closed_by)                    -- imutabilidade de período
```

### 8.4 Orquestração (`shared/sigsan-core` ou novo `sigsan-batch`)

```
job_run(id, tenant_id, job, ref int, group_key, status, requested_by, started_at, finished_at, params jsonb, spring_job_execution_id)  unique(tenant,job,ref,group_key,run_seq)
step_run(id, job_run_id, step, seq, status, started_at, finished_at, error)
unit_run(id, step_run_id, unit_type, unit_key, status, started_at, finished_at, error)   unique(step_run_id, unit_type, unit_key)
```

---

## 9. Licença, contaminação e armadilhas do GSAN

**Licença (fato / inferência):**

- Não encontrei `LICENSE`/`COPYING`/`NOTICE` na raiz e o `Arrecadador.java` aberto **não tem cabeçalho de licença**. O `README.md` afirma "software livre … Ministério das Cidades … Portal do Software Público Brasileiro". O SPB distribui, em geral, sob **GPL v2 [EXT]** — se for o caso, qualquer cópia de trechos de código **contaminaria** o SIGSAN com copyleft e obrigaria a abri-lo.
- **Recomendação:** tratar **todo o GSAN como referência de domínio apenas**; reescrever por especificação (casos de uso `[UCxxxx]` descrevem o "o quê"), nunca colar código. Usar os catálogos de **conceito** (estados, motivos, tipos) com **nomes e numeração próprios**.
- **Layouts FEBRABAN/CNAB** são especificações públicas dos bancos; implementar a partir do **manual oficial**, não do código GSAN.
- `lib/codigo-barras/codigo-barra.jar` e demais jars (`lib/*`) têm licenças de terceiros; não reutilizar.
- Os comentários citam **nomes de analistas e desenvolvedores** (PII de terceiros); não copiar.
- **Risco de "contaminação por proximidade":** não colar nomes de colunas `abcd_*` do GSAN em migrations do SIGSAN; nomes de domínio genéricos são seguros.

**Armadilhas a NÃO replicar (fato):**

1. `subtrairNumeroDiasDeUmaData` retorna nova data e o chamador **descarta** o resultado (`calcularAcrescimoPorImpontualidade`): a data de cálculo sem pagamento nunca recua os dias de carência.
2. `Funcionalidade.GERAR_DADOS_DIARIOS_ARRECADACAO = 457` **e** `ENCERRAR_ATIVIDADE_ACAO_COBRANCA = 457` (mesmo id).
3. Constantes de `DocumentoTipo` colidem (16, 17).
4. Tarefa Java **serializada em `bytea`** (`fuin_parametros`): quebra com qualquer mudança de classe.
5. **Convênio bancário fixo no código** (`registrarBoleto`) e **PII (CPF, nome, endereço) em *query string*** de URL para o banco (`formatarLinkBB`): proibido pela LGPD.
6. **Uma funcionalidade por vez** (`break` em `verificarProcessosIniciados`), polling a cada minuto.
7. Classes de 50–60 mil linhas; subclasses por empresa; cálculo de dinheiro com `BigDecimal` misturando `HALF_UP` e `HALF_DOWN` conforme o módulo (parcelamento usa `HALF_DOWN`, acréscimos `HALF_UP`): **definir arredondamento por regra e testar**.
8. `marcarProcessosInterrompidos` **cancela tudo** no boot.
9. Resumos recalculados com "apagar e inserir" (janelas sem dado).
10. Em `calcularAcrescimoPorImpontualidade` a multa usa `valor − multasJáCobradas`, mas juros usam `valor` cheio; documentar a intenção antes de copiar o comportamento.

**Lacunas da minha leitura (declaradas):** regra exata das parcelas bônus; leitura linha a linha de `processarPagamentoAMenorOUAMaior`; layout campo a campo (posições) dos arquivos A/G/Z/B/C/E/F/240/400 (só nomes e semântica); ordem real do fechamento (seed externo); `gerarMovimentoDebitoAutomaticoBanco`; módulo de cartão em detalhe; `ControladorCobrancaPorResultado`.

---

## Recomendações para o SIGSAN

Estado do SIGSAN (Diário de Borda, 09/10/2026): `commercial/*` e `operations/*` **vazios, sem POM**; não há `@SpringBootApplication`/REST; o que existe é multi-tenancy testada (RLS, provisionamento, `TenantFilter`) e `AuditLog`. O schema de tenant tem 8 tabelas (`consumer_units`, `service_orders`, `documents`…), **nenhuma de faturamento/arrecadação/cobrança**. Tudo abaixo é, portanto, **construção nova** sobre a fundação de tenant/RLS.

### P0 — fundação que, se errada, contamina o resto

| # | O que absorver | Módulo-alvo | Existe hoje | Falta | Risco / esforço |
|---|---|---|---|---|---|
| P0.1 | **Motor de jobs** idempotente multi-tenant: `job_run/step_run/unit_run`, execução por (tenant, job, referência, grupo), partições por unidade (localidade/setor/rota), retomada que pula unidades concluídas, reinício, autorização, precondições, período fechado (§5.4) | `shared/sigsan-core` (+ `platform/sigsan-tenant` para GUC) | `TenantContextHolder`, RLS, GUC por transação, Kafka no compose | Spring Batch + Quartz JDBC, helper que reaplica `set_config` por *chunk*, tabelas e API de operação | Médio-alto (se vazar tenant em *chunk*, é incidente de segurança) / **M** |
| P0.2 | **Classificação de pagamentos** (14 estados, tolerâncias parametrizáveis 0,03 e 2,00, duplicidade, a maior/menor, "a contabilizar") com **simulador puro e testes dourados** | `commercial/sigsan-collection` | nada | modelo `payment`, `payment_status_event`, serviço de regra puro (sem I/O), 30+ casos de teste calculados à mão | **Alto** (dinheiro) / **M** |
| P0.3 | **Acréscimos por impontualidade** (multa, juros, correção, carência em dias úteis municipais, débito automático isento) como serviço puro versionado por índice mensal; **corrigir** o bug do §9 | `commercial/sigsan-billing` (ou `dunning`) | nada | tabela `surcharge_index`, calendário de feriados por município, testes dourados, travas legais configuráveis | Alto / **S–M** |
| P0.4 | **Código de barras de arrecadação (44)**, **linha digitável**, **fator de vencimento com o ciclo novo**, DV módulo 10/11 e `barcode_layout` por tenant (substitui forks por empresa) | `commercial/sigsan-collection` | nada | gerador e *parser*, vetores de teste do manual FEBRABAN | Médio / **S** |
| P0.5 | **Núcleo contábil por evento**: `posting_rule` (conta débito/crédito/histórico por tipo × item × categoria), `journal_entry/line` com chave única por (origem, período, localidade), `period_close` | `commercial/sigsan-accounting` | nada | modelo, resumos como *materialized views*, exportação TXT/CSV adaptável | Médio / **M** |

### P1 — MVP funcional de arrecadação e cobrança

| # | O que absorver | Módulo-alvo | Existe | Falta | Risco / esforço |
|---|---|---|---|---|---|
| P1.1 | **Importadores de retorno bancário**: FEBRABAN 150 (A/G/Z), CNAB 240 (T/U), com validação de **NSA**, trailer, convênio; guardar linha crua; `bank_notice` + **conciliação aberto/fechado** (fórmula do §2.6) | `collection` | nada | parsers com **arquivos reais anonimizados**, estados de rejeição, relatório de documentos não aceitos | Alto (variações por banco) / **L** |
| P1.2 | **Dunning**: ação × atividade × critério × cronograma (+eventual), documento/itens, **catálogo de motivos de não geração** (§3.3), situação especial de cobrança | `commercial/sigsan-dunning` + integração com `operations/sigsan-field` (OS de corte/religação) | `service_orders` básico | modelo, motor de seleção por critério, geração de OS | Médio / **L** |
| P1.3 | **Parcelamento** (perfil × RD × faixas × entrada × prestações × Price/composto) com simulador puro e testes; desfazer por entrada não paga; débitos a cobrar de juros | `dunning` (ou `billing`) | nada | decidir **juros compostos vs Tabela Price** (§3.6) e arredondamento; levantar a **regra de parcelas bônus** | Alto / **L** |
| P1.4 | **Boleto/ficha de compensação** com registro online + **PIX (cob/cobv) e boleto híbrido**; baixa por webhook | `collection` + `compliance/sigsan-integration` | nada | adapter de PSP, webhook assinado, idempotência, conciliação por `txid` | Alto (integração e segurança) / **L** |
| P1.5 | **Relatórios MVP** (§6.1: 2ª via, extrato de débitos, relação de débitos, faturas em atraso, curva ABC, acompanhamento de ações, documentos a receber, resumo e dados diários de arrecadação, análise de avisos, pagamentos/devoluções, parcelamento, notificação, ordem de corte, quitação anual) | `commercial/sigsan-portal` + `billing` | nada | *templates* por tenant, motor de relatório (JasperReports ou PDF direto), views de apoio | Médio / **L** |
| P1.6 | **Declaração anual de quitação** e **certidão negativa** (Lei 12.007/2009 **[EXT]**) | `portal` + `compliance` | nada | jobs de emissão, assinatura, portal | Baixo-médio / **S** |

### P2 — profundidade e conformidade

| # | O que absorver | Módulo-alvo | Risco / esforço |
|---|---|---|---|
| P2.1 | **Negativação SPC/Serasa** (contrato, comando, critério, movimento, retorno, confirmação, exclusão online) **com prova de notificação prévia e retenção** | `dunning` + `compliance/sigsan-lgpd` | Alto (CDC/LGPD) / **L**; **recomendo só depois de P1** |
| P2.2 | **Débito automático** (B/C/E/F) e cartão | `collection` | Médio / **M** |
| P2.3 | **Cobrança por resultado** (empresa terceirizada, remuneração %) | `dunning` | Médio / **M** |
| P2.4 | **PDD**: manter a **meta de baixa** (GSAN) e **adicionar matriz de aging/perda esperada** (CPC 48 **[EXT]**) | `accounting` | Médio / **M** |
| P2.5 | **Contas a receber contábil, receita a faturar, aging (`DocumentosAReceber`)** como views materializadas; **BIG** substituído por TimescaleDB/views | `accounting` + `intelligence` | Baixo / **M** |
| P2.6 | **Auto de infração** e **prescrição** (por esfera de poder) | `dunning` / `billing` | Baixo-médio / **S–M** |
| P2.7 | Retenções na fonte de órgãos públicos (IR/CSLL/COFINS/PIS) | `billing` + `accounting` | Médio / **M** |
| P2.8 | Dívida ativa/protesto (não existe no GSAN) | `dunning` | Alto / **L**, só com demanda do cliente |

### Ordem sugerida de execução

1. P0.1 e P0.4 em paralelo (independentes); 2. P0.3 → P0.2 (a classificação consome o cálculo de acréscimos); 3. P0.5; 4. P1.1 + P1.5; 5. P1.2 → P1.3; 6. P1.4 (PIX/boleto) pode andar em paralelo com 5; 7. P2.

### Perguntas para o Daniel

1. `collection` = arrecadação e `dunning` = cobrança, como assumi?
2. Quais bancos/convênios os primeiros clientes usam? (define quais parsers CNAB entram primeiro e se PIX é obrigatório no MVP)
3. A regra de **parcelas bônus** existe nos clientes-alvo? Preciso levantar com quem opera o GSAN para escrever os testes.
4. O primeiro cliente fecha contabilidade em sistema externo? Qual formato de integração (TXT, API, SPED)?
5. Alguém do jurídico valida prazos de **prescrição**, **notificação prévia** e **limites de multa/juros** antes de codificarmos defaults?
