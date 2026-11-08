-- ED-19 — Emissao simultanea e impressao termica: contas de campo,
-- divergencias e trabalhos de impressao.
--
-- NUMERACAO PENDENTE (declarado): este SQL esta em db/pendente/ SEM numero
-- Flyway porque nenhum slot livre da serie tenant do billing foi CONFIRMADO
-- para a ED-19 nas fontes desta frente (a serie conhecida chega a V4_16 da
-- P2.20; o §3 do plano nao reserva numero para a ED-19). Na integracao, com
-- o historico Flyway real do tenant diante dos olhos, o arquivo recebe o
-- proximo numero livre e muda para db/migration/tenant/ — o CONTEUDO nao
-- muda. Precedente do projeto: ED-55 (db/pendente) e a renumeracao da P2.20.
--
-- Depende de: bills (V4_1/V4_4), bill_field_changes e trilhas da P2.20
-- (V4_16) apenas por referencia logica (bill_id) — nenhuma FK cruzada e
-- criada aqui para nao acoplar a ordem das migrations.
--
-- Desenho:
--  * field_bills: UM registro por (tenant_id, task_id) — a unicidade e a
--    garantia de banco da idempotencia da submissao (reenvio nao duplica
--    conta nem NFAg-e: a NFAg-e da P2.6 nasce por BILL + bill_id, e o
--    bill_id daqui e estavel por tarefa).
--  * O resultado impresso e o recalculado ficam em JSON canonico (a forma
--    do fingerprint das regras puras) + o fingerprint de cada um: a conta
--    impressa e imutavel; a correcao por nova versao (P2.20) muda
--    current_bill_version, nunca o printed_*.
--  * field_bill_divergences: a fila de divergencia com a explicacao
--    (diferencas campo a campo) e a trilha de aprovacao (dupla acima do
--    limite: segundo aprovador em coluna propria).
--  * field_print_jobs: trabalhos de impressao/reimpressao termica com o
--    modelo (P2.30) e a versao usados — documento gerado grava modelo e
--    versao, como manda o motor de modelos.

CREATE TABLE field_bills (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    task_id             uuid NOT NULL,
    route_id            uuid,
    bill_id             uuid NOT NULL,
    status              varchar(32) NOT NULL,
    printed_fingerprint varchar(64) NOT NULL,
    printed_result      jsonb NOT NULL,
    server_fingerprint  varchar(64),
    server_result       jsonb,
    current_bill_version integer NOT NULL DEFAULT 1,
    nfage_contingency   boolean NOT NULL DEFAULT false,
    received_at         timestamptz NOT NULL,
    CONSTRAINT uq_field_bills_tenant_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_field_bills_status CHECK (status IN
        ('CONFIRMED', 'DIVERGENT_QUEUED', 'OCR_HELD', 'CERTIFICATE_MISSING',
         'CORRECTED_BY_NEW_VERSION')),
    CONSTRAINT ck_field_bills_version CHECK (current_bill_version >= 1)
);

CREATE TABLE field_bill_divergences (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    task_id             uuid NOT NULL,
    bill_id             uuid NOT NULL,
    explanation         jsonb NOT NULL,
    delta_total         numeric(14, 2) NOT NULL,
    status              varchar(24) NOT NULL DEFAULT 'QUEUED',
    first_approver_id   uuid,
    second_approver_id  uuid,
    approved_at         timestamptz,
    correction_version  integer,
    created_at          timestamptz NOT NULL,
    CONSTRAINT uq_field_divergences_tenant_task UNIQUE (tenant_id, task_id),
    CONSTRAINT ck_field_divergences_status CHECK (status IN
        ('QUEUED', 'APPROVED_CORRECTED', 'REJECTED')),
    CONSTRAINT ck_field_divergences_double CHECK (
        status <> 'APPROVED_CORRECTED' OR first_approver_id IS NOT NULL)
);

CREATE TABLE field_print_jobs (
    id                  uuid PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    task_id             uuid NOT NULL,
    document_type       varchar(24) NOT NULL,
    template_id         uuid NOT NULL,
    template_version    varchar(32) NOT NULL,
    channel             varchar(8) NOT NULL,
    reprint             boolean NOT NULL DEFAULT false,
    printer_device_id   uuid,
    created_at          timestamptz NOT NULL,
    CONSTRAINT ck_field_print_jobs_type CHECK (document_type IN
        ('FIELD_BILL', 'DEBIT_NOTICE', 'EXCESS_NOTICE')),
    CONSTRAINT ck_field_print_jobs_channel CHECK (channel IN ('ZPL', 'CPCL'))
);

CREATE INDEX ix_field_bills_tenant_status ON field_bills (tenant_id, status);
CREATE INDEX ix_field_divergences_tenant_status ON field_bill_divergences (tenant_id, status);
CREATE INDEX ix_field_print_jobs_tenant_task ON field_print_jobs (tenant_id, task_id);
