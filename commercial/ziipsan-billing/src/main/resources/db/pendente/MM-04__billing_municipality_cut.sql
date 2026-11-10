-- MM-04 — Recorte nativo de faturamento/reporting por municipio (P-BF-08;
-- decisao do dono n. 45; MAPA-DE-PACOTES do Bloco Multimunicipio, MM-04).
--
-- SQL PENDENTE de numeracao Flyway (D-MM-06): nenhum numero inventado.
-- Na integracao, recebe o proximo slot livre da historia tenant do modulo
-- commercial/ziipsan-billing e e renomeado para V<numero>__*.sql.
--
-- DECISAO D-MM-03 (vinculativa, registada neste pacote — ver RELATORIO):
-- o recorte de bills/consumption_records faz-se por VIEW/join sobre a
-- derivacao canonica do MM-01, SEM coluna desnormalizada de municipio
-- em nenhuma das duas tabelas. Nenhuma tabela e tocada; nenhuma
-- migration publicada e alterada; nenhum backfill de historico existe.
--
-- OS CAMINHOS FK (transcritos do DDL vigente conferido):
--   * bills.unit_id -> consumer_units (V4_1__create_billing_schema.sql:
--     fk_bill_unit, FOREIGN KEY (tenant_id, unit_id) REFERENCES
--     consumer_units(tenant_id, id)) — caminho DIRECTO, um salto.
--   * consumption_records.unit_id -> consumer_units
--     (V5_1__create_metering_schema.sql, modulo metering:
--     fk_consumption_unit, FOREIGN KEY (tenant_id, unit_id) REFERENCES
--     consumer_units(tenant_id, id)) — caminho DIRECTO, um salto.
--   * da UC ao municipio consome-se a derivacao do MM-01 POR NOME:
--     a view consumer_unit_municipality (tenant_id, consumer_unit_id,
--     municipality_ibge), definida uma unica vez no registry
--     (MM-01__consumer_unit_municipality.sql, D-MM-02). Este ficheiro
--     NAO reimplementa a cadeia blocks/sectors/localities.
-- Nenhum caminho indirecto foi necessario: os dois sao FK compostas
-- (tenant_id, unit_id) directas para consumer_units.
--
-- LEFT JOIN, DE PROPOSITO (diferenca deliberada face a view do MM-01,
-- que e INNER porque la o assunto e a derivacao em si): aqui o assunto
-- e o RECORTE de linhas de faturamento — uma conta ou consumo cuja UC
-- nao tem municipio derivavel (UC sem quadra, cadeia do MM-01) tem de
-- CONTINUAR VISIVEL, com municipality_ibge NULL ("sem derivacao"),
-- nunca desaparecer do relatorio. Consequencia por construcao:
--   count(view) == count(tabela) e sum(view) == sum(tabela),
-- logo os totais do tenant sao preservados pela propria definicao —
-- e a prova diferencial do pacote verifica-o sobre a base 12k.
--
-- COLUNAS: b.* / cr.* + municipality_ibge. A forma de bills ja cresceu
-- por migrations aditivas depois da V4_1 (V4_6: tax_treatment,
-- audit_batch_id; V4_14: origin_reference_month, version,
-- tenant_schema na tabela particionada); enumerar colunas congelaria
-- esta view numa era do DDL e arriscaria PERDER uma coluna do recorte
-- na proxima adicao. Com *, o recorte e "a linha inteira, tal como a
-- tabela a tem, mais o seu municipio derivado" — que e o contrato.
-- (No PostgreSQL, o * de uma view expande-se as colunas existentes no
-- acto da criacao; uma coluna nova em bills exige re-executar este
-- CREATE OR REPLACE — idempotente — na mesma integracao que a criar.)
--
-- PADRAO DE VIEW do projeto: WITH (security_invoker = true), como a
-- view do MM-01 e as views de reporting da P3.22 (V15_1): a leitura
-- respeita a RLS do chamador (bills tem RLS por tenant, V4_14/V4_15).
-- bills e particionada por RANGE(reference_month) desde a V4_14:
-- uma view sobre a tabela-mae le todas as particoes — nenhum acesso
-- por particao e necessario nem desejado.
--
-- ADITIVO E IDEMPOTENTE: dois CREATE OR REPLACE VIEW; nada mais.

CREATE OR REPLACE VIEW bills_municipality_cut WITH (security_invoker = true) AS
SELECT b.*,
       m.municipality_ibge AS municipality_ibge
FROM bills b
LEFT JOIN consumer_unit_municipality m
  ON m.tenant_id = b.tenant_id AND m.consumer_unit_id = b.unit_id;

COMMENT ON VIEW bills_municipality_cut IS
    'MM-04 (D-MM-03/P-BF-08): contas (bills, V4_1) ao nivel da linha com o municipality_ibge derivado da UC via consumer_unit_municipality (MM-01). LEFT JOIN: conta sem derivacao aparece com IBGE NULL, nunca some. Recorte por view — sem coluna desnormalizada em bills.';

CREATE OR REPLACE VIEW consumption_records_municipality_cut WITH (security_invoker = true) AS
SELECT cr.*,
       m.municipality_ibge AS municipality_ibge
FROM consumption_records cr
LEFT JOIN consumer_unit_municipality m
  ON m.tenant_id = cr.tenant_id AND m.consumer_unit_id = cr.unit_id;

COMMENT ON VIEW consumption_records_municipality_cut IS
    'MM-04 (D-MM-03/P-BF-08): consumos faturados (consumption_records, metering V5_1) ao nivel da linha com o municipality_ibge derivado da UC via consumer_unit_municipality (MM-01). LEFT JOIN: consumo sem derivacao aparece com IBGE NULL, nunca some. Recorte por view — sem coluna desnormalizada em consumption_records.';
