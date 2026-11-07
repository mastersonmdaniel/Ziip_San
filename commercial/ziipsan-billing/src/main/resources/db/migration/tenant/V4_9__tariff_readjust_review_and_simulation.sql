-- Billing Module Migration for Tenant Schema
-- Version: 4.9.0 (P2.29; reserved in plan v3.0 section 3 for P2.29:
-- "versoes da estrutura tarifaria (reajuste por indice, revisao por ato da
-- agencia, copia de versao) e simulacoes de impacto, que nao alteram a base")
-- Description: gestao tarifaria sobre tariff_versions da V4_1: metadados do
-- reajuste (indice, fator X, formula, data-base), da revisao (ato da agencia),
-- da copia (versao de origem), da homologacao (expressa ou tacita) e do metodo
-- de calculo mantido para a transparencia (NR 13, arts. 61 e 62); tabela de
-- registro explicito de simulacoes de impacto; e o catalogo de parametros do
-- reajuste (bill.tariff_readjust ampliado + tariff.readjust_formula,
-- tariff.tacit_approval_days, tariff.history_years_public).
--
-- Depends on: V4_1 (tariffs, tariff_versions com a EXCLUDE de vigencia
-- excl_tariff_versions_no_overlap) e core V3 (public.parameter_definitions).
--
-- DECISOES DE DESENHO
-- 1. NENHUMA tabela nova de versao: reajuste, revisao e copia SAO novas linhas
--    de tariff_versions, entao a vigencia sem sobreposicao continua imposta
--    pela MESMA restricao de exclusao da V4_1 (tenant_id, tariff_id,
--    daterange(valid_from, valid_to, '[]')). Duplicar a estrutura criaria um
--    segundo caminho de vigencia que o motor (TariffEngine/TariffSchedule)
--    nao le.
-- 2. Colunas novas todas ANULAVEIS: versoes criadas antes da P2.29 nao tem
--    tipo de mudanca nem metadado de reajuste, e migration aplicada nao se
--    reescreve. A obrigatoriedade por tipo de mudanca (reajuste exige
--    data-base, indice e formula; revisao exige ato) e regra de dominio
--    (VersionChangeMetadata/TariffManagementService), nao CHECK cruzado:
--    o banco nao conhece o parametro do tenant que diz se a homologacao
--    tacita e prevista na norma da agencia.
-- 3. Trava dos 30 dias TAMBEM no banco, mas so para as linhas novas de
--    reajuste/revisao (chk_tariff_versions_publication_30d): linhas legadas
--    (change_type NULL) e copias em elaboracao ficam fora, porque a V4_1 ja
--    admitia published_on sem antecedencia e endurecer o passado quebraria
--    dado existente. A regra completa (inclusive o piso travado quando o
--    parametro pede menos) vive em TariffPublicationRule e e testada la;
--    published_on + 30 usa aritmetica de DATE com inteiro, imutavel.
-- 4. Simulacao NAO grava na base: o calculo (ImpactSimulator) e puro, sem
--    repositorio. A tabela tariff_impact_simulations guarda apenas o
--    registro EXPLICITO de uma simulacao ja calculada (auditoria e
--    transparencia), em tabela propria, sem FK para bills e sem nenhum
--    caminho de escrita a partir do simulador: gravar nela nao altera
--    tarifa, versao vigente nem conta.
-- 5. bill.tariff_readjust NAO existia no catalogo da core V3: existia so no
--    documento PARAMETROS-DO-TENANT (G1: objeto com data-base, indice, ato e
--    data de publicacao; classe d; aprovacao REG; vigencia >= publicacao +
--    30 dias, travada, NR 10). Esta migration o registra JA AMPLIADO, como
--    pede o aceite da P2.29 (formula, indice, fator X, regra de homologacao
--    tacita e prazo de publicacao), junto das tres chaves novas. Precedente
--    de registro em migration de tenant: secao 7 da V4_4.
-- 6. A metodologia da revisao PERIODICA (receita requerida, base de
--    remuneracao e fatores) NAO entra aqui: ela so entra quando a norma da
--    ANA em consulta publica for aprovada (aceite da P2.29). A revisao desta
--    migration e a revisao por ato da agencia, com os precos que o ato fixa.
-- 7. published_on NULO em linha nova de reajuste/revisao (decisao registrada
--    na revisao da P2.29, NAO apertar nesta migration): o CHECK da decisao 3
--    isenta published_on IS NULL de proposito. Exigir NOT NULL no banco
--    quebraria fluxos legitimos de rascunho/elaboracao que gravam a versao
--    antes da publicacao do ato. A trava vale no caminho de escrita real:
--    o servico de dominio (TariffManagementService/TariffPublicationRule)
--    recusa proposta de reajuste/revisao sem data de publicacao, logo toda
--    linha que nasce pelo fluxo entregue tem published_on. Fica registrado
--    para a integracao: escrita direta por SQL/entidade fora do servico e
--    o unico caminho sem a trava, e uma migration futura so deve fecha-lo
--    se nenhum fluxo de rascunho depender da isencao.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO. Indice, fator X, ato e datas sao politica
-- do prestador regulada pela agencia (ARESC e AGIR editam reajustes por
-- prestador); os INSERTs abaixo sao apenas catalogo de parametro (public.*),
-- como na V4_4.

-- ========================================================================
-- 1. METADADOS DA MUDANCA EM tariff_versions
-- ========================================================================

ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS change_type VARCHAR(20)
    CHECK (change_type IS NULL OR change_type IN ('INITIAL', 'READJUSTMENT', 'REVIEW', 'COPY'));
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS source_version_id UUID;
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS base_date DATE;
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS index_code VARCHAR(20);
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS index_pct NUMERIC(9,4)
    CHECK (index_pct IS NULL OR index_pct > -100);
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS factor_x_pct NUMERIC(9,4)
    CHECK (factor_x_pct IS NULL OR factor_x_pct >= 0);
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS applied_pct NUMERIC(9,4)
    CHECK (applied_pct IS NULL OR applied_pct > -100);
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS readjust_formula VARCHAR(30)
    CHECK (readjust_formula IS NULL OR readjust_formula IN ('INDEX_ONLY', 'INDEX_MINUS_FACTOR_X'));
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS approval_mode VARCHAR(20)
    CHECK (approval_mode IS NULL OR approval_mode IN ('EXPRESS', 'TACIT'));
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS approved_on DATE;
ALTER TABLE tariff_versions ADD COLUMN IF NOT EXISTS calculation_method TEXT;

ALTER TABLE tariff_versions ADD CONSTRAINT fk_tariff_version_source
    FOREIGN KEY (tenant_id, source_version_id) REFERENCES tariff_versions(tenant_id, id);

-- Trava de publicacao (ver decisao 3): reajuste e revisao so vigoram pelo
-- menos 30 dias depois da publicacao. O piso e travado: nenhum parametro o
-- reduz (TariffPublicationRule aplica max(parametro, 30) no dominio).
ALTER TABLE tariff_versions ADD CONSTRAINT chk_tariff_versions_publication_30d
    CHECK (change_type IS NULL
        OR change_type NOT IN ('READJUSTMENT', 'REVIEW')
        OR published_on IS NULL
        OR valid_from >= published_on + 30);

CREATE INDEX IF NOT EXISTS idx_tariff_versions_change
    ON tariff_versions(tenant_id, tariff_id, change_type, valid_from DESC);
CREATE INDEX IF NOT EXISTS idx_tariff_versions_source
    ON tariff_versions(tenant_id, source_version_id) WHERE source_version_id IS NOT NULL;

-- ========================================================================
-- 2. REGISTRO EXPLICITO DE SIMULACOES DE IMPACTO (ver decisao 4)
-- ========================================================================
-- input_snapshot / result_snapshot: JSON serializado pela classe Json do
-- core (a massa simulada e o relatorio de impacto). Sao o metodo e a massa
-- da simulacao, mantidos para a transparencia; nunca sao lidos pelo motor
-- de calculo da conta.

CREATE TABLE IF NOT EXISTS tariff_impact_simulations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    base_version_id UUID NOT NULL,
    proposed_valid_from DATE NOT NULL,
    input_snapshot JSONB NOT NULL,
    result_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE tariff_impact_simulations ADD CONSTRAINT uq_tariff_impact_simulations_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE tariff_impact_simulations ADD CONSTRAINT fk_tis_base_version
    FOREIGN KEY (tenant_id, base_version_id) REFERENCES tariff_versions(tenant_id, id);
CREATE INDEX IF NOT EXISTS idx_tis_base ON tariff_impact_simulations(tenant_id, base_version_id, created_at DESC);

-- ========================================================================
-- 3. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- ========================================================================

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('bill.tariff_readjust', 'OBJECT', 'd', NULL,
     'NR 10 e NR 6 da ANA; PARAMETROS-DO-TENANT G1; P2.29 (GES-01)',
     'REG',
     'Reajuste tarifario do tenant (objeto): {baseDate, indexCode, indexPct, factorXPct, formula, regulatoryAct, publishedOn, applicationDate, tacitApprovalAllowed, publicationMinDays}. Ampliado na P2.29 com formula, indice, fator X, regra de homologacao tacita e prazo de publicacao. Classe d, aprovacao REG. O prazo de publicacao tem piso travado de 30 dias: publicationMinDays abaixo de 30 e recusado na leitura (TariffReadjustParameters) e a vigencia de reajuste/revisao a menos de 30 dias da publicacao e recusada no dominio e no banco. Sem padrao de fabrica: indice e ato sao da agencia do prestador.'),
    ('tariff.readjust_formula', 'ENUM', 'd', NULL,
     'NR 10 e NR 6 da ANA; P2.29 (GES-01)',
     'REG',
     'Formula do reajuste: INDEX_ONLY (reajuste = indice do periodo, a partir da data-base) ou INDEX_MINUS_FACTOR_X (formula parametrica: reajuste = indice - fator X de produtividade). Classe d, aprovacao REG. Sem padrao de fabrica: a formula e a da norma da agencia do prestador.'),
    ('tariff.tacit_approval_days', 'INT', 'd', NULL,
     'Norma da agencia do prestador; P2.29 (GES-01)',
     'REG',
     'Dias apos a publicacao para a homologacao tacita, quando a norma da agencia a preve. Classe d, aprovacao REG. Sem padrao de fabrica e sem a chave a homologacao tacita nao se configura: homologacao tacita sem previsao na norma da agencia e recusada (TariffApprovalRule), mesmo com a chave presente, se bill.tariff_readjust.tacitApprovalAllowed nao for verdadeiro.'),
    ('tariff.history_years_public', 'INT', 'd', '5',
     'NR 13 da ANA, arts. 61 e 62 (transparencia tarifaria); P2.29 (GES-01)',
     'REG',
     'Anos do historico tarifario publico mantido com o metodo de calculo (versoes, atos, indices e metodos), para a transparencia da NR 13. Padrao de fabrica 5 anos; piso travado de 5 (valor menor e recusado). A publicacao no portal e da ED-38; esta chave define a janela que o historico cobre.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('bill.tariff_readjust', 'FLOOR', '30',
     'NR 10 da ANA; PARAMETROS-DO-TENANT G1 (vigencia >= publicacao + 30 dias, travada)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'O FLOOR vale para o campo publicationMinDays do objeto e para a distancia publicacao -> vigencia: parametro nenhum reduz o minimo de 30 dias. Reforcado em TariffPublicationRule (dominio), em TariffReadjustParameters (leitura) e no CHECK chk_tariff_versions_publication_30d (banco, linhas de reajuste/revisao).'),
    ('tariff.readjust_formula', 'ALLOWED_VALUES', '["INDEX_ONLY", "INDEX_MINUS_FACTOR_X"]',
     'NR 10 e NR 6 da ANA; P2.29', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'So as duas formulas implementadas em ReadjustmentCalculator; outra formula so entra com o calculo e o teste dela.'),
    ('tariff.tacit_approval_days', 'MIN_VALUE', '1',
     'P2.29', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Prazo de homologacao tacita, quando configurado, e de ao menos 1 dia. A existencia do prazo nao basta: a norma da agencia precisa prever a homologacao tacita (bill.tariff_readjust.tacitApprovalAllowed).'),
    ('tariff.history_years_public', 'FLOOR', '5',
     'NR 13 da ANA, arts. 61 e 62; P2.29', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Historico publico de pelo menos 5 anos, com o metodo de calculo de cada versao; reduzir abaixo de 5 e recusado em TariffReadjustParameters e em TariffHistory.')
ON CONFLICT DO NOTHING;

-- ========================================================================
-- 4. ISOLAMENTO (RLS)
-- ========================================================================

SELECT register_tenant_table('tariff_impact_simulations');
