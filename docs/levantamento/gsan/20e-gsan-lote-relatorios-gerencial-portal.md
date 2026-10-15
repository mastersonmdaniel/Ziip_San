# 20e — GSAN: processamento em lote, tarefas, relatórios, gerencial, portal e API — cruzamento com o Ziip SAN

Data: 14/10/2026. Autor: análise de domínio do projeto Ziip SAN (código interno SIGSAN).

Fonte analisada, somente leitura e em regime clean-room: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN`
(`src/gcom/batch`, `src/gcom/tarefa`, `src/gcom/gerencial`, `src/gcom/relatorio`, `reports/*.jrxml`, `src/gcom/portal`,
`src/gcom/gui/portal`, `gcom/jsp/portal`, `src/gcom/gerador`, `api/`, `src/gcom/integracao`). A licença do GSAN não
está confirmada: este documento registra conceitos em palavras próprias e cita caminhos apenas como evidência. Nenhum
código, SQL, JSP, leiaute de relatório ou texto foi copiado. Cada afirmação leva a marca [FATO] (visto no código ou nos
descritores) ou [INFERÊNCIA] (deduzido de nomes, parâmetros ou estrutura).

Pontos de partida que não se repetem aqui: `03-gsan-arrecadacao-cobranca-batch.md` §5 (modelo do motor de lote,
algoritmo de execução e inventário de tarefas) e §6 (agrupamento de relatórios por prefixo e os 20 mais relevantes);
`04-gsan-operacional-atendimento-seguranca-api.md` §4 (arquitetura gerencial, resumos, BIG), §5 (natureza da pasta
`api/`) e §6 (frontend legado). Este documento aprofunda: grafo e regras de execução do lote, catálogo completo de
relatórios com mapeamento para o Ziip SAN, indicadores com fórmula, portal do cliente final e superfície de integração.

Documentos do Ziip SAN usados no cruzamento: `PLANO-EXECUCAO-PDCA-R0-R9.md` v2.13 (P1.9, P2.1, P2.3, P2.5, P3.3,
P3.20, P3.22, P4.2, P4.3, ED-07, ED-15, ED-38, ED-39, ED-41, ED-45 a ED-49, §2.2 e §3), `ARQUITETURA-ALVO.md`,
`REQUISITOS-NAO-FUNCIONAIS.md` (RNF-05, RNF-07, RNF-11, RNF-25, RNF-26, RNF-71), ADR-0014 (outbox), ADR-0022 (portais),
ADR-0027 (BI), `18-ADERENCIA-EDITAIS-CONSOLIDADO.md` §5 e `08-regulatorio-saneamento-e-tarifas.md` b.3.

## 1. Resumo executivo

1. O lote do GSAN é um motor genérico de processos com passos ordenados e unidades de processamento, acionado por um
   verificador a cada minuto, que executa uma funcionalidade por vez em toda a instância [FATO]. O Ziip SAN (P2.1,
   ADR-0014) já supera esse desenho em concorrência e isolamento, mas ainda não tem catálogo de processos, cronograma
   por grupo, autorização prévia nem limite de lotes por usuário, que o GSAN tem.
2. O faturamento comandado nasce do cronograma mensal do grupo: cada atividade do cronograma aponta para um processo,
   declara a atividade precedente, se é obrigatória, repetível e comandável, e só pode ser comandada quando a
   precedente foi realizada no mesmo grupo e referência; o comando de faturar é recusado enquanto houver rota com
   leitura não retornada [FATO]. Essas pré-condições de negócio não estão previstas na P2.1 nem na P2.5.
3. O GSAN tem 500 arquivos de relatório, dos quais cerca de 150 são subrelatórios ou variantes por empresa; o catálogo
   funcional efetivo tem cerca de 230 relatórios em 16 domínios. A P3.22 cobre cerca de 20 e a ED-46 o restante; 60 a 70
   deles podem ser descartados (listagens de tabelas de apoio, variantes por companhia, campanhas).
4. Os indicadores do GSAN estão em tabelas de resumo mensais por setor comercial e quadra, com dimensões achatadas, e no
   Boletim de Indicadores Gerenciais (BIG). Nenhum indicador tem código SNIS ou SINISA [FATO]; a ED-48 precisará de um
   catálogo próprio, mas as medidas de base do GSAN (hidrometração, medição real, pendência, prazo de RA) são o ponto de
   partida correto.
5. O portal do GSAN (loja virtual) identifica o cliente por matrícula e, opcionalmente, CPF/CNPJ do titular, sem senha
   nem conta própria [FATO]. Faz 2ª via, parcelamento on-line, certidão negativa, declaração anual, solicitação de
   serviço com protocolo, cadastro de e-mail com confirmação e conta em braile. O Ziip SAN (P3.3, P3.20, ED-38) é mais
   seguro no acesso, mas não prevê parcelamento on-line com entrada nem conta em formato acessível.
6. A pasta `api/` é um cliente de um serviço externo de relatórios; não há API pública do GSAN [FATO]. A integração
   com sistemas de campo (UPA), SPC e GIS é por tabela compartilhada, stub SOAP e código de retorno.
7. Lacunas mais graves do Ziip SAN: ausência de catálogo e cronograma de processos de lote com pré-condições de negócio;
   relatório de "motivo de não geração" e de reinício por unidade fora do escopo da P2.1; indicadores sem fórmula
   versionada antes da ED-48; portal sem parcelamento on-line e sem autoleitura antes do P4; nenhuma API para terceiros.
8. Melhores práticas a manter: unidade de processamento com código real e retomada só do pendente; processo que exige
   autorização; relatório assíncrono com expiração; dimensões achatadas no data mart; relatório declarativo por metadados.
9. Práticas a não replicar: tarefa serializada em bytes, uma funcionalidade por vez, relatório em `bytea`, cancelamento
   geral no boot, variantes de relatório por empresa, indicadores sem fórmula registrada.
10. Dez perguntas ao dono do produto fecham o documento, com alternativas; nenhuma trata de equipe ou capacidade.

## 2. Grafo de processos de lote e catálogo de regras de execução

### 2.1 Modelo, aprofundado em relação ao 03 §5.1

- Sete tipos de processo [FATO, `batch/ProcessoTipo.java`]: faturamento comandado, cobrança comandado, mensal,
  eventual, relatório, semanal e diário. O tipo define quem dispara: cronograma de faturamento, cronograma ou comando de
  cobrança, operador (mensal e eventual), usuário final (relatório) e agendador (diário e semanal).
- O catálogo `processo` tem 165 constantes [FATO, `batch/Processo.java`]; 120 delas são relatórios assíncronos, o que
  mostra que o GSAN trata relatório pesado como processo de lote de primeira classe. Cada processo tem indicador de
  autorização, limite, prioridade e nome de arquivo de lote [FATO, `Processo.hbm.xml`]; o uso efetivo de prioridade e
  limite não aparece no controlador [INFERÊNCIA: campos herdados de uma versão anterior do agendador].
- Vinte e dois tipos de unidade de processamento [FATO, `UnidadeProcessamento.java`]: rota, conta, atividade de
  cronograma por rota, relatório, funcionalidade, localidade, grupo de cobrança no mês, setor comercial, marca de
  hidrômetro, atividade de cobrança por cronograma e por comando, encerramento de RA por comando, registro de movimento
  do negativador, comando de empresa de cobrança (conta e extensão), unidade de negócio, hidrômetro, empresa, quadra,
  grupo de faturamento e referência. A unidade iniciada guarda o código real da unidade (id da rota, da localidade
  etc.), e é essa chave que permite retomar só o pendente [FATO].
- O processo iniciado guarda o código do grupo do processo (grupo de faturamento ou grupo de cobrança), o usuário, as
  datas de agendamento, comando, início e término e um processo iniciado precedente [FATO, `ProcessoIniciado.hbm.xml`].
  O precedente é um encadeamento opcional: só é preenchido quando um processo é disparado por outro [INFERÊNCIA; o
  controlador não o usa como pré-condição].
- A tarefa de cada passo é um objeto Java com parâmetros tipados (22 tipos escalares e de vetor, com separador próprio
  para vetores) [FATO, `tarefa/TipoParametroTarefa.java`], serializado em bytes na funcionalidade iniciada [FATO].
  Relatórios usam a mesma base com formato de saída PDF, RTF, XLS ou HTML e indicador de exibir mensagem [FATO,
  `tarefa/TarefaRelatorio.java`].

### 2.2 Como o cronograma vira processo

[FATO, `faturamento/FaturamentoAtividade.hbm.xml`] O catálogo de atividades de faturamento declara, por atividade:
ordem de realização, atividade precedente, se é obrigatória, se pode repetir, se pode ser comandada e o processo de
lote correspondente. [FATO, `ControladorBatchSEJB.inserirProcessoIniciadoFaturamentoComandado` e
`ControladorFaturamentoFINAL`, seleção de atividades] O cronograma mensal do grupo tem uma linha por atividade, com
data prevista, data de realização e data de comando. A tela de comando só oferece a atividade que não tem precedente
ou cuja precedente já tem data de realização no cronograma do mesmo grupo e referência. Quando o operador comanda:

1. o sistema busca as rotas do cronograma daquela atividade; sem rota, o comando é recusado;
2. se o processo é "faturar grupo", verifica se há rotas do grupo e da referência com leitura ainda não transmitida; se
   houver, recusa e lista as rotas (trava de pré-condição de negócio, não de infraestrutura);
3. cria o processo iniciado com o código do grupo, a data do comando vinda do cronograma e a situação inicial "em
   espera" ou "aguardando autorização", conforme o indicador do processo;
4. cria uma funcionalidade iniciada por passo ativo do processo, cada uma com a tarefa parametrizada com referência do
   grupo, id do grupo, parâmetros do sistema e a coleção de rotas como unidades de processamento.

A cobrança comandada segue o mesmo padrão a partir do cronograma de ação de cobrança (grupo de cobrança no mês) ou do
comando eventual (critério de seleção de imóveis) [FATO, `inserirProcessoIniciadoCobrancaComandado`]. Os processos
mensais e eventuais entram por "parâmetros livres" (mapa de parâmetros definido pela tela) [FATO,
`inserirProcessoIniciadoParametrosLivres`].

### 2.3 Grafo de execução

Legenda: **G** = por grupo de faturamento e referência; **R** = por referência da empresa (mês de faturamento ou de
arrecadação); **D** = diário; **E** = eventual ou comandado; "→" = pré-condição observada no código; "⇢" = ordem
inferida do fluxo de dados.

```
TRILHO 1 — FATURAMENTO DO GRUPO (G; cronograma mensal do grupo)
  gerar dados para leitura ⇢ gerar arquivo texto para leiturista (ou rota on-line)
    → [retorno de todas as rotas]  (trava: rota não transmitida bloqueia o comando de faturar)
  ⇢ consistir leituras e calcular consumos ⇢ rateio de consumo (medição individualizada)
  ⇢ bônus de tarifa social ⇢ FATURAR GRUPO ⇢ débitos a cobrar de acréscimos por impontualidade
  ⇢ taxa de entrega em outro endereço ⇢ emitir contas (padrão, órgão público, braille, extrato de condomínio,
    fatura de cliente responsável, boletos, e-mail da conta) ⇢ histórico de conta
  ⇢ atualizar data de realização da atividade no cronograma

TRILHO 2 — ENCERRAMENTO MENSAL DE FATURAMENTO (R; só após o Trilho 1 de todos os grupos da referência)
  gerar histórico para encerrar faturamento ⇢ ENCERRAR FATURAMENTO DO MÊS (avança a referência da empresa)
  ⇢ resumos: faturamento, faturamento água e esgoto, refaturamento, situação especial, simulação, receitas a faturar,
    indicadores de faturamento, ligações e economias, consumo de água, histograma, metas, pendência, parcelamento
  ⇢ contas a receber contábil ⇢ lançamentos contábeis de faturamento
  ⇢ devedores duvidosos: apagar resumo → gerar resumo → atualizar → lançamentos contábeis

TRILHO 3 — ARRECADAÇÃO DIÁRIA (D)
  registrar movimento dos arrecadadores (on-line, por arquivo) ⇢ classificar pagamentos e devoluções
  ⇢ dados diários de arrecadação ⇢ diferença de dois reais ⇢ cancelar guias não pagas
  ⇢ atualizar diferença acumulada no mês ⇢ dados de pagamentos não classificados e documentos não identificados
  ⇢ ligação "ligado em análise" → "ligado" (pagamento da religação confirmado)

TRILHO 4 — ENCERRAMENTO MENSAL DE ARRECADAÇÃO (R)
  resumo de arrecadação → gerar histórico para encerrar arrecadação (exige o resumo do mês)
  ⇢ lançamentos contábeis de arrecadação e de avisos bancários ⇢ ENCERRAR ARRECADAÇÃO DO MÊS
  ⇢ resumo de indicadores de cobrança ⇢ dados do BIG ⇢ resumo de receita ⇢ documentos a receber

TRILHO 5 — COBRANÇA (E; cronograma do grupo de cobrança ou comando eventual)
  gerar atividade de ação de cobrança (seleciona imóveis e registra motivo de não geração)
  ⇢ emitir documentos de cobrança (aviso, notificação, ordem de corte, fiscalização, visita)
  ⇢ atualizar comando/atividade ⇢ [execução em campo] ⇢ processar encerramento de OS da ação
  ⇢ resumos de ações (cronograma, eventual, encerramento de OS) ⇢ encerrar comando
  Sub-trilhos: cobrança por resultado (gerar movimento por empresa → extensão → encerrar comando → relatório de
  pagamentos); negativação (executar comando → movimento de envio → retorno → confirmar → excluir → resumo diário);
  parcelamento (desfazer por entrada não paga; incluir débito da entrada não paga; cancelar parcelamentos);
  prescrição de débitos de imóveis públicos (automática e manual).

TRILHO 6 — CADASTRO E MICROMEDIÇÃO (E ou mensal)
  atualização cadastral: tabelas temporárias → arquivo texto → diferença de retorno
  hidrômetros: movimento → atualizar conjunto → resumo de hidrômetros e de instalação
  tarifa social: carta (simular → gerar) → retirar/excluir imóveis → resumo de excluídos
  RA e OS: resumo de RA (mês e ano) → programação automática de roteiro → OS de inspeção de anormalidade
```

O motor de lote só garante a ordem dos passos dentro de um processo [FATO,
`pesquisarQuantidadeFuncionaldadesIniciadasForaOrdemExecucao`]. A ordem entre processos é declarada apenas no Trilho 1,
pela atividade precedente do catálogo de atividades de faturamento, verificada contra a data de realização no
cronograma do grupo [FATO, §2.2]. Nos demais trilhos a ordem é garantida por três mecanismos indiretos [FATO]:
pré-condições de dados verificadas pela própria tarefa (exemplo: o histórico para encerrar arrecadação exige o resumo
do mês), a referência corrente da empresa (contas e pagamentos só são gerados na referência aberta) e a disciplina do
operador. O dado de qual processo contém quais passos está na tabela `processo_funcionalidade`, e o encadeamento das
atividades está na tabela de atividades de faturamento; a carga de ambas não está no repositório [FATO].

### 2.4 Catálogo de regras de execução (LOT)

| ID | Regra no GSAN | Marca | Situação no Ziip SAN | Tarefa afetada |
|---|---|---|---|---|
| LOT-01 | Processo = sequência ordenada de passos; o passo N só entra quando nenhum passo anterior do mesmo processo está pendente | FATO | Coberto pela estrutura de `Job`/`Step` da P2.1 | P2.1 |
| LOT-02 | Uma funcionalidade por vez em toda a instância; o verificador roda a cada minuto e dispara só a primeira pronta | FATO | Substituído por fila justa e concorrência por tenant (RNF-25); não replicar | P2.1 |
| LOT-03 | Fan-out da tarefa em unidades de processamento; cada unidade em transação própria; unidade concluída é pulada na retomada; funcionalidade "concluída com erro" recusa unidades restantes | FATO | Coberto em parte: a P2.1 prevê "reinício continua só rotas pendentes"; falta a recusa explícita das unidades restantes quando o lote já falhou e o registro por unidade com código real para outros tipos além de rota | P2.1, P2.3 |
| LOT-04 | Dois modos de recuperação: reiniciar (apaga unidades e recomeça) e continuar (só pendentes); continuar é vedado para relatório | FATO | Só "continuar" previsto; falta "reiniciar do zero" com motivo registrado | P2.1 |
| LOT-05 | Queda do servidor cancela todo processo em execução no boot; o operador decide reiniciar ou continuar | FATO | Posse com prazo e devolução à fila (ADR-0014) é superior; manter a decisão do operador para lotes de negócio (não reexecutar faturamento automaticamente) | P2.1, ED-07 |
| LOT-06 | Processo pode exigir autorização: nasce "aguardando autorização" e um segundo usuário libera | FATO | Ausente; a P2.1 só prevê trava (tenant, ciclo). A aprovação dupla existe na P3.1 para conciliação, não para lote | P2.1, P2.5 |
| LOT-07 | Limite de lotes simultâneos por usuário (atributo do usuário); excedido, o processo fica em espera | FATO | Ausente; a fila justa é por tenant, não por usuário | P2.1 |
| LOT-08 | Comando de faturamento recusado se há rota com leitura não transmitida; recusado sem rota no cronograma | FATO | Ausente na P2.5; a ARQUITETURA-ALVO §2 abre o ciclo sem pré-condição de leitura | P2.5, P2.7 |
| LOT-09 | Cronograma mensal por grupo com atividade, data prevista, data de realização e data de comando; o comando grava a data e o processo atualiza a realização; a atividade só é comandável quando a precedente declarada está realizada, e o catálogo marca obrigatória, repetível e comandável | FATO | Ausente; o Ziip SAN tem `cycle_id` e trava, mas não cronograma com atividades, precedência e datas previstas | P2.5, P2.11 |
| LOT-10 | Código do grupo no processo iniciado permite listar "o que rodou para o grupo X na referência Y" | FATO | `job_runs` deve guardar `tenant_id`, `cycle_id`, `billing_group_id`, `reference_month` como colunas consultáveis, não só em parâmetros | P2.1 |
| LOT-11 | Referência corrente da empresa avança no encerramento; nada é gerado em referência fechada | FATO | Previsto como "fechamento mensal congelado" na ED-26 e ED-32; não há tabela de período fechado por domínio no P2 | P2.5, P3.1, ED-26 |
| LOT-12 | Relatório pesado é processo de lote: fila própria, resultado guardado, expiração e exclusão automática, notificação ao usuário | FATO | P3.22 prevê agendamento pelo `worker`; falta expiração do arquivo gerado e fila separada da do faturamento | P3.22 |
| LOT-13 | Catálogo de processos em tabela, com descrição, tipo, indicador de uso, autorização e nome de arquivo | FATO | Ausente; a P2.1 define jobs em código. Recomenda-se `job_definitions` só para ligar/desligar, agendar e marcar autorização por tenant | P2.1 |
| LOT-14 | Motivo de não geração registrado por imóvel na seleção da cobrança (27 motivos) | FATO (03 §3.3) | Ausente no lote; a ED-26 e a P3.4 não preveem relatório de rejeição por unidade | P3.4, P3.22 |
| LOT-15 | Verificador de consultas demoradas e de processos do sistema no mesmo agendador | FATO (`verificadorQueriesDemoradasSistema`) | Coberto pela observabilidade (RNF-51) e Central (P1.17) | P1.17 |
| LOT-16 | Integração com sistema de campo (UPA) verificada de hora em hora por tabela compartilhada | FATO | Substituído pela sincronização do app de campo (P4.4, RNF-07) e pela ED-17 | P4.4 |

Cruzamento com a ADR-0014: a outbox cobre entrega garantida, retomada e isolamento por tenant, mas é um transporte;
LOT-06, LOT-07, LOT-08, LOT-09, LOT-11, LOT-13 e LOT-14 são regras de negócio do lote que ficam acima do transporte e
hoje não têm dono no plano. A P2.1 é a candidata natural para LOT-01 a LOT-07, LOT-10 e LOT-13; LOT-08, LOT-09 e LOT-11
pertencem à P2.5; LOT-12 à P3.22; LOT-14 à P3.4.

## 3. Catálogo completo de relatórios por domínio

Fonte: nomes dos 500 arquivos em `reports/`, parâmetros e grupos extraídos por script (sem leitura de leiaute), e as
classes `Relatorio*` de `src/gcom/relatorio` (arrecadação 81, atendimento 87, cadastro 31, cobrança 97, faturamento 93,
financeiro 20, micromedição 48, operacional 10, segurança 27, transação 6, BIG 4) [FATO]. Subrelatórios (`*Detail`,
`*Sub*`, `*1`/`*2`) e variantes por companhia (CAEMA, CAER, CAERN, COSANPA, JUAZEIRO) estão compactados na linha do
relatório principal. Finalidade é [INFERÊNCIA] a partir de nome, parâmetros e campos, salvo onde indicado.

Legenda de correspondência: **P3.22** = entre os cerca de 20 da tarefa; **ED-45** = núcleo declarativo (o relatório é
gerável pelo núcleo sem fonte nova); **ED-46** = catálogo completo; **doc:Px** = documento operacional emitido por outra
tarefa (não é relatório); **AUSENTE** = sem correspondente. Prioridade: **N** = núcleo da P3.22; **A**, **B**, **C** =
ondas da trilha de editais; **D** = descartar.

### 3.1 Cadastro: listagens de tabelas de apoio ("Manter")

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Listagem de localidades, setores comerciais, quadras, rotas, bairros, logradouros, municípios, gerências regionais, unidades de negócio e unidades organizacionais (12 relatórios) | Imprimir o resultado da tela de manutenção de cada tabela de divisão territorial e organizacional | Os da tela de pesquisa correspondente | Nenhum ou por localidade | ED-45 (exportação da listagem filtrada) | D |
| Listagem de categorias, subcategorias, perfis de imóvel, ramos de atividade, programas especiais, tipos de cartão de tarifa social, dados de tarifa social (8) | Idem, para a classificação do imóvel | Idem | — | ED-45 | D |
| Listagem de hidrômetros, diâmetros, locais de armazenagem, retorno de controle de hidrômetro, leituristas, anormalidades de consumo e ações por anormalidade (7) | Idem, para micromedição | Idem | — | ED-45 | D |
| Listagem de arrecadadores, contratos de arrecadador, formas de arrecadação, agências bancárias, avisos bancários com acertos e deduções, movimentos de arrecadadores e itens, guias de devolução, débito automático, situações de pagamento (10) | Idem, para arrecadação | Idem | Totais por aviso | ED-45 | D |
| Listagem de grupos e cronogramas de faturamento, situações de faturamento, consumo-tarifa, mensagens de conta, tipos de débito, resoluções de diretoria (7) | Idem, para faturamento | Idem | — | ED-45 | D |
| Listagem de grupos e cronogramas de cobrança, critérios de cobrança, situações de cobrança, motivos de corte, perfis de parcelamento, contratos de parcelamento (por cliente e por RD), negativadores com contratos, motivos e tipos de registro (11) | Idem, para cobrança | Idem | — | ED-45 | D |
| Listagem de clientes (padrão e outros critérios), imóveis (padrão e outros critérios), dados de economias do imóvel (5) | Resultado da pesquisa de cliente ou imóvel para impressão ou planilha | Critérios da tela: localidade, setor, quadra, situação, categoria, perfil, cliente | Localidade | ED-45 | A |
| Listagem de empresas, atividades, cargos, tipos e itens de serviço, tipos de perfil de serviço, projetos, custo de pavimento (8) | Idem, para operacional e serviços | Idem | — | ED-45 | D |
| Listagem de bacias, sistemas e fontes de abastecimento, zonas de pressão, divisões de esgoto, produção de água, perfis de ligação de esgoto, tipos de esgotamento, situações de ligação de água (9) | Idem, para cadastro técnico | Idem | — | ED-45; GIS (P2.21 a P3.27) | D |

### 3.2 Imóvel, cliente e recadastramento

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Dados cadastrais do imóvel (com clientes e economias) | Ficha completa do imóvel para atendimento | Matrícula | Seções | ED-38 (ficha no portal) e tela do backoffice | A |
| Dados complementares do imóvel (anormalidades, cobrança, débitos, faturamento, ocorrências, ramos, situação de cobrança, vencimentos) | Ficha estendida com histórico por assunto, com foto | Matrícula | Por assunto | Tela de consulta do imóvel (P1.x) | B |
| Clientes relacionados ao imóvel; imóveis relacionados ao cliente | Vínculos cliente-imóvel com economias | Matrícula ou código do cliente | Cliente, economia | ED-45 | A |
| Imóvel por endereço; logradouros por município; relação de imóveis da rota | Localizar imóveis por endereço e listar a rota | Logradouro, município, rota | Rota, quadra | ED-45; ED-15 (roteiro) | A |
| Cadastro de consumidores por inscrição; imóveis por situação no período; imóveis por situação da ligação de água | Quantitativos e relação de ligações por situação (ativa, cortada, suprimida, factível) | Período, unidade de negócio, setor | Unidade de negócio, setor | P3.22 (ligações e economias por situação e categoria) | N |
| Imóveis por tipo de consumo; consumo médio; últimos consumos de água; ativos não medidos | Análise de perfil de consumo e de ligações sem hidrômetro | Localidade, setor, quadra, rota, faixa | Localidade, setor | ED-46; ED-48 (hidrometração) | A |
| Clientes especiais; análise de imóvel corporativo grande | Carteira de grandes consumidores e órgãos | Referência, unidade, perfil | Perfil | ED-46; ED-22 (órgão centralizador) | B |
| Imóveis em programas especiais (analítico e sintético); imóveis com doações (por entidade e por imóvel); pagamentos a entidades beneficentes | Tarifa social e doações na conta | Referência, programa, entidade | Entidade, localidade | P3.22 (tarifa social e isenções); doação: AUSENTE | N / C |
| Resumo de imóveis excluídos da tarifa social (dois formatos) | Resultado do lote de retirada da tarifa social | Referência, motivo | Motivo | P3.22 (tarifa social) | N |
| Boletim de cadastro (geral e individual); boletim de custo de atualização cadastral | Ficha de campo para recadastramento e custo por imóvel atualizado | Rota, período, empresa | Rota | ED-55 / atualização cadastral (04 §7) | B |
| Atualização cadastral via internet (relatório e resumo); consulta de atualização cadastral com detalhe; inconsistências do retorno; alterações de CPF/CNPJ por localidade, meio e usuário | Auditoria do recadastramento (quantos nomes, documentos e e-mails mudaram, por quem) | Período, localidade, unidade, meio, usuário | Localidade, meio, usuário | AUSENTE (auditoria de dado pessoal alterado; ADR-0008) | A |
| Ficha de fiscalização cadastral | Documento de campo para fiscalizar o cadastro | OS | — | doc: ED-36 | B |
| Demonstrativo sintético de ligações; resumo de ligações por capacidade de hidrômetro; resumo de dados CAS; resumo por distrito operacional; resumo por zona de abastecimento; resumo do imóvel para micromedição | Quadros gerenciais de ligações e economias por corte territorial e técnico | Referência, totalização | Opção de totalização | ED-49 (painel de medição); GIS | B |
| Contrato de prestação de serviço (padrão e jurídico) | Documento de adesão do usuário | Matrícula | — | doc: AUSENTE (NR 11 contrato de adesão) | A |
| Relação de impostos por cliente responsável | Retenções tributárias por órgão pagador | Referência, cliente | Cliente | ED-32 (contábil); P2.5 (retenção) | B |

### 3.3 Faturamento e conta

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Conta (modelos 1, 2 e tipo 2, com consumos anteriores); fatura de cliente responsável; faturas agrupadas (padrão, sintético, Cosanpa); protocolo de entrega de fatura | Documentos de cobrança mensal e seus agrupamentos | Grupo, referência, cliente | Cliente responsável | doc: P2.5, P2.8, ED-22 | — |
| Segunda via de conta (padrão, tipo 2, com boleto, variantes CAERN) | Reimpressão com código de barras ou ficha de compensação | Matrícula, conta | — | doc: P2.20, P3.3 | — |
| Relação analítica e sintética de faturas; contas emitidas | Conferência do que foi emitido no grupo | Grupo, referência, vencimento | Localidade | P3.22 (contas emitidas) | N |
| Contas canceladas e retificadas (analítico e sintético) | Conferência das alterações pós-emissão, por motivo | Grupo, referência, ano | Motivo, localidade | P3.22 (canceladas e retificadas); P2.20 | N |
| Contas retidas; contas em revisão (analítico e resumido); mapa de controle de conta | Contas que não saíram para impressão e contas em análise | Grupo, referência | Localidade | P3.22 (leituras retidas) — ampliar para contas retidas e em revisão | N |
| Erros do movimento de contas pré-faturadas | Rejeições na importação de contas emitidas em campo | Arquivo, rota | Rota | ED-19 (emissão simultânea) | A |
| Posição do faturamento; acompanhamento do faturamento; análise do faturamento (sub por quebra) | Andamento do ciclo por grupo e análise por localidade, perfil, categoria, situação, esfera de poder | Referência, grupo, quebra, totalização | Grupo; quebra escolhida | P3.22 (fechamento do ciclo por grupo; faturamento por categoria e faixa) | N |
| Analítico do faturamento; volumes faturados (analítico e resumido); volumes consumidos não faturados | Volumes e valores faturados por localidade, setor e quadra; água não faturada | Referência, grupo, vencimento | Localidade, setor, quadra | P3.22 (faturamento detalhado); ED-48 (perdas de faturamento) | N |
| Faturamento de ligações com medição individualizada | Condomínios com rateio | Referência | Condomínio | ED-15 (regra de condomínio) | A |
| Resumo do faturamento; resumo por situação especial; comparativo faturamento × arrecadação com expurgo; comparativo de resumos (faturamento, arrecadação, pendência) | Quadros mensais por item de lançamento e corte territorial | Referência, gerência, localidade | Município, localidade, gerência, unidade de negócio, tipo de lançamento, item | ED-46; ED-49 (painel de faturamento) | A |
| Histórico de faturamento do imóvel (contas, créditos, débitos, guias) | Extrato histórico do imóvel | Matrícula, período | Tipo de documento | P3.20 (120 meses no portal) e backoffice | A |
| Acréscimos por impontualidade; índices de acréscimos | Memória de cálculo de multa, juros e atualização | Período | — | ED-45 (parâmetros do tenant com vigência, ADR-0015) | A |
| Receitas a faturar (analítico, sintético, por categoria) | Provisão de receita de consumo ocorrido e não faturado | Referência | Categoria | ED-32 | B |
| Análise de consumo; analisar metas do ciclo | Indicadores do ciclo contra meta | Referência, grupo, indicador | Localidade | ED-48 | B |
| Estrutura tarifária (loja virtual) | Tabela tarifária vigente para o cliente | Vigência | Categoria, faixa | ED-38 (tabela tarifária no portal) | A |

### 3.4 Débitos, extratos e guias

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Consultar débitos (do imóvel; do cliente; por endereço; analítico; resumido) | Posição de débito com acréscimos na data | Matrícula ou cliente, período de referência e de vencimento, tipo de relação | Imóvel, tipo de documento | Tela de débitos (P3.1) e portal (P3.3) | A |
| Extrato de débito (imóvel e cliente, com boleto) e "débito" (documento de cobrança com totais) | Documento para quitação de vários débitos de uma vez | Matrícula ou cliente | Contas, serviços | doc: P3.1, P3.3 | — |
| Guia de pagamento (padrão, ficha de compensação, parcelamento); guia em atraso | Documento avulso de cobrança de serviço ou entrada | Guia | — | doc: P3.1 | — |
| Relação de débitos (contas, guias, tipo de débito/crédito, totais por imóvel, resumido, sintético — 12 arquivos) | Carteira de débitos por localidade com situação da ligação e consumo médio | Localidade, setor, quadra, rota, faixa de valor, situação | Localidade, setor, imóvel | P3.22 (valores a receber por tipo de unidade) | N |
| Curva ABC de débitos | Concentração da inadimplência por faixa e categoria | Referência, categoria | Faixa de valor | ED-46; ED-49 (painel de cobrança) | A |
| Valor do débito na consulta do imóvel | Resumo de débito para a tela do imóvel | Matrícula | — | Tela | — |
| Declaração de transferência de débito/crédito; consultar transferências; transferência de pagamento | Movimentação de valores entre imóveis | Imóvel origem e destino, período, usuário | — | P2.20 (crédito avulso); AUSENTE (transferência entre imóveis) | B |
| Juros e multas de débitos cancelados (com totalizadores) | Encargos abandonados por cancelamento | Período | Motivo | ED-32 | B |

### 3.5 Arrecadação

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Dados diários de arrecadação (por arrecadador, forma, categoria, gerência, localidade, unidade de negócio, elo, perfil, documento agregador, valores diários, parâmetros, agente — 13 arquivos) | Arrecadação do dia e do mês por recorte, com quantidade, valor, float e tarifa do arrecadador | Período, arrecadador, forma, gerência, localidade | O recorte do título | P3.22 (arrecadação diária, POC-58) | N |
| Resumo da arrecadação | Arrecadado por tipo de recebimento, lançamento e item contábil | Referência | Município, localidade, gerência, unidade de negócio, tipo de recebimento, tipo de lançamento, item | ED-46; ED-49 | A |
| Análise da arrecadação; análise de avisos bancários; aviso bancário por conta corrente | Conciliação: calculado × informado pelo banco, devoluções, acertos, diferença acumulada | Referência, estado, arrecadador, forma | Arrecadador, forma | P3.22 (situação da importação do retorno) e P3.1 (conciliação) | N |
| Pagamentos (geral; por aviso bancário; por imóvel); devoluções; guia de devolução; devolução de pagamentos em duplicidade | Listagens de pagamentos e devoluções com situação | Período, arrecadador, gerência, categoria, perfil | Arrecadador | P3.22 (pagamentos por data de remessa, pagamento, crédito e banco; em duplicidade, POC-59 e POC-60) | N |
| Documentos não aceitos | Registros rejeitados do arquivo bancário | Referência, arrecadador, período | Arrecadador | P3.22 (pagamentos não identificados) | N |
| Acompanhamento do movimento dos arrecadadores (geral e por NSA); registrar movimento dos arrecadadores | Arquivos recebidos por banco e dia, valor do dia e acumulado | Referência, forma, arrecadador | Arrecadador, NSA | P3.22 (situação da importação do retorno) | N |
| Movimento de débito automático do banco | Remessa e retorno de débito automático | Referência, banco | Banco | ED-26 | B |
| Análise de pagamento com cartão de débito | Pagamentos por cartão nas lojas | Período | — | ED-26 (VAN) | C |
| Análise de perdas de créditos | Aging de recebíveis em faixas (a vencer, até 6 e até 12 meses, acima) | Referência | Faixa | ED-32; PDD (03 §4.3) | B |

### 3.6 Cobrança, corte e negativação

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Acompanhamento das ações de cobrança | Quantidade e valor por ação × situação | Período, ação | Estado, gerência, unidade, localidade | ED-46; ED-49 (painel de cobrança) | A |
| Comandos de ação de cobrança (cronograma e eventual); comando de documento de cobrança | O que cada comando selecionou e emitiu | Comando, combinação | Comando | P3.22 (aptos ao corte e notificações emitidas) | N |
| Motivo de não geração de documento de cobrança (analítico e sintético) | Por que cada imóvel elegível não recebeu documento (27 motivos) | Comando | Motivo, localidade | AUSENTE (LOT-14) | A |
| Filtrar documento de cobrança; protocolo de documento de cobrança | Localizar documentos emitidos e comprovar entrega | Período, tipo, imóvel | Tipo | P3.5 (prova de entrega) | A |
| Notificação de débito (padrão, impressão simultânea, 3 variantes Cosanpa); reaviso de débito | Carta de cobrança com código de barras | Comando | — | doc: P3.4, P3.5 | — |
| Ordem de corte (padrão, on-line, documento de cobrança); ordem de fiscalização (padrão, com faturas, on-line); documento de visita de cobrança (3 formatos) | Documentos de campo das ações | Comando, OS | — | doc: P3.4, ED-36 | — |
| Religação de clientes inadimplentes (5 modelos) | Ordem de religação após pagamento | Período | — | doc: P3.4 | — |
| Imóveis com faturas em atraso (descritas e agrupadas, por cliente e por localização — 8 arquivos); faturas recentes em dia e antigas em atraso; imóveis com acordo | Carteira em atraso por faixa de dias e por cliente | Localidade, setor, faixa, cliente, perfil | Cliente ou localização | P3.22 (valores a receber) e ED-46 | N / A |
| Supressões, religações e restabelecimentos | Movimento de cortes e religações no período | Período, localidade | Localidade | ED-49 (painel operacional); ED-48 | A |
| Rotas on-line por empresa; pagamentos de contas em cobrança por empresa (analítico e sintético); OS de acompanhamento da cobrança por resultado; boletim de medição de cobrança | Cobrança terceirizada: carteira entregue, pagamentos e remuneração com penalidades | Empresa, contrato, referência, grupo | Empresa | AUSENTE (cobrança por resultado; 03 §3.8) | C |
| Resumo das ações de cobrança eventuais; resumo de cobrança em situação especial | Quadros de resultado das ações | Referência, situação | Gerência, unidade, localidade | ED-46 | B |
| Acompanhamento de clientes negativados (analítico, sintético, parâmetros, dados do parcelamento — 5 arquivos); negativações excluídas; resultado da simulação do negativador | Negativação: enviados, pagos, parcelados, excluídos, rejeições | Negativador, período, comando, elo, localidade, setor, quadra, grupo, unidade, perfil, categoria, tipo de cliente, esfera, gerência | Negativador, comando | AUSENTE (negativação; 03 §3.7) | C |
| Acesso ao SPC; resultado da consulta de cadastro CDL | Auditoria das consultas ao bureau de crédito | Período, unidade, usuário | Usuário | AUSENTE (consulta a bureau; LGPD) | C |

### 3.7 Parcelamento

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Termo de parcelamento (9 variantes por empresa) com detalhamento de débitos; contrato de parcelamento por cliente (com 4 subrelatórios); extrato do contrato (3 formatos); comprovante de pagamento do contrato; documentos de parcelamento da loja virtual | Documentos do parcelamento para assinatura e pagamento | Parcelamento, cliente | Contas, débitos a cobrar, parcelas | doc: ED-38 (simulação e contrato no portal); P3.4 | — |
| Relação de parcelamentos (sintético, analítico, cartão de crédito) | Carteira de parcelamentos por período, situação e perfil | Período, situação, perfil | Gerência, localidade, município | ED-46 | A |
| Análise de parcelamento (com detalhamento de débitos) | Simulação de opções antes de contratar | Imóvel ou cliente | — | ED-38 (simulação) | A |

### 3.8 Financeiro e contábil

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Resumo de devedores duvidosos | Provisão por faixa de vencimento e categoria | Referência | Gerência, unidade, categoria | ED-32 | B |
| Saldo e evolução de contas a receber contábil | Saldo contábil mensal e variação | Referência, período | Gerência, unidade | ED-32 | B |
| Parâmetros contábeis (arrecadação, contas a receber, faturamento) | Plano de contas de-para por evento | — | Evento | ED-32 | B |
| Contas baixadas contabilmente | Baixas por prescrição ou decisão | Período, periodicidade, tipo | Motivo | ED-32 | B |
| Resumo de receita (analítico e sintético) | Receita por banco e conta bancária | Referência | Banco, conta bancária | ED-32 | B |
| Documentos a receber | Aging por categoria, gerência, unidade, localidade e faixa | Referência | Quebra, situação, vencidos | P3.22 (valores a receber por tipo de unidade, POC-57) | N |

### 3.9 Certidões e declarações

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Certidão negativa de débitos do imóvel e do cliente (padrão e CAEMA, com itens) | Comprovante de ausência de débito com validade | Matrícula ou cliente, validade | Itens | ED-38 (certidão no portal) | A |
| Declaração anual de quitação de débitos (padrão, CAEMA, CAERN) | Obrigação legal anual, por lote de grupo ou sob demanda | Ano, grupo, matrícula | Localidade, setor, rota | ED-38 (declaração anual) | A |

### 3.10 Micromedição, leitura e hidrômetros

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Dados para leitura (paisagem e retrato); roteiro de programação | Boletim impresso de leitura e roteiro da rota | Grupo, rota | Rota | ED-15 (boletim de contingência) | A |
| Leituras realizadas; leituras de telemetria; registrar leituras e anormalidades; atualizar leituras e anormalidades pelo celular; monitorar leitura mobile; consultar arquivos texto de leitura | Acompanhamento da coleta por rota e por origem (manual, celular, telemetria) | Grupo, empresa, localidade, rota, período | Localidade, rota | P3.22 (leituras retidas e ocorrências); ED-17 (telemetria) | N |
| Acompanhamento do leiturista (com anormalidades e horários); comparativo de leituras e anormalidades | Produtividade e qualidade do leiturista, horários de leitura | Referência, gerência, unidade, localidade, setor, leiturista, rota | Leiturista, rota | ED-46; ED-49 (painel de medição) | A |
| Anormalidade de consumo; anormalidade de consumo e leitura (geral e por amostragem); anormalidade de leitura por período; aviso de anormalidade | Ocorrências de leitura e consumo para análise e para comunicação ao cliente | Referência, período, anormalidade | Unidade de negócio, localidade | P3.22 (ocorrências de leitura) | N |
| Resumo de anormalidades de leitura (por anormalidade, localidade, quadra, setor); resumo de leituras e anormalidades (problemas e registradas) | Quadros mensais de anormalidade por corte territorial | Referência | Anormalidade, localidade, setor, quadra | ED-46 | A |
| Faixas falsas de leitura; releitura de imóveis; imóveis com leituras (quantitativos, relação, tipo 7) | Controle de qualidade da leitura e releituras | Referência, rota | Rota | ED-46 | B |
| Medição de consumo da ligação de água; análise de medição e consumo do imóvel; histórico de medição de poço | Histórico de leitura e consumo do imóvel | Matrícula, período | — | P3.20 (histórico de consumo no portal); ED-38 | A |
| Histograma de consumo (água e esgoto, por ligação e por economia, com subcategoria — 7 arquivos) | Distribuição de ligações por faixa de consumo | Referência, totalização | Opção de totalização, descrição | P3.22 (histograma por faixa, POC-56) | N |
| Boletim de medição (padrão, acompanhamento, arquivo texto) | Medição do serviço terceirizado de leitura com taxa de sucesso e penalidades | Referência, contrato, empresa | Empresa | AUSENTE (medição de contrato de leitura terceirizada; P2.7 só importa) | C |
| Consultar movimentação de hidrômetro; coleta de medidor de energia | Movimentos de estoque de hidrômetros e leitura de energia | Motivo, local de origem e destino, usuário, período | Local | ED-14 (hidrometria), ED-58 (estoque) | B |

### 3.11 Atendimento: registro de atendimento e ordem de serviço

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Consultar registro de atendimento (com detalhe; via cliente; variante Cosanpa); filtrar RA (geral e com coordenadas sem logradouro); numeração manual de RA | Listagem de RA por critério e comprovante ao cliente | Período, tipo, situação, unidade, meio | Unidade | P3.4 (protocolo) e P3.22 (serviços por protocolo com tempos, POC-64) | N |
| Acompanhamento de RA (analítico; sintético aberto; sintético encerrado); gestão de solicitações por chefia; resumo de solicitações por unidade; resumo de RA por unidade e por usuário | Pendências e encerramentos no prazo e fora do prazo por unidade e atendente | Período, unidade, tipo | Unidade, usuário | P3.22 (pendências por usuário, POC-65); ED-49 (painel de atendimento) | N |
| Ordem de serviço (padrão, CAERN, modelo 2); OS seletiva (analítico, por categoria, sugestão); OS de fiscalização; auto de infração | Documentos de campo | OS, comando | Categoria | doc: P3.4, ED-36 | — |
| Filtrar OS; acompanhamento da execução de OS; OS por situação (analítico e sintético); boletim e relação de OS concluídas; OS encerradas e pendentes (relação e resumo); OS encerradas por motivo | Carteira e produtividade das OS por situação, prazo e motivo | Período de atendimento e de encerramento, unidade, serviço | Situação, unidade, motivo | P3.22 (serviços por protocolo com tempos); ED-36 | N |
| OS executadas por prestadora de serviço (analítico, detalhe, sintético) | Medição de contrato de terceirizada por OS | Empresa, período | Empresa | ED-36 (executores); ED-58 | B |
| Ordens de serviço de fiscalização (analítico e sintético); multas de autos de infração (geral e pendentes) | Fiscalização e multas aplicadas | Unidade, funcionário, período | Unidade | ED-36; AUSENTE (auto de infração; 03 §3.9) | C |
| Gestão de serviços UPA | Resultado da integração com o sistema de campo | Período | Equipe | P4.4 (app de campo) | D |

### 3.12 Operacional

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Relação de ordens de repavimentação para aceite (com sub); serviços em acompanhamento de repavimentação (dois modelos); boletim de custo de pavimento | Repavimentação após serviço em via pública: medição e custo por unidade repavimentadora | Unidade repavimentadora, período, situação, gerência | Unidade, tipo de pavimento | AUSENTE (repavimentação; ED-36 só OS) | C |
| Resumo por zona de abastecimento e por distrito operacional | Ligações e volumes por recorte operacional | Referência | Zona, distrito | ED-48 (balanço por setor); GIS | B |
| Qualidade da água (padrão, CAERN, itens) | Bloco de qualidade para a conta e relatório mensal | Referência, sistema | Parâmetro | ED-50 | B |

### 3.13 Gerencial

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Boletim de Indicadores Gerenciais (BIG), saída XLS | Vinte e poucos indicadores mensais por gerência regional e localidade (ver §4) | Referência | Gerência, localidade | ED-48, ED-49 (indicadores estratégicos) | B |
| Quadro de metas do exercício e acumulado | Metas de ligações, cortes, débito > 3 meses, medição, por grupo de subcategoria | Exercício, totalização | Opção de totalização | ED-48 (metas e semáforo) | B |
| Orçamento SINP | Orçamento do plano de investimentos da companhia | Exercício | — | AUSENTE | D |

### 3.14 Segurança e auditoria

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Acessos por usuário; consultar operação; operação efetuada; funcionalidades e operações por grupo; solicitações de acesso; listagem de usuários, tipos, grupos e operações | Auditoria de acesso e de perfil de permissão | Referência, usuário, operação, período, horário | Usuário, grupo | Console do tenant (P0.13, P1.x) e `audit_log` (P2.3); ED-47 (auditoria de acesso ao BI) | A |
| Alterações do sistema por coluna (por localidade e por usuário) | Trilha de alteração de campo cadastral com valor anterior e posterior | Coluna, período, localidade, usuário | Localidade, usuário | P2.20 (trilha por campo da conta) — ampliar ao cadastro; ADR-0008 | A |

### 3.15 Infraestrutura de relatório

| Nome funcional | Finalidade | Filtros | Agrupamentos | Correspondente | Prior. |
|---|---|---|---|---|---|
| Relatório padrão de lote; relatório vazio | Cabeçalho comum e resposta para consulta sem resultado | — | — | Núcleo da P3.22 | N |

### 3.16 Síntese quantitativa e mecanismo de geração

- Contagem por domínio (arquivos, incluindo subrelatórios e variantes): cadastro de apoio 85; imóvel e recadastramento
  45; faturamento e conta 70; débitos 45; arrecadação 32; cobrança e negativação 60; parcelamento 25; financeiro 12;
  certidões 8; micromedição 50; atendimento 45; operacional 12; gerencial 5; segurança 12; infraestrutura 2 [FATO,
  contagem dos nomes; a atribuição a domínio é INFERÊNCIA].
- Mecanismo [FATO, `tarefa/TarefaRelatorio.java`, `batch/RelatorioGerado.hbm.xml`, `ControladorBatchSEJB`]: cada
  relatório tem uma classe que monta uma coleção de beans a partir de um filtro, um arquivo de leiaute e parâmetros de
  cabeçalho (empresa, usuário, formato). Relatórios curtos saem na requisição; relatórios longos viram processo do tipo
  "relatório", entram na fila de funcionalidades agendadas, gravam o PDF em tabela com número de páginas, ficam
  disponíveis em "relatórios gerados" por usuário e são apagados por data de expiração. Formatos: PDF, RTF, XLS e
  HTML. Não há CSV nem agendamento recorrente pelo usuário [FATO].
- Variantes por empresa (CAEMA, CAER, CAERN, COSANPA, JUAZEIRO) existem porque o leiaute impresso pertence à companhia.
  No Ziip SAN, o equivalente é um relatório com marca e rodapé do tenant (P2.17, ADR-0016), nunca um arquivo por tenant.

Resumo do mapeamento: os cerca de 20 relatórios da P3.22 cobrem 38 linhas do catálogo marcadas **N** (quando se
consideram as famílias); 46 linhas ficam na onda **A**, 30 na **B**, 10 na **C** e 11 em **D**. Itens marcados AUSENTE
que não dependem de módulo futuro: motivo de não geração (LOT-14), auditoria de alteração de dado pessoal, contrato de
adesão, transferência de débito entre imóveis.

## 4. Indicadores gerenciais

Fontes [FATO]: `gerencial/*/UnResumoIndicadores*.hbm.xml`, `UnResumoIndicadorDesempenhoMicromedicao.hbm.xml`,
`UnResumoIndicadorLigacaoEconomia.hbm.xml`, `UnResumoMeta.hbm.xml`, `UnResumoRegistroAtendimento.hbm.xml`,
`relatorio/big/*`, `reports/relatorioBIG.jrxml`. Grão comum das tabelas de resumo: referência (ano-mês, com colunas
separadas de ano e mês), setor comercial e quadra, com cerca de 20 dimensões achatadas (gerência regional, unidade de
negócio, localidade e elo, rota, categoria, subcategoria, perfil do imóvel, situação e perfil das ligações de água e
esgoto, tipo de cliente, esfera de poder, consumo-tarifa, grupo de faturamento, forma e arrecadador, documento, época
e situação de pagamento, meio de solicitação, tipo e especificação de solicitação, motivo de encerramento). Cada medida
aparece em duas visões: do mês e acumulada no ano ("mês/ano") [FATO].

As fórmulas abaixo são em palavras. Onde a fórmula foi vista no código ou na tabela de resumo, [FATO]; onde foi deduzida
do nome da medida, [INFERÊNCIA]. A coluna SINISA aponta o indicador do antigo SNIS com equivalência aproximada; o
glossário vigente do SINISA ainda não foi confirmado (pergunta E-07 do documento 08) e a ED-48 deve tratar cada código
como hipótese a validar.

| ID | Indicador | Fórmula em palavras | Unidade | Cortes | SINISA / ED-48 | Marca |
|---|---|---|---|---|---|---|
| IND-01 | Eficiência de arrecadação | Valor arrecadado no mês dividido pelo valor faturado no mês; o BIG também traz a variante acumulada no ano | % | Gerência, localidade, mês | Equivalente aproximado do IN029 (evasão de receitas = 1 − eficiência); ED-48 | FATO (BIG) |
| IND-02 | Arrecadação por época de pagamento | Valor arrecadado no mês até o vencimento, após o vencimento, no segundo mês, no terceiro mês e acumulado até três meses, cada um dividido pelo faturamento líquido do mês de origem | % e R$ | Setor, quadra e dimensões | Base do IN054 (dias de faturamento comprometidos); ED-48, ED-49 (cobrança) | FATO (`UnResumoIndicadoresCobranca`) |
| IND-03 | Inadimplência por faixa | Quantidade e valor de contas pendentes vencidas há até 30 dias, até 90 e acima de 90, divididos pelo total de contas e pelo faturamento do período | % (qtd e valor) | Gerência, localidade | ED-48; painel de cobrança (ED-49) | FATO (BIG) |
| IND-04 | Pendência total | Valor pendente de contas, serviços (guias e débitos a cobrar) e parcelamentos, com quantidade de documentos e de ligações devedoras; variante por referência do documento e indicador de vencido | R$, qtd | Setor, quadra, grupo de faturamento, consumo-tarifa | ED-48; ED-49 | FATO (`UnResumoPendencia`) |
| IND-05 | Faturamento líquido | Valor faturado de água e esgoto mais outros, menos cancelamentos, mais inclusões, por mês e por ano; valor médio por conta emitida | R$ | Todas | Base de IN005/IN006 (tarifa média); ED-48 | FATO (`UnResumoIndicadoresFaturamento`) |
| IND-06 | Erros de conta | Quantidade e valor de contas retificadas e canceladas divididos por contas emitidas | % | Gerência, localidade | Sem equivalente SNIS; indicador de qualidade da ED-48 | FATO (BIG) |
| IND-07 | Índice de hidrometração | Ligações ativas com hidrômetro divididas por ligações ativas de água | % | Setor, quadra e dimensões | IN009 (hidrometração); NR 15 IAG1003 (documento 08 R-31) | FATO (`UnResumoIndicadorDesempenhoMicromedicao`, BIG) |
| IND-08 | Índice de medição real | Ligações (ou economias) com medição real divididas por ligações ativas; complemento: com hidrômetro e consumo estimado | % | Idem | Base do IN010/IN044 (micromedição) | FATO (campos "com medição real" e "com hidrômetro e medição estimada") |
| IND-09 | Volume faturado medido e não medido | Volume faturado de água (e de esgoto) separado em medido e não medido; razão medido/total | m³, % | Idem | IN010 (micromedição relativa ao volume disponibilizado) exige macromedição, que o GSAN não tem; ED-48 (macromedição e balanço por setor) | FATO (volumes) / INFERÊNCIA (razão) |
| IND-10 | Água não faturada por consumo registrado | Volume consumido não faturado valorado (lote "volumes consumidos não faturados"), dividido pelo volume consumido | m³, R$, % | Localidade | Componente das perdas aparentes do balanço hídrico (NR 15); não é o IN013 (que exige volume produzido) | FATO (lote) / INFERÊNCIA (razão) |
| IND-11 | Perdas de faturamento | Não calculado: o GSAN não tem macromedição nem volume produzido; "perdas" no GSAN é perda de crédito | — | — | IN013, IN049, IN051 ficam inteiramente para a ED-48 e o módulo de perdas (documento 08 R-31) | FATO (ausência) |
| IND-12 | Consumo médio por economia e por ligação | Consumo de água das ligações ativas dividido por economias ativas (e por ligações) | m³/econ/mês | Idem; subcategoria | IN053 (consumo médio por economia); IN014 (micromedido por economia) | FATO (medidas) / INFERÊNCIA (razão) |
| IND-13 | Ligações por situação e índices de cortados, suprimidos e factíveis | Quantidade de ligações e economias ativas, cortadas, suprimidas e potenciais/factíveis; cada uma dividida pelo total | qtd, % | Setor, quadra | IN001 (economias por ligação); IN055 (atendimento urbano, só com domicílios do IBGE) | FATO (BIG e `UnResumoIndicadorLigacaoEconomia`) |
| IND-14 | Ligações ativas com débito acima de três meses | Quantidade de ligações ativas com pelo menos uma conta vencida há mais de três meses dividida pelas ativas | % | Setor, quadra, grupo de subcategoria | Candidato ao catálogo parametrizável da ED-48 | FATO (`UnResumoMeta`) |
| IND-15 | Ligações por faixa de consumo (até 5 m³, medido, não medido, média) | Quantidades por faixa do quadro de metas | qtd | Idem | ED-48 (metas) | FATO |
| IND-16 | Parque de hidrômetros e movimentação | Hidrômetros instalados, substituídos, remanejados e retirados no mês (ramal e poço), parque atual e idade por ano de fabricação | qtd | Setor, marca, capacidade | ED-14 (alerta de validade), ED-48 | FATO (`UnResumoHidrometro`, desempenho de micromedição) |
| IND-17 | Produtividade da leitura | Visitas realizadas, leituras efetuadas e leituras com anormalidade de hidrômetro; razão leituras/visitas e anormalidades/leituras | qtd, % | Rota, leiturista | ED-49 (painel de medição) | FATO (medidas) / INFERÊNCIA (razões) |
| IND-18 | Prazo médio de atendimento de OS | Soma dos dias entre abertura e encerramento das OS encerradas no mês dividida pela quantidade encerrada | dias | Gerência, localidade | NR 9 "duração média de reparo" (R-34); ED-48 | FATO (BIG) / INFERÊNCIA (definição exata do intervalo) |
| IND-19 | RA no prazo e fora do prazo | RA geradas, pendentes e encerradas no mês, separadas em dentro e fora do prazo da especificação; percentual fora do prazo; separação por meio (on-line ou presencial) | qtd, % | Unidade de solicitação, unidade de encerramento, tipo, motivo de encerramento, rota | NR 9 "reclamações" (R-34); NR 11 prazos; ED-48, ED-49 (atendimento) | FATO (`UnResumoRegistroAtendimento`) |
| IND-20 | Novas ligações e economias | Quantidade de ligações e economias de água e de esgoto incluídas no mês | qtd | Gerência, localidade | Base da evolução de cobertura (NR 8); ED-48 | FATO (BIG) |
| IND-21 | Ações de corte | Cortes, supressões, religações e restabelecimentos no mês; razão religações/cortes | qtd, % | Setor, quadra | ED-49 (operacional); NR 11 (prazo de religação) | FATO (`UnResumoIndicadoresCobranca`) |
| IND-22 | Parcelamento | Quantidade de parcelamentos, valor negociado, financiado e parcelado; valor arrecadado de parcelamento | qtd, R$ | Idem | ED-49 (cobrança) | FATO |
| IND-23 | Arrecadação por forma e arrecadador | Valor e quantidade de pagamentos por forma de arrecadação, arrecadador, época e situação de pagamento, com devoluções classificadas e não classificadas e valor não identificado | R$, qtd | Setor, quadra, forma, arrecadador | ED-49 (arrecadação); ED-26 | FATO (`UnResumoArrecadacao`) |
| IND-24 | Faturamento comprometido | Valor faturado de contas em situação especial de faturamento e de cobrança, por motivo | R$ | Localidade | ED-48 | FATO (resumos de situação especial) |

Observações para a ED-48:

- O GSAN não guarda a fórmula do indicador; guarda as medidas e calcula na consulta ou no BIG. Para a ED-48 o Ziip SAN
  precisa de `indicator_definitions` versionadas (numerador, denominador, período, filtro, unidade, fonte, código
  regulatório, vigência) e de `indicator_values` com fechamento mensal, sem o que o semáforo e as metas não auditam.
- As dimensões achatadas do GSAN equivalem às views de relatório por domínio do `reporting` (`V15_x`) e aos datasets
  do Superset (ADR-0027). O grão setor comercial × quadra é fino demais para painel e grosso demais para auditoria; o
  Ziip SAN deve guardar o grão natural (ligação, conta, pagamento) na réplica e agregar nas views.
- Nenhum indicador regulatório nominal foi encontrado em `src/gcom` (confirmação do 04 §4.4 com busca completa em
  `gerencial`, `relatorio` e `batch/gerencial`) [FATO].

## 5. Portal do cliente final (loja virtual)

Fontes [FATO]: `src/gcom/portal/*` (controlador e repositório da loja virtual), `src/gcom/gui/portal/*` (ações),
`gcom/jsp/portal/*.jsp` (telas), `gui/atendimentopublico/*SegundaViaContaInternet*`, relatórios
`relatorioDocumentosParcelamentoLojaVirtual` e `relatorioEstruturaTarifariaLojaVirtual`.

### 5.1 O que o cliente final faz no GSAN

| Função | Regras observadas | Marca |
|---|---|---|
| Acesso | Identificação pela matrícula do imóvel; conforme parâmetro da empresa, exige também CPF ou CNPJ, validado pelo dígito e conferido contra os clientes do imóvel. Sem senha, sem conta, sem captcha visível, sem limite de tentativas. Sessão guarda matrícula, nome do usuário e documento | FATO |
| Segunda via de conta | Lista as contas com débito do imóvel (e contas pretéritas), totaliza valor e acréscimos de impontualidade na data e emite a 2ª via com código de barras; recusa quando o imóvel não tem débito | FATO |
| Parcelamento de débitos on-line | Apresenta opções pré-calculadas por perfil de parcelamento (quantidade de parcelas, valor da prestação, entrada mínima); exige escolher uma opção; se a entrada informada difere da mínima, recalcula; o parcelamento é registrado em nome de um usuário técnico "internet"; emite os documentos do parcelamento (termo e guia da entrada) | FATO |
| Certidão negativa do imóvel | Só emite quando o imóvel não tem contas, guias nem débitos a cobrar válidos; validade impressa | FATO |
| Declaração anual de quitação de débitos | Emissão sob demanda por matrícula e ano | FATO (tela) |
| Solicitação de serviços (RA pelo portal) | Telefone de contato obrigatório; verifica se já existe RA pendente do mesmo tipo para o imóvel; devolve o número de protocolo | FATO |
| Cadastro e alteração de e-mail do cliente | Alteração proposta pelo portal, confirmada por link enviado ao e-mail; o nome do cliente só pode mudar em até cinco caracteres; valida CPF/CNPJ | FATO |
| Conta em braile | Cadastro da solicitação de conta em formato braile, atendida por lote de impressão | FATO |
| Informações | Estrutura tarifária vigente, normas de instalação, tarifa social, negociação de débitos, lojas de atendimento por localidade, ouvidoria, central de atendimento | FATO (telas) |
| Questionário de satisfação | Pesquisa ao final do atendimento no portal | FATO (tela) |
| Atualização cadastral pela internet | Cliente propõe alteração de dados; o lote compara e gera relatório de alterações | FATO (relatório e lote) |
| Ausentes no GSAN | Histórico de consumo gráfico, informe de autoleitura, débito automático pelo portal, troca de titularidade, alteração de vencimento, acompanhamento de OS, notificações, Pix, conta própria com senha | FATO (ausência) |

### 5.2 Cruzamento com o Ziip SAN

| Função | P3.3 | P3.20 | ED-38 | ED-39 / ED-41 / ED-15 | Observação |
|---|---|---|---|---|---|
| Identificação e acesso | Conta própria no realm `ziipsan-portal`, login por CPF/CNPJ ou e-mail e senha, MFA opcional, limite de tentativas, resposta indistinta | Vínculo por código impresso na conta + documento do titular | — | ED-39: app com PKCE | Superior ao GSAN. O GSAN aceita acesso só por matrícula quando o parâmetro desliga o documento; o Ziip SAN não deve oferecer esse modo |
| Segunda via e pagamento | 2ª via e Pix | Conta paga com quitação, DANFAG e XML | 2ª via de faturas pendentes e quitadas | — | Coberto |
| Histórico | — | 120 meses de contas e pagamentos | Histórico de consumo | — | Coberto a partir do P3.20 |
| Débitos e simulação | Protocolo | — | Simulação de fatura e de parcelamento | — | Coberto em simulação |
| Parcelamento on-line com contratação | — | — | Não explícito (POC-69 fala em simulação) | — | **Lacuna**: o GSAN contrata o parcelamento no portal, com entrada e termo. O Ziip SAN só simula |
| Certidão e declaração anual | — | — | Certidão de quitação anual e certidão | — | Coberto na onda A |
| Solicitação de serviço e acompanhamento | Protocolo (RN 11) | — | OS e solicitações | ED-41 notificações | Coberto; o GSAN não acompanha OS no portal |
| Cadastro de e-mail com confirmação | — | — | — | ED-41 (e-mail verificado para anexo) | Parcial: o Ziip SAN verifica e-mail para opt-in; não prevê alteração de dados cadastrais pelo portal com confirmação |
| Conta em formato acessível (braile) | — | — | — | — | **Lacuna**: RNF-60 cobre a acessibilidade da tela, não o documento em braile ou em leitura por voz |
| Tabela tarifária, tarifa social, normas | — | — | Tabela tarifária | — | Parcial: tarifa social e normas de instalação como conteúdo do tenant não previstos |
| Autoleitura | — | — | — | ED-15 (autoleitura com crítica) | Ausente no GSAN; previsto no Ziip SAN só no P4 |
| Qualidade da água | — | — | Qualidade da água | — | ED-50 |
| Questionário de satisfação | — | — | — | — | Ausente no Ziip SAN; relevante para NR 11 (ouvidoria) e editais de atendimento |

## 6. API e web services

### 6.1 O que existe no GSAN

- `api/` e `src/gcom/api` [FATO; detalhe no 04 §5]: cliente HTTP de um serviço externo de relatórios. O GSAN monta um
  objeto com nome, título, formato (PDF ou XLS), cabeçalho, grupos, totalizadores, linhas e resumo; as colunas são
  descritas por anotação na classe de dados (descrição, se é cabeçalho, grupo ou totalizador, alinhamento, tipo:
  texto, data, moeda, inteiro). A resposta traz a URL do arquivo ou o erro, e o GSAN baixa o arquivo e o devolve ao
  navegador. É um motor declarativo "dados + metadados → documento", sem leiaute por relatório. A conversa não é
  autenticada e o arquivo é gravado em disco pelo nome recebido [FATO; risco apontado no 04 §5].
- `src/gcom/integracao/webservice/spc` [FATO]: stub e callback gerados (Axis2) para consultar o SPC; direção consumida;
  credenciais em arquivo de propriedades.
- `src/gcom/integracao/upa` [FATO]: tabela de ordens de serviço espelhada com o sistema de campo UPA, com unidade e
  usuário executores, equipe, identificador do coletor, placa do veículo, datas de início e fim, situação, motivo de
  encerramento, pavimento de rua e calçada com áreas, reaterro, uso de retroescavadeira e compressor, trabalho em fim de
  semana. Um verificador de hora em hora troca os dados [FATO]. Direção: bidirecional por banco.
- `src/gcom/integracao/ServicoTerceiroAcompanhamentoServico` [FATO]: registro do acompanhamento de serviço por
  terceiro, alimentado por arquivo gerado em lote.
- `GisRetornoMotivo` [FATO; 04 §5]: códigos de retorno do GIS externo para a validação de endereço e coordenada.
- Leitura e recadastramento em campo: arquivos texto gerados e importados por lote; sem serviço HTTP [FATO].
- `src/gcom/gerador/Gerador.java` [FATO]: utilitário de linha de comando que gera código de classes a partir de uma
  lista de atributos e do nome do caso de uso; é ferramenta de desenvolvimento, não funcionalidade do produto.
- Servlets registrados [FATO, `gcom/WEB-INF/web.xml`]: inicializador do sistema, acesso ao "novo batch" (console do
  lote), acesso ao operacional, controlador Struts e autocompletar. Não há servlet de API.

### 6.2 O que o Ziip SAN já prevê em `/api/v1`

- API única por trás do BFF (ADR-0006), com emissor do portal aceito só nas rotas do portal (ADR-0022) [FATO, plano].
- Rotas nomeadas no plano: `GET /api/v1/gis/network-at` (P2.22); API REST de telemetria e importação de leituras AMR
  (ED-17); API de métricas `v1` da Central (P1.17); sincronização de leituras do app de campo em lotes de até 500 com
  chave de idempotência (RNF-07, P4.4); `Idempotency-Key` nas operações de conta (P2.20); exportação diária ao sistema
  contábil por arquivo e pela ED-62 [FATO, plano].
- Ausências em relação ao que um prestador pede hoje: nenhuma API pública documentada para terceiros (prefeitura,
  cartório, cobrança terceirizada, central de atendimento externa); nenhum webhook de evento de negócio para o tenant;
  nenhuma API do portal para parceiros (apps de pagamento). A ED-07 (eventos de negócio com alerta) e a ED-62
  (integração com ERP) são os pontos de entrada naturais, mas nenhuma delas define um contrato público versionado.

## 7. Melhores práticas

Manter (adaptadas ao Ziip SAN):

1. Unidade de processamento com código real e retomada só do pendente (LOT-03), estendida a localidade, setor, grupo de
   cobrança e arrecadador, não só a rota.
2. Processo que exige autorização de um segundo usuário antes de executar (LOT-06), aplicável a encerramento de
   referência, emissão de documentos de corte e cancelamento em massa.
3. Trava de pré-condição de negócio no comando do faturamento (LOT-08): nenhuma rota pendente de leitura.
4. Cronograma mensal por grupo com previsto, comandado e realizado e catálogo de atividades com precedência,
   obrigatoriedade e repetibilidade (LOT-09), que alimenta a aba Faturamento da Central (P2.11) e o alerta de ciclo
   atrasado.
5. Relatório pesado como trabalho assíncrono com expiração do arquivo e notificação ao solicitante (LOT-12).
6. Motivo de não geração por imóvel na seleção da cobrança (LOT-14), como catálogo de rejeições com relatório.
7. Data mart com dimensões achatadas e medida do mês e acumulada do ano, materializado após o fechamento (base das views
   do `reporting`).
8. Relatório declarativo por metadados de coluna (ideia do serviço `gsan-relatorios`), que é o núcleo da ED-45.
9. Cadastro de e-mail do cliente confirmado por link e limite de alteração de nome (LGPD e fraude).

Não replicar:

1. Tarefa serializada em bytes na base; uma funcionalidade por vez; cancelamento geral no boot; relatório em `bytea`.
2. Ordem entre processos garantida só pela disciplina do operador; dependências não declaradas.
3. Variantes de relatório e de documento por empresa; quatro formatos sem CSV; sem agendamento recorrente.
4. Indicadores sem fórmula registrada; grão fixo setor × quadra; cálculo no relatório.
5. Acesso ao portal só por matrícula; usuário técnico "internet" como autor do parcelamento sem registro do consumidor.
6. Integração por tabela compartilhada com sistema de campo e por arquivo texto para leitura.

## 8. Lacunas do Ziip SAN por impacto

| # | Lacuna | Impacto | Evidência | Tarefa afetada | Proposta |
|---|---|---|---|---|---|
| 1 | Nenhuma pré-condição de negócio no disparo do faturamento (rotas com leitura pendente, cronograma do grupo, referência aberta) | Conta emitida com consumo estimado indevido; ciclo rodado fora de ordem; retrabalho na P2.20 | LOT-08, LOT-09, LOT-11; ARQUITETURA-ALVO §2 | P2.5, P2.1, P2.11 | `billing_activities` (ordem, `predecessor_activity_id`, obrigatória, repetível, comandável, job) e `billing_schedules` por grupo e referência com previsto, comandado e realizado; `period_closes` por domínio; verificação no `JobExecutionListener` com mensagem de negócio |
| 2 | Sem catálogo de processos, autorização prévia nem limite por usuário | Operador dispara encerramento sem segunda aprovação; sem visão do que roda por grupo e referência | LOT-06, LOT-07, LOT-10, LOT-13 | P2.1 | `job_definitions` (ligado, agenda, `requires_approval`) e colunas de identidade em `job_runs`; aprovação dupla reaproveitando a da P3.1 |
| 3 | Motivo de não geração e relatório de rejeição por unidade ausentes | Cobrança e corte sem explicação do que ficou de fora; contestação regulatória (NR 11) sem rastro | LOT-14; §3.6 | P3.4, P3.22 | Catálogo `exclusion_reasons` e tabela por execução; relatório no núcleo da P3.22 |
| 4 | Indicadores sem fórmula versionada antes da ED-48; nenhum indicador no P3 | Painéis da ED-49 e SINISA (P4.2) sem base auditável; BIG do GSAN não tem equivalente até 2028 | §4 | ED-48, P4.2, P4.3 | Antecipar `indicator_definitions` e seis indicadores de base (IND-01, 03, 07, 08, 18, 19) para o núcleo da P3.22, calculados nas views |
| 5 | Portal sem parcelamento on-line com contratação, sem conta acessível e sem questionário de satisfação | Perda funcional frente ao legado que o piloto conhece; itens de edital de atendimento | §5.2 | P3.3, ED-38 | Incluir contratação do parcelamento com entrada por Pix na ED-38; formato acessível da conta como parâmetro do tenant; pesquisa de satisfação após protocolo |
| 6 | Relatórios de auditoria de alteração de dado pessoal e de acesso por usuário sem tarefa | LGPD (ADR-0008) sem relatório de prestação de contas ao DPO | §3.2, §3.14 | P2.3, P3.22 | Dois relatórios no núcleo: alterações por campo e acessos por usuário, sobre `audit_log` e `personal_data_access_log` |
| 7 | Sem expiração nem fila própria para relatórios gerados | Fila do faturamento disputada por relatório; armazenamento cresce sem limite | LOT-12 | P3.22 | Fila `reports` na outbox, `expires_at` e expurgo pelo `worker` (RNF-40) |
| 8 | Nenhuma API pública versionada nem webhook de evento para o tenant | Integrações com prefeitura, cobrança terceirizada e sistemas de campo de terceiros só por arquivo | §6.2 | ED-07, ED-62 | Definir na ED-07 o contrato `/api/v1/events` (assinatura, reentrega, sem dado pessoal) e na ED-62 o conjunto mínimo de leitura (`properties`, `bills`, `payments`, `service-orders`) com escopo por cliente OAuth |
| 9 | Contrato de adesão (NR 11) e transferência de débito entre imóveis sem tarefa | Exigência regulatória e operação diária de atendimento | §3.2, §3.4 | P3.4, P2.20 | Documento de adesão gerado na ligação nova; operação "transferir débito" com motivo e aprovação |
| 10 | Medição de contratos terceirizados (leitura, cobrança por resultado, prestadora de OS) sem módulo | Prestadores com leitura e cobrança terceirizadas não conseguem medir o contrato | §3.6, §3.10, §3.11 | P2.7, ED-36, ED-58 | Decidir se entra como relatório sobre OS e leituras (onda B) ou fica fora |

## 9. Perguntas ao dono do produto

1. Pré-condições do faturamento (lacuna 1): (a) bloquear o comando com rota pendente, como o GSAN; (b) permitir com
   justificativa registrada e consumo estimado marcado na conta; (c) deixar como parâmetro do tenant com padrão (a).
2. Cronograma por grupo (LOT-09): (a) entra na P2.5 com atividades previstas e realizadas; (b) entra na P2.11 só como
   datas-alvo para alerta; (c) fica para a ED-26.
3. Autorização prévia de lote (LOT-06): (a) aprovação dupla para encerramento de referência, corte em massa e
   cancelamento em massa; (b) só para encerramento; (c) nenhuma, confiando no papel RBAC.
4. Indicadores no P3 (lacuna 4): (a) seis indicadores de base com fórmula versionada no núcleo da P3.22; (b) só as
   medidas nas views, fórmulas na ED-48; (c) nada antes do P4.
5. Parcelamento on-line (lacuna 5): (a) contratação com entrada por Pix na ED-38; (b) só simulação, contratação no
   atendimento; (c) contratação sem entrada, parcelas em conta.
6. Conta em formato acessível: (a) braile por gráfica parceira como parâmetro do tenant; (b) PDF acessível com leitura
   por voz no portal; (c) ambos; (d) fora do escopo.
7. Relatórios de LGPD (lacuna 6): (a) no núcleo da P3.22; (b) na onda A; (c) só consulta no console sem relatório.
8. API pública (lacuna 8): (a) contrato de eventos na ED-07 e leitura na ED-62; (b) só eventos; (c) nenhuma API pública
   antes de pedido de cliente.
9. Descarte das listagens "Manter" (§3.1): (a) descartar e oferecer exportação CSV genérica de qualquer tela; (b)
   manter como relatórios do núcleo; (c) só as de cliente e imóvel.
10. Variantes por empresa (03 §6 e §3.16): (a) um relatório com marca do tenant, sem variante; (b) permitir leiaute
    alternativo por tenant só para conta e 2ª via; (c) leiaute por tenant para qualquer documento.

## 10. Limites

- As tabelas `processo_funcionalidade` (passos de cada processo) e de atividades de faturamento (precedências) não
  têm carga no repositório; o grafo do §2.3 foi montado a partir do controlador, das tarefas e dos nomes dos lotes, e
  as setas "⇢" são inferência.
- O controlador de faturamento tem uma variante por companhia (CAEMA, CAER, CAERN, COMPESA, COSAMA, COSANPA, JUAZEIRO)
  além da base; as regras citadas foram lidas na base e na variante consolidada, não em cada companhia.
- Parâmetros e grupos dos relatórios foram extraídos por script dos arquivos de leiaute; títulos, campos e leiaute não
  foram lidos. A finalidade de cada relatório é dedução do nome e dos parâmetros; a atribuição a domínio é editorial.
- A busca por indicadores regulatórios cobriu `gerencial`, `relatorio`, `batch/gerencial` e `relatorio/big`; não cobriu
  JSP nem scripts SQL fora de `src`.
- O portal foi analisado pelas ações, pelo controlador e pelos nomes das telas; textos e validações no lado do navegador
  não foram lidos.
- Códigos SNIS citados no §4 são aproximações; o glossário SINISA vigente não foi confirmado.
- Não há análise de equipe, prazo ou capacidade, por decisão do plano (ADR-0035).
