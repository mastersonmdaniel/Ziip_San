# ED-86 — Gestao de pressao e controle ativo de vazamento

Modulo `intelligence/ziipsan-losses`, pacote novo
`br.com.ziipsan.intelligence.losses.pressure`: zonas de pressao
(`PRESSURE_ZONE`) com limites resolvidos dos parametros do tenant
`pressure.min_mca`/`pressure.max_mca` — SEM valor de fabrica enquanto
a ABNT NBR 12218:2017 nao for conferida (ausencia =
`MissingParameterException`); pontos de controle (`PRESSURE_POINT`)
cuja serie de pressao vem da ED-17 pelo codigo do ponto de medicao
(porta fail-closed); alarme de pressao baixa e alta; ajuste de
valvula redutora de pressao registrado por OS (quem, quando, motivo,
valor anterior e valor novo, com cadeia de continuidade); relacao
pressao x rompimentos por arco (Pearson pressao media x
rompimentos/km, ausente sem base); vinculo com reclamacao de pouca
agua (OPE-04: corroborada / nao corroborada / sem dados); campanha
de pesquisa de vazamento nao visivel por DMC com hipotese,
equipamento, equipe, evidencia, achado no modelo da ED-92
(`LeakDetectionFinding`, DAD-27 — reparo so na OS) e ganho medido
pela vazao minima noturna liquida da ED-85 antes e depois; fila de
suspeitas ponderada pela vazao noturna liquida por ligacao; tempo de
reparo nas fases de conhecimento, localizacao e reparo contra
`leak.repair_phase_targets_hours`; ciclo de pesquisa por classe de
DMC em `leak.survey_cycle_days_by_dmc_class` (OPE-17). Parametros
sao do tenant: nenhum limite, meta ou ciclo tem valor de fabrica
nesta entrega. Correcao pos-revisao (D2/D3): o vinculo de
reclamacao amarra zona x ponto por FK composta
`(tenant_id, zone_id, point_id)` — zona divergente da zona do
ponto e recusada pelo banco — e os retratos tem CHECKs de
consistencia aritmetica em tabela unica (ganho da campanha =
antes - depois; vazao liquida do retrato noturno = minima -
legitimo; desvio do alarme = |observada - limite|; contadores e
desfecho do vinculo coerentes).
