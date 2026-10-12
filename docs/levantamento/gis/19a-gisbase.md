# 19a — Gisbase / Hidrogeo e documentos de Nova Trento

Levantamento de 12/10/2026 para o Ziip SAN (módulo GIS de cadastro técnico). As fontes locais foram apenas lidas. Os trechos citados aparecem resumidos, com indicação da origem.

> **Observação sobre os arquivos.** O arquivo `edital_09-2020_-_pp_sistema_gestao_saneamento1.pdf`, guardado na pasta de Nova Trento, é o **Pregão Presencial 09/2020 do SAMAE de São Ludgero/SC** (Processo 12/2020), e não um edital de Nova Trento. A seção 3 trata dele como edital de São Ludgero. O site da Gisbase lista o SAMAE São Ludgero como cliente desde outubro/2021. A data é compatível com esse certame, mas o resultado da licitação não foi verificado.

---

## 1. Gisbase: empresa e produto

### 1.1 Empresa

| Item | Dado | Fonte |
|---|---|---|
| Razão social | Gisbase Informática Ltda ME, CNPJ 23.059.026/0001-00 | contrato 00029/2019 |
| Sede | Rua Adolfo Melo, 35, Centro, Florianópolis/SC (sala 802 em 2019; sala 101 no site atual) | contrato; site |
| Fundação | 2015. Começou como fornecedora de tecnologia para uma empresa de sistema de faturamento e hoje atua de forma independente | gisbase.com.br/sobre |
| Origem técnica | Foi contratada pela JTech (sistema comercial **Sansys**) para desenvolver o módulo GIS do Sansys no SAMAE de Jaraguá do Sul, em 2014–2015 | artigo COBRAC 2020 (Schwingel, Dacol, Ribas) |
| Porte declarado | Mais de 30 cidades em 9 estados (BA, GO, MG, MS, MT, RJ, RS, SC, SP), mais de 1 milhão de UCs e mais de 13 bilhões de litros/mês geridos | site |
| Posicionamento | "Startup de geoprocessamento dedicada ao saneamento", mantenedora do PMI-SC | site |
| Contato público | contato@gisbase.com.br | site |

### 1.2 Produto: Hidrogeo

O Hidrogeo é um SIG web feito especificamente para saneamento. A proposta é registrar todos os elementos do sistema de uma cidade e gerar mapas temáticos alimentados com dados atualizados. A Gisbase o apresenta como uma plataforma colaborativa usada da operação à gestão e organizada em três pilares: **georreferenciar**, **converter e estruturar** e **mapear e cruzar informações** (gisbase.com.br/hidrogeo).

| Funcionalidade | Descrição resumida | Fonte |
|---|---|---|
| Cadastro e consulta de redes | Camadas de negócio editáveis: trechos, conexões, ligações, ramais, unidades, ETAs, reservatórios etc. | site; COBRAC |
| Áreas de influência | Roteamento pelo grafo da rede a partir de ETA, reservatório, ETE ou ERE, gerando o polígono da área atendida | site; COBRAC |
| DMC, bacias e sub-bacias | Delimitação de distritos de medição e de bacias de esgotamento | site (serviços 05) |
| Roteirização | Rotas de leitura e manutenção baseadas na geografia da rede | site |
| Consumidores e hidrômetros | Mapas temáticos por consumo, faturamento, pendência, grupo, rota e região | site; contrato NTR |
| Perdas e ocorrências | Cruzamento de macromedição, consumo, OS e território para localizar áreas críticas; simulador de perdas público | site (/calculadora) |
| Priorização de intervenções | Ordena as áreas pelo impacto financeiro | site |
| Simulação hidráulica | Exportação da rede estruturada para EPANET (botão "EPA Export .inp" na barra de ferramentas) e WaterGEMS | site; prints da proposta NTR |
| Integração comercial | Vínculo da UC e da ligação com o cadastro comercial; abertura de OS a partir do mapa | site; contrato NTR |
| Isolamento de rede | Identificação dos registros que isolam um trecho e das UCs afetadas pela manobra | COBRAC |
| Ferramentas de mapa (prints de 2019) | Pan, zoom, vista anterior e seguinte, troca de mapa base (Google), medição de distância e área, identificação, seleção por retângulo, inclusão e remoção de feições, validação, exportação para PDF e configurações | proposta NTR (imagens) |

### 1.3 Serviços

O catálogo do site tem oito linhas de serviço: (1) implantação do Hidrogeo; (2) conversão e saneamento de dados legados, de CAD ou papel, com padronização de nomenclatura; (3) cadastro técnico de redes, com tubulações, registros, válvulas, ligações e reservatórios; (4) gestão de perdas; (5) DMC e roteirização; (6) preparação de rede para simulação hidráulica; (7) integração com ERP, faturamento e cadastro comercial; (8) atualização contínua da base, acompanhando obras e loteamentos (gisbase.com.br/servicos).

**O que não aparece no site:** aerolevantamento, levantamento topográfico próprio, cadastro imobiliário ou multifinalitário, drenagem e resíduos como produtos, e aplicativo de campo próprio. O blog recomenda GNSS de alta precisão, geofone e detector de massa metálica no levantamento, além de edição móvel em campo. Esses itens aparecem como boas práticas, sem indicação de que sejam oferecidos como produto. Os documentos de Nova Trento mostram que a empresa recebe bases topográficas de terceiros (DGN) e trabalha sobre imagens aéreas.

### 1.4 Tecnologias

- **Banco:** PostgreSQL + PostGIS. No caso de Jaraguá do Sul, o SIG compartilha o banco do sistema comercial Sansys, e gatilhos propagam ao mapa as alterações do cadastro comercial (COBRAC).
- **Processamento:** PL/pgSQL com `ST_StartPoint`, `ST_EndPoint`, `ST_DWithin` e centroides para montar a tabela de conectividade, fazer o roteamento iterativo (que para em válvula fechada e respeita o sentido de fluxo no esgoto) e reposicionar UCs no centro do lote (COBRAC).
- **Integrações declaradas:** Sansys, ArcGIS, QGIS, EPANET e WaterGEMS (site /hidrogeo).
- **Mapa base:** Google Maps (prints de 2019).
- **Modelo de camadas:** camadas de **contexto**, apenas visuais e não editáveis, e camadas de **negócio**, editáveis e integradas ao comercial (COBRAC).

### 1.5 Clientes e casos (site /projetos)

São 31 operadores listados. Os destaques são SAMAE Jaraguá do Sul (desde 2014), SAEV Votuporanga, SAAE Valença, SANEAR Rondonópolis (98 mil UCs), Águas de Joinville (175 mil UCs), DEMAE Caldas Novas, EMASA Balneário Camboriú e COMUSA Novo Hamburgo (desde 02/2026). Em SC há uma concentração de SAMAEs: Araranguá, São Bento do Sul, Tijucas, **Nova Trento (4.490 UCs, desde 11/2018)**, Gaspar, São Ludgero, Orleans, Pomerode e Fraiburgo. VISAN Videira tem início previsto para 10/2026. Para cada cliente, o site mostra o número de UCs, o volume medido por mês e a data de início.

### 1.6 Modelo de contratação

- **SaaS ou locação:** o site não publica preços. O edital de São Ludgero, típico do mercado, separa (i) implantação e conversão da base cartográfica como serviço único, (ii) locação mensal com suporte, licença de banco e Google Maps e (iii) banco de horas de customização (200 h). O valor máximo global foi de R$ 161.900,00 para 12 meses, prorrogáveis até 48.
- **Serviço avulso:** em Nova Trento, o cadastro representativo foi contratado à parte por preço fixo de R$ 17.460,00, pago em duas parcelas de 50% (assinatura e aceite), com prazo de 50 dias úteis (contrato 00029/2019).
- **Canal comercial:** demonstração com os dados da cidade do cliente e parceria com o fornecedor do sistema comercial (Sansys/JTech).

### 1.7 Diferenciais

1. Foco exclusivo em saneamento, com o modelo de dados já pronto para água e esgoto.
2. SIG no mesmo banco do comercial: a UC e a ligação nascem uma vez só.
3. Rede topológica, que permite roteamento, área de influência, isolamento e exportação EPANET.
4. Serviço de dados incluído: conversão, saneamento e atualização contínua.
5. Base instalada grande entre SAMAEs e SAAEs de pequeno e médio porte.

---

## 2. Documentos de Nova Trento (SAMAE NTR)

### 2.1 Documentos

| Arquivo | Conteúdo |
|---|---|
| `Proposta serviço SAMAE de NTR setembro 2019.docx` | Proposta de 09/09/2019, assinada em Florianópolis, com cinco prints do Hidrogeo |
| `Gisbase - Contrato ... .docx`, `(1)` e `(2)` | Minutas. A versão `(2)` é mais rígida (multa diária de 1%, propriedade do produto final do contratante, foro em SP). A versão sem sufixo é a que foi assinada |
| `contrato.novatrento.pdf` | Digitalização do **Contrato 00029/2019**, de 02/10/2019, com 5 páginas (sem camada de texto; lido por imagem) |
| 7 PNGs | Prints de MicroStation (*Level Display*) do arquivo `SES-DES-TOP-CAD.dgn`, que é a base topográfica usada como referência |

### 2.2 Escopo contratado

1. **Desenho representativo da rede de água**, sem levantamento de precisão. Foi feito sobre imagem aérea, com a geolocalização das UCs já existente no GIS e a informação dos técnicos do SAMAE. Inclui trechos e conexões com tipo, diâmetro, material e, quando possível, cotas.
2. **Complementação e atualização de logradouros:** 233 km de vias, com prioridade para onde já há consumidores cadastrados.
3. **Ramais gerados por geoprocessamento:** para cada UC com ligação ativa no cadastro comercial, o ramal liga o cavalete ao trecho de rede **mais próximo, desde que esteja a menos de 100 m**.
4. **Levantamento de componentes com a equipe local** (até 2 visitas de 1 dia): reservatórios e seus atributos, mananciais, ETAs, registros, válvulas e poços.
5. **Treinamento de 2 dias:** edição da rede, mapas temáticos (OS, faturamento, consumo, pendência, grupo, rota e UCs por região), abertura de OS pelo mapa e configuração do GIS.
6. **Precisão:** o documento declara a rede "aproximada", voltada à decisão gerencial. Não há classe de exatidão, levantamento GNSS nem norma cartográfica.
7. **Entregáveis:** base no Hidrogeo já em produção e relatório de entrega e conclusão, que dispara a segunda parcela.

### 2.3 Simbologia vista nos prints do Hidrogeo (proposta)

- **Rede:** cor por diâmetro, um tom para cada classe (rosa, roxo, laranja, verde-limão, azul etc.).
- **Conexões:** pontos com cor por tipo. Registro de manobra, tê e cap aparecem em vermelho, laranja, roxo e ciano.
- **Infraestrutura:** marcador verde no formato de pino.
- **Consumidores:** pontos amarelos sobre os logradouros.
- **Mapa base:** satélite Google, com escala gráfica.

### 2.4 Camadas da base topográfica (DGN, níveis vistos nos PNGs)

| Prefixo | Níveis | Geometria e simbologia observada |
|---|---|---|
| `_TER` | Curva Int, Curva Mest, Ponto cotado | Curvas intermediárias em verde e mestras em laranja. Ponto cotado como círculo vazado com cota em metros e duas casas decimais (ex.: 26,84) |
| `DIV` | Árvore, Caixa energia, Caixa telefone, Poste, Registro água | Pontos (registro de água como círculo ciano em quadrantes) |
| `DRE` | Ala drenagem, Boca de lobo, Caixa drenagem, Grelha, Nome, Rio, Tubulação, Vala | Bocas de lobo e grelhas como retângulos hachurados. Caixa como quadrado com meio preenchimento. Tubulação, vala e rio como linhas |
| `PAV` | Asfalto, Chão batido, Concreto, Lajota, Paralelepípedo | Linhas ou polígonos de revestimento da via (print "Topo Tipo PAV") |
| `URB` | Alinhamento predial, Divisa lote | Alinhamento como linha contínua. Divisa de lote como linha tracejada magenta |
| `USO` | Baldio, Comercial, Industrial, Obra, Residencial | Rótulo em caixa com letra de uso (R, C, I, B) sobre o número de pavimentos (1P, 2P) |
| `VIA` | Acostamento, Bordo pista, Logradouro, Meio-fio, Ponte | Linhas. O print "5 tipos de via" separa esses cinco tipos |
| `MALHA`, `FOLHA` | Texto, Externo, Fina | Articulação de folhas e textos |

Conclusões para o padrão de levantamento: a base segue a convenção **PREFIXO_Feição** por tema (terreno, diversos, drenagem, pavimento, urbano, uso, via). Ela reúne cadastro técnico de saneamento (registro de água e drenagem completa) com elementos de cadastro imobiliário (lote, alinhamento, uso e pavimentos) e de topografia. Os prints não mostram a escala nem a tolerância do levantamento.

---

## 3. Requisitos de GIS do Pregão 09/2020 (SAMAE São Ludgero)

Os requisitos estão no Termo de Referência, itens 3.2.18 e 3.6.

**Arquitetura e base**
- GIS web no mesmo ambiente e do mesmo fornecedor do sistema comercial (3.2.18).
- Camadas que podem ser ligadas e desligadas sobre mapa base Google, com Bing e OSM como alternativa (3.2.18.2).
- Dados de negócio vetoriais em **PostgreSQL ≥ 9.1 + PostGIS ≥ 2.0**, ou banco equivalente com funções espaciais (3.2.18.3).
- Consumo de **WMS ≥ 1.0** (ex.: IBGE) e edição pelo navegador via **WFS ≥ 1.0** (3.2.18.4; 3.6.4).
- Acesso segregado por perfil, tanto para leitura quanto para edição (3.2.18.10–11).

**Navegação e busca**
- Zoom, zoom para camada ou elemento, pan e rótulos por camada (3.2.18.5).
- Busca por lat/long ou UTM SIRGAS 2000, por coluna de atributo e por endereço, inclusive de imóveis ainda em cadastramento (3.2.18.6).

**Edição**
- Ponto, linha e polígono, com inserção e movimentação de vértices, escala e rotação (3.2.18.7).
- Criação de camada vetorial e da sua tabela, junção de tabelas e inclusão de camada raster.
- Camadas de negócio mínimas: reservatório, trecho, poço, manancial, ETA, ETE, EEE, elevatória de água, conexão entre objetos, ligação de água e de esgoto, válvula, macromedidor, hidrante, projetos internos e externos, registro de manobra, booster, PV e TI (3.2.18.8).
- Edição simultânea da geometria e do cadastro comercial: o reservatório criado no mapa aparece no comercial (3.2.18.9).
- Ligação nova de água ou esgoto desenhada no GIS e criada no comercial com vínculo bidirecional (3.2.18.12).
- Rede lógica até o ponto de ligação (3.2.18.13).
- **Roteamento** da rede para identificar UCs e elementos conectados (booster, EEE, reservatório, ETA, ETE, ERAT, bacias) (3.2.18.14).
- Simbologia, cores e rótulos configuráveis pelo editor, com legenda temática (3.2.18.15–16).
- Importação de shapefile como camada de contexto (3.2.18.17).

**Mapas temáticos mínimos (3.2.18.18)**
Consumo acima ou abaixo da média, consumo faturado pela média, leitura zero, cortadas, suprimidas ou desligadas, OS abertas de vazamento no cavalete ou na rede, OS de falta de água ou qualidade, hidrômetro por idade e tipo, faturas vencidas há mais de 60 dias e grandes consumidores. Todos com filtro por categoria.

**Implantação (3.6)**
- Três etapas: rede lógica até a UC, temático com dados comerciais e camadas cartográficas restantes.
- Importação de **DWG ≥ 2007, DXF, DGN, SHP e INP** em qualquer datum usado no Brasil, com resultado em camadas separadas.
- Diagnóstico e correção de topologia, conversão para metro, escala 1:1 e georreferenciamento em **SIRGAS 2000 / UTM 22S**.
- Setorização em quadrículas e vínculo de cada elemento ao comercial.
- Auditoria do modelo de dados: integridade referencial, PKs, normalização e índices.
- Bairros, distritos, lotes e arruamentos em camada sobreposta ao mapa base.
- Integração com Google Earth e Maps.
- Conversão de bairros, quadras, lotes e logradouros a partir de CAD ou SHP sem custo adicional, com análise do pedido em até 1 dia útil (3.2.18.19).
- Treinamento em inserção, validação e edição.

**Requisitos espaciais fora do módulo GIS**
- Croqui de localização da UC (3.2.2.4).
- Trajeto GPS do leiturista no mapa, com cor por status da leitura e animação do percurso (3.2.4.18; 3.2.5.6).
- OS programadas e equipes no mapa (3.2.11.25; 3.2.12.6).
- Fotos de campo associadas à UC.
- Drill-down geográfico no BI (3.2.20.8.11).
- Na prova de conceito, demonstrar a gestão GIS e **2 mapas temáticos escolhidos pela comissão** (10.2.22–23).

---

## 4. O que o Ziip SAN deve absorver (priorizado)

| # | Capacidade | Origem |
|---|---|---|
| P0 | GIS no **mesmo banco** do comercial (PostgreSQL/PostGIS), com UC, ligação e ramal únicos e gatilhos de sincronização | COBRAC; edital 3.2.18.3/9/12 |
| P0 | **Modelo topológico obrigatório:** todo trecho começa e termina em uma conexão; tabela de conectividade; atributo de sentido de fluxo no esgoto | COBRAC |
| P0 | Camadas de negócio mínimas do edital (18 tipos) e separação entre **contexto** e **negócio** | edital 3.2.18.8; COBRAC |
| P0 | Edição web completa (vértices, *split* e *merge* de trecho, inserção de conexão que segmenta a rede) com permissão por camada | edital 3.2.18.7/10/11; COBRAC |
| P0 | Mapas temáticos parametrizáveis com os 10 temas do edital, filtro por categoria e legenda | edital 3.2.18.18; contrato NTR |
| P0 | Importação de DWG, DXF, DGN, SHP e INP com reprojeção para SIRGAS 2000 / UTM, diagnóstico de topologia e *parsing* de rótulos CAD (DN e material) para atributos, guardando o texto não interpretado em `observacoes` | edital 3.6.1–3.6.7; COBRAC |
| P1 | **Roteamento e área de influência** por ETA, reservatório, ETE ou EEE (para em válvula fechada); polígono de atendimento; UCs afetadas por manobra e registros a fechar | COBRAC; edital 3.2.18.14; site |
| P1 | **Geração automática de ramais:** cavalete → trecho mais próximo, com distância máxima parametrizável (padrão de 100 m) | contrato NTR 1.3 |
| P1 | Reposicionamento de UC (ponto de GPS da leitura → centroide do lote mais próximo) com fila de revisão manual | COBRAC |
| P1 | Exportação **EPANET .inp** da rede (e entrada para WaterGEMS) | site; prints da proposta |
| P1 | Abertura e visualização de OS no mapa; trajeto do leiturista e das equipes | contrato NTR; edital 3.2.4.18, 3.2.11.25, 3.2.12.6 |
| P1 | WMS e WFS (entrada e saída), busca por coordenada, por atributo e por endereço; mapa base Google, Bing ou OSM | edital 3.2.18.2–6, 3.6.4 |
| P2 | DMC, bacias e sub-bacias com balanço macromedição × micromedição por setor e mapa de perdas (com simulador de perdas como isca comercial) | site (serviços e calculadora) |
| P2 | Biblioteca de camadas de cadastro urbano e topográfico no padrão `PREFIXO_Feição` (TER, DIV, DRE, PAV, URB, USO, VIA), com simbologia padrão e import direto de DGN por nível | PNGs de NTR |
| P2 | Camadas de **drenagem** (boca de lobo, grelha, caixa, tubulação, vala, rio) e de **uso do lote** (uso e pavimentos), que abrem caminho para cadastro multifinalitário e taxa de drenagem | PNGs de NTR |
| P2 | Simbologia de rede por classe de diâmetro e de conexões por tipo, editável pelo usuário | prints da proposta; edital 3.2.18.15 |
| P2 | Pacote de serviço de dados (conversão, saneamento e atualização contínua) vendido junto com o SaaS, com SLA de análise de 1 dia útil | site; edital 3.2.18.19 |
| P3 | Drill-down espacial no BI; exportação de mapa em PDF; integração com Google Earth (KML) | edital 3.2.20.8.11, 3.6.12; prints |
| P3 | Registro de nível de confiança da geometria (representativa, cadastral ou GNSS), porque o caso NTR mostra base "aproximada" convivendo com base precisa | contrato NTR |

---

## URLs e arquivos consultados

**Web**
- https://www.gisbase.com.br/
- https://www.gisbase.com.br/hidrogeo
- https://www.gisbase.com.br/servicos
- https://www.gisbase.com.br/projetos
- https://www.gisbase.com.br/sobre
- https://www.gisbase.com.br/calculadora
- https://www.gisbase.com.br/contato
- https://www.gisbase.com.br/blog
- https://www.gisbase.com.br/blog/cadastro-tecnico-redes-agua-esgoto-por-onde-comecar
- Schwingel, Dacol, Ribas. *Desenvolvimento de um SIG aplicado ao cadastro técnico de infraestrutura… a experiência do SAMAE de Jaraguá do Sul*. COBRAC 2020, UFSC: https://ojs.sites.ufsc.br/index.php/cobrac/article/download/10693/9127/39539

**Locais** (`G:\Meu Drive\Old Drive\ATESTADOS\Nova trento\`)
- `Proposta serviço SAMAE de NTR setembro 2019.docx`
- `Gisbase - Contrato de Prestação de Serviços.docx`, `(1).docx`, `(2).docx`
- `contrato.novatrento.pdf` (Contrato 00029/2019, digitalizado)
- `edital_09-2020_-_pp_sistema_gestao_saneamento1.pdf` (SAMAE São Ludgero)
- `5 tipos de via.png`, `Alinha predial (quadra) - aberta.png`, `CN e Pto cotado.png`, `DRE registro e caixas.png`, `Divisa Lote - linha.png`, `Topo Tipo PAV.png`, `ocupação lote.png`

Não foram encontradas fontes públicas adicionais sobre o Hidrogeo além do site da empresa e do artigo COBRAC. Perplexity e Brave retornaram erro de autenticação nesta sessão.
