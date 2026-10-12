# 04 - GSAN: operacional, atendimento público, segurança, gerencial, API e frontend

Fatia do levantamento: `gcom/atendimentopublico`, `gcom/seguranca`, `gcom/gerencial`, `gcom/operacional`, `gcom/atualizacaocadastral`, `gcom/spcserasa` (só superfície), `api/`, `gcom/WEB-INF`, `gcom/jsp`, `documents/recadastramento`, `lib/`, `descriptors/` e `reports/`.
Fonte: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN` (somente leitura). Data da análise: 11/10/2026.

Convenções: **[V]** = verificado em arquivo citado; **[I]** = inferência minha; caminhos relativos a `GSAN/` (código em `src/gcom/...`).

---

## 0. Observações de método e riscos

- **Licença (risco de contaminação)**: não há arquivo `LICENSE` na raiz (`ls -a` mostra só `README.md`, `build.xml`, etc.) **[V]**. O `README.md` diz que é "software livre", publicado pelo Ministério das Cidades via Portal do Software Público Brasileiro **[V]**. A licença exata (provavelmente GPL, **[I]**, não confirmada nos arquivos) precisa ser checada pelo jurídico antes de qualquer uso além de "estudo de domínio". Se for GPL, copiar código, SQL de carga, layouts Jasper (`reports/*.jrxml`) ou texto de telas para o SIGSAN contaminaria o produto SaaS. Este relatório extrai apenas conceitos, nomes de catálogos e regras. **Recomendação: engenharia limpa (clean-room)**: quem escreve o código do SIGSAN lê este relatório, não o código do GSAN.
- Os `.jrxml` e os layouts de arquivo (`documents/recadastramento/layouts/*.xlsx`) são documentação/artefatos de terceiros: usar só como referência de campos, não copiar.
- `lib/` contém jars de terceiros com licenças heterogêneas (Struts 1.1, Axis2 1.5.1, Jasper, Quartz, Hibernate, jersey-client 1.18, gson 2.2.4, JBoss libs) **[V]** (`ls lib`). Nada disso deve entrar no SIGSAN.
- Há muita **customização por concessionária** no código: sufixos `CAEMA`, `CAER`, `CAERN`, `COMPESA`, `COSAMA`, `COSANPA`, `JUAZEIRO` em `descriptors/` (ex.: `faturamentoCAERN`, `arrecadacaoCOMPESA`), em controladores (`cadastro/ControladorCadastroCAERNSEJB.java` etc.) e em relatórios (`relatorioNotificacaoDebitoCosanpa.jrxml`) **[V]**. É exatamente o anti-padrão que o modelo SaaS multi-tenant do SIGSAN deve resolver com configuração por tenant, não com subclasses.
- Aviso operacional: a listagem inicial de `src/gcom` pelo `ls` do Drive virtual veio **incompleta** (não mostrou `seguranca`, `util`, `tarefa`, `spcserasa`), mas o diretório existe. Se outro agente não achar um pacote, tentar Glob direto.

---

## 1. Atendimento público

### 1.1 Mapa de pacotes **[V]**

| Pacote | Conteúdo |
|---|---|
| `atendimentopublico/registroatendimento` | RA (Registro de Atendimento), tipo/especificação de solicitação, trâmite, solicitante, anexos, reiteração, reativação, agência reguladora, comando de encerramento em lote |
| `atendimentopublico/ordemservico` | OS, tipos de serviço, atividades, materiais, equipes, programação/roteiro, fiscalização, OS seletiva, acompanhamento por celular, boletins, repavimentação |
| `atendimentopublico/ligacaoagua`, `ligacaoesgoto` | situações e perfis de ligação, corte/supressão, motivos |
| `atendimentopublico/portal` | `QuestionarioSatisfacaoCliente` |
| Controladores (EJB2) | `ControladorRegistroAtendimentoSEJB` (16.122 linhas), `ControladorOrdemServicoSEJB` (18.872 linhas), `ControladorLigacaoAguaSEJB` (869) |

### 1.2 RA - Registro de Atendimento

Tabela `atendimentopublico.registro_atendimento` (`RegistroAtendimento.hbm.xml`) **[V]**:

- Estados (`rgat_cdsituacao`): **1 Pendente, 2 Encerrado, 3 Bloqueado** (`RegistroAtendimento.java`).
- Datas: `dataPrevistaOriginal` e `dataPrevistaAtual` (prazo original vs prorrogado), `ultimaEmissao`, `dataEncerramento`, `ultimaReiteracao`, `quantidadeReiteracao`, `inicioEspera/fimEspera` (fila de atendimento presencial), `indicadorAtendimentoOnline` (1 online, 2 manual), `manual` (RA com numeração manual de talonário).
- Localização do problema: logradouro/bairro/CEP, setor comercial, quadra, localidade, divisão de esgoto, pavimento rua/calçada, `coordenadaNorte/Leste` + `indicadorCoordenadaSemLogradouro`, `perimetroInicial/Final` (trecho de rua), `nnDiametro`.
- Vínculos: imóvel, `solicitacaoTipoEspecificacao`, `meioSolicitacao`, `localOcorrencia`, `unidadeAtual` (onde o RA está), `atendimentoMotivoEncerramento`, RA de reativação e RA de duplicidade (auto-relacionamentos).
- Filhas: `RegistroAtendimentoSolicitante` (+ fones), `RegistroAtendimentoUnidade` (histórico de trâmites com `AtendimentoRelacaoTipo`: 1 Abrir/Registrar, 3 Encerrar), `Tramite` (parecer, data), `RAReiteracao`, `RegistroAtendimentoAnexo`, `RegistroAtendimentoConta` e `RegistroAtendimentoPagamentoDuplicidade` (RA que corrige conta/devolve pagamento), `RaDadosAgenciaReguladora` (+ fones; `AgenciaReguladora`, `AgenciaReguladoraMunicipio`, motivos de reclamação/retorno).
- Protocolo de atendimento: `obterProtocoloAtendimento()` (linha 13756 do controlador) **[V]**.

**Catálogo de classificação** **[V]**:

| Tabela | Campos de regra relevantes |
|---|---|
| `solicitacao_tipo_grupo` | descrição, `icesgoto`; grupo "Operacional - Água com diagnóstico" = id 6 (constante) |
| `solicitacao_tipo` | `icfaltaagua`, `ictarifasocial`, `icusosistema` |
| `solicitacao_tipo_espec` (a "especificação", é onde mora a regra) | `nndiaprazo` (SLA em dias), `icgeracaoordemservico` (OS opcional), serviço padrão (gera OS automática), `icpavimentorua/calcada`, `iccobrancamaterial`, `icmatricula` (exige imóvel), `icparecerencerramento`, `icgeracaodebito`/`icgeracaocredito`, `vldebito` + `icpermitealterarvalor` + `iccobrarjuros`, `icdocumentoobrigatorio`, `icurgencia`, `icencerramentoautomatico`, `icvaldocresponsavel`, `icinformarcontara`, `icinformarpgtoduplicidade`, `icalterarvencimento`, `iclojavirtual`, `nncodigoconstante` |
| `meio_solicitacao` | ids conhecidos: 4 Interno, 8 Internet, 10 Atualização cadastral via celular |
| `atend_motivo_encmt` | `icexecucao` (houve execução?), `icduplicidade`, `qtdiasaditivoprazo`, `icexibiformordemseletiva`; ids conhecidos: 2 Conclusão do serviço, 3 Débito pago, 4 Desistência do usuário, 32 Cancelado por decurso de prazo, 15/53/54/55 tarifa social |
| `local_ocorrencia`, `espec_imov_situacao` + critérios | especificação só vale para imóvel em certas situações (ex.: não abrir religação para imóvel já ligado) |
| `solicitacao_documento_obrig` | documentos obrigatórios por especificação |
| `localidade_espec_unidade`, `localidade_solic_tipo_grupo`, `espec_unid_cobranca` | **roteamento**: qual unidade organizacional recebe cada especificação por localidade |
| `visualizacao_ra_urgencia` | quem vê RA urgente |

**Regras de negócio extraídas** (todas **[V]** em `ControladorRegistroAtendimentoSEJB.java` salvo indicação):

1. **Data prevista** (`definirDataPrevistaRA`, l.8821): `data do atendimento + step_nndiaprazo`; se o parâmetro de sistema `indicadorCalculoPrevisaoRADiasUteis` estiver ligado, usa dias úteis excluindo feriados nacionais (`pesquisarFeriadosNacionais`); feriado municipal está **comentado** no código (não implementado). Se a especificação não tem prazo, a data prevista = data do atendimento.
2. **Unidade destino do trâmite** (`definirUnidadeDestino`, l.8899): só sugere se `indicadorSugestaoTramite` ligado; ordem de precedência: (a) unidade fixa da especificação; (b) unidade por localidade+tipo; (c) para tipo "relativo a esgoto", unidade da divisão de esgoto. Depois aplica "unidade centralizadora" (`verificarExistenciaUnidadeCentralizadora`).
3. **Validações de abertura**: exigência de imóvel pela especificação (`verificarExigenciaImovelPelaEspecificacao`), bloqueio de RA duplicado no mesmo imóvel/mesma especificação (`verificarExistenciaRAImovelMesmaEspecificacao`), imóvel com débitos (`verificarImovelComDebitos`), situação do imóvel x especificação (`verificarSituacaoImovelEspecificacao`), ordem de execução entre serviços da especificação (`EspecificacaoServicoTipo.ordemExecucao`, `verificarOrdemExecucaoForaOrdem`), RA pendente no mesmo local de ocorrência (agrupa chamados do mesmo vazamento/falta d'água: `verificaExistenciaRAPendenteLocalOcorrencia`, `pesquisarRAPendenteLocalOcorrencia`).
4. **Falta d'água** (`verificarRegistroAtendimentoFaltaAgua*`, `verificarRegistroAtendimentoFaltaAguaGeneralizada`, l.4836-5242 e 9813): ao abrir RA de falta d'água, consulta **programação de abastecimento e manutenção** (`operacional/abastecimento/AbastecimentoProgramacao`, `ManutencaoProgramacao`) por município/bairro e avisa o atendente ("já há interrupção programada"); agrupa RAs pendentes do mesmo local; existe especificação "falta d'água generalizada" (`pesquisarTipoEspecificacaoFaltaAguaGeneralizada`). Isso liga **atendimento ao operacional** e é um caso de uso forte para SIGSAN.
5. **Reiteração** (`reiterarRegistroAtendimento`): cliente liga de novo; incrementa `qtreiteracoes`, registra dados do solicitante da reiteração; `dados_reiteracao_ra_consultar.jsp`.
6. **Reativação** (`reativarRegistroAtendimento`, `validaPossibilidadeReativacaoRA`): RA encerrado vira novo RA vinculado (`registroAtendimentoReativacao`, `RaMotivoReativacao`).
7. **Tramitação** (`tramitar`, `validarTramitacao`, `tramitarConjuntoRA`): troca `unidadeAtual`, grava parecer; permissão especial "tramitar para qualquer unidade" (110); só usuário da unidade atual pode mexer (`verificarUnidadeUsuario`); tramitação em lote; tramitar exportando OS a prestadoras terceirizadas (`tramitarRAExportandoOSPrestadoras`, `exportarOrdemServicoPrestadoras`, tabela `integracao.serv_terceiro_acomp_serv`).
8. **Encerramento** (`validarEncerramentoRA` l.7405, `encerrarRegistroAtendimento` l.7497): a data de encerramento não pode ser futura, nem anterior à data do RA, nem anterior ao maior encerramento de OS do RA; controle de concorrência otimista (`verificarRegistroAtendimentoControleConcorrencia`, compara `ultimaAlteracao`); se o motivo tem `icexecucao = NÃO`, **encerra automaticamente todas as OS pendentes** do RA sem execução; se o serviço tem `icEnvioPesquisaSatisfacao` e motivo = Conclusão do Serviço, **envia e-mail com questionário de satisfação** (`enviarEmailQuestionarioSatisfacao`, tabela do portal); pode gerar débito a cobrar (`gerarDebitoRegistroAtendimento`); pode abrir **novo RA encadeado** (`solicitacaoTipoEspecificacaoNovoRA`).
9. **Encerramento em lote por comando** (`RaEncerramentoComando`, `RaEncerramentoComandoEspecificacoes`, `executarComandoEncerramentoRA`, batch `batchExecutarComandoEncerramentoRA`): encerrar em massa RAs de determinadas especificações/períodos (limpeza de passivo).
10. **Cancelamento por decurso de prazo**: motivo 32 + batch `batchProcessarEncerramentoOSFiscalizacaoDecursoPrazo` e `encerrarOrdemServicoVencida` (OS) **[V]**.
11. **Gestão/relatórios**: `filtrarGestaoRA`, `gerarResumoRegistroAtendimento(PorAno)` (alimenta `un_resumo_registro_atendimento`), `RelatorioGestaoSolicitacoesRAPorChefia`, `RelatorioResumoSolicitacoesRAPorUnidade`, `RelatorioRegistroAtendimentoPorUnidadePorUsuario`.

**Anexos de RA**: validação de arquivo (`validarRegistroAtendimentoAnexos`), tabela `registro_atendimento_anexo` **[V]**.

### 1.3 OS - Ordem de Serviço

Tabela `atendimentopublico.ordem_servico` **[V]** (`OrdemServico.hbm.xml`):

**Estados** (`orse_cdsituacao`, `OrdemServico.java`) **[V]**:

| Valor | Descrição | Letra |
|---|---|---|
| 1 | Pendente | P |
| 2 | Encerrada (com subtipo "encerrada porém não foi executada" definido por motivo/indicador de execução) | E |
| 3 | Execução em andamento | A |
| 4 | Aguardando liberação para execução | L |

`indicadorProgramada` (1 programada / 2 não programada), `indicadorEncerramentoAutomatico`, `indicadorBoletim`, `indicadorAtualizaAgua/Esgoto`, `indicadorServicoDiagnosticado`, `indicadorComercialAtualizado`.

**Transições observadas** **[V]**:
- Criação (`gerarOrdemServico`, l.887): situação = Pendente; `valorOriginal = servicoTipo.valor`; prioridade original = prioridade padrão do tipo de serviço; "atual" pode ser alterada depois; unidade atual = unidade atual do RA; grava `OrdemServicoUnidade` (histórico de unidade) com `AtendimentoRelacaoTipo` = Abrir/Registrar.
- **OS com referência** (`validarOrdemServico` l.773, `ServicoTipoReferencia`): um tipo de serviço pode exigir outra OS de referência (ex.: "serviço de execução" depende de "diagnóstico/vistoria"); valida que a OS-referência é do mesmo RA, que o tipo é compatível e que a situação dela é a exigida (`situacaoOsReferenciaAntes`); **ao gerar a OS filha a OS-referência passa para "Aguardando liberação" (4)** e, ao encerrar a filha, vai para a situação `situacaoOsReferenciaApos` / do `OsReferidaRetornoTipo` (deferido/indeferido, troca de serviço).
- Encerramento (`encerrarOSSemExecucao` l.2824; `encerrarOSComExecucaoSemReferencia` l.3069; `encerrarOSComExecucaoComReferencia` l.3417): exige motivo, data, parecer ≤ 400 caracteres, atividades registradas (se `svtp_icatividade`) **exceto** quem tem a permissão especial "Encerrar OS sem atividades" (106), vistoria obrigatória (`svtp_icvistoria` → `codigoRetornoVistoria`), pavimento obrigatório se deferida com `svtp_icpavimento`, controle de concorrência por `ultimaAlteracao`, e `validarEncerramentoOsImovelEmCampo` (bloqueia se o imóvel está em atualização cadastral em campo).
- Efeito comercial no encerramento (`integracaoComercial`, l.8979): o tipo de serviço aponta, via `ServicoTipoOperacao`, para uma **operação do sistema** e o encerramento executa o efeito. Operações mapeadas **[V]**: efetuar ligação de água; inserir ligação de esgoto; corte de ligação de água; supressão; restabelecimento; religação; corte administrativo; retirada, remanejamento, instalação e substituição de hidrômetro; mudança de situação de faturamento da ligação de esgoto; atualizar consumo mínimo de água; atualizar volume mínimo de esgoto; ligação de água com instalação de hidrômetro; restabelecimento/religação com instalação de hidrômetro; alterar tipo de corte.
- Cobrança do serviço (`gerarDebitoOrdemServico`, l.1877): `svtp_icincluirdebito` + `debito_tipo` do serviço + valor (editável se `svtp_icpermitealterarvalor`), parcelas e juros (`svtp_iccobrarjuros`); `ServicoNaoCobrancaMotivo`; valor por vigência/medido/faixa de economias em `servico_cobranca_valor` (`ServicoCobrancaValor`: `dtvigenciainicial/final`, `qteconomiasinicial/final`, `icmedido`, `icgerardebito`).
- Atualização manual de OS (`atualizarOrdemServico`), com permissão por unidade/localidade (`obterIndicadorAutorizacaoManutencaoOS`, `verificarOSLocalidadeUsuario`) **[V]**.

**Tipo de serviço** (`atendimentopublico.servico_tipo`, `ServicoTipo.hbm.xml`) **[V]**: valor padrão, `nnprioridade`, `nntempomedioexecucao` (usado na carga de equipe), `icpavimento`, `icatualizacomercial`, `icterceirizado`, `cdservicotipo`, `icfiscalizacaoinfracao`, `icvistoria`, `icpermitealterarvalor`, `icincluirdebito`, `iccobrarjuros`, `icemcobranca`, `icprogramacaoautomatica`, `nncodigoconstante`, `icconsideraeconomias`, `icboletim`, `icatividade`, `icservicoordemseletiva`, `icenvioemailpesquisa`, `icgerarosinspanormalidade`, `icencautomaticoraqdoencos` (encerra RA automaticamente ao encerrar OS), `icpermitealteracaoimovelemcampo`; vínculos: tipo de referência, tipo de crédito, perfil (`ServicoPerfilTipo` = tamanho da equipe + veículo próprio), subgrupo/grupo, prioridade, tipo de débito, programa de calibragem.

Tabelas filhas de configuração do tipo: `servico_tipo_atividade`, `servico_tipo_material` (+ quantidade padrão), `servico_tipo_operacao`, `servico_tipo_motivo_encerramento`, `servico_tipo_boletim`, `servico_tipo_referencia`, `servico_tipo_prioridade`, `servico_tipo_subgrupo`, `servico_tipo_grupo`, `espec_servico_tipo` (especificação -> serviços, com ordem de execução) **[V]**.

**Prioridade/SLA de OS** (`servico_tipo_prioridade`): `stpr_nnprazoexecucaoini` e `stpr_nnprazoexecucaofim` (janela de prazo de execução) **[V]**; `numeroFatorPrioridade` na OS; atualização de "grau de importância" (`atualizarGrauImportanciaServicoTipo`) **[V]**. Prazo de OS em aditivo: `validaOrdemServicoDiasAditivoPrazo` + `AtendimentoMotivoEncerramento.qtdiasaditivoprazo` **[V]**.

**IDs "mágicos" de tipos de serviço** (`ServicoTipo.java`, ex.: 619 ligação de água, 726 ligação de esgoto, 242 corte, 243 religação, 244 restabelecimento, 245 supressão, 241 corte administrativo, 307 retirada de hidrômetro, 310 substituição...). Observação: a constante 308 aparece duplicada (`TIPO_REMANEJAMENTO_HIDROMETRO_LIGACAO_AGUA` e `TIPO_EFETUAR_REMOCAO_HIDROMETRO`) **[V]**, o que indica fragilidade de depender de IDs numéricos por base. **[I]**: no SIGSAN usar códigos estáveis (enum/`service_code`) e não IDs.

### 1.4 Geração de OS por evento

| Evento | Como o GSAN faz **[V]** |
|---|---|
| Abertura de RA | `solicitacao_tipo_espec` tem serviço padrão (automática: `gerarOrdemServicoAutomatica`, l.7957/8002) ou `icgeracaoordemservico` (opcional: operador decide); se a especificação cai no grupo 6 "Operacional - Água com diagnóstico", a OS gerada é a de **diagnóstico** e o serviço pedido vira "referência" |
| Corte por débito | Cobrança: `gerarOrdemServicoCorte` (l.12684) a partir do **documento de cobrança** (`ordem_servico.cbdo_id`); `RelatorioOrdemCorte`, `RelatorioDocumentoCobrancaOrdemCorte`; tipo 458 "corte de água por débito" |
| Fiscalização por débito | `gerarOrdemServicoFiscalizacao` (l.12348) / `gerarVariasOsFiscalizacao` (l.13913), `OrdemFiscalizacao` e `batchEmitirOrdemDeFiscalizacao`, `batchGerarArquivoOrdemDeFiscalizacao` |
| Religação de cortados quando o consumo real aparece | `batchReligarImoveisCortadosComConsumoReal` **[V]** (`descriptors/`) |
| Anormalidade de consumo | `batchGerarRAOSAnormalidadeConsumo` **[V]** gera RA/OS por anomalia de leitura/consumo |
| OS seletiva por critério (inspeção de hidrômetro/consumo/anormalidade) | `ComandoOrdemSeletiva` (`comando_ordem_seletiva`): faixas de sequencial de rota, ano/mês de instalação do hidrômetro, quantidade de economias, de moradores, área construída, média de consumo, consumo por economia, anormalidades consecutivas, imóvel condomínio; `gerarComando`, `gerarOrdemServicoSeletiva`, `gerarTxtOsInspecaoAnormalidade`; batch `batchGerarTxtOsInspecaoAnormalidade`, `batchEncerrarComandoOSSeletivaInspecaoAnormalidade` |
| Cobrança por resultado (terceirizada) | `batchGerarMovimentoContasCobrancaPorEmpresa`, `batchProcessarArquivoTxtEncerramentoOSCobranca` |
| Troca/instalação de hidrômetro, ligação nova, vazamento | tratados como **tipos de serviço** com `ServicoTipoOperacao` (ver 1.3); vazamento é só tipo de solicitação/serviço comum sem lógica especial (**[I]**, não achei regra específica de vazamento) |

### 1.5 Programação, roteirização e execução

Modelo **[V]**:

- `programacao_roteiro` (data do roteiro por unidade) -> `os_programacao` (sequencial, ativo, equipe principal, `cdsituacaofechamento`, `icacompservico`) -> `os_programacao_equipe` (N equipes por programação).
- `equipe` (`eqpe_*`): nome, placa do veículo, **carga de trabalho/dia** (`nncargatrabalhodia`), DDD+fone, **IMEI** (celular da equipe), `icprogramacaoautomatica`; `equipe_componentes` (membros, com funcionário e flag de encarregado), `equipe_equipamentos_especiais` (retroescavadeira, compressor...).
- Execução: `os_execucao_equipe` (+componentes), `os_atividade_periodo_execucao` (início/fim por atividade e equipe), `os_atividade_material_execucao` (material realmente usado), `ordem_servico_pavimento` (área/tipo de pavimento reposto), `ordem_servico_foto` (imagem em BLOB `osft_imfoto`, com `foto_situacao_os`: antes/depois), `os_prog_nao_encer_motivo` (motivo de não ter concluído; ids conhecidos 5 equipamento quebrado, 7 falta de equipamento).
- **Carga de trabalho** (`obterCargaTrabalhoEquipe`, l.2327): soma `tempoMedioExecucao` dos tipos de serviço das OS programadas, dividido pelas programações ativas, menos horas já executadas; compara com `nncargatrabalhodia` e dispara `validarCargaTrabalhoEquipe` ao remanejar.
- **Programação automática diária** (`inserirProgramacaoAutomaticaAcompanhamentoOS`, UC1190, l.16581; batch `batchProgramacaoAutoRoteiroAcompServico`): apaga a programação do dia; fecha OS transmitidas e não atualizadas do dia anterior; **rola as OS não encerradas para o próximo dia**; se o motivo do não encerramento foi falta/quebra de equipamento, só realoca para equipe que tenha o equipamento especial. Reordenação (`reordenarSequencialProgramacao`), alocar/remanejar equipe (`alocaEquipeParaOs`) e incluir OS na programação existem **[V]**.
- **Acompanhamento em campo por celular** (UC1227 "Atualizar OS de Acompanhamento de Celular"): `gerarArquivoTextoOrdensServicoAcompanhamentoEquipe` gera arquivo texto por equipe/IMEI (`arq_txt_acomp_servico`); tabelas espelho `os_prg_acomp_servico` (dados desnormalizados da OS: endereço, solicitante, inscrição, hidrômetro, situações das ligações, flag de transmissão, flag excluído), `os_at_exec_acomp_servico` (início/fim da execução), mensagens pré-cadastradas `cad_msg_acompanh_servico`; tela de percurso GPS (`consultar_percurso_acomp_arq_roteiro.jsp`) **[V]**. O fluxo é "gera arquivo -> equipe baixa -> equipe devolve -> sistema concilia", sem API REST. Tabela `integracao.ordens_servico_demanda` (`integracao/upa/OrdensServico.hbm.xml`) é uma integração com um banco SQL Server externo ("UPA", `palm_id`, `os_placa_veiculo`, `os_retro`, `os_compressor`, `os_reaterro`) **[V]**, claramente legada (PALM).
- **Boletim de OS concluídas / medição** (`BoletimOsConcluida`, `OrdemServicoBoletim`, `gerarInformacoesBoletimMedicao`, `RelatorioBoletimOrdensServicoConcluidas`, `RelatorioAcompanhamentoBoletimMedicao`) e **repavimentação** (processo de aceite/rejeição da repavimentadora por OS, custo por rua/calçada com vigência em `cadastro/unidade/UnidadeRepavimentadoraCusto*`, `RelatorioBoletimCustoPavimento`) **[V]**: é o fluxo de **medição de empreiteira**, importante para quem terceiriza obra.

### 1.6 Fiscalização e autos de infração

- `ServicoTipo.icfiscalizacaoinfracao` marca OS de fiscalização; `informarRetornoOSFiscalizacao` (l.6819, ~970 linhas) atualiza a situação encontrada: água/esgoto/hidrômetro (`FiscalizacaoSituacao`: `icmedido`, `icaguasituacao`, `icesgotosituacao`, `ichidrometrocapacidade`, `icconsumocalculo`, `icligacaodataatualiza`, `icatualizaautosinfracao`, `icverificarreincidencia`, `icnotificacaoesgoto`) **[V]**; várias situações por OS (`ordem_servico_fisc_sit`: data, indicador de débito); **fator de multiplicação** para multa conforme situação da ligação (`obterFatorMultiplicacaoParaFiscalizacaoAgua/Esgoto`, tabelas `fiscalizacao_situacao_agua/esgoto/hidrometro_capacidade`); gera **`AutosInfracao`** e débito a cobrar (`gerarAutosInfracao`, `gerarAutosInfracaoDebitoACobrar`); situação 10 = "ligado clandestino de água"; `OsReferidaRetornoTipo` tem `cdpenalidade` e `pcpenaseminfracao`; batch `batchAtualizarAutosInfracaoPrazoRecursoVencido` (prazo de recurso) **[V]**. **Fiscalização coletiva** (`FiscalizacaoColetiva`, `orse_cdretornofisclzcoltv`) **[V]**.
- Permissões especiais associadas: "Geração de débito OS fiscalização" (31), "Atualizar dados fiscalização" (52), "Desfazer fiscalização boletim OS concluída" (53) **[V]**.

### 1.7 Ligações, cortes e supressões

- `ligacao_agua_situacao` (id 1 Potencial, 2 Factível, 3 Ligado, 4 Em fiscalização/Ligado em análise, 5 Cortado, 6 Suprimido, 7 Supr. parcial, 8 Supr. parcial a pedido, 9 Em cancelamento) com flags de comportamento: `icfaturamento`, `nnconsumominimo`, `icexistenciarede`, `icexistencialigacao`, `icabastecimento`, `icativaagua`, `icdesligadaagua`, `iccadastradaagua`, `icanaliseagua`, `icconsumoreal`, `nndiascorte` **[V]**. A situação é **dado de configuração com semântica**, não só rótulo: decide se fatura, se conta como ativa, se abastece.
- `ligacao_esgoto_situacao`: 1 Potencial, 2 Factível, 3 Ligado, 4 Em fiscalização, 5 Fora de uso, 6 Tamponado **[V]**.
- `ligacao_agua`: datas de implantação/ligação/corte/supressão/religação/restabelecimento/corte administrativo, nº do **selo de corte**, **selo de supressão**, **lacre**, consumo mínimo **[V]**.
- Catálogos: `corte_tipo` (`iccorteadministrativo`; id 4 = corte no ramal), `motivo_corte`, `supressao_tipo`, `supressao_motivo`, `ligacao_agua_perfil/diametro/material`, `ramal_local_instalacao`, `ligacao_agua_abastecimento_alternativo`, `ligacao_esgoto_{caixa_inspecao,destino_aguas_pluviais,destino_dejetos,esgotamento,perfil,diametro,material}` **[V]**.
- Processos: efetuar ligação (com/sem RA, com/sem hidrômetro), corte, supressão, religação, restabelecimento, alterar situação, alterar tipo de corte (`WEB-INF/atendimentopublico/struts-config-Efetuar*.xml`), certidão negativa (imóvel e cliente) **[V]**.
- Regra fina de permissões: "Efetuar ligação de água sem RA" (65), "Efetuar supressão" (79), "Alterar situação da ligação para imóvel com débito" (81) **[V]**.

---

## 2. Operacional / manutenção

O pacote `gcom/operacional` é **muito menor do que o nome sugere**: só cadastros de **topologia comercial-operacional**, sem rede, sem ativos físicos, sem estoque **[V]**.

| Entidade (`operacional.*`) | Relação **[V]** |
|---|---|
| `sistema_abastecimento` | raiz |
| `zona_abastecimento` | -> sistema |
| `setor_abastecimento` | -> sistema |
| `distrito_operacional` | -> zona e/ou setor de abastecimento (equivale a "área de medição"; **[I]** é o análogo mais próximo de um DMA/setor de medição) |
| `zona_pressao` | -> distrito operacional |
| `fonte_captacao`, `tipo_captacao`, `setor_fonte_captacao` (setor comercial x fonte) | captação |
| `producao_agua` | **volume produzido** (`pdag_voproduzido`) por **localidade** x ano/mês, `ProducaoAgua.hbm.xml` |
| `sistema_esgoto`, `sistema_esgoto_tratamento_tipo`, `bacia`, `divisao_esgoto` | esgoto |
| `abastecimento_progr`, `manutencao_programacao` | programação de interrupção/abastecimento por período e horário, com descrição e situação; consumida pelo RA de falta d'água |

O que **não existe** (confirmado pela ausência no pacote e na lista de tabelas): trechos de rede, válvulas, hidrantes, reservatórios, elevatórias, bombas, estoque/almoxarifado, ordens de manutenção preventiva, plano de manutenção, histórico de ativo. Equipes, atividades, materiais (só catálogo: `material`, `material_unidade`), equipamentos especiais e custo de pavimento existem dentro de `ordemservico` (seção 1.5) **[V]**. Tabela de preços de serviço = `servico_cobranca_valor` + `servico_tipo.vlservico` (seção 1.3).

Implicação **[I]**: o GSAN serve como referência para **hierarquia de abastecimento e programação de interrupções**, **não** para `operations/assets`, `maintenance` ou GIS de rede. Para o `LossesService` do SIGSAN, `producao_agua` (volume por localidade/mês) e `un_resumo_indicador_desempenho_mmd` (volume faturado medido/não medido, seção 4) são as duas fontes do GSAN que mapeiam para "volume de entrada" e "consumo autorizado faturado" do balanço IWA/AWWA. O GSAN **não calcula balanço hídrico nem índice de perdas** (nenhum resultado em `SNIS|perdas` encontrado em `gerencial`/`relatorio/gerencial`, ver seção 4.3).

---

## 3. Segurança

### 3.1 Modelo de acesso **[V]** (`src/gcom/seguranca/acesso`, schema `seguranca`)

```
Modulo (modu) 1-N Funcionalidade (fncd, com URL .do, caminho de menu, ponto de entrada, OLAP, nova janela)
Funcionalidade 1-N Operacao (oper, com URL, tipo, registra transação?)
Operacao -> OperacaoTipo (1 Pesquisar, 2 Remover, 3 Atualizar, 4 Inserir ... 6 outro)
Grupo (grup) N-N Funcionalidade x Operacao via grupo_func_operacao (grup_id, oper_id, fncd_id)
Usuario N-N Grupo via usuario_grupo
usuario_grupo_restricao (grup_id, usur_id, fncd_id, oper_id)  -> RESTRIÇÃO: nega operação específica dentro do grupo
Funcionalidade N-N Funcionalidade via funcionalidade_dependencia (operação exige outras funcionalidades)
Grupo N-N Grupo via grupo_acesso (grup_id, grup_idacesso): grupo "gerencia" outros grupos (delegação de quem pode atribuir o quê)
permissao_especial (pmep) N-N Usuario via usuario_permissao_espec  (flags de exceção, ver 3.3)
func_contr_lib_pmep: funcionalidades que controlam liberação de permissão especial
usuario_favoritos (funcionalidades favoritas / mais acessadas)
```

- **Abrangência (escopo geográfico/organizacional)**: `usuario_abrangencia` com 5 níveis hierárquicos: **1 Estado, 2 Gerência Regional, 3 Elo Pólo, 4 Localidade, 5 Unidade de Negócio** (`UsuarioAbrangencia.java`); o `Usuario` carrega os ponteiros `gerenciaRegional`, `unidadeNegocio`, `localidadeElo`, `localidade`, `empresa`, `unidadeOrganizacional`, `funcionario` (`Usuario.hbm.xml`); `Abrangencia.java` aplica o filtro (usuário x imóvel/localidade/elo/unidade/gerência/município/setor/quadra). `pesquisarUsuarioAbrangenciaPorSuperior` limita quem o administrador pode criar (usuário só delega abrangência igual ou inferior à dele) **[V]**.
- **Tipo de usuário** (`usuario_tipo`: administrador = 1, flag `icfuncionario`), **situação** (`usuario_situacao`: 1 Ativo, 2 Pendente de senha, 3 Senha bloqueada, 4 Inativo), flags `icinternet` (usuário do portal) e `icrotinabatch` (usuário técnico de rotinas batch; `pesquisarUsuarioRotinaBatch`, `pesquisarUsuarioInternet`), `usur_limitebatch` **[V]**.
- Ciclo de vida do usuário: `solicitacao_acesso` + situação + grupos solicitados (workflow de solicitação/autorização com período de validade do acesso, `soac_dtinicioperiodo/fimperiodo`), `inserirUsuario` com data de expiração calculada a partir de parâmetros do sistema (`numeroDiasExpiracaoAcesso`, `numeroDiasMensagemExpiracao`), `usur_nnacessos`, `usur_nnbloqueioacesso` (contador de tentativas -> bloqueio), `usur_dtcadastroinicio/fim` (vigência), `usuario_senha_historico` (não reutilizar senhas), `grupo.grup_nndiasexpiracaosenha` (expiração por grupo), `grup_nncompetenciaretificacao` (competência até a qual o grupo pode retificar conta), `grup_icsuperintendencia` **[V]**.
- Senha: `Criptografia.encriptarSenha` usa **SHA-1 sem sal** em Base64 e há `Criptografia.encrypt` que é uma **tabela de substituição de dígitos** (`util/Criptografia.java`) **[V]**. **Não migrar.** Também há SSO caseiro por cookie + chamada HTTP (`util/GerenciadorSSO.java`) **[V]**.
- Menu dinâmico montado do banco (`organizar_menu_exibir.jsp`, `fncd_dscaminhomenu`, `fncd_nnordemmenu`, `modu_nnordemmenu`, `funcionalidade_categoria`) **[V]**.

### 3.2 Auditoria / log **[V]**

Duas camadas:

1. **Operação efetuada** (`operacao_efetuada`, `opef_cnargumento`, `opef_dsdadosadicionais`, `usuario_acao`): registra *quem fez qual operação* (inclusão/remoção/atualização), com argumento (id do objeto principal), por `RegistradorOperacao`; `operacao.oper_icregistratransacao` liga/desliga por operação; `operacao_tabela` diz quais **tabelas** cada operação toca; `tabela_caso_de_uso` associa tabelas a casos de uso.
2. **Alteração por coluna** (`interceptor/Interceptador.java`, 1.766 linhas, interceptor Hibernate): para classes anotadas `@ControleAlteracao` (por campo, por funcionalidade e `idTabelaColuna`), grava em `tabela_linha_alteracao` (tabela + id, `icprincipal`) e `tab_linha_col_alteracao` (**valor anterior e valor atual por coluna**), com `alteracao_tipo`; catálogo de tabelas/colunas monitoradas em `seguranca.tabela` e `tabela_coluna` (`tbco_icprimarykey`, `tbco_icpoderetificarconta`, `tbco_icatucadastral`). `transacao` + `transacao_efetuada` agrupam. Relatórios: `relatorioOperacaoEfetuada`, `relatorioAlteracoesSistemaColunaPorUsuario/PorLocalidade`, `relatorioAlteracoesCpfCnpj{Usuario,Localidade,Meio}`, `relatorioAcessosPorUsuario` **[V]** (`reports/`).
3. Consulta de dados do cliente a bureau (`ConsultaCdl`, `relatorioAcessoSPC`): registro de **quem consultou dado de terceiro** **[V]**: relevante para LGPD.

**Comparação com SIGSAN** (**[I]** com base em `docs/DIARIO-DE-BORDA.md` e `V1_1__create_tenant_schema.sql`):

| Tema | GSAN | SIGSAN hoje | Lacuna |
|---|---|---|---|
| Autenticação | senha SHA-1 + SSO cookie | Keycloak (stack local saudável) + `platform/sigsan-auth` (frontend) | OK, melhor. Falta política de expiração/bloqueio/histórico de senha configurável (Keycloak pode fazer) |
| Autorização | Grupo x Funcionalidade x Operação + restrição + permissão especial + abrangência | `roles` (ADMIN, OPERATOR, MANAGER, VIEWER) e `user_roles` | **Falta** permissão granular por recurso/ação, restrição negativa, abrangência por unidade/localidade, "permissões especiais" de exceção |
| Escopo organizacional | Estado > Gerência Regional > Elo Pólo > Localidade > Unidade de Negócio | só tenant (empresa) | Falta hierarquia dentro do tenant (RLS por tenant não cobre escopo interno) |
| Auditoria | operação + diff por coluna (anterior/atual) | `audit_log` (entidade `AuditLog`) com `AuditAction` | Falta diff por campo e catálogo de campos auditados; falta `registra_transacao` por operação |
| Isolamento multi-tenant | **inexistente** (um banco por concessionária, subclasses por empresa) | schema por tenant + RLS FORCE testada | SIGSAN já é melhor |
| LGPD | nada formal; só log de consulta a bureau | módulo `compliance/lgpd` (engines + `ConsentRecord`) | GSAN não ajuda; usar só o conceito "log de acesso a dado de terceiro" |

### 3.3 Catálogo de permissões especiais **[V]** (`PermissaoEspecial.java`)

~100 flags de exceção. Agrupadas por tema (ids entre parênteses):

- **Alçada financeira**: incluir devolução acima do valor máximo (40), crédito a realizar acima do valor máximo (41) ou do nº máximo de parcelas (42), informar motivo de não cobrança (30), retirar taxa de cobrança do extrato (44), incluir acréscimo de impontualidade em extrato com desconto (43), alterar valor sugerido em tipo de débito (64), testar valor mínimo de prestação/entrada (21/22), parcelar sem incluir acréscimos (18) ou sem débito a cobrar (14), cancelar parcelamento (201).
- **Faturamento/conta sem RA**: retificar conta sem RA (48), cancelar conta sem RA (51), alterar vencimento sem RA (35), inserir débito a cobrar sem RA (37), guia de pagamento sem RA (38), retificar para menor (58), retificar além do prazo padrão (59), retificar contas pagas (77), retificar conjunto (74), enviar conta para revisão (94), retirar de revisão por antiguidade (78), retificar perfil bloqueado (83), conta de faturamento antecipado (36).
- **Cadastro**: alterar imóvel (24), alterar nome de cliente (39), alterar área construída (88), alterar cliente inativo (111), incluir cliente sem CPF (96) / sem CPF-CNPJ (112) / nome genérico (103) / nome curto (102) / cliente duplicado (101), imóvel público (63), perfil corporativo (55/56), bloquear alteração de imóveis (86), alterar percentual de coleta de esgoto (76), alterar tarifa de consumo ao retificar (61), remover categoria não residencial (25).
- **Operacional/OS**: encerrar OS sem atividades (106), informar data de encerramento de OS anterior à corrente (57), gerar OS seletiva de hidrômetro (50), efetuar/atualizar ligação sem RA (65-70), efetuar supressão (79), tramitar RA para qualquer unidade (110), tramitar RA para sistema GIS externo "ACQUAGIS" (87), replicar valor de cobrança de serviço (93), finalizar arquivo texto de leitura (92).
- **Cobrança/cobrança terceirizada**: emitir documento de cobrança (85), informar qualquer situação de cobrança (90), imóvel em situação de cobrança (6), encerrar comando de cobrança por empresa (109), certidão negativa com cliente superior (75), emitir 2ª via sem documento válido (73), incluir cliente sem negativação (107).
- **Sistema/batch**: reiniciar batch (47), consultar arquivo texto gerencial (100), habilitar banco ao manter conta (80).

Leitura **[I]**: isso é o **catálogo de regras de segregação de funções e limites de alçada** que as concessionárias exigiram ao longo dos anos. Vale como checklist de requisitos; o *mecanismo* (flag booleano por usuário) deve virar permissão com **limite parametrizável** (valor máximo, nº de parcelas) e **trilha de aprovação**.

---

## 4. Gerencial

### 4.1 Arquitetura **[V]**

- Schema/DB "gerencial" separado, alimentado por **batch de resumo** (≈45 `batchGerarResumo*` em `descriptors/`): tabelas `un_resumo_*` por **mês x setor comercial x quadra** (`codigoSetorComercial`, `numeroQuadra`, `anoMesReferencia`) e variante `...PorAno`. Padrão **pré-agregação noturna** (ETL), não OLAP em tempo real; há batch "Olap" (`batchGerarResumoReFaturamentoOlap`) e `fncd_icolap` na funcionalidade.
- Dimensões `G*` (cópia achatada de cadastros: `GEmpresa`, `GConsumoTarifa`, `GFaturamentoGrupo`, `GImpostoTipo`, `GArrecadador`, `GArrecadacaoForma`, `GDevolucaoSituacao`, `GDocumentoTipo`, `GLigacaoAguaSituacao/Perfil`, `GLigacaoEsgotoSituacao/Perfil`, `GSolicitacaoTipo/Especificacao`, `GMeioSolicitacao`, `GAtendimentoMotivoEncerramento`, `GDistritoOperacional`, `GRota`, `GHidrometro{Capacidade,Classe,Diametro,Marca,Motivo,Situacao,Tipo,LocalArmazenagem}`, `GLeituraSituacao`, `GConsumoTipo`, `GFinanciamentoTipo`) = **modelo estrela** com dimensões desnormalizadas (padrão a aproveitar em `intelligence`).

### 4.2 Resumos e indicadores **[V]**

| Tabela | Conteúdo |
|---|---|
| `faturamento.un_res_ind_fat` | contas emitidas/retificadas/canceladas/incluídas, economias faturadas, volume faturado água/esgoto (e cancelado/incluído), valor faturado água/esgoto/crédito/outros, acréscimo por impontualidade, arrastos, parcelamento; cada métrica em duas visões: "mês" e "mês/ano" |
| `cobranca.un_res_ind_cob` | pendência (qtd de contas e valor, por conta/serviço/parcelamento), arrecadação (no mês até/após vencimento, 2º e 3º mês, acumulado até 3 meses), faturamento líquido, parcelamento (negociado, financiado, parcelado), ligações (ativas/inativas/totais) e **ações de corte: cortes, supressões, religações, restabelecimentos** |
| `cadastro.un_resi_lig_eco` | ligações e economias por situação (ativa, potencial/factível, cortado, suprimido) x hidrômetro, poço, volume fixado (água e esgoto) |
| `micromedicao.un_resi_des_mmd` (**desempenho de micromedição**) | ligações/economias ativas, **com hidrômetro, com medição real, com hidrômetro e medição estimada**, consumo e **volume faturado medido vs. não medido** (água e esgoto), visitas e leituras efetuadas, leituras com anormalidade, hidrômetros instalados/substituídos/remanejados/retirados (ramal e poço) |
| `micromedicao.un_resumo_metas` / `un_resumo_metas_acumulado` | ligações cadastradas/cortadas/suprimidas/ativas, **ativas com débito > 3 meses**, com consumo medido/não medido/até 5 m3/média, economias por grupo de subcategoria (quadro de metas) |
| `un_resumo_hidrometro` | parque de hidrômetros por ano de fabricação (idade do parque) e macro |
| `un_resumo_registro_atendimento` | RAs gerados, **pendentes no prazo / fora do prazo**, encerrados no prazo / fora do prazo, bloqueados, por unidade de solicitação e de encerramento, por meio (online/manual), por rota |
| outros | arrecadação (por forma, arrecadador, categoria), pendência (por quadra), parcelamento, devedores duvidosos, receitas a faturar, situações especiais de cobrança/faturamento, histograma de consumo (água/esgoto, ligação/economia, subcategoria), anormalidades de leitura, instalação de hidrômetros, coleta de esgoto, consumo de água |

Há também `UnResumoIndicadorDesempenhoMicromedicaoRef2010` (variante de referência 2010 do indicador de desempenho) **[V]**.

### 4.3 BIG (Boletim de Indicadores Gerenciais) **[V]**

`relatorio/big/RelatorioBIG*` + `reports/relatorioBIG.jrxml` + batch `batchGerarDadosRelatorioBIG`, saída XLS por gerência regional/localidade/mês: nº de contas, valor faturado, valor arrecadado, **eficiência de arrecadação**, valor médio de faturamento, indicador de erros de contas (qtd/valor), recebimento médio, **inadimplência por faixa (até 30 dias, até 90 dias, acima de 90; em qtd e em valor)**, faturamentos comprometidos, **índice de hidrometração**, hidrômetros instalados/substituídos, **prazo médio de atendimento de OS**, novas ligações de água/esgoto (e economias), consumidores ligados/cortados/suprimidos/factíveis/total e índices de cortados/suprimidos/factíveis. Esta lista é o melhor catálogo de KPI do GSAN.

### 4.4 Indicadores regulatórios (SNIS/ANA/ARSAE...)

**Nenhum** indicador regulatório nominal foi encontrado. Pesquisei `SNIS|ARSAE|ARSEP|SINISA|IN049|IN013|índice de perdas|inadimpl` em `src/gcom/relatorio/gerencial` (sem resultados) e a única referência a "perdas" são relatórios financeiros (`relatorioAnalisePerdasCreditos`, que trata de **perda de crédito/inadimplência**, não de perda de água) **[V]**. A busca em `src/gcom/gerencial` estourou o tempo no Drive e o padrão com `-i` casou "ana" por engano (descartado); busca sem falso-positivo em `gerencial` por `SNIS|ARSAE` não conseguiu rodar completa, então **o "não há" cobre `relatorio/gerencial` e `relatorio/big` e é não conclusivo para o resto de `src/gcom`**. Os dados-base existem (índice de hidrometração, ligações ativas, volume faturado medido, inadimplência, RAs no prazo), mas os *indicadores com código SNIS/ARSAE* (IN009, IN013, IN049, IN051 etc.) **[I]** terão de ser definidos do zero a partir das normas, não do GSAN. Outro item: `RelatorioOrcamentoSINP` e `RelatorioQuadroMetas*` são metas internas de companhia (SINP = orçamento do plano de investimentos, **[I]**).

---

## 5. `api/`: o que é, de fato

**[V]** (`api/GsanApi.java`, `GsanRelatorio.java`, `JsonReturn.java`, `api/relatorio/*`, `lib/gsan-relatorios/`):

- **Não é uma API exposta pelo GSAN.** É um **cliente** (Jersey 1.18 + Gson 2.2.4) para um **serviço externo de relatórios** (`gsan-relatorios`, URL no parâmetro de segurança `URL_GSAN_RELATORIOS` da tabela `seguranca.parametro`).
- Contrato: o GSAN monta um `ReportDTO` e faz `POST application/json`. Campos: `name`, `titulo`, `formato` (`PDF` ou `XLS`), `cabecalho[]`, `grupos[]`, `totalizadores[]`, `dados[]` (linhas) e `resumo` (cabeçalho + dados), `omitirTotalGeral`. Cada coluna é um `ReportField` (`name`, `description`, `align`, `type`). Os campos vêm da anotação `@ReportElementType` (`description`, `header`, `group`, `totalizer`, `align`, `type` com `string|date|money|integer`) em cima da classe de dados. Resposta: `JsonReturn {url, erro}`; o GSAN então baixa o arquivo por HTTP e devolve ao navegador (`GsanApi.download`).
- Ou seja: é um **motor de relatório genérico "dados + metadados -> PDF/XLS"** desacoplado do JasperReports embutido. A ideia **é boa** e vale para o SIGSAN (relatório declarativo, sem template por relatório). A implementação (arquivo gravado em disco local com o nome, sem autenticação aparente, URL de arquivo pública) tem **falhas de segurança** (**[I]**: o `salvar()` escreve `new FileOutputStream(name)` com nome vindo do DTO, sem sanitizar: risco de path traversal e de race entre usuários).
- **Contratos mobile**: não há API REST/mobile no repositório. O "mobile" é por **arquivos texto** (leitura, atualização cadastral, acompanhamento de serviço) + um `VersaoMobile`/`FiltroVersaoMobile` em `cadastro/` **[V]**. Integrações de entrada web: `integracao/webservice/spc` (SPC) e `ConstantesWebService` (arquivo `webservice.properties`, senha CDL) **[V]**; Axis2 1.5.1 em `lib/axis2` para SOAP **[V]**. Loja virtual/portal: `portal/ControladorLojaVirtual` + `gcom/jsp/portal/*` **[V]**.
- `gcom/integracao/GisRetornoMotivo` define códigos de retorno de integração com GIS (1 sucesso, 2 login inexistente, 3-6 identificadores de logradouro/bairro/CEP inválidos ou inexistentes, 7/8 coordenada norte/leste inválida, 9 referência de endereço inválida) **[V]**: contrato de **retorno do mapa para o comercial** (usado com o sistema externo "ACQUAGIS", **[I]**).

---

## 6. Frontend legado

### 6.1 Estrutura **[V]**

- Struts 1.1 (`lib/struts1.1`, `gcom/WEB-INF/struts-config.xml` com 241 KB e **455 `<action>`**, mais ~50 `struts-config-*.xml` modulares por caso de uso), JSP + taglibs (`gsanLib.tld`, `pager-taglib`, `menus-taglib.tld`, `jhlib.tld` JavaHelp), `javascript/` com `Calendario*.js`, `dtree*.js` (árvore de menu), `wz_tooltip.js`, `popup.js`, jQuery (`javascript/jquery`), `jquery.realperson.js` (captcha), Applet de impressão térmica (`gcom/aAppletImpressaoTermica.jar`), JavaHelp (`gcom/help`).
- Páginas por módulo: `jsp/atendimentopublico` (39 itens + subpastas `registroatendimento`, `ordemservico`, `ligacaoagua`, `ligacaoesgoto`, `hidrometro`), `jsp/cadastro`, `jsp/seguranca`, `jsp/acesso`, `jsp/portal` (autoatendimento do cliente), `jsp/gerencial`, `jsp/relatorio` etc.

### 6.2 Fluxos de tela principais **[V]** (nomes de JSP)

1. **Registro de Atendimento em abas/etapas**: `registro_atendimento_inserir_dados_gerais` -> `..._dados_local_ocorrencia` -> `..._dados_solicitante` -> `..._anexos`; idem para atualizar. Telas de apoio: consultar RA, tramitar (individual e em conjunto), reiterar, reativar, encerrar, numeração manual, pendentes por local de ocorrência (popup), pendentes por imóvel (popup), consulta da OS do RA (popup), falta d'água pendente (popup), dados de agência reguladora.
2. **Ordem de Serviço**: pesquisar/filtrar -> resultado -> manter -> consultar dados -> atualizar -> encerrar; **roteiro** (`ordem_servico_roteiro_elaboracao_criterios` -> `..._programacao` -> `..._acompanhamento` com popups de alocar/remanejar equipe, reordenar, incluir serviço, informar situação); gerar OS (com popup de inserir RA); fotos; atividades, materiais, horas de execução por equipe; relatório de acompanhamento de execução; boletim; repavimentação.
3. **Consulta do imóvel em "abas"** (`jsp/cadastro/imovel/`): `imovel_consultar_cadastro`, `..._dados_complementares`, `..._debitos`, `..._pagamentos`, `..._parcelamentos_debitos`, `..._documentos_cobranca`, `..._historico_faturamento`, `..._devolucoes`, `..._analise_medicao_consumo`, `..._registro_atendimento`. É a **visão 360 da matrícula** e o ponto de entrada natural do atendente.
4. **Pesquisa de imóvel**: `imovel_filtrar` com blocos de critérios separados (`imovel_localizacao_filtrar`, `imovel_cliente_filtrar`, `imovel_caracteristicas_filtrar`, `imovel_perfil_filtrar`, `imovel_agua_esgoto_consumo_filtrar`, `imovel_dados_faturamento_cobranca_filtrar`, `imovel_programa_especial_filtrar`, `imovel_filtrar_situacao`, `exibir_filtrar_imovel_outros_criterios_localizar_imoveis`) e o popup `imovel_pesquisar`. O padrão é **identificar por inscrição** (localidade.setor.quadra.lote.sublote) ou matrícula; inscrição em campos separados com auto-avanço. Esse padrão de **"pesquisa-popup reutilizável" para qualquer FK** (cliente, logradouro, tipo de serviço, equipe, etc.) é onipresente (`*_pesquisar.jsp` + `*_resultado_pesquisa.jsp`).
5. **Manter X**: todo cadastro auxiliar segue `*_filtrar` -> `*_manter` (lista com checkbox de remoção em lote) -> `*_atualizar` / `*_inserir`. CRUD repetitivo e testável por geração.
6. **Portal do cliente** (`jsp/portal`): 2ª via de conta, certidão negativa (imóvel/cliente), declaração anual de quitação de débitos, parcelamento de débitos online, solicitação de serviços, cadastro de e-mail, **conta em braile**, estrutura tarifária, tarifa social, normas de instalação, negociação de débitos, lojas de atendimento, ouvidoria, questionário de satisfação **[V]**.
7. **Segurança**: login/senha (lembrar, alterar, alterar simplificada), grupo (dados gerais + acessos em 2 passos), usuário, operação/funcionalidade, permissão especial, solicitação de acesso, controle de liberação de permissão especial, consulta de transação/alteração **[V]**.

### 6.3 O que vale em UX vs. descartar

| Aproveitar (padrão de produto) | Descartar |
|---|---|
| Visão 360 do imóvel com abas por domínio | Popups JSP (`window.open`) para tudo; navegação por `.do` |
| Busca por inscrição/matrícula com campos segmentados e auto-avanço | Applet de impressão térmica (`aAppletImpressaoTermica.jar`) |
| Wizard do RA (dados gerais, local, solicitante, anexos) com validação por etapa | Calendário/tooltip/árvore de menu em JS próprio (`Calendario.js`, `dtree.js`, `wz_tooltip.js`) |
| Alerta de duplicidade/pendência no local ao abrir RA | Captcha `jquery.realperson.js` (usar hCaptcha/Turnstile no portal) |
| Roteiro de OS: tela única com programação por equipe, carga de trabalho, realocar e reordenar | JavaHelp |
| `manter` com ação em lote e filtro persistente | Menu montado de banco com URL `.do` por funcionalidade (substituir por rotas SPA + permissões) |
| Autoatendimento do portal (2ª via, parcelamento, certidão, tarifa social, braile) | Conta em braile por arquivo TXT (`batchGerarTxtImpressaoContasBraille`) como está; tratar como requisito de acessibilidade, não de formato |

---

## 7. Recadastramento / atualização cadastral

### 7.1 Conteúdo de `documents/recadastramento` **[V]**

- `layouts/Layout_Cadastro.xlsx`: arquivo **de saída do sistema para o aplicativo de campo** (arquivo de rota, texto posicional, linhas tipo 01-17): 01 cliente (usuário, proprietário, responsável: nome, tipo de pessoa, CPF/CNPJ, RG, UF, sexo, fones, e-mail, endereços), 02 imóvel (matrícula, inscrição, rota, face, município, nº IPTU, nº do medidor de energia (campo `numero_celpa`), pontos úteis, ocupantes, endereço, subcategorias residencial/comercial/pública/industrial x 4, fonte de abastecimento), 03 ramo de atividade, 04 serviços (tipo de ligação de água e esgoto, local de instalação do ramal), 05 hidrômetro (possui, número, marca, capacidade, caixa de proteção), 06 anormalidade do imóvel (lat/long, código, comentário 200 caracteres, 2 fotos, data), 07 gerais (código Febraban, ano/mês de faturamento, 0800, CNPJ, IE, login, senha, indicador de transmissão offline, versão do app, datas), 08-17 **listas de domínio** (anormalidades, ramos de atividade, situação água, situação esgoto, proteção do hidrômetro, fonte de abastecimento, marca, local do ramal, capacidade, tipo de logradouro).
- `layouts/Layout_Retorno_Cadastro.xlsx`: arquivo **de retorno do campo para o sistema**: cabeçalho tipo 00 (grupo/localidade/setor/rota/referência/versão do app) e as mesmas linhas 01-06 acrescidas de **latitude, longitude e data/hora de cada bloco**, **tipo de operação do imóvel (1 alterado, 2 novo cadastro, 3 excluído)** e **tipo de entrevistado** (usuário, proprietário, responsável, parente, funcionário). Regras de validação explícitas (tipo de logradouro e ramo "não pode ser zero ou nulo"; linha de ramo só para imóvel com categoria comercial/pública/industrial).
- `scripts/` (33 SQLs numerados) criam o módulo: menu, funcionalidades/operações, colunas monitoradas em `seguranca.tabela_coluna` (`*_atlz_cadastral`), tabelas `atualizacaocadastral.*_retorno` (imóvel, subcategoria, ramo, cliente, fone, endereço, cliente-imóvel, imagem), controle `imovel_controle_atlz_cad` e **situações** (`cadastro.situacao_atlz_cadastral`: 0 Disponível, 1 Bloqueado, 2 Em campo, 3 Transmitido, 4 Aprovado), batch e relatório.

### 7.2 Como alimenta o cadastro **[V]**

Fluxo: (1) gerar arquivo de rota por localidade/setor/rota (`batchGerarArquivoTextoAtualizacaoCadastral`, `ArquivoTextoAtualizacaoCadastral`, `gerarArquivosRevisaoAtualizacaoCadastral`, `gerarArquivosFiscalizacaoAtualizacaoCadastral` com amostra percentual); imóvel vai para **Em campo**, e abrir OS/atualizar o imóvel é **bloqueado** (`validarEncerramentoOsImovelEmCampo`); (2) aplicativo coleta e devolve; (3) retorno é carregado em tabelas **de staging** (`imovel_retorno` etc.) -> **Transmitido**; (4) **análise por diferença campo a campo** contra o cadastro vigente (`tab_atlz_cadastral`, `tab_col_atlz_cadastral`, `ValidadorAtualizacaoCadastral`, `AtualizacaoCadastralComparator`), com autorização por coluna (`tatc_icautorizado`); (5) **aprovação** individual ou em lote (`aprovarImoveisEmLote`, `verificarPermissaoAprovarImovel`, `atualizarRetornoPreAprovado`), **fiscalização** de amostra (`fiscalizarImovel`, `relatorioFichaFiscalizacaoCadastral`); (6) `atualizarImoveisAprovados` grava no cadastro de verdade e o `Interceptador` audita; (7) relatórios de inconsistências, boletim de custo, relação de imóveis por rota. Há ainda atualização cadastral **pela internet** (`relatorioAtualizacaoCadastralViaInternet`, meio de solicitação 10 "celular") e uma variante `cadastro/atualizacaocadastralsimplificado` **[V]**.

**Valor para o SIGSAN [I]**: o desenho **"staging de retorno -> diff por campo -> aprovação (individual/lote) -> fiscalização por amostragem -> merge auditado"** é excelente e serve a recadastramento censitário, a `consumer_units` e a LGPD (minimização/retificação). O formato (arquivo texto posicional) deve ser descartado em favor de sincronização JSON offline-first no app Capacitor.

---

## 8. Itens transversais relevantes encontrados

- **Batch**: ≈190 jobs em `descriptors/` (JBoss MDB/Quartz) cobrindo faturamento, arrecadação, cobrança, resumos gerenciais, atualização cadastral, negativação, contabilidade etc. Os que tocam esta fatia: `batchProgramacaoAutoRoteiroAcompServico`, `batchGerarDadosArquivoAcompanhamentoServico`, `batchGerarRAOSAnormalidadeConsumo`, `batchExecutarComandoEncerramentoRA`, `batchEmitirOrdemDeFiscalizacao`, `batchProcessarEncerramentoOSFiscalizacaoDecursoPrazo`, `batchAtualizarAutosInfracaoPrazoRecursoVencido`, `batchReligarImoveisCortadosComConsumoReal`, `batchAtualizarLigacaoAguaLigadoAnaliseParaLigado`, `batchVerificarFaturamentoImoveisCortados`, `batchGerarResumo*`, `batchGerarDadosRelatorioBIG` **[V]**. Existe uma pasta `test/` na raiz do GSAN (`ls -a`) cujo conteúdo **não foi inspecionado**; não assumir que haja suíte de testes aproveitável.
- **Multiempresa por herança**: `ControladorCadastro{CAEMA,CAER,CAERN,COMPESA,COSAMA,COSANPA,JUAZEIRO}SEJB` **[V]**.
- **SPC/Serasa** (`gcom/spcserasa`): negativação (comando, movimento, retorno, simulação, `Negativador*`) com batch; fica na fatia de cobrança, só registro da existência **[V]**.
- **Concorrência**: controle otimista por `ultimaAlteracao` em RA, OS e outros (`atencao.atualizacao.timestamp`) **[V]**; padrão a replicar com `@Version` (o `BaseEntity` do SIGSAN já tem `version`).

---

## 9. (a) Backlog de épicos para o SIGSAN

Estimativa relativa: P (<= 2 semanas), M (2-6 semanas), G (> 6 semanas). Pressupõe os módulos-alvo hoje sem POM (`commercial/*`, `operations/*`).

| # | Épico | Módulo-alvo | Origem GSAN | Tam. | Dep. |
|---|---|---|---|---|---|
| E1 | **Catálogo de solicitação**: grupo > tipo > especificação com SLA em dias (úteis/corridos + feriados), flags de comportamento, roteamento por localidade, documentos obrigatórios | `commercial/sigsan-portal` + `shared/core` | `solicitacao_tipo*`, `espec_*` | M | tenant |
| E2 | **RA (registro de atendimento/protocolo)**: máquina de estados, trâmites entre unidades, reiteração, reativação, duplicidade, encerramento com regras, anexos, canais (call center, loja, web, app, WhatsApp) | `commercial/sigsan-portal` (ou novo `sigsan-service-desk`) | `registro_atendimento*` | G | E1, cadastro de unidades |
| E3 | **OS: ciclo de vida e tipos de serviço** com estados Pendente/Em andamento/Aguardando liberação/Encerrada, OS de referência (diagnóstico -> execução), operação comercial no encerramento, cobrança do serviço | `operations/sigsan-field` | `ordem_servico`, `servico_tipo*` | G | E1; tabela `service_orders` já existe |
| E4 | **Efeitos comerciais por evento** (corte, religação, supressão, ligação nova, troca de hidrômetro) como *handlers* de domínio acionados no encerramento de OS, via eventos em `shared/sigsan-events` | `operations/sigsan-field` + `sigsan-metering` + `commercial/*` | `integracaoComercial` | M | E3, metering |
| E5 | **Máquina de situação da ligação** (potencial, factível, ligada, cortada, suprimida...) com flags de faturamento/abastecimento configuráveis | `operations/sigsan-metering` / `commercial/sigsan-billing` | `ligacao_*_situacao` | M | cadastro de ligação |
| E6 | **Programação/roteirização de equipes**: equipe, componentes, equipamentos, carga/dia, roteiro por data, realocar, rolagem automática de pendências | `operations/sigsan-field` | `programacao_roteiro`, `os_programacao` | G | E3 |
| E7 | **App de campo (Capacitor, offline-first)**: receber roteiro, registrar atividades/materiais/fotos/GPS/assinatura, sincronizar; substitui arquivo texto + IMEI | `operations/sigsan-field` + `frontend` | acompanhamento por celular | G | E6 |
| E8 | **Medição de empreiteira / boletim de OS concluídas / repavimentação** (aceite/rejeição, custo por m2) | `operations/sigsan-field` + `commercial/sigsan-accounting` | boletim, repavimentação | M | E3 |
| E9 | **Fiscalização e autos de infração** (situação encontrada, multa por fator, prazo de recurso, reincidência) | `commercial/sigsan-collection` + `operations/sigsan-field` | `fiscalizacao_situacao*`, `autos_infracao` | M | E3 |
| E10 | **OS seletiva por critério** (inspeção de hidrômetro, consumo, anormalidades) | `operations/sigsan-metering` + `intelligence/*` | `comando_ordem_seletiva` | M | E3; leitura |
| E11 | **Interrupção programada x atendimento (falta d'água)**: agenda de abastecimento/manutenção por área, aviso ao abrir RA, RA em cluster por local | `operations/sigsan-maintenance` + portal | `abastecimento_progr`, `manutencao_programacao`, RA falta d'água | M | E2, hierarquia |
| E12 | **Hierarquia operacional** (sistema > zona > setor > distrito/DMA > zona de pressão; fontes de captação; produção mensal) ligada ao GIS | `operations/sigsan-assets` + `intelligence/sigsan-gis` | `operacional.*` | M | GIS |
| E13 | **Volumes de entrada** (produção por área/mês) alimentando `LossesService` em vez de placeholders | `intelligence/sigsan-losses` | `producao_agua`, `un_resi_des_mmd` | M | E12, metering |
| E14 | **Autorização granular** (recurso x ação, restrição negativa, abrangência hierárquica, permissões especiais com limite de alçada) | `platform/sigsan-auth` + `shared/sigsan-security` | `grupo_func_operacao`, `permissao_especial` | G | Keycloak |
| E15 | **Auditoria por campo** (diff antes/depois, catálogo de campos auditados, consulta por usuário/localidade) | `shared/sigsan-core` + `compliance/lgpd` | `Interceptador`, `tab_linha_col_alteracao` | M | E14 |
| E16 | **Log de acesso a dado pessoal de terceiro** e rastro de consulta (LGPD art. 37) | `compliance/lgpd` | `ConsultaCdl`, relatório de acessos | P | E15 |
| E17 | **Recadastramento censitário**: arquivos de rota por área, staging de retorno, diff por campo, aprovação em lote, fiscalização por amostra | `commercial/sigsan-billing` (cadastro) + `operations/sigsan-reading` + app | `atualizacaocadastral` | G | E7, cadastro |
| E18 | **Data mart gerencial** (resumos mensais por setor/quadra, dimensões desnormalizadas) e **painel de KPI** | `intelligence/*` | `un_resumo_*`, BIG | M | dados de billing/cobrança |
| E19 | **KPI regulatórios** (SNIS/ANA/ARSAE: definir pela norma) | `intelligence/*` + `compliance` | **sem origem no GSAN** | M | E18 |
| E20 | **Serviço de relatórios declarativo** (DTO de colunas/grupos/totais -> PDF/XLSX, assíncrono, com autorização) | `shared/sigsan-core` | `api/` | M | storage (SeaweedFS) |
| E21 | **Pesquisa de satisfação** pós-encerramento (e-mail/WhatsApp/QR), vinculada ao serviço | `commercial/sigsan-portal` | `questionario_satisfacao_cliente` | P | E2 |
| E22 | **Portal do cliente** (2ª via, parcelamento online, certidão negativa, tarifa social, solicitação de serviços, acompanhamento de protocolo) | `commercial/sigsan-portal` + `frontend` | `jsp/portal` | G | billing, collection |
| E23 | **Terceirizadas/prestadoras**: tramitar RA/OS para prestadora, exportar/importar retorno, medição | `operations/sigsan-field` | `serv_terceiro_acomp_serv`, `exportarOrdemServicoPrestadoras` | M | E3 |
| E24 | **Configuração por tenant** de regras hoje feitas por herança por empresa (parâmetros de sistema, textos de documentos, regras de prazo) | `platform/sigsan-tenant` | `*CAERN*`, `*COSANPA*` | M | tenant |

## 10. (b) O que NÃO migrar (dívida técnica / obsolescência)

| Item | Motivo |
|---|---|
| Struts 1.1, JSP/taglibs, `struts-config*.xml` (455 actions) | EOL há mais de uma década, falhas conhecidas de segurança |
| EJB 2 (`*SEJB`, `*LocalHome`, `*Remote`), MDB e JBoss 4.0.1sp1 (`jboss-web.xml`, `descriptors/`) | substituído por Spring + Kafka/eventos |
| Hibernate com `.hbm.xml` e filtros `Filtro*` caseiros (`FiltroOrdemServico` etc.) | usar JPA/Spring Data com specifications |
| Controladores gigantes (RA 16 mil linhas, OS 19 mil) com validação + regra + persistência juntos, e mensagens por chave `atencao.*` | quebrar em serviços de domínio pequenos e testáveis |
| Herança por empresa (`ControladorCadastroCAERNSEJB` etc.) e relatórios por empresa | usar configuração por tenant |
| IDs numéricos "mágicos" de tipos/operações (`TIPO_CORTE_LIGACAO_AGUA = 242`, etc.; duplicidade do 308) | usar códigos estáveis e catálogos versionados |
| Senha SHA-1 sem sal, `Criptografia.encrypt` por substituição, SSO por cookie (`GerenciadorSSO`) | Keycloak/OIDC |
| Applet de impressão térmica, JavaHelp, calendários/tooltips/árvores em JS próprio, captcha jQuery | substituir por stack React |
| Arquivos texto posicionais e `IMEI` como identidade de equipe; integração PALM/SQL Server (`integracao/upa`) | sync JSON offline-first + identidade do dispositivo no app |
| Fotos como BLOB no banco (`osft_imfoto`, `imagem_*_atlz_cadastral`) | objeto no SeaweedFS/S3 com URL assinada |
| `GsanApi.download` gravando arquivo por nome em disco | relatório assíncrono com ID e URL assinada |
| Esquema `gerencial` como "cópia achatada" com batch noturno por quadra para tudo | manter o conceito de dimensões e pré-agregação, mas com visões materializadas/TimescaleDB e janelas incrementais |
| Dependência de SPC/Serasa específico (`integracao/webservice/spc`), SOAP/Axis2 1.5.1 | reavaliar conector por contrato; REST/mTLS |
| Calendário de feriado só nacional | usar calendário por município/UF por tenant |

## 11. (c) Mapa proposto de perfis e permissões

### 11.1 Princípios (derivados do GSAN, adaptados ao SIGSAN)

1. **Perfil = conjunto de permissões** `recurso:ação` (ex.: `os:encerrar`, `ra:tramitar`, `ligacao:cortar`), editável por tenant; os 4 papéis atuais (ADMIN, OPERATOR, MANAGER, VIEWER) viram **perfis-base** (seed) sem remover os existentes.
2. **Restrição negativa por usuário** (equivalente a `usuario_grupo_restricao`): permitir tirar uma ação específica de um perfil herdado.
3. **Abrangência** (escopo) em até 5 níveis dentro do tenant: **tenant > região/gerência > unidade de negócio > localidade/sistema > setor/DMA**; toda consulta/escrita filtra pelo escopo do usuário, além da RLS por tenant.
4. **Permissões com limite** (substituem as "permissões especiais" booleanas): `limite_valor`, `limite_parcelas`, `janela_retroativa_dias`, e exigência de **aprovação em 4 olhos** acima do limite.
5. **Perfis técnicos**: `batch/integração` (equivalente ao usuário de rotina batch e ao usuário internet) como *service accounts* sem login interativo.
6. Toda ação com `registra_auditoria = true` grava diff por campo (E15).

### 11.2 Perfis propostos

| Perfil | Escopo típico | Permissões principais | Origem no GSAN |
|---|---|---|---|
| `TENANT_ADMIN` | tenant | gestão de usuários/perfis/parâmetros, catálogos | grupo Administrador (id 1), `UsuarioTipo` admin |
| `SEGURANCA_DPO` | tenant | consulta de auditoria e log de acesso, atender titular LGPD, exportar relatórios de acesso | relatórios de acesso/alterações + `compliance/lgpd` |
| `GESTOR_REGIONAL` | região/unidade de negócio | leitura gerencial, KPI, aprovação de alçadas, reabrir RA/OS | `grup_icsuperintendencia`, abrangência Gerência |
| `ATENDENTE` (central) | tenant ou região | `ra:criar/consultar/tramitar(própria unidade)/reiterar`, consulta 360 do imóvel, 2ª via | grupos de atendimento; "unid_iccentralatendimento" |
| `ATENDENTE_LOJA` | unidade/loja | idem + parcelamento, certidão, caixa de atendimento | grupo `ATENDENTE_LOJA` (id 46) |
| `ATENDENTE_SENIOR` | região | `ra:tramitar(qualquer unidade)`, `ra:reativar`, `ra:encerrar em lote` | permissão especial 110 |
| `DESPACHANTE` / `PROGRAMADOR` | unidade | `os:gerar/atualizar/programar/remanejar equipe`, carga de trabalho | roteiro de OS |
| `EQUIPE_CAMPO` | equipe/dispositivo | `os:executar`, registrar atividades/materiais/fotos/GPS, motivos de não conclusão | acompanhamento por celular |
| `ENCERRAMENTO_OS` | unidade | `os:encerrar` (com atividades); variante `os:encerrar_sem_atividades` e `os:data_retroativa` como permissões com janela | permissões 106 e 57 |
| `FISCAL` | unidade | `fiscalizacao:gerar/informar_retorno`, `autos_infracao:emitir`, `desfazer_fiscalizacao` | 31, 52, 53 |
| `LIGACOES` (corte/religação) | unidade | `ligacao:cortar/religar/suprimir/restabelecer`, alterar situação (com débito exige alçada) | 65-81 |
| `COMERCIAL_CADASTRO` | região | cadastro de imóvel/cliente, recadastramento, aprovar atualização cadastral em lote | 24, 39, 86-88, 96-112 |
| `FATURAMENTO` | tenant | faturar, retificar/cancelar/revisar conta (com limites e janela de competência) | 48-61, 74-78, 83-84, 94, 105 |
| `COBRANCA` | região | ação de cobrança, negativação, parcelamento com limites, extrato com desconto | 14, 18, 21-22, 43-44, 85, 90, 109 |
| `FINANCEIRO` | tenant | devolução/crédito acima de limite (aprovação), contabilidade | 40-42 |
| `MEDICAO_LEITURA` | rota/setor | leitura, anormalidades, hidrômetro, OS seletiva de hidrômetro | 50, 92, 104 |
| `OPERACAO_REDE` (futuro) | sistema/DMA | ativos, programação de interrupção, manutenção, perdas | `operacional.*` (E11-E13) |
| `PRESTADORA_TERCEIRO` | contrato | só OS/RA tramitadas a ela, retorno de execução, medição | `tramitarRAExportandoOSPrestadoras` |
| `AUDITOR` (leitura) | tenant | leitura total sem escrita + trilha | VIEWER ampliado |
| `PORTAL_CLIENTE` | próprio titular | ver/pagar contas, solicitar serviço, acompanhar protocolo, LGPD self-service | `icinternet`, `jsp/portal` |
| `INTEGRADOR` (service account) | tenant | escopos de API específicos (GIS, bancos, SINIR, SCADA), sem UI | usuário batch |

### 11.3 Mapeamento rápido: permissão especial do GSAN -> permissão SIGSAN

| GSAN (id) | SIGSAN |
|---|---|
| 106 Encerrar OS sem atividades | `os:encerrar_sem_atividades` |
| 57 Data de encerramento anterior à corrente | `os:encerrar_retroativo` (limite em dias) |
| 110 Tramitar RA para qualquer unidade | `ra:tramitar_qualquer_unidade` |
| 40/41/42 Devolução/crédito acima do máximo | `financeiro:credito` com `limite_valor` e `limite_parcelas` + aprovação |
| 48, 51, 35, 37, 38 Conta/débito/guia sem RA | `faturamento:*` com `exige_protocolo=false` apenas para perfis qualificados |
| 65-70 Ligação sem RA | `ligacao:efetuar_sem_protocolo` |
| 79 Efetuar supressão | `ligacao:suprimir` |
| 81 Alterar situação da ligação com débito | `ligacao:alterar_situacao_com_debito` com aprovação |
| 86 Bloquear alteração de imóveis | flag de **congelamento cadastral** por área/período (ligada a E17) |
| 96, 112 Cliente sem CPF/CNPJ | `cadastro:cliente_sem_documento` (deve gerar alerta LGPD/qualidade de dado) |
| 47 Reiniciar batch | `plataforma:reprocessar_job` |
| 100 Consultar arquivo gerencial | `intelligence:exportar_dados` |

---

## Recomendações para o SIGSAN

Legenda de risco/esforço: B (baixo), M (médio), A (alto). "Existe" refere-se ao que o diário de bordo (09/10/2026) e o schema de tenant mostram.

### P0 (fazer primeiro; destravam o produto executável e o diferencial de perdas/LGPD)

| # | O que absorver | Módulo-alvo | Existe hoje | Falta | Risco/esforço |
|---|---|---|---|---|---|
| 1 | **Ciclo de vida da OS e tipos de serviço configuráveis** (4 estados, OS de referência, operação comercial ao encerrar, cobrança do serviço), modelados como máquina de estados + eventos | `operations/sigsan-field`, `shared/sigsan-events` | tabela `service_orders` (status VARCHAR, priority, parent_order_id, JSONB de materiais/fotos, GPS LINESTRING) sem POM/serviço/API | catálogo `service_type`, transições e validações (E3/E4), controller REST, handlers de efeito comercial | M / G |
| 2 | **Autorização granular + abrangência + alçada** (E14) | `platform/sigsan-auth`, `shared/sigsan-security`, `platform/sigsan-tenant` | 4 roles seed, RLS por tenant testada, Keycloak no ar | permissões `recurso:ação`, restrição negativa, escopo hierárquico dentro do tenant, limites e 4 olhos | M / M-G |
| 3 | **Auditoria por campo** (diff antes/depois) e trilha de operação | `shared/sigsan-core`, `compliance/lgpd` | `audit_log` + `AuditAction` (corrigido no diário) | diff por coluna, catálogo de campos auditados, consulta por usuário/área, retenção | B-M / M |
| 4 | **Volumes de entrada para perdas** a partir de produção por área/mês e volume faturado medido/não medido (resumos do tipo `un_resi_des_mmd`) | `intelligence/sigsan-losses`, `operations/sigsan-metering` | `LossesService` com 4 métodos de placeholder | modelo de produção/volume por DMA e mês, ingestão (SCADA/telemetria/manual), testes com cálculo à mão | M / M |
| 5 | **Hierarquia operacional** sistema > zona > setor > distrito/DMA > zona de pressão, ligada a imóvel/ligação | `operations/sigsan-assets`, `intelligence/sigsan-gis` | EPANET stub, `addresses` com GEOMETRY | cadastro de áreas de medição e relação com ligações/consumidores | M / M |

### P1 (cobertura funcional de atendimento e campo)

| # | O que absorver | Módulo-alvo | Existe | Falta | Risco/esforço |
|---|---|---|---|---|---|
| 6 | **RA/protocolo** (catálogo de solicitação com SLA, trâmite, reiteração, duplicidade, encerramento com regras e pesquisa de satisfação) | `commercial/sigsan-portal` | nada | E1, E2, E21 | M / G |
| 7 | **Programação e roteirização de equipes + app de campo offline** | `operations/sigsan-field`, `frontend` | colunas `team_id`, `vehicle_id` em `service_orders`; Capacitor planejado | entidades equipe/roteiro, carga/dia, rolagem automática, sync offline, fotos em objeto storage | M / G |
| 8 | **Máquina de situação da ligação** e efeitos de corte/religação/supressão/troca de hidrômetro | `operations/sigsan-metering`, `commercial/sigsan-billing` | `consumer_units` | situação com flags de faturamento/abastecimento, selo/lacre, motivos | M / M |
| 9 | **Interrupção programada x RA de falta d'água** (aviso ao atendente, cluster por local) | `operations/sigsan-maintenance`, portal | nada | agenda de interrupção por área, cluster de RAs | B-M / M |
| 10 | **Fiscalização e autos de infração** | `commercial/sigsan-collection`, `operations/sigsan-field` | nada | modelo de situação encontrada, multa por fator, prazo de recurso | M / M |
| 11 | **Recadastramento com staging, diff por campo e aprovação em lote** | `commercial/sigsan-billing`, `operations/sigsan-reading`, app | nada | E17; **congelamento cadastral** durante coleta | M / G |
| 12 | **Serviço de relatórios declarativo** (metadados + dados -> PDF/XLSX, assíncrono) | `shared/sigsan-core` | geração de PDF do MTR (OpenPDF/similar) | contrato do DTO, fila, storage, autorização | B / M |

### P2 (maturidade e diferenciação)

| # | O que absorver | Módulo-alvo | Risco/esforço |
|---|---|---|---|
| 13 | **Data mart gerencial** (dimensões desnormalizadas + resumos mensais por setor/quadra) e **BIG** como pacote de KPI inicial (eficiência de arrecadação, inadimplência por faixa, índice de hidrometração, prazo médio de OS, cortes/suprimidos/factíveis) | `intelligence/*` | M / M |
| 14 | **KPI regulatórios SNIS/ANA/ARSAE** definidos pela norma (não há base no GSAN) | `intelligence/*`, `compliance` | M / M (pesquisa regulatória) |
| 15 | **OS seletiva por critério** (inspeção de hidrômetro/consumo/anormalidade) e **medição de empreiteira** (boletim, repavimentação) | `operations/sigsan-metering`, `field`, `accounting` | M / M |
| 16 | **Terceirizadas** (tramitar RA/OS a prestadora e receber retorno) | `operations/sigsan-field` | M / M |
| 17 | **Portal do cliente** (2ª via, parcelamento, certidão, tarifa social, protocolo) | `commercial/sigsan-portal`, `frontend` | M / G |
| 18 | **Parametrização por tenant** das regras que o GSAN resolve por herança por empresa | `platform/sigsan-tenant` | M / M |
| 19 | **LGPD**: log de acesso a dado pessoal de terceiro, minimização no recadastramento (campos opcionais, CPF sem obrigatoriedade onde a lei não exige), retificação via fluxo de aprovação | `compliance/lgpd` | B / P-M |

### Alertas finais

- **Licença**: tratar o GSAN como "fonte de conhecimento" somente; confirmar licença exata com o jurídico; não copiar SQL, jrxml, layouts nem texto de telas. Registrar a regra no diário de borda.
- **Testes**: não confiar em suíte de testes do GSAN (pasta `test/` não inspecionada); as regras acima (prazos úteis, encerramento de RA/OS, carga de trabalho) devem entrar no SIGSAN já com testes de regra escritos a partir deste relatório, seguindo a lição do diário ("build verde não prova correção").
- **Não repetir**: ausência de `@SpringBootApplication` e API REST continua sendo o gargalo; os épicos E3, E2 e E14 só viram valor depois de existir o entry point (Fase B do diário).
