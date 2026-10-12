# 15b - Avaliação do trecho "GEOSAN" do relatório ChatGPT, contra o código-fonte VB6

Documento avaliado: `docs/ANALISE-FONTES-OPEN-ENRIQUECIMENTO-ZIIP-SAN.md`, seção "Capacidades herdáveis do GEOSAN" (itens 1 a 4) e lista de evidências do GEOSAN.
Fonte verificada: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GEOSAN` (somente leitura). Contexto: `05-geosan-banco-e-modelo-de-rede.md` e `06-geosan-funcionalidades-epanet-docs.md`.

**Método.** Li os `.frm` (Latin-1, convertidos para leitura) de: `frmAlteraConsumoPorPoligono`, `frmAlteraNoPoligono`, `frmAtualizarSetores`, `frmAtualizacaoConsumo`, `frmConsumidoresDesabastecidos`, `FrmExport`, `frmImportDxf`, `frmImportFile`, `frmCadastroRamalAgua/Esgoto/AutoLote`, `frmConsumoLote/Ligacao`, trechos de `frmCanvas`, `FrmProcess`, `clsGeoReference` e `Global.bas`. Extraí por Word COM (em cópia no scratchpad) o texto dos documentos `01` (especificação 07.00.31), `02` (manual do usuário) e `07` (exportação EPANET). Nenhum código foi copiado; só nomes de tabela, coluna e controle.
**Não li:** o conteúdo de `GS_QUERYS_CLIENT` nº 18 e 19 (estão no banco, não no código; os campos exibidos vêm do código); recursos `.frx` (itens fixos de listas); o `.vsd`; não executei o aplicativo. Onde isso limita a conclusão, está dito.

---

## 1. Tabela de afirmações

| # | Afirmação do relatório | Veredito | Evidência | Comentário |
|---|---|---|---|---|
| 1 | Existem formulários de cadastro de ramal de água e de esgoto (`frmCadastroRamalAgua`, `frmCadastroRamalEsgoto`) | **CONFIRMADO** | `Forms/frmCadastroRamalAgua.frm` (Caption "Cadastro de Ramal de Água"), `frmCadastroRamalEsgoto.frm` ("Cadastro de Ramal de Esgoto"). Gravam `RAMAIS_AGUA` e `RAMAIS_AGUA_LIGACAO` (e equivalentes de esgoto). | Campos: posicionamento no lote (esquerdo/centro/direito/desconhecido), profundidade, comprimento, distância da testada, distância do lado; `USUARIO_LOG`/`DATA_LOG`. |
| 2 | Existe "ramal em lote" | **ERRADO (leitura de nome)** | `frmCadastroRamalAutoLote.frm` tem Caption "Campos de Pesquisa por Lotes". "Lote" é **parcela/terreno**, não "batch". O formulário só escolhe o tema de lotes, a tabela de atributos, a coluna do IPTU e a coluna do nº da ligação, e grava isso no `GEOSAN.INI` (seção de filtro de lotes). Não cria ramal algum. | Nenhum formulário cria ramais em massa por polígono. O mais próximo: (a) ferramenta "Inserir ramal automaticamente" (`kdrawramalAuto`): o usuário seleciona **1 trecho** e clica, **um a um**, em pontos GPS da camada `NXGS_V_LIG_COMERCIAL_GPS`; cada clique cria ramal + ligação e apaga o ponto GPS da fila; (b) "Ligações Fictícias" (quantidade + consumo médio) dentro de **um** ramal; (c) operação por polígono "Associa o ramal a rede mais próxima (até 5 m)", que sim é em lote, mas só **reatribui o trecho** de ramais existentes. |
| 3 | Ramal liga-se ao imóvel/ligação comercial | **CONFIRMADO, com ressalva de cardinalidade** | Pré-filtro "Consumidor / Endereço / Inscrição / Ligação-Matrícula" sobre `NXGS_V_LIG_COMERCIAL`; `RAMAIS_AGUA_LIGACAO.NRO_LIGACAO` + `INSCRICAO_LOTE`. Lista "Selecione os consumidores associados ao ramal" com várias marcas. | É **1 ramal : N ligações**, não 1:1 como sugere a tabela "Ramal: imóvel/ligação" do relatório. Ligações fictícias recebem número com prefixo "999"+id do ramal, tipo FICTÍCIA, 1 economia, não hidrometrada. |
| 4 | "Atualização de setores" | **ERRADO quanto à função** | `frmAtualizarSetores.frm`, Caption "Carregar Polígono de Setor". Só há DELETE/INSERT em `POLIGONO_SELECAO` (por usuário; TIPO 0 = nós, 1 = redes, 2 = ramais), com contadores "dentro do polígono" e "na divisa". Comentário do próprio código: carrega a seleção "para depois gerar relatórios ou exportar para o Epanet". Há um aviso interno ("XXX") de que falta desconsiderar redes desativadas. | **Não atualiza o atributo `SECTOR`.** Busca em `Forms`, `classes`, `Modules`: nenhum UPDATE de setor. É seleção espacial (staging). O botão "Operações" abre `frmAlteraNoPoligono`. |
| 5 | "Consulta de consumo" | **CONFIRMADO** | `frmConsumoLote.frm` e `frmConsumoLigacao.frm` (Caption "Consultar Consumo"), `frmConsumoLoteGraf.frm`. SELECT em `NXGS_V_LIG_COMERCIAL_CONSUMO` ⨝ `NXGS_V_LIG_COMERCIAL` pela conexão comercial (`ConnSec`): `CONSUMO_FATURADO`, `CONSUMO_MEDIDO`, mês/ano, agrupar ligações, gráfico. | **Só leitura** e é consumo **faturado/medido** real. Abre a partir dos formulários de ramal (lista de ligações marcadas), **não por polígono**. |
| 6 | "Ligação a partir do mapa" | **CONFIRMADO** | `frmMain.frm`: botões `kdrawramal` ("Inserir Ramal", exige conexão comercial ativa), `kdrawramalAuto`, `kdrawramalAddConsumer`, `kMoveConsumidorGPS`. `frmEncontraConsumidor` (busca por nº ou nome, mostra o ramal no mapa). | Observação de qualidade (ver seção 3, item 13): no fluxo GPS, o volume faturado é gravado em `CONSUMO_LPS` sem conversão visível. |
| 7 | `frmConsumidoresDesabastecidos` exibe ligação, endereço, usuário e **telefones** | **CONFIRMADO** | ListView com 5 colunas: "Nro Ligação", "Endereço", "Usuário", "Tel. Res.", "Tel. Com."; campo "Total de Ligações". Comentário no código aponta `NXGS_V_LIG_COMERCIAL` (`TEL_RES`, `TEL_COM`, `CONSUMIDOR`, endereço montado). | Telefones **sim**, residencial e comercial. Não há campo de unidade crítica, tarifa social ou consentimento. Esses itens do relatório são proposta nova, não herança. |
| 8 | Permite exportar a lista | **CONFIRMADO** | Botão "Gerar txt": arquivo `ClientesAfetadosManobra_AAAA-MM-DD-HHMMSS.txt`, 5 colunas separadas por `;`, aberto no Bloco de Notas. | Sem filtro por finalidade, sem log, sem consentimento. Relevante para a nota LGPD do relatório. |
| 9 | Obtém trechos afetados "por seleção de trechos" e (proposta) "simular isolamento por válvulas" | **PARCIAL** | `frmCanvas`, ferramenta `ksearchinnetwork`: exige **exatamente 1 trecho** selecionado ("Selecione 1 trecho de rede de agua"). `FrmProcess.FindValvulas` → `clsGeoReference.SELECTRede` percorre a rede pelos nós inicial/final (`WATERLINES`) até achar nó cujo tipo tem `SPECIFICATION_ = REGISTER`. O formulário só **recebe** a lista de trechos. Manual 02, item 7.5, descreve o mesmo. | É **busca em grafo até o primeiro registro**, não simulação hidráulica. VRP/válvula e "registro fixo" não param a busca. Não consulta o estado aberto/fechado do registro, nem trata malhas/alimentação alternativa. Só enxerga ligações **com ramal cadastrado** (o manual avisa). Usa a tabela temporária `GS_TEMP` e, em SQL Server/Oracle, **escreve** nela pela conexão comercial. |
| 10 | `FrmExport`: DXF por tema, plano e itens selecionados | **CONFIRMADO** | `FrmExport.frm`, Caption "Exportação DXF". Filtros: "Seleção de Temas", "Plano Atual Selecionado", "Itens Selecionados no Mapa". Arquivo: "1 Completo" ou "Separados por" geometria (pontos, linhas, polígonos, textos). Chama `TeExport.exportDXF`. | Na exportação por tema, aplica o `generate_attribute_where` do `te_theme` como filtro (ver seção 3). Só DXF (filtros MIF/SHP estão comentados). Síncrono, sem histórico. |
| 11 | Importação DXF/Shapefile (e o relatório sugere "importador versionado de DXF, Shapefile, GeoJSON e CSV") | **PARCIAL** | `frmImportDxf.frm` ("Importar Dxf": camada do DXF, tipos de geometria, plano de destino, projeção/datum via `frmProjection`); `frmImportFile.frm` (`.shp`, SPRING `.geo`, MapInfo `.mif`, GeoTab); `frmImportarCotas.frm` (MDT). | Importação **existe**. **GeoJSON e CSV não existem.** Sem preview, mapeamento de atributos, versão ou relatório de erros: só "Importação concluída" ou "Ocorreu um erro". Todo o pipeline governado é desenho novo do Ziip. Omitido pelo relatório: o GEOSAN também **exporta shape** (`gsConsumidores/gsRamais/gsRedes/gsNos.shp`, a partir das tabelas planas `GS_*`). |
| 12 | Documento de exportação EPANET existe e "não pôde ser convertido automaticamente" | **PARCIAL** | `doc/07 - Exportacao Epanet-5.9.9.1.doc` existe e **foi lido por Word COM** (1.565 caracteres). | É um **roteiro de uso** (versão 5.7.72, ~10 passos), não especificação do `.inp`: atualizar consumos → distribuir demandas (nós recebem a soma do consumo dos ramais próximos) → rodar ValidaBase e ler o log (correções críticas "poderão inviabilizar a exportação") → importar cotas → exportar por "Área Selecionada" ou "Seleção de Setores". A falha de conversão foi do método do relatório. |
| 13 | Item 4: GEOSAN oferece "consulta e alteração de consumo **por polígono**" | **PARCIAL / ERRADO na leitura** | As 4 operações por polígono (`frmAlteraNoPoligono`): associar ramal à rede (até 5 m), relatórios em texto, alterar consumo de ligações, exportar para EPANET. | "Alteração" existe, mas é **demanda do modelo** (seção 2). "Consulta de consumo por polígono": **NÃO ENCONTRADA** (a consulta de consumo é por ligação/lote, a partir do ramal). |
| 14 | Item 4: "priorização territorial baseada em telemetria real" como combinação com o GEOSAN | **NÃO ENCONTRADO** no GEOSAN | Nenhuma ingestão de telemetria nos formulários. Só atributos de "código de telemetria" em metadados antigos de componentes (05, seção 4.3). | É proposta do Ziip, corretamente isolada como tal; não é capacidade herdada. |
| 15 | Lista de evidências: caminhos dos 4 `.frm` e do `.doc` | **CONFIRMADO (existência)** | Todos os 5 arquivos existem nos caminhos citados. | A **descrição** de `frmAlteraConsumoPorPoligono` ("tratamento de consumo em lote") está errada no alcance (seção 2). A de `frmCadastroRamalAgua/Esgoto` ("cadastro de ramais e ligações") está correta. |

---

## 2. Ponto especial: `frmAlteraConsumoPorPoligono.frm`

### 2.1 O que o formulário é

- Caption "Alteração de Consumo por Polígono". Moldura "Ligações de água selecionadas pelo polígono". Cabeçalho da grade: "Tipo, Quantidade, Economias, Consumo LPS, Medido". Grupo "**Consumo (médio/ligação)**" com duas opções, "M³/Mês" (padrão) e "LPS", e uma caixa de texto com dica "Informe o consumo médio de uma ligação". Botão "Atribuir".
- Entrada: a seleção espacial do usuário em `POLIGONO_SELECAO` (TIPO 2 = ramais, filtrada por `USUARIO`), montada por polígono desenhado ou por "Carregar Polígono de Setor". Chega-se a ele por **Operações por Polígono → Alterar Consumo de Ligações**.
- Lista: agrupa `RAMAIS_AGUA_LIGACAO` por **TIPO** (situação da ligação) e **HIDROMETRADO**, com quantidade, soma de economias e soma de `CONSUMO_LPS`. Os comentários do código dão exemplos de TIPO: CORTADO, FICTÍCIA, LIGADO, LIGADO EM ANÁLISE, SUPRIMIDO, cada um com "sim/não" de hidrometrado.

### 2.2 O que ele grava, e onde

1. Para cada linha marcada, executa **um UPDATE** (três dialetos: SQL Server, Oracle, PostgreSQL) em **`RAMAIS_AGUA_LIGACAO`**, coluna **`CONSUMO_LPS`**, para as ligações cujo ramal está em `POLIGONO_SELECAO` do usuário **e** cujo TIPO e HIDROMETRADO coincidem com a linha marcada.
2. Valor: o mesmo número para todas as ligações do grupo. Se a unidade for M³/mês, multiplica por **0,00038580246** (= 1000/(30·24·3600) = 1/2592, mês de 30 dias); se for LPS, usa direto; valor ≤ 0 grava zero.
3. Executa na conexão **`Conn`** (banco do GEOSAN), **não** em `ConnSec` (conexão do banco comercial).
4. Depois só recarrega a grade. **Não recalcula `WATERCOMPONENTS.DEMAND`.**

### 2.3 Prova de que é demanda do modelo e não consumo faturado

- Especificação 07.00.31 (`doc/01`): `CONSUMO_LPS` = "consumo da ligação em litros por segundo"; `WATERCOMPONENTS.DEMAND` = "demanda de uso em litros por segundo"; fórmula de conversão "Demanda LPS = ((Demanda mensal × 1000)/30)/24/3600".
- Cadeia de uso, em `frmAtualizacaoConsumo` ("Atualizações de Consumo", menu Arquivo → "Atualizar Consumos e Distribuir Demandas"): (1) copia o consumo médio da view comercial para `RAMAIS_AGUA_LIGACAO.CONSUMO_LPS` com o mesmo fator; (2) "Distribuir as demandas de consumo, em l/s, em todos os nós das redes": zera `DEMAND` de todos os nós, soma `CONSUMO_LPS` por trecho do ramal e põe **metade em cada nó** do trecho. É esse `DEMAND` que o exportador lê para `[JUNCTIONS]` (06, seção 5.7).
- Manual 02 (item 7.7): "Exportar para Epanet … transportando … dados de consumo"; "Alterar Consumo de Ligações" aparece como irmã de "Exportar para Epanet" no mesmo menu de operações por polígono. O comentário do carregador de polígono diz que a seleção serve "para gerar relatórios ou exportar para o Epanet".
- **Faturamento intocado:** o formulário só emite UPDATE sobre uma tabela do GEOSAN. Nas leituras comerciais, `ConnSec` executa SELECT (consumo faturado/medido, dados de ligação, consumidores), com **uma única exceção de escrita**: a tabela temporária `GS_TEMP` do isolamento (seção 1, linha 9). Não achei nenhum UPDATE/INSERT/DELETE do GEOSAN em tabela de faturamento. O dado faturado vive em views `NXGS_V_*` lidas por "Consultar Consumo". (Limite: não reli o texto das consultas `GS_QUERYS_CLIENT`; 05 as descreve como consultas.)

### 2.4 Conclusões adicionais sobre o comportamento

- **Efeito volátil.** "Atualizar Consumo das Ligações Cadastradas" regrava `CONSUMO_LPS` de **todas** as ligações com correspondência na view comercial e apaga, na prática, o ajuste por polígono. Sobrevive só o que não casa com a view (por exemplo, as FICTÍCIA). Portanto é um **ajuste de cenário**, não um cadastro.
- **Controles que o GEOSAN não tem** (e que seriam desejáveis até para parâmetro de modelo): confirmação com contagem (existe só em bloco comentado), transação, log (`USUARIO_LOG`/`DATA_LOG` não são atualizados), desfazer, versão. Erros são engolidos por tratador "continuar" para os códigos 0 e 20.
- **Unidade:** o valor é **por ligação**, não por economia, e o fator pressupõe mês de 30 dias.

### 2.5 O que muda na recomendação do relatório

| Trecho do relatório | Situação |
|---|---|
| Evidência: "seleção espacial e tratamento de consumo em lote" | **Corrigir** para "atribuição de demanda média (L/s) por ligação, por polígono, para o modelo hidráulico". |
| Risco: "Não executar alterações de consumo … em lote sem prévia, aprovação, auditoria e rollback lógico" | A boa prática **continua válida**, mas a **premissa (consumo faturado) é falsa**. Reclassificar: o objeto de governança é um **cenário de demanda** do modelo hidráulico, não a fatura. Rollback = descartar/superar o cenário, não estornar cobrança. Aprovação deve ser proporcional (modelo ≠ conta). |
| Item 4 (perdas): "consulta e alteração de consumo por polígono" | Não é insumo de **balanço hídrico/perdas aparentes**. Alterar demanda de modelo não pode contaminar volume faturado. Separar dois conceitos no Ziip: `billed_consumption` (imutável, vem do comercial) e `modeled_demand` (versionado, com escopo, origem e unidade). |
| Item 3: "gerador de cenário EPANET a partir de snapshot aprovado de rede, demanda e condições de contorno" | **Alinha** com o GEOSAN, mas o relatório não percebe que a edição por polígono **é** a forma de montar a demanda desse cenário. Recomendo que a edição em lote de demanda seja funcionalidade do módulo EPANET/cenários, não do domínio comercial. |
| Backlog | Rebaixar "alteração em lote de consumo" de risco comercial para recurso de cenário hidráulico; remover qualquer acoplamento com faturamento. Guardar a demanda em unidade canônica (m³/mês ou L/s) com a conversão explícita e o número de dias como parâmetro, não o fator 1/2592 fixo. |

---

## 3. O que o relatório OMITE do GEOSAN e importa

Comparação com 05 (banco/rede) e 06 (funcionalidades/EPANET), que já cobrem a maior parte. Itens marcados **[NOVO]** não estão em 05/06.

| # | Omissão | Onde está documentado / evidência | Por que importa |
|---|---|---|---|
| 1 | **ValidaBase** (13 verificações) e `frmVerificaConectividade` | 05 seção 9 e 10.3; 06 seção 3.4. `Projeto_ValidaBase/Leiame.txt` lista os 13. O doc `07` o põe como **passo obrigatório antes da exportação EPANET**. | O relatório propõe "validador de rede" sem citar que o GEOSAN o tinha como pré-condição de exportação. |
| 2 | **Metadados de componentes e parâmetros EPANET** (`TYPES/SUBTYPES/SELECTIONS/DATA`, `SPECIFICATION_` = JUNCTION/PUMP/VALVE/REGISTER/RNF/RNV, `EPAREF`) | 05 seção 4 e 6; 06 seção 5.4 | O relatório modela "Equipamento" genérico, sem a classe hidráulica que define o comportamento no modelo e no isolamento. |
| 3 | **Regras topológicas de edição**: trecho exige 2 nós; componente sobre o trecho o divide; proibido trecho duplicado; mover nó move ramais; ramal ancorado por distância da testada/lado; snap; "ponta seca" | 05 seção 2.2 e 3.3; 06 seção 2.2 | O relatório diz "snapping, validação topológica" sem as regras. |
| 4 | **"Tema com WHERE"** (`te_theme.generate_attribute_where`) | 05 seção 8. Evidência nova: **usado na exportação DXF por tema** (`FrmExport`) | É o mecanismo de "camada filtrada" que o relatório pede como "exportação filtrada por camada". |
| 5 | **Contrato com o comercial** (3 views `NXGS_V_*`, chaves com/sem DV, inscrição, `STRING_CONNECTION_SECUNDARY`, modo ETL x tempo real) e a **regra de que o GIS lê e não grava no comercial** | 05 seção 5 | O relatório não descreve o contrato nem essa fronteira, justamente o que evitaria o erro do ponto especial. |
| 6 | **Demanda → EPANET**: `CONSUMO_LPS` → `DEMAND`, conversão, ½ por nó, rugosidade por material, cota por MDT/bilinear, padrões e curvas, limites do `.inp` gerado | 06 seções 5.4 a 5.8 | O relatório só fala em "snapshot de rede, demanda e condições de contorno". |
| 7 | **Operações por polígono + `POLIGONO_SELECAO`** (staging por usuário) | 05 seção 7 (nota 5.9.5); 06 seção 2.5 | É o padrão "seleção → ação em lote". O relatório a trata como "cadastro em lote". |
| 8 | **Exportação shape desnormalizada** (`GS_*`) e exportação de consumos por mês/ano | 06 seção 2.6 | Alternativa real de interoperabilidade, ausente na proposta de "exportações assíncronas". |
| 9 | **Produtividade de cadastro** (`USUARIO_LOG`/`DATA_LOG`, relatórios) | 05 seção 5.1 | Base natural da "campanha de campo" que o relatório propõe. |
| 10 | **Licença GPL v2 e credenciais em texto claro** | 05 seção 1.3; 06 seção 1 | O relatório fala só em "verificar licença"; a licença é GPL-2.0-only declarada, e há segredos expostos. |
| 11 | Ligações fictícias; cadastro automático a partir de GPS (7.3.3+) | 06 seção 2.3 | Resolve o caso "lote sem ligação" e "campo". |
| 12 | Tabelas de **manobra** (`manobra`, `manobraSetor`, `NXGS_MANOBRA`) | 05 seção 3.3 (só nomes de objeto no `.bak`) | Candidato a base do "incidente/comunicação" do item 2. Não li nenhum formulário que as use. |
| 13 **[NOVO]** | **Defeitos a não herdar**, achados nesta leitura: (a) isolamento **não verifica o estado aberto/fechado** do registro nem trata malhas; (b) na variante **PostgreSQL** da distribuição de demanda, o UPDATE do nó **atribui** o valor em vez de **somar** (nó com vários trechos fica só com o último), diferente da variante SQL Server; (c) a variante PostgreSQL de "atualizar consumo" usa `CONSUMO_MEDIDO`, e um comentário do desenvolvedor (2012-08-21) diz que "provavelmente está errada"; (d) no cadastro por GPS, `VOL_FATURA` é gravado em `CONSUMO_LPS` **sem conversão de unidade**; (e) `frmAlteraConsumoPorPoligono` sem confirmação nem log. | `clsGeoReference.SELECTRede`; `frmAtualizacaoConsumo`; `frmCanvas` (inserção GPS); `frmAlteraConsumoPorPoligono` | Se o Ziip for reimplementar a distribuição de demanda, não herdar essas inconsistências. Sugestão: acrescentar (b) a 06 seção 5.7. |
| 14 **[NOVO]** | **Refino do modelo de chaves em 05**: nos formulários, `INITIALCOMPONENT`/`FINALCOMPONENT` são resolvidos contra `WATERCOMPONENTS.OBJECT_ID_`, e novos nós são gravados com `COMPONENT_ID = OBJECT_ID_` (mesmo valor). | `clsGeoReference.SELECTRede`; `frmAtualizacaoConsumo`; `clsTerraLib` (inserção de nós) | 05 afirma que apontam para `COMPONENT_ID`. Na prática os dois coincidem; vale uma nota para quem for importar dados legados. |

---

## 4. Conclusão: confiabilidade do trecho sobre o GEOSAN

**MÉDIO-BAIXO.**

- **A favor:** todos os caminhos de arquivo existem; os formulários de ramal, o DXF (tema/plano/itens), a lista de desabastecidos com telefones e a exportação em texto estão **corretos**. O relatório marca corretamente que topologia/EPANET "é estimativa" sem solver.
- **Contra:**
  1. leu o **nome** do formulário sem ler o **SQL**: `frmAlteraConsumoPorPoligono` virou "consumo em lote" faturável quando é **demanda do modelo hidráulico**, e disso derivou um risco mal-fundamentado;
  2. dois "formulários" têm função distinta da descrita (`AutoLote` = filtro por lote/parcela; `AtualizarSetores` = carregar polígono de seleção), e o item "ramal em lote" não existe;
  3. confunde o que o GEOSAN faz (busca em grafo até o registro, 1 trecho) com o que o relatório propõe (simular isolamento por válvulas, comunicação segmentada, tarifa social), sem separar herança de desenho novo;
  4. afirma que o doc `07` não pôde ser convertido, mas ele abre por Word COM;
  5. omite o que há de mais valioso e já levantado em 05/06 (ValidaBase, metadados EPANET, regras topológicas, tema com WHERE, contrato com o comercial, GPL v2).
- **Uso recomendado:** aproveitar o inventário de funcionalidades como lista de **temas**, mas **não** usar as descrições nem o item de risco "consumo em lote" como base de decisão. Para o GEOSAN, 05, 06 e este documento prevalecem sobre o trecho do relatório.
