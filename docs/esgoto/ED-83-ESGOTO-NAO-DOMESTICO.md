# ED-83 — Esgoto não doméstico e carga poluidora do usuário

Núcleo puro Java (`shared/ziipsan-core`, pacote
`br.com.ziipsan.core.nondomestic`), sem JPA/Spring: persistência,
controllers, telas e a fiação das portas são da integração.

## Desenho

- **Cadastro** (`DischargerRegistration`): gerador não doméstico
  (indústria, agroindústria, hospital, posto, lavanderia, outro
  declarado), ponto de lançamento, marca `non_domestic_discharger`
  declarada para a unidade consumidora (a gravação na tabela da
  unidade é da integração). Autorização de lançamento só com
  caracterização do efluente (`EffluentCharacterization`, com
  pré-tratamento às expensas do usuário, NR 11/ANA) e termo de
  compromisso referenciado — o texto do termo é do processo
  [SEM FONTE aqui].
- **Rota ED-22**: sem cadastro comercial, o usuário é atendido pela
  unidade avulsa `CASUAL` da ED-22 (`Route.CASUAL_VIA_ED22`), via
  `CommercialRegistrationPort`. Sem nenhuma das duas, recusa.
- **Padrão de lançamento** (`UserStandardCatalog`): catálogo do
  prestador SOBRE o `EffluentCatalog` real da ED-105 — linha revisada
  do prestador prevalece; na falta, a linha revisada da base; sem
  linha revisada, `INDETERMINATE`. Nenhum limite nasce no código nem
  no SQL (tabelas nascem vazias).
- **Amostragem do prestador** (`ProviderSamplingPlan`,
  `ProviderSampling`): vencimentos por `effluent.sampling_interval_days`
  (parâmetro do tenant, sem fábrica) a partir da autorização; a
  análise só se registra com o recebimento pelo laboratório provado
  pela cadeia de custódia da ED-50 (porta real da ED-105).
- **Automonitoramento** (`SelfMonitoringReport`): declaração do
  usuário, avaliada contra o mesmo catálogo pelo
  `NonDomesticService`; não substitui amostragem nem vistoria.
- **Vistoria por OS**: `FieldInspectionOrderPort` (motor vigente);
  a ED-83 guarda o id, não cria motor de OS.
- **Não conformidade** (`NonConformityCase`): nasce só de veredito
  `NON_CONFORMING`; notificação (prova pelo canal vigente) antes da
  penalidade; valor e base da penalidade são dados do processo
  [SEM FONTE]; resolução exige evidência de correção e OS de
  vistoria de conferência.
- **Cobrança complementar** (`NonDomesticChargeCalculator`): devolve
  o componente `NONDOMESTIC_EFFLUENT` para o motor da P2.5 compor na
  conta — este pacote não emite conta, não cria débito nem liga
  cobrança automática. Modelos (`sewer.nondomestic_charge_model`,
  classe d): `VOLUME_PERCENT` (percentual do volume de água × tarifa
  unitária de esgoto vigente), `POLLUTANT_LOAD` (carga kg × preço/kg
  — **só com a previsão da agência**, guarda literal do aceite) e
  `FIXED_BY_CLASS` (taxa da classe na tabela do prestador; classe
  sem taxa recusa). Carga: `kg = mg/L × m³ / 1000` (conversão
  declarada em `PollutantLoad`).

## Parâmetros do tenant (sem valor de fábrica)

| Chave | Forma | Efeito da ausência |
|---|---|---|
| `effluent.user_standard_catalog` | versão do catálogo do prestador | erro nomeado (`MissingParameterException`) |
| `effluent.sampling_interval_days` | inteiro > 0 (dias) | erro nomeado |
| `sewer.nondomestic_charge_model` | `VOLUME_PERCENT`/`POLLUTANT_LOAD`/`FIXED_BY_CLASS` | erro nomeado |

## Fronteiras

- ED-105: catálogo-base e porta de laboratório consumidos reais,
  sem duplicar. ED-50: laboratório/custódia consumidos reais (via
  ED-105), sob correção paralela de outro lote — ver ESPELHO-NOTA.
- ED-22: rota do avulso consumida por porta; nenhum cadastro,
  agrupador ou contrato de grande usuário é duplicado.
- P2.5: o componente é dado para o motor; a composição na conta, o
  snapshot e a emissão são da P2.5/integração.
- CDA (ED-31) desligado por desenho; item 32 (arquitetura tarifária)
  não executado; os 14 valores da P3.9 não são tocados.
