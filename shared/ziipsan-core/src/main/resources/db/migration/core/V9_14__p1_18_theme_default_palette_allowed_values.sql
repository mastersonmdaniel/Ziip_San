-- Core Migration — Version: 9.14 (primeira livre apos a V9_13 da P2.28 no
-- grupo V9 do core; verificada livre em todo o repo, incluindo
-- db/pendente/ de todos os modulos — ver pacote PT9-01)
-- Description: PT9-01 (pendencia P-T9-01) — alarga o ALLOWED_VALUES de
-- theme.default_palette, gravado pela V9_10 (P1.18), das 2 paletas
-- originais para as 7 paletas do registro.
--
-- A V9_10 publicada fica INTACTA (migrations publicadas sao imutaveis):
-- a regra nova entra aqui, como a propria V9_10 anuncia ("Padrao novo
-- entra aqui junto do registro (§4.7)"). Como a linha ja existe e o
-- INSERT da V9_10 e ON CONFLICT DO NOTHING, a forma desta migration e
-- um UPDATE idempotente da unica linha alvo — segunda aplicacao produz
-- o mesmo estado, sem erro.
--
-- Fontes: ADR-0017; MARCA-E-TEMAS §4.2 (registro com as 7 paletas apos o
-- pacote temas-9) e §4.7 (padrao novo entra nos dois lugares juntos);
-- KNOWN_PALETTES de UserPreferencesService (os 7 ids, nesta ordem);
-- decisao 69 do dono (10/11/2026), que despachou este pacote para
-- completar as decisoes 63/65: sem esta migration, as 5 paletas novas
-- funcionam como preferencia do utilizador, mas o console de
-- parametros (P1.6) recusa-as como padrao do tenant ("nao esta na
-- lista permitida", TenantParameterService, caso ALLOWED_VALUES).
--
-- Numeracao: V9_14 no location de core. O CoreMigrationRunner
-- (CoreMigrationRunner.java:53) configura outOfOrder(true) para o
-- core, com o precedente declarado no codigo (V3/V4 depois da V5);
-- a disciplina sem out-of-order da P3.12 e do TenantMigrator (serie
-- tenant), nao do core.
--
-- O que esta migration NAO faz:
--   * nao toca em parameter_definitions (nenhuma definicao muda);
--   * nao cria ALLOWED_VALUES para theme.allowed_palettes — por
--     desenho da V9_10, essa chave continua sem esse limite (o valor
--     dela e uma lista, nao um escalar);
--   * nao toca nas linhas de ui.default_locale nem de
--     theme.default_mode.

UPDATE public.parameter_legal_limits
   SET limit_value = '["aqua-blue", "aqua-green", "violet", "amber", "executive-navy", "graphite", "sanitation-green"]'::jsonb,
       norm_source = 'ADR-0017; MARCA-E-TEMAS §4.2/§4.7',
       notes = 'Padroes do registro (Azul Agua, Verde Agua, Violeta, Ambar, Marinho Executivo, Grafite Institucional, Verde Saneamento). Lista alargada de 2 para 7 pela V9_14 (PT9-01), junto do registro (§4.7).',
       updated_at = NOW()
 WHERE definition_key = 'theme.default_palette'
   AND limit_kind = 'ALLOWED_VALUES'
   AND applies_to IS NULL;
