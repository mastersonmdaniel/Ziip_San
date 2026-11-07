-- V4_7 — RESERVED, SHARED migration (plan v3.0, section 3): "NFAg-e e NFS-e avulsa:
-- leiaute, contingência, inutilização".
--
-- *** THIS FILE IS ONLY THE NFS-e PART (P3.6). ***
-- The NFAg-e part belongs to P2.6, which has NOT been executed yet. P2.6 must APPEND its
-- tables/columns to this same V4_7 file (or to the still-free part of it) before this
-- migration is first applied by Flyway; once applied anywhere, this file is immutable and
-- any NFAg-e remainder must arrive as a new migration. No NFAg-e table is created here,
-- on purpose, so the two tasks do not invent each other's schema.
--
-- P3.6 part (acceptance): avulso service types carry the IBS/CBS obligation stage of Ato
-- Conjunto RFB/CGIBS 4/2026, and each NFS-e generated from a service order records the
-- stage applied, the issue date and whether the IBS/CBS groups were included.
-- Stage codes mirror br.com.ziipsan.commercial.fiscal.nfse.NfseEtapa:
--   ISS_FORA_HIPOTESES_ESPECIAIS (from 2026-10-01), DEMAIS_HIPOTESES (from 2026-12-01),
--   SIMPLES_NACIONAL (from 2027-01-01).
--
-- NO SEEDED DATA: service types are tenant cadastre, never migration seed.

CREATE TABLE IF NOT EXISTS fiscal_avulso_service_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    nfse_etapa VARCHAR(40) NOT NULL CHECK (nfse_etapa IN
        ('ISS_FORA_HIPOTESES_ESPECIAIS', 'DEMAIS_HIPOTESES', 'SIMPLES_NACIONAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE fiscal_avulso_service_types ADD CONSTRAINT uq_fiscal_avulso_types_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_fiscal_avulso_types_code ON fiscal_avulso_service_types(tenant_id, code);

CREATE TABLE IF NOT EXISTS fiscal_nfse_avulsas (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    service_order_id UUID NOT NULL,
    service_type_code VARCHAR(40) NOT NULL,
    etapa_aplicavel VARCHAR(40) NOT NULL CHECK (etapa_aplicavel IN
        ('ISS_FORA_HIPOTESES_ESPECIAIS', 'DEMAIS_HIPOTESES', 'SIMPLES_NACIONAL')),
    issue_date DATE NOT NULL,
    prestador_cnpj VARCHAR(14) NOT NULL,
    tomador_cnpj VARCHAR(14) NOT NULL,
    service_amount NUMERIC(14,2) NOT NULL CHECK (service_amount > 0),
    com_grupos_ibs_cbs BOOLEAN NOT NULL,
    ambiente VARCHAR(30) NOT NULL CHECK (ambiente IN ('PRODUCAO_RESTRITA')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('GERADA_NAO_TRANSMITIDA')),
    dps_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID,
    updated_by UUID,
    tenant_schema VARCHAR(100)
);
ALTER TABLE fiscal_nfse_avulsas ADD CONSTRAINT uq_fiscal_nfse_avulsas_tenant_id UNIQUE (tenant_id, id);
CREATE INDEX idx_fiscal_nfse_avulsas_os ON fiscal_nfse_avulsas(tenant_id, service_order_id);
CREATE INDEX idx_fiscal_nfse_avulsas_issue ON fiscal_nfse_avulsas(tenant_id, issue_date);

-- ========================================================================
-- ISOLATION AND AUDIT (same helper used by V4_6)
-- ========================================================================

SELECT register_tenant_table('fiscal_avulso_service_types');
SELECT register_tenant_table('fiscal_nfse_avulsas');
