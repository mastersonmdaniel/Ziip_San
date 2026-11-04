-- ======================================================================
-- V13_5 — gis_check_feature resolve tudo por TG_TABLE_SCHEMA (P1-C)
-- ======================================================================
-- Problema (exposto pela IT AssetRegistryIT.gisAndMeterReferenceAsset, da
-- frente P1.35): a definicao da V13_2 chamava gis_validate_attributes()
-- sem qualificar e consultava gis_catalog_item / gis_feature_type /
-- gis_quality_target sem qualificar. Corpo plpgsql resolve nomes no
-- search_path da conexao EM EXECUCAO — e o caminho da app (AssetService,
-- testes via appDs) nao fixa search_path, so app.current_tenant. Resultado:
-- todo INSERT/UPDATE de feicao GIS por esse caminho quebrava com
-- "function gis_validate_attributes(uuid, uuid, jsonb) does not exist"
-- (e, apos o PERFORM, quebraria nas tabelas pelo mesmo motivo).
--
-- A V13_2 esta PUBLICADA (base 5376a2d): nao e editada. Esta migration
-- redefine a funcao inteira (CREATE OR REPLACE, mesmo corpo, mesmas
-- mensagens, mesma ordem) com as 4 referencias resolvidas por
-- TG_TABLE_SCHEMA — o schema da tabela que disparou o trigger, ou seja,
-- o tenant — via SQL dinamico, no mesmo padrao da guarda de hierarquia
-- de ativos (V27_1, P1-C). Para conexoes COM search_path do tenant o
-- comportamento e identico (mesmas tabelas, mesma logica); para as sem,
-- passa a funcionar.
--
-- Colunas consultadas sao NOT NULL (feature_type_id em gis_catalog_item,
-- requires_catalog_item e class_id em gis_feature_type), logo IS NULL
-- apos EXECUTE ... INTO significa exatamente "nenhuma linha" — o mesmo
-- teste que o SELECT INTO estatico original fazia (e o padrao adotado
-- na V27_1 apos a prova de que FOUND nao e confiavel apos EXECUTE).
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_check_feature() RETURNS TRIGGER AS $$
DECLARE
    v_item_type_id UUID;
    v_requires_item BOOLEAN;
    v_class_id UUID;
    v_h_target NUMERIC;
    v_v_target NUMERIC;
BEGIN
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
