-- GIS-07 (P3.26) — importador do cadastro tecnico: staging, perfis de
-- mapeamento de camadas, conversao assistida registrada, limpeza
-- topologica com contagens, relatorio ISO 19157 e publicacao somente
-- por pacote IMPORT revisado (GIS-CADASTRO-TECNICO §3.4 e §9.1/§9.2;
-- ADR-0034; ADR-0028 item b; ADR-0033; ED-55).
--
-- NUMERACAO: a spec reservava a V13_7, mas V13_1 a V13_9 ja estao
-- aplicados/entregues (V13_7 = fix da integracao P1c, V13_8 = GIS-03/
-- P2.21, V13_9 = GIS-04/P2.22) e a tarefa paralela P2.23 toma a primeira
-- versao livre seguinte (V13_10). Precedente do projeto (slot ocupado ->
-- proxima versao livre da serie, sem editar migration aplicada): esta
-- migration e a V13_11. Evidencia e coordenacao: RELATORIO-P3.26.md.
--
-- DESENHO:
-- (1) gis_import_profile / gis_cad_layer_map (§9.2): perfil salvo por
--     cliente (tenant) com o mapeamento camada/bloco/texto -> tabela de
--     feicao + tipo (factory_code) ou camada de contexto. Seeds por
--     tenant: CORSAN_ANEXO_A (camadas do §9.2; subconjunto com alvo
--     conferido no catalogo de fabrica semeado, o restante nasce sem
--     alvo para o tenant completar — nada inventado) e GENERICO (vazio).
--     O perfil Saneago NAO e semeado: o §9.2 manda validar a IN 00.0229
--     antes (pendencia registrada no relatorio).
-- (2) gis_import_batch: o lote. Guarda hash SHA-256 do arquivo original
--     e, quando ha conversao assistida (DWG -> DXF pelo ODA File
--     Converter; DGN v8 -> DWG/DXF por ferramenta do cliente ou do
--     Ziip), hash e formato do convertido e a ferramenta declarada.
--     Conversao e ETAPA ASSISTIDA EXTERNA (fora da imagem de producao,
--     ADR-0028 b): esta migration so REGISTRA e VALIDA o resultado —
--     nenhum conversor e executado aqui. UNIQUE (tenant, hash do
--     original): reimportar o mesmo arquivo nao cria lote novo.
-- (3) gis_import_raw: staging (§9.1 etapa 2). Tudo entra aqui — camada,
--     source_ref (EntityHandle do DXF), bloco, rotulo com o texto NAO
--     interpretado, atributos e geometria ja transformada para o SRID
--     4674 — SEM TOCAR no cadastro: nenhuma tabela gis_node/gis_arc/...
--     e escrita pelo staging, pela limpeza ou pela qualidade. A
--     transformacao de SRS (inclusive SAD69/Corrego Alegre pela grade
--     do IBGE) e feita pelo worker/GDAL fora do banco; o banco registra
--     o SRS de origem, quem/como o declarou e a grade declarada, e
--     RECUSA avancar sem SRS ou sem grade quando o datum a exige.
-- (4) Limpeza topologica em conjunto no staging (gis_import_clean,
--     §9.1 etapa 5): ST_MakeValid, duplicatas (mesma camada, geometria
--     e source_ref), segmentos de arco abaixo da tolerancia do lote;
--     contagens ANTES e DEPOIS gravadas no lote. O snap em lote e a
--     quebra nos nos (ST_Node) acontecem na publicacao, pelos gatilhos
--     da GIS-03 sobre as feicoes PLANNED do pacote — ver (6).
-- (5) gis_quality_report: relatorio ISO 19157-1 (§9.1 etapa 6) em JSONB
--     (serializado pela classe Json do core na aplicacao), um por lote,
--     copiado para o quality_report do pacote na publicacao. O PDF do
--     relatorio e pendencia declarada (nenhuma biblioteca de PDF foi
--     conferida no POM do modulo; ver relatorio da tarefa).
-- (6) Publicacao (gis_import_publish, §9.1 etapa 7): cria o pacote
--     gis_change_set type=IMPORT channel=IMPORT em DRAFT, as feicoes
--     PLANNED ligadas ao pacote (capture_method=CAD_IMPORT,
--     source=IMPORT, source_batch_id=lote, source_ref=origem) e os
--     itens INSERT com lifecycle proposto IN_SERVICE, e submete o
--     pacote (SUBMITTED). NADA entra em operacao por aqui: quem aplica
--     e o gis_apply_change_set da GIS-04, apos revisao por OUTRA
--     pessoa. Reimportacao nao duplica: lote repetido e recusado pelo
--     UNIQUE do hash e feicao ja publicada com o mesmo
--     (source_batch_id, source_ref, tabela) e pulada na republicacao.
-- (7) gis_context_feature: camadas de contexto importadas (bairros,
--     quadras, lotes, logradouros — §9.1/GIS-07). Camada PESSOAL
--     (personal_data) exige o metodo de anonimizacao registrado
--     (ADR-0033/§12 item 8: coordenada deslocada dentro da quadra,
--     agregada por quadra ou dado sintetico): CHECK na tabela, no
--     mapa de camadas e guarda no lote recusam sem o metodo.
--
-- CONVENCOES (as mesmas das V13_8/V13_9): toda funcao de gatilho fixa o
-- search_path LOCAL para o schema do tenant (TG_TABLE_SCHEMA); as
-- funcoes chamadas pela api resolvem o schema por public.tenant_catalog
-- via GUC app.current_tenant (fallback: schema corrente); mensagens de
-- erro sem diacriticos; RLS via register_tenant_table.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) Perfis e mapeamento de camadas (§9.2)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_import_profile (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    key TEXT NOT NULL,
    label TEXT NOT NULL,
    origin TEXT NOT NULL DEFAULT 'CUSTOM'
        CHECK (origin IN ('BUILTIN', 'CUSTOM')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_import_profile_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_import_profile_tenant_key UNIQUE (tenant_id, key)
);

SELECT register_tenant_table('gis_import_profile');

CREATE TABLE IF NOT EXISTS gis_cad_layer_map (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    source_layer TEXT NOT NULL,
    source_block TEXT,
    text_pattern TEXT,
    target_kind TEXT NOT NULL CHECK (target_kind IN ('FEATURE', 'CONTEXT')),
    feature_table TEXT CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure',
         'gis_zone', 'gis_poi')),
    feature_type_code TEXT,
    context_kind TEXT CHECK (context_kind IN
        ('NEIGHBORHOOD', 'BLOCK', 'PARCEL', 'STREET')),
    personal_data BOOLEAN NOT NULL DEFAULT FALSE,
    anonymization_method TEXT CHECK (anonymization_method IN
        ('SHIFTED_WITHIN_BLOCK', 'AGGREGATED_BY_BLOCK', 'SYNTHETIC')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_cad_layer_map_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_cad_layer_map_profile
        FOREIGN KEY (tenant_id, profile_id)
        REFERENCES gis_import_profile (tenant_id, id),
    CONSTRAINT ck_gis_cad_layer_map_target
        CHECK ((target_kind = 'FEATURE'
                AND (feature_table IS NULL OR feature_type_code IS NOT NULL))
               OR (target_kind = 'CONTEXT' AND context_kind IS NOT NULL)),
    CONSTRAINT ck_gis_cad_layer_map_personal
        CHECK (NOT personal_data OR anonymization_method IS NOT NULL)
);

SELECT register_tenant_table('gis_cad_layer_map');

CREATE INDEX IF NOT EXISTS idx_gis_cad_layer_map_profile
    ON gis_cad_layer_map (tenant_id, profile_id, source_layer);

-- Seeds por tenant (§9.2). CORSAN_ANEXO_A: a lista de camadas e a do
-- documento; os alvos pre-mapeados usam SOMENTE tipos que o catalogo de
-- fabrica (R__gis_factory_catalog / V13_2, prefixo gis01:) semeia —
-- WATER_MAIN, GRAVITY_SEWER, WATER_SERVICE_LINE, SEWER_SERVICE_LINE,
-- MANHOLE e HYDRANT (verificado no espelho). Camadas sem alvo conferido
-- nascem com feature_table NULL: o tenant completa o mapeamento na tela
-- antes de usar (a guarda de mapeamento recusa publicar linha crua cuja
-- camada FEATURE nao tem alvo).
INSERT INTO gis_import_profile (tenant_id, key, label, origin)
VALUES ('${tenant_id}', 'CORSAN_ANEXO_A', 'Corsan Anexo A (rev. 03/2024)', 'BUILTIN'),
       ('${tenant_id}', 'GENERICO', 'Generico (mapeamento do cliente)', 'BUILTIN')
ON CONFLICT (tenant_id, key) DO NOTHING;

INSERT INTO gis_cad_layer_map
    (tenant_id, profile_id, source_layer, target_kind, feature_table, feature_type_code)
SELECT '${tenant_id}', p.id, m.layer, 'FEATURE', m.tbl, m.code
FROM gis_import_profile p,
     (VALUES
        ('A_REDE', 'gis_arc', 'gis01:WATER_MAIN'),
        ('A_RAMAIS', 'gis_connection', 'gis01:WATER_SERVICE_LINE'),
        ('A_HIDRANTE', 'gis_node', 'gis01:HYDRANT'),
        ('E_REDE', 'gis_arc', 'gis01:GRAVITY_SEWER'),
        ('E_POCO_VISITA', 'gis_node', 'gis01:MANHOLE'),
        ('E_RAMAL', 'gis_connection', 'gis01:SEWER_SERVICE_LINE'),
        ('A_CONEXOES', NULL, NULL),
        ('A_VALVULA', NULL, NULL),
        ('A_PROTEHIDRAULICAS', NULL, NULL),
        ('A_MACROMEDIDOR', NULL, NULL),
        ('A_PCP', NULL, NULL),
        ('A_PCQ', NULL, NULL),
        ('A_POCO', NULL, NULL),
        ('A_RESERVATORIO', NULL, NULL),
        ('A_EEAT', NULL, NULL),
        ('A_MOTOBOMBA', NULL, NULL),
        ('A_ETA', NULL, NULL),
        ('A_CAPTACOES', NULL, NULL),
        ('A_SETOR_FORNECIMENTO', NULL, NULL),
        ('E_TIL', NULL, NULL),
        ('E_CX_LIGACAO', NULL, NULL),
        ('E_CX_DESCARGA', NULL, NULL),
        ('E_VALVULAS', NULL, NULL),
        ('E_EEE', NULL, NULL),
        ('E_MOTOBOMBA', NULL, NULL),
        ('E_ETE', NULL, NULL),
        ('E_BACIA', NULL, NULL),
        ('E_SUB_BACIA', NULL, NULL)
     ) AS m(layer, tbl, code)
WHERE p.tenant_id = '${tenant_id}' AND p.key = 'CORSAN_ANEXO_A'
  AND NOT EXISTS (SELECT 1 FROM gis_cad_layer_map x
                  WHERE x.tenant_id = p.tenant_id AND x.profile_id = p.id
                    AND x.source_layer = m.layer);

-- ======================================================================
-- (2) Lote de importacao
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_import_batch (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    profile_id UUID,
    source_format TEXT NOT NULL CHECK (source_format IN
        ('DWG', 'DXF', 'SHP', 'GPKG', 'DGN_V7', 'DGN_V8', 'CSV_GNSS')),
    original_file_name TEXT NOT NULL,
    original_sha256 TEXT NOT NULL
        CHECK (original_sha256 ~ '^[0-9a-f]{64}$'),
    converted_format TEXT CHECK (converted_format IN ('DWG', 'DXF')),
    converted_sha256 TEXT
        CHECK (converted_sha256 IS NULL OR converted_sha256 ~ '^[0-9a-f]{64}$'),
    conversion_tool TEXT CHECK (conversion_tool IN
        ('ODA_FILE_CONVERTER', 'ASSISTED_EXTERNAL')),
    status TEXT NOT NULL DEFAULT 'RECEIVED' CHECK (status IN
        ('RECEIVED', 'STAGED', 'MAPPED', 'CLEANED', 'QUALITY_REPORTED',
         'PUBLISHED', 'REJECTED', 'FAILED')),
    source_srs_epsg INT,
    srs_source TEXT CHECK (srs_source IN ('DETECTED', 'OPERATOR_DECLARED')),
    srs_declared_by UUID,
    datum_transform TEXT NOT NULL DEFAULT 'NONE' CHECK (datum_transform IN
        ('NONE', 'IBGE_GRID', 'CONTROL_POINTS')),
    ibge_grid_ref TEXT,
    control_point_residual_m NUMERIC,
    snap_tolerance_m NUMERIC NOT NULL DEFAULT 0.50
        CHECK (snap_tolerance_m > 0 AND snap_tolerance_m <= 5.00),
    pilot_homologation BOOLEAN NOT NULL DEFAULT FALSE,
    counts_before JSONB,
    counts_after JSONB,
    change_set_id UUID,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_import_batch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_import_batch_original_hash
        UNIQUE (tenant_id, original_sha256),
    CONSTRAINT fk_gis_import_batch_profile
        FOREIGN KEY (tenant_id, profile_id)
        REFERENCES gis_import_profile (tenant_id, id),
    CONSTRAINT ck_gis_import_batch_conversion
        CHECK ((source_format = 'DWG'
                AND converted_format = 'DXF'
                AND converted_sha256 IS NOT NULL
                AND conversion_tool = 'ODA_FILE_CONVERTER')
               OR (source_format = 'DGN_V8'
                AND converted_format IN ('DWG', 'DXF')
                AND converted_sha256 IS NOT NULL
                AND conversion_tool = 'ASSISTED_EXTERNAL')
               OR (source_format NOT IN ('DWG', 'DGN_V8')
                AND converted_sha256 IS NULL)),
    CONSTRAINT ck_gis_import_batch_srs_choice
        CHECK (source_srs_epsg IS NULL
               OR srs_source IS NOT NULL)
);

SELECT register_tenant_table('gis_import_batch');

CREATE INDEX IF NOT EXISTS idx_gis_import_batch_status
    ON gis_import_batch (tenant_id, status, created_at);

-- ======================================================================
-- (3) Staging (§9.1 etapa 2) e rotulos (§9.1 etapa 3)
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_import_raw (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    source_layer TEXT NOT NULL,
    source_ref TEXT,
    block_name TEXT,
    label_text TEXT,
    label_raw_id UUID,
    attributes JSONB NOT NULL DEFAULT '{}',
    geom geometry(Geometry, 4674),
    status TEXT NOT NULL DEFAULT 'RAW' CHECK (status IN
        ('RAW', 'MAPPED', 'CLEANED', 'SKIPPED_DUPLICATE',
         'SKIPPED_SHORT_SEGMENT', 'INVALID_GEOMETRY', 'PUBLISHED')),
    layer_map_id UUID,
    published_feature_table TEXT,
    published_feature_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_import_raw_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_import_raw_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES gis_import_batch (tenant_id, id),
    CONSTRAINT fk_gis_import_raw_map
        FOREIGN KEY (tenant_id, layer_map_id)
        REFERENCES gis_cad_layer_map (tenant_id, id),
    CONSTRAINT ck_gis_import_raw_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object')
);

SELECT register_tenant_table('gis_import_raw');

CREATE INDEX IF NOT EXISTS idx_gis_import_raw_batch
    ON gis_import_raw (tenant_id, batch_id, status);
CREATE INDEX IF NOT EXISTS idx_gis_import_raw_geom
    ON gis_import_raw USING GIST (geom);
-- Casamento de reimportacao dentro do lote: mesma camada + source_ref.
CREATE UNIQUE INDEX IF NOT EXISTS uq_gis_import_raw_source_ref
    ON gis_import_raw (tenant_id, batch_id, source_layer, source_ref)
    WHERE source_ref IS NOT NULL;

-- ======================================================================
-- (7) Camadas de contexto importadas
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_context_feature (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    context_kind TEXT NOT NULL CHECK (context_kind IN
        ('NEIGHBORHOOD', 'BLOCK', 'PARCEL', 'STREET')),
    code TEXT,
    name TEXT,
    personal_data BOOLEAN NOT NULL DEFAULT FALSE,
    anonymization_method TEXT CHECK (anonymization_method IN
        ('SHIFTED_WITHIN_BLOCK', 'AGGREGATED_BY_BLOCK', 'SYNTHETIC')),
    attributes JSONB NOT NULL DEFAULT '{}',
    geom geometry(Geometry, 4674) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_context_feature_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_gis_context_feature_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES gis_import_batch (tenant_id, id),
    CONSTRAINT ck_gis_context_feature_personal
        CHECK (NOT personal_data OR anonymization_method IS NOT NULL),
    CONSTRAINT ck_gis_context_feature_attributes_object
        CHECK (jsonb_typeof(attributes) = 'object')
);

SELECT register_tenant_table('gis_context_feature');

CREATE INDEX IF NOT EXISTS idx_gis_context_feature_geom
    ON gis_context_feature USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_gis_context_feature_batch
    ON gis_context_feature (tenant_id, batch_id, context_kind);

-- ======================================================================
-- (5) Relatorio de qualidade ISO 19157
-- ======================================================================

CREATE TABLE IF NOT EXISTS gis_quality_report (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    batch_id UUID NOT NULL,
    report JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_quality_report_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_quality_report_batch UNIQUE (tenant_id, batch_id),
    CONSTRAINT fk_gis_quality_report_batch
        FOREIGN KEY (tenant_id, batch_id)
        REFERENCES gis_import_batch (tenant_id, id),
    CONSTRAINT ck_gis_quality_report_object
        CHECK (jsonb_typeof(report) = 'object')
);

SELECT register_tenant_table('gis_quality_report');

-- O pacote da GIS-04 guarda a FK do lote sem ciclo: gis_change_set nao
-- conhece o lote; o lote aponta para o pacote.
ALTER TABLE gis_import_batch
    ADD CONSTRAINT fk_gis_import_batch_change_set
    FOREIGN KEY (tenant_id, change_set_id)
    REFERENCES gis_change_set (tenant_id, id);

-- ======================================================================
-- Guarda do lote: maquina de estados + SRS + grade + anonimizacao
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_import_batch_guard() RETURNS TRIGGER AS $$
BEGIN
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    IF TG_OP = 'UPDATE' AND NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT ((OLD.status = 'RECEIVED' AND NEW.status IN ('STAGED', 'REJECTED', 'FAILED'))
             OR (OLD.status = 'STAGED' AND NEW.status IN ('MAPPED', 'REJECTED', 'FAILED'))
             OR (OLD.status = 'MAPPED' AND NEW.status IN ('CLEANED', 'REJECTED', 'FAILED'))
             OR (OLD.status = 'CLEANED' AND NEW.status IN ('QUALITY_REPORTED', 'REJECTED', 'FAILED'))
             OR (OLD.status = 'QUALITY_REPORTED' AND NEW.status IN ('PUBLISHED', 'REJECTED', 'FAILED'))) THEN
            RAISE EXCEPTION 'gis: transicao de lote de importacao invalida: % -> %', OLD.status, NEW.status;
        END IF;
        -- SRS (§9.1 etapa 4): sem SRS nao sai do recebimento; a escolha
        -- do operador fica registrada (srs_source/declarado por).
        IF NEW.status = 'STAGED' THEN
            IF NEW.source_srs_epsg IS NULL THEN
                RAISE EXCEPTION 'gis: arquivo sem SRS recusado: declare o SRS de origem ou registre a escolha do operador';
            END IF;
            IF NEW.srs_source = 'OPERATOR_DECLARED' AND NEW.srs_declared_by IS NULL THEN
                RAISE EXCEPTION 'gis: escolha de SRS pelo operador exige o responsavel registrado';
            END IF;
            -- SAD69 (EPSG 4618) e Corrego Alegre (EPSG 4225): so pela
            -- grade do IBGE, com a referencia da grade registrada.
            IF NEW.source_srs_epsg IN (4618, 4225) THEN
                IF NEW.datum_transform <> 'IBGE_GRID'
                   OR NEW.ibge_grid_ref IS NULL OR btrim(NEW.ibge_grid_ref) = '' THEN
                    RAISE EXCEPTION 'gis: SAD69/Corrego Alegre exigem conversao pela grade do IBGE registrada no lote';
                END IF;
            END IF;
            IF NEW.datum_transform = 'CONTROL_POINTS'
               AND NEW.control_point_residual_m IS NULL THEN
                RAISE EXCEPTION 'gis: ajuste por pontos de controle exige o residuo registrado';
            END IF;
        END IF;
        -- Anonimizacao (ADR-0033): lote com camada pessoal sem metodo
        -- registrado nao publica — nem contexto, nem feicao.
        IF NEW.status = 'PUBLISHED' THEN
            IF EXISTS (SELECT 1 FROM gis_context_feature c
                       WHERE c.batch_id = NEW.id AND c.tenant_id = NEW.tenant_id
                         AND c.personal_data AND c.anonymization_method IS NULL) THEN
                RAISE EXCEPTION 'gis: lote com camada pessoal sem metodo de anonimizacao registrado recusado (ADR-0033)';
            END IF;
            IF EXISTS (SELECT 1 FROM gis_import_raw r
                       JOIN gis_cad_layer_map m ON m.id = r.layer_map_id
                        AND m.tenant_id = r.tenant_id
                       WHERE r.batch_id = NEW.id AND r.tenant_id = NEW.tenant_id
                         AND m.personal_data AND m.anonymization_method IS NULL) THEN
                RAISE EXCEPTION 'gis: lote com camada pessoal sem metodo de anonimizacao registrado recusado (ADR-0033)';
            END IF;
        END IF;
    END IF;
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS gis_import_batch_guard_trigger ON gis_import_batch;
CREATE TRIGGER gis_import_batch_guard_trigger
    BEFORE INSERT OR UPDATE ON gis_import_batch
    FOR EACH ROW EXECUTE FUNCTION gis_import_batch_guard();

-- ======================================================================
-- (4) Limpeza topologica em conjunto no staging (§9.1 etapa 5)
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_import_clean(p_batch_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_batch gis_import_batch;
    v_schema TEXT;
    v_tenant_guc UUID;
    v_before JSONB;
    v_after JSONB;
    v_invalid INT;
    v_duplicates INT;
    v_short INT;
    v_remaining INT;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    SELECT * INTO v_batch FROM gis_import_batch WHERE id = p_batch_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'gis: lote de importacao % nao encontrado', p_batch_id;
    END IF;
    IF v_batch.status <> 'MAPPED' THEN
        RAISE EXCEPTION 'gis: limpeza exige lote MAPPED (atual %)', v_batch.status;
    END IF;

    SELECT jsonb_build_object(
               'total', count(*),
               'invalid_geometry', count(*) FILTER (WHERE geom IS NOT NULL AND NOT ST_IsValid(geom)),
               'without_geometry', count(*) FILTER (WHERE geom IS NULL))
      INTO v_before
      FROM gis_import_raw
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status IN ('RAW', 'MAPPED');

    -- Geometrias invalidas: ST_MakeValid; o que nao se recupera sai da
    -- publicacao marcado, nunca em silencio.
    UPDATE gis_import_raw
       SET geom = ST_MakeValid(geom), status = 'CLEANED', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED' AND geom IS NOT NULL AND NOT ST_IsValid(geom);
    GET DIAGNOSTICS v_invalid = ROW_COUNT;
    UPDATE gis_import_raw
       SET status = 'INVALID_GEOMETRY', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED' AND geom IS NULL;

    -- Duplicatas: mesma camada e geometria igual (o mesmo trecho vindo
    -- em dois arquivos/camadas sobrepostas) — fica a primeira (menor
    -- created_at/id), as demais sao marcadas. (Mesma camada+source_ref
    -- nem chega aqui: o UNIQUE parcial do staging ja recusa a segunda.)
    UPDATE gis_import_raw r
       SET status = 'SKIPPED_DUPLICATE', updated_at = clock_timestamp()
     WHERE r.batch_id = p_batch_id AND r.tenant_id = v_batch.tenant_id
       AND r.status IN ('MAPPED', 'CLEANED') AND r.geom IS NOT NULL
       AND EXISTS (SELECT 1 FROM gis_import_raw o
                   WHERE o.batch_id = r.batch_id AND o.tenant_id = r.tenant_id
                     AND o.source_layer = r.source_layer
                     AND o.status IN ('MAPPED', 'CLEANED')
                     AND ST_Equals(o.geom, r.geom)
                     AND (o.created_at, o.id) < (r.created_at, r.id));
    GET DIAGNOSTICS v_duplicates = ROW_COUNT;

    -- Segmentos curtos: linhas abaixo da tolerancia do lote.
    UPDATE gis_import_raw
       SET status = 'SKIPPED_SHORT_SEGMENT', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status IN ('MAPPED', 'CLEANED') AND geom IS NOT NULL
       AND GeometryType(geom) IN ('ST_LineString', 'ST_MultiLineString')
       AND ST_Length(geom::geography) < v_batch.snap_tolerance_m;
    GET DIAGNOSTICS v_short = ROW_COUNT;

    UPDATE gis_import_raw
       SET status = 'CLEANED', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED';

    SELECT count(*) INTO v_remaining FROM gis_import_raw
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'CLEANED';

    v_after := jsonb_build_object(
        'total', v_remaining,
        'invalid_fixed', v_invalid,
        'duplicates_removed', v_duplicates,
        'short_segments_removed', v_short);

    UPDATE gis_import_batch
       SET counts_before = v_before, counts_after = v_after, status = 'CLEANED'
     WHERE id = p_batch_id;
    RETURN v_after;
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- Rotulos (§9.1 etapa 3): associa o texto ao arco cru mais proximo do
-- mesmo lote, guardando o texto NAO interpretado na propria linha.
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_import_extract_labels(p_batch_id UUID)
RETURNS INT AS $$
DECLARE
    v_schema TEXT;
    v_tenant_guc UUID;
    v_count INT;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    UPDATE gis_import_raw t
       SET label_raw_id = t.id, updated_at = clock_timestamp()
     WHERE t.batch_id = p_batch_id AND t.label_text IS NOT NULL
       AND t.geom IS NOT NULL;
    -- O vinculo texto->arco fica no atributo label_for_source_ref do
    -- staging (JSONB do staging, nao do cadastro).
    UPDATE gis_import_raw a
       SET attributes = a.attributes || jsonb_build_object(
               'label_texts',
               (SELECT jsonb_agg(t.label_text ORDER BY t.id)
                  FROM gis_import_raw t
                 WHERE t.batch_id = a.batch_id AND t.tenant_id = a.tenant_id
                   AND t.label_text IS NOT NULL AND t.geom IS NOT NULL
                   AND ST_DWithin(t.geom::geography, a.geom::geography, 5.0))),
           updated_at = clock_timestamp()
     WHERE a.batch_id = p_batch_id AND a.geom IS NOT NULL
       AND GeometryType(a.geom) IN ('ST_LineString', 'ST_MultiLineString')
       AND EXISTS (SELECT 1 FROM gis_import_raw t
                   WHERE t.batch_id = a.batch_id AND t.tenant_id = a.tenant_id
                     AND t.label_text IS NOT NULL AND t.geom IS NOT NULL
                     AND ST_DWithin(t.geom::geography, a.geom::geography, 5.0));
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- (6) Publicacao: somente pacote IMPORT (§9.1 etapa 7)
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_import_publish(
    p_batch_id UUID, p_author_id UUID)
RETURNS UUID AS $$
DECLARE
    v_batch gis_import_batch;
    v_schema TEXT;
    v_tenant_guc UUID;
    v_set_id UUID;
    v_raw gis_import_raw;
    v_map gis_cad_layer_map;
    v_type_id UUID;
    v_feature_id UUID;
    v_code TEXT;
    v_geom geometry;
    v_report JSONB;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    SELECT * INTO v_batch FROM gis_import_batch WHERE id = p_batch_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'gis: lote de importacao % nao encontrado', p_batch_id;
    END IF;
    IF v_batch.status <> 'QUALITY_REPORTED' THEN
        RAISE EXCEPTION 'gis: publicacao exige lote QUALITY_REPORTED com relatorio ISO 19157 (atual %)', v_batch.status;
    END IF;
    SELECT report INTO v_report FROM gis_quality_report
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id;
    IF v_report IS NULL THEN
        RAISE EXCEPTION 'gis: lote sem relatorio de qualidade nao publica';
    END IF;
    IF v_batch.profile_id IS NULL THEN
        RAISE EXCEPTION 'gis: lote sem perfil de mapeamento nao publica';
    END IF;

    v_set_id := uuid_generate_v4();
    INSERT INTO gis_change_set (id, tenant_id, type, channel, status,
            author_id, technical_report, quality_report, created_by, updated_by)
    VALUES (v_set_id, v_batch.tenant_id, 'IMPORT', 'IMPORT', 'DRAFT',
            p_author_id, 'Importacao GIS-07 lote ' || p_batch_id::text,
            v_report, p_author_id, p_author_id);

    FOR v_raw IN
        SELECT * FROM gis_import_raw
         WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
           AND status = 'CLEANED'
         ORDER BY created_at, id
    LOOP
        SELECT * INTO v_map FROM gis_cad_layer_map
         WHERE id = v_raw.layer_map_id AND tenant_id = v_batch.tenant_id;
        IF NOT FOUND OR v_map.target_kind <> 'FEATURE' THEN
            CONTINUE;
        END IF;
        IF v_map.feature_table IS NULL OR v_map.feature_type_code IS NULL THEN
            RAISE EXCEPTION 'gis: camada % sem alvo de mapeamento no perfil; complete o mapeamento antes de publicar', v_raw.source_layer;
        END IF;
        -- Idempotencia: feicao ja publicada deste lote com a mesma
        -- origem nao e criada de novo.
        IF v_raw.source_ref IS NOT NULL AND EXISTS (
            SELECT 1 FROM gis_import_raw done
             WHERE done.tenant_id = v_batch.tenant_id
               AND done.batch_id = p_batch_id
               AND done.source_layer = v_raw.source_layer
               AND done.source_ref = v_raw.source_ref
               AND done.status = 'PUBLISHED'
               AND done.published_feature_table = v_map.feature_table) THEN
            UPDATE gis_import_raw SET status = 'SKIPPED_DUPLICATE',
                updated_at = clock_timestamp() WHERE id = v_raw.id;
            CONTINUE;
        END IF;
        SELECT id INTO v_type_id FROM gis_feature_type
         WHERE tenant_id = v_batch.tenant_id
           AND factory_code = v_map.feature_type_code;
        IF v_type_id IS NULL THEN
            RAISE EXCEPTION 'gis: tipo % do mapeamento nao existe no catalogo do tenant', v_map.feature_type_code;
        END IF;
        v_feature_id := uuid_generate_v4();
        v_code := 'IMP-' || substr(p_batch_id::text, 1, 8) || '-'
                  || substr(v_feature_id::text, 1, 8);
        v_geom := v_raw.geom;
        IF v_map.feature_table IN ('gis_structure', 'gis_zone')
           AND GeometryType(v_geom) = 'ST_Polygon' THEN
            v_geom := ST_Multi(v_geom);
        END IF;
        -- Dimensionalidade das tabelas da V13_2: node/arc/connection
        -- guardam Z (PointZ/LineStringZ); structure/zone/poi sao 2D.
        IF v_map.feature_table IN ('gis_structure', 'gis_zone', 'gis_poi') THEN
            v_geom := ST_Force2D(v_geom);
        ELSE
            v_geom := ST_Force3D(v_geom);
        END IF;
        IF v_map.feature_table = 'gis_structure'
           AND GeometryType(v_geom) IN ('ST_Polygon', 'ST_MultiPolygon') THEN
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        ELSIF v_map.feature_table = 'gis_structure' THEN
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom_point, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        ELSE
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        END IF;
        INSERT INTO gis_change_set_item (id, tenant_id, change_set_id,
                operation, feature_table, feature_id, proposed_state,
                proposed_geom_wkt, proposed_geom_point_wkt,
                created_by, updated_by)
        VALUES (uuid_generate_v4(), v_batch.tenant_id, v_set_id, 'INSERT',
                v_map.feature_table, v_feature_id,
                '{"lifecycle_status":"IN_SERVICE"}'::jsonb,
                CASE WHEN v_map.feature_table = 'gis_structure'
                          AND GeometryType(v_geom) NOT IN ('ST_Polygon', 'ST_MultiPolygon')
                     THEN NULL ELSE ST_AsText(v_geom) END,
                CASE WHEN v_map.feature_table = 'gis_structure'
                          AND GeometryType(v_geom) NOT IN ('ST_Polygon', 'ST_MultiPolygon')
                     THEN ST_AsText(v_geom) ELSE NULL END,
                p_author_id, p_author_id);
        UPDATE gis_import_raw
           SET status = 'PUBLISHED', published_feature_table = v_map.feature_table,
               published_feature_id = v_feature_id, updated_at = clock_timestamp()
         WHERE id = v_raw.id;
    END LOOP;

    IF NOT EXISTS (SELECT 1 FROM gis_change_set_item
                   WHERE change_set_id = v_set_id AND tenant_id = v_batch.tenant_id) THEN
        RAISE EXCEPTION 'gis: lote sem nenhuma feicao mapeada para publicar';
    END IF;

    UPDATE gis_change_set SET status = 'SUBMITTED', submitted_at = clock_timestamp()
     WHERE id = v_set_id;
    UPDATE gis_import_batch
       SET change_set_id = v_set_id, status = 'PUBLISHED'
     WHERE id = p_batch_id;
    RETURN v_set_id;
END;
$$ LANGUAGE plpgsql;
