# GIS-08 (P3.27) — notas de desenho

Decisões que a implementação tomou onde o aceite e a spec
(GIS-CADASTRO-TECNICO) deixam o "como" aberto. Nenhuma amplia o
escopo; as que são interpretação estão marcadas e voltam para a
integração/CI conferirem.

1. **Migration V13_13, não V13_8.** A reserva nominal da P3.27
   (V13_8, `gis_thematic_map`) já foi consumida no repo real pela
   GIS-03/P2.21 (`gis_topology_validation`, na main) e a série
   segue ocupada até a V13_12 (P3.25). Vale o precedente do projeto
   (V13_9 a V13_12): próximo slot livre, troca declarada no
   cabeçalho da migration.

2. **SG-882 sem limiar de fábrica.** Nenhum documento do plano
   (aceite, spec, levantamento 18c/18d) fixa o que é "grande
   consumidor". A semente nasce com `classes_json = []` e o serviço
   recusa renderizar (422) até o tenant declarar as faixas
   numéricas de `billed_consumption`. Inventar um corte (ex.: 50 m³
   ou 2× a média) seria dado fabricado — a mesma doutrina do
   "nenhum dado é semeado" da V4_1 do billing.

3. **SG-877/878/879 sem códigos de fábrica.** `service_types` é
   catálogo do tenant (V9_1, sem semente): não existe código de
   fábrica para "vazamento no cavalete". As três sementes nascem
   com `filter_json.serviceTypeCodes = []`, que o serviço trata
   como "exige configuração" (422), nunca como "todas as OS".

4. **SG-874 = `METER_AVERAGE` apenas.** O `ConsumptionType` da
   P2.5 define METER_AVERAGE como "consumo igual à média do
   hidrômetro" e ADJUSTED_AVERAGE como proporcional aos dias
   (ajuste por dias ou leitura ausente). Só o primeiro é "faturado
   pela média" do SG-874. Interpretação registrada; se o dono
   quiser os dois, é uma linha na view
   `gis_thematic_unit_consumption`.

5. **SG-873 compara com a média do próprio registro vigente**
   (`average_consumption` da `consumption_records` vigente da
   unidade), que é a média que a P2.5 calculou e faturou — não uma
   média recalculada pelo GIS.

6. **Posição da OS.** `service_orders` não tem geometria (V9_1).
   A view `gis_thematic_service_order` resolve, por precedência:
   coordenada do alvo → ponto da unidade consumidora alvo →
   centroide da feição GIS alvo. OS sem nenhum dos três não entra
   na camada (não se inventa ponto). "Equipe" do SG-491 é o
   usuário da `service_order_crew` (não há tabela de equipes no
   núcleo; ED-59 é a dona de equipes de campo).

7. **Extensão em qualquer data.** O núcleo da P3.22
   (ReportQueryBuilder) monta SELECT/GROUP BY com filtros de
   igualdade/faixa e não expressa a regra de dois tempos do §3.7
   (`installed_on <= data` E (`abandoned_on` nula OU `> data`)).
   Por isso: a view `rpt_gis_extensao_rede` (foto na data
   corrente) alimenta o núcleo (GIS-001, PDF/XLSX/CSV), e a função
   `gis_network_extension_at(date)` serve qualquer data pelo
   endpoint do GIS. O setor do arco é o da `gis_zone`
   COMMERCIAL_SECTOR que cobre o centroide (vínculo
   `commercial_sector_id` do §3.6.5); arco fora de zona sai sem
   setor, nunca com setor chutado. DN é o `dn_mm` exato do item de
   catálogo (o aceite pede "DN"; a "faixa de DN" do §11 vira
   agrupamento posterior sobre o valor exato, sem faixas
   inventadas).

8. **Agregado público.** Célula = quadra (`blocks`) ou setor
   (`commercial_sectors`), conforme a definição. `blocks` não tem
   geometria (V3_1): o agregado por quadra sai tabular (código da
   quadra) e o por setor sai com o polígono da `gis_zone`
   comercial. Célula abaixo do mínimo é suprimida (não aparece,
   nem com contagem) e o serviço devolve quantas foram suprimidas.
   O piso 5 é reaplicado em código (`GisThematicRules`) além do
   limite do catálogo de parâmetros (defesa em profundidade da
   classe c). Para fonte não pessoal (rede/precisão), o canal
   público devolve contagens por categoria do tenant — o ponto
   público de rede é o tile da GIS-06, já publicado com as suas
   guardas.

9. **Tokens `map-thematic-*`.** As classes de fábrica usam tokens
   novos `map-thematic-1..4`, no vocabulário `map-*` do ADR-0017
   (a coluna de estilo da GIS-06 só aceita `map-%`). O registro
   dos tokens no tema (P1.30) é pendência declarada; o frontend
   tem paleta de recurso estável por ordem de classe enquanto o
   tema não os resolve — nunca cor inventada em dado.

10. **Categoria principal da unidade (SG-883).** Unidade pode ter
    várias subcategorias; a view-fonte usa a de menor código de
    categoria (desempate por código de subcategoria) para a
    escolha ser determinística. O filtro por categoria do
    backoffice é sobre esse código.
