# 19c — Normativos e padrões para o cadastro técnico em GIS

Data da pesquisa: 12/10/2026. Escopo: normas técnicas (ABNT), cartografia e geodésia, regulação do saneamento que alcança o cadastro de ativos, padrões abertos de dados geoespaciais e LGPD aplicada a dados georreferenciados. Complementa, sem repetir, os relatórios `08` (NR da ANA, SINISA, tarifa), `10` (LGPD), `12` (resíduos e MTR), `14` (consolidação) e `editais/18d` (aderência GIS dos editais).

> **Método e limites.** Os MCPs Perplexity (401) e Brave (token inválido) falharam nesta sessão; a pesquisa usou busca web e leitura direta de PDFs oficiais (MCPSB e Resolução 302 da ANA, manual da NR 12, glossário SINISA de esgoto, padrão de cadastro da Corsan). **A situação das normas ABNT veio de revendedores autorizados (normas.com.br, Target), não do ABNT Catálogo**: todas devem ser conferidas lá antes de citação contratual. Normas ABNT são protegidas; este documento cita número, título, edição e objeto, sem transcrever texto. Não é parecer jurídico.

---

## 1. Achados que mudam o modelo de dados

1. **Existem normas ABNT específicas de cadastro**: NBR 12586 (água) e NBR 12587 (esgoto), ambas de 04/1992, reconfirmadas (a 12587 em 01/2022). São as referências que prestadoras como Corsan e Sanepar citam para cadastro técnico. Nenhuma das buscas anteriores do projeto as mencionava.
2. **A NR 16/2026 da ANA (MCPSB) define a unidade de registro patrimonial das redes**: "estruturas lineares de saneamento" são controladas **em massa, por metro**, com 11 subdivisões (rede de distribuição, adutora, rede coletora, coletor-tronco, interceptor, emissário, linha de recalque, interligação, extravasor, lododuto, **rede de drenagem**), vida útil de 50 anos e taxa de 2% a.a. O manual admite expressamente o **cadastro técnico e registros georreferenciados** como base complementar do inventário de bens de massa, com rastreabilidade. O GIS do Ziip SAN passa a ser fonte do inventário regulatório.
3. **A NR 12/2025 da ANA (drenagem) exige manter cadastro técnico atualizado e georreferenciado** dos elementos do sistema de drenagem, e o manual da ANA associa esse cadastro à conciliação físico-contábil dos ativos.
4. **NBR 17287 (05/2026)**: nova norma de operação de redes de distribuição de água, alinhada à NBR 12218 e à NBR 5626. Conteúdo sobre cadastro: a confirmar.
5. **NBR 7675 foi reeditada em 08/2026** (ferro dúctil), assim como a NBR 10160 (tampões e grelhas). Catálogos de material e de tampão devem ter vigência por edição de norma.
6. **SINISA pede coordenadas** (WGS-84, 7 casas decimais) de estações elevatórias e ETEs, e define a extensão da rede de esgoto (GTE1001) incluindo coletores-tronco e interceptores e excluindo ramais, emissários e recalques. A regra de soma precisa estar no modelo.

---

## 2. Quadro normativo

Legenda da última coluna: **O** = obrigatório (por lei, regulamento, ou quando o contrato, o edital ou a ERI do cliente o referencia; norma ABNT em produto ou serviço ao consumidor também por força do CDC, art. 39, VIII); **R** = recomendado.

### 2.1 ABNT: projeto, execução, cadastro e materiais

| Norma (edição) | O que regula | Impacto no Ziip SAN | O/R |
|---|---|---|---|
| NBR 12586:1992 — Cadastro de sistema de abastecimento de água | Condições para elaborar o cadastro de sistemas de água | Base do catálogo de feições de água e do checklist de completude; detalhes (elementos, escalas) a confirmar no texto | O (quando exigida) |
| NBR 12587:1992 (conf. 01/2022) — Cadastro de sistema de esgotamento sanitário | Idem, para esgoto | Idem, para esgoto | O (quando exigida) |
| NBR 12211:1992; 12213:1992; 12216:1992; 12217:1994 | Concepção, captação superficial, ETA, reservatório | Tipos de unidade localizada (ponto/polígono) e atributos de projeto | R |
| NBR 12212 — poço tubular (projeto); NBR 12244:2006 (construção) | Poço tubular | Feição "poço" com profundidade, vazão outorgada; edição da 12212 a confirmar (provável 2017) | R |
| NBR 12214:2020 | Estação de bombeamento de água | Feição EEA/booster, vínculo com linha de recalque | R |
| NBR 12215-1:2017 | Adutora (conduto forçado) | Classe "adutora" distinta de "rede" (regra SINISA e MCPSB) | R |
| NBR 12218:2017 (conf. 01/2022) | Projeto de rede de distribuição; prevê modelagem hidráulica | Atributos hidráulicos (DN, material, rugosidade, cota, zona de pressão) e exportação EPANET | O (quando exigida) |
| NBR 17287:2026 | Operação de redes de distribuição | Manobras, setorização, registro de intervenções; ler o texto antes de modelar | R (a confirmar) |
| NBR 5626:2020 (a confirmar) | Instalação predial de água | Só para marcar o limite rede/ramal/cavalete (ponto de entrega) | R |
| NBR 9648:1986; NBR 9649:1986 | Concepção e projeto de redes coletoras de esgoto | Trecho entre singularidades (PV, TL, TIL), cotas de fundo, declividade, profundidade | O (quando exigida) |
| NBR 14486:2000; NBR 7367:1988 | Redes coletoras em PVC; assentamento de PVC | Material e regras por material | R |
| NBR 12207:2016; 12208:2020; 12209:2011; 17076:2024 | Interceptores, elevatórias, ETE, ETE de menor porte | Classes "interceptor", "EEE", "ETE" com hierarquia | R |
| NBR 17015:2023 | Execução de obras lineares de água, esgoto e drenagem | Fonte do as-built de obra; não confirmei que substitui a NBR 12266 | R |
| NBR 12266 (edição a confirmar) | Valas para assentamento | Profundidade e envoltória no as-built | R |
| Drenagem urbana | **Não há NBR de projeto de microdrenagem pública**; usa-se NBR 10844:1989 (predial), NBR 8890 (tubos de concreto, edição a confirmar), NBR ISO 21138-1/-3:2021, DNIT 030/2004-ES e manuais municipais | Catálogo de drenagem configurável por tenant, sem regra rígida de norma | R |
| NBR 5647-1:2025; NBR 7665:2025; NBR 15750:2024 | Tubos PVC-U (DN ≤ 100), PVC-M DEFOFO, PVC-O | Domínio de material e classe de pressão, com edição da norma | R |
| NBR 15561:2024; NBR 15802:2010 | Tubos PE 80/PE 100; projeto em PE | Domínio PEAD (SDR, PN) | R |
| NBR 7675:2026; NBR 7362:2025 | Ferro dúctil (água); PVC parede maciça (esgoto) | Domínio de material | R |
| NBR 10160:2026; NBR 7676:2024 | Tampões e grelhas; anéis de vedação | Atributos de tampão (classe de carga) em PV e caixas | R |
| NBR 5667-1/-2/-3:2006 (conf. 04/2024) | Hidrantes urbanos de coluna, subterrâneos e com obturação própria | Feição hidrante com subtipo | O (quando exigida) |
| Registros e válvulas | Não identifiquei norma de produto específica nesta pesquisa | Domínio de tipo de válvula configurável | a confirmar |
| Desenho técnico: NBR 10647, 8402, 8403, 8196, 10068, 13142 (citadas pela Corsan, 2024) | Regras gerais de desenho | Só para pranchas exportadas; há indício de consolidação em NBR 16752 e 16861 (2020), a confirmar | R |
| Simbologia de redes | **Não há NBR de simbologia de redes de saneamento**; cada prestadora tem padrão próprio | Simbologia parametrizável por tenant | R |

### 2.2 Cartografia, geodésia e cadastro territorial

| Norma (vigência) | O que exige | Impacto no Ziip SAN | O/R |
|---|---|---|---|
| SIRGAS2000 (IBGE R.PR 1/2005; fim da transição pela R.PR 1/2015) | Referencial geodésico oficial | SRID de armazenamento 4674 (já adotado); proibir SAD69/Córrego Alegre sem conversão | O |
| Referencial altimétrico: Datum de Imbituba (Santana no Amapá); altitudes normais do REALT-2018; conversão pelo hgeoHNOR2020 | Altitude física para cotas | Campo de datum vertical e método da cota; conversão de altitude elipsoidal (GNSS) obrigatória antes de calcular declividade | O |
| Projeção UTM/SIRGAS2000: EPSG 31978–31985 (fusos 18S–25S) e 31972–31976 (18N–22N) | Plano de trabalho métrico | SRID de trabalho por tenant (ex.: 31982 no RS/SC/PR/oeste de SP). Os fusos 31982–31985 não cobrem AC, RO, MT, AM, RR e AP | O (editais pedem) |
| NBR 13133:2021 | Execução de levantamento topográfico; plano topográfico local até 35 km | Método de levantamento e classe por feição | O (quando exigida) |
| NBR 14166:2022 (errata 03/2023) | Rede de Referência Cadastral Municipal (RRCM) | Vincular levantamento à RRCM do município quando existir | R |
| NBR 17047:2022 | Levantamento cadastral territorial para registro público; cancelou a NBR 14645-2 | Fora do escopo de redes; relevante para parcelas | R |
| NBR 14645-1:2001; 14645-3 (cancelada 09/2022, substituta a confirmar) | As built de **edificações** | Não é norma de rede; não usar como base do as-built de redes | — |
| Decreto 89.817/1984 (PEC) + ET-CQDG (DSG, 2016) e ET-ADGV (PEC-PCD) | Exatidão posicional por classe e escala: 90% dos pontos ≤ PEC e RMS ≤ EP | Relatório de acurácia por lote importado. PEC-PCD classe A: 0,28 mm × escala (EP 0,17 mm, a confirmar no texto); B/C/D = 0,5/0,3, 0,8/0,5, 1,0/0,6 mm | O (para produto cartográfico); R (para cadastro) |
| ET-EDGV 3.0 (CONCAR, 2017/2018) | Modelo de dados vetoriais da INDE | Mapear camadas de base (lotes, quadras, vias, hidrografia) para exportação; não modela rede de saneamento em detalhe | R |
| Decreto 6.666/2008 (INDE); Perfil MGB 2.0 (IBGE/DSG, ISO 19115-1) | Metadados e normas CONCAR para órgãos federais | Exportar metadados MGB por conjunto de dados; obrigatório só para publicação na INDE | R |
| Portaria MDR 3.242/2022 (revogou a Portaria MCidades 511/2009) | Diretrizes do Cadastro Territorial Multifinalitário: parcela, cadastros temáticos, vínculo à RRCM | Saneamento é cadastro temático: guardar inscrição imobiliária/ID da parcela do CTM na unidade consumidora | R |

### 2.3 Regulação do saneamento que alcança o cadastro

| Norma | O que exige | Impacto no Ziip SAN | O/R |
|---|---|---|---|
| Lei 11.445/2007, alterada pela Lei 14.026/2020 (art. 3º, I, "d", e art. 3º-D: drenagem; art. 3º-C: resíduos) | Drenagem e manejo de resíduos são serviços de saneamento | Drenagem e resíduos como domínios de primeira classe no GIS, não camadas acessórias | O |
| NR 16/2026 da ANA (Res. 302, 28/08/2026), MCPSB e MCRSB | Inventário e codificação patrimonial; ERIs com 3 anos para adotar e 5 para ajustar sistemas; prestadores até o exercício de 2032 | Atributos MCPSB na feição: LOC (código IBGE de 7 dígitos), BCP, BRV (reversível), NTS-SDS, UP, UAR, AT1–AT5; agregação de extensão em metros por subdivisão; conciliação GIS × patrimônio | O (prazo da ERI) |
| NR 3/2023 (indenização de investimentos não amortizados) | Levantamento de bens reversíveis | Data de implantação, origem do investimento e reversibilidade por trecho | O (em extinção de contrato) |
| NR 12/2025 (Res. 245, 17/03/2025) | Cadastro técnico atualizado e georreferenciado do sistema de drenagem, com material e estado de conservação | Classe de drenagem com estado de conservação, foto e histórico | O (onde a ERI adotar) |
| NR 9/2024 e NR 15/2025 | Indicadores e balanço hídrico padronizado (ver `08`) | Extensão de rede e ligações por setor/DMA vindas do GIS | O |
| SINISA (glossários 2025, ano-base 2024) | GTE1001 extensão da rede de esgoto (km, regra de inclusão própria); GTE0003/0004 ligações; GTE0006–0013 economias; coordenadas de EEE (GTE2002/2003) e ETE (GTE2103) em WGS-84 | Cálculo reprodutível por regra SINISA; exportação de coordenadas em graus decimais; código da extensão de água: a confirmar (equivale ao AG005 do SNIS) | O |
| Agências infranacionais | Não encontrei resolução da ARIS, ARESC, ARSAE ou ARSESP que exija cadastro georreferenciado; a AGENERSA fiscalizou cadastro técnico de esgoto em 2024 (diferença de 192 km entre bases); a exigência costuma estar no contrato de concessão | Perfil regulatório por tenant (ver `14`) com regra de cadastro configurável; "AGESAN" não identificada | a confirmar |
| Lei 12.305/2010 (PNRS) e Decreto 10.936/2022 | Coleta seletiva, logística reversa integrada ao SINIR, MTR (ver `12`) | Feições de ecoponto/PEV, setor e rota de coleta, frequência; o decreto não exige georreferenciamento expresso (a confirmar no texto integral) | R |
| NR 7/2024 e NR 14/2025 (resíduos) | Condições gerais e indicadores de SLU (ver `08`) | Setores e rotas georreferenciados alimentam indicadores | R |

### 2.4 Padrões abertos

| Padrão (versão) | O que define | Impacto no Ziip SAN | O/R |
|---|---|---|---|
| OGC Simple Features / ISO 19125; ISO 19107:2019 | Geometria vetorial | PostGIS já atende; validar geometria (ST_IsValid) na importação | O (técnico) |
| WMS 1.3.0 (ISO 19128); WMTS 1.0 | Mapas e tiles | Cliente WMS (editais) e publicação opcional | O (editais) |
| WFS 2.0 (ISO 19142), com WFS-T | Feições e transação | Edital pede edição via WFS 1.0+; WFS-T com auditoria e RLS (ver `18d`) | O (editais) |
| OGC API – Features, partes 1 a 3 (Core, CRS, Filtering/CQL2); parte 4 (CRUD) | API REST de feições | Adotar partes 1–3 na API pública; parte 4 só depois de aprovada (aprovação não confirmada) | R |
| OGC API – Tiles 1.0 (2022; ISO 19177-1:2026) | Tiles vetoriais | Servir camadas ao MapLibre | R |
| GeoPackage 1.4.0 (2024) | Arquivo geoespacial aberto | Formato padrão de exportação e coleta offline | R |
| CityGML 3.0 (2021); Utility Network ADE (não é norma OGC) | Modelo urbano 3D e redes | Só referência conceitual (rede com nós, arestas e hierarquia) | R |
| LandInfra/InfraGML 1.0 (OGC, 2016); OGC MUDDI (aprovação a confirmar) | Infraestrutura civil; integração de dados de subsolo | Referência para intercâmbio de as-built e dados subterrâneos | R |
| ISO 19115-1:2014 (+ emendas); ISO 19157-1:2023 | Metadados; qualidade (completude, consistência lógica, acurácia posicional, temporal, temática) | Metadado por conjunto e linhagem por feição | R (O na INDE) |
| ISO 55001:2024 | Gestão de ativos | Ciclo de vida do ativo ligado à feição | R |
| INSPIRE Utility and Governmental Services (D2.8.III.6 v3.0) | Modelo europeu de redes | Referência para tipologia de rede, nó e acessório | R |
| IFC 4.3 (ISO 16739-1:2024) | BIM de infraestrutura (IfcPipeSegment, IfcPipeFitting, alinhamentos) | Importar projeto/as-built BIM no futuro; obrigação depende da Estratégia BIM BR para obras federais (Decreto 11.888/2024, a confirmar) | R |

### 2.5 LGPD aplicada ao GIS

| Norma | O que exige | Impacto no Ziip SAN | O/R |
|---|---|---|---|
| Lei 13.709/2018, art. 5º, I e XI; art. 12 | Dado pessoal é o que identifica pessoa natural, direta ou indiretamente; anonimizado só se a reidentificação não for razoável | Coordenada da unidade consumidora vinculada a matrícula, CPF ou titular é dado pessoal. Rede, PV e registro não são | O |
| LGPD, arts. 6º (necessidade), 46 (segurança) | Minimização e segurança | Camadas públicas (WMS/WFS, portal) sem ligação nominal; mapas de calor de consumo e inadimplência agregados por quadra ou setor, com limiar mínimo de unidades | O |
| Guia de Anonimização e Pseudonimização da ANPD (consulta de 2024; versão final a confirmar) | Risco de reidentificação | Ponto exato de um imóvel isolado reidentifica; agregar ou deslocar antes de publicar | R |
| Decreto 9.573/2018 (segurança de infraestruturas críticas) | Proteção de infraestrutura crítica | Publicar adutoras, reservatórios e válvulas de manobra com controle de acesso, não em camada aberta | R |

---

## 3. Recomendações

### 3.1 Sistema de referência padrão

- **Armazenamento:** SIRGAS2000 geográfico, **EPSG 4674**, em todas as tabelas (o código já faz isso). A coluna genérica `geometry(Geometry, 4674)` do `sigsan-gis` deve ganhar restrição de tipo por classe (linha para trecho, ponto para nó).
- **Cálculo métrico:** `geography` ou projeção UTM do tenant. Nunca `ST_Length` sobre 4674 sem conversão (já apontado no `06`).
- **SRID de trabalho por tenant:** configurável entre 31972–31976 e 31978–31985, usado em importação, exportação, EPANET e pranchas. Município que cruza fuso usa o fuso da sede, com registro da escolha.
- **Altimetria:** guardar `z` com `datum_vertical` (Imbituba ou Santana) e `metodo_cota` (nivelamento, GNSS convertido pelo hgeoHNOR2020, estimada). Cota de fundo de PV e de tubo é atributo, não só o Z da geometria.
- **Importação:** recusar arquivo sem SRID declarado; converter SAD69 e Córrego Alegre com as grades oficiais do IBGE, registrando o método.
- **SINISA:** exportar em graus decimais com 7 casas. A diferença entre SIRGAS2000 e WGS-84 é desprezível nessa finalidade.

### 3.2 Precisão por classe de feição

Proposta de referência para parametrização por tenant. Os valores seguem a prática documentada da Corsan (RTK com ±0,10 m na horizontal e ±0,05 m na vertical) e o PEC-PCD; não são exigência de norma.

| Classe de feição | Exatidão horizontal alvo | Vertical | Método típico |
|---|---|---|---|
| PV, TL, caixa de passagem, singularidades de esgoto | ±0,10 m | ±0,05 m (cota de tampa e de fundo) | GNSS RTK ou estação total |
| Registro, válvula, hidrante, ventosa, descarga | ±0,10 m | não exigida | GNSS RTK |
| Trecho de rede (vértices) | ±0,10 a 0,50 m | por cota dos nós | RTK ou amarração a trena ao alinhamento predial |
| Ramal/ligação (caixa, cavalete) | ±0,50 m | não exigida | Amarração ou GNSS de navegação corrigido |
| Unidades localizadas (ETA, ETE, EEE, reservatório) | ±1,0 m (centroide) | cota de projeto | Imagem ou GNSS |
| Drenagem (boca de lobo, galeria) | ±0,10 a 0,50 m | cota de fundo onde houver | RTK |
| Ecoponto, PEV, ponto de coleta | ±2,0 m | — | GNSS de navegação |
| Legado digitalizado de prancha | estimada (1 a 5 m) | — | Digitalização |
| Base cartográfica | PEC-PCD classe A em 1:1.000 (0,28 m) ou 1:2.000 (0,56 m) | — | Restituição ou levantamento |

Cada feição deve guardar `exatidao_h_m`, `exatidao_v_m`, `metodo_captura`, `escala_origem`, `data_levantamento` e `responsavel_tecnico` (ART/RRT). Assim o sistema separa cadastro confiável de cadastro estimado e mostra a diferença no mapa.

### 3.3 Metadados obrigatórios

**Por feição** (linhagem; ISO 19157-1): identificador estável, classe e subtipo, situação (projetada, em obra, ativa, desativada, removida), data de implantação, data e método do levantamento, exatidões, fonte (projeto, as-built, OS, digitalização, importação com `legacy_object_id`), responsável técnico, vigência temporal (`valido_de`/`valido_ate`, para histórico) e códigos MCPSB (UP, UAR, AT1–AT5, BRV, BCP, LOC).

**Por conjunto de dados** (Perfil MGB 2.0 / ISO 19115-1): título, responsável, extensão geográfica, SRID, datum vertical, escala ou exatidão nominal, data de referência, linhagem, restrições de acesso (LGPD e infraestrutura crítica) e frequência de atualização. Gerar o XML na exportação.

**Atributos mínimos de trecho:** DN, material (com norma e edição), classe de pressão ou rigidez, extensão calculada e extensão declarada, profundidade ou cotas de montante e jusante (esgoto e drenagem), declividade calculada, setor/DMA/bacia, classe SINISA/MCPSB (rede, adutora, coletor-tronco, interceptor, emissário, recalque) e estado de conservação.

### 3.4 Simbologia

Como não há NBR de simbologia de redes, adotar uma **simbologia padrão do produto** (estilos SLD/Mapbox GL por classe e situação, com cor por material ou por DN), editável por tenant para reproduzir o padrão da prestadora (Corsan, Sabesp, Sanepar), e exportável junto com o GeoPackage. Pranchas impressas seguem as NBR de desenho técnico. Representação de projetado, existente e desativado com tipos de linha distintos.

### 3.5 Formatos de intercâmbio exigidos

| Destino | Formato |
|---|---|
| Editais (SAMAE, VISAN; ver `18d`) | Importar DWG (via conversão para DXF), DXF, DGN, SHP, GeoPackage, INP (EPANET); exportar KML; consumir WMS; editar por WFS-T |
| Prestadoras (as-built) | SHP ou geodatabase em SIRGAS2000/UTM do fuso (padrão Corsan), CSV dos pontos GNSS |
| SINISA | Valores agregados e coordenadas de unidades em graus decimais, pela plataforma do MCID |
| ERI (NR 16) | Relatório de controle patrimonial com a codificação MCPSB; formato definido por cada ERI |
| INDE / órgãos federais | WMS/WFS com metadados MGB 2.0 (só quando houver publicação) |
| Modelagem hidráulica | EPANET INP em UTM; SWMM para drenagem (recomendado) |
| Interoperabilidade aberta | GeoPackage 1.4, GeoJSON (RFC 7946, em WGS-84), OGC API – Features |

### 3.6 LGPD no GIS

1. Separar fisicamente as camadas técnicas (rede) das camadas vinculadas a pessoas (unidade consumidora, titular, débito).
2. Publicação externa (portal, WMS aberto, compartilhamento com prefeitura) só com agregação ou sem atributo pessoal. A agregação por quadra só vale com número mínimo de unidades, a definir com o jurídico.
3. Registrar no inventário de tratamento (ver `10`) a finalidade de cada camada pessoal.
4. Aplicar ao QGIS e ao WFS a mesma RLS por tenant e o mesmo perfil de acesso da aplicação.

---

## 4. Pendências a confirmar

1. Status oficial de todas as NBR no ABNT Catálogo; conteúdo das NBR 12586, 12587 e 17287 (exige a compra da norma).
2. Edições vigentes das NBR 12212, 12266, 8890, 5626 e das normas de desenho (consolidação nas NBR 16752 e 16861).
3. Valor de EP da classe A do PEC-PCD (0,17 mm) no texto da ET-CQDG.
4. Código SINISA da extensão da rede de água e o glossário de água de 2025.
5. Artigo exato da NR 12/2025 sobre cadastro técnico (lido no manual da ANA, não na resolução).
6. Aprovação da OGC API – Features parte 4 e da OGC MUDDI.
7. Resoluções de cadastro das ERIs dos primeiros clientes; identificação da "AGESAN".
8. Versão final do guia de anonimização da ANPD.

---

## Fontes

- ANA, Resolução 302/2026 (NR 16): https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/resolucao-ana-302_nr16.pdf
- ANA, MCPSB versão 2026: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/mcpsb____pos_pfe_ana.pdf
- ANA, Manual da NR 12: https://www.gov.br/ana/pt-br/assuntos/saneamento-basico/Normativos-publicados-pela-ANA/manual-nr12-final.pdf
- MCID, Glossário SINISA Esgoto (emitido em 17/12/2025): https://www.gov.br/cidades/pt-br/acesso-a-informacao/acoes-e-programas/saneamento/sinisa/area-do-prestador/arquivos/GlossrioInformaes_ESGOTO_2024.pdf
- Corsan/Aegea, Anexo A — Padrões para cadastro e GIS (rev. 03/2024): https://corsan.com.br/upload/arquivos/202411/21152701-2-anexo-a-padroes-para-cadastro-e-gis.pdf
- Normas.com.br, listagem do CB-177: https://normas.com.br/produto/normas-brasileiras-e-mercosul/pesquisar/cb-177/saneamento-basico/1 ; NBR 12586: https://www.normas.com.br/visualizar/abnt-nbr-nm/6322/abnt-nbr12586-cadastro-de-sistema-de-abastecimento-de-agua-procedimento ; NBR 12587: https://www.normas.com.br/visualizar/abnt-nbr-nm/6323/abnt-nbr12587-cadastro-de-sistema-de-esgotamento-sanitario-procedimento ; NBR 5667-1: https://normas.com.br/visualizar/abnt-nbr-nm/25018/abnt-nbr5667-1-hidrantes-urbanos-de-incendio-de-ferro-fundido-ductil-parte-1-hidrantes-de-coluna ; NBR 17047: https://www.normas.com.br/visualizar/abnt-nbr-nm/13419/abnt-nbr17047-levantamento-cadastral-territorial-para-registro-publico-procedimento ; NBR 14645-3: https://www.normas.com.br/visualizar/abnt-nbr-nm/24917/abnt-nbr14645-3-elaboracao-do-como-construido-as-built-para-edificacoes-parte-3-locacao-topografica-e-controle-dimensional-da-obra-procedimento
- Target, NBR 17287:2026: https://www.target.com.br/produtos/materias-tecnicas/2026/06/24/6628/a-operacao-das-redes-de-distribuicao-de-agua-para-abastecimento-publico ; NBR 14166: https://www.target.com.br/produtos/normas-tecnicas/27187/nbr14166-rede-de-referencia-cadastral-municipal-requisitos-e-procedimento
- NBR 13133:2021 (cópia institucional UFRRJ): https://arquivos.ufrrj.br/arquivos/20230102302b983573184b44fe6044133/ABNT_NBR_13133_2021-1-1.pdf
- IBGE, Sistema Geodésico Brasileiro: https://www.ibge.gov.br/geociencias/informacoes-sobre-posicionamento-geodesico/sirgas/16327-sistema-geodesico-brasileiro-sgb.html ; hgeoHNOR2020: https://nada.ibge.gov.br/en/highlights/38255-ibge-will-release-on-november-28-the-methodological-report-hgeohnor2020-model-for-conversion-of-geometric-altitudes-into-normal-altitudes-2nd-edition.html ; Perfil MGB: https://www.ibge.gov.br/geociencias/metodos-e-outros-documentos-de-referencia/normas/30717-perfil-de-metadados-geoespaciais-do-brasil.html
- Decreto 89.817/1984: https://www.planalto.gov.br/ccivil_03/decreto/1980-1989/D89817.htm ; ET-EDGV 3.0: https://geoportal.eb.mil.br/portal/images/Documentos/2024/ET-EDGV-3_0_210518.pdf ; comparativo PEC/PEC-PCD (RBC/UFU): https://seer.ufu.br/index.php/revistabrasileiracartografia/article/view/45259
- Portaria MDR 3.242/2022: https://www.lex.com.br/portaria-mdr-no-3-242-de-9-de-novembro-de-2022/
- Decreto 10.936/2022: https://legis.senado.leg.br/norma/35443315/publicacao/35443916
- AGENERSA, RF 222/2024: https://www.rj.gov.br/agenersa/sites/default/files/arquivos_paginas_basicas/RF%20222-2024.pdf
- OGC API – Tiles: https://docs.ogc.org/is/20-057/20-057.html ; GeoPackage 1.4: https://docs.ogc.org/is/12-128r19/12-128r19.html ; consulta OGC API – Features partes 4 e 5: https://www.ogc.org/requests/ogc-seeks-public-comment-on-ogc-api-features-parts-4-5/ ; Utility Network ADE (TUM): https://asg.ed.tum.de/en/gis/projects/citygml-utility-network-ade
- ISO 19157-1:2023: https://www.iso.org/standard/78900.html ; IFC 4.3 (buildingSMART): https://standards.buildingsmart.org/IFC/RELEASE/IFC4_3/HTML/lexical/IfcPipeSegmentTypeEnum.htm
- IRIB, LGPD e anonimização no registro de imóveis: https://irib.org.br/noticias/detalhes/registro-de-imoveis-dados-pessoais-undefined-anonimizacao-undefined-pseudonimizacao ; ANPD, consulta do guia de anonimização (2024): https://www.mattosfilho.com.br/unico/anonimizacao-pseudonimizacao-direitos-titulares/
