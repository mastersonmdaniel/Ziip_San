-- Billing Module Migration for Tenant Schema
-- Version: 4.12.0 (P2.34 — migration RESERVADA a esta tarefa no mapa do plano v3.0, §3)
-- Description: cashback de CBS e IBS na fatura (LC 214/2025, arts. 112 a 118;
-- Decreto 12.955/2026, art. 511): lista de domicilios elegiveis da RFB importada
-- com origem, data e versao; registro de cada acesso a lista (padrao G2);
-- devolucao UNICA por domicilio e periodo de afericao como credito na fatura;
-- tax_event_date da conta separado de due_date e de issued_on; efeito
-- tributario por competencia para o pleito de reequilibrio contratual
-- (LC 214/2025, arts. 373 a 377); e o parametro tax.profile (classe b,
-- aprovacao JUR) no catalogo global.
--
-- Depende de: V4_1/V4_14/V4_15 (bills particionada), V3_1 (consumer_units,
-- customers) e V4_5 (fiscal — tabela de aliquotas com vigencia da P2.4, que
-- esta migration NAO duplica nem altera: aliquota nenhuma e criada aqui).
--
-- O QUE ESTA MIGRATION FAZ
-- 1. Lista da RFB: cashback_list_imports (origem, data, versao, base legal,
--    contagem, situacao) e cashback_list_entries (uma linha por domicilio e
--    periodo de afericao). PRIVACIDADE COMO NA V4_2 (tarifa social): o
--    domicilio e identificado SOMENTE por indice cego (hash HMAC-SHA-256,
--    domicile_key_hash) — nenhum CPF/documento em claro entra nestas tabelas.
-- 2. Acesso registrado (G2): cashback_list_accesses grava cada acesso a lista
--    (quem, quando, finalidade, tipo) — importar, consultar elegibilidade,
--    relatar ou conceder devolucao SEM linha de acesso e recusado pelo
--    servico (CashbackService), nunca silencioso.
-- 3. Devolucao: cashback_returns, com a garantia central do aceite na
--    constraint UNIQUE (tenant_id, domicile_key_hash, assessment_month):
--    UMA UNICA devolucao por domicilio no periodo, mesmo quando a familia
--    tem mais de uma unidade — a segunda unidade encontra a devolucao ja
--    concedida em vez de gerar outra. Os valores de tributo (cbs_tax_amount,
--    ibs_tax_amount) sao os resolvidos pelo chamador a partir da tabela com
--    vigencia da P2.4 / itens da conta; as devolucoes sao 100% da CBS e 20%
--    do IBS (LC 214/2025), calculadas por CashbackCalculator.
-- 4. Conta: bills ganha tax_event_date, gravado SEPARADO de due_date e de
--    issued_on, pela regra de fato gerador de operacao continua da
--    LC 227/2026 (art. 10, §3o). ATENCAO — ATIVACAO CONDICIONADA: o texto da
--    regra e conferido antes da ativacao (ver TaxEventDateResolver); a coluna
--    existe desde ja, mas nenhum valor e gravado por regra nao conferida.
--    AVISO DE INTEGRACAO (mesmo achado da V4_7): a V4_14 recria bills com
--    lista explicita de colunas; tax_event_date precisa ser carregado para
--    essa recriacao ou aplicado depois dela, senao se perde.
-- 5. Reequilibrio: cashback_tax_effects agrega, por competencia, o efeito
--    tributario das devolucoes (registro para o pleito dos arts. 373 a 377
--    da LC 214/2025). A SIMULACAO tarifaria e da P2.29 — aqui so o registro.
-- 6. Parametro: tax.profile (OBJECT, classe b, aprovacao JUR) registrado em
--    public.parameter_definitions com os subcampos cashback_enabled,
--    tax_event_date_rule e split_payment_mode. factory_default NULL:
--    ausencia do perfil e erro explicito no motor, nunca perfil presumido.
--    O catalogo de split_payment_mode e o mesmo da conciliacao da P3.1
--    (SplitPaymentReconciler), que aguardava esta definicao.
--
-- PONTO ABERTO ISOLADO (nao bloqueia nada acima): a condicao de contribuinte
-- de IBS/CBS de prestador constituido como AUTARQUIA fica registrada como
-- ponto a confirmar por parecer tributario (ver CashbackOpenPoints). Nenhuma
-- coluna, CHECK ou regra desta migration presume essa condicao.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO: nenhuma lista, domicilio, aliquota ou
-- valor entra por esta migration. Os INSERTs sao so catalogo de parametro,
-- classificacao/redacao de auditoria (precedente: V4_2 e V4_4).

-- ========================================================================
-- 1. LISTA DA RFB IMPORTADA (origem, data e versao)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cashback_list_imports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    -- Proveniencia declarada de cada importacao: origem (a lista e a da RFB,
    -- Decreto 12.955/2026, art. 511), documento/fonte de origem quando houver,
    -- data de importacao e versao da lista. Nada disso tem default: importar
    -- sem proveniencia e recusado (NOT NULL).
    source_origin VARCHAR(100) NOT NULL,
    source_reference VARCHAR(200),
    imported_on DATE NOT NULL,
    list_version VARCHAR(50) NOT NULL,
    legal_basis VARCHAR(200) NOT NULL,
    record_count INT NOT NULL CHECK (record_count >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECEIVED', 'VALIDATED', 'APPLIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cashback_list_imports ADD CONSTRAINT uq_cashback_imports_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_cashback_imports_version ON cashback_list_imports(tenant_id, list_version, imported_on DESC);

-- Uma linha por domicilio elegivel e periodo de afericao, vinda de UMA
-- importacao. assessment_month e o primeiro dia do mes do periodo de
-- afericao (CHECK de dia 1). matched_unit_id e a unidade encontrada para o
-- domicilio, quando o cruzamento a localiza; a devolucao unica NAO depende
-- dela — depende do domicile_key_hash (a familia pode ter varias unidades).
CREATE TABLE IF NOT EXISTS cashback_list_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    import_id UUID NOT NULL,
    domicile_key_hash VARCHAR(64) NOT NULL,
    assessment_month DATE NOT NULL,
    matched_unit_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (EXTRACT(DAY FROM assessment_month) = 1)
);
ALTER TABLE cashback_list_entries ADD CONSTRAINT uq_cashback_entries_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cashback_list_entries ADD CONSTRAINT fk_cashback_entry_import
    FOREIGN KEY (tenant_id, import_id) REFERENCES cashback_list_imports(tenant_id, id);
ALTER TABLE cashback_list_entries ADD CONSTRAINT fk_cashback_entry_unit
    FOREIGN KEY (tenant_id, matched_unit_id) REFERENCES consumer_units(tenant_id, id);
CREATE UNIQUE INDEX idx_cashback_entry_domicile ON cashback_list_entries(tenant_id, import_id, domicile_key_hash, assessment_month);
CREATE INDEX idx_cashback_entry_lookup ON cashback_list_entries(tenant_id, domicile_key_hash, assessment_month);

-- ========================================================================
-- 2. REGISTRO DE ACESSO A LISTA (padrao G2: cada acesso registrado)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cashback_list_accesses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    import_id UUID,
    domicile_key_hash VARCHAR(64),
    accessed_by UUID NOT NULL,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    access_type VARCHAR(30) NOT NULL CHECK (access_type IN ('IMPORT', 'ELIGIBILITY_CHECK', 'REPORT', 'RETURN_GRANT')),
    purpose VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE cashback_list_accesses ADD CONSTRAINT uq_cashback_accesses_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cashback_list_accesses ADD CONSTRAINT fk_cashback_access_import
    FOREIGN KEY (tenant_id, import_id) REFERENCES cashback_list_imports(tenant_id, id);
CREATE INDEX idx_cashback_accesses_import ON cashback_list_accesses(tenant_id, import_id, accessed_at DESC);

-- ========================================================================
-- 3. DEVOLUCAO (credito na fatura; unica por domicilio e periodo)
-- ========================================================================
-- bill_id/bill_reference_month sem FK composta: bills e particionada por
-- reference_month (V4_14) e o vinculo e garantido pelo servico e pelo indice
-- abaixo (mesmo desenho de fiscal_nfage_payments.payment_id na V4_7).

CREATE TABLE IF NOT EXISTS cashback_returns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    domicile_key_hash VARCHAR(64) NOT NULL,
    assessment_month DATE NOT NULL,
    bill_id UUID NOT NULL,
    bill_reference_month DATE NOT NULL,
    list_import_id UUID NOT NULL,
    list_entry_id UUID NOT NULL,
    cbs_tax_amount NUMERIC(14, 2) NOT NULL CHECK (cbs_tax_amount >= 0),
    ibs_tax_amount NUMERIC(14, 2) NOT NULL CHECK (ibs_tax_amount >= 0),
    cbs_return_amount NUMERIC(14, 2) NOT NULL CHECK (cbs_return_amount >= 0),
    ibs_return_amount NUMERIC(14, 2) NOT NULL CHECK (ibs_return_amount >= 0),
    total_return_amount NUMERIC(14, 2) NOT NULL CHECK (total_return_amount >= 0),
    tax_event_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GRANTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (EXTRACT(DAY FROM assessment_month) = 1),
    CHECK (total_return_amount = cbs_return_amount + ibs_return_amount),
    CHECK (cbs_return_amount <= cbs_tax_amount),
    CHECK (ibs_return_amount <= ibs_tax_amount)
);
ALTER TABLE cashback_returns ADD CONSTRAINT uq_cashback_returns_tenant_id UNIQUE (tenant_id, id);
-- A garantia do aceite: uma unica devolucao por domicilio no periodo.
ALTER TABLE cashback_returns ADD CONSTRAINT uq_cashback_return_domicile_period
    UNIQUE (tenant_id, domicile_key_hash, assessment_month);
ALTER TABLE cashback_returns ADD CONSTRAINT fk_cashback_return_import
    FOREIGN KEY (tenant_id, list_import_id) REFERENCES cashback_list_imports(tenant_id, id);
ALTER TABLE cashback_returns ADD CONSTRAINT fk_cashback_return_entry
    FOREIGN KEY (tenant_id, list_entry_id) REFERENCES cashback_list_entries(tenant_id, id);
CREATE INDEX idx_cashback_returns_bill ON cashback_returns(tenant_id, bill_id);
CREATE INDEX idx_cashback_returns_competence ON cashback_returns(tenant_id, bill_reference_month);

-- ========================================================================
-- 4. CONTA: tax_event_date separado de due_date e de issued_on
-- ========================================================================

ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_event_date DATE;
COMMENT ON COLUMN bills.tax_event_date IS
    'Data do fato gerador (LC 227/2026, art. 10, §3o — operacao continua), separada de due_date e de issued_on. Gravada somente com a regra conferida antes da ativacao (P2.34); NULL enquanto a ativacao nao ocorre.';

-- ========================================================================
-- 5. EFEITO TRIBUTARIO POR COMPETENCIA (registro p/ reequilibrio, LC 214/2025 arts. 373-377)
-- ========================================================================

CREATE TABLE IF NOT EXISTS cashback_tax_effects (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    competence_month DATE NOT NULL,
    total_cbs_returned NUMERIC(14, 2) NOT NULL CHECK (total_cbs_returned >= 0),
    total_ibs_returned NUMERIC(14, 2) NOT NULL CHECK (total_ibs_returned >= 0),
    total_returned NUMERIC(14, 2) NOT NULL CHECK (total_returned >= 0),
    returns_count INT NOT NULL CHECK (returns_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (EXTRACT(DAY FROM competence_month) = 1),
    CHECK (total_returned = total_cbs_returned + total_ibs_returned)
);
ALTER TABLE cashback_tax_effects ADD CONSTRAINT uq_cashback_tax_effects_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE cashback_tax_effects ADD CONSTRAINT uq_cashback_tax_effect_competence
    UNIQUE (tenant_id, competence_month);

-- ========================================================================
-- 6. PARAMETRO tax.profile NO CATALOGO GLOBAL (classe b, aprovacao JUR)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('tax.profile', 'OBJECT', 'b', NULL,
     'LC 214/2025, arts. 112 a 118 e 373 a 377; Decreto 12.955/2026, art. 511; LC 227/2026, art. 10, §3o',
     'JUR',
     'Perfil tributario do tenant para o cashback de CBS/IBS: objeto {cashback_enabled (booleano), tax_event_date_rule (regra do fato gerador), split_payment_mode (modo do split payment, cuja conciliacao e da P3.1)}. Classe b, aprovacao JUR. Sem padrao de fabrica: perfil ausente e erro explicito. As aliquotas NAO vivem aqui: ficam na tabela com vigencia da P2.4 (V4_5). A regra CONTINUOUS_OPERATION so e ativada depois da conferencia do texto da LC 227/2026, art. 10, §3o.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('tax.profile', 'ALLOWED_VALUES',
     '{"tax_event_date_rule": ["CONTINUOUS_OPERATION"], "split_payment_mode": ["SEGREGATED_AT_SETTLEMENT", "SIMPLIFIED_SEGREGATION", "NO_SEGREGATION"]}',
     'P2.34 (GES-13, RAS-02); modos de split alinhados a conciliacao da P3.1 (RAS-09)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'tax_event_date_rule: CONTINUOUS_OPERATION e a regra de operacao continua da LC 227/2026, art. 10, §3o; a ativacao depende da conferencia do texto antes do uso. split_payment_mode: catalogo aguardado pela P3.1; a conciliacao em si e da P3.1, aqui so o parametro existe.')
ON CONFLICT DO NOTHING;

-- ========================================================================
-- 7. ISOLAMENTO, CLASSIFICACAO E REDACAO DE AUDITORIA
-- ========================================================================

SELECT register_tenant_table('cashback_list_imports');
SELECT register_tenant_table('cashback_list_entries');
SELECT register_tenant_table('cashback_list_accesses');
SELECT register_tenant_table('cashback_returns');
SELECT register_tenant_table('cashback_tax_effects');

-- Indices cegos nunca aparecem na trilha de auditoria: ligariam registros
-- do mesmo domicilio (mesma razao da V4_2 para family_code_hash).
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('cashback_list_entries', 'domicile_key_hash', 'OMIT', 'blind index of the domicile key: links records'),
    ('cashback_list_accesses', 'domicile_key_hash', 'OMIT', 'blind index of the domicile key: links records'),
    ('cashback_returns', 'domicile_key_hash', 'OMIT', 'blind index of the domicile key: links records')
ON CONFLICT (table_name, column_name) DO NOTHING;
