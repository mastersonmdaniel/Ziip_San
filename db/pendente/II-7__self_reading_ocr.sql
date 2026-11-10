-- II-7 (Onda II do PDCA-01): persistencia da submissao de autoleitura
-- com foto/OCR, do score e da prova geografica. Ficheiro PENDENTE: sem
-- numero Flyway (a numeracao global e atribuida na integracao). Prefixo
-- II-7 por instrucao do pacote.
-- NENHUM limiar operacional esta neste DDL: limiares de score e de
-- distancia sao parametros do tenant (chaves meter.selfreading_* em
-- SelfReadingParameters) [A DEFINIR] -- pendencia P-II7-LIMIARES.
-- A leitura aceite segue para a critica da II-2; esta tabela guarda a
-- submissao e o desfecho da conferencia da autoleitura, nao a critica.

CREATE TABLE IF NOT EXISTS self_reading_submissions (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    unit_id                 UUID NOT NULL,
    reference_month         DATE NOT NULL,
    declared_reading        INTEGER,
    photo_artifact_ref      VARCHAR(512),
    photo_sha256            CHAR(64),
    ocr_engine_id           VARCHAR(80),
    ocr_reading             INTEGER,
    ocr_confidence          NUMERIC(5, 4),
    score_total             NUMERIC(5, 2),
    score_ocr               NUMERIC(10, 6),
    score_coherence         NUMERIC(10, 6),
    score_photo             NUMERIC(10, 6),
    score_geo               NUMERIC(10, 6),
    geo_latitude            NUMERIC(10, 7),
    geo_longitude           NUMERIC(10, 7),
    geo_accuracy_m          NUMERIC(10, 2),
    geo_distance_to_link_m  NUMERIC(12, 3),
    status                  VARCHAR(30) NOT NULL,
    reasons                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_self_reading_status
        CHECK (status IN ('ACCEPTED_FOR_CRITIQUE', 'PENDING_HUMAN_REVIEW', 'PENDING_OCR')),
    CONSTRAINT chk_self_reading_ocr_reading
        CHECK (ocr_reading IS NULL OR ocr_reading >= 0),
    CONSTRAINT chk_self_reading_confidence
        CHECK (ocr_confidence IS NULL OR (ocr_confidence >= 0 AND ocr_confidence <= 1)),
    CONSTRAINT chk_self_reading_score
        CHECK (score_total IS NULL OR (score_total >= 0 AND score_total <= 100)),
    CONSTRAINT chk_self_reading_lat
        CHECK (geo_latitude IS NULL OR (geo_latitude >= -90 AND geo_latitude <= 90)),
    CONSTRAINT chk_self_reading_lon
        CHECK (geo_longitude IS NULL OR (geo_longitude >= -180 AND geo_longitude <= 180)),
    CONSTRAINT chk_self_reading_accepted_has_reading
        CHECK (status <> 'ACCEPTED_FOR_CRITIQUE' OR (ocr_reading IS NOT NULL AND score_total IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS idx_self_reading_queue
    ON self_reading_submissions (tenant_id, reference_month, unit_id, status);

CREATE INDEX IF NOT EXISTS idx_self_reading_review
    ON self_reading_submissions (tenant_id, status, reference_month)
    WHERE status = 'PENDING_HUMAN_REVIEW';
