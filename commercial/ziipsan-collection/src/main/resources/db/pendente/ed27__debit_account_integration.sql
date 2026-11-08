-- ED-27 - Debito em conta: integracao Pix Automatico (P4.5) + legado FEBRABAN 150 (P3.24).
-- SEM NUMERO Flyway: a numeracao e fixada na integracao (precedente P4.1/ED-30/ED-31).
-- A migration do legado em si e a V6_4 da P3.24 (tabelas do FEBRABAN 150) - NAO duplicada aqui.
-- Este SQL cobre so o que e proprio da ED-27: consentimento da conversao e
-- idempotencia do processamento de retorno legado. Nenhum valor de parametro
-- e semeado: pay.legacy_debit_enabled (classe d) e cadastrado por tenant na
-- implantacao; a data de desligamento NAO e coluna digitada - e deduzida em
-- BcbDebitRegimeRules (P3.24, fonte unica) do enquadramento do convenio;
-- DebitRegulatoryCalendar (ED-27) apenas delega a ela (correcao D3).
-- Idempotencia do retorno (correcoes D2/D3): legacy_return_processing guarda
-- DOIS tipos de chave na coluna return_key, com a MESMA PK (tenant, chave):
-- (a) a chave canonica POR DEBITO 'DEBIT:<identificador>', marcada logo apos
-- a baixa de cada linha (semantica alinhada a P3.24, onde a baixa e por
-- debito - assim o mesmo debito vindo em outro arquivo nao baixa 2x); e
-- (b) a chave do arquivo (NSA+hash), marcada so no fim do processamento
-- completo, como atalho de repeticao.

CREATE TABLE IF NOT EXISTS debit_conversion_consent (
    tenant_id          uuid        NOT NULL,
    unit_id            uuid        NOT NULL,
    legacy_enrollment  text        NOT NULL,
    status             text        NOT NULL CHECK (status IN ('CANDIDATO', 'CONVERTIDO')),
    consent_proof_hash text        CHECK (consent_proof_hash IS NULL OR consent_proof_hash ~ '^[0-9a-fA-F]{64}$'),
    consent_proof_ref  text,
    decided_at         timestamptz,
    created_at         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, unit_id),
    CHECK (status <> 'CONVERTIDO' OR (consent_proof_hash IS NOT NULL AND consent_proof_ref IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS legacy_return_processing (
    tenant_id    uuid        NOT NULL,
    return_key   text        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    settled_count int        NOT NULL DEFAULT 0 CHECK (settled_count >= 0),
    PRIMARY KEY (tenant_id, return_key)
);

-- Registrar as duas tabelas no mecanismo de tenant da plataforma na integracao:
-- SELECT register_tenant_table('debit_conversion_consent');
-- SELECT register_tenant_table('legacy_return_processing');
