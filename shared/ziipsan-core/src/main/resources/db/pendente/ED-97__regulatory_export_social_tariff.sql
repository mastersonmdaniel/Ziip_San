-- ED-97 -- Pacote regulatorio de saida por agencia, subsidios e Indice
-- de Tarifa Social: registro de envio com hash e evidencia legivel pela
-- agenda da ED-98 (RAS-08), sobre o perfil regulatorio da P2.31 (RAS-01).
-- Status: PENDENTE DE NUMERACAO -- SQL sem numero global, em
-- db/pendente/ com prefixo ED-97__ (regra da cascata). Nenhuma migration
-- publicada e tocada e nenhum numero e atribuido neste arquivo.
--
-- PARAMETRO reg.reports (aceite da ED-97): objeto, classe d, REG -- o
-- aceite o nomeia como o parametro do modo assistido (ADR-0004). Se a
-- chave ja existir no catalogo do core, o INSERT abaixo e ignorado
-- (ON CONFLICT DO NOTHING), no precedente da ED-75/ED-06.
-- social.annual_report JA existe no catalogo de parametros (destinatario,
-- data, formato; classe d, REG) e NAO e redefinido aqui.
--
-- MODO ASSISTIDO (ADR-0004): status nasce GENERATED_VALIDATED; so vira
-- SUBMITTED_BY_TENANT com protocolo da agencia declarado pelo tenant
-- (CHECK abaixo) -- envio real nao se finge.
-- Correcao R7-B2 (Rodada 7): o CHECK do protocolo exige protocolo
-- NAO-BRANCO (btrim <> ''), como o Java (SubmissionRecord: isBlank), e
-- o formato de evidence_ref e validado aqui com o mesmo padrao que o
-- Java produz em SubmissionRecord.evidenceRef():
-- ED97-EVIDENCE:<agencia>:<periodo>:<16 hex>.
-- SEPARACAO: kind AGGREGATE_PUBLIC x NOMINAL_RESTRICTED gravado na linha;
-- a trava de conteudo (nominal nunca no agregado) e do codigo
-- (ExportPackage) e repetida aqui pela coluna kind.
-- FK COMPOSTA: nenhuma FK neste arquivo (tenant_id e a particao RLS).

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('reg.reports', 'OBJECT', 'd', NULL,
     'ADR-0004; ED-97 (RAS-01, RAS-08); GES-04, CAD-92',
     NULL,
     'Configuracao dos relatorios regulatorios de saida por agencia (leiaute, periodicidade, canal, versao do leiaute) no modo assistido do ADR-0004: o sistema gera e valida o arquivo, o tenant envia. Sem valor de fabrica.')
ON CONFLICT (definition_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS reg_export_submissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    agency_code VARCHAR(32) NOT NULL,
    profile_key VARCHAR(96) NOT NULL,
    layout_version VARCHAR(64) NOT NULL,
    period_ref VARCHAR(16) NOT NULL,
    kind VARCHAR(24) NOT NULL CHECK (kind IN ('AGGREGATE_PUBLIC', 'NOMINAL_RESTRICTED')),
    package_sha256 CHAR(64) NOT NULL CHECK (package_sha256 ~ '^[0-9a-f]{64}$'),
    status VARCHAR(24) NOT NULL DEFAULT 'GENERATED_VALIDATED'
        CHECK (status IN ('GENERATED_VALIDATED', 'SUBMITTED_BY_TENANT')),
    agency_protocol VARCHAR(128),
    evidence_ref VARCHAR(160) NOT NULL,
    validated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reg_export_submissions_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_reg_export_submitted_needs_protocol
        CHECK (status <> 'SUBMITTED_BY_TENANT'
               OR (agency_protocol IS NOT NULL AND btrim(agency_protocol) <> '')),
    CONSTRAINT ck_reg_export_evidence_ref_format
        CHECK (evidence_ref ~ '^ED97-EVIDENCE:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+:[0-9a-f]{16}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_reg_export_submission_pkg
    ON reg_export_submissions(tenant_id, agency_code, period_ref, package_sha256);

SELECT register_tenant_table('reg_export_submissions');
