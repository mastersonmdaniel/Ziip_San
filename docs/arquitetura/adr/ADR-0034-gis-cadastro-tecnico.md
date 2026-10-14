# ADR-0034 — GIS e cadastro técnico de redes

- **Status:** Aceito, 12/10/2026
- **Decisor:** dono do produto (cartões de 12/10/2026)
- **Afeta:** ADR-0001 (P1 a P3 recebem o núcleo do GIS); ADR-0002 (parâmetros `gis.*`); ADR-0004 (SINISA e GRS
  assistidos, alimentados pelo cadastro); ADR-0005 (código único por tenant no schema compartilhado); ADR-0010
  (exceções: pgRouting e endpoint do QGIS, ajuste v2.10); ADR-0013 (PostgreSQL 17 e PostGIS 3.5; histórico sem
  `WITHOUT OVERLAPS`); ADR-0017 (tokens `map-*` na simbologia); ADR-0023 (prontidão de POC); ADR-0026 e ADR-0030
  (campo); ADR-0027 (role por identidade da conexão, mesmo fundamento do QGIS); ADR-0028 (item b, ajuste v2.10);
  ADR-0029 (capacidade e gatilho da quinta contratação); ADR-0033 (ambiente local até o lançamento);
  `docs/arquitetura/GIS-CADASTRO-TECNICO.md`; `PARAMETROS-DO-TENANT.md`; plano v2.10 (P1.26, P1.27, P2.21 a P2.23,
  P3.25 a P3.27, ED-13, ED-52 a ED-56, ED-63 a ED-67, §2.4, §3, §5 a §7); consolidado 18
- **Base:** `docs/levantamento/gis/19a-gisbase.md`, `19b-concorrentes.md`, `19c-normativos-e-padroes.md`,
  `19d-melhores-praticas-modelo-de-dados.md`, `19e-requisitos-de-editais-gis.md`;
  `docs/levantamento/editais/18d-aderencia-cadastro-medicao-gis.md` (E12 a E16, decisões 3 e 4);
  `18-ADERENCIA-EDITAIS-CONSOLIDADO.md` (ED-13, ED-52 a ED-56); código de `intelligence/sigsan-gis`
- **Ajuste de 12/10/2026 (GIS, v2.11), antes de qualquer implementação:** resolvidas as pendências deste ADR (plano
  §2.4; projeto §14.2). (1) **Capacidade do núcleo** (seção "Efeito no esforço e na capacidade", item 1): o perfil 3 do
  ADR-0029 (GIS e dados) é antecipado para dez/2026–jan/2027 e executa o núcleo (GIS-01 a GIS-08) no P1 a P3, sob
  revisão do dono do produto, como uma das 4 contratações da trilha (ADR-0029, ajuste v2.11); homologação local e
  lançamento mantidos. Com entrada no início de janeiro de 2027 e onboarding de 8 semanas a 50%, ele entrega cerca de 16
  semanas-pessoa até 28/05/2027 contra 22: faltam cerca de 6 (cerca de 2,5 com entrada em 07/12/2026), e o núcleo
  termina por volta do início de julho de 2027, durante a homologação local e antes do M10; o tratamento da falta é
  pendência nova do plano §2.4, e a decisão 4 continua valendo até ela. (2) **Gatilho** (item 3 da mesma seção):
  limiares de 95% e 85%, que cobrem as velocidades mínimas recalculadas (cerca de 93% sem a quinta pessoa e 84% com
  ela). (3) **Ligação:** a antecipação da base para a P2.23 (seção "Forma") fica confirmada. (4) **Rede do piloto** na
  homologação local: redes, peças e estruturas reais; ligações, ramais e imóveis sintéticos ou anonimizados, com
  coordenada deslocada ou agregada por quadra (ADR-0033, ajuste v2.11). (5) **LGPD no mapa:** fora do backoffice
  autenticado (portal, WMS e OGC públicos ou entregues a terceiros, relatórios externos), dados de imóveis e ligações só
  agregados por quadra ou setor com no mínimo 5 imóveis; rede e ativos podem ser publicados; parâmetro
  `gis.public_aggregation_min_units`, classe c, só ampliável, com piso 5 (`PARAMETROS-DO-TENANT.md`). (6) **Normas:** a
  tarefa derivada "Compra das normas para curadoria do catálogo" passa a ser a P1.28 do plano, no início do P1, antes do
  catálogo, com NBR 12586, 12587, 12218, 9649, 5647, 7665, 7675 e 15561 e as demais apontadas no projeto (§4.1), custo
  a estimar. (7) **DGN v8:** conversão assistida na implantação, para DWG ou DXF, com ferramenta do cliente ou da
  equipe; o importador aceita DGN v7, DWG, DXF, SHP, GPKG e INP do EPANET e do SWMM. (8) **OGC** (seção "Forma"):
  OGC API – Features e WMS, autenticados por tenant, na onda B, junto do GIS avançado; o WMS servidor deixa de ser só
  por exceção, é feito na `api` sem servidor de mapas novo (se a renderização exigir um, só em leitura e com exceção no
  ADR-0010 antes da implementação) e acrescenta cerca de 1 semana-pessoa à ED-67. A tabela "Efeito no esforço e na
  capacidade" lê-se com GIS no total de 62 semanas-pessoa (22 no núcleo, 40 na trilha: 5 na onda A, 31 na B, 4 na C),
  trilha do P4 de 225,8, prontidão de POC de cerca de 217,8 e trilha completa de cerca de 234,2; o item 2 dela, com
  capacidade de cerca de 251 até 30/09/2028 (o perfil 3 chega à trilha sem onboarding), folga de cerca de 7% e prontidão
  de POC por volta de meados de agosto de 2028. As tarefas derivadas "Decisão de capacidade para o núcleo do GIS" e
  "Revisão do gatilho da quinta contratação" ficam cumpridas; decisão do dono do produto.
- **Ajuste de 12/10/2026 (v2.12):** referências a equipe e capacidade sem efeito (ADR-0029 revogado). O item (1) do
  ajuste v2.11, os itens 1 a 3 da seção "Efeito no esforço e na capacidade", a negativa "Capacidade" e as tarefas
  derivadas de capacidade e gatilho deixam de valer; a tabela da mesma seção fica só como indicação de esforço, e o
  núcleo continua no P1 a P3, com cada parte no marco da sua fase.
- **Ajuste de 12/10/2026 (GIS, v2.12), antes de qualquer implementação:** o WMS servidor da onda B (item 8 do ajuste
  v2.11; seção "Forma", OGC) é publicado pelo **GeoServer**, por exigência de edital, como exceção do ADR-0010: só
  leitura, sem porta publicada, acessível só pela `api`, que autentica e encaminha ao workspace do tenant da
  credencial; um workspace por tenant, com role de banco própria do tenant, somente leitura, presa ao schema dele ou a
  views filtradas pelo tenant da role no schema compartilhado, sem acesso cruzado; simbologia em SLD gerado da mesma
  fonte única que gera o mapa web e o projeto QGIS (decisão 6). OGC API – Features e WFS 2.0 com `Transaction` no
  pacote continuam na `api`, e a escrita continua fora do servidor de mapas (decisão 7). A frase "na `api` sem
  servidor de mapas novo" do item 8 lê-se só para OGC API – Features e WFS; estimativa da ED-67 mantida; decisão do
  dono do produto.
- **Ajuste de 14/10/2026 (plano v2.13):** (1) a lista fixa de `domain` de `gis_feature_class` é substituída por `gis_domain` (V13_1), carregada pelo pacote de vertical (ADR-0037); os domínios `WATER`, `SEWER`, `STORM`, `WASTE` e `CONTEXT` são os do pacote de saneamento; (2) a simbologia da decisão 6 passa a ter conjunto SVG próprio de fonte única, com símbolos por tipo e estado e aceite do dono por lote (ADR-0036; P1.30, GIS-06, ED-64, ED-65).

## Contexto

O módulo `sigsan-gis` tem uma tabela única, `gis_network_element`, com 40 tipos fixos por `CHECK`, geometria genérica,
material em texto livre, `status` em texto, idade armazenada e `properties` JSONB sem validação; a topologia é
recalculada por proximidade (tolerância em graus) só para gerar o `.inp` do EPANET. Não há catálogo, ciclo de vida,
histórico, metadado de precisão, vínculo com a ligação, API nem tela (19d §1; 18d §1.3, item 4).

O ADR-0028 (item b) trouxe para a trilha de editais do P4 o GIS web, a edição, a migração do cadastro técnico em DWG, o
acesso pelo QGIS e as manobras "sobre o modelo de rede existente". Os levantamentos 19a a 19e mostraram que esse modelo
não sustenta cadastro técnico: os editais pedem topologia na edição, catálogo de peças nas companhias com norma própria,
ciclo de vida, metadados de coleta e integração com o comercial (19e §2); o padrão de mercado converge para classe e
tipo com catálogo, topologia nó-arco validada, zonas por traçado, isolamento com estado da válvula e planejamento como
versão (19b §4); a NR 16/2026 da ANA faz do cadastro georreferenciado base do inventário patrimonial das redes, e a NR
12/2025 exige cadastro técnico de drenagem (19c §1). Nenhum concorrente nacional publica modelo de dados nem liga, no
mesmo banco, técnico, comercial e OS (19b §5).

O dono do produto decidiu o escopo, o momento e a forma do GIS em oito cartões, em 12/10/2026.

## Decisão

1. **Domínios.** Água, esgoto, drenagem e resíduos sólidos no mesmo modelo de dados, sobre o mesmo catálogo e a mesma
   topologia. Água e esgoto completos primeiro; drenagem e resíduos em seguida.
2. **Catálogo brasileiro de fábrica.** O produto traz catálogo de materiais, DN e DN2, ângulos de curva, classes de
   pressão, juntas, peças e conexões, baseado nas NBR e nos padrões Corsan/Aegea e Saneago. Cada tenant ativa,
   desativa e acrescenta itens, sem código. Valor que não vem de norma conferida entra marcado como valor de referência
   a validar.
3. **Edição por pacotes revisados.** Toda mudança na rede em operação entra por pacote de edição revisado por outra
   pessoa antes de ser aplicada, qualquer que seja o canal (web, QGIS, app de campo, importação). Ciclo de vida das
   feições: projetado → em obra → em operação → abandonado. Histórico temporal completo, com consulta da "rede na
   data".
4. **Momento.** O núcleo do GIS entra **antes da homologação local do piloto**, entre o P1 e o P3: modelo e catálogo,
   topologia, mapa web de consulta com tiles vetoriais, ligação da feição com imóvel, ligação e ramal, importador de
   DWG e SHP e mapas temáticos. Na trilha de editais (ondas A e B): edição web completa, QGIS, isolamento por válvulas
   e manobras com OS em lote, drenagem, resíduos e campo.
5. **Precisão.** Metadado por feição: método de captura (RTK, topografia, digitalização, ramal automático e demais),
   exatidão estimada, data, responsável e origem. Meta por tenant e por classe de feição, com padrão de ±0,10 m na
   horizontal e ±0,05 m na vertical. Mapa e relatórios destacam o que está fora da meta e distinguem cadastro
   aproximado de cadastro levantado.
6. **Simbologia.** Padrão Ziip por tipo e por estado (projetado, em operação, abandonado), consistente no mapa web, no
   projeto QGIS gerado e nos PDFs, sobrescrevível por tenant.
7. **QGIS.** Role de banco individual por usuário, sem privilégios, presa ao tenant; edição só por views com gatilhos
   `INSTEAD OF`, que gravam no pacote de edição e passam pela validação; projeto QGIS gerado pelo produto; endpoint
   próprio, com TLS e auditoria, fora do pooler.
8. **Hidráulica.** EPANET (água) e SWMM (esgoto e drenagem) juntos, por exportação e importação a partir da topologia.

## Forma

O projeto está em `docs/arquitetura/GIS-CADASTRO-TECNICO.md`. Pontos que este ADR fixa:

- **Modelo.** Papel topológico fixo no código (`NODE`, `ARC`, `CONNECTION`, `STRUCTURE`, `ZONE`, `POI`); classe, tipo,
  item de catálogo, listas de valores e regras de conectividade em tabelas; atributos de filtro, simbologia, indicador
  e hidráulica em colunas tipadas; o restante em `attributes` JSONB validado por JSON Schema do tipo, na `api` e no
  banco. `gis_network_element` vira view de compatibilidade até a exportação hidráulica ler o modelo novo.
- **Regras no banco.** Topologia (snap com tolerância do tenant em metros, divisão de arco), validação do JSONB,
  histórico, trava por área e marcação de áreas e tiles sujos são gatilhos, porque há escrita fora da `api`.
- **Referência.** Armazenamento em SIRGAS 2000 (EPSG 4674); UTM de trabalho por tenant em toda a faixa de fusos do
  país; altitude normal no Datum de Imbituba (Santana no Amapá), com conversão do GNSS pelo hgeoHNOR2020 no servidor.
- **Histórico.** Tempo do negócio (`installed_on`, `abandoned_on`) e tempo do sistema (`gis_*_history` com
  `tstzrange` e `EXCLUDE`), porque o PostgreSQL 17 não tem `WITHOUT OVERLAPS`.
- **Catálogo de fábrica** em migration repetível de tenant, por *upsert* de chave estável, sem tocar no que o tenant
  ativou ou criou.
- **Ligação.** A base da ligação de água e esgoto (`service_connections`, 1:1 com o imóvel por tipo) é antecipada da
  ED-13 para o núcleo (P2.23), sem mudar o ciclo de faturamento; o restante da ED-13 continua na onda A.
- **Publicação.** Tiles MVT pela `api` (`ST_AsMVT`), cache por tenant e versão de camada, sem CDN pública e sem
  servidor de mapas novo (ADR-0010).
- **pgRouting** só para lote (componentes conexos) e grafo de ruas (rotas de coleta); traçado e isolamento em Java
  sobre a topologia persistida.
- **OGC.** Cliente WMS no núcleo; OGC API – Features (partes 1 a 3) e WFS 2.0 com `Transaction` gravando no pacote, na
  `api`, na trilha; WMS servidor só por exceção no ADR-0010.

## Efeito no esforço e na capacidade

| Bloco | Plano v2.9 | Plano v2.10 |
|---|---|---|
| GIS no total | 20 semanas-pessoa (ED-52 a ED-56) | 61 (22 no núcleo antes do P4, 39 na trilha: 5 na onda A, 30 na B, 4 na C) |
| Antes do P4 | 13,3 | 35,3 |
| Trilha do P4 (ondas A, B, C) | 206,3 | 224,8 |
| Prontidão de POC (ondas A e B e tarefas originais do P4) | ≈ 199 | ≈ 216,8 |
| Trilha completa e tarefas originais do P4 | ≈ 215 | ≈ 233,2 |

1. **Núcleo sobre o dono do produto.** As 22 semanas-pessoa do núcleo caem no P1 a P3, fases sem folga, antes da
   entrada da equipe do ADR-0029. Equivalem a cerca de 26 semanas de calendário de uma pessoa. Sem capacidade
   adicional, a homologação local do piloto escorrega de jun–jul/2027 para cerca de dez/2027–jan/2028, com efeito em
   cadeia na entrada da equipe, no lançamento, no atestado e na primeira licitação. A forma de absorver fica como
   pendência do dono do produto no plano §2.4, até 27/11/2026 (M4), com as opções do projeto (§14.2, P-1).
2. **Trilha com 4 contratados.** Capacidade até 30/09/2028 de cerca de 247 semanas-pessoa contra cerca de 233,2:
   folga de cerca de 6% (antes, 15%); trilha completa por volta do início de setembro de 2028; prontidão de POC (M12)
   por volta de meados de agosto de 2028 (faixa de julho a setembro), em vez do fim de julho.
3. **Gatilho da quinta contratação.** Para fechar a trilha em 30/09/2028 passam a ser necessárias velocidades de pelo
   menos 94% da planejada sem a quinta pessoa e de 85% com ela; os limiares do ADR-0029 (90% e 80%) deixam de garantir
   a data. A revisão fica como pendência do dono do produto no plano §2.4, até o M8.

## Consequências

### Positivas

- O cadastro técnico passa a ter modelo de nível Utility Network sobre PostGIS, sem licença Esri, com catálogo
  brasileiro e topologia validada em todos os caminhos de escrita.
- Técnico, comercial e OS no mesmo banco: a manobra gera a lista de imóveis afetados, o aviso e a OS em lote, o que
  nenhum concorrente documentado entrega de ponta a ponta (19b §5).
- O requisito mínimo de mercado (19e §4.2) fica coberto; SWMM, drenagem, resíduos, catálogo formal e campo offline
  viram diferencial.
- O piloto e a POC veem mapa, camadas, temáticos e importação já na homologação local.
- O cadastro passa a alimentar SINISA, NR 16/2026, NR 12/2025, gestão de ativos e modelagem hidráulica por regra
  reprodutível e "rede na data".

### Negativas

- **Capacidade:** +40,5 semanas-pessoa até 2028 (consolidado 18); núcleo de 22 semanas sobre uma pessoa no P1 a P3 e
  folga da trilha reduzida a cerca de 6% (seção anterior).
- **Regra de negócio em gatilhos:** erro num gatilho afeta web, QGIS, app e importação ao mesmo tempo; exige teste de
  banco por gatilho e por SQL direto.
- **Revisão obrigatória** acrescenta um passo a toda correção, inclusive de atributo simples; o prestador pequeno
  precisa de duas pessoas habilitadas a revisar.
- **Catálogo** com valores ainda sem norma conferida; exige curadoria e a compra das normas.
- **QGIS** continua sendo caminho de escrita fora da `api`, agora restrito ao pacote; exige endpoint próprio, auditoria
  e pentest.
- **Extensões no RDS** (`pgrouting`, `pg_jsonschema`, `pgaudit`) a conferir antes do lançamento.
- **Base da ligação** antecipada para o P2 cria uma tabela do cadastro antes da ED-13, que a completará.

### O que muda no código

- `intelligence/sigsan-gis`: tabelas novas (`V13_1` a `V13_8`), migration repetível do catálogo, gatilhos de topologia,
  validação, histórico, pacote, trava e tiles sujos; view de compatibilidade `gis_network_element`;
  `NetworkTopologyBuilder` sobre `node_1_id`/`node_2_id`; serviços e rotas `/api/v1/gis/*` (tiles, feições, pesquisa,
  pacotes, catálogo, importação, temáticos, "rede na data"); `worker` com validação por área suja, invalidação de tiles
  e importador GDAL.
- `commercial/sigsan-registry`: `service_connections` (`V3_7`).
- Core: definições dos parâmetros `gis.*` (core `V9_6`); mapeamento de roles do QGIS (core `V12`, na trilha).
- Frontend: mapa web de consulta, catálogo, revisão de pacotes, importação, qualidade e temáticos; depois, editor web e
  campo no app Capacitor.
- `pom.xml` do módulo sem as menções a QGIS Server e EPANET WASM.

### O que muda no roadmap

- Plano v2.10: P1.26 e P1.27 (modelo e catálogo), P2.21 a P2.23 (topologia, pacotes, vínculo comercial), P3.25 a
  P3.27 (mapa web, importador, temáticos).
- Trilha: ED-52 sai da onda B para o P1 a P3; ED-53 passa à onda A; ED-54 e ED-55 crescem na onda B; ED-56 cresce na
  onda C; ED-13 cede 0,5 semana à P2.23; novas ED-63 (EPANET e SWMM), ED-64 (drenagem), ED-65 (resíduos), ED-66 (campo
  GIS) e ED-67 (exportação, devolução da base e OGC) na onda B.
- Marcos: M6 a M8 passam a incluir o núcleo do GIS; M12 por volta de meados de agosto de 2028; datas de M6 a M10
  sujeitas à pendência de capacidade do núcleo.

## Alternativas consideradas

- **Manter o modelo atual e só acrescentar API e telas (forma do ADR-0028, item b, até a v2.9).** Rejeitada: sem
  topologia gravada, catálogo, ciclo de vida e histórico, não há isolamento confiável, validação na edição nem
  inventário regulatório.
- **Só água e esgoto, com drenagem e resíduos em modelo separado ou fora do produto.** Rejeitada pela decisão 1: a Lei
  14.026/2020 trata drenagem e resíduos como saneamento, e nenhum concorrente nacional os cobre com GIS.
- **Tipos fixos no código ou catálogo vazio a cargo do tenant.** Rejeitadas: tipo fixo exige migration para cada peça;
  catálogo vazio transfere ao cliente pequeno um trabalho que o mercado não faz.
- **Edição direta na rede em operação, com histórico, sem revisão.** Rejeitada pela decisão 3: cadastro de terceiros,
  importação e campo precisam de conferência antes de valer.
- **Versionamento por ramo (modelo da Esri).** Rejeitada: áreas sujas recriadas no *reconcile* e no *post*, validação
  nas duas versões e transação longa no banco (19d §5.1); pacotes com ciclo de vida cobrem projeto e obra.
- **Núcleo do GIS na trilha do P4, como na v2.9.** Rejeitada pela decisão 4: o piloto e o ensaio de POC ficariam sem
  mapa até 2028.
- **Precisão como atributo livre ou uma meta única do produto.** Rejeitadas pela decisão 5: a meta muda por prestadora
  e por classe, e o cadastro aproximado precisa aparecer como tal.
- **QGIS com escrita direta nas tabelas, com `WITH CHECK` (ADR-0028, item b, até a v2.9), ou edição por GeoServer com
  WFS-T.** Rejeitadas pela decisão 7: a primeira contorna o pacote e a revisão; a segunda põe um servidor de mapas no
  caminho de escrita, contra o ADR-0010.
- **Só EPANET.** Rejeitada pela decisão 8: esgoto e drenagem precisam de SWMM, e o modelo já guarda o que ele pede.
- **Servidor de tiles Martin.** Mantido como plano B, por exceção no ADR-0010, se a latência pela `api` não atender.

## Tarefas derivadas

| Tarefa | Fase |
|---|---|
| Decisão de capacidade para o núcleo do GIS (plano §2.4) | até 27/11/2026 (M4) |
| Modelo do núcleo, histórico, metadados, SRS, parâmetros `gis.*` e migração do `gis_network_element` (P1.26, GIS-01) | P1 |
| Catálogo de fábrica de água e esgoto e administração por tenant (P1.27, GIS-02) | P1 |
| Compra das normas para curadoria do catálogo | P1 |
| Topologia, regras de conectividade, validação contínua e EPANET sobre a topologia (P2.21, GIS-03) | P2 |
| Pacotes, revisão, ciclo de vida, travas e "rede na data" (P2.22, GIS-04) | P2 |
| Base da ligação, vínculo feição ↔ imóvel ↔ ligação ↔ ramal e ramais automáticos (P2.23, GIS-05) | P2 |
| Tiles vetoriais, simbologia padrão e mapa web de consulta (P3.25, GIS-06) | P3 |
| Importador DWG, DXF, SHP e GPKG com relatório de qualidade (P3.26, GIS-07) | P3 |
| Temáticos, precisão, OS no mapa e relatórios do cadastro (P3.27, GIS-08) | P3 |
| Revisão do gatilho da quinta contratação (plano §2.4) | até o M8 (28/05/2027) |
| Edição web completa (ED-53, GIS-09) | P4, onda A |
| QGIS, traçados e manobras, EPANET e SWMM, drenagem, resíduos, campo, exportação, OGC e importação ampliada (ED-54, ED-55, ED-63 a ED-67; GIS-10 a GIS-18) | P4, onda B |
| Ativos e inventário pela NR 16/2026 (ED-56, GIS-19) | P4, onda C |
| Conferência de `pgrouting`, `pg_jsonschema`, `pgaudit` e do SRID 5396 no RDS | P0.26 (documental) e LC.1 (prática) |
