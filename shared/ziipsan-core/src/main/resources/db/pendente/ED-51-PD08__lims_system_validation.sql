-- ED-51-PD08 — Pacote de validacao do sistema por versao (PD-08;
-- decisao do dono de 09/11/2026, item 40 do DECISOES-DO-DONO.md;
-- apoio a ISO/IEC 17025, ADR-0041; pendencia P-ED51-01 da ED-51).
-- SQL PENDENTE de numeracao Flyway (regra da cascata: nenhum numero
-- inventado). Na integracao: atribuir o proximo slot livre da faixa
-- certification (pacotes por norma sobre o nucleo das V21_1 a V21_3,
-- como a ED-51/ED-69), renomear para
-- V<slot>__lims_system_validation.sql e conferir a FK contra
-- lims_laboratories (SQL pendente da ED-51, ED-51__lims17025.sql —
-- os dois SQL so resolvem juntos quando ambos forem numerados;
-- pendencia P-ED51PD08-01).
--
-- REGRA DURA: este SQL NAO semeia NENHUMA versao de sistema, NENHUM
-- item de plano de validacao e NENHUMA evidencia. Versao, itens do
-- plano e referencias de evidencia sao DADO DECLARADO do tenant/
-- laboratorio. O conteudo normativo do checklist de validacao por
-- versao e [A DEFINIR] (pendencia P-ED51PD08-02) — nenhuma linha
-- deste arquivo o presume.
--
-- SEM DELETE: desligar a norma nao apaga registro (ADR-0041;
-- cert.deactivation_keeps_records travado em true). Nenhum comando
-- deste arquivo apaga linha de tabela alguma. Historicos (execucoes
-- de itens, revisoes do dossie) sao por INSERT de registro novo.

-- ---------------------------------------------------------------------
-- Dossie de validacao do sistema por versao: uma revisao por linha;
-- a emenda de dossie concluido nasce como linha nova (revisao
-- seguinte, supersedes_id para esta) e esta fica SUPERSEDED,
-- preservada (padrao de lims_lab_reports da ED-51).
-- Segregacao concluinte x declarante espelhada em CHECK; a
-- segregacao concluinte x executores e conferida no servico (vive
-- entre tabelas — padrao da ED-51 para papeis conferidos no servico).
-- ---------------------------------------------------------------------
CREATE TABLE lims_system_validations (
    tenant_id       UUID NOT NULL,
    id              UUID NOT NULL,
    laboratory_id   UUID NOT NULL,
    system_version  TEXT NOT NULL CHECK (btrim(system_version) <> ''),
    declared_by_ref TEXT NOT NULL CHECK (btrim(declared_by_ref) <> ''),
    declared_at     TIMESTAMPTZ NOT NULL,
    status          TEXT NOT NULL CHECK (status IN ('OPEN', 'CONCLUDED', 'SUPERSEDED')),
    concluded_by_ref TEXT,
    concluded_at    TIMESTAMPTZ,
    conclusion_outcome TEXT CHECK (conclusion_outcome IS NULL
        OR conclusion_outcome IN ('PASSED', 'FAILED')),
    revision        INTEGER NOT NULL CHECK (revision >= 1),
    supersedes_id   UUID,
    amendment_reason TEXT,
    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, id),
    FOREIGN KEY (tenant_id, laboratory_id)
        REFERENCES lims_laboratories (tenant_id, id),
    FOREIGN KEY (tenant_id, supersedes_id)
        REFERENCES lims_system_validations (tenant_id, id),
    CHECK (concluded_by_ref IS NULL OR btrim(concluded_by_ref) <> ''),
    CHECK (concluded_by_ref IS NULL OR concluded_by_ref <> declared_by_ref),
    CHECK ((status = 'OPEN' AND concluded_by_ref IS NULL AND concluded_at IS NULL
            AND conclusion_outcome IS NULL)
        OR (status = 'CONCLUDED' AND concluded_by_ref IS NOT NULL
            AND concluded_at IS NOT NULL AND concluded_at >= declared_at
            AND conclusion_outcome IS NOT NULL)
        OR (status = 'SUPERSEDED' AND concluded_by_ref IS NOT NULL
            AND concluded_at IS NOT NULL AND conclusion_outcome IS NOT NULL)),
    CHECK ((revision = 1 AND supersedes_id IS NULL AND amendment_reason IS NULL)
        OR (revision > 1 AND supersedes_id IS NOT NULL
            AND amendment_reason IS NOT NULL AND btrim(amendment_reason) <> ''))
);
SELECT register_tenant_table('lims_system_validations');

-- ---------------------------------------------------------------------
-- Itens do plano declarado do dossie (chave x descricao, declarados
-- pelo tenant/laboratorio — nenhum item nasce deste arquivo)
-- ---------------------------------------------------------------------
CREATE TABLE lims_system_validation_items (
    tenant_id    UUID NOT NULL,
    validation_id UUID NOT NULL,
    item_key     TEXT NOT NULL CHECK (btrim(item_key) <> ''),
    description  TEXT NOT NULL CHECK (btrim(description) <> ''),
    PRIMARY KEY (tenant_id, validation_id, item_key),
    FOREIGN KEY (tenant_id, validation_id)
        REFERENCES lims_system_validations (tenant_id, id)
);
SELECT register_tenant_table('lims_system_validation_items');

-- ---------------------------------------------------------------------
-- Execucoes dos itens: historico por INSERT (um item pode ter mais
-- de um registo; o vigente e o mais recente — o anterior e
-- preservado, nunca sobrescrito). O resultado e o COMO REGISTADO
-- por quem executou (PASSED/FAILED — dado, sem faixa nesta tabela).
-- ---------------------------------------------------------------------
CREATE TABLE lims_system_validation_executions (
    tenant_id     UUID NOT NULL,
    id            UUID NOT NULL,
    validation_id UUID NOT NULL,
    item_key      TEXT NOT NULL CHECK (btrim(item_key) <> ''),
    outcome       TEXT NOT NULL CHECK (outcome IN ('PASSED', 'FAILED')),
    evidence_ref  TEXT NOT NULL CHECK (btrim(evidence_ref) <> ''),
    executed_by_ref TEXT NOT NULL CHECK (btrim(executed_by_ref) <> ''),
    executed_at   TIMESTAMPTZ NOT NULL,
    note          TEXT,
    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, validation_id, item_key)
        REFERENCES lims_system_validation_items (tenant_id, validation_id, item_key)
);
SELECT register_tenant_table('lims_system_validation_executions');
