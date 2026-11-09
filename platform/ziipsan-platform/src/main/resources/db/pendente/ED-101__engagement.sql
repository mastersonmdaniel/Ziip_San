-- ED-101 — Metas de consumo, comparacao com pares e gamificacao (GES-28).
-- SQL PENDENTE DE NUMERACAO (regra da cascata): nenhum numero Flyway foi
-- confirmado livre para esta tarefa; a integracao numera na camada pela
-- sequencia global (ultimo usado conhecido: V101, correcao P3.26). Sem
-- numero, este arquivo vive em db/pendente/ com o prefixo ED-101__.
--
-- Desenho:
--   * eng_user_enrollments: adesao voluntaria do usuario (aceite) por
--     unidade; revogacao registrada, historico preservado.
--   * eng_consumption_goals: meta definida PELO USUARIO (nenhum valor
--     semeado nem sugerido); uma meta vigente por (tenant, unidade,
--     periodo) — redefinicao substitui pela chave unica.
--   * eng_peer_reports: SOMENTE o resultado agregado publicado ou o motivo
--     da supressao. PRIVACIDADE POR SCHEMA: nao existe coluna para consumo
--     individual de terceiro nem para membros do grupo — a coorte nunca e
--     persistida por esta tarefa.
--   * eng_goal_milestone_awards: marcos concedidos da meta do PROPRIO
--     usuario (codigo do marco configurado pelo tenant); sem pontos, sem
--     ranking — nenhuma tabela de classificacao existe neste desenho.
--   * eng_reminder_log: deduplicacao dos lembretes despachados pela ED-41.
--   * NENHUM dado de tenant e semeado: metas, marcos e modelos sao do
--     tenant/usuario (parametros eng.* / bill.invoice_template).

CREATE TABLE IF NOT EXISTS eng_user_enrollments (
    id           uuid PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    customer_id  uuid NOT NULL,
    unit_id      uuid NOT NULL,
    opted_in_at  timestamptz NOT NULL,
    opted_out_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, customer_id, unit_id),
    CHECK (opted_out_at IS NULL OR opted_out_at >= opted_in_at)
);

CREATE TABLE IF NOT EXISTS eng_consumption_goals (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL,
    customer_id uuid NOT NULL,
    unit_id    uuid NOT NULL,
    period     date NOT NULL,
    target_m3  numeric(12, 3) NOT NULL CHECK (target_m3 > 0),
    defined_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, unit_id, period)
);

CREATE TABLE IF NOT EXISTS eng_peer_reports (
    id                uuid PRIMARY KEY,
    tenant_id         uuid NOT NULL,
    unit_id           uuid NOT NULL,
    period            date NOT NULL,
    group_key         text NOT NULL,
    published         boolean NOT NULL,
    suppression       text CHECK (suppression IN
        ('FEATURE_DISABLED','PEER_COMPARISON_DISABLED','BELOW_MIN_GROUP_SIZE')),
    min_group_size    int NOT NULL CHECK (min_group_size >= 5),
    cohort_average_m3 numeric(12, 4),
    cohort_median_m3  numeric(12, 4),
    own_consumption_m3 numeric(12, 3) NOT NULL CHECK (own_consumption_m3 >= 0),
    position          text CHECK (position IN ('BELOW_AVERAGE','AT_AVERAGE','ABOVE_AVERAGE')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, unit_id, period),
    CHECK ((published AND suppression IS NULL AND cohort_average_m3 IS NOT NULL
            AND cohort_median_m3 IS NOT NULL AND position IS NOT NULL)
        OR (NOT published AND suppression IS NOT NULL AND cohort_average_m3 IS NULL
            AND cohort_median_m3 IS NULL AND position IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_eng_peer_reports_tenant_period
    ON eng_peer_reports (tenant_id, period);

CREATE TABLE IF NOT EXISTS eng_goal_milestone_awards (
    id            uuid PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    customer_id   uuid NOT NULL,
    unit_id       uuid NOT NULL,
    period        date NOT NULL,
    milestone_code text NOT NULL,
    achieved_ratio numeric(7, 4) NOT NULL CHECK (achieved_ratio >= 0),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, unit_id, period, milestone_code)
);

CREATE TABLE IF NOT EXISTS eng_reminder_log (
    id         uuid PRIMARY KEY,
    tenant_id  uuid NOT NULL,
    unit_id    uuid NOT NULL,
    kind       text NOT NULL CHECK (kind IN
        ('GOAL_ACHIEVED','GOAL_EXCEEDED','PEER_REPORT_AVAILABLE')),
    period     date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, unit_id, kind, period)
);

-- CORRECAO R3-B (R3-M1, Rodada 3, padrao G2 / B2-6): tabelas de tenant inscritas no
-- registro de isolamento por tenant (RLS) — uma chamada por tabela, no proprio SQL pendente.
SELECT register_tenant_table('eng_user_enrollments');
SELECT register_tenant_table('eng_consumption_goals');
SELECT register_tenant_table('eng_peer_reports');
SELECT register_tenant_table('eng_goal_milestone_awards');
SELECT register_tenant_table('eng_reminder_log');

-- Parametros do tenant (integrar no catalogo core, migration core tambem
-- sem numero — padrao da ED-38/ED-41):
-- eng.peer_report_enabled      BOOLEAN classe d  fabrica false (opcional por tenant, aceite)
-- eng.peer_group_min_size      INT     classe c  piso 5, so amplia (REUTILIZA o piso da
--                              ED-38/O9-3 de bill.peer_min_units; nao e um segundo piso)
-- eng.gamification_milestones  TEXT    sem fabrica [A DEFINIR pelo tenant] — formato
--                              PROPOSTO "fracao=CODIGO;..." (ver GamificationMilestones)
-- bill.invoice_template        — JA registrada no catalogo core V3 (tipo VERSION,
--                              classe d, sem fabrica; aprovacao por conteudo na P2.32,
--                              bloco do fornecedor travado, CDC art. 42-A): NAO
--                              redefinida aqui; nenhum texto de modelo e semeado.
-- bill.peer_comparison / bill.peer_min_units: definidos pela ED-38 — nao duplicar.
