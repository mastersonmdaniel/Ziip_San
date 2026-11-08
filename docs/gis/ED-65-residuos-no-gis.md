# ED-65 / GIS-14 — Resíduos sólidos no GIS: notas de integração e ensaio

Spec: JSON do plano v3.0 (`tarefas-onda-B.json`, id `ED-65`) — "aceite
da GIS-14: contêineres, PEV, ecopontos, lixeiras, pontos de coleta e
de descarte irregular com foto e OS de limpeza; setores e rotas de
coleta sobre o grafo de logradouros com pgRouting; imóvel ligado ao
setor para a taxa da ED-25; TR-1049; camadas de resíduos na
importação (SG-1340, parte); símbolos de resíduos
(ICONES-E-SIMBOLOGIA §4.3) em cinco estados, gerados no mesmo
pipeline da P1.30, com aceite do dono". Fonte de desenho:
GIS-CADASTRO-TECNICO §4.5, §5.4, §5.7, §11 e linha GIS-14 do §13.2.

## O que a integração precisa fazer (nesta ordem)

1. **Numeração Flyway.** Reservar o próximo número livre da série
   tenant do GIS (a main está na V13_12) e mover
   `db/pendente/ED-65__gis_solid_waste.sql` para
   `db/migration/tenant/V13_<n>__gis_solid_waste.sql`. Atualizar o
   caminho na `Gis14WasteIT`.
2. **Carga do grafo de logradouros.** As tabelas
   `gis_waste_street_vertex`/`gis_waste_street_edge` nascem vazias:
   a carga (quebrar as vias da camada de contexto STREET nos
   cruzamentos, montar source/target e o custo em metros) é rotina
   de conjunto da implantação, como o snap em lote da GIS-07.
   Depois da carga, `SELECT gis_waste_project_features(tenant)`
   (re)projeta os pontos em operação nos vértices.
3. **Extensão pgRouting.** Conferir/criar a extensão no banco do
   tenant (o ADR-0010 registra a extensão; disponibilidade no RDS é
   da P0.26/LC.1, como o §5.7 manda). Sem ela,
   `gis_waste_pgr_distance` levanta erro explícito e rota nenhuma
   nasce — comportamento provado na IT nos dois ramos.
4. **OS de limpeza.** Ligar `gis_waste_cleanup_request` ao núcleo de
   OS (ED-36): ao abrir a OS do pedido REQUESTED, gravar
   `service_order_id` e mover o pedido a ORDER_OPENED. Nenhum tipo
   de OS foi inventado nesta entrega — o catálogo de tipos de OS é
   do núcleo.
5. **Taxa da ED-25.** O chamador da ED-25 lê o setor do imóvel em
   `gis_waste_property_sector` (status LINKED) via
   `GET /api/v1/gis/waste/properties/{id}/sector-link`; o (re)cálculo
   é `POST` no mesmo caminho. Imóvel UNLINKED não tem setor (a taxa
   não nasce); REVIEW é sobreposição de setores a corrigir no
   desenho, não escolha automática.
6. **Parâmetros de estimativa de rota.** Velocidade média e tempo
   por parada NÃO têm padrão nesta entrega: cadastrar como
   parâmetros do tenant (sugestão de chaves, a confirmar no catálogo
   de parâmetros: `waste.route_avg_speed_kmh`,
   `waste.route_minutes_per_stop`) e passá-los no `RouteInput`;
   sem eles a rota nasce com extensão e sem estimativa de tempo.
7. **Símbolos (P1.30 + aceite do dono).** Gerar a arte dos 13 tipos
   a partir das chaves de `GisWasteSymbols` (inventário em
   `GET /api/v1/gis/waste/symbols`) no pipeline da P1.30 e submeter
   a folha de contato ao ACEITE DO DONO — pendência do dono, não
   presumida. O quinto estado declarado nesta entrega é o
   OUT_OF_TARGET do §10.2 (ver o marcador em `GisWasteSymbols`):
   confirmar contra o ICONES-E-SIMBOLOGIA §4.3 quando o documento
   estiver disponível; se ele nomear outro estado, a troca é na
   lista `SYMBOL_STATES` (dado), sem mudar cobertura nem manifesto.
8. **Pacotes de edição.** Ligar a criação de pontos/setores aos
   pacotes da GIS-04 (IMPORT/FIELD/SERVICE_ORDER) se a integração
   decidir que o cadastro operacional de resíduos também passa por
   pacote — nesta entrega a gravação é direta (pontos sem topologia
   de rede, §3.6.6), fronteira declarada no relatório.

## TR-1049 e SG-1340

- **TR-1049**: o roteiro TR não está nesta VM (como os roteiros SG
  nas entregas vizinhas). O conteúdo da GIS-14 no §13.2 é a
  referência interna do que o TR confere: catálogo do §4.5
  carregado; pontos/descarte com foto e OS; setores; rotas sobre o
  grafo com pgRouting (sequência, extensão, tempo); imóvel↔setor
  para a taxa; veículo por catálogo até a ED-59; camadas SG-1340
  (parte). O ensaio com o roteiro real é pendência nomeada.
- **SG-1340 (parte)**: a parte da GIS-14 são as 13 camadas de
  resíduos do perfil GENÉRICO (`GisWasteImportLayers` + seção 9 do
  SQL). As demais camadas/formatos são da GIS-07/GIS-18.

## Ensaio sugerido (CI do dono)

1. `scripts/ci.sh` com a IT `Gis14WasteIT` (4 testes: catálogo,
   contenção do vínculo, foto/limpeza, grafo/pgRouting nos dois
   ramos da extensão).
2. Massa sintética: um bairro com 2 setores (1 sobreposição
   proposital), 20 pontos coletáveis, 1 descarte com foto e um
   grafo de ~30 arestas; conferir sequência/extensão da rota contra
   o `GisWasteRoutePlanner` (a divergência pgr_TSP × planner é o
   item de validação que o §5.7 pede para a GIS-14).
3. Varrição (percorrer vias): `pgr_chinesePostman` segue
   experimental no §5.7 — NÃO usar para setor de varrição até a
   validação; setor SWEEPING nasce como setor comum nesta entrega.
