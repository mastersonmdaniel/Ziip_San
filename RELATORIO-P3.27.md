# Relatório — P3.27

## ⚠️ Divergência de título (ler primeiro)

A tarefa chegou intitulada **"Acesso ao roteiro da leitura por QR
Code"**, mas **esse título não existe no plano**. Conferido na
fonte (`fontes/docs__plano-v3__redacao__tarefas-P3.json` e
`docs__PLANO-EXECUCAO-PDCA-R0-R9.md`, tabela de migrations e de
tarefas): a **P3.27 real é "GIS-08: temáticos, precisão, OS no mapa
e relatórios do cadastro"** (origem ADR-0034; ED-52; G(2);
dependências P3.25, P2.5, P3.1, P3.4, P3.22; migration nominal
V13_8). Nenhuma tarefa do plano trata de QR de roteiro de leitura
(o QR de campo aparece na ED-18/ED-20, remetido a outro lugar).

**Decisão tomada:** implementei a P3.27 real (GIS-08), item a item
do aceite dela, em vez de inventar um sistema de tokens QR que o
plano não pede. A atenção de segurança pedida no despacho foi
aplicada à superfície equivalente da GIS-08: isolamento por
tenant, permissão `gis.view-personal` para pontos, e anonimização
por agregação mínima no canal público. **Se o dono queria mesmo
uma tarefa de QR, ela não está no plano v3 sob este número — é
preciso confirmar com ele antes de qualquer push.**

## O que foi entregue

31 arquivos em `~/workspace/ziipsan-producao/p3.27/entrega/`:

- **Migration** `V13_13__gis_thematic_maps.sql` (módulo GIS):
  tabela `gis_thematic_map` (definições de temático por tenant,
  com CHECK que impede temático pessoal sem agregação), 10 views
  `security_invoker` (fontes de unidade/consumo/vencidas/status de
  ligação/hidrômetros/OS/rede/precisão + as duas `rpt_*` do
  núcleo de relatórios), função `gis_network_extension_at(date)`
  e 14 sementes de fábrica (SG-872 a SG-883, rede água/esgoto,
  precisão).
- **Backend Java** (pacote `intelligence.gis.thematic`): CRUD de
  definições com `personal` derivado da fonte (nunca do pedido),
  renderização com legenda automática e filtro por categoria,
  canal público agregado com supressão de célula pequena, OS no
  mapa com filtros e exportação CSV, seleção por polígono com
  estatísticas, relatórios de extensão (qualquer data) e de
  precisão, e os dois `ReportDefinition` externos (GIS-001,
  GIS-002) para o núcleo da P3.22. JSON sempre pela classe `Json`
  do core; catálogo fechado de fontes (nenhum SQL cru do cliente).
- **Testes**: 6 classes unitárias (regras, fontes, SQL com
  paridade placeholder/argumento e sem ponto no SQL público,
  serviço com guardas antes do acesso a dados, definições de
  relatório, consistência da migration) + `Gis08ThematicIT` com 8
  testes de integração sobre massa sintética (cenários de
  consumo, ligação/hidrômetro/vencidas, OS no mapa, grupos de
  precisão, extensão em 1995 × 2026 × 1990, supressão pública
  4 × 6 imóveis, estatísticas do polígono).
- **Frontend**: serviço `gis/thematic.ts` + teste vitest + página
  `GisThematics.tsx` (legenda, filtro por categoria SG-883, aviso
  de cenário que exige configuração).
- **Nota de desenho** `docs/gis/GIS-08-NOTAS.md` com as 10
  decisões/interpretações.

## Cobertura do aceite (item a item)

| Aceite | Como foi atendido |
|---|---|
| `V13_8` (ou sequência global) | Slot ocupado na main (GIS-03) e série ocupada até V13_12 → **V13_13**, troca declarada no cabeçalho, precedente V13_9–V13_12 |
| Temáticos configuráveis, legenda automática, filtro por categoria | CRUD + classes declaradas ou dinâmicas + legenda com contagens + `categoryCode` (SG-883) |
| Cenários SG-873 a SG-883 sobre massa sintética | Views + sementes de fábrica; IT cobre 873/874/875/883, 876/880/881 |
| Temático e relatório de precisão separando na meta / fora da meta / aproximado | View de precisão sobre o `accuracy_status` da GIS-01 + GIS-002 no núcleo |
| OS no mapa (SG-491, TR-534) | View de OS com posição resolvida (alvo → unidade → feição) + filtros endereço/equipe/tipo + CSV (TR-1061) |
| Seleção por polígono com estatísticas (TR-1059) | `POST /gis/selection/statistics`: ligações, economias, consumo médio |
| Relatório de extensão por domínio, função, material, DN, década e setor em qualquer data, pelo núcleo da P3.22 | GIS-001 + view `rpt_gis_extensao_rede` (foto) + função datada para qualquer data |
| Fora do backoffice: só agregado por quadra/setor com mínimo `gis.public_aggregation_min_units` (piso 5, classe c); célula de 4 não publicada; nenhum ponto | Canal público com `HAVING COUNT(DISTINCT unit) >= mínimo`, piso reaplicado em código, SQL sem geometria/id de unidade (provado em teste), IT 4 × 6 |
| Rede e ativos publicáveis | Fontes NETWORK_ARC/PRECISION publicáveis; OS nunca (403) |

## Revisão cruzada — achados e correções

1. **View de vencidas agrupava por `geom`** (GROUP BY em geometria
   pode fundir pontos pela bounding box) → reescrita com
   subconsulta agregada de faturas + JOIN na base.
2. **Helper de massa da IT** duplicava a subcategoria em unidades
   da mesma categoria (violaria o UNIQUE) → reuso da existente.
3. **Dois defeitos no serviço durante a escrita**: lambda chamando
   `rs.getLong` (SQLException não declarada) dentro de
   `computeIfAbsent`, e update/delete que silenciavam em linha de
   fábrica → corrigidos (guarda explícita 403 antes do SQL).
4. **Filtro de lista vazia** (`serviceTypeCodes: []`) geraria
   `IN ()` inválido → o builder pula listas vazias; e
   `requiresConfiguration` distingue "sem restrição" (OS no mapa)
   de "exige configuração" (SG-877/878/879) pelo campo presente.
5. **Validação de filtro** trocada de `JsonNode.properties()` para
   `Json.readMap` (API estável do core/Jackson 3).
6. **`gis_structure`** tem `geom` anulável → views usam
   `COALESCE(geom, geom_point)`.

## Interpretações declaradas (não são invenção silenciosa)

- **SG-882 sem limiar de fábrica**: nenhum documento do plano fixa
  o corte de "grande consumidor"; a semente nasce sem faixas e o
  serviço recusa (422) até o tenant defini-las.
- **SG-877/878/879**: `service_types` é catálogo do tenant sem
  semente — códigos de vazamento não existem de fábrica; sementes
  nascem com lista vazia = "exige configuração".
- **SG-874** = só `METER_AVERAGE` (o `ADJUSTED_AVERAGE` é ajuste
  proporcional por dias, conforme a P2.5).
- **SG-873** compara com a `average_consumption` do registro
  vigente (a média que a P2.5 calculou), não com média recalculada.
- **Equipe** do SG-491 = usuário da `service_order_crew` (não há
  tabela de equipes no núcleo; ED-59 é a dona).
- Tokens de cor `map-thematic-1..4`: registro no tema (P1.30) é
  pendência; o frontend usa paleta de recurso por ordem de classe.

## Pendências para a integração

1. **CI total no PC do dono é a prova** — nada compilou nesta VM
   (sem JDK/Maven/Docker); a IT foi escrita seguindo o padrão da
   `Gis06PublicationIT`, mas nunca executada.
2. **Nova dependência de módulo** `ziipsan-gis → ziipsan-reporting`
   (entregue no pom da entrega) para registrar GIS-001/GIS-002;
   a integração precisa aplicá-la (sem ciclo: reporting não
   depende de gis).
3. **Rota pública** `/api/v1/gis/public/thematics/**` precisa ser
   liberada na cadeia pública do app (resolução de tenant por
   domínio, P2.27) e o chamador de `registerReports` ser ligado na
   subida (mesma pendência de fiação das P3.22/P3.25).
4. Registrar os tokens `map-thematic-*` no tema (P1.30/ADR-0017).
5. Conferir na main a numeração final V13_13 (se outra frente
   ocupar antes, renumerar na integração, como nas anteriores).

**Sem push** — conforme a ordem. Espelho do repo feito via MCP sem
HTTP 429 (2 chamadas, listagens GIS de migrations e de código).
