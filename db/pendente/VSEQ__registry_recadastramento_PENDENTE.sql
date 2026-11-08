-- ============================================================================
-- ED-16 — Recadastramento (18d-E11; ADR-0039) — SQL PENDENTE (SEM NUMERO)
-- ----------------------------------------------------------------------------
-- ESTE ARQUIVO NAO E UMA MIGRATION APLICAVEL AINDA: o mapa de versoes do
-- plano v3.0 (secao 3) NAO reserva numero fixo para a ED-16, e a instrucao
-- da tarefa proibe inventar numero. Na integracao, este conteudo entra
-- como
--   V<proximo da sequencia global>__registry_recadastramento.sql
-- no location de tenant do modulo ziipsan-registry, DEPOIS da migration
-- da P3.32 (a fila cadastral_quality_findings, da qual a REC-001 e a
-- REC-004 leem, precisa existir antes). Os nomes de tabela/coluna abaixo
-- sao os das entidades JPA entregues (RecadastramentoBatchEntity,
-- CollectedFieldEntity, CollectionErrorEntity,
-- RecadastramentoHistoryEntity) — mudar aqui exige mudar la.
--
-- Convencoes seguidas (das migrations reais do registry, ex. V3_1/V3_12
-- e do rascunho da P3.32): UUID PK com uuid_generate_v4(), tenant_id em
-- toda tabela, UNIQUE (tenant_id, id) para FK composta, timestamps, e as
-- colunas version e tenant_schema NAO sao declaradas aqui: quem as
-- acrescenta e o register_tenant_table(...), que tambem liga
-- RLS/auditoria. Nenhum catalogo e semeado.
-- ============================================================================

-- Lote de recadastramento (= a OS interna de lote dele)
CREATE TABLE IF NOT EXISTS recadastramento_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    queue_item_ids_json TEXT NOT NULL,
    target_unit_ids_json TEXT NOT NULL,
    confirmed_unit_ids_json TEXT,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'CLOSED')),
    closed_by UUID,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- Lote e a OS dele: encerrado exige quem e quando.
    CHECK (status <> 'CLOSED' OR (closed_by IS NOT NULL AND closed_at IS NOT NULL))
);
ALTER TABLE recadastramento_batches ADD CONSTRAINT uq_recad_batches_tenant_id UNIQUE (tenant_id, id);
-- Um lote por OS de recadastramento.
CREATE UNIQUE INDEX IF NOT EXISTS uq_recad_batches_order
    ON recadastramento_batches(tenant_id, service_order_id);
-- SEM FK para service_order_id aqui: service_orders e criada pela V9_1 do
-- core (ordem entre locations e da integracao, como na V3_12 -> V9_3).

-- Campo coletado em campo (uma linha por tarefa/campo)
CREATE TABLE IF NOT EXISTS recadastramento_collected_fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    task_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    customer_id UUID,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CUSTOMER', 'CONSUMER_UNIT')),
    field VARCHAR(100) NOT NULL,
    value_at_collection TEXT,
    collected_value TEXT NOT NULL,
    collector_id UUID NOT NULL,
    photo_refs_json TEXT,
    captured_at TIMESTAMPTZ,
    status VARCHAR(12) NOT NULL DEFAULT 'COLLECTED'
        CHECK (status IN ('COLLECTED', 'APPROVED', 'REJECTED', 'APPLIED', 'STALE', 'SUPERSEDED')),
    decided_by UUID,
    decision_reason VARCHAR(300),
    decided_at TIMESTAMPTZ,
    applied_change_request_id UUID,
    applied_at TIMESTAMPTZ,
    value_at_application TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    -- Campo de cliente exige o cliente coletado.
    CHECK (target_type <> 'CUSTOMER' OR customer_id IS NOT NULL),
    -- Decisao campo a campo: decididos carregam quem e quando; rejeitado
    -- carrega tambem o motivo.
    CHECK (status NOT IN ('APPROVED', 'REJECTED', 'APPLIED')
           OR (decided_by IS NOT NULL AND decided_at IS NOT NULL)),
    CHECK (status <> 'REJECTED' OR decision_reason IS NOT NULL),
    -- Aplicado carrega o instante; a exigencia do registro no historico
    -- quando ha diferenca e do servico (o banco nao conhece a diferenca).
    CHECK (status <> 'APPLIED' OR applied_at IS NOT NULL)
    -- STALE: deliberadamente SEM check de NOT NULL sobre
    -- value_at_application. O STALE guarda o valor vigente lido na
    -- tentativa de aplicacao, e esse valor pode ser NULL de forma
    -- legitima: quando o campo foi ESVAZIADO no cadastro depois da
    -- coleta, a porta de leitura (CadastralFieldReader) devolve null
    -- ("campo sem valor devolve null") e o esvaziamento tambem e
    -- divergencia que marca STALE. Nesse caso, value_at_application
    -- NULL significa "campo esvaziado apos a coleta" — nao ausencia de
    -- leitura — e a linha e valida (dominio: CollectedField.markStale
    -- aceita null exatamente para este caso). Um CHECK exigindo
    -- IS NOT NULL violaria a persistencia dessa ocorrencia legitima.
);
ALTER TABLE recadastramento_collected_fields ADD CONSTRAINT uq_recad_fields_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE recadastramento_collected_fields ADD CONSTRAINT fk_recad_fields_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES recadastramento_batches(tenant_id, id);
ALTER TABLE recadastramento_collected_fields ADD CONSTRAINT fk_recad_fields_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
-- Idempotencia da sincronizacao (ED-18): um task_id por lote, uma coleta.
CREATE UNIQUE INDEX IF NOT EXISTS uq_recad_fields_task
    ON recadastramento_collected_fields(tenant_id, batch_id, task_id);
CREATE INDEX IF NOT EXISTS idx_recad_fields_batch_status
    ON recadastramento_collected_fields(tenant_id, batch_id, status);
CREATE INDEX IF NOT EXISTS idx_recad_fields_collector
    ON recadastramento_collected_fields(tenant_id, collector_id, created_at);

-- Erro de coleta (submissao recusada na sincronizacao)
CREATE TABLE IF NOT EXISTS recadastramento_collection_errors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    task_id UUID,
    unit_id UUID,
    target_type VARCHAR(20),
    field VARCHAR(100),
    error_code VARCHAR(30) NOT NULL CHECK (error_code IN (
        'FIELD_NOT_PREDEFINED', 'VALUE_BLANK', 'UNIT_NOT_IN_BATCH',
        'TASK_PAYLOAD_CONFLICT', 'DOCUMENT_NOT_MASKED', 'BATCH_NOT_OPEN',
        'TARGET_MISMATCH')),
    detail VARCHAR(500) NOT NULL,
    collector_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE recadastramento_collection_errors ADD CONSTRAINT uq_recad_errors_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE recadastramento_collection_errors ADD CONSTRAINT fk_recad_errors_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES recadastramento_batches(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_recad_errors_batch
    ON recadastramento_collection_errors(tenant_id, batch_id, occurred_at);

-- Historico do recadastramento (uma linha por alteracao aplicada)
CREATE TABLE IF NOT EXISTS recadastramento_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    collected_field_id UUID NOT NULL,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CUSTOMER', 'CONSUMER_UNIT')),
    target_id UUID NOT NULL,
    field VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    collector_id UUID NOT NULL,
    approver_id UUID NOT NULL,
    change_request_id UUID NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE recadastramento_history ADD CONSTRAINT uq_recad_history_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE recadastramento_history ADD CONSTRAINT fk_recad_history_batch
    FOREIGN KEY (tenant_id, batch_id) REFERENCES recadastramento_batches(tenant_id, id);
ALTER TABLE recadastramento_history ADD CONSTRAINT fk_recad_history_field
    FOREIGN KEY (tenant_id, collected_field_id) REFERENCES recadastramento_collected_fields(tenant_id, id);
ALTER TABLE recadastramento_history ADD CONSTRAINT fk_recad_history_change
    FOREIGN KEY (tenant_id, change_request_id) REFERENCES cadastral_change_requests(tenant_id, id);
-- Uma aplicacao por campo coletado.
CREATE UNIQUE INDEX IF NOT EXISTS uq_recad_history_field
    ON recadastramento_history(tenant_id, collected_field_id);
CREATE INDEX IF NOT EXISTS idx_recad_history_target
    ON recadastramento_history(tenant_id, target_type, target_id, applied_at);

SELECT register_tenant_table('recadastramento_batches');
SELECT register_tenant_table('recadastramento_collected_fields');
SELECT register_tenant_table('recadastramento_collection_errors');
SELECT register_tenant_table('recadastramento_history');

-- Auditoria (padrao V3_12): os valores dos campos coletados podem conter
-- dado pessoal (nome, contato); na trilha, REDACT como em
-- cadastral_change_requests.
-- INSERT INTO audit_column_policies (table_name, column_name, action, reason) VALUES
--     ('recadastramento_collected_fields', 'value_at_collection', 'REDACT', 'valor anterior do campo, pode conter dado pessoal'),
--     ('recadastramento_collected_fields', 'collected_value', 'REDACT', 'valor coletado do campo, pode conter dado pessoal'),
--     ('recadastramento_collected_fields', 'value_at_application', 'REDACT', 'valor vigente na aplicacao, pode conter dado pessoal'),
--     ('recadastramento_history', 'old_value', 'REDACT', 'valor anterior do campo, pode conter dado pessoal'),
--     ('recadastramento_history', 'new_value', 'REDACT', 'valor novo do campo, pode conter dado pessoal');
-- (descomentar na integracao conferindo o nome exato da tabela de
-- politicas na migration vigente do registry — nao inventado aqui)

-- ============================================================================
-- Views do nucleo de relatorios da P3.22 (RecadastramentoReports):
-- colunas identicas as declaradas nas definicoes REC-001 a REC-005,
-- mais tenant_id (padrao das views da P3.32).
-- ============================================================================

-- REC-001 (SG-910): diferencas cadastrais com necessidade de
-- recadastramento = achados da P3.32 abertos ou agrupados em lote.
CREATE OR REPLACE VIEW rpt_recadastramento_differences AS
SELECT f.created_at::date AS referencia,
       f.unit_id::text AS unidade_id,
       f.rule_code AS regra,
       f.severity AS severidade,
       f.status AS situacao,
       f.detail AS detalhe,
       f.tenant_id AS tenant_id
  FROM cadastral_quality_findings f
 WHERE f.status IN ('OPEN', 'BATCHED');

-- REC-002 (SG-911): erros no recadastramento.
CREATE OR REPLACE VIEW rpt_recadastramento_errors AS
SELECT e.occurred_at::date AS referencia,
       e.batch_id::text AS lote_id,
       e.unit_id::text AS unidade_id,
       e.field AS campo,
       e.error_code AS codigo_erro,
       e.collector_id::text AS coletor_id,
       e.tenant_id AS tenant_id
  FROM recadastramento_collection_errors e;

-- REC-003 (SG-912): ocorrencias de recadastramento incorreto
-- (campos STALE ou ja supersedidos por nova coleta).
-- valor_na_aplicacao NULL numa ocorrencia = o campo foi esvaziado no
-- cadastro depois da coleta (ver a nota do STALE na tabela acima).
CREATE OR REPLACE VIEW rpt_recadastramento_incorrect AS
SELECT c.updated_at::date AS referencia,
       c.batch_id::text AS lote_id,
       c.unit_id::text AS unidade_id,
       c.field AS campo,
       c.value_at_collection AS valor_na_coleta,
       c.collected_value AS valor_coletado,
       c.value_at_application AS valor_na_aplicacao,
       c.collector_id::text AS coletor_id,
       c.tenant_id AS tenant_id
  FROM recadastramento_collected_fields c
 WHERE c.status IN ('STALE', 'SUPERSEDED');

-- REC-004 (SG-913): unidades recentes com necessidade de
-- recadastramento (achado aberto/agrupado; a "recencia" e a data de
-- criacao da unidade, filtrada/ordenada por quem executa o relatorio).
CREATE OR REPLACE VIEW rpt_recadastramento_recent_units AS
SELECT u.created_at::date AS unidade_criada_em,
       f.unit_id::text AS unidade_id,
       f.rule_code AS regra,
       f.severity AS severidade,
       f.created_at::date AS data_achado,
       f.tenant_id AS tenant_id
  FROM cadastral_quality_findings f
  JOIN consumer_units u
    ON u.tenant_id = f.tenant_id AND u.id = f.unit_id
 WHERE f.status IN ('OPEN', 'BATCHED')
   AND f.unit_id IS NOT NULL;

-- REC-005 (SG-914): eficiencia dos recadastradores por dia.
-- aprovados conta APPROVED + APPLIED (aplicado ja foi aprovado);
-- taxa_aprovacao = aprovados / coletados no dia.
CREATE OR REPLACE VIEW rpt_recadastramento_collector_efficiency AS
SELECT agg.referencia AS referencia,
       agg.coletor_id AS coletor_id,
       agg.coletados AS coletados,
       agg.aprovados AS aprovados,
       agg.rejeitados AS rejeitados,
       agg.aplicados AS aplicados,
       COALESCE(err.erros, 0) AS erros,
       agg.taxa_aprovacao AS taxa_aprovacao,
       agg.tenant_id AS tenant_id
  FROM (
        SELECT c.created_at::date AS referencia,
               c.collector_id::text AS coletor_id,
               COUNT(*) AS coletados,
               COUNT(*) FILTER (WHERE c.status IN ('APPROVED', 'APPLIED')) AS aprovados,
               COUNT(*) FILTER (WHERE c.status = 'REJECTED') AS rejeitados,
               COUNT(*) FILTER (WHERE c.status = 'APPLIED') AS aplicados,
               ROUND(COUNT(*) FILTER (WHERE c.status IN ('APPROVED', 'APPLIED'))::numeric
                     / NULLIF(COUNT(*), 0), 2) AS taxa_aprovacao,
               c.tenant_id AS tenant_id
          FROM recadastramento_collected_fields c
         GROUP BY c.created_at::date, c.collector_id, c.tenant_id
       ) agg
  LEFT JOIN (
        SELECT e.occurred_at::date AS referencia,
               e.collector_id::text AS coletor_id,
               COUNT(*) AS erros,
               e.tenant_id AS tenant_id
          FROM recadastramento_collection_errors e
         WHERE e.collector_id IS NOT NULL
         GROUP BY e.occurred_at::date, e.collector_id, e.tenant_id
       ) err
    ON err.referencia = agg.referencia
   AND err.coletor_id = agg.coletor_id
   AND err.tenant_id = agg.tenant_id;
