-- ======================================================================
-- V13_7 — gis_write_history lista as colunas-alvo explicitamente (P1-C)
-- ======================================================================
-- Problema (exposto pela IT AssetRegistryIT.gisAndMeterReferenceAsset):
-- a V27_1 acrescentou asset_id as 6 tabelas de feicao GIS, mas o
-- gis_write_history (V13_2) fazia INSERT INTO <hist> SELECT (OLD).*,
-- $2, $3, $4, $5 SEM lista de colunas — posicional. Com a coluna nova
-- na tabela principal e ausente no historico, todo UPDATE/DELETE de
-- feicao quebrava com 'INSERT has more expressions than target columns'.
--
-- A V13_2 esta PUBLICADA e nao e editada. Esta migration redefine a
-- funcao com a MESMA semantica, mas com lista de colunas-alvo
-- explicita, montada a partir do catalogo (colunas da tabela principal
-- em ordem ordinal + os 4 extras). Assim o INSERT continua correto
-- qualquer que seja a coluna acrescentada depois a tabela principal —
-- e a posicao fisica da coluna no historico deixa de importar (a V27_1
-- acrescenta asset_id ao historico com ADD COLUMN simples).
--
-- O max(upper(valid_period)) e o restante do corpo nao mudam.
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_write_history() RETURNS TRIGGER AS $$
DECLARE
    v_hist TEXT := TG_TABLE_NAME || '_history';
    v_lower TIMESTAMPTZ;
    v_channel TEXT;
    v_cols TEXT;
BEGIN
    EXECUTE format('SELECT max(upper(valid_period)) FROM %I WHERE id = $1', v_hist)
        INTO v_lower USING OLD.id;
    IF v_lower IS NULL THEN
        v_lower := OLD.created_at;
    END IF;
    v_channel := COALESCE(NULLIF(current_setting('app.channel', true), ''), 'SQL_DIRECT');
    -- Colunas da tabela principal em ordem ordinal: (OLD).* expande
    -- nessa mesma ordem, entao a lista-alvo casa posicao a posicao,
    -- qualquer que seja a coluna acrescentada depois (ex.: asset_id).
    SELECT string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position)
        INTO v_cols
        FROM information_schema.columns
        WHERE table_schema = TG_TABLE_SCHEMA AND table_name = TG_TABLE_NAME;
    EXECUTE format('INSERT INTO %I (%s, valid_period, history_author, history_role, history_channel) SELECT ($1).*, $2, $3, $4, $5', v_hist, v_cols)
        USING OLD, tstzrange(v_lower, clock_timestamp()),
              COALESCE(OLD.updated_by, OLD.created_by), session_user, v_channel;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
