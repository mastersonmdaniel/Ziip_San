-- ======================================================================
-- V13_6 — gis_check_feature fixa search_path LOCAL (P1-C, complemento da V13_5)
-- ======================================================================
-- A V13_5 qualificou a CHAMADA ao validador, mas o validador em si
-- (V13_1, publicada) consulta gis_feature_type / gis_value_list sem
-- qualificar — e ele nao e trigger (nao tem TG_TABLE_SCHEMA). Em vez de
-- mudar a assinatura de uma funcao publicada, o trigger fixa o
-- search_path da transacao para o schema do tenant antes de chamar o
-- validador (ver comentario no corpo). V13_1 e V13_5 nao sao editadas.
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_check_feature() RETURNS TRIGGER AS $$
DECLARE
    v_item_type_id UUID;
    v_requires_item BOOLEAN;
    v_class_id UUID;
    v_h_target NUMERIC;
    v_v_target NUMERIC;
BEGIN
    -- P1-C (V13_6): fixa o search_path da TRANSACAO para o schema do
    -- trigger (fallback "$user", public preservado). O validador
    -- gis_validate_attributes() (V13_1, publicada) consulta
    -- gis_feature_type / gis_value_list sem qualificar, e corpo plpgsql
    -- resolve nomes no search_path da conexao em execucao — que no
    -- caminho da app nao tem o schema do tenant. Sem isto, a V13_5
    -- chegava ao validador e caia em 'relation gis_feature_type does
    -- not exist'. SET LOCAL morre no commit; audit_log e public.
    -- qualificado, e o servico qualifica todas as suas tabelas, logo
    -- nada mais na transacao muda de resolucao. Provado: planos plpgsql
    -- re-resolvem a cada mudanca de search_path (repro: ta/tb/rc),
    -- sem risco de plano de outro tenant vazar entre conexoes do pool.
    PERFORM set_config('search_path', TG_TABLE_SCHEMA || ', "$user", public', true);
    IF NEW.attributes IS NULL THEN
        NEW.attributes := '{}'::jsonb;
    END IF;
    EXECUTE format('SELECT %I.gis_validate_attributes($1, $2, $3)', TG_TABLE_SCHEMA)
        USING NEW.feature_type_id, NEW.tenant_id, NEW.attributes;

    IF NEW.catalog_item_id IS NOT NULL THEN
        EXECUTE format('SELECT feature_type_id FROM %I.gis_catalog_item WHERE id = $1 AND tenant_id = $2', TG_TABLE_SCHEMA)
            INTO v_item_type_id
            USING NEW.catalog_item_id, NEW.tenant_id;
        IF v_item_type_id IS NULL THEN
            RAISE EXCEPTION 'gis: item de catalogo % nao encontrado no tenant', NEW.catalog_item_id;
        ELSIF v_item_type_id <> NEW.feature_type_id THEN
            RAISE EXCEPTION 'gis: item de catalogo % e de outro tipo de feicao', NEW.catalog_item_id;
        END IF;
    END IF;

    EXECUTE format('SELECT requires_catalog_item, class_id FROM %I.gis_feature_type WHERE id = $1 AND tenant_id = $2', TG_TABLE_SCHEMA)
        INTO v_requires_item, v_class_id
        USING NEW.feature_type_id, NEW.tenant_id;
    IF v_requires_item AND NEW.catalog_item_id IS NULL THEN
        RAISE EXCEPTION 'gis: tipo de feicao exige item de catalogo';
    END IF;

    IF NEW.capture_method IN ('DIGITIZED', 'AUTO_SERVICE_LINE', 'DESIGN', 'ESTIMATED') THEN
        NEW.accuracy_status := 'APPROXIMATE';
    ELSIF NEW.capture_method = 'CAD_IMPORT' AND NEW.h_accuracy_m IS NULL THEN
        NEW.accuracy_status := 'APPROXIMATE';
    ELSIF NEW.h_accuracy_m IS NOT NULL OR NEW.v_accuracy_m IS NOT NULL THEN
        EXECUTE format('SELECT h_target_m, v_target_m FROM %I.gis_quality_target WHERE tenant_id = $1 AND class_id = $2 AND valid_from <= CURRENT_DATE AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) ORDER BY valid_from DESC LIMIT 1', TG_TABLE_SCHEMA)
            INTO v_h_target, v_v_target
            USING NEW.tenant_id, v_class_id;
        v_h_target := COALESCE(v_h_target, 0.10);
        v_v_target := COALESCE(v_v_target, 0.05);
        IF (NEW.h_accuracy_m IS NULL OR NEW.h_accuracy_m <= v_h_target)
           AND (NEW.v_accuracy_m IS NULL OR NEW.v_accuracy_m <= v_v_target) THEN
            NEW.accuracy_status := 'WITHIN_TARGET';
        ELSE
            NEW.accuracy_status := 'OUT_OF_TARGET';
        END IF;
    ELSE
        NEW.accuracy_status := 'UNKNOWN';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
