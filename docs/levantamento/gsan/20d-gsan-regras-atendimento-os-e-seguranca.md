# 20d - GSAN: regras de atendimento, ordens de serviço, operacional e segurança, cruzadas com o Ziip SAN

Data: 14/10/2026. Plano de referência: `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` v2.13.

Fonte GSAN (somente leitura, engenharia limpa): `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN\src\gcom`, pacotes
`atendimentopublico`, `operacional`, `seguranca` e `integracao`, além de `cadastro/sistemaparametro`,
`interceptor/Interceptador.java`, `gui/util/FiltroSegurancaAcesso.java` e `util/Util.java`.
Ponto de partida: `docs/levantamento/04-gsan-operacional-atendimento-seguranca-api.md`, que não é repetido aqui.

Convenções:

- [FATO]: verificado no código citado. [INFERÊNCIA]: leitura do analista, sem prova direta no código.
- Siglas de arquivo, todas sob `src/gcom/`: CRA = `atendimentopublico/registroatendimento/ControladorRegistroAtendimentoSEJB.java`;
  COS = `atendimentopublico/ordemservico/ControladorOrdemServicoSEJB.java`;
  CLA = `atendimentopublico/ligacaoagua/ControladorLigacaoAguaSEJB.java`; CINT = `integracao/ControladorIntegracaoSEJB.java`;
  CA = `seguranca/acesso/ControladorAcessoSEJB.java`; CU = `seguranca/acesso/usuario/ControladorUsuarioSEJB.java`;
  CPE = `seguranca/ControladorPermissaoEspecialSEJB.java`; CT = `seguranca/transacao/ControladorTransacaoSEJB.java`;
  FSA = `gui/util/FiltroSegurancaAcesso.java`; ELA = `gui/seguranca/acesso/EfetuarLoginAction.java`;
  SHL = `gui/SessaoHttpListener.java`; INT = `interceptor/Interceptador.java`;
  SP = `cadastro/sistemaparametro/SistemaParametro.java`. O número após dois-pontos é linha aproximada.
- Natureza da regra: **LEGAL** (decorre de lei ou norma); **POLÍTICA** (parâmetro do tenant); **GSAN** (escolha do
  GSAN, não absorver); **PRÁTICA** (boa prática, absorver como comportamento fixo do produto).
- Situação no Ziip SAN: **COBERTA** (tarefa ou ADR já prevê), **PARCIAL** ou **AUSENTE**.
- Nenhum trecho de código, SQL, JSP ou texto de tela do GSAN foi copiado. A licença do GSAN segue não confirmada (doc 04 §0).

## 1. Resumo executivo

1. O GSAN trata atendimento e OS como um fluxo único (o RA gera OS; a OS encerrada com execução aplica o efeito comercial), mas sem prorrogação de prazo, sem ouvidoria e sem reversão do efeito comercial [FATO].
2. O prazo do RA conta dias úteis só com feriado nacional; o feriado municipal está desligado no RA; não há prazo por canal nem por urgência [FATO].
3. O efeito comercial (ligação, corte, religação, supressão, hidrômetro) só ocorre no encerramento com execução e é definitivo; o corte não revalida débito no encerramento [FATO].
4. O GSAN não tem estoque: material é catálogo, quantidade prevista e quantidade usada. O ADR-0032 (alçada, reserva e baixa) é diferencial do Ziip SAN [FATO].
5. A segurança do GSAN tem bom modelo conceitual (funcionalidade × operação, restrição negativa, abrangência) e implementação frágil: rotas de pesquisa e relatório sem checagem, RA e OS sem filtro de abrangência, senha provisória em claro por e-mail, remoção de usuário que apaga a trilha [FATO].
6. O Ziip SAN não tem hoje protocolo, catálogo de solicitação, OS com ciclo de vida, unidade organizacional, permissão granular nem abrangência; só `service_orders` sem regras e papéis do realm (`V1_1`, `SecurityConfig.java`) [FATO].
7. A diretriz D2 (toda solicitação ou alteração de cliente ou UC gera OS, inclusive interna) exige que a P1.3 grave alterações por OS interna, o que ela hoje não prevê; P3.4 e ED-36 passam a estender um núcleo antecipado.
8. Pela D1, bifurcações legais ou de prática viraram parâmetros (seção 5): 22 chaves novas, 17 existentes reaproveitadas; pela D3, lei federal proibitiva fica em classe a.
9. As cinco lacunas mais graves: OS interna antes da P3; protocolo com prazo, prorrogação e relatório mensal antes da verificação da NR 11 (20/05/2027); permissão granular com escopo; revalidação do corte na execução; política de senha e bloqueio por tenant em realm único.
10. As perguntas ao dono (seção 8) tratam só de escopo e ordem de entrega.

### 1.1 Diretrizes do dono do produto aplicadas (14/10/2026)

- **D1. Bifurcação vira parâmetro.** Quando lei, norma de agência ou prática legítima admite caminhos alternativos,
  ou há normas em conflito, o produto oferece todos como parâmetro do tenant, com o caminho mais protetivo ao usuário
  como padrão de fábrica, nas classes do `PARAMETROS-DO-TENANT.md` §2.
- **D2. A unidade consumidora é o pilar.** Tudo se liga a `consumer_units`. Toda solicitação ou alteração de cliente
  ou de unidade consumidora, inclusive alteração cadastral feita no backoffice ou pelo consumidor no portal, gera uma
  OS, ainda que interna e encerrada sem ida a campo; o efeito no cadastro é aplicado pelo encerramento da OS.
- **D3. Lei federal proibitiva prevalece.** Onde a lei federal proíbe expressamente (por exemplo, as vedações de corte
  por inadimplência da Lei 14.015/2020), o comportamento é travado (classe a), ainda que a norma da agência permita.

## 2. Catálogo de regras

### 2.1 Atendimento: registro, protocolo, prazo, trâmite e ouvidoria

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| ATD-01 | Todo atendimento recebe protocolo único na abertura; o GSAN compõe ano, dígito fixo e sequencial de 9 dígitos [FATO] | CRA (`obterProtocoloAtendimento`) | LEGAL (NR 11 art. 52 §1º; Lei 13.460 art. 12) | PARCIAL: P3.4 prevê "protocolo NR 11" sem formato nem canais | P3.4: sequência por tenant, comprovante em todo canal; reaproveitar o gerador parametrizado do ADR-0020 |
| ATD-02 | Protocolo ligado ao imóvel quando a especificação exige matrícula; sem exigência, só endereço [FATO] | `SolicitacaoTipoEspecificacao.icmatricula`; CRA:715-749 | PRÁTICA | AUSENTE | D2: protocolo sempre com `consumer_unit_id` quando houver UC; sem UC, endereço ou geometria (18f TR-613); P3.4 |
| ATD-03 | Data prevista = data do atendimento + dias da especificação; sem prazo cadastrado, prevista = data do atendimento [FATO] | CRA:715-749, 8844-8880 | POLÍTICA (lista de prazos da ERI: NR 11 arts. 72-73) | AUSENTE: `sac.response_days` é chave genérica | Prazo por especificação no catálogo versionado (ED-34); sem prazo, prazo acordado registrado na abertura (NR 11 art. 72); mínimo na P3.4 |
| ATD-04 | Dias úteis excluem sábado, domingo e feriado nacional; o municipal está desligado no RA; a cobrança usa nacional e municipal [FATO] | `Util.java`:2054-2083, 4428-4441; CRA:727-739 | POLÍTICA | PARCIAL: `cal.holidays` por esfera no P2; motor de dias úteis previsto só no suporte (AJUDA-SUPORTE §2) | Motor único de prazo em `shared` para protocolo, OS, corte e suporte; `sac.deadline_day_basis` e `sac.holiday_scope`; P3.4 |
| ATD-05 | Não há prorrogação: prevista original e atual recebem o mesmo valor e a atualização sobrescreve as duas, sem histórico [FATO] | CRA:6976-6979, 8346-8350 | GSAN | AUSENTE | Prorrogação como evento com motivo, autor e aviso ao usuário, sem apagar o prazo original; relatórios contra o original; `sac.extension_policy`; P3.4 |
| ATD-06 | Reiteração só aceita depois de vencida a data prevista atual [FATO] | CRA:3203-3208 | GSAN | AUSENTE | Reiteração aceita a qualquer tempo e registrada (Lei 13.460 art. 11: nenhuma manifestação recusada); soma à prioridade; ED-35 |
| ATD-07 | Abertura bloqueada se houver RA aberto da mesma especificação no mesmo imóvel [FATO] | CRA (`verificarExistenciaRAImovelMesmaEspecificacao`) | GSAN | AUSENTE | Não recusar: vincular como reiteração ou duplicidade, com aviso ao atendente (`sac.duplicate_handling`); ED-35 |
| ATD-08 | A especificação pode impedir a abertura quando o imóvel tem débito [FATO] | `SolicitacaoTipoEspecificacao.icverificardebito` | POLÍTICA (conflito: dever de registrar versus condição de serviço) | AUSENTE | Registrar sempre; condicionar só a execução dos serviços listados pela ERI (`svc.debt_blocks_request`, padrão nenhum); ED-34 |
| ATD-09 | Documento obrigatório por especificação e por meio de solicitação [FATO] | `SolicitacaoDocumentoObrigatorio`; `icdocumentoobrigatorio` | LEGAL (limites: Lei 13.726 art. 3º; Lei 13.460 art. 10-A) | AUSENTE | Lista de documentos no catálogo, sem firma reconhecida nem cópia autenticada (travado); CPF basta; anexos pela P1.20; ED-34 |
| ATD-10 | Solicitante pode ser cliente, funcionário de unidade ou só nome; ao menos um; desde 2011 o cliente não precisa ter vínculo com o imóvel [FATO] | `RegistroAtendimentoSolicitante`; CRA | PRÁTICA | AUSENTE | Solicitante como papel no protocolo (titular, usuário, terceiro, interno), contato com opt-in por canal (ED-41) e minimização; ED-35 |
| ATD-11 | Especificação com encerramento automático: RA encerrado logo após o registro, com motivo "conclusão" e observação obrigatória [FATO] | `ConcluirInserirRegistroAtendimentoAction`:322-374 | PRÁTICA | AUSENTE | Protocolo informativo encerra no ato com resposta registrada; havendo alteração de cliente ou UC, gera OS interna (D2); ED-35 |
| ATD-12 | Ao encerrar a OS, o RA encerra junto se o tipo de serviço mandar; senão a tela pergunta; RA de terceirizada volta tramitado [FATO] | COS:4046-4105; `EncerrarOrdemServicoAction`:448-500 | PRÁTICA | AUSENTE | Regra por tipo de serviço; protocolo só fecha com todas as OS encerradas e resposta conclusiva ao usuário (doc 13 AT-04, CB-07); ED-35 e ED-36 |
| ATD-13 | Encerramento do RA com motivo "sem execução" encerra as OS pendentes sem execução [FATO] | CRA:7405-7497 | PRÁTICA | AUSENTE | Manter, com motivo propagado, efeito comercial bloqueado e reserva de material liberada (ADR-0032); ED-36 |
| ATD-14 | Motivo de encerramento com indicadores de execução, duplicidade, aditivo de prazo e uso na seletiva; duplicidade exige RA de referência distinto e aberto [FATO] | `AtendimentoMotivoEncerramento`; CRA | PRÁTICA | AUSENTE | Catálogo de motivos por tenant com semântica fixa (executado, não executado, duplicidade, desistência, débito pago, decurso); ED-34 |
| ATD-15 | Alteração cadastral, de conta, de vencimento e transferência de débito só liberadas com RA aberto de especificação ligada à ação [FATO] | `EspecificacaoTipoValidacao`; CRA:10674-10800 | PRÁTICA | AUSENTE | Base da D2: invariante de domínio, sem flag; P1.3, P2.20, ED-21 |
| ATD-16 | Critério de situação do imóvel por especificação impede serviço incompatível; a combinação tem provável defeito de OU [FATO] | CRA:2326-2470 | PRÁTICA | AUSENTE | Pré-condição declarativa por tipo de serviço, avaliada na abertura e de novo no encerramento; ED-34 |
| ATD-17 | Trâmite com parecer de até 400 caracteres; RA e suas OS mudam juntas de unidade; trâmite impedido com OS em execução, aguardando liberação ou programada [FATO] | CRA:5662-5735 | PRÁTICA | AUSENTE: não há unidade organizacional no tenant | `org_units` por tenant e trâmite no protocolo; bloqueio com OS programada mantido; ED-35 |
| ATD-18 | Quem altera o RA: permissão especial, lotação na unidade atual, central de atendimento ou unidade superior [FATO] | CRA:2508-2600 | PRÁTICA | AUSENTE | Escopo por unidade (seção 4); permissão `protocol:transfer-any-unit`; ED-06 |
| ATD-19 | Unidade destino sugerida por especificação e localidade; grande consumidor vai à unidade do parâmetro; uma empresa aceita várias centralizadoras [FATO] | CRA:8899, 5547-5590 | POLÍTICA | AUSENTE | Tabela de roteamento por tenant (especificação × área × perfil do cliente), nunca código por empresa; ED-35 |
| ATD-20 | RA urgente só gera alerta a grupos configurados; não muda prazo [FATO] | `icurgencia`; `VisualizacaoRegistroAtendimentoUrgencia` | PRÁTICA | AUSENTE | Urgência no catálogo com prazo próprio (religação urgente, extravasamento) e fila priorizada; ED-35 |
| ATD-21 | Ouvidoria não existe; há só registro da reclamação vinda da agência (número, situação, previsão, retorno), sem fluxo [FATO] | `RaDadosAgenciaReguladora`, `AgenciaReguladoraMot*` | LEGAL (Lei 13.460 arts. 10-16; NR 11 art. 52 §§2º-3º) | AUSENTE: nenhuma tarefa cita ouvidoria | Tipo "manifestação de ouvidoria" com 30 dias e uma prorrogação, identidade preservada, relatório anual; referência externa da ERI; P3.4 (campo) e ED-35 (fluxo) |
| ATD-22 | Reclamações por mês, motivo e sistema e percentual não atendido: o GSAN só tem resumos gerenciais [FATO] | `un_resumo_registro_atendimento` (doc 04 §4.2) | LEGAL (NR 11 art. 54) | PARCIAL: P3.22 tem "serviços por protocolo com tempos" (POC-64) | Acrescentar à P3.22 o relatório do art. 54, com motivo classificado obrigatório no encerramento |
| ATD-23 | Pesquisa de satisfação por e-mail só ao encerrar com conclusão, com marca de uma empresa fixa no código [FATO] | CRA (envio do questionário) | LEGAL (Lei 13.460 art. 23: no mínimo anual) | AUSENTE | Pesquisa pós-encerramento e periódica, com opt-in por canal (ED-41) e marca do tenant (P3.21); `sac.satisfaction_survey` |
| ATD-24 | Não há aviso ao solicitante na abertura, no trâmite nem na prorrogação [FATO] | serviço de e-mail do GSAN | LEGAL (Lei 13.460 art. 5º; CDC art. 26 §2º I) | PARCIAL: P3.5 e ED-41 cobrem canais | Eventos do protocolo na outbox (abertura, prorrogação, resposta), com prova de entrega guardada; P3.5 |
| ATD-25 | RA de falta d'água consulta programação de abastecimento e de manutenção da área e alerta o atendente [FATO] | `operacional` (programações); CRA:4836-5242 | PRÁTICA | AUSENTE: não há entidade de interrupção no plano | `interruption_event` com área no GIS; aviso no atendimento e no portal; agrupamento de protocolos do mesmo evento; onda B, junto da ED-56 |
| ATD-26 | Pagamento em duplicidade e revisão de conta tratados por RA: vincula contas e pagamentos; a devolução gera crédito [FATO] | CRA:15149-15210 | PRÁTICA | PARCIAL: P2.20 (retificação) | D2: retificação e devolução nascem de protocolo e OS interna; P2.20 e ED-21 |
| ATD-27 | Estado "bloqueado" do RA derivado de endereço só descritivo; bloqueado não tramita nem reitera [FATO] | CRA:7000-7002, 8374-8392 | GSAN | AUSENTE | Não replicar: endereço incompleto é pendência de dado, não estado do protocolo |

### 2.2 Ordens de serviço e operacional

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| OS-01 | No mesmo RA não pode haver outra OS não encerrada do mesmo tipo [FATO] | COS (`validarOrdemServico`) | PRÁTICA | AUSENTE | Invariante por protocolo e UC; exceção explícita para lote e seletiva; ED-36 |
| OS-02 | OS sem RA só por cobrança, seletiva, retorno de terceirizada e lote [FATO] | COS (pontos de geração) | PRÁTICA | AUSENTE | D2: OS sem protocolo só de origem sistêmica (cobrança, seletiva, manutenção planejada); toda OS ligada a UC, endereço ou ativo; ED-36 |
| OS-03 | Tipo com referência exige OS de referência do mesmo RA na situação prévia exigida; a referência fica "aguardando liberação" até o retorno [FATO] | COS:773; `ServicoTipoReferencia`, `OsReferidaRetornoTipo` | PRÁTICA | PARCIAL: `parent_order_id` sem regra (`V1_1`) | Dependência com estado `WAITING_DEPENDENCY` e retorno deferido ou indeferido; "desdobramento" do edital (18f SG-577); ED-36 |
| OS-04 | O efeito comercial só ocorre no encerramento com execução, pela operação mapeada no tipo de serviço (17 a 18 operações) [FATO] | COS:3355-3358, 3882-3887, 8979-9052 | PRÁTICA | AUSENTE | Manipuladores por `effect_code` estável, disparados no encerramento, com evento na outbox (ADR-0014); P3.4 (corte e religação), ED-36 (demais) |
| OS-05 | Indicador "comercial atualizado" impede aplicar o efeito duas vezes [FATO] | COS:3208-3231, 5038-5061 | PRÁTICA | AUSENTE | Idempotência por `effect_applied_at` e chave da OS; no campo, chave da tarefa (ADR-0031, ADR-0032); ED-36 |
| OS-06 | Não há reabertura, desfazimento nem estorno do efeito; volta-se atrás com nova OS inversa [FATO] | COS (ausência de reabertura) | PRÁTICA | AUSENTE | Efeito definitivo; correção por OS compensatória vinculada, com motivo "correção" e trilha; ED-36 |
| OS-07 | Corte só de ligação ligada ou em análise; grava data, selo, tipo, motivo e leitura; não revalida débito nem data no encerramento [FATO] | COS (operação de corte); CLA | LEGAL (Lei 14.015/2020; Lei 13.460 art. 6º VII) | PARCIAL: P3.4 bloqueia geração e data | Revalidar na execução, inclusive offline: quitação ou parcelamento, contestação, vedação de dia e horário, aviso válido, classe protegida; falha cancela a OS (classe a); P3.4 e ED-20 |
| OS-08 | Religação só de cortado; restabelecimento só de suprimido; supressão de vários estados, com retirada do hidrômetro [FATO] | COS (operações) | PRÁTICA | PARCIAL: `service_connections` com situação (P2.23) | Máquina de situação da ligação com transições permitidas por efeito; ED-13 e ED-29 |
| OS-09 | Supressão a pedido bloqueia com débito; outros motivos exigem permissão especial, conforme parâmetro [FATO] | COS (supressão); SP | POLÍTICA | PARCIAL: `cut.reasons` | Supressão a pedido não depende de quitação quando a ERI vedar; ED-29 |
| OS-10 | Religação automática de cortados quando surge consumo real (rotina em lote) [FATO] | doc 04 §1.4 | GSAN | AUSENTE | Não replicar: consumo em ligação cortada gera OS de fiscalização (ED-24); religação após pagamento gera OS (`rec.auto_order_on_payment`) |
| OS-11 | Encerramento exige motivo, parecer de até 400 caracteres, atividades (salvo permissão), vistoria e pavimento conforme o tipo; concorrência otimista [FATO] | COS:2824, 3069, 3417 | PRÁTICA | AUSENTE | Requisitos de encerramento por tipo (fotos, assinatura, leitura, pavimento); `@Version`; ED-36 |
| OS-12 | Data de encerramento retroativa limitada por parâmetro, nunca futura nem anterior à geração; permissão especial libera além [FATO] | CLA; SP | POLÍTICA | AUSENTE | `so.retroactive_close_days` e permissão `so:close-retroactive`; data de execução do app não editável; ED-36 |
| OS-13 | OS pendente há mais de N + 90 dias encerrada por decurso, sem efeito comercial; OS de fiscalização vence em N dias úteis [FATO] | COS (`encerrarOrdemServicoVencida`); SP | POLÍTICA | AUSENTE | OS de cobrança expira com o aviso (`cut.notice_validity_days`); OS pedida pelo usuário não expira em silêncio (`so.customer_order_auto_expire`); ED-36 |
| OS-14 | Prioridade por seis critérios com calibragem peso × fator; prazo de execução por janela da prioridade [FATO] | `OSPriorizacaoTipo`, `OSProgramacaoCalibragem`, `ServicoTipoPrioridade` | POLÍTICA | AUSENTE | Prioridade calculada (urgência legal, prazo restante, classe protegida, reiteração) com pesos do tenant (`sched.priority_weights`); ED-36 |
| OS-15 | Programação automática diária: apaga o dia, leva pendentes da véspera, realoca por equipamento especial e preenche cada equipe até a carga, pela maior folga [FATO] | COS:16581 | PRÁTICA | PARCIAL: ED-36 (programar e desprogramar), ED-59 (equipes) | Sugestão automática com confirmação humana; nunca apagar a programação do dia sem registro; ED-36 e ED-59 |
| OS-16 | Carga da equipe em minutos (máximo 24 h), somando o tempo médio dos tipos programados [FATO] | `Equipe`; `ServicoTipo.nntempomedioexecucao` | PRÁTICA | PARCIAL: ED-59 (escala) | Tempo médio no catálogo (ED-34), recalibrado pelo tempo real medido no app (ED-20) |
| OS-17 | Inclusão manual na programação exige OS na unidade do usuário, não encerrada e não programada [FATO] | COS (inclusão manual) | PRÁTICA | AUSENTE | Manter; ED-36 |
| OS-18 | Retorno do campo com motivo de não encerramento devolve a OS a pendente; motivos catalogados [FATO] | `OsProgramNaoEncerMotivo`; COS | PRÁTICA | PARCIAL: ED-20 | Motivo de não execução obrigatório, com foto em "sem acesso"; reagendamento e aviso ao usuário; ED-20 |
| OS-19 | OS seletiva por comando: critérios combinados, quantidade máxima (0 = todos), exclusão de OS recentes de instalação ou troca, mês fechado; sem checagem de duplicidade; defeito no filtro de marca [FATO] | `ComandoOrdemSeletiva`; COS:14562-14566 | PRÁTICA | PARCIAL: ED-24 (fiscalização por critério), ED-14 (validade de hidrômetro) | Comando seletivo genérico com prévia da lista e checagem de OS aberta; ED-24 e ED-14 |
| OS-20 | Material: catálogo sem preço, quantidade padrão por tipo, quantidade usada na execução; sem estoque, requisição, reserva nem baixa; indicador de cobrança de material sem efeito [FATO] | `Material`, `ServicoTipoMaterial`, `OsAtividadeMaterialExecucao` | PRÁTICA | COBERTA: ADR-0032, ED-36, ED-58 | Manter o ADR-0032; acrescentar política de cobrança de material ao usuário (`so.material_charge_policy`) |
| OS-21 | Terceirizada: OS exportada ao tramitar para unidade terceirizada; estados a enviar, enviado, retornado; o retorno encerra com execução em nome da firma; sem medição [FATO] | CINT; `ServicoTerceiroAcompanhamentoServico` | PRÁTICA | PARCIAL: ED-37 (cadastro, contrato, medição) | Perfil `CONTRACTOR` restrito às OS do contrato; retorno por app ou API; conferência antes do efeito; ED-37 |
| OS-22 | Fotos com situação (início, meio, fim); coordenada só no movimento terceirizado; sem validação de GPS [FATO] | `OrdemServicoFoto`, `FotoSituacaoOrdemServico` | PRÁTICA | COBERTA: ED-20 | Foto com hora e posição, em armazenamento de objetos; distância da UC como alerta, não bloqueio |
| OS-23 | Histórico da OS em tabelas espelho alimentadas fora do código Java [FATO] | `OrdemServicoHistorico` | GSAN | COBERTA em parte: gatilho de auditoria (`V1_1`) | Linha do tempo por eventos de domínio, além do `audit_log`; ED-36 |
| OS-24 | Quase toda operação comercial é bloqueada com o imóvel "em campo" (leitura ou recadastramento) [FATO] | COS (imóvel em campo) | PRÁTICA | AUSENTE | Efeito que altera leitura ou ligação aguarda a sincronização do pacote de campo (ADR-0031); P2.7 e ED-36 |
| OS-25 | Permissão para manter OS: lotação, empresa terceira da localidade ou unidade superior, depois abrangência; localização validada só no encerramento, por parâmetro [FATO] | COS (manutenção de OS) | PRÁTICA | AUSENTE | Escopo por unidade e área sempre aplicado (seção 4); ED-06 |
| OS-26 | Cobrança do serviço no encerramento se o tipo tem tipo de débito e a OS não tem motivo de não cobrança; valor por vigência e faixa [FATO] | COS:1877, 3313-3353 | POLÍTICA | COBERTA: ED-34 | Manter; motivo de não cobrança com permissão; religação sem taxa quando faltou aviso (classe a, P3.4) |
| OS-27 | Programação de interrupção de abastecimento e manutenção por área, consultada pelo atendimento [FATO] | `operacional` (programações) | LEGAL (Lei 11.445 art. 40 §1º; NR 11 arts. 39-41) | AUSENTE | Ver ATD-25; aviso prévio aos usuários afetados (ED-41) e registro para o indicador de intermitência |
| OS-28 | Variação por empresa não toca atendimento, OS nem segurança; só textos de e-mail e colunas da seletiva [FATO] | `ServiceLocator.java`:157-172 | GSAN | COBERTA: ADR-0002 | Nada a herdar; textos por modelo do tenant (P3.21) |

### 2.3 Segurança

| ID | Regra | Evidência | Natureza | Situação no Ziip SAN | Recomendação |
|---|---|---|---|---|---|
| SEG-01 | Autorização por funcionalidade (URL) e operação, por grupo, com restrição negativa por usuário e dependência entre funcionalidades [FATO] | FSA; `GrupoFuncionalidadeOperacao` | PRÁTICA | PARCIAL: `roles.permissions` JSONB sem uso; só `ROLE_*` do realm (`SecurityConfig.java`:98-105) | Permissão `recurso:ação` no serviço, perfis compostos pelo ADMIN do tenant, restrição negativa; ED-06 com núcleo antecipado (seção 4) |
| SEG-02 | Rota com "pesquisar" ou "relatorio" no nome pula a checagem; rota não cadastrada é negada [FATO] | FSA:168-193 | GSAN | COBERTA: `/api/**` autenticado | Negar por padrão; teste no CI de que toda rota declara permissão |
| SEG-03 | Abrangência por igualdade de nível (estado, gerência, unidade de negócio, elo, localidade), aplicada só onde o código chama; RA e OS sem filtro [FATO] | `Abrangencia.java`; COS; CRA | PRÁTICA | AUSENTE | Escopo hierárquico em `org_units` e áreas, aplicado em toda leitura e escrita por filtro obrigatório; ED-06 |
| SEG-04 | Usuário só delega abrangência igual ou inferior à sua [FATO] | `pesquisarUsuarioAbrangenciaPorSuperior` | PRÁTICA | AUSENTE | ADMIN do tenant não concede escopo nem permissão que não possui; teste; ED-06 |
| SEG-05 | Alçada só por permissões especiais sim ou não; sem limite numérico; permissão especial inativa libera para todos [FATO] | CPE:979-1014 | GSAN | PARCIAL: ADR-0032 (material) | Generalizar `approval_rule` a crédito, devolução, desconto por vazamento (ED-23) e retificação (P2.20), com segregação travada |
| SEG-06 | Competência máxima de retificação = maior valor entre todos os grupos ativos, não os do usuário [FATO] | CPE | GSAN | AUSENTE | Não replicar; limite pela permissão efetiva do usuário |
| SEG-07 | Senha: modo normal com 4 caracteres; modo forte com letra, dígito, 6 caracteres, sem 3 iguais seguidos; lista negra modelada e não usada [FATO] | CA; SP | POLÍTICA | PARCIAL: ED-06; realm único | Mínimo de 12 caracteres e lista de senhas vazadas; tenant só endurece (`sec.password_min_length`); ED-06 |
| SEG-08 | Histórico das 3 últimas senhas opcional; expiração global ou por grupo, valendo o último grupo lido [FATO] | CA; `Grupo` | POLÍTICA | PARCIAL: ED-06 | Expiração e histórico por tenant; vale o mais restritivo entre os perfis do usuário |
| SEG-09 | Bloqueio por tentativas contado na sessão HTTP (nova sessão zera); login revela se o usuário existe [FATO] | ELA; CA | GSAN | PARCIAL: P3.3 (portal); ED-06 (POC-05 com 3 tentativas) | Contagem no servidor de identidade por usuário e IP, resposta indistinta; limite por tenant em realm único exige solução própria [INFERÊNCIA]; ED-06 |
| SEG-10 | "Lembrar senha" envia login e senha provisória em claro por e-mail; desbloqueio grava senha sem criptografia [FATO] | CA; CU | GSAN | COBERTA: Keycloak | Redefinição só por link de uso único com validade |
| SEG-11 | Solicitação de acesso com autorização do superior e período de validade [FATO] | `SolicitacaoAcesso*` | PRÁTICA | AUSENTE | Acesso com data de fim e aprovação; inativação por não uso (`sec.user_inactivity_days`); ED-06 |
| SEG-12 | Remoção física do usuário apaga também a trilha dele [FATO] | CU:1046-1062 | GSAN | PARCIAL: `audit_log` | Usuário nunca removido, só inativado; trilha imutável (classe a); P1.7 |
| SEG-13 | Auditoria por coluna (anterior e atual) só onde o código registra e a tabela está cadastrada; erro de auditoria engolido; atualização em massa escapa [FATO] | INT; CT | PRÁTICA | COBERTA em parte: gatilho em todas as tabelas com valores anterior e novo (`V1_1`); partição e retenção na P1.7 | Manter o gatilho; acrescentar `correlation_id`, protocolo e OS de origem a cada linha; consulta por usuário (ED-06) |
| SEG-14 | Bloqueio, desbloqueio, lembrar senha e bloqueio por tentativas não auditados; sem registro de login e logout [FATO] | CA; SHL | GSAN | PARCIAL: eventos do Keycloak; `log.access_retention_days` | Eventos de autenticação levados à trilha do tenant, sem documento em claro (P3.3); ED-06 |
| SEG-15 | Sessão de 900 s; login único opcional por mapa em memória, sem efeito em cluster [FATO] | SHL; SP | POLÍTICA | PARCIAL: sessão no realm (PARAMETROS §4); BFF com sessão no PostgreSQL (ADR-0006) | Sessão única opcional no BFF (`sec.single_session`); sessões ativas visíveis e revogáveis pelo ADMIN (ED-06) |
| SEG-16 | Parâmetros de segurança em registro único global, sem tenant [FATO] | SP; `SegurancaParametro` | GSAN | COBERTA: ADR-0002, P0.19 | Parâmetros com vigência e aceite por tenant |
| SEG-17 | Consulta a bureau registrada (quem consultou dado de terceiro) [FATO] | `ConsultaCdl` | LEGAL (LGPD art. 37) | COBERTA: `personal_data_access_log` (P3.20), P0.11 | Estender o registro à ficha de atendimento e à OS |
| SEG-18 | Permissões especiais de OS: encerrar sem atividades, data retroativa, tramitar a qualquer unidade, supressão, ligação sem RA [FATO] | `PermissaoEspecial` (doc 04 §3.3) | PRÁTICA | AUSENTE | Permissões nomeadas (`so:close-without-activities`, `so:close-retroactive`, `protocol:transfer-any-unit`); "ligação sem protocolo" deixa de existir pela D2 |

## 3. Máquinas de estado inferidas

### 3.1 Registro de atendimento e protocolo proposto

GSAN [FATO]: estados `PENDENTE`, `ENCERRADO` e `BLOQUEADO` (este derivado de endereço descritivo). Abertura leva a
pendente, ou a encerrado no ato quando a especificação encerra automaticamente. Pendente permanece pendente em
trâmite, reiteração e atualização. Pendente passa a encerrado por encerramento manual, pelo encerramento da OS ou por
comando em lote. Encerrado não reabre: a reativação cria novo RA vinculado.

Proposta para o Ziip SAN [INFERÊNCIA]:

```
OPEN ──(OS geradas)──> IN_PROGRESS ──(todas as OS encerradas)──> ANSWER_PENDING ──(resposta enviada)──> ANSWERED ──> CLOSED
  │                       ├──(prorrogação registrada)──> IN_PROGRESS (prazo novo; original mantido)
  │                       └──(desistência do usuário)──> CANCELLED
  └──(informativo, sem OS)──> ANSWERED ──(prova de entrega ou registro presencial)──> CLOSED
CLOSED ──(reativação)──> novo protocolo vinculado; o fechado não reabre
```

Regras do protocolo:

- O relógio do prazo para em `ANSWERED`; prorrogação não apaga o prazo original.
- Reiteração não muda o estado e soma à prioridade.
- Duplicidade fecha como `CLOSED` com motivo e vínculo ao protocolo principal, sem recusa.
- Manifestação de ouvidoria segue o mesmo fluxo, com prazo de 30 dias e uma prorrogação por igual período.
- Protocolo com OS em `SCHEDULED` ou `IN_EXECUTION` não tramita.

### 3.2 Ordem de serviço

GSAN [FATO]: `PENDENTE` (1), `ENCERRADA` (2, com ou sem execução), `EM_EXECUCAO` (3) e `AGUARDANDO_LIBERACAO` (4,
quando é referência de outra OS). A programação é atributo separado; o retorno do campo sem conclusão devolve a OS a
pendente; o decurso de prazo encerra sem execução.

Proposta [INFERÊNCIA]:

```
DRAFT ──> OPEN ──(programada)──> SCHEDULED ──(início no app)──> IN_EXECUTION ──(concluída)──> EXECUTED ──(conferência)──> CLOSED_EXECUTED
           │         └──(desprogramada)──> OPEN       └──(não concluída, motivo)──> OPEN ou CLOSED_NOT_EXECUTED
           ├──(depende de outra OS)──> WAITING_DEPENDENCY ──(retorno)──> OPEN ou CLOSED_NOT_EXECUTED
           ├──(alçada ou análise cadastral)──> PENDING_APPROVAL ──> OPEN, CLOSED_EXECUTED (interna) ou CLOSED_NOT_EXECUTED
           └──(cancelada, decurso, pré-condição perdida)──> CLOSED_NOT_EXECUTED
```

Regras da OS:

- O efeito comercial é aplicado só na passagem a `CLOSED_EXECUTED`.
- `EXECUTED` separa a execução de campo da conferência: fiscal da terceirizada, alçada a posteriori de material
  (ADR-0032), divergência de cálculo da emissão em campo (ADR-0031).
- OS interna vai de `OPEN` ou `PENDING_APPROVAL` direto a `CLOSED_EXECUTED`.
- Nenhum estado sai de `CLOSED_*`; correção por OS compensatória vinculada.
- Antes de `IN_EXECUTION` e de `CLOSED_EXECUTED`, a pré-condição do tipo de serviço é avaliada de novo (ATD-16, OS-07).

### 3.3 Vínculo protocolo → OS → efeito comercial (regra geral pela D2)

1. Toda solicitação, reclamação ou alteração de cliente ou de unidade consumidora abre ou usa um protocolo ligado à
   UC (`consumer_unit_id`); sem UC, ligado a endereço ou geometria.
2. Todo pedido que muda dado de cliente, UC, ligação, hidrômetro, conta ou débito gera ao menos uma OS a partir do tipo
   de serviço do catálogo. A OS tem `execution_mode`: `FIELD` (campo), `INTERNAL` (backoffice, sem ida a campo) ou
   `CONTRACTOR` (terceirizada).
3. O efeito é aplicado exclusivamente no encerramento com execução, por manipulador identificado por `effect_code`,
   uma única vez, na mesma transação que grava o encerramento, com evento na outbox para notificações e integrações.
4. Alteração gravada fora desse fluxo é recusada pela camada de domínio. Exceções: carga inicial e importação de
   legado (origem `MIGRATION`) e correções da plataforma por acesso assistido (ADR-0020), sempre auditadas.
5. O protocolo encerra quando todas as suas OS estão encerradas e a resposta ao usuário foi registrada.

Efeitos por tipo de pedido [INFERÊNCIA a partir das operações do GSAN, COS:8979-9052]:

| Pedido | Modo da OS | `effect_code` | Efeito no encerramento com execução |
|---|---|---|---|
| Contato (e-mail, telefone) pelo portal ou backoffice | `INTERNAL`; encerra sozinha se `so.internal_change_auto_apply` permitir | `CUSTOMER_CONTACT_UPDATE` | grava o contato; aviso ao contato anterior |
| Dados do cliente (nome, documento) | `INTERNAL` com análise | `CUSTOMER_DATA_UPDATE` | nova versão com documento anexo |
| Troca de titularidade | `INTERNAL` com análise | `OWNERSHIP_TRANSFER` | encerra o vínculo anterior, abre o novo, revoga o vínculo do portal (P3.20) |
| Categoria, economias, área | `INTERNAL` ou `FIELD` (vistoria) | `UNIT_PROFILE_UPDATE` | nova versão do perfil da UC, com vigência na competência seguinte |
| Ligação nova de água ou esgoto | `FIELD` com dependência de vistoria | `CONNECTION_CREATE` | situação ligada, data, hidrômetro instalado |
| Corte, supressão | `FIELD` | `CONNECTION_CUT`, `CONNECTION_SUPPRESS` | situação, selo, leitura de corte; revalidação na execução |
| Religação, restabelecimento | `FIELD` | `CONNECTION_RECONNECT`, `CONNECTION_RESTORE` | situação ligada; prazo medido contra `rec.sla_hours` |
| Hidrômetro: instalação, troca, retirada | `FIELD` | `METER_INSTALL`, `METER_REPLACE`, `METER_REMOVE` | histórico do hidrômetro, leituras de retirada e instalação, baixa de estoque (ADR-0032) |
| Retificação de conta, devolução, desconto por vazamento | `INTERNAL` com alçada | `BILL_ADJUST`, `REFUND`, `LEAK_DISCOUNT` | retificação pela P2.20, aprovada conforme a alçada |
| Vencimento alternativo | `INTERNAL` | `DUE_DATE_CHANGE` | novo dia a partir da competência seguinte |
| Reclamação sem alteração (qualidade, atendimento) | `INTERNAL` ou `FIELD` (vistoria) | nenhum | só registro e resposta; alimenta o relatório da NR 11 art. 54 |

### 3.4 O que muda nas tarefas do plano

- **P1.3 (R2: cadastro completo).** Hoje grava o cadastro direto pela tela (rascunho e `validate-step`). Com a D2, a
  alteração de cadastro existente vira pedido que gera OS `INTERNAL`; a tela monta a proposta e o encerramento aplica.
  O primeiro cadastro de cliente e de UC também nasce de protocolo e OS de "inclusão cadastral". A carga inicial e a
  importação (P1.2) ficam com origem `MIGRATION`. Como a OS só chega na P3.4, a P1.3 precisa de um núcleo mínimo
  antecipado: protocolo, `service_orders` com `execution_mode`, `effect_code` e estado, e os manipuladores cadastrais.
  Critério de aceite novo: tentativa de alterar cliente ou UC fora de OS é recusada (teste).
- **P3.4 (R7 mínimo).** Passa a entregar o protocolo completo da seção 3.1 (prazo pelo motor único, prorrogação,
  motivo obrigatório, referência da ERI, campo de ouvidoria) e os efeitos de corte e religação pela regra 3, com
  revalidação na execução (OS-07) e geração automática da religação após pagamento (`rec.auto_order_on_payment`).
- **ED-36 (OS completa).** Estende o núcleo com `WAITING_DEPENDENCY`, `PENDING_APPROVAL`, `EXECUTED`, lote,
  desdobramento, retrabalho, OS compensatória e os demais `effect_code`; o aceite inclui "nenhuma alteração de
  cliente ou UC sem OS" e "efeito aplicado uma única vez" (reenvio da sincronização não duplica).
- **Reflexos:** P2.20, ED-21 e ED-23 abrem OS `INTERNAL` com alçada; P3.20 e ED-38 abrem OS para alteração pelo
  portal; ED-20 sincroniza execução sem aplicar efeito antes da conferência.

## 4. Modelo de permissões: GSAN comparado ao previsto no Ziip SAN

GSAN [FATO]: módulo → funcionalidade (URL) → operação (pesquisar, inserir, atualizar, remover, outros); o grupo
concede pares funcionalidade × operação; o usuário pertence a grupos; há restrição negativa por usuário,
dependência entre funcionalidades, grupo que administra grupos, cerca de 100 permissões especiais booleanas,
abrangência por nível hierárquico e competência de retificação por grupo. A checagem é por URL, no filtro web; a
abrangência é aplicada caso a caso e não cobre RA nem OS.

Ziip SAN hoje [FATO]: Keycloak com realm único de backoffice (`sigsan`, futuro `ziipsan`) e realm do portal
(`ziipsan-portal`, ADR-0022); papéis do realm viram `ROLE_*` (`SecurityConfig.java`:98-105); tabelas `roles` com
`permissions` JSONB e `user_roles`, com RLS por tenant (`V1_1`:54-78), sem uso nas regras; MFA por papel
(`sec.mfa_roles`, ADR-0007); BI com role de banco por tenant (ADR-0027); ED-06 prevê perfis compostos pelo ADMIN do
tenant, sessões visíveis, trilha por usuário, bloqueio e expiração por tenant e federação LDAP/AD.

| Conceito | GSAN | Ziip SAN previsto | Proposta |
|---|---|---|---|
| Unidade de permissão | funcionalidade × operação, por URL | papel do realm | permissão `recurso:ação` verificada no serviço (`protocol:create`, `so:close`, `connection:cut`), catálogo do produto versionado |
| Agrupamento | grupo | papéis ADMIN, OPERATOR, MANAGER, VIEWER | perfil composto pelo ADMIN do tenant (ED-06); os quatro papéis viram perfis de fábrica |
| Exceção negativa | restrição por usuário | nenhuma | restrição por usuário sobre o perfil, auditada |
| Escopo | abrangência por nível, aplicação manual | só tenant (RLS) | `org_units` e áreas por tenant; escopo aplicado por filtro obrigatório de repositório, com teste; RLS segue só para tenant |
| Alçada | permissão especial sim ou não | ADR-0032 só para material | `approval_rule` genérica: faixa, papel aprovador, item controlado, segregação travada |
| Delegação | grupo administra grupos; abrangência menor ou igual | ADMIN do tenant | ADMIN só concede o que tem; papéis de plataforma fora do tenant (ADR-0018) |
| Onde se decide | filtro web | `hasRole` | Keycloak autentica e entrega identidade, tenant e perfis; permissão fina e escopo resolvidos na aplicação, com cache na sessão do BFF e invalidação ao alterar perfil |
| Usuário técnico | flags de rotina e de internet | contas de serviço do Keycloak | conta de serviço por integração, com escopo de API mínimo |
| Terceirizada | login da firma | ED-37 sem papel | perfil `CONTRACTOR` com escopo por contrato |
| Segregação | não há | ADR-0032 (requisitante não aprova) | regra travada estendida: quem pede não aprova, quem executa não confere a própria OS de terceirizada |

Observação [INFERÊNCIA]: com realm único de backoffice, a política de senha e a detecção de força bruta do Keycloak
são do realm, não do tenant. Atender "bloqueio por tentativas e expiração de senha por tenant" (ED-06) exige
autenticador ou política própria que leia o parâmetro do tenant, ou aplicar ao realm o valor mais restritivo entre
os tenants. A escolha cabe ao desenho da ED-06 e deve ser testada a cada atualização do Keycloak (pergunta 3).

## 5. Parâmetros deste domínio

Classes do `PARAMETROS-DO-TENANT.md` §2: a travado; b padrão de fábrica com aceite; c só ampliável (ou só redutível
onde há teto); d livre. "Existe" indica chave já presente no catálogo; "novo" indica proposta deste documento.
Padrões seguem a D1 (mais protetivo ao usuário) e a D3 (lei federal proibitiva em classe a).

| Chave | Finalidade | Opções | Padrão de fábrica | Cl. | Situação |
|---|---|---|---|---|---|
| `sac.response_days` | prazo de resposta por tipo de manifestação | dias por tipo | sem (ERI) | d | existe |
| `sac.deadline_day_basis` | base de contagem do prazo de protocolo e OS | `CALENDAR`, `BUSINESS` | `CALENDAR` (prazo efetivo menor) | b | novo |
| `sac.holiday_scope` | feriados que suspendem a contagem em dias úteis | `NATIONAL`, `NATIONAL_STATE`, `NATIONAL_STATE_MUNICIPAL` | `NATIONAL` | b | novo |
| `sac.extension_policy` | prorrogação do prazo do protocolo | `NONE`, `ONCE_JUSTIFIED`, `PER_ERI_RULE` | `NONE`; ouvidoria no máximo uma vez, por igual período (Lei 13.460 art. 16) | b | novo |
| `sac.duplicate_handling` | tratamento de pedido repetido | `LINK_AS_REITERATION`, `LINK_AS_DUPLICATE` | `LINK_AS_REITERATION`; recusar não é opção | a (não recusar) e d (forma) | novo |
| `sac.ombudsman_mode` | quem responde a manifestação de ouvidoria | `OWN_OMBUDSMAN`, `TITULAR`, `REGULATOR_CHANNEL` | sem | d | novo |
| `sac.ombudsman_days` | prazo da ouvidoria | dias, uma prorrogação | 30 + 30 | c (só reduz) | novo |
| `sac.satisfaction_survey` | pesquisa de satisfação | `POST_CLOSURE`, `PERIODIC`, ambos | ambos, periódica anual | c (mínimo anual) | novo |
| `sac.protocol_receipt_channels` | canais do comprovante do protocolo | canais com opt-in | todos os canais com contato informado | b | novo |
| `svc.debt_blocks_request` | serviços cuja execução depende de quitação | `NONE`, lista da ERI | `NONE`; o registro do protocolo nunca é bloqueado | b | novo |
| `cut.third_party_debt_blocks` | débito de outro serviço motiva corte | bool | não | d | existe |
| `so.internal_change_auto_apply` | alteração pelo portal ou backoffice aplicada sem análise | `NONE`, `CONTACT_ONLY`, `ALL_LOW_RISK` | `CONTACT_ONLY` (titularidade, documento, categoria e economias sempre com análise) | b | novo |
| `so.retroactive_close_days` | dias máximos de encerramento retroativo | int | 3 | b | novo |
| `so.customer_order_auto_expire` | encerramento por decurso de OS pedida pelo usuário | `NEVER`, dias | `NEVER` (alerta e decisão registrada) | b | novo |
| `cut.notice_validity_days` | validade do aviso; a OS de corte expira com ele | dias | ERI | d | existe |
| `cut.reasons`, `cut.business_hours` | motivos e janela de horário do corte | ver G9 | ver G9 | b | existe |
| `cut.protected_classes` | classes protegidas | lista | saúde, educação, internação coletiva, tarifa social | c | existe |
| vedação de dia no corte por inadimplência | sexta, fim de semana, feriado de qualquer esfera e véspera | fixo | vedado | a | existe como regra travada do G9, sem chave |
| `cut.revalidate_on_execution` | revalidar débito, contestação, aviso e vedações ao executar o corte | fixo | sempre | a | novo |
| `rec.sla_hours` | prazos de religação normal, urgente e indevida | horas | ERI | d | existe |
| `rec.urgent_fee`, `rec.requires_settlement` | taxa de urgência; exigência de quitação | ver G9 | ver G9 | d | existe |
| religação sem taxa sem aviso provado | Lei 13.460 art. 6º p.u. | fixo | sem taxa | a | existe como regra travada do G9, sem chave |
| `rec.auto_order_on_payment` | OS de religação ao quitar ou parcelar o débito que motivou o corte | bool | sim | b | novo |
| `so.material_charge_policy` | cobrança do material ao usuário | `NEVER`, `PRICE_TABLE_ERI` | `NEVER` | b | novo |
| `inv.approval_bands`, `inv.controlled_items`, `inv.auto_approve_below` | alçada de material | ADR-0032 | ADR-0032 | b | existe |
| `sched.priority_weights` | pesos da prioridade da OS | pesos por critério | do produto; urgência legal sempre no topo | d | novo |
| `sec.password_min_length` | tamanho mínimo da senha do backoffice | int | 12 | c (só amplia) | novo |
| `sec.password_max_age_days` | expiração periódica da senha | `OFF`, dias | `OFF`, ligável por exigência de edital | b | novo |
| `sec.password_history` | senhas anteriores não reutilizáveis | int | 5 | c (só amplia) | novo |
| `sec.lockout_attempts` | tentativas antes do bloqueio progressivo | int | 5 | c (só reduz; POC-05 usa 3) | novo |
| `sec.user_inactivity_days` | inativação por não uso | dias | 90 | c (só reduz) | novo |
| `sec.single_session` | sessão única por usuário | bool | não | d | novo |
| `sec.mfa_roles` | papéis com MFA | lista | ADMIN, MANAGER | b | existe |
| `log.audit_retention_months`, `log.access_retention_days` | retenção da trilha e dos logs de acesso | meses, dias | pisos de G6 | c | existe |
| `ret.service_request_years` | retenção de protocolos e OS | anos | piso de G6 | c | existe |
| `cal.holidays` | feriados por esfera | tabela | nacionais pré-carregados | c | existe |

Nota: em `sac.holiday_scope` e na vedação de corte o padrão protetivo vai em sentidos opostos. No prazo de
atendimento, contar menos feriados encurta o prazo; na vedação de corte, considerar todos os feriados amplia a
proteção. Por isso são regras separadas e a do corte é travada (D3).

## 6. Melhores práticas

### 6.1 Manter

- Exigir protocolo aberto para liberar ações comerciais sobre o imóvel (ATD-15), base da D2.
- Efeito comercial só no encerramento com execução, idempotente e com pré-condição de situação (OS-04, OS-05, OS-08).
- OS de referência com retorno deferido ou indeferido, separando diagnóstico de execução (OS-03).
- Bloqueio de trâmite com OS programada e de alteração com imóvel em campo (ATD-17, OS-24).
- Programação sugerida por carga e por equipamento especial, com motivo de não conclusão catalogado (OS-15, OS-18).
- Restrição negativa por usuário e delegação limitada ao que o administrador possui (SEG-01, SEG-04).
- Auditoria com valor anterior e atual por coluna (SEG-13), já superada pelo gatilho do Ziip SAN.
- Aviso ao atendente de interrupção programada na área ao registrar falta d'água (ATD-25).
- Concorrência otimista em protocolo e OS.

### 6.2 Não replicar

- Prazo sem prorrogação registrada; reiteração recusada antes do vencimento; recusa por duplicidade (ATD-05 a ATD-07).
- Feriado municipal desligado no prazo e calendário sem feriado estadual (ATD-04).
- Encerramento silencioso, por decurso, de OS pedida pelo usuário (OS-13).
- Corte sem revalidação de débito e de vedações na execução (OS-07).
- Religação automática por consumo real, sem OS nem fiscalização (OS-10).
- Exceções de autorização por padrão de nome de rota; abrangência aplicada caso a caso (SEG-02, SEG-03).
- Senha provisória em claro por e-mail; bloqueio por sessão HTTP; enumeração de login (SEG-09, SEG-10).
- Remoção física de usuário com perda da trilha; falha de auditoria ignorada (SEG-12, SEG-13).
- Permissão especial inativa liberando para todos; competência pelo máximo de todos os grupos (SEG-05, SEG-06).
- Textos, marcas e regras por empresa no código e identificadores numéricos como regra (OS-28; doc 04 §1.3).
- Histórico alimentado fora da aplicação e fotos gravadas no banco (OS-22, OS-23).

## 7. Lacunas do Ziip SAN por impacto

| # | Lacuna | Impacto | Tarefa afetada | Recomendação |
|---|---|---|---|---|
| 1 | Não há núcleo de protocolo e OS antes da P3.4, mas a D2 exige OS para toda alteração já no cadastro da P1.3 | alterações cadastrais do P1 ficariam fora do fluxo, sem protocolo nem trilha por pedido | P1.3, P3.4, ED-36 | núcleo mínimo no P1 (protocolo, OS `INTERNAL`, efeitos cadastrais, estado); P3.4 estende a campo e corte |
| 2 | Protocolo sem prazo por especificação, motor de dias úteis, prorrogação, motivo de encerramento e relatório da NR 11 art. 54 | verificação da NR 11 pela ANA a partir de 20/05/2027, dentro da janela da P3 | P3.4, P3.22, ED-35 | motor de prazo único (o mesmo do ADR-0020) e prorrogação na P3.4; relatório do art. 54 na P3.22 |
| 3 | Permissão granular, unidade organizacional e escopo inexistentes; ED-06 só na onda A | piloto operaria atendimento, OS e corte com quatro papéis, sem segregação nem escopo por área | P0.13, ED-06, P3.4 | catálogo `recurso:ação` e `org_units` na P3, antes da homologação local; perfis compostos e restrição negativa na ED-06 |
| 4 | Corte sem revalidação na execução, inclusive offline | corte indevido após quitação ou contestação, com indenização prevista por várias ERI | P3.4, ED-20, ED-29 | regra classe a: pacote de campo com estado do débito e vedações; nova verificação no app e no servidor |
| 5 | Política de senha e bloqueio por tenant em realm único | ED-06 e POC-05 não atendidos sem solução própria | ED-06, P3.3 | decidir na ED-06 (pergunta 3); teste por tenant |
| 6 | Ouvidoria sem tarefa | Lei 13.460 incide diretamente na administração indireta, perfil do piloto | ED-35 | tipo "ouvidoria" com 30 + 30 dias e relatório anual |
| 7 | Interrupção programada sem entidade e sem aviso | NR 11 arts. 39-41; indicador de intermitência | ED-56 | nova entrega curta: `interruption_event`, aviso e agrupamento de protocolos |
| 8 | Alçada genérica ausente fora do almoxarifado | desconto, crédito e retificação sem limite por papel | P2.20, ED-21, ED-23 | generalizar `approval_rule` do ADR-0032 |
| 9 | Terceirizada sem perfil e sem conferência antes do efeito | efeito comercial aplicado por terceiro sem aceite | ED-37 | perfil `CONTRACTOR` e estado `EXECUTED` com conferência |
| 10 | Trilha sem vínculo a protocolo e OS | consulta da trilha por atendimento impossível | P1.7, ED-06 | `correlation_id`, `protocol_id` e `service_order_id` no contexto da transação |

## 8. Perguntas ao dono do produto

Restritas a escopo e ordem de entrega; as bifurcações regulatórias estão na seção 5 como parâmetros (D1).

1. Núcleo de protocolo e OS exigido pela D2 no P1:
   (a) antecipar para a P1.3 um núcleo mínimo (protocolo, OS `INTERNAL`, efeitos cadastrais), estendido na P3.4;
   (b) a P1.3 grava pedidos de alteração em tabela própria, convertidos em OS quando a P3.4 chegar;
   (c) manter a P1.3 gravando direto e aplicar a D2 só a partir da P3.4.
2. Núcleo de permissão `recurso:ação` e unidades organizacionais:
   (a) na P3, antes da homologação local, com a ED-06 completando perfis e restrições;
   (b) só na ED-06 (onda A), com o piloto nos quatro papéis;
   (c) catálogo e checagem na P0.13; escopo por unidade na P3.
3. Bloqueio e expiração de senha por tenant no realm único:
   (a) autenticador e política próprios lendo o parâmetro do tenant;
   (b) valor mais restritivo entre os tenants aplicado ao realm inteiro;
   (c) realm separado para o tenant que exigir política própria.
4. Ouvidoria:
   (a) tipo de protocolo dentro da ED-35;
   (b) entrega própria na onda A;
   (c) fora do produto, com integração ao sistema do titular.
5. Interrupção programada e falta d'água:
   (a) nova entrega na onda B, junto da ED-56;
   (b) dentro da ED-35, sem geometria;
   (c) depois do lançamento comercial.
6. Módulo do protocolo:
   (a) `commercial/sigsan-portal`, como propôs o doc 04;
   (b) módulo novo de atendimento, separado do portal do consumidor;
   (c) dentro de `operations/sigsan-field`, junto da OS.

## 9. Limites

- O levantamento cobre os controladores e as entidades citados; as cerca de 35 mil linhas dos dois controladores
  principais não foram lidas por inteiro. Regras em JSP, JavaScript ou procedimentos de banco (como a gravação do
  histórico da OS) podem existir sem aparecer aqui.
- Os números de linha são aproximados e servem para localizar a evidência.
- As referências legais remetem aos levantamentos 08 e 13, que não são parecer jurídico; a Lei 13.460 alcança
  concessionárias privadas só de forma subsidiária, e a NR 11 depende da adoção pela ERI.
- A observação sobre o Keycloak (política de senha e força bruta por realm) é inferência a confirmar na versão em uso.
- Os padrões numéricos propostos (3 dias retroativos, 12 caracteres, 5 tentativas, 5 senhas, 90 dias de
  inatividade) são sugestões técnicas sem norma específica e precisam de aceite ao entrar no catálogo de parâmetros.
- Este documento não altera ADR, plano nem catálogo de parâmetros; as mudanças em P1.3, P3.4 e ED-36 são recomendações
  a registrar pelo processo de ADR e de revisão do plano.
