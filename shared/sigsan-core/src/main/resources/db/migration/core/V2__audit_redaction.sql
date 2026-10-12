-- Redacao de dado pessoal na trilha de auditoria
-- Version: 2.0.0
--
-- POR QUE (achado C4 do PDCA de 12/10/2026)
--
-- O trigger de auditoria gravava a linha inteira (to_jsonb) em public.audit_log, removendo so as
-- credenciais. Nome, data de nascimento, e-mail, telefone, CPF do motorista do MTR, IP e
-- geolocalizacao iam em claro para uma tabela em que a aplicacao so pode INSERIR: a anonimizacao
-- (LGPD art. 18) limpava a tabela de origem, mas a copia na auditoria ficava para sempre.
--
-- COMO
--
-- public.audit_redaction_rule diz, por tabela e coluna, o que fazer com o valor antes de gravar:
--   REDACT  o valor (nao nulo) vira "[redacted]": fica registrado QUE o campo existia/mudou, sem o valor.
--   OMIT    a chave sai da linha. Para valores sem utilidade na trilha (ciphertext, indice cego,
--           geometria); em changed_fields aparece como "[changed]".
-- Cada modulo declara as regras das suas tabelas na propria migration de tenant (INSERT ... ON
-- CONFLICT DO NOTHING). Um teste (AuditRedactionCoverageTest) falha se uma coluna com nome de dado
-- pessoal numa tabela auditada nao tiver regra nem justificativa explicita.
--
-- A tabela pertence a sigsan_migrator e NAO tem GRANT para sigsan_app: a aplicacao nao le nem
-- altera as regras. O trigger de auditoria (SECURITY DEFINER, dono sigsan_migrator) le.

CREATE TABLE IF NOT EXISTS public.audit_redaction_rule (
    table_name  TEXT NOT NULL,
    column_name TEXT NOT NULL,
    mode        TEXT NOT NULL CHECK (mode IN ('REDACT', 'OMIT')),
    reason      TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    PRIMARY KEY (table_name, column_name)
);

COMMENT ON TABLE public.audit_redaction_rule IS
    'Colunas com dado pessoal que o trigger de auditoria redige (REDACT) ou remove (OMIT) antes de gravar em audit_log';

-- p_row: linha (to_jsonb) ja sem credenciais. p_changed: true quando p_row e changed_fields, em que
-- uma coluna OMIT que mudou aparece como "[changed]" em vez de sumir.
CREATE OR REPLACE FUNCTION public.audit_redact(p_table TEXT, p_row JSONB, p_changed BOOLEAN DEFAULT FALSE)
RETURNS JSONB AS $$
    SELECT CASE WHEN p_row IS NULL THEN NULL ELSE (
        SELECT coalesce(jsonb_object_agg(
                   e.key,
                   CASE
                       WHEN r.mode = 'OMIT' THEN to_jsonb('[changed]'::text)
                       WHEN r.mode = 'REDACT' AND e.value <> 'null'::jsonb THEN to_jsonb('[redacted]'::text)
                       ELSE e.value
                   END), '{}'::jsonb)
        FROM jsonb_each(p_row) AS e
        LEFT JOIN public.audit_redaction_rule r
               ON r.table_name = p_table AND r.column_name = e.key
        WHERE r.mode IS DISTINCT FROM 'OMIT' OR p_changed
    ) END
$$ LANGUAGE sql STABLE SET search_path = pg_catalog, public, pg_temp;

REVOKE ALL ON FUNCTION public.audit_redact(TEXT, JSONB, BOOLEAN) FROM PUBLIC;
