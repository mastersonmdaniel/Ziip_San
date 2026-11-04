-- Platform Migration for Tenant Schema
-- Version: 1.7.0
-- Description: P1.18 — preferencias do usuario no servidor (tema e idioma).
--
-- Uma linha por (tenant, usuario): o usuario e identificado pelo `sub` do token
-- (Keycloak), nao pelo id da tabela users — um token valido pode nao ter linha em
-- users (o cadastro no Keycloak e manual, P0.13) e a preferencia nao pode depender
-- dele. Sem FK para users por isso; a unicidade (tenant_id, user_subject) e a
-- RLS por tenant (register_tenant_table, V1_2) sao as garantias.
--
-- Colunas guardadas sao a ESCOLHA do usuario, nunca o valor efetivo: quando o
-- tenant retira um padrao da lista dele, o efetivo cai para o padrao do tenant
-- sem apagar a escolha guardada (MARCA-E-TEMAS §4.4) — se o padrao voltar a
-- lista, a escolha volta a valer sem o usuario tocar em nada. Por isso as
-- colunas sao NULLaveis: NULL = "sem escolha guardada, vale o padrao do tenant".
--
-- theme_palette NAO tem CHECK de valores: o registro de padroes e extensivel
-- (MARCA-E-TEMAS §4.7 — um padrao novo entra por arquivo, sem migration). A
-- validacao contra o registro vive no servico (UserPreferencesService), que
-- recusa id desconhecido com 422 em vez de guardar lixo. theme_mode e um
-- dominio fechado (light, dark, system) e tem CHECK.
--
-- locale: so pt-BR e publicado (PLT-25). O servico recusa outro idioma com 422;
-- o padrao do tenant (ui.default_locale, classe d, padrao pt-BR) vive em
-- tenant_settings (MARCA-E-TEMAS §4.4) ou, quando o console (P1.6) o gerencia,
-- numa versao aceita vigente de parameter_versions — o servico le os dois, com
-- a versao vencendo. Nenhuma preferencia e semeada: sem linha, vale o padrao
-- do tenant e, sem ele, Azul Agua no modo do sistema.
--
-- Depende de V1_2 (register_tenant_table). V1_7 estava reservada para a P1.18
-- no mapa Flyway do plano v3.0 §3.

CREATE TABLE IF NOT EXISTS user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    user_subject VARCHAR(255) NOT NULL,
    theme_mode VARCHAR(20) CHECK (theme_mode IN ('light', 'dark', 'system')),
    theme_palette VARCHAR(40),
    locale VARCHAR(20),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE user_preferences ADD CONSTRAINT uq_user_preferences_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_preferences_tenant_subject
    ON user_preferences(tenant_id, user_subject);

SELECT register_tenant_table('user_preferences');
