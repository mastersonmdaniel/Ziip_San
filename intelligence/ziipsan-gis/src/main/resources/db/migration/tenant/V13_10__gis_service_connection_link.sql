-- GIS-05 (P2.23) — lado GIS da ligacao: FK do ramal para
-- service_connections, FK do historico da ligacao para service_orders,
-- parametro gis.service_line_max_distance_m resolvido no banco e a
-- geracao de ramais automaticos (GIS-CADASTRO-TECNICO §3.6.3, §3.10,
-- §3.11, §5.4 e §13.2 linha GIS-05; ADR-0034; ED-52).
--
-- NUMERACAO: a spec reservava V13_5 para esta migration, mas no repo
-- real V13_5 ja foi consumido (fix da integracao P1c); a serie tenant
-- do GIS na main vai ate V13_8 (GIS-03, P2.21) e a V13_9 e a GIS-04
-- (P2.22, pacote local sobre o mesmo HEAD). Seguindo o precedente do
-- projeto (slot ocupado -> proxima versao livre da serie, sem editar
-- migration aplicada), esta migration e a V13_10. Evidencia: listagem
-- MCP de intelligence/ziipsan-gis/src/main/resources/db/migration/
-- tenant/ na main (HEAD 0fbcd22) em 07/11/2026 + pacote da P2.22 —
-- ver RELATORIO-P2.23.md.
--
-- DESENHO:
-- (1) gis_connection.service_connection_id (coluna da V13_2, sem FK ate
--     aqui porque service_connections nao existia) ganha a FK para
--     service_connections (V3_14, registry) — FK entre modulos no
--     mesmo schema de tenant, como as FKs do GIS para dma_district.
--     Guardada por to_regclass: schema parcial de teste sem o registry
--     nao quebra (precedente das guardas da V9_3 e da V13_2).
-- (2) connection_status_history.service_order_id (V3_14, sem FK porque
--     a V9_1 roda depois da V3_14 na cadeia) ganha aqui a FK para
--     service_orders: nesta altura (V13_10) a V9_1 ja foi aplicada em
--     qualquer tenant, novo ou em upgrade. Guardada pelos dois
--     to_regclass.
-- (3) gis_service_line_max_distance_m(tenant): espelho exato de
--     gis_snap_tolerance_m da V13_8 — parameter_versions APPROVED
--     vigente, senao o factory_default de public.parameter_definitions
--     (core V9_6: 100), senao 100 (o factory_default registrado la).
-- (4) gis_generate_auto_service_lines: para cada ligacao ativa
--     (ended_on nulo) do tenant SEM ramal nao abandonado, procura o
--     arco de rede em operacao do dominio e da funcao certos (agua:
--     WATER + WATER_DISTRIBUTION; esgoto: SEWER + SEWER_NETWORK) mais
--     proximo do ponto do imovel (coordenadas da unidade ancora; sem
--     elas, as do endereco normalizado da unidade — P1.3/CAD-83) e,
--     estando a distancia <= p_max_distance_m (geodesica, geography),
--     cria o ramal gis_connection em PLANNED, dentro do pacote
--     informado (que a api cria como AUTO_SERVICE_LINE/AUTO e submete
--     a revisao — nada entra em operacao sem revisao, §6.1/§6.2), com
--     capture_method = AUTO_SERVICE_LINE e source = AUTO. O ramal vai
--     do ponto do imovel ao ponto mais proximo do arco
--     (ST_ClosestPoint), com arc_id preenchido; o snap da V13_8 cuida
--     da juncao na ponta de rede quando o pacote for aplicado.
--     Ligacao fora da distancia, sem ponto ou sem arco compativel NAO
--     gera ramal: ela volta na lista de retorno com generated = false
--     e o motivo, para o painel/relatorio — nunca e descartada em
--     silencio. Uma ligacao pode ter mais de um ramal so quando o tipo
--     permitir (§3.6.3): a geracao cria no maximo UM por ligacao e so
--     para ligacao que ainda nao tem nenhum.
-- (5) gis_active_connections_without_service_line: a consulta da
--     validacao "ligacao ativa sem ramal" (§5.4) — ligacoes ativas
--     (ended_on nulo, situacao com system_role <> 'NO_CONNECTION')
--     sem nenhum gis_connection nao abandonado. O motor Java
--     (GisValidationEngine) decide e grava o problema; esta funcao e a
--     leitura de conjunto para varreduras e para o painel.

SELECT set_config('app.current_tenant', '${tenant_id}', true);

-- ======================================================================
-- (1) e (2) FKs que a ordem da cadeia adiou para ca
-- ======================================================================

DO $$
BEGIN
    IF to_regclass('service_connections') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint
                       WHERE conname = 'fk_gis_connection_service_connection') THEN
        ALTER TABLE gis_connection
            ADD CONSTRAINT fk_gis_connection_service_connection
            FOREIGN KEY (tenant_id, service_connection_id)
            REFERENCES service_connections (tenant_id, id);
    END IF;
    IF to_regclass('service_connections') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_gis_connection_service_connection
            ON gis_connection (tenant_id, service_connection_id);
    END IF;
    IF to_regclass('connection_status_history') IS NOT NULL
       AND to_regclass('service_orders') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint
                       WHERE conname = 'fk_connection_history_service_order') THEN
        ALTER TABLE connection_status_history
            ADD CONSTRAINT fk_connection_history_service_order
            FOREIGN KEY (tenant_id, service_order_id)
            REFERENCES service_orders (tenant_id, id);
    END IF;
END $$;

-- ======================================================================
-- (3) Parametro gis.service_line_max_distance_m (§3.10)
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_service_line_max_distance_m(p_tenant_id UUID)
RETURNS NUMERIC AS $$
DECLARE
    v_value NUMERIC;
BEGIN
    IF to_regclass('parameter_versions') IS NOT NULL THEN
        EXECUTE 'SELECT (value #>> ''{}'')::numeric FROM parameter_versions '
             || 'WHERE tenant_id = $1 AND definition_key = ''gis.service_line_max_distance_m'' '
             || 'AND status = ''APPROVED'' AND valid_from <= CURRENT_DATE '
             || 'AND (valid_to IS NULL OR valid_to >= CURRENT_DATE) '
             || 'ORDER BY valid_from DESC LIMIT 1'
            INTO v_value USING p_tenant_id;
    END IF;
    IF v_value IS NULL THEN
        SELECT (factory_default #>> '{}')::numeric INTO v_value
        FROM public.parameter_definitions
        WHERE definition_key = 'gis.service_line_max_distance_m';
    END IF;
    RETURN COALESCE(v_value, 100);
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- (4) Geracao de ramais automaticos em pacote revisado
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_generate_auto_service_lines(
    p_tenant_id UUID,
    p_change_set_id UUID,
    p_max_distance_m NUMERIC)
RETURNS TABLE (
    service_connection_id UUID,
    gis_connection_id UUID,
    arc_id UUID,
    distance_m NUMERIC,
    generated BOOLEAN,
    skip_reason TEXT
) AS $$
DECLARE
    v_schema TEXT;
BEGIN
    -- Funcao chamada pela api (nao e de gatilho): fixa o search_path no
    -- schema do tenant resolvido em public.tenant_catalog pela GUC
    -- app.current_tenant (fallback: schema corrente, o caminho do
    -- migrador) — convencao da V13_9 (correcao A1).
    SELECT schema_name INTO v_schema
    FROM public.tenant_catalog
    WHERE id = NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    IF v_schema IS NULL THEN
        v_schema := current_schema();
    END IF;
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    IF p_max_distance_m IS NULL OR p_max_distance_m <= 0 THEN
        RAISE EXCEPTION 'gis: distancia maxima do ramal automatico invalida: %', p_max_distance_m;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM gis_change_set cs
                   WHERE cs.id = p_change_set_id AND cs.tenant_id = p_tenant_id
                     AND cs.type = 'AUTO_SERVICE_LINE') THEN
        RAISE EXCEPTION 'gis: pacote % nao e AUTO_SERVICE_LINE do tenant', p_change_set_id;
    END IF;

    RETURN QUERY
    WITH candidates AS (
        SELECT sc.id AS sc_id,
               sc.kind,
               sc.code AS sc_code,
               COALESCE(anchor.coordinates,
                        (SELECT a.coordinates FROM addresses a
                         WHERE a.id = anchor.address_id
                           AND a.tenant_id = anchor.tenant_id)) AS property_point
        FROM service_connections sc
        JOIN consumer_units anchor
          ON anchor.id = sc.property_unit_id AND anchor.tenant_id = sc.tenant_id
        WHERE sc.tenant_id = p_tenant_id
          AND sc.ended_on IS NULL
          AND NOT EXISTS (
              SELECT 1 FROM gis_connection gc
              WHERE gc.tenant_id = sc.tenant_id
                AND gc.service_connection_id = sc.id
                AND gc.lifecycle_status <> 'ABANDONED')
    ),
    nearest AS (
        SELECT c.sc_id, c.kind, c.sc_code, c.property_point,
               na.arc_id, na.distance_m, na.closest_point
        FROM candidates c
        LEFT JOIN LATERAL (
            SELECT a.id AS arc_id,
                   ST_Distance(a.geom::geography, c.property_point::geography) AS distance_m,
                   ST_ClosestPoint(a.geom, c.property_point) AS closest_point
            FROM gis_arc a
            JOIN gis_feature_type ft
              ON ft.id = a.feature_type_id AND ft.tenant_id = a.tenant_id
            JOIN gis_feature_class fc
              ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
            WHERE a.tenant_id = p_tenant_id
              AND a.lifecycle_status = 'IN_SERVICE'
              AND c.property_point IS NOT NULL
              AND fc.domain = CASE WHEN c.kind = 'WATER' THEN 'WATER' ELSE 'SEWER' END
              AND a.network_function = CASE WHEN c.kind = 'WATER'
                                            THEN 'WATER_DISTRIBUTION'
                                            ELSE 'SEWER_NETWORK' END
            ORDER BY a.geom::geography <-> c.property_point::geography
            LIMIT 1
        ) na ON TRUE
    ),
    inserted AS (
        INSERT INTO gis_connection (
            id, tenant_id, code, feature_type_id, lifecycle_status,
            change_set_id, capture_method, source, accuracy_status,
            arc_id, service_connection_id, endpoint_type, geom)
        SELECT uuid_generate_v4(), p_tenant_id,
               'AUTO-' || COALESCE(n.sc_code, LEFT(n.sc_id::text, 8)),
               (SELECT ft.id FROM gis_feature_type ft
                WHERE ft.tenant_id = p_tenant_id
                  AND ft.code = CASE WHEN n.kind = 'WATER'
                                     THEN 'WATER_SERVICE_LINE'
                                     ELSE 'SEWER_SERVICE_LINE' END
                LIMIT 1),
               'PLANNED', p_change_set_id, 'AUTO_SERVICE_LINE', 'AUTO',
               'APPROXIMATE', n.arc_id, n.sc_id,
               CASE WHEN n.kind = 'WATER' THEN 'METER_SETTING'
                    ELSE 'SEWER_CONNECTION_BOX' END,
               ST_Force3D(ST_SetSRID(ST_MakeLine(n.property_point, n.closest_point), 4674))
        FROM nearest n
        WHERE n.property_point IS NOT NULL
          AND n.arc_id IS NOT NULL
          AND n.distance_m <= p_max_distance_m
        RETURNING id, service_connection_id, arc_id
    )
    SELECT n.sc_id,
           i.id,
           n.arc_id,
           n.distance_m,
           i.id IS NOT NULL,
           CASE
               WHEN i.id IS NOT NULL THEN NULL
               WHEN n.property_point IS NULL THEN 'PROPERTY_WITHOUT_POINT'
               WHEN n.arc_id IS NULL THEN 'NO_COMPATIBLE_ARC'
               ELSE 'BEYOND_MAX_DISTANCE'
           END
    FROM nearest n
    LEFT JOIN inserted i ON i.service_connection_id = n.sc_id;
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- (5) Ligacoes ativas sem ramal (§5.4)
-- ======================================================================

CREATE OR REPLACE FUNCTION gis_active_connections_without_service_line(
    p_tenant_id UUID)
RETURNS TABLE (
    service_connection_id UUID,
    property_unit_id UUID,
    kind TEXT
) AS $$
DECLARE
    v_schema TEXT;
BEGIN
    SELECT schema_name INTO v_schema
    FROM public.tenant_catalog
    WHERE id = NULLIF(current_setting('app.current_tenant', true), '')::uuid;
    IF v_schema IS NULL THEN
        v_schema := current_schema();
    END IF;
    PERFORM set_config('search_path', v_schema || ', "$user", public', true);

    RETURN QUERY
    SELECT sc.id, sc.property_unit_id, sc.kind::text
    FROM service_connections sc
    JOIN connection_statuses cs
      ON cs.id = sc.status_id AND cs.tenant_id = sc.tenant_id
    WHERE sc.tenant_id = p_tenant_id
      AND sc.ended_on IS NULL
      AND cs.system_role <> 'NO_CONNECTION'
      AND NOT EXISTS (
          SELECT 1 FROM gis_connection gc
          WHERE gc.tenant_id = sc.tenant_id
            AND gc.service_connection_id = sc.id
            AND gc.lifecycle_status <> 'ABANDONED');
END;
$$ LANGUAGE plpgsql;
