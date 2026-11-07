-- Perfis regulatorios de fabrica das ERIs de SC (P2.31) -- CARGA REPETIVEL.
--
-- GERADO por scripts/regulatory/generate-regulatory-profile-catalog.py a
-- partir de ScRegulatoryProfiles.java (modulo platform). NAO EDITE A MAO:
-- regenere. Precedente: a carga do catalogo de normas da P1.34
-- (R__norm_catalog.sql, core) e o catalogo de fabrica do GIS (R__ tenant).
--
-- Fonte de cada valor: o mapa de ERIs (docs/normas/partes/04, §4, §5, §6 e
-- anexos A, B e F) e o PARAMETROS-DO-TENANT (G1, G5, G6, G9 e bifurcacoes).
-- Valor com confirmation = REQUIRES_TENANT_VALUE tem value NULL por desenho:
-- o texto da norma nao foi confirmado na leitura (AGR-Tubarao sem condicoes
-- gerais comerciais localizadas; CISAM-SUL com texto so digitalizado;
-- CISAM Meio-Oeste com juros/multa remetidos a legislacao municipal) e a
-- ativacao exige o valor informado pelo tenant. NENHUM valor e inventado.
--
-- Regra local contraria a lei federal NAO entra como valor: entra em
-- regulatory_profile_divergences, e o valor efetivo e o piso federal
-- (corte ate 12 h de sexta -> Lei 14.015/2020; perda da tarifa social apos
-- dois cortes na AGIR -> recusada pela Lei 14.898/2024).
--
-- Idempotencia: upsert pela chave estavel (profile_key, definition_key);
-- reaplicar nunca apaga linha nem toca em regulatory_profiles,
-- regulatory_profile_activations ou parameter_versions do tenant.
-- Contagens esperadas desta versao: 7 perfis,
-- 167 valores, 7 divergencias (guarda no fim).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'ARIS_RN19_2019', 'ARIS', 'ARIS - Agência Reguladora Intermunicipal de Saneamento', 'RN 19/2019 compilada', '2019-03-27'::date, NULL::date, 'Tarifa social da ARIS: RN 56/2026 (50% até 15 m³), citada no ato de condições gerais do perfil; o detalhamento vive nas tabelas do billing (V4_2), fora das chaves G1/G5/G6/G9 desta carga.')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'ARIS_RN19_2019', 'reg.regulator', '"ARIS"'::jsonb, 'RN 19/2019', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'reg.general_conditions_act', '{"act":"RN 19/2019","date":"2019-03-27","compiledWith":["RN 20/2019","RN 38/2023","RN 40/2024","RN 55/2026"]}'::jsonb, 'RN 19/2019 compilada', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'sac.deadline_counting_rule', '"CALENDAR"'::jsonb, 'RN 19/2019', 'art. 171 (redação da RN 38/2023)', 'CALENDAR_DAYS', 'CONFIRMED', 'Dias corridos, salvo previsão expressa de dias úteis.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'meter.average_months', '6'::jsonb, 'RN 19/2019', 'art. 83', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'meter.max_estimated_cycles', '3'::jsonb, 'RN 19/2019', 'art. 83', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'meter.after_cap_rule', '"BILL_MINIMUM"'::jsonb, 'RN 19/2019', 'art. 83, §§1º, 2º, 4º e 5º', 'NOT_APPLICABLE', 'CONFIRMED', 'Depois do teto, fatura-se pelo consumo mínimo sem compensação futura; se o impedimento é do usuário, mantém-se a média até corrigir.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'meter.reading_cycle_days', '{"minDays":27,"maxDays":33,"exceptionalMinDays":15,"exceptionalMaxDays":47}'::jsonb, 'RN 19/2019', 'art. 84', 'CALENDAR_DAYS', 'CONFIRMED', 'Excepcional de 15 a 47 dias com aviso de 30 dias; faturamento inicial entre 15 e 47 dias.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'bill.due_date_options', '{"minCount":6}'::jsonb, 'RN 19/2019', 'art. 84, §4º', 'NOT_APPLICABLE', 'CONFIRMED', 'Requisito do catálogo, NÃO valor de versão: a norma confirma o mínimo de seis opções de vencimento ao usuário; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'bill.min_days_delivery_to_due', '{"default":5,"PUBLIC":10}'::jsonb, 'RN 19/2019', 'art. 98, §1º', 'BUSINESS_DAYS', 'CONFIRMED', 'Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com "default"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 98, §1º): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'fee.fine_pct', '2.00'::jsonb, 'RN 19/2019', 'art. 108', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'fee.interest_mode', '"DAILY_RATE_WITH_CAP"'::jsonb, 'RN 19/2019', 'art. 108', 'NOT_APPLICABLE', 'CONFIRMED', 'Juros simples, sem capitalização (trava do produto, P0.20).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'fee.interest_daily_pct', '0.033'::jsonb, 'RN 19/2019', 'art. 108', 'NOT_APPLICABLE', 'CONFIRMED', 'Teto: até 0,033% ao dia; o prestador pode adotar menos.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'fee.correction_index', '"IPCA"'::jsonb, 'RN 19/2019', 'art. 108', 'NOT_APPLICABLE', 'CONFIRMED', 'IPCA (IBGE) ou outro índice previsto na legislação vigente.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: só amplia (P0.20, RAS-16).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'cut.min_notice_days', '30'::jsonb, 'RN 19/2019', 'art. 153, §§1º e 5º', 'CALENDAR_DAYS', 'CONFIRMED', 'Aviso por escrito, discriminando as faturas.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'cut.max_debt_age_days', '120'::jsonb, 'RN 19/2019', 'art. 153, §2º', 'CALENDAR_DAYS', 'CONFIRMED', 'Vedado cortar por tarifas vencidas há mais de 120 dias (só a ARIS tem este teto em SC).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado: vedado iniciar corte em sexta, sábado, domingo, feriado e véspera. A regra local (art. 158) é divergência, não valor.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'rec.sla_hours', '{"normalHours":48,"urgentHours":4,"urgentOptional":true,"undueHours":4}'::jsonb, 'RN 19/2019', 'arts. 160, 161 e 25, III', 'NOT_APPLICABLE', 'CONFIRMED', 'Urgência facultativa: se adotada, vale para qualquer usuário, com valores e prazos informados.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'rec.undue_cut_compensation', '"RECONNECTION_4H_NO_CHARGE"'::jsonb, 'RN 19/2019', 'arts. 25, III, 153, §7º e 107, §6º', 'NOT_APPLICABLE', 'CONFIRMED', 'Corte indevido: religação em 4 h, sem ônus (não há crédito em dinheiro na ARIS).'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'sac.response_days', '{"days":15,"unit":"CALENDAR_DAYS"}'::jsonb, 'RN 19/2019', 'art. 23 e §1º', 'CALENDAR_DAYS', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARIS_RN19_2019', 'sac.priority_min_age_years', NULL, 'RN 19/2019', 'art. 24', 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'O art. 24 prevê atendimento prioritário, mas os trechos lidos no mapa não fixam a idade; sem valor de fábrica.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'leak.max_discount_pct_water', '70'::jsonb, 'RN 19/2019', 'art. 89, §§1º a 8º', 'NOT_APPLICABLE', 'CONFIRMED', 'Desconto de até 70% do volume acima da média; reclamação em até 45 dias da leitura. Consumo pela P3.28.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'reg.ana_nr_adoption', '{"NR1":{"status":"ADOPTED","acts":["RN 49/2025"],"catalogCode":"ana-nr-1-res-79-2021"},"NR3":{"status":"ADOPTED","acts":["RN 52/2025"],"catalogCode":"ana-nr-3-res-161-2023"},"NR4":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-4-res-177-2024"},"NR5":{"status":"ADOPTED","acts":["RN 44/2025"],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"PARTIAL","acts":["RN 50/2025"],"catalogCode":"ana-nr-6-res-183-2024"},"NR7":{"status":"ADOPTED","acts":["RN 46/2025"],"catalogCode":"ana-nr-7-res-187-2024"},"NR8":{"status":"ADOPTED","acts":["RN 36/2022","RN 43/2025","RN 54/2026","RN 42/2025","RN 47/2025"],"catalogCode":"ana-nr-8-res-192-2024"},"NR9":{"status":"ADOPTED","acts":["RN 57/2026"],"catalogCode":"ana-nr-9-res-211-2024"},"NR10":{"status":"PARTIAL","acts":["RN 51/2025"],"catalogCode":"ana-nr-10-res-228-2024"},"NR11":{"status":"PARTIAL","acts":["RN 19/2019","RN 45/2025","RN 47/2025","RN 55/2026"],"catalogCode":"ana-nr-11-res-230-2024"},"NR12":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-12-res-245-2025"},"NR13":{"status":"PARTIAL","acts":["RN 56/2026"],"catalogCode":"ana-nr-13-res-271-2025"},"NR14":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-14-res-276-2025"},"NR15":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-15-res-275-2025"},"NR16":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-16-res-302-2026"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'ARIS_RN19_2019', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'ARIS_RN19_2019', 'CUT_FRIDAY_NOON', 'Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados', 'RN 19/2019, art. 158', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'A regra local não menciona sábado, domingo e o próprio feriado; vale o piso federal integral.')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'ARESC_RES046_2016', 'ARESC', 'ARESC - Agência de Regulação de Serviços Públicos de Santa Catarina', 'Res. 046/2016 consolidada', '2016-01-19'::date, NULL::date, 'Tarifa social da ARESC: Res. 313/2024 (provisória) e Res. 348/2025, anteriores à NR 13; por isso a NR 13 consta sem ato de adoção. Prazos rurais de ligação (4 e 6 dias úteis, arts. 30 e 31) não têm chave nos grupos desta carga e ficam registrados nesta nota.')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'ARESC_RES046_2016', 'reg.regulator', '"ARESC"'::jsonb, 'Res. 046/2016', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'reg.general_conditions_act', '{"act":"Res. 046/2016","date":"2016-01-19","compiledWith":["Res. 139/2019","Res. 259/2023"]}'::jsonb, 'Res. 046/2016 consolidada', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'sac.deadline_counting_rule', '"BUSINESS"'::jsonb, 'Res. 046/2016', 'art. 153', 'BUSINESS_DAYS', 'CONFIRMED', 'Dias úteis: exclui o dia do início, inclui o do vencimento; começa e termina em dia útil.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'meter.average_months', '6'::jsonb, 'Res. 046/2016', 'art. 90', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'meter.max_estimated_cycles', '3'::jsonb, 'Res. 046/2016', 'art. 90', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'meter.after_cap_rule', '"SETTLE_BALANCE_IN_3_INSTALLMENTS"'::jsonb, 'Res. 046/2016', 'art. 90, §4º', 'NOT_APPLICABLE', 'CONFIRMED', 'Depois do teto, o saldo positivo é parcelado em 3 vezes pela faixa tarifária anterior; se o impedimento é do usuário, estimativa sem compensação (§5º).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'meter.reading_cycle_days', '{"minDays":27,"maxDays":33,"exceptionalMinDays":15,"exceptionalMaxDays":47}'::jsonb, 'Res. 046/2016', 'art. 91', 'CALENDAR_DAYS', 'CONFIRMED', 'Faixa em dias corridos de duração do ciclo (27 a 33): não é prazo contado pela regra do art. 153, por isso a base é CALENDAR_DAYS (correção pós-revisão).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'bill.due_date_options', '{"minCount":6}'::jsonb, 'Res. 046/2016', 'art. 103', 'NOT_APPLICABLE', 'CONFIRMED', 'Requisito do catálogo, NÃO valor de versão: mínimo de seis datas de vencimento confirmado; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'bill.min_days_delivery_to_due', '{"default":5,"PUBLIC":10}'::jsonb, 'Res. 046/2016', 'art. 100, §1º', 'BUSINESS_DAYS', 'CONFIRMED', 'Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com "default"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 100, §1º): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'fee.fine_pct', '2.00'::jsonb, 'Res. 046/2016', 'art. 104', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'fee.interest_mode', '"DAILY_RATE_WITH_CAP"'::jsonb, 'Res. 046/2016', 'art. 104', 'NOT_APPLICABLE', 'CONFIRMED', 'Juros simples, sem capitalização (trava do produto, P0.20).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'fee.interest_daily_pct', '0.033'::jsonb, 'Res. 046/2016', 'art. 104', 'NOT_APPLICABLE', 'CONFIRMED', 'Teto: até 0,033% ao dia.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'fee.correction_index', '"IPCA"'::jsonb, 'Res. 046/2016', 'art. 104', 'NOT_APPLICABLE', 'CONFIRMED', 'IPCA/IBGE ou outro índice previsto em lei.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: só amplia (P0.20, RAS-16).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'cut.min_notice_days', '30'::jsonb, 'Res. 046/2016', 'art. 79, §§1º, 2º e 5º', 'BUSINESS_DAYS', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'cut.max_debt_age_days', NULL, 'Res. 046/2016', 'art. 79', 'NOT_APPLICABLE', 'CONFIRMED_ABSENT', 'Sem teto de idade da dívida para cortar no texto lido (ausência confirmada, anexo B §7, item 5).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado. A Lei SC 16.673/2015, art. 21, p.u., é ainda mais protetiva e consta da divergência.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'rec.sla_hours', '{"normalHours":48,"urgentHours":4,"urgentOptional":true,"undueHours":4}'::jsonb, 'Res. 046/2016', 'arts. 87, 88 e 79, §§6º e 7º', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'rec.undue_cut_compensation', '"GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION"'::jsonb, 'Res. 046/2016', 'art. 79, §§6º e 7º', 'NOT_APPLICABLE', 'CONFIRMED', 'Crédito do maior entre 2x a religação de urgência e 20% da primeira fatura após a religação.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'sac.response_days', '{"phoneBusinessDays":10,"writtenDays":30}'::jsonb, 'Res. 046/2016', 'arts. 128, §§4º, 6º e 7º, e 129', 'NOT_DEFINED', 'CONFIRMED', 'Bases MISTAS dentro do objeto, nomeadas nos próprios campos: resposta conclusiva em 10 dias ÚTEIS no canal telefônico (phoneBusinessDays) e resposta escrita em 30 dias (writtenDays) — a fonte não marca os 30 dias da escrita como úteis, por isso a base única do valor fica NOT_DEFINED em vez de BUSINESS_DAYS (correção pós-revisão).'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'sac.priority_min_age_years', '65'::jsonb, 'Res. 046/2016', 'art. 127, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'ARESC_RES046_2016', 'leak.max_discount_pct_water', '70'::jsonb, 'Res. 046/2016', 'art. 97', 'NOT_APPLICABLE', 'CONFIRMED', 'Até 70%; 2 faturas em 12 meses; pedido em 30 dias do vencimento. Consumo pela P3.28.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'reg.ana_nr_adoption', '{"NR3":{"status":"ADOPTED","acts":["Res. 433/2026"],"catalogCode":"ana-nr-3-res-161-2023"},"NR5":{"status":"ADOPTED","acts":["Res. 432/2026"],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-6-res-183-2024"},"NR8":{"status":"ADOPTED","acts":["Res. 430/2026","Res. 431/2026"],"catalogCode":"ana-nr-8-res-192-2024"},"NR9":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-9-res-211-2024"},"NR10":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-10-res-228-2024"},"NR11":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-11-res-230-2024"},"NR13":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-13-res-271-2025"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'ARESC_RES046_2016', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'ARESC_RES046_2016', 'CUT_FRIDAY_NOON', 'Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados', 'Res. 046/2016, art. 85', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'A Lei SC 16.673/2015, art. 21, p.u., veda o corte de sexta a domingo, véspera e feriado: camada estadual mais protetiva, registrada para a P3.4 (RAS-12).')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'AGIR_RN001_2013', 'AGIR', 'AGIR - Agência Intermunicipal de Regulação de Serviços Públicos', 'RN 001/2013', '2013-08-30'::date, NULL::date, 'A AGIR não adotou a NR 11 nem a NR 13; a RN 001/2013 segue como condições gerais (mapa, anexo B §5).')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'AGIR_RN001_2013', 'reg.regulator', '"AGIR"'::jsonb, 'RN 001/2013', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGIR_RN001_2013', 'reg.general_conditions_act', '{"act":"RN 001/2013","date":"2013-08-30","compiledWith":[]}'::jsonb, 'RN 001/2013', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Sem consolidação e sem alteração localizadas no mapa.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'sac.deadline_counting_rule', NULL, 'RN 001/2013', NULL, 'NOT_DEFINED', 'REQUIRES_TENANT_VALUE', 'A RN 001/2013 não define corridos ou úteis; os prazos da ouvidoria contam em dias úteis pelo CPC, art. 219 (RN 012/2021). O tenant declara a contagem na ativação.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'meter.average_months', '6'::jsonb, 'RN 001/2013', 'art. 102', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGIR_RN001_2013', 'meter.max_estimated_cycles', '3'::jsonb, 'RN 001/2013', 'art. 102', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGIR_RN001_2013', 'meter.after_cap_rule', '"BILL_MINIMUM"'::jsonb, 'RN 001/2013', 'art. 102, §§1º, 2º e 4º', 'NOT_APPLICABLE', 'CONFIRMED', 'Depois do teto, consumo mínimo sem compensação futura.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'meter.reading_cycle_days', '{"minDays":27,"maxDays":33,"exceptionalMinDays":15,"exceptionalMaxDays":47}'::jsonb, 'RN 001/2013', 'art. 103', 'NOT_DEFINED', 'CONFIRMED', 'Excepcional de 15 a 47 dias com aviso por escrito de um ciclo.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'bill.due_date_options', '{"minCount":6}'::jsonb, 'RN 001/2013', 'art. 128', 'NOT_APPLICABLE', 'CONFIRMED', 'Requisito do catálogo, NÃO valor de versão: mínimo de seis datas de vencimento confirmado; os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS).'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'bill.min_days_delivery_to_due', '{"default":5,"PUBLIC":10}'::jsonb, 'RN 001/2013', 'art. 118, p.u.', 'BUSINESS_DAYS', 'CONFIRMED', 'Formato do consumidor da P2.5 (BillingParameters): mapa por categoria com "default"; PUBLIC = categoria Pública. Em desligamento a pedido a antecedência é de 1 dia útil (mesmo art. 118, p.u.): não é categoria de consumo do billing e por isso não entra no mapa. Unidade: dias úteis (deadline_basis).'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'fee.fine_pct', '2.00'::jsonb, 'RN 001/2013', 'art. 129', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGIR_RN001_2013', 'fee.interest_mode', '"DAILY_RATE_WITH_CAP"'::jsonb, 'RN 001/2013', 'art. 129', 'NOT_APPLICABLE', 'CONFIRMED', 'Juros simples, sem capitalização (trava do produto, P0.20).'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'fee.interest_daily_pct', '0.033'::jsonb, 'RN 001/2013', 'art. 129', 'NOT_APPLICABLE', 'CONFIRMED', 'Teto: até 0,033% por dia.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'fee.correction_index', '"IPCA"'::jsonb, 'RN 001/2013', 'art. 129', 'NOT_APPLICABLE', 'CONFIRMED', 'IPCA/IBGE ou outro índice previsto em lei.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: só amplia (P0.20, RAS-16).'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'cut.min_notice_days', '30'::jsonb, 'RN 001/2013', 'art. 143, §§1º, 2º e 4º', 'NOT_DEFINED', 'CONFIRMED', 'Com discriminação das faturas; vedado corte por impedimento de leitura não notificado.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'cut.max_debt_age_days', NULL, 'RN 001/2013', 'art. 47, §1º, IV', 'NOT_APPLICABLE', 'CONFIRMED_ABSENT', 'Sem teto de idade para cortar: os 120 dias do art. 47 só condicionam ligação e religação (ausência confirmada, anexo B §7, item 5).'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado: a regra local (art. 148) é divergência, não valor.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'rec.sla_hours', '{"normalHours":48,"urgentHours":4,"urgentOptional":true,"undueHours":4}'::jsonb, 'RN 001/2013', 'arts. 151, 152 e 143, §§6º e 7º', 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGIR_RN001_2013', 'rec.undue_cut_compensation', '"GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE"'::jsonb, 'RN 001/2013', 'art. 143, §§6º e 7º', 'NOT_APPLICABLE', 'CONFIRMED', 'Crédito do maior entre 2x a religação de urgência e 20% da última fatura.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'sac.response_days', '{"days":15,"unit":"NOT_DEFINED"}'::jsonb, 'RN 001/2013', 'art. 27, §2º, e art. 28', 'NOT_DEFINED', 'CONFIRMED', '15 dias, sem unidade definida na RN 001/2013.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'sac.priority_min_age_years', '60'::jsonb, 'Mapa de ERIs, §6, item 4', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Idade confirmada no quadro-síntese do mapa (§6 e anexo B §7, item 13); o artigo da RN 001/2013 não foi discriminado nos trechos lidos.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'leak.max_discount_pct_water', '70'::jsonb, 'RN 001/2013', 'art. 107', 'NOT_APPLICABLE', 'CONFIRMED', 'Até 70% do volume acima da média; 2 faturas sequenciais em 12 meses; pedido em 30 dias do vencimento. Consumo pela P3.28.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'reg.ana_nr_adoption', '{"NR1":{"status":"ADOPTED","acts":["RN 018/2025"],"catalogCode":"ana-nr-1-res-79-2021"},"NR3":{"status":"ADOPTED","acts":["RN 019/2025"],"catalogCode":"ana-nr-3-res-161-2023"},"NR5":{"status":"ADOPTED","acts":["RN 020/2025"],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"ADOPTED","acts":["RN 021/2025"],"catalogCode":"ana-nr-6-res-183-2024"},"NR7":{"status":"ADOPTED","acts":["RN 022/2025"],"catalogCode":"ana-nr-7-res-187-2024"},"NR8":{"status":"ADOPTED","acts":["RN 023/2025","RN 024/2025"],"catalogCode":"ana-nr-8-res-192-2024"},"NR9":{"status":"ADOPTED","acts":["RN 025/2026"],"catalogCode":"ana-nr-9-res-211-2024"},"NR10":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-10-res-228-2024"},"NR11":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-11-res-230-2024"},"NR13":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-13-res-271-2025"},"NR15":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-15-res-275-2025"},"NR16":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-16-res-302-2026"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'AGIR_RN001_2013', 'CUT_FRIDAY_NOON', 'Interrupção permitida até as 12 h de sexta-feira e na véspera de feriados nacionais, estaduais e municipais', 'RN 001/2013, art. 148', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'Vale o piso federal integral.'),
    ('${tenant_id}', 'AGIR_RN001_2013', 'SOCIAL_BENEFIT_LOSS_AFTER_TWO_CUTS', 'Mais de 2 cortes no ano fiscal retiram a categoria social', 'RN 001/2013, art. 127, §7º', 'Lei 14.898/2024', 'RULE_REJECTED', 'Recusada na carga: nenhum valor a substitui; o benefício não se perde por corte. O art. 127, §4º (religação sem taxa após purgação da mora) é compatível e segue valendo.')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'AGR_TUBARAO', 'AGR-Tubarão - Agência Reguladora de Saneamento de Tubarão', 'Res. 007/2013 e Res. 040/2024', NULL::date, NULL::date, 'Data exata da Res. 007/2013 não localizada no mapa: a vigência do perfil é definida na ativação pelo tenant. NR 3 em Tomada de Subsídios 001/2026, sem ato de adoção (mapa, anexo B §3).')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'reg.regulator', '"AGR_TUBARAO"'::jsonb, 'Res. 007/2013', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'reg.general_conditions_act', '{"act":"Res. 007/2013","compiledWith":["Res. 040/2024"],"note":"condicoes gerais comerciais nao localizadas"}'::jsonb, 'Res. 007/2013 e Res. 040/2024', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Res. 007/2013: serviço adequado e indicadores. Res. 040/2024: enquadramento de economias. Tarifa social: Res. 043/2025. As condições comerciais vivem no contrato de concessão 038/2012 e no regulamento da concessionária, não localizados (mapa, pendência 25).'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'sac.deadline_counting_rule', NULL, NULL, NULL, 'NOT_DEFINED', 'REQUIRES_TENANT_VALUE', 'Contagem não confirmada: sem condições gerais lidas.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'meter.average_months', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Média sem leitura nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'meter.max_estimated_cycles', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Teto de ciclos nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'meter.after_cap_rule', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Regra posterior à média nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'meter.reading_cycle_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Ciclo de leitura nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'bill.due_date_options', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Datas de vencimento nas condições não localizadas; exige valor do tenant (para concessionária, o piso federal de 6 segue travado pela P0.20).'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'bill.min_days_delivery_to_due', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Antecedência nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'fee.fine_pct', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Multa nas condições não localizadas; exige valor do tenant (o padrão de fábrica do produto é 2%, CDC art. 52, §1º, via catálogo da P0.20).'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'fee.interest_mode', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Modo de juros nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'fee.interest_daily_pct', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Juros nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'fee.correction_index', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Correção nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: vale independentemente do texto da ERI (P0.20, RAS-16).'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'cut.min_notice_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Aviso de corte nas condições não localizadas; exige valor do tenant (o piso federal de 30 dias segue travado, Lei 11.445/2007, art. 40, §2º).'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'cut.max_debt_age_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade máxima da dívida nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado, válido sem texto local; nenhuma regra local foi localizada, logo nenhuma divergência é registrada.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'rec.sla_hours', '{"reconnectionHours":24}'::jsonb, 'Res. 007/2013', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Reparo e religação em 24 h confirmados na Res. 007/2013 (mapa, apêndice B); a discriminação normal/urgência/indevida não consta dos trechos lidos. Ligação em 5 dias úteis, também da Res. 007/2013, não tem chave nos grupos desta carga.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'rec.undue_cut_compensation', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Compensação por corte indevido nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'sac.response_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Prazos de resposta nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'sac.priority_min_age_years', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade do atendimento prioritário nas condições não localizadas; exige valor do tenant.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'leak.max_discount_pct_water', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Vazamento nas condições não localizadas; exige valor do tenant. Consumo pela P3.28.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'reg.ana_nr_adoption', '{"NR7":{"status":"ADOPTED","acts":["Res. 044/2025"],"catalogCode":"ana-nr-7-res-187-2024"},"NR8":{"status":"ADOPTED","acts":["Res. 045/2025","Res. 048/2026"],"catalogCode":"ana-nr-8-res-192-2024"},"NR9":{"status":"ADOPTED","acts":["Res. 049/2026"],"catalogCode":"ana-nr-9-res-211-2024"},"NR3":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-3-res-161-2023"},"NR5":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-6-res-183-2024"},"NR10":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-10-res-228-2024"},"NR11":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-11-res-230-2024"},"NR13":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-13-res-271-2025"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'AGR_TUBARAO_RES007_2013', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'CISAM_MO', 'CISAM Meio-Oeste (CISAM-REG/CREFISBA)', 'RN 01/2017', '2017-12-21'::date, '2027-05-19'::date, 'Sem mapa de adoção neste perfil: os atos de adoção das NR são de 2025/2026 e constam do perfil sucessor (RN 05/2026). Ligação em 10 ou 15 dias úteis, confirmada no mapa, não tem chave nos grupos desta carga.')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'reg.regulator', '"CISAM_MO"'::jsonb, 'RN 01/2017', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'reg.general_conditions_act', '{"act":"RN 01/2017","compiledWith":[]}'::jsonb, 'RN CISAM-REG 01/2017', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Em vigor desde 21/12/2017 até 19/05/2027, quando a RN 05/2026 a revoga.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'sac.deadline_counting_rule', NULL, 'RN 01/2017', NULL, 'NOT_DEFINED', 'REQUIRES_TENANT_VALUE', 'O mapa confirma resposta em 15 dias úteis, mas não uma regra geral de contagem da RN 01/2017; o tenant declara na ativação.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'meter.average_months', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Média não consta dos trechos lidos da RN 01/2017 no mapa; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'meter.max_estimated_cycles', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Teto de ciclos não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'meter.after_cap_rule', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Regra posterior não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'meter.reading_cycle_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Ciclo de leitura não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'bill.due_date_options', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Datas de vencimento não constam dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'bill.min_days_delivery_to_due', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Antecedência não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'fee.fine_pct', NULL, 'RN 01/2017', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Juros, multa e correção remetidos à legislação municipal, sem percentuais (confirmada a remissão, não o valor); exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'fee.interest_mode', NULL, 'RN 01/2017', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação municipal; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'fee.interest_daily_pct', NULL, 'RN 01/2017', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação municipal; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'fee.correction_index', NULL, 'RN 01/2017', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação municipal; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: só amplia (P0.20, RAS-16).'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'cut.min_notice_days', '30'::jsonb, 'RN 01/2017', 'art. 104', 'NOT_DEFINED', 'CONFIRMED', NULL),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'cut.max_debt_age_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade máxima não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado: a regra local (art. 107) é divergência, não valor.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'rec.sla_hours', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Prazos de religação da RN 01/2017 não discriminados no mapa; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'rec.undue_cut_compensation', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Compensação não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'sac.response_days', '{"days":15,"unit":"BUSINESS_DAYS"}'::jsonb, 'RN 01/2017', NULL, 'BUSINESS_DAYS', 'CONFIRMED', 'Resposta em 15 dias úteis, confirmada no mapa (apêndice B); artigo não discriminado nos trechos lidos.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'sac.priority_min_age_years', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade do atendimento prioritário não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'leak.max_discount_pct_water', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Vazamento não consta dos trechos lidos; exige valor do tenant. Consumo pela P3.28.'),
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN01_2017', 'CUT_FRIDAY_NOON', 'Corte vedado apenas após as 12 horas de sexta e na véspera de feriado', 'RN 01/2017, art. 107', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'Vale o piso federal integral.')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'CISAM_MO', 'CISAM Meio-Oeste (CISAM-REG/CREFISBA)', 'RN 05/2026', '2027-05-20'::date, NULL::date, 'Ligação em 10 dias úteis (urbana) ou 15 (rural) na RN 05/2026, confirmada no mapa, sem chave nos grupos desta carga.')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'reg.regulator', '"CISAM_MO"'::jsonb, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'reg.general_conditions_act', '{"act":"RN 05/2026","compiledWith":[]}'::jsonb, 'RN 05/2026', 'art. 163', 'NOT_APPLICABLE', 'CONFIRMED', 'Publicada em 05/10/2026; em vigor a partir de 20/05/2027, revogando a RN 01/2017 (vigência do art. 163, conforme o acceptance da P2.31 e o mapa). Tarifa social: RN 04/2026 (50% até 15 m³, renda até 1/2 salário mínimo com CadÚnico/BPC).'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'sac.deadline_counting_rule', '"BUSINESS"'::jsonb, 'RN 05/2026', NULL, 'BUSINESS_DAYS', 'CONFIRMED', 'Prazos em dias úteis na RN 05/2026 (mapa, apêndice B); artigo não discriminado nos trechos lidos.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'meter.average_months', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Média não consta dos trechos lidos da RN 05/2026 no mapa; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'meter.max_estimated_cycles', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Teto de ciclos não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'meter.after_cap_rule', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Regra posterior não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'meter.reading_cycle_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Ciclo de leitura não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'bill.due_date_options', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Datas de vencimento não constam dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'bill.min_days_delivery_to_due', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Antecedência não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'fee.fine_pct', NULL, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Juros, multa e atualização remetidos à legislação, sem percentuais confirmados; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'fee.interest_mode', NULL, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'fee.interest_daily_pct', NULL, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'fee.correction_index', NULL, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Remetido à legislação; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: só amplia (P0.20, RAS-16).'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'cut.min_notice_days', '30'::jsonb, 'RN 05/2026', NULL, 'BUSINESS_DAYS', 'CONFIRMED', 'Aviso de suspensão de 30 dias confirmado no mapa; artigo não discriminado nos trechos lidos.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'cut.max_debt_age_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade máxima não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'A RN 05/2026 já veda sábado, domingo, feriado e véspera; a permissão implícita na manhã de sexta segue divergente do piso federal.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'rec.sla_hours', '{"confirmedHours":[12,24,120]}'::jsonb, 'RN 05/2026', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Religação de 12/24/120 horas confirmada no mapa; a correspondência com normal/urgência/indevida não é discriminada nos trechos lidos, e o tenant a declara na ativação.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'rec.undue_cut_compensation', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Compensação não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'sac.response_days', NULL, 'RN 05/2026', NULL, 'BUSINESS_DAYS', 'REQUIRES_TENANT_VALUE', 'A RN 05/2026 conta prazos em dias úteis, mas o prazo de resposta não é discriminado no mapa; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'sac.priority_min_age_years', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Idade do atendimento prioritário não consta dos trechos lidos; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'leak.max_discount_pct_water', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Vazamento não consta dos trechos lidos; exige valor do tenant. Consumo pela P3.28.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'reg.ana_nr_adoption', '{"NR3":{"status":"ADOPTED","acts":["RN 01/2025"],"catalogCode":"ana-nr-3-res-161-2023"},"NR5":{"status":"ADOPTED","acts":["RN 02/2025"],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"ADOPTED","acts":["RN 03/2025"],"catalogCode":"ana-nr-6-res-183-2024"},"NR7":{"status":"ADOPTED","acts":["RN 04/2025"],"catalogCode":"ana-nr-7-res-187-2024"},"NR8":{"status":"ADOPTED","acts":["RN 05/2025","RN 06/2025"],"catalogCode":"ana-nr-8-res-192-2024"},"NR9":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-9-res-211-2024"},"NR10":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-10-res-228-2024"},"NR11":{"status":"ADOPTED","acts":["RN 05/2026"],"catalogCode":"ana-nr-11-res-230-2024"},"NR13":{"status":"ADOPTED","acts":["RN 04/2026"],"catalogCode":"ana-nr-13-res-271-2025"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'CISAM_MO_RN05_2026', 'CUT_FRIDAY_MORNING', 'Corte vedado aos sábados, domingos, feriados e vésperas, e na sexta apenas após as 12 horas', 'RN 05/2026', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'A manhã de sexta segue aberta pela regra local e fechada pela lei federal: vale o piso federal.')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_catalog
    (tenant_id, profile_key, agency_code, agency_name, general_conditions_act, valid_from, valid_to, notes)
VALUES
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'CISAM_SUL', 'CISAM-SUL (CREFISBA)', 'RN 03/2017', '2017-10-17'::date, NULL::date, 'Adoção das NR: o CISAM-SUL consta como atendente das NR 3, 5 e 8 na lista positiva da ANA de 2025, sem ato individual de adoção localizado (mapa, anexo B §3 e §5): por isso o status é o da lista, não ADOPTED.')
ON CONFLICT (tenant_id, profile_key) DO UPDATE SET
    agency_code = EXCLUDED.agency_code, agency_name = EXCLUDED.agency_name,
    general_conditions_act = EXCLUDED.general_conditions_act,
    valid_from = EXCLUDED.valid_from, valid_to = EXCLUDED.valid_to,
    notes = EXCLUDED.notes, updated_at = NOW();

INSERT INTO regulatory_profile_values
    (tenant_id, profile_key, definition_key, value, source_act, source_article, deadline_basis, confirmation, note)
VALUES
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'reg.regulator', '"CISAM_SUL"'::jsonb, 'RN 03/2017', NULL, 'NOT_APPLICABLE', 'CONFIRMED', NULL),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'reg.general_conditions_act', '{"act":"RN 03/2017","date":"2017-10-17","compiledWith":[]}'::jsonb, 'RN 03/2017', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Texto digitalizado, com reconhecimento imperfeito (mapa, pendência 24): os valores por artigo não foram confirmados nesta carga.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'sac.deadline_counting_rule', NULL, 'RN 03/2017', NULL, 'NOT_DEFINED', 'REQUIRES_TENANT_VALUE', 'Contagem não confirmada no texto digitalizado; a RN 03/2016 (reajuste) também não define. Exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'meter.average_months', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'meter.max_estimated_cycles', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'meter.after_cap_rule', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'meter.reading_cycle_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'bill.due_date_options', '{"minCount":4}'::jsonb, 'Mapa de ERIs, §6, item 4', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Requisito do catálogo, NÃO valor de versão: mínimo de quatro datas de vencimento (diferença do CISAM-SUL no quadro de SC); artigo não localizado no texto digitalizado. Os dias concretos (a lista LIST_INT que o billing lê) são escolha do tenant e completam esta chave na ativação (ver RegulatoryLoadGuard.TENANT_COMPLETED_KEYS). Para concessionária, o piso federal de 6 segue travado pela P0.20.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'bill.min_days_delivery_to_due', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'fee.fine_pct', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; no quadro do mapa, juros e multa do CISAM-SUL remetem à legislação municipal e ao contrato. Exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'fee.interest_mode', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'fee.interest_daily_pct', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'fee.correction_index', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'ret.billing_history_months', '120'::jsonb, 'NR 11 (Res. ANA 230/2024)', 'art. 22', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal, classe c: vale independentemente do texto da ERI (P0.20, RAS-16).'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'cut.min_notice_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant (o piso federal de 30 dias segue travado, Lei 11.445/2007, art. 40, §2º).'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'cut.max_debt_age_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'cut.forbidden_window', '{"rule":"FEDERAL_FLOOR_14_015"}'::jsonb, 'Lei 14.015/2020', 'art. 6º, §4º, da Lei 8.987/1995', 'NOT_APPLICABLE', 'CONFIRMED', 'Piso federal travado. O quadro do mapa (§6) registra para o CISAM-SUL a mesma regra local das demais ERIs de SC (até 12 h de sexta), como divergência.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'rec.sla_hours', NULL, 'Mapa de ERIs, §4.1', NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'O mapa registra religação na faixa de 24 h a 120 h para o CISAM-SUL, sem discriminar normal/urgência/indevida no texto digitalizado; a discriminação exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'rec.undue_cut_compensation', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'sac.response_days', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'sac.priority_min_age_years', '60'::jsonb, 'Mapa de ERIs, §6, item 4', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atendimento prioritário a partir de 60 anos (quadro de SC do mapa, §6 e anexo B §7, item 13); artigo não localizado no texto digitalizado.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'leak.max_discount_pct_water', NULL, NULL, NULL, 'NOT_APPLICABLE', 'REQUIRES_TENANT_VALUE', 'Texto só digitalizado; exige valor do tenant. Consumo pela P3.28.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'reg.ana_nr_adoption', '{"NR3":{"status":"ATENDIDA_LISTA_ANA_2025","acts":[],"catalogCode":"ana-nr-3-res-161-2023"},"NR5":{"status":"ATENDIDA_LISTA_ANA_2025","acts":[],"catalogCode":"ana-nr-5-res-178-2024"},"NR6":{"status":"DESOBRIGADA_LISTA_ANA_2025","acts":[],"catalogCode":"ana-nr-6-res-183-2024"},"NR8":{"status":"ATENDIDA_LISTA_ANA_2025","acts":[],"catalogCode":"ana-nr-8-res-192-2024"},"NR11":{"status":"NO_ACT_LOCATED","acts":[],"catalogCode":"ana-nr-11-res-230-2024"}}'::jsonb, 'Mapa de ERIs (docs/normas/partes/04), §5 e anexos A/B/F', NULL, 'NOT_APPLICABLE', 'CONFIRMED', 'Atos de adoção das NR da ANA; cada NR é ligada ao catálogo de normas da P1.34 pelo cert_standard.code em catalogCode (DAD-28), validado no aceite da ativação.'),
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'reg.normative_facts', '[{"code":"ANA_NR11_VERIFICATION_FROM","date":"2027-05-20","source":"Res. ANA 230/2024 (NR 11), art. 74, §3º"}]'::jsonb, 'Res. ANA 230/2024 (NR 11)', 'art. 74, §3º', 'NOT_APPLICABLE', 'CONFIRMED', NULL)
ON CONFLICT (tenant_id, profile_key, definition_key) DO UPDATE SET
    value = EXCLUDED.value, source_act = EXCLUDED.source_act,
    source_article = EXCLUDED.source_article, deadline_basis = EXCLUDED.deadline_basis,
    confirmation = EXCLUDED.confirmation, note = EXCLUDED.note, updated_at = NOW();

INSERT INTO regulatory_profile_divergences
    (tenant_id, profile_key, rule_code, local_rule, local_source, federal_norm, resolution, note)
VALUES
    ('${tenant_id}', 'CISAM_SUL_RN03_2017', 'CUT_FRIDAY_NOON', 'Interrupção permitida até as 12 h de sexta-feira (regra registrada no quadro do mapa para o CISAM-SUL)', 'Mapa de ERIs, §6 (camada sobre os parâmetros); RN 03/2017 digitalizada, artigo não localizado', 'Lei 14.015/2020', 'FEDERAL_FLOOR_APPLIED', 'Vale o piso federal integral.')
ON CONFLICT (tenant_id, profile_key, rule_code) DO UPDATE SET
    local_rule = EXCLUDED.local_rule, local_source = EXCLUDED.local_source,
    federal_norm = EXCLUDED.federal_norm, resolution = EXCLUDED.resolution,
    note = EXCLUDED.note, updated_at = NOW();

-- =======================================================================
-- Guarda de contagem (precedente da P1.34): falha alto se a carga divergir
-- =======================================================================

DO $$
DECLARE
    v_profiles INT;
    v_values INT;
    v_divergences INT;
BEGIN
    SELECT COUNT(*) INTO v_profiles FROM regulatory_profile_catalog
        WHERE tenant_id = '${tenant_id}'::uuid;
    SELECT COUNT(*) INTO v_values FROM regulatory_profile_values
        WHERE tenant_id = '${tenant_id}'::uuid;
    SELECT COUNT(*) INTO v_divergences FROM regulatory_profile_divergences
        WHERE tenant_id = '${tenant_id}'::uuid;
    IF v_profiles <> 7 OR v_values <> 167
            OR v_divergences <> 7 THEN
        RAISE EXCEPTION 'Carga P2.31 divergente: % perfis, % valores, % divergencias (esperados 7/167/7)',
            v_profiles, v_values, v_divergences;
    END IF;
END $$;
