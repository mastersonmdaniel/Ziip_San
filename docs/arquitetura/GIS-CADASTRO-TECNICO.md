# GIS e cadastro técnico de redes: projeto de enriquecimento

- **Data:** 12/10/2026
- **Produto:** Ziip SAN (código interno SIGSAN, renomeado `ziipsan` na P0.33)
- **Decisões:** [ADR-0034](adr/ADR-0034-gis-cadastro-tecnico.md) (oito decisões do dono do produto, cartões de 12/10/2026);
  ajustes v2.10 do [ADR-0028](adr/ADR-0028-escopo-funcional-ampliado-pelos-editais.md) (item b) e do
  [ADR-0010](adr/ADR-0010-infra-nao-usada-congelada.md) (exceções); ajustes v2.11 do ADR-0034 e do
  [ADR-0033](adr/ADR-0033-desenvolvimento-local-e-aws-no-lancamento.md) (rede do piloto na homologação local), que
  resolvem as pendências P-3 a P-7 e o WMS da P-8 (§14.2); ajustes v2.12: equipe e capacidade fora do plano, com o
  [ADR-0029](adr/ADR-0029-capacidade-e-equipe-da-trilha-de-editais.md) revogado, e WMS servidor pelo GeoServer
  (ADR-0010 e ADR-0034, ajustes "GIS, v2.12"; §7.2)
- **v2.13:** ícones e símbolos ([ADR-0036](adr/ADR-0036-icones-e-simbologia-do-mapa.md)) e domínio do GIS por referência para outras verticais ([ADR-0037](adr/ADR-0037-plataforma-com-pacotes-de-vertical.md)); P1.30 entrega os símbolos de água e esgoto.
- **Plano:** [PLANO-EXECUCAO-PDCA-R0-R9.md](../PLANO-EXECUCAO-PDCA-R0-R9.md) v2.10 a v2.12 (P1.26 a P1.28, P2.21 a
  P2.23, P3.25 a P3.27; ED-13, ED-52 a ED-56 e ED-63 a ED-67 na trilha de editais)
- **Base:** levantamentos [19a](../levantamento/gis/19a-gisbase.md) (Gisbase/Hidrogeo e Nova Trento),
  [19b](../levantamento/gis/19b-concorrentes.md) (concorrentes), [19c](../levantamento/gis/19c-normativos-e-padroes.md)
  (normas e padrões), [19d](../levantamento/gis/19d-melhores-praticas-modelo-de-dados.md) (modelo de dados) e
  [19e](../levantamento/gis/19e-requisitos-de-editais-gis.md) (editais); matriz
  [18d](../levantamento/editais/18d-aderencia-cadastro-medicao-gis.md) (E12 a E16) e consolidado
  [18](../levantamento/editais/18-ADERENCIA-EDITAIS-CONSOLIDADO.md); código de `intelligence/sigsan-gis`
  (`V2_3__create_gis_schema.sql`, `GisNetworkElement`, `NetworkTopologyBuilder`, `InpWriter`, `EpanetSimulationService`)

Convenções. Nomes de tabelas, colunas, valores gravados, rotas e permissões em inglês (plano §8); textos ao usuário em
português. Esforço na escala do plano: **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa
com agentes; entre parênteses, semanas-pessoa, com incerteza de ±30%, como indicação de esforço, sem conversão em
equipe (v2.12). "Valor de referência a validar" marca todo valor
que não vem de norma conferida: deve ser confirmado contra a norma, o fabricante ou o padrão da prestadora antes de
entrar como padrão de fábrica. As normas ABNT foram identificadas em revendedores autorizados (19c §0) e precisam ser
conferidas no ABNT Catálogo.

---

## 1. Objetivo, escopo e posição de mercado

### 1.1 Objetivo

Transformar o módulo `sigsan-gis`, hoje uma tabela única que alimenta o escritor do EPANET, num cadastro técnico de
redes de água, esgoto, drenagem e resíduos sólidos no mesmo banco do comercial e da OS, com:

1. modelo de dados de papel topológico fixo e tipos extensíveis por tenant, com catálogo brasileiro de peças;
2. topologia persistida e validada em todos os caminhos de escrita (web, QGIS, app de campo, importação);
3. edição por pacotes revisados, ciclo de vida e histórico temporal com consulta "rede na data";
4. metadado de precisão por feição e meta por tenant, que separa cadastro levantado de cadastro aproximado;
5. publicação em tiles vetoriais pela `api`, simbologia padrão do produto e mapas temáticos sobre dados comerciais;
6. importação do cadastro existente (DWG, DXF, SHP e GPKG no núcleo; DGN v7 e INP do EPANET e do SWMM na trilha; DGN
   v8 por conversão assistida para DWG ou DXF) e exportação nos formatos de intercâmbio;
7. ligação direta entre feição, ramal, ligação, imóvel, hidrômetro e OS, que sustenta manobras com aviso e OS em lote.

### 1.2 Escopo

| Bloco | Conteúdo | Quando |
|---|---|---|
| Núcleo | modelo e catálogo (água e esgoto completos), topologia, pacotes e histórico, vínculo com o comercial, mapa web de consulta com tiles vetoriais, importador DWG/DXF/SHP/GPKG, temáticos e relatórios de precisão e extensão | P1 a P3, antes da homologação local do piloto (GIS-01 a GIS-08), cada parte no marco da sua fase (M6, M7 e M8; §13.6) |
| Trilha de editais | edição web completa (onda A); QGIS, traçados e manobras, EPANET e SWMM, drenagem, resíduos, campo, exportação, OGC autenticado por tenant (OGC API – Features e WFS na `api`, WMS pelo GeoServer) e importação ampliada (onda B); ativos e inventário regulatório (onda C) | P4, ondas A, B e C (GIS-09 a GIS-19) |

Os quatro domínios usam o mesmo modelo, o mesmo catálogo e a mesma topologia. Água e esgoto entram completos no
núcleo; drenagem e resíduos têm classes e papéis previstos desde o núcleo e recebem catálogo, regras e telas na onda B
(ADR-0034, decisão 1).

### 1.3 Não objetivos

- Ferramenta de desenho CAD ou de topografia. O produto importa e exporta CAD; não substitui AutoCAD, MicroStation ou
  software de levantamento.
- Serviço de levantamento de campo, GPR ou sondagem. O produto recebe e preserva o dado levantado por terceiros.
- Solver hidráulico próprio, calibração ou modelagem de qualidade da água. A hidráulica é exportação e importação para
  EPANET e SWMM (ADR-0034, decisão 8); as tabelas de simulação de `V2_3` ficam sem uso até decisão própria.
- Cadastro imobiliário ou multifinalitário. Lotes, quadras e logradouros entram como camadas de contexto e como
  referência do imóvel, sem gestão territorial.
- Integração de escrita com Esri (fora do escopo pelo diário §1). Exportação em SHP e GPKG para quem usa ArcGIS
  continua prevista.
- Servidor de mapas novo (QGIS Server, Martin, pg_tileserv), congelado pelo ADR-0010, salvo exceção registrada lá. O
  GeoServer entra só para o WMS servidor da onda B, em leitura, por exceção registrada no ADR-0010 (v2.12, §7.2).
- Versionamento por ramo (modelo da Esri). Projetos e obras usam pacotes com ciclo de vida (§6).
- Modelo 3D, BIM e IFC; ficam como referência futura (19c §2.4).

### 1.4 Requisito mínimo de mercado

Pelos 17 editais e termos de referência de 2022 a 2026 lidos no 19e, o mínimo para disputar editais de software (9 ou
mais dos 12 que contratam software, ou todos os modelos de redação) é:

| Requisito mínimo (19e §4.2) | Onde o projeto atende |
|---|---|
| GIS web no navegador sobre banco espacial (PostGIS) em SIRGAS 2000/UTM | GIS-01, GIS-06 |
| Camadas de água e esgoto com unidades operacionais, ligações e hidrômetros ligados à matrícula, setores e zonas de pressão; material, diâmetro, extensão, profundidade e cotas | GIS-01, GIS-02, GIS-05 |
| Edição web com topologia (nós automáticos, snap, sem trecho solto) | GIS-03, GIS-09 |
| Migração da base em DWG/DXF/SHP com diagnóstico; exportação SHP, DXF e KML | GIS-07, GIS-16, GIS-18 |
| Integração com o comercial e OS no mapa | GIS-05, GIS-08 |
| Simulação de interrupção ou manobra com clientes e trechos afetados | GIS-11 |
| Exportação para o EPANET | GIS-12 (antes dela, o escritor atual pela view de compatibilidade, §2.3) |
| Temáticos, simbologia e rótulos configuráveis, pesquisa por atributo, endereço e UTM, relatório de extensão de rede | GIS-06, GIS-08 |
| App móvel de campo integrado ao mapa | GIS-15 |
| POC eliminatória com limiar de 75% a 100% | ensaios por fase (P3.23, ED-01) |

Diferenciais frequentes (5 a 8 editais): WMS/WFS (GIS-06, GIS-17), QGIS lendo e editando (GIS-10), traçado a montante
e jusante (GIS-11), seleção por polígono com estatísticas (GIS-08), permissões por camada (GIS-09), telemetria (ED-17)
e SMS por área (GIS-11 com ED-41). Não é requisito de mercado hoje, mas entra por decisão do produto: SWMM (0/17),
catálogo formal de peças (5/17, exigido por companhias com norma própria), edição offline (não expressa), resíduos
(5/17) e GeoPackage (4/17).

### 1.5 Diferenciais frente aos concorrentes

Pelo 19b, o mercado nacional se divide em sistemas comerciais com GIS acoplado (Sansys, Waterfy), GIS separado ligado
por integração (GEOSAN, ARGOS) e Esri implantada pela Imagem nas companhias estaduais; a Saneago usa Giswater. Nenhum
concorrente nacional publica modelo de dados, catálogo ou regras de topologia. Diferenciais que este projeto busca:

| # | Diferencial | Quem não tem (19b) | Entrega |
|---|---|---|---|
| 1 | Técnico, comercial e OS no mesmo banco: manobra → válvulas a fechar → imóveis afetados, com prioridade para clientes sensíveis → aviso → OS de fechamento e reabertura em lote → período de desabastecimento por imóvel | GEOSAN e ARGOS integram por views; Esri e Giswater não têm comercial | GIS-05, GIS-11 |
| 2 | Modelo de nível Utility Network (classe, tipo, catálogo, terminais, regras de conectividade em tabela, zonas por traçado, isolamento com estado da válvula) sobre PostGIS, sem licença Esri | Esri exige Advanced Editing e Enterprise | GIS-01 a GIS-03 |
| 3 | Catálogo brasileiro de fábrica (NBR, padrões Corsan/Aegea e Saneago), ativável e extensível por tenant sem código | nenhum nacional documentado | GIS-02 |
| 4 | Importação de as-built no formato das companhias, com perfis "Corsan Anexo A" e "Saneago" e relatório de qualidade ISO 19157 | só por serviço | GIS-07, GIS-18 |
| 5 | QGIS multitenant com edição por views que passam pelo pacote e pela validação | Giswater não é multitenant | GIS-10 |
| 6 | Pacotes revisados por outra pessoa, ciclo de vida e "rede na data" | GEOSAN sem versionamento; Esri por ramo | GIS-04 |
| 7 | Qualidade contínua: validação a cada gravação por área suja, não em lote noturno | ValidaBase do GEOSAN é posterior | GIS-03 |
| 8 | Precisão por feição com meta do tenant e destaque do que está fora dela | não documentado | GIS-01, GIS-08 |
| 9 | Drenagem e resíduos sólidos no mesmo modelo | nenhum nacional cobre os dois com GIS | GIS-13, GIS-14 |
| 10 | EPANET e SWMM gerados da topologia, com demanda vinda do consumo faturado | GEOSAN só EPANET; comerciais não documentam | GIS-12 |
| 11 | Devolução da base em formatos abertos ao fim do contrato | não declarado | GIS-16 |
| 12 | Dicionário de dados publicado (gerado do schema, ED-09) | nenhum publica | GIS-01, ED-09 |

---

## 2. Diagnóstico do modelo atual e plano de migração

### 2.1 O que existe

| Artefato | Conteúdo |
|---|---|
| `V2_3__create_gis_schema.sql` | `gis_network_element` (40 tipos por `CHECK`, `geometry(Geometry, 4674)`, `material` texto, `diameter_mm`, `length_m`, `age_years`, `status` texto, `properties` JSONB livre, FK para `dma_district`); tabelas de simulação EPANET (`epanet_simulation`, `epanet_node_result`, `epanet_link_result`, `hydraulic_indicators`, `scenario_analysis`); `register_tenant_table` em todas |
| `GisNetworkElement.java` | entidade JPA com `ElementType` de 40 valores, `properties` como `String` JSON, `element_code` com `unique = true` |
| `NetworkTopologyBuilder.java` | monta a rede EPANET por proximidade: snap das pontas ao nó mais próximo com tolerância em graus (0,00001°, cerca de 1,1 m), válvulas e bombas posicionadas sobre a tubulação mais próxima, sem valor inventado (cota, carga, potência, regulagem ausentes viram problema) |
| `InpWriter.java` | escritor `.inp` (L/s, Hazen-Williams, `[END]`), que recusa modelo com problema |
| `EpanetSimulationService.java` | lê elementos por DMA, converte tipo em papel hidráulico, gera a rede; sem solver |
| `dma_district` (`V2_2`, módulo losses) | polígono do distrito e balanço hídrico |

### 2.2 Problemas (19d §1, conferidos no código)

1. **Tipo e subtipo misturados.** `CHECK_VALVE` e `PRESSURE_REDUCING_VALVE` são irmãos de `PIPE`; `TEE`, `CAP` e
   `REDUCER` são "comuns". Tipo novo exige migration; não há ângulo de curva, DN secundário nem classe.
2. **Sem catálogo.** Material em texto livre; `diameter_mm` não distingue DN de diâmetro interno; faltam classe de
   pressão, junta, fabricante, norma, rugosidade e vida útil.
3. **Topologia implícita.** A conectividade é recalculada por proximidade a cada exportação (`nearestNode`, varredura
   de todos os nós por ponta), com tolerância em graus, que varia com a latitude. Sem `node_1`/`node_2` gravados não há
   validação, traçado nem isolamento.
4. **Sem ciclo de vida nem histórico.** `status` é texto com padrão `ACTIVE`; `age_years` é armazenado e envelhece
   errado; não há projetado, em obra, abandonado, vigência nem histórico.
5. **Geometria fraca.** Tipo misto, sem Z, sem exatidão nem método de captura, sem coluna para tiles.
6. **Unicidade errada.** `element_code` é `UNIQUE` global na tabela e na entidade, além de `(tenant_id, element_code)`;
   no schema compartilhado (ADR-0005) dois tenants não podem usar o mesmo código.
7. **Sem vínculo comercial.** Não há ramal ligado à ligação; a ligação nem é entidade (18d, linha 17).
8. **Hidrômetro como feição.** `METER` duplica o parque de `metering` (`meters`, `meter_installations`); o hidrômetro
   pertence à ligação.
9. **Metadados de módulo desatualizados.** O `pom.xml` descreve "QGIS Server" e "EPANET WASM", que não existem e
   contrariam o ADR-0010; a entidade declara índice B-tree sobre a coluna de geometria (`idx_gis_elem_coords`), que a
   migration não cria.

### 2.3 Plano de migração

Não há dado real em nenhuma base (ADR-0033, item 5); o que existe em `gis_network_element` é sintético. A migração
preserva o escritor EPANET e os testes até a topologia persistida assumir o lugar do snap.

| Passo | O que faz | Entrega | Critério |
|---|---|---|---|
| M1 | Cria as tabelas novas (`V13_1`, `V13_2`) sem tocar no `V2_3` já aplicado | GIS-01 | migrations aplicadas em tenant novo e em tenant existente |
| M2 | Renomeia `gis_network_element` para `gis_network_element_legacy` e converte as linhas: tipo antigo → papel, classe, tipo e item de catálogo pela tabela do §2.4; `properties` numéricas conhecidas (`elevation_m`, `head_m`, `setting`, `power_kw`, `diameter_m`, níveis) → colunas tipadas; o restante → `attributes`, com `schema_version` do tipo; `capture_method = CAD_IMPORT` e precisão desconhecida | GIS-01 | 100% das linhas convertidas ou listadas no relatório de não mapeadas |
| M3 | Cria a view `gis_network_element` sobre `gis_node`, `gis_arc`, `gis_structure` e `gis_connection`, com as colunas que a entidade JPA lê, para que `EpanetSimulationService` continue funcionando só em leitura | GIS-01 | `.inp` da massa de referência igual ao anterior |
| M4 | `NetworkTopologyBuilder` passa a receber `node_1_id`/`node_2_id`: deixa de procurar o nó mais próximo; válvulas e bombas, que passam a ser nós, continuam virando ligações curtas no `.inp`; o snap por proximidade fica só como diagnóstico de dado legado | GIS-03 | testes do builder refeitos sobre topologia persistida; `.inp` igual onde a topologia antiga era correta |
| M5 | Serviço de exportação EPANET lê diretamente `gis_node`/`gis_arc` por zona, estado e data; a entidade `GisNetworkElement` e a view saem na release seguinte à que deixou de usá-las (expand/contract, plano §3) | GIS-12 | nenhum uso da view (teste de arquitetura) |
| M6 | `gis_network_element_legacy` é removida depois de M5; `dma_district` continua no módulo losses, ligada a `gis_zone` do tipo `DMA` (§3.6.5) | GIS-12 | tabela ausente; balanço por DMA inalterado |

### 2.4 Mapeamento dos 40 tipos atuais

| Tipo atual | Papel | Classe | Tipo novo |
|---|---|---|---|
| `PIPE` | ARC | `PIPE` | `WATER_MAIN` (função `DISTRIBUTION` por padrão; `TRANSMISSION` quando o atributo indicar adutora) |
| `VALVE` | NODE | `VALVE` | `GATE_VALVE` (padrão; revisar no pacote de migração) |
| `PRESSURE_REDUCING_VALVE` | NODE | `VALVE` | `PRV` |
| `AIR_VALVE` | NODE | `VALVE` | `AIR_VALVE` |
| `BLOWOFF_VALVE` | NODE | `VALVE` | `BLOWOFF` |
| `CHECK_VALVE` | NODE | `VALVE` | `CHECK_VALVE` |
| `HYDRANT` | NODE | `HYDRANT` | `HYDRANT_PILLAR` (revisar) |
| `METER` | — | — | sem destino no GIS: hidrômetro é da ligação (`metering`); posição pela ligação ou pelo imóvel |
| `MACRO_METER` | NODE | `METER_STATION` | `MACRO_METER` |
| `RESERVOIR` | STRUCTURE | `STORAGE` | `RESERVOIR` (com nó hidráulico `TANK`) |
| `TANK` | STRUCTURE | `STORAGE` | `ELEVATED_TANK` (com nó hidráulico `TANK`) |
| `PUMP_STATION` | STRUCTURE | `PUMP_STATION` | `WATER_PUMP_STATION` (com nó `PUMP` contido) |
| `TREATMENT_PLANT` | STRUCTURE | `TREATMENT_PLANT` | `WTP` |
| `WELL` | STRUCTURE | `SOURCE` | `WELL` (com nó hidráulico `SOURCE`) |
| `SPRING` | STRUCTURE | `SOURCE` | `SPRING` |
| `INTAKE` | STRUCTURE | `SOURCE` | `SURFACE_INTAKE` |
| `CONNECTION` | CONNECTION | `SERVICE_LINE` | `WATER_SERVICE_LINE` (ligação vinculada pela GIS-05) |
| `SERVICE_LINE` | CONNECTION | `SERVICE_LINE` | `WATER_SERVICE_LINE` |
| `SEWER_PIPE` | ARC | `PIPE` | `GRAVITY_SEWER` (ou `FORCE_MAIN` se pressurizado) |
| `MANHOLE` | NODE | `MANHOLE` | `MANHOLE` |
| `SEWER_VALVE` | NODE | `VALVE` | `GATE_VALVE` (domínio esgoto) |
| `LIFT_STATION` | STRUCTURE | `PUMP_STATION` | `SEWAGE_LIFT_STATION` |
| `SEWER_TREATMENT_PLANT` | STRUCTURE | `TREATMENT_PLANT` | `WWTP` |
| `SEWER_CONNECTION` | CONNECTION | `SERVICE_LINE` | `SEWER_SERVICE_LINE` |
| `GREASE_TRAP` | NODE | `BOX` | `GREASE_TRAP` (predial; inativo por padrão) |
| `SIPHON` | ARC | `PIPE` | `INVERTED_SIPHON` |
| `OVERFLOW_WEIR` | NODE | `OUTFALL` | `OVERFLOW` |
| `NODE`, `JUNCTION` | NODE | `JUNCTION` | `JUNCTION` |
| `REDUCER` | NODE | `FITTING` | `REDUCER` |
| `TEE` | NODE | `FITTING` | `TEE` |
| `CROSS` | NODE | `FITTING` | `CROSS` |
| `CAP` | NODE | `FITTING` | `CAP` |
| `FLANGE` | NODE | `FITTING` | `FLANGE_ADAPTER` |
| `COUPLING` | NODE | `FITTING` | `COUPLING` |
| `SUPPORT`, `ANCHOR`, `THRUST_BLOCK` | STRUCTURE | `ATTACHMENT` | `PIPE_SUPPORT`, `ANCHOR_BLOCK`, `THRUST_BLOCK` (anexo estrutural, relação `ATTACHES`) |
| `CATHODIC_PROTECTION` | STRUCTURE | `ATTACHMENT` | `CATHODIC_PROTECTION` |
| `MONITORING_POINT` | NODE | `MONITORING` | `PRESSURE_POINT` (ou `QUALITY_POINT`, revisar) |

---

## 3. Modelo de dados proposto

### 3.1 Princípios

1. **Papel topológico no código, tudo o mais em dado.** O papel (`NODE`, `ARC`, `CONNECTION`, `STRUCTURE`, `ZONE`,
   `POI`) é enum fixo, porque decide tabela, geometria e regras de topologia. Classe, tipo, item de catálogo, listas
   de valores e regras de conectividade são linhas, ativáveis e extensíveis por tenant sem código nem migration
   (ADR-0034, decisões 1 e 2).
2. **Uma regra, um lugar: o banco.** Topologia, validação do JSON Schema, histórico, trava por área, área suja e tile
   sujo são gatilhos, porque há escrita fora da `api` (QGIS por views, importação em lote). A `api` repete a validação
   para dar mensagem boa ao usuário, nunca a substitui.
3. **Nada entra na rede em operação sem pacote revisado por outra pessoa** (ADR-0034, decisão 3).
4. **Precisão é dado.** Toda feição diz como foi capturada, com que exatidão, quando, por quem e de onde veio
   (decisão 5).
5. **Armazenar em SIRGAS 2000 geográfico, calcular em metros.** Comprimento e área por `geography` ou na UTM de
   trabalho do tenant; nunca `ST_Length` sobre 4674 (19c §3.1).
6. **Idade, declividade e comprimento são calculados.** Nada que envelhece é armazenado.

### 3.2 Classificação em três níveis e catálogo

| Nível | Tabela | Origem | Exemplo |
|---|---|---|---|
| Papel topológico | enum `topology_role` no código | fixo | `NODE` |
| Classe | `gis_feature_class` | fábrica; o tenant ativa ou desativa | `VALVE` (domínio `WATER`) |
| Tipo | `gis_feature_type` | fábrica e tenant | `GATE_VALVE`, `PRV` |
| Item de catálogo | `gis_catalog_item` | fábrica e tenant | registro de gaveta DN 100, ferro fundido dúctil, flangeado PN 10 |

O item de catálogo fixa a peça física: material, DN principal e secundário, ângulo, classe de pressão ou rigidez,
junta, diâmetros interno e externo, SDR, seção (`geom1` a `geom4`, padrão do SWMM), fabricante e modelo, norma com
edição, rugosidade e vida útil padrão. O que varia por exemplar (profundidade, cotas, data, estado, posição) fica na
feição. Feição guarda a chave do item, nunca texto livre de material ou DN.

### 3.3 Atributos tipados e JSONB validado

**Coluna tipada** para o que entra em filtro, simbologia, tile, indicador regulatório, EPANET ou SWMM, validação ou
relatório: DN, material (pelo item), cotas, profundidade, ciclo de vida, estado operacional, datas, metadados de
qualidade, zona. **`attributes` JSONB** para o que é específico de poucos tipos e só aparece no formulário (número de
voltas do registro, tipo de acionamento, lacre, classe do tampão quando não for filtro).

Validação do JSONB:

- `gis_feature_type.attributes_schema` guarda um JSON Schema (draft 2020-12) com `required`, `type`, `enum` e
  `$ref` a listas de `gis_value_list`; `schema_version` incrementa a cada mudança e é gravado na feição, para que a
  mudança do formulário não invalide o histórico;
- a `api` valida em Java antes de gravar e devolve erro por campo (`errors[].code` em inglês);
- o banco valida de novo num gatilho `BEFORE INSERT OR UPDATE`: com `pg_jsonschema` (`jsonb_matches_schema`), se a
  extensão estiver disponível no RDS; senão, função PL/pgSQL que confere chaves obrigatórias, tipos e valores de lista
  (a confirmar na P0.26 e na LC.1);
- o JSON Schema também gera o formulário da web, do app de campo e do projeto QGIS (widgets de lista de valores).

### 3.4 Tabelas

**Classificação, catálogo e configuração** (`V13_1`; catálogo de fábrica em `R__gis_factory_catalog.sql`)

| Tabela | Conteúdo |
|---|---|
| `gis_feature_class` | `code`, `domain` (chave de `gis_domain`, v2.13; no pacote de saneamento: `WATER`, `SEWER`, `STORM`, `WASTE`, `CONTEXT`), `topology_role`, `geometry_type`, `is_isolating`, `hydraulic_role` (`EPANET` e `SWMM`), `origin` (`FACTORY`, `TENANT`), `active` |
| `gis_feature_type` | `class_id`, `code`, `label`, `attributes_schema`, `schema_version`, `terminal_config` (entrada e saída para dispositivos direcionais), `min_zoom`, `style_key`, `origin`, `factory_code`, `active`, `approval_status` |
| `gis_material` | `code`, `family`, `standard_ref` (norma e edição), `hazen_williams_c`, `manning_n`, `roughness_mm`, `service_life_years`, `legacy_only`, `origin`, `active` |
| `gis_catalog_item` | `feature_type_id`, `material_id`, `dn_mm`, `dn2_mm`, `angle_deg`, `pressure_class`, `stiffness_class`, `joint_type`, `internal_diameter_mm`, `external_diameter_mm`, `sdr`, `section_shape`, `geom1`–`geom4`, `manufacturer`, `model`, `standard_ref`, `reference_status` (`STANDARD`, `REFERENCE_TO_VALIDATE`, `TENANT`), `origin`, `factory_code`, `factory_retired_on`, `active` |
| `gis_value_list` | `list_code`, `code`, `label`, `sort_order`, `origin`, `active` |
| `gis_connectivity_rule` | `from_type_id` (ou classe), `to_type_id` (ou classe), `relation` (`CONNECTS`, `CONTAINS`, `ATTACHES`), `min_arcs`, `max_arcs`, `condition` (JSON: mesmo DN, DN diferente, domínio, função do arco), `severity` |
| `gis_quality_target` | meta de exatidão por classe (`class_id`), `h_target_m`, `v_target_m`, `valid_from`, `valid_to`, aprovação (§3.8) |
| `gis_responsible_party` | responsável técnico do levantamento ou do projeto: nome profissional, registro (CREA, CAU, CFT), ART ou RRT; dado de profissional, com finalidade registrada |

**Feições** (`V13_2`)

| Tabela | Papel | Geometria |
|---|---|---|
| `gis_node` | `NODE` | `geometry(PointZ, 4674)` |
| `gis_arc` | `ARC` | `geometry(LineStringZ, 4674)` |
| `gis_connection` | `CONNECTION` (ramal) | `geometry(LineStringZ, 4674)` |
| `gis_structure` | `STRUCTURE` | `geometry(MultiPolygon, 4674)` ou `geometry(Point, 4674)` em `geom_point`, uma das duas obrigatória |
| `gis_containment` | relação `CONTAINS` e `ATTACHES` | — |
| `gis_zone` | `ZONE` | `geometry(MultiPolygon, 4674)` |
| `gis_poi` | `POI` | `geometry(Point, 4674)` |
| `gis_attachment` | fotos, croquis, documentos, monografias de marco | posição e azimute opcionais |

**Edição, histórico e qualidade** (histórico em `V13_2`, com as feições; validação em `V13_3`; pacotes e travas em
`V13_4`)

| Tabela | Conteúdo |
|---|---|
| `gis_change_set` | pacote: tipo, canal, estado, autor, revisor, OS ou projeto, abrangência (`geometry`), datas, relatório técnico, relatório de qualidade |
| `gis_change_set_item` | operação (`INSERT`, `UPDATE`, `RETIRE`), feição, versão de base, estado proposto (geometria e atributos), resultado da validação |
| `gis_change_set_review` | decisão (`APPROVED`, `RETURNED`, `REJECTED`), revisor, motivo, data |
| `gis_edit_lock` | trava por área: `geom`, `change_set_id`, dono, `expires_at` |
| `gis_node_history`, `gis_arc_history`, `gis_connection_history`, `gis_structure_history`, `gis_zone_history`, `gis_poi_history` | versão anterior completa, `valid_period tstzrange`, autor, role do banco, canal, `change_set_id` |
| `gis_dirty_area` | áreas a validar depois de gravação |
| `gis_validation_issue` | problema encontrado: regra, severidade, feições, área, estado (aberto, aceito com justificativa, resolvido) |

**Publicação, importação e operação**

| Tabela | Conteúdo | Migration |
|---|---|---|
| `gis_domain` | domínio da vertical: `key`, `vertical_id`, `label`, `map_color_token`, `sort_order` (ADR-0037, v2.13) | `V13_1` |
| `gis_layer`, `gis_style`, `gis_basemap`, `gis_wms_source` | camadas publicadas, estilo padrão e do tenant, mapas base e fontes WMS | `V13_6` |
| `gis_tile_dirty` | tiles a invalidar por camada e zoom | `V13_6` |
| `gis_import_batch`, `gis_import_raw`, `gis_import_profile`, `gis_cad_layer_map`, `gis_quality_report` | importação (§9) | `V13_7` |
| `gis_thematic_map` | definição de temático: fonte, campo, classes, cores, filtros, regra de agregação | `V13_8` |
| `gis_minsector`, `gis_isolation_plan` | setores de manobra pré-calculados e planos de manobra | trilha (GIS-11) |
| `gis_hydraulic_mapping` | tipo → elemento EPANET ou SWMM por tenant | trilha (GIS-12) |
| `gis_waste_route`, `gis_waste_route_stop` | rotas de coleta (objeto operacional, não feição de cadastro) | trilha (GIS-14) |
| `gis_field_package` | pacote offline por área para o app de campo | trilha (GIS-15) |

Todas as tabelas de tenant passam por `register_tenant_table` (RLS, `version`, auditoria), com exceção das tabelas de
histórico e de `gis_import_raw`, que não herdam de `BaseEntity` e não têm auditoria por linha, como as séries do
`V2_3`.

### 3.5 Colunas comuns às feições

| Coluna | Tipo | Regra |
|---|---|---|
| `id` | UUIDv7 | padrão no banco, para o QGIS não depender de sequência |
| `tenant_id` | UUID | RLS |
| `code` | texto | único por `(tenant_id, code)`, nunca global |
| `feature_type_id`, `catalog_item_id` | FK | o item precisa ser do tipo; item obrigatório nos tipos que o exigem |
| `name` | texto | opcional |
| `lifecycle_status` | `PLANNED`, `UNDER_CONSTRUCTION`, `IN_SERVICE`, `ABANDONED` | §3.7 |
| `retired_reason` | `ABANDONED_IN_PLACE`, `REMOVED` | só com `ABANDONED` |
| `operational_status` | lista por classe (válvula: `OPEN`, `CLOSED`, `PARTIAL`, `INOPERABLE`, `UNKNOWN`) | estado do momento, distinto do ciclo de vida |
| `normal_status` | idem | posição normal, para isolamento e EPANET |
| `condition_rating` | lista (`GOOD`, `FAIR`, `POOR`, `CRITICAL`, `UNKNOWN`) | NR 12/2025 na drenagem |
| `installed_on`, `installed_on_precision` | data; `DAY`, `MONTH`, `YEAR`, `UNKNOWN` | legado costuma saber só o ano |
| `abandoned_on` | data | tempo do negócio |
| `change_set_id` | FK | pacote que criou ou alterou por último |
| `attributes`, `schema_version` | JSONB, int | §3.3 |
| `capture_method`, `h_accuracy_m`, `v_accuracy_m`, `vertical_datum`, `captured_on`, `responsible_party_id`, `source`, `source_batch_id`, `source_ref`, `legacy_object_id`, `accuracy_status` | §3.8 | obrigatórios conforme o método |
| `asset_codes` | JSONB | códigos MCPSB da NR 16/2026 (LOC, BCP, BRV, NTS-SDS, UP, UAR, AT1–AT5), preenchidos na GIS-19 |
| `geom_web` | geometria 3857 | coluna gerada (`ST_Transform`), ou mantida por gatilho se a função não for aceita em coluna gerada na versão em uso |
| `version`, auditoria | — | `register_tenant_table` |

### 3.6 Tabelas por papel

#### 3.6.1 Nó (`gis_node`)

Cota do terreno (`ground_elevation_m`), da tampa (`rim_elevation_m`) e do fundo (`invert_elevation_m`), profundidade
(calculada quando houver tampa e fundo, informada quando não houver), rotação do símbolo, zona de pressão, DMA e bacia
derivadas (atualizadas pela validação), número de arcos ligados (calculado). Dispositivos direcionais (VRP, retenção,
bomba) usam `terminal_config` do tipo para saber entrada e saída; o sentido é guardado como o arco de entrada.

#### 3.6.2 Arco (`gis_arc`)

`node_1_id` e `node_2_id` (obrigatórios em `IN_SERVICE` e `UNDER_CONSTRUCTION`), `length_m` geodésico calculado,
`declared_length_m` (o do projeto ou do as-built), `upstream_invert_m` e `downstream_invert_m`, `slope` calculada,
`pressurized`, `flow_direction` (`WITH_GEOMETRY`, `AGAINST_GEOMETRY`, `BOTH`, `UNKNOWN`), `network_function`
(lista do §4.6, que alimenta SINISA e NR 16), `depth_m`, `street_side` (par, ímpar, eixo), distâncias ao alinhamento
predial e ao meio-fio, tipo de pavimento (atributos da Corsan, 19b §3.3).

#### 3.6.3 Conexão: ramal (`gis_connection`)

Ramal do arco de distribuição ou do coletor até o cavalete ou a caixa de ligação: `arc_id` (arco onde se liga),
`tap_node_id` (colar de tomada ou junção na rede, quando cadastrado), `service_connection_id` (ligação, §3.11),
ponto final (`METER_SETTING` ou `SEWER_CONNECTION_BOX`), DN, material pelo item, comprimento, profundidade, distâncias
ao alinhamento e ao meio-fio. Uma ligação pode ter mais de um ramal só quando o tipo permitir (ramal com vários
hidrômetros, F10 do 19e, fica pela ligação).

#### 3.6.4 Estrutura (`gis_structure`) e contenção

Unidades operacionais (ETA, ETE, reservatório, elevatória, booster, poço, captação), caixas e abrigos (caixa de
registro, abrigo de macromedidor) e anexos estruturais (bloco de ancoragem, suporte, proteção catódica). Atributos de
capacidade por tipo (volume, vazão nominal, potência, outorga). `gis_containment` registra o que está contido (bomba na
elevatória, registro na caixa) e o que está anexado (bloco no arco). O nó hidráulico de uma estrutura (tanque,
reservatório de nível fixo, bomba) é um `gis_node` contido, que o EPANET usa.

#### 3.6.5 Zona (`gis_zone`)

`zone_type`: `SUPPLY_SYSTEM`, `PRESSURE_ZONE`, `DMA`, `MANEUVER_SECTOR`, `SEWER_BASIN`, `SEWER_SUBBASIN`,
`STORM_BASIN`, `STORM_SUBCATCHMENT`, `COLLECTION_SECTOR`, `COMMERCIAL_SECTOR`, `OPERATIONAL_SECTOR`. `derivation`:
`DRAWN` ou `TRACED` (zona calculada por traçado a partir de controladores e delimitadores, GIS-11). Para `DMA`,
`dma_district_id` aponta para o `dma_district` do módulo losses, que continua dono do balanço; o polígono passa a ser
mantido em `gis_zone` e sincronizado. Para `COMMERCIAL_SECTOR`, `commercial_sector_id` aponta para o setor do cadastro
(18d, linha 285).

#### 3.6.6 Ponto de interesse (`gis_poi`)

Ponto sem rede: contêiner, PEV, ecoponto, lixeira, ponto de coleta, ponto de descarte irregular, ponto de amostragem
de qualidade (ligação com ED-50). Atributos por tipo no JSONB validado.

### 3.7 Ciclo de vida, vigência e histórico

**Ciclo de vida** (decisão 3): `PLANNED` (projetado) → `UNDER_CONSTRUCTION` (em obra) → `IN_SERVICE` (em operação) →
`ABANDONED` (abandonado, com `retired_reason` `ABANDONED_IN_PLACE` ou `REMOVED`). Transições válidas no banco:
`PLANNED → UNDER_CONSTRUCTION → IN_SERVICE`, `PLANNED → IN_SERVICE` (as-built aprovado sem registro de obra),
`IN_SERVICE → ABANDONED`, `PLANNED → ABANDONED` (projeto cancelado). Feição `PLANNED` ou `UNDER_CONSTRUCTION` fica fora
de traçado, isolamento, indicadores e da exportação EPANET padrão, mas entra em cenário de projeto (GIS-12).

**Dois tempos** (19d §5.2):

- **tempo do negócio**: `installed_on` e `abandoned_on`, a vida do ativo;
- **tempo do sistema**: quando o cadastro mudou. Um gatilho `AFTER UPDATE OR DELETE` grava a versão anterior em
  `gis_*_history` com `valid_period tstzrange`, autor, role do banco, canal e pacote. Não sobreposição por
  `EXCLUDE USING gist (id WITH =, valid_period WITH &&)` com `btree_gist`, porque `WITHOUT OVERLAPS` só existe a partir
  do PostgreSQL 18 (ADR-0013 fixa o 17).

**"Rede na data".** Duas consultas, expostas pela `api` e usadas por relatórios e exportações:

- `GET /api/v1/gis/network-at?date=AAAA-MM-DD`: rede em operação naquela data pelo tempo do negócio
  (`installed_on ≤ data` e (`abandoned_on` nula ou `> data`)), com os atributos vigentes na data pelo histórico;
- `GET /api/v1/gis/network-at?recorded-at=...`: o cadastro como estava registrado naquele instante (auditoria,
  conferência com relatório já enviado, por exemplo o SINISA de um ano-base).

Feição nunca é apagada fisicamente; `DELETE` só é aceito em feição `PLANNED` de pacote não aprovado.

### 3.8 Metadados de captura e qualidade

Por feição (decisão 5; ISO 19157-1:2023 para os elementos de qualidade):

| Coluna | Domínio |
|---|---|
| `capture_method` | `RTK_FIXED`, `RTK_FLOAT`, `TOTAL_STATION`, `GNSS_NAVIGATION`, `TAPE_TIE` (amarração a trena), `DIGITIZED` (digitalização sobre imagem ou prancha), `CAD_IMPORT`, `AUTO_SERVICE_LINE` (ramal gerado automaticamente), `DESIGN` (projeto), `ESTIMATED` |
| `h_accuracy_m`, `v_accuracy_m` | exatidão estimada (declarada pelo levantamento ou atribuída pelo método) |
| `vertical_datum` | `IMBITUBA`, `SANTANA`, `ELLIPSOIDAL` (só em trânsito, antes da conversão), `UNKNOWN` |
| `captured_on` | data do levantamento ou da digitalização |
| `responsible_party_id` | responsável técnico (ART ou RRT) |
| `source` | `PROJECT`, `AS_BUILT`, `SERVICE_ORDER`, `FIELD`, `IMPORT`, `DIGITIZATION`, `AUTO` |
| `source_batch_id`, `source_ref`, `legacy_object_id` | lote de importação, referência na origem (por exemplo o `EntityHandle` do DXF), identificador no sistema anterior |
| `accuracy_status` | `WITHIN_TARGET`, `OUT_OF_TARGET`, `APPROXIMATE`, `UNKNOWN`, gravado por gatilho e recalculado pelo `worker` quando a meta muda |

Regra do `accuracy_status`: métodos `DIGITIZED`, `CAD_IMPORT` sem exatidão declarada, `AUTO_SERVICE_LINE`, `DESIGN` e
`ESTIMATED` dão `APPROXIMATE` (cadastro aproximado); levantamento com exatidão declarada dá `WITHIN_TARGET` ou
`OUT_OF_TARGET` contra a meta vigente da classe; sem exatidão nem método conhecido, `UNKNOWN`. O mapa e os relatórios
distinguem cadastro levantado de cadastro aproximado (GIS-08).

**Meta por tenant e por classe.** Padrão de fábrica para toda classe com posição de rede: **±0,10 m na horizontal e
±0,05 m na vertical** (decisão 5, igual à prática da Corsan, 19b §3.3); vertical "não se aplica" nas classes sem cota
(POI de resíduos, por exemplo). O tenant altera a meta por classe, com vigência e registro de quem alterou e de quem
aprovou (quem propõe não aprova, como nos parâmetros do ADR-0002). Valores de referência para quem quiser afrouxar
classes de menor criticidade, sem valor normativo (19c §3.2): ramal e cavalete ±0,50 m; unidade operacional ±1,0 m no
centroide; ecoponto, PEV e ponto de coleta ±2,0 m; legado digitalizado de prancha de 1 a 5 m (estimado).

**Por conjunto de dados** (perfil MGB 2.0 / ISO 19115-1): metadado gerado na exportação (GIS-16) com título,
responsável, extensão, SRS, datum vertical, exatidão nominal, data de referência, linhagem, restrições de acesso (LGPD
e infraestrutura crítica) e frequência de atualização.

### 3.9 Sistemas de referência

| Uso | Referência |
|---|---|
| Armazenamento | SIRGAS 2000 geográfico, **EPSG 4674**, em todas as tabelas (já adotado no `V2_3`) |
| Trabalho métrico por tenant (`gis.work_srid`) | SIRGAS 2000 / UTM, **faixa completa**: fusos 18S a 25S (EPSG 31978 a 31985), fusos 18N a 22N (EPSG 31972 a 31976, para AM, RR, AP e o norte do PA) e 26S (EPSG 5396, Trindade e Martim Vaz; conferir no `spatial_ref_sys` da versão em uso). Município que cruza fuso usa o fuso da sede, com a escolha registrada |
| Tiles | Web Mercator (EPSG 3857), só em `geom_web` |
| Intercâmbio | GeoJSON (RFC 7946) e KML em WGS-84; SINISA em graus decimais com 7 casas (a diferença SIRGAS 2000 × WGS-84 é desprezível para essa finalidade) |
| Altimetria | altitude normal (REALT-2018) no Datum de Imbituba, ou de Santana no Amapá; altitude elipsoidal do GNSS convertida pelo modelo hgeoHNOR2020 do IBGE **no servidor**, antes de calcular declividade, com método registrado |
| Legado | SAD69 e Córrego Alegre convertidos com as grades oficiais do IBGE; arquivo sem SRS declarado é recusado ou exige escolha explícita, registrada no lote; coordenada local ajustada por pontos de controle, com resíduo registrado |

Cota de fundo de PV e de tubo é atributo, não só o Z da geometria (19c §3.1); o Z guarda a altitude do ponto
levantado.

### 3.10 Parâmetros do tenant

Entram no catálogo de parâmetros (ADR-0002, core `V9_6`) e em `PARAMETROS-DO-TENANT.md` na P1.26;
`gis.public_aggregation_min_units`, com o piso decidido na v2.11, já está lá.

| Chave | Descrição | Tipo | Sugestão | Limite | Cl. |
|---|---|---|---|---|---|
| `gis.work_srid` | SRID UTM de trabalho | enum §3.9 | fuso da sede | lista do §3.9 | d |
| `gis.vertical_datum` | datum vertical do tenant | enum | Imbituba (Santana no AP) | — | d |
| `gis.snap_tolerance_m` | tolerância de snap na edição | m | 0,10 (valor de referência a validar) | 0,01 a 1,00 (valor de referência a validar) | b |
| `gis.import_snap_tolerance_m` | tolerância padrão de snap na importação em lote | m | 0,50 (valor de referência a validar) | 0,01 a 5,00; o lote pode reduzir | b |
| `gis.service_line_max_distance_m` | distância máxima do ramal automático | m | 100 (contrato de Nova Trento, 19a §2.2) | 1 a 300 | b |
| `gis.accuracy_targets` | meta de exatidão por classe | tabela | ±0,10 m H e ±0,05 m V (decisão 5) | — | b |
| `gis.basemap_provider` | mapa base padrão | enum | OpenStreetMap | Google e Bing só com licença no contrato | d |
| `gis.style_profile` | perfil de simbologia | enum | padrão Ziip | override por tipo e estado | b |
| `gis.public_aggregation_min_units` | mínimo de imóveis por célula (quadra ou setor) em publicação fora do backoffice autenticado (§12, item 3) | int | 5 | piso 5, decidido pelo dono do produto (v2.11); só aumenta | c |

Travado (classe a): revisão por outra pessoa antes de entrar na rede em operação (decisão 3); autor não aprova o
próprio pacote; feição em operação não é apagada; fora do backoffice autenticado, imóvel e ligação nunca publicados por
ponto, só agregados por quadra ou setor (§12, item 3; v2.11).

### 3.11 Vínculo com o comercial: ligação

A ligação ainda não é entidade (18d, linha 17; ED-13). O vínculo da decisão 4 (feição ↔ imóvel ↔ ligação ↔ ramal) exige
uma base mínima antes do P4, confirmada pelo dono do produto na v2.11. A GIS-05 cria em `commercial/sigsan-registry`
(`V3_7`) a tabela `service_connections`:
ligação de água ou de esgoto (`kind`), 1:1 com o imóvel por tipo enquanto ativa, com situação pelos catálogos de
situação de água e de esgoto que o cadastro já tem, datas e código. A ED-13 continua na onda A com o restante (catálogos
técnicos, consumo mínimo e situação lidos da ligação no ciclo, alteração por OS); até lá, o faturamento continua lendo o
imóvel, sem mudança na P2.5. O hidrômetro continua em `metering` e se liga à ligação; o mapa o mostra por junção.

---

## 4. Catálogo inicial de fábrica

### 4.1 Regras do catálogo

- **Entrega.** O catálogo de fábrica é carregado em cada schema de tenant por migration repetível
  (`R__gis_factory_catalog.sql`), por *upsert* pela chave estável `factory_code`, com `origin = 'FACTORY'`. A
  reaplicação nunca altera `active` definido pelo tenant nem itens do tenant; item retirado da fábrica recebe
  `factory_retired_on` e continua válido nas feições existentes. Migration repetível não entra na regra de versão
  crescente do plano §3.
- **Tenant.** Ativa ou desativa classe, tipo, item e valor de lista; cria tipo (com JSON Schema montado por formulário),
  item e valor de lista próprios, com `origin = 'TENANT'`, sem código nem migration. Tipo ou item novo do tenant fica
  pendente até aprovação por outro usuário com `gis.catalog.manage`.
- **Norma com edição.** Cada material e item cita a norma e a edição (`standard_ref`); a mudança de edição (como a NBR
  7675 de 08/2026) gera item novo, e o anterior fica para o legado.
- **Valores a validar.** Item sem norma conferida entra com `reference_status = REFERENCE_TO_VALIDATE` e aparece em
  relatório próprio. Nenhum item marcado assim é padrão de fábrica ativo antes da validação, que cabe à curadoria do
  catálogo (GIS-02) com as normas compradas.
- **Normas compradas (v2.11).** Compra no início do P1, antes do catálogo (plano P1.28, custo a estimar): NBR 12586 e
  12587 (cadastro de água e de esgoto), 12218 (rede de distribuição), 9649 (rede coletora), 5647, 7665 e 7675 (tubos de
  PVC e de ferro fundido dúctil) e 15561 (PE), mais as demais citadas neste §4: 17287, 15750, 15802, 5667-1 a -3,
  12208, 12209, 12212, 12213, 12214, 12216, 12217, 12244, 7362, 7367, 14486, NBR ISO 21138-1 e -3, 8890, 9648, 10160,
  17076, 10004-1 e 10004-2 (série de 2024). Edição conferida no ABNT Catálogo antes da compra; arquivos fora do repositório, conforme a licença de
  uso; norma não comprada deixa os itens dela marcados.
- **Perfis de prestadora.** Os padrões Corsan/Aegea (Anexo A, rev. 03/2024) e Saneago (IN 00.0229, rev. 04 de 11/03/2024, e modelo
  Giswater) entram como perfis de importação e exportação (mapeamento de camadas e atributos, §9.2) e como conjuntos de
  itens ativáveis, não como catálogo paralelo.

Nas tabelas abaixo, "Fonte" traz a norma (19c) ou a marca **valor de referência a validar**.

### 4.2 Água

#### 4.2.1 Materiais e tubos

| Material (`code`) | Norma | DN ou DE comerciais | Classe | Juntas | C de Hazen-Williams padrão | Fonte dos valores |
|---|---|---|---|---|---|---|
| PVC-U PBA (`PVC_PBA`) | NBR 5647-1:2025 | DN 50, 75, 100 (DE 60, 85, 110) | classes 12, 15 e 20 | elástica, soldável | 140 | norma para material e classes; DE e C: valor de referência a validar |
| PVC-M DEFOFO (`PVC_DEFOFO`) | NBR 7665:2025 | DN 100 a 500 (DE do ferro fundido) | 1,0 MPa | elástica | 140 | norma para o material; DN, classe e C: valor de referência a validar |
| PVC-O (`PVC_O`) | NBR 15750:2024 | DN 100 a 500 | classes 1,25; 1,60; 2,00; 2,50 MPa | elástica | 145 | norma para o material; faixas e C: valor de referência a validar |
| PE 80 e PE 100 (`PE80`, `PE100`) | NBR 15561:2024; projeto NBR 15802:2010 | DE 20, 25, 32 (ramal); DE 63 a 500 (rede) | SDR 11, 13,6, 17, 21, 26 (PE 100: SDR 11 = PN 16, SDR 17 = PN 10, SDR 21 = PN 8, SDR 26 = PN 6,3) | eletrofusão, termofusão de topo, compressão | 150 | norma para material; DE, SDR × PN e C: valor de referência a validar |
| Ferro fundido dúctil (`DUCTILE_IRON`) | NBR 7675:2026 | DN 80 a 1200 | K7, K9; classes de pressão da edição vigente | elástica (ponta e bolsa), travada interna, mecânica, flangeada PN 10, 16, 25 | 130 (revestido de argamassa) | norma para material; classes da edição 2026, DN e C: valor de referência a validar |
| Aço carbono (`STEEL`) | a confirmar | DN 150 a 1500 (adutoras e travessias) | por espessura | soldada, flangeada | 120 | valor de referência a validar |
| PRFV (`GRP`) | a confirmar | DN 300 a 1500 | PN 6 a 16 | elástica | 145 | valor de referência a validar |
| Fibrocimento (`ASBESTOS_CEMENT`, só legado) | sem norma para instalação nova | DN 50 a 500 | classes 10, 15, 20 | elástica, Gibault | 120 | valor de referência a validar; `legacy_only` (feição nova recusada) |
| Ferro fundido cinzento (`CAST_IRON`, só legado) | — | DN 50 a 600 | — | chumbo, elástica | 100 | valor de referência a validar; `legacy_only` |
| Ferro galvanizado (`GALVANIZED_STEEL`, só legado) | — | ½" a 2" (ramais) | — | rosca | 120 | valor de referência a validar; `legacy_only` |

Série de DN da rede de água: 50, 75, 100, 150, 200, 250, 300, 350, 400, 450, 500, 600, 700, 800, 900, 1000, 1200, com
as combinações válidas por material no item de catálogo (valor de referência a validar). Função do arco
(`network_function`): rede de distribuição, adutora de água bruta, adutora de água tratada, subadutora, interligação,
linha de recalque, ramal de hidrante.

#### 4.2.2 Conexões

| Tipo (`code`) | Variáveis do item | Domínio | Grau na topologia | Fonte |
|---|---|---|---|---|
| Curva (`BEND`) | DN, ângulo, extremidades (ponta-bolsa, bolsa-bolsa, flangeada), material, junta | ângulo na série comercial: 11°15' (11,25), 22°30' (22,5), 45 e 90 graus | 2, mesmo DN | série: prática comercial, valor de referência a validar; deflexão maior em junta elástica é atributo do arco |
| Tê (`TEE`) | DN × DN2 (tê de redução quando DN2 < DN), extremidades, material | DN2 ≤ DN | 3 | — |
| Cruzeta (`CROSS`) | DN × DN2 | DN2 ≤ DN | 4 | — |
| Redução (`REDUCER`) | DN → DN2, concêntrica ou excêntrica | DN2 < DN | 2, DN diferentes | — |
| Luva (`COUPLING`) | DN, tipo: de correr, de reparo, de transição (material A → B), junta Gibault | — | 2 | — |
| Junta de desmontagem (`DISMANTLING_JOINT`) | DN, PN | PN 10, 16, 25 | 2 | — |
| Cap (`CAP`) e tampão (`PLUG`) | DN; cap na ponta, tampão na bolsa | — | 1 | — |
| Flange cego (`BLIND_FLANGE`) | DN, PN | PN 10, 16, 25 | 1 | — |
| Adaptador e toco flangeado (`FLANGE_ADAPTER`) | DN, flange-ponta, flange-bolsa, toco | PN 10, 16, 25 | 2 | — |
| Junção (`WYE`) | DN × DN2, ângulo 45 graus | — | 3 | — |
| Colar de tomada (`SERVICE_SADDLE`) | DN da rede × DN do ramal, material | DN do ramal ≤ 50 | na rede, com o ramal | valor de referência a validar |
| Peça especial (`SPECIAL_PIECE`) | descrição, DN, desenho anexo | — | 2 a 4 | — |
| Nó de junção (`JUNCTION`) | sem peça | — | 2 ou mais | nó topológico gerado; mudança de DN ou de material num `JUNCTION` gera aviso de validação |

#### 4.2.3 Registros, válvulas e hidrantes

| Tipo (`code`) | Isolante | Atributos principais | Domínios | Fonte |
|---|---|---|---|---|
| Registro de gaveta (`GATE_VALVE`) | sim | DN, item (corpo, cunha de borracha ou metálica, extremidades), acionamento, sentido de fechamento, número de voltas, posição normal, estado operacional, profundidade, caixa | acionamento: cabeçote, volante, redutor, atuador elétrico; sentido: horário, anti-horário | Corsan A_VALVULA; voltas por DN: valor de referência a validar |
| Válvula borboleta (`BUTTERFLY_VALVE`) | sim | DN, PN, wafer ou flangeada, acionamento, sentido, posição normal, estado | PN 10, 16, 25 | — |
| Registro de esfera (`BALL_VALVE`) | não (ramal) | DN, material | — | — |
| VRP (`PRV`) | não; controlador de zona de pressão | DN, ação direta ou pilotada, pressão de saída regulada (`setting`), pressões a montante e a jusante, terminais | — | Corsan A_PROTEHIDRAULICAS |
| Válvula sustentadora (`PSV`), redutora-sustentadora (`PRSV`), controladora de vazão (`FCV`), quebra-pressão (`PBV`) | não | DN, `setting`, terminais | tipos do EPANET | — |
| Ventosa (`AIR_VALVE`) | não | DN, função (simples, dupla, tríplice), registro de bloqueio, caixa | DN 50, 80, 100 | valor de referência a validar |
| Descarga (`BLOWOFF`) | sim | DN, registro, caixa, ponto de lançamento | — | — |
| Válvula de retenção (`CHECK_VALVE`) | não; direcional | DN, portinhola simples ou dupla, wafer, terminais | — | — |
| Válvula antigolpe ou de alívio (`SURGE_RELIEF_VALVE`) | não | DN, pressão de abertura | — | Corsan A_PROTEHIDRAULICAS |
| Hidrante de coluna (`HYDRANT_PILLAR`) | não | DN, expedições, registro de manobra, data do último teste, vazão, estado | DN 75, 100, 150 | NBR 5667-1:2006 (conf. 04/2024); DN e expedições: valor de referência a validar |
| Hidrante subterrâneo (`HYDRANT_UNDERGROUND`) | não | idem, caixa | — | NBR 5667-2:2006 (conf. 04/2024) |
| Hidrante com obturação própria (`HYDRANT_SELF_SEALING`) | não | idem | — | NBR 5667-3:2006 (conf. 04/2024) |

Válvula de rede tem `normal_status` e `operational_status` (`OPEN`, `CLOSED`, `PARTIAL` com percentual,
`INOPERABLE`, `UNKNOWN`); `INOPERABLE` faz o isolamento seguir até a próxima válvula (GIS-11). Registro contido em caixa
usa `gis_containment` (`VALVE_CHAMBER`).

#### 4.2.4 Medição e monitoramento

| Tipo (`code`) | Papel | Atributos | Fonte |
|---|---|---|---|
| Macromedidor (`MACRO_METER`) | NODE, controlador de DMA | princípio (eletromagnético, ultrassônico de inserção ou externo, Woltmann, Venturi, Parshall), DN, faixa de vazão, telemetria (ID do ponto na ED-17), abrigo, última aferição | Corsan A_MACROMEDIDOR |
| Ponto de controle de pressão (`PRESSURE_POINT`) | NODE | cota, registrador, telemetria | Corsan A_PCP |
| Ponto de controle de qualidade (`QUALITY_POINT`) | NODE ou POI | ponto de amostragem do plano da ED-50 | Corsan A_PCQ |

#### 4.2.5 Unidades operacionais

| Tipo (`code`) | Papel | Atributos principais | Fonte |
|---|---|---|---|
| Captação superficial (`SURFACE_INTAKE`) | STRUCTURE + nó `SOURCE` | manancial, vazão outorgada, outorga (número e validade), cota | NBR 12213:1992 |
| Poço tubular (`WELL`) | STRUCTURE + nó `SOURCE` | profundidade, vazão outorgada, níveis estático e dinâmico, conjunto motobomba, outorga | NBR 12212 (edição a confirmar), NBR 12244:2006 |
| Nascente (`SPRING`) | STRUCTURE + nó `SOURCE` | vazão, proteção | — |
| ETA (`WTP`) | STRUCTURE | tecnologia (convencional, compacta, filtração direta, simplificada), vazão nominal (L/s), cota | NBR 12216:1992 |
| Reservatório (`RESERVOIR`, `ELEVATED_TANK`) | STRUCTURE + nó `TANK` | tipo (apoiado, enterrado, semienterrado, elevado), volume (m³), cota de fundo, níveis mínimo, máximo e inicial, diâmetro ou área, material | NBR 12217:1994; atributos hidráulicos exigidos pelo EPANET (`NetworkTopologyBuilder`) |
| Elevatória de água (`WATER_PUMP_STATION`) | STRUCTURE + nós `PUMP` | água bruta ou tratada, conjuntos (potência kW, vazão, altura manométrica, curva), gerador, telemetria | NBR 12214:2020; Corsan A_EEAT, A_MOTOBOMBA |
| Booster (`BOOSTER`) | STRUCTURE + nó `PUMP` | idem, em linha | edital SAMAE (SG-860) |
| Caixa de registro (`VALVE_CHAMBER`) e abrigo de macromedidor (`METER_CHAMBER`) | STRUCTURE | dimensões, tampão (classe NBR 10160:2026) | — |
| Anexos (`THRUST_BLOCK`, `ANCHOR_BLOCK`, `PIPE_SUPPORT`, `CATHODIC_PROTECTION`) | STRUCTURE, relação `ATTACHES` | dimensões, data | — |

#### 4.2.6 Ramais e cavaletes

| Tipo (`code`) | Papel | Atributos | Domínios | Fonte |
|---|---|---|---|---|
| Ramal predial de água (`WATER_SERVICE_LINE`) | CONNECTION | DN, material (PE, PVC soldável; legado: ferro galvanizado, cobre), comprimento, profundidade, lado, distâncias ao alinhamento e ao meio-fio, ligação | DN ½" (DE 20), ¾" (DE 25), 1" (DE 32) | valor de referência a validar |
| Registro de passeio (`CURB_STOP`) | NODE | DN, caixa | — | valor de referência a validar |
| Cavalete (`METER_SETTING`) | NODE (ponta do ramal) | tipo (kit PVC, PE, metálico), posição (muro, calçada, caixa de proteção), ligação | — | valor de referência a validar |
| Ramal de hidrante (`HYDRANT_LATERAL`) | ARC | DN, comprimento | — | — |

### 4.3 Esgoto

#### 4.3.1 Materiais e tubos

| Material (`code`) | Norma | DN | Classe ou rigidez | Juntas | n de Manning padrão | Fonte dos valores |
|---|---|---|---|---|---|---|
| PVC parede maciça (`PVC_SOLID_WALL`) | NBR 7362:2025; assentamento NBR 7367:1988; redes em PVC NBR 14486:2000 | DN 100 a 400 | — | elástica | 0,010 | norma para material; DN e n: valor de referência a validar |
| PVC e PE de parede estruturada (`PVC_STRUCTURED`, `HDPE_STRUCTURED`) | NBR ISO 21138-1 e -3:2021 | DN 150 a 1000 | SN 4, SN 8 | elástica | 0,010 | norma para material; DN, SN e n: valor de referência a validar |
| Concreto (`CONCRETE`) | NBR 8890:2020 | DN 300 a 1500 | classes da norma para esgoto, simples e armado | elástica | 0,013 | valor de referência a validar |
| Cerâmica (`VITRIFIED_CLAY`) | a confirmar | DN 100 a 400 | — | elástica, argamassa (legado) | 0,013 | valor de referência a validar |
| Ferro fundido dúctil (`DUCTILE_IRON`) | NBR 7675:2026 | DN 80 a 1200 (recalque) | idem água | idem água | C 130 (recalque, Hazen-Williams) | valor de referência a validar |
| PE 100 (`PE100`) | NBR 15561:2024 | DE 63 a 500 (recalque) | SDR | eletrofusão, topo | C 150 | valor de referência a validar |
| Fibrocimento (`ASBESTOS_CEMENT`, só legado) | — | DN 100 a 400 | — | — | 0,012 | valor de referência a validar; `legacy_only` |

Função do arco: rede coletora, coletor-tronco, interceptor, emissário, linha de recalque, extravasor, sifão invertido,
ramal predial, lododuto. Regras de projeto usadas pela validação (GIS-03), a conferir no texto das normas antes de
virar erro bloqueante: DN mínimo da rede coletora (NBR 9649:1986: DN 100; prestadoras costumam adotar DN 150, valor de
referência a validar), declividade mínima por tensão trativa (NBR 9649: 1,0 Pa; fórmula I_min = 0,0055·Q_i^-0,47 m/m,
a conferir), lâmina máxima de 75% do diâmetro (a conferir) e trecho entre singularidades (NBR 9648 e 9649).

#### 4.3.2 Singularidades, ramais e unidades

| Tipo (`code`) | Papel | Atributos principais | Domínios | Fonte |
|---|---|---|---|---|
| Poço de visita (`MANHOLE`) | NODE | cotas de tampa e de fundo, profundidade, diâmetro interno, material do corpo, tampão (classe de carga), degraus, tubo de queda (altura), estado | classe do tampão: A15, B125, C250, D400, E600, F900; diâmetro interno 1,00 m (valor de referência a validar) | NBR 9649; tampão NBR 10160:2026; Corsan E_POCO_VISITA |
| Tubo de inspeção e limpeza (`TIL`) | NODE | cotas, DN, profundidade | — | NBR 9649; Corsan E_TIL |
| Terminal de limpeza (`TERMINAL_CLEANOUT`) | NODE (início de rede) | cotas, DN | — | NBR 9649 |
| Caixa de passagem (`JUNCTION_CHAMBER`) | NODE | cotas, dimensões | — | NBR 9649 |
| Caixa de inspeção (`INSPECTION_BOX`) | NODE | cotas, dimensões, tampa | — | valor de referência a validar |
| Caixa de ligação (`SEWER_CONNECTION_BOX`) | NODE (ponta do ramal) | cota, dimensões, ligação | — | Corsan E_CX_LIGACAO |
| Caixa de descarga (`FLUSH_CHAMBER`) | NODE | cotas, volume | — | Corsan E_CX_DESCARGA; finalidade a validar no Anexo A |
| Sifão invertido (`INVERTED_SIPHON`) | ARC entre caixas de entrada e saída (`SIPHON_CHAMBER`) | número de tubos, DN, cotas | — | — |
| Ramal predial de esgoto (`SEWER_SERVICE_LINE`) | CONNECTION | DN, material, declividade, comprimento, profundidade, caixa de ligação, ligação | DN 100 (valor de referência a validar) | Corsan E_RAMAL |
| Válvulas do recalque (`GATE_VALVE`, `CHECK_VALVE`, `AIR_VALVE`, domínio esgoto) | NODE | idem água | — | Corsan E_VALVULAS |
| Elevatória de esgoto (`SEWAGE_LIFT_STATION`) | STRUCTURE + nós `PUMP` | volume do poço úmido, conjuntos (potência, vazão, altura), extravasor, gerador, telemetria | — | NBR 12208:2020; Corsan E_EEE, E_MOTOBOMBA |
| ETE (`WWTP`) | STRUCTURE | tecnologia (lodos ativados, UASB, lagoas, filtro biológico, outras), vazão nominal, corpo receptor, licença | — | NBR 12209:2011; NBR 17076:2024 (menor porte); Corsan E_ETE |
| Extravasor (`OVERFLOW`) | NODE + ARC de função extravasor | cota de extravasamento, corpo receptor | — | — |
| Ponto de lançamento (`DISCHARGE_POINT`) | NODE (`OUTFALL`) | corpo receptor, outorga, cota | — | — |
| Bacia e sub-bacia de esgotamento | ZONE `SEWER_BASIN`, `SEWER_SUBBASIN` | área, população, contribuição | — | Corsan E_BACIA, E_SUB_BACIA |

### 4.4 Drenagem

Não há NBR de projeto de microdrenagem pública (19c §2.1): catálogo configurável por tenant, com NR 12/2025 da ANA
exigindo cadastro georreferenciado atualizado com material e estado de conservação. Classes e papéis existem desde a
GIS-01; catálogo, regras e telas na GIS-13 (onda B).

| Tipo (`code`) | Papel | Atributos principais | Domínios | Fonte |
|---|---|---|---|---|
| Galeria tubular (`STORM_PIPE`) | ARC | DN, material, cotas de montante e jusante, declividade, `geom1` | DN 300 a 1500; concreto simples ou armado, PE e PVC estruturados, aço corrugado | NBR 8890:2020; NBR ISO 21138; DN: valor de referência a validar |
| Galeria celular (`BOX_CULVERT`) | ARC | base × altura (`geom1`, `geom2`), número de células, material | 1,0 × 1,0 a 3,0 × 3,0 m | valor de referência a validar |
| Canal (`OPEN_CHANNEL`) | ARC | seção (trapezoidal, retangular, natural), base, altura, talude, revestimento | revestimento: concreto, gabião, enrocamento, grama, terra | valor de referência a validar |
| Bueiro (`CULVERT`) | ARC | tubular ou celular, DN ou seção, alas, cotas | — | DNIT 023/2006-ES (bueiros tubulares de concreto) e DNIT 025/2004-ES (bueiros celulares de concreto) como referência (verificado em 22/10/2026: a DNIT 030/2004-ES trata de dispositivos de drenagem pluvial urbana, não de bueiros; o Busca Normas menciona uma DNIT 023/2024-ES, a confirmar no IPR antes de citar a 023) |
| Ligação boca de lobo–PV (`INLET_LEAD`) | ARC | DN, material, declividade | DN 300 a 400 | valor de referência a validar |
| Sarjeta (`GUTTER`), sarjetão (`VALLEY_GUTTER`), valeta (`DITCH`) | ARC | seção, revestimento | — | valor de referência a validar |
| Boca de lobo (`INLET`) | NODE | tipo (simples, dupla, tripla; com ou sem grelha; guia chapéu ou lateral), dimensões, cota, depressão, capacidade de engolimento | — | SWMM 5.2 (objeto *inlet*); valores: valor de referência a validar |
| Grelha (`GRATE_INLET`) | NODE | dimensões, classe de carga, cota | A15 a F900 | NBR 10160:2026 |
| PV pluvial (`STORM_MANHOLE`) | NODE | cotas de tampa e de fundo, diâmetro, tampão | — | idem esgoto |
| Caixa de ligação ou de passagem (`STORM_JUNCTION_BOX`) | NODE | dimensões, cotas | — | — |
| Poço de queda (`DROP_SHAFT`) | NODE | altura de queda, cotas | — | — |
| Dissipador de energia (`ENERGY_DISSIPATOR`) | NODE ou STRUCTURE | tipo (degraus, bacia de dissipação, enrocamento) | — | — |
| Ala e testa (`HEADWALL`) | STRUCTURE, `ATTACHES` | material, dimensões | — | camada `DRE Ala drenagem` (19a §2.4) |
| Lançamento ou exutório (`OUTFALL`) | NODE | corpo receptor, cota, comporta ou válvula *flap* | — | SWMM *outfall* |
| Bacia de detenção (`DETENTION_BASIN`), de retenção (`RETENTION_BASIN`) | STRUCTURE + nó `STORAGE` | volume, área, cotas de fundo e de vertimento, vertedor, orifício, curva cota × área | — | SWMM *storage* |
| Sub-bacia (`STORM_SUBCATCHMENT`) | ZONE | área, impermeabilidade (%), declividade, largura, parâmetros de infiltração, exutório | — | SWMM *subcatchment* |

Atributos comuns de drenagem (NR 12/2025): `condition_rating`, obstrução ou assoreamento (%), foto, data da última
inspeção, n de Manning do material (concreto 0,013; PE 0,010; canal em terra 0,025; valores de referência a validar).

### 4.5 Resíduos sólidos

Resíduos não é rede de fluxo: contêineres e pontos são POI, setores são ZONE, unidades são STRUCTURE, e as rotas são
objeto operacional sobre o grafo de logradouros (19d §3.3). O vínculo com o imóvel serve à taxa de resíduos (ED-25) e ao
módulo GRS (ADR-0004; MTR do levantamento `12`). Classes e papéis desde a GIS-01; catálogo e telas na GIS-14 (onda B).

| Tipo (`code`) | Papel | Atributos principais | Domínios | Fonte |
|---|---|---|---|---|
| Ponto de coleta (`COLLECTION_POINT`) | POI | tipo de resíduo, frequência, turno, setor | resíduo: rejeito, orgânico, reciclável seco, vidro, misto | — |
| Contêiner (`CONTAINER`) | POI | tipo de resíduo, volume, modelo, material, patrimônio, estado | PE com rodas: 120, 240, 360, 660, 1000 L; caçamba estacionária 3, 4, 5 m³; semienterrado ou subterrâneo 3 a 5 m³ | valor de referência a validar |
| PEV (`DROP_OFF_POINT`) | POI | frações aceitas, logística reversa (pilhas, lâmpadas, eletroeletrônicos, óleo), responsável (prefeitura ou sistema de logística reversa) | — | Lei 12.305/2010; Decreto 10.936/2022 |
| Ecoponto (`ECOPOINT`) | STRUCTURE ou POI | materiais aceitos (RCC de pequeno volume, volumosos, podas, recicláveis), limite por entrega, horário, capacidade | limite de RCC por entrega: 1 m³ (valor de referência a validar; regra municipal) | — |
| Lixeira pública (`LITTER_BIN`) | POI | volume, tipo, estado | — | — |
| Setor de coleta (`COLLECTION_SECTOR`) | ZONE | tipo de coleta (convencional, seletiva, serviços de saúde, varrição), dias, turno, executor | — | NR 7/2024 e NR 14/2025 (indicadores) |
| Rota de coleta (`gis_waste_route`) | objeto operacional | setor, veículo, equipe, sequência, extensão, tempo estimado, quilometragem | veículo: compactador 15 ou 19 m³, baú, poliguindaste (valor de referência a validar) | — |
| Área de transbordo (`TRANSFER_STATION`) | STRUCTURE | capacidade (t/dia), licença ambiental (número e validade) | — | — |
| Unidade de triagem (`SORTING_FACILITY`) | STRUCTURE | operador (cooperativa, associação, empresa), capacidade | — | — |
| Compostagem (`COMPOSTING_SITE`) | STRUCTURE | capacidade, licença | — | — |
| Aterro sanitário (`SANITARY_LANDFILL`) | STRUCTURE | licença, vida útil, células | — | — |
| Aterro de inertes e área de transbordo e triagem de RCC (`INERT_LANDFILL`, `CONSTRUCTION_WASTE_FACILITY`) | STRUCTURE | licença, capacidade | — | — |
| Ponto de descarte irregular (`ILLEGAL_DUMP`) | POI | data de identificação, tipo (RCC, volumoso, doméstico, misto), volume estimado (m³), foto, situação (ativo, em limpeza, eliminado, reincidente), OS de limpeza | — | — |

Classe do resíduo pelas NBR 10004-1 e 10004-2:2024 (classe 1, perigoso, e classe 2, não perigoso) (verificado em 22/10/2026: a série de 2024 abandonou a divisão em classe I, II A e II B da NBR 10004:2004, cancelada em 11/2024, com transição até 31/12/2026; fonte secundária, conferir no texto da ABNT) quando a unidade recebe resíduo
classificado.

### 4.6 Listas de valores comuns

`lifecycle_status`, `retired_reason`, `operational_status` (por classe), `condition_rating`, `capture_method`,
`vertical_datum`, `source`, `joint_type` (elástica, soldável, flangeada, mecânica, travada, eletrofusão, termofusão de
topo, compressão, rosca, argamassa), `pressure_class` (por família de material), `network_function` (água, esgoto e
drenagem, mapeada para as 11 subdivisões de estruturas lineares da NR 16/2026 e para a regra de extensão do SINISA),
`pavement_type` (asfalto, concreto, paralelepípedo, lajota, bloquete, terra; como a camada `PAV` de Nova Trento, 19a
§2.4), `street_side`, `valve_actuator`, `closing_direction`, `cover_load_class`, `waste_fraction`.

---

## 5. Topologia, conectividade e traçados

### 5.1 Modelo nó-arco persistido

Todo arco em `UNDER_CONSTRUCTION` ou `IN_SERVICE` tem `node_1_id` e `node_2_id`; em `PLANNED`, as pontas podem apontar
para nós também projetados ou para nós em operação. Ramal (CONNECTION) liga um arco de distribuição ou coletor a um nó
final (cavalete ou caixa de ligação). Isso vale para web, QGIS, app de campo e importação, porque está no banco.

### 5.2 Snap e tolerância

Gatilho `BEFORE INSERT OR UPDATE OF geom` em `gis_arc` e `gis_connection`:

1. procura nó a até `gis.snap_tolerance_m`, com a distância medida na UTM de trabalho do tenant (ou em `geography`);
2. havendo nó, move o vértice extremo para a posição dele e preenche a chave;
3. sem nó, cria `JUNCTION` (classes que admitem) ou recusa a gravação (classes que exigem peça cadastrada, por regra);
4. nó inserido sobre um arco a até a tolerância divide o arco em dois, copiando atributos, histórico e pacote (como no
   Giswater);
5. mover um nó arrasta as pontas dos arcos ligados.

Na importação em lote, o snap roda numa rotina de conjunto no staging, com a tolerância do lote, antes de publicar o
pacote (§9.1): gatilho por linha em milhões de feições seria lento (19d §11, risco 4).

### 5.3 Regras de conectividade por tipo

Carregadas em `gis_connectivity_rule` pelo catálogo de fábrica e extensíveis pelo tenant.

| Elemento | Regra | Severidade |
|---|---|---|
| Cap, tampão, flange cego, terminal de limpeza | exatamente 1 arco | erro |
| Curva, luva, redução, válvula em linha, junta de desmontagem | exatamente 2 arcos | erro |
| Curva, luva | mesmo DN nos dois arcos | erro |
| Redução | DN diferente nos dois arcos, igual a DN e DN2 do item | erro |
| Tê, junção | exatamente 3 arcos; DN2 no ramal | erro |
| Cruzeta | exatamente 4 arcos | erro |
| `JUNCTION` | 2 ou mais arcos; mudança de DN ou de material sem peça gera aviso | aviso |
| Ramal de água | liga a arco de função distribuição; nunca a adutora | erro (adutora); aviso (subadutora) |
| Hidrante | ligado a rede de água por ramal de hidrante ou tê | erro |
| PV, TIL, caixa de passagem | um ou mais arcos de entrada e um de saída (exceção configurável por tipo) | erro |
| Boca de lobo | liga a PV pluvial ou galeria por `INLET_LEAD` | erro |
| Domínio | arco e nó do mesmo domínio, salvo tipos de interface (extravasor, lançamento, interligação) | erro |
| Contenção | registro pode estar em `VALVE_CHAMBER`; bomba em elevatória; nó hidráulico em reservatório (`CONTAINS`) | — |
| Anexo | bloco de ancoragem e suporte anexados a arco (`ATTACHES`) | — |

### 5.4 Validações

| Validação | Domínio | Severidade |
|---|---|---|
| Nó solto (sem arco) em tipo que exige arco | todos | erro |
| Arco sem nó numa das pontas | todos | erro |
| Arcos duplicados ou sobrepostos; autointerseção | todos | erro |
| Segmento menor que o mínimo do tipo | todos | aviso |
| Ilha sem fonte (componente sem captação, poço, reservatório ou interligação) | água | aviso |
| DN incompatível sem peça | água, esgoto | erro |
| Válvula sem estado normal ou operacional | água, esgoto (recalque) | aviso |
| Declividade negativa, abaixo da mínima ou sentido contrário às cotas | esgoto, drenagem (gravidade) | erro (negativa); aviso (mínima) |
| Ciclo em rede por gravidade | esgoto, drenagem | erro |
| PV sem cota de fundo | esgoto, drenagem | aviso |
| Ramal sem ligação; ligação ativa sem ramal | água, esgoto | aviso |
| Feição sem metadado de captura; exatidão fora da meta | todos | erro; aviso |
| Material `legacy_only` em feição nova | água, esgoto | erro |
| Geometria inválida (`ST_IsValid`) ou fora do município do tenant | todos | erro |
| Contêiner, PEV ou ecoponto fora de setor de coleta | resíduos | aviso |

Feição com erro grave fica fora do traçado e da exportação hidráulica, listada no relatório da exportação.

### 5.5 Áreas sujas

Toda gravação aprovada registra em `gis_dirty_area` o envelope alterado com margem. O `worker` consome as áreas pela
outbox (ADR-0014), roda as validações do §5.4 só nelas, grava em `gis_validation_issue`, recalcula zonas derivadas e
setores de manobra afetados e marca os tiles sujos. O painel de qualidade mostra problemas por tipo, severidade,
setor e pacote, e cada problema pode ser aceito com justificativa (por exemplo, rede legada abaixo do DN mínimo).

### 5.6 Traçados

Implementação própria em Java, sobre adjacências carregadas por zona, porque o traçado depende de regras de negócio
(válvula inoperável, fonte, ciclo de vida, sentido) e precisa de teste unitário, como o construtor atual.

- **Montante e jusante.** Esgoto e drenagem pelo sentido do arco (cotas ou `flow_direction`); água a partir das fontes
  e controladores (reservatório, VRP, macromedidor).
- **Isolamento** (mincut do Giswater e *isolation trace* da Esri, 19d §4): a partir do arco ou nó afetado, busca que
  para nas válvulas isolantes operáveis (válvulas a fechar); válvula `INOPERABLE` ou de estado desconhecido é
  atravessada e a busca segue até a próxima; retirado o trecho isolado, componentes conexos a partir das fontes; o que
  fica sem fonte também perde abastecimento; os ramais levam às ligações e aos imóveis afetados. Setores de manobra
  (`gis_minsector`) pré-calculados na validação reduzem o traçado a um grafo de setores.
- **Interrupção com aviso e OS em lote.** O plano de manobra (`gis_isolation_plan`) guarda válvulas a fechar em ordem,
  estado anterior, imóveis e economias afetados, clientes sensíveis primeiro (hospital, escola, unidade de saúde e
  usuário eletrodependente, este como marcador mínimo, sem dado clínico, com acesso restrito), janela prevista, aviso
  aos afetados pela ED-41 (opt-in por canal) e OS de fechamento e de reabertura em lote pela ED-36. A baixa das OS
  atualiza `operational_status` das válvulas e registra o período de desabastecimento por imóvel (indicadores e
  eventual abatimento).
- **Áreas de influência.** Polígono atendido a partir de ETA, reservatório, ETE ou elevatória (SG-889) e bacias e
  sub-bacias geradas (SG-887), gravados como `gis_zone` com `derivation = TRACED`; conexões funcionais entre unidades
  (manancial → ETA → reservatório, SG-852).

### 5.7 pgRouting

Fica para o que é lote e grafo de ruas: validação de componentes conexos (`pgr_connectedComponents`), roteirização de
coleta de resíduos sobre logradouros (`pgr_TSP` para pontos; `pgr_chinesePostman`, experimental, ou heurística própria
para percorrer vias; a validar na GIS-14) e, depois, rotas de leitura. A versão 3.8 depreciou `pgr_createTopology` e
`pgr_analyzeGraph` em favor de topologia mantida pelo usuário (19d §4), o que confirma `node_1`/`node_2` próprios. A
extensão é criada no init do banco, como a PostGIS, e registrada no ADR-0010 (ajuste v2.10); disponibilidade e versão
no RDS conferidas na P0.26 e na LC.1.

---

## 6. Edição

### 6.1 Pacotes

Toda mudança na rede em operação entra por `gis_change_set` (decisão 3), qualquer que seja o canal.

| Tipo de pacote | Uso |
|---|---|
| `PROJECT` | projeto interno ou externo: feições `PLANNED` que coexistem com a rede, fora de traçado e indicadores (SG-858, TR-1050) |
| `AS_BUILT` | obra concluída: substitui a geometria projetada pela medida e passa as feições a `IN_SERVICE`, encerrando a vigência do que foi substituído |
| `CORRECTION` | correção cadastral de geometria ou atributo |
| `IMPORT` | lote de importação (§9) |
| `FIELD` | coleta e edição do app de campo |
| `QGIS` | edição feita pelo QGIS através das views |
| `SERVICE_ORDER` | alteração de rede decorrente de OS (retroalimentação, TR-626) |
| `AUTO_SERVICE_LINE` | ramais gerados automaticamente (GIS-05) |

Estados: `DRAFT` → `SUBMITTED` → `IN_REVIEW` → `APPROVED` → `APPLIED`, com `RETURNED` (volta ao autor com apontamentos),
`REJECTED` e `CANCELLED`. Novas feições de um pacote existem nas tabelas com o `change_set_id` e ciclo de vida
`PLANNED`, visíveis na camada do pacote; alterações e retiradas de feições em operação ficam em `gis_change_set_item`
como estado proposto, com a versão de base, até a aplicação.

### 6.2 Revisão

- Revisor diferente do autor, conferido por gatilho e pela `api` (recusa 403 e auditoria). Vale para todos os tipos e
  canais.
- A tela de revisão mostra, por feição, antes e depois no mapa e nos atributos, o resultado da validação do pacote e o
  relatório de qualidade (importação e as-built).
- Aprovação aplica o pacote numa só transação, pelos mesmos gatilhos de topologia, validação e histórico; qualquer
  erro desfaz tudo e devolve o pacote com o problema.
- Pacote com problema de severidade erro não é aprovado; aviso exige justificativa do revisor.

### 6.3 Travas por área e concorrência

- Pacote aberto pode travar sua abrangência (`gis_edit_lock`, com expiração): outro pacote não altera feições em
  operação dentro da área travada. Obra grande, migração e edição pelo QGIS travam a área; correção pontual não precisa.
- Na `api`, controle otimista pela coluna `version` (`If-Match`, P1.11).
- Na aprovação, item cuja feição mudou depois da versão de base é conflito: o autor refaz o item sobre a versão atual
  (*rebase*) antes de nova revisão.
- No app de campo, o conflito é por atributo, na sincronização (§8).

### 6.4 As-built

Fluxo projeto → obra → cadastro (19d §5.1): o pacote `PROJECT` nasce ligado ao projeto ou à OS, com planta e memorial;
a obra muda as feições para `UNDER_CONSTRUCTION`; o as-built medido em campo (RTK fixo dentro da meta, §8, ou
levantamento topográfico importado) gera o pacote `AS_BUILT`, com relatório técnico, croquis e monografia de marcos, e
a aprovação passa as feições a `IN_SERVICE` e encerra a vigência da geometria projetada. Feição `PLANNED` sem as-built
continua visível como projeto, com simbologia própria (§10.2).

### 6.5 Histórico e "rede na data"

Conforme §3.7. A ficha de cada feição mostra a linha do tempo (versões, pacote, autor, revisor, canal) e permite ver a
feição numa data.

### 6.6 Permissões

Permissões por operação e camada (em inglês, como as demais): `gis.view`, `gis.view-personal` (camadas e atributos
ligados a imóvel e titular), `gis.edit` (criar e editar pacotes), `gis.review` (revisar e aprovar), `gis.import`,
`gis.export`, `gis.catalog.manage`, `gis.style.manage`, `gis.qgis` (acesso pelo QGIS). Permissão por camada e operação
(incluir, editar, excluir, visualizar; SG-864, SG-865) na GIS-09.

---

## 7. QGIS e serviços OGC

### 7.1 QGIS (decisão 7)

1. **Role individual por usuário**, criada pela rotina privilegiada de implantação a partir de pedido aprovado do
   administrador do tenant: login próprio, sem privilégio, sem `BYPASSRLS`, nunca dona de tabela, `CONNECTION LIMIT` e
   `statement_timeout` próprios, validade da senha ou credencial de curta duração (autenticação IAM no RDS, a partir do
   lançamento). Mapeamento role → tenant e usuário numa tabela da plataforma (core `V12`) que só essa rotina grava; a
   restrição de linhas usa a identidade da role, nunca a GUC de sessão (ARQUITETURA-ALVO §4; ADR-0027 e ADR-0028).
2. **Leitura** por views `qgis_*` com `security_invoker = true` e RLS pela identidade da role; tabelas base sem
   privilégio de escrita para a role.
3. **Escrita só por views `qgis_edit_*`** com gatilhos `INSTEAD OF`: a gravação vai para o pacote `QGIS` aberto do
   usuário (cria um em `DRAFT` se não houver), passa pela validação do JSON Schema, pela topologia e pela trava por área,
   e nunca altera a rede em operação; o pacote é submetido e revisado na web por outra pessoa. A função do gatilho é
   `SECURITY DEFINER` de uma role sem login que só grava nas tabelas de pacote, e lê o tenant do mapeamento da role.
4. **Projeto QGIS gerado pelo produto** (`.qgz` por tenant e perfil): camadas, grupos, formulários gerados do JSON
   Schema, listas de valores como *Value Relation*, simbologia igual à da web (gerada da mesma fonte, §10.2), rótulos,
   escala de visibilidade, arquivo de serviço (`pg_service.conf`) sem senha, configuração de autenticação no banco de
   autenticação do QGIS, grupos de transação ligados e valores padrão avaliados no servidor (UUID gerado no banco).
5. **Endpoint próprio**, fora do PgBouncer em modo transaction da `api`: listener dedicado com TLS e verificação do
   servidor (`verify-full`), acesso por VPN ou por IP de origem do cliente, nunca aberto à internet. Até o lançamento,
   o endpoint existe só no ambiente local (ADR-0033).
6. **Auditoria**: conexões e comandos da role registrados (pgAudit, se a extensão estiver disponível no RDS; conferir
   na LC.1) e histórico por gatilho com role e canal; revogação da role encerra o acesso na próxima conexão.
7. **Teste de aceite** com conta real sem privilégio: a role de um tenant não lê nem grava outro, em schema próprio e
   em schema compartilhado; uma conexão salva com outro usuário não contorna a regra (relato do 19d §5.4); pentest do
   caminho antes do primeiro cliente.

### 7.2 Serviços OGC

| Serviço | Forma | Entrega |
|---|---|---|
| Cliente WMS (SG-827, TR-1014) | fonte raster do MapLibre por URL cadastrada pelo tenant | GIS-06 |
| OGC API – Features, partes 1 a 3 (Core, CRS, Filtering; o CQL2 é norma OGC separada, referenciada pela parte 3) | na `api`, autenticada por tenant, com permissões por camada e o mesmo serviço de leitura dos tiles | GIS-17 |
| WMS 1.3.0 servidor (`GetCapabilities`, `GetMap`, `GetFeatureInfo`) | pelo GeoServer (v2.12; decidido na v2.11, onda B), só leitura e acessível só pela `api`, autenticado por tenant, com permissões por camada e simbologia em SLD gerado de `gis_style` (§10.2); isolamento por tenant descrito abaixo; exceção registrada no ADR-0010 | GIS-17 |
| WFS 2.0 com Transaction (SG-1357, F10 do 19e) | fachada na `api` sobre o mesmo serviço: `GetCapabilities`, `DescribeFeatureType`, `GetFeature`; `Transaction` grava no pacote do usuário, nunca na rede em operação | GIS-17 |
| WCS | não previsto; edital que exigir abre exceção no ADR-0010 | pendência §14 (P-8) |
| OGC API – Features parte 4 (escrita) | depois de aprovada pela OGC (19c §2.4; em 22/10/2026 a parte 4 segue em minuta, e a parte 5, Schemas, foi aprovada em 30/04/2026) | — |

Toda publicação OGC é autenticada por tenant (decisão do dono do produto, v2.11): não há acesso anônimo, a credencial
pertence a um usuário ou a uma integração cadastrada pelo tenant, e uma requisição nunca lê camada de outro tenant. O
usuário do tenant com `gis.view-personal` vê as camadas pessoais pelas regras do backoffice; credencial entregue a
terceiro (prefeitura, órgão, consultoria) e qualquer publicação pública recebem rede, ativos e camadas agregadas pela
regra do §12, item 3. Conformidade conferida com o validador da OGC (TEAM Engine) nas partes implementadas.

**WMS pelo GeoServer (decisão do dono do produto, v2.12).** O WMS servidor é exigido em edital e fica com o GeoServer,
componente da onda B registrado nas exceções do ADR-0010; OGC API – Features e WFS continuam na `api`, e a escrita
nunca passa pelo servidor de mapas (decisão 7).

1. **Acesso.** O GeoServer não tem porta publicada fora da rede interna nem console administrativo exposto. Toda
   requisição WMS chega à `api`, que autentica o usuário (sessão do BFF ou credencial OGC emitida ao tenant), confere a
   permissão por camada e encaminha ao workspace do tenant da credencial; o GeoServer só aceita o usuário de serviço da
   `api`, com acesso restrito por workspace. WFS-T, edição e REST de escrita ficam desligados para fora.
2. **Isolamento por tenant.** Um workspace por tenant. O store PostGIS de cada workspace conecta com role de banco
   própria do tenant, somente leitura, sem privilégio, criada pela rotina privilegiada de implantação e mapeada ao
   tenant como as roles do QGIS (§7.1): no schema próprio, ela só tem `SELECT` nas views de publicação do schema do
   tenant; no schema compartilhado (ADR-0005), lê views filtradas pelo tenant da role, pela identidade da conexão,
   nunca pela GUC. Nenhum workspace, store, role ou cache alcança outro tenant; teste no CI pela `api` (credencial do
   tenant A pedindo camada do workspace B) e pela role (consulta direta a objeto de outro tenant), em schema próprio e
   compartilhado.
3. **Camadas.** As camadas publicadas são views de publicação: rede e ativos, salvo infraestrutura crítica sem controle
   de acesso (§12, item 7); imóveis e ligações, para credencial de terceiro ou publicação pública, só pelas views
   agregadas pelo piso de `gis.public_aggregation_min_units` (§12, item 3). Cache do GeoServer, se usado, por
   workspace, com invalidação na aprovação de pacote pela mesma marcação de `gis_tile_dirty` (§10.1).
4. **Simbologia.** SLD gerado de `gis_style`, a mesma fonte do estilo MapLibre e do projeto QGIS (§10.2), com o
   override do tenant; nenhum estilo editado à mão no GeoServer; teste compara regras e cores das três saídas.
5. **Configuração e operação.** Workspaces, stores, camadas e estilos são criados e atualizados pelo produto pela API
   REST do GeoServer, na implantação do tenant e a cada mudança de estilo ou camada; versão do GeoServer fixada, com
   atualização de segurança no ritmo das demais dependências. Até o lançamento, roda só no ambiente local (ADR-0033).

---

## 8. Campo

App de campo Android em Capacitor (ADR-0026), com autenticação, dispositivo registrado, banco local cifrado e bloqueio
remoto do ADR-0030. Entrega GIS-15, depois da ED-18 e da ED-20.

- **Pacote offline por área**: mapa base em PMTiles, feições da área em SQLite cifrado, catálogo, listas de valores,
  JSON Schemas e estilos, com versão do pacote e validade, no mesmo padrão da carga de rota (ARQUITETURA-ALVO §2,
  item 5). Camadas pessoais (imóveis, ligações) só no pacote de quem tem `gis.view-personal`.
- **Coleta e edição offline** como deltas: `client_change_id` (UUIDv7), versão de base e só os campos alterados,
  aplicados num pacote `FIELD`. Reenvio não duplica. Versão de base desatualizada vai para a fila de conflito por
  atributo, resolvida na web. Topologia e validação definitivas ficam no servidor, porque gatilhos não rodam na cópia
  local (19d §6).
- **GNSS**: receptor RTK externo por Bluetooth (NMEA) via plugin nativo, ou localização simulada do Android a partir
  do receptor; correção NTRIP pelo celular. Cada ponto guarda tipo de solução (RTK fixo, flutuante, autônomo),
  exatidões, PDOP, número de satélites e altura da antena. As-built só com RTK fixo dentro da meta do tenant; altitude
  elipsoidal convertida no servidor pelo hgeoHNOR2020, com o datum registrado.
- **Fotos e croqui**: `gis_attachment` com posição, azimute, hora e hash, varridos pelo pipeline de anexos da P1.20
  (ClamAV); croqui vetorial com amarrações ao alinhamento predial e ao meio-fio (padrão Corsan).
- **Integração com a OS**: OS de campo (ED-20) abre a feição, registra a intervenção e, se alterar a rede, gera itens
  num pacote `SERVICE_ORDER`.

---

## 9. Importação, migração e exportação

### 9.1 Pipeline de importação (`worker`)

| Etapa | O que faz |
|---|---|
| 1. Conversão | DWG (qualquer versão) → DXF pelo ODA File Converter, na etapa assistida da implantação, fora da imagem de produção (ADR-0028, item b; licença conferida na P1.25); hash do original e do convertido no lote. DXF, SHP, GPKG lidos com GDAL; DGN v7 com GDAL; DGN v8 convertido para DWG ou DXF na etapa assistida da implantação, com ferramenta do cliente (por exemplo, MicroStation) ou do Ziip, com hash do original e do convertido no lote (v2.11); INP do EPANET e do SWMM pelo leitor da GIS-12; CSV de pontos GNSS com metadados de precisão. Formatos aceitos pelo importador: DGN v7, DWG, DXF, SHP, GPKG e INP do EPANET e do SWMM, além do CSV de pontos GNSS |
| 2. Preparação | tudo entra em `gis_import_raw` com lote, camada, `EntityHandle` (DXF), atributos e geometria original, sem tocar no cadastro; blocos do DXF como pontos de inserção com nome, escala, ângulo e atributos (`DXF_INLINE_BLOCKS=FALSE`) |
| 3. Mapeamento | `gis_cad_layer_map`: camada, bloco ou padrão de texto → classe, tipo e item de catálogo, revisável na tela e salvo por cliente para reuso; extração de rótulos (por exemplo "DN 100 PVC") por associação espacial ao arco mais próximo, com o texto não interpretado guardado |
| 4. Referência | identificação do SRS de origem; conversão de SAD69 e Córrego Alegre pelas grades do IBGE; coordenada local ajustada por pontos de controle com resíduo registrado; escala 1:1 e metro conferidos |
| 5. Limpeza topológica | `ST_MakeValid`, duplicatas, segmentos curtos, snap em lote com a tolerância do lote, quebra nos nós (`ST_Node`), junção de polilinhas partidas, sentido do esgoto pelas cotas; contagem antes e depois de cada operação |
| 6. Qualidade | relatório ISO 19157-1: completude (omissão e comissão contra a contagem da origem), consistência lógica (conceitual, de domínio, de formato, topológica), acurácia posicional (declarada e, quando houver, amostra RTK), acurácia temática (DN e material em amostra de campo) e qualidade temporal; PDF anexado ao pacote e metadado ISO 19115/MGB gerado |
| 7. Publicação | pacote `IMPORT` com `capture_method = CAD_IMPORT` e exatidão estimada pelo método, revisado por outra pessoa; o as-built posterior substitui a geometria |

Reimportar o mesmo arquivo não duplica (hash e `source_ref`). O casamento de unidades consumidoras por matrícula ou
endereço (SG-1349) e a conciliação da base cadastral fornecida com o desenho (SG-1356) vão para a GIS-18, com fila de
revisão.

### 9.2 Perfis de importação e exportação

| Perfil | Conteúdo | Origem |
|---|---|---|
| Corsan Anexo A (rev. 03/2024) | camadas `A_REDE`, `A_CONEXOES`, `A_VALVULA`, `A_HIDRANTE`, `A_PROTEHIDRAULICAS`, `A_RAMAIS`, `A_MACROMEDIDOR`, `A_PCP`, `A_PCQ`, `A_POCO`, `A_RESERVATORIO`, `A_EEAT`, `A_MOTOBOMBA`, `A_ETA`, `A_CAPTACOES`, `A_SETOR_FORNECIMENTO`; `E_REDE`, `E_POCO_VISITA`, `E_TIL`, `E_CX_LIGACAO`, `E_CX_DESCARGA`, `E_RAMAL`, `E_VALVULAS`, `E_EEE`, `E_MOTOBOMBA`, `E_ETE`, `E_BACIA`, `E_SUB_BACIA`, com os atributos mapeados para colunas e itens | 19b §3.3 |
| Saneago | camadas e atributos da IN 00.0229 (rev. 04, de 11/03/2024; verificado em 22/10/2026: a revisão publicada é a 04, e a 03 citada antes não foi localizada) e do modelo Giswater (`arc`, `node`, `connec`, catálogos) | 19b §3.1; conteúdo a validar na leitura da IN |
| Nova Trento/DGN `PREFIXO_Feição` | níveis `_TER`, `DIV`, `DRE`, `PAV`, `URB`, `USO`, `VIA` como camadas de contexto e de drenagem | 19a §2.4 |
| Genérico | mapeamento montado na tela e salvo por cliente | — |

### 9.3 Exportação

| Formato | Uso | Entrega |
|---|---|---|
| GeoPackage 1.4 | formato padrão de intercâmbio e de devolução | GIS-16 |
| Shapefile | companhias e ArcGIS; nomes de atributo até 10 caracteres por mapa de nomes, `.prj` | GIS-16 |
| DXF | prancha e CAD, camada por tipo, blocos por símbolo | GIS-16 |
| KML/KMZ | Google Earth (SG-1365) | GIS-16 |
| GeoJSON (RFC 7946) e CSV | integração e planilha | GIS-16 |
| EPANET `.inp` | modelagem de água | GIS-12 |
| SWMM `.inp` | modelagem de esgoto e drenagem | GIS-12 |
| Perfis Corsan e Saneago | entrega de as-built às companhias | GIS-16 |
| Metadado ISO 19115/MGB (XML) | acompanha toda exportação | GIS-16 |

Filtros por área, domínio, ciclo de vida, data ("rede na data") e camada. Exportação com camada pessoal registrada no
`personal_data_access_log` (P0.11).

### 9.4 Devolução da base ao fim do contrato

A exportação de fim de contrato (RNF-42; ADR-0025, 10 dias úteis no nível Setor Público; ensaio na P3.16) inclui o
cadastro técnico em GeoPackage e Shapefile, com catálogo, listas de valores, histórico, anexos, dicionário de dados
gerado do schema e metadados MGB. Atende ao requisito de devolução da base (F10 do 19e) e à objeção de aprisionamento.

---

## 10. Publicação e desempenho

### 10.1 Tiles vetoriais

- `GET /api/v1/gis/tiles/{layer}/{z}/{x}/{y}.mvt`, gerado por `ST_AsMVT` e `ST_AsMVTGeom` sobre `ST_TileEnvelope`,
  extent 4096, buffer 64, filtro pelo envelope com margem sobre `geom_web` indexada; sem servidor de mapas novo
  (ADR-0010; Martin só como plano B, por exceção).
- Uma camada MVT por domínio e papel (água-arcos, água-nós, esgoto-arcos...), com atributos mínimos (id, tipo, DN,
  material, ciclo de vida, estado, `accuracy_status`); o detalhe vem da `api` ao clicar. Zoom mínimo por tipo: em zoom
  baixo, adutoras, coletores-tronco e DN maiores; ramais e conexões a partir de z17.
- Pontos densos (imóveis, ligações, OS) agregados no servidor por grade ou `ST_ClusterDBSCAN` em zoom baixo, e não no
  navegador.
- **Cache** com chave `(tenant, camada, z, x, y, versão da camada)`, na `api` ou em armazenamento de objetos; nunca em
  CDN pública; `ETag` pela versão. Aprovação de pacote marca em `gis_tile_dirty` os tiles afetados (z10 a z18), que o
  `worker` invalida. O teste de isolamento cobre a chave de cache (19d §11, risco 5).

### 10.2 Simbologia (decisão 6)

- **Símbolos (v2.13, ADR-0036):** o `style_key` do tipo aponta para um símbolo SVG do pacote de vertical; o conjunto, a geração do sprite, do QGIS, do SLD e da legenda, e o aceite do dono estão em [ICONES-E-SIMBOLOGIA.md](ICONES-E-SIMBOLOGIA.md). O tenant escolhe símbolo da biblioteca, sem enviar SVG.
- **Fonte única**: `gis_style` guarda, por tipo e por estado, cor, espessura, padrão de traço, ícone, rotação, rótulo
  e zoom. Dela saem o estilo MapLibre (expressões `match`, seleção por `feature-state` com `promoteId`), o estilo do
  projeto QGIS (QML), o SLD do WMS servido pelo GeoServer (v2.12, §7.2) e a legenda dos PDFs. O tenant sobrescreve por tipo e estado, sem código; o padrão Ziip continua
  disponível para restaurar.
- **Padrão Ziip por estado**: em operação, traço contínuo na cor do domínio; projetado, tracejado; em obra, traço e
  ponto; abandonado, cinza tracejado fino; fora da meta de precisão, halo ou hachura; aproximado, marcador próprio na
  legenda. Estado nunca é comunicado só por cor (padrão de traço e ícone), por acessibilidade.
- **Padrão Ziip por tipo**: cor por domínio (água, esgoto, drenagem, resíduos), espessura por classe de DN, ícone por
  classe de nó (válvula, hidrante, PV, boca de lobo, contêiner), temático alternativo por material ou por DN. As cores
  passam pelos tokens `map-*` do ADR-0017, com contraste conferido sobre mapa base claro e escuro.
- **PDFs**: mapa exportado no navegador a partir do mesmo estilo, com escala, norte, legenda gerada de `gis_style`,
  SRS e data; pranchas seguem as NBR de desenho técnico (19c §2.1). Renderização no servidor, se vier a ser
  necessária para relatório agendado com mapa, exige avaliação contra o ADR-0010.

### 10.3 Mapa web de consulta (núcleo)

Visualizador MapLibre no frontend existente: camadas liga e desliga por grupo (água, esgoto, drenagem, resíduos,
contexto, comercial), rótulos por camada, mapas base (OpenStreetMap padrão; Google e Bing com licença do contrato,
SG-1364), cliente WMS, identificação com ficha da feição (atributos, catálogo, ligação, imóvel, hidrômetro, histórico,
anexos, problemas de validação), medição de distância e área, pesquisa por coordenada (latitude e longitude ou UTM
SIRGAS 2000 no fuso do tenant), por atributo e por endereço (logradouros do cadastro, inclusive em cadastramento),
seleção por polígono com estatísticas, OS no mapa e temáticos (GIS-08). Rotas do frontend: `/gis`, `/gis/catalog`,
`/gis/change-sets`, `/gis/imports`, `/gis/quality`.

### 10.4 Volume, índices e metas

- GiST em `geom` e `geom_web`; B-tree em tipo, ciclo de vida, `node_1_id`, `node_2_id`, `service_connection_id`;
  índices parciais em `IN_SERVICE`. Particionamento só com dezenas de milhões de linhas por tabela; no schema por
  tenant, um município grande tem centenas de milhares de feições (19d §8).
- Metas propostas, a registrar no RNF pela tarefa da GIS-06 (não vigentes antes disso): p95 de tile ≤ 300 ms com
  cache quente e ≤ 1 s sem cache, em tenant sintético com 1 milhão de feições; ficha da feição ≤ 500 ms; validação de
  área suja com até 1.000 feições alteradas em ≤ 5 min; isolamento em rede sintética de 5.000 km em ≤ 5 s.

---

## 11. Integrações

| Integração | Forma | Entrega |
|---|---|---|
| Comercial: ramal ↔ ligação ↔ imóvel ↔ hidrômetro | `gis_connection.service_connection_id`; ligação 1:1 com o imóvel; hidrômetro pela instalação vigente na ligação (`metering`); imóvel mantém sua coordenada; ligação nova desenhada no mapa cria a ligação no cadastro, sincronizada (SG-866) | GIS-05 (base), GIS-09 |
| OS no mapa | OS com posição ou referência a feição; camada de OS com filtros (SG-491, TR-534); OS que altera a rede abre pacote `SERVICE_ORDER` | GIS-08, GIS-09 |
| Manobras | plano de manobra, aviso pela ED-41, OS em lote pela ED-36, período de desabastecimento por imóvel | GIS-11 |
| EPANET | exportação de `gis_node`/`gis_arc` em operação (ou com pacote `PLANNED` como cenário), rugosidade do material, demanda do consumo faturado das ligações do nó (média de meses parametrizável), cotas; importação de `.inp` como pacote | GIS-12 |
| SWMM 5.2 | junção = PV (cota de fundo, profundidade máxima = tampa − fundo); conduto = arco com cotas, seção `geom1`–`geom4` e Manning; armazenamento = bacia de detenção; *outfall* = lançamento; *subcatchment* = sub-bacia; *inlet* = boca de lobo; importação de `.inp` | GIS-12 |
| Perdas e DMA | `gis_zone` `DMA` ligada a `dma_district`; macromedidores como controladores; balanço por setor na ED-48 | GIS-01, ED-48 |
| Telemetria | macromedidor e ponto de pressão ligados ao ponto da ED-17 | ED-17 |
| Gestão de ativos (ISO 55000:2024 e ISO 55001:2024) | probabilidade de falha (idade, material, vida útil, rompimentos por arco vindos das OS de reparo, estado) × consequência (traçado de isolamento: ligações, clientes sensíveis, DN, local); plano de renovação; manutenção preventiva e corretiva ligada à feição | GIS-19 (ED-56) |
| Inventário patrimonial pela NR 16/2026 da ANA (MCPSB) | códigos LOC, BCP, BRV, NTS-SDS, UP, UAR, AT1–AT5 por feição; extensão em metros por subdivisão de estrutura linear (rede de distribuição, adutora, rede coletora, coletor-tronco, interceptor, emissário, linha de recalque, interligação, extravasor, lododuto, rede de drenagem); conciliação GIS × patrimônio exportada à contabilidade (ED-32); bens reversíveis com data e origem do investimento (NR 3/2023) | GIS-19 |
| Drenagem pela NR 12/2025 da ANA | cadastro georreferenciado com material, estado de conservação, foto e histórico; relatório para a ERI | GIS-13 |
| SINISA | extensão da rede de esgoto pela regra do GTE1001 (inclui coletores-tronco e interceptores; exclui ramais, emissários e recalques), extensão de água (código a confirmar no glossário vigente), coordenadas de elevatórias e ETEs em graus decimais com 7 casas, na data de referência ("rede na data"), para o exportador assistido (ADR-0004, ED-48) | GIS-08 |
| Relatório de extensão | por domínio, função, material, faixa de DN, década de instalação e setor, em qualquer data, em PDF, XLSX e CSV pelo núcleo de relatórios da P3.22 | GIS-08 |
| Resíduos | imóvel ↔ setor de coleta para a taxa de resíduos (ED-25); destino no MTR e no GRS (ADR-0004); veículos da frota (ED-59) nas rotas | GIS-14 |
| Qualidade da água | pontos de amostragem e rotas de coleta de amostra (SG-802, SG-804) como POI e rota | ED-50, GIS-14 (rotas) |

---

## 12. LGPD e infraestrutura crítica

1. **Coordenada ligada a pessoa é dado pessoal.** Ponto do imóvel, da ligação ou do cavalete vinculado a matrícula,
   CPF ou titular identifica pessoa natural (LGPD, art. 5º, I; 19c §2.5). Rede, PV, válvula e unidade operacional não
   são dado pessoal.
2. **Separação.** Camadas técnicas e camadas ligadas a imóvel e titular são separadas na publicação: tile de imóvel ou
   ligação só para quem tem `gis.view-personal`, com atributos mínimos (id e situação), sem nome nem documento; a ficha
   completa passa pelas regras do cadastro (máscara do ADR-0008, registro de leitura da P0.11).
3. **Publicação fora do backoffice autenticado** (decisão do dono do produto, v2.11). No portal, nos serviços WMS e OGC
   públicos ou entregues a terceiros, nos relatórios externos e no compartilhamento com prefeitura, dados de imóveis e
   de ligações (inclusive temáticos de consumo e de inadimplência) só saem agregados por quadra ou por setor, com no
   mínimo `gis.public_aggregation_min_units` imóveis por célula: piso 5, classe c, só ampliável pelo tenant
   (PARAMETROS-DO-TENANT, GIS). Célula abaixo do mínimo não é publicada; ponto de imóvel, de ligação, de ramal ou de
   cavalete nunca é publicado, porque reidentifica. Rede e ativos não são dado pessoal e podem ser publicados, com a
   ressalva de infraestrutura crítica do item 7, que tem outra base legal.
4. **Clientes sensíveis.** O marcador de eletrodependente usado na manobra é dado de saúde (dado sensível): guardado
   como marcador mínimo, com base legal registrada, acesso restrito e registrado, nunca em tile nem em exportação
   aberta.
5. **Exportação e campo.** Exportação com camada pessoal registrada no `personal_data_access_log`; pacote offline do
   app com camada pessoal só para quem tem a permissão, cifrado e apagável pelo bloqueio remoto (ADR-0030).
6. **QGIS e WFS.** Mesmo perfil de acesso e mesma RLS da aplicação (19c §3.6); camada pessoal só na role autorizada.
7. **Infraestrutura crítica** (Decreto 9.573/2018): adutoras, reservatórios, captações e válvulas de manobra não entram
   em camada aberta; publicação só com controle de acesso.
8. **Ambiente local até o lançamento** (ADR-0033, ajuste v2.11, como regra e não como exceção de dado pessoal). Na
   homologação local entram as redes, peças e estruturas reais do piloto, que são ativos e não dado pessoal, com
   autorização no termo de cooperação (plano P0.30) e acesso restrito, por serem informação de infraestrutura do
   cliente. Ligações, ramais, cavaletes e imóveis vêm sintéticos ou anonimizados na origem, com código substituto e
   coordenada deslocada dentro da quadra, sem coincidir com o lote de origem, ou agregada no centroide da quadra, com o
   método registrado por camada no lote (plano P0.45 e P3.26); lote com camada pessoal sem o método registrado é
   recusado. O critério é o do item 1: entra real o que não se liga a pessoa; o que se liga entra com o vínculo desfeito
   na origem. O ambiente de demonstração continua só com massa sintética.
9. **Inventário de tratamento.** Cada camada pessoal com finalidade registrada no inventário (levantamento `10`) e no
   RIPD do módulo.

---

## 13. Fases e entregas

### 13.1 Visão geral

| Bloco | Fase | Entregas | Esforço (semanas-pessoa) |
|---|---|---|---|
| Núcleo | P1 | GIS-01, GIS-02 | 5 |
| Núcleo | P2 | GIS-03, GIS-04, GIS-05 | 7 |
| Núcleo | P3 | GIS-06, GIS-07, GIS-08 | 10 |
| **Núcleo antes do P4** | | **8 entregas** | **22** |
| Trilha | onda A | GIS-09 | 5 |
| Trilha | onda B | GIS-10 a GIS-18 | 31 (30 na v2.10; +1 do WMS servidor na GIS-17, v2.11; mantido com o GeoServer na v2.12) |
| Trilha | onda C | GIS-19 | 4 |
| **Trilha** | | **11 entregas** | **40** |
| **Total** | | **19 entregas** | **62** |

### 13.2 Núcleo antes do P4 (P1 a P3)

Cada entrega fecha no marco da sua fase (§13.6). No P3, a GIS-07, que carrega a rede real do piloto na homologação
local, vem antes da GIS-08 e da parte não essencial da GIS-06 (cliente WMS, medição).

| ID | Entrega | Tarefa | Tam. | Dependências | Critérios de aceite | ED |
|---|---|---|---|---|---|---|
| GIS-01 | Modelo de dados do núcleo, histórico, metadados de qualidade, SRS e migração do `gis_network_element` | P1.26 | G (3) | P0.4, P0.19, P0.33 | `V13_1`, `V13_2` e core `V9_6` aplicados em tenant novo e em tenant com o modelo antigo sintético, com 100% das linhas convertidas ou listadas no relatório de não mapeadas (teste); linha em `gis_node` e ponto em `gis_arc` recusados pelo banco (teste); mesmo `code` em dois tenants do mesmo schema compartilhado aceito (teste); `attributes` fora do JSON Schema recusado pela `api` e por SQL direto (teste nos dois caminhos); feição sem `capture_method` recusada; nenhuma coluna de idade armazenada; alteração grava versão anterior com período sem sobreposição e consulta por data devolve o estado da data (teste); view `gis_network_element` faz o `EpanetSimulationService` gerar o mesmo `.inp` sobre a massa de referência (teste de regressão); `gis.work_srid` aceita só a lista do §3.9; parâmetros `gis.*` no catálogo e em PARAMETROS-DO-TENANT | ED-52 |
| GIS-02 | Catálogo de fábrica de água e esgoto e administração por tenant | P1.27 | G (2) | GIS-01 | `R__gis_factory_catalog.sql` carrega as tabelas do §4.2, §4.3 e §4.6; reaplicação não duplica nem altera ativação ou item do tenant (teste); todo item com norma e edição ou com `REFERENCE_TO_VALIDATE`, e relatório dos itens a validar gerado (teste de completude); tela de catálogo: ativar e desativar, criar tipo com JSON Schema montado por formulário e item do tenant, aprovado por outra pessoa, sem código nem migration (teste E2E); item desativado some da criação e continua válido nas feições existentes (teste); item retirado da fábrica não é apagado | ED-52 |
| GIS-03 | Topologia persistida, regras de conectividade, validação contínua e EPANET sobre a topologia | P2.21 | G (3) | GIS-01, GIS-02, P1.9 | arco gravado pela `api`, por SQL direto e por importação recebe `node_1_id`/`node_2_id`, com snap pela tolerância do tenant medida em metros, criação de `JUNCTION` ou recusa conforme a classe (teste por caminho); nó sobre arco divide o arco preservando atributos e histórico (teste); cada regra do §5.3 com teste que gera o problema esperado; validações do §5.4 no `worker` sobre `gis_dirty_area`, com painel de qualidade; `pgr_connectedComponents` em lote igual ao traçado Java na massa sintética (teste); `NetworkTopologyBuilder` sem `nearestNode`, com `.inp` igual ao anterior onde a topologia era correta (testes do builder refeitos); feição com erro grave fora da exportação e listada | ED-52 |
| GIS-04 | Pacotes de edição, revisão, ciclo de vida, travas e "rede na data" | P2.22 | G (2) | GIS-01, GIS-03 | gravação em feição `IN_SERVICE` fora de pacote aplicado recusada pelo banco, inclusive por SQL direto (teste); autor não aprova o próprio pacote, com 403 na `api` e erro no banco (teste); transições de pacote e de ciclo de vida do §3.7 e do §6.1 testadas; aprovação aplica tudo numa transação e falha desfaz tudo (teste); item com base desatualizada exige rebase (teste); trava por área impede gravação de outro pacote na área (teste); feição `PLANNED` fora de traçado e indicadores (teste); `network-at` por data de negócio e por data de registro corretos em massa com alterações datadas (teste); tela de revisão com antes e depois por feição | ED-52 |
| GIS-05 | Ligação base e vínculo feição ↔ imóvel ↔ ligação ↔ ramal; ramais automáticos | P2.23 | G (2) | P1.3, GIS-01, GIS-04 | registry `V3_7` com `service_connections` 1:1 com o imóvel por tipo enquanto ativa, sem mudança no ciclo da P2.5 (testes do faturamento verdes); ramal sem ligação e ligação ativa sem ramal viram problema de validação (teste); ramal automático do cavalete ao arco de distribuição mais próximo até `gis.service_line_max_distance_m`, em pacote `AUTO_SERVICE_LINE`, `capture_method = AUTO_SERVICE_LINE`, nada em operação sem revisão (teste); ficha mostra ligação, imóvel, situação e hidrômetro vigente; sem `gis.view-personal`, só identificador e situação (teste LGPD) | ED-52 (1,5), ED-13 (0,5) |
| GIS-06 | Tiles vetoriais, simbologia padrão e mapa web de consulta | P3.25 | G (4) | GIS-01 a GIS-05, P0.14, P0.36 | rota de tiles com `ST_AsMVT`; tile e cache nunca servidos a outro tenant (teste de isolamento com a chave de cache); invalidação em até 1 min após aprovação de pacote (teste); estilo MapLibre gerado de `gis_style` com o padrão do §10.2 e override do tenant sem código (teste); visualizador com camadas, rótulos, mapas base, cliente WMS, identificação, medição e pesquisas por coordenada UTM, atributo e endereço (E2E sobre SG-824 a SG-836, SG-888 e TR-1012 a TR-1024); axe sem violação séria ou crítica, nos temas claro e escuro; metas do §10.4 medidas e registradas como linha de base e propostas ao RNF | ED-52 |
| GIS-07 | Importador DWG (via DXF), DXF, SHP e GPKG com staging, mapeamento, perfis, conversão de SRS, limpeza e relatório de qualidade | P3.26 | G (4) | P1.9, P1.20, P1.25, GIS-03, GIS-04 | etapas do §9.1 com hash do DWG e do DXF no lote; arquivo sem SRS recusado ou com escolha registrada; SAD69 e Córrego Alegre convertidos pela grade do IBGE com resíduo dentro do esperado no arquivo de referência (teste); nada no cadastro sem pacote `IMPORT` aprovado (teste); perfis Corsan Anexo A e genérico com amostras sintéticas, e Saneago com amostra a validar; rótulos extraídos com o texto não interpretado guardado; relatório ISO 19157 em PDF no pacote; 50 km sintéticos importados e validados em até 2 h na máquina de referência; reimportação não duplica (teste); camadas de contexto (bairros, quadras, lotes, logradouros) importadas; DGN v8 aceito só depois da conversão assistida para DWG ou DXF, com hash do original e do convertido (v2.11); rede real do piloto (redes, peças e estruturas) importada na homologação local, com ligações, ramais, cavaletes e imóveis só sintéticos ou anonimizados, e lote com camada pessoal sem o método de anonimização registrado recusado (teste; §12, item 8) | ED-55 (4) |
| GIS-08 | Temáticos, precisão, OS no mapa e relatórios do cadastro | P3.27 | G (2) | GIS-06, P2.5, P3.1, P3.4, P3.22 | temático configurável com legenda automática e filtro por categoria; cenários SG-873 a SG-883 e TR-1060 a TR-1063 sobre massa sintética (teste por cenário); temático de precisão com "levantado na meta", "levantado fora da meta" e "aproximado", e relatório por classe e método (decisão 5); OS no mapa com filtros (SG-491, TR-534); seleção por polígono com estatísticas (TR-1059); relatório de extensão por domínio, função, material, DN, década e setor, em qualquer data, com soma igual ao cálculo geodésico (teste); fora do backoffice autenticado, temático e relatório com imóveis ou ligações só agregados por quadra ou setor com no mínimo `gis.public_aggregation_min_units` imóveis (piso 5), célula de 4 imóveis não publicada e nenhum ponto de imóvel ou ligação (teste) | ED-52 |

### 13.3 Trilha de editais (P4)

| ID | Entrega | Onda | Tam. | Dependências | Critérios de aceite | ED |
|---|---|---|---|---|---|---|
| GIS-09 | Edição web completa e cadastro de unidades | A | G (5) | GIS-04, GIS-06, ED-13, ED-36 | editor de ponto, linha e polígono com vértices, mover, rotacionar e escalar (SG-838, SG-839, TR-1026, TR-1027); dividir e unir arcos; inserir nó que segmenta; formulário gerado do JSON Schema; cadastro de ETA, ETE, elevatórias e reservatório com geometria (SG-148 a SG-152); camada do usuário e junção de tabelas (SG-840, SG-841); permissões por camada e operação (SG-864, SG-865); ligação nova desenhada vira ligação no cadastro, sincronizada (SG-866, TR-1053); pacotes `PROJECT` e `AS_BUILT` com tela; projetos e obras (SG-858, TR-1050, TR-1051); OS que altera rede abre pacote (TR-626); SAMAE 2.8 sem falha no ensaio da onda A | ED-53 |
| GIS-10 | QGIS por role individual, views com `INSTEAD OF` e projeto gerado | B | G (3) | GIS-04, GIS-06, P3.8 | §7.1 itens 1 a 7 com teste: role sem privilégio, leitura e escrita só por views, escrita no pacote do usuário com validação, tabelas base sem escrita; role de um tenant não lê nem grava outro, em schema próprio e compartilhado; `.qgz` gerado com formulários, listas e simbologia iguais à web; endpoint próprio com TLS `verify-full`, fora do pooler, por VPN ou IP; conexões e comandos auditados; revogação efetiva na próxima conexão; pentest do caminho; TR-1067 atendido | ED-55 (3) |
| GIS-11 | Traçados, isolamento, manobras com aviso e OS em lote, áreas de influência | B | G (5) | GIS-03, GIS-05, ED-36, ED-41 | montante e jusante; setores de manobra pré-calculados; isolamento com válvulas a fechar, inoperável atravessada, imóveis e economias afetados e clientes sensíveis primeiro (teste em massa sintética com resultado conhecido); plano de manobra com aviso pela ED-41 e OS de fechamento e reabertura em lote pela ED-36; baixa das OS atualiza o estado das válvulas e registra o período por imóvel; áreas de influência, bacias e polígonos de roteamento (SG-887, SG-889); conexões funcionais (SG-852); TR-1064 e TR-1065; SAMAE 2.27 sem falha nos itens de manobra; meta de tempo do §10.4 medida | ED-54 |
| GIS-12 | Hidráulica: EPANET e SWMM, exportação e importação | B | G (4) | GIS-03, GIS-05, GIS-13 | `.inp` EPANET por zona, estado e data, com rugosidade do material e demanda do consumo faturado; arquivo abre e roda na biblioteca do EPANET 2.2 no CI; `.inp` SWMM 5.2 de esgoto e drenagem com junções, condutos, armazenamento, *outfalls*, *subcatchments* e *inlets*, executado na biblioteca do SWMM no CI; importação de `.inp` EPANET e SWMM como pacote com SRS declarado (SG-1339, parte INP); mapeamento tipo → elemento por tenant; ida e volta preserva o código das feições (teste); view de compatibilidade e entidade antiga removidas (M5, M6) | ED-63 |
| GIS-13 | Drenagem | B | G (3) | GIS-02, GIS-03, GIS-09 | catálogo do §4.4 carregado; regras de conectividade e validações de declividade e sentido para drenagem; estado de conservação, obstrução, foto e inspeção (NR 12/2025); sub-bacias com atributos do SWMM; temático de estado de conservação; relatório do cadastro de drenagem com extensão por material e estado; TR-1048 | ED-64 |
| GIS-14 | Resíduos sólidos | B | G (3) | GIS-02, GIS-06, ED-25 | catálogo do §4.5 carregado; contêineres, PEV, ecopontos, lixeiras, pontos de coleta e de descarte irregular com foto e OS de limpeza; setores de coleta; rotas sobre o grafo de logradouros com pgRouting (sequência, extensão, tempo); imóvel ligado ao setor para a taxa (ED-25); veículo por catálogo até a ED-59; TR-1049; camadas de resíduos da importação (SG-1340, parte) | ED-65 |
| GIS-15 | Campo GIS no app Capacitor | B | G (5) | ED-18, ED-20, GIS-09 | §8 com teste instrumentado: pacote offline por área, edição como deltas em pacote `FIELD`, reenvio sem duplicar, conflito por atributo na fila; GNSS externo por Bluetooth com tipo de solução e PDOP; as-built recusado sem RTK fixo dentro da meta; altitude convertida no servidor; fotos com posição, azimute e hash varridas; croqui com amarrações; dispositivo registrado e bloqueio remoto (ADR-0030); jornada sem rede em aparelho Android | ED-66 |
| GIS-16 | Exportação e devolução da base | B | G (2) | GIS-06 | formatos e perfis do §9.3 com teste de leitura em GDAL e QGIS; metadado MGB em toda exportação; exportação com camada pessoal registrada; devolução do §9.4 dentro do prazo no ensaio de saída; SG-1365 por KML | ED-67 (2) |
| GIS-17 | OGC autenticado por tenant: OGC API – Features e WFS 2.0 na `api`, WMS pelo GeoServer | B | G (4; 3 na v2.10, +1 do WMS servidor na v2.11; mantido com o GeoServer na v2.12) | GIS-04, GIS-06 | partes 1 a 3 da OGC API – Features e WFS 2.0 (consulta e `Transaction` no pacote) na `api`; WMS 1.3.0 (`GetCapabilities`, `GetMap`, `GetFeatureInfo`) pelo GeoServer, nos itens 1 a 5 do §7.2: só leitura, acesso só pela `api`, workspace e role de banco somente leitura por tenant (ou views filtradas pelo tenant da role no schema compartilhado), configuração gerada pela API REST e SLD gerado de `gis_style` (teste compara as três saídas de estilo); todos sem acesso anônimo, autenticados por tenant e com permissões por camada; requisição de um tenant nunca lê camada de outro, nem pela `api` nem pela role do workspace, em schema próprio e compartilhado (teste); credencial de terceiro e publicação pública só com rede, ativos e camadas agregadas pelo §12, item 3 (teste); conformidade conferida no TEAM Engine nas partes implementadas; consumo pelo QGIS e por clientes WMS e WFS de teste; SG-1357; GeoServer como único componente novo, pela exceção do ADR-0010 (v2.12) | ED-67 (4) |
| GIS-18 | Importação ampliada e implantação do GIS | B | G (2) | GIS-07, GIS-12 | DGN v7, INP do EPANET e do SWMM e CSV de pontos GNSS; DGN v8 pela conversão assistida da implantação (§9.1, v2.11); casamento de unidades por matrícula ou endereço com fila de revisão (SG-1349); conciliação base cadastral × desenho (SG-1356); plano de implantação em três etapas (SG-1334) e roteiro de treinamento (SG-884, SG-885); migração de amostra de rede em até 60 dias no ensaio | ED-55 (2) |
| GIS-19 | Ativos, manutenção e inventário regulatório | C | G (4) | GIS-09, GIS-11, ED-32, ED-36 | códigos MCPSB por feição e extensão por subdivisão da NR 16/2026; conciliação GIS × patrimônio exportada à ED-32; manutenção preventiva e corretiva ligada à feição; risco por probabilidade × consequência com plano de renovação; bens reversíveis (NR 3/2023); TR-155, TR-1010, SG-1180 | ED-56 |

### 13.4 Mapa entre entregas GIS e ED

| ED | Antes (consolidado 18, v2.9) | Agora (v2.10) | Fase ou onda | Esforço |
|---|---|---|---|---|
| ED-13 | ligações como entidade, 3 | base da ligação na GIS-05 (P2); o restante na onda A | P2 e onda A | 0,5 + 2,5 |
| ED-52 | GIS web, onda B, 4 | núcleo do GIS: GIS-01 a GIS-06 e GIS-08 | P1 a P3 | 17,5 |
| ED-53 | edição e integração, onda B, 5 | GIS-09, edição web completa | onda A | 5 |
| ED-54 | traçado e manobras, onda B, 4 | GIS-11 | onda B | 5 |
| ED-55 | migração DWG e QGIS, onda B, 4 | GIS-07 (núcleo) + GIS-10 e GIS-18 (trilha) | P3 e onda B | 4 + 5 |
| ED-56 | ativos, onda C, 3 | GIS-19, com NR 16/2026 | onda C | 4 |
| ED-63 (nova) | — | GIS-12, EPANET e SWMM | onda B | 4 |
| ED-64 (nova) | — | GIS-13, drenagem | onda B | 3 |
| ED-65 (nova) | — | GIS-14, resíduos sólidos | onda B | 3 |
| ED-66 (nova) | — | GIS-15, campo GIS | onda B | 5 |
| ED-67 (nova) | — | GIS-16 e GIS-17, exportação, devolução e OGC autenticado por tenant (Features e WFS na `api`, WMS pelo GeoServer) | onda B | 6 (5 na v2.10) |

### 13.5 Esforço

| Bloco | v2.9 | v2.10 | v2.11 | Diferença (v2.9 → v2.11) |
|---|---|---|---|---|
| Antes do P4 (P0 a P3) | 13,3 | 35,3 | 35,3 | +22 (núcleo do GIS) |
| Onda A | 116,4 | 120,9 | 120,9 | +5 (ED-53) −0,5 (ED-13) |
| Onda B | 74,5 | 87,5 | 88,5 | −4 (ED-52) −5 (ED-53) +1 (ED-54) +1 (ED-55) +20 (ED-63 a ED-67) +1 (WMS servidor na ED-67, v2.11) |
| Onda C | 15,4 | 16,4 | 16,4 | +1 (ED-56) |
| Trilha do P4 | 206,3 | 224,8 | 225,8 | +19,5 |
| Até 2028, com o P0 a P3 | 219,6 | 260,1 | 261,1 | +41,5 |
| Prontidão de POC (ondas A e B e tarefas originais do P4, ≈ 8,4) | ≈ 199 | ≈ 216,8 | ≈ 217,8 | +18,5 |
| Trilha completa e tarefas originais do P4 | ≈ 215 | ≈ 233,2 | ≈ 234,2 | +19,5 |

GIS no total: 62 semanas-pessoa (22 antes do P4 e 40 na trilha), contra 20 das ED-52 a ED-56 na v2.9 e 61 na v2.10.
Os valores indicam esforço por entrega e por onda, sem conversão em equipe (v2.12).

### 13.6 Calendário

Desde a v2.12, este projeto trata do que será feito e de como, sem análise de equipe nem de capacidade (ADR-0029,
revogado; as projeções da v2.11 com o perfil 3 e o gatilho da quinta contratação saíram). As datas são metas de
calendário do plano: GIS-01 e GIS-02 no fim do P1 (M6, 29/01/2027); GIS-03 a GIS-05 no fim do P2 (M7, 26/03/2027);
GIS-06 a GIS-08 no fim do P3 (M8, 28/05/2027), antes da homologação local (M9 e M10); GIS-09 na onda A e GIS-10 a
GIS-18 na onda B, a partir de 02/08/2027, com a prontidão de POC por volta de meados de agosto de 2028; GIS-19 na onda
C, com a trilha completa até 30/09/2028. O núcleo não está no caminho do faturamento paralelo (GL.2) e não depende dele.

---

## 14. Riscos e pendências

### 14.1 Riscos

| Risco | Efeito | Mitigação |
|---|---|---|
| Regra de negócio em gatilhos (topologia, validação, histórico, pacote) | erro num gatilho afeta web, QGIS, app e importação ao mesmo tempo | teste de banco por gatilho, inclusive por SQL direto; revisão de cada gatilho; pentest do caminho QGIS |
| Catálogo com valores sem norma conferida | peça errada como padrão de fábrica; perda de confiança da prestadora | marca `REFERENCE_TO_VALIDATE`, item não ativo como padrão antes da validação, curadoria com as normas compradas no início do P1 (plano P1.28, v2.11), perfis Corsan e Saneago como referência |
| Qualidade do legado (DWG sem referência, sem cotas, desenho representativo) | rede aproximada tratada como levantada | `accuracy_status` e temático de precisão; relatório ISO 19157 por lote; amostragem RTK recomendada na implantação |
| Topologia em lote lenta | importação de municípios grandes fora do prazo de implantação | snap e limpeza em rotina de conjunto no staging; meta de 50 km em 2 h medida na GIS-07 |
| Tiles privados vazando entre tenants pelo cache | incidente de dado pessoal | chave com tenant, sem CDN pública, teste de isolamento do cache |
| Extensões no RDS (`pgrouting`, `pg_jsonschema`, `pgaudit`) e SRID 5396 | recurso indisponível no lançamento | conferência documental na P0.26 e prática na LC.1; alternativas: validação PL/pgSQL no lugar do `pg_jsonschema`, log do PostgreSQL no lugar do pgAudit |
| Troca do modelo atual quebra o EPANET | perda do único fluxo hoje testado | view de compatibilidade (M3), testes do builder refeitos (M4), remoção só depois de M5 |
| Escrita do QGIS fora da `api` | caminho de alteração sem as regras da aplicação | escrita só por views que gravam no pacote, revisão obrigatória, role sem privilégio, endpoint restrito, auditoria, pentest |
| Licença do ODA File Converter e DGN v8 | etapa de migração sem ferramenta | licença conferida na P1.25; alternativa: SDK licenciado da ODA, com custo; DGN v8 por conversão assistida na implantação, com ferramenta do cliente ou do Ziip (P-6, resolvida na v2.11) |
| Mapa base Google licenciado exigido em edital | custo por visualização acima do teto do SAMAE (R$ 7.636,74/mês com Google Maps) | OpenStreetMap como padrão; Google só com licença e repasse no contrato (ED-10) |
| Rede real do piloto no ambiente local (P-7, resolvida na v2.11) | infraestrutura crítica fora do controle do cliente; reidentificação de imóvel pela ponta do ramal ou do cavalete | autorização no termo de cooperação, acesso restrito; ligações, ramais, cavaletes e imóveis só sintéticos ou anonimizados na origem, com coordenada deslocada dentro da quadra ou agregada por quadra e método registrado por camada; lote sem o método recusado (§12, item 8) |
| GeoServer para o WMS servidor (v2.12) | segunda pilha de mapas, com vulnerabilidades próprias; leitura cruzada entre tenants por erro de configuração; simbologia diferente da web e do QGIS | só leitura, sem porta publicada, acesso só pela `api`; workspace e role de banco somente leitura por tenant, ou views filtradas pelo tenant da role; configuração gerada pela API REST, nunca manual; teste de isolamento no CI pela `api` e pela role; SLD gerado de `gis_style` com teste das três saídas; versão fixada e atualização de segurança (§7.2; exceção no ADR-0010) |

### 14.2 Pendências para o dono do produto

**Resolvidas pelo dono do produto em 12/10/2026 (cartões, plano v2.11 e v2.12)**

P-1 (capacidade para o núcleo do GIS) e P-2 (gatilho da quinta contratação), resolvidas na v2.11, saíram na v2.12,
com o ADR-0029 revogado: o projeto não trata de equipe nem de capacidade. P-9 (falta de capacidade no núcleo) saiu
pela mesma decisão.

| # | Pendência | Decisão | Onde foi aplicada |
|---|---|---|---|
| P-3 | Base da ligação antecipada | confirmada na P2.23 | §3.11; plano P2.23 |
| P-4 | Piso de agregação para publicação externa | fora do backoffice autenticado, imóveis e ligações só agregados por quadra ou setor com no mínimo 5 imóveis; rede e ativos publicáveis; `gis.public_aggregation_min_units`, classe c, piso 5 | §3.10, §12 (item 3), GIS-08, GIS-17; PARAMETROS-DO-TENANT |
| P-5 | Compra das normas para a curadoria do catálogo | comprar no início do P1, antes do catálogo (plano P1.28, custo a estimar) | §4.1; plano P1.27, P1.28 |
| P-6 | Conversão de DGN v8 | conversão assistida na implantação, para DWG ou DXF, com ferramenta do cliente ou do Ziip; importador com DGN v7, DWG, DXF, SHP, GPKG e INP do EPANET e do SWMM | §1.1, §9.1, GIS-07, GIS-18 |
| P-7 | Rede do piloto no ambiente local | entram redes, peças e estruturas reais; ligações, ramais, cavaletes e imóveis sintéticos ou anonimizados, com coordenada deslocada ou agregada por quadra; regra do ADR-0033, não exceção de dado pessoal | ADR-0033 (ajuste v2.11); §12 (item 8), GIS-07; plano P0.30, P0.45, P3.26, GL.1 |
| P-8 (WMS) | WMS servidor | OGC API – Features e WMS autenticados por tenant na onda B, junto do GIS avançado (+1 semana-pessoa na GIS-17); na v2.12, o WMS passa ao GeoServer, com workspace e role de banco por tenant, acesso só pela `api` e SLD gerado de `gis_style`, e a OGC API – Features continua na `api` | §7.2, GIS-17; ADR-0010 e ADR-0034 (ajustes "GIS, v2.12"); plano ED-67 |

**Em aberto**

| # | Pendência | Opções | Até | Bloqueia |
|---|---|---|---|---|
| P-8 (WCS) | WCS (F10 do 19e) | sob demanda, por exceção no ADR-0010, ou recusar com pedido de esclarecimento | quando um edital exigir | — |
