-- Tenant Migration (platform)
-- Version: 1.17.0
-- Description: P1.33 — visões salvas de filtros e colunas por usuário
--   (`saved_views`): título, recurso da lista, filtros e colunas em JSONB,
--   ordenação, densidade da tabela e visibilidade.
--
-- DESVIO DOCUMENTADO DO §3 DO PLANO (leia antes de renumerar): o plano v3.0
-- reserva a `V1_10` para as visões salvas da P1.33, mas a `V1_10` JÁ ESTÁ
-- OCUPADA E APLICADA nesta base por `V1_10__users_audit_redaction.sql`
-- (P0.13, integração turno 8 — regras de redação de `users`). Também já
-- ocupadas: `V1_11` (`personal_data_access_log_export`, P1.12) e `V1_16`
-- (`p1_20_documents`, P1.20); `V1_12`–`V1_15` estão reservadas no §3 para
-- P2/P3 (modelos de documento, perfis regulatórios, assinaturas,
-- notificações). A `V1_17` é a primeira versão livre do schema do tenant do
-- módulo platform sem colisão, e o `TenantMigrator` usa `outOfOrder(true)`
-- (decisão G10a), de modo que a ordem de aplicação não depende do número.
-- O registro desta versão no mapa do §3 fica para o coordenador da onda,
-- para não haver edição concorrente do plano por várias frentes da camada C.
--
-- O QUE ESTA MIGRATION DELIBERADAMENTE NÃO CRIA (ordem da frente):
--   * `user_preferences` (P1.18, frente paralela): NÃO é criada nem alterada
--     aqui. A `saved_views` é uma tabela separada, sem FK para ela — o
--     vínculo com o usuário é só o `owner_user_id` (o `sub` do token, o mesmo
--     que o `JwtTenantFilter` grava na GUC `app.current_user`).
--   * `org_units` (P3.30, não existe ainda): NÃO há FK para unidade
--     organizacional. `shared_org_unit_id` é um UUID solto, sem FK, e
--     `visibility = 'ORG_UNIT'` é armazenado mas NÃO lista a visão para
--     outros usuários até a P3.30 existir e definir o escopo por área: sem
--     a tabela de escopo, listar visão alheia como "compartilhada" seria
--     fingir um controle que não existe. Quando a P3.30 chegar, ela cria a
--     FK e a regra de listagem por unidade — sem reescrever esta migration.
--
-- RECURSOS (`resource`): a lista a que a visão se aplica. `global` é a busca
-- global da própria P1.33 (a visão guarda o `q` reaplicável); os demais são
-- as listas existentes. Recurso fora do domínio é recusado pelo CHECK, não
-- por texto livre.
--
-- DENSIDADE (`density`): `comfortable` (padrão, o espaçamento atual das
-- tabelas) ou `compact` (linhas mais baixas). É dado de apresentação da
-- visão, não regra de negócio: o frontend aplica; o backend só valida o
-- domínio.
--
-- FILTROS (`filters`): JSONB com os filtros da lista (ex.: `{"q": "..."}`).
-- Podem conter valor digitado pelo usuário — por isso a coluna ganha regra
-- de redação (REDACT) e classificação ID, como `service_order_notes.body`
-- na V9_1: a trilha de auditoria não guarda o filtro em claro. Nenhum
-- filtro aqui contém documento em claro por construção do frontend (a
-- busca por documento vai pelo corpo do POST da busca, não pela visão),
-- mas a redação vale pela mesma cautela do texto livre.
--
-- NENHUM DADO SEMEADO: nenhuma visão de fábrica. Título obrigatório
-- (CHECK de não-vazio — o equivalente em DDL ao @NotBlank) e único por
-- dono, para dois "Minhas pendências" não se confundirem.
--
-- `register_tenant_table` acrescenta `version` e `tenant_schema` (BaseEntity
-- / TenantAwareEntity), RLS forçada por tenant e o trigger de auditoria.

CREATE TABLE IF NOT EXISTS saved_views (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    owner_user_id UUID NOT NULL,
    title TEXT NOT NULL CHECK (btrim(title) <> ''),
    resource TEXT NOT NULL
        CHECK (resource IN ('customers', 'consumer-units', 'service-orders',
                            'bills', 'documents', 'global')),
    filters JSONB NOT NULL DEFAULT '{}'::jsonb,
    columns JSONB NOT NULL DEFAULT '[]'::jsonb,
    sort JSONB,
    density TEXT NOT NULL DEFAULT 'comfortable'
        CHECK (density IN ('comfortable', 'compact')),
    visibility TEXT NOT NULL DEFAULT 'PRIVATE'
        CHECK (visibility IN ('PRIVATE', 'ORG_UNIT')),
    shared_org_unit_id UUID,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_saved_views_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_saved_views_owner_title UNIQUE (tenant_id, owner_user_id, title)
);

CREATE INDEX IF NOT EXISTS idx_saved_views_tenant_owner
    ON saved_views (tenant_id, owner_user_id);

SELECT register_tenant_table('saved_views');

-- Filtros salvos podem conter valor digitado pelo usuário: redigidos na
-- trilha, como texto livre (precedente V9_1, service_order_notes.body).
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('saved_views', 'filters', 'REDACT', 'filtros salvos podem conter valor digitado pelo usuario')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- Classificação do dado redigido acima (P0.11: toda regra de redação tem
-- classificação; a repetível R__data_classification deriva a mesma linha
-- de `filters` para ID, e esta linha explícita cobre tenants migrados fora
-- de ordem, no mesmo precedente da V9_1).
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('saved_views', 'filters', 'ID', 'filtros salvos podem conter valor digitado pelo usuario')
ON CONFLICT (table_name, column_name) DO NOTHING;
