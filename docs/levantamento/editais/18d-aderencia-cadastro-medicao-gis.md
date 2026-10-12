# 18d — Aderência aos editais: cadastro, leitura/medição e GIS

Data: 12/10/2026. Produto: Ziip SAN (código interno SIGSAN, renomeado `ziipsan` na P0.33). Escopo desta matriz: cadastro
(clientes, imóveis, ligações, economias, categorias, logradouros, quadras, setores, rotas e hidrômetro como cadastro),
leitura e medição (rotas, app de leitura e emissão simultânea, impressão térmica, crítica, consumo médio e estimado,
telemetria, hidrometria e troca de medidores) e GIS/SIG (cadastro técnico, redes, PostGIS, QGIS, DWG, manobras e
camadas). Os demais temas dos editais ficam para as matrizes das outras áreas.

## 0. Fontes, método e convenções

**Requisitos.** `18a-videira-termo-de-referencia.md` (IDs `TR-`, VISAN, Videira/SC, PE 06/2026), `18c-sigcos-pe-2-2026.md`
(IDs `SG-`, SAMAE de São Ludgero/SC, PE 2/2026) e `18b-videira-edital-etp-precos.md` (IDs `ED-`, só os dois itens da
área: ED-058 e ED-080). Seleção: todas as linhas cuja categoria é de cadastro, hidrometria, leitura, leitura e
faturamento móvel, revisão de consumo, telemetria, app móvel/GPS ou GIS (inclusive a implantação e a migração do GIS no
SAMAE), mais as linhas de outras categorias cujo texto é da área (atualização cadastral no atendimento, pesquisa de
logradouro, OS no mapa, relatórios e painéis de cadastro e leitura, autoleitura, QR Code na conta emitida em campo).
Revisão de consumo entrou por estar classificada junto da leitura nos dois termos e por ser crítica na POC do SAMAE
(2.12.5); se a matriz de faturamento também a cobrir, vale a desta. Ficaram fora, com o motivo, os itens listados no §5.

**Uma linha por requisito.** Requisitos equivalentes dos dois editais foram juntados numa linha (o TR da VISAN e o TR
do SAMAE derivam do mesmo modelo e repetem a maior parte do texto); a coluna IDs traz todos. Diferenças de redação
relevantes aparecem no resumo (ex.: envio a cada 50 imóveis na VISAN e a cada 10 no SAMAE).

**Estado do produto.** Código lido em 12/10/2026 e documentos `docs/PLANO-EXECUCAO-PDCA-R0-R9.md` (v2.2),
`docs/arquitetura/*.md`, `docs/DIARIO-DE-BORDO.md` §18 e levantamentos `01`, `02`, `05`, `06`, `07` e `15c`.
Abreviações usadas na coluna de evidência:

| Sigla | Onde | O que há |
|---|---|---|
| REG | `commercial/sigsan-registry` (migrations `V3_1`–`V3_3`) | Território (localidade, setor, quadra, rota), catálogos (categoria, subcategoria, situações de água e esgoto), cliente com CPF/CNPJ cifrado e índice cego, imóvel com matrícula e DV, vínculo cliente-imóvel com vigência, histórico. API REST `/api/registry/*` com teste E2E em banco real |
| MET | `operations/sigsan-metering` (`V5_1`, `V5_2`) | Tabelas de hidrômetro, instalação, leitura, anormalidades e consumo; regras puras e testadas (cascata de consumo, média, mínimo, não medido, faixa esperada, virada, esgoto, rateio de condomínio). **Sem serviço, API, lote nem tela** |
| GIS | `intelligence/sigsan-gis` (`V2_3`) | `gis_network_element` (geometria SIRGAS 2000, índice GIST, ~40 tipos de elemento), montagem de rede e escritor `.inp` do EPANET, tabelas de simulação. **Sem API nem tela; sem solver** |
| LOS | `intelligence/sigsan-losses` (`V2_2`) | `dma_district` com polígono, balanço hídrico e pressões |
| BIL | `commercial/sigsan-billing` (`V4_1`) | Tarifas com vigência e `TariffEngine` puro; sem API |
| FE | `frontend/src/pages/cadastro` | Telas de imóvel (assistente), cliente, território e catálogos; `MapPointPicker` com MapLibre |
| V1_1 | `platform/sigsan-platform` | `addresses` (polimórfica, com tipo), `documents` e `service_orders`, sem serviço |

Observação de caminho: os módulos `sigsan-gis` e `sigsan-losses` estão em `intelligence/`, não em `operations/`; as pastas
`operations/sigsan-reading` e `operations/sigsan-field` existem vazias e fora do reator Maven.

**Situação no código hoje.** **Atende**: modelo, regra e API existem e funcionam ponta a ponta. **Parcial**: existe
modelo de dados ou regra pura, mas falta serviço, API, tela ou parte dos atributos. **Não atende**: nada que sirva ao
requisito. Ressalva que vale para toda a seção de cadastro: as telas de cadastro de cliente e de imóvel estão
bloqueadas até a P1.3, porque pedem `customer-types` e `property-profiles`, que ainda não têm tabela no backend
(diário §18, E5/E6). A API grava; numa POC, porém, a demonstração é pela tela.

**Situação no plano.** Número da tarefa do plano v2.2 (`P1.3`, `P2.5`...). "(parcial)" indica que a tarefa cobre só parte
do requisito; o restante está na coluna de lacuna. "Não planejado" significa que nenhuma tarefa de P0 a P4 o cobre.
"2028 (R9, sob demanda)" é o GIS e o incidente de rede, que o plano deixa fora das fases P0–P4 (§4, "2028, sob demanda");
para efeito de contagem, esses itens entram como não planejados. "n/a" marca obrigação comercial ou de implantação que
não é função do software.

**POC?** Como consta na extração. VISAN: "Sim (II-x)" quando o item é citado na alínea x do Anexo II; "Possível" quando
está no intervalo 5.1–23.1 que a comissão pode cobrar (3.2.8–3.2.9); "Não" fora dele (o item 24, SIG/GIS, não é
avaliado). SAMAE: todas as linhas desta área estão em seções críticas (2.8 a 2.12, 2.17, 2.27, 2.28 e 3), ou seja,
"Sim (crítico)": a falha em qualquer uma delas desclassifica, sem implementação posterior (Edital 7.7.7–7.7.10).

**Esforço** (escala do plano, §4): **P** até 2 dias, **M** até 1 semana, **G** mais de 1 semana, para uma pessoa com
agentes. "—" quando nada falta.

## 1. Resumo

### 1.1 Totais

Contagem por ID de requisito (cada ID conta uma vez, com a situação da linha em que está):

| Edital | Requisitos da área | Atende | Parcial | Não atende | Não planejados |
|---|---|---|---|---|---|
| VISAN, Termo de Referência (TR-) | 242 | 14 | 77 | 151 | 179 |
| SAMAE São Ludgero (SG-) | 332 | 16 | 105 | 211 | 251 |
| VISAN, edital/ETP (ED-) | 2 | 0 | 0 | 2 | 1 |
| **Total** | **576** | **30** (5%) | **182** (32%) | **364** (63%) | **431** (75%) |

Na matriz, os 576 IDs ocupam **368 linhas** (requisitos equivalentes juntados): **16 Atende**, **111 Parcial**, **241 Não atende**; **281 linhas não planejadas** (das quais 88 só no "2028, sob demanda") e 2 n/a.

### 1.2 Por bloco (linhas da matriz)

| Bloco | Linhas | Atende | Parcial | Não atende | Não planejadas |
|---|---|---|---|---|---|
| Cadastro (cliente, imóvel, endereço, catálogos, ligações) | 99 | 14 | 36 | 49 | 52 |
| Hidrometria | 16 | 0 | 12 | 4 | 16 |
| Leitura no servidor | 22 | 1 | 13 | 8 | 6 |
| Acompanhamento, relatórios e comunicação da leitura | 19 | 0 | 1 | 18 | 18 |
| App de leitura e emissão simultânea e app offline | 39 | 0 | 9 | 30 | 21 |
| Revisão de consumo | 29 | 0 | 1 | 28 | 26 |
| Relatórios e painéis da área | 27 | 0 | 5 | 22 | 27 |
| GIS | 83 | 1 | 33 | 49 | 81 |
| Migração do cadastro técnico e QGIS | 34 | 0 | 1 | 33 | 34 |

### 1.3 Leitura do resultado

1. **Cadastro: base certa, cobertura incompleta.** Matrícula com DV, inscrição única, vínculo cliente-imóvel com
   vigência, situações com flags, CPF/CNPJ cifrado e histórico atendem ao núcleo dos dois editais. Faltam logradouro,
   bairro e município normalizados, ligação como entidade, atributos do imóvel e do cliente (moradores, pontos, RG,
   filiação), anexos e os catálogos operacionais da lista do SAMAE (2.8.10). As telas de cliente e imóvel só destravam
   na P1.3.
2. **Medição: regra pronta, sem operação.** O motor de consumo cobre o que os editais pedem do cálculo (mínimo por
   economias e por pontos, média, faixa esperada, virada, troca de hidrômetro, fonte alternativa para esgoto, isenção,
   rateio de condomínio), com testes. Não há serviço, API, lote, grupo de faturamento, massa de leitura nem crítica
   com retenção; isso é a P2.5 e a P2.7. O parque de hidrômetros tem tabelas, mas nenhuma tarefa do plano o expõe.
3. **App de leitura e emissão simultânea: o maior buraco.** É exigido pelos dois editais (VISAN 8.1 e alínea e do
   Anexo II; SAMAE 2.11, crítico), e o plano só prevê, na P4.4 (ago–dez/2027), um PWA de leitura offline, sem emissão
   da conta, impressão térmica, comunicados nem rastreamento.
4. **GIS: modelo e PostGIS existem; o produto não.** Há `gis_network_element` com geometria e o escritor do EPANET,
   mas nenhuma API, tela, importador DWG ou acesso pelo QGIS. O plano deixa o GIS para 2028, sob demanda. Na VISAN o
   GIS fica fora da POC, mas a migração do DWG para PostGIS e o acesso pelo QGIS são obrigatórios nos 90 dias de
   implantação; no SAMAE a seção 2.27 e a implantação do GIS (3.11.10) são críticas na POC. A pilha escolhida
   (PostgreSQL 17 + PostGIS 3.5, MapLibre) é a que os dois editais pedem, o que reduz o risco técnico.
5. **Efeito sobre as POCs.** VISAN: a área responde por 15 itens do Anexo II (c: 4, d: 3, e: 7, g: 1), ou 30 dos 162
   pontos, e a aprovação admite perder no máximo 16. Só as alíneas d (hidrômetros), e (leitura móvel com impressão
   Zebra) e g (revisão de consumo) somam 22 pontos hoje não demonstráveis; mesmo com a P2.5 concluída, a alínea e
   continua sem pontuar sem a E6. SAMAE: a falha em qualquer item crítico desclassifica, e 2.11, 2.12.5 e 2.27 não
   estão no plano. Com o plano atual, o Ziip SAN não passa em nenhuma das duas POCs por causa desta área.
6. **O que já cabe no plano.** A P1.3 resolve a maior parte do cadastro; a P2.5 e a P2.7, o ciclo de leitura no
   servidor; a P0.4, o PostGIS. O restante está agrupado em 16 entregas no §3. As que decidem as POCs são E4
   (hidrometria), E6 (emissão simultânea), E9 (revisão de consumo), E12 e E15 (GIS e migração).


## 2. Matriz de aderência

Colunas: IDs; requisito (resumo fiel); POC (§0); situação no código com a evidência; situação no plano v2.2; lacuna ou ação proposta; esforço da lacuna.

| # | IDs | Requisito (resumo) | POC? | Código hoje | Plano | Lacuna / ação | Esf. |
|---|---|---|---|---|---|---|---|
| 1 | TR-129, SG-066 | Conjunto de cadastros necessários à operação do produto | TR: Possível; SG: Sim (crítico) | **Parcial**: REG: território, catálogos, cliente e imóvel com API; faltam logradouro, ligação, hidrômetro (API) e catálogos operacionais | P1.3 (parcial) | Completar os cadastros das linhas seguintes | M |
| 2 | TR-130, SG-067 | Atributos mínimos do cliente/usuário | TR: Sim (II-c); SG: Sim (crítico) | **Parcial**: REG `customers` e `customer_contacts` com API; tela bloqueada até a P1.3 (sem `customer-types`) | P1.3 | RG, filiação e endereço do cliente; destravar a tela | M |
| 3 | TR-131, SG-068 | Nome do cliente | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: REG `customers.name`, API `/customers` (E2E); tela bloqueada até a P1.3 | P1.3 (tela) | Destravar a tela de cliente | P |
| 4 | TR-132 | Tipo de usuário Padrão/Baixa Renda (tarifa social) | Possível | **Não atende**: Sem campo; tarifa social é satélite | P1.5 | Enquadramento por imóvel com vigência, ligado ao satélite da P1.5 | P |
| 5 | TR-133, SG-069 | CPF ou CNPJ | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: REG: cifra AES-GCM, índice cego, máscara, busca e revelação com finalidade | P0.11, P1.3 (tela) | Destravar a tela de cliente | P |
| 6 | TR-134, SG-070 | Número do RG | TR: Sim (II-c); SG: Sim (crítico) | **Não atende**: Sem coluna (07 §4.3: coletar só sem CPF ou na tarifa social) | Não planejado | Campo opcional cifrado, com finalidade registrada (E1) | P |
| 7 | TR-135, SG-071 | Filiação | TR: Sim (II-c); SG: Sim (crítico) | **Não atende**: Sem coluna (07 §4.3: nome da mãe só para desambiguar) | Não planejado | Campo opcional, com base legal; avaliar minimização (E1) | P |
| 8 | TR-136, SG-072 | Telefone residencial | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: REG `customer_contacts` PHONE com rótulo de tipo | P1.3 (tela) | Tipo como lista fechada (residencial, comercial, celular) | P |
| 9 | TR-137, SG-073 | Telefone comercial | TR: Possível; SG: Sim (crítico) | **Atende**: Idem | P1.3 (tela) | Idem | P |
| 10 | TR-138, SG-074 | Telefone celular | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: Idem | P1.3 (tela) | Idem | P |
| 11 | TR-139, SG-075 | Data de nascimento | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: REG `customers.birth_date` | P1.3 (tela) | — | P |
| 12 | TR-140, SG-076 | E-mail | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: REG `customer_contacts` EMAIL com `consent_id` | P1.3 (tela) | — | P |
| 13 | TR-142, SG-077, SG-087 | Até três endereços (principal, alternativo/secundário, correspondência), com escolha do de correspondência | TR: Sim (II-c); SG: Sim (crítico) | **Parcial**: `addresses` (V1_1) tem tipos PHYSICAL, CORRESPONDENCE, ALTERNATIVE; a API grava só o endereço físico do imóvel; cliente sem endereço | P1.3 (endereço) | Vários endereços por imóvel e cliente; eleger o de correspondência | M |
| 14 | TR-141, SG-078 | Gravar imagens dos documentos do cliente | TR: Possível; SG: Sim (crítico) | **Não atende**: Tabela `documents` (V1_1) sem serviço nem armazenamento de objetos | Não planejado (P1.20 dá a base de upload) | Anexos no cliente com varredura, retenção e acesso registrado (E1) | M |
| 15 | TR-143 | Cadastro próprio por usuário e cobrança das faturas por CPF | Possível | **Parcial**: Cliente único por documento (índice cego) e vínculo com o imóvel; a conta é por imóvel | P1.3, P2.5 (parcial) | Consulta e cobrança consolidadas por CPF/CNPJ | M |
| 16 | TR-144 | Integração com API do governo para validar Bolsa Família/BPC | Possível | **Não atende**: Nenhuma integração | Não planejado (P1.5 só aplica a regra) | Adaptador para a consulta oficial, se houver API disponível ao prestador (E16) | M |
| 17 | TR-145, SG-085 | Cadastro (georreferenciado) da unidade de consumo; criar ou alterar ligação | TR: Sim (II-c); SG: Sim (crítico) | **Parcial**: `consumer_units` com `coordinates` (PostGIS) e API; a tela captura lat/long, mas `AddressInput` não a recebe; ligação não é entidade | P1.3 (parcial) | Expor a coordenada na API; entidade de ligação (linha das ligações) | M |
| 18 | TR-146, SG-086 | Atributos do imóvel: lote, unidade, moradores, tomadas, área, inscrição imobiliária, caixa d'água, piscina etc. | TR: Sim (II-c); SG: Sim (crítico) | **Parcial**: Lote, sublote e área construída existem; moradores, pontos, IPTU, reservatório e piscina não | P1.3 (parcial) | Características do imóvel (07 §4.5) com validação | M |
| 19 | TR-147 | Informações gerais do imóvel | Possível | **Atende**: `consumer_units.notes` (750) pela API; tela bloqueada até a P1.3 (sem `property-profiles`) | P1.3 (tela) | — | P |
| 20 | TR-148, SG-088 | Unidade identificada por código único (matrícula) | TR: Sim (II-c); SG: Sim (crítico) | **Atende**: `Matricula.java` (DV módulo 11), índice único `(tenant_id, matricula)` | P1.4 (sequência) | — | — |
| 21 | TR-149, SG-089 | Identificar unidade de usuário especial (grande usuário, órgão público) | TR: Possível; SG: Sim (crítico) | **Parcial**: `categories.public_entity`; sem perfil de grande consumidor | P1.3 (`property_profiles`) | Flags no perfil do imóvel | P |
| 22 | TR-150, SG-090 | Vínculo usuário/proprietário/inquilino com tipo e vigência; reversão automática ao fim da locação | TR: Sim (II-c); SG: Sim (crítico) | **Parcial**: `unit_customers` com papel e vigência; sem reversão automática | P1.3 (parcial) | Rotina que encerra o vínculo do inquilino e restaura o anterior (depende da P2.1) | P |
| 23 | TR-151, SG-091 | Controle das unidades de um órgão pagador | TR: Possível; SG: Sim (crítico) | **Parcial**: Cliente RESPONSIBLE ligado a N unidades (`unitCount`); sem consulta nem conta agrupada | Não planejado | Consulta por responsável e emissão agrupada (E1) | M |
| 24 | TR-152, SG-092 | Cadastro individual de unidades de condomínio com medição individualizada | TR: Possível; SG: Sim (crítico) | **Parcial**: MET `CondominiumApportionment`; o cadastro não liga macro e micros | P2.5 (parcial) | `parent_unit_id` no imóvel e regra de mesma quadra | M |
| 25 | TR-153, SG-093 | Funcionalidades de cadastro e alteração para comercial, serviços e relacionamento | TR: Possível; SG: Sim (crítico) | **Parcial**: REG CRUD com auditoria e histórico; OS, atendimento e ligações ausentes | P1.3, P2.5, P3.4 | Cobertura pelos demais itens | G |
| 26 | TR-154 | Integração em tempo real entre cadastro georreferenciado de ativos e eventos de manutenção/vistoria | Possível | **Não atende**: `gis_network_element` sem vínculo com OS; `service_orders` sem serviço | 2028 (R9, sob demanda) | Ligar OS a elemento de rede e atualizar o ativo na baixa (E13) | G |
| 27 | TR-155 | Integração entre cadastro técnico e sistema contábil | Possível | **Não atende**: Nenhuma | Não planejado | Exportação de ativos com valor e depreciação para o contábil (E16) | M |
| 28 | SG-079 | Cadastro de município | Sim (crítico) | **Parcial**: `localities.municipality_ibge`; `addresses.city` em texto | P1.3 (endereço) | Catálogo IBGE de municípios | P |
| 29 | SG-080, SG-147 | Cadastro de bairro | Sim (crítico) | **Não atende**: `addresses.neighborhood` em texto livre | P1.3 (endereço) | Tabela de bairros por município | P |
| 30 | SG-081 | Cadastro de tipo de logradouro | Sim (crítico) | **Não atende**: Sem tabela | P1.3 (endereço) | Catálogo | P |
| 31 | SG-082, SG-145, SG-146 | Cadastro de logradouro (ruas) | Sim (crítico) | **Não atende**: `addresses.street` em texto livre | P1.3 (endereço) | Tabela `streets` com código, tipo, título, bairro e CEP | M |
| 32 | SG-083 | Cadastro de faixa de logradouro (numeração/CEP) | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Faixas por logradouro e lado (E1) | P |
| 33 | SG-084 | Cadastro de endereço | Sim (crítico) | **Parcial**: `addresses` com API para o imóvel; CEP com máscara, sem consulta | P1.3 (endereço, CEP) | Endereço do cliente e consulta de CEP | P |
| 34 | TR-777, SG-1004 | Termo de adesão emitido automaticamente no cadastro de nova unidade | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem gerador de documentos | Não planejado | Modelo de termo com dados do imóvel e do requerente, em PDF (E1) | P |
| 35 | TR-578, SG-555 | Atualizar dados técnicos da ligação de água, com observações | TR: Possível; SG: Sim (crítico) | **Não atende**: Ligação não modelada (07 §4.6) | Não planejado | Entidade de ligação de água e esgoto com ações por OS (E3) | M |
| 36 | TR-582, SG-559 | Atualizar localização (setor, rota, sequência, quadra, lote) | TR: Possível; SG: Sim (crítico) | **Atende**: `PUT /consumer-units/{id}` com unicidade da inscrição e histórico | — | Permissão específica para alterar inscrição (07 §4.8) | P |
| 37 | TR-585, SG-562 | Atualizar categoria, tipo de tarifa e número de economias | TR: Possível; SG: Sim (crítico) | **Parcial**: Subcategorias e economias pela API; tipo de tarifa/perfil ausente | P1.3 | Perfil do imóvel e tipo de tarifa | P |
| 38 | TR-586, SG-563 | Atualizar setor, rota e sequência de leitura da unidade | TR: Possível; SG: Sim (crítico) | **Atende**: `readingRouteId` e `readingSequence` no `UnitInput` | — | — | — |
| 39 | TR-611, SG-591 | Pesquisar logradouro por código ou por UF, município, bairro e nome | TR: Possível; SG: Sim (crítico) | **Não atende**: Logradouro em texto; há busca de imóvel por CEP e faixa de número | P1.3 (endereço) | Busca de logradouro sobre a tabela normalizada | M |
| 40 | SG-156 | Croqui que represente graficamente a posição da unidade | Sim (crítico) | **Parcial**: `MapPointPicker`/`MapCanvas` (MapLibre) escolhe o ponto; a API não o persiste; sem croqui | P1.3 (coordenada); croqui não planejado | Persistir a coordenada; croqui (desenho ou foto anotada) | M |
| 41 | SG-094 | Cadastro de funcionários | Sim (crítico) | **Parcial**: `users` (V1_1) e Keycloak; sem dados de funcionário (cargo, setor) | P0.13 | Ficha de funcionário separada do usuário de sistema | P |
| 42 | SG-095 | Cadastro de cargos | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E2) | P |
| 43 | SG-096 | Cadastro de categorias | Sim (crítico) | **Atende**: REG `categories`/`subcategories`, API e tela Catálogos | — | — | — |
| 44 | SG-097 | Cadastro de tarifas | Sim (crítico) | **Parcial**: BIL `tariffs`, `tariff_versions`, `tariff_blocks` (V4_1) e `TariffEngine`; sem API nem tela | P2.5 | API e tela de tarifas com vigência | M |
| 45 | SG-098 | Cadastro de diâmetro de cavalete | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da ligação (E3) | P |
| 46 | SG-099 | Cadastro de diâmetro de hidrômetro | Sim (crítico) | **Parcial**: `meters.nominal_diameter_mm` numérico; sem catálogo | Não planejado | Catálogo (E4) | P |
| 47 | SG-100 | Cadastro de diâmetro de ramal de água | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da ligação (E3) | P |
| 48 | SG-101 | Cadastro de diâmetro de ramal de esgoto | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da ligação (E3) | P |
| 49 | SG-102 | Cadastro de estado civil | Sim (crítico) | **Não atende**: Sem campo (07 §5.3: dado sem finalidade comercial) | Não planejado | Catálogo opcional; avaliar minimização (E2) | P |
| 50 | SG-103 | Cadastro de grupos de serviços | Sim (crítico) | **Não atende**: `service_orders.service_code_id` sem tabela | P3.4 (OS mínima) | Catálogo de serviços e grupos | P |
| 51 | SG-104 | Cadastro de veículos | Sim (crítico) | **Não atende**: `service_orders.vehicle_id` sem tabela | Não planejado | Catálogo de frota (E2) | P |
| 52 | SG-105 | Cadastro de material | Sim (crítico) | **Não atende**: `service_orders.materials_used` em JSONB | Não planejado | Catálogo com unidade de medida (E2) | P |
| 53 | SG-106 | Cadastro de material de cavalete | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da ligação (E3) | P |
| 54 | SG-107 | Cadastro de modelo de veículo | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E2) | P |
| 55 | SG-108 | Cadastro de motivo de cancelamento | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo do faturamento (E2) | P |
| 56 | SG-109 | Cadastro de motivo de crítica | Sim (crítico) | **Parcial**: MET `reading_anomalies` e `consumption_anomalies` (sem semente, sem API) | P2.5 | API e tela dos catálogos | P |
| 57 | SG-110 | Cadastro de motivo de emissão de fatura | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo do faturamento (E2) | P |
| 58 | SG-111 | Cadastro de motivo de estorno de pagamento | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 59 | SG-112 | Cadastro de motivo de liberação de compensação | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 60 | SG-113 | Cadastro de motivo de movimento de hidrômetro | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da hidrometria (E4) | P |
| 61 | SG-114 | Cadastro de motivo de número de lacre | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da hidrometria (E4) | P |
| 62 | SG-115 | Cadastro de motivo de reenvio de fatura em débito automático | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 63 | SG-116 | Cadastro de motivo de situação de fatura | Sim (crítico) | **Não atende**: `BillStatus` é enum fixo | Não planejado | Catálogo do faturamento (E2) | P |
| 64 | SG-117 | Cadastro de motivo de situação da ligação de água | Sim (crítico) | **Parcial**: `unit_status_history.reason` em texto livre | Não planejado | Catálogo de motivos (E3) | P |
| 65 | SG-118 | Cadastro de motivo de situação da ligação de esgoto | Sim (crítico) | **Parcial**: Idem | Não planejado | Catálogo de motivos (E3) | P |
| 66 | SG-119 | Cadastro de motivo de transferência de pagamento | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 67 | SG-120 | Cadastro de motivo de valores a compensar | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 68 | SG-121 | Cadastro de nacionalidade | Sim (crítico) | **Não atende**: Sem campo | Não planejado | Catálogo opcional; avaliar minimização (E2) | P |
| 69 | SG-122 | Cadastro de ocorrência de débito em conta | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da arrecadação (E2) | P |
| 70 | SG-123 | Cadastro de origem de leitura | Sim (crítico) | **Parcial**: `meter_readings.source` enum fixo (MANUAL, MOBILE, COLLECTOR, TELEMETRY) | P2.5 | Descrição configurável sobre o enum | P |
| 71 | SG-124 | Cadastro de posição de cavalete | Sim (crítico) | **Parcial**: `meter_installations.location_description` em texto | Não planejado | Catálogo (E3) | P |
| 72 | SG-125 | Cadastro de tipo de cavalete | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E3) | P |
| 73 | SG-126 | Cadastro de tipo de cliente | Sim (crítico) | **Não atende**: Tabela ausente (tela pede `customer-types`: 404) | P1.3 | `customer_types` (V3_6) | P |
| 74 | SG-127 | Cadastro de tipo de cobrança | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo da cobrança (E2) | P |
| 75 | SG-128 | Cadastro de tipo de coleta de leitura | Sim (crítico) | **Parcial**: Mesmo enum de origem de leitura | P2.5, P2.7 | Descrição configurável | P |
| 76 | SG-129 | Cadastro de tipo de consumo faturado | Sim (crítico) | **Parcial**: `consumption_type` (10 tipos, MET) | P2.5 | Descrição configurável | P |
| 77 | SG-130 | Cadastro de tipo de consumo lido | Sim (crítico) | **Parcial**: `reading_status` (REALIZED, NOT_REALIZED, CONFIRMED, CHANGED) | P2.5 | Descrição configurável | P |
| 78 | SG-131 | Cadastro de tipo de convênio bancário | Sim (crítico) | **Parcial**: `CollectionAgreement` (collection) sem API | P3.1 | API e tela | P |
| 79 | SG-132 | Cadastro de tipo de feriado | Sim (crítico) | **Parcial**: `holidays` (V6_1) com `scope` NATIONAL/STATE/MUNICIPAL e fonte legal; sem API | P0.19 (`V1_5`) | API e tela | P |
| 80 | SG-133 | Cadastro de tipo de outras fontes | Sim (crítico) | **Parcial**: Medição WELL no MET; catálogo `water-sources` só no frontend | P1.3 | Tabela no backend | P |
| 81 | SG-134 | Cadastro de tipo de grupo de faturamento | Sim (crítico) | **Não atende**: Grupo de faturamento inexistente | P2.5 (ciclo) | Grupos com calendário | M |
| 82 | SG-135 | Cadastro de tipo de hidrômetro | Sim (crítico) | **Não atende**: `meters.brand`/`model` em texto | Não planejado | Catálogo (E4) | P |
| 83 | SG-136 | Cadastro de tipo de ligação | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E3) | P |
| 84 | SG-137 | Cadastro de tipo de material | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E2) | P |
| 85 | SG-138 | Cadastro de tipo de pavimento de logradouro | Sim (crítico) | **Não atende**: Catálogo `pavements` só no frontend | Não planejado | Tabela no backend (E2) | P |
| 86 | SG-139 | Cadastro de tipo de pavimento de calçada | Sim (crítico) | **Não atende**: Idem | Não planejado | Tabela no backend (E2) | P |
| 87 | SG-140 | Cadastro de tipo de rede | Sim (crítico) | **Parcial**: `blocks.has_water_network`/`has_sewer_network`; `element_type` no GIS | Não planejado | Catálogo (material, função) (E2) | P |
| 88 | SG-141 | Cadastro de tipo de situação | Sim (crítico) | **Atende**: `water_statuses`/`sewer_statuses` com flags, API e tela | — | — | — |
| 89 | SG-142 | Cadastro de tipo de tarifa | Sim (crítico) | **Parcial**: BIL `tariffs` sem API | P2.5 | API e tela | P |
| 90 | SG-143 | Cadastro de tipo de unidade de medida | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E2) | P |
| 91 | SG-144 | Cadastro de tipo de veículo | Sim (crítico) | **Não atende**: Sem tabela | Não planejado | Catálogo (E2) | P |
| 92 | SG-148 | Cadastro de elevatórias de esgoto | Sim (crítico) | **Parcial**: GIS `element_type` LIFT_STATION; sem API | 2028 (R9, sob demanda) | Cadastro alfanumérico com geometria (E13) | P |
| 93 | SG-149 | Cadastro de elevatórias de água | Sim (crítico) | **Parcial**: GIS PUMP_STATION; sem API | 2028 (R9, sob demanda) | Idem (E13) | P |
| 94 | SG-150 | Cadastro de reservatório de água | Sim (crítico) | **Parcial**: GIS RESERVOIR/TANK; sem API | 2028 (R9, sob demanda) | Idem (E13) | P |
| 95 | SG-151 | Cadastro de ETA | Sim (crítico) | **Parcial**: GIS TREATMENT_PLANT; sem API | 2028 (R9, sob demanda) | Idem (E13) | P |
| 96 | SG-152 | Cadastro de ETE | Sim (crítico) | **Parcial**: GIS SEWER_TREATMENT_PLANT; sem API | 2028 (R9, sob demanda) | Idem (E13) | P |
| 97 | SG-153 | Cadastro de bacias de contribuição de esgoto | Sim (crítico) | **Não atende**: Sem tipo nem tabela | 2028 (R9, sob demanda) | Polígono de bacia ligado à rede (E14) | M |
| 98 | SG-154 | Cadastro de fossa séptica | Sim (crítico) | **Não atende**: Sem tipo | 2028 (R9, sob demanda) | Tipo de elemento e atributo no imóvel (E13) | P |
| 99 | TR-189, SG-155, SG-191 | Cadastro de setores e rotas de leitura | TR: Possível; SG: Sim (crítico) | **Atende**: REG `commercial-sectors` e `reading-routes`, API e tela Território | — | — | — |
| 100 | TR-156, SG-157 | Cadastro e gestão do ciclo de vida do hidrômetro | TR: Possível; SG: Sim (crítico) | **Parcial**: MET `meters` (situações) e `meter_installations` (histórico); sem serviço, API ou tela | Não planejado (P2.5 só usa a instalação no cálculo) | API e telas do parque (E4) | G |
| 101 | TR-157, SG-158 | Estoque e locais de armazenamento; um hidrômetro ativo por ligação | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: Situação AVAILABLE e EXCLUDE por unidade e tipo; sem local de armazenamento | Não planejado | Locais e movimentação de estoque (E4) | M |
| 102 | TR-158, SG-159 | Cadastro unitário e em lote por faixa de códigos | TR: Sim (II-d); SG: Sim (crítico) | **Não atende**: Só a tabela | Não planejado | Cadastro em lote com prefixo e faixa (E4) | P |
| 103 | TR-159, SG-160 | Identificação única e rastreio das movimentações | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: `meter_number` único; histórico de instalação; sem `meter_movements` | Não planejado | Movimentações com origem, destino e motivo (E4) | M |
| 104 | TR-160, SG-161 | Atributos técnicos (tipo, classe, ponteiros, vazões, tecnologia, diâmetro, marca, fabricação, aferição) | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: Marca, modelo, classe metrológica, diâmetro, dígitos, ano, Inmetro, última revisão; faltam vazões, tecnologia e pressão | Não planejado | Completar atributos e catálogos (E4) | P |
| 105 | TR-161, SG-162 | Número de lacre do hidrômetro | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: `meter_installations.seal_number`, sem API | Não planejado | Expor na instalação e na troca (E4) | P |
| 106 | TR-162, SG-163 | Histórico de aferições em forma de OS | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem aferição; OS sem serviço | Não planejado | Aferição como tipo de OS com laudo (depende da P3.4) (E4) | M |
| 107 | TR-163, SG-164 | Repositório de fotos de aferição e ocorrências, com tratamento de tamanho | TR: Possível; SG: Sim (crítico) | **Não atende**: `documents` sem serviço | Não planejado | Upload com redimensionamento (base na P1.20) (E4) | M |
| 108 | TR-164, SG-165 | Na troca, destino do anterior: estoque, manutenção ou sucata | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: Situações AVAILABLE, IN_MAINTENANCE, WRITTEN_OFF; sem operação de troca | Não planejado | Operação de troca com destino (E4) | P |
| 109 | TR-165, SG-166 | Histórico detalhado de hidrômetro por ligação | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: Índice `idx_installations_unit`; sem consulta | Não planejado | Consulta na ficha do imóvel (E4) | P |
| 110 | TR-166, SG-167 | Alerta de fim de validade (5/10 anos, Inmetro), relatórios e OS automática de troca | TR: Possível; SG: Sim (crítico) | **Não atende**: `last_revision_on` existe; sem rotina | Não planejado | Rotina de vencimento com OS em lote (depende da P3.4) (E4) | M |
| 111 | SG-168 | OS automática de substituição corretiva a partir de ocorrência de leitura | Sim (crítico) | **Parcial**: `reading_anomalies.generates_service_order`; sem OS | Não planejado | Gerar OS na crítica (depende da P3.4) (E4) | M |
| 112 | TR-168, SG-170 | Instalação de hidrômetro com OS automática (leitura de instalação, usuário, data, parecer) | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: `meter_installations.installed_reading`; OS sem serviço | Não planejado | Operação com OS (depende da P3.4) (E4) | M |
| 113 | TR-169, SG-171 | Retirada com OS automática e baixa no parque | TR: Sim (II-d); SG: Sim (crítico) | **Parcial**: `removed_on`/`removed_reading`; OS sem serviço | Não planejado | Idem (E4) | M |
| 114 | TR-170, SG-172 | Troca de hidrômetro com OS automática | TR: Possível; SG: Sim (crítico) | **Parcial**: `is_replacement`; troca no mesmo dia; cascata trata o consumo na substituição | Não planejado | Idem (E4) | M |
| 115 | TR-167, SG-169 | Receber leituras de sistema AMR por transmissão de dados | TR: Possível; SG: Sim (crítico) | **Parcial**: `meter_readings.source` aceita TELEMETRY; sem ingestão | Não planejado | Endpoint/arquivo de ingestão AMR (E10) | M |
| 116 | TR-171, SG-173 | Ciclo de leitura completo: geração da massa, distribuição aos dispositivos e retorno | TR: Possível; SG: Sim (crítico) | **Parcial**: MET valida o retorno (`ReadingValidator`) e calcula consumo; sem massa, distribuição nem orquestração | P2.5, P2.7 (`V5_3`), P4.4 | Coberto pela tarefa do plano | G |
| 117 | TR-172, SG-174 | Gestão dos roteiros de leitura (sequência) | TR: Possível; SG: Sim (crítico) | **Parcial**: `reading_routes` e `reading_sequence`; sem tela de roteiro | Não planejado | Tela de roteiro com reordenação (E5) | M |
| 118 | TR-173, SG-175 | Parametrizar roteiros por tipologia da localidade | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem parâmetro | Não planejado | Parâmetros de roteiro por localidade (E5) | P |
| 119 | TR-174, SG-176 | Atribuir rota e sequência a cada ligação | TR: Possível; SG: Sim (crítico) | **Atende**: Campos na API do imóvel; `requireReadingSequence` no tenant | — | — | — |
| 120 | TR-175, SG-177 | Gerar massa de leitura e distribuir por grupo de faturamento | TR: Sim (II-e); SG: Sim (crítico) | **Não atende**: Sem grupo de faturamento nem tarefa de leitura | P2.7 (`V5_3`), P4.4 | Coberto pela tarefa do plano | G |
| 121 | TR-176, SG-178 | Preservar a situação cadastral ao iniciar o campo | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem fotografia da carga | P2.7 (`V5_3`, implícito) | Congelar os dados da carga na tarefa de leitura | M |
| 122 | TR-177, SG-179 | Selecionar as unidades a ler pela situação cadastral | TR: Possível; SG: Sim (crítico) | **Parcial**: Flags `connected`/`supplies`; `suspend_reading_months` nas anormalidades | P2.5 | Seleção na geração da carga | P |
| 123 | TR-178, SG-180 | Consumo mínimo por economias (com hidrômetro) e por tomadas (sem hidrômetro) | TR: Possível; SG: Sim (crítico) | **Parcial**: MET `ConnectionMinimum` e `NonMeteredConsumption` (BY_UTILIZATION_POINTS), sem serviço | P2.5 | Orquestração no ciclo | P |
| 124 | TR-179, SG-181 | Faixa de leitura mínima e máxima pela média e pela tabela de faixas | TR: Possível; SG: Sim (crítico) | **Parcial**: MET `ExpectedReadingRange`/`ExpectedRangePolicy`, com virada | P2.5 | Orquestração no ciclo | P |
| 125 | TR-180, SG-182 | Processar automaticamente o retorno e gravar leituras, consumo, forma de coleta e ocorrências | TR: Sim (II-e); SG: Sim (crítico) | **Parcial**: `meter_readings` (origem, anormalidade, leiturista) e `consumption_records` com snapshot; sem endpoint | P2.5, P2.7, P2.3 | Coberto pela tarefa do plano | M |
| 126 | SG-183 | Faturas de condomínio com medição individualizada pela regra do prestador | Sim (crítico) | **Parcial**: MET `CondominiumApportionment` | P2.5 | Coberto pela tarefa do plano | M |
| 127 | SG-184 | Fórmula MFC = MMC − TMM | Sim (crítico) | **Parcial**: O rateio usa max(0, macro − soma dos micros), mas reparte por economia; não fatura a diferença ao condomínio | P2.5 | Modo "diferença faturada à área comum" | P |
| 128 | SG-185, SG-186 | Permitir alterar a regra/fórmula de leitura do condomínio | Sim (crítico) | **Não atende**: Regra fixa no código (só o teto de incremento é parâmetro) | Não planejado | Estratégia configurável por tenant (E5) | P |
| 129 | TR-181, TR-182, SG-187 | Crítica de leitura (não faturados) e de faturas emitidas no coletor, com retenção por parâmetros | TR: Sim (II-e); SG: Sim (crítico) | **Parcial**: MET detecta estouro, alto e baixo consumo com ação por reincidência; sem fila de crítica nem retenção | P2.5 (parcial) | Fila de crítica e retenção de conta | M |
| 130 | TR-183 | Crítica manual e automatizada | Sim (II-e) | **Parcial**: Automática nas regras; manual inexistente | P2.5 (N6, parcial) | Tela de crítica | M |
| 131 | TR-184 | Interface de crítica manual para tratar consumos, leituras, ocorrências e ajustar faturas | Sim (II-e) | **Não atende**: Sem tela | P2.5 (N6, parcial) | Tela de análise com histórico e ajuste auditado | M |
| 132 | TR-185 | Crítica automatizada que reproduz os ajustes do manual e reduz a intervenção | Sim (II-e) | **Parcial**: Cascata automática por parâmetros; não aprende com o manual | P2.5 | Regras derivadas das decisões manuais (N5 ajuda) | M |
| 133 | TR-186, SG-188 | Leitura de fontes alternativas para faturar esgoto | TR: Possível; SG: Sim (crítico) | **Parcial**: Medição WELL e `SewageVolumeCalculator` | P2.5 | Orquestração | P |
| 134 | TR-187, SG-189 | Leitura de imóvel isento de faturamento | TR: Possível; SG: Sim (crítico) | **Parcial**: `billable = false` e `SpecialSituation.pausesBilling` | P2.5 | Orquestração | P |
| 135 | TR-188, SG-190 | Boletim de leitura impresso (contingência) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | PDF da carga da rota (E5) | P |
| 136 | TR-190, SG-192 | Sequenciar rota por intervalos definidos | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem rotina | Não planejado | Renumeração por intervalo (E5) | P |
| 137 | SG-755 | Faturamento por autoleitura no app do cliente | Sim (crítico) | **Não atende**: Sem origem SELF; portal sem leitura | Não planejado | Autoleitura com foto e crítica (depende da P3.3) (E5) | M |
| 138 | TR-191, SG-193 | Relatório de ocorrências de leitura com filtro parametrizável | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 139 | TR-192, SG-194 | Boletim de verificação de leitura com OS automática | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório nem OS | Não planejado | Relatório e OS de verificação (depende da P3.4) (E8) | M |
| 140 | TR-193, SG-195 | Acompanhar em tempo real a distribuição e o andamento das leituras por rota, grupo e dispositivo | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem painel | Não planejado | Painel sobre as tarefas de leitura (E7) | M |
| 141 | TR-194, SG-196 | Relatório comparativo de consumo faturado (e valor) por percentual de desvio | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 142 | TR-195, SG-197 | Relatório/comunicado de excesso de consumo (sem valor financeiro) | TR: Possível; SG: Sim (crítico) | **Não atende**: MET detecta HIGH_CONSUMPTION/BURST; sem saída | Não planejado | Relatório e comunicado (E8) | P |
| 143 | TR-196, TR-205, SG-206 | Ver leituristas on-line e enviar mensagens aos dispositivos | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app nem canal | Não planejado | Presença e mensagens no app (E7) | M |
| 144 | TR-197, SG-198 | Conjunto de relatórios de produtividade e qualidade da leitura | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatórios | Não planejado | Relatórios (E8) | M |
| 145 | TR-198, SG-199 | Produtividade e ocorrências por leiturista, grupo e rota | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório (`reader_id` gravado) | Não planejado | Relatório (E8) | P |
| 146 | TR-199, SG-200 | Tempo entre leituras por leiturista e período | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório (`captured_at` gravado) | Não planejado | Relatório (E8) | P |
| 147 | TR-200, SG-201 | Acompanhar no mapa nativo, em tempo real, a rota percorrida pelo leiturista | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem rastreamento nem mapa operacional | Não planejado | Trilha GPS e mapa (E7) | G |
| 148 | TR-201, SG-202 | Rota georreferenciada traçada e número de leituras | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Idem (E7) | M |
| 149 | TR-202, SG-203 | Cada leitura no mapa com endereço, leitura/ocorrência e consumo | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Idem (E7) | M |
| 150 | TR-203, SG-204 | Monitorar o percurso (distância, tempo, sentido animado, posição das UCs) | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Idem (E7) | M |
| 151 | TR-204, SG-205 | Status da leitura por cores (não efetivada, efetivada, calculada) | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Idem (E7) | P |
| 152 | TR-208, SG-207 | Quantitativos de leituras, faturas impressas e não impressas, reavisos, comunicados, transmissões | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem painel | Não planejado | Painel (E7) | M |
| 153 | TR-206 | Enviar faturas e comunicado de débito por e-mail | Sim (II-e) | **Não atende**: Sem envio | P3.5 (parcial) | Envio com prova de entrega | M |
| 154 | TR-207, TR-243 | Imprimir o comunicado de débito a qualquer momento | Possível | **Não atende**: Sem documento | Não planejado | Comunicado no app e no backoffice (E6) | P |
| 155 | TR-209 | Arquivo das faturas retidas na crítica para reimpressão em Zebra no coletor | Sim (II-e) | **Não atende**: Sem app nem impressão | Não planejado | Reenvio ao dispositivo (E6) | M |
| 156 | TR-210, SG-208, SG-209 | Leitura automática e integração por API com telemetria (grandes consumidores, alarmes) | TR: Possível; SG: Sim (crítico) | **Parcial**: `source` TELEMETRY previsto; diário prevê tabela bruta sem auditoria; nada implementado | Não planejado | API de ingestão, tabela de séries e alarmes (E10) | M |
| 157 | TR-211, SG-210, ED-058 | Módulo de leitura e emissão simultânea em dispositivo móvel Android, integrado e do mesmo fornecedor | TR: Sim (II-e); SG: Sim (crítico); ED: Sim (III-e) | **Não atende**: Nenhum app | P4.4 (parcial: PWA só de leitura) | Emissão simultânea com impressão térmica | G |
| 158 | TR-212, SG-211 | Acesso ao app por usuário e senha | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | P4.4 | Login OIDC com uso offline | P |
| 159 | TR-213, SG-212 | Perfis distintos para leiturista e administrador | TR: Possível; SG: Sim (crítico) | **Não atende**: Papéis atuais sem leiturista | P4.4 (implícito) | Papel de leiturista | P |
| 160 | TR-214, SG-213 | Leitura por rota e sequência, com navegação entre registros | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | P4.4 | Coberto pela tarefa do plano | P |
| 161 | TR-215, SG-214 | Inverter a ordem e buscar por hidrômetro, endereço ou matrícula | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | P4.4 | Coberto pela tarefa do plano | P |
| 162 | TR-216, SG-215 | Mostrar antes da leitura: matrícula, endereço, titular, categoria, economias, leitura anterior, local do hidrômetro | TR: Possível; SG: Sim (crítico) | **Não atende**: Dados existem no cadastro e na instalação; sem app | P4.4 | Carga da rota com esses campos | P |
| 163 | TR-217, SG-216 | Rastrear por GPS o trajeto da saída até o fim da leitura | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app (`service_orders.gps_track` só na OS) | Não planejado | Trilha GPS no app (E7) | M |
| 164 | TR-218, SG-217 | Validar faixa mínima e máxima após a digitação | TR: Possível; SG: Sim (crítico) | **Parcial**: `ExpectedReadingRange` pronto para ir na carga | P4.4 | Faixa calculada no servidor e enviada | P |
| 165 | TR-219, SG-218 | Código de ocorrência com foto opcional (parametrizável) | TR: Possível; SG: Sim (crítico) | **Parcial**: Catálogo `reading_anomalies`; sem foto | P4.4 (ocorrência); foto não planejado | Foto na leitura | P |
| 166 | TR-220, SG-219 | Calcular o consumo no app, tratando virada e consumo residual por troca | TR: Possível; SG: Sim (crítico) | **Parcial**: MET `ConsumptionCalculator`, `RolloverPolicy`; roda no servidor, não no dispositivo | P4.4 (crítica no worker) | Cálculo no dispositivo para emissão offline | M |
| 167 | TR-221, SG-220 | Faturamento no local on-line e offline | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Emissão offline com numeração e sincronização (E6) | G |
| 168 | TR-222, SG-221 | Leitura de imóvel cortado com hidrômetro; havendo consumo, fatura e OS de fiscalização | TR: Possível; SG: Sim (crítico) | **Parcial**: Flags da situação de água e `generates_service_order` | Não planejado | Regra no app e OS (depende da P3.4) (E6) | M |
| 169 | TR-223, SG-222 | Calcular a fatura em campo (água, esgoto, resíduos; impostos; faixas e economias) | TR: Sim (II-f); SG: Sim (crítico) | **Parcial**: BIL `TariffEngine` puro, em Java no servidor | Não planejado | Motor tarifário portável ao dispositivo (E6) | G |
| 170 | TR-224, SG-223 | Bobina térmica em branco, com leiaute do prestador desenhado na impressão | TR: Sim (II-e); SG: Sim (crítico) | **Não atende**: Sem impressão | Não planejado | Editor de leiaute e driver de impressora (E6) | G |
| 171 | TR-225, SG-224 | Fatura com código de barras FEBRABAN e dados mínimos | TR: Sim (II-e); SG: Sim (crítico) | **Parcial**: collection `CollectionBarcode`/`AgreementBarcodeLayout` prontos; sem leiaute de campo | Não planejado (P2.8 é gráfica) | Leiaute térmico da conta (E6) | M |
| 172 | TR-226, SG-225 | QR Code Pix na fatura de campo, sem duplicidade de pagamento | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem Pix | P3.2 (parcial: só no servidor) | Pix gerado na emissão em campo | M |
| 173 | TR-227, SG-226 | Comunicado de débito após a fatura, com alerta de corte e faturas pendentes | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem documento | Não planejado | Comunicado no app (E6) | M |
| 174 | TR-228, SG-227 | Comunicado de excesso de consumo sem valor, com retenção e foto | TR: Possível; SG: Sim (crítico) | **Parcial**: Detecção de alto consumo nas regras | Não planejado | Comunicado e retenção no app (E6) | M |
| 175 | TR-229 | Outros comunicados com leiaute do prestador | Possível | **Não atende**: Sem documento | Não planejado | Modelos configuráveis (E6) | P |
| 176 | TR-230, SG-228 | Foto obrigatória por tipo (cúpula, fachada, numeração) conforme parametrização | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem foto | Não planejado | Regras de foto por ocorrência (E6) | M |
| 177 | TR-231, SG-229 | Ver no dispositivo as fotos registradas | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Galeria local (E6) | P |
| 178 | TR-232, SG-230 | Solicitação de serviço automática a partir da leitura (vazamento, violação) | TR: Possível; SG: Sim (crítico) | **Parcial**: Flag `generates_service_order`; sem OS | Não planejado | OS a partir da ocorrência (depende da P3.4) (E6) | M |
| 179 | TR-233, SG-231 | Aviso de consumo zero, alto ou baixo com redigitação obrigatória | TR: Possível; SG: Sim (crítico) | **Parcial**: Anormalidades e faixa esperada nas regras | P4.4 (implícito) | Redigitação no app | P |
| 180 | TR-234, SG-232 | Alterar em campo dados cadastrais pré-definidos | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app nem recadastramento | Não planejado | Atualização cadastral com aprovação (E11) | M |
| 181 | TR-235, SG-233 | Navegar pelas UCs lidas e não lidas de uma rua ou quadra | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | P4.4 | Coberto pela tarefa do plano | P |
| 182 | TR-236, SG-234 | Envio automático das leituras e faturas a cada 50 (VISAN) ou 10 (SAMAE) imóveis | TR: Possível; SG: Sim (crítico) | **Não atende**: ARQUITETURA-ALVO §5 prevê lotes de até 500 com `client_reading_id` | P4.4 (parcial) | Tamanho do lote configurável | P |
| 183 | TR-237, SG-235 | Enviar ao dispositivo as faturas retidas, registrar a revisão e reimprimir | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Fluxo de retida → campo (E6) | M |
| 184 | TR-238, SG-236 | Acompanhar o consumo de bateria durante a coleta | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Telemetria do dispositivo (E6) | P |
| 185 | TR-239, SG-237 | Mostrar a quantidade de bobinas necessária para a carga | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Estimativa por leiaute (E6) | P |
| 186 | TR-240, SG-238 | Teste automático do cabeçote de impressão | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Comando da impressora (E6) | P |
| 187 | TR-241, SG-239 | Leiturista ajusta sequência, rota e setor | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Ajuste com registro e devolução ao cadastro (E6) | P |
| 188 | TR-242, SG-240 | Corrigir leitura errada e reimprimir a fatura | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app | Não planejado | Correção auditada e reimpressão (E6) | P |
| 189 | TR-244, SG-241 | Comunicado de vencimento de benefício (tarifa social, ligação provisória), com antecedência configurável | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem comunicado | P1.5 (aviso); impressão em campo não planejado | Comunicado impresso na leitura | P |
| 190 | TR-647 | App de rastreamento GPS integrado nos tablets e smartphones | Possível | **Não atende**: Sem app | Não planejado | GPS no app de campo (E7) | M |
| 191 | TR-648 | Requisitos para falha de comunicação | Possível | **Não atende**: Sem app | P4.4 | Coberto pela tarefa do plano | P |
| 192 | TR-649 | Acumular ocorrências e transmitir ao restabelecer o sinal | Possível | **Não atende**: Sem app; ARQUITETURA-ALVO §5 descreve a fila offline | P4.4 | Coberto pela tarefa do plano | P |
| 193 | TR-650 | Preencher e encerrar offline e sincronizar depois | Possível | **Não atende**: Sem app | P4.4 (parcial: leitura; OS offline não) | OS offline | M |
| 194 | TR-651 | Toda atualização refletida nos sistemas; integração avaliada com a TI | Possível | **Não atende**: Sem app | P4.4 | Coberto pela tarefa do plano | P |
| 195 | TR-652 | Formulários de cadastro técnico obrigatórios nas OS antes da baixa | Possível | **Não atende**: `service_orders.form_data` em JSONB, sem serviço | Não planejado | Formulários por tipo de OS (E13) | M |
| 196 | TR-245, SG-242 | Geração e gestão de revisão de consumo | TR: Sim (II-g); SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Processo de revisão (E9) | G |
| 197 | TR-246, SG-243 | Revisão on-line ou presencial, pelo titular ou operador, com protocolo | TR: Sim (II-g); SG: Sim (crítico) | **Não atende**: Protocolo só na P3.4 | Não planejado | Revisão sobre o protocolo da P3.4 (E9) | M |
| 198 | TR-247, SG-244 | Formulário de adesão com dados mínimos | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Formulário (E9) | P |
| 199 | TR-248, SG-245 | Controle por ano/número | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Numeração anual (E9) | P |
| 200 | TR-249, SG-246 | Mês/ano de referência da revisão | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 201 | TR-250, SG-247 | Cliente/matrícula do imóvel | TR: Possível; SG: Sim (crítico) | **Não atende**: Dado existe no cadastro; sem revisão | Não planejado | Campo (E9) | P |
| 202 | TR-251, SG-248 | Endereço completo | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Campo (E9) | P |
| 203 | TR-252 | Número de economias | Possível | **Não atende**: Idem | Não planejado | Campo (E9) | P |
| 204 | TR-253, SG-249 | Data da solicitação | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 205 | TR-254, SG-250 | Nome do proponente | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 206 | TR-255, SG-251 | Quantidade de moradores | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 207 | TR-256, SG-252 | Informações de piscina | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 208 | TR-257, SG-253 | Máquina de lavar roupas | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 209 | TR-258, SG-254 | Caixa d'água | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 210 | TR-259, SG-255 | Fonte alternativa de água | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 211 | TR-260, SG-256 | Descrição da ocorrência | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 212 | TR-261 | Leituras e consumos dos últimos meses | Possível | **Parcial**: Histórico em `meter_readings`/`consumption_records`; sem consulta | Não planejado | Bloco de histórico no formulário (E9) | P |
| 213 | TR-262, SG-257 | Classificar a revisão por fase, com registro automático de cada etapa | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Máquina de estados com trilha (E9) | M |
| 214 | TR-263, SG-258 | Formulário impresso configurável | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | PDF configurável (E9) | P |
| 215 | TR-264 | Formulário: local do vazamento | Possível | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 216 | TR-265 | Formulário: materiais substituídos/reparados | Possível | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 217 | TR-266 | Formulário: indício de vazamento resolvido | Possível | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 218 | TR-267 | Formulário: método de detecção do vazamento | Possível | **Não atende**: Inexistente | Não planejado | Campo (E9) | P |
| 219 | TR-268 | Formulário: anexos (fotos) | Possível | **Não atende**: Inexistente | Não planejado | Anexo (base P1.20) (E9) | P |
| 220 | TR-269, SG-259 | Revisão inteira em dispositivo móvel, sem papel, como as OS | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem app de OS | Não planejado | Revisão no app de campo (E9) | G |
| 221 | TR-270, SG-260 | Agenda de visitas técnicas das revisões | TR: Possível; SG: Sim (crítico) | **Não atende**: Inexistente | Não planejado | Agenda por equipe (E9) | M |
| 222 | TR-271, SG-261 | Adequar-se aos processos atuais sem perdas nem custos | TR: Possível; SG: Sim (crítico) | **Não atende**: Obrigação de implantação, não função | n/a (implantação) | Mapear o processo do cliente na implantação | P |
| 223 | TR-272, SG-262 | Ao final, alterar as faturas revisadas no próprio processo, conforme permissão | TR: Possível; SG: Sim (crítico) | **Não atende**: Retificação de conta ainda não existe | P2.5 (retificação, sem vínculo à revisão) | Retificação disparada pela revisão | M |
| 224 | TR-273, SG-263 | Enviar ao cliente a fatura alterada e o laudo (e-mail; VISAN: WhatsApp e app) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem envio | P3.5 (parcial) | Envio do laudo | M |
| 225 | TR-684, SG-910 | Relatório de diferenças cadastrais com necessidade de recadastramento | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem recadastramento | Não planejado | Relatório (E11) | P |
| 226 | TR-685, SG-911 | Relatório de erros no recadastramento | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Relatório (E11) | P |
| 227 | TR-686, SG-912 | Relatório de ocorrências de recadastramento incorreto | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Relatório (E11) | P |
| 228 | TR-687, SG-913 | Unidades recentes com necessidade de recadastramento | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Relatório (E11) | P |
| 229 | TR-688, SG-914 | Eficiência dos recadastradores | TR: Possível; SG: Sim (crítico) | **Não atende**: Idem | Não planejado | Relatório (E11) | P |
| 230 | TR-690, SG-916 | Unidades com divergência de logradouro | TR: Possível; SG: Sim (crítico) | **Não atende**: Logradouro em texto livre | Não planejado | Relatório (depende do logradouro normalizado) (E8) | P |
| 231 | TR-693, SG-920 | Unidades cadastradas com dados de leitura e consumo (mensal, semestral, anual) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 232 | TR-748, SG-976 | Histograma de consumo (SAMAE: por faixa e categoria) | TR: Sim (II-r); SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 233 | TR-762, SG-989 | Painel de medições por grupo, dia e leiturista | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem painel | Não planejado | Painel (E7) | M |
| 234 | TR-767, SG-994 | Painel do processo de leitura (dias no intervalo, dias por UC lida) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem painel | Não planejado | Painel (E7) | M |
| 235 | TR-775, SG-1002 | Sintético de economias de água e esgoto por situação e categoria | TR: Possível; SG: Sim (crítico) | **Parcial**: Dados em `unit_subcategories` e situações; sem relatório | Não planejado | Relatório (E8) | P |
| 236 | TR-776 | Detalhado de economias por situação e categoria | Possível | **Parcial**: Idem | Não planejado | Relatório (E8) | P |
| 237 | SG-1000 | Sintético de ligações de água e esgoto por situação e categoria | Sim (crítico) | **Parcial**: Idem | Não planejado | Relatório (E8) | P |
| 238 | SG-1001, SG-1003 | Detalhado de ligações de água e esgoto por situação e categoria | Sim (crítico) | **Parcial**: Idem | Não planejado | Relatório (E8) | P |
| 239 | TR-791, SG-1018 | Detalhado de ligações e economias por categoria | TR: Possível; SG: Sim (crítico) | **Parcial**: Idem | Não planejado | Relatório (E8) | P |
| 240 | SG-1017 | Comparação de micromedição mensal (≥ 6 meses) por rota, setor e ligação | Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 241 | TR-793, SG-1020 | Leituras realizadas no mês por leiturista e setorização | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 242 | TR-794, SG-1021 | Ocorrências de leitura por leiturista (VISAN: grupo e DMC) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 243 | TR-795, SG-1022 | Tempo entre leituras e totais por leiturista | TR: Sim (II-r); SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 244 | TR-796, SG-1023 | Leituras enviadas aos coletores × lidas, por agrupamento, setor, rota ou quadra | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 245 | TR-797, SG-1024 | Leituras que não retornaram ou não foram gravadas | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 246 | TR-798, SG-1025 | Distribuição das leituras por coletor, com impressões e bobinas | TR: Sim (II-r); SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (depende do app) (E8) | P |
| 247 | TR-799, SG-1026 | Relatório para leitura manual | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Mesmo PDF do boletim de leitura (E8) | P |
| 248 | TR-800, SG-1027 | Leituras retidas por excesso ou abaixo da média, por grupo | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 249 | TR-801, SG-1028 | Leituras retidas com histórico de consumo | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 250 | TR-802, SG-1029 | Relatório para revisar as leituras retidas | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem relatório | Não planejado | Relatório (E8) | P |
| 251 | TR-825, SG-1052 | Serviços abertos por ocorrência de leitura (VISAN: reiterações e tempo de resposta) | TR: Possível; SG: Sim (crítico) | **Não atende**: Sem OS | Não planejado | Relatório (depende da P3.4) (E8) | P |
| 252 | TR-1009, SG-823 | Módulo GIS integrado: dados espaciais e alfanuméricos, temáticos, consultas espaciais, sobreposição, exportação | TR: Não; SG: Sim (crítico) | **Parcial**: GIS `gis_network_element` (SIRGAS 2000, GIST) e LOS `dma_district.boundary`; sem API nem tela | 2028 (R9, sob demanda) | Visualizador web e serviços espaciais (E12) | G |
| 253 | TR-1010 | Visualizar e analisar dados geográficos para gestão, expansão e manutenção das redes | Não | **Parcial**: Idem | 2028 (R9, sob demanda) | Idem (E12) | G |
| 254 | TR-1011 | GIS integrado ao cadastro comercial, com acesso móvel a ligações, serviços e consumidores | Não | **Não atende**: Sem integração | 2028 (R9, sob demanda) | Camada de UCs ligada ao cadastro; mapa no app (E13) | G |
| 255 | SG-824 | GIS web executável em Edge, Firefox e Chrome | Sim (crítico) | **Parcial**: Frontend React + MapLibre (só seletor de ponto) | 2028 (R9, sob demanda) | Visualizador completo (E12) | P |
| 256 | TR-1012, SG-825 | Camadas liga/desliga sobre mapas base selecionáveis | TR: Não; SG: Sim (crítico) | **Não atende**: `MapCanvas` mostra um mapa base para escolher ponto | 2028 (R9, sob demanda) | Gerenciador de camadas (E12) | M |
| 257 | TR-1013, SG-826 | Camadas corporativas vetoriais em PostgreSQL/PostGIS (SAMAE: PG ≥ 15) | TR: Não; SG: Sim (crítico) | **Atende**: PostGIS em uso; PG 17 + PostGIS 3.5 na P0.4 (ADR-0013) | P0.4 | — | — |
| 258 | TR-1014, SG-827 | Conectar a servidores WMS públicos (IBGE etc.) | TR: Não; SG: Sim (crítico) | **Não atende**: Sem cliente WMS | 2028 (R9, sob demanda) | Fonte WMS no MapLibre (E12) | P |
| 259 | TR-1015, SG-828 | Interface com ferramentas básicas de navegação no mapa | TR: Não; SG: Sim (crítico) | **Parcial**: MapLibre no seletor de ponto | 2028 (R9, sob demanda) | Barra de ferramentas (E12) | P |
| 260 | TR-1016, SG-829 | Zoom in e zoom out | TR: Não; SG: Sim (crítico) | **Parcial**: MapLibre (seletor de ponto) | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 261 | TR-1017, SG-830 | Zoom para camada ou elemento | TR: Não; SG: Sim (crítico) | **Não atende**: Sem camadas | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 262 | TR-1018, SG-831 | Deslocamento (pan); SAMAE: também zoom para elemento | TR: Não; SG: Sim (crítico) | **Parcial**: MapLibre (seletor de ponto) | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 263 | TR-1019, SG-832 | Exibir/ocultar rótulos por camada | TR: Não; SG: Sim (crítico) | **Não atende**: Sem camadas | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 264 | TR-1020, TR-1021, SG-833 | Localizar no mapa elementos cadastrados, com modalidades de pesquisa | TR: Não; SG: Sim (crítico) | **Não atende**: Sem pesquisa espacial | 2028 (R9, sob demanda) | Pesquisa integrada ao mapa (E12) | M |
| 265 | TR-1022, SG-834 | Pesquisa por coordenada (lat/long ou UTM SIRGAS 2000) com centralização | TR: Não; SG: Sim (crítico) | **Parcial**: Seletor aceita lat/long decimal; sem UTM | 2028 (R9, sob demanda) | Conversão UTM (proj4) (E12) | P |
| 266 | TR-1023, SG-835 | Pesquisa por atributo de camada (rua, matrícula) | TR: Não; SG: Sim (crítico) | **Não atende**: Sem camadas | 2028 (R9, sob demanda) | Entra na E12 (§3) | M |
| 267 | TR-1024, SG-836 | Pesquisa otimizada por endereço (cadastrados e em cadastramento) | TR: Não; SG: Sim (crítico) | **Não atende**: Busca textual de imóvel sem geocodificação | 2028 (R9, sob demanda) | Geocodificação sobre logradouros (E12) | M |
| 268 | SG-888 | Pesquisa múltipla por atributos (UC, trechos de água e esgoto, campos extras) | Sim (crítico) | **Não atende**: Sem pesquisa | 2028 (R9, sob demanda) | Entra na E12 (§3) | M |
| 269 | TR-1025, SG-837 | Cadastrar e editar elementos de água e esgoto (VISAN: e resíduos) ligados ao cadastro técnico | TR: Não; SG: Sim (crítico) | **Parcial**: Modelo com ~40 `element_type` e `properties` JSONB; sem API | 2028 (R9, sob demanda) | API e editor (E13) | G |
| 270 | TR-1026, SG-838 | Editar feições ponto/linha/polígono, mover e alterar atributos | TR: Não; SG: Sim (crítico) | **Não atende**: Sem editor | 2028 (R9, sob demanda) | Editor web (ou QGIS na fase 1) (E13) | G |
| 271 | TR-1027, SG-839 | Editar vértices, dimensões e rotação | TR: Não; SG: Sim (crítico) | **Não atende**: Sem editor | 2028 (R9, sob demanda) | Entra na E13 (§3) | M |
| 272 | SG-840 | Criar e editar nova camada vetorial com tabela de atributos | Sim (crítico) | **Não atende**: Sem editor | 2028 (R9, sob demanda) | Camadas do usuário (E13) | G |
| 273 | SG-841 | Unir tabelas de atributos de camadas diferentes | Sim (crítico) | **Não atende**: Sem editor | 2028 (R9, sob demanda) | Entra na E13 (§3) | M |
| 274 | SG-842 | Inserir camada raster | Sim (crítico) | **Não atende**: Sem suporte | 2028 (R9, sob demanda) | Upload de ortofoto (COG) (E12) | M |
| 275 | TR-1028, SG-843 | Ferramentas de edição para o conjunto mínimo de camadas de negócio | TR: Não; SG: Sim (crítico) | **Parcial**: Tipos modelados; sem editor | 2028 (R9, sob demanda) | Entra na E13 (§3) | G |
| 276 | TR-1029, SG-844 | Camada: reservatórios | TR: Não; SG: Sim (crítico) | **Parcial**: `element_type` RESERVOIR/TANK | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 277 | TR-1030, SG-845 | Camada: redes de adução e distribuição (trechos) | TR: Não; SG: Sim (crítico) | **Parcial**: PIPE | 2028 (R9, sob demanda) | Função adução/distribuição (E13) | P |
| 278 | TR-1031, SG-847 | Camada: mananciais | TR: Não; SG: Sim (crítico) | **Parcial**: SPRING/INTAKE | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 279 | SG-846 | Camada: poço de abastecimento | Sim (crítico) | **Parcial**: WELL | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 280 | TR-1032, SG-848 | Camada: ETA | TR: Não; SG: Sim (crítico) | **Parcial**: TREATMENT_PLANT | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 281 | TR-1033, SG-849 | Camada: ETE | TR: Não; SG: Sim (crítico) | **Parcial**: SEWER_TREATMENT_PLANT | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 282 | TR-1034, SG-850 | Camada: estações de recalque de esgoto | TR: Não; SG: Sim (crítico) | **Parcial**: LIFT_STATION | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 283 | TR-1035, SG-851 | Camada: elevatórias de água | TR: Não; SG: Sim (crítico) | **Parcial**: PUMP_STATION | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 284 | TR-1036, SG-852 | Conexões funcionais entre elementos (ex.: manancial → ETA) | TR: Não; SG: Sim (crítico) | **Parcial**: `NetworkTopologyBuilder` liga por snap só para gerar o .inp; sem relação gravada | 2028 (R9, sob demanda) | Conectividade persistida (E14) | M |
| 285 | TR-1037 | Camada: setores comerciais | Não | **Parcial**: `commercial_sectors` sem geometria | 2028 (R9, sob demanda) | Polígono do setor (E13) | P |
| 286 | TR-1038 | Camada: setores operacionais | Não | **Não atende**: Sem entidade | 2028 (R9, sob demanda) | Entidade com polígono (E13) | P |
| 287 | TR-1039 | Camada: distritos de medição e controle | Não | **Parcial**: LOS `dma_district.boundary` | 2028 (R9, sob demanda) | Exibir e editar (E13) | P |
| 288 | TR-1040, SG-853 | Camada: ligações de esgoto | TR: Não; SG: Sim (crítico) | **Parcial**: SEWER_CONNECTION | 2028 (R9, sob demanda) | Vínculo com a matrícula (E13) | P |
| 289 | TR-1041 | Camada: imóveis com tratamento individualizado | Não | **Não atende**: Sem atributo | 2028 (R9, sob demanda) | Atributo no imóvel (E13) | P |
| 290 | TR-1042, SG-854 | Camada: ligações de água | TR: Não; SG: Sim (crítico) | **Parcial**: CONNECTION/SERVICE_LINE | 2028 (R9, sob demanda) | Vínculo com a matrícula (E13) | P |
| 291 | TR-1043 | Camada: hidrômetros | Não | **Parcial**: METER no GIS; `meters` sem posição própria | 2028 (R9, sob demanda) | Posição pela UC ou pela ligação (E13) | P |
| 292 | TR-1044, SG-855 | Camada: válvulas | TR: Não; SG: Sim (crítico) | **Parcial**: VALVE e variantes (PRV, ventosa, descarga, retenção) | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 293 | SG-859 | Camada: registros de manobra | Sim (crítico) | **Parcial**: VALVE sem atributo de manobra | 2028 (R9, sob demanda) | Estado aberto/fechado e operabilidade (E14) | P |
| 294 | TR-1045, SG-856 | Camada: macromedidores | TR: Não; SG: Sim (crítico) | **Parcial**: MACRO_METER | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 295 | TR-1046, SG-857 | Camada: hidrantes | TR: Não; SG: Sim (crítico) | **Parcial**: HYDRANT | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 296 | SG-860 | Camada: booster | Sim (crítico) | **Parcial**: Só PUMP_STATION genérico | 2028 (R9, sob demanda) | Subtipo booster (E13) | P |
| 297 | TR-1047, SG-861 | Camada: poços de visita (SAMAE: e terminais de inspeção) | TR: Não; SG: Sim (crítico) | **Parcial**: MANHOLE; sem terminal de inspeção | 2028 (R9, sob demanda) | Subtipo (E13) | P |
| 298 | SG-862 | Outros componentes de água e esgoto | Sim (crítico) | **Parcial**: ~40 tipos e `properties` JSONB | 2028 (R9, sob demanda) | Entra na E13 (§3) | P |
| 299 | TR-1048 | Camada: drenagem (redes, galerias, bocas de lobo) | Não | **Não atende**: Sem tipos | 2028 (R9, sob demanda) | Tipos de drenagem (E13) | P |
| 300 | TR-1049 | Camada: rotas de coleta de resíduos sólidos | Não | **Não atende**: Módulo GRS de coleta sem código | 2028 (R9, sob demanda) | Rota de coleta como linha (E13) | M |
| 301 | TR-1050, SG-858 | Cadastro de projetos internos e externos | TR: Não; SG: Sim (crítico) | **Não atende**: Sem entidade | 2028 (R9, sob demanda) | Projeto com feições (E13) | M |
| 302 | TR-1051 | Cadastro de obras e trechos executados ou planejados | Não | **Não atende**: `status` livre no elemento; sem obra | 2028 (R9, sob demanda) | Obra com trechos e situação (E13) | M |
| 303 | SG-863 | Edição simultânea dos dados geográficos e alfanuméricos do cadastro comercial | Sim (crítico) | **Não atende**: Sem editor | 2028 (R9, sob demanda) | Formulário único geo + atributos (E13) | G |
| 304 | TR-1052, SG-864, SG-865 | Permissões por perfil para incluir, editar, excluir e visualizar dados geográficos | TR: Não; SG: Sim (crítico) | **Parcial**: RLS por tenant e papéis globais; sem permissão por camada | 2028 (R9, sob demanda) | Permissão por camada e operação (E13) | M |
| 305 | TR-1053, SG-866 | Cadastrar ligação nova no GIS e criá-la no cadastro comercial, sincronizada | TR: Não; SG: Sim (crítico) | **Não atende**: Sem editor nem entidade de ligação | 2028 (R9, sob demanda) | Fluxo GIS → ligação → matrícula (E13) | G |
| 306 | TR-1054, SG-871 | Importar camadas de contexto em shapefile (VISAN: e GeoPackage) | TR: Não; SG: Sim (crítico) | **Não atende**: Sem importador | 2028 (R9, sob demanda) | Importação com reprojeção (GDAL/ogr2ogr no worker) (E12) | M |
| 307 | TR-1055, SG-867 | Rede lógica de distribuição até as ligações, vinculada ao cadastro | TR: Não; SG: Sim (crítico) | **Não atende**: Sem topologia gravada | 2028 (R9, sob demanda) | Grafo de rede com ligação à UC (E14) | G |
| 308 | TR-1056, SG-868 | Roteamento dinâmico das redes (UCs e elementos conectados) | TR: Não; SG: Sim (crítico) | **Parcial**: Topologia por snap no `NetworkTopologyBuilder`, só para o .inp | 2028 (R9, sob demanda) | Traçado sobre o grafo (pgRouting ou próprio) (E14) | G |
| 309 | TR-1057, SG-869 | Simbologia, cores e rótulos configuráveis | TR: Não; SG: Sim (crítico) | **Não atende**: Sem camadas | 2028 (R9, sob demanda) | Estilos por camada (E12) | M |
| 310 | TR-1058, SG-870 | Mapas temáticos com legenda automática | TR: Não; SG: Sim (crítico) | **Não atende**: Sem temáticos | 2028 (R9, sob demanda) | Entra na E12 (§3) | M |
| 311 | TR-1059 | Seleção espacial por polígono com estatísticas (ligações, economias, consumo médio) | Não | **Não atende**: PostGIS permite; sem função | 2028 (R9, sob demanda) | Consulta espacial agregada (E12) | M |
| 312 | TR-1060, SG-872 | Mapas temáticos parametrizáveis dos cenários mínimos | TR: Não; SG: Sim (crítico) | **Não atende**: Sem temáticos | 2028 (R9, sob demanda) | Entra na E12 (§3) | M |
| 313 | TR-1061 | Temático de OS com filtros e exportação analítica | Não | **Não atende**: OS sem serviço | 2028 (R9, sob demanda) | Depende da P3.4 (E12) | M |
| 314 | TR-534, SG-491 | OS solicitadas e programadas em mapa, com filtros por endereço, equipe e tipo | TR: Possível; SG: Sim (crítico) | **Não atende**: OS sem serviço; sem mapa | Não planejado | Camada de OS (depende da P3.4) (E12) | M |
| 315 | TR-1062 | Temático de redes de água em operação ou implantação | Não | **Não atende**: Sem temáticos | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 316 | TR-1063 | Temático de redes de esgoto ativas ou em implantação | Não | **Não atende**: Sem temáticos | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 317 | SG-873 | Temático: consumo abaixo e acima da média | Sim (crítico) | **Não atende**: Dados em MET; sem temático | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 318 | SG-874 | Temático: consumo faturado pela média | Sim (crítico) | **Não atende**: Idem | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 319 | SG-875 | Temático: consumo lido zero | Sim (crítico) | **Não atende**: Idem | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 320 | SG-876 | Temático: ligações cortadas, suprimidas ou desligadas | Sim (crítico) | **Não atende**: Situações existem; sem temático | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 321 | SG-877 | Temático: OS abertas de vazamento no cavalete | Sim (crítico) | **Não atende**: OS sem serviço | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 322 | SG-878 | Temático: OS abertas de vazamento na rede | Sim (crítico) | **Não atende**: Idem | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 323 | SG-879 | Temático: OS abertas de falta d'água e qualidade | Sim (crítico) | **Não atende**: Idem | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 324 | SG-880 | Temático: hidrômetros por tempo de instalação e tipo | Sim (crítico) | **Não atende**: Dados em MET; sem temático | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 325 | SG-881 | Temático: faturas vencidas há mais de 60 dias | Sim (crítico) | **Não atende**: Sem faturamento operante | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 326 | SG-882 | Temático: grandes consumidores | Sim (crítico) | **Não atende**: Sem perfil | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 327 | SG-883 | Filtro por categoria em todos os temáticos | Sim (crítico) | **Não atende**: Sem temáticos | 2028 (R9, sob demanda) | Entra na E12 (§3) | P |
| 328 | TR-1064 | Simular manobras em trecho em manutenção, com equipamentos envolvidos e OS em lote | Não | **Não atende**: Cenário VALVE_CLOSURE só no modelo EPANET; sem solver nem traçado | 2028 (R9, sob demanda) | Isolamento de trecho por válvulas (E14) | G |
| 329 | TR-1065 | Simular fechamento com consumidores impactados e OS simultâneas | Não | **Não atende**: Idem | 2028 (R9, sob demanda) | Lista de afetados (N2) e OS em lote (E14) | G |
| 330 | SG-887 | Geração automática de bacias e sub-bacias de água e esgoto | Sim (crítico) | **Não atende**: Sem algoritmo | 2028 (R9, sob demanda) | Áreas de influência sobre o grafo (E14) | G |
| 331 | SG-889 | Geração automática de polígonos de roteamento de água e esgoto | Sim (crítico) | **Não atende**: Sem algoritmo | 2028 (R9, sob demanda) | Polígono da área atendida a partir do traçado (E14) | G |
| 332 | TR-626 | Usar dados das OS para mapeamento colaborativo (retroalimentar o cadastro técnico) | Possível | **Não atende**: OS sem serviço | 2028 (R9, sob demanda) | Proposta de edição vinda da OS, com aprovação (E13) | M |
| 333 | SG-1365 | Cadastro técnico e comercial interligados e integrados a Google Earth/Maps | Sim (crítico) | **Não atende**: Sem integração | 2028 (R9, sob demanda) | Exportação KML e mapa base Google licenciado (E12) | M |
| 334 | SG-1364 | Custos de licenças por conta da contratada | Sim (crítico) | **Parcial**: Pilha sem licença paga (PostGIS, MapLibre); mapa base Google teria custo | n/a (condição comercial) | Precificar o mapa base | P |
| 335 | TR-1066, SG-1339, ED-080 | Base GIS a partir do cadastro técnico em DWG, migrada pela contratada para PostGIS (SAMAE: DWG 2007+, DXF, DGN, SHP e INP, qualquer datum) | TR: Não; SG: Sim (crítico); ED: Não | **Não atende**: Sem importador; destino PostGIS existe | Não planejado (P1.2 é o cadastro comercial) | Importador DWG/DXF/SHP com reprojeção e mapeamento de camadas (E15) | G |
| 336 | TR-1067 | Consulta e edição da base geográfica pelo QGIS | Não | **Parcial**: PostGIS acessível, mas a RLS exige a GUC de tenant: QGIS não conecta direto | Não planejado | Views por tenant com role própria ou WFS-T, com auditoria (E15) | M |
| 337 | SG-884 | Forma de criação e manutenção da base geográfica | Sim (crítico) | **Não atende**: Sem processo | Não planejado | Procedimento de implantação (E15) | P |
| 338 | SG-885 | Treinamento em inserção, validação e edição de dados do GIS | Sim (crítico) | **Não atende**: Sem material | Não planejado | Roteiro e material de treinamento (E15) | P |
| 339 | SG-886 | Importar bairros, quadras, lotes e logradouros de CAD ou shapefile, sem custo | Sim (crítico) | **Não atende**: Sem importador | Não planejado | Mesmo importador, ligando às tabelas do cadastro (E15) | M |
| 340 | SG-1334 | Implantar o GIS junto dos demais módulos, em 3 etapas | Sim (crítico) | **Não atende**: Sem GIS operante | Não planejado | Plano de implantação em etapas (E15) | P |
| 341 | SG-1335 | Publicar a rede lógica de água e esgoto até a UC | Sim (crítico) | **Não atende**: Sem rede lógica | Não planejado | Depende da rede lógica (E15) | G |
| 342 | SG-1336 | Temático da rede e dos pontos com dados comerciais | Sim (crítico) | **Não atende**: Sem temático | Não planejado | Entra na E15 (§3) | M |
| 343 | SG-1337 | Exibir em camadas as demais informações do desenho | Sim (crítico) | **Não atende**: Sem importador | Não planejado | Entra na E15 (§3) | M |
| 344 | SG-1338 | Requisitos mínimos da implantação (caput) | Sim (crítico) | **Não atende**: Sem importador | Não planejado | Entra na E15 (§3) | P |
| 345 | SG-1340 | Importação gerando camadas distintas para os elementos de rede e resíduos | Sim (crítico) | **Não atende**: Sem importador; tipos de destino existem | Não planejado | Mapa camada DWG → `element_type` (E15) | M |
| 346 | SG-1341 | Camada importada: manancial e poço | Sim (crítico) | **Não atende**: Destino SPRING/INTAKE/WELL | Não planejado | Entra na E15 (§3) | P |
| 347 | SG-1342 | Camada importada: ETA | Sim (crítico) | **Não atende**: Destino TREATMENT_PLANT | Não planejado | Entra na E15 (§3) | P |
| 348 | SG-1343 | Camada importada: ETE | Sim (crítico) | **Não atende**: Destino SEWER_TREATMENT_PLANT | Não planejado | Entra na E15 (§3) | P |
| 349 | SG-1344 | Camada importada: elevatória de água | Sim (crítico) | **Não atende**: Destino PUMP_STATION | Não planejado | Entra na E15 (§3) | P |
| 350 | SG-1345 | Camada importada: estação e rede de recalque de esgoto | Sim (crítico) | **Não atende**: Destino LIFT_STATION/SEWER_PIPE | Não planejado | Entra na E15 (§3) | P |
| 351 | SG-1346 | Camada importada: reservatórios | Sim (crítico) | **Não atende**: Destino RESERVOIR/TANK | Não planejado | Entra na E15 (§3) | P |
| 352 | SG-1347 | Camada importada: trechos de rede e adutoras | Sim (crítico) | **Não atende**: Destino PIPE | Não planejado | Entra na E15 (§3) | P |
| 353 | SG-1348 | Camada importada: ligação com a UC | Sim (crítico) | **Não atende**: Destino CONNECTION/SERVICE_LINE | Não planejado | Vínculo à matrícula (E15) | M |
| 354 | SG-1349 | Camada importada: unidade consumidora | Sim (crítico) | **Não atende**: Destino `consumer_units.coordinates` | Não planejado | Casamento por matrícula ou endereço (E15) | M |
| 355 | SG-1350 | Camada importada: conexões entre elementos | Sim (crítico) | **Não atende**: Sem conectividade gravada | Não planejado | Depende da rede lógica (E15) | M |
| 356 | SG-1351 | Camada importada: válvulas e ventosas | Sim (crítico) | **Não atende**: Destino VALVE/AIR_VALVE | Não planejado | Entra na E15 (§3) | P |
| 357 | SG-1352 | Camada importada: macromedidores | Sim (crítico) | **Não atende**: Destino MACRO_METER | Não planejado | Entra na E15 (§3) | P |
| 358 | SG-1353 | Camada importada: hidrantes | Sim (crítico) | **Não atende**: Destino HYDRANT | Não planejado | Entra na E15 (§3) | P |
| 359 | SG-1354 | Camada importada: projetos internos e externos | Sim (crítico) | **Não atende**: Sem entidade de projeto | Não planejado | Entra na E15 (§3) | P |
| 360 | SG-1355 | Camada importada: poços de visita | Sim (crítico) | **Não atende**: Destino MANHOLE | Não planejado | Entra na E15 (§3) | P |
| 361 | SG-1356 | Tratar base cadastral fornecida, possivelmente inconsistente com o desenho | Sim (crítico) | **Não atende**: Sem conciliação | Não planejado | Relatório de inconsistências (ValidaBase, `05` §9) (E15) | M |
| 362 | SG-1357 | Inserção e edição pelo navegador via WFS 1.0+ | Sim (crítico) | **Não atende**: Sem WFS | Não planejado | WFS-T (GeoServer ou equivalente) ou API própria (E15) | M |
| 363 | SG-1358 | Diagnóstico dos elementos gráficos do DWG/SHP | Sim (crítico) | **Não atende**: Sem ferramenta | Não planejado | Relatório de diagnóstico (E15) | M |
| 364 | SG-1359 | Ajustes: metro, escala 1:1, SIRGAS 2000/UTM 22S, ordenação em camadas | Sim (crítico) | **Não atende**: SRID 4674 adotado; sem rotina | Não planejado | Reprojeção e normalização (E15) | M |
| 365 | SG-1360 | Análise da base existente (integridade referencial, chaves, normalização) | Sim (crítico) | **Não atende**: Sem ferramenta | Não planejado | Validador de base (E15) | M |
| 366 | SG-1361 | Converter a rede até a UC (trecho, ligação, UC, elevatória, reservatório, ETA/ETE, manancial) | Sim (crítico) | **Não atende**: Sem importador | Não planejado | Entra na E15 (§3) | G |
| 367 | SG-1362 | Exibir a rede convertida no GIS web e permitir novos cadastros | Sim (crítico) | **Não atende**: Sem GIS web | Não planejado | Depende do visualizador e do editor (E15) | G |
| 368 | SG-1363 | Bairros, distritos, lotes e arruamentos em camada sobre mapa base (Google licenciado, Bing ou OSM) | Sim (crítico) | **Não atende**: Sem camadas | Não planejado | Camadas de contexto e escolha do mapa base (E15) | M |


## 3. Requisitos não planejados, agrupados em entregas propostas

São 281 linhas (431 IDs) sem tarefa nas fases P0–P4. Os números entre parênteses remetem à coluna "#" da matriz. A fase sugerida usa os nomes do plano; "antecipar" indica que a entrega só cabe antes do go-live se o dono do produto decidir disputar os editais (decisão 5 do §4).

| Entrega | Linhas | IDs | Fase sugerida | Esf. | Dependências |
|---|---|---|---|---|---|
| E1 — Cadastro comercial ampliado | 6 | 11 | P1, como extensão da P1.3 (`V3_6`) | M | P1.3; P1.20 (upload com varredura) |
| E2 — Catálogos auxiliares (SAMAE 2.8.10) | 22 | 22 | P1 (backend genérico) e cada catálogo junto do módulo dono (P2.5, P3.1, P3.4) | M | P1.3 (o `CatalogCrud` do frontend já é dirigido por metadados) |
| E3 — Ligações de água e esgoto | 10 | 11 | P1–P2, antes da P2.5 (consumo mínimo e situação vêm da ligação) | G | P1.3; ações por OS dependem da P3.4 |
| E4 — Hidrometria: parque e ciclo de vida | 19 | 33 | P2, junto da P2.5 (POC da VISAN, alínea d) | G | P2.5 (usa as instalações no ciclo); P3.4 (OS de instalação, retirada, troca e aferição); P1.20 (fotos) |
| E5 — Ciclo de leitura: complementos | 6 | 11 | P2, junto da P2.7 | M | P2.5, P2.7 (`V5_3`); autoleitura depende da P3.3 |
| E6 — App de leitura e emissão simultânea | 19 | 36 | Antecipar a P4.4 e ampliá-la (P3, se houver decisão de disputar editais; senão P4) | G (o maior item da área) | P2.5 (conta), P2.3 (idempotência), P3.2 (Pix), P3.4 (OS); decisões 1 a 3 do §4 |
| E7 — Acompanhamento de campo e painéis de leitura | 12 | 24 | P4, depois da E6 | G | E6 (trilha GPS e presença); E12 (mapa operacional) |
| E8 — Relatórios de cadastro, leitura e consumo | 27 | 51 | P2–P3 (os relatórios contam na POC da VISAN, alínea r) | M | P2.5; gerador de relatórios da matriz de relatórios; P4.3 (réplica) para volume |
| E9 — Revisão de consumo | 26 | 45 | P3, depois da P3.4 (protocolo e OS) | G | P3.4; P2.5 (retificação de conta); P3.5 (envio); app de OS offline para o SAMAE 2.12.5 |
| E10 — Telemetria e AMR | 2 | 5 | P4 | M | P1.9 (worker); P2.3 (tabelas particionadas) |
| E11 — Recadastramento | 6 | 12 | P4 | G | E6 (app de campo); P1.3 |
| E12 — GIS web: visualização, pesquisa e temáticos | 37 | 55 | Antecipar do 2028 para P3–P4 | G | P0.4 (PG 17 + PostGIS 3.5); serviço de tiles vetoriais; P2.5 e P3.4 para os temáticos de consumo e OS |
| E13 — GIS: edição e integração com o cadastro | 44 | 65 | P4 (ou 2028) | G | E12; E3 (ligações); P3.4 (OS) |
| E14 — Rede lógica, traçado e manobras | 9 | 12 | 2028 (ou P4, se a E13 for antecipada) | G | E13; P3.4 (OS em lote); N2 (incidente e afetados, `15c`) |
| E15 — Migração do cadastro técnico (DWG/SHP → PostGIS) e acesso QGIS | 34 | 36 | P3 (ferramenta) e implantação de cada cliente | G | P1.9 (worker com GDAL); P0.4; E12 para publicar o resultado |
| E16 — Integrações de cadastro | 2 | 2 | P4 | M | P1.5 (tarifa social); módulo contábil |

### E1 — Cadastro comercial ampliado

Atributos e funções do cadastro que a P1.3 não lista: RG e filiação (opcionais, cifrados e com finalidade registrada, conforme `07` §5.3), anexos de documentos do cliente, faixa de logradouro, unidades por órgão pagador com consulta e emissão agrupada, termo de adesão gerado no cadastro da unidade.

- **Requisitos cobertos** (11): TR-134, SG-070, TR-135, SG-071, TR-141, SG-078, TR-151, SG-091, SG-083, TR-777, SG-1004 (linhas 6, 7, 14, 23, 32, 34).
- **Fase sugerida:** P1, como extensão da P1.3 (`V3_6`). **Esforço:** M. **Dependências:** P1.3; P1.20 (upload com varredura).

### E2 — Catálogos auxiliares (SAMAE 2.8.10)

Tabelas simples (código, descrição, ativo) para os catálogos da lista do SAMAE que não pertencem a outra entrega: cargos, estado civil, nacionalidade, veículos e modelos, materiais e tipos, unidade de medida, pavimentos, tipo de rede, tipo de cobrança e os motivos do faturamento e da arrecadação. Um recurso genérico de catálogo no backend resolve a maioria; estado civil e nacionalidade só com finalidade definida.

- **Requisitos cobertos** (22): SG-095, SG-102, SG-104, SG-105, SG-107, SG-108, SG-110, SG-111, SG-112, SG-115, SG-116, SG-119, SG-120, SG-121, SG-122, SG-127, SG-137, SG-138, SG-139, SG-140, SG-143, SG-144 (linhas 42, 49, 51, 52, 54, 55, 57, 58, 59, 62, 63, 66, 67, 68, 69, 74, 84, 85, 86, 87, 90, 91).
- **Fase sugerida:** P1 (backend genérico) e cada catálogo junto do módulo dono (P2.5, P3.1, P3.4). **Esforço:** M. **Dependências:** P1.3 (o `CatalogCrud` do frontend já é dirigido por metadados).

### E3 — Ligações de água e esgoto

Entidades `water_connections` e `sewer_connections` (1:1 com o imóvel, `07` §4.6), catálogos de diâmetro de ramal e cavalete, material, posição e tipo de cavalete, tipo de ligação, motivos de mudança de situação e a atualização dos dados técnicos pela tela de atendimento.

- **Requisitos cobertos** (11): TR-578, SG-555, SG-098, SG-100, SG-101, SG-106, SG-117, SG-118, SG-124, SG-125, SG-136 (linhas 35, 45, 47, 48, 53, 64, 65, 71, 72, 83).
- **Fase sugerida:** P1–P2, antes da P2.5 (consumo mínimo e situação vêm da ligação). **Esforço:** G. **Dependências:** P1.3; ações por OS dependem da P3.4.

### E4 — Hidrometria: parque e ciclo de vida

Serviço, API e telas sobre `meters` e `meter_installations`, que já existem: cadastro unitário e em lote por faixa, locais de estoque e movimentações, atributos técnicos e catálogos (tipo, diâmetro, motivos), lacre, instalação, retirada e troca com destino do retirado, histórico por ligação, aferição com laudo e fotos, alerta de validade (Inmetro) com OS em lote e OS corretiva a partir da ocorrência de leitura.

- **Requisitos cobertos** (33): SG-099, SG-113, SG-114, SG-135, TR-156, SG-157, TR-157, SG-158, TR-158, SG-159, TR-159, SG-160, TR-160, SG-161, TR-161, SG-162, TR-162, SG-163, TR-163, SG-164, TR-164, SG-165, TR-165, SG-166, TR-166, SG-167, SG-168, TR-168, SG-170, TR-169, SG-171, TR-170, SG-172 (linhas 46, 60, 61, 82, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114).
- **Fase sugerida:** P2, junto da P2.5 (POC da VISAN, alínea d). **Esforço:** G. **Dependências:** P2.5 (usa as instalações no ciclo); P3.4 (OS de instalação, retirada, troca e aferição); P1.20 (fotos).

### E5 — Ciclo de leitura: complementos

O que o ciclo planejado não explicita: tela de roteiro com reordenação, parâmetros de roteiro por tipologia da localidade, sequenciamento por intervalo, boletim de leitura impresso para contingência, regra de condomínio configurável (rateio por economia ou diferença faturada à área comum, SAMAE 2.10.10) e autoleitura pelo portal ou app do cliente.

- **Requisitos cobertos** (11): TR-172, SG-174, TR-173, SG-175, SG-185, SG-186, TR-188, SG-190, TR-190, SG-192, SG-755 (linhas 117, 118, 128, 135, 136, 137).
- **Fase sugerida:** P2, junto da P2.7. **Esforço:** M. **Dependências:** P2.5, P2.7 (`V5_3`); autoleitura depende da P3.3.

### E6 — App de leitura e emissão simultânea

Estende o PWA de leitura da P4.4 para a emissão simultânea exigida pelos dois editais: cálculo da conta no dispositivo (cascata de consumo e `TariffEngine` portáveis ou executados em modo embarcado), emissão offline com numeração e sincronização, impressão térmica em bobina em branco com leiaute configurável (Zebra), comunicados de débito, excesso e outros, Pix na conta, fotos obrigatórias por ocorrência, retidas reenviadas ao campo, correção e reimpressão, bobinas, teste de cabeçote e bateria.

- **Requisitos cobertos** (36): TR-207, TR-243, TR-209, TR-221, SG-220, TR-222, SG-221, TR-223, SG-222, TR-224, SG-223, TR-225, SG-224, TR-227, SG-226, TR-228, SG-227, TR-229, TR-230, SG-228, TR-231, SG-229, TR-232, SG-230, TR-237, SG-235, TR-238, SG-236, TR-239, SG-237, TR-240, SG-238, TR-241, SG-239, TR-242, SG-240 (linhas 154, 155, 167, 168, 169, 170, 171, 173, 174, 175, 176, 177, 178, 183, 184, 185, 186, 187, 188).
- **Fase sugerida:** Antecipar a P4.4 e ampliá-la (P3, se houver decisão de disputar editais; senão P4). **Esforço:** G (o maior item da área). **Dependências:** P2.5 (conta), P2.3 (idempotência), P3.2 (Pix), P3.4 (OS); decisões 1 a 3 do §4.

### E7 — Acompanhamento de campo e painéis de leitura

Trilha GPS do leiturista, mapa das leituras por status (cores), percurso com distância e tempo, leituristas on-line e mensagens, quantitativos da carga e painéis de medição e do processo de leitura.

- **Requisitos cobertos** (24): TR-193, SG-195, TR-196, TR-205, SG-206, TR-200, SG-201, TR-201, SG-202, TR-202, SG-203, TR-203, SG-204, TR-204, SG-205, TR-208, SG-207, TR-217, SG-216, TR-647, TR-762, SG-989, TR-767, SG-994 (linhas 140, 143, 147, 148, 149, 150, 151, 152, 163, 190, 233, 234).
- **Fase sugerida:** P4, depois da E6. **Esforço:** G. **Dependências:** E6 (trilha GPS e presença); E12 (mapa operacional).

### E8 — Relatórios de cadastro, leitura e consumo

Cerca de 27 relatórios: ocorrências, verificação, comparativo de consumo, excesso, produtividade e tempo entre leituras, histograma, unidades com leitura e consumo, economias e ligações por situação e categoria, comparação de micromedição, distribuição e retorno da carga, retidas e OS por ocorrência de leitura.

- **Requisitos cobertos** (51): TR-191, SG-193, TR-192, SG-194, TR-194, SG-196, TR-195, SG-197, TR-197, SG-198, TR-198, SG-199, TR-199, SG-200, TR-690, SG-916, TR-693, SG-920, TR-748, SG-976, TR-775, SG-1002, TR-776, SG-1000, SG-1001, SG-1003, TR-791, SG-1018, SG-1017, TR-793, SG-1020, TR-794, SG-1021, TR-795, SG-1022, TR-796, SG-1023, TR-797, SG-1024, TR-798, SG-1025, TR-799, SG-1026, TR-800, SG-1027, TR-801, SG-1028, TR-802, SG-1029, TR-825, SG-1052 (linhas 138, 139, 141, 142, 144, 145, 146, 230, 231, 232, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251).
- **Fase sugerida:** P2–P3 (os relatórios contam na POC da VISAN, alínea r). **Esforço:** M. **Dependências:** P2.5; gerador de relatórios da matriz de relatórios; P4.3 (réplica) para volume.

### E9 — Revisão de consumo

Processo com protocolo e numeração anual, formulário de adesão e de vistoria (moradores, piscina, máquina, caixa d'água, vazamento, materiais, fotos), fases com trilha, agenda de visitas, execução no dispositivo móvel sem papel e retificação da conta dentro do processo. O item 2.12.5 é crítico na POC do SAMAE.

- **Requisitos cobertos** (45): TR-245, SG-242, TR-246, SG-243, TR-247, SG-244, TR-248, SG-245, TR-249, SG-246, TR-250, SG-247, TR-251, SG-248, TR-252, TR-253, SG-249, TR-254, SG-250, TR-255, SG-251, TR-256, SG-252, TR-257, SG-253, TR-258, SG-254, TR-259, SG-255, TR-260, SG-256, TR-261, TR-262, SG-257, TR-263, SG-258, TR-264, TR-265, TR-266, TR-267, TR-268, TR-269, SG-259, TR-270, SG-260 (linhas 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221).
- **Fase sugerida:** P3, depois da P3.4 (protocolo e OS). **Esforço:** G. **Dependências:** P3.4; P2.5 (retificação de conta); P3.5 (envio); app de OS offline para o SAMAE 2.12.5.

### E10 — Telemetria e AMR

API e importação de leituras de AMR e telemetria (grandes consumidores), tabela de séries sem auditoria por linha (como previsto no comentário do `V5_1`), alarmes e consolidação mensal para o ciclo.

- **Requisitos cobertos** (5): TR-167, SG-169, TR-210, SG-208, SG-209 (linhas 115, 156).
- **Fase sugerida:** P4. **Esforço:** M. **Dependências:** P1.9 (worker); P2.3 (tabelas particionadas).

### E11 — Recadastramento

Lote, coleta em campo, retorno, aprovação campo a campo e aplicação (`01` §6, `07` §4.8), com a alteração de dados cadastrais pré-definidos durante a leitura e os cinco relatórios de recadastramento.

- **Requisitos cobertos** (12): TR-234, SG-232, TR-684, SG-910, TR-685, SG-911, TR-686, SG-912, TR-687, SG-913, TR-688, SG-914 (linhas 180, 225, 226, 227, 228, 229).
- **Fase sugerida:** P4. **Esforço:** G. **Dependências:** E6 (app de campo); P1.3.

### E12 — GIS web: visualização, pesquisa e temáticos

Visualizador web sobre o MapLibre já usado no frontend: camadas liga/desliga, mapas base, WMS, zoom e rótulos por camada, pesquisas por coordenada (com UTM), atributo e endereço, seleção por polígono com estatísticas, simbologia configurável, temáticos com legenda (consumo, situação, OS, hidrômetros, inadimplência, grandes consumidores), importação de shapefile e GeoPackage, raster e OS no mapa.

- **Requisitos cobertos** (55): TR-1009, SG-823, TR-1010, SG-824, TR-1012, SG-825, TR-1014, SG-827, TR-1015, SG-828, TR-1016, SG-829, TR-1017, SG-830, TR-1018, SG-831, TR-1019, SG-832, TR-1020, TR-1021, SG-833, TR-1022, SG-834, TR-1023, SG-835, TR-1024, SG-836, SG-888, SG-842, TR-1054, SG-871, TR-1057, SG-869, TR-1058, SG-870, TR-1059, TR-1060, SG-872, TR-1061, TR-534, SG-491, TR-1062, TR-1063, SG-873, SG-874, SG-875, SG-876, SG-877, SG-878, SG-879, SG-880, SG-881, SG-882, SG-883, SG-1365 (linhas 252, 253, 255, 256, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267, 268, 274, 306, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327, 333).
- **Fase sugerida:** Antecipar do 2028 para P3–P4. **Esforço:** G. **Dependências:** P0.4 (PG 17 + PostGIS 3.5); serviço de tiles vetoriais; P2.5 e P3.4 para os temáticos de consumo e OS.

### E13 — GIS: edição e integração com o cadastro

API de `gis_network_element`, editor de feições (vértices, mover, rotacionar), camadas de negócio e do usuário, permissões por camada, ligação nova desenhada no GIS e criada no cadastro, setores, DMCs, projetos e obras, edição simultânea do dado geográfico e alfanumérico, formulários técnicos na OS e retroalimentação do cadastro técnico pelas OS.

- **Requisitos cobertos** (65): TR-154, SG-148, SG-149, SG-150, SG-151, SG-152, SG-154, TR-652, TR-1011, TR-1025, SG-837, TR-1026, SG-838, TR-1027, SG-839, SG-840, SG-841, TR-1028, SG-843, TR-1029, SG-844, TR-1030, SG-845, TR-1031, SG-847, SG-846, TR-1032, SG-848, TR-1033, SG-849, TR-1034, SG-850, TR-1035, SG-851, TR-1037, TR-1038, TR-1039, TR-1040, SG-853, TR-1041, TR-1042, SG-854, TR-1043, TR-1044, SG-855, TR-1045, SG-856, TR-1046, SG-857, SG-860, TR-1047, SG-861, SG-862, TR-1048, TR-1049, TR-1050, SG-858, TR-1051, SG-863, TR-1052, SG-864, SG-865, TR-1053, SG-866, TR-626 (linhas 26, 92, 93, 94, 95, 96, 98, 195, 254, 269, 270, 271, 272, 273, 275, 276, 277, 278, 279, 280, 281, 282, 283, 285, 286, 287, 288, 289, 290, 291, 292, 294, 295, 296, 297, 298, 299, 300, 301, 302, 303, 304, 305, 332).
- **Fase sugerida:** P4 (ou 2028). **Esforço:** G. **Dependências:** E12; E3 (ligações); P3.4 (OS).

### E14 — Rede lógica, traçado e manobras

Grafo da rede persistido, conectividade entre elementos, traçado dinâmico (pgRouting ou próprio), isolamento de trecho pelas válvulas com consumidores afetados e OS em lote, registros de manobra, bacias, sub-bacias e polígonos de roteamento.

- **Requisitos cobertos** (12): SG-153, TR-1036, SG-852, SG-859, TR-1055, SG-867, TR-1056, SG-868, TR-1064, TR-1065, SG-887, SG-889 (linhas 97, 284, 293, 307, 308, 328, 329, 330, 331).
- **Fase sugerida:** 2028 (ou P4, se a E13 for antecipada). **Esforço:** G. **Dependências:** E13; P3.4 (OS em lote); N2 (incidente e afetados, `15c`).

### E15 — Migração do cadastro técnico (DWG/SHP → PostGIS) e acesso QGIS

Importador DWG/DXF/DGN/SHP/GPKG/INP com diagnóstico (o GDAL aberto não lê DWG 2007+; ver decisão 3 do §4), reprojeção para SIRGAS 2000, escala, mapeamento de camada para `element_type`, casamento de UC por matrícula ou endereço, validação da base (conceito ValidaBase do GEOSAN, `05` §9), publicação em camadas e acesso de consulta e edição pelo QGIS (views por tenant com role própria ou WFS-T, já que a RLS exige a GUC de tenant), com roteiro de treinamento. A VISAN exige a migração do DWG dentro dos 90 dias de implantação; o SAMAE, dentro dos 60.

- **Requisitos cobertos** (36): TR-1066, SG-1339, ED-080, TR-1067, SG-884, SG-885, SG-886, SG-1334, SG-1335, SG-1336, SG-1337, SG-1338, SG-1340, SG-1341, SG-1342, SG-1343, SG-1344, SG-1345, SG-1346, SG-1347, SG-1348, SG-1349, SG-1350, SG-1351, SG-1352, SG-1353, SG-1354, SG-1355, SG-1356, SG-1357, SG-1358, SG-1359, SG-1360, SG-1361, SG-1362, SG-1363 (linhas 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368).
- **Fase sugerida:** P3 (ferramenta) e implantação de cada cliente. **Esforço:** G. **Dependências:** P1.9 (worker com GDAL); P0.4; E12 para publicar o resultado.

### E16 — Integrações de cadastro

Consulta de Bolsa Família/BPC (só se houver API oficial acessível ao prestador; senão importação de lista com base legal) e exportação do cadastro técnico de ativos para o sistema contábil.

- **Requisitos cobertos** (2): TR-144, TR-155 (linhas 16, 27).
- **Fase sugerida:** P4. **Esforço:** M. **Dependências:** P1.5 (tarifa social); módulo contábil.

## 4. Decisões do dono do produto

Cinco decisões, na ordem em que bloqueiam as entregas. A recomendada está marcada.

### Decisão 1 — Tecnologia do app de campo (leitura, emissão e OS)

Os dois editais pedem app Android (TR 8.1 e SAMAE 2.11: "compatíveis com a plataforma Android"); nenhum pede iOS no
campo (o iOS só aparece no app do consumidor). A emissão simultânea exige impressão por Bluetooth, GPS contínuo
durante a rota e operação offline longa.

| Opção | Prós | Contras |
|---|---|---|
| a) PWA, como está na P4.4 | Um código só com o frontend; sem loja | O navegador não acessa Bluetooth clássico (o Web Bluetooth do Chrome só fala BLE) e não mantém GPS com a tela apagada; impressão Zebra e rastreamento ficam frágeis |
| **b) Híbrido Android (Capacitor) reaproveitando o React, com plugins nativos de impressão e GPS em segundo plano — recomendada** | Reaproveita componentes, validações e o cliente da API; acesso ao SDK da Zebra e a serviço de localização em primeiro plano; um APK, como os editais preveem | Exige pipeline de build Android e assinatura; plugins nativos a manter |
| c) Nativo Kotlin | Melhor controle de hardware e bateria | Segunda base de código e segunda linguagem para uma pessoa só |

Consequência: a P4.4 passa a ser "app de campo Android (Capacitor)", com a leitura offline como primeira entrega e a
emissão simultânea (E6) como segunda. iOS fica fora do app de campo.

### Decisão 2 — Impressora térmica

A VISAN cita "impressora Zebra ou alguma similar" (7.24.9); os dois editais exigem bobina em branco e leiaute desenhado
na impressão.

| Opção | Prós | Contras |
|---|---|---|
| **a) Homologar a linha Zebra (ZPL), com editor de leiaute que gera ZPL — recomendada** | Padrão de mercado no saneamento; SDK oficial para Android; ZPL resolve código de barras e QR Code na impressora | Fica preso a uma marca até haver segundo driver |
| b) Camada de impressão com ZPL e ESC/POS desde o início | Aceita impressoras genéricas mais baratas | Dobra o teste de leiaute (largura, fontes, códigos de barras) sem demanda comprovada |
| c) Não oferecer emissão simultânea | Elimina a E6 | Inviabiliza os dois editais e a maior parte dos SAAEs que leem e emitem em campo |

### Decisão 3 — Leitura do DWG na migração do cadastro técnico

Os dois editais entregam o cadastro técnico em DWG (SAMAE: versão 2007 ou superior, além de DXF, DGN, SHP e INP). O GDAL
de código aberto só lê DWG antigo; DWG atual exige a biblioteca da Open Design Alliance ou conversão prévia.

| Opção | Prós | Contras |
|---|---|---|
| **a) Converter DWG para DXF com o ODA File Converter (gratuito, licença proprietária) numa etapa assistida da implantação e importar o DXF com GDAL no worker — recomendada** | Sem custo de licença; cobre DWG de qualquer versão; o importador do produto trata só formatos abertos | Ferramenta proprietária na esteira de implantação (fora da imagem de produção); conferir a licença de uso comercial |
| b) Licenciar o SDK da ODA e ler DWG dentro do produto | Importação de DWG pelo próprio cliente | Custo anual de licença e dependência binária na imagem |
| c) Exigir do cliente DXF ou SHP | Nada a licenciar | Contraria o texto dos editais ("importar DWG"); risco de impugnação ou desclassificação |

### Decisão 4 — Como e quando construir o GIS

O plano deixa o GIS para 2028, sob demanda. A VISAN veda subcontratação; o SAMAE só admite subcontratar
"georreferenciamento" como serviço acessório, mantendo o cadastro técnico operacional como parcela nuclear.

| Opção | Prós | Contras |
|---|---|---|
| a) Manter em 2028 | Preserva o caminho crítico do piloto | Exclui o Ziip SAN dos editais com GIS (os dois analisados têm), inclusive na implantação |
| **b) GIS web próprio sobre MapLibre e PostGIS (E12, E13), com o QGIS como editor avançado na primeira fase (E15) e WFS-T por componente aberto onde o edital o exige — recomendada** | Usa a pilha que já existe e que os editais pedem; o QGIS cobre edição pesada sem escrever o editor completo de saída; componente de software não é subcontratação | E12 + E15 somam semanas de trabalho; o acesso do QGIS exige views por tenant (a RLS depende da GUC) |
| c) Integrar um GIS de terceiro (SaaS) | Rapidez | Vedado na VISAN; no SAMAE só como serviço acessório; quebra a "base única" exigida (SAMAE "Item 0") |

### Decisão 5 — Prioridade dos editais frente ao piloto

Com uma pessoa e o caminho crítico sem folga (plano §5), qualquer antecipação desloca o go-live.

| Opção | Prós | Contras |
|---|---|---|
| a) Seguir o plano e não disputar editais com POC antes de 2028 | Go-live em 30/07/2027 preservado | Fica fora dos certames de 2027 que tenham leitura móvel ou GIS |
| **b) Abrir uma trilha "editais" no P4 (ago–dez/2027) com E4, E6, E9, E12 e E15, mirando certames a partir de 2028 — recomendada** | Não toca o caminho crítico; a P4.4 já está no P4 e vira a base da E6; o piloto valida o ciclo antes | Exige rever o escopo do P4 (R8, R6, BI e isolamento por porte disputam a mesma capacidade) |
| c) Antecipar as cinco entregas para P2–P3 | Permite disputar editais em 2027 | Desloca o go-live em vários meses e aumenta o risco do piloto |

## 5. Itens dos editais deixados fora deste recorte

Avaliados nas matrizes das outras áreas, porque o texto é de outro processo, ainda que cite cadastro ou leitura:

- TR-778 e TR-779 (termo de confissão e parcelamento): cobrança.
- TR-277 e SG-267 (cronograma de faturamento, com a data da massa de leitura), TR-291 e SG-280 (rateio entre
  categorias), TR-292 e SG-281 (cálculo da conta), TR-301 e SG-290 (histórico de leitura impresso na conta), TR-311,
  TR-312, SG-300 e SG-301 (alterar leitura e consumo da fatura), TR-557, TR-558, SG-533 e SG-534 (as mesmas alterações
  no atendimento): faturamento e atendimento.
- TR-964, TR-965 e TR-969 a TR-978 (painéis de BI de ligações, economias e leituras): BI.
- TR-486 (cadastro imobiliário no e-Sfinge): integrações TCE/SC.
- TR-659, TR-845, TR-866, SG-610, SG-622, SG-638, SG-681, SG-714, SG-715, SG-726 (histórico e previsão de leitura no
  portal, totem, omnichannel e app do consumidor): canais de atendimento.
- TR-629 e SG-512 (deslocamento das equipes de OS no mapa), SG-471 (setores e rotas de execução de serviço): OS.
- TR-752, TR-753, TR-834, SG-970, SG-971, SG-972, SG-977, SG-979 e SG-980 (faturamento por categoria e faixa), TR-820 e SG-1047 (repavimentação):
  relatórios de faturamento e de OS.
- SG-802 e SG-804 (rotas e pontos de coleta de amostras): qualidade da água.
