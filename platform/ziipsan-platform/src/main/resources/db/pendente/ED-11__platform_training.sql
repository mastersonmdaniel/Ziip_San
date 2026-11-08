-- ED-11 -- Implantacao e treinamento: plano, material e ambiente.
--
-- *** NUMERACAO — PRECEDENTE ED-09/ED-14/ED-17 ***
-- O mapa Flyway do plano v3.0 (§3) NAO reserva versao para a ED-11: as
-- tabelas sao do schema `platform` do banco CORE (o mesmo da jornada de
-- implantacao da P1.6, core V9, e da governanca do clone da ED-09), e a
-- ED-11 e posterior a P3.12 — pela regra do §3, versao da sequencia global
-- com o modulo no nome, cujo numero sai na ordem do merge. Este arquivo
-- fica em db/pendente/ e NAO deve ser renomeado por chute: na integracao
-- vira core V<proximo livre>__platform_training.sql, DEPOIS do SQL da
-- ED-09 (a tabela do tenant de pre-treinamento referencia o clone dela).
-- Nenhum numero inventado.
--
-- Conteudo (so governanca do treinamento — nenhum dado pessoal):
--   * platform.training_module_completion: a conclusao de cada modulo do
--     escopo de treinamento de um tenant (plano de 40 h travado por CHECK,
--     horas e unidades entregues, contagens de participantes — ids de
--     usuario vivem no registro de conclusao, nunca nome de participante
--     aqui — e a data de encerramento). CONCLUDED exige as 40 h entregues.
--   * platform.pretraining_tenant: o tenant de pre-treinamento (origem
--     massa sintetica ou clone mascarado da ED-09, marca de sintetico
--     travada em TRUE, varredura da P0.45 e, se clone, a referencia ao
--     tenant_clone da ED-09). READY exige varredura 0. A coerencia
--     origem x clone e travada aqui, na mesma regua do Java/Python:
--     massa sintetica NAO tem clone (clone_id NULL, por CHECK) e clone
--     de treinamento precisa ter finalidade TRAINING no tenant_clone
--     da ED-09 — CHECK nao alcanca outra tabela, entao essa segunda
--     trava e um trigger (ver pretraining_clone_training_guard abaixo,
--     que tambem impede trocar a finalidade de um clone ja em uso).
--   * platform.tenant_customization: o registro de customizacoes do
--     tenant (categoria do dominio da ED-11, situacao e, quando aceita,
--     quem decidiu, quando e a nota de reversao — CHECK que recusa
--     tambem string vazia/so-espacos, como o isBlank do Java).
--
-- Limite declarado (R2 da revisao da ED-11): delivered_units e JSONB e
-- o SQL nao confere o seu conteudo contra as unidades do plano do
-- modulo — essa conferencia e da regua Java (TrainingCompletion); o SQL
-- trava horas, participantes e data, nao o miolo do JSONB.
--
-- A correspondencia matricula <-> codigo substituto NAO aparece aqui:
-- ela fica so no piloto (roteiro da P0.45), como na ED-09.

CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE IF NOT EXISTS platform.training_module_completion (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    module_key VARCHAR(60) NOT NULL,
    planned_hours INT NOT NULL CHECK (planned_hours = 40),
    delivered_hours INT NOT NULL CHECK (delivered_hours >= 0),
    delivered_units JSONB NOT NULL DEFAULT '[]',
    participants_enrolled INT NOT NULL CHECK (participants_enrolled >= 0),
    participants_completed INT NOT NULL CHECK (participants_completed >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'CONCLUDED')),
    concluded_on DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (participants_completed <= participants_enrolled),
    CHECK (status <> 'CONCLUDED' OR delivered_hours >= planned_hours),
    CHECK (status <> 'CONCLUDED' OR participants_enrolled > 0),
    CHECK (status <> 'CONCLUDED' OR participants_completed = participants_enrolled),
    CHECK (status <> 'CONCLUDED' OR concluded_on IS NOT NULL),
    UNIQUE (tenant_id, module_key)
);

CREATE TABLE IF NOT EXISTS platform.pretraining_tenant (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    source VARCHAR(20) NOT NULL CHECK (source IN ('SYNTHETIC_MASS', 'MASKED_CLONE')),
    synthetic BOOLEAN NOT NULL DEFAULT TRUE CHECK (synthetic = TRUE),
    sweep_violation_count INT NOT NULL CHECK (sweep_violation_count >= 0),
    clone_id UUID REFERENCES platform.tenant_clone (id),
    fiscal_production_allowed BOOLEAN NOT NULL DEFAULT FALSE
        CHECK (fiscal_production_allowed = FALSE),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROVISIONED', 'READY', 'REJECTED', 'DISPOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (source <> 'MASKED_CLONE' OR clone_id IS NOT NULL),
    CHECK (source <> 'SYNTHETIC_MASS' OR clone_id IS NULL),
    CHECK (status <> 'READY' OR sweep_violation_count = 0)
);

-- Trava cruzada da finalidade do clone (R1 da revisao da ED-11): um
-- CHECK nao le platform.tenant_clone, entao a recusa do clone com
-- finalidade HOMOLOGATION como ambiente de treinamento — que a regua
-- Java (PreTrainingTenant) e o validador Python ja fazem — e aplicada
-- aqui por trigger, na escrita e na troca de finalidade do clone.
CREATE OR REPLACE FUNCTION platform.pretraining_clone_training_guard()
RETURNS TRIGGER AS $$
DECLARE
    v_purpose VARCHAR(20);
BEGIN
    IF NEW.source = 'MASKED_CLONE' THEN
        SELECT purpose INTO v_purpose
          FROM platform.tenant_clone
         WHERE id = NEW.clone_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'pretraining_tenant: clone % nao encontrado em platform.tenant_clone', NEW.clone_id;
        END IF;
        IF v_purpose <> 'TRAINING' THEN
            RAISE EXCEPTION 'pretraining_tenant: clone % tem finalidade %, mas ambiente de treinamento exige TRAINING (PLT-39)', NEW.clone_id, v_purpose;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_pretraining_clone_training ON platform.pretraining_tenant;
CREATE TRIGGER trg_pretraining_clone_training
    BEFORE INSERT OR UPDATE ON platform.pretraining_tenant
    FOR EACH ROW EXECUTE FUNCTION platform.pretraining_clone_training_guard();

CREATE OR REPLACE FUNCTION platform.tenant_clone_training_purpose_guard()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.purpose <> 'TRAINING'
       AND EXISTS (SELECT 1 FROM platform.pretraining_tenant pt
                    WHERE pt.clone_id = OLD.id) THEN
        RAISE EXCEPTION 'tenant_clone: finalidade nao pode sair de TRAINING enquanto o clone e ambiente de pre-treinamento (ED-11)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_tenant_clone_training_purpose ON platform.tenant_clone;
CREATE TRIGGER trg_tenant_clone_training_purpose
    BEFORE UPDATE OF purpose ON platform.tenant_clone
    FOR EACH ROW EXECUTE FUNCTION platform.tenant_clone_training_purpose_guard();

CREATE INDEX IF NOT EXISTS idx_pretraining_tenant_tenant
    ON platform.pretraining_tenant (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS platform.tenant_customization (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES public.tenant_catalog (id),
    customization_key VARCHAR(80) NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN (
        'PARAMETER', 'BRAND_THEME', 'ACCESS_PROFILE', 'LEGACY_MAPPING',
        'DOCUMENT_TEMPLATE', 'INTEGRATION', 'VERTICAL_MANIFEST')),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    source_ref VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RECORDED', 'PENDING_ACCEPTANCE', 'ACCEPTED')),
    decided_by VARCHAR(120),
    decided_on DATE,
    rollback_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (status <> 'ACCEPTED' OR (decided_by IS NOT NULL
        AND length(trim(decided_by)) > 0 AND decided_on IS NOT NULL
        AND rollback_note IS NOT NULL AND length(trim(rollback_note)) > 0)),
    UNIQUE (tenant_id, customization_key)
);

GRANT SELECT, INSERT, UPDATE ON platform.training_module_completion TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.pretraining_tenant TO ziipsan_app;
GRANT SELECT, INSERT, UPDATE ON platform.tenant_customization TO ziipsan_app;
