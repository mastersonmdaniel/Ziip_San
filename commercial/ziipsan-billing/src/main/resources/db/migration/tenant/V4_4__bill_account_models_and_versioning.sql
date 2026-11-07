-- Billing Module Migration for Tenant Schema
-- Version: 4.4.0 (P2.5, V4_4 FUNDIDA na correcao da revisao cruzada: frentes F2, F3,
-- F5 e o bloco B da F4; correcoes B1, B2-DDL, B5, B6 e B8 aplicadas)
-- Description: modelos de conta (bill_templates e suas versoes), os campos novos da
-- conta (parameter_version_ids, billed_customer_id, billed_customer_snapshot cifrado,
-- fixed_part_value), os catalogos debit_types/credit_origins e a proveniencia tipada
-- dos recebiveis (DAD-20), o enquadramento em categorias opcionais da NR 13 e o
-- registro das chaves de parametro das frentes no catalogo global.
--
-- Depende de: V4_1/V4_14/V4_15 (bills particionada, com o CHECK do total nomeado
-- chk_bills_total_with_withholding_tax) e V3_1 (customers, consumer_units).
--
-- O QUE ESTA MIGRATION FAZ
-- 1. Modelos de conta (F2): bill_templates + bill_template_versions, no mesmo
--    desenho de tariffs/tariff_versions da V4_1 (vigencia por data com exclusao de
--    sobreposicao). O parametro bill.invoice_template (catalogo da core V3, tipo
--    VERSION) aponta para uma versao destas tabelas; o conteudo minimo da fatura
--    e a emissao sao da P2.32.
-- 2. Conta (F2, DAD-15): bills ganha parameter_version_ids (coluna propria, alem
--    do tariff_snapshot), billed_customer_id (o cliente do vinculo show_on_bill
--    vigente na emissao) e billed_customer_snapshot (nome, documento MASCARADO e
--    endereco de entrega, serializados em JSON pela classe Json do core e
--    CIFRADOS com o FieldCipher do ziipsan-security, contexto
--    bills.billed_customer_snapshot). A coluna guarda SO o texto cifrado
--    (base64): nunca o JSON em claro. Conta sem titular (unidade sem usuario
--    vigente, P1.41) nasce com os dois campos NULL -- o par anda junto (CHECK
--    chk_bills_billed_customer_pair) e a fatura fica retida ate a definicao do
--    responsavel; snapshot parcial e recusado.
-- 3. Linhas de consumo (DAD-14): MOVIDO para a V5_6 do metering (correcao B1).
--    Ver o comentario-ponteiro na secao 3.
-- 4. Catalogos e proveniencia (F3, DAD-20): debit_types (com generates_bill) e
--    credit_origins substituem o texto livre de debit_receivables,
--    credit_receivables, bill_debits e bill_credits; os recebiveis ganham
--    source_type/source_id (6 valores do aceite), legacy_reference,
--    service_order_id, accounting_month, collection_month e financing_term.
--    Estrutura executavel da correcao B6: catalogos e colunas ANULAVEIS,
--    backfill deterministico das linhas da conta, guarda que aborta com
--    mensagem clara se houver linha legada nao mapeada, e so entao SET NOT
--    NULL e DROPs (ver o cabecalho da secao 4).
-- 5. Componente fixo (F5, RAS-22; correcao B2-DDL): bills ganha fixed_part_value
--    (TFDI, Res. ARESC 105/2018; Lei 11.445/2007, art. 45) e os DOIS CHECKs
--    vigentes do total/creditos sao derrubados e recriados com a parcela fixa
--    na formula (ver o cabecalho da secao 5).
-- 6. Categorias opcionais (F5, RAS-13; correcoes B5/B8): a tabela
--    optional_category_enrollments registra o enquadramento da unidade em
--    categoria opcional da NR 13, art. 6o, com entidade e repositorio no
--    billing, e classificacao de dados + redacao de auditoria para criterion
--    e proof (secao 8).
-- 7. Parametros no catalogo global (public.parameter_definitions, mesmo dono
--    das regras de redacao): bill.holder_name_display (F2, secao 7.1),
--    bill.partial_period_rule e meter.after_cap_rule (bloco B da F4, secao
--    7.2) e as 19 chaves da F5 (secao 7.3). O bloco A da F4 (coluna em
--    reading_anomalies, tabela do metering) NAO esta aqui: virou a migration
--    V5_7 do metering.
-- 8. Isolamento, classificacao de dados e redacao de auditoria das tabelas e
--    colunas novas (padrao da core V2 e da V4_2/V5_2): o snapshot cifrado e
--    OMIT na trilha -- nem o ciphertext pode aparecer no audit_log.
--
-- NENHUM DADO DE NEGOCIO E SEMEADO. Modelo de conta, layout, vigencia, tipo de
-- debito e origem de credito sao politica do prestador; os INSERTs abaixo sao
-- apenas catalogo de parametro, classificacao e regra de redacao (precedente:
-- V4_2 e core V3).

-- ========================================================================
-- 1. MODELOS DE CONTA
-- ========================================================================

CREATE TABLE IF NOT EXISTS bill_templates (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE bill_templates ADD CONSTRAINT uq_bill_templates_tenant_id UNIQUE (tenant_id, id);
CREATE UNIQUE INDEX idx_bill_templates_code ON bill_templates(tenant_id, code);

-- Versao (vigencia) do modelo. Intervalo [valid_from, valid_to] em DATAS; valid_to NULL
-- = aberta. A restricao de exclusao impede duas versoes do mesmo modelo vigentes no
-- mesmo dia (mesmo desenho de tariff_versions, V4_1; precisa de btree_gist).
-- layout: estrutura do modelo em JSON (blocos e ordem), gravada pela classe Json do
-- core. NENHUM dado pessoal entra aqui: o layout e do modelo, nao de um titular.
CREATE TABLE IF NOT EXISTS bill_template_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    template_id UUID NOT NULL,
    version_no INT NOT NULL CHECK (version_no >= 1),
    valid_from DATE NOT NULL,
    valid_to DATE,
    layout JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
ALTER TABLE bill_template_versions ADD CONSTRAINT uq_bill_template_versions_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE bill_template_versions ADD CONSTRAINT fk_bill_template_version_template
    FOREIGN KEY (tenant_id, template_id) REFERENCES bill_templates(tenant_id, id);
CREATE UNIQUE INDEX idx_bill_template_versions_no ON bill_template_versions(tenant_id, template_id, version_no);
ALTER TABLE bill_template_versions ADD CONSTRAINT excl_bill_template_versions_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, template_id WITH =, daterange(valid_from, valid_to, '[]') WITH &&);

-- ========================================================================
-- 2. CONTA: VERSOES DE PARAMETRO E SNAPSHOT DO TITULAR FATURADO (DAD-15)
-- ========================================================================

-- Ids das parameter_versions (core V1_5) que o motor leu nesta emissao, em coluna
-- propria: responder "com quais parametros esta conta foi emitida" sem abrir o
-- tariff_snapshot (que continua sendo a fotografia dos valores, nao das versoes).
ALTER TABLE bills ADD COLUMN IF NOT EXISTS parameter_version_ids JSONB;

-- Cliente faturado: o do vinculo show_on_bill vigente na emissao (cadastro, P1.3/P1.41).
ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_id UUID;
ALTER TABLE bills ADD CONSTRAINT fk_bill_billed_customer
    FOREIGN KEY (tenant_id, billed_customer_id) REFERENCES customers(tenant_id, id);
CREATE INDEX idx_bills_billed_customer ON bills(tenant_id, billed_customer_id)
    WHERE billed_customer_id IS NOT NULL;

-- Snapshot do titular faturado NO MOMENTO da emissao: nome, documento mascarado e
-- endereco de entrega. Guarda SOMENTE o texto cifrado (AES-256-GCM, FieldCipher,
-- contexto bills.billed_customer_snapshot) do JSON montado pela aplicacao. Troca
-- posterior de titular NAO toca esta coluna: a conta continua mostrando quem foi
-- faturado (NR 11, art. 33, paragrafo 2; CDC, art. 42-A).
ALTER TABLE bills ADD COLUMN IF NOT EXISTS billed_customer_snapshot TEXT;

-- O par anda junto: snapshot sem cliente (ou cliente sem snapshot) e emissao incompleta.
ALTER TABLE bills ADD CONSTRAINT chk_bills_billed_customer_pair
    CHECK ((billed_customer_id IS NULL) = (billed_customer_snapshot IS NULL));

COMMENT ON COLUMN bills.billed_customer_snapshot IS
    'Texto CIFRADO (FieldCipher, contexto bills.billed_customer_snapshot) do JSON {name, maskedDocument, deliveryAddress} do titular faturado na emissao (DAD-15). Nunca em claro; OMIT no audit_log.';

-- ========================================================================
-- 3. LINHAS DE CONSUMO VERSIONADAS (DAD-14) -- MOVIDO PARA A V5_6 DO METERING
-- ========================================================================
-- Correcao B1 da revisao cruzada da P2.5: esta secao NAO esta mais aqui. O
-- versionamento de consumption_records (version_no, superseded_by_id, bill_id,
-- bill_reference_month, parameter_version_ids e os indices da versao vigente)
-- vive na migration do metering operations/ziipsan-metering:
-- db/migration/tenant/V5_6__consumption_record_versioning.sql.
-- Motivo: o historico Flyway do tenant e unico e ordenado por versao; num tenant
-- novo a V4_4 (4.4) roda ANTES da V5_1 (5.1) que cria consumption_records, e o
-- ALTER TABLE falharia no provisionamento (em tenant existente o outOfOrder
-- mascarava o defeito). A V5_3 nao foi usada: esta reservada a P2.7.

-- ========================================================================
-- 4. CATALOGOS DE DEBITOS/CREDITOS E PROVENIENCIA (DAD-20) -- F3, com B6
-- ========================================================================
-- Escopo desta secao, e so dele: catalogos debit_types / credit_origins no lugar
-- do texto livre, e os campos tipados de debitos a cobrar e creditos a realizar
-- (source_type/source_id, legacy_reference, service_order_id, accounting_month,
-- collection_month, financing_term). Depende de: V4_1 (debit_receivables,
-- credit_receivables, bill_debits, bill_credits) e V1_2 (register_tenant_table).
--
-- NENHUM DADO E SEMEADO: os catalogos comecam vazios. Tipo de debito e origem de
-- credito sao referencia do produto/tenant; o aceite da P2.5 nao nomeia codigo
-- nenhum de catalogo, e codigo inventado aqui viraria politica falsa (mesma regra
-- da V4_1 e da V4_2). Os unicos valores fixos desta secao sao os seis source_type
-- nomeados no aceite (DAD-20).
--
-- ESTRUTURA EXECUTAVEL (correcao B6 da revisao cruzada): o trecho original da F3
-- exigia um passo manual (cadastrar o catalogo e mapear os ids) ENTRE statements
-- da propria migration, o que e impossivel dentro de uma transacao. A secao foi
-- reestruturada, sem mudar o schema final:
--   (1) catalogos e colunas novas nascem ANULAVEIS;
--   (2) backfill deterministico das linhas da conta (bill_debits/bill_credits) a
--       partir do item de catalogo e do codigo fotografados no recebivel ja
--       tipado -- nao inventa dado: copia da propria linha relacionada;
--   (3) ANTES de qualquer SET NOT NULL ou DROP, uma guarda (bloco DO) por tabela
--       levanta excecao com mensagem clara se existir linha legada nao mapeada;
--       a transacao da migration aborta inteira e NADA e aplicado (nem DROP);
--   (4) sem linhas nao mapeadas, aplicam-se os SET NOT NULL e os DROPs do texto
--       livre.
--
-- ETAPA OPERACIONAL (so para tenant COM dados legados): o mapeamento texto livre
-- -> item de catalogo e dado do tenant e esta migration NAO o inventa. Antes de
-- aplicar esta migration num tenant com debitos/creditos ja gravados, o operador
-- aplica em sessao propria a parte (1) desta secao (catalogos e colunas
-- anulaveis), cadastra os itens em debit_types/credit_origins e preenche, nas
-- linhas legadas, debit_type_id/credit_origin_id, source_type e accounting_month;
-- so entao a migration roda ate o fim. Em tenant novo (sem linhas) nao ha etapa
-- manual: a secao roda inteira.

-- ------------------------------------------------------------------------
-- 4.1 Catalogos (substituem o texto livre)
-- ------------------------------------------------------------------------

-- Tipos de debito. generates_bill NAO tem padrao: e decisao do tipo (a coluna
-- sai de debit_receivables e passa a viver so aqui).
CREATE TABLE IF NOT EXISTS debit_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    generates_bill BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE debit_types ADD CONSTRAINT uq_debit_types_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE debit_types ADD CONSTRAINT uq_debit_types_code UNIQUE (tenant_id, code);

-- Origens de credito (classe a).
CREATE TABLE IF NOT EXISTS credit_origins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    description VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
ALTER TABLE credit_origins ADD CONSTRAINT uq_credit_origins_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE credit_origins ADD CONSTRAINT uq_credit_origins_code UNIQUE (tenant_id, code);

-- ------------------------------------------------------------------------
-- 4.2 Debitos a cobrar: tipo tipado + proveniencia DAD-20
-- ------------------------------------------------------------------------
-- service_order_id SEM foreign key: a tabela de ordens de servico e de outro
-- modulo (mesmo padrao da V4_2 para social_tariff_enrollments). A
-- obrigatoriedade para debito de servico e o CHECK chk_drec_service_order
-- abaixo + a regra de dominio em DebitReceivable/ReceivableChecks.

ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS debit_type_id UUID;
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS source_id UUID;
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS legacy_reference VARCHAR(100);
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS service_order_id UUID;
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS accounting_month DATE;
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS collection_month DATE;
ALTER TABLE debit_receivables ADD COLUMN IF NOT EXISTS financing_term INT;

ALTER TABLE debit_receivables ADD CONSTRAINT fk_drec_debit_type
    FOREIGN KEY (tenant_id, debit_type_id) REFERENCES debit_types(tenant_id, id);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_source_type
    CHECK (source_type IN ('SERVICE_ORDER', 'INSTALLMENT_AGREEMENT', 'INFRACTION',
                           'PAYMENT_ADJUSTMENT', 'LEGACY_IMPORT', 'MANUAL'));
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_source_id
    CHECK (source_type NOT IN ('SERVICE_ORDER', 'INSTALLMENT_AGREEMENT', 'INFRACTION')
           OR source_id IS NOT NULL);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_service_order
    CHECK (source_type <> 'SERVICE_ORDER' OR service_order_id IS NOT NULL);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_service_order_type
    CHECK (service_order_id IS NULL OR source_type = 'SERVICE_ORDER');
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_legacy_reference
    CHECK (source_type <> 'LEGACY_IMPORT'
           OR (legacy_reference IS NOT NULL AND legacy_reference <> ''));
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_accounting_month
    CHECK (accounting_month IS NULL OR EXTRACT(DAY FROM accounting_month) = 1);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_collection_month
    CHECK (collection_month IS NULL OR EXTRACT(DAY FROM collection_month) = 1);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_collection_after_accounting
    CHECK (collection_month IS NULL OR accounting_month IS NULL
           OR collection_month >= accounting_month);
ALTER TABLE debit_receivables ADD CONSTRAINT chk_drec_financing_term
    CHECK (financing_term IS NULL OR financing_term > 0);

CREATE INDEX idx_drec_type ON debit_receivables(tenant_id, debit_type_id);
CREATE INDEX idx_drec_source ON debit_receivables(tenant_id, source_type, source_id);
CREATE INDEX idx_drec_service_order ON debit_receivables(tenant_id, service_order_id)
    WHERE service_order_id IS NOT NULL;

-- Guarda B6: nenhuma obrigatoriedade nem DROP antes de provar que nao ha linha
-- legada sem mapeamento (a etapa operacional esta no cabecalho da secao 4).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM debit_receivables
                WHERE debit_type_id IS NULL OR source_type IS NULL OR accounting_month IS NULL) THEN
        RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): debit_receivables tem linhas legadas nao mapeadas (debit_type_id, source_type ou accounting_month nulos). O mapeamento do texto livre para o catalogo debit_types e dado do tenant e nao pode ser inventado por esta migration: execute a etapa operacional do cabecalho da secao 4 e reaplique. A transacao foi abortada sem aplicar nenhuma obrigatoriedade nem remocao de coluna.';
    END IF;
END $$;

-- Fecha a tipagem: tipo, origem e mes contabil sao obrigatorios; o texto
-- livre e o generates_bill por lancamento saem (o indicador vive no catalogo).
ALTER TABLE debit_receivables ALTER COLUMN debit_type_id SET NOT NULL;
ALTER TABLE debit_receivables ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE debit_receivables ALTER COLUMN accounting_month SET NOT NULL;
ALTER TABLE debit_receivables DROP COLUMN IF EXISTS type_code;
ALTER TABLE debit_receivables DROP COLUMN IF EXISTS generates_bill;

-- ------------------------------------------------------------------------
-- 4.3 Creditos a realizar: origem tipada + proveniencia DAD-20 (mesmas regras)
-- ------------------------------------------------------------------------

ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS credit_origin_id UUID;
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS source_id UUID;
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS legacy_reference VARCHAR(100);
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS service_order_id UUID;
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS accounting_month DATE;
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS collection_month DATE;
ALTER TABLE credit_receivables ADD COLUMN IF NOT EXISTS financing_term INT;

ALTER TABLE credit_receivables ADD CONSTRAINT fk_crec_credit_origin
    FOREIGN KEY (tenant_id, credit_origin_id) REFERENCES credit_origins(tenant_id, id);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_source_type
    CHECK (source_type IN ('SERVICE_ORDER', 'INSTALLMENT_AGREEMENT', 'INFRACTION',
                           'PAYMENT_ADJUSTMENT', 'LEGACY_IMPORT', 'MANUAL'));
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_source_id
    CHECK (source_type NOT IN ('SERVICE_ORDER', 'INSTALLMENT_AGREEMENT', 'INFRACTION')
           OR source_id IS NOT NULL);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_service_order
    CHECK (source_type <> 'SERVICE_ORDER' OR service_order_id IS NOT NULL);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_service_order_type
    CHECK (service_order_id IS NULL OR source_type = 'SERVICE_ORDER');
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_legacy_reference
    CHECK (source_type <> 'LEGACY_IMPORT'
           OR (legacy_reference IS NOT NULL AND legacy_reference <> ''));
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_accounting_month
    CHECK (accounting_month IS NULL OR EXTRACT(DAY FROM accounting_month) = 1);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_collection_month
    CHECK (collection_month IS NULL OR EXTRACT(DAY FROM collection_month) = 1);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_collection_after_accounting
    CHECK (collection_month IS NULL OR accounting_month IS NULL
           OR collection_month >= accounting_month);
ALTER TABLE credit_receivables ADD CONSTRAINT chk_crec_financing_term
    CHECK (financing_term IS NULL OR financing_term > 0);

CREATE INDEX idx_crec_origin ON credit_receivables(tenant_id, credit_origin_id);
CREATE INDEX idx_crec_source ON credit_receivables(tenant_id, source_type, source_id);

-- Guarda B6 (mesma regra da secao 4.2, para credit_receivables/credit_origin_id).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM credit_receivables
                WHERE credit_origin_id IS NULL OR source_type IS NULL OR accounting_month IS NULL) THEN
        RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): credit_receivables tem linhas legadas nao mapeadas (credit_origin_id, source_type ou accounting_month nulos). O mapeamento do texto livre para o catalogo credit_origins e dado do tenant e nao pode ser inventado por esta migration: execute a etapa operacional do cabecalho da secao 4 e reaplique. A transacao foi abortada sem aplicar nenhuma obrigatoriedade nem remocao de coluna.';
    END IF;
END $$;

ALTER TABLE credit_receivables ALTER COLUMN credit_origin_id SET NOT NULL;
ALTER TABLE credit_receivables ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE credit_receivables ALTER COLUMN accounting_month SET NOT NULL;
ALTER TABLE credit_receivables DROP COLUMN IF EXISTS origin;

-- ------------------------------------------------------------------------
-- 4.4 Linhas da conta: a fotografia do tipo/origem passa a ser id + codigo
-- ------------------------------------------------------------------------
-- O backfill abaixo nao inventa dado: copia do proprio lancamento (que ja foi
-- tipado pelas secoes 4.2/4.3 acima, ou pela etapa operacional) o item de
-- catalogo e o codigo vigente.

ALTER TABLE bill_debits ADD COLUMN IF NOT EXISTS debit_type_id UUID;
ALTER TABLE bill_debits ADD COLUMN IF NOT EXISTS debit_type_code VARCHAR(40);

UPDATE bill_debits bd
   SET debit_type_id = dr.debit_type_id,
       debit_type_code = dt.code
  FROM debit_receivables dr, debit_types dt
 WHERE dr.tenant_id = bd.tenant_id AND dr.id = bd.receivable_id
   AND dt.tenant_id = dr.tenant_id AND dt.id = dr.debit_type_id;

-- Guarda B6: linha da conta cujo recebivel nao foi mapeado fica com NULL apos o
-- backfill deterministico; nesse caso a migration aborta antes da obrigatoriedade.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM bill_debits WHERE debit_type_id IS NULL OR debit_type_code IS NULL) THEN
        RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): bill_debits tem linhas legadas nao mapeadas (o recebivel correspondente nao foi tipado para o catalogo debit_types). Execute a etapa operacional do cabecalho da secao 4 e reaplique. A transacao foi abortada sem aplicar nenhuma obrigatoriedade nem remocao de coluna.';
    END IF;
END $$;

ALTER TABLE bill_debits ADD CONSTRAINT fk_bdebit_debit_type
    FOREIGN KEY (tenant_id, debit_type_id) REFERENCES debit_types(tenant_id, id);
ALTER TABLE bill_debits ALTER COLUMN debit_type_id SET NOT NULL;
ALTER TABLE bill_debits ALTER COLUMN debit_type_code SET NOT NULL;
ALTER TABLE bill_debits DROP COLUMN IF EXISTS type_code;

ALTER TABLE bill_credits ADD COLUMN IF NOT EXISTS credit_origin_id UUID;
ALTER TABLE bill_credits ADD COLUMN IF NOT EXISTS origin_code VARCHAR(40);

UPDATE bill_credits bc
   SET credit_origin_id = cr.credit_origin_id,
       origin_code = co.code
  FROM credit_receivables cr, credit_origins co
 WHERE cr.tenant_id = bc.tenant_id AND cr.id = bc.receivable_id
   AND co.tenant_id = cr.tenant_id AND co.id = cr.credit_origin_id;

-- Guarda B6 (mesma regra, para bill_credits/credit_origins).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM bill_credits WHERE credit_origin_id IS NULL OR origin_code IS NULL) THEN
        RAISE EXCEPTION 'V4_4 secao 4 (F3/B6): bill_credits tem linhas legadas nao mapeadas (o recebivel correspondente nao foi tipado para o catalogo credit_origins). Execute a etapa operacional do cabecalho da secao 4 e reaplique. A transacao foi abortada sem aplicar nenhuma obrigatoriedade nem remocao de coluna.';
    END IF;
END $$;

ALTER TABLE bill_credits ADD CONSTRAINT fk_bcredit_credit_origin
    FOREIGN KEY (tenant_id, credit_origin_id) REFERENCES credit_origins(tenant_id, id);
ALTER TABLE bill_credits ALTER COLUMN credit_origin_id SET NOT NULL;
ALTER TABLE bill_credits ALTER COLUMN origin_code SET NOT NULL;
ALTER TABLE bill_credits DROP COLUMN IF EXISTS origin;

-- ========================================================================
-- 5. COMPONENTE FIXO (TFDI) NA CONTA E A FORMULA DO TOTAL -- F5 secao 1, B2-DDL
-- ========================================================================
-- A TFDI de unidade nao conectada nao e agua nem esgoto: coluna propria, somada
-- ao total (RAS-22; Res. ARESC 105/2018; Lei 11.445/2007, art. 45). O valor e
-- gravado pelo motor (BillEntity.fixedPartValue / BillCalculator).
--
-- SUBSTITUICAO DOS CHECKS VIGENTES (correcao B2-DDL da revisao cruzada): os
-- CHECKs do total NAO sao os anonimos/inline da V4_1 que o trecho original da
-- F5 presumia. A V4_14 recriou bills particionada com os dois CHECKs sem nome
-- explicito e a V4_15 renomeou o CHECK do total para
-- chk_bills_total_with_withholding_tax (nome que e contrato); o CHECK de
-- creditos ficou com nome automatico gerado pelo PostgreSQL, que NAO e
-- contrato. Por isso esta secao:
--   (a) derruba o CHECK do total PELO NOME chk_bills_total_with_withholding_tax;
--   (b) derruba o CHECK de creditos PELO TEXTO da sua definicao (o texto real
--       da V4_14: credits_value <= water_value + sewer_value + debits_value),
--       localizado no catalogo pg_constraint -- nunca por um nome automatico
--       presumido;
--   (c) recria os dois com fixed_part_value na formula, mantendo o nome
--       chk_bills_total_with_withholding_tax no total.
-- Criar um CHECK paralelo sem derrubar os vigentes deixaria a formula antiga
-- valendo: toda conta com fixed_part_value > 0 seria recusada pelo banco.

ALTER TABLE bills ADD COLUMN IF NOT EXISTS fixed_part_value NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE bills ADD CONSTRAINT chk_bills_fixed_part_value
    CHECK (fixed_part_value >= 0);

ALTER TABLE bills DROP CONSTRAINT IF EXISTS chk_bills_total_with_withholding_tax;

DO $$
DECLARE
    v_credits_check TEXT;
BEGIN
    SELECT conname INTO v_credits_check
      FROM pg_constraint
     WHERE conrelid = 'bills'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%credits_value <=%'
       AND pg_get_constraintdef(oid) NOT LIKE '%fixed_part_value%';
    IF v_credits_check IS NOT NULL THEN
        EXECUTE format('ALTER TABLE bills DROP CONSTRAINT %I', v_credits_check);
    END IF;
END $$;

ALTER TABLE bills ADD CONSTRAINT chk_bills_total_with_withholding_tax
    CHECK (total_value = water_value + sewer_value + fixed_part_value + debits_value - credits_value - taxes_value);
ALTER TABLE bills ADD CONSTRAINT chk_bills_credits_cap
    CHECK (credits_value <= water_value + sewer_value + fixed_part_value + debits_value);

-- ========================================================================
-- 6. ENQUADRAMENTO EM CATEGORIA OPCIONAL DA NR 13 -- F5 secao 2, com B5 e B8
-- ========================================================================
-- Uma linha por enquadramento vigente/expirado de uma unidade. category_code
-- e o codigo do catalogo de categorias opcionais do motor
-- (VULNERABLE_RESIDENTIAL, MICRO_SMALL_BUSINESS, COLLECTIVE_SINGLE_METER,
-- ASSISTANCE_ENTITY); criterion e proof sao COPIA do que o tenant declarou
-- em tariff.optional_categories no ato do enquadramento (auditoria do
-- porque), e valid_to = concedido + validityMonths do parametro.
-- A tabela entra ACOMPANHADA (correcao B5): entidade JPA
-- OptionalCategoryEnrollmentEntity + OptionalCategoryEnrollmentRepository no
-- billing, e guarda no BillAccountMigrationConsistencyTest. A classificacao
-- de dados e a redacao de auditoria de criterion/proof estao na secao 8
-- (correcao B8).

CREATE TABLE IF NOT EXISTS optional_category_enrollments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    unit_id UUID NOT NULL,
    category_code VARCHAR(40) NOT NULL CHECK (category_code IN (
        'VULNERABLE_RESIDENTIAL', 'MICRO_SMALL_BUSINESS',
        'COLLECTIVE_SINGLE_METER', 'ASSISTANCE_ENTITY')),
    criterion TEXT NOT NULL CHECK (criterion <> ''),
    proof TEXT NOT NULL CHECK (proof <> ''),
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CHECK (valid_to >= valid_from)
);
ALTER TABLE optional_category_enrollments ADD CONSTRAINT uq_optional_category_enrollments_tenant_id
    UNIQUE (tenant_id, id);
ALTER TABLE optional_category_enrollments ADD CONSTRAINT fk_oce_unit
    FOREIGN KEY (tenant_id, unit_id) REFERENCES consumer_units(tenant_id, id);
-- Sem sobreposicao de enquadramento da mesma categoria na mesma unidade:
-- EXCLUDE com daterange, o mesmo padrao de tariff_versions na V4_1
-- (precisa de btree_gist, criada no init do banco).
ALTER TABLE optional_category_enrollments ADD CONSTRAINT excl_oce_no_overlap
    EXCLUDE USING gist (tenant_id WITH =, unit_id WITH =, category_code WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&);
CREATE INDEX idx_oce_unit ON optional_category_enrollments(tenant_id, unit_id, valid_to DESC);

-- ========================================================================
-- 7. PARAMETROS NO CATALOGO GLOBAL (public.parameter_definitions)
-- ========================================================================

-- ------------------------------------------------------------------------
-- 7.1 Nome impresso (F2; decisao do dono de 28/10/2026, item 6)
-- ------------------------------------------------------------------------

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('bill.holder_name_display', 'ENUM', 'b', '"USER_HOLDER_ONLY"',
     'NR 11, art. 33, paragrafo 2o; decisao do dono de 28/10/2026, item 6',
     'REG',
     'Nome impresso na conta: USER_HOLDER_ONLY (so o usuario titular, padrao de fabrica) ou USER_HOLDER_AND_OWNER (titular e proprietario). Classe b: fora do padrao exige aceite formal do tenant.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('bill.holder_name_display', 'ALLOWED_VALUES', '["USER_HOLDER_ONLY", "USER_HOLDER_AND_OWNER"]',
     'Decisao do dono de 28/10/2026, item 6', NULL,
     FALSE, FALSE, FALSE, FALSE, 'So os dois valores: qualquer outro e recusado na gravacao do parametro e na leitura pelo motor.')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------------
-- 7.2 Periodo parcial e regra pos-teto de media (bloco B da F4)
-- ------------------------------------------------------------------------
-- As chaves meter.average_months e meter.max_estimated_cycles JA EXISTEM
-- (V3 do core, classe d, REFERENCE 6 e 3 para SC): nada a registrar nelas.
-- Faltavam as duas chaves que a P2.5 le e que nao estavam no catalogo; sem
-- definicao, TenantParameterService recusa qualquer valor proposto.
-- Classes e padroes sao os do aceite da P2.5: bill.partial_period_rule e
-- classe b com padrao proporcional; meter.after_cap_rule nao tem classe
-- nem padrao unico fixados no plano (a regra posterior DIVERGE entre as
-- ERIs de SC -- ARIS/AGIR minimo, ARESC saldo em 3x -- e chega ao tenant
-- pelo perfil da P2.31), por isso factory_default fica NULL, como nas
-- demais chaves dependentes de perfil. Destino unico definido na fusao:
-- esta V4_4 (precedente da secao 7.1); nao ha registro duplicado no core.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('bill.partial_period_rule', 'ENUM', 'b', '"PROPORTIONAL"',
     'Res. ARESC 046/2016 (faixa de 27 a 33 dias); P2.5 (JOR-11, RAS-14)',
     'REG',
     'Regra de periodo parcial: primeira conta, conta final e ciclo com dias fora da faixa. Padrao de fabrica proporcional (parte fixa e franquia de consumo proporcionalizadas pelos dias). Classe b: sair do padrao exige aceite formal com alerta de risco.'),
    ('meter.after_cap_rule', 'ENUM', NULL, NULL,
     'NR 11, art. 35; mapa de ERIs de SC (P2.31)',
     'REG',
     'Regra depois do teto de ciclos estimados (meter.max_estimated_cycles): BILL_MINIMUM (ARIS e AGIR) ou SETTLE_BALANCE_IN_3_INSTALLMENTS (ARESC). Sem padrao de fabrica: a divergencia entre ERIs chega pelo perfil regulatorio da P2.31.')
ON CONFLICT (definition_key) DO NOTHING;

INSERT INTO public.parameter_legal_limits
    (definition_key, limit_kind, limit_value, norm_source, applies_to,
     above_max_requires_norm, above_max_requires_dual_approval,
     above_max_requires_formal_acceptance, above_max_risk_alert, notes)
VALUES
    ('bill.partial_period_rule', 'ALLOWED_VALUES', '["PROPORTIONAL"]',
     'P2.5 (JOR-11, RAS-14)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Nesta versao so o padrao proporcional esta implementado (PartialPeriodRule); outro valor e recusado pelo motor e por esta lista.'),
    ('meter.after_cap_rule', 'ALLOWED_VALUES', '["BILL_MINIMUM", "SETTLE_BALANCE_IN_3_INSTALLMENTS"]',
     'Mapa de ERIs de SC (P2.31)', NULL,
     FALSE, FALSE, FALSE, FALSE,
     'Valores sao as divergencias registradas entre as ERIs de SC para a regra depois da media; um terceiro comportamento so entra com a norma que o define.')
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------------
-- 7.3 Chaves da F5 (19 registros)
-- ------------------------------------------------------------------------
-- Sem este registro o console de parametros (P1.6) recusa propor valores.
-- factory_default fica NULL em todas: o acceptance nao declara padrao de
-- fabrica para nenhuma delas (ausencia = erro explicito no motor).
-- param_class so onde o acceptance declara: tariff.optional_categories e
-- benefit.expiry_notice_days sao classe d; nas demais, NULL com a
-- pendencia registrada na descricao (mesmo padrao da core V3 para classe
-- nao fixada no plano). bill.due_date_options NAO entra: ja existe na
-- core V3, com a trava MIN_COUNT 6 para concessionaria.

INSERT INTO public.parameter_definitions
    (definition_key, value_type, param_class, factory_default, norm_source, approver_area, description)
VALUES
    ('bill.min_days_delivery_to_due', 'OBJECT', NULL, NULL,
     'P2.5 (RAS-24)', 'REG',
     'Antecedencia minima, em dias, entre a entrega da conta e o vencimento, por categoria (chave "default" para as demais). Classe nao fixada no plano (pendencia).'),
    ('bill.hold_on_anomaly', 'BOOLEAN', NULL, NULL,
     'P2.5', 'REG',
     'Reter (nao emitir) conta com anomalia de leitura/consumo. Classe nao fixada no plano (pendencia).'),
    ('bill.auto_review_value_bands', 'TABLE', NULL, NULL,
     'P2.5', 'REG',
     'Faixas de valor da conta que a mandam para revisao automatica: [{min, max (null = aberta), review}]. Classe nao fixada no plano (pendencia).'),
    ('bill.min_issue_amount', 'DECIMAL', NULL, NULL,
     'P2.5', 'REG',
     'Valor minimo para emissao da conta. Classe nao fixada no plano (pendencia).'),
    ('bill.below_min_rule', 'ENUM', NULL, NULL,
     'P2.5', 'REG',
     'Destino da conta abaixo de bill.min_issue_amount: ISSUE_ANYWAY, CARRY_OVER_TO_NEXT ou HOLD_FOR_REVIEW. Classe nao fixada no plano (pendencia).'),
    ('bill.validity_months', 'INT', NULL, NULL,
     'P2.5', 'REG',
     'Meses de validade da conta apos o vencimento. Classe nao fixada no plano (pendencia).'),
    ('bill.mail_extra_days', 'INT', NULL, NULL,
     'P2.5', 'REG',
     'Dias extras de vencimento quando a entrega da conta e por correio. Classe nao fixada no plano (pendencia).'),
    ('bill.delivery_address_fee', 'DECIMAL', NULL, NULL,
     'P2.5', 'REG',
     'Taxa cobrada pela entrega da conta em endereco alternativo. Classe nao fixada no plano (pendencia).'),
    ('bill.inactive_economy_rule', 'ENUM', NULL, NULL,
     'P2.5; extensao pedida pela P1.41 (CAD-68)', 'REG',
     'Faturamento de economia inativa/unidade vaga: CHARGE_FIXED_ONLY, ESTIMATE_BY_AVERAGE ou NO_BILL. Classe nao fixada no plano (pendencia).'),
    ('bill.carry_over_unpaid', 'BOOLEAN', NULL, NULL,
     'P2.5', 'REG',
     'Saldo nao pago da conta anterior e carregado para a conta seguinte. Classe nao fixada no plano (pendencia).'),
    ('bill.cut_connection_charge', 'BOOLEAN', NULL, NULL,
     'P2.5 (RAS-22)', 'REG',
     'Cobrar o componente fixo (TFDI) de unidade com ligacao cortada/nao conectada. Classe nao fixada no plano (pendencia).'),
    ('sewer.no_water_volume_rule', 'ENUM', NULL, NULL,
     'P2.5', 'REG',
     'Volume-base do esgoto sem volume de agua medido: ALTERNATIVE_SOURCE_VOLUME, MINIMUM_VOLUME ou NO_SEWER_CHARGE. Classe nao fixada no plano (pendencia).'),
    ('credit.type_limits', 'OBJECT', NULL, NULL,
     'P2.5', 'REG',
     'Teto por tipo de credito a realizar: {tipo: {maxAmount, maxInstallments}}. Leitura do motor; o dominio de creditos e da F3. Classe nao fixada no plano (pendencia).'),
    ('fee.fine_mode', 'ENUM', NULL, NULL,
     'P2.5', 'JUR',
     'Modo da multa de mora, no vocabulario do LateFeePolicy: FLAT ou PER_DAY. Classe nao fixada no plano (pendencia).'),
    ('fee.charge_moment', 'ENUM', NULL, NULL,
     'P2.5', 'JUR',
     'Momento da cobranca de multa/juros: AT_PAYMENT ou ON_NEXT_BILL. Classe nao fixada no plano (pendencia).'),
    ('fee.exemptions', 'LIST_TEXT', NULL, NULL,
     'P2.5', 'JUR',
     'Chaves de categoria/situacao isentas de multa e juros. Classe nao fixada no plano (pendencia).'),
    ('tariff.fixed_part_model', 'ENUM', NULL, NULL,
     'P2.5 (RAS-22); Res. ARESC 105/2018; Lei 11.445/2007, art. 45', 'REG',
     'Modelo do componente fixo por disponibilidade de infraestrutura (TFDI): MINIMUM_CHARGE_PER_ECONOMY ou NONE. Classe nao fixada no plano (pendencia).'),
    ('tariff.optional_categories', 'TABLE', 'd', NULL,
     'P2.5 (RAS-13); NR 13, art. 6o', 'REG',
     'Categorias opcionais da NR 13: [{category, criterion, proof, validityMonths}] - residencial vulneravel, micro e pequena empresa, coletiva com varias economias e hidrometro unico, entidade assistencial.'),
    ('benefit.expiry_notice_days', 'INT', 'd', NULL,
     'P2.5 (CAD-88)', 'REG',
     'Antecedencia, em dias, do aviso de vencimento de beneficio (a mensagem e da P2.32).')
ON CONFLICT (definition_key) DO NOTHING;

-- ========================================================================
-- 8. ISOLAMENTO, CLASSIFICACAO DE DADOS E REDACAO DE AUDITORIA
-- ========================================================================

SELECT register_tenant_table('bill_templates');
SELECT register_tenant_table('bill_template_versions');
SELECT register_tenant_table('debit_types');
SELECT register_tenant_table('credit_origins');
SELECT register_tenant_table('optional_category_enrollments');

-- Classificacao de dados (catalogo da P0.11, classes ID/CT/LOC/FIN/SOC/SENS): o
-- snapshot tem dado pessoal (nome e documento mascarado = ID; o endereco de entrega
-- dentro dele e LOC, coberto pela mesma linha e pela regra OMIT abaixo). As demais
-- colunas novas de bills sao UUIDs de FK e ids de versao de parametro, sem dado
-- pessoal: nao ganham linha, como os atores UUID da V21_1. A linha explicita cobre
-- tenants migrados fora de ordem, em que o R__data_classification (que deriva a
-- classificacao das regras de redacao) nao roda de novo -- precedente da V9_1.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('bills', 'billed_customer_snapshot', 'ID', 'DAD-15 (P2.5): snapshot cifrado do titular faturado (nome, documento mascarado e endereco de entrega)')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- O ciphertext NUNCA aparece na trilha: nem o valor, nem pista dele (padrao OMIT da
-- core V2 para ciphertext e indice cego, como na V4_2). Em changed_fields a coluna
-- aparece apenas como "[changed]".
INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('bills', 'billed_customer_snapshot', 'OMIT', 'ciphertext AES-GCM do snapshot do titular faturado (DAD-15): ciphertext nunca entra no audit_log')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- Correcao B8: criterion e proof do enquadramento em categoria opcional sao as
-- unicas colunas novas com dado potencialmente pessoal/sensivel sem cobertura.
-- criterion e a copia do criterio declarado pelo tenant no ato do enquadramento
-- e pode identificar a condicao social da unidade (classe SOC); proof e a prova
-- dessa condicao -- documento ou declaracao de vulnerabilidade, de micro/pequena
-- empresa ou de entidade assistencial (classe SENS). Sem regra de redacao, a
-- trilha de auditoria da tabela registrada gravaria os dois em claro. Os dois
-- ganham REDACT (a trilha marca a mudanca, nunca o conteudo), no padrao de
-- meters.notes na V5_2; proof nao usa OMIT porque, ao contrario do ciphertext
-- do snapshot, saber QUE a prova mudou e informacao de auditoria legitima --
-- o que nao pode vazar e o conteudo.
INSERT INTO public.data_classification (table_name, column_name, class, reason) VALUES
    ('optional_category_enrollments', 'criterion', 'SOC', 'RAS-13 (P2.5): criterio do enquadramento em categoria opcional da NR 13; pode identificar a condicao social da unidade'),
    ('optional_category_enrollments', 'proof', 'SENS', 'RAS-13 (P2.5): prova do enquadramento em categoria opcional da NR 13 (vulnerabilidade, micro/pequena empresa ou entidade assistencial) - dado sensivel')
ON CONFLICT (table_name, column_name) DO NOTHING;

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('optional_category_enrollments', 'criterion', 'REDACT', 'criterio do enquadramento pode identificar condicao social da unidade: conteudo nunca em claro no audit_log'),
    ('optional_category_enrollments', 'proof', 'REDACT', 'prova de condicao sensivel (vulnerabilidade/entidade assistencial): conteudo nunca em claro no audit_log')
ON CONFLICT (table_name, column_name) DO NOTHING;
