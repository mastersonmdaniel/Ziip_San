-- P2.3 -- Particao de meter_readings por mes de referencia e chave de idempotencia.
--
-- O QUE MUDA
--   * meter_readings vira particionada por RANGE (reference), uma particao por mes civil
--     (meter_readings_AAAA_MM). PK (tenant_id, reference, id): a chave de particao
--     precisa estar na PK, e todo indice unico sobre a tabela particionada precisa
--     inclui-la.
--   * idempotency_key VARCHAR(120), NULL por padrao: a chave externa da leitura (ex.:
--     lote de importacao + linha, ou identificador do coletor), para que uma leitura
--     reenviada com a mesma chave nao duplique. A unicidade e por (tenant_id,
--     reference, idempotency_key), com indice parcial (so linhas com chave): o
--     PostgreSQL exige a chave de particao no indice unico, de modo que a mesma chave
--     em meses distintos nao colide -- o escopo natural do reenvio e o mes da leitura.
--     Linhas sem chave (o legado) nao colidem entre si: NULLs sao distintos no indice
--     unico, e o parcial as exclui de qualquer forma.
--   * consumption_records ganha reading_reference DATE (NULL quando reading_id e NULL)
--     e passa a referenciar meter_readings por (tenant_id, reading_reference,
--     reading_id): o PostgreSQL exige a chave de particao na FK para tabela
--     particionada. A coluna e preenchida por trigger a partir da leitura (mesmo
--     padrao da V4_14), de modo que nenhum caminho de escrita existente precisa mudar;
--     um valor divergente da leitura e recusado. reading_reference e deliberadamente
--     distinta de consumption_records.reference: o consumo faturado e de um mes, e a
--     leitura faturada pode, em principio, ser de outro (o trigger copia o mes real
--     da leitura, sem presumir igualdade).
--   * Sem particao DEFAULT, de proposito (mesmo motivo da core V14 e da V4_14): mes
--     sem particao falha em vez de cair num balde sem poda. Quem cria particoes e
--     meter_readings_create_partition (papel do migrador, nunca da app). A migration
--     pre-cria a janela 2026-01 .. mes corrente + 12 meses (mesma janela da V4_14:
--     2026 e o primeiro ano de faturamento do projeto).
--
-- CONVERSAO COM DADOS (bancos de dev ja tem linhas sinteticas)
--   Renomeia a tabela antiga, cria a particionada, cria uma particao para cada mes
--   presente no legado mais a janela, copia e apaga o legado -- tudo na mesma
--   transacao do Flyway. RLS, policy, grants e trigger de auditoria sao recriados
--   depois do DROP do legado (os grants da tabela antiga se perdem na recriacao; o
--   provisionamento concede depois das migrations para tenants novos, mas tenants
--   existentes subem so com o migrador -- precedente da core V14).
--
-- PADRAO: o mesmo da V4_14 (e da core V14 para audit_log), que a ED-17 usa para as
-- series temporais: RANGE no mes de competencia, PK com a chave de particao, sem
-- DEFAULT, janela pre-criada + funcao de criacao por mes, coluna de mes
-- desnormalizada nas filhas com trigger de preenchimento, e recriacao de
-- RLS/policy/grants/trigger apos a conversao. A funcao resolve o schema pelo
-- search_path da chamada: quem chama precisa ter o schema do tenant em primeiro
-- lugar, a mesma exigencia de register_tenant_table.
--
-- NO SEEDED DATA. Nenhuma linha de negocio e criada; so particoes vazias.

-- ======================================================================
-- 1. Gestao de particoes (so o migrador: DDL no schema do tenant)
-- ======================================================================

CREATE OR REPLACE FUNCTION meter_readings_create_partition(p_month DATE)
RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_start DATE := date_trunc('month', p_month)::DATE;
    v_end DATE := (v_start + INTERVAL '1 month')::DATE;
    v_name TEXT := 'meter_readings_' || to_char(v_start, 'YYYY_MM');
BEGIN
    IF to_regclass(v_schema || '.' || v_name) IS NULL THEN
        EXECUTE format('CREATE TABLE %I.%I PARTITION OF %I.meter_readings '
                       || 'FOR VALUES FROM (%L) TO (%L)',
                       v_schema, v_name, v_schema, v_start, v_end);
    END IF;
    RETURN v_name;
END;
$$;

CREATE OR REPLACE FUNCTION meter_readings_ensure_partitions()
RETURNS INT LANGUAGE plpgsql AS $$
DECLARE
    v_schema TEXT := current_schema();
    v_month DATE := DATE '2026-01-01';
    v_last DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '12 months')::DATE;
    v_created INT := 0;
    v_before BOOLEAN;
BEGIN
    WHILE v_month <= v_last LOOP
        v_before := to_regclass(v_schema || '.meter_readings_' || to_char(v_month, 'YYYY_MM')) IS NOT NULL;
        PERFORM meter_readings_create_partition(v_month);
        IF NOT v_before THEN
            v_created := v_created + 1;
        END IF;
        v_month := (v_month + INTERVAL '1 month')::DATE;
    END LOOP;
    RETURN v_created;
END;
$$;

REVOKE ALL ON FUNCTION meter_readings_create_partition(DATE) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION meter_readings_create_partition(DATE) TO ziipsan_migrator;
REVOKE ALL ON FUNCTION meter_readings_ensure_partitions() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION meter_readings_ensure_partitions() TO ziipsan_migrator;

-- ======================================================================
-- 2. Trigger de preenchimento do mes da leitura em consumption_records
-- ======================================================================

CREATE OR REPLACE FUNCTION meter_readings_fill_record_month()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    v_month DATE;
BEGIN
    IF NEW.reading_id IS NULL THEN
        RETURN NEW;
    END IF;
    EXECUTE format('SELECT reference FROM %I.meter_readings WHERE tenant_id = $1 AND id = $2',
                   TG_TABLE_SCHEMA)
        INTO v_month USING NEW.tenant_id, NEW.reading_id;
    IF v_month IS NULL THEN
        RAISE EXCEPTION 'consumption_records: leitura % inexistente', NEW.reading_id;
    END IF;
    IF NEW.reading_reference IS NULL THEN
        NEW.reading_reference := v_month;
    ELSIF NEW.reading_reference <> v_month THEN
        RAISE EXCEPTION 'consumption_records: mes da leitura (%) difere do informado (%)',
            v_month, NEW.reading_reference;
    END IF;
    RETURN NEW;
END;
$$;

-- ======================================================================
-- 3. Conversao da tabela (idempotente: se ja particionada, ignora)
-- ======================================================================

DO $$
DECLARE
    r RECORD;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_partitioned_table WHERE partrelid = 'meter_readings'::regclass) THEN
        RAISE NOTICE 'meter_readings ja particionada; conversao ignorada';
        RETURN;
    END IF;

    -- A FK de consumption_records referencia (tenant_id, id); a nova PK inclui o mes.
    ALTER TABLE consumption_records DROP CONSTRAINT IF EXISTS fk_consumption_reading;

    ALTER TABLE meter_readings RENAME TO meter_readings_legacy;

    -- meter_readings particionada: as mesmas colunas da V5_1, mais idempotency_key,
    -- version e tenant_schema (que register_tenant_table acrescentaria; aqui vao
    -- explicitas para a copia).
    CREATE TABLE meter_readings (
        id UUID NOT NULL DEFAULT uuid_generate_v4(),
        tenant_id UUID NOT NULL,
        unit_id UUID NOT NULL,
        installation_id UUID NOT NULL,
        kind VARCHAR(5) NOT NULL CHECK (kind IN ('WATER', 'WELL')),
        reference DATE NOT NULL CHECK (EXTRACT(DAY FROM reference) = 1),
        read_on DATE NOT NULL,
        reading INT CHECK (reading >= 0),
        reading_status VARCHAR(15) NOT NULL
            CHECK (reading_status IN ('REALIZED', 'NOT_REALIZED', 'CONFIRMED', 'CHANGED')),
        anomaly_id UUID,
        informed_consumption INT CHECK (informed_consumption >= 0),
        source VARCHAR(15) NOT NULL CHECK (source IN ('MANUAL', 'MOBILE', 'COLLECTOR', 'TELEMETRY')),
        reader_id UUID,
        captured_at TIMESTAMPTZ NOT NULL,
        validation_warnings JSONB,
        idempotency_key VARCHAR(120),
        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
        created_by UUID,
        updated_by UUID,
        version BIGINT NOT NULL DEFAULT 0,
        tenant_schema VARCHAR(100),
        CHECK (reading IS NOT NULL OR anomaly_id IS NOT NULL),
        PRIMARY KEY (tenant_id, reference, id)
    ) PARTITION BY RANGE (reference);

    -- Uma particao por mes presente no legado, para a poda continuar valendo
    -- sobre o historico migrado (e nao so sobre o que vem depois).
    FOR r IN SELECT DISTINCT reference AS m FROM meter_readings_legacy LOOP
        PERFORM meter_readings_create_partition(r.m);
    END LOOP;
    PERFORM meter_readings_ensure_partitions();

    -- Copia: o trigger de preenchimento ainda nao existe, e a copia nao precisa
    -- dele (a leitura carrega o proprio mes).
    INSERT INTO meter_readings
        (id, tenant_id, unit_id, installation_id, kind, reference, read_on, reading,
         reading_status, anomaly_id, informed_consumption, source, reader_id,
         captured_at, validation_warnings, created_at, updated_at, created_by,
         updated_by, version, tenant_schema)
    SELECT id, tenant_id, unit_id, installation_id, kind, reference, read_on, reading,
         reading_status, anomaly_id, informed_consumption, source, reader_id,
         captured_at, validation_warnings, created_at, updated_at, created_by,
         updated_by, version, tenant_schema
    FROM meter_readings_legacy;

    DROP TABLE meter_readings_legacy;

    -- Indices da V5_1, recriados sobre a tabela particionada, mais o de
    -- idempotencia (parcial: so linhas com chave; inclui a chave de particao).
    CREATE INDEX idx_readings_unit_reference ON meter_readings(tenant_id, unit_id, kind, reference);
    CREATE INDEX idx_readings_installation ON meter_readings(tenant_id, installation_id);
    CREATE UNIQUE INDEX idx_readings_idempotency ON meter_readings(tenant_id, reference, idempotency_key)
        WHERE idempotency_key IS NOT NULL;

    -- Mes da leitura desnormalizado em consumption_records, com backfill.
    ALTER TABLE consumption_records ADD COLUMN IF NOT EXISTS reading_reference DATE;
    UPDATE consumption_records c SET reading_reference = m.reference
        FROM meter_readings m WHERE m.tenant_id = c.tenant_id AND m.id = c.reading_id;

    -- FKs recriadas: a de consumption_records agora inclui a chave de particao.
    ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_unit
        FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
    ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_installation
        FOREIGN KEY (tenant_id, installation_id) REFERENCES meter_installations(tenant_id, id);
    ALTER TABLE meter_readings ADD CONSTRAINT fk_reading_anomaly
        FOREIGN KEY (tenant_id, anomaly_id) REFERENCES reading_anomalies(tenant_id, id);
    ALTER TABLE consumption_records ADD CONSTRAINT fk_consumption_reading
        FOREIGN KEY (tenant_id, reading_reference, reading_id) REFERENCES meter_readings(tenant_id, reference, id);

    -- Trigger de preenchimento (criado DEPOIS da copia, pelo motivo da V4_14).
    DROP TRIGGER IF EXISTS trg_consumption_fill_reading_month ON consumption_records;
    CREATE TRIGGER trg_consumption_fill_reading_month
        BEFORE INSERT OR UPDATE OF reading_id ON consumption_records
        FOR EACH ROW EXECUTE FUNCTION meter_readings_fill_record_month();

    -- RLS FORCED, policy e trigger de auditoria (o helper e idempotente).
    PERFORM register_tenant_table('meter_readings');

    -- Grants refeitos: a recriacao perde os da tabela antiga (ver cabecalho).
    GRANT SELECT, INSERT, UPDATE, DELETE ON meter_readings TO ziipsan_app;
END;
$$;
