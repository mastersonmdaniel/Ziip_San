package br.com.ziipsan.intelligence.gis.thematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SQL da GIS-08, montado SOMENTE a partir da lista fechada do
 * {@link GisThematicSources}: nome de view, coluna de categoria,
 * coluna de medida e chaves de filtro nunca vêm de entrada externa
 * — vêm do enum ou da definição já validada contra o enum. Valores
 * são sempre parâmetros {@code ?}. Classe pura, testável sem banco,
 * no padrão do {@code GisTileSql} (GIS-06).
 *
 * <p>Invariantes embutidas:
 * <ul>
 *   <li>tenant SEMPRE como filtro explícito ({@code tenant_id = ?}),
 *       além da RLS e do {@code security_invoker} das views —
 *       defesa em profundidade, como nas demais consultas GIS;</li>
 *   <li>o SQL público de fonte pessoal NÃO seleciona geometria nem
 *       id de unidade: agrega por célula (quadra ou setor) e o
 *       {@code HAVING} da célula aplica o mínimo de unidades —
 *       célula abaixo do mínimo não gera linha (§12, item 3);</li>
 *   <li>o ponto do backoffice de rede é o centroide do arco (o
 *       traço completo é do tile da GIS-06, não do temático);</li>
 *   <li>filtro por categoria (SG-883) é igualdade sobre
 *       {@code category_code}, só nas fontes de unidade.</li>
 * </ul>
 */
public final class GisThematicSql {

    public record BuiltSql(String sql, List<Object> args) {
    }

    private GisThematicSql() {
    }

    // ------------------------------------------------------------------ pontos

    /**
     * Pontos do backoffice (e do canal público para fontes NÃO
     * pessoais publicáveis). Args: tenant, filtros..., limite.
     */
    public static BuiltSql pointsSql(GisThematicSources source, String categoryField,
                                     String measureField, Map<String, Object> filter,
                                     String categoryCode, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        String geomExpr = source == GisThematicSources.NETWORK_ARC
                ? "ST_Centroid(geom)" : "geom";
        sql.append("SELECT ").append(source.idColumn()).append("::text AS id, ")
                .append("ST_X(ST_Transform(").append(geomExpr).append(", 4326)) AS lon, ")
                .append("ST_Y(ST_Transform(").append(geomExpr).append(", 4326)) AS lat, ")
                .append(categoryField == null ? "NULL::text" : categoryField + "::text")
                .append(" AS category, ")
                .append(measureField == null ? "NULL::numeric" : measureField)
                .append(" AS measure ")
                .append("FROM ").append(source.viewName())
                .append(" WHERE tenant_id = ?");
        args.add("__TENANT__");
        appendFilters(sql, args, source, filter);
        if (categoryCode != null && source.unitBased()) {
            sql.append(" AND category_code = ?");
            args.add(categoryCode);
        }
        sql.append(" ORDER BY ").append(source.idColumn()).append(" LIMIT ?");
        args.add(limit);
        return new BuiltSql(sql.toString(), List.copyOf(args));
    }

    // ------------------------------------------------------------------ público

    /**
     * Agregado público de fonte pessoal de unidade: uma linha por
     * (célula, categoria), só de células com pelo menos
     * {@code minUnits} unidades distintas. NENHUMA coluna de ponto,
     * id de unidade ou geometria de unidade é selecionada.
     * Args: tenant, filtros..., minUnits.
     */
    public static BuiltSql publicAggregateSql(GisThematicSources source, String categoryField,
                                              Map<String, Object> filter, String aggregation,
                                              int minUnits) {
        if (!source.unitBased()) {
            throw new IllegalArgumentException(
                    "agregado público só existe para fonte de unidade: " + source.name());
        }
        boolean bySector = "SECTOR".equals(aggregation);
        String cellExpr = bySector ? "v.sector_id" : "v.block_id";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered AS (")
                .append("SELECT v.unit_id, v.").append(cellExpr.substring(2)).append(" AS cell_id, ")
                .append(categoryField == null ? "NULL::text" : "v." + categoryField + "::text")
                .append(" AS category_value ")
                .append("FROM ").append(source.viewName()).append(" v ")
                .append("WHERE v.tenant_id = ? AND ").append(cellExpr).append(" IS NOT NULL");
        args.add("__TENANT__");
        appendFilters(sql, args, source, filter);
        sql.append("), cell_totals AS (")
                .append("SELECT cell_id, COUNT(DISTINCT unit_id) AS unit_count ")
                .append("FROM filtered GROUP BY cell_id HAVING COUNT(DISTINCT unit_id) >= ?")
                .append(") ");
        args.add(minUnits);
        sql.append("SELECT ct.cell_id::text AS cell_id, ")
                .append("f.category_value, COUNT(*) AS category_count, ct.unit_count ")
                .append("FROM filtered f JOIN cell_totals ct ON ct.cell_id = f.cell_id ")
                .append("GROUP BY ct.cell_id, f.category_value, ct.unit_count ")
                .append("ORDER BY ct.cell_id, f.category_value");
        return new BuiltSql(sql.toString(), List.copyOf(args));
    }

    /** Total de células com alguma unidade (para contar as suprimidas). */
    public static BuiltSql publicCellCountSql(GisThematicSources source,
                                              Map<String, Object> filter, String aggregation) {
        boolean bySector = "SECTOR".equals(aggregation);
        String cellColumn = bySector ? "sector_id" : "block_id";
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(DISTINCT ").append(cellColumn).append(") FROM ")
                .append(source.viewName()).append(" v WHERE v.tenant_id = ? AND ")
                .append(cellColumn).append(" IS NOT NULL");
        args.add("__TENANT__");
        appendFilters(sql, args, source, filter);
        return new BuiltSql(sql.toString(), List.copyOf(args));
    }

    // ------------------------------------------------------------------ relatórios

    /** Extensão em qualquer data (função da V13_13). Args: a data. */
    public static String extensionAtSql() {
        return """
                SELECT dominio, funcao, material, dn_mm, decada_inicio,
                       setor_codigo, setor, extensao_m, arcos
                FROM gis_network_extension_at(?)
                ORDER BY dominio NULLS LAST, funcao NULLS LAST,
                         material NULLS LAST, dn_mm NULLS LAST,
                         decada_inicio NULLS LAST, setor_codigo NULLS LAST
                """;
    }

    /** Precisão agrupada por classe, método e grupo. Args: tenant. */
    public static String precisionSummarySql() {
        return """
                SELECT classe, metodo_captura, grupo_precisao, dominio,
                       SUM(feicoes) AS feicoes
                FROM rpt_gis_precisao_cadastro
                WHERE tenant_id = ?
                GROUP BY classe, metodo_captura, grupo_precisao, dominio
                ORDER BY classe, metodo_captura, grupo_precisao
                """;
    }

    /** Estatísticas da seleção por polígono (TR-1059). Args: geoJson, tenant x3. */
    public static String polygonStatsSql() {
        return """
                WITH poly AS (
                    SELECT ST_SetSRID(ST_GeomFromGeoJSON(?), 4674) AS geom
                ), units_in AS (
                    SELECT b.unit_id, b.economies
                    FROM gis_thematic_unit_base b, poly
                    WHERE b.tenant_id = ?
                      AND b.geom IS NOT NULL
                      AND ST_Within(b.geom, poly.geom)
                )
                SELECT (SELECT COUNT(*) FROM units_in) AS units,
                       (SELECT COALESCE(SUM(economies), 0) FROM units_in) AS economies,
                       (SELECT COUNT(DISTINCT sc.id)
                          FROM service_connections sc
                          JOIN service_connection_units scu
                            ON scu.tenant_id = sc.tenant_id
                           AND scu.connection_id = sc.id
                         WHERE sc.tenant_id = ?
                           AND sc.ended_on IS NULL
                           AND scu.unit_id IN (SELECT unit_id FROM units_in)) AS connections,
                       (SELECT AVG(c.billed_consumption)
                          FROM gis_thematic_unit_consumption c
                         WHERE c.tenant_id = ?
                           AND c.unit_id IN (SELECT unit_id FROM units_in)) AS avg_consumption,
                       (SELECT ST_Area(geom::geography) FROM poly) AS area_m2
                """;
    }

    // ------------------------------------------------------------------ filtros

    /**
     * Traduz o filter_json (já validado contra a fonte) para ANDs
     * parametrizados. Chaves especiais da fonte de OS
     * ({@code statuses}, {@code serviceTypeCodes}) e chaves-coluna
     * das demais fontes; escalar vira {@code = ?} e lista vira
     * {@code IN (?, ...)}; lista vazia em serviceTypeCodes já foi
     * recusada antes (temático não configurado) e aqui é ignorada
     * por segurança — nunca vira {@code IN ()}.
     */
    static void appendFilters(StringBuilder sql, List<Object> args,
                              GisThematicSources source, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            String column = switch (key) {
                case "statuses" -> "status";
                case "serviceTypeCodes" -> "service_type_code";
                default -> key;
            };
            if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    continue;
                }
                sql.append(" AND ").append(column).append(" IN (");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append("?");
                    args.add(list.get(i));
                }
                sql.append(")");
            } else {
                sql.append(" AND ").append(column).append(" = ?");
                args.add(value);
            }
        }
    }
}
