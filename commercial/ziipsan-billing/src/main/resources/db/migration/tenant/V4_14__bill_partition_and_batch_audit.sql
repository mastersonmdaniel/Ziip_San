-- P2.3 -- Particao de bills e bill_items por mes de referencia, com auditoria por lote.
--
-- DESVIO DOCUMENTADO (o plano v3.0, secao 3, reservava a V4_6 para esta particao, e o
-- cabecalho da V4_6 diz que a particao ficaria "neste mesmo arquivo"): a V4_6 JA foi
-- aplicada pela P1.16 (CHECK do total com tributo retido + fundacao do lote). Migration
-- aplicada e imutavel: o Flyway recusa checksum alterado (ver scripts/reset-it-db.sh).
-- Precedente: a V3_6 tambem nao pode ser reutilizada e o conteudo R2 foi entregue na
-- V3_12 (nota de correcao no mapa do plano). A particao chega aqui, na V4_14, a primeira
-- versao livre de billing depois da V4_13; a V5_4 (metering) segue o numero do plano.
-- O plano deve ganhar a mesma nota de correcao (pendencia do relatorio da frente).
--
-- O QUE MUDA
--   * bills vira particionada por RANGE (reference_month), uma particao por mes civil
--     (bills_AAAA_MM); bill_items acompanha (bill_items_AAAA_MM), com a mesma chave.
--   * PK (tenant_id, reference_month, id): a chave de particao precisa estar na PK, e
--     todo indice unico sobre a tabela particionada precisa inclui-la. O indice parcial
--     de idempotencia (uma conta viva por unidade e referencia) ja a incluia.
--   * Filhas de bills (bill_items, bill_debits, bill_credits, bill_taxes) ganham a coluna
--     reference_month e passam a referenciar bills por (tenant_id, reference_month,
--     bill_id): o PostgreSQL exige a chave de particao na FK para tabela particionada.
--     Debito/credito/tributo (tabelas NAO particionadas) tem a coluna preenchida por
--     trigger a partir da conta (secao 3): inserir sem reference_month continua
--     funcionando, e um valor divergente da conta e recusado. EXCECAO DOCUMENTADA
--     (validacao em scratch da P2.3): bill_items E particionada, e numa tabela
--     particionada o roteamento precede o trigger -- inserir item sem reference_month
--     falha com "no partition of relation" ANTES do trigger preencher. O trigger de
--     bill_items, na pratica, so VALIDA um mes informado (divergente, recusado); quem
--     insere item PRECISA informar reference_month = mes da conta. Nenhum codigo Java
--     cria BillItemEntity hoje (so o repository existe; o caminho de escrita do ciclo
--     chega na P2.5), de modo que nenhum caminho existente quebra, mas o contrato e
--     este: item sem mes e erro, nao preenchimento.
--   * bills ganha origin_reference_month, pela mesma razao, para a auto-FK de origem
--     (uma retificacao pode apontar para conta de outro mes): o trigger preenche a
--     partir da conta de origem.
--   * Sem particao DEFAULT, de proposito (mesmo motivo da core V14): mes sem particao
--     falha em vez de cair num balde sem poda, o que esconderia particao faltante e
--     quebraria a poda por lote. Quem cria particoes e bills_create_partition (papel
--     do migrador, nunca da app). A migration pre-cria a janela 2026-01 .. mes corrente
--     + 12 meses: 2026 e o primeiro ano de faturamento do projeto (o plano o trata como
--     ano de teste), e a janela relativa a frente cobre os testes existentes, que usam
--     2026-11, qualquer que seja o mes em que a migration rode. Meses fora da janela
--     exigem bills_create_partition (o teste de particao da P2.3 prova o caminho).
--   * Auditoria por lote (fundacao da V4_6, aqui completada): todo lote (bill_audit_batches,
--     um run_id por mes) tem suas contas ligadas por bills.audit_batch_id, e a consulta
--     da trilha de um lote inteiro -- tenant + reference_month do lote + audit_batch_id --
--     e podada para a particao do lote (plano de execucao conferido no teste da P2.3,
--     com 100 mil contas). bills.audit_batch_id continua NULLABLE: a P1 insere contas
--     sem lote (BillingP1MigrationTest) e ainda nao existe caminho de escrita de
--     faturamento em lote; torna-la NOT NULL quebraria esses inserts e pertence ao
--     caminho de escrita do ciclo (P2.5), nao a esta migration (pendencia registrada).
--
-- CONVERSAO COM DADOS (bancos de dev ja tem linhas sinteticas)
--   Renomeia as tabelas antigas, cria as particionadas, cria uma particao para cada
--   mes presente no legado mais a janela, copia e apaga o legado -- tudo na mesma
--   transacao do Flyway. RLS, policy, grants e trigger de auditoria sao recriados
--   depois do DROP do legado, porque os indices antigos ocupam os nomes da V4_1 ate
--   la. Os GRANTs para ziipsan_app sao refeitos aqui (a recriacao perde os grants da
--   tabela antiga; o provisionamento concede depois das migrations para tenants novos,
--   mas tenants existentes subem so com o migrador -- precedente da core V14, que
--   concede sobre public.audit_log recriada).
--
-- PADRAO PARA ED-17 (series temporais): particionar por RANGE no mes de competencia,
-- PK com a chave de particao, sem DEFAULT, janela pre-criada + funcao de criacao por
-- mes, coluna de mes desnormalizada nas filhas com trigger de preenchimento, e
-- recriacao de RLS/policy/grants/trigger apos a conversao. E o mesmo padrao da core
-- V14 (audit_log), adaptado ao schema do tenant (a funcao resolve o schema pelo
-- search_path da chamada: quem chama precisa ter o schema do tenant em primeiro
-- lugar, a mesma exigencia de register_tenant_table).
--
-- NO SEEDED DATA. Nenhuma linha de negocio e criada; so particoes vazias.

-- ======================================================================
-- 1. Gestao de particoes (so o migrador: DDL no schema do tenant)
-- ======================================================================

CREATE OR REPLACE FUNCTION bills_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'bills_' || to_char(v_start, 'YYYY_MM');
    v_item_name TEXT := 'bill_items_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.bills '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    IF to_regclass(v_schema || '.' || v_item_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.bill_items '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_item_name, v_schema, v_start, v_end);
    END IF;
    RETURN v_name;
END;
$$;

CREATE OR REPLACE FUNCTION bills_ensure_partitions()
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_month DATE := DATE '2026-01-01';
    v_last DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '12 months')::DATE;
    v_created INT := 0;
    v_before BOOLEAN;
BEGIN
    WHILE v_month <= v_last LOOP
        v_before := to_regclass(v_schema || '.bills_' || to_char(v_month, 'YYYY_MM')) IS NOT NULL;
        PERFORM bills_create_partition(v_month);
        IF NOT v_before THEN
            v_created := v_created + 1;
        END IF;
        v_month := (v_month + INTERVAL '1 month')::DATE;
    END LOOP;
    RETURN v_created;
END;
$$;

REVOKE ALL ON FUNCTION bills_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bills_create_partition(DATE) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION bills_ensure_partitions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION bills_ensure_partitions() TO ziipsan_migrator;

-- ======================================================================
-- 2. Triggers de preenchimento do mes (filhas e origem)
-- NOTA (P2.3): em bill_items o trigger so valida -- o roteamento da tabela
-- particionada precede o trigger, de modo que um item sem reference_month nunca
-- chega a ser preenchido (falha com "no partition"). Ver o cabecalho.
-- ======================================================================
-- Roda como quem insere (nao SECURITY DEFINER): a consulta a bills passa pela
-- RLS FORCED com o contexto de tenant da sessao, como qualquer leitura da app.
-- TG_TABLE_SCHEMA e o schema da tabela que disparou (particoes ficam no mesmo
-- schema do pai), de modo que o nome nao depende do search_path da sessao.

CREATE OR REPLACE FUNCTION bills_fill_origin_month()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_month DATE;
BEGIN
    IF NEW.origin_bill_id IS NULL THEN
        RETURN NEW;
    END IF;
    EXECUTE format('SELECT reference_month FROM %I.bills WHERE tenant_id = $1 AND id = $2',
                   TG_TABLE_SCHEMA)
        INTO v_month USING NEW.tenant_id, NEW.origin_bill_id;
    IF v_month IS NULL THEN
        RAISE EXCEPTION 'bills: conta de origem % inexistente', NEW.origin_bill_id;
    END IF;
    IF NEW.origin_reference_month IS NULL THEN
        NEW.origin_reference_month := v_month;
    ELSIF NEW.origin_reference_month <> v_month THEN
        RAISE EXCEPTION 'bills: mes da conta de origem (%) difere do informado (%)',
            v_month, NEW.origin_reference_month;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION bills_fill_child_month()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_month DATE;
BEGIN
    EXECUTE format('SELECT reference_month FROM %I.bills WHERE tenant_id = $1 AND id = $2',
                   TG_TABLE_SCHEMA)
        INTO v_month USING NEW.tenant_id, NEW.bill_id;
    IF v_month IS NULL THEN
        RAISE EXCEPTION '%: conta % inexistente', TG_TABLE_NAME, NEW.bill_id;
    END IF;
    IF NEW.reference_month IS NULL THEN
        NEW.reference_month := v_month;
    ELSIF NEW.reference_month <> v_month THEN
        RAISE EXCEPTION '%: mes da conta (%) difere do informado (%)',
            TG_TABLE_NAME, v_month, NEW.reference_month;
    END IF;
    RETURN NEW;
END;
$$;

-- ======================================================================
-- 3. Conversao das tabelas (idempotente: se ja particionada, ignora)
-- ======================================================================

DO $$
DECLARE
    r RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = 'bills'::regclass) THEN
        RAISE NOTICE 'bills ja particionada; conversao ignorada';
        RETURN;
    END IF;

    -- FKs para bills referenciam (tenant_id, id); a nova PK inclui o mes.
    -- As FKs de recebivel tambem sao derrubadas aqui: a secao de recriacao as
    -- re-adiciona abaixo, e sem o DROP o ADD falharia ("already exists") em
    -- qualquer base com a V4_1 aplicada (achado da validacao em scratch da P2.3).
    ALTER TABLE bill_items DROP CONSTRAINT IF EXISTS fk_bitem_bill;
    ALTER TABLE bill_debits DROP CONSTRAINT IF EXISTS fk_bdebit_bill;
    ALTER TABLE bill_debits DROP CONSTRAINT IF EXISTS fk_bdebit_receivable;
    ALTER TABLE bill_credits DROP CONSTRAINT IF EXISTS fk_bcredit_bill;
    ALTER TABLE bill_credits DROP CONSTRAINT IF EXISTS fk_bcredit_receivable;
    ALTER TABLE bill_taxes DROP CONSTRAINT IF EXISTS fk_btax_bill;
    ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bill_origin;
    ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bill_audit_batch;

    ALTER TABLE bills RENAME TO bills_legacy;
    ALTER TABLE bill_items RENAME TO bill_items_legacy;

    -- bills particionada: as mesmas colunas da V4_1 + V4_6 (tax_treatment,
    -- audit_batch_id), mais origin_reference_month, version e tenant_schema
    -- (que register_tenant_table acrescentaria; aqui vao explicitas para a copia).
    CREATE TABLE bills (
        id UUID NOT NULL DEFAULT uuid_generate_v4(),
        tenant_id UUID NOT NULL,
        unit_id UUID NOT NULL,
        reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
        status VARCHAR(30) NOT NULL CHECK (status IN (
            'NORMAL', 'RECTIFIED', 'ADDED', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'INSTALLMENT',
            'CARRIED_FORWARD', 'INSTALLMENT_DOWN_PAYMENT', 'PRESCRIBED_DEBT', 'PRE_BILLED', 'CREDIT_CARD',
            'PROCESSING_ERROR', 'PRESCRIBED_ADDED_BILLS', 'PAID')),
        previous_status VARCHAR(30) CHECK (previous_status IS NULL OR previous_status IN (
            'NORMAL', 'RECTIFIED', 'ADDED', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'INSTALLMENT',
            'CARRIED_FORWARD', 'INSTALLMENT_DOWN_PAYMENT', 'PRESCRIBED_DEBT', 'PRE_BILLED', 'CREDIT_CARD',
            'PROCESSING_ERROR', 'PRESCRIBED_ADDED_BILLS', 'PAID')),
        period_from DATE,
        period_to DATE,
        water_consumption INT CHECK (water_consumption IS NULL OR water_consumption >= 0),
        sewer_volume INT CHECK (sewer_volume IS NULL OR sewer_volume >= 0),
        sewer_pct NUMERIC(7,4),
        collection_pct NUMERIC(7,4),
        water_value NUMERIC(14,2) NOT NULL CHECK (water_value >= 0),
        sewer_value NUMERIC(14,2) NOT NULL CHECK (sewer_value >= 0),
        debits_value NUMERIC(14,2) NOT NULL CHECK (debits_value >= 0),
        credits_value NUMERIC(14,2) NOT NULL CHECK (credits_value >= 0),
        taxes_value NUMERIC(14,2) NOT NULL CHECK (taxes_value >= 0),
        total_value NUMERIC(14,2) NOT NULL CHECK (total_value >= 0),
        due_date DATE,
        original_due_date DATE,
        valid_until DATE,
        issued_on DATE,
        rectification_count INT NOT NULL DEFAULT 0 CHECK (rectification_count >= 0),
        due_change_count INT NOT NULL DEFAULT 0 CHECK (due_change_count >= 0),
        late_fee_charged BOOLEAN NOT NULL DEFAULT FALSE,
        review_reason VARCHAR(100),
        reviewed_on DATE,
        cancel_reason VARCHAR(200),
        origin_bill_id UUID,
        origin_reference_month DATE CHECK (origin_reference_month IS NULL OR EXTRACT(DAY FROM origin_reference_month) = 1),
        tariff_snapshot JSONB,
        completed BOOLEAN NOT NULL DEFAULT FALSE,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        created_by UUID,
        updated_by UUID,
        tax_treatment VARCHAR(20) NOT NULL DEFAULT 'WITHHOLDING_ONLY',
        audit_batch_id UUID,
        version BIGINT NOT NULL DEFAULT 0,
        tenant_schema VARCHAR(100),
        CHECK (period_from IS NULL OR period_to IS NULL OR period_to >= period_from),
        CHECK (total_value = water_value + sewer_value + debits_value - credits_value - taxes_value),
        CHECK (credits_value <= water_value + sewer_value + debits_value),
        CHECK (tax_treatment IN ('WITHHOLDING_ONLY')),
        PRIMARY KEY (tenant_id, reference_month, id)
    ) PARTITION BY RANGE (reference_month);

    -- bill_items particionada: as mesmas colunas da V4_1, mais reference_month.
    CREATE TABLE bill_items (
        id UUID NOT NULL DEFAULT uuid_generate_v4(),
        tenant_id UUID NOT NULL,
        bill_id UUID NOT NULL,
        reference_month DATE NOT NULL CHECK (EXTRACT(DAY FROM reference_month) = 1),
        service_kind VARCHAR(5) NOT NULL CHECK (service_kind IN ('WATER', 'SEWER')),
        category_id UUID NOT NULL,
        subcategory_id UUID,
        tariff_version_id UUID NOT NULL,
        period_from DATE NOT NULL,
        period_to DATE NOT NULL,
        proration_factor NUMERIC(7,4) NOT NULL CHECK (proration_factor > 0 AND proration_factor <= 1),
        line_type VARCHAR(7) NOT NULL CHECK (line_type IN ('MINIMUM', 'BLOCK')),
        economies INT NOT NULL CHECK (economies >= 1),
        social_economies INT NOT NULL DEFAULT 0,
        from_m3 INT NOT NULL CHECK (from_m3 >= 0),
        to_m3 INT,
        price_per_m3 NUMERIC(14,4),
        consumption INT NOT NULL CHECK (consumption >= 0),
        gross_value NUMERIC(14,2) NOT NULL CHECK (gross_value >= 0),
        discount_value NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (discount_value >= 0),
        net_value NUMERIC(14,2) NOT NULL,
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        created_by UUID,
        updated_by UUID,
        version BIGINT NOT NULL DEFAULT 0,
        tenant_schema VARCHAR(100),
        CHECK (period_to >= period_from),
        CHECK (social_economies BETWEEN 0 AND economies),
        CHECK (net_value = gross_value - discount_value),
        CHECK (discount_value <= gross_value),
        CHECK ((line_type = 'BLOCK') = (price_per_m3 IS NOT NULL)),
        PRIMARY KEY (tenant_id, reference_month, id)
    ) PARTITION BY RANGE (reference_month);

    -- Uma particao por mes presente no legado, para a poda continuar valendo
    -- sobre o historico migrado (e nao so sobre o que vem depois).
    FOR r IN SELECT DISTINCT reference_month AS m FROM bills_legacy LOOP
        PERFORM bills_create_partition(r.m);
    END LOOP;
    PERFORM bills_ensure_partitions();

    -- Copia: os triggers de preenchimento ainda nao existem, por isso a copia
    -- preenche as colunas de mes explicitamente (o trigger validaria, nao
    -- preencheria sem contexto de tenant na migration, pois a RLS e FORCED).
    INSERT INTO bills
        (id, tenant_id, unit_id, reference_month, status, previous_status, period_from,
         period_to, water_consumption, sewer_volume, sewer_pct, collection_pct, water_value,
         sewer_value, debits_value, credits_value, taxes_value, total_value, due_date,
         original_due_date, valid_until, issued_on, rectification_count, due_change_count,
         late_fee_charged, review_reason, reviewed_on, cancel_reason, origin_bill_id,
         origin_reference_month, tariff_snapshot, completed, created_at, updated_at,
         created_by, updated_by, tax_treatment, audit_batch_id, version, tenant_schema)
    SELECT b.id, b.tenant_id, b.unit_id, b.reference_month, b.status, b.previous_status,
         b.period_from, b.period_to, b.water_consumption, b.sewer_volume, b.sewer_pct,
         b.collection_pct, b.water_value, b.sewer_value, b.debits_value, b.credits_value,
         b.taxes_value, b.total_value, b.due_date, b.original_due_date, b.valid_until,
         b.issued_on, b.rectification_count, b.due_change_count, b.late_fee_charged,
         b.review_reason, b.reviewed_on, b.cancel_reason, b.origin_bill_id,
         o.reference_month, b.tariff_snapshot, b.completed, b.created_at, b.updated_at,
         b.created_by, b.updated_by, b.tax_treatment, b.audit_batch_id, b.version,
         b.tenant_schema
    FROM bills_legacy b
    LEFT JOIN bills_legacy o ON o.tenant_id = b.tenant_id AND o.id = b.origin_bill_id;

    INSERT INTO bill_items
        (id, tenant_id, bill_id, reference_month, service_kind, category_id,
         subcategory_id, tariff_version_id, period_from, period_to, proration_factor,
         line_type, economies, social_economies, from_m3, to_m3, price_per_m3,
         consumption, gross_value, discount_value, net_value, created_at, updated_at,
         created_by, updated_by, version, tenant_schema)
    SELECT i.id, i.tenant_id, i.bill_id, b.reference_month, i.service_kind, i.category_id,
         i.subcategory_id, i.tariff_version_id, i.period_from, i.period_to,
         i.proration_factor, i.line_type, i.economies, i.social_economies, i.from_m3,
         i.to_m3, i.price_per_m3, i.consumption, i.gross_value, i.discount_value,
         i.net_value, i.created_at, i.updated_at, i.created_by, i.updated_by, i.version,
         i.tenant_schema
    FROM bill_items_legacy i
    JOIN bills b ON b.tenant_id = i.tenant_id AND b.id = i.bill_id;

    DROP TABLE bill_items_legacy;
    DROP TABLE bills_legacy;

    -- Indices da V4_1 + V4_6, recriados sobre as tabelas particionadas. O indice
    -- parcial de idempotencia ja incluia a chave de particao, como exige a tabela
    -- particionada; os demais sao nao-unicos e nao precisam dela.
    CREATE UNIQUE INDEX idx_bills_unit_reference_live ON bills(tenant_id, unit_id, reference_month)
        WHERE status NOT IN ('CANCELLED', 'CANCELLED_BY_RECTIFICATION', 'PROCESSING_ERROR');
    CREATE INDEX idx_bills_unit ON bills(tenant_id, unit_id, reference_month DESC);
    CREATE INDEX idx_bills_reference ON bills(tenant_id, reference_month, status);
    CREATE INDEX idx_bills_audit_batch ON bills(tenant_id, audit_batch_id);
    CREATE INDEX idx_bitem_bill ON bill_items(tenant_id, bill_id);

    -- Mes desnormalizado nas demais filhas de bills, com backfill pelo mes da conta.
    ALTER TABLE bill_debits ADD COLUMN IF NOT EXISTS reference_month DATE;
    ALTER TABLE bill_credits ADD COLUMN IF NOT EXISTS reference_month DATE;
    ALTER TABLE bill_taxes ADD COLUMN IF NOT EXISTS reference_month DATE;
    UPDATE bill_debits d SET reference_month = b.reference_month
        FROM bills b WHERE b.tenant_id = d.tenant_id AND b.id = d.bill_id;
    UPDATE bill_credits c SET reference_month = b.reference_month
        FROM bills b WHERE b.tenant_id = c.tenant_id AND b.id = c.bill_id;
    UPDATE bill_taxes t SET reference_month = b.reference_month
        FROM bills b WHERE b.tenant_id = t.tenant_id AND b.id = t.bill_id;
    ALTER TABLE bill_debits ALTER COLUMN reference_month SET NOT NULL;
    ALTER TABLE bill_credits ALTER COLUMN reference_month SET NOT NULL;
    ALTER TABLE bill_taxes ALTER COLUMN reference_month SET NOT NULL;

    -- FKs recriadas: as de bills agora incluem a chave de particao.
    ALTER TABLE bills ADD CONSTRAINT fk_bill_unit
        FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
    ALTER TABLE bills ADD CONSTRAINT fk_bill_origin
        FOREIGN KEY (tenant_id, origin_reference_month, origin_bill_id) REFERENCES bills(tenant_id, reference_month, id);
    ALTER TABLE bills ADD CONSTRAINT fk_bill_audit_batch
        FOREIGN KEY (tenant_id, audit_batch_id) REFERENCES bill_audit_batches(tenant_id, id);
    ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_bill
        FOREIGN KEY (tenant_id, reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
    ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_version
        FOREIGN KEY (tenant_id, tariff_version_id) REFERENCES tariff_versions(tenant_id, id);
    ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_category
        FOREIGN KEY (tenant_id, category_id) REFERENCES categories(tenant_id, id);
    ALTER TABLE bill_items ADD CONSTRAINT fk_bitem_subcategory
        FOREIGN KEY (tenant_id, subcategory_id) REFERENCES subcategories(tenant_id, id);
    ALTER TABLE bill_debits ADD CONSTRAINT fk_bdebit_bill
        FOREIGN KEY (tenant_id, reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
    ALTER TABLE bill_debits ADD CONSTRAINT fk_bdebit_receivable
        FOREIGN KEY (tenant_id, receivable_id) REFERENCES debit_receivables(tenant_id, id);
    ALTER TABLE bill_credits ADD CONSTRAINT fk_bcredit_bill
        FOREIGN KEY (tenant_id, reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);
    ALTER TABLE bill_credits ADD CONSTRAINT fk_bcredit_receivable
        FOREIGN KEY (tenant_id, receivable_id) REFERENCES credit_receivables(tenant_id, id);
    ALTER TABLE bill_taxes ADD CONSTRAINT fk_btax_bill
        FOREIGN KEY (tenant_id, reference_month, bill_id) REFERENCES bills(tenant_id, reference_month, id);

    -- Triggers de preenchimento (criados DEPOIS da copia, pelo motivo acima).
    DROP TRIGGER IF EXISTS trg_bills_fill_origin_month ON bills;
    CREATE TRIGGER trg_bills_fill_origin_month
        BEFORE INSERT OR UPDATE OF origin_bill_id ON bills
        FOR EACH ROW EXECUTE FUNCTION bills_fill_origin_month();
    DROP TRIGGER IF EXISTS trg_bill_items_fill_month ON bill_items;
    CREATE TRIGGER trg_bill_items_fill_month
        BEFORE INSERT OR UPDATE OF bill_id ON bill_items
        FOR EACH ROW EXECUTE FUNCTION bills_fill_child_month();
    DROP TRIGGER IF EXISTS trg_bill_debits_fill_month ON bill_debits;
    CREATE TRIGGER trg_bill_debits_fill_month
        BEFORE INSERT OR UPDATE OF bill_id ON bill_debits
        FOR EACH ROW EXECUTE FUNCTION bills_fill_child_month();
    DROP TRIGGER IF EXISTS trg_bill_credits_fill_month ON bill_credits;
    CREATE TRIGGER trg_bill_credits_fill_month
        BEFORE INSERT OR UPDATE OF bill_id ON bill_credits
        FOR EACH ROW EXECUTE FUNCTION bills_fill_child_month();
    DROP TRIGGER IF EXISTS trg_bill_taxes_fill_month ON bill_taxes;
    CREATE TRIGGER trg_bill_taxes_fill_month
        BEFORE INSERT OR UPDATE OF bill_id ON bill_taxes
        FOR EACH ROW EXECUTE FUNCTION bills_fill_child_month();

    -- RLS FORCED, policy e trigger de auditoria (o helper e idempotente).
    PERFORM register_tenant_table('bills');
    PERFORM register_tenant_table('bill_items');

    -- Grants refeitos: a recriacao perde os da tabela antiga (ver cabecalho).
    GRANT SELECT, INSERT, UPDATE, DELETE ON bills TO ziipsan_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON bill_items TO ziipsan_app;
END;
$$;
