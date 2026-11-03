-- Limites do §2.3 no banco (P0.20): multa acima de 2% e juros diarios
-- Version: 6.6.0
--
-- POR QUE UMA VERSAO NOVA, E NAO EDICAO DO V6_1: o plano v3.0 pede "o CHECK de V6_1
-- ajustado", mas as migrations existentes nao sao reescritas (o checksum do Flyway
-- mudaria para tenants ja provisionados), e a faixa V6_2-V6_5 esta reservada para a
-- P3 (conciliacao/Pix, particao, debito automatico, reembolsos). V6_6 e a primeira
-- versao livre do modulo e carrega o ajuste. O TenantMigrator ja usa outOfOrder(true).
--
-- 1. Multa: o CHECK antigo do V6_1 continua valendo (teto proprio so com
--    justificativa) e o CHECK novo abaixo o aperta: a conjuncao dos dois e a regra
--    P0.20.
-- 2. Juros: o CHECK de dominio do V6_1 so aceitava NONE/SIMPLE_PER_DAY, entao o modo
--    diario (fee.interest_mode = DAILY_RATE_WITH_CAP, ex.: SC 0,033% ao dia) nao era
--    expressavel na politica. Aqui a constraint de dominio e substituida (o nome e
--    localizado dinamicamente; se nao for achado, a migration falha alto em vez de
--    seguir com a restricao antiga em silencio).
--
-- REGRA (PARAMETROS G5, classe b; ADR-0002): multa padrao 2% (CDC art. 52, §1º).
-- Teto proprio ACIMA de 2% so vale com, juntos: norma citada, quem propoe diferente
-- de quem aprova, aceite formal registrado (referencia do aceite em
-- parameter_acceptances, V1_5) e alerta de risco reconhecido. Justificativa em texto
-- livre, sozinha, nao basta mais — era o que o V6_1 aceitava.
-- Teto absoluto acima de 2%, se houver: a definir pelo dono (PARAMETROS §7, P0).
-- Nenhum teto absoluto e fixado aqui.
--
-- As colunas novas sao NULL por padrao: politicas com teto ate 2% nao precisam delas,
-- e politicas antigas com teto acima de 2% e so justificativa passam a FALHAR neste
-- CHECK ate que o tenant registre o aceite — o que e o comportamento pretendido
-- (o aceite nao pode ser presumido a partir de um texto livre).

ALTER TABLE collection_policies
    ADD COLUMN IF NOT EXISTS fine_cap_norm_citation VARCHAR(300),
    ADD COLUMN IF NOT EXISTS fine_cap_proposed_by UUID,
    ADD COLUMN IF NOT EXISTS fine_cap_approved_by UUID,
    ADD COLUMN IF NOT EXISTS fine_cap_acceptance_ref VARCHAR(64),
    ADD COLUMN IF NOT EXISTS fine_cap_risk_alert_ack BOOLEAN;

ALTER TABLE collection_policies ADD CONSTRAINT chk_fine_cap_above_legal
    CHECK (
        fine_cap_percent IS NULL
        OR fine_cap_percent <= 2.00
        OR (
            fine_cap_norm_citation IS NOT NULL AND length(trim(fine_cap_norm_citation)) > 0
            AND fine_cap_proposed_by IS NOT NULL
            AND fine_cap_approved_by IS NOT NULL
            AND fine_cap_proposed_by <> fine_cap_approved_by
            AND fine_cap_acceptance_ref IS NOT NULL AND length(trim(fine_cap_acceptance_ref)) > 0
            AND fine_cap_risk_alert_ack = TRUE
        )
    );

-- =======================================================================
-- Juros diarios (fee.interest_mode = DAILY_RATE_WITH_CAP / DAILY_RATE)
-- =======================================================================

ALTER TABLE collection_policies
    ADD COLUMN IF NOT EXISTS interest_daily_percent NUMERIC(7,4)
        CHECK (interest_daily_percent IS NULL OR interest_daily_percent >= 0);

-- Substitui o CHECK de dominio do V6_1 (NONE/SIMPLE_PER_DAY) por um que inclui
-- DAILY_RATE, com a mesma coerencia dos demais modos. A constraint de dominio e a
-- CHECK de interest_mode que NAO menciona interest_monthly_percent (a de coerencia
-- menciona, e fica intacta — mas ela so conhece NONE/SIMPLE_PER_DAY, entao e
-- substituida junto, com a mesma forma, mais o ramo DAILY_RATE).
DO $$
DECLARE
    v_domain TEXT;
    v_coherence TEXT;
BEGIN
    SELECT conname INTO v_domain FROM pg_constraint
    WHERE conrelid = 'collection_policies'::regclass AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%interest_mode%'
      AND pg_get_constraintdef(oid) NOT LIKE '%interest_monthly_percent%';
    SELECT conname INTO v_coherence FROM pg_constraint
    WHERE conrelid = 'collection_policies'::regclass AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%interest_mode%'
      AND pg_get_constraintdef(oid) LIKE '%interest_monthly_percent%';
    IF v_domain IS NULL OR v_coherence IS NULL THEN
        RAISE EXCEPTION 'V6_6: CHECKs de interest_mode do V6_1 nao localizados (dominio=%, coerencia=%)',
            v_domain, v_coherence;
    END IF;
    EXECUTE format('ALTER TABLE collection_policies DROP CONSTRAINT %I', v_domain);
    EXECUTE format('ALTER TABLE collection_policies DROP CONSTRAINT %I', v_coherence);
END $$;

ALTER TABLE collection_policies ADD CONSTRAINT chk_collection_policies_interest_mode
    CHECK (interest_mode IN ('NONE', 'SIMPLE_PER_DAY', 'DAILY_RATE'));
ALTER TABLE collection_policies ADD CONSTRAINT chk_collection_policies_interest_coherence
    CHECK ((interest_mode = 'NONE' AND interest_monthly_percent IS NULL
            AND interest_month_basis_days IS NULL AND interest_cap_percent IS NULL
            AND interest_daily_percent IS NULL)
        OR (interest_mode = 'SIMPLE_PER_DAY' AND interest_monthly_percent IS NOT NULL
            AND interest_month_basis_days IS NOT NULL AND interest_month_basis_days BETWEEN 1 AND 31
            AND interest_daily_percent IS NULL)
        OR (interest_mode = 'DAILY_RATE' AND interest_daily_percent IS NOT NULL
            AND interest_monthly_percent IS NULL AND interest_month_basis_days IS NULL));
