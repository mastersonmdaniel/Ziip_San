-- Catalogo global de parametros do tenant (P0.19) e limites legais do §2.3 (P0.20)
--
-- O catalogo pertence ao produto, nao ao tenant: as definicoes e os limites sao os mesmos
-- para todos os tenants. Por isso as tabelas vivem em public (core), e nao no schema do
-- tenant. A aplicacao (ziipsan_app) so tem SELECT aqui: quem escreve no catalogo e o
-- migrador, numa migration, nunca a aplicacao em runtime. Os VALORES por tenant (com
-- vigencia, proposta, aprovacao e aceite) vivem no schema do tenant, na V1_5.
--
-- O que esta versao NAO faz (fica para as tarefas citadas):
--   * tela do tenant para propor/aprovar/aceitar (P1.6);
--   * carga dos perfis das agencias (P2.31): os tetos por agencia (ex.: o teto diario de
--     juros de SC) chegam como valores de perfil, nao como trava global;
--   * aplicacao dos encargos na conta (P2.5) e conteudo minimo da fatura (P2.32).
--
-- Nenhum dado regulatorio e inventado aqui: cada definicao e cada limite cita a norma
-- de origem que o plano v3.0 e o PARAMETROS-DO-TENANT ja registram. Onde o plano diz
-- "a confirmar" ou "a definir", a linha registra a pendencia em vez de fixar um numero:
--   * teto absoluto da multa acima de 2% (PARAMETROS §7, P0): pendente do dono;
--   * minimo de dias de vencimento para autarquia (PARAMETROS G1): pendente;
--   * classe de registry.require_user_document: o plano v3.0 nao a fixa; param_class NULL
--     com a pendencia registrada na descricao (o padrao de fabrica true preserva o
--     comportamento atual, o mais restritivo).
--
-- Core V3 chega depois da V5 ja aplicada em bancos existentes: o CoreMigrationRunner
-- usa outOfOrder(true) (mesma decisao do TenantMigrator, G10a), porque as versoes
-- reservadas do core (V3, V4, ...) nao seguem a ordem de aplicacao.

CREATE TABLE IF NOT EXISTS public.parameter_definitions (
    definition_key TEXT PRIMARY KEY,
    value_type TEXT NOT NULL CHECK (value_type IN (
        'TEXT', 'INT', 'DECIMAL', 'PERCENT', 'BOOLEAN', 'ENUM',
        'LIST_INT', 'LIST_TEXT', 'OBJECT', 'TABLE', 'VERSION')),
    -- Classe do ADR-0015: a travado, b padrao de fabrica com aceite fora do padrao,
    -- c so ampliavel, d livre. NULL = classe nao fixada no plano (pendencia registrada
    -- na descricao); a validacao de limite nao depende da classe.
    param_class CHAR(1) CHECK (param_class IN ('a', 'b', 'c', 'd')),
    factory_default JSONB,
    norm_source TEXT,
    approver_area TEXT,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.parameter_legal_limits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_key TEXT NOT NULL REFERENCES public.parameter_definitions(definition_key),
    limit_kind TEXT NOT NULL CHECK (limit_kind IN (
        'MIN_VALUE', 'MAX_VALUE', 'MIN_COUNT', 'MAX_COUNT',
        'ALLOWED_VALUES', 'FLOOR', 'REFERENCE', 'LOCKED', 'METHODOLOGY')),
    limit_value JSONB,
    norm_source TEXT NOT NULL,
    -- A quem o limite se aplica, quando nao e global (ex.: 'CONCESSIONARIA', 'SC').
    applies_to TEXT,
    -- Classe b acima do padrao: os quatro requisitos andam juntos (P0.20, fee.fine_pct).
    above_max_requires_norm BOOLEAN NOT NULL DEFAULT FALSE,
    above_max_requires_dual_approval BOOLEAN NOT NULL DEFAULT FALSE,
    above_max_requires_formal_acceptance BOOLEAN NOT NULL DEFAULT FALSE,
    above_max_risk_alert BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_parameter_legal_limits_key_kind
    ON public.parameter_legal_limits(definition_key, limit_kind, COALESCE(applies_to, ''));

-- Catalogo so de leitura para a aplicacao (o migrador, dono das tabelas, escreve).
GRANT SELECT ON public.parameter_definitions TO ziipsan_app;
GRANT SELECT ON public.parameter_legal_limits TO ziipsan_app;

COMMENT ON TABLE public.parameter_definitions IS
    'Catalogo global de parametros do tenant (P0.19): tipo, classe, padrao de fabrica e norma de origem';
COMMENT ON TABLE public.parameter_legal_limits IS
    'Limites legais do §2.3 (P0.20) por chave de parametro, cada um com a norma de origem';

-- =======================================================================
-- Definicoes (P0.19 + P0.20)
-- =======================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('tenant.timezone', 'TEXT', 'd', '"America/Sao_Paulo"',
     'IANA Time Zone Database',
     NULL,
     'Fuso do tenant: identificador IANA. Instantes sao gravados em UTC; datas de negocio sao a data local do tenant. Padrao de fabrica America/Sao_Paulo.'),
    ('registry.require_user_document', 'BOOLEAN', NULL, 'true',
     NULL,
     NULL,
     'Documento do usuario obrigatorio no cadastro. Pendencia: o plano v3.0 (P0.19) nao fixa a classe deste parametro; o padrao true preserva o comportamento atual (mais restritivo). A partir da V1_5, o valor vigente vem de parameter_versions com aceite, e nao mais de tenant_settings.'),
    ('cert.deactivation_keeps_records', 'BOOLEAN', 'a', 'true',
     'ADR-0041, item 5',
     NULL,
     'Desligar a norma nao apaga registro (travado, classe a).'),
    ('cert.evidence_pack_personal_data', 'BOOLEAN', 'a', 'false',
     'ADR-0041, item 10',
     NULL,
     'Pacote de evidencia contem dado pessoal: falso por padrao (travado, classe a); o pacote leva dado agregado, mascarado ou pseudonimizado.'),
    ('cert.scope_statement', 'TEXT', 'd', NULL,
     'ADR-0041, item 6',
     NULL,
     'Texto do escopo e do local da norma ligada (tenant, laboratorio, instalacao), versionado.'),
    ('cert.record_retention_years', 'INT', 'c', NULL,
     'Maior prazo do G6 que o registro comprova (PARAMETROS-DO-TENANT, certificacoes)',
     NULL,
     'Retencao de registro do sistema de gestao sem lei especifica; so amplia.'),
    ('cert.external_auditor_access', 'BOOLEAN', 'b', 'false',
     'ADR-0041, item 10',
     NULL,
     'Papel temporario de auditor externo, somente leitura, com prazo e registro de cada leitura; desligado por padrao.'),
    ('cert.internal_target_vs_legal', 'TEXT', 'c', NULL,
     'ADR-0041, item 9 (a lei vence)',
     NULL,
     'Meta interna comparada ao prazo legal: nunca mais frouxa que o prazo legal.'),
    ('cert.risk_scale', 'OBJECT', 'd', NULL,
     'PARAMETROS-DO-TENANT, certificacoes',
     NULL,
     'Escala e matriz de risco do tenant; matriz 5x5 do produto.'),
    ('cert.audit_cycle', 'INT', 'd', NULL,
     'PARAMETROS-DO-TENANT, certificacoes',
     NULL,
     'Intervalo do programa de auditoria interna; unidade definida na P3.31 (pendencia).'),
    ('cert.iso9001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 9001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso14001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 14001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso45001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 45001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso55001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 55001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso27001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO/IEC 27001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso27701.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO/IEC 27701 ligada ou desligada; desligada por padrao.'),
    ('cert.iso17025.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO/IEC 17025 ligada ou desligada; desligada por padrao.'),
    ('cert.iso46001.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 46001 ligada ou desligada; desligada por padrao.'),
    ('cert.iso24510.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 24510 ligada ou desligada; desligada por padrao.'),
    ('cert.iso24511.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 24511 ligada ou desligada; desligada por padrao.'),
    ('cert.iso24512.enabled', 'BOOLEAN', 'd', 'false', 'ADR-0041', NULL, 'Norma ISO 24512 ligada ou desligada; desligada por padrao.'),
    ('cert.iso9001.edition', 'ENUM', 'd', NULL, 'ADR-0041, item 7', NULL, 'Edicao da ISO 9001 em uso: 2015 ou 2026 (2026 e o alvo, 2015 so em transicao).'),
    ('cert.iso14001.edition', 'ENUM', 'd', NULL, 'ADR-0041, item 7', NULL, 'Edicao da ISO 14001 em uso: 2015 ou 2026 (2026 e o alvo, 2015 so em transicao).'),
    ('cert.iso45001.edition', 'ENUM', 'd', '"2018"', 'ADR-0041, item 7', NULL, 'Edicao da ISO 45001 em uso: 2018.'),
    ('cert.iso55001.edition', 'ENUM', 'd', '"2024"', 'ADR-0041, item 7', NULL, 'Edicao da ISO 55001 em uso: 2024.'),
    ('cert.iso27001.edition', 'ENUM', 'd', '"2022"', 'ADR-0041, item 7', NULL, 'Edicao da ISO/IEC 27001 em uso: 2022, com a Emenda 1:2024.'),
    ('cert.iso27701.edition', 'ENUM', 'd', NULL, 'ADR-0041, item 7', NULL, 'Edicao da ISO/IEC 27701 em uso: 2019 ou 2025 (2025 e o alvo, 2019 so em transicao).'),
    ('cert.iso17025.edition', 'ENUM', 'd', NULL, 'ADR-0041, item 7', NULL, 'Edicao da ISO/IEC 17025 em uso; edicoes no catalogo da P1.32 (core V9_8).'),
    ('cert.iso46001.edition', 'ENUM', 'd', NULL, 'ADR-0041', NULL, 'Edicao da ISO 46001 em uso; edicoes no catalogo da P1.32 (core V9_8).'),
    ('cert.iso24510.edition', 'ENUM', 'd', NULL, 'ADR-0041', NULL, 'Edicao da ISO 24510 em uso; edicoes no catalogo da P1.32 (core V9_8).'),
    ('cert.iso24511.edition', 'ENUM', 'd', NULL, 'ADR-0041', NULL, 'Edicao da ISO 24511 em uso; edicoes no catalogo da P1.32 (core V9_8).'),
    ('cert.iso24512.edition', 'ENUM', 'd', NULL, 'ADR-0041', NULL, 'Edicao da ISO 24512 em uso; edicoes no catalogo da P1.32 (core V9_8).'),
    ('cal.holidays', 'TABLE', 'c', NULL,
     'Calendario oficial nacional, estadual e municipal',
     'OPE',
     'Feriados (nacional, estadual, municipal) e pontos facultativos do tenant, na tabela holidays da V1_5. Nacionais nao se removem; so acrescenta.'),
    ('bill.due_date_options', 'LIST_INT', 'c', NULL,
     'Lei 8.987/1995, art. 7º-A (incluido pela Lei 9.791/1999)',
     'REG',
     'Dias de vencimento ofertados. Para concessionaria, ao menos 6; so amplia (reduzir a lista aceita e recusado).'),
    ('meter.average_months', 'INT', 'd', NULL,
     'NR 11, art. 35',
     'REG',
     'Meses da media sem leitura. Referencia convergente das ERIs de SC: 6 meses (P2.5, RAS-14) — referencia, nao trava global; o teto da ERI chega no perfil da P2.31.'),
    ('meter.max_estimated_cycles', 'INT', 'd', NULL,
     'NR 11, art. 35',
     'REG',
     'Teto de ciclos estimados sem leitura. Referencia convergente das ERIs de SC: ate 3 ciclos (P2.5, RAS-14) — referencia, nao trava global.'),
    ('fee.fine_pct', 'PERCENT', 'b', '2.00',
     'CDC, art. 52, §1º (Lei 8.078/1990, redacao da Lei 9.298/1996)',
     'JUR',
     'Multa de mora, percentual unico. Padrao de fabrica 2%. Acima de 2% exige norma citada, aprovacao dupla, aceite formal registrado e alerta de risco.'),
    ('fee.interest_mode', 'ENUM', 'b', '"DAILY_RATE_WITH_CAP"',
     'Juros simples, sem capitalizacao (travado); teto da agencia',
     'JUR',
     'Modo dos juros de mora: por dia com teto da agencia, por dia sem teto, ou mensal pro rata. CALENDAR_MONTH (mes cheio, modo do GSAN) nao entra no catalogo P0.20: nao implementado, decisao pendente do dono.'),
    ('fee.interest_daily_pct', 'PERCENT', 'b', NULL,
     'Teto da agencia; referencia SC: 0,033% ao dia',
     'JUR',
     'Taxa diaria de juros de mora. O valor de SC (0,033%/dia) e referencia documentada, nao trava global; travas por agencia chegam nos perfis da P2.31.'),
    ('fee.legal_rate_fallback', 'BOOLEAN', 'd', 'false',
     'CC, art. 406 (Lei 10.406/2002), redacao da Lei 14.905/2024; metodologia da Res. CMN 5.171/2024; taxa divulgada mensalmente pelo BCB',
     'JUR',
     'Taxa legal do art. 406 do Codigo Civil quando nada foi contratado. Desligado por padrao; aplicacao a tarifa a confirmar (PARAMETROS G5).'),
    ('bill.invoice_template', 'VERSION', 'd', NULL,
     'Lei 11.445/2007, art. 39, paragrafo unico; CDC, art. 42-A',
     'REG',
     'Modelo de fatura aprovado pela ERI. O bloco do fornecedor e travado na fatura (CDC, art. 42-A); a emissao com o bloco e da P2.32.'),
    ('ret.billing_history_months', 'INT', 'c', '120',
     'NR 11, art. 22',
     'DPO+JUR',
     'Historico de leitura e faturamento, em meses. Piso 120; so amplia, tambem quando a ERI nao adotou a NR 11.')
ON CONFLICT (definition_key) DO NOTHING;

-- =======================================================================
-- Limites legais (P0.20): cada limite travado tem norma de origem
-- =======================================================================

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('cert.deactivation_keeps_records', 'LOCKED', 'true', 'ADR-0041, item 5', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Desligar nunca apaga registro.'),
    ('cert.evidence_pack_personal_data', 'LOCKED', 'false', 'ADR-0041, item 10', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Pacote de evidencia sem dado pessoal por padrao.'),
    ('cal.holidays', 'LOCKED', '"NATIONAL_IMMUTABLE"', 'Calendario oficial', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Feriados nacionais nao se removem; so acrescenta. Nao ha semente nacional nesta versao: a pre-carga nacional e trabalho futuro, fora do P0.20.'),
    ('bill.due_date_options', 'MIN_COUNT', '6', 'Lei 8.987/1995, art. 7º-A', 'CONCESSIONARIA',
     FALSE, FALSE, FALSE, FALSE, 'Ao menos 6 dias para concessionaria; so amplia. Minimo para autarquia: a confirmar (PARAMETROS G1, pendencia).'),
    ('meter.average_months', 'REFERENCE', '6', 'NR 11, art. 35', 'SC',
     FALSE, FALSE, FALSE, FALSE, 'Valores convergentes das ERIs de SC (P2.5, RAS-14). Referencia, nao trava: a classe e d.'),
    ('meter.max_estimated_cycles', 'REFERENCE', '3', 'NR 11, art. 35', 'SC',
     FALSE, FALSE, FALSE, FALSE, 'Valores convergentes das ERIs de SC (P2.5, RAS-14). Referencia, nao trava: a classe e d.'),
    ('fee.fine_pct', 'MAX_VALUE', '2.00', 'CDC, art. 52, §1º (Lei 8.078/1990)', NULL,
     TRUE, TRUE, TRUE, TRUE, 'Acima de 2%: norma citada, aprovacao dupla (quem propoe nao aprova), aceite formal registrado e alerta de risco. Teto absoluto acima de 2%, se houver: a definir pelo dono (PARAMETROS §7, P0) — nenhum teto absoluto nesta versao.'),
    ('fee.interest_mode', 'ALLOWED_VALUES', '["DAILY_RATE_WITH_CAP", "DAILY_RATE", "MONTHLY_PRO_RATA"]', 'Juros simples, sem capitalizacao (travado)', NULL,
     FALSE, FALSE, FALSE, FALSE, 'CALENDAR_MONTH fora do catalogo P0.20: modo do GSAN (mes cheio) nao implementado, decisao pendente do dono.'),
    ('fee.interest_daily_pct', 'REFERENCE', '0.033', 'Teto da agencia (SC)', 'SC',
     FALSE, FALSE, FALSE, FALSE, 'Referencia documentada em PARAMETROS G5. Nao e trava global; travas por agencia chegam nos perfis da P2.31.'),
    ('fee.legal_rate_fallback', 'METHODOLOGY', NULL, 'Res. CMN 5.171/2024; taxa divulgada mensalmente pelo BCB', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Quando ligado e nada foi contratado, a taxa e a do BCB do mes de referencia, pela metodologia da Res. CMN 5.171/2024. Mes sem taxa divulgada: erro explicito, nunca taxa inventada.'),
    ('bill.invoice_template', 'LOCKED', '"supplier_block"', 'CDC, art. 42-A', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Bloco do fornecedor travado na fatura; ausencia invalida a fatura. A emissao e da P2.32.'),
    ('ret.billing_history_months', 'FLOOR', '120', 'NR 11, art. 22', NULL,
     FALSE, FALSE, FALSE, FALSE, 'Piso 120 meses; so amplia. Reduzir abaixo de 120 (ou abaixo do valor aceito) e recusado.'),
    ('cert.iso9001.edition', 'ALLOWED_VALUES', '["2015", "2026"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO 9001: 2015 ou 2026.'),
    ('cert.iso14001.edition', 'ALLOWED_VALUES', '["2015", "2026"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO 14001: 2015 ou 2026.'),
    ('cert.iso45001.edition', 'ALLOWED_VALUES', '["2018"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO 45001: 2018.'),
    ('cert.iso55001.edition', 'ALLOWED_VALUES', '["2024"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO 55001: 2024.'),
    ('cert.iso27001.edition', 'ALLOWED_VALUES', '["2022"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO/IEC 27001: 2022, com a Emenda 1:2024.'),
    ('cert.iso27701.edition', 'ALLOWED_VALUES', '["2019", "2025"]', 'ADR-0041, item 7', NULL,
     FALSE, FALSE, FALSE, FALSE, 'ISO/IEC 27701: 2019 ou 2025.')
ON CONFLICT DO NOTHING;
