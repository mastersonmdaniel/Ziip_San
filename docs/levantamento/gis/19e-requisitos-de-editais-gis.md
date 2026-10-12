# 19e — Requisitos de GIS e cadastro técnico em editais públicos (2022–2026)

Data: 12/10/2026. Objetivo: identificar o que as licitações brasileiras de saneamento exigem de GIS e de cadastro técnico de
redes, para definir o requisito mínimo de mercado do módulo GIS do Ziip SAN. Complementa o `../editais/18d-aderencia-cadastro-medicao-gis.md`,
que já cobre VISAN/Videira-SC (PE 06/2026) e SAMAE São Ludgero-SC (PE 2/2026); esses dois entram aqui só na contagem
(colunas V e S), sem repetir o detalhe.

## 0. Método e limitações

- Busca no PNCP (API de busca), em sites de autarquias e companhias, BLL, BNC, Portal de Compras Públicas e TCE-RS.
  O serviço de anexos do PNCP (`pncp-api/.../arquivos`) respondeu 503 durante toda a pesquisa; os documentos foram
  obtidos nas fontes de origem. Perplexity e Brave estavam indisponíveis (chave inválida).
- Foram lidos 15 TRs/editais novos (F1–F15), mais os dois do 18d (V, S): 17 fontes. Universos de contagem:
  **U = 17** (todas); **Sw = 12** (as que contratam software: F1–F10, V, S); **L = 10** (as que contratam levantamento de
  campo: F1–F6, F11–F14). Cada contagem diz o universo usado.
- **Modelos repetidos.** Vários TRs são cópia de um mesmo modelo, o que infla a frequência. Identificados: **Modelo A**
  (F1, F2, F3, F4: SIG web SaaS + recadastramento + OS online + CCO; F1 traz no título "cGis Saneamento"; a Céltica é
  citada no ETP de Passos e venceu em Manhuaçu); **Modelo B** (F5, F6: GIS municipal em código aberto com entrega de
  código-fonte; vencedora SQL Tecnologia nos dois); **Modelo C** (F7, F9, S e, segundo o 18d, V: sistema integrado com GIS,
  origem declarada no SAMAE de Jaraguá do Sul). F8, F10 e os serviços F11–F15 têm redação própria. Há, portanto, cerca de
  oito matrizes de redação independentes; a frequência deve ser lida com esse desconto.
- Requisitos foram resumidos pelos subagentes a partir do texto integral; os que não apareceram no resumo foram tratados
  como ausentes, o que pode subestimar algumas contagens.

## 1. Fontes

### 1.1 Lidas (base da matriz)

| ID | Órgão | Porte (aprox.) | Ano | Processo | Tipo | Objeto (resumo) | Fonte |
|---|---|---|---|---|---|---|---|
| F1 | SAAE Senador Firmino-MG | Pequeno (≈7 mil hab.) | 2023 (TR de 04/01/2023) | Não consta no TR; estimado R$ 26.000/ano | Misto | Locação de SIG web SaaS (cGIS), OS online, app do cidadão, CCO e atualização cadastral | [TR](https://www.saaesenadorfirmino.com.br/imagens_sistema/documento/a4f29daddf4245331ef6a19c87ecd7f6.pdf) |
| F2 | SAAE Passos-MG | Médio (≈111 mil hab.) | 2024 | PE 002/2024, proc. 237/2023; PNCP 23278690000140-1-000060/2023; homol. R$ 189.400 | Misto (Modelo A) | Atualização cadastral georreferenciada + SIG web SaaS + CCO | [Edital/TR](https://saaepassos.com.br/pdfs/uploadpdf/PREG%c3%83O_ELETR%c3%94NICO_002_2024_GEOPROCESSAMENTO.pdf), [ETP](https://saaepassos.com.br/pdfs/uploadpdf/ETP_GEO.pdf) |
| F3 | SAS Barbacena-MG | Médio (≈125 mil hab.) | 2024 | PE 010/2024, proc. 009/2024; PNCP 17714486000122-1-000027/2024; homol. R$ 269.000 | Misto (Modelo A) | Atualização cadastral de água, esgoto e drenagem + SIG SaaS | [Edital/TR](https://lanceeletronico.blob.core.windows.net/processfiles/75d1c26c08c943b5ac18a8ebb47555dd.pdf) |
| F4 | SAAE Manhuaçu-MG | Médio (27.738 ligações) | 2024 | PE 32/2024, proc. 59/24; PNCP 22050561000138-1-000125/2024; contrato R$ 248.500 (Céltica) | Misto (Modelo A) | Idem, com drenagem e CCO com videowall | [Contrato com edital e TR](https://www.saaemanhuacu.mg.gov.br/processos-licitatorios/download/file?fid=3.541&file_id=f_a54a823fe962d1b071b9415d6f2dc296) |
| F5 | SANEAR Colatina-ES | Médio (33 mil UCs) | 2024 | PE/SRP 18/2024, proc. 064/2024; PNCP 06698248000154-1-000045/2024; homol. R$ 4.322.450 | Misto (Modelo B) | Módulo de saneamento no GIS web da Prefeitura (código aberto) + recadastramento | [TR](https://sanear.es.gov.br/wp-content/uploads/2024/08/PE-018-NOVO-GIS.pdf) |
| F6 | SAAE Linhares-ES | Médio (48 mil unidades) | 2025 | PE/SRP 18/2025, proc. 000669/2025; PNCP 27834977000160-1-000094/2025; homol. R$ 5.737.450 | Misto (Modelo B) | Módulo GSAN no GIS da Prefeitura + recadastramento técnico-comercial | [TR](https://www.saaelinhares.com.br/uploads/bids/32.pdf) |
| F7 | SAAE Costa Rica-MS | Pequeno (≈26 mil hab.) | 2024 | PE 12/2024; PNCP 00202770000117-1-000014/2024; homol. R$ 256.000 | GIS em sistema integrado (Modelo C) | Solução integrada comercial, técnica e operacional | [PNCP](https://pncp.gov.br/app/editais/00202770000117/2024/14) (edital no PCP, PE-12-2024-2024-320088) |
| F8 | SIMAE Capinzal/Ouro-SC | Pequeno (≈30 mil hab.) | 2025 | PE 8/2025, proc. 114/2025; PNCP 82782079000114-1-000114/2025; homol. R$ 102.000 | GIS em sistema integrado | Locação de sistemas, com item de cadastro técnico de redes | [PNCP](https://pncp.gov.br/app/editais/82782079000114/2025/114) |
| F9 | SAMAE São Bento do Sul-SC | Médio (≈83 mil hab.) | 2025 | PE 49/2025; PNCP 86050978000183-1-000094/2025; homol. R$ 378.500 | GIS em sistema integrado (Modelo C) | Software integrado comercial e operacional | [PNCP](https://pncp.gov.br/app/editais/86050978000183/2025/94) (PCP, PE-49-2025-2025-422816) |
| F10 | SAMAE Balneário Rincão-SC | Pequeno (≈15 mil hab.) | 2026 | PE 10/2026; PNCP 17509587000161-1-000010/2026; estimado R$ 319.673,52; abertura 04/11/2026 | GIS em sistema integrado | Solução integrada (GIS é o módulo 15 de 24) | [Edital](https://bnccompras.blob.core.windows.net/processfiles/35e280dbea3e4929a2890cda73fd2427.pdf) |
| F11 | DAE Santa Bárbara d'Oeste-SP | Médio (≈900 km de rede) | 2025 | CC eletrônica 06/2025, proc. 2422/2025; PNCP 54010863000179-1-000416/2025; contrato R$ 165.000 (50 km) | Serviço | Cadastro de rede de água com GPR, pago por metro validado | [Edital/TR](https://www.daesbo.sp.gov.br/storage/licitacoes/263/10-2025/0iky8ZudalM9XeXoSiBQb7b3N0dGmdICKVHsgS5e.pdf) |
| F12 | CISPAR-PR (consórcio) | Municípios pequenos | 2025 | PE/SRP 90009/2025, proc. 56; PNCP 04823494000165-1-000051/2025 | Serviço (projeto + as-built) | Projetos de redes, cadastro técnico e topografia | [Edital/TR](https://www.cispar.pr.gov.br/uploads/licitacao/P092025SRP092025.pdf) |
| F13 | Município de Canela-RS | Médio (≈49 mil hab.) | 2026 | CC eletrônica 06/2026, proc. 225/2026; PNCP 88585518000185-1-000376/2026; R$ 634.665 no cadastro | Serviço (drenagem) | Plano diretor de drenagem e cadastro georreferenciado das redes pluviais | [Edital (TCE-RS)](https://portal.tce.rs.gov.br/app/silicon-download/documento_licitacao/9667296) |
| F14 | CAESB-DF | Companhia distrital | 2025 | CC eletrônica 90021/2025, proc. 00092-00025979/2025-43; PNCP 00082024000137-1-000220/2025; homol. R$ 4.616.176 | Serviço | Levantamento e atualização cadastral de redes de água e esgoto e unidades operacionais | [Edital e anexos (ZIP)](https://www2.caesb.df.gov.br/images/licitacoes/concorrencia/2025/CoE_90021_2025/1-Edital%20e%20Anexos.zip) |
| F15 | Cesan-ES | Companhia estadual | 2024 (homol. 01/2026) | LCE 026/2024 (Lei 13.303), proc. 2024.010714; homol. R$ 1.499.541 | Serviço (escritório) | Geoprocessamento para atualizar o cadastro técnico no GIS da Cesan | [Portal Cesan](https://compras.cesan.com.br/licitacaoView.php?idLicitacao=40185) |
| V | VISAN Videira-SC | Pequeno/médio | 2026 | PE 06/2026 | GIS em sistema integrado | Ver 18a/18d | — |
| S | SAMAE São Ludgero-SC | Pequeno (≈14 mil hab.) | 2026 | PE 2/2026, proc. 43/2026 | GIS em sistema integrado (Modelo C) | Ver 18c/18d | [Edital](https://samaesl.sc.gov.br/uploads/8_pe_2-2026-sigcos.pdf) |

Porte populacional aproximado pelo Censo 2022 quando o edital não informa.

### 1.2 Complementares (lidas em parte ou só metadados)

- **EMBASA-BA**, LC 128/2025 (PNCP 13504675000110-1-000053/2025; homol. R$ 1.966.683,84): cadastro de rede de água com
  GPR e sondagens; Projeto Básico não obtido. Habilitação por km pesquisado com GPR (110 km no lote 1).
- **Cesan-ES**, PEL 123/2022 (contrato R$ 1.218.999,60): fábrica de desenvolvimento do "GIS 2.0" (ArcGIS Server,
  GeoServer, PostGIS/SQL Server, Leaflet), medida em pontos de função.
- **TR não obtido, só metadados do PNCP:** SAAE Pirapora-MG PE 52/2026 (23535271000147-1-000050/2026; R$ 122.587,99;
  "Solução GIS Integrada" SaaS); SANEFRAI Fraiburgo-SC PCE 12/2025 (R$ 260.552,67; sistema web com SIG); SAMAE Rio
  Negrinho-SC inexigibilidade PRI 7/2024 (suporte ao GEOREDE/LOGPRO); EMASA Balneário Camboriú-SC inexigibilidade
  18-IL/2026 (R$ 1.912.586,59; ArcGIS); SAAE Jaguaribe-CE CC 2026082101 (cadastro de redes; homol. R$ 178.204,70).

## 2. Matriz consolidada

Ordenada pela frequência dentro de cada bloco. "Fontes" lista os IDs da seção 1.1.

### 2.1 Camadas, atributos e base de dados (U = 17)

| Requisito | Freq. | Fontes / exemplos |
|---|---|---|
| Atributos mínimos de trecho: material, diâmetro, extensão | 17/17 | Todos; CAESB acrescenta status (executada, ativa, desativada, retirada) e datas |
| Camadas da rede de água (trechos, registros/válvulas, hidrantes, ventosas, descargas) | 16/17 | Todos, exceto F13 (drenagem) |
| Camadas de esgoto (coletores, PVs, TIL/TL, ligações, recalque) | 15/17 | Exceto F11 (só água) e F13 |
| DWG como insumo ou entrega | 15/17 | Importação: F1–F7, F10, V (migração DWG → PostGIS), S (DWG 2007+, DXF, DGN, SHP, INP); entrega: F11–F15 |
| Shapefile (importar, exportar ou entregar) | 15/17 | Exceto F13 (DWG + GPKG) e F15 (edição direta no GIS) |
| Profundidade e cotas (tampa e fundo de PV, profundidade de rede) | 13/17 | F1–F6, F8, F9, F11–F15 |
| Base de contexto: logradouros, bairros, quadras, lotes | 12/17 | F1–F7, F12, F14, F15, V, S |
| Setorização: zonas de pressão, setores de manobra, DMC, bacias | 12/17 | F1–F4 (zonas de abastecimento e pressão), F7–F11, F15, V, S |
| Unidades operacionais (ETA, ETE, elevatórias, reservatórios, poços, captações, macromedidores) | 11/17 | F1–F6, F12, F14, F15, V, S |
| Ligações e hidrômetros georreferenciados ligados à matrícula | 11/17 | F1–F7, F10, F14, V, S; F5/F6 exigem croqui do hidrômetro com amarração ao lote |
| SIRGAS 2000 expresso | 11/17 | F2–F4, F7, F9, F11–F14, V, S; fuso UTM fixado em 7 (22S ou 23S) |
| Regras de topologia (nós automáticos, snap, sem trechos soltos) | 11/17 | F1–F7, F9, F10, F11 (controle de qualidade), F14 (topologia íntegra) |
| Drenagem (galerias, bocas de lobo, PVs pluviais) | 8/17 | F1, F3–F6, F9, F13, V; F14 só como interferência |
| Metadados de coleta por feição (precisão, autor, data, foto) | 8/17 | F1–F4 (campos "Precisão", "Correção", "Coletado por"), F11, F13, F14, F15 (extratos e certificado de confiabilidade) |
| Banco espacial obrigatório | 8/17 | PostGIS nomeado em F5, F6, F9 (ou SQL Server), S (PG ≥ 15), V; F10 (índice GiST, sem licença proprietária); F1, F4 (genérico) |
| Interface com QGIS (edição, padrão de simbologia ou entrega .qgs) | 7/17 | F5, F8, F9, F11, F12, F15, V |
| Estado operacional de válvula (aberta/fechada) | 6/17 | F7 (padrão EPANET), F9, F10, F11, F15, S |
| Ciclo de vida da feição (projeto, obra, ativa, desativada) | 6/17 | F1, F9, F14, F15, V, S |
| Catálogo formal de materiais, peças e conexões | 5/17 | F7 (tê, cruzeta, curva, cap), F10 (nós, acessórios, fabricantes), F11, F14 (tabela de materiais e quadro de simbologia), F15 (normas ENG.049/050) |
| Resíduos sólidos (rotas de coleta, serviços urbanos) | 5/17 | F5, F6, F9, V, S |
| GeoPackage | 4/17 | F7 (com fluxo de aprovação), F9, F13, V |

### 2.2 Funcionalidades do software (Sw = 12)

| Requisito | Freq. | Fontes / exemplos |
|---|---|---|
| Integração com o cadastro comercial (UC/ligação no mapa, sincronização) | 12/12 | Todos; F5 (FOXFAT), F6 (GSAN), F8 só por importação |
| GIS acessado pelo navegador | 11/12 | Exceto F8 (admite QGIS local) |
| GIS avaliado na POC | 11/12 | Exceto V (item 24 fora da POC); detalhe na §2.4 |
| Edição web de feições e atributos | 10/12 | F1–F7, F10, V, S; F6 classifica como "desejável" |
| Simulação de interrupção ou manobra com clientes, logradouros e trechos afetados | 10/12 | F1–F7, F9 (com OS em lote), F10, V |
| OS georreferenciadas no mapa | 10/12 | F1–F7, F9, V, S |
| Mapas temáticos (consumo, OS, inadimplência, hidrometria) | 10/12 | F1–F7, F9, F10, S |
| Simbologia e rótulos configuráveis por camada | 10/12 | F1–F7, F10, V, S |
| Importação de CAD/SHP com conversão da base existente | 10/12 | F1–F7, F10, V, S; F5/F6 e S exigem diagnóstico e validação topológica da base |
| EPANET | 9/12 | Exportação .inp em F1–F7, F10 (com distribuição de demandas); S importa INP |
| Mapa base Google, Bing ou OSM | 9/12 | F1, F4 (Google licenciado: 7.000 e 25.000 visualizações/ano), F5, F6 (Mapbox), F7–F9, V, S |
| App móvel de campo integrado ao GIS | 9/12 | F1–F7, F10, V; exigência expressa de edição offline não apareceu |
| Pesquisa por atributo, endereço e coordenada (UTM) | 9/12 | F1–F5, F8, F10, V, S |
| Relatório de extensão de rede por material e diâmetro | 8/12 | F1–F6, F8, F10; F15 pede o cálculo automático a partir de cada DWG |
| Seleção por polígono com estatísticas ou cálculo de vazão | 8/12 | F1–F6 (vazão com per capita e perdas), F9, V |
| Traçado a montante/jusante ou roteamento | 7/12 | F1–F4, F9, V, S |
| Exportação DXF | 7/12 | F1–F6, F10 |
| Exportação KML / Google Earth | 7/12 | F1–F6, S |
| Permissões por perfil e camada | 7/12 | F1–F4, F10, V, S |
| Integração com telemetria, SCADA ou macromedição | 6/12 | F1–F6 |
| WMS/WFS expressos | 5/12 | F7, F9, F10 (também WFS-T e WCS), V, S; F5/F6 com GeoServer |
| SMS aos clientes de uma área | 5/12 | F1, F3–F6 |
| Camada de rastreamento de veículos | 5/12 | F1–F5 |
| App do cidadão integrado ao GIS | 5/12 | F1–F5 |
| Centro de controle operacional com hardware | 4/12 | F1–F4 (Modelo A) |
| Impressão de mapa em PDF com escala, norte e legenda | 3/12 | F5, F6, F10 |
| SWMM | 0/12 | Nenhum; F13 usa HEC-HMS/SWAT e HEC-RAS/MIKE 11 |

### 2.3 Levantamento de campo (L = 10)

| Requisito | Freq. | Fontes / exemplos |
|---|---|---|
| Precisão exigida em número ou classe | 8/10 | Decimétrica: F1, F3 (F2 diz decimétrica e centimétrica); centimétrica ou ≤ 2 cm: F4, F5/F6 (8 mm + 1 ppm H, 15 mm + 1 ppm V), F13, F14 (estação total 2″, 2 mm ± 2 ppm) |
| GNSS geodésico/RTK com estação total para apoio | 6/10 | F3, F12, F13, F14 (RINEX, marcos com monografia), F11 (apoio ao GPR), F5/F6 |
| Ficha de campo por feição com fotos | 6/10 | F1–F4 (registros, elevatórias, PVs, hidrômetro com foto da leitura), F5/F6 (campos obrigatórios e de foto) |
| Escala ou referencial cartográfico definido | 5/10 | 1:1 no CAD (F12, S), 1:1.000 e perfis (F14), 1:2.000 (F13), SICAD (F14) |
| Normas da contratante para desenho e simbologia | 4/5 serviços | F11 (padrão DAE no QGIS), F12 (Manual de Obras Sanepar), F14 (NR-01/02/05), F15 (ENG.049/050) |
| Pagamento por metro ou unidade validada | 4/5 serviços | F11, F12, F14, F15 |
| GPR ou outra detecção não invasiva | 3/10 | F11, F12, F14 (+ EMBASA); na CAESB e na EMBASA, como habilitação |
| Sondagens | 2/10 | F12, F14 (+ EMBASA) |
| NBR 13.133 | 2/10 | F12, F14; F12 cita também NBR 12586, 12218 e 14645 |

### 2.4 POC e habilitação

| Requisito | Freq. | Fontes / exemplos |
|---|---|---|
| POC com GIS, eliminatória | 11/12 (Sw) | Limiar: 75% (F9, 528 itens), 80% por módulo (F8), 89–90% (F2–F4, S), 100% obrigatórios + 80–90% demais (F5, F6, F10), todos os itens (F1). Prazo para pendências: 60 dias (F3), 90 dias (F10); S não admite |
| Atestado com quantitativo | 11/17 (U) | Km de rede: F1 (150), F2/F3 (100), F4 (400), F11 (25 km com GPR), F14 (158 km com GPR e geofone), F15 (50 água + 100 esgoto); unidades: F1 (40 mil entidades, 20 mil consumidores), F5 (16 mil), F6 (24 mil), F9 (25 mil ligações), F8 (5 mil) |
| Registro no CREA/CAU e CAT do responsável | 7/17 | F1, F3, F5, F6, F11, F14, F15 |
| Nenhuma POC em contratos de serviço | 0/5 | F11–F15 usam treinamento, garantia ou controle de qualidade |

## 3. Requisitos raros, mas relevantes

1. **Saída do contrato e licença.** F10 exige banco sem licença proprietária e a entrega da base em SHP ou GeoJSON ao fim
   do contrato; S põe os custos de licença por conta da contratada. É barato de atender e protege contra a objeção de
   aprisionamento.
2. **Código-fonte e GIS da Prefeitura (Modelo B).** F5 e F6 exigem GIS em código aberto, entrega do código, pilha fixa
   (PostgreSQL 14/PostGIS, GeoServer 2.15, Angular 8, Leaflet, Java 11) e uma POC em que o licitante altera o código ao
   vivo. F6 recusa atestado de SaaS. É incompatível com o modelo SaaS do Ziip SAN.
3. **Rastreabilidade e qualidade do dado (F15, F11).** A Cesan pede extrato de entrada e de cadastramento por feição,
   certificado de confiabilidade, evidências anexadas e controle por acurácia posicional, completude, consistência lógica
   e acurácia temática. O DAE de Santa Bárbara d'Oeste pede checagem de nós soltos e sobreposições e garantia de seis meses.
   Sugere um fluxo de aprovação com versão e evidência por feição, que também aparece em F7 (GeoPackage com aprovação).
4. **Análises e desenho assistido:** bacias e polígonos de roteamento gerados automaticamente (S), balanço macro × micro
   por DMC (F7), amarração automática a postes e árvores (F7), desenho paralelo ao meio-fio (F5), ortofoto (S), TIN
   (F10) e cota automática ao inserir ponto (F5).
5. **Ligações:** ramal com vários hidrômetros (F10) e croqui do hidrômetro obrigatório (F5, F6).
6. **Gestão do cadastro:** log de produtividade do cadastrador (F10) e extensão calculada por diâmetro e material de cada
   DWG recebido de terceiros (F15).
7. **Interoperabilidade de escrita:** WFS-T e WCS (F10); inserção pelo navegador via WFS (S).
8. **Direcionamento.** F1 traz o nome comercial "cGis Saneamento" no título e no atestado; F1–F4 exigem app publicado nas
    lojas e CCO com hardware. São pontos de impugnação por restrição à competitividade, se o Ziip SAN quiser disputar.

## 4. Conclusões: o requisito mínimo de mercado

1. **Dois mercados distintos.** Autarquias pequenas e médias contratam software, com o GIS dentro do sistema integrado
   (Modelo C, F8, F10, V, S) ou como SIG web próprio com recadastramento junto (Modelos A e B). Companhias estaduais e
   autarquias grandes (F11, F14, F15, EMBASA) já têm GIS (ArcGIS Pro/Server, QGIS, PostGIS) e licitam só o serviço de
   cadastro, medido em metros, com GPR como requisito de habilitação. O Ziip SAN disputa o primeiro mercado. No segundo,
   o que importa é a aderência do modelo de dados às normas da companhia.
2. **Mínimo de mercado para o software** (presente em 9 ou mais dos 12 editais de software, ou em todos os modelos):
   - GIS web no navegador, sobre banco espacial (PostGIS predomina e não pede licença), em SIRGAS 2000/UTM;
   - camadas de água e esgoto com unidades operacionais, ligações e hidrômetros ligados à matrícula, setores e zonas
     de pressão; atributos material, diâmetro, extensão, profundidade e cotas;
   - edição web com regras de topologia (nós automáticos, snap, sem trecho solto);
   - migração da base existente a partir de DWG/DXF/SHP, com diagnóstico, e exportação SHP/DXF/KML;
   - integração com o comercial e OS no mapa;
   - simulação de interrupção ou manobra com lista de clientes e trechos afetados;
   - exportação para o EPANET;
   - temáticos, simbologia e rótulos configuráveis, pesquisa por atributo, endereço e UTM, relatório de extensão de rede;
   - app móvel de campo integrado ao mapa;
   - demonstração em POC eliminatória, com limiar entre 75% e 100%.
3. **Diferencial frequente, não universal** (5 a 8 editais): WMS/WFS, QGIS lendo e editando a base, traçado a montante
   e jusante, seleção por polígono com estatísticas ou vazão, permissões por camada, telemetria e SMS por área.
4. **Não é requisito de mercado hoje:** SWMM (0/17), catálogo formal de peças e conexões (5/17), edição offline expressa
   (não apareceu), resíduos no GIS (5/17) e GeoPackage (4/17). O catálogo de peças, porém, é exigido pelas companhias que
   fixam normas próprias (F14, F15) e pelo Modelo C (F7, F10). Convém tê-lo no modelo de dados mesmo sem tela dedicada.
5. **Levantamento:** quando o edital contrata campo, o padrão é GNSS geodésico/RTK com precisão centimétrica, SIRGAS 2000,
   ficha por feição com foto e entrega em DWG e SHP (ou geodatabase), no padrão de desenho da contratante. O GPR é exigido
   só pelas companhias grandes. Para o Ziip SAN, o que importa é importar esse pacote sem retrabalho, preservando a
   precisão e o autor de cada ponto, como pede F12.
6. **Relação com o 18d.** As entregas E12–E15 do 18d cobrem o núcleo do item 2. Faltam nelas, pela frequência observada:
   relatório de extensão por material e diâmetro, exportação DXF/KML, seleção com estatísticas, metadados de coleta por
   feição, cláusula de devolução da base (F10) e o fluxo de aprovação com evidências (F7, F15). O escritor `.inp` já
   existente atende ao requisito EPANET de 9 dos 12 editais de software, desde que exposto na interface.
7. **Oportunidades abertas** na data deste levantamento: SAAE Pirapora-MG PE 52/2026 (propostas até 19/10/2026) e SAMAE
   Balneário Rincão-SC PE 10/2026 (abertura 04/11/2026). Ambas exigem GIS integrado, que o produto ainda não demonstra.
