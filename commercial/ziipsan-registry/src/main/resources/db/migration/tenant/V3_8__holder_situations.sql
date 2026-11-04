-- ============================================================================
-- P1.41 (camada D) — Cadastro: situações do titular e validação externa.
-- Migration V3_8 do registry (o gap reservado desde P0; V3_7 existe, V3_9–V3_13
-- existem, V3_11 é da P1.12, V3_12/V4_13 são da camada C).
--
-- RESTRIÇÃO DE ORDENAÇÃO (ler antes de tocar neste arquivo): o histórico de
-- tenant é ÚNICO com ordenação global por versão (TenantMigrator, outOfOrder).
-- Num fresh install, esta migration roda DEPOIS da V3_7 e ANTES da V3_9–V3_13;
-- num banco já em 3b0badd, ela roda DEPOIS da V3_13 via outOfOrder. Por isso:
--   * SÓ referencia objetos que existem em <= V3_7 (na prática, tabelas da
--     V3_1: customers, consumer_units, unit_customers) ou cria os próprios.
--   * NÃO há FK para service_orders (V9_1, posterior): o padrão é o da V3_12
--     (coluna service_order_id sem FK; as FKs chegam numa V9_x posterior,
--     nunca por reescrita de migration aplicada — a V9_3 já existe e NÃO é
--     tocada aqui).
--   * NÃO há FK nem referência a unit_link_end_reasons, relation_roles ou
--     cadastral_change_requests (todas da V3_12, posterior no fresh install).
--     O motivo de fim de vínculo por falecimento e a existência do catálogo
--     são validados pelo SERVIÇO em runtime (tenant totalmente migrado), não
--     pelo banco nesta migration.
--   * NÃO usa cadastral_change_requests para as pendências de ocupação e de
--     nome social: o CHECK de status daquela tabela (V3_12) não tem REJECTED,
--     e esta migration roda antes dela no fresh install — não há como
--     estendê-lo daqui. As pendências vivem em holder_change_requests (§7),
--     com o mesmo formato (antes/depois + OS) e estado REJECTED próprio.
--   * Tudo é defensivo (IF NOT EXISTS / blocos DO com pg_constraint / ON
--     CONFLICT DO NOTHING), para o fresh install e o upgrade passarem iguais.
--
-- O QUE ESTA MIGRATION FAZ (plano v3.0 §3, V3_8, e §2.2):
--   1. customers.social_name (só PF; nome civil continua no documento fiscal).
--   2. consumer_units.occupancy_status (OCCUPIED/VACANT/UNKNOWN) + backfill:
--      com usuário vigente -> OCCUPIED; sem usuário vigente -> VACANT (é a
--      regra do plano para as unidades carregadas sem titular pela P1.2).
--      VACANT só sem usuário vigente: trigger de backstop (§2).
--   3. unit_customers.provisional/provisional_kind (responsável provisório
--      da sucessão: HEIR, ESTATE_ADMINISTRATOR, OCCUPANT).
--   4. customer_representations (representante legal e terceiro autorizado,
--      com escopo = poderes, validade e documento comprobatório obrigatório).
--   5. holder_death_alerts (alerta de óbito: abre OS HOLDER_DECEASED para
--      revisão humana; NUNCA gera efeito automático — o efeito só existe
--      quando um revisor humano confirma, no serviço).
--   6. customer_document_validations (resultado da validação externa de CPF
--      e CNPJ: SÓ situação e data guardadas — nunca o número do documento,
--      que continua cifrado em customers; base legal LGPD art. 7º).
--   7. holder_change_requests (pendências de ocupação e de nome social).
--   8. customer_merges / customer_splits (fusão e separação, com prévia e
--      snapshot para a OS inversa via reverses_order_id; preview e snapshot
--      levam SÓ identificadores, contagens, códigos e datas — nunca nomes
--      nem documentos, nem mascarados — para nada pessoal vazar no audit_log
--      através do JSONB).
--   9. Definições de parâmetros do tenant no catálogo global (classe b/d do
--      plano; valores lidos de public.tenant_settings pelo serviço, com os
--      padrões de fábrica abaixo quando o tenant não definiu nada).
--
-- O QUE ESTA MIGRATION NÃO FAZ (de propósito, com o dono no relatório):
--   * NÃO remove unit_customers.role (espelho do system_role): a V3_12
--     anunciou a remoção como "trabalho da P1.41", mas ela exigiria reescrever
--     as regras de consistência e os testes E2E na mesma frente — fora do
--     corte desta entrega. O espelho continua sendo gravado junto (serviço).
--   * NÃO semeia catálogos de domínio (unit_link_end_reasons etc.): catálogo
--     é política do prestador (regra da V3_1/V3_12). O tenant cria a linha
--     DECEASED (ou outra) e a informa na confirmação do óbito.
--   * NÃO cria cliente SIRC nem gate de natureza jurídica: reg.deceased_check
--     é registrado, mas a consulta ao Sirc (só tenant de direito público, em
--     pedido motivado, Decreto 9.929/2019 art. 7º) é frente de integração
--     futura — a fonte SIRC existe como valor reservado, sem produtor.
--   * NÃO religa protocolos, débitos nem contas do portal na fusão: esses
--     módulos (P1.36, P2.5, P3.3) ainda não existem nesta camada. A prévia
--     lista essas seções como vazias hoje; o serviço as preenche quando os
--     módulos existirem, sem mudar o formato.
--   * NÃO aplica os bloqueios de corte/negativação do titular falecido: são
--     da P3.4 e da ED-30, que consomem o alerta CONFIRMADO daqui.
-- ============================================================================

-- ============================================================================
-- 1. NOME SOCIAL (só PF)
-- ============================================================================

ALTER TABLE customers ADD COLUMN IF NOT EXISTS social_name VARCHAR(200);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_customers_social_name_pf') THEN
        ALTER TABLE customers ADD CONSTRAINT chk_customers_social_name_pf
            CHECK (person_type = 'PF' OR social_name IS NULL);
    END IF;
END $$;

-- ============================================================================
-- 2. OCUPAÇÃO DA UNIDADE + BACKSTOP VACANT-SÓ-SEM-USUÁRIO + BACKFILL
-- ============================================================================

ALTER TABLE consumer_units ADD COLUMN IF NOT EXISTS occupancy_status VARCHAR(10)
    NOT NULL DEFAULT 'UNKNOWN'
    CHECK (occupancy_status IN ('OCCUPIED', 'VACANT', 'UNKNOWN'));

-- VACANT só sem usuário vigente. Backstop do serviço (que dá 422 com o
-- campo): o banco recusa a marcação direta que viole a regra. A recíproca
-- (unidade sem usuário ter de ser VACANT) NÃO é imposta aqui: UNKNOWN é o
-- estado honesto de "ainda não classificada" (ex.: unidade PROVISIONAL
-- recém-criada sem vínculo), e a manutenção por transição de vínculo vive
-- no serviço (ConsumerUnitService.syncCustomers).
CREATE OR REPLACE FUNCTION registry_unit_occupancy_guard() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.occupancy_status = 'VACANT' THEN
        IF EXISTS (SELECT 1 FROM unit_customers uc
                   WHERE uc.tenant_id = NEW.tenant_id AND uc.unit_id = NEW.id
                     AND uc.role = 'USER' AND uc.end_date IS NULL) THEN
            RAISE EXCEPTION 'VACANT unit % still has a current USER link', NEW.id
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_consumer_units_occupancy ON consumer_units;
CREATE TRIGGER trg_consumer_units_occupancy
    BEFORE INSERT OR UPDATE OF occupancy_status ON consumer_units
    FOR EACH ROW EXECUTE FUNCTION registry_unit_occupancy_guard();

-- Backfill (idempotente: roda o mesmo resultado em fresh e em upgrade, e
-- pode ser reaplicado sem mudar nada). Unidades com usuário vigente ->
-- OCCUPIED; sem usuário vigente -> VACANT (plano: as carregadas sem
-- titular pela P1.2 recebem VACANT).
--
-- RLS: o migrador roda SEM app.current_tenant (TenantMigrator: "dentro de
-- uma migration não há contexto de tenant"), e a policy FORÇADA
-- (tenant_id = current_tenant_id()) filtraria TODAS as linhas — o backfill
-- atualizaria 0 linhas em silêncio (provado pelo IT P141MigrationIT: no
-- upgrade com dado legado, tudo saía UNKNOWN). O placeholder tenant_id do
-- Flyway devolve o contexto só para esta transação (SET LOCAL, sem vazar
-- para as migrations seguintes); a V3_12 tem o mesmo risco latente no seu
-- backfill, sem workaround — aqui ele é explícito.
SELECT set_config('app.current_tenant', '${tenant_id}', true);
UPDATE consumer_units u SET occupancy_status =
    CASE WHEN EXISTS (SELECT 1 FROM unit_customers uc
                      WHERE uc.tenant_id = u.tenant_id AND uc.unit_id = u.id
                        AND uc.role = 'USER' AND uc.end_date IS NULL)
         THEN 'OCCUPIED' ELSE 'VACANT' END;

-- ============================================================================
-- 3. RESPONSÁVEL PROVISÓRIO NO VÍNCULO (sucessão por falecimento)
-- ============================================================================

ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS provisional BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE unit_customers ADD COLUMN IF NOT EXISTS provisional_kind VARCHAR(20)
    CHECK (provisional_kind IN ('HEIR', 'ESTATE_ADMINISTRATOR', 'OCCUPANT'));

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_ucust_provisional_kind') THEN
        ALTER TABLE unit_customers ADD CONSTRAINT chk_ucust_provisional_kind
            CHECK (provisional_kind IS NULL OR provisional);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ucust_provisional ON unit_customers(tenant_id, unit_id)
    WHERE provisional AND end_date IS NULL;

-- ============================================================================
-- 4. REPRESENTAÇÕES (representante legal e terceiro autorizado)
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_representations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    represented_id UUID NOT NULL,
    representative_id UUID NOT NULL,
    type VARCHAR(25) NOT NULL CHECK (type IN ('ATTORNEY', 'GUARDIAN', 'CURATOR',
        'CONDO_MANAGER', 'ESTATE_ADMINISTRATOR', 'LEGAL_REPRESENTATIVE', 'AUTHORIZED_THIRD_PARTY')),
    powers TEXT[] NOT NULL,
    unit_id UUID,
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE,
    -- Documento comprobatório obrigatório: referência ao documento no
    -- pipeline da P1.20 (nunca o conteúdo). REDACT na auditoria (§10).
    proof_document_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'EXPIRED', 'REVOKED', 'REJECTED')),
    -- SEM FK (V9_1 ordena depois da V3_8): ver o cabeçalho.
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (represented_id <> representative_id),
    CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CHECK (cardinality(powers) >= 1)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_representation_represented') THEN
        ALTER TABLE customer_representations ADD CONSTRAINT fk_representation_represented
            FOREIGN KEY (tenant_id, represented_id) REFERENCES customers(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_representation_representative') THEN
        ALTER TABLE customer_representations ADD CONSTRAINT fk_representation_representative
            FOREIGN KEY (tenant_id, representative_id) REFERENCES customers(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_representation_unit') THEN
        ALTER TABLE customer_representations ADD CONSTRAINT fk_representation_unit
            FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_representation_powers') THEN
        ALTER TABLE customer_representations ADD CONSTRAINT chk_representation_powers
            CHECK (powers <@ ARRAY['VIEW', 'REQUEST', 'PAY', 'CHANGE_BILLING', 'OWNERSHIP']);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_representations_represented
    ON customer_representations(tenant_id, represented_id, status);
CREATE INDEX IF NOT EXISTS idx_representations_representative
    ON customer_representations(tenant_id, representative_id, status);

-- ============================================================================
-- 5. ALERTAS DE ÓBITO (revisão humana; nenhum efeito automático)
-- ============================================================================

CREATE TABLE IF NOT EXISTS holder_death_alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    unit_id UUID,
    source VARCHAR(25) NOT NULL CHECK (source IN ('EXTERNAL_VALIDATION', 'SIRC', 'MANUAL_REPORT')),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW'
        CHECK (status IN ('PENDING_REVIEW', 'CONFIRMED', 'REJECTED')),
    -- OS HOLDER_DECEASED aberta para a revisão. SEM FK (ver o cabeçalho).
    service_order_id UUID,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_death_alert_customer') THEN
        ALTER TABLE holder_death_alerts ADD CONSTRAINT fk_death_alert_customer
            FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_death_alert_unit') THEN
        ALTER TABLE holder_death_alerts ADD CONSTRAINT fk_death_alert_unit
            FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_death_alerts_customer ON holder_death_alerts(tenant_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_death_alerts_pending ON holder_death_alerts(tenant_id, status)
    WHERE status = 'PENDING_REVIEW';

-- ============================================================================
-- 6. VALIDAÇÃO EXTERNA DE CPF/CNPJ (só situação e data)
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_document_validations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    -- Espécie do documento validado, sem o número (o número continua cifrado
    -- em customers; esta coluna é espécie, não dado pessoal — ver a allowlist
    -- de NOT_PERSONAL no teste de cobertura, mesmo precedente de
    -- legacy_import_staging_units.holder_document_kind).
    document_kind VARCHAR(5) NOT NULL CHECK (document_kind IN ('CPF', 'CNPJ')),
    result VARCHAR(20) NOT NULL CHECK (result IN
        ('REGULAR', 'IRREGULAR', 'NOT_FOUND', 'UNABLE_TO_VERIFY', 'DECEASED')),
    provider VARCHAR(100),
    legal_basis VARCHAR(100) NOT NULL DEFAULT 'LGPD art. 7º',
    -- Omissão do provedor de biometria: FALSE até que um provedor registre
    -- o resultado biométrico. A exigência de biometria na troca de
    -- titularidade (registry.validate_document_online = STATUS_AND_BIOMETRICS)
    -- só é atendida com result REGULAR e biometric_verified TRUE.
    biometric_verified BOOLEAN NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- OS de referência (ex.: a OWNERSHIP_TRANSFER que a validação condicionou).
    -- SEM FK (ver o cabeçalho).
    service_order_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doc_validation_customer') THEN
        ALTER TABLE customer_document_validations ADD CONSTRAINT fk_doc_validation_customer
            FOREIGN KEY (tenant_id, customer_id) REFERENCES customers(tenant_id, id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_doc_validations_customer
    ON customer_document_validations(tenant_id, customer_id, validated_at DESC);

-- ============================================================================
-- 7. PENDÊNCIAS DE OCUPAÇÃO E DE NOME SOCIAL (ver o cabeçalho: por que não
--    cadastral_change_requests)
-- ============================================================================

CREATE TABLE IF NOT EXISTS holder_change_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('OCCUPANCY_CHANGE', 'SOCIAL_NAME_CHANGE')),
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('CUSTOMER', 'CONSUMER_UNIT')),
    target_id UUID NOT NULL,
    old_value TEXT,
    new_value TEXT,
    -- OS do pedido (OCCUPANCY_CHANGE ou CUSTOMER_DATA_UPDATE). SEM FK.
    service_order_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX IF NOT EXISTS idx_holder_changes_target
    ON holder_change_requests(tenant_id, target_type, target_id);

-- ============================================================================
-- 8. FUSÃO E SEPARAÇÃO (com OS inversa via reverses_order_id)
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_merges (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_customer_id UUID NOT NULL,
    target_customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REVERSED', 'REJECTED')),
    -- OS CUSTOMER_MERGE do pedido. SEM FK (ver o cabeçalho).
    service_order_id UUID,
    -- OS inversa (CUSTOMER_MERGE com reverses_order_id = service_order_id).
    -- SEM FK (ver o cabeçalho).
    reversed_by_service_order_id UUID,
    -- Motivo de fim de vínculo (unit_link_end_reasons, V3_12 — sem FK aqui
    -- pela ordenação; validado pelo serviço em runtime) usado quando um
    -- vínculo da origem não pode ser movido por conflito de sobreposição e
    -- precisa ser encerrado em vez de religado.
    merge_end_reason_id UUID,
    -- Prévia (o que será religado) e snapshot (estado anterior, para a
    -- reversa): SÓ identificadores, contagens, códigos e datas.
    preview JSONB NOT NULL DEFAULT '{}'::jsonb,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (source_customer_id <> target_customer_id)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_merge_source') THEN
        ALTER TABLE customer_merges ADD CONSTRAINT fk_merge_source
            FOREIGN KEY (tenant_id, source_customer_id) REFERENCES customers(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_merge_target') THEN
        ALTER TABLE customer_merges ADD CONSTRAINT fk_merge_target
            FOREIGN KEY (tenant_id, target_customer_id) REFERENCES customers(tenant_id, id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS customer_splits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    source_customer_id UUID NOT NULL,
    new_customer_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL'
        CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REVERSED', 'REJECTED')),
    -- OS CUSTOMER_SPLIT do pedido. SEM FK (ver o cabeçalho).
    service_order_id UUID,
    -- OS inversa (CUSTOMER_SPLIT com reverses_order_id = service_order_id).
    reversed_by_service_order_id UUID,
    -- Vínculos a mover (ids) na prévia; estado anterior no snapshot.
    preview JSONB NOT NULL DEFAULT '{}'::jsonb,
    snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (source_customer_id <> new_customer_id)
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_split_source') THEN
        ALTER TABLE customer_splits ADD CONSTRAINT fk_split_source
            FOREIGN KEY (tenant_id, source_customer_id) REFERENCES customers(tenant_id, id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_split_new') THEN
        ALTER TABLE customer_splits ADD CONSTRAINT fk_split_new
            FOREIGN KEY (tenant_id, new_customer_id) REFERENCES customers(tenant_id, id);
    END IF;
END $$;

-- ============================================================================
-- 9. DEFINIÇÕES DE PARÂMETROS DO TENANT (catálogo global, core V3)
--    Inserção idempotente por tenant (ON CONFLICT DO NOTHING): a migration
--    de tenant roda uma vez por tenant, mas o catálogo é global.
--    Os VALORES por tenant vivem em public.tenant_settings (transição) ou
--    em parameter_versions (P1.6); o serviço lê tenant_settings com os
--    padrões de fábrica abaixo quando não há valor.
-- ============================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('registry.deceased_holder_policy', 'TEXT', 'b', '"CONTINUE_SERVICE_SUSPEND_CHARGES"',
     'P1.41; ADR-0042',
     NULL,
     'Política do titular falecido. Padrão protetivo: continuidade do serviço com suspensão de encargos e de negativação por período definido pelo tenant. Os bloqueios de corte e de negativação são aplicados pela P3.4 e pela ED-30, que consomem o alerta CONFIRMADO da P1.41; o domínio de valores além do padrão de fábrica é decisão dessas frentes, não inventado aqui.'),
    ('registry.vacant_unit_holder', 'ENUM', 'b', '"NO_HOLDER_BILL_HELD"',
     'P1.41; decisão do dono 29/10/2026 (§2.2)',
     NULL,
     'Unidade consumidora sem usuário vigente. Valores do plano: OWNER_AS_PROVISIONAL_HOLDER (proprietário como titular provisório), LAST_USER_UNTIL_NOTICE (último usuário enquanto não houver aviso) e NO_HOLDER_BILL_HELD (sem titular, com fatura retida até definir o responsável). Padrão de fábrica NO_HOLDER_BILL_HELD, o mais protetivo.'),
    ('registry.representation_proof', 'TEXT', 'd', '"REQUIRED"',
     'P1.41',
     NULL,
     'Exigência de documento comprobatório da representação. O plano exige o documento (obrigatório); esta chave registra a exigência. As espécies de prova aceitas são política do tenant, sem domínio fixado no plano.'),
    ('registry.social_name_display', 'ENUM', 'd', '"SOCIAL_EVERYWHERE_EXCEPT_FISCAL"',
     'P1.41; P2.17',
     NULL,
     'Exibição do nome social (só PF). Padrão protetivo: nome social em toda interação, exceto documento fiscal (onde vale o nome civil). Alternativa: CIVIL_NAME_ONLY. A conta (fatura) mostra o nome social pela P2.17; o documento fiscal próprio (P2.8) mantém o civil.'),
    ('registry.validate_document_online', 'ENUM', 'd', '"NONE"',
     'P1.41',
     NULL,
     'Validação externa de documento. NONE (nenhuma), STATUS_ONLY (só situação) ou STATUS_AND_BIOMETRICS (situação e biometria, nas operações de risco: troca de titularidade, tarifa social). Resultado irregular gera alerta, não bloqueio; sem validação REGULAR registrada, a troca de titularidade é recusada quando o modo não é NONE.'),
    ('reg.cpf_validation_provider', 'TEXT', 'd', NULL,
     'P1.41',
     NULL,
     'Provedor da validação externa de CPF (e, por extensão, de CNPJ). Sem provedor configurado, nenhuma consulta externa é feita; a credencial vive no cofre e a consulta é paga por tenant com custo medido (frente de integração futura — esta frente só registra o resultado).'),
    ('reg.deceased_check', 'TEXT', 'd', '"DISABLED"',
     'P1.41; ADR-0042; Decreto 9.929/2019, art. 7º',
     NULL,
     'Verificação de óbito. DISABLED por padrão. A consulta ao Sirc é só para tenant de direito público, em pedido motivado; qualquer resultado de óbito abre OS interna de titular falecido para revisão humana, sem efeito automático — o cliente Sirc em si é frente de integração futura.'),
    ('portal.delegated_access', 'BOOLEAN', 'b', 'false',
     'P1.41; P3.3',
     NULL,
     'Acesso delegado no portal (representação da P1.41). Desligado por padrão para operações que alteram faturamento; a aplicação no portal é da P3.3, que consome escopo e validade daqui.'),
    ('bill.holder_name_display', 'ENUM', 'b', '"USER_HOLDER_ONLY"',
     'P1.3 (decisão do dono 6); P1.41 (§2.2); NR 11, art. 33, §2º',
     NULL,
     'Nome na conta. USER_HOLDER_ONLY (só o usuário titular, padrão de fábrica) ou USER_HOLDER_AND_OWNER (titular e proprietário). A regra vive em BillHolderResolver desde a P1.3; o registro no catálogo chega nesta frente, que detém a decisão §2.2.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('registry.vacant_unit_holder', 'ALLOWED_VALUES',
     '["OWNER_AS_PROVISIONAL_HOLDER", "LAST_USER_UNTIL_NOTICE", "NO_HOLDER_BILL_HELD"]',
     'P1.41 (§2.2)', NULL, false, false, false, false,
     'Domínio fixado no plano; fora dele, o valor é recusado pela tela do tenant (P1.6).'),
    ('registry.social_name_display', 'ALLOWED_VALUES',
     '["SOCIAL_EVERYWHERE_EXCEPT_FISCAL", "CIVIL_NAME_ONLY"]',
     'P1.41', NULL, false, false, false, false,
     'O segundo valor é a alternativa documentada no relatório da frente (o plano fixa só o padrão); sem ele, o tenant não teria como optar pelo civil.'),
    ('registry.validate_document_online', 'ALLOWED_VALUES',
     '["NONE", "STATUS_ONLY", "STATUS_AND_BIOMETRICS"]',
     'P1.41', NULL, false, false, false, false,
     'Domínio fixado no plano (nenhuma; situação; situação e biometria).'),
    ('bill.holder_name_display', 'ALLOWED_VALUES',
     '["USER_HOLDER_ONLY", "USER_HOLDER_AND_OWNER"]',
     'P1.3 (decisão do dono 6)', NULL, false, false, false, false,
     'Domínio fixado na decisão do dono.')
ON CONFLICT (definition_key, limit_kind, COALESCE(applies_to, '')) DO NOTHING;

-- ============================================================================
-- 10. ISOLAMENTO (RLS FORCE + policy + trigger de auditoria, padrão V3_1),
--     E REDAÇÃO (padrão V3_2/V3_4; a R__ data_classification deriva a
--     classificação destas regras)
-- ============================================================================

SELECT register_tenant_table('customer_representations');
SELECT register_tenant_table('holder_death_alerts');
SELECT register_tenant_table('customer_document_validations');
SELECT register_tenant_table('holder_change_requests');
SELECT register_tenant_table('customer_merges');
SELECT register_tenant_table('customer_splits');

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('customers', 'social_name', 'REDACT', 'nome social (dado pessoal)'),
    ('customer_representations', 'proof_document_id', 'REDACT',
     'referência ao documento comprobatório da representação'),
    ('holder_change_requests', 'old_value', 'REDACT',
     'valor anterior (nome social é dado pessoal)'),
    ('holder_change_requests', 'new_value', 'REDACT',
     'valor novo (nome social é dado pessoal)')
ON CONFLICT (table_name, column_name) DO NOTHING;
