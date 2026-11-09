# Relatório de Cobertura — Base Fictícia de 12.000 UCs

Situação × contagem × fonte no domínio. Gerado a partir de `matriz.csv`/`manifesto.json` do gerador.

| Situação | Contagem | Descrição |
|---|---:|---|
| S01 | 1800 | Residencial ligada, com hidrometro, leitura real, conta aberta |
| S02 | 1500 | Residencial ligada, leitura real, conta paga |
| S03 | 900 | Residencial ligada, conta vencida (NORMAL vencida) com debito em aberto |
| S04 | 400 | Idem S03, com ordem de servico aberta |
| S05 | 700 | Tarifa social ativa (CadUnico), leitura real, conta aberta |
| S06 | 300 | Tarifa social ativa, conta vencida com debito |
| S07 | 800 | Comercial ligada, leitura real, conta aberta |
| S08 | 600 | Comercial ligada, leitura real, conta paga |
| S09 | 300 | Industrial ligada, leitura real, conta aberta |
| S10 | 200 | Categoria publica ligada, leitura real, conta aberta |
| S11 | 500 | Com hidrometro, consumo faturado ESTIMATED (sem leitura do mes), conta aberta |
| S12 | 600 | SEM hidrometro, faturada por estimativa (ESTIMATED), conta aberta |
| S13 | 300 | Sem hidrometro, estimativa, conta vencida com debito |
| S14 | 350 | Leitura com anormalidade de consumo alto, conta aberta, OS aberta |
| S15 | 450 | Ligacao CORTADA (supplies=false), conta vencida com debito, OS aberta |
| S16 | 300 | Ligacao SUSPENSA (nao fatura), hidrometro instalado, sem leitura e sem conta |
| S17 | 250 | Conta CANCELLED, OS fechada executada |
| S18 | 400 | Conta INSTALLMENT com parcelamento ACTIVE e cronograma; OS do acordo aberta |
| S19 | 400 | Cadastro PROVISIONAL, sem ligacao, sem hidrometro, sem conta |
| S20 | 300 | Hidrometro IN_MAINTENANCE, consumo estimado, conta aberta |
| S21 | 200 | Hidrometro WRITTEN_OFF (instalacao encerrada), consumo estimado, conta aberta |
| S22 | 250 | Tarifa social ativa, sem hidrometro, estimativa, conta aberta |
| S23 | 200 | Conta aberta com debito avulso NORMAL (sem conta vencida) |
| **TOTAL** | **12000** | soma mecânica = 12.000 |

## Fontes por dimensão

- **status_uc**: consumer_units.status — CHECK ('ACTIVE','PROVISIONAL'), V3_1 registry
- **categoria**: categories.code VARCHAR(20) livre (catálogo do tenant, V3_1) via unit_subcategories/subcategories
- **situacao_agua**: water_statuses (catálogo V3_1; flags bills/connected/supplies) — códigos = política deste seed
- **tarifa_social**: social_tariff_enrollments status='ACTIVE', basis CADUNICO, source OFFICIAL_LIST — CHECKs V4_2 billing
- **hidrometro**: meters.status CHECK V5_1 metering (INSTALLED/IN_MAINTENANCE/WRITTEN_OFF) + meter_installations; NONE = sem instalação
- **leitura**: consumption_records.consumption_type CHECK V5_1 (REAL/ESTIMATED) + meter_readings.reading_status/anomaly_id V5_1
- **conta**: bills.status CHECK V4_1 billing (NORMAL/PAID/CANCELLED/INSTALLMENT); VENCIDA = NORMAL com due_date < 2026-11-01 (não há status 'vencida' no domínio)
- **debito**: debit_receivables status='NORMAL' — V4_1 billing
- **ordem_servico**: service_orders + service_order_targets (CONSUMER_UNIT) — V9_1 core; status pela máquina DRAFT→OPEN→EXECUTED→CLOSED_EXECUTED

## Tabelas tocadas e contagens da massa (manifesto.json)

| Tabela | Linhas | Fonte (migration) |
|---|---:|---|
| addresses | 2000 | V3_1 registry (forma pós-ALTER) + Address.java |
| bill_items | 11300 | V4_1 billing |
| bills | 11300 | V4_1 billing |
| blocks | 20 | V3_1 registry |
| categories | 4 | V3_1 registry |
| commercial_sectors | 1 | V3_1 registry |
| consumer_units | 12000 | V3_1 registry (forma pós-ALTER) |
| consumption_anomalies | 1 | V5_1 metering |
| consumption_records | 11300 | V5_1 metering |
| customers | 12000 | V3_1 registry |
| debit_receivables | 2550 | V4_1 billing |
| installment_agreements | 400 | P4.1 PENDENTE (collection) |
| installment_schedule | 1200 | P4.1 PENDENTE (collection) |
| localities | 1 | V3_1 registry |
| meter_installations | 10450 | V5_1 metering |
| meter_readings | 9150 | V5_1 metering |
| meters | 10450 | V5_1 metering |
| reading_anomalies | 2 | V5_1 metering |
| reading_routes | 2 | V3_1 registry |
| service_order_close_reasons | 1 | V9_1 core (tenant) |
| service_order_targets | 1850 | V9_1 core (tenant) |
| service_orders | 1850 | V9_1 core (tenant) |
| service_types | 1 | V9_1 core (tenant) |
| sewer_statuses | 2 | V3_1 registry |
| social_tariff_end_reasons | 1 | V4_2 billing |
| social_tariff_enrollments | 1250 | V4_2 billing |
| subcategories | 4 | V3_1 registry |
| tariff_versions | 1 | V4_1 billing |
| tariffs | 1 | V4_1 billing |
| unit_customers | 12000 | V3_1 registry |
| unit_subcategories | 12000 | V3_1 registry |
| water_statuses | 4 | V3_1 registry |

## Divergências entre cópias locais (declaradas)

1. **consumer_units (V3_1 × V3_12)**: a V3_1 (ed-12/espelho, fonte nomeada) usa `matricula`/`dv`; a V3_12 (p2.23/espelho, cópia mais recente) renomeia para `registration_number`/`check_digit` e acrescenta `occupancy_status` (V3_8). A fixture e a carga seguem a **era V3_1**; na integração com um tenant totalmente migrado, basta renomear as duas colunas na carga (pendência P-BF-01).
2. **installment_agreements/installment_schedule**: não há migration versionada nos espelhos — a fonte é o DDL **PENDENTE** da P4.1 (`db/pendente/PENDENTE__dunning_installments_campaigns_prescription.sql`). O parcelamento entra na massa a partir dessa fonte, declarado como pendente de número.
3. **social_tariff_enrollments.service_order_id**: a V4_2 o cria anulável; a V4_13 (p2.5/espelho) o torna NOT NULL num tenant totalmente migrado. A carga segue a V4_2 (NULL) — na integração, os 1.250 enquadramentos precisam de OS vinculada (pendência P-BF-02).
4. **Catálogos sem semente**: categories/water_statuses/sewer_statuses/service_types/reading_anomalies nascem vazios nas migrations (política do tenant). Os códigos usados (LIGADA, CORTADA, SUSPENSA, RESIDENCIAL, …) são **política deste seed fictício**, criados no próprio tenant fictício — não são valores do sistema.
5. **billing V4_3/V4_4, V4_6, V4_14, V4_15 e metering V5_4/V5_5**: tratam de ciclos, versionamento, partições e RLS — não alteram as colunas/valores das tabelas tocadas; por isso não mudam a massa (conferidos, sem divergência de valores).
6. **addresses/consumer_units base (V1_1)**: a DDL base não está nos espelhos locais; a forma usada é a pós-V3_1 (ALTERs da V3_1 + entidades Address/ConsumerUnit de p2.23/espelho), declarada na fixture.
