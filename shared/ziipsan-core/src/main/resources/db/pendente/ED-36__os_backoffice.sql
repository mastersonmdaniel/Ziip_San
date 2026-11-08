-- ED-36 OS completa no backoffice — SQL PENDENTE DE NUMERACAO FLYWAY.
-- Nenhum numero foi confirmado livre para esta frente (historias tenant/core
-- ja ocupadas e pacotes locais pendentes disputam slots); NAO inventar
-- numero: na integracao, atribuir o proximo slot livre da historia tenant do
-- modulo shared/ziipsan-core, renomear o arquivo e conferir FKs contra V9_1
-- (service_orders/service_types/service_order_targets), V8_1
-- (service_protocols) e o catalogo de parametros (V9_7 registrou so.*/sst.*
-- da P1.31 e declarou as chaves da ED-36 pendentes desta frente — ver o
-- bloco de definitions ao final).
-- Escopo: SO o que a P1.31 nao tem. Estado, alvos, efeitos, equipe e
-- seguranca continuam nas tabelas da V9_1 (nenhum modelo redefinido).
-- Materiais: previsto x usado SOMENTE. Requisicao com alcada, reserva e
-- baixa de estoque sao da ED-58 (ADR-0032) — nenhuma tabela de estoque aqui.
-- Anexos: o arquivo vive nos documentos da P1.20; aqui so a ligacao com o
-- estado da varredura (ADR-0020: quarentena ate o ClamAV liberar).

CREATE TABLE IF NOT EXISTS service_order_material_lines (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    material_code VARCHAR(100) NOT NULL,
    planned_qty NUMERIC(15,3) NOT NULL CHECK (planned_qty > 0),
    used_qty NUMERIC(15,3) CHECK (used_qty IS NULL OR used_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_material_lines ADD CONSTRAINT uq_so_material_lines_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_so_material_lines_order_code ON service_order_material_lines(tenant_id, order_id, material_code);

-- Agendamento em janela do tenant (sched.customer_windows): a OS continua
-- com scheduled_for na V9_1; esta tabela guarda a janela escolhida, a
-- confirmacao do usuario e a trilha de reagendamento.
CREATE TABLE IF NOT EXISTS service_order_window_bookings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    window_id VARCHAR(60) NOT NULL,
    scheduled_start TIMESTAMPTZ NOT NULL,
    customer_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((customer_confirmed) = (confirmed_at IS NOT NULL) OR (customer_confirmed = FALSE AND confirmed_at IS NULL)),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_window_bookings ADD CONSTRAINT uq_so_window_bookings_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_so_window_bookings_window ON service_order_window_bookings(tenant_id, window_id, scheduled_start) WHERE active;

CREATE TABLE IF NOT EXISTS service_order_schedule_trail (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    action VARCHAR(20) NOT NULL CHECK (action IN ('SCHEDULE','RESCHEDULE','UNSCHEDULE','CONFIRM')),
    previous_start TIMESTAMPTZ,
    previous_window_id VARCHAR(60),
    new_start TIMESTAMPTZ,
    new_window_id VARCHAR(60),
    reason VARCHAR(500),
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (action <> 'RESCHEDULE' OR NULLIF(btrim(reason),'') IS NOT NULL),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_schedule_trail ADD CONSTRAINT uq_so_schedule_trail_tenant_id UNIQUE (tenant_id, id);

-- Baixa posterior (equipe sem app / empreiteira): executed_at e a hora que
-- vale para os prazos de religacao e de compensacao (P3.4 conta dela).
CREATE TABLE IF NOT EXISTS service_order_retroactive_closes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    execution_mode VARCHAR(20) NOT NULL CHECK (execution_mode IN ('FIELD','CONTRACTOR')),
    contractor_ref VARCHAR(200),
    executor_id UUID NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    photo_document_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (executed_at <= registered_at),
    CHECK (execution_mode <> 'CONTRACTOR' OR NULLIF(btrim(contractor_ref),'') IS NOT NULL),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_retroactive_closes ADD CONSTRAINT uq_so_retro_closes_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX uq_so_retro_closes_order ON service_order_retroactive_closes(tenant_id, order_id);

-- Retrabalho: OS nova ligada a original CLOSED_EXECUTED (a original nao reabre).
CREATE TABLE IF NOT EXISTS service_order_rework_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    original_order_id UUID NOT NULL,
    rework_order_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL CHECK (NULLIF(btrim(reason),'') IS NOT NULL),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (original_order_id <> rework_order_id),
    FOREIGN KEY (tenant_id, original_order_id) REFERENCES service_orders(tenant_id, id),
    FOREIGN KEY (tenant_id, rework_order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_rework_links ADD CONSTRAINT uq_so_rework_links_tenant_id UNIQUE (tenant_id, id);

-- Anexos: ligacao OS <-> documento da P1.20 com o estado da varredura.
CREATE TABLE IF NOT EXISTS service_order_attachments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    document_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PHOTO','DOCUMENT','OTHER')),
    scan_status VARCHAR(20) NOT NULL CHECK (scan_status IN ('QUARANTINE','CLEARED','INFECTED')),
    content_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (tenant_id, order_id) REFERENCES service_orders(tenant_id, id)
);
ALTER TABLE service_order_attachments ADD CONSTRAINT uq_so_attachments_tenant_id UNIQUE (tenant_id, id);

SELECT register_tenant_table('service_order_material_lines');
SELECT register_tenant_table('service_order_window_bookings');
SELECT register_tenant_table('service_order_schedule_trail');
SELECT register_tenant_table('service_order_retroactive_closes');
SELECT register_tenant_table('service_order_rework_links');
SELECT register_tenant_table('service_order_attachments');

-- Definitions dos parametros da ED-36 — historia CORE (public), conferir o
-- formato exato das colunas contra a V9_7 na integracao. A V9_7 declarou
-- pendentes desta frente: so.retroactive_close_days,
-- so.customer_order_auto_expire e so.material_charge_policy. Os valores de
-- fabrica abaixo sao os do catalogo PARAMETROS-DO-TENANT (20d SS5): NAO sao
-- numeros inventados aqui.
--   so.retroactive_close_days:    INTEGER, classe b, padrao 3
--   so.customer_order_auto_expire: ENUM NEVER/dias, classe b, padrao NEVER
--   so.material_charge_policy:    ENUM NEVER/PRICE_TABLE_ERI, classe b, padrao NEVER
--   sched.priority_weights:       OBJECT pesos por criterio, classe d
--                                 (urgencia legal sempre no topo)
--   sched.customer_windows:       ARRAY de janelas {id, dias, inicio, fim,
--                                 capacidade}, classe d, SEM padrao (tenant define)
--   sched.hard_constraints:       ARRAY de restricoes, classe d, SEM padrao
--                                 (SAFETY_QUALIFICATION e ORDER_DEADLINE nao
--                                 removiveis — trava no dominio, SchedParameters)
--   sched.soft_weights:           OBJECT {regionGrouping, loadBalance},
--                                 classe d, SEM padrao
--   sched.replan_on_urgency:      BOOLEAN, classe d, SEM padrao
