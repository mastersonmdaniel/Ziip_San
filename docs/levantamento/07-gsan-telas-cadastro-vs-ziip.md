# Levantamento 07 — GSAN: telas e formulários de CADASTRO, comparados campo a campo com o Ziip SAN

Data: 11/10/2026. Escopo: inventário das telas de cadastro do GSAN e matriz de lacunas contra o SIGSAN.
Fonte (somente leitura): `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GSAN`. Nada do GSAN ou do SIGSAN foi alterado; este arquivo é a única escrita.

**Regras de higiene (clean-room).** Este documento registra conceitos: nome de tela, campos (rótulo, tipo, obrigatoriedade, domínio, regra de habilitação), fluxo e abas. Não há código, JSP, SQL, `.jrxml` nem texto de mensagem copiado. As mensagens de erro do GSAN aparecem parafraseadas. Layouts e nomes de coluna `abcd_*` não entram. Licença do GSAN segue "não verificada" (ver `01` seção 2).

**Convenções.** **[FATO]** = visto em arquivo (caminho citado). **[INFERÊNCIA]** = dedução minha. **(*)** = rótulo marcado como obrigatório na JSP. "Obrig." na tabela vem de três fontes cruzadas: marcador (*) da JSP, `validator.xml` e checagens do controlador. Onde só uma delas confirma, está dito.

---

## 0. Como o levantamento foi feito e o que ele não cobre

| Item | Detalhe |
|---|---|
| Telas (JSP) | `gcom/jsp/cadastro/**`, `gcom/jsp/atendimentopublico/**`, `gcom/jsp/micromedicao/hidrometro/**`, `gcom/jsp/micromedicao/rota_*.jsp`. Extraí rótulo, controle, `maxlength`, `readonly`/`disabled` e eventos com um script de leitura (em `scratchpad`, fora do projeto). |
| Formulários | O GSAN usa **DynaForm declarado em `gcom/WEB-INF/struts-config.xml`** para o imóvel (`InserirImovelActionForm`, linha 287), não classe Java. Os `*ActionForm.java` de `src/gcom/gui/cadastro/**` existem só para telas secundárias. [FATO] |
| Validações | `gcom/WEB-INF/validator.xml` (formulários `InserirImovelActionForm` l.398, `FiltrarImovelActionForm` l.662, `ClienteActionForm` l.691) e controladores: `ControladorImovelSEJB.validarImovelAba*` (l.13020 a 14100), `InserirClienteAction`, `ControladorAtendimentoPublicoSEJB.validarExibir*`. |
| Menu | Só consegui o menu do **help** (`gcom/help/toc.xml`), que cobre Cadastro e Micromedição. O menu real do sistema vive no banco (funcionalidades), **não visto** [INFERÊNCIA]. |
| Não lido | Religação/restabelecimento (JSP vistas só por nome), movimentação de hidrômetro (parcial), situação de cobrança, ficha de fiscalização, relatórios. Nenhum `.hbm` novo: tipos de coluna vêm do relatório `01`. |
| Encoding | Fontes em ISO-8859-1. Lidos com conversão em memória; nada regravado. |
| Falha de processo | Lancei por engano uma busca recursiva no Drive (violava a regra 2). Foi interrompida na hora e não gerou saída usada. |

---

## 1. Mapa do menu de Cadastro e das telas

Menu (help `toc.xml`) [FATO]: **Cadastro** > Cliente | Imóvel | Localização | Endereço | Categoria e Subcategoria | Tarifa Social. **Micromedição** > Hidrômetro | Medição Individualizada.

| Grupo do menu | Tela (nome no GSAN) | JSP | Configuração Struts / form | Prioridade de uso (inferência) |
|---|---|---|---|---|
| Imóvel | **Inserir Imóvel** (assistente de 7 etapas) | `cadastro/imovel/imovel_inserir_{localidade,endereco,principal,cliente,sub_categoria,caracteristicas,conclusao}.jsp` | `struts-config.xml` l.1029+, `InserirImovelWizardAction`, DynaForm `InserirImovelActionForm` | Altíssima |
| Imóvel | **Manter/Atualizar Imóvel** (mesmas etapas, mais "Matrícula") | `imovel_filtrar.jsp` > `imovel_manter.jsp` > `imovel_atualizar_*.jsp` | `AtualizarImovelWizardAction`, `ManterImovelActionForm` | Altíssima |
| Imóvel | **Consultar Imóvel** (10 abas) | `imovel_consultar_{cadastro,dados_complementares,analise_medicao_consumo,historico_faturamento,debitos,pagamentos,devolucoes,documentos_cobranca,parcelamentos_debitos,registro_atendimento}.jsp` | `WEB-INF/cadastro/imovel/struts-config-ConsultarImovel.xml`, `ConsultarImovelWizardAction` | Altíssima (atendimento) |
| Imóvel | **Filtrar / Pesquisar Imóvel** | `imovel_filtrar.jsp`, `imovel_pesquisar.jsp` (popup), `imovel_resultado_pesquisa.jsp`, "outros critérios" (5 JSP) | `FiltrarImovelActionForm`, `PesquisarActionForm` | Altíssima |
| Imóvel | Informar Economia | `economia_informar.jsp`, `inserir_economia_popup.jsp`, `atualizar_economia_popup.jsp` | `EconomiaPopupActionForm` | Média |
| Imóvel | Alterar Inscrição | `imovel_alterar_inscricao.jsp`, `alteracao_inscricao_imovel_{filtrar,autorizar}.jsp` | `AlterarImovelInscricaoAction` | Média |
| Imóvel | Consultar Relação Cliente e Imóvel; Fim de relação | `relacao_cliente_imovel_*.jsp`, `imovel_manter_fim_relacao_cliente.jsp` | `ConsultarRelacaoClienteImovelActionForm`, `FimRelacaoClienteImovelActionForm` | Média |
| Imóvel | Situação do Imóvel (catálogo), Doação mensal, Programa especial, Situação de cobrança, Transferir imóveis de logradouro | `imovel_inserir_situacao.jsp`, `imovel_doacao_*`, `imovel_programa_especial_*`, `imovel_situacao_cobranca_*`, `imovel_transferir_logradouro.jsp` | forms próprios | Baixa/Média |
| Cliente | **Inserir Cliente** (4 etapas) / Manter / Consultar / Filtrar | `cadastro/cliente/cliente_{inserir,atualizar}_{nome_tipo,pessoa_fisica,pessoa_juridica,endereco,telefone}.jsp`, `cliente_filtrar.jsp`, `cliente_outros_criterios_filtrar.jsp`, `cliente_consultar.jsp`, `cliente_resultado_pesquisa.jsp` | `ClienteActionForm` (validator l.691), `InserirClienteWizardAction` | Altíssima |
| Cliente (catálogos) | Tipo de cliente, Ramo de atividade, "descrição genérica" (lista de nomes proibidos) | `cliente_tipo_*.jsp`, `ramo_atividade_*.jsp`, `descricao_generica_informar.jsp` | | Média |
| Localização | Gerência Regional, Unidade de Negócio, **Localidade, Setor Comercial, Quadra (+Face)** | `cadastro/localidade/*.jsp` | `WEB-INF/cadastro/localidade/*.xml` | Média (configuração inicial) |
| Localização | **Rota** (fica em Micromedição no código) | `micromedicao/rota_{inserir,atualizar,filtrar,manter}.jsp` | `InserirRotaActionForm` | Média |
| Endereço | **Logradouro, Bairro, Município, CEP**, popup "Informar Endereço" | `cadastro/endereco/*.jsp`, `cadastro/geografico/*.jsp`, `cadastro/cep_*.jsp`, `endereco_inserir.jsp` | | Média; o popup é usado em todo cadastro |
| Categoria e Subcategoria | Categoria, Subcategoria | `categoria_{inserir,atualizar,filtrar,manter}.jsp`, `inserir_subcategoria.jsp` e correlatos | `CategoriaActionForm`, `InserirSubcategoriaActionForm` | Baixa (configuração) |
| Imóvel (catálogo) | **Perfil do Imóvel** | `imovel_perfil_*.jsp` | `InserirImovelPerfilActionForm` | Baixa |
| Tarifa Social | Inserir/Manter Tarifa Social, Tipo de cartão, Excluir | `cadastro/tarifasocial/*.jsp` (27 arquivos) | `struts-config-InserirTarifaSocial*`, `WEB-INF/tarifaSocial/` | Média |
| Micromedição > Hidrômetro | **Inserir (lote), Manter, Movimentar, Histórico de instalação**; catálogos (marca, capacidade, diâmetro, local de armazenagem) | `micromedicao/hidrometro/*.jsp` | `WEB-INF/micromedicao/*.xml` | Média/Alta (almoxarifado) |
| Atendimento (ligações) | **Efetuar Ligação de Água/Esgoto, Atualizar, Corte, Supressão, Religação, Restabelecimento, Substituição/Instalação/Retirada de Hidrômetro** | `atendimentopublico/ligacaoagua/*.jsp`, `ligacaoesgoto/*.jsp`, `hidrometro/*.jsp` | `WEB-INF/atendimentopublico/**` | Altíssima (campo/OS) |
| Atendimento (catálogos) | Situação de ligação água/esgoto, perfil de ligação esgoto, esgotamento, motivo/tipo de corte | `ligacao_agua_situacao_*.jsp`, `ligacao_esgoto_*` | | Baixa |
| Recadastramento | Gerar arquivo texto de campo, Carregar retorno, Filtrar/Consultar atualizações, Aprovar/Fiscalizar, Situação de transmissão, versão simplificada | `cadastro/arquivo_texto_dados_cadastrais_gerar.jsp`, `carregar_dados_atualizacao_cadastral.jsp`, `cadastro/atualizacaocadastral/*.jsp`, `atualizacaocadastralsimplificado/*.jsp` | `WEB-INF/cadastro/atualizacaocadastral/*.xml` | Média (campanhas) |

**Observação de arquitetura de tela** [FATO]: o padrão do GSAN é **Filtrar > Manter (lista) > Atualizar** para cada cadastro, com a caixa "Atualizar" no filtro decidindo se o resultado leva à consulta ou à manutenção. Códigos digitados com **Enter** disparam a busca da descrição (campo ao lado, somente leitura), e uma lupa abre popup de pesquisa.

---

## 2. Formulários, tela a tela

Legenda das colunas: **Tipo/máscara** descreve o controle e o limite de caracteres do GSAN; **Obrig.** = S, N, ou "C" (condicional, com a condição na coluna Validação).

### T1. Inserir Imóvel (assistente em 7 etapas) e Atualizar Imóvel

Fluxo [FATO, `struts-config.xml` l.1029 a 1057]: Localidade (aba 1) > Endereço > Imóvel principal > Cliente > Subcategoria > Características > Conclusão. O servidor valida **cada aba ao avançar** (`ControladorImovelSEJB.validarImovelAbaLocalidade/Endereco/Cliente/Caracteristicas/Conclusao`). A matrícula **não é digitada** na inclusão: é atribuída ao gravar [INFERÊNCIA: não há campo de matrícula nas abas]. Há um link de ajuda por aba.

**T1.1 Aba "Localidade" (localização e inscrição)** — `imovel_inserir_localidade.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Localidade | numérico, 3; completa com zeros à esquerda ao gravar; lupa e Enter buscam o nome | S | catálogo de localidades | Deve existir; se `bloqueio de inserção` ligado, só passa com permissão especial (mensagens distintas para inclusão e alteração) | Troca de localidade limpa setor e quadra (cascata) |
| Setor comercial | numérico, 3 | S | setores da localidade | Deve existir naquela localidade; bloqueio idem; **setor "alternativo" ativo não pode ser informado** | Popup de pesquisa recebe a localidade já digitada |
| Quadra | numérico, tamanho pelo parâmetro de dígitos da quadra; valida ao sair do campo | S | quadras do setor | Deve existir no setor; bloqueio idem; na saída do campo exibe mensagem com dados da quadra | |
| Face da quadra | select | C | faces da quadra | Obrigatória só se o tenant usa "face de quadra" (parâmetro do sistema) | Com face, rede de água/esgoto e distrito passam a vir da face |
| Lote | numérico, 4; zeros à esquerda | S | | | |
| Sublote | numérico, 3; zeros à esquerda | S | | **Inscrição duplicada** (localidade+setor+quadra+lote+sublote) bloqueia, citando a matrícula existente; na alteração, ignora o próprio imóvel | |
| Testada do lote | numérico, 4 | N | | | |
| Sequência na rota | numérico, 4 | C | | Obrigatória quando a rota está habilitada; **o validator só a exige para duas empresas fixas no código** (flag por concessionária). Se outro imóvel já ocupa a sequência, pede **confirmação** (não bloqueia) | Regra por empresa = configuração por tenant no SIGSAN |

Alterações na "Atualizar": mostra **Matrícula** (somente leitura), lote/sublote/testada/sequência só aceitam dígitos, e a validação extra [FS0037] avisa se o imóvel está **em processo de faturamento**.

**T1.2 Aba "Endereço"** — `imovel_inserir_endereco.jsp` + popup `endereco_inserir.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Endereço do imóvel | lista com botão Adicionar (abre popup) | S | | **Exatamente um** endereço (zero ou mais de um dá erro) | |
| (popup) Tipo de endereço | select | N | catálogo de tipo de endereço | | |
| (popup) CEP | 8 dígitos; lupa de consulta de CEP | N | CEPs cadastrados | Ao informar, preenche município, logradouro e bairro | |
| (popup) Logradouro | código numérico, 9 | C | logradouros | Se sem CEP, informa o código do logradouro | |
| (popup) Bairro | select | N | bairros do logradouro | | |
| (popup) Referência | select | N | tipos de ponto de referência | | |
| (popup) Número do imóvel | texto, 5 | S | | | |
| (popup) Complemento | texto, 25 | N | | | |

Regras da aba [FATO, `validarImovelAbaEndereco`]: (a) se o **município do endereço difere do município do setor**, exige permissão especial (uma para inserir, outra para manter); (b) se o município está marcado como "usa relação quadra-bairro", o **bairro do logradouro tem de coincidir com o bairro da quadra**, e a quadra precisa ter bairro.

**T1.3 Aba "Imóvel principal"** — `imovel_inserir_principal.jsp`: um campo **Imóvel** (matrícula, numérico 9, busca por Enter) e a lista de endereços. Regra [FS0015]: o imóvel principal tem de estar na **mesma quadra**. Na tela "Conclusão" o mesmo campo reaparece. Opcional.

**T1.4 Aba "Cliente"** — `imovel_inserir_cliente.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Código do cliente | numérico, 9; Enter busca nome (somente leitura) | S | clientes | Deve existir | Botão "Inserir Cliente" abre o cadastro em popup |
| Tipo do cliente (na relação) | select | S | Proprietário / Usuário / Responsável | **Ao menos um cliente tipo Usuário**; documento (CPF/CNPJ) do Usuário obrigatório, exceto com permissão especial ou parâmetro "documento obrigatório = não" | |
| Data início da relação | dd/mm/aaaa | S (só na atualização) | | | |
| Nome do imóvel | texto, 30 | N (só na atualização) | | | |
| Nome na conta | radio por linha | S | uma linha | Com um único cliente, ele é o "nome na conta" | |
| Lista de clientes informados | tabela | | | Colunas: nome na conta, código, nome, tipo, CPF/CNPJ | |

**T1.5 Aba "Subcategoria"** — `imovel_inserir_sub_categoria.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Categoria | select | S | catálogo | Categoria marcada "exige permissão especial" (ex.: órgão público) bloqueia sem a permissão | Troca recarrega subcategorias |
| Subcategoria | select | S | ativas da categoria | Repetida bloqueia | |
| Quantidade de economias | numérico, 4 | S | | Acima do limite de validação da categoria: **pede confirmação** | |
| Qtd. unidades privativas / coletivas | numérico, 4 cada | N | | | |
| Ramo de atividade | select + Adicionar | N | catálogo | Repetido bloqueia | Lista própria de ramos |

Regras [FATO]: ao menos uma subcategoria; a **categoria principal** e a **subcategoria principal** do imóvel são **derivadas** das subcategorias informadas; se o **perfil** do imóvel está atrelado a uma subcategoria, o imóvel só pode ter **essa** subcategoria (`InserirImovelAction` l.378 a 405).

**T1.6 Aba "Características"** — `imovel_inserir_caracteristicas.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Área construída | decimal pt-BR (máscara monetária), 10; unidade m² | S | | Informar o valor **ou** a faixa; digitado o valor, a faixa é **derivada** e o select fica travado | Faixas em catálogo |
| Volume reservatório inferior | decimal, 6; m³ | N | faixa derivada | idem | |
| Volume reservatório superior | decimal, 6; m³ | N | faixa derivada | idem | |
| Volume piscina | decimal, 6; m³ | N | faixa derivada | idem | |
| Jardim | radio Sim/Não | N | | | |
| Pavimento da calçada | select | S | catálogo | | |
| Pavimento da rua | select | S | catálogo | | |
| Fonte de abastecimento | select | S | catálogo | Habilita/desabilita "Poço" conforme a fonte | |
| Situação ligação de água | select | S | catálogo com flags | **Compatível com a rede da quadra**: quadra sem rede só admite situação sem rede e sem ligação; rede parcial só admite sem ligação (R1 do relatório `01`, [FS008]) | |
| Situação ligação de esgoto | select | S | catálogo com flags | idem para esgoto [FS009] | Muda a obrigatoriedade de "Esgotamento" |
| Esgotamento | select | C | catálogo | Obrigatório se a situação de esgoto **fatura** | |
| Perfil do imóvel | select | S | catálogo | Setor 999 + quadra 999 só aceita o perfil "cadastro provisório" [FS0023] | |
| Existe nível para instalação de esgoto | radio | C | | Aparece conforme a configuração | |
| Poço | select | C | tipos de poço | Habilitado conforme a fonte | |
| Tipo de despejo | select | N | catálogo | | |
| Tipo de habitação / propriedade / construção / cobertura | 4 selects | N | catálogos | | |

**T1.7 Aba "Conclusão"** — `imovel_inserir_conclusao.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Número de pontos | numérico, 4 | S na tela (marca "(*)") | | Só dígitos | |
| Número de moradores | numérico, 4 | S na tela | | Só dígitos | Dado pessoal indireto (seção 5) |
| Número de IPTU | numérico, 20 | N | | **Único** por município, dizendo a matrícula que já o usa; travado se o imóvel está em tarifa social | |
| Contrato companhia de energia | numérico, 10 | N | | **Único**; travado em tarifa social | |
| Medidor de energia | texto, 10 | N | | **Único** | |
| Data da visita comercial | dd/mm/aaaa, máscara | N | | Data válida | |
| Envio da conta | select | C | catálogo | **Obrigatório quando há cliente do tipo Responsável**, opcional nos demais casos; se for envio por e-mail (duas opções), o cliente do "nome na conta" **precisa ter e-mail** | |
| Extrato para responsável | radio Emitir/Não emitir | C | | Aparece quando o envio é "listar"; bloqueado em certos casos | |
| Quadra de entrega | numérico, 4 | C | | | |
| Código da rota de entrega | numérico, 4, somente leitura | C | | Rota deve estar ativa e **não** pode ser alternativa | |
| Sequencial da rota de entrega | numérico, 4 | C | | | |
| Código da rota alternativa | numérico, 4 | C | | Só se a rota informada for alternativa | |
| Coordenada X / Y | decimal, 30 (UTM, texto) | N | | **Ambas ou nenhuma** | Rótulos na consulta dizem "Latitude/Longitude" para os mesmos campos: inconsistência |
| Botão GIS | integração AcquaGIS | | | | Captura coordenada no mapa |
| Imóvel principal | matrícula, 9 | N | | Mesma quadra | |
| Funcionário | código, 9 + nome (somente leitura) | N | | Deve existir | |
| Informações complementares | área de texto, 750, contador | N | | | |

**T1.8 Efeitos de gravar o imóvel** [FATO, `InserirImovelAction`, `ControladorImovelSEJB.inserirImovelRetorno` conforme `01` seção 5.9]: indicadores nascem "ativo"/"não condomínio"; lote automático se a quadra tem incremento; imóvel inserido em abrangência do usuário; auditoria registrada.

### T2. Consultar Imóvel (atendimento)

Cabeçalho comum a todas as abas: **Imóvel** (matrícula, 9, Enter busca; aviso "(Excluído)" quando excluído), **Situação de água**, **Situação de esgoto**, **Endereço**. As 10 abas [FATO, `struts-config-ConsultarImovel.xml`]: Dados cadastrais | Dados complementares | Análise de medição e consumo | Histórico de faturamento | Débitos | Pagamentos | Devoluções | Documentos de cobrança | Parcelamentos | Registros de atendimento.

| Aba | Conteúdo (campos exibidos, todos somente leitura) |
|---|---|
| **Dados cadastrais** | Município; tabela de **clientes** (nome, tipo da relação, data início, telefone, CPF/CNPJ); **categorias/subcategorias/economias** com total; perfil; tipo de despejo; área construída; testada; volumes (inferior, superior, piscina); fonte de abastecimento; poço; **distrito de abastecimento**; **divisão de esgoto**; pavimento de rua e calçada; pontos de utilização; moradores; IPTU; contrato de energia; coordenadas; ocorrência de cadastro; anormalidade da localidade pólo; indicador e matrícula de condomínio; matrícula do imóvel principal; nível de esgoto; jardim; tipo de habitação/propriedade/construção/cobertura; data de processamento |
| **Dados complementares** | Tarifa de consumo; quantidade de retificações, parcelamentos e reparcelamentos; funcionário responsável; informações complementares; **situações de cobrança**; negativações; **vencimentos alternativos**; **débito automático** (banco, agência, identificação do cliente no banco); ocorrências de cadastro com **fotos**; anormalidades da localidade pólo; **situações especiais de faturamento e cobrança**; ramos de atividade; **dados de contrato** (número, tipo, datas, valor de tarifa, consumo contratado, matrículas associadas) |
| **Análise de medição e consumo** | Grupo de faturamento, dia de vencimento, mês/ano de faturamento, empresa leiturista, rota, sequencial; **ligação de água** (datas de ligação, corte, religação, supressão, restabelecimento; diâmetro, material, perfil, consumo mínimo); **hidrômetro da ligação** (tipo de medição, número, instalação, capacidade, tipo, marca, local, diâmetro, proteção, leitura de instalação, cavalete, ano de fabricação, relojoaria, usuário que instalou, lacre); **ligação de esgoto** (data, diâmetro, material, perfil, consumo mínimo, % esgoto, % coleta, poço, condição de esgotamento, caixa de inspeção, destino dos dejetos, destino das águas pluviais); **hidrômetro do poço** |

### T3. Filtrar, Pesquisar e Manter Imóvel

**Filtro rápido** — `imovel_filtrar.jsp` (todos opcionais):

| Campo | Tipo/máscara | Obrig. | Observações |
|---|---|---|---|
| Atualizar (caixa) | checkbox | N | Decide se o resultado abre manutenção ou consulta |
| Matrícula | numérico, 10 | N | |
| Localidade / Setor / Quadra / Lote / Sublote | 3 / 3 / 4 / 4 / 3 | N | Setor depende da localidade; descrição ao lado |
| Cliente | código, 9 + nome | N | |
| Município / Bairro / Logradouro | 4 / 4 / 9, cada um com descrição | N | Cascata: trocar o pai limpa os filhos |
| Número do imóvel | intervalo inicial/final, 5 (final replica o inicial) | N | |
| CEP | 8 | N | |

**Pesquisa popup** (`imovel_pesquisar.jsp`) usa o mesmo conjunto (sem a caixa Atualizar) e devolve ao chamador. **Colunas do resultado** [FATO, `imovel_resultado_pesquisa.jsp`]: **Matrícula, Cliente, Endereço**. **Lista de manutenção** (`imovel_manter.jsp`): Matrícula, Endereço do imóvel, seleção em lote.

**"Outros critérios"** (assistente de 5 abas, `FiltrarImovelOutrosCriteriosWizardAction`, usado para relatórios e listas): [FATO]
- **Localização**: gerência regional, unidade de negócio, inscrição inicial e final (localidade, setor, quadra, lote), município, bairro, logradouro, CEP, rota e sequência inicial/final, ordenação alternativa.
- **Cliente**: cliente, tipo da relação, tipo do cliente, CPF/CNPJ informado/não informado/todos, imóvel condomínio, imóvel principal.
- **Características**: perfil, categoria, subcategoria, intervalo de economias, de pontos, de moradores, de área construída (valor ou faixa), tipo de poço.
- **Água/esgoto/consumo**: situação de água, intervalo de consumo mínimo fixado, situação de esgoto, intervalo de % de esgoto e de consumo mínimo de esgoto, indicador e tipo de medição, intervalo de média mínima do imóvel e do hidrômetro.
- **Faturamento/cobrança**: situação especial de faturamento, situação especial de cobrança, situação de cobrança, dia de vencimento alternativo, anormalidade da localidade pólo, ocorrência de cadastro, tarifa de consumo.

### T4. Cliente (Inserir, Manter, Consultar, Filtrar)

Assistente de 4 etapas [FATO, `cliente_inserir_*.jsp`, `ClienteActionForm`]. Também abre em **popup** dentro do cadastro do imóvel (modo `POPUP=true`, sem abas de histórico).

**T4.1 Etapa "Nome e tipo"**

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| Nome | texto, 50 | S | | Sem caracteres especiais. Parâmetros por tenant: bloquear nome com **menos de 10 caracteres** (sem espaços) e **nome genérico** (lista cadastrada), ambos liberáveis por permissão especial | |
| Nome abreviado | texto, 40 | N | | | Derivável |
| Nome exibido na conta | select | S (se o tenant usa nome fantasia) | Nome fantasia / Nome na Receita | | |
| Tipo de pessoa | radio | S | Física / Jurídica | Trocar recarrega a etapa seguinte | |
| Tipo do cliente | select | S | catálogo filtrado por pessoa; cada tipo tem esfera de poder (municipal, estadual, federal, privada) | | |
| E-mail | texto, 40 | N | | Formato de e-mail | Um só |
| Dia do vencimento da conta | numérico, 2 | N | 1 a 31 [INFERÊNCIA] | Edição só com permissão; senão somente leitura | |
| Vencimento para o mês seguinte | radio | N | | | |
| Permite fatura antecipada | radio | S | | | |
| Cliente bloqueado para negativação | radio | N | | Só com permissão especial | |

**T4.2 Etapa "Pessoa física"**

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| CPF | só dígitos, 11, **sem máscara** | C | | Dígito verificador; **obrigatório salvo permissão especial** de incluir sem CPF; **duplicidade** (mesmo CPF, ou mesmo RG+órgão+UF) devolve o cliente existente (parâmetro por tenant) | |
| RG | só dígitos, 13 | N | | | |
| Data de emissão | dd/mm/aaaa, máscara | N | | | |
| Órgão expedidor | select | N | catálogo | | |
| UF do RG | select | N | UFs | | |
| Data de nascimento | dd/mm/aaaa | N | | Data válida | |
| Profissão | select | N | catálogo | | |
| Sexo | select | S | catálogo | **Obrigatório para PF** | |
| Nome da mãe | texto, 50 | N | | | |

**T4.3 Etapa "Pessoa jurídica"**

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra | Observações |
|---|---|---|---|---|---|
| CNPJ | só dígitos, 14 | N (o código tem a obrigatoriedade comentada) | | Dígito verificador | Só numérico |
| Ramo de atividade | select | N | catálogo | | |
| Cliente responsável superior | código, 9 + nome | N | clientes | Deve existir | Hierarquia entre clientes |

**T4.4 Etapa "Endereço"**: ao menos **um**; o botão Adicionar abre o popup de endereço (T10.5); um endereço é marcado como **correspondência** (radio); lista com tipo, correspondência e endereço formatado; remoção por seleção.

**T4.5 Etapa "Telefone"**

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra |
|---|---|---|---|---|
| Tipo de telefone | select | S | Residencial, Comercial, Celular, Fax (catálogo) | |
| Município | código, 4 | N | | Auxilia o DDD [INFERÊNCIA] |
| DDD | numérico, 2 | S | | |
| Número | numérico, 9 | S | | Duplicado avisa |
| Ramal | numérico, 4 | N | | |
| Nome do contato | texto, 50 | N | | |
| Principal | radio na lista | N | | Um por cliente |

**T4.6 Filtrar cliente** (`cliente_filtrar.jsp`): CPF (11), RG (13), CNPJ (14), código (9), **nome** (50, "iniciando pelo texto" ou "contendo o texto"), **nome da mãe** (idem), município, bairro, logradouro, CEP, esfera de poder, indicador de uso (ativo/inativo/todos). **Outros critérios** (`cliente_outros_criterios_filtrar.jsp`) acrescenta: nome abreviado, matrícula do imóvel, tipo de cliente, ramo, data de emissão, órgão emissor, responsável superior, data de nascimento, profissão, sexo, e-mail. **Colunas do resultado**: código, nome, tipo de cliente, CPF, CNPJ.

**T4.7 Consultar cliente** (`cliente_consultar.jsp`): código, nome, abreviado, dia de vencimento, tipo, e-mail, **"executa ações de cobrança nos imóveis em que é responsável"**, CPF, RG, emissão, órgão/UF, nascimento, sexo, profissão, CNPJ, ramo, tabela de endereços (tipo, correspondência), tabela de telefones (tipo, principal).

### T5. Vínculo cliente-imóvel

Não é tela única: vive na **aba Cliente** do imóvel (T1.4), no **popup de economia** (cliente por economia: código, tipo de relação, data de início) e em duas telas próprias. [FATO]

| Tela | Campos | Regras |
|---|---|---|
| **Fim de relação** (`imovel_manter_fim_relacao_cliente.jsp`) | Lista dos clientes selecionados (nome, tipo, CPF/CNPJ); **Data de término da relação (S)**; **Motivo** (catálogo) | Fecha a relação com motivo e data; não apaga. O usuário anterior continua consultável por data |
| **Filtrar relação cliente-imóvel** (`relacao_cliente_imovel_consulta.jsp`) | Matrícula do imóvel; código do cliente; tipo da relação; período de início da relação (de/até); **situação da relação (vigente/encerrada/todos)**; período de fim; motivo do fim | Consulta temporal. Informar imóvel limpa cliente e vice-versa [INFERÊNCIA pelo script] |

Tipos de relação [FATO, `01` seção 5.3]: Proprietário, Usuário, Responsável. Motivos de fim têm ids com efeito de negócio (exclusão do imóvel, saída de programa especial, atualização cadastral).

### T6. Ligação de água

Todas as operações abaixo **exigem uma Ordem de Serviço** (campo "Ordem de Serviço (S)" que traz o imóvel), exceto as variantes "sem RA", que pedem a **matrícula**. O efeito comercial é gerado pela execução da OS. [FATO]

**T6.1 Efetuar ligação de água (com instalação de hidrômetro, sem RA)** — `ligacao_agua_com_instalacao_hidrometro_efetuar_sem_ra.jsp`

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra |
|---|---|---|---|---|
| Matrícula do imóvel | numérico, 9; Enter traz inscrição, cliente usuário, CPF/CNPJ, situações | S | | |
| Data da ligação | dd/mm/aaaa, máscara | S | | |
| Diâmetro da ligação | select | S | catálogo | |
| Material da ligação | select | S | catálogo | |
| Perfil da ligação | select | S | catálogo | O perfil precisa ter **tarifa de consumo** associada |
| Local de instalação do ramal | select | N | catálogo | |
| Existe lacre | radio Sim/Não | N | | Habilita o número do lacre |
| Número do lacre | numérico, 9 | C | | |
| (bloco hidrômetro) Número do hidrômetro | texto, 10; Enter busca | N | hidrômetros **disponíveis** [INFERÊNCIA] | |
| Data da instalação | dd/mm/aaaa | S | | |
| Local de instalação | select | S | catálogo | |
| Proteção | select | S | catálogo (caixa de proteção) | |
| Leitura de instalação | numérico, 6 | S | | |
| Número do selo | texto, 12 | N | | |
| Cavalete | radio COM/SEM | S | | |
| Número do lacre do hidrômetro | numérico, 9 | N | | |

Regras da execução [FATO, `ControladorAtendimentoPublicoSEJB`]: só pode ligar imóvel cuja situação de água é **Potencial, Factível ou Em fiscalização**; a quadra não pode ser **sem rede**; o imóvel não pode estar excluído; o controlador chama uma verificação "imóvel em campo" antes de gravar (provável bloqueio durante recadastramento [INFERÊNCIA pelo nome do método `validarImovelEmCampo`]); ao efetuar, a situação passa a **Ligado**, e uma ligação de esgoto "fora de uso" passa a **Ligado**; o consumo mínimo da ligação é validado; se a OS tem tipo de débito, **gera débito** (valor, motivo de não cobrança, percentual 100/70/50, parcelas).

**T6.2 Atualizar ligação de água** — mesmos campos (data da ligação (S), diâmetro, material, perfil, ramal, lacre) mais **Ligação de origem** (select); e, **se a ligação está cortada**: motivo do corte, tipo do corte, número do selo de corte (S), leitura de corte; **se suprimida**: motivo, tipo e selo de supressão (S), leitura de supressão. Campos de corte/supressão ficam desabilitados fora do respectivo estado.

**T6.3 Corte** (`ligacao_agua_corte_efetuar.jsp`): data do corte (somente leitura, hoje), **motivo (S)**, **tipo (S)**, leitura do corte (6), selo do corte (8); bloco de débito. **Supressão** (`..._supressao_efetuar.jsp`): data (somente leitura), motivo (S), **tipo: Total ou Parcial (S)** + tipo de supressão, leitura (6), selo (8); bloco de débito. **Religação / Restabelecimento**: telas análogas; a religação só vale para ligação **cortada**.

### T7. Ligação de esgoto

`ligacao_esgoto_sem_ra_efetuar.jsp` e `ligacao_esgoto_atualizar.jsp` [FATO]:

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra |
|---|---|---|---|---|
| Matrícula (ou OS) | numérico, 9 | S | | |
| Data da ligação | dd/mm/aaaa | S | | |
| Diâmetro | select | S | catálogo | |
| Material | select | S | catálogo | |
| Perfil da ligação | select; troca recarrega a tela | S | catálogo | O perfil define o **% de esgoto** |
| Percentual de coleta | decimal, 6 | S | | Nasce 100,00; **editável só com permissão especial** |
| Percentual de esgoto | decimal, somente leitura | | derivado do perfil | |
| Com caixa de gordura | radio Sim/Não | S | | |
| Ligação | radio **Disponível / Efetivado** | S | | Distingue "tem rede disponível" de "ligado" |
| Ligação de origem | select (só atualizar) | N | catálogo | |

Complementares em telas próprias: **volume mínimo** da ligação, **mudança de situação de faturamento** da ligação de esgoto. Na aba Características do imóvel entram ainda esgotamento, destino de dejetos, caixa de inspeção e águas pluviais (T1.6 e consulta T2).

### T8. Hidrômetro

**T8.1 Inserir hidrômetro (cadastro em lote)** — `micromedicao/hidrometro/hidrometro_inserir.jsp` [FATO]

| Campo | Tipo/máscara | Obrig. | Domínio | Validação / regra |
|---|---|---|---|---|
| Numeração: Fixo | texto, 4 | S | | Prefixo comum; ligado ao ano de fabricação (valida ao sair) |
| Numeração: Faixa inicial e final | numérico, 7 cada | S | | **Gera N hidrômetros de uma vez** |
| Capacidade | select; troca recarrega | S | catálogo (com faixa de dígitos) | |
| Ano de fabricação | aaaa, 4, somente leitura | S | | Derivado do prefixo [INFERÊNCIA] |
| Marca | select | S | catálogo (com validade de revisão em dias) | |
| Data de aquisição | dd/mm/aaaa | S | | |
| Finalidade | radio Comercial / Operacional | S | | Operacional = macromedidor |
| Classe metrológica | select | S | catálogo | |
| Diâmetro | select | S | catálogo | |
| Número de dígitos | select | S | depende da capacidade | |
| Tipo de fluxo | select | S | catálogo | |
| Tipo de relojoaria | select | N | catálogo | |
| Local de armazenagem | código, 3 + nome | S | catálogo (com "é oficina") | |
| Vazão de transição, nominal e mínima | decimal, 6 | N | | |
| Nota fiscal | texto, 9 | N | | |
| Tempo de garantia (anos) | numérico, 4 | N | | |

**T8.2 Filtrar/Manter**: os mesmos campos, mais **Situação** (Instalado, Em manutenção, Disponível, Roubado), e, com situação "instalado", **localidade (S), setor, quadra**. **Movimentar**: seleciona hidrômetros do filtro e informa origem, destino, motivo e parecer (campos vistos só pelo relatório `01`). **Histórico de instalação**: por hidrômetro ou por imóvel.

**T8.3 Instalação, substituição e retirada** (por OS) [FATO]:
- **Substituição**: hidrômetro atual (somente leitura), tipo de medição (ligação de água ou poço), data da retirada, **leitura de retirada (S, 6)**, **situação do hidrômetro retirado (S)** e **local de armazenagem (S)** (habilitado conforme a situação), **número do novo hidrômetro (S, 10)**, data da instalação, local de instalação (S), proteção (S), **troca de proteção** (Sim/Não), **troca de registro** (Sim/Não), leitura de instalação, selo (12), **cavalete COM/SEM (S)**, bloco de débito.
- **Retirada**: hidrômetro e tipo de medição (leitura), data, leitura da retirada, situação (S), local de armazenagem (S), bloco de débito.
- **Instalação** no imóvel já ligado: mesmos campos de instalação da T6.1.

**T8.4 Marca** (`hidrometro_marca_inserir.jsp`): descrição (S, 30), descrição abreviada (3), **validade da revisão em dias (S)**, código da marca (S, 1 caractere). Demais catálogos (capacidade, diâmetro, local de armazenagem, classe, proteção, local de instalação, motivo de baixa) seguem o padrão catálogo da T11.

### T9. Estrutura territorial e rota

| Tela | Campos [FATO] | Regras |
|---|---|---|
| **Gerência regional** | nome (S, 25), nome abreviado (S, 3), CNPJ (14), **endereço (S)**, telefone (9), ramal (4), fax (9), e-mail (40), gerente (cliente, 9) | |
| **Unidade de negócio** | nome (S, 50), nome abreviado (S, 4), CNPJ (14), gerente (cliente), **gerência regional (S)** | |
| **Localidade** | código (S, 3, só dígitos), nome (S, 30), endereço, telefone, ramal, fax, e-mail, menor consumo (6), **unidade de negócio (S)**, localidade pólo, **classe (S)**, **porte (S)**, local de armazenagem de hidrômetro, ICMS, centro de custo, centro de custo de esgoto, **informatizada (S)**, gerente (cliente), **sede (S)**, município principal | Troca da unidade recarrega as localidades pólo; marcar "sede" valida unicidade da sede |
| **Setor comercial** | **localidade (S)**, código (S, 3), nome (S, 30), **município (S)**, **setor alternativo (S)**, fontes de captação (lista) | Setor alternativo ativo **não aceita imóvel novo** |
| **Quadra** | **localidade (S)**, **setor (S)**, **número da quadra (S)**, **rota (S)**, bairro, incrementar lote (Sim/Não), tipo da área, **perfil da quadra (S)**, **rede de água** (sem, com, parcial), **rede de esgoto** (sem, com, parcial), **sistema de esgoto (S)**, **bacia (S)**, **distrito operacional (S)**, setor censitário IBGE, ZEIS, **faces (S)** | Distrito só se há rede de água; sistema/bacia só se há rede de esgoto |
| **Face da quadra** (popup) | número (S, 3), rede de água, rede de esgoto, grau de dificuldade de execução, grau de risco à segurança física, **condição de abastecimento: nível de pressão e grau de intermitência**, sistema de esgoto, bacia, distrito operacional | Esta tela já carrega dados de **pressão e intermitência**: ponte natural para o módulo de perdas |
| **Rota** | **localidade (S)**, **setor (S)**, **código da rota (S, 4)**, **grupo de faturamento (S)**, **grupo de cobrança (S)**, **tipo de leitura (S)**, **limite de imóveis por rota (S)**, **empresa de leitura (S)**, **empresa de cobrança (S)**, **empresa de entrega das contas (S)**, fiscaliza cortados na leitura, fiscaliza suprimidos, **ajuste de consumo** + dias, **rota alternativa**, **transmissão off-line**, **sequencial de leitura**, **impressão térmica no fim do grupo**, **gera faixa falsa** + percentual, **gera fiscalização de leitura** + percentual, leiturista, **critérios de cobrança da rota** (ação x critério) | Vários radios (S); percentuais habilitam por radio |

### T10. Endereço: logradouro, bairro, município, CEP

| Tela | Campos [FATO] | Regras |
|---|---|---|
| **Logradouro** | tipo (S), título, nome (S, 40), nome popular (30), **município (S)**, **bairros (S, ao menos 1)**, **CEPs (S, ao menos 1)** | N bairros e N CEPs por logradouro |
| **Bairro** | **município (S)**, código do bairro (S, 4), nome (S, 30), código na prefeitura (4), **áreas do bairro (S)** (nome da área + distrito operacional) | |
| **Município** | código (S, 4), nome (S, 30), **DDD (S, 3)**, **UF (S)**, **microrregião (S)**, **região de integração (S)**, **CEP inicial e final (S)**, início e fim da concessão, **código IBGE (10)**, "usa relação quadra-bairro" | O código IBGE é campo à parte; o código "próprio" de 4 dígitos é o que as telas usam |
| **CEP** | tipo (S), **CEP (S, máscara `##.###-###`)**, município (S), bairro (S), tipo de logradouro (S), nome do logradouro (S, 50) | |
| **Popup "Informar endereço"** | tipo de endereço, CEP + consulta, município/UF/logradouro/bairro (preenchidos), referência, **número (S, 5)**, complemento (25) | Usado por imóvel, cliente, localidade, gerência |

### T11. Catálogos de cadastro (padrão "Inserir / Filtrar / Manter / Atualizar")

| Catálogo | Campos [FATO] |
|---|---|
| **Categoria** | descrição (S, 15), descrição abreviada (S, 3), **tipo da categoria (S)**, **consumo mínimo (S, 3)**, **estouro**: consumo de referência (S, 4, m³) e fator sobre a média (S); **baixo consumo**: média de referência (S, 4) e percentual da média (S); **alto consumo**: consumo de referência (S, 4) e fator (S) |
| **Subcategoria** | **categoria (S)**, código (S, 4), descrição (S, 50), abreviada (20), código da tarifa social (1), código do grupo (4), **fator de fiscalização (S, 2)**, indicador de tarifa de consumo, **indicador de sazonalidade de abastecimento (S)** |
| **Perfil do imóvel** | descrição (S, 20), indicador de uso, **geração automática**, **inserir/manter perfil**, **gerar dados de leitura**, **bloquear retificação**, **grande consumidor**, **bloquear dados sociais**, **gerar débito na segunda via da conta** (todos radios Sim/Não) |
| **Situação da ligação de água** | descrição (S, 20), abreviada (S, 3), **consumo mínimo de faturamento (S)**, e os radios (S): **faturamento, existência de rede, existência de ligação, abastecimento, água ativa, água desligada, água cadastrada, análise de água** |
| **Situação da ligação de esgoto** | descrição (S), abreviada (S, 3), **volume mínimo (S)**, radios: **faturamento, existência de rede, existência de ligação** |
| **Situação do imóvel** (combinação) | tipo da situação do imóvel (S), situação de água (S), situação de esgoto (N): matriz que resolve a situação derivada |
| **Tipo de cartão (tarifa social)** | descrição (S, 20), abreviada (S, 2), tem validade, nº máximo de meses de adesão (2) |

### T12. Tarifa social

Fluxo [FATO, `struts-config.xml` e `tarifasocial/*.jsp`]: **(1) Imóvel**: informa o **Registro de Atendimento (S)**, a matrícula vem dele (a inclusão é **proibida sem RA**); **(2) Economias**: escolhe as economias (uma ou várias); **(3) Dados** por economia, em popup.

| Campo | Tipo/máscara | Obrig. | Validação / regra |
|---|---|---|---|
| Registro de atendimento | numérico, 9 | S | Deve estar associado ao imóvel e ser de tarifa social |
| Cliente usuário e proprietário | da relação do imóvel | S | Ambos obrigatórios; **RG/CPF configurados**; sem duplicidade de usuário |
| Cartão do programa social: número | numérico, 11 | N | **Duplicidade** de cartão bloqueia |
| Tipo do cartão | select | N | Catálogo; algumas têm validade |
| Data de validade | dd/mm/aaaa | N | Não pode ser anterior a hoje; formato válido |
| Nº de parcelas | numérico, 2 | N | |
| Nº de moradores | numérico, 2 | N | |
| Contrato companhia de eletricidade | numérico, 10 | N | **Único** |
| Consumo médio de energia | numérico, 5; kWh | N | Limite máximo vem de parâmetro do sistema |
| Número do IPTU | numérico, 19 | N | **Único** |
| Área construída (valor) e faixa | decimal, 9 | N | Limite máximo de área em parâmetro |
| Renda familiar: valor | decimal, 8 | N | |
| Renda familiar: tipo | select | N | Comprovada / Declarada |
| Cliente é usuário da tarifa social | marca | | |
| Motivo de revisão; motivo de exclusão no imóvel anterior | select | C | Obrigatório ao tirar de outro imóvel |

Datas da relação (início e fim) também são validadas (não futuras, fim posterior ao início, início posterior ao nascimento). **Excluir**: motivo (S), e o motivo pode **permitir ou não recadastramento**.

### T13. Atualização cadastral (recadastramento)

| Tela | Campos [FATO] | Observação |
|---|---|---|
| **Gerar arquivo de campo** | gerência regional, unidade de negócio, localidade/setor/rota/sequencial **inicial e final** (3 dígitos cada) | Imóveis passam a "em campo" e ficam **bloqueados** para ligação, entre outras operações |
| **Carregar retorno** | arquivo (S) | Devolve **relatório de inconsistências**: nome do arquivo, quantidade total, quantidade com erro, matrícula e descrição do erro |
| **Filtrar atualizações** | **empresa (S)**, agente cadastral, inscrição inicial e final (localidade, setor, rota), **exibir**: pendentes de aprovação, aprovados, aprovar em lote, todos; filtros Sim/Não/Todos para: sem ocorrência de cadastro, alteração de hidrômetro, de situação de água, de situação de esgoto, de categoria/subcategoria/economias | |
| **Consultar movimento** | lista: imóvel, agente cadastral, situação; marcações "novo imóvel" e "exclusão"; **aprovar em lote**, **gerar fichas de fiscalização**, **imprimir relatório** | |
| **Detalhe do imóvel** (popup) | imóvel, localidade, setor, quadra, situação; imagens carregadas (nome, download); **tabela de campos atualizados**: tabela, campo, **anterior**, **pré-aprovado**, **fiscalizado**, data/hora, usuário; seleção por registro | Botões: Aprovar, Fiscalizar, Concluir fiscalização. É a **aprovação campo a campo** |
| **Situação de transmissão** | por arquivo: imóveis na rota, transmitidos, aprovados, com anormalidade, com alteração de hidrômetro, de situação de água, de esgoto, de categoria/economias | Painel de campanha |
| **Simplificado** | comentário (S, 50), arquivo texto (S); lista de cargas (arquivo, comentário, usuário, data, detalhes) | Carga em lote com tabela de críticas |

### T14. Telas menores (resumo)

| Tela | Campos [FATO] |
|---|---|
| **Alterar inscrição** | origem (localidade S, setor, quadra, lote) e destino (idem): move imóveis em bloco. Segue para **autorização** (lista por quadra com quantidade e marcação) |
| **Programa especial** | matrícula (S, 9), observação (S, 200), data de apresentação dos documentos (S), número do Bolsa Família (11) |
| **Doação mensal** | imóvel (S), entidade beneficente (S), valor mensal (S), quantidade de parcelas |
| **Situação de cobrança do imóvel** | situação (S), escritório e advogado (quando a situação pede), data de implantação (S), cliente alvo (S), referências inicial e final `mm/aaaa` (S) |
| **Transferir imóveis de logradouro** | logradouro de origem (S) + seleção de imóveis; logradouro de destino (S), bairro, CEP |

---

## 3. Estado atual do Ziip SAN (o que existe hoje)

| Camada | O que há [FATO, conferido nesta leitura] |
|---|---|
| Banco | `V1_1__create_tenant_schema.sql`: `consumer_units` (`matricula VARCHAR(50)`, `dv CHAR(1)`, `owner_id` e `responsible_id` **FK para `users`**, `category_id`, `tariff_type_id`, `reading_route_id`, `meter_id` como UUID **sem FK**, `water_economies` e `sewage_economies INT`, `status VARCHAR(20)`, `water_connection_date`, `sewage_connection_date`, `address JSONB`, `coordinates GEOMETRY(POINT,4674)`, `reading_sequence INT`, `properties JSONB`); `addresses` polimórfica (`entity_type/entity_id`, `type`, `cep`, `street`, `number`, `complement`, `neighborhood`, `city`, `state`, `coordinates`, `is_primary`); `users`; `documents`; `service_orders`. Índice único `(tenant_id, matricula)`. RLS com FORCE e auditoria em todas |
| Helpers | `V1_2__tenant_table_helper.sql`: `register_tenant_table(tabela, entidade, auditoria)` aplica RLS, `version`, `tenant_schema` e trigger. Toda tabela nova deve chamá-la |
| Backend | **Nenhuma classe Java** referencia `consumer_units` além do provisionamento e do teste de instalação. `commercial/sigsan-*` são **pastas vazias** (sem POM). Não há controlador `/consumers` |
| Frontend | `frontend/src/pages/` contém **só `Consumers.tsx`**, mas `App.tsx` importa 18 páginas (`Login`, `Dashboard`, `Billing`...): o app **não compila** hoje. `Consumers.tsx` chama `GET /consumers?search&status`, mostra colunas `matricula, nome, cpf_cnpj, categoria, status` e tem os botões Ver/Editar/Excluir **sem ação**. As colunas `nome`, `cpf_cnpj` e `categoria` **não existem** em `consumer_units` |
| Modelo proposto | Seção 8 do relatório `01`: `customers`, `unit_customers`, `localities`/`commercial_sectors`/`blocks`/`block_faces`/`reading_routes`, `unit_subcategories`, `economies`, catálogos de situação, `water_connections`/`sewer_connections`, `meters`/`meter_installations`, `cadastral_survey_*`, `social_tariff_enrollments` |

---

## 4. Matriz de lacunas campo a campo

Colunas: **Existe?** SIM / PARCIAL / NÃO. **Onde**: tabela ou coluna do Ziip, ou a tabela do modelo proposto no relatório `01` seção 8 (marcada "`01§8`"). **Prio.**: P0 (cadastro mínimo operável), P1, P2, **DESCARTAR**.

### 4.1 Imóvel: localização e inscrição

| Campo do GSAN | Existe? | Onde | Ação recomendada | Prio. |
|---|---|---|---|---|
| Matrícula | PARCIAL | `consumer_units.matricula` (texto 50) | Gerar por sequência por tenant; manter texto; **DV obrigatório** | P0 |
| Dígito verificador (módulo 11) | PARCIAL | `dv CHAR(1)` | Serviço `MatriculaDv` com testes de valor conhecido; torná-lo `NOT NULL` | P0 |
| Localidade | NÃO | `01§8 localities` | Criar tabela; FK por quadra | P0 |
| Setor comercial | NÃO | `01§8 commercial_sectors` | Criar | P0 |
| Quadra | NÃO | `01§8 blocks` | Criar, com rede água/esgoto (enum) e DMA | P0 |
| Face da quadra | NÃO | `01§8 block_faces` | Criar; usar só se o tenant ligar a flag | P1 |
| Lote / Sublote | NÃO | falta em `consumer_units` | `lot`, `sublot` inteiros; zeros só na exibição | P0 |
| Unicidade da inscrição | NÃO | | `UNIQUE (tenant_id, block_id, lot, sublot)` | P0 |
| Formato da inscrição (3.3.3.4.3) e dígitos da quadra por tenant | NÃO | `tenant_metadata.settings` | Parâmetros tipados | P1 |
| Testada do lote | NÃO | | `lot_frontage_m` numérico | P2 |
| Sequência na rota | PARCIAL | `reading_sequence` | Manter; **obrigatoriedade por configuração** (não por empresa fixa) | P0 |
| Bloqueio de inserção por localidade/setor/quadra | NÃO | | `blocked` + permissão `PROPERTY_INSERT_OVERRIDE` | P1 |
| Setor alternativo (não aceita imóvel) | NÃO | | `is_alternative` no setor | P2 |
| Convenção setor/quadra 999 = "cadastro provisório" | NÃO | | **DESCARTAR**; usar `status = PROVISIONAL` | DESCARTAR |
| Hierarquia gerência regional / unidade de negócio | NÃO | `01§8 regional_offices`, `business_units` | Criar; só se o cliente usar | P1 |
| Rota de leitura (cadastro) | PARCIAL | `reading_route_id` sem FK | Tabela `reading_routes`; FK | P0 |
| Rota/quadra/sequencial de **entrega** da conta; rota alternativa | NÃO | | `delivery_route_id`, `delivery_sequence` | P1 |

### 4.2 Imóvel: endereço e geolocalização

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Endereço único do imóvel | PARCIAL | `addresses` (type PHYSICAL) **e** `consumer_units.address JSONB` | **Remover o JSONB**; `address_id` na unidade; regra "exatamente um físico" | P0 |
| CEP (8) | SIM | `addresses.cep` | Validar máscara; consulta ViaCEP/base própria | P0 |
| Logradouro (tipo, título, nome) | PARCIAL | `street` texto | Texto livre + busca por CEP agora; FK `streets` depois | P1 |
| Bairro | PARCIAL | `neighborhood` texto | FK `neighborhoods` | P1 |
| Município / UF | PARCIAL | `city`, `state` texto | FK `municipalities` com **código IBGE de 7 dígitos** | P1 |
| Número | SIM | `number VARCHAR(20)` | Aceitar "S/N" | P0 |
| Complemento | SIM | `complement VARCHAR(100)` | | P0 |
| Tipo de ponto de referência | NÃO | | Catálogo | P2 |
| Logradouros de esquina (perímetro) | NÃO | | `intersection_street_a/b` | P2 |
| Coordenadas X/Y (UTM como texto) | PARCIAL | `coordinates GEOMETRY(POINT,4674)` | Campo de lat/long com seletor de mapa; **abandonar UTM em texto**; exigir par completo | P0 |
| Município do endereço diferente do município do setor | NÃO | | Regra com permissão | P1 |
| Bairro do logradouro = bairro da quadra (flag do município) | NÃO | | Regra opcional por município | P2 |

### 4.3 Clientes e vínculo

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Entidade Cliente (PF/PJ) | **NÃO** | `owner_id/responsible_id → users` (conceito errado) | Criar `customers`; **remover os dois FKs** de `consumer_units` antes de haver dado | P0 |
| Nome | NÃO | `01§8 customers.name` | | P0 |
| Nome abreviado | NÃO | | **DESCARTAR** (derivar na exibição) | DESCARTAR |
| Nome fantasia / nome exibido na conta | NÃO | `trade_name` | | P1 |
| Tipo de pessoa (PF/PJ) | NÃO | `person_type` | | P0 |
| Tipo do cliente (esfera de poder) | NÃO | `customer_types` | | P1 |
| CPF/CNPJ | NÃO | `document_encrypted` + `document_hash` | **Cifrar + índice cego**; **CNPJ alfanumérico** (14 posições, ver seção 6) | P0 |
| Unicidade do documento (e RG+órgão+UF) | NÃO | | Unicidade por `document_hash`; **não** usar RG como chave | P0 |
| RG, órgão, UF, data de emissão | NÃO | `rg_*` | Coletar só quando não há CPF ou em tarifa social | P2 |
| Data de nascimento | NÃO | | Opcional; obrigatória só em tarifa social | P1 |
| Nome da mãe | NÃO | | Opcional (desambiguar homônimo) | P2 |
| Sexo | NÃO | | **DESCARTAR** como obrigatório | DESCARTAR |
| Profissão | NÃO | | **DESCARTAR** (sem uso comercial visto) | DESCARTAR |
| Ramo de atividade | NÃO | catálogo | Só PJ; alimenta a categoria | P1 |
| Responsável superior | NÃO | `parent_customer_id` | | P2 |
| E-mail | PARCIAL | `users.email` (de usuário do sistema) | `customer_emails` N, com `consent_id` | P1 |
| Telefones (tipo, DDD, ramal, contato, principal) | PARCIAL | `users.phone` | `customer_phones` (E.164 + tipo + principal + contato) | P1 |
| Endereço de correspondência | PARCIAL | `addresses.type=CORRESPONDENCE` | `customer_addresses` ou `addresses` com `entity_type='CUSTOMER'` | P0 |
| Dia de vencimento da conta (por cliente) | NÃO | | **Por unidade** (`due_day`); no cliente só padrão | P1 |
| Vencimento no mês seguinte; fatura antecipada | NÃO | | Flags | P2 |
| Bloqueio de negativação; executa cobrança como responsável | NÃO | | Flags do módulo `dunning` | P2 |
| Vínculo com papel (proprietário/usuário/responsável) | NÃO | `01§8 unit_customers` | Criar com **vigência** | P0 |
| Data início/fim e motivo do fim | NÃO | | | P0 |
| Nome na conta (exatamente 1) | NÃO | `show_on_bill` | Índice parcial único por unidade | P0 |
| ≥ 1 usuário vigente por imóvel | NÃO | | Regra de serviço | P0 |
| Documento do usuário obrigatório (parametrizável) | NÃO | `tenant_metadata.settings` | | P0 |
| Cliente por economia | NÃO | `economy_customers` | | P2 |
| Envio da conta (responsável, imóvel, e-mail, braile) | NÃO | `bill_delivery_modes` | Catálogo; regra de e-mail do nome na conta | P1 |

### 4.4 Categoria e economias

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Categoria | PARCIAL | `category_id` sem FK | Tabela `categories` + FK | P0 |
| Subcategoria | NÃO | `01§8 subcategories` | | P0 |
| Quantidade de economias | PARCIAL | `water_economies`, `sewage_economies` | Fonte única em `unit_subcategories.economies`; totais derivados | P0 |
| Unidades privativas/coletivas | NÃO | | | P1 |
| Ramos de atividade do imóvel | NÃO | `unit_activity_branches` | | P1 |
| Categoria/subcategoria principal (derivadas) | NÃO | | Calcular em serviço, não digitar | P0 |
| Permissão para categoria pública | NÃO | | Flag na categoria + permissão | P1 |
| Limite de economias com confirmação | NÃO | | `soft_limit` na categoria + "aviso confirmável" | P2 |
| Perfil exige subcategoria | NÃO | | Regra opcional no perfil | P2 |
| Economias individuais (complemento, pontos, moradores, IPTU, energia, área) | NÃO | `economies` | Só se o cliente precisar de economia nominal | P2 |
| Parâmetros de consumo da categoria (mínimo, estouro, baixo, alto) | NÃO | `categories` | Pertencem ao módulo de consumo (relatório `02`) | P1 |

### 4.5 Características e situação

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Área construída + faixa | PARCIAL | `properties JSONB` (inferência) | Coluna `built_area_m2`; faixa **derivada** | P0 |
| Volumes (reservatórios, piscina) | NÃO | | JSONB validado ou colunas | P2 |
| Jardim; pavimentos; tipo de habitação/propriedade/construção/cobertura | NÃO | | JSONB validado `physical` | P2 |
| Fonte de abastecimento; poço | NÃO | | Catálogos; relevante para esgoto sem água | P1 |
| Situação de água | PARCIAL | `status` e `water_connection_date` | Catálogo `water_statuses` com flags (fatura, rede, ligação, abastece, consumo mínimo, dias de corte) | P0 |
| Situação de esgoto | PARCIAL | `sewage_connection_date` | Catálogo `sewer_statuses` | P0 |
| Situação do imóvel (derivada de água x esgoto) | NÃO | `property_status_matrix` | Derivar por matriz, não digitar | P1 |
| Consistência situação x rede da quadra | NÃO | | **Regra de domínio testada** (R1) | P0 |
| Esgotamento | NÃO | | Catálogo; obrigatório se esgoto fatura | P1 |
| Perfil do imóvel | PARCIAL | `tariff_type_id` | `property_profiles` com flags | P1 |
| Tipo de despejo; destino de dejetos; caixa de inspeção; águas pluviais | NÃO | | Catálogos de esgoto | P2 |
| Nível para instalação de esgoto | NÃO | | | P2 |
| Nº de pontos de utilização | NÃO | | | P1 |
| Nº de moradores | NÃO | | Opcional; dado pessoal indireto | P1 |
| Classe social; animais domésticos; acesso ao hidrômetro; % de abastecimento | NÃO | | **DESCARTAR** os dois primeiros; acesso ao hidrômetro vira campo da instalação | DESCARTAR / P2 |
| IPTU (único por município) | NÃO | `external_ids` | | P1 |
| Contrato e medidor de energia | NÃO | | Só na tarifa social | P2 |
| Data da visita comercial | NÃO | | **DESCARTAR** (auditoria cobre) | DESCARTAR |
| Imóvel principal / condomínio / área comum | NÃO | `parent_unit_id`, `is_condominium` | Mesma quadra | P2 |
| Informações complementares | PARCIAL | `properties` | Coluna `notes`, limite 750 | P1 |
| Funcionário responsável | NÃO | | **DESCARTAR** (`created_by` já audita) | DESCARTAR |
| Exclusão lógica | PARCIAL | `status` | `deleted_at` + motivo, separado da situação | P0 |
| Histórico de mudança de situação | NÃO | `unit_status_history` | Gravar a cada transição, com OS de origem | P0 |
| Ocorrência de cadastro / anormalidade de localidade pólo | NÃO | | Catálogo, só com recadastramento | P2 |

### 4.6 Ligações

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Ligação de água (entidade 1:1) | PARCIAL | só `water_connection_date` | `water_connections` | P1 |
| Datas: ligação, corte, religação, supressão, restabelecimento | PARCIAL | 1 de 5 | Colunas ou eventos em `unit_status_history` | P1 |
| Diâmetro, material, perfil, local do ramal | NÃO | catálogos | | P1 |
| Selo de corte e de supressão; lacre; leituras de corte/supressão | NÃO | | Campos do evento de corte/supressão | P1 |
| Tipo e motivo de corte; tipo (total/parcial) e motivo de supressão | NÃO | catálogos | | P1 |
| Consumo mínimo da ligação | NÃO | | Campo da ligação, validado | P1 |
| Ligação de origem | NÃO | | **DESCARTAR** até haver demanda | DESCARTAR |
| Ligação de esgoto (entidade 1:1) | PARCIAL | `sewage_connection_date` | `sewer_connections` | P1 |
| Perfil de esgoto, **% esgoto derivado**, **% coleta** (editável só com permissão) | NÃO | | Regra de faturamento (relatório `02`) | P1 |
| Caixa de gordura | NÃO | | boolean | P2 |
| "Ligação: disponível / efetivado" | NÃO | | **DESCARTAR**; é a situação de esgoto | DESCARTAR |
| Operações de campo exigem OS e movem a situação | NÃO | `service_orders` existe | Máquina de estados com evento (relatório `04`) | P1 |
| Regras: só liga Potencial/Factível/Em fiscalização, quadra com rede, imóvel ativo, fora de recadastramento | NÃO | | Regras de domínio | P1 |
| Geração de débito pela OS (percentual 100/70/50, parcelas, motivo de não cobrança) | NÃO | | Módulo `billing` | P2 |

### 4.7 Hidrômetro

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Hidrômetro (estoque) | NÃO | `meter_id` solto | `meters` (`operations/sigsan-metering`) | P1 |
| Número; prefixo e faixa (cadastro em lote) | NÃO | | Cadastro unitário **e importação em lote**; ano derivado | P1 |
| Capacidade; nº de dígitos; marca; diâmetro; classe metrológica; tipo de fluxo; relojoaria | NÃO | catálogos | Catálogos; marca com **intervalo de revisão em dias** | P1 |
| Data de aquisição; nota fiscal; garantia; ano de fabricação | NÃO | | | P1 |
| Vazões de transição, nominal e mínima | NÃO | | Opcional | P2 |
| Finalidade comercial/operacional | NÃO | `is_bulk_meter` | Ponte para o balanço hídrico | P1 |
| Situação (instalado, manutenção, disponível, roubado, baixado) | NÃO | enum | | P1 |
| Local de armazenagem (oficina?) | NÃO | | | P2 |
| Instalação (data, leitura, selo, lacre, cavalete, proteção, local) | NÃO | `meter_installations` | Histórico temporal | P1 |
| Tipo de medição: ligação de água ou poço | NÃO | | Enum `WATER/WELL` | P2 |
| Troca de proteção / de registro na substituição | NÃO | | Flags no evento | P2 |
| Retirada (leitura, situação do hidrômetro retirado) | NÃO | | | P1 |
| Movimentação entre locais (origem, destino, motivo, parecer) | NÃO | `meter_movements` | | P2 |

### 4.8 Tarifa social, programas e recadastramento

| Campo do GSAN | Existe? | Onde | Ação | Prio. |
|---|---|---|---|---|
| Inscrição em tarifa social (RA obrigatório, datas, motivo de revisão/exclusão) | NÃO | `social_tariff_enrollments` | Criar com consentimento e base legal | P2 |
| Cartão do programa social (número, tipo, validade) | NÃO | | **Cifrar** | P2 |
| Renda familiar (valor, tipo) | NÃO | | **Cifrar**; considerar guardar só a faixa | P2 |
| Consumo de energia, IPTU, área (limites do programa) | NÃO | | Parâmetros por tenant | P2 |
| Programa especial / Bolsa Família | NÃO | `special_programs` | Idem; **não** guardar número em claro | P2 |
| Doação mensal a entidade beneficente | NÃO | | Fora do núcleo | P2 |
| Lote/arquivo de campo; retorno; aprovação campo a campo; fiscalização; revisita | NÃO | `cadastral_survey_*` | Fase de recadastramento (relatório `01` P1.1) | P1 |
| Layout posicional com login/senha do coletor | NÃO | | **DESCARTAR**; JSON/OpenAPI e token de dispositivo | DESCARTAR |
| Alterar inscrição com autorização | NÃO | `inscription_changes` | | P1 |

### 4.9 Lista consolidada do que DESCARTAR (legado ou obsoleto)

| Item do GSAN | Motivo |
|---|---|
| Formulário em **sessão HTTP** que carrega o estado do assistente entre abas | Estado preso ao servidor; no SIGSAN, rascunho versionado |
| **Popups** (`window.open`) para busca e cadastro auxiliar | Trocar por modal/drawer acessível |
| Recarga total da página ao trocar um select (`redirecionarSubmit`) | Atualização reativa |
| IDs e códigos fixos no código (perfil, relação, situação, envio de conta 4/5), linhas "não informado" = 0 | FK nula; flags em catálogo |
| Flags 1/2 em vez de boolean; `indicadorUso` ao lado de `indicadorExclusao` | `boolean` e `deleted_at` |
| Coordenadas **texto UTM**, rótulo "Latitude/Longitude" sobre campo X/Y | `GEOMETRY(POINT,4674)` |
| CPF/CNPJ **só dígitos sem máscara e sem aceitar letras** | Máscara; CNPJ alfanumérico |
| Obrigatoriedade por **empresa fixa** no validator (sequência na rota) | Configuração por tenant |
| Nome abreviado, sexo obrigatório, profissão, classe social, animais domésticos, data de visita comercial, funcionário do imóvel | Sem uso claro; coleta de dado sem finalidade |
| "Ligação: Disponível/Efetivado", "Atualizar" no filtro, "Desfazer/Cancelar" para a tela principal | Substituídos por situação e por ações de linha |
| Código de marca de 1 caractere; município de 4 dígitos próprio | Usar IBGE e UUID |
| Validação duplicada em JS e no servidor sem fonte única | Esquema compartilhado |
| Dados do cliente em **query string** (documentado no `00`) | Proibido pela LGPD |

---

## 5. Padrões de UX e dados pessoais

### 5.1 O que vale copiar como conceito

| Padrão no GSAN | Como adaptar no Ziip SAN |
|---|---|
| **Busca por matrícula e por inscrição como atalho universal**: campo de código + Enter traz o nome ao lado; lupa abre pesquisa | Componente `LookupField` único (autocomplete com busca por nome e código), usado em todas as telas; campo "Ir para matrícula/inscrição" na barra superior |
| **Validação por etapa** do assistente (cada aba valida no servidor antes de avançar) | Mesmo desenho, com **rascunho salvo no servidor** e `POST /…/validate-step`; não manter estado na sessão |
| **Aviso confirmável vs erro bloqueante** (limite de economias, sequência já ocupada na rota) | Resposta com `errors[]` e `warnings[]`; aviso exige "Confirmar e continuar" |
| **Campos derivados** (faixa de área, % de esgoto, categoria principal, situação do imóvel) | Calcular no servidor; exibir somente leitura com a origem |
| **Habilitação condicional** (esgotamento se a situação fatura; motivo e tipo de corte só se cortado; percentual de faixa falsa só se marcado) | Regras no esquema do formulário, uma por campo |
| **Cascata** (localidade > setor > quadra; município > bairro > logradouro) | Selects dependentes que limpam os filhos |
| **Histórico temporal** (relação cliente-imóvel com vigência; histórico de instalação de hidrômetro) | Linha do tempo na ficha do imóvel; "como era em dd/mm/aaaa" |
| **Validação ao sair do campo** (quadra, dígito do documento) | Validação em tempo real no `onBlur`, mensagem inline |
| **Cabeçalho persistente do imóvel** nas abas de consulta (matrícula, situação de água e esgoto, endereço, marca "excluído") | Cabeçalho fixo na ficha |
| **Ajuda contextual por etapa** | Texto de ajuda no próprio formulário |
| **Padrão uniforme de catálogos** (inserir, filtrar, manter, atualizar) | Um componente CRUD genérico dirigido por metadados |
| **Ações por ordem de serviço** com cabeçalho de imóvel preenchido e bloco de débito | Ações na ficha do imóvel, iniciadas pela OS |
| **Controle de concorrência** | `@Version` com mensagem "o registro mudou, recarregue", sem perder o digitado |

### 5.2 O que não replicar

| Padrão no GSAN | Problema |
|---|---|
| Navegar por **código digitado** sem pesquisar por nome/endereço | Lento e dependente de decorar códigos |
| Lista "todos os imóveis encontrados" com seleção em massa e sem paginação | Não escala |
| Formulários de 20+ campos numa só aba (características, conclusão) | Dividir por tema, com resumo no fim |
| Máscara ausente (CPF, CNPJ, CEP só dígitos) | Erro de digitação silencioso |
| Mensagens de erro acopladas à empresa; regras com nome de concessionária | Configuração por tenant |
| Busca de cliente que devolve **CPF e CNPJ completos** na lista | Mascarar (`***.123.456-**`) e revelar com permissão e log |

### 5.3 Dados pessoais coletados por tela (LGPD) e necessidade

Legenda: **N** necessário para a finalidade do serviço; **C** condicional (só em caso específico); **D** desnecessário, evitar.

| Tela | Dado | Classe | Necessidade e recomendação |
|---|---|---|---|
| Cliente (nome/tipo) | Nome | Pessoal | **N** |
| | E-mail | Pessoal | **C**: só com consentimento ou legítimo interesse para fatura digital |
| | Dia de vencimento, flags de cobrança | Pessoal comercial | **N** |
| Cliente (PF) | CPF | Pessoal (identificador) | **N**; cifrar, índice cego, máscara na UI. Exceção "sem CPF" só com permissão e motivo |
| | RG, órgão, UF, data de emissão | Pessoal | **C**: só quando não há CPF, ou tarifa social. Caso contrário **D** |
| | Data de nascimento | Pessoal | **C**: tarifa social e capacidade civil; senão **D** |
| | Nome da mãe | Pessoal (de terceiro) | **C/D**: desambiguar homônimo apenas |
| | Sexo | Pessoal | **D** (não vi uso comercial); se mantido, opcional |
| | Profissão | Pessoal | **D** |
| Cliente (PJ) | CNPJ, ramo, responsável superior | Empresarial | **N** (dado pessoal só quando MEI ou empresário individual) |
| Telefone | DDD, número, ramal, contato | Pessoal | **C**: contato operacional; guardar base legal e finalidade; "nome do contato" é dado de terceiro |
| Endereço do cliente e do imóvel | Logradouro, número, CEP, coordenadas | Pessoal quando ligado a pessoa | **N** (fatura e prestação). Coordenada do domicílio é geolocalização: **C** com finalidade operacional |
| Imóvel (conclusão) | Número de moradores, pontos | Pessoal indireto | **C**: estimar consumo; coletar o mínimo |
| | IPTU, contrato e medidor de energia | Identificadores de terceiros | **C**: só tarifa social ou unicidade; tratar como pessoal |
| | Foto de fachada e de anormalidade | Pode capturar pessoas | **C**: orientar o agente; revisar retenção |
| Vínculo cliente-imóvel | Papel, datas, motivo | Pessoal | **N**; manter histórico com prazo de retenção |
| Tarifa social | Renda familiar, número do cartão do programa, NIS/Bolsa Família, moradores, consumo de energia | **Alto risco** (socioeconômico). O `00` o trata como "sensível"; a lista do art. 5º II da LGPD não cita renda, então **confirmar o enquadramento com o jurídico** | **C**: só para quem pede o benefício; **cifrar**, base legal explícita (política pública / obrigação regulatória), acesso restrito, retenção curta, ocultar de relatórios gerais |
| Recadastramento | Fotos, lat/long do agente, **login e senha do coletor no arquivo** | Pessoal e credencial | Fotos **C**; credencial em arquivo: **D** (proibido) |
| Hidrômetro, ligação, catálogos | Nenhum pessoal | | |
| Doação a entidade beneficente | Entidade escolhida pelo titular | Pode revelar convicção (se a entidade for religiosa) [INFERÊNCIA] | **C**: avaliar |
| Usuário que operou (auditoria) | Identificação do funcionário | Pessoal de terceiros | **N** para trilha; acesso restrito |

---

## 6. Recomendações para o SIGSAN

### 6.1 Lista priorizada de campos e telas

**P0: sem isso não há cadastro operável**

1. **Consertar o modelo antes do primeiro dado**: criar `customers` e `unit_customers` (com vigência, papel e "nome na conta"); **remover** `owner_id`/`responsible_id` de `consumer_units`; **remover** `address JSONB`; nova migration `V2_x` (não editar V1_1) usando `register_tenant_table()`.
2. **Hierarquia**: `localities`, `commercial_sectors`, `blocks` (rede de água e esgoto como enum; DMA), `reading_routes`; em `consumer_units`: `block_id`, `lot`, `sublot`, `UNIQUE (tenant_id, block_id, lot, sublot)`.
3. **Matrícula com DV módulo 11** (serviço puro com testes de valor conhecido) e **inscrição formatada** `LLL.SSS.QQQ.LLLL.SSS`.
4. **Categoria e subcategoria** com `unit_subcategories`; categoria principal derivada.
5. **Catálogos de situação de água e esgoto com flags** e **histórico de situação**; regra "situação x rede da quadra" como serviço de domínio testado.
6. **Cliente**: PF/PJ, documento cifrado + hash, endereços, telefones, e-mail com consentimento; unicidade por hash.
7. **Frontend**: lista de imóveis, ficha do imóvel, assistente de cadastro do imóvel, cadastro de cliente, e os 4 cadastros territoriais. Corrigir `Consumers.tsx` (colunas inexistentes, ações mortas, exclusão física) e as 17 páginas ausentes que impedem o build.
8. **Regras de consistência com teste** (R1 a R9 do `01` mais as desta leitura): documento obrigatório do usuário (por parâmetro), ≥1 usuário vigente, exatamente 1 endereço físico, imóvel principal na mesma quadra, IPTU único, coordenada em par.

**P1: logo depois**

9. Ligação de água e de esgoto (entidades 1:1 e ações por OS: ligar, atualizar, cortar, suprimir, religar), **perfil de imóvel**, **modo de envio da conta**, **rota de entrega**.
10. **Hidrômetro**: estoque, instalação com histórico, substituição e retirada, catálogos, importação em lote por faixa.
11. **Endereço normalizado** (IBGE, bairro, logradouro, CEP com busca), **alterar inscrição com autorização**, **fim de relação**, **busca avançada** (filtros de localização, cliente, características, água/esgoto, faturamento).
12. **Recadastramento** (lote > coleta > retorno > aprovação campo a campo > aplicação), com a tela de aprovação em tabela anterior/novo.
13. **Parâmetros do tenant tipados** para cadastro (dígitos da quadra, uso de face, documento obrigatório, tamanho mínimo de nome, lista de nomes genéricos, duplicidade de cliente).

**P2: depois**

14. Tarifa social e programas especiais; doação; economias nominais; condomínio e imóvel principal; características físicas finas; tipos de despejo; situação de cobrança do imóvel.

### 6.2 Contratos comuns para as telas React

**API (REST, JSON).** `GET /api/registry/{recurso}?q=&page=&size=&sort=` (paginado, nunca "todos"); `GET /…/{id}`; `POST`, `PUT /…/{id}` com `version`; `DELETE` só lógico (`deleted_at` + motivo). Erros em `application/problem+json` com `errors[{field,code,message}]` e `warnings[{code,message,confirmToken}]`; reenviar com `confirmToken` aceita o aviso.
**Validação.** Esquema único (Zod no front, Bean Validation no back, gerado do mesmo contrato). Mensagens em pt-BR no front, códigos estáveis no back.
**Componentes.** `LookupField` (código/nome, modal de busca, descrição ao lado), `InscriptionInput` (3.3.3.4.3, zeros à esquerda só na exibição), `CpfCnpjInput` (máscara, DV, aceita CNPJ alfanumérico), `CepInput` (máscara `00000-000`, consulta), `PhoneInput`, `MapPointPicker` (lat/long, SIRGAS 2000), `Wizard` (rascunho no servidor), `ConfirmWarning`, `Timeline`, `CatalogCrud` (por metadados).
**CNPJ alfanumérico.** A Receita Federal passou a emitir CNPJ com letras a partir de julho de 2026 [INFERÊNCIA a partir de conhecimento geral; **confirmar a norma vigente**]. O GSAN só aceita dígitos. Modelar `document` como texto de 14 posições maiúsculas, com cálculo de DV adaptado, e testar com vetores oficiais.

### 6.3 Esboço das telas React

**Estrutura de rotas**

| Rota | Tela |
|---|---|
| `/cadastro/imoveis` | S1 Pesquisa de imóveis |
| `/cadastro/imoveis/novo`, `/cadastro/imoveis/:id/editar` | S2 Assistente de imóvel |
| `/cadastro/imoveis/:id` | S3 Ficha do imóvel |
| `/cadastro/clientes`, `/novo`, `/:id`, `/:id/editar` | S4 Cliente |
| `/cadastro/territorio/:nivel` | S5 Hierarquia territorial |
| `/cadastro/catalogos/:tipo` | S6 Catálogos |
| `/medicao/hidrometros`, `/:id` | S7 Hidrômetros (P1) |
| `/cadastro/recadastramento` | S8 Recadastramento (P1) |

**S1. Pesquisa de imóveis** (substitui `Consumers.tsx`)

| Campo (chave) | Tipo/máscara | Regra |
|---|---|---|
| `q` | texto livre; aceita matrícula, inscrição, nome, documento, endereço | O servidor reconhece o formato: só dígitos de 7 a 10 = matrícula (valida DV); `LLL.SSS…` = inscrição; 11 ou 14 posições com DV válido = documento (busca por hash) |
| `localityId` > `sectorId` > `blockId` | `LookupField` em cascata | Filhos habilitam após o pai |
| `waterStatusId`, `sewerStatusId`, `categoryId`, `profileId` | selects | |
| `municipalityId`, `neighborhoodId`, `streetId`, `cep`, `numberFrom`, `numberTo` | lookups / `CepInput` / numéricos | |
| `routeId` | lookup | |
| `includeDeleted` | checkbox | Só com permissão |

Colunas: **Matrícula (com DV), Inscrição, Usuário (nome), Endereço, Categoria principal, Situação de água, Situação de esgoto, Economias**. Documento **mascarado**. Ações de linha: Abrir ficha, Editar, Ações de campo (menu). Atalho de teclado "/" foca `q`; paginação de 25 por página; exportação CSV com permissão e registro em log.

**S2. Assistente de imóvel (6 etapas)**

| Etapa | Campo (chave) | Tipo | Obrig. | Validação | Habilita quando |
|---|---|---|---|---|---|
| 1 Localização | `localityId`, `sectorId`, `blockId` | `LookupField` | S | existir; não bloqueado | em cascata |
| | `blockFaceId` | select | C | se `tenant.useBlockFace` | |
| | `lot` | inteiro 1 a 9999 | S | | |
| | `sublot` | inteiro 0 a 999 | S | **inscrição única** (bloqueia, mostra a matrícula existente) | |
| | `readingSequence` | inteiro | C | se `tenant.requireReadingSequence` | aviso confirmável se já ocupada |
| | `lotFrontageM` | decimal | N | | |
| 2 Endereço | `cep` | `CepInput` | N | preenche município, bairro, logradouro | |
| | `streetId` ou `streetName` | lookup / texto | S | | |
| | `number` | texto até 20 | S | aceita "S/N" | |
| | `complement` | texto até 100 | N | | |
| | `referenceTypeId` | select | N | | |
| | `coordinates` | `MapPointPicker` | N | **par completo** lat/long dentro do Brasil | |
| | | | | regra: município diferente do setor exige permissão; bairro x quadra por flag do município | |
| 3 Clientes | `customerId` | `LookupField` + "Novo cliente" (drawer) | S | | |
| | `relation` | OWNER / USER / RESPONSIBLE | S | **≥1 USER**; documento do USER obrigatório (por parâmetro) | |
| | `startDate` | data | S | não futura | |
| | `showOnBill` | radio | S | **exatamente 1** | |
| | `deliveryModeId` | select | C | obrigatório se há RESPONSIBLE; se for e-mail, o `showOnBill` precisa ter e-mail | |
| 4 Categorias | `subcategoryId` (com `categoryId` derivado) | select em cascata | S | ≥1; sem repetir; permissão se categoria pública | |
| | `economies` | inteiro ≥1 | S | acima do limite: **aviso confirmável** | |
| | `privateUnits`, `collectiveUnits` | inteiro | N | | |
| | `activityBranchIds` | multi-select | N | sem repetir | |
| 5 Características | `builtAreaM2` | decimal 2 casas | S | faixa derivada (somente leitura) | |
| | `waterStatusId` | select | S | **compatível com a rede da quadra** | |
| | `sewerStatusId` | select | S | idem | |
| | `sewerDischargeId` (esgotamento) | select | C | se `sewerStatus.billable` | |
| | `profileId` | select | S | perfil "provisório" só se `status = PROVISIONAL` | |
| | `waterSourceId`, `wellTypeId` | select | N | poço habilita conforme a fonte | |
| | `pavementStreetId`, `pavementSidewalkId` | select | N | | |
| | `physical` (jardim, reservatórios, piscina, habitação, construção, cobertura) | grupo recolhido | N | JSON validado | |
| 6 Conclusão | `usagePoints`, `residents` | inteiro | N | | |
| | `externalIds.iptu` | texto | N | **único por município** | travado em tarifa social |
| | `deliveryRouteId`, `deliverySequence`, `altRouteId` | lookups | C | rota de entrega ativa e não alternativa | |
| | `parentUnitId` | `LookupField` | N | mesma quadra | |
| | `notes` | texto até 750 | N | contador | |
| Final | Resumo com inscrição, matrícula (após gravar), diferenças | | | | Botões: Salvar rascunho, Voltar, Gravar |

**S3. Ficha do imóvel (consulta)**: cabeçalho fixo (matrícula-DV, inscrição, situação de água e esgoto, endereço, marca "excluído"). Abas: **Cadastro**, **Clientes** (vigentes e histórico), **Ligações e medição** (água, esgoto, hidrômetro atual e histórico), **Faturamento**, **Débitos e pagamentos**, **Atendimentos e OS**, **Histórico** (situação, inscrição, auditoria campo a campo), **Documentos**. Ações (menu): Editar, Alterar inscrição, Encerrar relação de cliente, Ações de campo (ligar, cortar, suprimir, religar, trocar hidrômetro), Excluir (lógico, com motivo).

**S4. Cliente**

| Campo (chave) | Tipo/máscara | Obrig. | Validação | Habilita quando |
|---|---|---|---|---|
| `personType` | radio PF/PJ | S | | troca o bloco seguinte |
| `name` | texto 3 a 200 | S | tamanho mínimo e lista de nomes genéricos (por parâmetro, com permissão para exceção) | |
| `tradeName` | texto 200 | C | | PJ, ou tenant usa nome fantasia |
| `customerTypeId` | select | S | filtrado por pessoa | |
| `document` | `CpfCnpjInput` | C | DV; **duplicidade por hash**, devolve o existente | obrigatório salvo permissão |
| `birthDate` | data | C | não futura | PF; obrigatória em tarifa social |
| `rg`, `rgIssuerId`, `rgState`, `rgIssueDate` | grupo recolhido | N | | só PF sem CPF |
| `motherName` | texto | N | | |
| `activityBranchId`, `parentCustomerId` | select / lookup | N | | PJ |
| `emails[]` | e-mail + `consentId` | N | formato | |
| `phones[]` | tipo, E.164, ramal, contato, principal | N | **um principal** | |
| `addresses[]` | tipo, endereço, correspondência | S | **≥1; um de correspondência** | |
| `billing` (dia de vencimento, mês seguinte, fatura antecipada, bloqueio de negativação) | grupo | N | dia 1 a 31; bloqueio só com permissão | |

Lista: nome, tipo, documento **mascarado**, cidade, nº de imóveis. Filtros: nome (inicia/contém), documento, mãe, cidade, tipo, ativo. Revelar documento exige permissão e gera log.

**S5. Hierarquia territorial** (uma tela, abas por nível: Regionais, Unidades, Localidades, Setores, Quadras e faces, Rotas): `code` (numérico, largura por nível), `name`, `parentId`; **Localidade**: município principal, porte, classe, centro de custo, sede, informatizada, bloqueada; **Setor**: município, alternativo, bloqueado; **Quadra**: rota, bairro, perfil, rede de água e de esgoto (enum), distrito/**DMA**, setor censitário, incremento automático de lote, bloqueada, faces; **Face**: rede, dificuldade, risco, **nível de pressão e intermitência**; **Rota**: grupo de faturamento, grupo de cobrança, tipo de leitura, limite de imóveis, empresas de leitura/cobrança/entrega, fiscalização, faixa falsa (% e flag), ajuste de consumo (dias). Regras: código único por pai; DMA exige rede de água; sistema e bacia exigem rede de esgoto.

**S6. Catálogos** (`CatalogCrud`): `categories`, `subcategories`, `property_profiles`, `water_statuses`, `sewer_statuses`, `sewer_profiles`, `bill_delivery_modes`, `customer_types`, `activity_branches`, `area_ranges`, `pavements`, `water_sources`, `well_types`, `connection_diameters`, `connection_materials`, `connection_profiles`, `cut_types`, `cut_reasons`. Cada um declara colunas, tipos e flags; o CRUD é gerado. Catálogo **semente** só com dado oficial; nenhum valor inventado (regra do diário, seção 15.4).

**S7. Hidrômetros (P1)**: lista com filtros (número, marca, capacidade, situação, local de armazenagem, ano, aquisição) e ação "Cadastrar em lote (prefixo + faixa)"; ficha com instalações (linha do tempo), revisão vencida (marca > intervalo em dias), movimentações. Ação "Substituir": leitura de retirada (S), situação do retirado (S), novo número (S), local, proteção, cavalete, troca de proteção/registro, selo, lacre, leitura de instalação.

**S8. Recadastramento (P1)**: lista de lotes (escopo, agente, tipo, situação), painel de transmissão (totais e alterações por tipo), tela de **aprovação** com tabela *tabela, campo, anterior, coletado, decisão, aprovador* e aprovação em lote filtrada por "sem ocorrência".

### 6.4 Sequência sugerida de entrega

1. Migration do modelo corrigido + serviços de DV e de consistência, **com testes** (valores calculados à mão, como o diário exige).
2. API de catálogos e hierarquia + S5 e S6.
3. API de cliente + S4.
4. API de imóvel + S2, S1 e S3.
5. Ligações, hidrômetro e recadastramento (P1).

---

## 7. Limites e pendências

- Não li os `.hbm.xml` de cada entidade nesta rodada; tipos de coluna e unicidades do banco do GSAN continuam desconhecidos (o DDL está em `gsan-migracoes`). Não confirmei se a **inscrição é UNIQUE no banco**; só que o código a verifica.
- O menu real do sistema está no banco; usei o `toc.xml` do help, que é parcial.
- "Obrigatório" vem do marcador (*) da JSP e do `validator.xml`; quando o controlador exige mais, citei. Alguns (*) da JSP não têm checagem no servidor (por exemplo "Número de pontos") [INFERÊNCIA: obrigatoriedade apenas visual].
- Não li as telas de religação, restabelecimento, movimentação de hidrômetro, situação de cobrança e fichas de fiscalização campo a campo.
- Os catálogos de valores (tipos de cliente, órgãos expedidores, motivos de corte) vêm de dados, não do código; só listei o que o código fixa.
- Itens marcados [INFERÊNCIA] precisam de confirmação com quem opera o GSAN antes de virarem requisito.
- Licença do GSAN segue não verificada (ver `01` seção 2). Nada daqui deve ser usado além de conceito.
