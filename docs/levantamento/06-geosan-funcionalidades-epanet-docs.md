# 06 - GEOSAN: funcionalidades, exportação EPANET, documentação e arquitetura

Levantamento de domínio para o SIGSAN. Fonte: `G:\Meu Drive\PROJETOS\Diversos\FONTES OPEN\GEOSAN` (somente leitura).
Objetivo: extrair **conceitos, regras e algoritmos descritos**. Nenhum código foi copiado.
Convenção: **[F]** fato verificado em arquivo; **[I]** inferência minha; **[?]** não consegui confirmar.

---

## 0. Resumo executivo

1. O GEOSAN é um cadastro técnico de redes (água, esgoto, drenagem) + ligação com o comercial (GSAN) + exportador para EPANET, em VB6 sobre TerraLib/TeCom e banco SQL Server, Oracle ou PostgreSQL. A versão mais nova é a 7.5.6 (git, 19/06/2018).
2. O exportador EPANET (`Projeto_ExportEpaNe`) é um programa separado que lê os trechos selecionados por polígono, converte nós especiais (bomba, válvula, registro) em trechos com nó virtual e grava um `.inp` **sem** `[OPTIONS]`, `[TIMES]`, `[REPORT]`, `[DEMANDS]`, `[CONTROLS]`, `[ENERGY]` e `[END]`. O usuário ajusta isso na interface do EPANET.
3. Cotas vêm de um **MDT em grade regular** (tabela `MDT` + geometria `Points107`) com **interpolação bilinear** própria (`CIntBilinear`, `CObtemZdoNo`). Demandas vêm do comercial: consumo médio por ligação convertido para L/s e distribuído metade para cada nó do trecho do ramal.
4. O escritor `.inp` do SIGSAN hoje gera arquivo **inválido para qualquer engine** (ver seção 6): sem topologia (nós de pipe vazios), `[PUMPS]` fora da sintaxe, `[OPTIONS]` com palavras-chave de `[TIMES]`, diâmetro em metros, rugosidade 0,01 chamada de "C=100", formatação sensível ao locale e reservatórios nunca emitidos.
5. Licença do GEOSAN: **GPL v2 apenas** (cabeçalho em `doc/20141027141200-cabeçalhoGPL.txt`). Usar só como fonte de conhecimento (clean-room). Há também credenciais em texto claro em docs e `.ini` (ver seção 1).

### O que li e o que não consegui ler

| Material | Situação |
|---|---|
| `doc/00. Leiame.txt` (histórico de versões 06.00.07 a 06.10.27 + notas 5.9.0 a 6.0.7) | lido |
| `doc/01 Especificação` (07.00.31), `02 Manual do Usuário` (texto), `03 Administrador`, `03/04 Visitante e Visualizador`, `04 Informações da biblioteca`, `05 Ambiente`, `06 Tutorial shape`, `07 Exportação EPANET`, `Passo a Passo EPANET`, `Vistas do comercial`, `Object_ID`, `Instruções`, `Implantação Postgres-LogPro`, `Questionário NEXUS` | lidos por extração de texto via Word COM para a pasta temporária da sessão (as figuras não foram vistas) |
| `trunk/Projeto_ExportEpaNe` (`ModExporte.bas`, `Rugosidade.cls`, `Geral.bas`, `TrechosRedeEpanet.cls`, `RedeBancoDados.cls`, `FrmEPANET.frm`), `classes/CIntBilinear.cls`, `CObtemZdoNo.cls`, `CConsumo.cls`, `Forms/frmAtualizacaoConsumo.frm`, partes de `frmCanvas.frm` | lidos (texto) |
| `sql/*.sql` (metadados de componentes, parâmetros EPANET) | amostrados por Grep (contagem de valores `EPAREF`) |
| Diagramas `.vsd` (desenho, mover rede, mover vértice, modelo físico, UML) | **não consegui ler** (Visio sem texto extraível, Visio não instalado). O fluxo foi reconstruído a partir do Leiame e do código |
| `.chm` (TeCanvas, TeDatabase, TeNetwork, TeViewManager, TeImport/TeExport) | **não lidos** |
| PDFs `UNIT 38 Digital Elevation Models` e `Bilinear interpolation` | o primeiro lido (material acadêmico NCGIA, plano `z=a+bx+cy`, grade regular); o segundo não abri |
| PDF `GEOSAN SOFTWARE LIVRE PARA REDUÇÃO DE PERDAS` | lido, mas quase só imagens (história: 2007-2009, pilotos CAERN/CAESB/COSANPA/EMBASA/COMPESA, integração com GSAN, publicação no Portal do Software Público) |
| Manual 02 (39 MB) | só texto; telas não vistas |
| `Updates.txt` (trunk) | lido: **não é changelog**, é manifesto do auto-atualizador (2 linhas) |
| `trunk/Forms` (81 formulários), `Modules`, `classes` | lista e amostras; não li formulário por formulário |

---

## 1. Licença e risco de contaminação

| Item | Achado | Risco |
|---|---|---|
| Licença do GEOSAN | [F] `doc/20141027141200-cabeçalhoGPL.txt`: GNU GPL, "versão 2 da Licença" (sem "ou posterior"), copyright 2006-2014 José Maria Villac Pinheiro. Repo no GitLab do Portal do Software Público (`softwarepublico.gov.br/gitlab/gsan/geosan`). | **Alto** se copiar código: GPL-2.0-only não combina com Apache-2.0/proprietário. SaaS puro não "distribui", mas app móvel/on-premise distribuiria. |
| Aplicação da licença nos fontes | [F] Não há `LICENSE` na raiz. Os primeiros 3 KB de `frmCanvas.frm`, `RedeBancoDados.cls` e `CObtemZdoNo.cls` não têm aviso GPL. [?] Não varri todos os arquivos. | Situação jurídica ambígua. Tratar como GPL-2.0-only por prudência. |
| TerraLib / TeCom | [F] `install/TeCom/3.3.1` e `4.2.0` trazem DLLs binárias sem texto de licença. [I] TerraLib é do INPE, LGPL (conhecimento geral, **não confirmado no repo**). TeCom é wrapper COM da NEXUS, [?] licença desconhecida. | Médio. Irrelevante para o SIGSAN se for substituído. |
| Componentes "Nexus" | [F] `NexusConnection.dll`, `NexusPM4.ocx`, `NxViewManager.ocx`, `NUsers.dll`, `NSecurity.dll`: binários na pasta `dlls/` e fontes em `trunk/Projeto_*`. Doc 04 lista licença "Pública" para `NexusConnection.dll` e `NexusPM4.ocx`. | Cobertos pela GPL do projeto, porém dependem de VB6. |
| Terceiros embutidos | [F] OCX da Microsoft (MSCOMCTL, MSFLXGRD, MSCHRT20 etc.), `LoozeXP.ocx`, `Zlib.dll`, ícones `gis-0.1` (CC BY-SA 3.0, `doc/icon_license.txt`), PDFs acadêmicos (NCGIA/Klinkenberg), `regdllview`. | Não reutilizar. CC BY-SA é copyleft para os ícones. |
| Imagens de satélite | [F] Tutorial em `Implantação Postgres-LogPro.docx` usa AutoCAD + Bing Maps para gerar GeoTIFF. | Termos de uso do Bing: não replicar. |
| **Segredos expostos** | [F] Senhas de SQL Server (`sa`), PostgreSQL, SMTP em texto claro em `doc/*.doc*`, `trunk/Controles/GEOSAN.INI`, `Projeto_NxConnection/GEOSAN.INI`. Usuário admin padrão documentado. Rotina própria de "criptografia" por substituição (`FunDecripta`, `ModExporte.bas`). [?] Não confirmei como `SYSTEMUSERS.USRPWD` é armazenada (indício de texto ou cifra reversível). | Tratar como comprometido. **Nunca** reutilizar valores. Para o SIGSAN: Keycloak + hash forte. |

**Regra de trabalho recomendada:** quem escrever o código do SIGSAN lê este documento, não os fontes do GEOSAN. Citar o GEOSAN como "fonte de requisitos" apenas.

---

## 2. Catálogo de funcionalidades do GEOSAN

Fonte principal: Manual do Usuário (`02`), Administrador (`03`), Visitante/Visualizador, Especificação (`01`), `frmCanvas.frm` (rotina `Tb_SELECT` com os botões `k*`).

### 2.1 Navegação e visualização

| Funcionalidade | Descrição | Observação para web |
|---|---|---|
| Vistas e temas | Cada usuário tem 1+ **Vistas**; cada Vista tem **Temas** (instância de um *plano de informação* com estilo e filtro). Liga/desliga por duplo clique; um tema é o "ativo" onde as ferramentas operam. | Equivale a "mapa do usuário" + camadas + estilo. Persistir em tabela, não em arquivo (`FTema.txt`). |
| Múltiplas janelas de mapa | "Novo" abre outra área de trabalho com outra extensão/combinação de temas. | Abas ou split view. |
| Zoom área, mais/menos, pan, voltar/avançar visualização, recompor, atualizar, definir escala | Navegação clássica. | MapLibre nativo; "voltar/avançar" exige histórico de câmera. |
| Teclas de zoom sem sair do comando (Ctrl+setas, PgUp/PgDn, roda do mouse, A/Z/S/W) | Release 06.10.08. | UX de edição. |
| Calibrar zoom | Visitante/Visualizador. | Desnecessário. |
| Login automático | Menu Arquivo. | Sessão/refresh token. |

### 2.2 Cadastro de redes (edição)

| Funcionalidade | Regra de negócio observada |
|---|---|
| **Desenhar rede** | Todo trecho **precisa** de nó inicial e final. Ao clicar sobre componente existente, ele vira nó do trecho. Comprimento aparece na barra de status. Vários trechos salvam de uma vez (Enter/Salvar). Não permite 2 trechos com mesma peça inicial e final (5.9.0). |
| **Desenhar componente na rede** | Clique sobre um trecho **divide** o trecho em dois, conectados pelo novo componente; renumeração automática. |
| **Mover componente com a rede** | Arrastar nó recalcula os trechos ligados e, desde 06.09.17 (23/05/2013), os **ramais** associados. Cota do nó é recalculada (06.10.12, 06.10.14). |
| **Mover vértice de rede** | Diagrama `20130704...mover vértice rede.vsd` (não lido); botão `kMoveVertice`. |
| Excluir | Com confirmação; para ramal exige selecionar linha e ponto (hidrômetro). |
| Gerenciador de propriedades | Atributos por tipo de objeto, **metadados em tabelas** (`*SUBTYPES`, `*SELECTIONS`, `*DATA`): novo atributo = nova linha, sem recompilar. |
| Cadastro de fabricantes e fornecedores | Tabelas `X_MANUFACTURERS`, `X_SUPPLIERS`. |
| Tolerância de captura (snap) | Leiame 06.09.15 relata o problema: nós muito próximos fazem o desenho "pegar" o nó errado; reduzir tolerância torna difícil selecionar. |
| Atributos de trecho | tipo (adutora, secundária, água bruta, viela, fictícia), cota terreno inicial/final, profundidade, Ø interno/externo, material, comprimento informado e calculado, fornecedor, localização, estado de conservação, validade da informação, rugosidade, setor, data de instalação, lado da rua, distância da divisa, não conformidade, usuário e data do log. |
| Tipos de componente (~35) | adaptador, atuador, bomba, booster, cap, captação, cruzeta, curvas, descarga, elevatória, filtro, hidrante, hidrômetro, junção, luva, macromedidor, medidor de nível/pressão/vazão, poço, redução, registro, **RNV**, **RNF**, te, retenção, ventosa, **VRP/PRV/PSV/PBV/FCV/TCV/GPV**, registro fixo, registro de setor. |

### 2.3 Ramais e ligações (liga GIS ao comercial)

| Funcionalidade | Descrição |
|---|---|
| Inserir ramal | Clica no trecho, arrasta e solta o ponto do hidrômetro; janela lista ligações/matrículas do comercial. Pré-filtro por ligação, **inscrição**, endereço ou consumidor. |
| Ligações fictícias | Para lote sem ligação cadastrada: quantidade + consumo médio (m³/mês ou L/s, conversão automática). Tipo "FICTÍCIA". Zero apaga. |
| Filtro por lote | Usa um tema de lotes (polígonos) com IPTU/inscrição e número da ligação para pré-carregar as ligações do lote. |
| Ramal de esgoto | Mesma lógica, vinculado ao ramal de água. |
| Associar ramal à rede mais próxima | Operação por polígono; raio máximo **5 m** quando o trecho original foi apagado. |
| Cadastro automático a partir de GPS | 7.3.3/7.4.0 (outubro/2016): ligações levantadas no coletor GPS (celular) do GSAN são associadas ao trecho que o usuário seleciona; associação rápida de várias ligações a um ramal. |
| Indicador de produtividade por ligação | 7.5.0 (nov/2016): quem cadastrou cada ligação (separado de quem desenhou o ramal). |

### 2.4 Consultas e busca

| Funcionalidade | Detalhe |
|---|---|
| Encontrar objeto | Por identificador, nos temas de componentes, redes, ramais, lotes. |
| Encontrar textos | Por tema de texto (ex.: nome de ruas) com lista de resultados. |
| Encontrar coordenada | Digitar X e Y. |
| **Encontrar consumidores** | Por número da ligação ou nome (início, fim ou qualquer parte). Mostra o ramal no mapa. |
| **Encontrar válvulas / consumidores desabastecidos** | A partir de um trecho selecionado, percorre a rede até "pontos de parada" (registro) e pinta o trecho isolado; abre lista de consumidores desabastecidos. Rotina `SELECTRede` (`frmCanvas`, botão `ksearchinnetwork`). Só enxerga consumidores que têm ramal no cadastro. |
| Verificar declividade | Trecho selecionado (botão `kdeclivity`), útil para esgoto. [?] fórmula não lida. |
| Verificar conectividade | Formulário `frmVerificaConectividade`. [?] não lido. |
| Calcular área / medir distância | Polígono com área em m² na barra de status. |
| Temas e filtros | Filtro por atributos (até 2) e data de desenho. Combinação de temas = mapa temático. Query de where por tema (`doc/20130325...sql`). |
| Documentos | Ponto no mapa com N arquivos anexos (`X_FILES`). Visitante também pode inserir/consultar. |
| Manobra | Tabelas `manobra`, `manobraSetor`, `NXGS_MANOBRA`: setores em manobra (falta de água), previsão, início, término; usadas para informar o atendimento ao público. |

### 2.5 Operações por polígono

"Polígono por Seleção" (desenhado) ou "Carregar Polígono de Setor" (polígono existente de um tema como DISTRITOS). Copia objetos para `POLIGONO_SELECAO` (TIPO 0 = nós, 1 = redes, 2 = ramais, por usuário) e habilita: Exportar para EPANET, Alterar consumo das ligações, Relatórios em arquivo texto (2 fixos + 1 personalizável para redes; relatórios de ramais), Associar ramal à rede mais próxima.

### 2.6 Importação e exportação

| Função | Detalhe |
|---|---|
| Importar DXF, SIG (shape; pede projeção e datum), cotas (MDT) | Menu Arquivo > Importar. |
| Exportar DXF | Filtros: itens selecionados, tema atual, seleção de temas; arquivo completo ou separado por tipo de geometria. |
| Exportar imagem da área visualizada; salvar imagem (BMP, GIF, JPG, PNG, TIF) | |
| Exportar **shape** | 06.08.02: `GeoSanExport` **desnormaliza** as tabelas em uma tabela de atributos única de nós e redes. 7.5.5 corrige shape de consumidores. Tutorial alternativo com TerraView. |
| Exportar localização de nós com cota; consumidores e consumo | Arquivos texto. |
| Exportar consumos por mês e ano | 06.10.06. |
| Impressão | Módulo de impressão A0 a A5 (5.9.2), correção de escala (5.9.4). |
| Relatórios | HTML, abertos no navegador padrão. |
| E-mail automático de erros | 06.09.17: erro no cliente envia e-mail à NEXUS (SMTP no `.ini`). |

### 2.7 Administração e segurança

| Função | Detalhe |
|---|---|
| Papéis | **Administrador** (tudo), **Usuário** (cadastro e edição), **Visitante** (consulta + gerenciador de propriedades + documentos + localizar válvulas), **Visualizador** (consulta sem propriedades). |
| Administrar usuários | Nome, login, senha, departamento, e-mail, permissão. Tabela `SYSTEMUSERS` (inclui bloqueio e expiração de senha). Grupos e recursos (`FrmGroups`, `FrmResources`). |
| Log | `FrmLog`, campos `USUARIO_LOG`/`DATA_LOG` em redes e ramais. |
| Administrar banco de dados | Troca de conexão (Access, SQL Server, Oracle, PostgreSQL). |
| Indicadores de produtividade | Relatórios de km de rede, ramais por usuário e data (água, esgoto). |
| ValidaBase | Corretor de base (seção 3.4). |
| Auto-atualização | `GeoSanIni.exe` baixa componentes de um servidor web (06.10.22 a 06.10.27). |

---

## 3. Fluxos de trabalho

### 3.1 Cadastro de rede

1. Ativar tema de redes de água.
2. Desenhar rede: clicar em nó existente ou criar novo; arrastar; Enter para salvar.
3. Sistema grava a linha em `WATERLINES` com `INITIALCOMPONENT`/`FINALCOMPONENT`, comprimento calculado e (desde 06.10.04) **cota Z dos nós por MDT**.
4. Preencher atributos no gerenciador.
5. Inserir componentes (divide trecho) e ramais.

### 3.2 Ramal e ligação

Trecho → ponto de captação no trecho → ponto do hidrômetro no lote → escolher ligações (comercial) → grava `RAMAIS_AGUA` (linha + ponto, `OBJECT_ID_TRECHO`, comprimento, distância da testada, profundidade) e `RAMAIS_AGUA_LIGACAO` (N ligações por ramal, consumo em L/s, economias, hidrometrado).

### 3.3 Isolamento de trecho (consumidores desabastecidos)

[F] Percorre a rede a partir do trecho até nós cujo tipo tem `Specification_ = REGISTER` (histórico no Leiame: já parou em VALVE, depois em "registro fixo"; fixado em 06.09.17 para **REGISTER**; "REG. FIXO" e "REG. SETOR" não podem ser manobrados). Depois lista consumidores via ramais dos trechos pintados. Bug conhecido: cancelar a operação exige reiniciar o programa antes da próxima.
Distinção de domínio (06.08.00): **válvula** abre progressivamente e não tem status; **registro** só abre/fecha e tem status aberto/fechado/desconhecido.

### 3.4 ValidaBase (qualidade do cadastro antes de simular)

13 verificações [F] (`Projeto_ValidaBase/Leiame.txt`): linhas sem geometria; componentes sem geometria; componentes duplicados; linhas duplicadas; linhas sem atributos; componentes sem atributos; correção de textos; relação componente inicial com rede; relação componente final com rede; comprimento das redes de água; componentes próximos/sobrepostos; trechos de rede dos ramais; correspondência de coordenadas entre redes e componentes. Mais tarde: nós soltos (06.09.09), apagar pontos sem `WATERCOMPONENTS` e unicidade de nós (06.08.02). Roda agendável fora do horário, grava log `*_CORRETOR_BASE.txt`. O manual manda ler o log antes de exportar: correções críticas pendentes "poderão inviabilizar a exportação".

### 3.5 Roteiro oficial para exportar ao EPANET (doc `Passo a Passo`)

1. Arquivo > **Atualizar Consumos e Distribuir Demandas** > "Atualizar Consumo das Ligações Cadastradas" (copia consumo da vista comercial para `RAMAIS_AGUA_LIGACAO.CONSUMO_LPS`).
2. "Distribuir Demandas" (soma o consumo dos ramais próximos em cada nó).
3. Rodar **ValidaBase** (admin; horário opcional) e conferir o log.
4. **Importar cotas** (MDT).
5. Exportar por **polígono desenhado** (desmarcar "Ramais de Água") ou por **polígono de setor**; informar nome do `.inp`.

---

## 4. Modelo de dados relevante (Especificação 07.00.31)

| Grupo | Tabelas | Função |
|---|---|---|
| Redes | `WATERLINES`, `SEWERLINES`, `DRAINLINES` + `*TYPES`, `*SUBTYPES`, `*SELECTIONS`, `*DATA` | Cadastro de trechos, tipos, atributos dinâmicos (EAV). |
| Componentes | `WATERCOMPONENTS` (+ `*TYPES` com `Specification_`), `SEWERCOMPONENTS`, `DRAINCOMPONENTS` | Nós/peças. `DEMAND` (L/s), `PATTERN`, `GROUNDHEIGHT`, `STATE`. |
| Ramais | `RAMAIS_AGUA`, `RAMAIS_AGUA_LIGACAO`, `RAMAIS_ESGOTO(_LIGACAO)` | Ponte GIS-comercial. |
| Domínios | `X_MATERIAL`, `X_STATE`, `X_LOCATION`, `X_SIDESTREET`, `X_MANUFACTURERS`, `X_SUPPLIERS`, `X_YES_NO` | |
| EPANET | `X_PATTERNS` (24 multiplicadores separados por `;`), `X_CURVES` (tipos Bomba, Rendimento, Volume, Perda de Carga; X e Y como listas `;`) | Alimentam `[PATTERNS]` e `[CURVES]`. |
| Elevação | `MDT` (campo `COTA`, `object_id_107`) + geometria `Points107` | Grade de pontos. |
| Operação | `manobra`, `manobraSetor`, `NXGS_MANOBRA`, `POLIGONO_SELECAO`, `VALIDA_LINES` | |
| Integração comercial | vistas `NXGS_V_LIG_COMERCIAL`, `NXGS_V_LIG_COMERCIAL_CONSUMO`, `NXGS_V_LIG_COM_CONSUMO_MEDIO`; `GS_PARAMETERS.STRING_CONNECTION_SECUNDARY`; `GS_QUERYS_*` | Contrato de integração (seção 10.c). |
| Metadados TerraLib | `te_layer`, `te_view`, `te_theme`, `te_representation`, `te_projection`, `te_legend`, `te_visual`... + `PointsN`, `LinesN`, `PolygonsN` por layer | Substituíveis por PostGIS + estilos. |
| Chave | `OBJECT_ID_` (varchar) sequencial. Doc `Como criar um Object_ID` propõe tabela `tb_Senhas` (auto-incremento + string de controle) para evitar duplicidade multiusuário. | Em PostgreSQL: sequence/UUID. Padrão obsoleto. |

---

## 5. Exportação EPANET: algoritmo e parâmetros

### 5.1 Arquitetura do exportador

[F] `Exporte EPANet.exe` (`EpanetExport.vbp`, versão 7.0.55) é chamado pelo GeoSan.exe quando o usuário escolhe "Exportar Epanet" em *Operações por Polígono*. Ele lê `Controles\UserLog.txt` (usuário ativo) e `GEOSAN.INI` (conexão), consulta `POLIGONO_SELECAO` e grava o `.inp` no caminho escolhido (padrão `Meus Documentos\GeoSan_Exp_Epanet_AAAA-MM-DD-HHMMSS.INP`). Há campo de horário para agendar (oculto na tela). Cancelável.

### 5.2 Algoritmo em passos

1. **Preparação de dados.** Recalcula `WATERLINES.ROUGHNESS` por **material** (tabela fixa, abaixo) e zera material nulo para "desconhecido". Carrega em memória todos os nós (`WATERCOMPONENTS` + geometria + subtipo "tipo de válvula" + subtipo "estado" aberto/fechado, com estado 2 = Closed) em `rsNosTmp`.
2. **Carrega o catálogo de tipos de componente** (`WATERCOMPONENTSTYPES.Specification_`): `JUNCTION`, `PUMP`, `VALVE`, `VALVE2`, `REGISTER`, `RNV`, `RNF`.
3. **Percorre os trechos selecionados** (polígono + tipo de rede). Para cada trecho e para cada um dos 2 nós, se ainda não exportado:
   - `PUMP`, `VALVE`, `VALVE2`, `REGISTER`: **converte o nó em link**. Cria um **nó virtual** `ID & "A"` sobre o próprio trecho a 1/3 do comprimento (nó inicial) ou 2/3 (nó final), respeitando vértices, e liga o nó real ao virtual por um link novo.
     - bomba: link `B<ID>`; a **ordem Node1/Node2 define o sentido** (por isso o manual manda desenhar os dois trechos adjacentes no sentido de operação).
     - válvula: link `V<ID>`, Ø do trecho adjacente se o da válvula for zero.
     - registro: trecho de 1 m (válvula "tipo 2": 0,1 m) com Ø e rugosidade do trecho e status `Open`/`Closed` do componente (padrão `Open`).
   - `RNV` vira **tanque**, `RNF` vira **reservatório**, demais viram **junção** com cota, demanda (L/s) e padrão.
   - Grava coordenadas do nó.
4. **Trecho:** link com comprimento (`LENGTH` se > 0, senão `LENGTHCALCULATED`), Ø interno, rugosidade, status fixo `Open`. Vértices intermediários vão para `[VERTICES]`.
5. **Saída:** escreve seções na ordem JUNCTIONS, RESERVOIRS, TANKS, PUMPS, VALVES, STATUS (todas válvulas `Open`), PIPES, PATTERNS, CURVES, COORDINATES, VERTICES. Parâmetros de bomba: se há `POWER` usa `POWER x`; senão se há `CARGA` e `VAZAO` gera curva de 1 ponto `C<ID>` e usa `HEAD`; senão se há `CURBOMBA` usa `HEAD <curva>`; `SPEED` e `PATTERN` opcionais.
6. Log em arquivo; erro cai em `LogErroExportEPANET.txt`.

### 5.3 Divergências entre documento e código [F]

| Tema | Documento (03 §4.5) | Código |
|---|---|---|
| Comprimento | prioridade para `LENGTHCALCULATED` | usa `LENGTH` se > 0, só então `LENGTHCALCULATED` |
| Cota de junção/tanque | `GROUNDHEIGHT - DEEPNESS` | só `GROUNDHEIGHT` |
| Carga do reservatório fixo | `GROUNDHEIGHT + nível` | `cota x valor` (multiplica) |
| Materiais | `X_MATERIAL`: 1 PVC, 2 F.F., 3 C.A., 4 Defofo, 5 A.C., 6 PEAD | `Rugosidade.cls` usa outra numeração (0 desconhecido ... 11 PVC PBA) |
| Requisito "não exportar rede fora de operação" | levantado em 06.09.01 | [?] não encontrei no exportador |

### 5.4 Mapeamento conceitual GEOSAN para EPANET

| Elemento EPANET | Origem no GEOSAN | Regra |
|---|---|---|
| `[JUNCTIONS]` | `WATERCOMPONENTS` tipo JUNCTION | Elev = cota do nó; Demand = `DEMAND` (L/s); Pattern = `PATTERN` (0 = vazio) |
| `[RESERVOIRS]` | tipo `RNF` (inclui "poço profundo") | Head por subtipo `HEAD`/nível, Pattern por subtipo |
| `[TANKS]` | tipo `RNV` | subtipos `NINICIAL`, `NMINIMO`, `NMAXIMO`, `DIAMETER`, `VOLUME`, `VOLCURVE` (curva prefixada `C`) |
| `[PIPES]` | `WATERLINES` + registros convertidos | Ø interno (mm), rugosidade H-W por material, status Open/Closed |
| `[PUMPS]` | tipos `PUMP`/`BOOSTER` | POWER, ou HEAD com curva (gerada de carga+vazão ou `CURBOMBA`), SPEED, PATTERN; `CURREND` (rendimento) |
| `[VALVES]` | tipo `VALVE` (VRP/PRV, PSV, PBV, FCV, TCV, GPV) | subtipos `TYPE` (1 a 6), `SETTING`, `DIAMETER`, `MINORLOSS` |
| `[PATTERNS]` | `X_PATTERNS` | 24 multiplicadores, 6 por linha |
| `[CURVES]` | `X_CURVES` | tipos Bomba, Rendimento, Volume, Perda de Carga |
| `[COORDINATES]`, `[VERTICES]` | geometria TerraLib | |
| `[STATUS]` | válvulas | todas `Open` |
| `[OPTIONS]`, `[TIMES]`, `[REPORT]`, `[ENERGY]`, `[CONTROLS]`, `[DEMANDS]` | **não gerados** | Requisitos 4, 5, 6, 7, 8, 10, 11 do Leiame 06.09.01 (padrões temporais, controles IF, curvas de rendimento, volume do tanque) ficaram parcialmente atendidos (padrões, curvas); **controles não**. |

### 5.5 Rugosidade (Hazen-Williams C) por material, "tubulação de 20 anos" [F]

Desconhecido 111; cerâmico 130; concreto 120; ferro fundido 110; fibrocimento 105; PEAD 90; PRSV 130; PVC (branco, corrugado, DeFoFo, ocre, PBA) 140. São valores de C típicos de literatura. Revisar sempre que `X_MATERIAL` mudar (nota do Leiame 06.00.07.13). [I] No SIGSAN, guardar por material + faixa de idade (C decai com o tempo) em tabela de configuração por tenant, nunca fixo em código.

### 5.6 Parâmetros que o usuário configura

- No exportador: **apenas** nome/caminho do `.inp` (+ horário oculto) e a **seleção** (polígono desenhado ou de setor; incluir/excluir ramais).
- Nos dados (antes de exportar): tipo de cada componente (Specification), subtipos EPANET por componente (potência, carga, vazão, curvas, níveis, tipo/ajuste de válvula, perda singular), curvas e padrões (`X_CURVES`, `X_PATTERNS`), material, diâmetro, estado do registro, consumo por ligação.
- No EPANET (manual): unidades de vazão, fórmula de perda de carga, tempos e demais opções. [I] como o arquivo não traz `[OPTIONS]`, é preciso ajustar Unidades para L/s e fórmula H-W (Ø em mm), senão o EPANET assume padrões americanos. Os manuais lidos não mencionam isso.

### 5.7 Demandas (de onde vêm os L/s) [F]

1. Consumo por ligação: média dos últimos 6 ou 12 meses (vista `NXGS_V_LIG_COM_CONSUMO_MEDIO`) ou consumo medido de um mês (`NXGS_V_LIG_COMERCIAL_CONSUMO`).
2. **Conversão:** `L/s = (m³/mês x 1000) / 30 / 24 / 3600`, fator `0,00038580246` (inverso: `m³/mês = L/s x 2592`).
3. **Distribuição:** soma `CONSUMO_LPS` das ligações de cada ramal, agrupa por **trecho** (`OBJECT_ID_TRECHO`), e adiciona **metade** ao `DEMAND` do nó inicial e metade ao do nó final. Antes zera `DEMAND` de todos os nós.
4. Há um bloco comentado que transferia demanda de nós de válvula/bomba/registro para o vizinho; está desativado [F], mas o exportador ignora demanda dos nós virtuais (`demanda = 0`).
5. Dica de desempenho (Leiame 06.00.07.07): a junção pelo número de ligação **sem dígito verificador** e com índice reduziu a consulta de timeout para menos de 1 minuto.
6. Observação do código: ligações sem consumo no mês não têm demanda; [?] não vi tratamento de perdas físicas nem de padrão diferente por categoria.

### 5.8 Cotas: MDT + interpolação bilinear [F]

1. Tabela `MDT` guarda `COTA` por ponto; geometria `Points107` guarda x,y da **grade regular**. A grade vem de importação (menu Importar Cotas) e é considerada de passo ~10 m (o código procura vizinhos a ±10 m com tolerância de "meia diagonal").
2. Para um nó `(x,y)`: localiza o ponto de grade mais próximo; pela posição relativa (4 quadrantes) acha os outros 3 vértices da célula; lê as 4 cotas.
3. **Bilinear:** duas interpolações em X e uma em Y entre os 4 cantos. Resultado arredondado a 2 casas.
4. Chamado ao desenhar (06.10.04), ao mover (06.10.12/14) e em lote ("atualizar cotas", com barra de progresso e ESC para cancelar).
5. **Falhas conhecidas:** se não acha os vizinhos grava log e retorna **0** (cota zero silenciosa); exige o layer MDT ligado na vista; comentário fala em grade 5x5 e código em 10 m (inconsistente); há tratamento de divisão por zero.
6. A referência acadêmica incluída é o plano `z = a + bx + cy` de uma janela 2x2; o PDF de bilinear não foi lido.

### 5.9 Regras de diâmetro, comprimento e válvulas (resumo)

- Ø da válvula: o próprio, ou o do trecho se zero (06.09.10, 06.09.03).
- Comprimento: `LENGTH` informado prevalece no código; na ausência, `LENGTHCALCULATED` (da geometria).
- Apenas **VRP** e válvulas explícitas viram `[VALVES]`; registros, descargas e outros viram só nó (06.09.03). Registro de divisão de setor deve ser cadastrado como "registro fixo" e sai como válvula/trecho fechado (06.09.02).
- Bomba só tem sentido correto se os dois trechos adjacentes foram desenhados no sentido de operação.
- Status `[STATUS]` das válvulas sempre `Open` (para o usuário não precisar abrir todas antes de simular).

### 5.10 Limitações do exportador GEOSAN (a não herdar)

Sem `[OPTIONS]`/`[TIMES]`/`[END]`; sem `[CONTROLS]`/`[RULES]`/`[ENERGY]`; divergência doc vs código; contadores `Integer` de 16 bits no exportador (`contadorTrechos`, `ObtemNumeroTrechosQueSeraoExportados`) [I] limitam a ~32 mil trechos (coerente com o bug "travamento com mais de 32 mil ramais" corrigido em 7.4.3); cota 0 silenciosa; caminhos fixos (`C:\Program Files\GeoSan\...`); arquivo de depuração gravado a cada nó; leitura de senha do `.ini` com cifra caseira; desenho de estado só `Open`.

---

## 6. Lacunas do escritor `.inp` do SIGSAN

Arquivo analisado: `intelligence/sigsan-gis/.../epanet/EpanetSimulationService.java` (método `generateInpFile`) e entidades `Epanet*.java`, `GisNetworkElement`.

### 6.1 Seções EPANET

| Seção | Estado no SIGSAN | Falta |
|---|---|---|
| `[TITLE]` | ok | |
| `[JUNCTIONS]` | emitida | demanda sempre 0; padrão vazio; cota vem de `properties.elevation` ou 0 |
| `[RESERVOIRS]` | emitida, **sempre vazia** | `buildNetworkFromDma` manda `RESERVOIR` e `TANK` para o mapa de tanques; `createReservoir` nunca é chamado |
| `[TANKS]` | emitida | InitLevel 50 e MaxLevel 100 são tratados como "%" mas EPANET usa **unidade de comprimento**; Ø padrão 10; sem curva |
| `[PIPES]` | emitida | **Node1/Node2 vazios** (topologia não resolvida); Ø em **metros** (EPANET SI espera mm); rugosidade 0,01 comentada como "H-W C=100"; status impresso como `1/0` (EPANET aceita `OPEN`, `CLOSED`, `CV`) |
| `[PUMPS]` | emitida | sintaxe errada: `[PUMPS]` é `ID Node1 Node2 HEAD curva \| POWER kW [SPEED] [PATTERN]`; "Efficiency" é de `[ENERGY]`/curva; nós vazios |
| `[VALVES]` | emitida | toda `VALVE` (inclusive registro de manobra) vira PRV 50; nós vazios; registro deveria ser pipe com status |
| `[OPTIONS]` | emitida | `Duration`, `Hydraulic Timestep`, `Quality Timestep`, `Pattern Timestep`, `Report Timestep`, `Statistic` pertencem a **`[TIMES]`**; faltam `Units`, `Headloss`, `Viscosity`, `Specific Gravity`, `Demand Multiplier`, `Trials`; `Max Trials` [I] não é palavra-chave EPANET |
| `[TIMES]` | **ausente** | Duration, Hydraulic/Pattern/Report Timestep, Pattern/Report Start, Start ClockTime, Statistic |
| `[PATTERNS]` | **ausente** | padrões horários (equivalente a `X_PATTERNS`; categorias residencial, comercial, industrial) |
| `[CURVES]` | **ausente** | curva de bomba, rendimento, volume do tanque, perda de GPV |
| `[DEMANDS]` | **ausente** | múltiplas demandas por nó (categoria + padrão) |
| `[CONTROLS]`, `[RULES]` | **ausente** | controle de bomba por nível do tanque; regras |
| `[STATUS]` | **ausente** | estado inicial de bomba/válvula/registro |
| `[ENERGY]` | **ausente** | eficiência global, preço, padrão de preço |
| `[EMITTERS]` | **ausente** | vazamento (emissor); `emitterCoeff` existe no modelo mas não é emitido nem separado de `[JUNCTIONS]` |
| `[QUALITY]`, `[REACTIONS]`, `[SOURCES]`, `[MIXING]` | **ausente** | fase de qualidade (cloro residual) |
| `[REPORT]` | **ausente** | |
| `[COORDINATES]`, `[VERTICES]`, `[LABELS]`, `[BACKDROP]` | cabeçalhos **sem dados** (comentários "Add coordinates") | coordenadas em SIRGAS 2000 geográfico (4674) precisam de projeção plana (UTM) para o EPANET |
| `[TAGS]` | ausente | útil para guardar `dma_id`/`element_code` real |
| `[END]` | **ausente** | |

### 6.2 Defeitos de código (além das seções)

1. **Locale:** `String.format("%.2f")` usa o locale padrão; numa JVM pt-BR grava vírgula decimal e corrompe o arquivo. Usar `Locale.ROOT` e `BigDecimal.toPlainString()` (evita notação científica).
2. **Topologia inexistente:** `GisNetworkElement` não tem `node_from`/`node_to`. Os comentários "resolved from topology" não têm implementação.
3. `EpanetNetwork.nodeCount()` soma bombas como nós, e `linkCount()` soma bombas como links: bombas são links.
4. IDs: EPANET limita IDs (31 caracteres, sem espaços e sem `;`); `element_code` livre quebra. Precisa tabela de mapeamento `epanet_id <-> element_id`.
5. Comprimento: `ST_Length` em graus se a geometria for 4674. Usar geografia (`::geography`) ou projeção métrica.
6. Elevação silenciosa 0 quando falta `properties.elevation`. Deve virar erro de validação (ou cota por DEM com proveniência).
7. Nenhuma validação prévia: nó sem link, grafo desconexo, ausência de fonte (reservatório ou tanque), comprimento zero, Ø ausente, demanda negativa.
8. Pump power 50 kW e eficiência 0,75 inventados quando falta dado; PRV 50 inventada. O diário prega "honestidade": o escritor deve recusar em vez de inventar.
9. `generateInpFile` é privado e de 80 linhas num serviço de 470; extrair para `InpWriter` testável.

### 6.3 Plano de teste recomendado

- Teste de ida e volta: gerar `.inp`, abrir no engine, comparar contagem de nós/links.
- Redes de referência do próprio EPANET (Net1, Net3) com resultados conhecidos, e rede mínima desenhada à mão (reservatório, 1 pipe, 1 junção) com pressão calculável.
- Teste de locale (rodar com `-Duser.language=pt -Duser.country=BR`).

---

## 7. Solver EPANET real para o SIGSAN

### 7.1 Opções [F] onde indicado (licenças consultadas na web em 11/10/2026)

| Opção | Licença | Integração com a JVM | Prós | Contras |
|---|---|---|---|---|
| **EPANET 2.2 nativo** (`libepanet2` ou CLI `runepanet`) | **MIT** [F] (OpenWaterAnalytics/EPANET, LICENSE) | CLI via `ProcessBuilder` (MVP); biblioteca via JNA/JNR ou FFM (Java 21 = preview; final no Java 22) | Engine de referência; 2.2 tem **projetos múltiplos** (`EN_createproject`, seguro para vários tenants no mesmo processo); PDA, controles, qualidade | Build/binário por arquitetura; falha nativa derruba a JVM se in-process; saída binária `.out` exige parser |
| **epanet-js** (npm, WASM) | **MIT** [F] (registro npm, v0.9.0, motores 2.2 e 2.3.x, mais variantes MSX) | **Sidecar Node** (Node 24 já existe no ambiente) por HTTP/gRPC, ou no **navegador** | Mesmo engine em C compilado; sem binário nativo; roda no frontend para "e se" interativo; isolamento por processo; simples de containerizar | Outro runtime; pacote ainda 0.x; limite de memória de WASM (redes enormes); serialização `.inp`/JSON |
| **Baseform Epanet-Java-Library** | **GPL v3** [F] (API do GitHub); port de EPANET 2.0, último push 2017 | JAR puro, in-process | Sem nativo | GPLv3 (risco se distribuir); versão antiga (sem PDA/2.2); sem manutenção |
| **WNTR** (Python) | **BSD revisada** [F] (Sandia; dependências com licenças próprias) | Sidecar Python (`uv`, FastAPI); `EpanetSimulator` usa o toolkit EPANET 2.2 | Leitura/escrita de `.inp` robusta; PDD, vazamentos, criticidade, incêndio, calibração, resiliência; ótimo para **análise de perdas** | Pesado e lento de iniciar; um runtime a mais; `WNTRSimulator` puro-Python é mais lento e menos estável que o EPANET |
| GraalWasm/GraalJS in-process | n/a | complexo | | maturidade baixa; descartar |

### 7.2 Recomendação

1. **Porta `HydraulicSolver`** no `sigsan-gis` (entrada: `EpanetNetwork` ou `.inp`; saída: `SimulationResult`). `runEpanetWasm` vira apenas uma implementação; renomear.
2. **Fase 1:** sidecar com **epanet-js (motor 2.2)**: recebe `.inp` + opções, devolve nós/links por passo de tempo em JSON. Mesmo módulo no navegador para simulação interativa. Motivo: MIT, zero binário nativo, aproveita o Node que o ambiente já tem, e preserva a honestidade (`solverExecuted=true` só quando o engine retornar sem erro).
3. **Fase 2 (opcional):** serviço **WNTR** para análises avançadas (vazamentos/emissores, falta de água por manobra, vazão de incêndio). Manter fora do caminho crítico.
4. **Plano B:** CLI nativo do EPANET 2.2 dentro da imagem do serviço, se o sidecar Node não for aceito pela operação.
5. Em todos os casos: validar o `.inp` com o próprio engine na CI; guardar versão do engine e hash do `.inp` junto com cada resultado (auditoria/LGPD de modelos); limitar tamanho/tempo por tenant.

---

## 8. Arquitetura do app VB6

### 8.1 Componentes

| Projeto (pasta) | Binário | Papel | Dependências proprietárias / riscos |
|---|---|---|---|
| `trunk/GeoSan.vbp` (Forms, Modules, classes, Controles) | `GeoSan.exe` | Aplicação principal: tela de mapa (`frmCanvas`, ~126 KB), cadastro de ramais, relatórios, importação/exportação, EPANET (navegador, curvas, padrões), impressão (`frm1TePrinter`) | VB6 (sem suporte desde 2008), ActiveX, ADO 2.6, TeCom |
| `Projeto_NxConnection` | `NexusConnection.dll` | Abstração de conexão (Access 0, SQL Server 1, Oracle 2, PostgreSQL 4); lê `GEOSAN.INI`; cifra caseira de senha | `.ini` com credenciais; sem TLS configurável |
| `Projeto_PManager4` | `NexusPM4.ocx` | **Gerenciador de propriedades**: edição dos atributos dinâmicos (EAV) de trechos e componentes | OCX COM; depende de TeCom |
| `Projeto_NxViewmanager` | `NxViewManager.ocx` | **Vistas e temas** por usuário (usa `te_view`/`te_theme`; grava `FTema.txt`) | OCX; `LoozeXP.ocx` (terceiro) |
| `Projeto_NSecurity` | `NSecurity.dll` | Login, usuários, grupos, recursos, troca de senha | ver seção 1 |
| `Projeto_NxUsers` | `NUsers.dll` | Modelo de usuários (`clsUsers`) | idem |
| `Projeto_ValidaBase` | `ValidaBase.exe` | Corretor de base (13 verificações) | roda com credencial admin |
| `Projeto_AtualizaApp` | `GeoSanIni.exe` | **Atualizador automático**: baixa binários de um servidor web (controle Internet Transfer) e atualiza o GeoSan | supply chain (sem assinatura observada) |
| `Projeto_ExportEpaNe` | `Exporte EPANet.exe` | Exportador `.inp` (seção 5) | TeComDatabase/TeComNetwork |
| `install/TeCom` (3.3.1 e 4.2.0) | DLLs `TeCom*` | Componentes COM sobre TerraLib: `Canvas`, `Database`, `Export`, `Import`, `Network`, `Printer`, `ViewDatabase`, `ViewManager`, `Geometry` (4.2) + `terralib.dll`, `libpq`, `shapelib`, `libxml2` | binários sem licença no repositório |
| `install/GeraSetup*` | Inno Setup | Instaladores (GeoSan e ValidaBase) | |
| `dlls/` | `NexusPM4.ocx`, `NxViewManager.ocx`, `NexusConnection.dll`, `NUsers.dll`, `NSecurity.dll` (+ versões "old") | cópia de distribuição | |
| `db/` (Oracle, Postgres, SqlServer) | scripts e `banco_geosan.backup` | modelo de banco padrão | pode servir de **dados de teste** (conferir licença) |

### 8.2 Observações de arquitetura

- **Cliente gordo + banco compartilhado**, sem camada de serviço. Regras no cliente (topologia, cálculo de cota, isolamento). Concorrência por `OBJECT_ID_` manual.
- **EAV** para atributos de redes e componentes: flexível, mas lento e difícil de validar. No SIGSAN: colunas tipadas + `jsonb` (já usado em `properties`).
- **Metadados TerraLib** (`te_*`) acoplam o cadastro ao produto antigo; geometrias em tabelas `PointsN/LinesN/PolygonsN` por layer.
- **Estado em arquivos** (`UserLog.txt`, `FTema.txt`, `variaveisGlobais.txt`) para falar entre executáveis. Fonte de bugs multiusuário.
- Hard code de `C:\Arquivos de Programas\GeoSan` e Windows XP como alvo; 32 bits; instalação exige desligar antivírus e dar permissão total na pasta.

### 8.3 Módulos e formulários mais reveladores

`frmCanvas` (despacho de ferramentas `k*`), `frmCadastroRamal*` (ramais, auto-lote, esgoto, filtro), `frmAtualizacaoConsumo` (consumo e demanda), `frmConsumidoresDesabastecidos`, `frmVerificaConectividade`, `frmEPANavegator`/`frmEPANetNavegador`/`frmEPACurves`/`frmEPAPatterns`, `frmConsumoLote(Graf)`, `frmNetWorkLegth`, `frmImportarCotas`, `frmImportDxf`/`frmImportFile`, `frmRelatoriosAvancados`, `frmIndicadorProdutividade`, `frmEntradasDetecta`/`calcDetecta.bas` (exportação para o aplicativo "Detecta", 2010), `mdlRelatoriosProdutividade.bas`, classes `CIntBilinear`, `CObtemZdoNo`, `CAcertaZsDosNos`, `CLocalizaTrechos`, `CDistRamalInicioTrecho`, `CCoordXYaDistLinha` (geometria linear), `clsRamalController/Model`, `clsConsumidorController/Model` (já com padrão MVC), `CEmail`, `CPrintErro`.

---

## 9. Evolução por versão

[F] O arquivo `trunk/Updates.txt` só lista dois executáveis (GeoSan 7.0.0.1 e GeoSanIni 2.0.0.0) para o atualizador. O histórico real está em `doc/00. Leiame.txt` (e na história Git).

| Período / versão | O que mudou |
|---|---|
| Release 5.9.0 a 5.9.8 | indicador de produtividade de esgoto; cotas de PV com 3 casas; bloqueio de rede duplicada (mesmas peças inicial/final); ramais de esgoto; salvar mapa como imagem; módulo de impressão A0-A5; localizar textos; pesquisa por ligação/nome; relatório de ramais personalizado; filtro de ramais; definir escala; busca por IPTU; calcular área; painel de status; `.ini` no lugar de `.cfg`; visitante com documentos e localizar válvulas; redes de drenagem |
| 6.0.0, 6.0.5, 6.0.7 (fev/2012) | **PostgreSQL**; SQL Server 2008; perfis visitante/visualizador |
| 06.00.07.x (set a nov/2012) | consultas de demanda otimizadas; índices; rugosidade por material; tratamento de timeout; Leiame unificado |
| 06.08.00 a 06.08.02 (nov/dez 2012) | distinção válvula x registro; filtros de ramais; ligação ao comercial via `GS_PARAMETERS`; ValidaBase (nós sem atributos, unicidade); **exportação shape** desnormalizada |
| 06.09.00 a 06.09.17 (jan a mai/2013) | índices nas vistas; **requisitos EPANET** (somente VRP como válvula; reservatórios RNF/RNV; bomba/booster; padrões e curvas); parada do isolamento em REGISTER; Ø da válvula; relatórios no navegador; ValidaBase para nós soltos; versão instalável; mover nó com ramais; envio de erro por e-mail |
| 06.10.00 a 06.10.14 (23/mai a jun/2013) | **Cota Z por MDT** (cálculo em lote e ao desenhar), barra de progresso e ESC; conversão L/s e m³/mês; exportar consumos por mês/ano; correções de cota de esgoto |
| 06.10.15 a 06.10.27 (jun a jul/2013) | gestão de erros de rede lenta; **auto-atualização** (GeoSanIni) |
| 07.00.xx (2014 a 2016) | revisão das vistas do comercial (spec 07.00.31, 2015); parâmetros EPANET novos nas scripts `7.0.43`/`7.0.44` (POWER, CARGA, VAZAO, RENDIM, CURBOMBA, CURREND, SPEED, PATTERN, TYPE, SETTING, DIAMETER, MINORLOSS, níveis do tanque); guia de implantação com PostgreSQL e views do **GSAN** (2015) |
| 7.2 a 7.5.6 (git, 17/out/2016 a 19/jun/2018) | **cadastro automático de ramais a partir do coletor GPS do GSAN** (7.3.3), associação rápida de ligações (7.4.0), banco modelo (7.4.1), correção de travamento com >32 mil ramais (7.4.3), indicadores de produtividade por ligação (7.5.0 a 7.5.2), shape de consumidores (7.5.5), instruções de conexão ao comercial |

---

## 10. Entregas pedidas

### 10.a Catálogo GIS priorizado para a versão web (MapLibre + PostGIS + pg_tileserv/GeoServer)

| Prio | Funcionalidade | Como fazer na web | Origem GEOSAN |
|---|---|---|---|
| P0 | Mapa base + camadas do tenant (redes, componentes, ramais, DMC/setores) | vector tiles via `pg_tileserv` ou Martin sobre views por tenant (RLS ou schema); MapLibre já está no `package.json` | vistas/temas |
| P0 | Identificar objeto + painel de atributos (somente leitura) | clique + `GET /gis/elements/{id}` | gerenciador de propriedades, perfil Visualizador |
| P0 | **Busca**: por inscrição, ligação/matrícula, consumidor, endereço, coordenada, id do objeto | endpoint de busca no comercial + geocoding interno | Encontrar consumidores/objeto/coordenada |
| P0 | **Topologia obrigatória** de rede (trecho sempre com nó inicial e final) | tabelas `gis_node` e `gis_pipe` com FK; snapping com tolerância; transação | Desenhar rede |
| P0 | Validador de cadastro (as 13 verificações) como jobs SQL com relatório | consultas PostGIS (`ST_DWithin`, `ST_Equals`, `ST_StartPoint`) | ValidaBase |
| P0 | Cota por DEM/MDT | raster no PostGIS e `ST_Value(..., resample)` ou amostragem em lote com GDAL | Cota Z |
| P1 | Edição: desenhar, dividir trecho por componente, mover nó (arrasta trechos e ramais), excluir, mover vértice | MapLibre + `maplibre-gl-draw` ou `terra-draw`; transações no servidor com versão (`version` do `BaseEntity`) | 5.2 a 5.6 |
| P1 | Ramais: criar, associar N ligações, ligação fictícia, "associar à rede mais próxima" (raio 5 m) | `ST_ClosestPoint`, `ST_DWithin` | seção 2.3 |
| P1 | **Isolamento de trecho / consumidores desabastecidos** | grafo (JGraphT no serviço ou `pgRouting`) com parada em registro; lista via ramais | `SELECTRede` |
| P1 | Seleção por polígono (desenhado ou setor) e **operações em lote** (exportar `.inp`, alterar consumo, relatório) | `ST_Within` + job assíncrono | Operações por polígono |
| P1 | Temas e filtros por atributo | expressões MapLibre + filtro SQL parametrizado | filtros |
| P1 | Importar/exportar SHP, GeoJSON, DXF | `ogr2ogr` (GDAL) em job | importar/exportar |
| P1 | Papéis: administrador, editor, visitante, visualizador | Keycloak (já no stack), RBAC por camada | SYSTEMUSERS |
| P2 | Documentos anexados a pontos | SeaweedFS/S3 + tabela | `X_FILES` |
| P2 | Medir distância/área, calcular declividade | Turf.js e SQL | calcular área |
| P2 | Impressão A0-A5 | renderização server-side (MapLibre headless ou QGIS Server) | módulo de impressão |
| P2 | Manobras programadas com setores afetados, visíveis ao atendimento | tabela `maneuver` + camada | `NXGS_MANOBRA` |
| P2 | Produtividade de cadastro (km desenhados, ramais por usuário/data) | `audit` + agregações | indicadores |
| Não portar | Auto-atualizador, módulo de e-mail de erro, login automático local | substituídos por deploy web e observabilidade | |

### 10.b O que depende de TerraLib/Nexus e o substituto OSS

| Dependência | Função | Substituto |
|---|---|---|
| TerraLib 3.3.1 / TeCom 4.2 (Canvas, Database, Network, Printer, ViewManager) | desenho, banco geográfico, operações lineares, impressão, vistas | **PostGIS** (armazenamento e topologia), **MapLibre** (render), funções `ST_LineInterpolatePoint`, `ST_LineLocatePoint`, `ST_DumpPoints`, `ST_Length(geography)`, **JTS** em Java |
| TeImport/TeExport (shape, MIF, DXF) | I/O de formatos | **GDAL/OGR** (`ogr2ogr`), **GeoTools** na JVM |
| Tabelas `te_*` (metadados) | layers, vistas, temas, projeção | `geometry_columns`/`spatial_ref_sys` do PostGIS + tabelas próprias de estilo (JSON de estilo MapLibre) |
| `NexusPM4.ocx` (propriedades) | edição de atributos por tipo | formulário dinâmico guiado por metadados (as tabelas `*SUBTYPES`/`EPAREF` são um bom modelo conceitual) |
| `NxViewManager.ocx` | vistas por usuário | tabela `user_map_view` + camadas |
| `NexusConnection.dll`, `NUsers.dll`, `NSecurity.dll` | conexão, usuários, permissões | Spring + Keycloak + RLS (já existem) |
| `Exporte EPANet.exe` | exportador | `InpWriter` + `HydraulicSolver` (seções 6 e 7) |
| `ValidaBase.exe` | corretor | jobs de validação |
| `GeoSanIni.exe` | auto-update | deploy web |
| MDT em tabela + bilinear caseiro | cota | PostGIS raster ou GDAL (`gdallocationinfo`/`rasterio`) com interpolação bilinear |
| AutoCAD + Bing para satélite | camada de fundo | OSM, IBGE ou ortofoto municipal em XYZ/COG; respeitar termos |

### 10.c Integração GIS e comercial, perdas por DMC e balanço IWA/AWWA

**Contrato GEOSAN-comercial (conceito):** três vistas no banco comercial, chave `NRO_LIGACAO` (+ versão sem dígito verificador por desempenho).
- `LIG_COMERCIAL`: ligação, **inscrição** (`CLASSIFICACAO_FISCAL` = localidade.setor.quadra.lote.sublote formatado), logradouro, nº, bairro, hidrômetro, cliente, telefones, tipo/situação (ligada, suprimida, cortada, potencial, factível), economias, hidrometrado, datas de implantação (água e esgoto), rota/grupo/roteiro/sequência de leitura.
- `LIG_COMERCIAL_CONSUMO`: faturado e medido por mês/ano.
- `LIG_COM_CONSUMO_MEDIO`: média de 6 ou 12 meses.
- No GSAN (doc `Implantação Postgres-LogPro`): `cadastro.imovel`, `setor_comercial`, `quadra`, `logradouro(_bairro)`, `cliente(_imovel,_fone)`, `atendimentopublico.ligacao_agua(_situacao)`, `micromedicao.hidrometro(_instalacao_historico)`, `medicao_historico`, `consumo_historico`, `rota`. **Este mapeamento deve ser cruzado com o relatório do GSAN (fatia sua colega).**

**Ligação no mapa (SIGSAN):**
`ramal (geom linha + ponto do hidrômetro) 1---N ligação (imovel_id)`; `ramal.pipe_id` para trecho; `ramal.dma_id` derivado por `ST_Within(ponto, dma.boundary)`. Chave de busca por **inscrição** deve ser armazenada normalizada, e busca livre por endereço usa o cadastro de logradouros.

**Alimentando o `LossesService` (hoje 100% placeholder):**

| Campo do balanço IWA | Fonte proposta | Observação |
|---|---|---|
| Volume de entrada (SIV) | macromedidores de entrada/saída do DMC (`DmaDistrict.inputMeterId/outputMeterId`), telemetria (SCADA/MQTT) | componentes "macromedidor" e "medidor de vazão" do cadastro identificam os pontos |
| Consumo autorizado faturado | soma de `consumo_faturado` das ligações cujo ponto de hidrômetro está no polígono do DMC, no período | usar a competência (mês/ano) e converter período para dias |
| Consumo autorizado não faturado | OS de campo (descarga, lavagem, combate a incêndio, hidrantes) | integra `operations/field` |
| Consumo não autorizado | ligações sem ramal no mapa, ramais sem ligação, ligações cortadas/suprimidas com consumo, ligações "fictícias" | estimativa por regra, com grau de confiança |
| Imprecisão de medição | idade e classe do hidrômetro (cadastro de hidrômetros) | hoje fixo em 2% |
| Perdas reais | por diferença (já implementado), **ou** vazão mínima noturna com fator de pressão | `calculateMinimumNightFlow` existe como esqueleto |
| **UARL** | `Lm` = soma `ST_Length` dos trechos do DMC (km); `Nc` = número de ramais/ligações ativas; `Lp` = trecho privado do ramal entre testada e hidrômetro (`DISTANCIA_TESTADA` do GEOSAN é um proxy [I]); `P` = pressão média do DMC (simulação EPANET ou loggers) | hoje `Lp=5`, `Nc=1000`, `P=30` entram como valores inventados quando faltam dados |
| Nível de confiança dos dados | indicadores do cadastro: % ramais georreferenciados, tempo desde a última atualização, aprovação na validação, `INFORMATIONVALIDITY` | alimenta `dataQualityGrade`; o questionário NEXUS tem uma escala de 5 níveis (desatualizado a totalmente atualizado) |

**Defeito de unidade no `LossesService` [F]:** `calculateUARL` devolve m³/**dia** e o ILI divide `realLosses` do **período** por esse valor. Multiplicar o UARL pelos dias do período (ou anualizar os dois lados). Teste com volumes conhecidos (item 11.1 do diário).

**Uso do EPANET para perdas [I]:** a pressão média por DMC e a pressão noturna vêm do modelo calibrado; perdas reais podem ser distribuídas como **emissores** (`Q = C x P^N1`, `emitterExponent` já existe no modelo, padrão 0,5) proporcionalmente a comprimento x pressão, calibrando contra a vazão mínima noturna.

---

## 11. Recomendações para o SIGSAN

Legenda de esforço: P pequeno (até 3 dias), M médio (1 a 3 semanas), G grande (mais de 3 semanas).

### P0 (bloqueia qualquer valor real)

| # | Ação | Módulo | Já existe | Falta | Risco / esforço |
|---|---|---|---|---|---|
| 1 | **Corrigir o escritor `.inp`**: extrair `InpWriter`; `Locale.ROOT`; `[OPTIONS]` e `[TIMES]` corretos (`Units LPS`, `Headloss H-W`, `[END]`); Ø em mm; status `OPEN/CLOSED/CV`; sintaxe de `[PUMPS]`; reservatórios emitidos; `[COORDINATES]` e `[VERTICES]` em projeção plana; `[PATTERNS]`, `[CURVES]`, `[DEMANDS]`, `[STATUS]` | sigsan-gis | `generateInpFile` (80 linhas, 7 seções inválidas) | seções e testes (seção 6) | Baixo risco, esforço **P a M** |
| 2 | **Topologia explícita**: `gis_node` e `gis_pipe` com `node_from/node_to`, snapping e validação (grafo conexo, ao menos uma fonte, sem duplicados) | sigsan-gis, operations/assets | `GisNetworkElement` genérico, sem ligações | migration, repositório, regra transacional; derivar de geometrias existentes por `ST_StartPoint/ST_EndPoint` | **Alto**; esforço **M a G** |
| 3 | **Solver real** atrás de `HydraulicSolver` (epanet-js sidecar) e resultados persistidos | sigsan-gis | stub honesto, `storeSimulationResults` vazio | sidecar, contrato, testes com Net1/Net3, persistência | Médio; **M** |
| 4 | **Cotas**: DEM/MDT no PostGIS + job de atribuição de cota por nó, com proveniência e **erro em vez de zero** | sigsan-gis | `elevation` em `properties` ou 0 | raster, `ST_Value` bilinear, job, flag de qualidade | Baixo; **M** |
| 5 | **Demandas** a partir do comercial: consumo médio por ligação para L/s (`x1000/30/86400`), alocação 50/50 nos nós do trecho do ramal, `[DEMANDS]` por categoria | sigsan-gis, commercial, losses | nada | vistas/contrato de integração, entidade `service_connection` ligada ao trecho | Médio-alto (depende do GSAN); **M** |
| 6 | **Alimentar o `LossesService`** com dados reais (SIV, faturado, UARL por geometria) e corrigir unidade UARL x período | sigsan-losses | estrutura IWA com placeholders | consultas por DMC, testes com volumes conhecidos | Alto valor; **M** |
| 7 | **Higiene de segredos** do conhecimento herdado: nada de credenciais em docs; revisar `.env.example` | todos | | | **P** |

### P1

| # | Ação | Módulo | Já existe | Falta | Risco / esforço |
|---|---|---|---|---|---|
| 8 | Página de mapa (MapLibre) com camadas, identificar e busca por inscrição/ligação/consumidor | frontend | `maplibre-gl`, `@vis.gl/react-maplibre` e `Consumers.tsx` (sem mapa) | tiles (pg_tileserv/Martin; GeoServer está comentado no compose), páginas | **M** |
| 9 | Validador de cadastro (portar as 13 verificações como regras SQL + relatório) e **gate** antes de exportar/simular | sigsan-gis, operations/assets | nada | regras e tela | **M** |
| 10 | Ramais e ligação ao comercial (CRUD, fictícias, associação por proximidade, georreferenciamento por GPS de campo) | operations/field, commercial | módulo vazio | entidades e app de campo (Capacitor) | **G** |
| 11 | Isolamento de trecho e consumidores afetados + manobra programada | sigsan-gis, operations/field | nada | grafo, tipos de componente com `Specification` (REGISTER/REG. FIXO/VALVE), tela | **M** |
| 12 | Tabela de configuração de parâmetros EPANET por tenant: rugosidade por material e idade, padrões de consumo, curvas, tipos de componente com `epanet_role` | sigsan-gis | `Specification_`/`EPAREF` do GEOSAN como modelo conceitual | tabelas e UI | **M** |
| 13 | Operações por polígono (setor/DMC): exportar `.inp`, alterar consumo, relatório; jobs assíncronos | sigsan-gis | `DmaDistrict.boundary` | endpoints e fila | **M** |
| 14 | Importar/exportar SHP/GeoJSON/DXF via GDAL | sigsan-gis | nada | job com `ogr2ogr`, validação de SRID | **M** |

### P2

| # | Ação | Módulo | Observação |
|---|---|---|---|
| 15 | Análises avançadas com WNTR (vazamentos/emissores, falta de água, incêndio) e calibração por vazão mínima noturna | sigsan-gis, sigsan-losses | alto valor para perdas; depende de P0 3 e 5 |
| 16 | Controles e regras (`[CONTROLS]`, `[RULES]`), qualidade da água (`[QUALITY]`, cloro) integrada a `sigsan-quality` | sigsan-gis | |
| 17 | Documentos anexados a objetos do mapa; impressão A0-A5 em PDF | frontend, grs | |
| 18 | Indicadores de produtividade de cadastro e trilha de auditoria por objeto | operations/assets | `AuditLogRepository` já existe |
| 19 | Dados de teste: banco modelo `db/*/banco_geosan.backup` (verificar licença e anonimização) para popular um tenant de demonstração | platform | só como massa de teste |

### Itens para o Diário de Bordas (sugestão)

- Registrar a decisão: **GEOSAN é fonte de requisitos, não de código** (GPL-2.0-only).
- Corrigir no `EpanetSimulationService`: `Locale`, `[OPTIONS]` x `[TIMES]`, topologia, reservatórios, `nodeCount/linkCount`.
- `LossesService`: unidade do UARL (m³/dia) contra volume do período.
