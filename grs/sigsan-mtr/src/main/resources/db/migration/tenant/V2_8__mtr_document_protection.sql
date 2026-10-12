-- MTR: documentos pessoais cifrados na própria tabela (achado C4 do PDCA de 12/10/2026, E4)
-- Version: 2.8.0
--
-- POR QUE
--
-- waste_mtr.driver_cpf, waste_generator.cnpj_cpf (CPF quando o gerador é pessoa física) e
-- waste_transporter.drivers (nome, CPF e CNH dos motoristas) ficavam em claro. A V2_6 tirou esses
-- valores da trilha de auditoria; esta versão os tira da tabela. Passam a existir só:
--   *_ciphertext  AES-256-GCM com a chave do tenant (o contexto do campo entra na AAD);
--   *_hash        índice cego (HMAC-SHA-256) para unicidade e busca sem decifrar;
--   *_masked      só o miolo, para listas e para o PDF (decisão S2).
-- Quem grava calcula os três: MtrDocumentProtection (sigsan-mtr), com FieldProtector (sigsan-security).
-- A lista de motoristas vai cifrada inteira (sem índice nem máscara): não é consultada por campo.
--
-- SEM BACKFILL
--
-- A chave fica na aplicação, não no banco: não há como cifrar em SQL o que já está gravado. Um
-- tenant com dado nas colunas antigas exige migração ASSISTIDA PELA APLICAÇÃO (ler em claro, cifrar
-- com MtrDocumentProtection e gravar nas colunas novas) ANTES de aplicar esta versão. Para não
-- apagar documento em silêncio, a migration recusa o tenant que ainda tem valor em claro. Hoje
-- nenhum tenant tem MTR em produção (o módulo é rascunho operacional; ver MtrService).
--
-- A RLS é FORCE também para o migrador (dono das tabelas): sem app.current_tenant a policy esconde
-- todas as linhas e a contagem daria zero com a tabela cheia. Por isso a GUC é definida antes da
-- verificação (local à transação da migration).

SELECT set_config('app.current_tenant', '${tenant_id}', true);

DO $$
DECLARE
    v_mtr        BIGINT;
    v_generator  BIGINT;
    v_drivers    BIGINT;
BEGIN
    SELECT count(*) INTO v_mtr FROM waste_mtr
     WHERE driver_cpf IS NOT NULL AND btrim(driver_cpf) <> '';
    -- cnpj_cpf é NOT NULL: qualquer gerador cadastrado tem documento em claro.
    SELECT count(*) INTO v_generator FROM waste_generator;
    SELECT count(*) INTO v_drivers FROM waste_transporter
     WHERE drivers IS NOT NULL AND drivers NOT IN ('null'::jsonb, '[]'::jsonb);

    IF v_mtr > 0 OR v_generator > 0 OR v_drivers > 0 THEN
        RAISE EXCEPTION 'V2_8: documento em claro no MTR (waste_mtr.driver_cpf: %, waste_generator.cnpj_cpf: %, waste_transporter.drivers: %)',
                        v_mtr, v_generator, v_drivers
              USING HINT = 'Faça a migração assistida pela aplicação (cifrar com MtrDocumentProtection) antes desta versão; nada foi alterado.';
    END IF;
END
$$;

-- ------------------------------------------------------------------------ waste_mtr

ALTER TABLE waste_mtr
    ADD COLUMN driver_cpf_ciphertext TEXT,
    ADD COLUMN driver_cpf_hash VARCHAR(64),
    ADD COLUMN driver_cpf_masked VARCHAR(14),
    ADD CONSTRAINT ck_waste_mtr_driver_cpf_protected
        CHECK ((driver_cpf_ciphertext IS NULL) = (driver_cpf_hash IS NULL));

ALTER TABLE waste_mtr DROP COLUMN driver_cpf;

-- ------------------------------------------------------------------------ waste_generator

-- NOT NULL sem default é possível porque a verificação acima garantiu a tabela vazia.
ALTER TABLE waste_generator
    ADD COLUMN document_ciphertext TEXT NOT NULL,
    ADD COLUMN document_hash VARCHAR(64) NOT NULL,
    ADD COLUMN document_masked VARCHAR(30);

DROP INDEX IF EXISTS idx_waste_generator_cnpj;
-- Um gerador por documento no tenant, pelo índice cego (era UNIQUE (tenant_id, cnpj_cpf)).
CREATE UNIQUE INDEX idx_waste_generator_document ON waste_generator(tenant_id, document_hash);

ALTER TABLE waste_generator DROP COLUMN cnpj_cpf;

-- ------------------------------------------------------------------------ waste_transporter

ALTER TABLE waste_transporter ADD COLUMN drivers_ciphertext TEXT;
ALTER TABLE waste_transporter DROP COLUMN drivers;

-- ------------------------------------------------------------------------ auditoria

INSERT INTO public.audit_redaction_rule (table_name, column_name, mode, reason) VALUES
    ('waste_mtr', 'driver_cpf_ciphertext',          'OMIT',   'CPF do motorista cifrado: sem uso na trilha'),
    ('waste_mtr', 'driver_cpf_hash',                'OMIT',   'índice cego do CPF do motorista: permite ligar registros'),
    ('waste_mtr', 'driver_cpf_masked',              'REDACT', 'máscara do CPF do motorista ainda identifica a pessoa'),
    ('waste_generator', 'document_ciphertext',      'OMIT',   'CPF/CNPJ do gerador cifrado: sem uso na trilha'),
    ('waste_generator', 'document_hash',            'OMIT',   'índice cego do documento do gerador: permite ligar registros'),
    ('waste_generator', 'document_masked',          'REDACT', 'máscara do CPF quando o gerador é pessoa física'),
    ('waste_transporter', 'drivers_ciphertext',     'OMIT',   'lista de motoristas (nome, CPF, CNH) cifrada: sem uso na trilha')
ON CONFLICT (table_name, column_name) DO NOTHING;

-- As regras das colunas removidas saem do catálogo, mas só quando nenhum outro schema ainda tem a
-- coluna: audit_redaction_rule é global (por nome de tabela), e um tenant que ainda não chegou a
-- esta versão continua gravando driver_cpf/cnpj_cpf/drivers em claro na trilha se a regra sumir.
-- O último tenant a migrar apaga. pg_attribute é legível sem privilégio sobre o schema do outro tenant.
DELETE FROM public.audit_redaction_rule r
 WHERE (r.table_name, r.column_name) IN (
           ('waste_mtr', 'driver_cpf'),
           ('waste_generator', 'cnpj_cpf'),
           ('waste_transporter', 'drivers'))
   AND NOT EXISTS (
           SELECT 1
             FROM pg_catalog.pg_attribute a
             JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
            WHERE c.relname = r.table_name
              AND c.relkind IN ('r', 'p')
              AND a.attname = r.column_name
              AND a.attnum > 0
              AND NOT a.attisdropped);
