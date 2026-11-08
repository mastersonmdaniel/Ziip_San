-- ED-44 Telefonia minima — SQL PENDENTE DE NUMERACAO FLYWAY (prefixo ED-44__).
-- Sem numero inventado. Licacao C3: TODA FK composta (tenant_id, X) exige
-- UNIQUE (tenant_id, id) no pai — declarado em todas as tabelas deste arquivo.
-- Credencial de URA: so credential_reference (referencia ao cofre), nunca valor.
CREATE TABLE IF NOT EXISTS telephony_ura_clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL,
    client_id VARCHAR(80) NOT NULL, credential_reference VARCHAR(300) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, id), UNIQUE (tenant_id, client_id));
CREATE TABLE IF NOT EXISTS telephony_call_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL,
    call_id VARCHAR(120) NOT NULL, caller_number_normalized VARCHAR(20) NOT NULL,
    attendant_id UUID, consumer_unit_id UUID, outcome VARCHAR(20) NOT NULL
      CHECK (outcome IN ('OPENED','NOT_FOUND','AMBIGUOUS')),
    received_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, id), UNIQUE (tenant_id, call_id));
CREATE TABLE IF NOT EXISTS telephony_access_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL,
    actor_id UUID, ura_client_id VARCHAR(80), access_type VARCHAR(30) NOT NULL
      CHECK (access_type IN ('CALL_SCREEN_POP','URA_QUERY','DIALING_LIST_GENERATED')),
    consumer_unit_id UUID, caller_number_normalized VARCHAR(20),
    result VARCHAR(20) NOT NULL CHECK (result IN ('FOUND','NOT_FOUND','AMBIGUOUS','DENIED')),
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, id));
CREATE INDEX IF NOT EXISTS idx_telephony_access_tenant_time ON telephony_access_log (tenant_id, accessed_at);
CREATE TABLE IF NOT EXISTS telephony_dialing_lists (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL,
    requested_by UUID NOT NULL, generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, id));
CREATE TABLE IF NOT EXISTS telephony_dialing_list_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(), tenant_id UUID NOT NULL,
    list_id UUID NOT NULL, consumer_unit_id UUID NOT NULL, phone_normalized VARCHAR(20) NOT NULL,
    source_rule_code VARCHAR(80) NOT NULL, source_kind VARCHAR(20) NOT NULL
      CHECK (source_kind IN ('COBRANCA','ATENDIMENTO')),
    UNIQUE (tenant_id, id), UNIQUE (tenant_id, list_id, phone_normalized),
    FOREIGN KEY (tenant_id, list_id) REFERENCES telephony_dialing_lists(tenant_id, id));
SELECT register_tenant_table('telephony_ura_clients');
SELECT register_tenant_table('telephony_call_events');
SELECT register_tenant_table('telephony_access_log');
SELECT register_tenant_table('telephony_dialing_lists');
SELECT register_tenant_table('telephony_dialing_list_items');
-- Parametros (definitions no catalogo do core na integracao, sem valor semeado):
-- telephony.screen_pop_enabled, telephony.ura_enabled, telephony.ura_client
