# II-6 — DMA com balanço hídrico e VMN + ranking de DMC por retorno — NOTAS

**Natureza:** camada ADITIVA (pacote novo `br.com.ziipsan.intelligence.losses.dma`,
módulo `intelligence/ziipsan-losses`) sobre a base viva GIS-03/ED-85.
Nenhuma classe da ED-85 é alterada; nenhuma tela (decisão 57).

## O que a base já tinha (ED-85, lido — não refeito)

- `DmcDistrict`/`DmcSectorService`: DMC vivo ancorado em `dma_district`
  (GIS-03, V2_2) + zona DMA de `gis_zone` (V13_2), macromedidores ED-17.
- `WaterBalanceCalculator`: balanço MENSAL por DMC em `double`.
- `NightFlowAnalyzer`: VMN sobre amostras HORÁRIAS em `double`.
- `DmcPriorityQueue`: fila pelo EXCESSO sobre a meta do tenant.

## O que o II-6 acrescenta

1. **Agregação de DMA** (`DmaModel.DmaDefinition` + `ExactWaterBalance.aggregate`):
   agrupamento nomeado de DMCs do mesmo setor; a agregação exige
   exactamente os membros da definição, no mesmo período — subconjunto
   em silêncio recusa.
2. **Balanço por período arbitrário com aritmética BigDecimal exacta**
   (`ExactWaterBalance.calculate`): mesmas identidades do balanço
   hídrico da ED-85, sem arredondamento. Componentes que a base não
   expõe (perdas aparentes) saem AUSENTES e nomeadas em
   `absentComponents` — nunca estimadas. DMA sem consumo: perdas =
   entrada líquida. Sem entrada (e sem exportado): todas as parcelas
   derivadas são zero exacto.
3. **VMN tipificada** (`MinimumNightFlow`): série em qualquer
   cadência declarada; janela nocturna é PARÂMETRO
   (`LossParameters.NightWindow` da ED-85); cobertura verificada
   noite a noite — desfechos `COMPLETE` / `NO_SAMPLE_IN_WINDOW` /
   `INCOMPLETE_COVERAGE`; VMN líquida só com consumo legítimo
   declarado (sem ele, ausente declarada); líquida negativa exposta,
   não truncada.
4. **Ranking de DMC por retorno** (`DmcReturnRanking`): índice
   declarado = perdas recuperáveis × tarifa (parâmetro); tarifa é
   `[A DEFINIR]` (sem fonte nesta entrega) ⇒ sem tarifa o ranking sai
   por volume de perdas com o modo `LOSS_VOLUME_ONLY` declarado.
   Desempate determinístico: índice desc → volume desc → código asc.

## O que NÃO existe aqui (por desenho)

- Nenhum valor de perda "aceitável", meta ou limiar inventado.
- Nenhuma série real: séries de vazão/macromedição são integração
  (P-II6-SERIES) — as provas correm sobre fixtures.
- Persistência/JDBC/Spring: portas e snapshots SQL em
  `db/pendente/II-6__dma_aggregation.sql` (sem número Flyway);
  a fiação viva é pendência de integração (P-II6-FIACAO).
