-- GIS-02 (P1.27) — aprovacao de tipo e item do catalogo pelo tenant.
--
-- gis_feature_type ja tinha approval_status (V13_1) sem autor/aprovador; gis_catalog_item
-- nao tinha nenhum dos tres. Sem estas colunas, "pendente ate aprovacao por outra pessoa"
-- (GIS-CADASTRO-TECNICO §4.1) nao seria verificavel no banco.
--
-- Regra no CHECK (mesmo desenho da V1_5 de parametros): APROVADO com autor conhecido
-- exige aprovador diferente do autor. Linha de fabrica nasce APROVADA sem autor (o
-- catalogo de fabrica e aprovado pela curadoria, nao por um usuario do tenant), por
-- isso o CHECK so morde quando proposed_by nao e nulo. Nada aqui altera active,
-- origin, factory_code, standard_ref ou reference_status de linha existente.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

ALTER TABLE gis_feature_type
    ADD COLUMN IF NOT EXISTS proposed_by UUID,
    ADD COLUMN IF NOT EXISTS approved_by UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    -- Carimbo de retirada de fabrica (§4.1), espelhando o do item: tipo retirado
    -- continua valido nas feicoes existentes, nunca e apagado.
    ADD COLUMN IF NOT EXISTS factory_retired_on DATE;

ALTER TABLE gis_catalog_item
    ADD COLUMN IF NOT EXISTS approval_status TEXT,
    ADD COLUMN IF NOT EXISTS proposed_by UUID,
    ADD COLUMN IF NOT EXISTS approved_by UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;

DO $$
BEGIN
    -- Escopo de schema: pg_constraint e global no banco e o tenant compartilhado dos
    -- testes (ProvisionedTenant) ja tem este nome; sem o filtro, o segundo schema
    -- a rodar a V13_4 ficaria sem o CHECK.
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.conname = 'ck_gis_feature_type_approval' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE gis_feature_type ADD CONSTRAINT ck_gis_feature_type_approval
            CHECK ((approval_status IS NULL
                OR approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
            -- Aprovado com autor conhecido exige aprovador diferente do autor.
            AND (approval_status <> 'APPROVED' OR proposed_by IS NULL
                OR (approved_by IS NOT NULL AND approved_by <> proposed_by)));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE c.conname = 'ck_gis_catalog_item_approval' AND n.nspname = current_schema()
    ) THEN
        ALTER TABLE gis_catalog_item ADD CONSTRAINT ck_gis_catalog_item_approval
            CHECK ((approval_status IS NULL
                OR approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
            AND (approval_status <> 'APPROVED' OR proposed_by IS NULL
                OR (approved_by IS NOT NULL AND approved_by <> proposed_by)));
    END IF;
END
$$;

-- Backfill: linhas anteriores ao workflow de aprovacao nao sao propostas pendentes;
-- sao dados migrados (V13_1/V13_2) e nascem APROVADOS. Linhas de tenant criadas
-- depois disso passam sempre pela API, que as cria PENDING.
UPDATE gis_feature_type SET approval_status = 'APPROVED'
WHERE approval_status IS NULL;
UPDATE gis_catalog_item SET approval_status = 'APPROVED'
WHERE approval_status IS NULL;
