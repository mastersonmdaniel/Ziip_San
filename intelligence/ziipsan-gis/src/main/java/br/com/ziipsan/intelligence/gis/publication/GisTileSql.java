package br.com.ziipsan.intelligence.gis.publication;

/**
 * SQL do tile MVT da GIS-06 (§10.1): {@code ST_AsMVT} +
 * {@code ST_AsMVTGeom} sobre {@code ST_TileEnvelope}, extent 4096,
 * buffer 64, filtro pelo envelope com margem sobre {@code geom_web}
 * indexada. Classe pura (só monta texto) para ser testável sem banco:
 * a Gis06PublicationIT prova o mesmo formato contra o PostgreSQL.
 *
 * <p>Regras embutidas no SQL:
 * <ul>
 *   <li>tenant SEMPRE como filtro explícito ({@code f.tenant_id = ?}),
 *       além da RLS — defesa em profundidade, como nas demais
 *       consultas GIS;</li>
 *   <li>tipo com {@code min_zoom} maior que o z sai do tile (zoom
 *       mínimo por tipo, §10.1);</li>
 *   <li>atributos mínimos (§10.1): id, code, tipo, DN, material,
 *       ciclo de vida, estado operacional e accuracy_status — o
 *       detalhe vem da api ao clicar (identificação), nunca do
 *       tile;</li>
 *   <li>camada de contexto exclui linhas {@code personal_data} (elas
 *       já nascem anonimizadas na GIS-07, mas tile público de
 *       backoffice não é o canal delas);</li>
 *   <li>imóveis em zoom baixo (z &lt; 15) agregados no servidor por
 *       grade com {@code count} (§10.1: pontos densos agregados no
 *       servidor, não no navegador). A célula é em metros Web
 *       Mercator e encolhe com o zoom.</li>
 * </ul>
 *
 * <p>Parâmetros (na ordem): tenant, z, x, y (o envelope é calculado
 * pelo {@code ST_TileEnvelope} no banco). Nenhum nome de tabela ou
 * coluna vem de entrada externa: tudo sai do enum
 * {@link GisTileLayers}.
 */
public final class GisTileSql {

    public static final int EXTENT = 4096;
    public static final int BUFFER = 64;
    /** Abaixo deste zoom os imóveis são agregados por grade. */
    public static final int PROPERTIES_AGGREGATION_MAX_ZOOM = 14;

    private GisTileSql() {
    }

    public static String tileSql(GisTileLayers layer, int z) {
        return switch (layer.sourceKind()) {
            case FEATURE -> featureSql(layer);
            case CONTEXT -> contextSql();
            case PROPERTIES -> z <= PROPERTIES_AGGREGATION_MAX_ZOOM
                    ? propertiesAggregatedSql() : propertiesSql();
        };
    }

    private static String featureSql(GisTileLayers layer) {
        String table = layer.featureTable();
        String domainFilter = layer.domainKey() == null ? ""
                : " AND fc.domain = '" + layer.domainKey() + "'";
        return """
                WITH tile AS (
                    SELECT ST_TileEnvelope(?, ?, ?) AS env
                ), envelope AS (
                    SELECT env, ST_Expand(env,
                               (ST_XMax(env) - ST_XMin(env)) * 64.0 / 4096.0) AS env_margin
                    FROM tile
                ), features AS (
                    SELECT f.id::text AS id, f.code AS code,
                           ft.code AS type_code,
                           ci.dn_mm AS dn,
                           NULLIF(m.code, 'UNKNOWN') AS material,
                           f.lifecycle_status AS lifecycle_status,
                           f.operational_status AS operational_status,
                           f.accuracy_status AS accuracy_status,
                           ST_AsMVTGeom(f.geom_web, envelope.env, 4096, 64, true) AS geom
                    FROM %s f
                    CROSS JOIN envelope
                    JOIN gis_feature_type ft
                      ON ft.id = f.feature_type_id AND ft.tenant_id = f.tenant_id
                    JOIN gis_feature_class fc
                      ON fc.id = ft.class_id AND fc.tenant_id = ft.tenant_id
                    LEFT JOIN gis_catalog_item ci
                      ON ci.id = f.catalog_item_id AND ci.tenant_id = f.tenant_id
                    LEFT JOIN gis_material m
                      ON m.id = ci.material_id AND m.tenant_id = f.tenant_id
                    WHERE f.tenant_id = ?
                      AND f.geom_web IS NOT NULL
                      AND f.geom_web && envelope.env_margin
                      AND (ft.min_zoom IS NULL OR ft.min_zoom <= ?)%s
                )
                SELECT ST_AsMVT(features.*, ?, 4096, 'geom') FROM features
                """.formatted(table, domainFilter);
    }

    private static String contextSql() {
        return """
                WITH envelope AS (
                    SELECT ST_TileEnvelope(?, ?, ?) AS env
                ), features AS (
                    SELECT c.id::text AS id, c.code AS code,
                           c.context_kind AS type_code,
                           c.name AS name,
                           ST_AsMVTGeom(ST_Transform(c.geom, 3857),
                               envelope.env, 4096, 64, true) AS geom
                    FROM gis_context_feature c
                    CROSS JOIN envelope
                    WHERE c.tenant_id = ?
                      AND NOT c.personal_data
                      AND ST_Transform(c.geom, 3857) && envelope.env
                )
                SELECT ST_AsMVT(features.*, ?, 4096, 'geom') FROM features
                """;
    }

    private static String propertiesSql() {
        return """
                WITH envelope AS (
                    SELECT ST_TileEnvelope(?, ?, ?) AS env
                ), features AS (
                    SELECT u.id::text AS id,
                           ST_AsMVTGeom(ST_Transform(u.coordinates, 3857),
                               envelope.env, 4096, 64, true) AS geom
                    FROM consumer_units u
                    CROSS JOIN envelope
                    WHERE u.tenant_id = ?
                      AND u.coordinates IS NOT NULL
                      AND ST_Transform(u.coordinates, 3857) && envelope.env
                )
                SELECT ST_AsMVT(features.*, ?, 4096, 'geom') FROM features
                """;
    }

    private static String propertiesAggregatedSql() {
        // Célula da grade em metros 3857: ~20 km em z10 encolhendo
        // 4x a cada 2 zooms; o count vai como atributo do ponto.
        return """
                WITH envelope AS (
                    SELECT ST_TileEnvelope(?, ?, ?) AS env
                ), points AS (
                    SELECT ST_Transform(u.coordinates, 3857) AS geom_web
                    FROM consumer_units u
                    CROSS JOIN envelope
                    WHERE u.tenant_id = ?
                      AND u.coordinates IS NOT NULL
                      AND ST_Transform(u.coordinates, 3857) && envelope.env
                ), cells AS (
                    SELECT ST_SnapToGrid(geom_web,
                               (SELECT (ST_XMax(env) - ST_XMin(env)) / 16.0 FROM envelope)) AS cell,
                           count(*) AS unit_count
                    FROM points GROUP BY 1
                ), features AS (
                    SELECT unit_count,
                           ST_AsMVTGeom(ST_Centroid(cell),
                               (SELECT env FROM envelope), 4096, 64, true) AS geom
                    FROM cells
                )
                SELECT ST_AsMVT(features.*, ?, 4096, 'geom') FROM features
                """;
    }

    /**
     * Argumentos na ordem dos {@code ?} do SQL gerado: z, x, y do
     * envelope; nas FEATURE, depois tenant, z (min_zoom do tipo) e o
     * nome da camada MVT; nas demais, tenant e o nome da camada.
     */
    public static Object[] tileArgs(GisTileLayers layer, java.util.UUID tenantId,
                                    int z, int x, int y) {
        return switch (layer.sourceKind()) {
            case FEATURE -> new Object[]{z, x, y, tenantId, z, layer.key()};
            case CONTEXT, PROPERTIES -> new Object[]{z, x, y, tenantId, layer.key()};
        };
    }
}
