-- P0.16 opcao 1 (decisao do dono, 2026-11-02): modelar no backend os campos que o
-- frontend de cliente ja envia e hoje sao descartados em silencio (fail-on-unknown=false).
-- Version: 3.4.0
--
-- Escopo: SOMENTE o dominio cliente. Nao depende de tabelas de nenhuma outra frente
-- (por isso customer_type_id e activity_branch_id sao colunas sem FK: as tabelas de
-- catalogo ainda nao existem no backend; a referencia fica pendente, registrada no
-- codigo, e a FK podera ser acrescentada quando a frente de catalogos criar as tabelas).
-- parent_customer_id referencia a propria tabela customers, que existe: FK composta por
-- tenant, no mesmo padrao das FKs de V3_1.
--
-- addresses[] REUSA a tabela polimorfica addresses (entity_type='customers',
-- entity_id=cliente, type=CORRESPONDENCE/ALTERNATIVE, is_primary=correspondence):
-- nenhum DDL novo nela, exceto a coluna position, que preserva a ordem da lista do
-- formulario (created_at nao basta: e o inicio da transacao para todas as linhas).
-- A tabela addresses ja tem RLS forcada e register_tenant_table desde V1_1/V3_1, assim
-- como customers e customer_contacts: nenhuma tabela nova, nenhum registro novo.
--
-- billing.* vai para customers, onde due_day ja vive (nao ha tabela de cobranca
-- separada no modelo atual). Os tres flags sao NOT NULL DEFAULT FALSE: o frontend
-- sempre os envia como booleanos, e payloads antigos sem eles leem false.

-- ---------------------------------------------------------------- clientes
ALTER TABLE customers ADD COLUMN IF NOT EXISTS customer_type_id UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS activity_branch_id UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS parent_customer_id UUID;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS document_exemption_reason VARCHAR(300);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS next_month_due BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS advance_invoice BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS block_negativation BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE customers ADD CONSTRAINT fk_customer_parent
    FOREIGN KEY (tenant_id, parent_customer_id) REFERENCES customers(tenant_id, id);

CREATE INDEX IF NOT EXISTS idx_customers_customer_type ON customers(tenant_id, customer_type_id);
CREATE INDEX IF NOT EXISTS idx_customers_parent ON customers(tenant_id, parent_customer_id);

-- ---------------------------------------------------------------- contatos
-- extension e contact_name: o comentario do DTO ja dizia que "ramal e nome do contato
-- nao tem coluna". Passam a ter. label continua sendo o tipo do telefone.
ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS extension VARCHAR(10);
ALTER TABLE customer_contacts ADD COLUMN IF NOT EXISTS contact_name VARCHAR(100);

-- ---------------------------------------------------------------- enderecos
ALTER TABLE addresses ADD COLUMN IF NOT EXISTS position INTEGER;

-- ---------------------------------------------------------------- auditoria
-- document_exemption_reason (texto livre) e contact_name (nome de terceiro) casam com
-- o padrao de dado pessoal do AuditRedactionCoverageTest: sem regra, o build quebra.
-- Seguem o padrao de V3_2 (deleted_reason, label). extension (ramal) nao e pessoal.
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('customers', 'document_exemption_reason', 'REDACT', 'texto livre: pode conter dado pessoal'),
    ('customer_contacts', 'contact_name', 'REDACT', 'nome de terceiro (contato do cliente)')
ON CONFLICT (table_name, column_name) DO NOTHING;
