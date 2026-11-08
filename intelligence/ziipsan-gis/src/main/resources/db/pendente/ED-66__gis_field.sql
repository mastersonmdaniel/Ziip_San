-- ED-66 — Campo GIS no app Capacitor (GIS-15) — SQL PENDENTE, SEM NUMERO Flyway.
-- Motivo: a numeracao global e atribuida no pouso pelo coordenador (regra da
-- cascata, precedente ED-20/ED-53/ED-65). Este arquivo vive em db/pendente/ do
-- modulo ziipsan-gis e NAO e executado pelo Flyway ate receber numero.
-- Ancoragem: gis_change_set/gis_change_set_item JA existem (V13_9, GIS-04) com
-- type FIELD e channel FIELD — os deltas de campo entram NESSE pacote, sem
-- tabela nova de pacote de edicao. gis_attachment JA existe (V13_2) com geom
-- (Point, 4674) e azimuth_deg — hash e varredura vao na extensao abaixo.
-- NENHUM valor de tenant e semeado: a meta de exatidao e gis.accuracy_targets
-- (core V9_6, ja existente) e o desfoque e field.photo_blur_faces (ED-20).

-- (1) Pacote offline por area (§8; tabela gis_field_package do §3.4) -----------
CREATE TABLE IF NOT EXISTS gis_field_package (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    area_ref TEXT NOT NULL CHECK (btrim(area_ref) <> ''),
    extent geometry(Geometry, 4674),
    package_version INT NOT NULL CHECK (package_version > 0),
    basemap_ref TEXT,
    style_profile_key TEXT,
    issued_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    issued_to_device_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_field_package_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_gis_field_package_validity CHECK (valid_until > issued_at)
);
SELECT register_tenant_table('gis_field_package');
CREATE INDEX IF NOT EXISTS idx_gis_field_package_tenant
    ON gis_field_package (tenant_id, valid_until);

-- (2) Recibo de delta: a prova persistida da idempotencia (§8) -----------------
-- Um client_change_id (UUIDv7) por tenant, UMA vez: o reenvio bate no UNIQUE
-- e devolve o recibo original (DUPLICATE) em vez de reaplicar o delta.
CREATE TABLE IF NOT EXISTS gis_field_delta_receipt (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    client_change_id UUID NOT NULL,
    field_package_id UUID,
    change_set_id UUID,
    outcome TEXT NOT NULL CHECK (outcome IN ('APPLIED', 'DUPLICATE', 'CONFLICT', 'REJECTED')),
    conflict_fields JSONB,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_gis_field_delta_receipt_client UNIQUE (tenant_id, client_change_id)
);
SELECT register_tenant_table('gis_field_delta_receipt');

-- (3) Fila de conflito por atributo (§8/§6.3): resolucao na web ----------------
CREATE TABLE IF NOT EXISTS gis_field_conflict (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    client_change_id UUID NOT NULL,
    feature_table TEXT NOT NULL CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure', 'gis_zone', 'gis_poi')),
    feature_id UUID NOT NULL,
    conflict_fields JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'DISCARDED')),
    resolved_by UUID,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_field_conflict_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('gis_field_conflict');
CREATE INDEX IF NOT EXISTS idx_gis_field_conflict_open
    ON gis_field_conflict (tenant_id, status, created_at);

-- (4) Extensao da foto de campo sobre gis_attachment (V13_2) -------------------
-- gis_attachment ja guarda geom (posicao) e azimuth_deg; aqui vao o hash, a
-- varredura (P1.20) e o retrato do fix GNSS da captura. A altitude do fix
-- chega ELIPSOIDAL (transito) e a normal so existe depois da conversao no
-- servidor (hgeoHNOR2020) — por isso as duas colunas, com o metodo registrado.
CREATE TABLE IF NOT EXISTS gis_field_photo (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    sha256 CHAR(64) NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    scan_status TEXT NOT NULL DEFAULT 'QUARANTINE'
        CHECK (scan_status IN ('QUARANTINE', 'CLEARED', 'INFECTED')),
    faces_blurred BOOLEAN NOT NULL DEFAULT FALSE,
    gnss_solution TEXT CHECK (gnss_solution IN ('RTK_FIXED', 'RTK_FLOAT', 'AUTONOMOUS')),
    gnss_pdop NUMERIC,
    gnss_satellites INT,
    gnss_h_accuracy_m NUMERIC,
    gnss_v_accuracy_m NUMERIC,
    antenna_height_m NUMERIC,
    ellipsoidal_height_m NUMERIC,
    normal_height_m NUMERIC,
    vertical_datum TEXT CHECK (vertical_datum IN ('IMBITUBA', 'SANTANA', 'ELLIPSOIDAL', 'UNKNOWN')),
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_field_photo_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_gis_field_photo_attachment UNIQUE (tenant_id, attachment_id),
    -- Correcao m3 da revisao da ED-66: altitude NORMAL so existe em datum
    -- vertical de nivelamento (IMBITUBA/SANTANA) — normal_height_m com
    -- datum ELLIPSOIDAL/UNKNOWN (ou sem datum) e recusado pelo banco, nao
    -- so pelo nucleo Java: elipsoidal nunca e gravada como normal.
    CONSTRAINT ck_gis_field_photo_normal_height_datum CHECK (
        normal_height_m IS NULL OR vertical_datum IN ('IMBITUBA', 'SANTANA'))
);
SELECT register_tenant_table('gis_field_photo');

-- (5) Croqui vetorial com amarracoes (§8): anexo vetorial da feicao ------------
CREATE TABLE IF NOT EXISTS gis_field_sketch (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    feature_table TEXT NOT NULL CHECK (feature_table IN
        ('gis_node', 'gis_arc', 'gis_connection', 'gis_structure', 'gis_zone', 'gis_poi')),
    feature_id UUID NOT NULL,
    strokes JSONB NOT NULL CHECK (jsonb_typeof(strokes) = 'array'),
    ties JSONB NOT NULL CHECK (jsonb_typeof(ties) = 'array'),
    captured_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_gis_field_sketch_tenant_id UNIQUE (tenant_id, id)
);
SELECT register_tenant_table('gis_field_sketch');
