-- ED-20 — OS em campo e acompanhamento de campo — SQL PENDENTE, SEM NUMERO Flyway.
-- Motivo: a numeracao global e atribuida no pouso pelo coordenador (regra da
-- cascata; ultimo numero usado na main declarado: V101). Este arquivo vive em
-- db/pendente/ do modulo core e NAO e executado pelo Flyway ate receber numero.
-- NENHUM valor de tenant e semeado: janelas, retencao e modos de aceite sao
-- do tenant; field.track_only_in_shift nasce com a trava do aceite (true).

-- (1) Pacote de corte emitido ao aparelho (retrato na emissao) --------------------
CREATE TABLE IF NOT EXISTS field_cut_package (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    task_id VARCHAR(120) NOT NULL CHECK (btrim(task_id) <> ''),
    debit_state VARCHAR(10) NOT NULL CHECK (debit_state IN ('OPEN', 'PAID', 'NONE')),
    contestation_open BOOLEAN NOT NULL,
    notice_given BOOLEAN NOT NULL,
    prohibitions JSONB NOT NULL DEFAULT '[]'::jsonb,
    issued_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_field_cut_package_order ON field_cut_package (tenant_id, order_id);

-- (2) Execucao de campo (estados visiveis; o estado do motor e o da P1.31) -------
CREATE TABLE IF NOT EXISTS field_execution (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    visible_state VARCHAR(14) NOT NULL CHECK (visible_state IN ('EN_ROUTE', 'ON_SITE', 'IN_EXECUTION', 'COMPLETED')),
    before_photo_document_id UUID,
    after_photo_document_id UUID,
    acceptance_mode VARCHAR(10) CHECK (acceptance_mode IN ('SIGNATURE', 'CODE', 'NONE')),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (visible_state <> 'COMPLETED' OR (before_photo_document_id IS NOT NULL AND after_photo_document_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_field_execution_order ON field_execution (tenant_id, order_id);

-- (3) Trilha GPS (acesso so da chefia; retencao por field.track_retention_days) --
CREATE TABLE IF NOT EXISTS field_track_points (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    worker_id UUID NOT NULL,
    team_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180)
);
CREATE INDEX IF NOT EXISTS idx_field_track_worker_time ON field_track_points (tenant_id, worker_id, occurred_at);

-- (4) Material usado por tarefa (baixa e da ED-58; aqui so o registro) ----------
CREATE TABLE IF NOT EXISTS field_material_use (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    task_id VARCHAR(120) NOT NULL CHECK (btrim(task_id) <> ''),
    material_code VARCHAR(120) NOT NULL CHECK (btrim(material_code) <> ''),
    used_qty NUMERIC(19, 4) NOT NULL CHECK (used_qty >= 0),
    synced_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_field_material_use_task ON field_material_use (tenant_id, task_id);

SELECT register_tenant_table('field_cut_package');
SELECT register_tenant_table('field_execution');
SELECT register_tenant_table('field_track_points');
SELECT register_tenant_table('field_material_use');

COMMENT ON TABLE field_track_points IS
    'ED-20: trilha GPS de campo. Finalidade: acompanhamento da execucao das OS. Base legal: LGPD art. 7o, V e IX. Item do RIPD do tenant (P3.10): FIELD_GPS_TRACKING. Acesso restrito a chefia.';

-- (5) Definicoes de parametros no catalogo global (CORE — destacar na integracao) --
-- Padrao da V9_11 (P3.36). Chaves nomeadas pelo acceptance da ED-20.
-- factory_default: track_only_in_shift nasce "true" porque o aceite o TRAVA em
-- verdadeiro (nao e um dado presumido: e a propria regra); os demais NAO tem
-- fabrica — ausencia = recusa nomeada.
INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('field.track_only_in_shift', 'BOOLEAN', 'd', '"true"',
     'ED-20 (travado em verdadeiro pelo aceite)', NULL,
     'Rastreamento GPS somente durante a jornada. Travado em verdadeiro: configuracao em falso e recusada.'),
    ('field.track_retention_days', 'INTEGER', 'd', NULL,
     'ED-20', NULL,
     'Retencao da trilha GPS em dias. So reduz em relacao ao teto da plataforma. Sem valor vigente, nao ha retencao definida.'),
    ('field.photo_blur_faces', 'BOOLEAN', 'd', NULL,
     'ED-20', NULL,
     'Desfoque de rostos nas fotos de campo. Sem valor vigente, a exigencia nao esta definida para o tenant.'),
    ('so.appointment_window_hours', 'INTEGER', 'd', NULL,
     'ED-20', NULL,
     'Janela de atendimento combinada em horas: base da janela estimada do aviso ''a caminho'' (ED-41).'),
    ('so.require_customer_acceptance_by_type', 'JSON', 'd', NULL,
     'ED-20', NULL,
     'Mapa tipo de OS -> modo de aceite do usuario (SIGNATURE | CODE | NONE). Tipo fora do mapa: [A DEFINIR], conclusao recusada.')
ON CONFLICT (definition_key) DO NOTHING;
