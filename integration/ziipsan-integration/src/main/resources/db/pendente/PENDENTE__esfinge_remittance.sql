-- ED-33 Remessa e-Sfinge assistida — SQL PENDENTE, SEM NUMERO Flyway.
-- Motivo: o plano (PLANO-v3.0, §3) reserva para a ED-33 apenas a FAIXA
-- V7_x do modulo integration, sem numero especifico (V7_1 e da P2.26), e
-- o numero livre nao foi confirmado por leitura completa do diretorio de
-- migrations da main (HEAD 2f77582). Atribuir numero na integracao, apos
-- a leitura completa — ou pela sequencia global V100+ se a tarefa for
-- executada depois da P3.12 (fim do outOfOrder).
-- NENHUMA versao de leiaute e semeada: a versao vigente do TCE/SC nao
-- esta nas fontes (pendencia declarada PENDENTE_LEIAUTE_OFICIAL_TCE_SC);
-- esfinge.layout_version e configuracao do tenant, sem valor de fabrica.

CREATE TABLE IF NOT EXISTS esfinge_layout_versions (
    version VARCHAR(60) PRIMARY KEY,
    norm_reference VARCHAR(200),
    official BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATE NOT NULL,
    effective_to DATE,
    layout_json JSONB NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL);

CREATE TABLE IF NOT EXISTS esfinge_remittances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    competence DATE NOT NULL,
    layout_version VARCHAR(60) NOT NULL REFERENCES esfinge_layout_versions(version),
    layout_official BOOLEAN NOT NULL,
    sha256 CHAR(64) NOT NULL,
    content JSONB NOT NULL,
    status VARCHAR(24) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    downloaded_at TIMESTAMPTZ,
    downloaded_by VARCHAR(200),
    return_protocol VARCHAR(120),
    return_outcome VARCHAR(16),
    return_received_on DATE,
    return_notes VARCHAR(1000));

-- Indice de apoio a unicidade viva (tenant, competencia, versao) tratada
-- na regra de EsfingeRemittance.Ledger: a unicidade e condicional ao
-- desfecho do retorno (retificacao apos REJECTED), por isso NAO e uma
-- constraint simples — a integracao decide a forma final (indice parcial
-- ou trava na transacao) sem afrouxar a regra da classe.
CREATE INDEX IF NOT EXISTS idx_esfinge_remittances_tenant_competence
    ON esfinge_remittances (tenant_id, competence, layout_version);

-- Definicao de parametro (catalogo P0.19): SEM valor de fabrica para
-- esfinge.layout_version (leiaute vigente do TCE/SC nao esta nas fontes).
