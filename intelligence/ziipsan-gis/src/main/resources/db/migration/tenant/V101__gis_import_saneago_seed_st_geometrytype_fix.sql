-- P3.26 / GIS-07 — CORRECAO M3: seed Saneago (A4) + ST_GeometryType
-- nas funcoes de importacao (defeito provado pelo benchmark A2).
-- SQL SEM numero de Flyway reservado — fica em db/pendente/ como
-- PENDENTE__* e so vira migration quando a integracao atribuir o
-- proximo numero livre pela sequencia global da P3.12 (precedentes:
-- P4.1/P4.5 e ED-67/ED-55 neste modulo). NENHUM numero foi inventado.
--
-- ORIGEM E FORMA DE POUSO (remontagem de pouso, 08/11/2026):
--   * Esta migration NOVA substitui a proposta ORIGINAL da correcao
--     M3 da P3.26, que era SUBSTITUIR a V13_11__gis_import_staging.sql
--     sob a premissa de que ela "ainda nao esta em main".
--   * A confirmacao independente (REVISAO-CONFIRMACAO-P3.26.md,
--     secao "CONDICAO DE POUSO DA V13_11") CONTRADITOU essa premissa
--     com tres registros locais: commits feat(p3-26) partes 1-3/3 na
--     main, contagem oficial com a P3.26 na main e a lista
--     published-tenant-migrations.txt contendo a V13_11 como
--     migration de tenant PUBLICADA. Reescrever migration publicada
--     e proibido pelo portao do projeto.
--   * Portanto o pouso correto e ESTA migration nova, aplicada DEPOIS
--     da V13_11 original ja publicada, contendo apenas: (a) os
--     INSERTs do seed Saneago e (b) o CREATE OR REPLACE das tres
--     funcoes afetadas pela troca GeometryType( -> ST_GeometryType(
--     (gis_import_clean, gis_import_extract_labels,
--     gis_import_publish).
--   * Conteudo EXTRAIDO PROGRAMATICAMENTE da V13_11 corrigida da
--     correcao M3 (sem redigitacao): corpos das funcoes e INSERTs
--     identicos aos confirmados — prova de equivalencia em
--     remontagem/PROVA-EQUIVALENCIA.md e no RELATORIO-REPOUSO-P3.26.md.
--   * Idempotencia: o INSERT de perfis usa ON CONFLICT DO NOTHING
--     (CORSAN_ANEXO_A/GENERICO ja semeados pela V13_11 original sao
--     no-op; so o SANEAGO entra) e o INSERT de camadas do Saneago usa
--     NOT EXISTS, como na V13_11 corrigida.
--
-- CONVENCOES: as mesmas da V13_11 (search_path resolvido dentro das
-- funcoes via public.tenant_catalog/GUC app.current_tenant; mensagens
-- sem diacriticos; placeholder ${tenant_id} substituido na integracao
-- por tenant, como nas migrations tenant da serie V13_x).

-- ======================================================================
-- (a) SEED SANEAGO (correcao M3, A4) — INSERTs identicos aos da
--     V13_11 corrigida confirmada.
-- ======================================================================

SELECT set_config('app.current_tenant', '${tenant_id}', true);

INSERT INTO gis_import_profile (tenant_id, key, label, origin)
VALUES ('${tenant_id}', 'CORSAN_ANEXO_A', 'Corsan Anexo A (rev. 03/2024)', 'BUILTIN'),
       ('${tenant_id}', 'GENERICO', 'Generico (mapeamento do cliente)', 'BUILTIN'),
       ('${tenant_id}', 'SANEAGO', 'Saneago IN 00.0229 rev. 04 / Giswater (a validar)', 'BUILTIN')
ON CONFLICT (tenant_id, key) DO NOTHING;

-- SANEAGO (correcao M3, A4): amostra a validar da linha GIS-07 (§11).
-- Fonte UNICA das camadas: §9.2 — "modelo Giswater (arc, node, connec,
-- catalogos)". Catalogos nao sao camadas de importacao e nao entram.
-- Como nas camadas Corsan sem tipo conferido, arc/node/connec nascem
-- SEM alvo (feature_table/feature_type_code NULL): o tenant completa o
-- mapeamento depois de validar a amostra contra a IN 00.0229 rev. 04 —
-- nenhum alvo nem camada da IN foi inventado.
INSERT INTO gis_cad_layer_map
    (tenant_id, profile_id, source_layer, target_kind, feature_table, feature_type_code)
SELECT '${tenant_id}', p.id, m.layer, 'FEATURE', NULL, NULL
FROM gis_import_profile p,
     (VALUES
        ('arc'),
        ('node'),
        ('connec')
     ) AS m(layer)
WHERE p.tenant_id = '${tenant_id}' AND p.key = 'SANEAGO'
  AND NOT EXISTS (SELECT 1 FROM gis_cad_layer_map x
                  WHERE x.tenant_id = p.tenant_id AND x.profile_id = p.id
                    AND x.source_layer = m.layer);

-- ======================================================================
-- (b) FUNCOES CORRIGIDAS — CREATE OR REPLACE com os corpos
--     identicos aos da V13_11 corrigida (ST_GeometryType nas 6
--     ocorrencias: 1 em gis_import_clean, 1 em
--     gis_import_extract_labels, 4 em gis_import_publish).
-- ======================================================================

-- CORRECAO M3 (achado do benchmark A2, provado por execucao em
-- PostgreSQL 16 + PostGIS 3.4): GeometryType() devolve 'LINESTRING'
-- (sem o prefixo ST_), enquanto os filtros abaixo comparam com
-- 'ST_LineString'/'ST_Polygon' — com a funcao antiga, a remocao de
-- segmentos curtos da limpeza NUNCA disparava (short_segments_removed
-- ficava 0 com 10 segmentos de 0,2 m no staging), a associacao de
-- rotulos a arcos idem e o ramo de poligono da publicacao idem.
-- ST_GeometryType() devolve os literais com prefixo e e a funcao que
-- as migrations irmaos (V13_2) ja usam. Troca aplicada nas 6
-- ocorrencias desta migration (clean, extract_labels, publish).

CREATE OR REPLACE FUNCTION gis_import_clean(p_batch_id UUID)
RETURNS JSONB AS $$
DECLARE
    v_batch gis_import_batch;
    v_schema TEXT;
    v_tenant_guc UUID;
    v_before JSONB;
    v_after JSONB;
    v_invalid INT;
    v_duplicates INT;
    v_short INT;
    v_remaining INT;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    SELECT * INTO v_batch FROM gis_import_batch WHERE id = p_batch_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'gis: lote de importacao % nao encontrado', p_batch_id;
    END IF;
    IF v_batch.status <> 'MAPPED' THEN
        RAISE EXCEPTION 'gis: limpeza exige lote MAPPED (atual %)', v_batch.status;
    END IF;

    SELECT jsonb_build_object(
               'total', count(*),
               'invalid_geometry', count(*) FILTER (WHERE geom IS NOT NULL AND NOT ST_IsValid(geom)),
               'without_geometry', count(*) FILTER (WHERE geom IS NULL))
      INTO v_before
      FROM gis_import_raw
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status IN ('RAW', 'MAPPED');

    -- Geometrias invalidas: ST_MakeValid; o que nao se recupera sai da
    -- publicacao marcado, nunca em silencio.
    UPDATE gis_import_raw
       SET geom = ST_MakeValid(geom), status = 'CLEANED', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED' AND geom IS NOT NULL AND NOT ST_IsValid(geom);
    GET DIAGNOSTICS v_invalid = ROW_COUNT;
    UPDATE gis_import_raw
       SET status = 'INVALID_GEOMETRY', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED' AND geom IS NULL;

    -- Duplicatas: mesma camada e geometria igual (o mesmo trecho vindo
    -- em dois arquivos/camadas sobrepostas) — fica a primeira (menor
    -- created_at/id), as demais sao marcadas. (Mesma camada+source_ref
    -- nem chega aqui: o UNIQUE parcial do staging ja recusa a segunda.)
    UPDATE gis_import_raw r
       SET status = 'SKIPPED_DUPLICATE', updated_at = clock_timestamp()
     WHERE r.batch_id = p_batch_id AND r.tenant_id = v_batch.tenant_id
       AND r.status IN ('MAPPED', 'CLEANED') AND r.geom IS NOT NULL
       AND EXISTS (SELECT 1 FROM gis_import_raw o
                   WHERE o.batch_id = r.batch_id AND o.tenant_id = r.tenant_id
                     AND o.source_layer = r.source_layer
                     AND o.status IN ('MAPPED', 'CLEANED')
                     AND ST_Equals(o.geom, r.geom)
                     AND (o.created_at, o.id) < (r.created_at, r.id));
    GET DIAGNOSTICS v_duplicates = ROW_COUNT;

    -- Segmentos curtos: linhas abaixo da tolerancia do lote.
    UPDATE gis_import_raw
       SET status = 'SKIPPED_SHORT_SEGMENT', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status IN ('MAPPED', 'CLEANED') AND geom IS NOT NULL
       AND ST_GeometryType(geom) IN ('ST_LineString', 'ST_MultiLineString')
       AND ST_Length(geom::geography) < v_batch.snap_tolerance_m;
    GET DIAGNOSTICS v_short = ROW_COUNT;

    UPDATE gis_import_raw
       SET status = 'CLEANED', updated_at = clock_timestamp()
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'MAPPED';

    SELECT count(*) INTO v_remaining FROM gis_import_raw
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
       AND status = 'CLEANED';

    v_after := jsonb_build_object(
        'total', v_remaining,
        'invalid_fixed', v_invalid,
        'duplicates_removed', v_duplicates,
        'short_segments_removed', v_short);

    UPDATE gis_import_batch
       SET counts_before = v_before, counts_after = v_after, status = 'CLEANED'
     WHERE id = p_batch_id;
    RETURN v_after;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_import_extract_labels(p_batch_id UUID)
RETURNS INT AS $$
DECLARE
    v_schema TEXT;
    v_tenant_guc UUID;
    v_count INT;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    UPDATE gis_import_raw t
       SET label_raw_id = t.id, updated_at = clock_timestamp()
     WHERE t.batch_id = p_batch_id AND t.label_text IS NOT NULL
       AND t.geom IS NOT NULL;
    -- O vinculo texto->arco fica no atributo label_for_source_ref do
    -- staging (JSONB do staging, nao do cadastro).
    UPDATE gis_import_raw a
       SET attributes = a.attributes || jsonb_build_object(
               'label_texts',
               (SELECT jsonb_agg(t.label_text ORDER BY t.id)
                  FROM gis_import_raw t
                 WHERE t.batch_id = a.batch_id AND t.tenant_id = a.tenant_id
                   AND t.label_text IS NOT NULL AND t.geom IS NOT NULL
                   AND ST_DWithin(t.geom::geography, a.geom::geography, 5.0))),
           updated_at = clock_timestamp()
     WHERE a.batch_id = p_batch_id AND a.geom IS NOT NULL
       AND ST_GeometryType(a.geom) IN ('ST_LineString', 'ST_MultiLineString')
       AND EXISTS (SELECT 1 FROM gis_import_raw t
                   WHERE t.batch_id = a.batch_id AND t.tenant_id = a.tenant_id
                     AND t.label_text IS NOT NULL AND t.geom IS NOT NULL
                     AND ST_DWithin(t.geom::geography, a.geom::geography, 5.0));
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION gis_import_publish(
    p_batch_id UUID, p_author_id UUID)
RETURNS UUID AS $$
DECLARE
    v_batch gis_import_batch;
    v_schema TEXT;
    v_tenant_guc UUID;
    v_set_id UUID;
    v_raw gis_import_raw;
    v_map gis_cad_layer_map;
    v_type_id UUID;
    v_feature_id UUID;
    v_code TEXT;
    v_geom geometry;
    v_report JSONB;
BEGIN
    BEGIN
        v_tenant_guc := NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    EXCEPTION WHEN invalid_text_representation THEN
        v_tenant_guc := NULL;
    END;
    IF v_tenant_guc IS NOT NULL THEN
        SELECT schema_name INTO v_schema FROM public.tenant_catalog
        WHERE id = v_tenant_guc;
    END IF;
    v_schema := COALESCE(v_schema, current_schema());
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    SELECT * INTO v_batch FROM gis_import_batch WHERE id = p_batch_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'gis: lote de importacao % nao encontrado', p_batch_id;
    END IF;
    IF v_batch.status <> 'QUALITY_REPORTED' THEN
        RAISE EXCEPTION 'gis: publicacao exige lote QUALITY_REPORTED com relatorio ISO 19157 (atual %)', v_batch.status;
    END IF;
    SELECT report INTO v_report FROM gis_quality_report
     WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id;
    IF v_report IS NULL THEN
        RAISE EXCEPTION 'gis: lote sem relatorio de qualidade nao publica';
    END IF;
    IF v_batch.profile_id IS NULL THEN
        RAISE EXCEPTION 'gis: lote sem perfil de mapeamento nao publica';
    END IF;

    v_set_id := uuid_generate_v4();
    INSERT INTO gis_change_set (id, tenant_id, type, channel, status,
            author_id, technical_report, quality_report, created_by, updated_by)
    VALUES (v_set_id, v_batch.tenant_id, 'IMPORT', 'IMPORT', 'DRAFT',
            p_author_id, 'Importacao GIS-07 lote ' || p_batch_id::text,
            v_report, p_author_id, p_author_id);

    FOR v_raw IN
        SELECT * FROM gis_import_raw
         WHERE batch_id = p_batch_id AND tenant_id = v_batch.tenant_id
           AND status = 'CLEANED'
         ORDER BY created_at, id
    LOOP
        SELECT * INTO v_map FROM gis_cad_layer_map
         WHERE id = v_raw.layer_map_id AND tenant_id = v_batch.tenant_id;
        IF NOT FOUND OR v_map.target_kind <> 'FEATURE' THEN
            CONTINUE;
        END IF;
        IF v_map.feature_table IS NULL OR v_map.feature_type_code IS NULL THEN
            RAISE EXCEPTION 'gis: camada % sem alvo de mapeamento no perfil; complete o mapeamento antes de publicar', v_raw.source_layer;
        END IF;
        -- Idempotencia: feicao ja publicada deste lote com a mesma
        -- origem nao e criada de novo.
        IF v_raw.source_ref IS NOT NULL AND EXISTS (
            SELECT 1 FROM gis_import_raw done
             WHERE done.tenant_id = v_batch.tenant_id
               AND done.batch_id = p_batch_id
               AND done.source_layer = v_raw.source_layer
               AND done.source_ref = v_raw.source_ref
               AND done.status = 'PUBLISHED'
               AND done.published_feature_table = v_map.feature_table) THEN
            UPDATE gis_import_raw SET status = 'SKIPPED_DUPLICATE',
                updated_at = clock_timestamp() WHERE id = v_raw.id;
            CONTINUE;
        END IF;
        SELECT id INTO v_type_id FROM gis_feature_type
         WHERE tenant_id = v_batch.tenant_id
           AND factory_code = v_map.feature_type_code;
        IF v_type_id IS NULL THEN
            RAISE EXCEPTION 'gis: tipo % do mapeamento nao existe no catalogo do tenant', v_map.feature_type_code;
        END IF;
        v_feature_id := uuid_generate_v4();
        v_code := 'IMP-' || substr(p_batch_id::text, 1, 8) || '-'
                  || substr(v_feature_id::text, 1, 8);
        v_geom := v_raw.geom;
        IF v_map.feature_table IN ('gis_structure', 'gis_zone')
           AND ST_GeometryType(v_geom) = 'ST_Polygon' THEN
            v_geom := ST_Multi(v_geom);
        END IF;
        -- Dimensionalidade das tabelas da V13_2: node/arc/connection
        -- guardam Z (PointZ/LineStringZ); structure/zone/poi sao 2D.
        IF v_map.feature_table IN ('gis_structure', 'gis_zone', 'gis_poi') THEN
            v_geom := ST_Force2D(v_geom);
        ELSE
            v_geom := ST_Force3D(v_geom);
        END IF;
        IF v_map.feature_table = 'gis_structure'
           AND ST_GeometryType(v_geom) IN ('ST_Polygon', 'ST_MultiPolygon') THEN
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        ELSIF v_map.feature_table = 'gis_structure' THEN
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom_point, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        ELSE
            EXECUTE format(
                'INSERT INTO %I.%I (id, tenant_id, code, feature_type_id,'
                || ' lifecycle_status, change_set_id, attributes,'
                || ' capture_method, source, source_batch_id, source_ref,'
                || ' geom, created_by, updated_by)'
                || ' VALUES ($1,$2,$3,$4,''PLANNED'',$5,$6,''CAD_IMPORT'','
                || '''IMPORT'',$7,$8, ST_GeomFromText($9, 4674),$10,$10)',
                v_schema, v_map.feature_table)
            USING v_feature_id, v_batch.tenant_id, v_code, v_type_id,
                  v_set_id, v_raw.attributes, p_batch_id, v_raw.source_ref,
                  ST_AsText(v_geom), p_author_id;
        END IF;
        INSERT INTO gis_change_set_item (id, tenant_id, change_set_id,
                operation, feature_table, feature_id, proposed_state,
                proposed_geom_wkt, proposed_geom_point_wkt,
                created_by, updated_by)
        VALUES (uuid_generate_v4(), v_batch.tenant_id, v_set_id, 'INSERT',
                v_map.feature_table, v_feature_id,
                '{"lifecycle_status":"IN_SERVICE"}'::jsonb,
                CASE WHEN v_map.feature_table = 'gis_structure'
                          AND ST_GeometryType(v_geom) NOT IN ('ST_Polygon', 'ST_MultiPolygon')
                     THEN NULL ELSE ST_AsText(v_geom) END,
                CASE WHEN v_map.feature_table = 'gis_structure'
                          AND ST_GeometryType(v_geom) NOT IN ('ST_Polygon', 'ST_MultiPolygon')
                     THEN ST_AsText(v_geom) ELSE NULL END,
                p_author_id, p_author_id);
        UPDATE gis_import_raw
           SET status = 'PUBLISHED', published_feature_table = v_map.feature_table,
               published_feature_id = v_feature_id, updated_at = clock_timestamp()
         WHERE id = v_raw.id;
    END LOOP;

    IF NOT EXISTS (SELECT 1 FROM gis_change_set_item
                   WHERE change_set_id = v_set_id AND tenant_id = v_batch.tenant_id) THEN
        RAISE EXCEPTION 'gis: lote sem nenhuma feicao mapeada para publicar';
    END IF;

    UPDATE gis_change_set SET status = 'SUBMITTED', submitted_at = clock_timestamp()
     WHERE id = v_set_id;
    UPDATE gis_import_batch
       SET change_set_id = v_set_id, status = 'PUBLISHED'
     WHERE id = p_batch_id;
    RETURN v_set_id;
END;
$$ LANGUAGE plpgsql;
