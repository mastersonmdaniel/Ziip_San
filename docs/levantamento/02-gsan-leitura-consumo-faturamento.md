# Levantamento 02 — GSAN: micromedição/leitura, consumo e faturamento

Fatia: pacotes `gcom/micromedicao`, `gcom/faturamento` e correlatos (`cadastro/imovel`, `cadastro/sistemaparametro`, `cobranca` só no cálculo de acréscimos, `batch`, `reports`).
Fonte: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN` (somente leitura). Última data de commit do clone: 30/08/2018 (2.166 commits).
Data do levantamento: 11/10/2026.

> **Como ler.** "Fato" = visto no código/mapeamento citado. "Inferência" = dedução minha, marcada. Nada foi executado contra o GSAN; os casos de teste da seção 10 foram **calculados à mão a partir da leitura do código** e precisam ser confrontados com o motor do SIGSAN (não com o GSAN).
> Os arquivos-fonte estão em latin1 (acentos aparecem quebrados em leitura UTF-8).
> Valores monetários, alíquotas e parâmetros nos exemplos são **fictícios/ilustrativos**.

---

## 0. Resumo executivo

1. O GSAN separa três camadas de dado de consumo: `medicao_historico` (o que foi lido), `consumo_historico` (o que será faturado, com tipo e anormalidade) e `conta` (o que foi cobrado). Essa separação é o que permite auditar "por que cobrou X".
2. O consumo faturado **não** é a diferença de leituras: passa por uma cascata (leitura → anormalidade de leitura → anormalidade de consumo estouro/alto/baixo → ajuste por dias → situação especial de faturamento → consumo mínimo fixado → múltiplo de economias → rateio de condomínio → % de esgoto). Cada passo grava `ConsumoTipo` e `ConsumoAnormalidade`.
3. Tarifa = `consumo_tarifa` → `vigência` (data) → `categoria/subcategoria` (consumo mínimo + tarifa mínima) → `faixas` (progressivas). Esgoto = mesma tabela de tarifa aplicada ao consumo de esgoto, multiplicada por um percentual (fator arredondado a 2 casas).
4. O faturamento é por **grupo** (`faturamento_grupo`) com **cronograma mensal** de atividades por rota; o processamento é fan-out por rota (fila JMS/MDB, uma unidade de processamento por rota, reinício possível), paginado de 3.000 imóveis.
5. Há três regras de negócio de alto valor que um motor novo precisaria replicar ou decidir conscientemente: consumo mínimo/tarifa mínima por economia, "consumo múltiplo de economias" e estouro/alto/baixo consumo parametrizados por categoria com escalonamento em 3 meses.
6. O código legado tem forks por concessionária (`ControladorFaturamentoCAERNSEJB`, `...CAERS`, `...COSAMA`, `...COSANPA`, `...COMPESA`, `...CAEMA`, `...JUAZEIRO`) e lookup `getLocalHomePorEmpresa(...)`: o produto se tornou um conjunto de variantes. Para SaaS multi-tenant a lição é projetar **pontos de extensão por tenant** (estratégia de cálculo), não forks.
7. Qualidade do legado: classes-deus (`ControladorMicromedicao` 40 mil linhas, `ControladorFaturamentoFINAL` 69,6 mil linhas, `Fachada` com ~2.200 métodos), regras duplicadas, `System.out.println` em caminho de cálculo e vários arredondamentos ad hoc. **Extrair regras, não estrutura.**
8. Risco de licença: **não há arquivo LICENSE no clone**; o README o descreve como software livre do Ministério das Cidades distribuído pelo Portal do Software Público Brasileiro. Licença exata **não confirmada** (ver seção 11).

---

## 1. Mapa de código

| Área | Caminho | Observação (fato) |
|---|---|---|
| Controlador de micromedição | `src/gcom/micromedicao/ControladorMicromedicao.java` | ~40.078 linhas, 1,4 MB. Interface `IControladorMicromedicao`. Variantes por empresa: `ControladorMicromedicao{CAEMA,CAERN,CAERS,COMPESA,COSAMA,COSANPA,JUAZEIRO}SEJB` |
| Repositório HQL | `micromedicao/RepositorioMicromedicaoHBM.java` (+`...PostgresHBM`) | ~887 KB |
| Controlador de faturamento (núcleo) | `src/gcom/faturamento/ControladorFaturamentoFINAL.java` | ~69.602 linhas, 2,5 MB. `ControladorFaturamento extends ControladorFaturamentoFINAL` (15.793 linhas) e as variantes por empresa herdam dele |
| Controladores "novos" (mais limpos) | `faturamento/controladores/ControladorAnaliseGeracaoConta`, `ControladorDebitoACobrar`, `ControladorRetificarConta` (1.758 linhas) | Padrão melhor: regra em classe pequena |
| Utilitário de créditos | `faturamento/FaturamentoUtil.java` (101 linhas) | Única regra bem isolada e **com teste** (`test/gcom/faturamento/TesteAtualizacaoCreditosARealizarPreFaturamento.java`) |
| Batch | `src/gcom/batch/faturamento/*` e `batch/micromedicao/*` | `TarefaBatch*` (despacho) + `Batch*MDB` (execução por rota) |
| Fachada | `src/gcom/fachada/Fachada.java` | 40.222 linhas, ~2.219 métodos públicos: god-object que delega aos controladores |
| Relatórios | `reports/*.jrxml` (500 arquivos): `relatorio2ViaConta*.jrxml`, `relatorioConta*.jrxml`, `relatorioBoletimMedicao.jrxml`, `relatorioFaixasFalsasLeitura.jrxml`, `relatorioAnormalidadeConsumo*.jrxml`, `relatorioEmitirGuiaPagamento*.jrxml`... | Só conceito de layout interessa |
| Mapeamento | `*.hbm.xml` ao lado de cada entidade (schemas `micromedicao.*`, `faturamento.*`, `cadastro.*`, `atendimentopublico.*`) | Fonte confiável do modelo |
| Testes | `test/gcom/faturamento/*` (5 arquivos, ~280 linhas) | Cobertura mínima; os testes de crédito são úteis como oráculo |

Etapas do código legado com número de caso de uso (usados nos comentários): UC0068 consumo médio, UC0083 gerar dados para leitura, UC0086 faixa de leitura esperada, UC0101 consistir leituras e calcular consumos, UC0103 rateio de consumo, UC0105 consumo mínimo da ligação, UC0111 executar atividade de faturamento, UC0113 faturar grupo, UC0120 calcular valores de água/esgoto, UC0145 inserir conta, UC0147 cancelar conta, UC0148 revisão, UC0150 retificar, UC0151 alterar vencimento, UC0155 encerrar faturamento do mês, UC0185 obter valor por categoria, UC0187 guia de pagamento, UC0194 crédito a realizar, UC0216 acréscimo por impontualidade, UC0800 consumo não medido, UC0810 economias virtuais.

---

## 2. Micromedição e leitura

### 2.1 Entidades e campos-chave

| Conceito | Entidade → tabela | Campos-chave (coluna) |
|---|---|---|
| Rota | `Rota` → `micromedicao.rota` | `rota_cdrota` (código, short), `stcm_id` setor comercial, `ftgr_id` grupo de faturamento, `leit_id` leiturista, `lttp_id` tipo de leitura, `empr_id` empresa de leitura, `empr_identregacontas`, `empr_idcobranca`, `cbgr_id` grupo de cobrança, `rota_icconsumoajuste` (1 = ajuste mensal por dias), `rota_dtleituraajuste`, `rota_nndiasconsumoajuste`, `rota_icalternativa`, `rota_ictransmissaooffline`, `rota_nnlimiteimoveis`, `rota_icfaixafalsageracao` + `rota_pcfaixafalsageracao` (anti-fraude), `rota_icleitfisclzger` + `rota_pcleitfisclzger` (fiscalização por amostragem), `rota_icleitfisclzcortado/suprimido`, `rota_icimpressaotermicafinalgrupo` |
| Leiturista | `Leiturista` → `micromedicao.leiturista` | `clie_id` (é um cliente), `func_id`, `empr_id`, `usur_id`, `leit_nnimei` (aparelho), DDD/fone |
| Roteiro da empresa | `RoteiroEmpresa` → `roteiro_empresa` | liga empresa × leiturista |
| Histórico de medição | `MedicaoHistorico` → `micromedicao.medicao_historico` | `imov_id`, `medt_id` (tipo: 1 ligação de água, 2 poço), `mdhi_amleitura`, `hidi_id` (instalação do hidrômetro), leitura anterior faturada/informada + data (`mdhi_nnleitantfatmt`, `mdhi_dtleitantfatmt`, `mdhi_nnleitantinformada`), leitura atual informada/faturada + data (`mdhi_nnleituraatualinformada`, `mdhi_dtleituraatualinformada`, `mdhi_nnleituraatualfaturamento`, `mdhi_dtleituraatualfaturamento`), `mdhi_nnconsumomedidomes`, `mdhi_nnconsumoinformado`, `mdhi_nnconsumomediohidrometro`, `ltst_idleiturasituacaoanterior/atual`, `ltan_idleitanorminformada` (anormalidade informada pelo leiturista), `ltan_idleitanormfatmt` (anormalidade usada no faturamento), `mdhi_icanalisado`, `mdhi_nnxconsecanormalidade` (vezes consecutivas), `mdhi_nnleituracampo`, `leit_id`, `func_id`, `motp_id` (interferência) |
| Histórico de consumo | `ConsumoHistorico` → `micromedicao.consumo_historico` | `imov_id`, `lgti_id` (1 água, 2 esgoto), `cshi_amfaturamento`, `cshi_nnconsumofaturadomes`, `cshi_nnconsumocalculomedia` (base da média futura), `cshi_nnconsumomedio`, `cshi_nnconsumominimo`, `cstp_id` tipo de consumo, `csan_id` anormalidade de consumo, `rttp_id` tipo de rateio, `cshi_nnconsumorateio`, `cshi_idconsumoimovelcondominio` (auto-relacionamento macro/micro), `cshi_nnconsimoveisvinculados`, `cshi_pccoleta`, `cshi_icfaturamento` (1 faturar / 2 não), `cshi_icimovelcondominio`, `cshi_icajuste`, `poco_id`, `ftst_id` (situação especial), `rota_id` |
| Anormalidade de leitura | `LeituraAnormalidade` → `leitura_anormalidade` | `ltan_dsleituraanormalidade`, `ltan_icrelativohidrometro`, `ltan_icimovelsemhidrometro`, `ltan_icleitura` (exige leitura?), `ltan_icemissaoordemservico` + `svtp_id` (gera OS), `ltan_nnfatorsemleitura`, `ltan_nnfatorcomleitura` (multiplicadores do consumo), `ltan_nnmesesleiturasuspensa`, `ltan_nnvezessuspendeleitura`, `ltan_icperdatarifasocial`, `ltan_icimpressaosimultanea`; **efeito no faturamento via 4 FKs**: `lalt_idleitafaturarsemleit/comleit` (como fica a *leitura* faturada) e `lacs_idconsacobrarsemleit/comleit` (como fica o *consumo* faturado) |
| Efeito sobre a leitura | `LeituraAnormalidadeLeitura` → `leitura_anorm_leitura` | 0 anterior+média, 1 anterior, 2 anterior+consumo, 3 informada, 4 normal |
| Efeito sobre o consumo | `LeituraAnormalidadeConsumo` → `leitura_anorm_consumo` | 0 não ocorre (=média), 1 mínimo, 2 média, 3 normal, 4 maior entre média e medido, 5 menor entre média e medido, 7 fixo, 8 não medido |
| Anormalidade de consumo | `ConsumoAnormalidade` → `consumo_anormalidade` | `csan_dsconsumoanormalidade`, `csan_dsmensagemconta` (texto impresso na conta), `csan_iccalcularmedia` (entra na média?), `csan_icrevcompermissaoespecial` |
| Ação por reincidência | `ConsumoAnormalidadeAcao` → `consumo_anorm_acao` | chave (`csan_id`, `catg_id`, `iper_id`); para meses 1/2/3: `csaa_nnfatorconsumomesN`, `lacs_idmesN` (o que fazer com o consumo), `csaa_icgeracaocartamesN`, `csaa_dsmensagemcontamesN`, `svtp_idmesN` (serviço/OS), `step_idmesN` (especificação de RA) |
| Tipo de consumo | `ConsumoTipo` → `consumo_tipo` | `cstp_iccalculomedia` (entra na média?) |
| Situação da leitura | `LeituraSituacao` → `leitura_situacao` | 1 REALIZADA, 2 NAO_REALIZADA, 3 CONFIRMADA, 4 LEITURA_ALTERADA |
| Tipo de leitura da rota | `LeituraTipo` | 1 CONVENCIONAL, 2 MICROCOLETOR, 3 LEITURA_E_ENTRADA_SIMULTANEA (impressão simultânea), 4 CELULAR_MOBILE |
| Tipo de medição / ligação / rateio | `MedicaoTipo` 1 água, 2 poço; `LigacaoTipo` 1 água, 2 esgoto; `RateioTipo` 0 sem rateio, 4 rateio não medido água, 5 rateio por área comum |
| Faixa falsa | `LeituraFaixaFalsa` → `leitura_faixa_falsa` (PK = `mdhi_id`) | faixa falsa e faixa correta (inferior/superior) gravadas por medição |
| Fiscalização de leitura | `LeituraFiscalizacao` → `leitura_fiscalizacao` | leitura da empresa × leitura do fiscalizador (`ltfz_nnleituraempresa`, data, anormalidade, funcionário) |
| Movimento (arquivo para o leiturista) | `MovimentoRoteiroEmpresa` → `movimento_roteiro_empr` | 1 linha por imóvel/medição: dados do imóvel, endereço, hidrômetro, leitura anterior, **faixa esperada inicial/final**, consumo dos 6 meses anteriores, média, anormalidade anterior, leitura informada, `mrem_icconfirmacaoleitura`, `mrem_tmprocessamento` |
| Arquivo texto | `ArquivoTextoRoteiroEmpresa` → `arquivo_texto_rot_empr` (+ `ArquivoTextoRoteiroEmpresaDivisao` → `arq_txt_roteiro_emp_div`) | `txre_artexto` (binário), setor/quadras inicial/final (até 3 faixas), IMEI, `txre_nnsequencialeiturista`, `sitl_id`, `txre_dsmotivofinalizacao`, arquivo "não recebido" |
| Situação da transmissão | `SituacaoTransmissaoLeitura` → `situacao_transm_leitura` | 1 DISPONIVEL, 2 LIBERADO, 3 EM_CAMPO, 4 TRANSMITIDO, 5 FINALIZADO_NAO_TRANSMITIDO, 6 FINALIZADO_INCOMPLETO, 7 FINALIZADO_USUARIO, 8 FINALIZADO_POR_DIGITACAO, 9 INFORMAR_MOTIVO_FINALIZACAO |
| Releitura | `ReleituraMobile` → `releitura_mobile` | solicitação de releitura água/poço com anormalidade anterior/atual |
| Telemetria | `TelemetriaLog`, `TelemetriaMov`, `TelemetriaMovReg`, `TelemetriaRetMot` | log do payload bruto recebido, registro por imóvel (inscrição, leitura, hidrômetro, processado, erro), motivo de retorno aceito/recusado |
| Sequência de rota | `RotaAtualizacaoSeq` → `rota_atualizacao_seq` | reordenação sugerida de imóveis na rota por mês, aplicada ao fim do faturamento |
| Alto consumo | `ComunicadoAltoConsumo` | aviso ao cliente (imóvel × referência) |
| Consumo mínimo por área | `ConsumoMinimoArea` → `consumo_minimo_area` | (`catg_id`/`scat_id`, `cmar_amreferencia`, `cmar_nnareafinal`, `cmar_nnconsumo`) |
| Consumo mínimo por parâmetro | `ConsumoMinimoParametro` → `consumo_minimo_parametro` | (`catg_id`/`scat_id`, `cmpc_amreferencia`, `cmpc_nnfinparametro`, `cmpc_nnconsumo`) - pontos de utilização ou nº de moradores |

**Hidrômetro** (`micromedicao.hidrometro*`): `Hidrometro` (nº, marca, tipo, capacidade, classe metrológica, diâmetro, relojoaria, ano de fabricação, **nº de dígitos da leitura** `hidr_nndigitosleitura`, vazões de transição/nominal/mínima, leitura acumulada, data de última revisão, baixa), `HidrometroInstalacaoHistorico` (`hidi_dtinstalacaohidrometro`, `hidi_nnleitinstalacaohidmt`, retirada + leitura de retirada, leitura de corte/supressão, selo, lacre, proteção, local de instalação, `hidi_icinstalacaosubstituicao`, rateio tipo, ligação, medição tipo), `HidrometroMovimentacao`/`HidrometroMovimentado` (estoque, oficina), `HidrometroSituacao` (1 INSTALADO, 2 EM_MANUTENCAO, 3 DISPONIVEL, 4 ROUBADO), `HidrometroMotivoBaixa`, `HidrometroMotivoMovimentacao`, `HidrometroLocalArmazenagem` (flag oficina), `HidrometroMarca` (`himc_nndiarevisao`: intervalo de revisão em dias), `RetornoControleHidrometro`. Esta parte pertence a `operations/sigsan-metering` + `sigsan-assets`.

### 2.2 Fluxo de leitura (ciclo operacional)

```
[cronograma] GERAR_ARQUIVO_LEITURA (atividade 1)
   └─ UC0083 gerarDadosPorLeitura{Convencional|MicroColetor|CelularMobile}(rotas, anoMes)
        • para cada imóvel ligado/cortado de água ou ligado de esgoto da rota:
          - média (UC0068) e faixa de leitura esperada (UC0086)
          - sorteia "faixa falsa" (anti-fraude) e/ou marca para fiscalização
          - grava MovimentoRoteiroEmpresa + gera ArquivoTextoRoteiroEmpresa (registros 0/1/2)
          - arquivo divide-se em partes (ArquivoTextoRoteiroEmpresaDivisao) e é liberado por IMEI
[campo]     EFETUAR_LEITURA (atividade 2): leiturista baixa o arquivo (situação 2→3 em campo),
            lê hidrômetros, devolve arquivo (4 transmitido) ou digita (8)
[retorno]   REGISTRAR_LEITURA_ANORMALIDADE (atividade 3)
   └─ registrarLeiturasAnormalidades / processarArquivoTextoParaRegistrarLeiturasAnormalidades
        • valida cada linha (lista na seção 2.4); inválidas vão para relatório de erros, válidas gravam
          MedicaoHistorico (leitura atual informada, data, anormalidade informada, leiturista)
        • atualiza a data de realização da atividade com a data de leitura mais frequente
[crítica]   CONSISTIR_LEITURAS_E_CALCULAR_CONSUMOS (atividade 9) = UC0101 (seção 3)
[pós]       EFETUAR_RATEIO (UC0103) para condomínios; GERAR_FISCALIZACAO (atividade 4)
```
(Ordem das atividades acima = **inferência** a partir dos ids/precedência e da sequência de chamadas; os dados de semente de `faturamento_atividade.ftat_nnordemrealizacao` não estão no repositório.)

Integrações de leitura vistas no código:
* **Arquivo texto/coletor** (`gerarDadosLeituraConvencionalRegistroZERO/UM/DOIS` e `...SistemaLegado`): layout posicional por registro, enviado por e-mail (`mandaArquivoLeituraEmail`) ou baixado (`baixarArquivoTextoParaLeitura(imei, ...)`).
* **Celular/mobile** (`atualizarLeituraAnormalidadeCelularCasoOperador/CasoSistema`, `atualizarLeituraAnormalidadeSemCelular`): retorna leituras em lote; `parm_nndiascelular` bloqueia aparelho; relatório de inconsistências do retorno.
* **Telemetria** (`processarLeituraViaTelemetria(String)` + `consistirLeituraTelemetria`): payload textual de medidores remotos, validação (nº de consumidores, dados), log bruto, reprocessamento (`TelemetriaMovReg.icprocessado`).
* **Impressão simultânea** (leitura+faturamento em campo): `MovimentoContaPrefaturada` (`faturamento.mov_conta_prefaturada`) e conta com situação PRE_FATURADA (9); o retorno (`ArquivoTextoRetornoIS`, `MovimentoArquivoTextoRetornoIS`) é processado por `processarMovimentoContaPrefaturada`/`processarImoveisImpressaoSimultanea` **sem refazer** o cálculo.
* **Releitura** (`solicitarReleitura`, `pesquisarImovelParaReleitura`): imóvel marcado vai de novo para o leiturista.

### 2.3 Anti-fraude e amostragem (conceito)

* **Faixa falsa** (`calcularFaixaLeituraFalsa`): para uma fração dos imóveis o arquivo carrega uma "leitura anterior falsa" (`anterior − meses×média − (consumoMínimo+1)`) e a faixa esperada derivada dela; se o leiturista lançar leitura coerente com a faixa falsa, a leitura foi fabricada. Sorteio = `(id do imóvel + segundos do relógio) × percentual % 100 == 0` (fraco; **inferência**: um CSPRNG é preferível). Parâmetros em rota e em `sistema_parametro` (`parm_pcfaixafalsa`, indicador de uso).
* **Fiscalização de leitura** (`selecionarFiscalizacaoLeitura`): mesma técnica de sorteio; gera `LeituraFiscalizacao` e (se configurado) OS para outro funcionário reler.
* **Suspensão de leitura** por anormalidade recorrente (`ltan_nnmesesleiturasuspensa`, `parm_nnvezessuspendeleitura`): `suspenderLeituraParaImovelComHidrometroRetirado`, `suspenderLeituraParaImovelComConsumoRealNaoSuperiorA10`.

### 2.4 Validações do retorno de leitura (registrarLeiturasAnormalidades)

Uma linha vai para o relatório de erros (não grava) se: matrícula do funcionário inexistente; data inválida ou **futura**; data de leitura fora de `[ref−1, ref+1]` meses da referência de faturamento; imóvel inexistente; tipo de medição inexistente; leitura > 0 para ligação sem hidrômetro; anormalidade "imóvel sem hidrômetro" lançada para ligação com medidor; código de anormalidade inexistente; indicador de confirmação de leitura inválido; água/poço informado sem hidrômetro instalado. Processa em lotes de 100 imóveis.

### 2.5 Crítica de leitura: faixa esperada (UC0086)

Pseudocódigo (m = consumo médio; LA = leitura anterior; os fatores no código usam `new BigDecimal(double)`; ver armadilha na seção 9):

```
se m ≤ 10:        [LA,            LA + m + 10]
se 11 ≤ m ≤ 20:   [LA + 0,4·m,    LA + 1,6·m]
se 21 ≤ m ≤ 45:   [LA + 0,5·m,    LA + 1,5·m]
se 46 ≤ m ≤ 100:  [LA + 0,6·m,    LA + 1,4·m]
se m > 100:       [LA + 0,7·m,    LA + 1,3·m]
arredonda cada extremo HALF_UP para inteiro
virada: se extremo > 10^dígitos − 1, subtrai 10^dígitos      (ex.: 5 dígitos → 100000)
"fora da faixa": leitura atual faturada < inicial OU > final   (FORA_FAIXA, anormalidade 13)
```

---

## 3. Consumo faturado: regras (pseudocódigo)

### 3.1 Média de consumo (UC0068 `obterVolumeMedioAguaEsgoto`)

```
N  = parm_nnmesescalcmediacons            (ex.: 6)
MAX= parm_nnmesesmaximocalculomedia       (limite de retroação)
base: consumo_historico.cshi_nnconsumocalculomedia (NÃO o faturado final),
      apenas linhas com ConsumoTipo.indicadorCalculoMedia = 1
      e (sem anormalidade OU anormalidade.indicadorCalcularMedia = 1)
percorre de ref−1 para trás; mês sem registro "retroage" (conta como retroação, até MAX);
média = TRUNCA( soma / mesesConsiderados )         ← divisão inteira
sem nenhum histórico: média = consumo mínimo da ligação (UC0105), mesesConsiderados = 1
```

### 3.2 Consumo mínimo da ligação (UC0105 `obterConsumoMinimoLigacao`)

```
tarifa vigente = vigência com MAIOR data ≤ hoje para a tarifa do imóvel
para cada categoria (ou subcategoria, conforme parm_ictarifacategoria):
    minTarifa = consumo_tarifa_categoria.nnconsumominimo (subcategoria → cai para categoria)
    parcela   = minTarifa × ( categoria.fatorEconomias  se não nulo, senão  nº de economias da categoria )
consumoMinimoLigacao = Σ parcelas
```

### 3.3 Água medida (UC0101 `determinarDadosFaturamentoAgua`, resumo por prioridade)

```
LA = (leituraAnteriorInformada == leituraAtualInformada) ? leituraAnteriorInformada : leituraAnteriorFaturada
1. hidrômetro substituído no período (data de instalação entre as datas informadas anterior e atual,
   mês anterior com leitura/anormalidade):
     se atual informada > leituraInstalação:  consumo = atual − leituraInstalação ; tipo REAL/ESTIMADO
     senão:                                   consumo = média ; tipo MEDIA_HIDROMETRO
     anormalidade = HIDROMETRO_SUBSTITUIDO_INFORMADO (9)
2. leitura atual informada e sem substituição:
     limite = 10^dígitos − 1 ; se atual > limite → trata como = limite (guarda original)
     atual > LA :  consumo = atual − LA ; tipo = REAL se situação anterior ∈ {REALIZADA, CONFIRMADA} senão ESTIMADO
                   anormalidade FORA_FAIXA se fora da faixa esperada
     atual = LA :  consumo = 0 ; tipo REAL/ESTIMADO
                   se atual > 1 e sem anormalidade informada e água com abastecimento e imóvel não sazonal:
                        gera anormalidade HIDROMETRO_PARADO (30)  [sem esgoto/poço ativo]
                        ou HIDROMETRO_PARADO_SEM_CONSUMO (38)     [com poço ou esgoto com mínimo fixado]
     atual < LA :  calc = (atual + 10^dígitos) − LA
                   virada plausível = calc > 0 E (calc ≤ 200 OU calc ≤ 2·média)
                   se virada: consumo = calc ; anormalidade VIRADA_HIDROMETRO (16)
                   senão:     consumo = média ; tipo MEDIA_HIDROMETRO ;
                              anormalidade = LEITURA_ATUAL_MENOR_ANTERIOR (8) se situação anterior real,
                                             LEITURA_ATUAL_MENOR_PROJETADA (7) caso contrário
                              leitura faturada = LA + média
3. sem leitura e sem anormalidade (LEITURA_NAO_INFORMADA, 10):
     dias = data atual do cronograma − data da leitura anterior (≥ 0)
     se |dias − 30| ≤ parm_nndiasvariacaoconsumo : consumo = média ; tipo MEDIA_HIDROMETRO
     senão: consumo = ARRED( média / diasCronograma [3 casas, HALF_DOWN] × dias ) ; tipo CONSUMO_MEDIO_AJUSTADO (2)
4. com anormalidade de leitura (tabela leitura_anormalidade) → seção 3.4
5. ajuste mensal por dias (se rota.indicadorAjusteConsumo = 1) → seção 3.5
6. estouro → alto → baixo consumo (seção 3.6)
7. situação especial de faturamento (seção 3.7)
8. consumo mínimo fixado na ligação:
     se ligacao_agua.lagu_nnconsumominimoagua ≠ nulo E consumo < mínimo fixado:
          consumo = mínimo fixado ; tipo CONSUMO_MINIMO_FIXADO (7)
     senão se medicao.consumoInformado ≠ nulo:
          consumo = consumo informado ; tipo INFORMADO (4) ; anormalidade CONSUMO_INFORMADO (2)
9. ligação CORTADA com situação "consumo real" e nº de dias de corte (last_nndiascorte > 0):
     mede a diferença entre a data do corte e a leitura anterior faturada (ou data prevista do cronograma, se não medido)
     ≤ 30 dias: indicadorFaturamento = NÃO se diasCorte < nndiascorte, senão SIM
     > 30 dias: não faturar se tipo ≠ REAL e consumo > 0
10. imóvel com > 1 economia → ajuste "múltiplo de economias" (seção 3.8)
11. permiteFaturamentoParaAgua(situação da ligação, consumo, tipo) → define cshi_icfaturamento;
    se não faturar, consumo faturado := 0 (mas cshi_nnconsumocalculomedia mantém o calculado)
```

### 3.3.1 Tipos e anormalidades de consumo (valores)

| ConsumoTipo | id | | ConsumoAnormalidade | id |
|---|---|---|---|---|
| INDEFINIDO | 0 | | CONSUMO_INFORMADO | 2 |
| REAL | 1 | | BAIXO_CONSUMO | 4 |
| CONSUMO_MEDIO_AJUSTADO | 2 | | ESTOURO_CONSUMO | 5 |
| MEDIA_HIDROMETRO | 3 | | ALTO_CONSUMO | 6 |
| INFORMADO | 4 | | LEITURA_ATUAL_MENOR_PROJETADA | 7 |
| NAO_MEDIDO | 5 | | LEITURA_ATUAL_MENOR_ANTERIOR | 8 |
| ESTIMADO | 6 | | HIDROMETRO_SUBSTITUIDO_INFORMADO | 9 |
| CONSUMO_MINIMO_FIXADO | 7 | | LEITURA_NAO_INFORMADA | 10 |
| SEM_CONSUMO | 8 | | ESTOURO_CONSUMO_COBRANCA_MEDIA | 11 |
| MEDIA_IMOVEL | 9 | | CONSUMO_MINIMO_FIXADO | 12 |
| FIXO_SITUACAO_ESPECIAL | 10 | | FORA_FAIXA | 13 |
| | | | HIDROMETRO_SUBSTITUIDO_NAO_INFORMADO | 14 |
| | | | FATURAMENTO_ANTECIPADO | 15 |
| | | | VIRADA_HIDROMETRO | 16 |
| | | | ANORMALIDADE_DE_LEITURA | 17 |

IDs de `LeituraAnormalidade` com significado em código (os demais são cadastro do cliente): 2 HIDROMETRO_RETIRADO, 30 HIDROMETRO_PARADO, 32 HIDROMETRO_SUBSTITUIDO, 38 HIDROMETRO_PARADO_SEM_CONSUMO, 81/82/83 ligado clandestino (água/esgoto/ambos).

### 3.4 Anormalidade de leitura informada (`dadosFaturamentoAnormalidadeLeitura`)

```
a = anormalidade de leitura de faturamento
se leitura atual NÃO informada ("sem leitura"):
     consumoAnormalidade = ANORMALIDADE_DE_LEITURA (17)  (salvo se já HIDROMETRO_SUBSTITUIDO_INFORMADO)
     conforme a.consumoSemLeitura:
        NAO_OCORRE(0) → consumo = média ; tipo MEDIA_HIDROMETRO
        MINIMO(1)     → consumo = consumo mínimo da ligação ; tipo SEM_CONSUMO(8)
        MEDIA(2)      → consumo = média ; tipo MEDIA_HIDROMETRO
        NAO_MEDIDO(8) → consumo = regra do não medido (3.9) ; tipo ESTIMADO
     conforme a.leituraSemLeitura:
        0 → leituraFaturada = LA + média ; 1 → = LA ; 2 → = LA + consumo
     consumo = TRUNCA( consumo × a.nnfatorsemleitura )      se fator ≠ nulo
senão ("com leitura"):
     conforme a.consumoComLeitura:
        NAO_OCORRE/MEDIA   → consumo = média ; MEDIA_HIDROMETRO
        MINIMO(1)          → consumo = 0 ; SEM_CONSUMO        (atenção: aqui é 0, ao contrário do sem leitura)
        NORMAL(3)          → mantém o calculado
        MAIOR_ENTRE(4)     → max(média, consumo medido)
        MENOR_ENTRE(5)     → min(média, consumo medido)
        NAO_MEDIDO(8)      → regra do não medido ; ESTIMADO
     conforme a.leituraComLeitura: 0 → LA + média ; 1 → LA ; 2 → LA + consumo ; 3 → informada
     consumo = TRUNCA( consumo × a.nnfatorcomleitura )      se fator ≠ nulo
```
(truncamento = `BigDecimal.intValue()`; ver seção 9.)

### 3.5 Ajuste mensal por dias (`ajusteMensalConsumo`, rota com `icconsumoajuste = 1`)

```
diasReais      = data atual faturada (ou informada) − data da leitura anterior do cronograma
diasCronograma = dias entre atividades EFETUAR_LEITURA de dois meses consecutivos (ou data de ajuste da rota)
diasAjuste     = diasCronograma − diasReais
se |diasAjuste| > 3 OU houve substituição de hidrômetro:
     consumoAjustado = ARRED( (consumo / diasReais) [4 casas HALF_UP] × diasCronograma )   ; tipo CONSUMO_MEDIO_AJUSTADO (2)
     leituraAjustada = leituraAtualInformada + ARRED( (consumo / diasReais)[4 casas] × diasAjuste )
     dataAtualFaturada = dataAtualInformada + diasAjuste
     hidrômetro novo / imóvel antes fixo agora medido: usa consumo diário desde a instalação;
         se diasConsumoLido ≤ 9 → consumo = mínimo/não medido, tipo CONSUMO_MINIMO_FIXADO
senão mantém leitura e data informadas
```
Evita cobrar 35 dias de consumo em um ciclo de 30 (e vice-versa).

### 3.6 Estouro, alto e baixo consumo (parametrizados em `cadastro.categoria`)

Campos usados (fato): `catg_nnconsumoestouro`, `catg_nnvezesmediaestouro`, `catg_nnconsumomaximoec`, `catg_nnconsumoalto`, `catg_nnvezesmediaaltoconsumo`, `catg_nnmediabaixoconsumo`, `catg_pcmediabaixoconsumo`.

```
Σ por categoria do imóvel (qtd = economias da categoria); categoria "principal" = a de MAIS economias

ESTOURO  : consumo > Σ(consumoEstouro × qtd)  E  consumo > ARRED( média × vezesMediaEstouro[principal] )
ALTO     : consumo > Σ(consumoAlto × qtd)     E  consumo > ARRED( média × vezesMediaAlto[principal] )
BAIXO    : média   > Σ(mediaBaixoConsumo × qtd)  E  consumo < média × pcMediaBaixoConsumo[principal] / 100
           (só avaliado se: ligação não CORTADA, sem poço, leitura REALIZADA/CONFIRMADA, sem anormalidade de leitura)

ação (ConsumoAnormalidadeAcao por anormalidade × categoria principal × perfil do imóvel):
   verifica se o MESMO tipo de anormalidade ocorreu em ref−1 e ref−2 → escolhe mês1 / mês2 / mês3
   aplica LeituraAnormalidadeConsumo do mês escolhido (NAO_OCORRE→média, MINIMO, MEDIA, NORMAL, MAIOR/MENOR)
   e multiplica por csaa_nnfatorconsumomesN (arredondando)
   mensagem na conta (csaa_dsmensagemcontamesN), carta, OS/RA (svtp_idmesN, step_idmesN)
sem ação cadastrada (estouro): 1ª ocorrência (ou sem histórico) → ESTOURO_CONSUMO_COBRANCA_MEDIA (11): cobra a média;
                               reincidente sem estourar o máximo (numeroConsumoMaximoEc×qtd) → mantém ESTOURO_CONSUMO (5)
conta resultante pode nascer EM REVISÃO (ContaMotivoRevisao 91 estouro, 100 baixo consumo) → ContaTipo 1 "retida por EC" / 2 "retida por BC"
```

### 3.7 Situação especial de faturamento (`faturamento_situacao_*`)

`FaturamentoSituacaoTipo` (ex.: 0 normal, 2 paralisar leitura e faturar média, 3 paralisar leitura e faturar taxa mínima, 5 faturar normal, 9 nitrato, 12 paralisar faturamento de esgoto) define: paralisa faturamento? paralisa leitura? vale p/ água/esgoto? informa consumo/volume? E **4 FKs** de efeito (as mesmas de leitura/consumo, com e sem leitura). `FaturamentoSituacaoHistorico` registra imóvel × tipo × motivo × intervalo `[amInicio, amFim]` × retirada, com consumo fixo opcional (`ftsh_nnconsumoaguamedido/naomedido`, `ftsh_nnvolumeesgotomedido/naomedido`). `FaturamentoSituacaoComando` aplica a situação em lote por faixa (localidade, setor, quadra, lote, rota, sequencial, categoria). Regra: se `refDoGrupo ∈ [inicio,fim]`, o consumo vem da situação (consumo tipo FIXO_SITUACAO_ESPECIAL = 10) e, se paralisado, **não gera conta**.

### 3.8 Imóvel com várias economias: consumo múltiplo de economias (`ajusteConsumoMultiploQuantidadeEconomias`)

```
min  = consumo mínimo da ligação (3.2)
exc  = consumo − min
se exc > 0:
     excPorEco = exc DIV economias                       (divisão inteira)
     totalAjustado = Σ_categorias ( excPorEco × econCat + minTarifaCat × econCat )
     resto = consumo − totalAjustado
senão: resto = consumo MOD economias
consumo faturado = consumo − resto ; se a leitura faturada = anterior + consumo, recua a leitura em 'resto'
```
Consequência: nunca se fatura fração de economia; o resto "se perde".

### 3.9 Consumo não medido (sem hidrômetro) — `parm_icnaomedidotarifa` e `parm_cdtipocalcnaomedido`

```
se ligacao_agua.lagu_nnconsumominimoagua ≠ nulo:   consumo = esse valor ; tipo CONSUMO_MINIMO_FIXADO (7)
senão:
   se parm_icnaomedidotarifa = SIM:   consumo = consumo mínimo da ligação (3.2)         [por TARIFA]
   senão por tipo de cálculo:
        1 (por ÁREA):      área = área construída (ou menor área da faixa, ou 1) ;
                           areaVirtual = área / economiasVirtuais [2 casas HALF_UP] ;
                           por subcategoria: consumoArea = consumo_minimo_area com
                               areaFinal ≥ areaVirtual, vigência (amReferencia ≤ ref) mais recente, menor areaFinal
                               (subcategoria → fallback categoria) ;
                           consumo = Σ consumoArea × (fatorEconomias ?? economias da subcategoria)
        2 (por PONTOS DE UTILIZAÇÃO) / 3 (por Nº DE MORADORES):
                           parametro = pontos (ou moradores; nulo → 1) ;
                           consumoParam = consumo_minimo_parametro [lookup por faixa do parâmetro, subcategoria → categoria]
                           consumo = Σ consumoParam × economias da subcategoria
   tipo = NAO_MEDIDO (5)
```

### 3.10 Esgoto (`determinarDadosFaturamentoEsgoto`)

Dois percentuais distintos em `atendimentopublico.ligacao_esgoto`:
* `lesg_pccoleta` (**% da água consumida coletada**): reduz o **volume** faturado de esgoto.
* `lesg_pcesgoto` (**% da tarifa de água** cobrado como esgoto): fator aplicado ao **valor** (seção 4.3).
* Opcionais: `lesg_pcalternativo` + `lesg_nnconsumopcalternativo` (percentual menor para consumo baixo) e `lesg_nnconsumominimoesgoto` (volume mínimo fixo).

```
SEM hidrômetro de poço:
    se tem ligação de água: volume = consumo faturado de água (e tipo = tipo da água)
    senão: volume = mínimo da ligação (se situação LIGADO) ou não medido (3.9)
COM hidrômetro de poço: aplica 3.3 à medição tipo POÇO;
    se tipo ∉ {MEDIA_HIDROMETRO, MEDIA_IMOVEL}: volume = volume do poço + consumo de água faturado
mínimo de esgoto: se volume < lesg_nnconsumominimoesgoto → volume = mínimo ; anormalidade CONSUMO_MINIMO_FIXADO
consumo informado no poço sobrepõe (tipo INFORMADO)
volume final (se tipo ∉ média) = ARRED( volume × lesg_pccoleta / 100 )              [HALF_UP]
```
Poço sem esgoto (`PocoTipo`, `poco_ichidrometro`) é a "fonte alternativa": não há consumo de água; o esgoto é cobrado pelo volume do poço.

### 3.11 Rateio de condomínio (UC0103) — `micromedicao.efetuarRateioDeConsumo`

Estrutura: imóvel **macro** (`indicadorImovelCondominio=1`, hidrômetro geral) com imóveis **micro** vinculados (`imov_idimovelcondominio`); `consumo_historico.cshi_idconsumoimovelcondominio` liga os históricos.

```
aSerRateado = MAX(0, consumoMacro − Σ consumoFaturado dos micros)        (água e esgoto separados)
se existe micro NÃO medido: rateia SÓ entre os não medidos: porEco = aSerRateado DIV econNãoMedidas
senão (todos medidos):      porEco = aSerRateado DIV econMedidas ; 
                            limita porEco ao intervalo [ −parm_nnconsrateiodecrementomax , +parm_nnconsrateioincrementomax ]
consumoRateio(micro) = porEco × economiasDoMicro
   (não medido com hidrômetro → 0 ; piso: consumoRateio ≥ −consumoFaturado do micro, para não negativar)
macro com poço e consumo da ligação < Σ mínimos: consumo do micro = (consumoMacro DIV econMedidas) × economiasMicro
tipo de rateio 5 = RATEIO_AREA_COMUM (SB0007 `efetuarRateioPorAreaComum`)
```
Na versão mais recente do código (alteração de 2012) o consumo faturado do micro **não** soma o rateio; o rateio entra na **conta** como **valor** (`conta.cnta_vlrateioagua/esgoto`): valor = tarifa do macro aplicada ao consumo a ratear (se 0 < consumo ≤ mínimo da ligação, usa o mínimo) ÷ economias do condomínio × economias do micro, com arredondamento ad hoc (`+0,005` e `FLOOR`; ver seção 9).

---

## 4. Tarifa

### 4.1 Estrutura (hbm)

```
consumo_tarifa (cstf_id)  ── tarifa de consumo (ex.: 1 NORMAL, 2 SOCIAL, 21 ÁGUA BRUTA/ADUTORA); tem ttpc_id TarifaTipoCalculo e lapf_id (perfil da ligação)
   └─ consumo_tarifa_vigencia (cstv_id, cstv_dtvigencia)             ← vigência por DATA (não por competência)
        └─ consumo_tarifa_categoria (cstc_id, catg_id, scat_id?)     ← cstc_nnconsumominimo, cstc_vltarifaminima
             └─ consumo_tarifa_faixa (ctfx_id)                       ← inicio, fim, ctfx_vlconsumotarifa (valor por m³)
imovel.cstf_id  → tarifa do imóvel ;  conta.cstf_id guarda a tarifa usada (snapshot)
```
Outros catálogos de tarifa/serviço: `faturamento.debito_tipo` (serviços e taxas; `dbtp_vlsugerido/vllimite/vllimiteinferior`, `dbtp_icgeracaoconta`, `dbtp_icgeracaoautomatica`, `dbtp_nncodigoconstante`, `fntp_id` tipo de financiamento, `lict_id` item contábil) e `debito_tipo_vigencia` (valor do serviço por vigência), `debito_faixa_valores`, `conta_revisao_fx_valor` (faixa de valor que dispara revisão), `imposto_tipo` + `imposto_tipo_aliquota` (alíquota por referência), `cobranca.indices_acresc_impont` (multa, juros, correção por referência).

* **TarifaTipoCalculo** (`faturamento.tarifa_tipo_calculo`): `1` faixas progressivas (padrão; **inferência**: é o ramo "else" do código), `2` fórmula regulatória específica de uma concessionária (método `calcularValorFaturadoFaixaCAER`: polinômio no consumo ≤ 200 m³ e linear acima; **conceito: tarifa por fórmula pluginável**), `4` "consumo direto na faixa" (tarifa única da faixa onde cai o consumo por economia, aplicada ao consumo **inteiro**, não aos blocos). O valor `3` não aparece em ramo próprio nas rotinas lidas.
* **Categoria × subcategoria**: `parm_ictarifacategoria` decide se a tarifa é por categoria ou por subcategoria (com fallback subcategoria → categoria). Categorias: 1 RES, 2 COM, 3 IND, 4 PÚB. `Categoria.fatorEconomias` ("econ. equivalentes") e `Subcategoria.indicadorSazonalidade` (imóvel sazonal não gera "hidrômetro parado").
* **Tarifa social**: duas peças. (a) o imóvel é associado à tarifa de consumo `CONSUMO_SOCIAL`/perfil `TARIFA_SOCIAL (4)`; cadastro em `cadastro.tarifa_social_*` (`TarifaSocialDado`, `TarifaSocialDadoEconomia` com renda/consumo de energia/área máxima, `TarifaSocialCarta*`, motivos de exclusão/revisão, `imov_amrefexclusaotarifasocial`). (b) **bônus social** (`gerarBonusTarifaSocial`): crédito a realizar tipo `DESCONTO_TARIFA_SOCIAL (11)` = `tarifaMínima(residencial, tarifa NORMAL vigente) × parm_pcbonussocial / 100`, concedido só se perfil = tarifa social, subcategoria R1, água LIGADO, conta do mês anterior **paga no prazo** (dias úteis ≤ `parm_nndiascalculoacrescimos`), consumo ≤ 10 m³ e sem contas anteriores vencidas em aberto. Anormalidades marcadas `icperdatarifasocial` fazem perder o benefício. (Regras (b) parecem de uma concessionária específica: **inferência** — modelar como política configurável.)
* **Vigência**: a tarifa usada é a de maior data ≤ data da leitura anterior **mais** todas as vigências com data dentro do período de leitura; com 2+ vigências o valor é rateado por dias (4.4).

### 4.2 Cálculo de água por faixas (tipo 1) — `calculoSimplesUmaTarifa`

```
E        = Σ economias (todas as categorias do imóvel)
minImóvel= Σ_categorias ( consumoMinimoTarifa × economiasCat )
excesso  = consumoFaturado − minImóvel
excPorEco= excesso DIV E                            (divisão inteira, truncada)
consPorEco = consumoFaturado DIV E
para cada categoria c (economias n):
    vlEco  = tarifaMinima[c]                         (cobre o consumo mínimo)
    se excesso > 0:  consEco = minTarifa[c] ; excedente = excPorEco
    senão:           consEco = MIN(consPorEco, minTarifa[c]) ; excedente = consPorEco − minTarifa[c]
    fimAnterior = minTarifa[c]
    para cada faixa f em ordem crescente, enquanto excedente > 0:
         qtd = MIN( excedente , f.fim − fimAnterior )
         vlEco += qtd × f.valor ; consEco += qtd ; excedente −= qtd ; fimAnterior = f.fim
    valor[c]     = vlEco × n
    consumo[c]   = consEco × n
valorÁgua = Σ valor[c]   (guarda por categoria e por faixa: conta_categoria e conta_catg_cons_fx)
```
Observações: (i) o consumo "excedente" por economia é o mesmo para todas as categorias (média simples); (ii) o resto da divisão inteira não é cobrado (mas o ajuste da 3.8 já o removeu do consumo faturado).

### 4.3 Esgoto na tarifa

```
fator = pcEsgoto / 100  com 2 casas HALF_UP         (ex.: 87,5 → 0,88 ; 33,33 → 0,33)
valorEsgoto[c]       = ARRED2( valorÁguaCalculadoSobreVolumeEsgoto[c] × fator )
tarifaMinimaEsgoto[c]= ARRED2( tarifaMinima[c] × fator )
```
O valor de esgoto usa a **mesma estrutura de tarifa** aplicada ao *volume de esgoto*, depois multiplicada pelo fator. Se o imóvel tem percentual alternativo e `consumoPorEconomia ≤ nnconsumopcalternativo`, usa o **menor** entre `pcesgoto` e `pcalternativo`.

### 4.4 Mais de uma vigência no período (`calculoProporcionalMaisDeUmaTarifa`)

```
diasLeitura = (dataAtual − dataAnterior) + 1
para cada vigência v (ordenadas):
    valores_v = calculo(consumo, tarifa v)                      (consumo inteiro em cada tarifa)
    inicio_v  = ( primeira ? dataAnterior : fim_{v−1}+1 )
    fim_v     = ( existe próxima ? MIN(próxima.data − 1, dataAtual) : dataAtual )
    diasV     = (fim_v − inicio_v) + 1                          (0 se fim < inicio)
    fator_v   = diasV / diasLeitura                             (4 casas HALF_UP)
    cada valor (categoria, faixa, tarifa mínima) × fator_v      (2 casas HALF_UP)
total = Σ valores_v
```

### 4.5 Serviços (taxas) e financiamento

* `debito_tipo` = qualquer cobrança de serviço (ligação, religação, 2ª via `TAXA_2_VIA_CONTA (101)`, taxa de cobrança, multa por infração, juros de mora `91`, multa `80`, atualização monetária `94`, parcelamentos `40-50`, doação `1/104/202`, tarifa de corte `2500`...). `indicadorGeracaoConta` diz se o débito, sozinho, justifica gerar conta.
* Parcelamento de serviço: `DebitoACobrar` com `dbac_nnprestacaodebito` prestações, `dbac_pctaxajurosfinanciamento`, `dbac_nnparcelabonus`, categorias (`debito_a_cobrar_categoria`) para repartir por categoria.

### 4.6 Retenção de impostos (`gerarImpostosDeduzidosConta`)

* Só existe se o imóvel tem **cliente responsável** (inferência: órgãos públicos que retêm tributos). Base = `água + esgoto + débitos − créditos` (truncada a 2 casas, `ROUND_DOWN`).
* Tipos em `imposto_tipo`: IR(1), CSLL(2), COFINS(3), PIS/PASEP(4); alíquota por referência (`imposto_tipo_aliquota`).
* Cada imposto = `base × alíquota/100` (HALF_DOWN, 2 casas). **O último imposto da lista = (base × Σalíquotas) − Σ(anteriores)**, para fechar o total (corrige resíduo de arredondamento). A conta guarda `cnta_vlimpostos` e itens em `conta_impostos_deduzidos`.
* **Valor total da conta** = `água + esgoto + débitos − créditos − impostos` (`Conta.getValorTotalContaBigDecimal`, HALF_UP 2).

### 4.7 Acréscimos por impontualidade (juros/multa/correção) — `ControladorCobranca.calcularAcrescimoPorImpontualidade`

Tabela `cobranca.indices_acresc_impont` (por referência): `iaci_pcmulta`, `iaci_pcjurosmora`, `iaci_nnfatoratlzcmonetaria`, `iaci_icmultamensal`, `iaci_pclimitemulta`, `iaci_icjurosmensal`, `iaci_pclimitejuros`. Índice buscado pelo mês **anterior** ao do vencimento; sem índice → menor índice da referência → menor índice geral → zeros.

```
não calcula se: pagamento por débito automático; ou dias ÚTEIS (calendário de feriados do município) entre vencimento e pagamento ≤ parm_nndiascalculoacrescimos
multa (só se conta.indicadorCobrancaMulta = 2 "ainda não cobrada"):
    mensal : % = iaci_pcmulta
    diária : % = MIN( iaci_pcmulta × diasCorridosEmAtraso , iaci_pclimitemulta )
    valorMulta = (valorConta − multasJáCobradas) × %/100
juros (não calcula se o vencimento é de mês posterior à arrecadação corrente):
    mensais: meses = diferença em MESES DE CALENDÁRIO (ignora o dia) ; valor = valorConta × meses × pcJuros/100   (0 se meses ≤ 0)
    diários: % = MIN( iaci_pcjurosmora × diasCorridos , iaci_pclimitejuros ) ; valor = valorConta × %/100
correção: monetária (fator por mês: relação entre índice do mês do pagamento e do mês do vencimento) OU "atualização tarifária" (reavalia a conta na tarifa vigente) conforme parm_icatualizacaotarifaria ; só se meses em atraso > parm_nnmesescalculocorrecao
tudo HALF_UP 2 casas
geração: `gerarDebitosACobrarDeAcrescimosPorImpontualidade` cria DebitoACobrar dos tipos MULTA_IMPONTUALIDADE(80), JUROS_MORA(91), ATUALIZACAO_MONETARIA(94) (por rota, no fechamento da arrecadação), respeitando `catg_iccobrancaacrescimos` e `clie_iccobrancaacrescimos` do cliente responsável (SIM/NÃO/ENCERRAMENTO_ARRECADACAO)
```

---

## 5. Faturamento

### 5.1 Entidades (hbm)

| Conceito | Entidade → tabela | Campos-chave |
|---|---|---|
| Grupo | `FaturamentoGrupo` → `faturamento.faturamento_grupo` | `ftgr_amreferencia` (competência corrente), `ftgr_nndiavencimento`, `ftgr_icvencimentomesfatura`, `ftgr_icuso`; rotas apontam para o grupo (`rota.ftgr_id`) |
| Cronograma mensal | `FaturamentoGrupoCronogramaMensal` → `fatur_grupo_crg_mensal` | grupo × `ftcm_amreferencia` |
| Atividade (catálogo) | `FaturamentoAtividade` → `faturamento_atividade` | `ftat_nnordemrealizacao`, `ftat_idatividadeprecedente`, `ftat_icobrigatoriedade`, `ftat_icrepeticao`, `ftat_iccomando`, `proc_id` (processo batch) |
| Atividade do cronograma | `FaturamentoAtividadeCronograma` → `fatur_ativ_cronograma` | `ftac_dtprevista`, `ftac_tmcomando`, `ftac_tmrealizacao` |
| Atividade × rota | `FaturamentoAtivCronRota` → `fatur_ativ_cron_rota` | `facr_dtcontavencimento` (vencimento das contas da rota) |
| Conta | `Conta` → `faturamento.conta` | ver 5.2 |
| Conta histórica | `ContaHistorico` → `conta_historico` | espelho (após encerramento); `ContaGeral` (`conta_geral.cntg_ichistorico`) é a identidade comum |
| Conta × categoria | `ContaCategoria` → `conta_categoria` | PK (`cnta_id`,`catg_id`,`scat_id`): economias, valor/consumo de água, valor/consumo de esgoto, tarifa mínima/consumo mínimo (água e esgoto) |
| Conta × categoria × faixa | `ContaCategoriaConsumoFaixa` → `conta_catg_cons_fx` | faixa início/fim, valor da tarifa na faixa, consumo e valor de água/esgoto |
| Débito a cobrar | `DebitoACobrar` → `debito_a_cobrar` | valor, prestações total/cobradas, referência de cobrança, financiamento, parcelamento, OS/RA de origem, `dcst_idatual`, `dbac_nnparcelabonus`, `dbac_dtrevisao` |
| Débito cobrado | `DebitoCobrado` → `debito_cobrado` | linha do débito dentro da conta: `dbcb_vlprestacao`, nº da prestação |
| Crédito a realizar | `CreditoARealizar` → `credito_a_realizar` | `crar_vlcredito`, prestações, **`crar_vlresidualmesanterior`** (resíduo), `crar_vlresidualconcedidomes`, origem/tipo |
| Crédito realizado | `CreditoRealizado` → `credito_realizado` | linha do crédito dentro da conta |
| Guia de pagamento | `GuiaPagamento` → `faturamento.guia_pagamento` (+ `guia_pagamento_item`) | cobrança avulsa (cliente ou imóvel): `gpag_vldebito`, vencimento, `gpag_nnguiafatura`, parcelamento, `gpag_iccobrancamulta` |
| Fatura (agrupa contas de cliente responsável) | `Fatura`/`FaturaItem` → `faturamento.fatura`/`fatura_item` | `fatu_vldebito`, `fatu_vltaxa`, vencimento, `fitm_vlconta`, `fitm_vlimposto` |
| Pré-faturamento / impressão simultânea | `MovimentoContaPrefaturada` (+ categorias, impostos) | leitura, consumos, rateio, lat/long, retransmissão, conta vinculada |
| Situação de débito/crédito | `DebitoCreditoSituacao` | ver 5.3 |
| Resumos gerenciais | `ResumoFaturamentoSimulacao*`, `ResumoFaturamentoSituacaoEspecial`, `HistogramaAgua*`, `ReceitasAFaturarResumo` | simulação, histograma de consumo por faixa/economia, receita a faturar |
| Qualidade da água na conta | `QualidadeAgua` (+ `QualidadeAguaPadrao`) | índices mensais por localidade/setor/fonte: turbidez, cloro, pH, cor, flúor, ferro, coliformes totais/fecais/termotolerantes, nitrato, alcalinidade, e quantidades exigidas/analisadas/conformes por parâmetro |
| Mensagem na conta | `ContaMensagem` | 3 linhas por referência × grupo/gerência/localidade/setor/quadra |
| Não entrega | `DocumentoNaoEntregue`, `MotivoNaoEntregaDocumento` | tentativas de entrega |
| Agência reguladora | `LancamentoAgenciaReguladora` | lançamento mensal água/esgoto por localidade/setor para a agência |
| Faturamento imediato | `FaturamentoImediatoAjuste` | ajuste valor/consumo da conta emitida em campo |

### 5.2 Conta: cabeçalho

Colunas relevantes de `faturamento.conta` (fato): `cnta_amreferenciaconta` (competência), `cnta_amreferenciacontabil`, `cnta_amreferenciabaixacontabil`, localização do imóvel (`loca_id`, setor, quadra, lote, sublote), `cnta_nnconsumoagua`, `cnta_nnconsumoesgoto`, `cnta_nnconsumorateioagua/esgoto`, `cnta_vlagua`, `cnta_vlesgoto`, `cnta_vldebitos`, `cnta_vlcreditos`, `cnta_vlimpostos`, `cnta_vlrateioagua/esgoto`, `cnta_pcesgoto`, `cnta_pccoleta`, `cnta_dtvencimentoconta`, `cnta_dtvencimentooriginal`, `cnta_dtvalidadeconta`, `cnta_dtemissao`, `cnta_dtinclusao`, `cnta_dtretificacao`, `cnta_dtcancelamento`, `cnta_dtrevisao`, `cnta_nnretificacao`, `cnta_nnalteracoesvencimento`, leituras anterior/atual (+ poço), `cnta_nnvolumepoco`, `cnta_nnboleto`, `cnta_dgverificadorconta`, `cnta_iccobrancamulta` (1 já cobrou / 2 não), `cnta_icdebitoconta`, `cnta_icfaturamentoconcluido`, `dcst_idatual/idanterior`, motivos (`cmcn` cancelamento, `cmic` inclusão, `cmrt` retificação, `cmrv` revisão), `cstf_id` (snapshot da tarifa), `iper_id` (perfil), `fttp_id`, `last_id`/`lest_id` (situações das ligações **no momento**), `ftgr_id`, `rota_id`, `parc_id`, `cnta_idorigem` (conta de origem em retificação/inclusão), `usur_id`.

### 5.3 Estados

`DebitoCreditoSituacao` (vale para conta, débito a cobrar, crédito a realizar e guia):

| id | Estado | | id | Estado |
|---|---|---|---|---|
| 0 | NORMAL | | 7 | ENTRADA_DE_PARCELAMENTO |
| 1 | RETIFICADA | | 8 | DEBITO_PRESCRITO |
| 2 | INCLUIDA | | 9 | PRE_FATURADA (impressão simultânea) |
| 3 | CANCELADA | | 10 | CARTAO_CREDITO |
| 4 | CANCELADA_POR_RETIFICACAO | | 11 | ERRO_PROCESSAMENTO |
| 5 | PARCELADA | | 12 | DEBITO_PRESCRITO_CONTAS_INCLUIDAS |
| 6 | ARRASTADA | | 13 | PAGA |

Estados auxiliares: revisão (`cmrv_id` não nulo = conta retida; motivos 91 estouro, 98 pagamento comprovado, 100 baixo consumo, 101 entrada de parcelamento, 102 antiguidade, 105/106 em contrato de parcelamento), `ContaTipo` de impressão (1 retida EC, 2 retida BC, 3 cliente responsável, 4 débito automático, 5 normal, 6 débito automático com cliente responsável), `ImovelContaEnvio` (1 cliente responsável, 2 imóvel, 3 não pagável no imóvel/pagável no responsável, 4 e-mail, 5 imóvel+e-mail, 6/7 braille, 9 responsável no fim do grupo).

### 5.4 Etapas do faturamento mensal (ordem, como o código as executa)

1. **Cronograma**: `inserirFaturamentoCronograma`/`FaturamentoGrupoCronogramaMensal` + atividades com datas previstas; `inserirComandoAtividadeFaturamento` seleciona rotas e comanda; `executarAtividadeFaturamento` (UC0111) dispara a tarefa batch por rota.
2. **PRÉ-FATURAR** (`preFaturarGrupoFaturamento`, atividade 0): só para impressão simultânea; cria conta PRE_FATURADA e `MovimentoContaPrefaturada`.
3. **Gerar dados de leitura** (atividade 1) → campo → **registrar leituras** (3) → **consistir e calcular consumos** (9) → **rateio** (UC0103). (Seção 2/3.)
4. **Débitos periódicos automáticos** antes de faturar: acréscimos por impontualidade (juros/multa/correção), taxa de entrega em outro endereço (`gerarTaxaEntregaDeContaEmOutroEndereco`), tarifa mínima de imóvel cortado (`gerarTaxaPercentualTarifaMinimaCortado`), doação (`gerarDebitoACobrarDoacao`), bônus tarifa social, crédito de situação especial (`gerarCreditoSituacaoEspecialFaturamento`), religar cortados com consumo real. (Ordem exata não confirmada: **inferência**.)
5. **SIMULAR_FATURAMENTO** (8): mesma rotina com `gerarAtividadeGrupoFaturamento=false` → só `ResumoFaturamentoSimulacao`.
6. **FATURAR_GRUPO** (5) = UC0113 `faturarGrupoFaturamento`:
```
para cada rota comandada (unidade de processamento = rota; MDB por rota; pode reiniciar):
    prepararFaturamentoImovel(atividade, rota)
    página de 3.000 imóveis (pesquisarImovelGrupoFaturamento):
        para cada imóvel: faturarImovel(ref) e faturarImovelAntecipado(ref+1, regra de novembro/dezembro p/ órgão público)
    persiste resumo da simulação
    atualiza sequencial de rota (rota_atualizacao_seq → imovel.imov_nnsequencialrota)

faturarImovel:  se NÃO existe conta da referência (ou existe PRE_FATURADA) → determinarFaturamentoImovel
determinarFaturamentoImovel(imovel):
    consumoAgua/Esgoto = consumo_historico do mês
    se NÃO paralisado por situação especial:
       se permiteFaturamentoParaAgua ou ...ParaEsgoto → valores = determinarValoresFaturamentoAguaEsgoto
            (UC0105 mínimo, datas de leitura, % esgoto [alternativo], % coleta, UC0120 calcularValoresAguaEsgoto)
       gerarConta? = verificarGeracaoConta (ver 5.5)
       se gerar:
          débitos = gerarDebitoCobrado  (prestação do mês de cada DebitoACobrar; última prestação absorve centavos)
          créditos = gerarCreditoRealizado (FaturamentoUtil: limita ao saldo da conta; resíduo vai para o mês seguinte)
          impostos = gerarImpostosDeduzidosConta(base = água+esgoto+débitos−créditos)
          conta = gerarConta  (vencimento, rateio, validade = último dia do mês de [venc + parm_nnmesesvalidadeconta], DV, snapshot)
          conta_categoria + conta_categoria_consumo_faixa + cliente_conta + impostos + debito/credito cobrado/realizado
          atualiza DebitoACobrar (prestação+1) e CreditoARealizar (prestação+1, resíduo)
          conta_impressao (tipo, empresa, cliente responsável, sequencial)
          débito automático → DebitoAutomaticoMovimento (se não em revisão)
          cnta_icfaturamentoconcluido = 1   (marcador de idempotência/consistência)
```
7. **Distribuição/Transmissão** (atividades 6 e 7): emissão de contas (`emitirContas`), arquivo para gráfica/impressão (`gerarArquivoTextoFaturamento`, braille, e-mail), fatura de cliente responsável (`gerarFaturaClienteResponsavel`).
8. **Encerrar faturamento do mês** (UC0155 `encerrarFaturamentoMes`, por localidade): valida que todos os grupos foram faturados (`validarGruposFaturadosParaEncerrarFaturamentoMensal`), gera `ResumoFaturamento` por localidade × categoria × item contábil (receita bruta, cancelada, devoluções, parcelamento curto/longo prazo, créditos por origem), lançamentos para agência reguladora; é bloqueado se já existe resumo da localidade (`atencao.resumofaturamento_ja_existente`). Existem `transferirContasParaHistorico`, `transferirDebitosACobrarParaHistorico`, `transferirCreditoARealizarParaHistorico` e `atualizarAnoMesFaturamento`; que o encerramento os chame e avance `parm_amreferenciafaturamento` é **inferência** (o método tem ~5.600 linhas e não foi lido por inteiro).

### 5.5 Quando NÃO gerar conta (`verificarNaoGeracaoConta`, e a versão nova `ControladorAnaliseGeracaoConta`)

Versão antiga: não gera quando (valorÁgua = 0 e valorEsgoto = 0, ou pré-faturamento sem situação ligada) **E** (não há débito a cobrar, ou o imóvel está paralisado, ou todos os débitos já têm pagamento, ou há créditos só devolvidos e todos os débitos têm `dbtp_icgeracaoconta = 2`). Versão nova (imóvel com `useNovaChecagemGerarConta` ou de condomínio):
```
gerar = situaçãoImóvelGeraConta  OU  débitosECréditosGeramConta  OU  condomínioSemConsumo
situaçãoImóvelGeraConta = NÃO( semConsumo OU (sem água ligada E sem esgoto ligado E não pertence a condomínio) )
débitosECréditosGeramConta = (há débito a cobrar sem pagamento e sem revisão) E NÃO paralisado,
                             e, se não há crédito a realizar (ou créditos já devolvidos), existe débito com indicadorGeracaoConta = SIM
condomínioSemConsumo = semConsumo E sem água E sem esgoto E pertence a condomínio
```

### 5.6 Débito a cobrar e crédito a realizar (parcelas)

* **Débito**: `prestação = TRUNCA2( valorDébito / nPrestações )`; na última prestação `(nCobradas = nPrestações − bônus − 1)` `prestação += valorDébito − prestação×nPrestações` (os centavos vão na última). Repartição por categoria via UC0185 `obterValorPorCategoria`: `fator = valor / Σeconomias (2 casas, DOWN)`; `valorCat = fator × economiasCat` (DOWN); a diferença para o total vai para a **primeira** categoria.
* **Crédito** (`FaturamentoUtil.atualizarCreditosARealizar`):
```
totalACobrar = água + esgoto + débitos        (no pré-faturamento: 1 fictício)
para cada crédito a realizar enquanto totalACobrar > 0:
    parcela = crédito.valor / nPrestações [2 casas, DOWN] + resíduoMêsAnterior
    se é a última prestação e já concedido na referência: parcela = (resíduo ≠ 0 ? resíduo : valor − parcela×(n−1) )
    totalACobrar −= parcela
    se totalACobrar < 0:  resíduo = −totalACobrar ; parcela −= resíduo ; totalACobrar = 0   (crédito nunca excede a conta)
    senão: resíduo = 0
    soma parcelas
```
Oráculo existente (teste do GSAN): crédito de R$ 35,00 em 6 prestações → parcelas 5,83 ×5 e **5,85** na última.

### 5.7 Vencimento (`determinarVencimentoConta`)

```
base = facr_dtcontavencimento da rota
se imóvel.diaVencimento ≠ 0 e não é extrato:  dia = imóvel.dia ; mesSeguinte = imóvel.indicadorVencimentoMesSeguinte
senão cliente responsável: dia = cliente.dataVencimento ; ou último dia do mês (extrato)
se não há dia alternativo → base
se mesSeguinte = NÃO:
     se diaBase ≤ dia : vence no dia alternativo (limitado ao último dia do mês da base)
     senão: candidato = DIA DA ROTA no mês da base (o legado sobrescreve o dia alternativo pelo dia da rota) ;
            se candidato ≤ hoje + parm_nnmindiasemsvcmto → mesmo dia da rota no mês seguinte, senão candidato
senão: vence no mês seguinte à base, dia alternativo (limitado ao último dia)
entrega por correio (imóvel conta envio 1 ou 3, sem dia alternativo, sem débito automático): + parm_nndiasadicionaiscorreios
validade = último dia do mês de (vencimento + parm_nnmesesvalidadeconta)
```

### 5.8 Cancelar, retificar, revisar, alterar vencimento, incluir

* `cancelarConta` (UC0147): conta NORMAL/RETIFICADA/INCLUÍDA com referência contábil já contabilizada (< `parm_amreferenciafaturamento`) → vira CANCELADA (3) (ou `DEBITO_PRESCRITO 8` se motivo = prescrição e `verificarPossibilidadePrescricaoConta`), guarda situação anterior se NORMAL do mês corrente, registra motivo, usuário, transação, e **exige RA** (registro de atendimento) salvo permissão especial `cancelarContaSemRA`; reflete em negativação SPC/SERASA. Se a retificação/inclusão ainda não foi contabilizada: **remove** a conta e restabelece a anterior.
* `retificarConta` (UC0150, `ControladorRetificarConta`, **só lido por cabeçalho/constantes**): cria conta nova com motivo de retificação (`cmrt`; o campo `cmrt_nnocorrmesmomotivonoano` sugere limite de ocorrências por motivo/ano e `cmrt_iccompetenciaconsumo` indica motivo que mexe em consumo/leitura); pelos estados e por `cnta_idorigem`, a antiga fica CANCELADA_POR_RETIFICACAO (4) e a nova RETIFICADA (1) (**inferência**); recalcula categorias, faixas, débitos, créditos, impostos. Há retificação em lote (`retificarConjuntoConta`, `RETIFICAR_CONJUNTO_CONTAS_CONSUMO`) e correção de pagamento com diferença de R$ 2,00 (`retificarContaPagamentosDiferenca2Reais`).
* `colocarRevisaoConta`/`retirarRevisaoConta` (UC0148): `cmrv_id` e `cnta_dtrevisao`; conta em revisão não gera acréscimo nem entra na cobrança; revisão automática por estouro/baixo consumo e por faixa de valor (`conta_revisao_fx_valor`).
* `alterarVencimentoConta` (UC0151): limite de alterações (`parm_nnlimitealtvencimento`, `parm_nnmesesminaltvcmto`, `cnta_nnalteracoesvencimento`), `cnta_icalteracaovencimento`.
* `inserirConta` (UC0145): inclusão manual de conta (motivo `cmic`: 41 transferência de débito, 46 recuperação de crédito, 47 cancelamento de parcelamento).
* Cancelamento/retificação/alteração de vencimento **em lote** (por grupo, por cliente, por coleção) via MDB com e-mail de conclusão (`BatchBatchFaturamento`: `CANCELAR_CONJUNTO_CONTA*`, `ALTERAR_VENCIMENTO_CONJUNTO_CONTA*`).

### 5.9 Segunda via e emissão

* `emitirContas` (lote por grupo; separa contas normais, retidas por estouro/baixo consumo, débito automático, cliente responsável, órgão público) e `emitir2ViaContas` (+ `ContaSegundaViaBO`, DTO `ContaSegundaViaDTO`; cobra `TAXA_2_VIA_CONTA (101)` se perfil gera débito de 2ª via; pode emitir "sem código de barras").
* Conteúdo impresso (campos do `relatorio2ViaConta.jrxml`, conceito): identificação (matrícula, inscrição, localidade, gerência), cliente/responsável, endereço, situação de água/esgoto, grupo/rota de entrega, dados de consumo dos 6 meses anteriores, leitura anterior/atual (+ informadas), datas, dias de consumo, consumo medido/faturado, **consumo médio diário**, tipo de consumo, anormalidade de consumo/leitura, economias por categoria (RES/COM/IND/PUB), nº do hidrômetro, linhas de tarifa por faixa, débitos e créditos, impostos retidos (descrição, alíquota, base, valor), mensagens (3 partes), **qualidade da água (valor médio, padrão, conformidade para turbidez, cor, cloro, flúor, coliformes totais e termotolerantes)**, agência reguladora (nome, telefone, e-mail), código de barras/representação numérica, débito automático, data de pagamento / "conta paga", boleto, valor total, validade, empresa (CNPJ/endereço), usuário/"pela INTERNET" na emissão.
* Impressão térmica (`ImpressaoContaImpressoraTermica`, `ContaImpressaoTermicaQtde`) para impressão simultânea; Braille (`gerarTxtImpressaoContasBraille`); e-mail (`envioEmailContaParaCliente`, `qtdiaenvioemailconta`).

### 5.10 Controladores/Fachada de faturamento (lista pedida)

| Classe | Papel |
|---|---|
| `ControladorFaturamentoFINAL` (`ControladorFaturamento` herda) | núcleo: cronograma, faturar grupo, conta, tarifa, guia, crédito, débito, encerramento, emissão, resumos |
| `ControladorMicromedicao` | leitura, consumo, rateio, hidrômetro, telemetria, arquivos |
| `controladores.ControladorAnaliseGeracaoConta` | regra de geração de conta |
| `controladores.ControladorDebitoACobrar` | débitos vigentes/sem pagamento |
| `controladores.ControladorRetificarConta` | retificação |
| `FaturamentoUtil` | atualização de créditos a realizar |
| `ControladorBatchFaturamento` (MDB) | cancelar/retificar/alterar vencimento em lote |
| `batch.faturamento.Batch*MDB` / `TarefaBatch*` | execução por rota (faturar grupo, emitir contas, encerrar mês, bônus social, acréscimos, taxas, prescrição, resumos) |
| `faturamento.bo.ContaSegundaViaBO`, `FaturamentoSeletivoBO` | 2ª via; faturamento seletivo de um imóvel |
| `fachada.Fachada` / `FachadaBatch` | façade (singleton) |

---

## 6. Quadro "o que existe hoje no SIGSAN" (para dimensionar)

Fato (diário de bordo + template de tenant): `consumer_units` já tem `matricula`, `category_id`, `tariff_type_id`, `water_economies`, `sewage_economies`, `reading_route_id`, `reading_sequence`, `meter_id` (todos **UUID soltos, sem tabela de destino**), `status`, datas de ligação; `service_orders`, `addresses` (PostGIS), `documents`. `commercial/*` e `operations/*` estão sem POM/código. Não há API REST nem `@SpringBootApplication`. `LossesService` consome placeholders de volume faturado/medido: **billing e metering reais são pré-requisito do balanço hídrico**.

---

## 7. Anti-padrões e armadilhas do legado (não replicar)

| # | Achado | Local | Efeito |
|---|---|---|---|
| 1 | `new BigDecimal(double)` em fatores (`0.4`, `0.7`, `1.3`) | `calcularFaixaLeituraEsperada` | Ex.: média 105 → `0,7×105 = 73,4999999…` arredonda para 73, não 74. **Análise por IEEE-754, não executado.** Usar `BigDecimal("0.7")` |
| 2 | Percentual de esgoto arredondado a 2 casas antes de multiplicar (`87,5% → 0,88`) | `getCalcularValoresAguaEsgotoHelper` | Cobrança 0,57 % maior que o contratado; definir política explícita |
| 3 | Truncamento por divisão inteira em consumo por economia e média | vários | Perda sistemática de m³ (compensada em 3.8) |
| 4 | Virada de hidrômetro: o ramo principal usa `10^d`, o ramo do ajuste mensal usa `10^d − 1` | `dadosFaturamentoLeituraMenorAnterior` × `ajusteMensalConsumo` | Inconsistência de 1 m³ |
| 5 | Rateio por valor: `valor + 0,005` e `divide(qtd, ROUND_FLOOR)` | `calcularValorRateioPorEconomia` | Soma dos rateios ≠ valor rateado; usar "maior resto" |
| 6 | Sorteio de amostragem por `(id+segundos) × % mod 100` | faixa falsa e fiscalização | Previsível; usar CSPRNG com semente por rota/mês auditável |
| 7 | Juros mensais contam **meses de calendário** (`Util.dataDiff`) | `calcularAcrescimoPorImpontualidade` | 1 dia de atraso pode gerar 1 mês de juros (venc. 31/01, pago 01/02) |
| 8 | Valores mágicos de ids de domínio no código (ids de `LeituraAnormalidade` 30/32/38/81-83, tipo de cálculo 1/2/3/4) | `LeituraAnormalidade`, `TarifaTipoCalculo` | Seeds acoplados a código; no SIGSAN usar `code` simbólico + enum |
| 9 | Forks por concessionária e `getLocalHomePorEmpresa` | `Controlador*{CAERN,...}SEJB` | Divergência de regra por cliente; usar estratégia por tenant |
| 10 | `System.out.println` com dados de cliente em cálculo (`quantTotalEconomia`, `Valor agua`, `ERRO...SemTarifaVigencia`) | faturamento | Vazamento de dado pessoal em log (LGPD) |
| 11 | Código de produção com `try/catch` que engole exceção (`new ControladorException(...)` sem `throw`) | `pesquisarConsumoMinimoTarifaCategoriaPorCategoria` etc. | Conta com consumo mínimo errado sem erro |
| 12 | Transação por rota enorme, estado em `Collection` crua, HQL concatenado | tudo | Difícil testar; usar domínio puro + repositório |

---

## 8. (a) Fluxo do ciclo mensal proposto para o SIGSAN

Princípios: motor de cálculo **puro** (sem I/O) em `commercial/sigsan-billing` e `operations/sigsan-reading`; jobs idempotentes por **(tenant, grupo, referência, rota)**; eventos em `sigsan-events` (Kafka) entre módulos; tudo versionado por **competência** e por **vigência**.

```
M0  Abertura do ciclo               billing: cria billing_cycle(group, ref) + atividades por rota com datas previstas (calendário/feriados)
M1  Geração do roteiro de leitura   reading: congela reading_task por imóvel/medidor (leitura anterior, média, faixa esperada,
                                    amostra de fiscalização / faixa falsa) → arquivo/pacote para app (Capacitor) ou API
M2  Campo                           reading: app offline (leitura, foto, GPS, anormalidade); estados da tarefa
                                    DISPONIVEL → LIBERADO → EM_CAMPO → TRANSMITIDO → FINALIZADO(…)
M3  Recebimento                     reading: valida cada leitura (regras 2.4) → aceita / rejeita com motivo; telemetria entra pelo mesmo contrato
M4  Crítica/consumo                 metering+reading: ConsumptionCalculator(imóvel, medição, parâmetros, histórico) → consumption_record
                                    (tipo, anormalidade, flags de revisão); grava medicao_historico e consumo em lote por rota
M5  Rateio de condomínio            billing: depende de M4 de TODOS os micros e do macro
M6  Revisão de anomalias            billing/field: fila de estouro/alto/baixo, relereitura, OS (field), carta/aviso (portal)
M7  Débitos periódicos automáticos  billing/dunning: acréscimos, taxas, bônus social, créditos de situação especial
M8  Simulação                       billing: roda M9 em modo "dry-run" → resumo por categoria/localidade, comparação com mês anterior
M9  Faturamento do grupo            billing: por rota → TariffEngine + CreditApplier + TaxWithholding + BillFactory → bill (+ itens)
                                    "pré-condição de geração" (5.5) e vencimento (5.7) em serviços separados
M10 Emissão/distribuição            billing/portal: PDF 2ª via, boleto/PIX, e-mail, braille, arquivo gráfica, fatura agrupada
M11 Pós-faturamento                 collection/dunning/accounting: eventos BillIssued; débito automático; contabilização por item contábil
M12 Encerramento                    accounting: consolidação por localidade×categoria×item, congelamento (bill→bill_history), avança competência
```
Pontos de decisão a registrar como ADR: arredondamento (política única), percentual de esgoto (fator exato × 2 casas), unidade de vigência (data × competência), consumo "perdido" no múltiplo de economias, rateio por consumo × por valor.

---

## 9. (b) Esboço de modelo de dados (por tenant; `tenant_id` + RLS em todas)

Convenções do SIGSAN: `id UUID`, `tenant_id UUID NOT NULL`, `created_at/updated_at/created_by/updated_by`, RLS por `app.current_tenant`. Valores monetários `NUMERIC(14,2)`, consumos `INTEGER`, percentuais `NUMERIC(7,4)`.

### 9.1 `operations/sigsan-metering` (hidrômetro e ligações)

| Tabela | Colunas essenciais |
|---|---|
| `meter_model` (marca/capacidade/classe/diâmetro) | `brand`, `capacity_code`, `metrological_class`, `diameter`, `digits`, `revision_interval_days` |
| `meter` | `serial_number`, `model_id`, `digits`, `manufacture_year`, `status` (INSTALLED, IN_MAINTENANCE, AVAILABLE, STOLEN, WRITTEN_OFF), `storage_location_id`, `last_revision_on`, `qr_code` |
| `meter_installation` | `meter_id`, `consumer_unit_id`, `kind` (WATER/WELL), `installed_on`, `installed_reading`, `removed_on`, `removed_reading`, `seal`, `protection`, `location`, `is_replacement` — **intervalo exclusivo por (unidade, kind)** (`EXCLUDE USING gist`) |
| `meter_movement` | `meter_id`, `from_location`, `to_location`, `reason`, `occurred_at`, `user_id` |
| `connection_status_ref` (situação da ligação água/esgoto) | `kind`, `code`, `is_billable`, `min_consumption_to_bill`, `has_supply`, `is_real_consumption`, `days_cut_threshold` + tabela `connection_status_consumption_type` (quais tipos de consumo permitem faturar) |

### 9.2 `operations/sigsan-reading`

| Tabela | Colunas essenciais |
|---|---|
| `reading_route` | `code`, `sector_id`, `billing_group_id`, `reader_id`, `reading_mode` (CONVENTIONAL, COLLECTOR, MOBILE, SIMULTANEOUS_PRINT), `monthly_adjust` bool, `adjust_days`, `fake_range_pct`, `inspection_pct`, `max_units` |
| `reader` | `person_id`/`user_id`, `employer_id` (empresa terceirizada), `device_id` |
| `reading_task` (movimento) | `cycle_id`, `route_id`, `consumer_unit_id`, `meter_installation_id`, `kind`, `previous_reading`, `previous_read_on`, `expected_min`, `expected_max`, `fake_min/max`, `avg_consumption`, `last_6_consumptions int[]`, `status`, `sequence`, `assigned_reader_id`, `package_id`, `sampled_for_inspection` |
| `reading_package` (arquivo) | `route_id`, `reader_id`, `device_id`, `status` (AVAILABLE, RELEASED, IN_FIELD, TRANSMITTED, FINISHED_*), `released_at`, `received_at`, `finish_reason`, `payload_ref` |
| `meter_reading` (medicao_historico) | `task_id`, `cycle_id`, `kind`, `previous_reading_billed/informed`, `previous_date_billed`, `current_reading_informed`, `current_date_informed`, `current_reading_billed`, `current_date_billed`, `measured_consumption`, `informed_consumption`, `reading_status_prev/current` (REALIZED/CONFIRMED/…), `anomaly_informed_id`, `anomaly_billed_id`, `consecutive_anomaly_count`, `lat`, `lon`, `photo_ref`, `reviewed` bool |
| `reading_anomaly` (catálogo) | `code`, `description`, `requires_reading`, `no_meter_only`, `generates_service_order_type`, `factor_without_reading`, `factor_with_reading`, `reading_effect_without/with` (PREV_PLUS_AVG, PREV, PREV_PLUS_CONSUMPTION, INFORMED, NORMAL), `consumption_effect_without/with` (NONE→AVG, MINIMUM, AVG, NORMAL, MAX_AVG_MEASURED, MIN_AVG_MEASURED, NOT_METERED, FIXED), `suspend_after_n_times`, `suspend_months`, `loses_social_tariff` |
| `consumption_anomaly` (catálogo) + `consumption_anomaly_action` | por (`anomaly`, `category`, `profile`) × mês 1/2/3: `effect`, `factor`, `bill_message`, `generate_letter`, `service_type_id`, `ra_specification_id` |
| `reading_inspection` / `false_range_log` | resultado da fiscalização/faixa falsa para auditoria de leiturista |
| `reading_event_raw` | payload bruto de telemetria/coletor (retém para reprocessar) |

### 9.3 `commercial/sigsan-billing`

| Tabela | Colunas essenciais |
|---|---|
| `billing_group` | `code`, `description`, `current_reference` (AAAAMM), `due_day`, `due_next_month` |
| `billing_cycle` | `group_id`, `reference`, `status` (OPEN, READING, CALCULATED, SIMULATED, BILLED, ISSUED, CLOSED) |
| `billing_activity` (catálogo) | `code` (GENERATE_READING_FILE, READ, REGISTER_READINGS, CONSISTENCY_AND_CONSUMPTION, GENERATE_INSPECTION, SIMULATE, BILL, DISTRIBUTE, TRANSMIT, PRE_BILL), `order`, `predecessor`, `mandatory`, `repeatable`, `commandable` |
| `billing_cycle_activity` / `billing_cycle_activity_route` | `planned_on`, `commanded_at`, `done_at`, `due_date_for_bills` (por rota), `job_id`, `status` |
| `job_unit` | (`job_id`, `route_id` ou `locality_id`), `status`, `started_at`, `ended_at`, `error`, `attempts` — base de reinício |
| `consumption_record` (consumo_historico) | `unit_id`, `kind` (WATER/SEWAGE), `reference`, `billed_consumption`, `average_basis_consumption`, `average`, `minimum`, `type` (REAL, ADJUSTED_AVG, METER_AVG, INFORMED, NOT_METERED, ESTIMATED, MIN_FIXED, NO_CONSUMPTION, UNIT_AVG, SPECIAL_FIXED), `anomaly_id`, `billable` bool, `special_status_id`, `well_type_id`, `condo_macro_record_id`, `condo_linked_consumption`, `apportioned_consumption`, `apportion_type`, `collection_pct`, `route_id` |
| `tariff` | `code`, `name`, `calc_type` (BLOCKS, DIRECT_BLOCK, FORMULA), `formula_ref` |
| `tariff_version` | `tariff_id`, `valid_from DATE` (+ constraint de não sobreposição), `status` |
| `tariff_category_rule` | `version_id`, `category_id`, `subcategory_id NULL`, `min_consumption`, `min_charge`, `economy_factor` |
| `tariff_block` | `rule_id`, `from_m3`, `to_m3`, `price_per_m3` |
| `min_consumption_by_area` / `min_consumption_by_param` | `category/subcategory`, `valid_from_ref`, `limit`, `consumption` |
| `bill` (conta) | `unit_id`, `reference`, `group_id`, `route_id`, `status` (NORMAL, RECTIFIED, ADDED, CANCELLED, CANCELLED_BY_RECTIFICATION, INSTALLMENT, CARRIED, INSTALLMENT_DOWN_PAYMENT, WRITTEN_OFF_PRESCRIBED, PRE_BILLED, CARD, ERROR, PAID), `previous_status`, `water_value`, `sewage_value`, `debits`, `credits`, `taxes`, `condo_water_apportion`, `condo_sewage_apportion`, `water_consumption`, `sewage_consumption`, `sewage_pct`, `collection_pct`, `reading_prev/curr`, `due_date`, `original_due_date`, `valid_until`, `issued_on`, `rectification_count`, `due_change_count`, `late_fee_charged`, `review_reason_id`, `review_on`, `cancel_reason_id`, `origin_bill_id`, `tariff_snapshot JSONB`, `water_status_snapshot`, `sewage_status_snapshot`, `profile_snapshot`, `barcode`, `completed bool` (**único `(tenant, unit, reference)` para status vivo**) |
| `bill_category` / `bill_category_block` | por categoria: economias, valores, consumos, tarifa/consumo mínimo (água e esgoto); por faixa: limites, preço, valores |
| `bill_debit_item` / `bill_credit_item` | linhas da conta com `type`, `installment_no/total`, `value`, `origin_receivable_id` + rateio por categoria |
| `debit_receivable` / `credit_receivable` | `type`, `total`, `installments_total`, `installments_done`, `monthly_interest_pct`, `bonus_installments`, `residual_prev` (crédito), `origin` (ordem de serviço, parcelamento, pagamento em duplicidade…), `status`, `review_reason` |
| `debit_type` / `credit_type` / `credit_origin` | catálogo (com `accounting_item`, `generates_bill`, `suggested_value`, `limit_value`, `auto_generated`) |
| `payment_slip` (guia) + `payment_slip_item` | cobrança avulsa por cliente/imóvel |
| `tax_type` / `tax_rate` / `bill_tax` | IR, CSLL, COFINS, PIS por referência; retenção por conta com `base`, `rate`, `value` |
| `special_billing_status` + `_history` + `_command` | situação especial (paralisar/fixar), intervalo, consumo/volume fixo, comando em lote por faixa |
| `late_fee_index` | `reference`, `fine_pct`, `interest_pct`, `monetary_factor`, `fine_is_monthly`, `fine_limit_pct`, `interest_is_monthly`, `interest_limit_pct` |
| `billing_parameters` (por tenant) | `average_months`, `max_average_months`, `rateio_increment_max`, `rateio_decrement_max`, `days_variation`, `non_metered_by_tariff`, `non_metered_calc` (AREA/POINTS/RESIDENTS), `tariff_by_category`, `valid_months`, `min_days_issue_to_due`, `extra_days_mail`, `social_bonus_pct`, `fine_days_threshold`, `correction_months`, `due_change_limit` |
| `water_quality_monthly` | já pertence a `intelligence/sigsan-quality`; a conta apenas **referencia** (por localidade/setor/fonte/mês) |

---

## 10. (c) Casos de teste de cálculo (entradas e resultado esperado, calculados à mão)

Convenções: `m³`, valores em R$. **Tarifa T1** (vigência 01/01/2026, `calc_type = BLOCKS`):

| Categoria | Consumo mín. (m³) | Tarifa mín. | Faixa 1 (fim) | Faixa 2 (fim) | Faixa 3 (fim) |
|---|---|---|---|---|---|
| RES | 10 | 30,00 | até 20 → 4,00 | até 50 → 6,00 | até 999999 → 9,00 |
| COM | 10 | 50,00 | até 20 → 7,00 | até 50 → 10,00 | até 999999 → 14,00 |

(Faixa 1 cobre de 11 a 20 m³ = 10 m³; o código mede cada faixa por `fim − fimAnterior`, começando do consumo mínimo.)

### 10.1 Tarifa por faixas (água)

| ID | Entrada | Cálculo | Esperado |
|---|---|---|---|
| T-01 | RES, 1 economia, consumo 8 | ≤ mínimo → só tarifa mínima | água = **30,00**; consumo da categoria = 8 |
| T-02 | RES, 1 eco, consumo 10 | excesso 0 | **30,00**; consumo cat. 10 |
| T-03 | RES, 1 eco, consumo 27 | 30 + 10×4,00 + 7×6,00 = 30+40+42 | **112,00** |
| T-04 | RES, 1 eco, consumo 60 | 30 + 10×4 + 30×6 + 10×9 = 30+40+180+90 | **340,00** |
| T-05 | RES, 2 eco, consumo 35 | min imóvel 20; excesso 15; por eco 15 DIV 2 = 7; cada eco 30+7×4 = 58; categoria 2×58 | água = **116,00**; consumo da categoria = (10+7)×2 = **34** |
| T-06 | RES 1 + COM 1, consumo 25 | min 20; excesso 5; por eco 5 DIV 2 = 2; RES 30+2×4 = 38; COM 50+2×7 = 64 | água = **102,00** (38,00 + 64,00); consumo por categoria 12 + 12 = **24** |
| T-07 | RES, 3 eco, consumo 28 | min 30; excesso −2 ≤ 0; por eco = 28 DIV 3 = 9 < 10 → consEco 9; valor por eco = tarifa mínima | água = 3 × 30,00 = **90,00**; consumo cat. = 27 |

### 10.2 Consumo múltiplo de economias (antes da tarifa)

| ID | Entrada | Esperado |
|---|---|---|
| M-01 | RES, 2 eco, consumo medido 35, mínimo 10/eco | resto 1 → consumo faturado = **34** (T-05) |
| M-02 | RES 1 + COM 1, consumo 25 | consumo faturado = **24** (T-06) |
| M-03 | RES, 3 eco, consumo 28 (excesso ≤ 0) | `resto = 28 MOD 3 = 1` → consumo faturado = **27**; leitura faturada recua 1 se `leituraAtual = anterior + consumo` |

### 10.3 Esgoto

| ID | Entrada | Esperado |
|---|---|---|
| E-01 | consumo água 27, `pcesgoto` 80, `pccoleta` 100 (RES, 1 eco) | volume esgoto = 27; valor = 112,00 × 0,80 = **89,60**; conta água+esgoto = **201,60** |
| E-02 | consumo água 27, `pcesgoto` 100, `pccoleta` 80 | volume = ARRED(27×0,80 = 21,6) = **22**; valor = 30 + 10×4 + 2×6 = **82,00** |
| E-03 | consumo 8, `pcesgoto` 80 | tarifa mínima de esgoto = 30,00 × 0,80 = **24,00** (valor de esgoto = 24,00) |
| E-04 | `pcesgoto` 87,5 sobre valor 112,00 (legado) | fator = 0,88 → **98,56** (matemática exata daria 98,00). **Caso de política**: o SIGSAN deve decidir; teste marcado "compatibilidade GSAN" |
| E-05 | consumo 12, 1 eco, `pcesgoto` 80, `pcalternativo` 50, `nnconsumopcalternativo` 15 | 12 ≤ 15 → usa MIN(80,50)=50; tarifa(12) = 30 + 2×4 = 38,00 → **19,00** (sem alternativo seria 30,40) |
| E-06 | esgoto sem água (poço), volume poço 14, `pccoleta` 100, `pcesgoto` 100 | tarifa(14) = 30 + 4×4 = **46,00** |

### 10.4 Duas vigências no período (rateio por dias)

Leitura anterior 05/09/2026, atual 04/10/2026; vigência A (T1) até 19/09, vigência B a partir de 20/09 com RES: mín 33,00, faixa 1 = 4,40, faixa 2 = 6,60. Consumo 27, 1 eco RES.

| Passo | Valor |
|---|---|
| `diasLeitura` = (04/10 − 05/09) + 1 | 30 |
| A: 05/09 a 19/09 → 15 dias → fator 0,5000 | 112,00 × 0,5 = 56,00 |
| B: 20/09 a 04/10 → 15 dias → fator 0,5000; valor B = 33 + 10×4,40 + 7×6,60 = 123,20 | 61,60 |
| **Esperado (T-08)** | **117,60** |

Variante T-09: troca de vigência em 01/10 (leitura 05/09→04/10): A = 26 dias (05/09..30/09) → 26/30 = 0,8667; B = 4 dias (01/10..04/10) → 4/30 = 0,1333. A = 112,00×0,8667 = 97,0704 → **97,07**; B = 123,20×0,1333 = 16,4226 → **16,42**; total **113,49** (a soma de fatores 0,8667+0,1333 = 1,0000; os 2 arredondamentos independentes podem divergir 0,01 do total exato 113,49 — manter a regra do legado ou garantir "fecha em 1,0000").

### 10.5 Faixa de leitura esperada

| ID | Média | Leitura anterior | Dígitos | Esperado |
|---|---|---|---|---|
| R-01 | 8 | 1000 | 5 | [1000 , 1018] |
| R-02 | 15 | 1000 | 5 | [1006 , 1024] |
| R-03 | 25 | 1000 | 5 | [1013 , 1038] (12,5→13; 37,5→38) |
| R-04 | 60 | 1000 | 5 | [1036 , 1084] |
| R-05 | 150 | 1000 | 5 | [1105 , 1195] |
| R-06 | 25 | 99990 | 5 | bruto [100003 , 100028] → virada → **[3 , 28]** |
| R-07 | 100 / 101 | 1000 | 5 | média 100 usa 0,6–1,4 → [1060 , 1140]; média 101 usa 0,7–1,3 → [1071 , 1131] (70,7→71; 131,3→131) |
| R-08 (compat.) | 105 | 1000 | 5 | matemática exata: [1074 , 1137]; **legado `new BigDecimal(0.7)` dá 1073** (armadilha 1). Teste de regressão para decidir a política |

### 10.6 Consumo medido, leitura menor, virada

| ID | Entrada | Esperado |
|---|---|---|
| L-01 | anterior 1000, atual 1027, situação anterior REALIZADA | consumo 27; tipo REAL |
| L-02 | idem, situação anterior NÃO_REALIZADA | consumo 27; tipo **ESTIMADO** |
| L-03 | 5 dígitos, anterior 99950, atual 30, média 60 | calc = 30+100000−99950 = 80; 80 ≤ 200 → virada; consumo **80**; tipo REAL; anormalidade 16 |
| L-04 | 5 dígitos, anterior 99950, atual 99000, média 25 | calc = 99000+100000−99950 = 99050 > 200 e > 50 → não é virada; consumo = média **25**; tipo MEDIA_HIDROMETRO; anormalidade 8; leitura faturada = 99975 |
| L-05 | atual = anterior = 1500 (>1), sem anormalidade, água abastecida, imóvel não sazonal, sem poço/esgoto mínimo | consumo 0; gera anormalidade HIDROMETRO_PARADO (30) |
| L-06 | sem leitura, sem anormalidade, média 22, `parm_nndiasvariacaoconsumo`=5, dias=33 | |33−30| ≤ 5 → consumo = média **22**; tipo MEDIA_HIDROMETRO; anormalidade 10 |
| L-07 | idem, dias = 38 (cronograma 30) | consumo = ARRED(22/30[0,733] × 38) = ARRED(0,733×38 = 27,854) = **28**; tipo CONSUMO_MEDIO_AJUSTADO |

### 10.7 Média e ajuste por dias

| ID | Entrada | Esperado |
|---|---|---|
| A-01 | ref 2026-10, `mesesMedia`=6; `cshi_nnconsumocalculomedia` de set, ago, jul, jun, mai, abr = 20, 18, 22, 25, 19, 21 (tipos que "entram na média") | soma 125; média = TRUNCA(125/6) = **20** (20,83 truncado) |
| A-02 | mesmo, sem histórico | média = consumo mínimo da ligação (RES 1 eco = **10**) |
| A-03 | rota com ajuste mensal; anterior 1000 em 01/09, atual 1040 em 06/10 (35 dias), cronograma 30 | diasAjuste = −5 → |−5| > 3: consumo/dia = 40/35 = 1,1429 → consumo ajustado = ARRED(1,1429×30 = 34,287) = **34**; leitura ajustada = 1040 + ARRED(1,1429×(−5) = −5,7145) = **1034**; data atual faturada = 01/10; tipo CONSUMO_MEDIO_AJUSTADO |
| A-04 | idem com 33 dias (diasAjuste = −3) | |−3| ≤ 3 → **sem ajuste**: consumo 40 |

### 10.8 Estouro, alto e baixo consumo

Parâmetros RES: `consumoEstouro`=50, `vezesMediaEstouro`=5, `consumoAlto`=40, `vezesMediaAlto`=3, `mediaBaixoConsumo`=10, `pcMediaBaixoConsumo`=40; imóvel 1 eco, média = 20.

| ID | Consumo | Esperado |
|---|---|---|
| X-01 | 120 | 120 > 50 e > ARRED(20×5)=100 → estouro detectado. **Com ação cadastrada** para (estouro, RES, perfil) → anormalidade **5** e efeito do mês 1. **Sem ação cadastrada e 1ª ocorrência** → anormalidade **11** (ESTOURO_CONSUMO_COBRANCA_MEDIA) e consumo faturado = média = **20** |
| X-02 | 100 | 100 > 50 mas não > 100 → sem estouro; avalia alto: 100 > 40 e > 60 → **ALTO** (6) |
| X-03 | 55 | 55 > 40 e < 60 → sem alto (não > 3×20) |
| X-04 | média 30, consumo 11 | baixo: 30 > 10 e 11 < 30×40/100 = 12,0 → **BAIXO** (4) |
| X-05 | média 30, consumo 12 | 12 < 12,0? não → sem baixo |
| X-06 | estouro em ref−1 e ref−2 (mesmo tipo) | usa ação do **mês 3** (`lacs_idmes3`, fator `nnfatorconsumomes3`) |

### 10.9 Não medido

| ID | Entrada | Esperado |
|---|---|---|
| N-01 | RES 3 eco, sem hidrômetro, `non_metered_by_tariff` = SIM, fator de economias nulo | consumo = 10 × 3 = **30**; tipo NAO_MEDIDO (5); valor = 3×30,00 = **90,00** |
| N-02 | idem, `fatorEconomias` da categoria = 2 | consumo = 10 × 2 = **20** |
| N-03 | tipo de cálculo "por área": área 85,40 m², 2 economias virtuais; tabela `consumo_minimo_area`: até 40→8, até 60→10, até 100→14 | área virtual = 85,40/2 = 42,70 → faixa "até 60" → 10 × 2 economias = **20** |
| N-04 | "por moradores", imóvel 1 eco com `numeroMorador` nulo; tabela: 1→6, 2→9, 3→12 | moradores := 1 → consumo **6** |
| N-05 | ligação com consumo mínimo fixado = 15, medido 9 | consumo = **15**; tipo CONSUMO_MINIMO_FIXADO (7) |

### 10.10 Débitos, créditos e parcelas

| ID | Entrada | Esperado |
|---|---|---|
| D-01 | débito R$ 100,00 em 3 prestações | 33,33 ; 33,33 ; **33,34** (Σ 100,00) |
| D-02 | repartição de R$ 10,00 entre RES (1 eco) e COM (2 eco) via UC0185 | fator = 10,00/3 = 3,33 (DOWN); RES 3,33; COM 6,66; Σ 9,99 → diferença 0,01 vai à 1ª categoria → **3,34 ; 6,66** |
| C-01 | crédito R$ 35,00 em 6 prestações (oráculo do teste do GSAN) | 5,83 ×5 e **5,85** na 6ª |
| C-02 | conta água 50,00 + esgoto 40,00 + débitos 10,00 = 100,00; crédito 150,00 em 1 prestação | crédito aplicado **100,00**; resíduo **50,00** guardado; total da conta = 0,00 |
| C-03 | mês seguinte, crédito C-02 (1/1 realizada) com resíduo 50,00, conta de 80,00 | crédito = **50,00**; resíduo 0,00; total = 30,00 |
| C-04 | crédito com 2 créditos na mesma conta: A=60 e B=60, conta = 100 | A aplica 60 (resta 40); B aplica 60 → excede 20 → B aplica **40**, resíduo B = 20 |

### 10.11 Impostos retidos (alíquotas **ilustrativas**: IR 1,20; CSLL 1,00; COFINS 3,00; PIS 0,65; soma 5,85)

| ID | Base (água+esgoto+déb−créd) | IR | CSLL | COFINS | PIS (último = total − soma) | Total | Total da conta |
|---|---|---|---|---|---|---|---|
| I-01 | 1.000,00 | 12,00 | 10,00 | 30,00 | 6,50 | 58,50 | 941,50 |
| I-02 | 333,33 | 4,00 | 3,33 | 10,00 | **2,17** (19,50 − 17,33) | 19,50 | 313,83 |
| I-03 | cliente responsável ausente | — | — | — | — | 0,00 | 100 % da base |

### 10.12 Acréscimos por impontualidade (conta R$ 100,00; venc. 10/10/2026; multa ainda não cobrada; `dias úteis > parm_nndiascalculoacrescimos`)

| ID | Índices | Pagamento | Esperado |
|---|---|---|---|
| J-01 | multa 2 % (fixa), juros 1 % a.m., correção 0 | 12/11/2026 | multa **2,00**; meses = 1 (calendário) → juros **1,00**; total 3,00 |
| J-02 | multa 2 %; juros 1 % a.m. | 20/10/2026 (mesmo mês) | multa 2,00; meses = 0 → juros **0,00** |
| J-03 | multa diária 0,33 %/dia, limite 2 %; juros diários 0,033 %/dia | 20/10/2026 (10 dias) | multa % = min(3,30; 2,00) = 2,00 → **2,00**; juros % = 0,33 → **0,33** |
| J-04 | juros 1 % a.m. | venc. 31/01/2027, pago 01/02/2027 | meses (calendário) = 1 → juros **1,00** com 1 dia de atraso (**compatibilidade**; decidir se o SIGSAN adota 30 dias) |
| J-05 | multa já cobrada (`indicadorCobrancaMulta` = 1) | qualquer | multa **0,00** |
| J-06 | débito automático | qualquer | **0,00** (não calcula) |

### 10.13 Rateio de condomínio

| ID | Entrada | Esperado |
|---|---|---|
| RC-01 | macro 100 m³; 4 micros medidos (1 eco cada) com 20+20+20+20 = 80; incremento máx. 10 | aSerRateado = 20; por eco = 5; `consumoRateio` de cada micro = **5** |
| RC-02 | idem, com macro 60 e micros 80 | aSerRateado = MAX(0, −20) = **0** |
| RC-03 | aSerRateado = 80 com 4 eco medidas e incremento máx. 10 | por eco = 20 → limitado a **10**; rateio por micro = 10 |
| RC-04 | 1 micro não medido (2 eco) + 2 medidos; aSerRateado 30 | rateio só entre não medidos: 30 DIV 2 = 15 por eco; micro não medido = 15×2 = **30**; medidos = 0 |
| RC-05 (propriedade) | rateio por valor R$ 70,00 / 3 economias | exigir Σ rateios = 70,00 (23,33 + 23,33 + 23,34); o legado dá 23,33 ×3 = 69,99 |

### 10.14 Não geração de conta e geração

| ID | Entrada | Esperado |
|---|---|---|
| G-01 | água 0, esgoto 0, sem débitos, sem créditos, ligado | **não gera** |
| G-02 | água 0, esgoto 0, com débito a cobrar `indicadorGeracaoConta = SIM` sem pagamento | **gera** |
| G-03 | situação especial "paralisar faturamento" com intervalo cobrindo a referência | **não gera** (mesmo com débito) |
| G-04 | débito a cobrar já pago (existe pagamento) e nada mais | **não gera** |
| G-05 | executar `faturar(grupo, ref)` duas vezes | 2ª execução não cria 2ª conta (idempotência por `(unidade, referência)`); conta PRE_FATURADA é reprocessada |

### 10.15 Vencimento

| ID | Entrada | Esperado |
|---|---|---|
| V-01 | rota vence 15/10/2026; imóvel `diaVencimento`=20, mês seguinte = NÃO | 15 ≤ 20 → **20/10/2026** |
| V-02 | rota 15/10; imóvel dia 10; mês seguinte = NÃO; hoje 02/10; mínimo 5 dias | candidato 15/10 (> hoje+5 = 07/10) → **15/10/2026** |
| V-03 | rota 15/10; imóvel dia 10, hoje 12/10 (hoje+5 = 17/10 ≥ 15/10) | vai para o mês seguinte, **com o dia da rota** (o legado sobrescreve o dia alternativo pelo dia da rota quando este é maior) → **15/11/2026**. Comportamento a decidir no SIGSAN (talvez o esperado fosse 10/11) |
| V-04 | imóvel dia 31, mês seguinte = SIM, base 15/11 | mês seguinte à base = dezembro; dia 31 ≤ 31 → **31/12/2026**; se fosse fevereiro → último dia do mês |
| V-05 | + correio (conta envio 1) sem dia alternativo, `parm_nndiasadicionaiscorreios` = 3 | vencimento da rota + 3 dias |

### 10.16 Testes de propriedade (invariantes)

* Σ(valor por categoria) = valor da água do imóvel; Σ(valor por faixa) = valor da categoria.
* `créditoAplicado ≤ água + esgoto + débitos` e `Σ parcelas de crédito + resíduo = valor do crédito`.
* `Σ prestações de débito = valor do débito` (centavos na última).
* Consumo faturado é múltiplo da quantidade de economias quando há > 1 economia (exceto rateio).
* Para qualquer média m, `faixaInicial ≤ faixaFinal` (antes da virada).
* Reexecutar qualquer atividade com os mesmos insumos produz o mesmo resultado (determinismo) e não duplica registros.

---

## 11. Risco de licença (nota)

* Não existe LICENSE/COPYING no clone. O README diz que o GSAN é "software livre" desenvolvido e disponibilizado pelo Ministério das Cidades, no Portal do Software Público Brasileiro (`remote.origin.url = https://softwarepublico.gov.br/gitlab/gsan/gsan.git`). **Inferência**: costuma ser GPL (variante "Licença Pública Geral" do SPB); **não confirmado**.
* **Contaminação potencial**: copiar código, nomes de método, estrutura de classes, scripts SQL, jrxml ou texto de mensagens traria obrigações de copyleft. O que este levantamento extrai são **fatos de domínio** (valores de estados, regras, fórmulas matemáticas, catálogos de conceitos). Fórmulas e percentuais regulatórios não são código, mas **reescreva do zero** a partir das especificações aqui, e implemente por TDD a partir da seção 10.
* Atenção especial: (i) o método `calcularValorFaturadoFaixaCAER` é uma fórmula de uma concessionária específica; foi descrito só como conceito ("tarifa por fórmula pluginável"), sem coeficientes; (ii) nomes de tabela/coluna deste documento ajudam a *entender* o domínio — o SIGSAN usa nomenclatura própria em inglês; (iii) os layouts `.jrxml` não devem ser reaproveitados; (iv) os dados de teste da seção 10 são **fictícios** (somente os valores 35,00 / 5,83 / 5,85 espelham um teste do GSAN e são aritmética elementar).
* Recomendação: pedir parecer jurídico sobre a licença exata do GSAN antes de qualquer reuso de artefatos; manter um registro "clean-room" (quem leu, quem implementou).

---

## 12. Recomendações para o SIGSAN

Legenda: **Já existe** = no repositório hoje; **Falta**; risco (R) e esforço (E) em P/M/G.

### P0 — fundação do ciclo comercial (sem isso nada fecha)

| # | Absorver | Módulo-alvo | Existe hoje | Falta | R / E |
|---|---|---|---|---|---|
| P0-1 | **Motor de tarifa** puro: `tariff`, `tariff_version` (vigência por data), `tariff_category_rule` (consumo mínimo + tarifa mínima + fator de economias), `tariff_block`; cálculo 4.2–4.4 com `BigDecimal`/`RoundingMode` explícitos; tipos `BLOCKS`, `DIRECT_BLOCK` e interface de fórmula pluginável | `commercial/sigsan-billing` | só `tariff_type_id` solto em `consumer_units` | tudo | R: médio (arredondamento) / E: M |
| P0-2 | **Cálculo de consumo faturado** (cascata 3.x) como `ConsumptionCalculator` sem I/O, com **tipo** e **anormalidade** gravados por registro | `operations/sigsan-metering` + `sigsan-reading` (regras) / `billing` (persistência) | nada | tudo | R: alto (muitas regras) / E: G |
| P0-3 | **Modelo `meter`/`meter_installation`/`meter_reading`/`consumption_record`** (9.1–9.3) com intervalo exclusivo de instalação | `operations/sigsan-metering`, `sigsan-reading` | `meter_id`, `reading_route_id` UUID soltos | tabelas + JPA + RLS + migrations | R: baixo / E: M |
| P0-4 | **Conta** (`bill`, `bill_category`, `bill_category_block`, itens de débito/crédito, estados 5.3) + **idempotência** por `(unidade, referência)` | `commercial/sigsan-billing` | nada | tudo | R: médio / E: G |
| P0-5 | **Ciclo mensal** (`billing_group`, `billing_cycle`, atividades, `job_unit` por rota, reinício) | `billing` + `shared/sigsan-events` | Kafka no compose; sem consumidores | orquestração (pode ser Spring Batch/Quartz + Kafka) | R: médio / E: M |
| P0-6 | **Suíte de testes de cálculo** da seção 10 como critério de aceite (JUnit parametrizado) | `billing`, `metering` | 35 testes (core/platform) | todos | R: baixo / E: P-M |
| P0-7 | **Placeholders do balanço hídrico**: expor `BilledConsumption` (consumo faturado por período/setor) e `MeteredConsumption` a partir de `consumption_record` | `intelligence/sigsan-losses` | `LossesService` com constantes | porta (interface) + adapter | R: baixo / E: P |

### P1 — regras que dão correção e robustez

| # | Absorver | Módulo-alvo | Existe | Falta | R / E |
|---|---|---|---|---|---|
| P1-1 | Catálogos de **anormalidade de leitura** e **de consumo** (com efeitos parametrizáveis) e `consumption_anomaly_action` (escalonamento 3 meses) | `reading`, `billing` | — | catálogo + seeds por tenant | M / M |
| P1-2 | **Validação do retorno de leitura** (2.4) e **faixa esperada** (2.5) com BigDecimal exato | `reading` | — | regras + testes R-01..R-08 | B / P |
| P1-3 | **Rateio de condomínio** (macro/micro) por consumo e por valor com "maior resto" | `billing` | — | regra + decisão de política | M / M |
| P1-4 | **Situação especial de faturamento** (paralisar/fixar) + comando em lote | `billing` | — | tabelas + serviço | B / M |
| P1-5 | **Débito/Crédito a realizar**, parcelas, repartição por categoria, resíduo (5.6) | `billing` | — | tudo; usar teste C-01..C-04 | B / M |
| P1-6 | **Acréscimos por impontualidade** (`late_fee_index`, juros/multa/correção) | `commercial/sigsan-dunning` | — | tudo | M / M |
| P1-7 | **Retenção de impostos** (IR/CSLL/COFINS/PIS) configurável por tenant, "último absorve resíduo" | `billing` + `accounting` | — | validar alíquotas com contabilidade | M / P |
| P1-8 | **Vencimento** e calendário de feriados por município | `billing` | — | `calendar`/`holiday` em `shared/core` | B / P |
| P1-9 | **Cancelar/Retificar/Revisar/Alterar vencimento** com motivos, permissões (cancelar sem RA) e trilha | `billing` + `compliance/lgpd` (auditoria) | `audit_log` | máquina de estados + RBAC | M / M |
| P1-10 | **2ª via/PDF** com qualidade da água, mensagens e boleto/PIX | `commercial/sigsan-portal` | `MtrPdfGenerator` como referência técnica de PDF | template próprio (não reusar jrxml) | B / M |
| P1-11 | **Pontos de extensão por tenant** (estratégia: tipo de cálculo de não medido, fórmula tarifária, bônus social) via SPI | `shared/core` + `platform/sigsan-tenant` | `tenant_metadata` | contrato `TenantPolicy` | M / M |

### P2 — diferenciais e integrações

| # | Absorver | Módulo-alvo | Observação |
|---|---|---|---|
| P2-1 | App de leitura offline (Capacitor/React Native): pacote por rota, faixa esperada local, fotos, GPS, fila de sincronização, conflito | `operations/sigsan-reading` + frontend | substitui arquivo posicional/coletor/celular do GSAN |
| P2-2 | **Telemetria/AMI** como fonte de leitura (`reading_event_raw`, consistência, reprocesso) | `operations/sigsan-reading` + `intelligence/sigsan-losses` | integra com SCADA/MQTT já previstos |
| P2-3 | **Anti-fraude de leitura** (faixa falsa, fiscalização amostral com CSPRNG, score de leiturista) | `reading` + `intelligence` | dados para ML (sinal de fabricação) |
| P2-4 | **Impressão simultânea / fatura em campo** (conta PRE_FATURADA, retorno sem recálculo) | `reading` + `billing` | só se houver cliente que peça |
| P2-5 | **Tarifa social** (cadastro socioeconômico, bônus, perda por anormalidade) | `billing` + `portal` + `compliance/lgpd` | dados sensíveis (renda): exige base legal e RLS fina |
| P2-6 | Encerramento/contábil: resumo por localidade × categoria × item contábil, lançamentos | `commercial/sigsan-accounting` | depende de plano de contas do cliente |
| P2-7 | Agência reguladora: relatório mensal e tarifa/qualidade impressa | `billing` + `intelligence/sigsan-quality` | contrato de dados por tenant |
| P2-8 | Débito automático, boleto/PIX, conciliação | `commercial/sigsan-collection` | fora desta fatia; só os pontos de ligação (`DebitoAutomaticoMovimento`, `cnta_nnboleto`) |
| P2-9 | Hidrômetro: ciclo de vida/oficina/revisão por marca (`intervalo de revisão`), inventário | `operations/sigsan-assets` + `metering` | gera OS preventiva em `operations/sigsan-maintenance` |

### Ordem sugerida de execução (dependências)

`P0-3 (modelo)` → `P0-1 (tarifa) ∥ P0-2 (consumo)` → `P0-6 (testes de aceite)` → `P0-4 (conta)` → `P0-5 (ciclo)` → `P0-7 (perdas)` → P1-5/P1-7/P1-8 → P1-1/P1-2/P1-3/P1-4 → P1-6/P1-9/P1-10/P1-11 → P2.

### Lacunas desta fatia (não cobertas — entregar a outros levantamentos)

* Cadastro de imóvel/cliente/economias (`cadastro.*`), ligações (`atendimentopublico.*`), parcelamento e cobrança (`cobranca.*`), arrecadação/pagamentos (`arrecadacao.*`), financeiro/contábil (`financeiro.*`), negativação SPC/Serasa, perfil/segurança — apenas citados quando entram no cálculo.
* Dados semente do GSAN (catálogos de `faturamento_atividade`, `leitura_anormalidade`, `consumo_anormalidade_acao`) **não estão no repositório** (as migrações vivem em `gsan-migracoes`, projeto separado); a seção 3 documenta os valores definidos em código.
