-- ED-31 Divida ativa (nucleo) — SQL PENDENTE, SEM NUMERO Flyway.
-- Motivo: a listagem de migrations da main (HEAD 2f77582) mostrou V6_1..V6_3
-- no modulo ziipsan-collection, mas a listagem veio truncada — numero livre
-- NAO confirmado. Atribuir numero na integracao, apos leitura completa do
-- diretorio. NENHUM valor de lei do ente e semeado: da.min_filing_amount e
-- os codigos de modelo sao configuracao do tenant (as minutas nao existem).
-- da.protest_enabled nasce FALSE (PD-10) e assim permanece ate o parecer.

CREATE TABLE IF NOT EXISTS active_debt_enrollments (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL, unit_id UUID NOT NULL, customer_id UUID NOT NULL,
    book_term_number BIGINT NOT NULL, total NUMERIC(19,2) NOT NULL, enrolled_on DATE NOT NULL,
    status VARCHAR(24) NOT NULL, UNIQUE (tenant_id, book_term_number));
CREATE TABLE IF NOT EXISTS active_debt_items (
    enrollment_id UUID NOT NULL REFERENCES active_debt_enrollments(id), bill_id UUID NOT NULL,
    due_date DATE NOT NULL, amount NUMERIC(19,2) NOT NULL, last_acknowledgment DATE);
CREATE TABLE IF NOT EXISTS active_debt_cda_documents (
    enrollment_id UUID PRIMARY KEY REFERENCES active_debt_enrollments(id),
    template_code VARCHAR(80) NOT NULL, template_version VARCHAR(40) NOT NULL, document_ref VARCHAR(200) NOT NULL);
CREATE TABLE IF NOT EXISTS active_debt_installments (
    id UUID PRIMARY KEY, enrollment_id UUID NOT NULL REFERENCES active_debt_enrollments(id),
    number INT NOT NULL, due_date DATE NOT NULL, amount NUMERIC(19,2) NOT NULL);
CREATE TABLE IF NOT EXISTS active_debt_filing_records (
    enrollment_id UUID PRIMARY KEY REFERENCES active_debt_enrollments(id),
    process_number VARCHAR(60) NOT NULL, court VARCHAR(160) NOT NULL, phase VARCHAR(24) NOT NULL, registered_on DATE NOT NULL);
CREATE TABLE IF NOT EXISTS active_debt_protest_records (
    enrollment_id UUID PRIMARY KEY REFERENCES active_debt_enrollments(id),
    opinion_reference VARCHAR(120) NOT NULL, authorized_on DATE NOT NULL);
CREATE TABLE IF NOT EXISTS active_debt_monthly_balances (
    tenant_id UUID NOT NULL, month DATE NOT NULL, total_enrolled NUMERIC(19,2) NOT NULL,
    enrollment_count INT NOT NULL, frozen BOOLEAN NOT NULL DEFAULT TRUE, PRIMARY KEY (tenant_id, month));

-- Definicoes de parametro (catalogo P0.19): SEM valor de fabrica para
-- da.min_filing_amount (lei do ente) e para os codigos de modelo (minutas).
-- da.protest_enabled: fabrica FALSE (PD-10).
INSERT INTO parameter_definitions (key, factory_value) VALUES ('da.protest_enabled', 'false')
ON CONFLICT (key) DO NOTHING;
