-- P2.28 — Minhas tarefas: caixa unificada de pendências e aprovações, com prazo e
-- delegação temporária (plano v3.0 §3, tenant V105, módulo platform — reserva exata
-- desta tarefa: "`work_items` (aprovações e pendências de OS, retificação, parâmetro e
-- documento, com origem, responsável e vencimento) e `work_item_delegations` (início,
-- fim, registro)").
-- Renumerada V1_11 → V105 na cura da CI (correcao-ci-cura-lote-2; colisão com a V1_11 da P1.12; regra da P3.12, precedente R2-G1; V103/V104 ocupadas pela ED-103).
--
-- O QUE MUDA
--   * work_items: o CONTRATO de item de trabalho que cada módulo registra (tipo,
--     objeto, módulo de origem, solicitante, responsável OU aprovadores elegíveis OU
--     permissão exigida, prazo, estado e endereço do registro). Abertura e encerramento
--     chegam por eventos do catálogo da P1.38 (JOR-20) — o nome/versão do evento de
--     origem ficam gravados — ou pelo caminho interno síncrono do mesmo processo.
--     source_message_id deduplica a abertura pela mensagem da outbox (P1.9).
--   * approval_tasks: a tarefa de aprovação COMUM (nome do aceite: `approval_task`),
--     1:1 com o item de aprovação. A segregação solicitante != aprovador
--     (wf.self_approval, classe a, sempre falso) é garantida em TRÊS camadas: o
--     registro Java nem se constrói decidido pelo solicitante, a ApprovalRule recusa
--     a decisão, e o CHECK desta migration recusa a gravação (decided_by diferente
--     de requester_id) — nem SQL direto aprova o próprio pedido.
--   * work_item_delegations: a delegação temporária COM REGISTRO (a linha é a
--     auditoria: delegante, delegado, início, fim, conjunto de permissões concedidas,
--     motivo, quem registrou e a revogação, quando houver). O teto de dias
--     (wf.delegation_max_days) e o subconjunto do delegante são regras da aplicação
--     (DelegationRule) porque dependem de parâmetro e de permissão vigente, que o
--     banco não conhece; janela e não-auto-delegação também são CHECK aqui.
--   * work_item_events: trilha só de inserção do item (CREATED, STARTED, APPROVED,
--     REJECTED, CLOSED, ESCALATED), gravada na mesma transação do fato.
--
-- FKs CONDICIONAIS (padrão da V1_15 com a guarda da integração P1-C — UNIQUE
-- (tenant_id, id) via pg_constraint, não só to_regclass):
--   * users é da V1_9, que ordena ANTES desta na história única do tenant: na montagem
--     completa as FKs de usuário aplicam aqui.
--   * As FKs ENTRE as tabelas desta migration são diretas (nascem juntas).
-- Sem semente: tipos, eventos e permissões são catálogos dos donos (P1.38 eventos,
-- P0.13 permissões); semear aqui congelaria conteúdo alheio.

CREATE TABLE IF NOT EXISTS work_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    item_type VARCHAR(40) NOT NULL CHECK (item_type IN
        ('SERVICE_ORDER_APPROVAL', 'BILL_RECTIFICATION_APPROVAL', 'PARAMETER_APPROVAL',
         'DOCUMENT_APPROVAL', 'PROTOCOL_DUE', 'SERVICE_ORDER_OVERDUE', 'HELD_BILL',
         'FISCAL_REJECTION', 'GIS_PACKAGE_REVIEW', 'CORRECTIVE_ACTION', 'CALIBRATION_DUE',
         'HOLDER_REQUEST', 'REGULATORY_OBLIGATION', 'RECONCILIATION_DIVERGENCE')),
    object_type VARCHAR(80) NOT NULL CHECK (btrim(object_type) <> ''),
    object_id UUID NOT NULL,
    origin_module VARCHAR(60) NOT NULL CHECK (btrim(origin_module) <> ''),
    requester_id UUID NOT NULL,
    assignee_id UUID,
    eligible_approver_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_permission VARCHAR(120),
    due_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED', 'DONE', 'CANCELLED')),
    record_address VARCHAR(500) NOT NULL CHECK (btrim(record_address) <> ''),
    source_event_name VARCHAR(120) NOT NULL CHECK (btrim(source_event_name) <> ''),
    source_event_version INT NOT NULL CHECK (source_event_version >= 1),
    source_message_id UUID,
    approval_task_id UUID,
    close_reason VARCHAR(60),
    closed_at TIMESTAMPTZ,
    escalated_at TIMESTAMPTZ,
    escalation_count INT NOT NULL DEFAULT 0 CHECK (escalation_count >= 0),
    escalated_to_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (assignee_id IS NOT NULL OR required_permission IS NOT NULL
           OR eligible_approver_ids <> '[]'::jsonb),
    CHECK ((status IN ('DONE', 'CANCELLED') AND closed_at IS NOT NULL AND close_reason IS NOT NULL)
           OR (status NOT IN ('DONE', 'CANCELLED') AND closed_at IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_work_items_assignee_due
    ON work_items (assignee_id, due_at) WHERE status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED');
CREATE INDEX IF NOT EXISTS idx_work_items_open_due
    ON work_items (due_at, created_at) WHERE status IN ('OPEN', 'IN_PROGRESS', 'ESCALATED');
CREATE INDEX IF NOT EXISTS idx_work_items_object
    ON work_items (object_type, object_id);
-- Deduplicação da abertura: uma mensagem da outbox abre NO MÁXIMO um item por tenant.
-- Parcial: itens do caminho interno (source_message_id nulo) não disputam a chave.
CREATE UNIQUE INDEX IF NOT EXISTS uq_work_items_source_message
    ON work_items (tenant_id, source_message_id) WHERE source_message_id IS NOT NULL;

-- A UNIQUE (tenant_id, id) nasce ANTES das FKs que a referenciam (approval_tasks e
-- work_item_events apontam para work_items): sem ela, o ALTER da FK falha na criação.
ALTER TABLE work_items ADD CONSTRAINT uq_work_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS approval_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    work_item_id UUID NOT NULL,
    requester_id UUID NOT NULL,
    object_type VARCHAR(80) NOT NULL CHECK (btrim(object_type) <> ''),
    object_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    decision_reason TEXT,
    decision_channel VARCHAR(20) CHECK (decision_channel IN ('DIRECT', 'DELEGATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_approval_tasks_work_item UNIQUE (tenant_id, work_item_id),
    CONSTRAINT fk_approval_tasks_work_item
        FOREIGN KEY (tenant_id, work_item_id) REFERENCES work_items (tenant_id, id),
    -- Segregação no banco (wf.self_approval, classe a, sempre falso): nem SQL direto
    -- grava decisão do próprio solicitante.
    CHECK (decided_by IS NULL OR decided_by <> requester_id),
    CHECK ((status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
           OR (status IN ('APPROVED', 'REJECTED') AND decided_by IS NOT NULL
               AND decided_at IS NOT NULL AND decision_reason IS NOT NULL
               AND btrim(decision_reason) <> '' AND decision_channel IS NOT NULL))
);

ALTER TABLE approval_tasks ADD CONSTRAINT uq_approval_tasks_tenant_id UNIQUE (tenant_id, id);

-- A referência item -> tarefa fecha o par 1:1 (a tarefa nasce antes do item no serviço,
-- por isso a FK do item é adicionada depois das duas tabelas existirem).
ALTER TABLE work_items ADD CONSTRAINT fk_work_items_approval_task
    FOREIGN KEY (tenant_id, approval_task_id) REFERENCES approval_tasks (tenant_id, id);

CREATE TABLE IF NOT EXISTS work_item_delegations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    delegator_id UUID NOT NULL,
    delegate_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    granted_permissions JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    created_by UUID NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (delegate_id <> delegator_id),
    CHECK (ends_at > starts_at),
    CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_work_delegations_delegate_window
    ON work_item_delegations (delegate_id, starts_at, ends_at) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_work_delegations_delegator
    ON work_item_delegations (delegator_id, starts_at);

CREATE TABLE IF NOT EXISTS work_item_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    work_item_id UUID NOT NULL,
    event_type VARCHAR(20) NOT NULL CHECK (event_type IN
        ('CREATED', 'STARTED', 'APPROVED', 'REJECTED', 'CLOSED', 'ESCALATED')),
    actor_id UUID,
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_work_item_events_item
        FOREIGN KEY (tenant_id, work_item_id) REFERENCES work_items (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS idx_work_item_events_item
    ON work_item_events (work_item_id, created_at);

-- FKs de usuário condicionais com a guarda da integração P1-C (UNIQUE (tenant_id, id)
-- via pg_constraint, como na V1_15 — não basta a tabela existir).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        WHERE c.conrelid = to_regclass('users')
          AND c.contype IN ('u', 'p')
          AND (SELECT array_agg(a.attname::text ORDER BY k.ord)
               FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord)
               JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum)
              = ARRAY['tenant_id', 'id']
    ) THEN
        ALTER TABLE work_items ADD CONSTRAINT fk_work_items_requester
            FOREIGN KEY (tenant_id, requester_id) REFERENCES users (tenant_id, id);
        ALTER TABLE work_items ADD CONSTRAINT fk_work_items_assignee
            FOREIGN KEY (tenant_id, assignee_id) REFERENCES users (tenant_id, id);
        ALTER TABLE work_items ADD CONSTRAINT fk_work_items_escalated_to
            FOREIGN KEY (tenant_id, escalated_to_id) REFERENCES users (tenant_id, id);
        ALTER TABLE approval_tasks ADD CONSTRAINT fk_approval_tasks_requester
            FOREIGN KEY (tenant_id, requester_id) REFERENCES users (tenant_id, id);
        ALTER TABLE approval_tasks ADD CONSTRAINT fk_approval_tasks_decider
            FOREIGN KEY (tenant_id, decided_by) REFERENCES users (tenant_id, id);
        ALTER TABLE work_item_delegations ADD CONSTRAINT fk_work_delegations_delegator
            FOREIGN KEY (tenant_id, delegator_id) REFERENCES users (tenant_id, id);
        ALTER TABLE work_item_delegations ADD CONSTRAINT fk_work_delegations_delegate
            FOREIGN KEY (tenant_id, delegate_id) REFERENCES users (tenant_id, id);
    END IF;
END $$;

SELECT register_tenant_table('work_items');
SELECT register_tenant_table('approval_tasks');
SELECT register_tenant_table('work_item_delegations');
SELECT register_tenant_table('work_item_events');

COMMENT ON TABLE work_items IS
    'P2.28: contrato de item de trabalho da caixa unificada Minhas tarefas (tipo, objeto, origem, solicitante, responsável/elegíveis/permissão, prazo, estado, endereço do registro); abertura/encerramento por eventos da P1.38.';
COMMENT ON TABLE approval_tasks IS
    'P2.28: tarefa de aprovação comum, 1:1 com o item; segregação solicitante != aprovador (wf.self_approval, classe a) também em CHECK.';
COMMENT ON TABLE work_item_delegations IS
    'P2.28: delegação temporária registrada (a linha é a auditoria): delegante, delegado, início, fim, permissões concedidas, motivo e revogação.';
COMMENT ON TABLE work_item_events IS
    'P2.28: trilha só de inserção dos itens de trabalho, gravada na mesma transação do fato.';
