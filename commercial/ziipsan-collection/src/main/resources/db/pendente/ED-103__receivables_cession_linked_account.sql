-- ED-103 Antecipacao de recebiveis e conta vinculada — MECANICA (decisao 62
-- do dono, 10/11/2026: so a mecanica; a logistica/forma juridica e do
-- cliente e fica FORA do software). Produtor (execucao ED-103, decisao 62):
-- sessao 66676ee1-5190-4d31-b6f5-21ed3322dfe5.
-- Ficheiro PENDENTE DE NUMERACAO Flyway: segue a convencao viva do modulo
-- ziipsan-collection (src/main/resources/db/pendente/ED-NNN__*.sql, como
-- ed27__debit_account_integration.sql; precedentes ED-102/ED-104 na raiz
-- db/pendente dos seus modulos) — NENHUM numero de versao e inventado e a
-- lista de migrations publicadas NAO e alterada por este pacote; a
-- numeracao global atribui-se no pouso/integracao.
-- Escopo: SO a mecanica. A forma juridica da operacao (FIDC, cessao
-- fiduciaria, conta vinculada) NAO esta modelada: destination_label e o
-- destino DECLARADO pelo cliente na exportacao/reserva. Leiautes oficiais
-- de banco/fundo/registradora = [SEM FONTE] — nenhum leiaute e presumido.
-- NENHUM INSERT neste ficheiro: nada e semeado.

-- E4: registo de cessao de um recebivel (um ciclo de vida por linha).
-- Estados: RESERVADO, CEDIDO, LIQUIDADO, LIBERTADO, REVERTIDO.
-- Activo (trava ligada) = RESERVADO ou CEDIDO.
CREATE TABLE IF NOT EXISTS cession_registrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    receivable_id VARCHAR(120) NOT NULL CHECK (NULLIF(btrim(receivable_id),'') IS NOT NULL),
    debtor_key VARCHAR(200) NOT NULL CHECK (NULLIF(btrim(debtor_key),'') IS NOT NULL),
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    destination_label VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(destination_label),'') IS NOT NULL),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVADO','CEDIDO','LIQUIDADO','LIBERTADO','REVERTIDO')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cession_registrations_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cession_registrations');

-- TRAVA DE DUPLA CESSAO (E4): no maximo UM registo activo por recebivel
-- e tenant — o desenho SQL da mesma garantia que a loja em memoria impoe
-- atomicamente (tryCreateActive) e que a prova de concorrencia exercita.
CREATE UNIQUE INDEX IF NOT EXISTS uq_cession_registrations_active_receivable
    ON cession_registrations (tenant_id, receivable_id)
    WHERE status IN ('RESERVADO','CEDIDO');

-- E4: eventos de transicao do registo (a reversao de CEDIDO exige motivo:
-- reason e obrigatorio por CHECK quando o destino e REVERTIDO).
CREATE TABLE IF NOT EXISTS cession_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    registration_id UUID NOT NULL,
    from_status VARCHAR(20) CHECK (from_status IN ('RESERVADO','CEDIDO','LIQUIDADO','LIBERTADO','REVERTIDO')),
    to_status VARCHAR(20) NOT NULL CHECK (to_status IN ('RESERVADO','CEDIDO','LIQUIDADO','LIBERTADO','REVERTIDO')),
    actor VARCHAR(200) NOT NULL CHECK (NULLIF(btrim(actor),'') IS NOT NULL),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cession_events_registration FOREIGN KEY (tenant_id, registration_id)
        REFERENCES cession_registrations (tenant_id, id),
    CONSTRAINT ck_cession_events_reversal_reason CHECK (to_status <> 'REVERTIDO' OR NULLIF(btrim(reason),'') IS NOT NULL)
);
SELECT register_tenant_table('cession_events');

-- E3: trilha de auditoria de exportacao da carteira (quem, quando,
-- ambito/filtros, destino declarado, hash do conteudo gerado).
CREATE TABLE IF NOT EXISTS cession_export_audits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    actor VARCHAR(200) NOT NULL CHECK (NULLIF(btrim(actor),'') IS NOT NULL),
    exported_at TIMESTAMPTZ NOT NULL,
    scope VARCHAR(500) NOT NULL CHECK (NULLIF(btrim(scope),'') IS NOT NULL),
    destination VARCHAR(300) NOT NULL CHECK (NULLIF(btrim(destination),'') IS NOT NULL),
    format VARCHAR(10) NOT NULL CHECK (format IN ('CSV','JSON')),
    content_hash CHAR(64) NOT NULL CHECK (NULLIF(btrim(content_hash),'') IS NOT NULL),
    line_count INTEGER NOT NULL CHECK (line_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cession_export_audits_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cession_export_audits');

-- E5: movimentos importados da conta vinculada (formato generico
-- declarado; leiaute real = [SEM FONTE]).
CREATE TABLE IF NOT EXISTS cession_linked_account_movements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    reference VARCHAR(120) NOT NULL CHECK (NULLIF(btrim(reference),'') IS NOT NULL),
    movement_date DATE NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cession_movements_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cession_linked_account_movements');

-- E5: casamentos e divergencias de uma reconciliacao (relatorio
-- persistido; NENHUM ajuste e aplicado por esta tabela — divergencia
-- sinaliza-se, nunca se corrige em silencio).
CREATE TABLE IF NOT EXISTS cession_reconciliation_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    receivable_id VARCHAR(120) NOT NULL CHECK (NULLIF(btrim(receivable_id),'') IS NOT NULL),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('CASADO','SEM_MOVIMENTO','SEM_BAIXA_ESPERADA','VALOR_DIVERGENTE')),
    expected_amount NUMERIC(15,2),
    movement_amount NUMERIC(15,2),
    tolerance NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (tolerance >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cession_reconciliation_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('cession_reconciliation_results');
