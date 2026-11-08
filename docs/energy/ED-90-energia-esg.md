# ED-90 — Eficiência energética e ESG (OPE-08, GES-21)

Núcleo puro Java em `intelligence/ziipsan-reporting`, pacote
`br.com.ziipsan.reporting.energy`. Sem registro próprio de indicadores:
os indicadores vivem no registro único da P3.41 (JOR-19).

**Correção de redação (D7 da REVISAO-ED-90):** a ED-90 **não** consome
o painel/catálogo da ED-48 — aquele painel é específico do SINISA.
A "publicação" desta entrega é a **marcação de perfil
`PROFILE_TAG=ENERGY_ESG` nas definições materializadas na P3.41**
(`listForProfile` real da P3.41 — o mesmo MECANISMO que a ED-48 usa
para o seu `SINISA`, sem nenhuma classe da ED-48 ser chamada).
Exibir essas definições num painel é fiação (P-ED90-03).

## Energia (OPE-08)

- **Cadastro**: `EnergyInstallation` — instalação de energia por
  unidade operacional (sistema, ETA, elevatória, ETE), com o **número
  da instalação na distribuidora** obrigatório.
- **Importação de fatura**: `EnergyBillImportService` é o portão;
  o leiaute de cada distribuidora é **conferido na implantação**
  (`DistributorLayout.verified` com versão e nota de conferência) e a
  leitura é da porta `EnergyBillFilePort`. **Nenhum leiaute é
  implementado nesta entrega** [SEM FONTE] — sem leiaute verificado, a
  importação recusa antes de ler o arquivo.
- **Eficiência**: `EnergyEfficiency.specificConsumptionKwhPerM3`
  é a DIVISÃO sobre os totais que recebe (kWh ÷ m³, denominador > 0
  obrigatório). **A apuração dos totais por sistema, ETA e
  elevatória e por período NÃO é desta entrega** — é pendência de
  fiação (P-ED90-03): quem chama agrega os totais da unidade/período
  (via variáveis do registro da P3.41 ou adaptador próprio). Nenhum
  volume é buscado na ED-48 por este pacote. Também fazem parte:
  `peakSharePercent` (consumo na ponta) e `assessDemand` — demanda
  medida × contratada com a tolerância `energy.demand_alert_pct` e
  fator de potência × `energy.pf_min`. Sem o parâmetro do tenant, a
  dimensão **não é avaliada** (declarado) — nenhum limiar de fábrica.
  O alerta é de risco de multa; nenhum valor em R$ é calculado
  (nenhuma tarifa consta das fontes).

## Fonte automática × ED-77 (GES-21/OPE-08)

`AutomaticSourceUpgrade`: precedência telemetria → fatura importada →
manual, com o grau de confiança atualizado declarado em
`EnergyDataSource` (MANUAL LOW — o grau da ED-77; IMPORTED_BILL MEDIUM;
FLEET_ED59 MEDIUM; TELEMETRY HIGH). A troca da variável no registro da
P3.41 exige o mapeamento explícito para a view `rpt_*` (desenho da
ED-48); sem ele, a variável manual da ED-77 permanece.

**Correções da REVISAO-ED-90 aplicadas:** (D2) a telemetria tem
instalação/mês próprios na resolução — presente sem fatura, ela NÃO é
ignorada — e valores ≤ 0 de qualquer fonte são recusados nos records
`ResolvedEnergy`/`ResolvedFuel`; (D4) `resolveKwh` recusa fatura de
outro tenant (`ENERGY_BILL_TENANT_MISMATCH`); (D6) a importação amarra
a chave do leiaute à distribuidora pedida e o inventário exige
coerência fonte×origem declarada (FLEET_ED59 só com FLEET_FUEL);
`fleetLitersFromEd59` não recebe tenant — a lista tem de vir
pré-filtrada pela fiação (aviso no Javadoc).

## Combustível (GES-21)

Lido da **ED-59** (que existe na main, camada C4): o adaptador
`fleetLitersFromEd59` soma os litros dos `FuelRecord` reais da ED-59
no período, atrás da porta `FleetFuelPort`. Sem dado da frota, o
**ramo manual do aceite** permanece (grau LOW).

## ESG (GES-21)

- **Catálogo global e versionado de fatores** (`EmissionFactorCatalog`,
  tabela global `esg_emission_factors`): nasce **vazio** — fator só
  entra com a fonte citada; sem fonte, recusa [SEM FONTE]. A carga com
  as fontes é a pendência P-ED90-02.
- **Inventário simplificado** (`EmissionInventory`): escopo 2 =
  energia elétrica; escopo 1 = metano de ETE + combustível. Exige
  `esg.emission_factors_version` declarado; fator ausente na versão ou
  unidade divergente = recusa nomeada. **Correção D3:** os totais só
  somam linhas da mesma `emissionUnit` — divergente (ex.: tCO2e ×
  kgCO2e) é recusa (`EMISSION_INVENTORY_EMISSION_UNIT_DIVERGENCE`),
  porque normalizar exigiria conversão COM FONTE [SEM FONTE]; o
  resultado e a exportação CSV carregam a unidade de emissão.
- **Exportação voluntária** (`SustainabilityExport`): só com
  `esg.report_profile` declarado; o inventário não depende do perfil.
  Nenhuma obrigação de relatório foi identificada para autarquia
  municipal (texto do aceite) — nenhum caminho obriga a exportação.

## Parâmetros (todos sem fábrica)

`energy.demand_alert_pct`, `energy.pf_min`,
`esg.emission_factors_version`, `esg.report_profile` — ver
`ED-90__core_energy_esg_parameters.sql`.

## Pendências

Ver `RELATORIO-ED-90.md` (P-ED90-01 leiaute; P-ED90-02 carga de
fatores com fontes; P-ED90-03 fiação; P-ED90-04 numeração/PG16).
