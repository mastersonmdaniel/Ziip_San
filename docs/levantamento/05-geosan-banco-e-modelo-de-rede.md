# 05 - GEOSAN: banco de dados, metadados e modelo de rede

Levantamento de domínio para o SIGSAN (intelligence/sigsan-gis, operations/assets, intelligence/sigsan-losses, shared/core).
Fonte: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GEOSAN` (somente leitura). Data do levantamento: 11/10/2026.

Convenções: **[FATO]** = lido diretamente na fonte (caminho citado). **[INFERÊNCIA]** = dedução minha, sem confirmação na fonte.

---

## 0. Resumo executivo

1. O GEOSAN não é um modelo GIS "puro": é um **modelo cadastral metadata-driven** sobre **TerraLib 3.3.1** (VB6). Geometrias ficam em tabelas TerraLib (`LinesN`, `PointsN`, `PolygonsN`, `TextsN`), atributos de negócio em tabelas próprias (`WATERLINES`, `WATERCOMPONENTS`...), ligados por `OBJECT_ID_`. **Não há PostGIS** na versão Postgres 8.4 do template.
2. A rede é um **grafo nó-trecho por chave de negócio**, não por geometria: `WATERLINES.INITIALCOMPONENT/FINALCOMPONENT` apontam para `WATERCOMPONENTS.COMPONENT_ID`. A consistência geometria x atributo x topologia é verificada a posteriori pelo `ValidaBase` (13 processos).
3. Tipos de componente, propriedades por tipo, domínios (materiais, estado, localização...) e o **mapeamento para EPANET** (`Specification_`, `EPAREF`) vivem em **tabelas de metadados** (`*TYPES`, `*SUBTYPES`, `*SELECTIONS`, `*DATA`), no padrão EAV. Isso deu flexibilidade, mas custa integridade e desempenho.
4. **Ramal = geometria de ligação trecho -> lote**; **ligação = linha filha do ramal** (`RAMAIS_AGUA_LIGACAO`), amarrada ao comercial por `NRO_LIGACAO` (no GSAN, `imov_id` = matrícula) e `INSCRICAO_LOTE`/`CLASSIFICACAO_FISCAL` (inscrição formatada).
5. O contrato GSAN <-> GEOSAN são **3 views** (`NXGS_V_LIG_COMERCIAL`, `..._CONSUMO`, `..._COM_CONSUMO_MEDIO`) com definição documentada e uma implementação concreta sobre o esquema do GSAN (documento Postgres-LogPro). Está inteiramente mapeado na seção 5.
6. A evolução 5.9.x -> 7.5.x revela requisitos reais de operação: simulação de **consumidores desabastecidos por manobra**, exportação **EPANET**, distribuição de **demanda por consumo médio**, **produtividade de cadastro** (log de quem/quando), **cota Z por MDT**, validação de base, índices de desempenho.
7. **Licença: GPL v2** (cabeçalho-modelo em `doc/20141027141200-cabeçalhoGPL.txt`). Risco de contaminação **alto se copiar código**; **baixo se usar só conceitos, modelo e regras** (ver seção 1.2).
8. O `V1__create_gis_schema.sql` atual do SIGSAN **tem erros que o impedem de rodar** e **não está no location de Flyway de tenant** (ver seção 10.2). Isso é mais urgente que qualquer enriquecimento de domínio.

---

## 1. Fontes, método e licença

### 1.1 O que foi lido

| Fonte | Como foi lido | Resultado |
|---|---|---|
| `db/Postgres/banco_geosan.backup` | `pg_restore -s` e `-a` num container `postgres:16-alpine` sobre **cópia** no scratchpad (original intocado) | Esquema completo (132 tabelas, PG 8.4.1) e dados de metadados. Banco **template** (sem rede cadastrada, sem dados em `lines*`/`points*`). |
| `db/Postgres/*.txt` (04, 05, 06) | texto (latin1) | Configuração de conexão, bbox, tabelas comerciais; "Modelo de dados compatível com GeoSan **6.0.0**" |
| `db/Oracle/banco_geosan.dmp` | extração de strings (export Oracle 9.2, 21/03/2011) | DDL de `LINES1`, `POINTS2`, `RAMAIS_*`, `WATER*` para comparar |
| `db/SqlServer/*.txt` | texto | DDL de `GS_NOS`, `GS_REDES`, `GS_RAMAIS`, `GS_CONSUMIDORES` (7.4.0) e alterações 7.05.05 |
| `db/SqlServer/*.bak` (7.0.31, 7.4.01, 7.05.05) | só o 7.05.05 (17 MB), varredura de nomes em UTF-16 | Apenas nomes de objetos (ver 3.5). **Não restaurado.** Os .bak de 44 MB e 237 MB não foram lidos. |
| `db/historico de alteracoes banco de dados.txt` | texto | **Contém uma única alteração (2012-10-04)**, não é histórico 5.9->7.4. O histórico real está em `doc/00. Leiame.txt` e nos scripts `sql/`. |
| `sql/*.sql` (7 arquivos) | texto, 5 em latin1 e 2 em UTF-8 | Metadados de componentes de água, parâmetros EPANET, colunas de log |
| `trunk/SQL dados dos nos.sql` | UTF-16 LE | Consultas de nós/valores/estado de registro |
| `doc/00. Leiame.txt` (1252 linhas) | texto | Notas de liberação 5.9.0 -> 6.10.27 (2013) com regras de negócio |
| `doc/01 - Especificacao de software Geosan - 07.00.31.doc` | extração de strings (Word 97, sem conversor) | Relação de tabelas, descrição de campos, mapeamento GeoSan x EPANET, desabastecimento |
| `doc/20150210285800-Especificações para criar vistas do comercial...doc` | extração de strings | Contrato das 3 views |
| `doc/20150219103100-GEOSAN Procedimentos - Implantação com Postgres-LogPro.docx` | markitdown | Views concretas sobre o GSAN, importação de mapas, SRID |
| `doc/20130325...querie para atualizar a clausula where de um tema.sql` | texto | Conceito de tema com WHERE |
| `trunk/Projeto_ValidaBase/*` (Leiame, Main.frm) | leitura de código VB6 (somente para entender regras) | 13 processos + funções |
| `trunk/Projeto_ExportEpaNe/Classes/Rugosidade.cls`, `trunk/Forms/frmVerificaConectividade.frm` | idem | Rugosidade por material, verificação de conectividade |

### 1.2 O que NÃO foi lido

- **`.vsd` (Visio)**: `20130513103800-modelo físico redes-ramais-ligações-A.vsd`, `20131211042000-uml db.vsd`, diagramas de sequência. Formato binário comprimido, sem conversor. Do `.vsd` só consegui o autor (José Maria Villac Pinheiro) e a palavra `WATERLINES`. **O modelo físico foi reconstruído a partir do `pg_restore` e do documento de especificação**, o que é mais confiável que o diagrama.
- `.doc` de manuais (02 a 07), `GEOSAN SOFTWARE LIVRE PARA REDUCAO DE PERDAS.pdf`, `.chm` do TeCom, PDFs de MDT/interpolação bilinear. Fora do escopo desta fatia (outros relatórios do levantamento podem cobrir).
- Backups SQL Server 7.0.31 e 7.4.01. O modelo **mais novo** (7.4/7.5) só aparece parcialmente (nomes de objetos e 4 DDLs); o `.backup` Postgres é de uma versão **6.0.0** (mais antiga). Há **deriva de esquema entre versões e entre SGBDs** (seção 3.6).
- Código-fonte dos módulos VB6 (`NxViewManager`, `PManager4`, `NSecurity`) além do estritamente necessário.

### 1.3 Licença e risco de contaminação

| Item | Evidência | Avaliação |
|---|---|---|
| Licença declarada | `doc/20141027141200-cabeçalhoGPL.txt`: **GNU GPL versão 2**, "Copyright (C) 2006-2014 the GeoSan", autor José Maria Villac Pinheiro. `ValidaBase.vbp` registra `VersionCompanyName="NEXUS GeoEngenharia e Com. Ltda."`. | [FATO] |
| Arquivo LICENSE/COPYING na raiz | **Não existe** na raiz do repositório. | [FATO] |
| Cabeçalho GPL nos fontes | Conferi 3 arquivos (`Modules/*.bas`, `frmMain.frm`, `ValidaBase/Main.frm`): **sem cabeçalho**. A busca recursiva em `trunk/` estourou o tempo (Drive virtual), então **não está verificado no restante**. | [FATO] parcial |
| Origem | `.git/config`: remote `https://softwarepublico.gov.br/gitlab/gsan/geosan.git` (Portal do Software Público Brasileiro), 19 commits locais | [FATO] |
| Dependências | TerraLib/TeComponents (`TerraComponents 3.3.1.13.msi`, `TeComDatabase.dll`...), `LoozeXP.ocx`, ícones com licença própria ("texto da licença de uso das ícones" no Leiame) | [FATO] existência; licenças de terceiros **não verificadas** |
| Credenciais em claro | `doc/` e `00. Leiame.txt` contêm senhas de banco e SMTP em texto. **Não reproduzi nenhuma.** | [FATO] |

**Risco**: GPL v2 é copyleft forte. Reutilizar código VB6, trechos de SQL de `GS_QUERYS_*` ou a estrutura literal dos scripts em código SIGSAN pode atrair obrigações de distribuição. **Mitigação recomendada**:

- Absorver **conceitos, regras de negócio e modelo de dados** (ideias não são protegidas por copyright de código); reescrever tudo com nomes e estrutura próprios (clean room: quem escreve não copia).
- Valores **públicos de engenharia** (coeficientes Hazen-Williams, parâmetros de EPANET, códigos de válvula PRV/PSV/PBV/FCV/TCV/GPV) vêm do domínio técnico/EPANET, não do GEOSAN.
- Listas de domínio (tipos de componente, estados) são fatos de domínio; ainda assim **reescrever** com taxonomia SIGSAN, e não colar o script.
- Não importar `.backup`/`.bak`/`.dmp` do GEOSAN em produção nem em repositório.
- Se um dia for desejável reaproveitar código, pedir parecer jurídico e/ou licença dual à NEXUS/autor.

---

## 2. Arquitetura de armazenamento (TerraLib 3.3.1)

[FATO] `te_database.db_version = 3.3.1`. Todo o GEOSAN roda sobre as tabelas de catálogo da TerraLib (`te_*`), que descrevem camadas, representações, temas e visões.

| Conceito TerraLib | Tabela | Papel no GEOSAN |
|---|---|---|
| Projeção | `te_projection` | 20 linhas idênticas: **UTM, meridiano central -45 (fuso 23), datum SAD69, escala 0,9996, unidade METERS** no template. Outras instalações usam SIRGAS 2000 fuso 22S (doc Postgres-LogPro). |
| Layer (camada de dados) | `te_layer` | 11 layers no template (tabela abaixo). Guarda bbox (`lower_x..upper_y`). |
| Representação geométrica | `te_representation` | Liga layer a tabela de geometria (`geom_table`) com `geom_type`: **1 polígono, 2 linha, 4 ponto, 128 texto** [INFERÊNCIA pela correlação com `Polygons11`, `Lines1`, `Points2`, `Texts1`]. |
| Tabela de atributos do layer | `te_layer_table` | `attr_table`, `unique_id=object_id_`, `attr_link=object_id_`. 8 entradas (redes, componentes, ramais). |
| Tema (camada de visualização) | `te_theme` | Layer + view + **cláusula WHERE** (seção 8). |
| Vista (workspace do usuário) | `te_view` | 1 por usuário (`user_name`, `visibility`, bbox). |
| Legenda/visual | `te_legend`, `te_visual`, `te_grouping`, `te_theme_application` | Simbologia, agrupamento por atributo, gráficos. |
| Relação com tabela externa | `te_tables_relation`, `te_theme_table` | Join de tema com tabela externa (vazias no template). |

### 2.1 Armazenamento de geometria (nível físico)

| SGBD | Linha (`LINESn`) | Ponto (`POINTSn`) | Observação |
|---|---|---|---|
| PostgreSQL 8.4 (template) | `geom_id`, `object_id`, `num_coords`, `spatial_box box`, `ext_max`, `spatial_data polygon` (os vértices da linha em tipo `polygon` nativo do PG) | `geom_id`, `object_id`, `spatial_box box`, `x`, `y` | Índice btree em `object_id` e (textos) em `(x,y)`. **Nenhum GiST.** |
| Oracle 9.2 (dmp) | `LOWER_X/LOWER_Y/UPPER_X/UPPER_Y` numéricos + `SPATIAL_DATA BLOB` | `X`, `Y` NUMBER(*,15) | Sem Oracle Spatial. |
| SQL Server | análogo ao Oracle (a validação usa `lower_x <= x+prec and upper_x >= x-prec`) | idem | ValidaBase consulta o bbox da linha por SQL. |

[FATO] `ValidaBase/Main.frm` busca trechos "encostados" num nó filtrando por bbox da linha (`lower_x`, `upper_x`...), o que confirma que **a busca espacial era feita por colunas de bbox, sem índice espacial**.

**Lição para o SIGSAN**: tudo isso (bbox manual, tabela de geometria separada por layer, `object_id` como string) é substituído por PostGIS (`geometry(LineString,4674)` + GiST). O que vale absorver é o **desenho lógico** (o que é nó, trecho, ramal, ligação, tema), não o armazenamento.

### 2.2 Layers do template

| layer_id | Nome | Tabela de geometria | Papel (`GS_LAYER_TYPE_REFERENCE`) |
|---|---|---|---|
| 1 | WATERLINES | Lines1 (+ Texts1) | Trechos da rede de água |
| 2 | WATERCOMPONENTS | Points2 (+ Texts2) | Componentes de rede de água |
| 3 | SEWERLINES | Lines3 | Trechos da rede de esgoto |
| 4 | SEWERCOMPONENTS | Points4 | Componentes da rede de esgoto |
| 5 | DRAINLINES | Lines5 | Trechos de drenagem |
| 6 | DRAINCOMPONENTS | Points6 | Componentes de drenagem |
| 7 | RAMAIS_AGUA | Lines7 + Points7 | Ramais de água (linha do ramal + ponto do hidrômetro) |
| 8 | RAMAIS_ESGOTO | Lines8 + Points8 | Ramais de esgoto |
| 9 | DOCUMENTOS | Points9 | Documentos georreferenciados (tabela `X_FILES`) |
| 10 | QUADRANTE_REF | Points10 | Quadrante/célula de referência (`CEL_PONTO_ATRIB`, DXF) |
| 11 | LOTEGIS | Polygons11 (+ Texts11) | Lotes (polígonos) |

`GS_LAYER_CONFIG_LAYERS` define **operações entre layers** (ex.: desenhar ramal de água = snap no trecho de água (layer 1) + localizar o lote sob o clique (layer 11); desenhar componente = referência ao trecho (layer 1); pesquisar lote retorna o ramal de água e de esgoto). Ou seja: **as regras de edição topológica eram configuração em tabela**.

---

## 3. Esquema completo

### 3.1 Resumo quantitativo

| Grupo | Tabelas | Observação |
|---|---|---|
| TerraLib `te_*` | ~25 | catálogo (layers, temas, visões, legendas, OWS/WMS vazias) |
| Geometria `lines*`, `points*`, `polygons*`, `texts*` (+`*_txvisual`) | 41 | 1 trio por layer + textos |
| Negócio de rede (água, esgoto, drenagem) | 30 | `*LINES`, `*COMPONENTS` x (principal, DATA, SELECTIONS, SUBTYPES, TYPES) x 3 redes |
| Ramais/ligações | 4 | `RAMAIS_AGUA`, `RAMAIS_AGUA_LIGACAO`, `RAMAIS_ESGOTO`, `RAMAIS_ESGOTO_LIGACAO` |
| Integração comercial | 5 | `NXGS_V_LIG_COMERCIAL`, `_E`, `_CONSUMO`, `_COM_CONSUMO_MEDIO`, `NXGS_FILT_TEMA` |
| Configuração e apoio `GS_*`, `X_*` | ~30 | domínios, queries, parâmetros, temporárias |
| Segurança | 2 | `SYSTEMUSERS`, `TERRACONFIG` |

### 3.2 (a) Tabelas de METADADOS

| Tabela | Colunas-chave | Função |
|---|---|---|
| `WATERCOMPONENTSTYPES` / `SEWERCOMPONENTSTYPES` / `DRAINCOMPONENTSTYPES` | `ID_TYPE` PK, `DESCRIPTION_`, `SPECIFICATION_` | Catálogo de tipos de componente. `SPECIFICATION_` = **classe EPANET/comportamento**: JUNCTION, PUMP, VALVE, REGISTER, RNF, RNV. |
| `WATERLINESTYPES` / `SEWERLINESTYPES` / `DRAINLINESTYPES` | idem | Tipos de trecho (adutora, coletor...). |
| `*SUBTYPES` (6 tabelas) | `(ID_TYPE, ID_SUBTYPE)`, `DESCRIPTION`, `SELECTION_` (é lista?), `MAX_`, `MIN_`, `DEFAULTVALUE`, `DATATYPE`, `EPAREF` | **Definição de propriedade por tipo** (nome, limites, padrão, tipo de dado, **nome do parâmetro EPANET**). |
| `*SELECTIONS` (6 tabelas) | `(ID_TYPE, ID_SUBTYPE)`, `OPTION_`, `VALUE_`, `DESCRIPTION_` | **Opções** de propriedades do tipo lista. `DESCRIPTION_` guarda, no template, a sigla EPANET da opção (VR, FCV, GPV, PRV, PSV, TCV). |
| `*DATA` (6 tabelas) | `(OBJECT_ID_, ID_TYPE, ID_SUBTYPE)`, `VALUE_ varchar(200)` | **Valores** das propriedades por objeto (EAV). |
| `X_MANAGERPROPERTIES` | `TABLENAMEIN`, `FIELDNAMERIN`, `TABLENAMEOUT`, `FIELDNAMEREL`, `FIELDNAMEOUT` | Mapeia coluna de atributo -> tabela de domínio `X_*` para o "Gerenciador de Atributos" (combo). 67 linhas. |
| `X_MANAGERPROPERTIESB` | `TABLENAME`, `FIELDNAME`, `FIELDSEQUENCE` | Ordem das colunas exibidas. |
| `X_LAYERGEOMAIN`, `X_LAYERSCOMPONENTS` | layer de linha <-> layer de componente | Qual componente pertence a qual rede; qual geometria é "principal" (2 = linha, 4 = ponto). |
| `GS_LAYER_CONFIG`, `GS_LAYER_CONFIG_LAYERS`, `GS_LAYER_TYPE_REFERENCE` | `LAYER_ID`, `TYPE_GEOM_MAIN`, `TYPE_REFERENCE`, `TYPE_TEXT`, `TYPE_OPERATION` | Papel funcional de cada layer e relações de edição. |
| `GS_QUERYS_SYSTEM` | `QUERY_ID`, `NAME`, `DESCRIPTION`, `REFERENCE_LAYER` | Catálogo de consultas funcionais (21 linhas): `CONSUMO_LOTE`, `LIGACOES_LOTE`, `INSCRICAO_LOTE`, `LIGACOES_POR_LOGRADOURO/PROPRIETARIO/LIGACAO/CLASSIF_FISCAL`, `LIGACOES_CAD_RAMAL_DIFERENTE`, `RETORNA_MAIOR_OBJECT_ID_RAMAL`, `CONSUMO POR TUBULACOES`... |
| `GS_QUERYS_CLIENT` | `(CLIENT_ID, QUERY_ID)`, `QUERYSTRING varchar(1000)` | **SQL por cliente e por dialeto**, com parâmetros `@TOKEN`. Um mesmo `QUERY_ID` tem implementações diferentes por operadora. |
| `GS_CLIENT` | `CLIENT_ID`, `NAME` | Identifica a operadora (template: id 0). |
| `GS_PARAMETERS` | `STRING_CONNECTION_SECUNDARY`, `DEMAND_UPDATE_LINE_ID`, `DEMAND_LAST_UPDATE_LINE_ID` | Parâmetros de sistema (seção 6). |
| `NX_BASE` | `VERSAO` | Versão do modelo de dados. |
| `X_CURVES`, `X_PATTERNS` | `ID`, `DESCRICAO`, `TIPO`, `COORDENADA_X/Y` (csv), `PADRAO` (csv) | Curvas (bomba, rendimento, volume, perda) e padrões temporais de consumo, para EPANET. |
| `SYSTEMUSERS`, `TERRACONFIG` | `USRID`, `USRLOG`, `USRPWD`, `USRFUN`, `USRDEP`, `USREXP`, `USRBRK`, `USRMAIL`...; `TEC_USER`, `TEC_XMIN..YMAX` | Usuários e janela de visualização por usuário (seção 6.3). |

### 3.3 (b) Tabelas de DADOS DE REDE E CADASTRO

#### Nós (componentes) de água: `WATERCOMPONENTS` [FATO, template PG]

| Coluna | Tipo | Sentido |
|---|---|---|
| `OBJECT_ID_` | varchar(10) PK | chave da **geometria** (`POINTSn.object_id`) |
| `COMPONENT_ID` | int | **id topológico do nó** (referenciado por `INITIALCOMPONENT`/`FINALCOMPONENT`) |
| `ID_TYPE` | int | tipo (FK lógica para `WATERCOMPONENTSTYPES`) |
| `YEAROFCONSTRUCTION`, `DATEINSTALLATION` (varchar) | | ano/data de instalação |
| `STATE`, `LOCATION`, `SUPPLIER`, `MANUFACTURER`, `MATERIAL` | int | domínios `X_STATE`, `X_LOCATION`, `X_SUPPLIERS`, `X_MANUFACTURERS`, `X_MATERIAL` |
| `INITIALGROUNDHEIGHT`, `FINALGROUNDHEIGHT` | int | cota do terreno (no Oracle: `GROUNDHEIGHT`, `GROUNDHEIGHTFINAL`) |
| `DEEPNESS` | int | profundidade (cota do nó = cota do terreno - profundidade) |
| `DEMAND` | int | demanda (mensal; conversão para L/s na exportação) |
| `PATTERN` | int | padrão de consumo (`X_PATTERNS`) |
| `SECTOR` | int | setor de abastecimento (sentinela 999 = "criado automaticamente pelo ValidaBase") |
| `CALCULENODE` | int | flag de nó "calculável" para distribuição de demanda [INFERÊNCIA] |
| `TROUBLE` | int | **não conformidade** (`X_YES_NO`) |
| `INFORMATIONVALIDITY` | int | validade/qualidade da informação |
| `NOTES` | varchar(2000) | |

`SEWERCOMPONENTS` e `DRAINCOMPONENTS` têm estrutura equivalente (com `ESPECIAL`, `ROUGHNESS` em drenagem; tipos divergem entre redes: seção 3.6).

#### Trechos de água: `WATERLINES` [FATO]

| Coluna | Tipo | Sentido |
|---|---|---|
| `OBJECT_ID_` | varchar(10) | chave da geometria (`LINESn.object_id`) |
| `LINE_ID` | int | id numérico do trecho |
| `ID_TYPE` | int | adutora / sub-adutora / distribuição / água bruta / desconhecido |
| `INITIALCOMPONENT`, `FINALCOMPONENT` | int | **nós inicial e final** (= `WATERCOMPONENTS.COMPONENT_ID`) |
| `INTERNALDIAMETER`, `EXTERNALDIAMETER`, `THICKNESS` | double | geometria da tubulação |
| `MATERIAL` | int | `X_MATERIAL` |
| `LENGTH`, `LENGTHCALCULATED` | double | comprimento informado vs medido da geometria. **Na exportação EPANET vale `LENGTHCALCULATED`** quando ambos existem. |
| `INITIALGROUNDHEIGHT/FINALGROUNDHEIGHT`, `INITIALTUBEDEEPNESS/FINALTUBEDEEPNESS` | double | cota do terreno e profundidade nas duas pontas |
| `ROUGHNESS` | double | rugosidade (C de Hazen-Williams), recalculada por material |
| `SECTOR` | double | setor de abastecimento |
| `STATE`, `LOCATION`, `SUPPLIER`, `MANUFACTURER`, `TROUBLE`, `INFORMATIONVALIDITY` | | domínios/qualidade |
| `SIDESTREET`, `DIVIDEDDISTANCE` | | **lado da rua** (ímpar/par) e **distância da divisa** |
| `DATEINSTALLATION` | varchar | |
| `USUARIO_LOG`, `DATA_LOG`, `DATALOG` | | autoria (quem desenhou/atualizou, quando) |

`SEWERLINES` repete a estrutura; `INITIALCOMPONENT`/`FINALCOMPONENT` são `varchar(10)` ali (tipo diferente do de água). `DRAINLINES` é quase idêntica.

#### Ramais e ligações [FATO]

| Tabela | Colunas |
|---|---|
| `RAMAIS_AGUA` | `OBJECT_ID_` (geometria do ramal), `OBJECT_ID_TRECHO` (trecho de água ao qual se liga), `COD_LOGRAD`, `DISTANCIA_TESTADA`, `DISTANCIA_LADO`, `COMPRIMENTO_RAMAL`, `PROFUNDIDADE_RAMAL`, `POSICIONAMENTO_LOTE`, `ID`, `DATA_LOG`, `USUARIO_LOG`, `CONSUMO_LPS`, `TIPO`, `ECONOMIAS`, `HIDROMETRADO` |
| `RAMAIS_AGUA_LIGACAO` | `OBJECT_ID_` (= ramal), `NRO_LIGACAO`, `INSCRICAO_LOTE`, `AUTO`, `TIPO` (default `HIDROMETRADA`), `CONSUMO_LPS`, `HIDROMETRADO`, `ECONOMIAS` + (7.4.6) `DATA_LOG`, `USUARIO_LOG` + (7.05.05) `NRO_LIGACAO_ANTIGO`, `NRO_LIGSDV` |
| `RAMAIS_ESGOTO` | como `RAMAIS_AGUA` sem consumo/tipo/economias (`OBJECT_ID_TRECHO` aponta trecho de esgoto) |
| `RAMAIS_ESGOTO_LIGACAO` | `OBJECT_ID_`, `NRO_LIGACAO`, `INSCRICAO_LOTE`, `AUTO`, `HIDROMETRADO`, `TIPO`, `ECONOMIAS`, `CONSUMO_LPS` |

#### Lotes, setores, logradouros

| Tabela | Situação |
|---|---|
| `LOTEGIS` (polígono do layer 11) | `COD_SFISCA`, `COD_QUADRA`, `COD_LOTE`, `CLASSIFICACAOFISCAL`, `OBJECT_ID_`, zoneamento (`ZONEAMENTO`, `MACROZONEA`, `ZONEAMENT1/2`, `RESTRICAO_`, `USOS_ESPEC`, `BACIAS_HID`, `HIERARQUIZ`, `HIERARQUI1`, leis/decretos). A especificação 7.0.31 diz que "era utilizada para ligar o consumidor ao lote da Prefeitura. **Não é mais utilizada**". O lote passou a ser achado por `CLASSIFICACAOFISCAL` vinda do comercial. |
| Setor/zona/DMC | **Não existem tabelas próprias no template Postgres.** Setor é só a coluna numérica `SECTOR` em linhas e componentes. No `.bak` 7.05.05 aparecem **objetos específicos de instalação** (inferência de uso): `setores_abastecimento`, `manobra`, `manobraSetor`, `NXGS_MANOBRA`, `GSETORZONA`, `KSETORCODIGO`, `QUADRAS`, `LOTES_BAIRRO`, `LOTES_TIPO`, `MDT`, `Nascente`, `LOTESXREDE`. Existência confirmada por nome; **estrutura não lida**. |
| `LOTESXREDE` | Usada em `GS_QUERYS_CLIENT` 20 e 21 (junta classificação fiscal -> `OBJECT_ID_` de trecho): **view/tabela de lote x trecho de rede** que não está no template; existe no .bak 7.05.05. |
| Logradouro | só `COD_LOGRADOURO`/`COD_LOGRAD` em ramais e comercial. Não há geometria de logradouro no modelo; nomes de rua entram como **textos** (DXF importado em `TextsN`). |
| Quadra | só `COD_QUADRA`, e polígono via DWG/shape importado como tema (doc Postgres-LogPro). |

#### Desnormalizadas 7.4.0 (SQL Server) [FATO]

`GS_NOS`, `GS_REDES`, `GS_RAMAIS`, `GS_CONSUMIDORES` (`db/SqlServer/20170824162400-Historico_modificacoes_...v.7.4.0`): tabelas **planas, sem chaves**, com nomes de coluna de 8 caracteres e `X`/`Y` explícitos; servem à **exportação para shapefile/web** (leiame 6.08.02: "desnormaliza as tabelas torre para uma tabela de atributos única"). `GS_NOS.cota`, `.demanda`, `.notacao`, `GS_REDES.noinic/nofinal/diamint/diamext/rugosid/ladorua/distdiv`, `GS_CONSUMIDORES` replica a view comercial com `NROL_SDV`, `INSCLOTE`, `CONS_LPS`, rota/grupo/sequência de leitura, consumo faturado/medido.

### 3.4 Chaves e índices

- **Quase nenhuma PK/FK física** no template Postgres além das de domínio (ex.: `X_MATERIAL`, `X_STATE`). Os relacionamentos são **lógicos** (por convenção de coluna).
- Índices reais no template: `te_idx_*` (btree em `object_id`, `(x,y)` de textos, nomes de layer/tema/vista).
- Índices que o produto **acabou adicionando por desempenho** (Leiame 6.00.07.x/6.09.x), todos por timeout de exportação/relatórios:
  - `WATERLINES (object_id_, material)`
  - `X_MATERIAL (materialid)`
  - `POLIGONO_SELECAO (object_id_)` (um índice não-clustered; resolveu "time-out" no export EPANET, 6.09.07)
  - `RAMAIS_AGUA_LIGACAO (nro_ligacao)` e `(object_id_)`
  - `NXGS_V_LIG_COMERCIAL (nro_ligacao, nro_ligacao_sem_dv)` e `NXGS_V_LIG_COM_CONSUMO_MEDIO (nro_ligacao, nro_ligacao_sem_dv)`

### 3.5 Geometria x PostGIS

Ver seção 10. Em resumo: o GEOSAN tinha 4 tipos (polígono, linha, ponto, texto). Texto (rótulos) era geometria própria; no SIGSAN vira **atributo de simbologia/rótulo** no front, não tabela.

### 3.6 Deriva de esquema (importante para qualquer importador)

| Item | PG template (6.0.0) | Oracle dmp | SQL Server 7.x |
|---|---|---|---|
| `WATERLINES.INITIALCOMPONENT` | int | `NVARCHAR2(10)` | n/d |
| `WATERCOMPONENTS` cota | `INITIALGROUNDHEIGHT`/`FINALGROUNDHEIGHT` int | `GROUNDHEIGHT NUMBER(14,10)`, `GROUNDHEIGHTFINAL` | n/d |
| `WATERCOMPONENTS.DIAMETER` | não existe | `FLOAT` | n/d |
| `DATEINSTALLATION` | varchar(50) | `DATE` | `varchar(50)` |
| `RAMAIS_AGUA` medidas | integer | `NUMBER(18,2)` | n/d |
| `WATERCOMPONENTSTYPES` | 29 tipos (geração antiga, `TANK`/`VALVE`...) | n/d | 0..46 (geração 7.0.4x) |
| `WATERCOMPONENTSSELECTIONS.VALUE_` | int | n/d | `tinyint` -> **`nvarchar(50)`** (7.0.43) |
| Boolean | `boolean` | n/d | `bit` |

Há **duas gerações de metadados** de componente de água (6.0.x vs 7.0.x), com semântica de `Specification_` diferente. Qualquer importação de dados legados precisa **detectar a geração** pelo `NX_BASE.VERSAO`/conteúdo de `WATERCOMPONENTSTYPES`.

---

## 4. Modelo de componentes (água e esgoto)

### 4.1 Padrão metadata-driven (EAV)

```
TYPES (id_type, descrição, specification)
  |-- SUBTYPES (id_type, id_subtype, descrição, selection_?, min, max, default, datatype, epaRef)
        |-- SELECTIONS (id_type, id_subtype, option_, value_)      <- só se selection_=true
  `-- DATA (object_id_, id_type, id_subtype, value_)               <- valor por objeto
```

`DATATYPE` usa **constantes ADO** [INFERÊNCIA pela correspondência]: 2 = smallint (referência de lista/curva), 5 = double, 129 = char, 202 = varchar unicode. `EPAREF` é o nome lógico do parâmetro na exportação (`POWER`, `HEAD`, `PATTERN`, `VOLCURVE`, `NINICIAL`, `NMINIMO`, `NMAXIMO`, `DIAMETER`, `TYPE`, `SETTING`, `MINORLOSS`, `SPEED`, `CURBOMBA`, `CURREND`, `RENDIM`, `CARGA`, `VAZAO`, `PARCONT`, `DESC`).

### 4.2 Tipos de componente de ÁGUA (geração 7.0.4x, `sql/20160210084500` e `...084600`)

`Specification_` define o **comportamento**: JUNCTION (nó comum), PUMP, VALVE (válvula EPANET), REGISTER (registro de manobra: ponto de parada na simulação de desabastecimento e nó comum no EPANET), RNF (reservatório de nível fixo), RNV (reservatório de nível variável).

| ID | Descrição | Classe | | ID | Descrição | Classe |
|---|---|---|---|---|---|---|
| 0 | Desconhecido | JUNCTION | | 22 | Medidor de pressão | JUNCTION |
| 1 | Adaptador | JUNCTION | | 23 | Medidor de vazão | JUNCTION |
| 2 | Atuador | JUNCTION | | 24 | Não identificado | JUNCTION |
| 3 | Bomba | PUMP | | 25 | RNF - Poço profundo | RNF |
| 4 | Booster | PUMP | | 26 | Redução | JUNCTION |
| 5 | CAP | JUNCTION | | 27 | **Registro** | **REGISTER** |
| 6 | Captação de água bruta | JUNCTION | | 28 | **RNV** - Reserv. nível variável | RNV |
| 7 | Cruzeta | JUNCTION | | 29 | Tê | JUNCTION |
| 8 | Curva 11-15 | JUNCTION | | 30 | Válvula de retenção | JUNCTION |
| 9 | Curva 22-30 | JUNCTION | | 31 | Ventosa | JUNCTION |
| 10 | Curva 45 | JUNCTION | | 32 | PRV - Válvula red. pressão | VALVE |
| 11 | Curva 90 | JUNCTION | | 35 | Registro fixo | JUNCTION |
| 12 | Descarga | JUNCTION | | 36 | **RNF** - Reserv. nível fixo | RNF |
| 13 | Elevatória de água | JUNCTION | | 38 | PSV - Sust. pressão | VALVE |
| 14 | Filtro | JUNCTION | | 39 | PBV - Perda de carga fixa | VALVE |
| 15 | Hidrante | JUNCTION | | 40 | FCV - Reg. vazão | VALVE |
| 16 | Hidrômetro | JUNCTION | | 41 | TCV - Contr. perda de carga | VALVE |
| 17 | Junção | JUNCTION | | 42 | GPV - Genérica | REGISTER (sic) |
| 18 | Junta de adaptação | JUNCTION | | 43 | **Válvula** (genérica, tipo em subtipo) | VALVE |
| 19 | Luva | JUNCTION | | 44 | Bomba por potência | PUMP |
| 20 | Macro medidor | JUNCTION | | 45 | Bomba por carga/vazão | PUMP |
| 21 | Medidor de nível | JUNCTION | | 46 | Bomba por curva | PUMP |

IDs 33, 34 e 37 não existem (lacunas, ex-"REG. SETOR"/REGSECTOR eliminado em 6.09.17). A geração **6.0.0** (template PG) tinha 29 tipos mais agregados (ex.: 21 "Registro de manobra" VALVE, 22 "Reservatório" TANK, 25 "VRP", 30 "Registro não manobrável", 2 "Bomba" PUMP).

### 4.3 Propriedades por tipo (subtipos) e domínios (geração 7.0.4x)

| Tipo | Propriedades (subtipo -> `EPAREF`) | Domínio de opções |
|---|---|---|
| 27 Registro | Situação (`selection`), Número do registro (texto) | situação: 0 desconhecida, 1 aberto, 2 fechado |
| 28 RNV | Alt. inicial (`NINICIAL`), Alt. mín (`NMINIMO`), Alt. máx (`NMAXIMO`), Diâmetro (`DIAMETER`), Curva de volume (`VOLCURVE`), Descrição | curva -> `X_CURVES` |
| 36 RNF | Nível da água (`HEAD`), Padrão de nível (`PATTERN`), Nome | padrão -> `X_PATTERNS` |
| 43 Válvula | Diâmetro, **Tipo** (`TYPE`), Parâmetro de controle (`SETTING`), Coef. de perda (`MINORLOSS`) | tipo: 1 PRV, 2 PSV, 3 PBV, 4 FCV, 5 TCV, 6 GPV |
| 32 PRV (legado) | Parâmetro de controle (`PARCONT`), Nome | |
| 44 Bomba por potência | Potência (`POWER`), Regulação de velocidade (`SPEED`), Padrão temporal (`PATTERN`), Descrição | |
| 45 Bomba por carga | Altura/carga (`CARGA`), Vazão (`VAZAO`), Velocidade, Padrão, Descrição | |
| 46 Bomba por curva | Curva (`CURBOMBA`), Velocidade, Padrão, Descrição | |
| 3 Bomba (6 itens) | Potência, Carga, Vazão, Rendimento (`RENDIM`), Curva da bomba, Curva de rendimento (`CURREND`) | |

Na geração 6.0.0 (template): **Reservatório** tinha tipo (desconhecido/elevado/apoiado/enterrado/torre), volume, cota de fundo, altura, curva de volume, nível máx/mín, diâmetro, base lado A/B, **código de área de reservação**, **código de telemetria**; **Medidores** (nível/pressão/vazão) tinham **código de telemetria**; **Bomba** tinha tipo (água bruta/tratada), sentido (direto/reverso), pressão, vazão, rotação, curva, **código de equipamento, de conjunto-bomba e de telemetria**; **Registro** tinha tipo (borboleta/esférica/gaveta/retenção/VRP/sustentadora/reguladora/controle de perda), estado operacional, **% de abertura** e diâmetro. Esse conjunto "código de telemetria / código de equipamento" é o **gancho de integração com SCADA e ativos**.

### 4.4 Tipos de trecho

| Rede | Tipos (`*LINESTYPES`) | Propriedade padrão |
|---|---|---|
| Água | 0 Desconhecido, 1 Adutora, 2 Sub-adutora, 3 Distribuição, 4 Água bruta | PROPRIEDADE: 0 desconhecido, 1 pública, 2 particular |
| Esgoto | 0 Desconhecido, 1 Emissário, 2 Coletor, 3 Coletor terceiro, 4 Interceptor, 5 Junta de adaptação, 6 Ramal coletor, 7 Recalque | idem |
| Drenagem | 0 "DRAINLINES" (placeholder) | |

### 4.5 Componentes de esgoto (`SEWERCOMPONENTSTYPES`)

0 Desconhecido, 1 Bomba (PUMP), 2 Caixa de inspeção, 3 Caixa (ponta seca), 4 Curva, 5 Elevatória de esgoto, 6 P.I., 7 P.I. (ponta seca), 8 P.V., 9 P.V. (ponta seca), 10 Selim, 11 Válvula de retenção. Drenagem: 0 Desconhecido, 1 Rede de drenagem.

Regra de domínio observada: "**ponta seca**" é um *estado de extremidade* (trecho sem continuidade) que o GEOSAN codifica como **tipo de componente** (3, 7, 9), e também como estado `7 Ponta seca` em `X_STATE`.

### 4.6 Domínios comuns (`X_*`)

| Domínio | Valores |
|---|---|
| `X_MATERIAL` | 0 Desc., 1 Cerâmico, 2 Concreto, 3 F.F. (ferro fundido), 4 Fibrocimento, 5 PEAD, 6 PRSV, 7 PVC branco, 8 PVC corrugado, 9 PVC DeFoFo, 10 PVC ocre, 11 PVC PBA |
| `X_STATE` (estado) | 0 Desconhecido, 1 Normal, 2 Fora de operação, 3 Quebrado, 4 Vazando, 5 Assoriado, 6 Encoberto, 7 Ponta seca |
| `X_LOCATION` (localização) | 0 Desconhecido, 2 Rua, 3 Passeio, 41 Canteiro, 42 Viela, 43 Passagem de servidão, 44 Área não edificante, 45 Área de proteção permanente, 46 Interno do lote |
| `X_SIDESTREET` | 1 Desconhecido, 2 Ímpar, 3 Par |
| `X_YES_NO` | 0 Não, 1 Sim (usado em `TROUBLE` = "não conformidade") |
| `X_SUPPLIERS`, `X_MANUFACTURERS` | cadastros (empresa, contato, endereço, telefone, site) |
| Rugosidade (Hazen-Williams C, classe `Rugosidade`) | desconhecido 111; cerâmico 130; concreto 120; F.F. 110; fibrocimento 105; PEAD 90; PRSV 130; PVC (branco, corrugado, DeFoFo, ocre, PBA) 140. "Considerada tubulação de 20 anos de idade." Reaplicar sempre que `X_MATERIAL` mudar. |

---

## 5. Ramal, ligação e o contrato GSAN <-> GEOSAN

### 5.1 Como o GEOSAN modela ramal e ligação [FATO + INFERÊNCIA marcada]

```
 trecho de rede (WATERLINES, Lines1)
      ^  OBJECT_ID_TRECHO
      |
 RAMAIS_AGUA  (linha Lines7 do ramal + ponto Points7 do hidrômetro)
      |   COD_LOGRAD, DISTANCIA_TESTADA, DISTANCIA_LADO,
      |   COMPRIMENTO_RAMAL, PROFUNDIDADE_RAMAL, POSICIONAMENTO_LOTE
      |
      +--< RAMAIS_AGUA_LIGACAO  (OBJECT_ID_ = ramal; 1 ramal : N ligações)
              NRO_LIGACAO  --->  GSAN: imovel.imov_id (matrícula)
              INSCRICAO_LOTE --> GSAN: inscrição (classificação fiscal)
              AUTO, TIPO, ECONOMIAS, HIDROMETRADO, CONSUMO_LPS
              (+ NRO_LIGACAO_ANTIGO, NRO_LIGSDV, DATA_LOG, USUARIO_LOG)
```

Fatos úteis:

- **O ramal é uma entidade geográfica; a ligação é uma entidade comercial pendurada nele.** Um ramal pode atender várias ligações (prédio, vila). `CONSUMO_LPS` e `ECONOMIAS` vivem na ligação; `RAMAIS_AGUA` repete `CONSUMO_LPS/TIPO/ECONOMIAS/HIDROMETRADO` (desnormalização histórica).
- O ramal se **ancora no trecho por posição**: `DISTANCIA_TESTADA` (distância ao longo da testada do lote), `DISTANCIA_LADO` (lateral), `POSICIONAMENTO_LOTE`. As notas 6.09.x tratam "a distância do ramal ao início do trecho cai fora do segmento" como caso a tratar e "mais de um ramal no trecho" como pendência: **ramal é referenciado linearmente (LRS)** ao trecho. Movimentar nó/trecho **move os ramais junto** (rev. 79 a 91).
- `AUTO = 0` aparece como filtro nas queries de ligação (`QUERY_ID` 10 e 12). [INFERÊNCIA] `AUTO` marca ligação **gerada automaticamente** (placeholder do lote, `AUTO=1`) versus ligação cadastrada manualmente (`AUTO=0`). A doc só diz "AUTO NUMÉRICO IDENTIFICADOR".
- **Produtividade de cadastro**: `DATA_LOG` e `USUARIO_LOG` nos ramais e (7.4.6) nas ligações registram "quem cadastrou e quando"; há relatórios `frmIndicadorProdutividade`, `frmIndicProdutRamaisAgua` e "indicador de produtividade de Rede de Esgoto" (5.9.0).
- `DATA_LOG` precisou crescer de 30 para 45 caracteres (segundos/milissegundos não cabiam). [FATO, spec 7.0.31]

### 5.2 Chaves de integração

| Chave GEOSAN | Equivalente comercial | Observação |
|---|---|---|
| `NRO_LIGACAO` | **GSAN `imovel.imov_id`** (matrícula do imóvel; no GSAN a ligação de água tem `lagu_id = imov_id`) | com dígito verificador. Query de join da view de ligação de água confirma `la.lagu_id = i.imov_id`. |
| `NRO_LIGACAO_SEM_DV` / `NRO_LIGSDV` | matrícula **sem** DV (= `NRO_LIGACAO / 10`) | criado por **desempenho**: a divisão por 10 nos joins fazia o `UPDATE` do consumo médio cair de timeout para < 1 minuto. |
| `NRO_LIGACAO_ANTIGO` (7.05.05) | matrícula anterior | migração/recadastramento de numeração |
| `CLASSIFICACAO_FISCAL` / `INSCRICAO_LOTE` | **inscrição** `LLL.SSS.QQQ.LLLL.SSS` (localidade.setor comercial.quadra.lote.sublote) | no GSAN, montada por `lpad` de `loca_id`, `stcm_cdsetorcomercial`, `qdra_nnquadra`, `imov_nnlote`, `imov_nnsublote` (sublote com 4 dígitos se > 999) |
| `COD_LOGRADOURO` | `logradouro_bairro.logr_id` | |
| `COD_CONSUMIDOR` | `cliente_imovel.clie_id` (tipo de relação `crtp_id = 2` = usuário; `1` = proprietário; ativo = `clim_dtrelacaofim IS NULL`) | |
| `HIDROMETRO` | `hidrometro.hidr_nnhidrometro` do histórico de instalação da ligação de água | identifica o "ramal" medido |
| `TIPO` | `ligacao_agua_situacao.last_dsligacaoaguasituacao` | "Ligada, Suprimida, Cortada, Potencial, Factível..." |
| `GRUPO_FATURAMENTO`, `ROTA_LEITURA`, `SEQUENCIA_LEITURA` | `rota.ftgr_id`, `rota.rota_cdrota`, `imovel.imov_nnsequencialrota` | |
| `ECONOMIAS` | soma de `imovel_subcategoria.imsb_qteconomia` | |

### 5.3 O contrato (3 views) [FATO: `doc/20150210285800-Especificações...doc`]

**`NXGS_V_LIG_COMERCIAL`**: uma linha por ligação.

| Campo | Tipo | Obrigatório | Conteúdo |
|---|---|---|---|
| `NRO_LIGACAO` | numeric(11) | **sim** | número da ligação de água |
| `NRO_LIGACAO_SEM_DV` | numeric(11) | **sim** | sem dígito verificador |
| `CLASSIFICACAO_FISCAL` | varchar(30) | **sim** | identificação do lote (setor/quadra/lote) |
| `COD_LOGRADOURO` | varchar(30) | | |
| `ENDERECO` | varchar(294) | | rua/avenida com tratamento |
| `NUM_CASA`, `COMPL_LOGRADOURO`, `BAIRRO` | varchar(15/60/150) | | |
| `HIDROMETRO` | varchar(30) | | código do hidrômetro |
| `COD_CONSUMIDOR`, `COD_CONSUMIDOR_SEM_DV` | numeric(11) | **sim** | |
| `CONSUMIDOR` | varchar(150) | | nome |
| `TEL_RES`, `TEL_COM` | varchar(20) | | |
| `TIPO` | varchar(20) | | situação da ligação |
| `ECONOMIAS` | numeric(10) | | |
| `HIDROMETRADO` | varchar(3) | | Sim/Não |
| `DATA_IMPLANTACAO_AGUA`, `DATA_IMPLANTACAO_ESGOTO` | datetime | | |
| `ROTA_LEITURA`, `GRUPO_LEITURA`, `ROTEIRO_LEITURA`, `SEQUENCIA_LEITURA` | numeric | | |
| `CONSUMO_MEDIO` | float | | (na tabela de exemplo) |

**`NXGS_V_LIG_COMERCIAL_CONSUMO`**: `NRO_LIGACAO`, `NRO_LIGACAO_SEM_DV`, `CONSUMO_FATURADO`, `CONSUMO_MEDIDO`, `MES`, `ANO` (uma linha por ligação x mês).

**`NXGS_V_LIG_COM_CONSUMO_MEDIO`**: `NRO_LIGACAO`, `NRO_LIGACAO_SEM_DV`, `CONSUMO_MEDIO` (média dos últimos 6 ou 12 meses, a operadora decide).

**Dois modos de integração** (instrução 05): (i) **carga** nas tabelas `NXGS_V_*` (ETL), ou (ii) **view em tempo real** via database link/servidor vinculado (SQL Server -> ODBC -> Postgres do GSAN, exemplo documentado), renomeando as tabelas locais para `_ORIG`. A string de conexão do banco comercial fica em `GS_PARAMETERS.STRING_CONNECTION_SECUNDARY`.

**Consultas funcionais** que dependem do contrato (`GS_QUERYS_CLIENT`, parâmetros `@CLASSIFICACAO_FISCAL`, `@NRO_LIGACAO`, `@ANO1/@PERIODO1`...): busca de ligações por logradouro/proprietário/ligação/inscrição; consumo mensal faturado x medido por lote ou ligação num intervalo; lista de ligações de uma seleção por polígono; consumo por tubulação (soma `CONSUMO_MEDIDO` das ligações de cada trecho via `LOTESXREDE`).

**Distribuição de demanda** (6.00.07.x): `UPDATE RAMAIS_AGUA_LIGACAO SET CONSUMO_LPS = CONSUMO_MEDIO * 0,00038580246` (= 1000 / (30 x 24 x 3600), m³/mês -> L/s), com ponteiros incrementais `GS_PARAMETERS.DEMAND_UPDATE_LINE_ID` e `DEMAND_LAST_UPDATE_LINE_ID`. A demanda do nó é depois o somatório das ligações dos ramais do trecho (distribuição nos nós; `X_TEMPCALCULENODE` guarda cálculo temporário) [INFERÊNCIA sobre o rateio nos nós de ponta].

### 5.4 Mapeamento sugerido para o SIGSAN

| GEOSAN | SIGSAN (existente) | Ação |
|---|---|---|
| `NRO_LIGACAO` | `consumer_units.matricula` + `dv` | já existe; falta a **amarra** `gis_service_connection.consumer_unit_id` |
| `CLASSIFICACAO_FISCAL` | (não existe campo de inscrição) | criar `consumer_units.inscricao` (ou tabela de imóvel) para casar lote |
| `COD_LOGRADOURO`, `NUM_CASA`, `BAIRRO` | `consumer_units.address JSONB`, `addresses` | normalizar logradouro (código) |
| `CONSUMO_MEDIO`/`_CONSUMO` | `commercial/*`, `sigsan-losses` (`water_balance`) | **consumo faturado por ligação/mês** é a entrada que falta ao balanço hídrico (hoje placeholder, ver Diário seção 9) |

---

## 6. Parâmetros de sistema e metadados novos

### 6.1 O que os scripts "parâmetros novos para exportação para EPANET" realmente são

[FATO] Apesar do nome, `sql/20151126132800-querie para inserir novos parâmetros.sql`, `...20160209175500-GeoSan 7.0.43 - parâmetros novos para exportação para EPANET.sql` e `...081600-...para banco 7.0.31.sql` **não mexem em `GS_PARAMETERS`**. São **carga de metadados de componentes** (`WaterComponentsTypes/SubTypes/Selections`) que habilitam a exportação:

| Script | O que faz |
|---|---|
| `20151126...inserir novos parâmetros` | Insere subtipos da **Bomba** (tipo 3: potência, carga, vazão, rendimento, curva da bomba, curva de rendimento) e do **RNV** (tipo 28: alt. inicial/mín/máx, diâmetro, curva de volume); contém o `DELETE` correspondente (rollback) e a limpeza dos valores já cadastrados em `WATERCOMPONENTSDATA` para esses tipos. **Versão preliminar** (usa `DataType`/`Selection_` com tipos antigos). |
| `20160209...7.0.43 EPANET` | Versão consolidada: recria `WaterComponentsTypes` (0..46), `SubTypes` com `EPAREF`, `Selections` (com `Value_` mudando para `nvarchar(50)`). Inclui "apaga todos os metadados dos nós" (reset). |
| `20160210081600...para banco 7.0.31` | Idêntico, para quem está na **7.0.31 e quer só atualizar metadados** para rodar a 7.0.43. |
| `20160210083900...scripts versão 7.0.30` | Metadados base 7.0.30: tipos 0..42 e só `REGISTRO` (situação aberto/fechado/desconhecido). |
| `20160210084500...scripts completos dos metadados das componentes de água` | Estado completo 7.0.43. |
| `20160210084600...atualizar para versão 7.0.44` | Delta 30 -> 44: tipos 43..46, subtipos de RNV/bomba/válvula, `Selections` de válvula. |
| `20161112081400...7.4.6 insere colunas log cadastro ramais` | `DATA_LOG`/`USUARIO_LOG` nas tabelas `RAMAIS_*_LIGACAO`. |

**Revela requisito**: o exportador EPANET passou de "tudo é nó" para **modelo hidráulico fiel** (bombas por potência/carga/curva, tanques de nível variável com curva de volume, reservatórios de nível fixo com padrão, válvulas tipadas com parâmetro de controle e perda singular, padrões e curvas em `X_PATTERNS`/`X_CURVES`).

### 6.2 Parâmetros reais de sistema

| Parâmetro | Local | Significado |
|---|---|---|
| String de conexão do comercial | `GS_PARAMETERS.STRING_CONNECTION_SECUNDARY` | formato por SGBD (ODBC Postgres, OLEDB SQL Server/Oracle). **Credencial em texto no banco.** |
| Ponteiros de demanda | `DEMAND_UPDATE_LINE_ID`, `DEMAND_LAST_UPDATE_LINE_ID` | processamento incremental da distribuição de demanda |
| Versão do modelo | `NX_BASE.VERSAO` | controle de migração (7.05.05 etc.) |
| Cliente (operadora) | `GS_CLIENT` | seleciona o conjunto de queries em `GS_QUERYS_CLIENT` |
| **BBox do município** | `te_layer`, `te_representation`, `te_theme`, `te_view` (`lower_x..upper_y`); `TERRACONFIG` por usuário | **toda instalação precisa reescrever o quadrante**, senão a camada "some" (rev. 83: "box que estava no infinito no layer RAMAIS_AGUA"). No SIGSAN: **extent por tenant**. |
| Banco/SGBD, e-mail de erro, diretórios | `GEOSAN.INI` (`Controles/`) e `UserLog.txt`, `FTema.txt` | arquivos locais do cliente (acoplamento de desktop) |

### 6.3 Segurança e usuários

`SYSTEMUSERS`: `USRLOG`, `USRNOM`, `USRFUN` (função), `USRDEP` (departamento), `USRPWD`, `USREXP`, `USRBRK`, `USRMAIL`, `USRDATA`. Perfis conhecidos (notas de versão): **Administrador, Consulta/Visitante, Visualizador**; o visitante pode inserir e consultar documentos, o visualizador só consulta. A senha é protegida por `CDecriptaSenha`/`FunDecripta` (**criptografia reversível**, anti-padrão; para o SIGSAN já está resolvido com Keycloak). Mantém-se como lição: permissão por **layer/tema** (frmLoginTema) e por vista de usuário.

---

## 7. Evolução de versões e o que cada mudança revela

Fontes: `doc/00. Leiame.txt` (notas até 6.10.27, jul/2013), scripts `sql/`, `db/SqlServer/*.txt`. **Não existe changelog do banco 7.0 -> 7.4 além dos scripts**; as versões 7.x aparecem só por nome de arquivo e documento.

| Versão / data | Mudança | Requisito revelado |
|---|---|---|
| 5.9.0 | Indicador de produtividade de rede de esgoto; cota PV com 3 casas; **proibir desenhar rede sobre outra com mesma peça inicial e final** | validação de duplicidade de trecho; precisão de cotas |
| 5.9.2 | Cadastro de ramais de esgoto; exportar mapa como imagem; impressão A0-A5 | esgoto como cidadão de primeira classe; saída cartográfica |
| 5.9.4 | Pesquisa de consumidor/ramal por ligação ou nome; relatório de ramais configurável; **filtro de tema de ramais** (tipo, hidrometrado, economias, consumo L/s, distância testada/lado, comprimento, profundidade, usuário); pesquisa por **IPTU** do lote; escala; área por polígono | busca comercial no mapa; tema filtrado por atributo |
| 5.9.5 | Config de conexão em `Geosan.ini`; polígono de seleção copia `OBJECT_ID` dos nós para `POLIGONO_SELECAO` | seleção espacial -> lista de ativos |
| 5.9.6-5.9.8 | Localizar válvulas; cadastro de drenagem; localizar consumidores | operação de manobra |
| 6.0.0 | **Postgres** | multi-SGBD |
| 6.0.5 | SQL Server 2008 | |
| 6.0.7.x (09-11/2012) | consumo médio -> `CONSUMO_LPS`; corrida contra timeouts (índices); dígito verificador; `ISNULL` no endereço | integração comercial é o gargalo; `NRO_LIGACAO_SEM_DV` |
| 6.08.00-02 (11-12/2012) | **Consumidores desabastecidos por manobra** (para em REGISTRO), exportação de shape desnormalizado; ValidaBase: `ApagaPointsSemWatercomponents`, `VefificaUnicidadeNos` | operação (fechamento de registro) e qualidade da base |
| 6.09.00 (01/2013) | índices em tabelas de ligação | |
| 6.09.01-03 (mar/2013) | **Requisitos EPANET**: exportar só VRP e registro fixo como válvula; **não exportar rede "fora de operação"**; reservatório/booster/bomba; curvas, padrões; controles; diâmetro da válvula = diâmetro da tubulação | exportação hidráulica fiel |
| 6.09.07-10 (abr/2013) | índice em `POLIGONO_SELECAO`; nós soltos afetam desabastecimento (ValidaBase); parar em REGISTER/VRP | topologia limpa é pré-condição de simulação |
| 6.09.12-17 | evolução de `Specification_` (RNF/RNV/POÇO/BOOSTER/REGISTER) | tipo de componente define papel no modelo hidráulico |
| 6.10.00-27 (mai-jul/2013) | **Cota Z** automática do nó (MDT, interpolação bilinear), mover nó/ramais junto, envio de erro por e-mail, **auto-atualização** | altimetria derivada; edição topológica consistente |
| 7.0.30 / 7.0.31 (2015-02) | especificação de software 7.0.31; views do comercial revisadas (`_SEM_DV`, datas de implantação, rota/roteiro) | contrato formalizado |
| 7.0.43 / 7.0.44 (2016-02) | metadados EPANET completos (bombas, RNV, válvulas) | |
| 7.4.6 (2016-11) | `DATA_LOG`/`USUARIO_LOG` em ligações | **produtividade** de cadastro |
| 7.4.0 doc (2017-08) | `GS_NOS`, `GS_REDES`, `GS_RAMAIS`, `GS_CONSUMIDORES` | exportação plana/web |
| 7.05.05 (2017-08) | `NRO_LIGACAO_ANTIGO`, `NRO_LIGSDV` em ligações | renumeração de ligações |

---

## 8. Conceito de "tema" com cláusula WHERE

[FATO] `te_theme` tem `generate_attribute_where text`, `generate_spatial_where`, `generate_temporal_where`; `te_theme_application.refine_attribute_where` etc. **Um tema = (vista do usuário, layer, predicado atributivo/espacial/temporal, simbologia, escala mín/máx, `visible_rep`)**.

Exemplo real (`doc/20130325...querie para atualizar a clausula where de um tema.sql`, tema 1454):

```
generate_attribute_where = object_id in (select object_id_ from WATERCOMPONENTS WHERE ID_TYPE='27')
```

(isto é, um tema "Registros" = pontos do layer cujo `object_id_` está em `WATERCOMPONENTS` com tipo 27). Detalhes:

- O WHERE é **SQL cru armazenado em texto**, inserido na consulta de geração da coleção (`collection_table = te_collection_N`, materialização por tema). Mudar o WHERE exige **regenerar a coleção**.
- `visible_rep` é máscara dos tipos de geometria visíveis: 130 = 2+128 (linha + texto), 132 = 4+128 (ponto + texto), 134 = 2+4+128 [INFERÊNCIA].
- `NXGS_FILT_TEMA (THEME_ID, FILT_1..3)`: filtros do usuário por tema (ex.: ramais por tipo/hidrometrado/consumo).
- `FTema.txt` (local): lista de temas ativos do usuário e vista com os filtros, usada pelo exportador.
- Legenda/agrupamento: `te_grouping` (atributo, modo, número de classes, desvio-padrão, função), `te_legend`, `te_visual` (216 linhas no template: cor, contorno, transparência, símbolo, fonte).

**Valor para o SIGSAN**: a ideia de **camada definida por filtro dinâmico** (por tipo, estado, material, DMA, setor) é boa e barata. **Não** armazenar SQL cru (injeção; o risco já foi visto no TenantFilter). Usar **filtro estruturado** (JSON de predicados: campo, operador, valor) compilado para consulta parametrizada ou **CQL/OGC Filter** e servir via vector tiles/WFS (ver seção 10.1, `gis_theme`).

---

## 9. ValidaBase: o que valida

[FATO] `trunk/Projeto_ValidaBase/Leiame.txt` e `Forms/Main.frm`. Executável VB6 independente; log em `ValidaBaseLog.txt`; **só SQL Server estava implementado** (os `Case "Oracle"`/`"Postgres"` do código estão vazios). Entradas: o próprio banco (compara tabelas de atributo com tabelas de geometria).

### 9.1 Os 13 processos declarados

| # | Processo | Regra |
|---|---|---|
| 1 | Linhas sem geometria | `WATERLINES.object_id_` sem `LINESn.object_id` -> **apaga o atributo** (a linha não aparece no mapa, usuário não consegue manipular) |
| 2 | Componentes sem geometria | `WATERCOMPONENTS.object_id_` sem `POINTSn.object_id` -> relatório (tenta recriar) |
| 3 | Componentes duplicados | `count(object_id_) > 1` por geometria de ponto (`VefificaUnicidadeNos`) |
| 4 | Linhas duplicadas | idem para trechos |
| 5 | Linhas sem atributos | `LINESn.object_id` sem `WATERLINES` -> **apaga a geometria** |
| 6 | Componentes sem atributos | `POINTSn.object_id` sem `WATERCOMPONENTS` -> **apaga o ponto** (`ApagaPointsSemWatercomponents`) |
| 7 | Correção de textos | correspondência de rótulos com correções de scripts anteriores |
| 8 | Relação componente inicial x rede | todo `INITIALCOMPONENT` existe em `WATERCOMPONENTS.COMPONENT_ID`; senão **cria componente default** (`SECTOR = 999`) se a geometria do ponto existir (`ValidaComponentesIniciaisDeWaterlines`) |
| 9 | Relação componente final x rede | idem para `FINALCOMPONENT` |
| 10 | Comprimento das redes de água | confronto `LENGTH`/`LENGTHCALCULATED` com a geometria; `corrigeDistancias` recalcula e conserta rótulos |
| 11 | Componentes próximos/sobrepostos | nós a menos da tolerância |
| 12 | Trechos de rede dos ramais de água | `RAMAIS_AGUA.OBJECT_ID_TRECHO` aponta trecho existente |
| 13 | Correspondência de coordenadas redes x componentes | extremidade do trecho coincide com o nó (tolerância **0,01** unidade do mapa = 1 cm em UTM) |

Acrescentos posteriores (6.08/6.09): **nó sem trecho encostado** (`ProcuraNosSemTrechosEncostados`: percorre `POINTSn`, busca por bbox + extremos de `LINESn`; **apaga o nó** se nenhum trecho toca, tolerância 0,01), "nós soltos" porque quebravam a simulação de desabastecimento; no GeoSan, `frmVerificaConectividade` ("Verificar Conectividade de Redes") faz o mesmo no cliente, checando também **nós ligados a um único trecho** (ponta) e a mais de um.

### 9.2 Avaliação crítica

- **Boas ideias**: pares de órfãos (atributo sem geometria e vice-versa), integridade de referência nó-trecho, unicidade de nó, tolerância de coincidência explícita, "sentinela" para correção automática, log por regra.
- **Más práticas a NÃO herdar**: correções **destrutivas automáticas** (apaga linha/nó sem confirmação), regras presas a um SGBD, execução fora do ciclo transacional, `SECTOR=999` como marca mágica, IDs em string.
- **Regras de rede que o GEOSAN exigia e que não estão no ValidaBase mas no editor**: proibir trecho duplicado (mesmo `INITIAL`+`FINAL`), bomba/válvula exigem trechos desenhados **no sentido do fluxo** antes e depois do nó (para exportar sentido correto), tolerância de snap (efeito colateral: nós muito próximos geram "pegar o nó da outra rede").

---

## 10. Proposta para o SIGSAN

### 10.1 Esquema PostGIS por tenant (proposta)

Premissas: tenant em schema próprio, `tenant_id` + RLS (padrão do projeto), **SRID 4674**, nomes em inglês `snake_case`, UUID. SRID 4674 é **geográfico (graus)**: para comprimento/tolerância usar `geography` ou projetar para o fuso SIRGAS 2000 UTM da concessionária (`ST_Transform`) em colunas geradas/funções; **nunca** comparar graus com metros (o GEOSAN trabalhava em UTM metros).

**Unificação**: o GEOSAN tinha 3 redes x (linhas + componentes) com estrutura quase idêntica (água/esgoto/drenagem). Propor **um par de tabelas com discriminador `network`** em vez de seis.

| Tabela | Geometria | Colunas obrigatórias / chave | Notas |
|---|---|---|---|
| `gis_domain` + `gis_domain_value` | n/a | `(tenant_id, domain, code)`, `label`, `sort`, `active`, `attrs JSONB` | domínios: `material`, `condition_state`, `placement`, `street_side`, `ownership`, `pipe_class_water`, `pipe_class_sewer`. `material.attrs` guarda `hazen_williams_c`. Semente por tenant, editável. |
| `gis_element_type` | n/a | `code`, `network` (WATER/SEWER/DRAIN), `kind` (NODE/PIPE), `solver_class` (JUNCTION/PUMP/VALVE/RESERVOIR_FIXED/TANK/…), `operable bool`, `stops_flow_trace bool`, `label` | substitui `*TYPES` + `Specification_`. `stops_flow_trace` = antigo REGISTER. Taxonomia SIGSAN própria (seção 11). |
| `gis_attribute_def` | n/a | `(element_type, key)`, `label`, `data_type`, `unit`, `min`, `max`, `default`, `required`, `domain`, `solver_param` | substitui `*SUBTYPES`; **EAV só para atributos raros/específicos de tipo**. Valores em `gis_node.attrs`/`gis_pipe.attrs` (JSONB) **validados** contra a definição (trigger/CHECK por função). Atributos comuns são colunas reais. |
| **`gis_node`** | `Point,4674` | `id`, `tenant_id`, `network`, `node_code` (único por tenant+network), `element_type`, `status`, `ground_elevation_m`, `depth_m`, `invert_or_head_m` (derivada), `demand_lps`, `demand_pattern_id`, `supply_zone_id`, `installed_at`, `year_built`, `supplier_id`, `manufacturer_id`, `info_validity`, `non_conformity`, `telemetry_code`, `asset_id`, `attrs JSONB` | `UNIQUE(tenant_id, network, node_code)`; `UNIQUE(tenant_id, id)` (necessário para FKs compostas). Índices: GiST(geom); btree `(tenant_id, network, element_type)`; parcial `status <> 'ACTIVE'`. |
| **`gis_pipe`** | `LineString,4674` (opcionalmente Z) | `id`, `tenant_id`, `network`, `pipe_code`, `pipe_class`, `from_node_id`, `to_node_id` **(FK composta)**, `length_informed_m`, `length_measured_m` (calculada com `ST_Length(geom::geography)`), `dn_internal_mm`, `dn_external_mm`, `thickness_mm`, `material`, `roughness_c`, `ground_elev_from/to_m`, `depth_from/to_m`, `slope` (esgoto), `flow_direction`, `status`, `placement`, `street_side`, `divided_distance_m`, `ownership`, `supply_zone_id`, `installed_at`, `info_validity`, `non_conformity`, `created_by/at`, `attrs` | `CHECK(from_node_id <> to_node_id)`; `UNIQUE(tenant_id, network, from_node_id, to_node_id)` com exceção explícita para paralelos (regra GEOSAN 5.9.0); índices GiST(geom), btree em `from_node_id` e `to_node_id` (topologia e travessia), parcial `status='ACTIVE'`. |
| **`gis_service_line`** (ramal) | `LineString,4674` + `meter_point Point` | `id`, `network`, `pipe_id` (trecho), `chainage_m` (posição linear no trecho), `lot_id`, `street_id`, `lot_front_distance_m`, `lot_side_distance_m`, `lot_position`, `length_m`, `depth_m`, `status`, `created_by/at` | substitui `RAMAIS_*`. `chainage_m` torna explícita a ancoragem (o GEOSAN a derivava de distâncias de lote). |
| **`gis_service_connection`** (ligação) | n/a | `id`, `service_line_id`, `consumer_unit_id` (FK `consumer_units`), `matricula`, `matricula_legacy`, `inscricao`, `connection_type`, `economies`, `metered`, `avg_demand_lps`, `demand_source_period`, `auto_generated`, `created_by/at` | substitui `RAMAIS_*_LIGACAO`. `UNIQUE(tenant_id, matricula, network)`. |
| `gis_lot` | `Polygon,4674` | `id`, `inscricao`, `block_code`, `sector_code`, `street_id`, `source`, `valid_from/to` | substitui `LOTEGIS` (sem zoneamento da prefeitura no núcleo; vai em `attrs`) |
| `gis_block` / `gis_street` | `Polygon` / `LineString` | `code`, `name` | opcional (quadra/logradouro), alimentados por importação |
| `gis_zone` | `Polygon,4674` | `kind` (SUPPLY_SECTOR, DMA, PRESSURE_ZONE, METERING_ZONE...), `code`, `parent_id`, `dma_district_id` | generaliza setor/zona; **`dma_district` (losses) continua dono da regra de perdas**; `gis_zone` serve ao espaço. Evitar duas fontes de verdade (ver lacuna L9). |
| `gis_curve`, `gis_pattern` | n/a | `id`, `kind` (PUMP, EFFICIENCY, VOLUME, HEADLOSS), `points JSONB / numeric[]` | substitui `X_CURVES`/`X_PATTERNS` |
| `gis_theme` (camada configurável) | n/a | `id`, `owner`, `name`, `network`, `filter JSONB` (predicados), `style JSONB`, `min_scale`, `max_scale`, `shared bool`, `position` | substitui `te_theme` **sem SQL cru** |
| `gis_data_quality_issue` | `Point` (localização do problema) | `rule_code`, `severity`, `entity_type`, `entity_id`, `detected_at`, `status` (OPEN/ACK/FIXED/WAIVED), `details JSONB`, `fixed_by` | **registro persistente** do que o ValidaBase só escrevia em log |
| `gis_edit_history` (ou estender o `audit_log`) | n/a | quem/quando/o quê na edição de rede | cobre `USUARIO_LOG/DATA_LOG` (produtividade) |

**Topologia mínima nó-trecho**: `gis_pipe.from_node_id/to_node_id` são a verdade topológica; a geometria deve *confirmar*: `ST_DWithin(ST_StartPoint(pipe.geom)::geography, from_node.geom::geography, :tol_m)`, idem fim. Garantir por **constraint trigger** diferida (rejeita na transação se violar) e por job de auditoria. A tolerância deve ser **parâmetro por tenant** (padrão 0,05 m; o GEOSAN usava 0,01 em UTM).

**Colunas obrigatórias comuns** (todas): `id uuid`, `tenant_id`, `code` natural único por tenant, `status`, `created_at/by`, `updated_at/by`, `version` (para o `BaseEntity`), `geom NOT NULL`, `source` (MANUAL, IMPORT_SHP, GEOSAN_MIGRATION, SURVEY), `info_validity`.

### 10.2 Comparação com `V1__create_gis_schema.sql` e lacunas

**Defeitos que impedem o V1 de funcionar [FATO, verificados no arquivo]** (prioridade antes de qualquer enriquecimento):

| # | Defeito | Onde |
|---|---|---|
| D1 | `ALTER TABLE scenario_analysis ENABLE ROW LEVEL SECURITY.` termina com **ponto em vez de ponto e vírgula** (erro de sintaxe) | linha 228 |
| D2 | `hydraulic_indicators` declara **colunas duplicadas** (`avg_velocity`, `max_velocity`, `high_velocity_count`, `total_headloss`, `pressure_deficit_count`, `pressure_excess_count` repetidas) -> `CREATE TABLE` falha | linhas 171-182 |
| D3 | FKs compostas `(tenant_id, dma_id) REFERENCES dma_district(tenant_id, id)` exigem **UNIQUE em `(tenant_id, id)`** em `dma_district`, que não existe (só PK em `id` e unique em `(tenant_id, code)`) | `V1__create_losses_schema.sql` linhas 9-38 + FK no gis |
| D4 | O migration está em `intelligence/sigsan-gis/src/main/resources/db/migration/`, mas o provisionamento de tenant só aplica `db/migration/tenant` (único arquivo: `V1_1__create_tenant_schema.sql`). **O mesmo caso do "tenant vazio" do Diário (seção 5): as tabelas GIS nunca são criadas**, sem erro. | `TenantProvisioningService.java:211` |
| D5 | Entidade x SQL divergem: entidade usa `dma_district_id`, SQL usa `dma_id`; entidade `unique=true` em `element_code` (único **global**, não por tenant) e SQL idem (`NOT NULL UNIQUE` além do índice composto) -> dois tenants não podem ter o mesmo código | `GisNetworkElement.java` / SQL |
| D6 | `dma_district.code` e `leak_detection.detection_code` também têm `UNIQUE` global | losses V1 |
| D7 | `idx_gis_elem_coords` na entidade é índice btree sobre `geometry` (JPA); o SQL real tem GiST. Remover da entidade. | entidade |

**Lacunas funcionais (V1 x modelo-alvo)**:

| # | Lacuna | Gravidade |
|---|---|---|
| L1 | Tabela única genérica `Geometry` sem **nó/trecho**: não há `from_node`/`to_node`, não há grafo, logo **não há como alimentar o EPANET real nem traçar manobra** | alta |
| L2 | Sem cota/profundidade/demanda do nó, sem diâmetro interno/externo/rugosidade/espessura/cotas nas pontas; `diameter_mm` único; `length_m` só informado (falta medido) | alta |
| L3 | Sem **ramal e ligação**, sem vínculo com `consumer_units`, sem lote | alta |
| L4 | Sem domínios (material, estado, localização, lado da rua, propriedade); `material`, `status`, `condition_rating` são texto livre | média |
| L5 | Sem **classe de comportamento** (JUNCTION/PUMP/VALVE/RESERVOIR/TANK; `stops_flow`) nem propriedades por tipo (bomba: curva; tanque: curva de volume e níveis; válvula: tipo e controle) | alta |
| L6 | Tipos do V1 não cobrem: **curva (11/22/45/90)**, filtro, atuador, bomba (como componente), registro fixo (divisor de setor), PSV/PBV/FCV/TCV/GPV, **adutora/sub-adutora/distribuição/água bruta**, emissário/coletor/interceptor/recalque, **P.V./P.I./caixa de inspeção/selim/ponta seca**, drenagem. Inversamente, sobram `SUPPORT`, `ANCHOR`, `THRUST_BLOCK`, `CATHODIC_PROTECTION`, `FLANGE`, `COUPLING` sem origem em requisito |
| L7 | CHECK com enum de 46 valores na coluna: qualquer novo tipo exige migration. Trocar por tabela `gis_element_type` | média |
| L8 | Sem `network` (água/esgoto/drenagem); mistura tudo | média |
| L9 | `dma_id` (perdas) é a **única** noção de setor; faltam setor de abastecimento, zona de pressão, macrossetor. Cuidado com duas fontes de verdade com `dma_district` | média |
| L10 | Sem tabela de curvas/padrões, sem demanda e padrão temporal -> o solver futuro não tem entrada | alta (quando houver solver) |
| L11 | Sem `gis_theme`, sem extent do tenant, sem simbologia | média |
| L12 | Sem registro de qualidade de dados (o ValidaBase persistente) | média |
| L13 | Sem ID legado (`legacy_object_id`) para migração do GEOSAN/shapefile | média |
| L14 | `age_years` armazenado e desatualizável (derivável de `installation_date`) | baixa |
| L15 | Sem Z (cota) na geometria: o GEOSAN calculava a cota por MDT; considerar `LineStringZ` ou coluna `elevation` derivada de raster | média |
| L16 | `epanet_node_result`/`epanet_link_result` guardam `node_id VARCHAR(100)` sem FK ao nó; deveriam apontar `gis_node.id`/`gis_pipe.id` | média |

### 10.3 Regras de validação topológica e de qualidade (a implementar)

Princípios: **detectar, registrar em `gis_data_quality_issue`, propor correção, nunca apagar sozinho** (diferença-chave frente ao ValidaBase). Rodar (a) no **write** (constraint trigger/serviço) para o que é barato e determinístico, (b) em **job** noturno por tenant/DMA para o que é custoso, (c) como **gate** antes de exportar para solver.

| Código | Regra | Origem GEOSAN | Verificação (esboço) | Sev. | Momento |
|---|---|---|---|---|---|
| Q-01 | Todo trecho tem geometria `LineString` válida (`ST_IsValid`, 2+ pontos, comprimento > 0) | ValidaBase 1 e 5 | `NOT NULL` + CHECK + `ST_IsValid` | ERRO | write |
| Q-02 | Todo nó tem geometria `Point` | ValidaBase 2 e 6 | idem | ERRO | write |
| Q-03 | `from_node_id`/`to_node_id` existem e são do mesmo `network` | ValidaBase 8 e 9 | FK composta + CHECK | ERRO | write |
| Q-04 | Início/fim do trecho coincidem com o nó (tolerância `tol_m`) | ValidaBase 13 | `ST_DWithin` em geography | ERRO | write (deferido) |
| Q-05 | Sem nó duplicado (mesmo código) | ValidaBase 3 | `UNIQUE` | ERRO | write |
| Q-06 | Sem nós sobrepostos/próximos (mesmo `network`) | ValidaBase 11 | `ST_DWithin` self-join com GiST | AVISO | job |
| Q-07 | Sem trecho duplicado (mesmos nós, ou geometria igual) | ValidaBase 4 + regra 5.9.0 | `UNIQUE` + `ST_Equals` | ERRO | write |
| Q-08 | **Nó órfão**: sem nenhum trecho encostado, exceto tipos que permitem (reservatório isolado, ponto de monitoramento) | ValidaBase "nós sem trecho" | `NOT EXISTS` em `gis_pipe` | AVISO | job |
| Q-09 | **Trecho isolado** / componentes conexos: toda a rede de um DMA deve formar **1 componente conexo** (ou conexo ao reservatório) | frmVerificaConectividade, "nós soltos quebram desabastecimento" | CTE recursiva / `pgrouting` ou grafo em memória | ERRO p/ solver | job/gate |
| Q-10 | Ponta (grau 1) só pode ser tipo permitido (hidrante, descarga, CAP, ponta seca) | frmVerificaConectividade | grau por nó | AVISO | job |
| Q-11 | `length_measured_m` = `ST_Length(geom)` ± 0,5 %; `length_informed` x medido divergente | ValidaBase 10 | comparação | AVISO | write |
| Q-12 | Diâmetro, material e classe obrigatórios (ou `DESCONHECIDO` explícito) e dentro de `[min,max]` do `gis_attribute_def` | `*SUBTYPES` min/max | CHECK/trigger | AVISO | write |
| Q-13 | Rugosidade coerente com material (recalcular quando o domínio mudar) | `Rugosidade.cls` | derivação | INFO | job |
| Q-14 | Cota do nó e profundidade presentes; cota do nó = terreno - profundidade; **cota PV com 3 casas** | 5.9.0; 6.10.x | CHECK | AVISO | write |
| Q-15 | Cota coerente com MDT (diferença > limiar) | cota Z | amostragem de raster | AVISO | job |
| Q-16 | Esgoto por gravidade: **declividade >= mínima** e **sentido a jusante** (cota de fundo decrescente) | (requisito de domínio, sem equivalente no GEOSAN) | `invert_from - invert_to` / comprimento | ERRO | job |
| Q-17 | **Sentido do fluxo**: bomba/válvula de retenção/PRV exigem trecho a montante e a jusante orientados no sentido operacional | Leiame 6.09.17 (bomba) | orientação da polilinha vs tipo | ERRO p/ solver | gate |
| Q-18 | Ramal ancorado em trecho existente e **dentro do segmento** (`chainage_m` entre 0 e comprimento) | Leiame 6.09.x (pendência) | `ST_LineLocatePoint` | ERRO | write |
| Q-19 | Ligação referencia matrícula existente em `consumer_units`; sem ligação duplicada (mesma matrícula em dois ramais ativos, salvo `auto_generated`) | `QUERY_ID` 10 (`LIGACOES_CAD_RAMAL_DIFERENTE`) | join/unique | ERRO | write |
| Q-20 | `consumer_units` ativas sem ligação georreferenciada (e vice-versa) | integração comercial | anti-join | AVISO | job (**alimenta perdas**: cadastro x comercial) |
| Q-21 | Lote por inscrição: ligação cai dentro de um lote com mesma inscrição | `LOTEGIS`/`CLASSIFICACAO_FISCAL` | `ST_Within` | AVISO | job |
| Q-22 | Rede "fora de operação" é excluída do solver e da simulação de manobra | requisito EPANET 6.09.01-2 | filtro de exportação | INFO | export |
| Q-23 | Todo ativo tem `info_validity`/origem e autor (rastreabilidade) | `USUARIO_LOG/DATA_LOG` | NOT NULL | AVISO | write |
| Q-24 | Geometria dentro do **extent do tenant** (evita "box no infinito") | te_layer bbox | `ST_Within(geom, extent)` | ERRO | write |
| Q-25 | SRID = 4674 e coordenadas dentro do Brasil | doc Postgres-LogPro (reprojeção SAD69 -> SIRGAS) | `ST_SRID`, bbox | ERRO | write/import |
| Q-26 | Atributo sem geometria / geometria sem atributo (para **importação** de dados legados) | ValidaBase 1,2,5,6 | staging | ERRO | import |

**Simulação de manobra (consumidores desabastecidos)** [FATO, spec seção 8; leiame 6.08/6.09]: a partir de um trecho selecionado, **percorrer o grafo** marcando trechos até encontrar nós com `stops_flow_trace` (REGISTER; em versões intermediárias VRP e registro fixo); listar ramais -> ligações -> consumidores (nome, endereço, telefones residencial e comercial) e exportar para arquivo. Gancho para **comunicação de interrupção programada**. Nós soltos/ruído topológico distorcem esse resultado, por isso Q-09/Q-10.

**Exportação EPANET (regras absorvíveis)**: nó = cota (terreno - profundidade); demanda base L/s = `((mensal x 1000) / 30 / 24) / 3600`; padrão de consumo por nó + tabela de padrões; reservatório nível fixo: carga = cota terreno + nível; tanque nível variável: cotas inicial/mín/máx, diâmetro ou curva de volume; **bomba e válvula viram link com nó sintético** (`<id>_A`) deslocado para o outro lado; tipo de válvula PRV/PSV/PBV/FCV/TCV/GPV + parâmetro de controle + perda singular; diâmetro da válvula = diâmetro do trecho; comprimento do trecho = medido, senão informado; rugosidade por material; **trechos "fora de operação" não exportam**; registro sem situação = aberto; registro fechado de divisão de setor exporta como válvula fechada. Isto é o roteiro funcional do solver/adaptador que falta (`runEpanetWasm` ainda é stub).

---

## 11. Recomendações para o SIGSAN

Legenda de esforço: **S** (dias), **M** (1-3 semanas), **L** (>3 semanas). Risco = técnico/legal. Todas as ações usam conceitos do GEOSAN reescritos (seção 1.3).

### P0: corrigir e fundar (bloqueia tudo)

| # | Ação | Módulo | Existe? | Falta | Esforço | Risco |
|---|---|---|---|---|---|---|
| P0-1 | **Consertar o V1 do GIS**: D1 (ponto final), D2 (colunas duplicadas), D3 (UNIQUE `(tenant_id,id)` em `dma_district`), D5/D6 (unicidade por tenant, não global), alinhar entidade x SQL | `intelligence/sigsan-gis`, `intelligence/sigsan-losses` | existe, quebrado | corrigir + teste | S | baixo |
| P0-2 | **Colocar as migrations GIS/losses/quality/mtr/lgpd no location de tenant** (ou fazer o provisionamento varrer os módulos) e **estender o teste de provisionamento** para afirmar que as tabelas GIS existem | `platform/sigsan-platform` (`TenantProvisioningService`) | só `V1_1` | mover/agregar migrations, teste `provisionsGisTables` | S-M | baixo (é o mesmo bug do Diário seção 5) |
| P0-3 | **Modelo nó-trecho**: `gis_node` + `gis_pipe` com `from/to` e FK composta, `network`, cota/profundidade/demanda/diâmetros/rugosidade, tolerância por tenant, constraint trigger Q-04 | `sigsan-gis` | tabela genérica | schema + entidades + testes de topologia | M | médio |
| P0-4 | **Tabelas de domínio + `gis_element_type`** (classe de comportamento, `stops_flow_trace`), substituindo o CHECK de 46 valores | `sigsan-gis`, `shared/core` (enums/lookup) | CHECK | semente por tenant, API de domínio | M | baixo |
| P0-5 | **Ramal e ligação** (`gis_service_line`, `gis_service_connection`) amarrados a `consumer_units` por matrícula, com `chainage_m` | `sigsan-gis` + `commercial` + `operations/assets` | ausente | schema + vínculo; campo `inscricao` em `consumer_units` | M | médio |

### P1: dar valor ao produto (perdas e operação)

| # | Ação | Módulo | Existe? | Falta | Esforço | Risco |
|---|---|---|---|---|---|---|
| P1-1 | **Qualidade de dados persistente** (`gis_data_quality_issue`) + regras Q-01 a Q-15, Q-18 a Q-21, **sem correção destrutiva** | `sigsan-gis` | nada | serviço de regras, job por tenant/DMA | M | baixo |
| P1-2 | **Grafo e traçado de manobra** (consumidores afetados por fechamento de registro): travessia a partir de trecho até `stops_flow_trace`, lista ramais -> ligações -> clientes e telefones | `sigsan-gis` + `sigsan-field` (OS) | ausente | algoritmo (grafo em memória ou CTE/pgRouting), endpoint, exportação | M | médio (exige Q-09) |
| P1-3 | **Contrato de integração comercial** definido como interface (carga ou leitura), com campos mínimos de `NXGS_V_*` reescritos: ligação (matrícula), inscrição, endereço, situação, economias, categoria, rota; e **consumo mensal faturado/medido por ligação** | `commercial/*`, `shared/core`, `sigsan-gis` | `consumer_units` existe | série de consumo por ligação/mês, consumo médio 6/12 m | M | médio |
| P1-4 | **Alimentar o balanço hídrico IWA com dados reais**: consumo autorizado faturado por DMA = soma de ligações do DMA (via ramal -> trecho -> `supply_zone`) e `unbilled`/`unauthorized` por diferença. Troca os `// Placeholder` do `LossesService` | `sigsan-losses` | estrutura IWA, entradas fictícias | consulta espacial por DMA | M | médio-alto (depende de P0-3/5 e P1-3) |
| P1-5 | **Zonas** (`gis_zone`: setor de abastecimento, DMA, zona de pressão) e regra Q-09 por DMA, com relação definida com `dma_district` | `sigsan-gis`, `sigsan-losses` | `dma_district` (polígono) | `gis_zone`, vínculo nó/trecho/ligação -> DMA | M | médio (duas fontes de verdade) |
| P1-6 | **Rugosidade por material e demanda por ligação** como derivados reproduzíveis (L/s = m³/mês x 1000 / 2.592.000) | `sigsan-gis` | ausente | funções + testes unitários | S | baixo |
| P1-7 | **Importação de legado** (shapefile/GEOSAN) com staging e validação Q-26, `legacy_object_id`, reprojeção para 4674 | `sigsan-gis`, `shared/core` | ausente | pipeline de importação | M-L | médio (qualidade do dado do cliente) |

### P2: robustez e diferencial

| # | Ação | Módulo | Existe? | Falta | Esforço | Risco |
|---|---|---|---|---|---|---|
| P2-1 | **Camadas dinâmicas por filtro estruturado** (`gis_theme`, sem SQL cru) + simbologia + vector tiles | `sigsan-gis`, `frontend` | ausente | modelo + API + front | M | baixo (não repetir SQL cru) |
| P2-2 | **Solver/adaptador EPANET**: exportador de rede (regras da seção 10.3), curvas e padrões (`gis_curve`/`gis_pattern`), propriedades específicas por tipo (bomba, tanque, válvula) | `sigsan-gis` | stub honesto (só conta) | exportador `.inp` + biblioteca hidráulica (ex.: EPANET 2.x via JNI/WASM) + resultados com FK ao nó/trecho (L16) | L | alto (esforço e validação hidráulica) |
| P2-3 | **Cota Z por MDT** (interpolação bilinear) e Q-15 | `sigsan-gis` | ausente | raster de terreno, serviço de amostragem | M | médio |
| P2-4 | **Produtividade de cadastro** (quem/quando/quantos ramais e ligações) a partir de `created_by/at` | `sigsan-gis`, `sigsan-field` | `audit_trigger` genérico | indicadores | S | baixo |
| P2-5 | **Gancho de telemetria e ativos**: `telemetry_code` e `asset_id` em `gis_node` (bomba: código de equipamento, conjunto, telemetria) ligando ao SCADA e a `operations/assets` | `operations/assets` (vazio), `ScadaIntegrationService` | ausente | cadastro de ativos + ligação | M | médio |
| P2-6 | Regras de esgoto (declividade mínima, sentido de jusante, P.V./P.I., ponta seca) e **drenagem** | `sigsan-gis` | ausente | extensão do modelo | M | médio (conhecimento normativo local) |
| P2-7 | Documento de **política de uso do GEOSAN** (clean room, o que é conceito vs cópia) e revisão jurídica da GPL v2 antes de qualquer reaproveitamento literal | jurídico/arquitetura | ausente | decisão registrada no Diário de Borda | S | **alto se ignorado** |

### Observações finais

- A maior contribuição do GEOSAN não é código nem schema: são **as regras operacionais que ele aprendeu a duras penas** (desempenho da integração comercial, DV da matrícula, nós soltos quebrando simulação, bbox por município, tolerâncias de snap, sentido de bomba). Elas valem como **casos de teste** do `sigsan-gis`.
- O que **não** vale absorver: TerraLib/tabelas de geometria próprias, EAV generalizado para atributos comuns, SQL cru em tema/consulta, `AUTO`/ID de string gerado no cliente, correções destrutivas automáticas, criptografia reversível de senha, credencial na base.
- Dado o `DIARIO-DE-BORDA` ("build verde não prova que o código está correto"), recomendo que P0-1/P0-2 sejam fechados **com teste de provisionamento contra o PostgreSQL real** antes de começar P0-3.
