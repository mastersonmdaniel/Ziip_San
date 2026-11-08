-- ED-38 — Portal e autoatendimento ampliados. SQL PENDENTE DE NUMERACAO:
-- nenhum numero Flyway foi confirmado livre para esta tarefa; a integracao
-- numera na camada. Sem tabelas novas de dominio: faturas (P3.20/billing),
-- protocolos (P1.36, V8_1) e vinculo (P3.20, V8_2) ja existem. Aqui ficam
-- (1) o registro de documentos autenticos emitidos pelo portal e
-- (2) o agrupamento de relatos anonimos, e (3) o cadastro das chaves novas
-- em public.parameter_definitions (migration core, tambem sem numero).

-- (1) Documentos autenticos do portal (certidao/declaracao/comprovante)
CREATE TABLE IF NOT EXISTS portal_authentic_documents (
    id              uuid PRIMARY KEY,
    tenant_id       uuid NOT NULL,
    kind            text NOT NULL CHECK (kind IN ('CERTIFICATE','DECLARATION','RECEIPT')),
    document_type   text NOT NULL,
    document_number text NOT NULL,
    issued_on       date NOT NULL,
    signature_level text NOT NULL CHECK (signature_level IN ('SIMPLE','ADVANCED','QUALIFIED')),
    verification_code text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, document_type, document_number)
);

-- (2) Relatos sem login agrupados por evento (protocolo da P1.36, alvo LOCATION_ONLY)
CREATE TABLE IF NOT EXISTS portal_anonymous_reports (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    protocol_id  uuid NOT NULL,
    event_key    text NOT NULL,
    photo_ref    text,
    contact      text,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_portal_anon_reports_event
    ON portal_anonymous_reports (tenant_id, event_key, created_at);

-- (3) Chaves novas (classes do PARAMETROS-DO-TENANT; integrar no catalogo core)
-- portal.idp_govbr_min_level  TEXT  classe d  sem fabrica (SILVER|GOLD; exigido se gov.br ligado)
-- bill.peer_comparison        BOOLEAN classe d fabrica false (desligado por padrao, aceite ED-38)
-- bill.peer_min_units         INT   classe c  piso 5, so amplia (analogia gis.public_aggregation_min_units)
-- portal.idp_govbr_enabled / portal.min_assurance_by_operation / portal.passkey_enabled:
-- JA definidos pela P3.3 / ED-06 — nao duplicar.
