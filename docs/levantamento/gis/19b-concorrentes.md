# 19b — GIS de cadastro técnico: concorrentes e referências

Data: 12/10/2026. Escopo: cadastro técnico de redes de água, esgoto, drenagem e resíduos sólidos (modelo de dados,
topologia, edição, versionamento, manobras, integrações, CAD, licenciamento). Complementa `05` e `06` (GEOSAN/GSAN),
que não são repetidos aqui, e `editais/18d` (requisitos de GIS da VISAN e do SAMAE).

## 0. Método e limites

- Perplexity e Brave estavam indisponíveis (chave inválida). A pesquisa usou busca web, leitura direta de páginas e
  conversão de PDFs públicos.
- Os sites da Jtech, da Waterfy e da Linedata bloqueiam leitura automatizada. O que consta sobre eles vem de páginas
  indexadas, notícias e diretórios, e é declaração do fornecedor.
- **Nenhum concorrente nacional publica modelo de dados, catálogo de peças ou regras de topologia.** Na tabela,
  "?" significa "não público ou não confirmado", e não "não tem".
- Preços quase nunca são públicos. Onde aparecem, a fonte é citada.

## 1. Resumo

1. O mercado nacional se divide em três grupos: **sistemas comerciais com módulo GIS acoplado** (Sansys, Waterfy),
   **GIS de cadastro separado, ligado ao comercial por integração** (GEOSAN, ARGOS) e **plataforma Esri implantada
   pela Imagem**, que predomina nas companhias estaduais (Sabesp, Copasa, Sanepar, Compesa, Cesan, Caesb). Os
   fornecedores de gestão pública (Elotech, IPM, Betha) não têm oferta pública de GIS de redes.
2. As companhias grandes ditam o padrão de entrega. O Anexo A da Corsan/Aegea (rev. 03/2024) exige shapefile ou
   geodatabase ArcGIS 10.x em SIRGAS 2000/UTM, com camadas e atributos mínimos para água e esgoto. A Saneago trocou
   o caminho Esri pelo **Giswater** (QGIS + PostgreSQL + EPANET) em 2020, numa rede de cerca de 35.000 km de água.
3. O estado da arte converge para quatro peças: **hierarquia classe → grupo de ativo → tipo de ativo** com catálogo
   de peças; **topologia nó-trecho validada**; **zonas derivadas por traçado** (zona de pressão, DMA, isolamento);
   e **estado de ciclo de vida, com o planejamento tratado como versão**.
4. A Esri Utility Network é o modelo de referência, mas editar exige a extensão de usuário ArcGIS Advanced Editing e
   o ecossistema Enterprise, o que fica fora do alcance de um SAAE pequeno. O Giswater entrega boa parte dessas
   funções sobre PostGIS e QGIS, sob GPL-3, sem integração com o comercial.
5. Oportunidade central do Ziip SAN: **comercial, técnico e OS no mesmo banco**. Isso permite que a manobra gere a
   lista de imóveis afetados, o aviso ao usuário e a OS sem integração por views, algo que nenhum concorrente
   documentado entrega de ponta a ponta.

## 2. Tabela comparativa de capacidades

Legenda: **S** documentado; **P** parcial ou por produto adicional; **N** não oferece; **?** não público ou não
confirmado.

| Capacidade | GEOSAN | Sansys | Waterfy | ARGOS | Esri UN | Giswater | TEKSI | Bentley | Autodesk | Trimble | GIS Cloud |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Modelo de dados de rede publicado | S | ? | ? | ? | S | S | S | S (hidráulico) | P | ? | N |
| Catálogo de peças e conexões (tê, curva, redução, cap, registro, hidrante, ventosa, descarga, PV) | S | ? | ? | ? | S | S | S | P | ? | ? | N |
| Materiais, DN e classe de pressão em catálogo | P | ? | ? | ? | S | S | S | S | ? | ? | N |
| Topologia nó-trecho imposta na edição | P (validação posterior) | ? | ? | ? | S | S | ? | S | ? | ? | N |
| Terminais (dispositivos direcionais) | P | ? | ? | ? | S | ? | ? | S | S | ? | N |
| Zonas calculadas por traçado | N | ? | ? | ? | S | S | ? | P | P | ? | N |
| Isolamento e manobra (válvulas a fechar, afetados) | S | ? | ? | P | S | S | ? | P | P | ? | N |
| Edição web | N | ? | ? | N | S | P | N | N | P | S | S |
| Edição desktop | S | ? | ? | S | S | S | S | S | S | ? | N |
| Campo offline | N | ? | P | N | S | ? | ? | N | P | S | S |
| Versionamento e projetos planejados | N | ? | ? | ? | S | S | P | S (cenários) | P | ? | N |
| Integração com comercial | S (views) | S | S | S | N | N | N | N | N | P | N |
| Integração com OS | N | P | S | P | P | P | N | N | P | S | P |
| Hidráulica (EPANET/SWMM ou própria) | S | ? | ? | ? | P | S | ? | S | S | N | N |
| Importação CAD/DWG | S (DXF) | ? | S | S | S | P | P | S | ? | ? | ? |
| Licença e preço | GPL-2, gratuito | Contrato | ? | ? | Cotação | GPL-3, gratuito | GPL-3, gratuito | A partir de US$ 1.021/ano | Cotação | Cotação | Por assento |

## 3. Produtos

### 3.1 Nacionais

**GSAN/GEOSAN (software público, NEXUS).** Cobertos em `05` e `06`: modelo EAV por tipo, cerca de 45 tipos de
componente de água com classe EPANET, ramal e ligação amarrados ao GSAN por três views, isolamento até registro e
ValidaBase. Desktop VB6, GPL-2, sem evolução desde 2018. Continua sendo a referência funcional mais completa entre
as soluções nacionais com documentação aberta.

**Sansys Water (Jtech, Veolia desde 2018).** Suíte web em Java com 18 módulos, do atendimento ao monitoramento
operacional, com "módulo GIS integrado aos demais" e oferta SaaS em AWS. O fornecedor declara mais de 150 municípios e
3,5 milhões de consumidores. Exemplo de contratação: Samae de Pomerode, R$ 71.500. O modelo de rede, a edição e o
campo não são públicos. É o concorrente mais parecido com o Ziip SAN em proposta (SaaS, comercial e operacional),
com a força comercial da Veolia.

**Waterfy.** Criada em 2018 a partir da compra da propriedade intelectual de um sistema do setor. Módulos:
Customerfy (atendimento, cadastro, leitura, faturamento, arrecadação, cobrança, OS), Datafy (BI), Geofy e Servicefy.
O Geofy "relaciona cadastros comerciais, técnicos (CAD) e operacionais" e acompanha equipes de campo em tempo real.
Declara implantação em até 45 dias. Sem documentação de modelo de rede.

**Sanegraph ARGOS + FOXFAT.** Cadastro técnico desktop Windows que trabalha junto com AutoCAD, BricsCAD, GstarCAD e
ZwCAD, com plugin para QGIS. Integra-se ao comercial FOXFAT, à micromedição por área e à contabilidade pública
(MIDAS), para a qual gera o inventário de redes. Cobre manutenção eletromecânica, poços e setores de manobra. É o
caso nacional que mais se aproxima do requisito "cadastro técnico integrado ao contábil" (TR-155 da VISAN).

**Sem GIS de redes documentado.** Strategos (Saema Araras), Linedata SANEGEO 2T, FlexSan e CWC aparecem como
fornecedores de comercial, leitura ou OS. Para Elotech, IPM (Atende.Net), Betha, Cittati e Tecnisys não se achou
oferta pública de saneamento com GIS de redes. A Geopixel faz geointeligência para prefeituras (cadastro
imobiliário, alvarás), sem produto de redes.

**Imagem (distribuidora Esri).** Lançou o "ArcGIS para Saneamento" em 2013 e cita projetos com Copasa, Sabesp,
Compesa, Cesan, Caesb e Sanepar. A Sabesp usa o GIOS (gestão de equipes de campo) da Imagem e, desde 2006, o SIGNOS
sobre GE Smallworld. A Copasa tem trajetória própria (CopaGIS em 2010, GERHA em 2014). Não se encontrou evidência
pública de migração dessas companhias para a Utility Network.

**Hexagon/Intergraph (HxGN NetWorks, ex-G/Technology).** O único caso encontrado de saneamento é a EPM (Medellín).
Não há caso brasileiro público.

**Aegea/Corsan e BRK.** A Corsan publica o padrão de cadastro e GIS (Anexo A, rev. 03/2024), detalhado em 3.3. Da BRK
não há material público.

**Saneago (Giswater).** GIS corporativo desde 2020: cadastro em QGIS, modelo hidráulico calibrado de Goiânia
replicado a outras cidades, esgoto incorporado em 2024 e visualizador web QWC2 em 2024. Atende 223 municípios, com
cerca de 35.000 km de rede de água e 12.300 km de esgoto. Não se encontrou integração com comercial ou OS.

### 3.2 Internacionais e código aberto

**Esri ArcGIS Utility Network (Water, Sewer e Stormwater Utility Network Foundation).**
- Modelo: o **grupo de ativo** é o subtipo da classe e o **tipo de ativo**, um domínio por grupo. As classes da
  rede de água são Water Device (válvulas, hidrantes, medidores), Water Junction (o "ponto de cola"; o grupo Fitting
  traz luvas, cotovelos, caps, tês, junções em Y, tampões e colares de tomada), Water Line (adutora, distribuição,
  ramal), Water Assembly e as classes Structure (caixas, abrigos, suportes).
- Esgoto: o grupo Device traz poço de visita, TIL/cleanout, válvula de retenção e caixa de gordura; o grupo Line traz
  rede por gravidade e recalque.
- Terminais: a configuração de terminais (por exemplo, montante e jusante de uma bomba ou VRP) é atribuída ao tipo
  de ativo, com caminhos válidos entre terminais, e não pode ser alterada depois de usada.
- Regras de conectividade: junção-aresta, contenção (válvula dentro da caixa) e anexo estrutural.
- Subredes por camada hierárquica (tier). Água: sistema, zona de pressão, DMA, isolamento e proteção catódica,
  aninhados. Esgoto: sistema coletor e sub-bacia, com PVs direcionais como controladores.
- Traços: montante, jusante, subrede e isolamento. O isolamento usa a categoria de rede "Isolating" e o status
  normal aberto para achar o conjunto mínimo de dispositivos a fechar.
- Edição: templates de grupo criam um ramal residencial completo (tomada, ramal, registro, ligação) em uma ação.
  Áreas sujas ficam marcadas até a validação da topologia. A edição em Enterprise usa versionamento por ramo, e o
  campo usa ArcGIS Field Maps com offline [conhecimento geral, não reverificado nesta pesquisa].
- Licença: criar, publicar e editar exigem a extensão de usuário ArcGIS Advanced Editing (consulta e traço, não).
  Preço só por cotação.
- O Local Government Information Model perdeu o conteúdo de água, esgoto e drenagem em 2017; esse conteúdo foi para
  as soluções de água e depois para a Utility Network.

**Giswater (BGEO, Barcelona; GPL-3).** Plugin QGIS sobre PostgreSQL/PostGIS.
- Modelo: tipos de sistema arc, node, connec (ligação ao imóvel), gully (boca de lobo), link (ramal desenhado) e
  element (não topológico). Os tipos de feição ficam em `cat_feature` e os catálogos com chave estrangeira, em
  `cat_arc`, `cat_node`, `cat_connec` e catálogos de materiais.
- Topologia: todo arco entre dois nós (`node_1`, `node_2`); o nó divide o arco; mover o nó arrasta os arcos;
  verificações de nó órfão e duplicado.
- Estados: obsoleto, operativo e planejado. O planejado vive em **psectors** (setores de planejamento), que
  funcionam como versão de projeto.
- Zonas (setor, DMA, zona de pressão e outras) desenhadas ou calculadas por traçabilidade.
- Mincut: exige topologia íntegra, `closed` e `broken` preenchidos nas válvulas, fontes em `config_graph_mincut` e
  tipos de válvula marcados como `MINSECTOR`.
- Exportação EPANET (água) e SWMM (esgoto e drenagem).
- Em 2026, o repositório `giswater_dbmodel` foi arquivado; o plugin e uma API FastAPI seguem ativos.
- Declara usuários que atendem mais de 20 milhões de pessoas.

**TEKSI (Suíça; antigos QWAT e QGEP; GPL-3).** Módulos QGIS e PostgreSQL mantidos por associação sem fins
lucrativos. O de água (QWAT) é quase compatível com a SIA405 e exporta INTERLIS. O de esgoto migrou do QGEP
(VSA-DSS 2015, em retirada) para o TEKSI Wastewater (VSA-DSS 2020, VSA-KEK). O histórico vem de triggers de
auditoria (pg-history-viewer). A lição para o Ziip SAN é ter **norma nacional de intercâmbio como contrato do
modelo**.

**Bentley OpenFlows (WaterGEMS, SewerGEMS).** Modelagem hidráulica, não cadastro. O LoadBuilder e o TRex alocam
demanda e cota a partir de shapefile, geodatabase, MDT e CAD; o campo GIS-ID mantém o vínculo com o GIS. Integra
com ArcGIS Pro, MicroStation e AutoCAD. A entrada do OpenFlows Water custa US$ 1.021/ano, mas inclui o WaterCAD;
o WaterGEMS só vem nos níveis superiores, por cotação. O AssetWise cobre gestão de ativos.

**Autodesk Innovyze.** InfoWater Pro e InfoWorks WS Pro enviam resultados de simulação ao Info360 Asset, que faz
risco, falha e árvores de reabilitação. Em 2026, a Autodesk padronizou o Esri como sistema de registro GIS do
Info360. O InfoAsset Manager foi relançado com recursos móveis e on-line.

**Trimble Unity e Cityworks.** O Unity é SaaS centrado em GIS (mapeamento, manutenção, serviço). O Cityworks
(gestão de ativos e OS sobre Esri) passou a ser vendido também como Unity Maintain. Referência de porte: contrato de
Toronto Water de cerca de CA$ 10,4 milhões até 2026, com integração ao IBM Maximo. É o padrão do GIS como
**sistema de registro da OS**.

**3-GIS, GIS Cloud, Mapgears.** O 3-GIS tem foco em fibra e, em água, depende de ferramentas Esri. O GIS Cloud é
coleta e edição genéricas com offline, cobradas por assento e aplicativo (dado de agregador, não confirmado no
fornecedor), sem modelo de rede. Da Mapgears não se achou oferta de redes; em QGIS há o plugin Network Studio
(GeoPackage ou PostGIS) e o QField para campo.

### 3.3 O padrão brasileiro de fato: Corsan/Aegea (Anexo A, rev. 03/2024)

- **Referência e precisão:** SIRGAS 2000/UTM 21S ou 22S, altimetria por Imbituba. Pontos RTK com ±0,10 m
  horizontal e ±0,05 m vertical. Curvas de nível a cada 1 m (esgoto) e 2 m (água).
- **Água:**
  - A_REDE: função, material, DN, diâmetros interno e externo, profundidade, lado par/ímpar, distâncias do
    alinhamento predial e do meio-fio, pavimento, situação, croqui.
  - A_CONEXOES: tipo, material, DN1/DN2.
  - A_VALVULA: tipo, função, acionamento, sentido de abertura e percentual de abertura.
  - A_HIDRANTE.
  - A_PROTEHIDRAULICAS (ventosas e demais proteções, com pressões a montante e a jusante).
  - A_RAMAIS, A_MACROMEDIDOR, A_PCP, A_PCQ, A_POCO, A_RESERVATORIO, A_EEAT, A_MOTOBOMBA, A_ETA, A_CAPTACOES e
    A_SETOR_FORNECIMENTO.
- **Esgoto:** E_REDE (cotas de entrada e saída, declividade, tipo de seção, pressurizada ou não), E_POCO_VISITA,
  E_TIL, E_CX_LIGACAO, E_CX_DESCARGA, E_RAMAL, E_VALVULAS, E_EEE, E_MOTOBOMBA, E_ETE, E_BACIA e E_SUB_BACIA.
- **Regras e entrega:** conectividade por snap, polígonos sem sobreposição, domínios por atributo, metadados ISO
  19115/MGB e plantas DWG/PDF A1 a 1:1.000 (esgoto) e 1:2.000 (água). Normas citadas: NBR 12586, 12587, 13133 e
  14166.

Esse documento serve de lista mínima de camadas e atributos para o Ziip SAN e de formato de importação e
exportação de *as built*.

## 4. Padrões de modelo de dados que dominam o mercado

1. **Três níveis de classificação e catálogo de peças.**

   | Nível | Esri | Giswater | GEOSAN |
   |---|---|---|---|
   | Classe | classe de rede | tipo de sistema | camada |
   | Grupo | grupo de ativo | tipo de feição | tipo |
   | Tipo/variação | tipo de ativo | linha de catálogo | subtipo EAV |

   A linha do catálogo combina tipo, material, DN (ou DN1×DN2 para redução e tê de redução), classe ou PN e
   fabricante. A feição guarda a chave do catálogo, não texto livre.
2. **Topologia nó-trecho como invariante**, verificada na gravação (Giswater) ou por validação de áreas sujas (Esri),
   e nunca só a posteriori (GEOSAN).
3. **Terminais e direção** para bomba, VRP, válvula de retenção e PV direcional. Sem isso, nem o traço nem o
   EPANET acertam o sentido.
4. **Zonas como resultado de traçado**: sistema, zona de pressão, DMA e setor de manobra são derivados de
   controladores (reservatório, VRP, macromedidor) e de delimitadores (válvulas operáveis), e não desenhados à mão.
5. **Isolamento com estado operacional da válvula**: aberta, fechada, quebrada ou inoperável, e a categoria
   "isolante" no tipo.
6. **Ciclo de vida e planejamento como versão**: projetado, em obra, operativo, desativado ou abandonado. Os
   projetos ficam separados da rede operativa (psector, ramo de versão) e entram por aprovação de *as built*.
7. **Ramal e ligação como objetos próprios**, que ligam o imóvel à rede (connec + link no Giswater; ramal + tomada +
   ligação por template na Esri; ramal + ligação no GEOSAN).
8. **Estruturas e contenção**: válvula dentro de caixa, bomba dentro de elevatória, peças dentro de um conjunto.
9. **Intercâmbio por padrão nacional**: INTERLIS/SIA405/VSA-DSS na Suíça, padrão de camadas da companhia no Brasil.
10. **Hidráulica acoplada por chave estável** (GIS-ID, mapeamento tipo → EPANET), com cota, demanda e padrões
    alimentados pelo cadastro e pelo comercial.

Drenagem: a referência é o Stormwater Utility Network Foundation (Esri) e o modelo UD do Giswater (gully + SWMM).
Resíduos sólidos: nenhum dos produtos estudados modela o cadastro de RSU (pontos de coleta, contêineres,
ecopontos, rotas). O Sansys Waste trata da relação entre geradores e aterro, não de cadastro geográfico.

## 5. Lacunas e oportunidades para o Ziip SAN

1. **Técnico, comercial e OS no mesmo banco.** GEOSAN e ARGOS integram por views ou exportação; a Esri e o Giswater
   não têm comercial. O Ziip SAN pode ligar imóvel, ligação, ramal e trecho por chave interna e oferecer a
   sequência que nenhum concorrente documenta inteira: manobra → válvulas a fechar → imóveis e economias afetados
   (com prioridade para hospitais e eletrodependentes) → aviso por SMS ou WhatsApp → OS de fechamento e reabertura →
   registro do período de desabastecimento por imóvel (indicadores regulatórios e eventual abatimento).
2. **Modelo de nível Utility Network sobre PostGIS, sem licença Esri.** Adotar os padrões da seção 4 (catálogo,
   terminais, regras de conectividade em tabela, zonas por traçado, mincut com estado da válvula) com nomes e
   código próprios: o Giswater é GPL-3 e o GEOSAN, GPL-2, e servem só como referência conceitual. O preço por
   tenant fica abaixo de qualquer pilha Esri para SAAE de pequeno e médio porte.
3. **Catálogo-semente brasileiro.** Materiais e classes de uso corrente (PVC PBA classes 12/15/20, PVC DeFoFo, PEAD
   PE80/PE100 por SDR, ferro fundido K7/K9, fibrocimento legado, concreto, cerâmico), DN normalizados, peças
   (tê, tê de redução, curvas de 11°15', 22°30', 45° e 90°, redução, cap, luva, cruzeta, registro de gaveta e de
   borboleta, VRP, ventosa, descarga, hidrante de coluna e subterrâneo, PV, TIL, caixa de ligação) e domínios de
   situação. Distribuir como semente editável por tenant.
4. **Importação de as built no formato das companhias.** Assistente de mapeamento de camadas e atributos DWG/DXF,
   SHP e GPKG para o modelo, com perfis prontos "Corsan Anexo A" e "Saneago". Responde à migração DWG → PostGIS
   exigida pela VISAN nos 90 dias e à implantação crítica do SAMAE (`18d`, E15). Validar com as regras da seção 4
   antes de gravar e produzir relatório de rejeições.
5. **QGIS como cliente de edição desde o início, editor web depois.** Os editais pedem acesso pelo QGIS (`18d`). Isso
   exige papéis por tenant e RLS no PostGIS, mais um projeto QGIS modelo publicado pelo próprio sistema. É o que o
   Giswater faz, mas sem multitenancy.
6. **Fluxo de aprovação de cadastro.** Edição de campo (PWA offline) e de terceiros entra como proposta (estado
   "projetado" ou "em análise") e passa à rede operativa por aprovação, com histórico temporal por feição. Isso
   substitui o versionamento por ramo, caro de implementar, e atende à produtividade de cadastro que o GEOSAN media.
7. **Qualidade contínua do cadastro.** Painel com as verificações do ValidaBase e do Giswater (nó órfão, trecho sem
   nó, duplicados, DN incompatível na conexão, ramal sem ligação, ligação sem ramal), executado a cada gravação e não
   em lote noturno.
8. **Drenagem e resíduos como diferencial municipal.** Concorrentes nacionais não cobrem drenagem nem RSU com GIS.
   Um modelo UD simples (galeria, boca de lobo, poço de visita, bacia, compatível com SWMM) e um cadastro de RSU
   (ponto de coleta, contêiner, ecoponto, rota, ligado ao MTR do `12`) atendem autarquias de saneamento ambiental
   (SAEMA, SAMAE com resíduos).
9. **Hidráulica como consequência do cadastro.** Tabela de mapeamento tipo de ativo → elemento EPANET por tenant,
   com demanda vinda do consumo do comercial (como o GEOSAN fazia) e solver já previsto em `06` §7.
10. **Transparência.** Nenhum concorrente nacional publica preço nem modelo de dados. Publicar o dicionário de
    dados e uma faixa de preço por ligação diferencia o Ziip SAN em licitação.

Riscos: a Sansys tem escala e o respaldo da Veolia; as companhias estaduais vão manter Esri ou Giswater, e o
Ziip SAN deve exportar para esses formatos em vez de competir com eles; o Giswater é gratuito e já tem caso
brasileiro grande, e o argumento contra ele é a integração com o comercial e a operação SaaS, não o modelo de rede.

## 6. URLs consultadas

Nacionais
- Sansys/Jtech: https://www2.jtech.com.br/ ; https://tiete.jtech.com.br/product/sansys-water ;
  https://ndmais.com.br/infraestrutura/santa-catarina-a-caminho-da-universalizacao-do-saneamento/ ;
  http://www.samaepomerode.com.br/index.php?pg=1325
- Waterfy: https://waterfy.com.br/sobre/ ; https://waterfy.com.br/solucoes/ ;
  https://saneamentobasico.com.br/empresa/waterfy/
- Sanegraph ARGOS: https://www.sanegraph.net.br/produtos/argos/
- Linedata SANEGEO 2T: https://www.linedata.com.br/sanegeo.html ; FlexSan: https://flexsan.com.br/ ;
  CWC: https://cwcsistemas.com.br/
- Strategos (Saema Araras): https://saema.com.br/novo-sistema/
- Geopixel: https://www.geopixel.com.br
- GSAN/GeoSan: https://softwarepublico.gov.br/social/gsan ; modelo de TR de GIS:
  https://softwarepublico.gov.br/social/gsan/termos-de-referencia/20181002200900-modelo-termo-referencia-suporte-implementacao-gis.docx
- Imagem/Esri: https://www.baguete.com.br/noticias/imagem-lanca-arcgis-para-saneamento ;
  https://www.baguete.com.br/noticias/sabesp-gestao-com-tecnologia-da-imagem
- Sabesp SIGNOS/Smallworld: https://www.wateronline.com/doc/ge-energy-provides-smallworld-technology-for-0001
- Copasa: https://assemae.org.br/noticias/item/download/362_41ff123e4615803fe18c7d955c9c1d8b
- Corsan/Aegea Anexo A: https://corsan.com.br/upload/arquivos/202411/21152701-2-anexo-a-padroes-para-cadastro-e-gis.pdf
- Saneago IN cadastro técnico: https://www.saneago.com.br/2016/fornecedores/arquivos/IN00.0229.03.pdf
- Saneago + Giswater: https://www.iagua.es/noticias/bgeo-open-gis/saneago-moderniza-gestion-saneamiento-giswater
- Samae Jaraguá do Sul (CAD → SIG): https://ojs.sites.ufsc.br/index.php/cobrac/article/download/10693/9127/39539
- Hexagon (EPM): https://hexagon.com/resources/resource-library/powerful-multiutility-geospatial-technology-enhances-and-optimizes-digital-management-of-networks

Esri
- Utility Network: https://www.esri.com/pt-br/arcgis/products/arcgis-utility-network/overview
- Water Foundation: https://doc.arcgis.com/en/arcgis-solutions/latest/reference/use-water-distribution-utility-network-foundation.htm
- Dicionário de água: https://doc.arcgis.com/en/arcgis-solutions/11.5/reference/other/WaterEssentials_UtilityNetwork.html
- Changelog do modelo de água v4: https://doc.arcgis.com/en/arcgis-solutions/reference/pdf/WaterDataManagementChangelogV4.pdf
- Sewer Foundation: https://doc.arcgis.com/en/arcgis-solutions/11.4/reference/use-sewer-utility-network-foundation.htm ;
  https://www.esri.com/arcgis-blog/products/utility-network/water/utility-network-wastewater-networks/
- Terminais: https://pro.arcgis.com/en/pro-app/help/data/utility-network/about-terminal-management.htm
- Subredes de água: https://www.esri.com/arcgis-blog/products/utility-network/water/creating-subnetworks-water
- Isolamento: https://www.esri.com/arcgis-blog/products/utility-network/data-management/isolating-arcgis-utility-network
- Web Editor: https://doc.arcgis.com/en/web-editor/11.3/create-and-edit/utility-network-overview.htm
- Licença: https://enterprise.arcgis.com/en/server/12.0/publish-services/windows/utility-network-services.htm ;
  https://www.esri.com/en-us/store/arcgis-online/user-types
- LGIM: https://www.esri.com/arcgis-blog/products/product/local-government/the-evolving-local-government-information-model-and-its-impact-on-water-utilities

Código aberto
- Giswater: https://docs.giswater.org/latest/en/docs/index.html ;
  https://docs.giswater.org/latest/en/docs/giswater/for-users/user-manual/data-model-giswater.html ;
  https://docs.giswater.org/master/en/docs/giswater/for-users/dialogs/mincut.html ; https://github.com/Giswater ;
  https://talks.osgeo.org/foss4g-na-2025/talk/RN3D7Z/
- TEKSI: https://github.com/teksi ; https://github.com/qwat/qwat-data-model ;
  https://plugins.qgis.org/plugins/teksi_wastewater
- QGIS Network Studio: https://plugins.qgis.org/plugins/network_studio/ ; QField: https://qfield.org/tags/wastewater/

Demais
- Bentley OpenFlows Water: https://www.bentley.com/products/openflows-water
- Autodesk Info360 Asset: https://www.autodesk.com/blogs/water/2026/09/29/info360-asset-capital-planning/ ;
  https://www.autodesk.com/blogs/water/2024/04/15/water-distribution-modeling-integration-with-asset-management-for-infoworks-ws-pro-and-infowater-pro-2025
- Trimble Unity/Cityworks: https://www.gpsworld.com/trimble-unity-replaces-connect-for-water-wastewater-and-stormwater-utilities/ ;
  https://www.toronto.ca/legdocs/mmis/2025/gg/bgrd/backgroundfile-255940.pdf ;
  https://news.trimble.com/Announcing-the-2025-Trimble-Asset-Lifecycle-Management-Award-Winners?asPDF=1
- 3-GIS: https://www.rfp.wiki/vendors/3-gis ; GIS Cloud: https://www.rfp.wiki/vendors/gis-cloud
