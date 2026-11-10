-- MM-05 — Fiscal por municipio: eixo de municipio NO EMISSOR fiscal.
-- SQL PENDENTE (sem numero Flyway — D-MM-06 do MAPA-DE-PACOTES do bloco
-- multimunicipio; a numeracao global atribui-se na integracao, com a lista
-- de publicadas actualizada no mesmo pouso). Nenhuma migration publicada
-- e tocada.
--
-- REDESENHO — ciclo de correccao R8-C (achado A-02 da Rodada 8, 09/11/2026;
-- corrector: sessao 66187316-31e4-42cc-ba7d-ff4adb372ac8). O ficheiro
-- ORIGINAL deste SQL (ADD COLUMN municipality_ibge VARCHAR(7) anulavel,
-- SHA-256 e8b21c9cf55929c54649c4f0b6394312d40dd9849493aa90f5b2755db773c1e0)
-- esta preservado em mm-05-fiscal/trabalho-r8c/ e no registo do ciclo
-- (CORRECAO-MM-05-R8C.md §2); este ficheiro SUBSTITUI-O no pacote
-- (ficheiro pendente, NAO publicado — substituicao autorizada pelo ciclo).
--
-- Facto que obriga o redesenho (R8 §A-02; conferencia da mesa da Rodada 8
-- por leitura directa da main): a V4_5 REAL tem, em fiscal_issuers,
--   ibge CHAR(7) NOT NULL CHECK (ibge ~ '^[0-9]{7}$')
-- (linhas 72-73, blob a1c1347ee7ff5acdd97451d220f91ffadb6b3144), e a UNIQUE
-- vigente e (tenant_id, document_hash) (linha 86) — SEM UNIQUE em
-- (tenant_id, ibge). O texto da V4_5 NAO foi relido neste ciclo (parada
-- dura de HTTP 429 em vigor); a fonte do facto e a R8 §A-02.
--
-- Consequencias (CORRECAO-MM-05-R8C.md §1):
--   * O eixo municipal do emissor JA EXISTE: e fiscal_issuers.ibge.
--     NENHUMA coluna nova e criada — uma segunda coluna seria segunda
--     fonte de verdade. A semantica antiga "NULL = emissor da empresa"
--     CAI: nenhum emissor existe sem ibge (NOT NULL).
--   * "Emissor da empresa" REDEFINIDO (D-MM05-R8C-02): nao ha escopo
--     empresa sem municipio; o conceito passa a ser o TENANT
--     MONO-EMISSOR — o unico emissor do tenant e o emissor da empresa
--     e resolve em qualquer contexto (ver FiscalIssuerMunicipalityResolver).
--   * Este SQL passa a ser uma GUARDA DE PRE-CONDICAO executavel: verifica
--     que o DDL real tem o eixo esperado (ibge CHAR(7) NOT NULL com a
--     CHECK de 7 digitos) e que a coluna duplicada do desenho antigo
--     (municipality_ibge) NAO existe. Se a V4_5 divergir, a aplicacao
--     ABORTA com excecao explicita — nunca aplica um desenho sobre um
--     DDL diferente. E a forma executavel da condicao de pouso P-MM05-01.
--   * A serie e o documento continuam a HERDAR o municipio pela cadeia
--     provada emissor -> serie -> documento: NENHUMA coluna neles.
--
-- Idempotente: o bloco DO de verificacao reaplicado e no-op (a guarda
-- passa de novo sem alterar nada). Nao altera dados nem schema.
-- Layouts de NFS-e por municipio concreto: FORA deste pacote (sem
-- fonte e sem municipio concreto — MAPA §6 / estudo §7.3).

DO $$
DECLARE
    v_is_nullable text;
    v_data_type text;
    v_char_max integer;
    v_check_count integer;
    v_dup_count integer;
BEGIN
    -- 1. A coluna duplicada do desenho antigo nao pode existir.
    SELECT count(*) INTO v_dup_count
      FROM information_schema.columns
     WHERE table_name = 'fiscal_issuers' AND column_name = 'municipality_ibge';
    IF v_dup_count > 0 THEN
        RAISE EXCEPTION 'MM-05: fiscal_issuers.municipality_ibge existe — coluna duplicada do desenho antigo (R8-C A-02); resolver antes de aplicar';
    END IF;

    -- 2. O eixo existe: fiscal_issuers.ibge (V4_5, linhas 72-73).
    SELECT is_nullable, data_type, character_maximum_length
      INTO v_is_nullable, v_data_type, v_char_max
      FROM information_schema.columns
     WHERE table_name = 'fiscal_issuers' AND column_name = 'ibge';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'MM-05: fiscal_issuers.ibge nao existe — DDL real divergente da V4_5 citada na R8 §A-02';
    END IF;
    IF v_is_nullable <> 'NO' THEN
        RAISE EXCEPTION 'MM-05: fiscal_issuers.ibge deve ser NOT NULL (V4_5: ibge CHAR(7) NOT NULL)';
    END IF;
    IF v_data_type <> 'character' OR v_char_max <> 7 THEN
        RAISE EXCEPTION 'MM-05: fiscal_issuers.ibge deve ser CHAR(7) (V4_5, linhas 72-73)';
    END IF;

    -- 3. A CHECK de 7 digitos existe sobre ibge.
    SELECT count(*) INTO v_check_count
      FROM pg_constraint
     WHERE conrelid = 'fiscal_issuers'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%ibge%'
       AND pg_get_constraintdef(oid) LIKE '%^[0-9]{7}$%';
    IF v_check_count < 1 THEN
        RAISE EXCEPTION 'MM-05: fiscal_issuers.ibge sem CHECK de 7 digitos (V4_5: CHECK (ibge ~ ''^[0-9]{7}$''))';
    END IF;

    RAISE NOTICE 'MM-05: guarda OK — eixo municipal = fiscal_issuers.ibge (CHAR(7) NOT NULL, CHECK 7 digitos)';
END $$;
