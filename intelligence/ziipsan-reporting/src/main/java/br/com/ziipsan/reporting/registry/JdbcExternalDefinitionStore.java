package br.com.ziipsan.reporting.registry;

import br.com.ziipsan.core.json.Json;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia REAL dos registros externos em {@code report_definitions}
 * (V15_1) com o {@code tenant_id} do dono - correcao G5 pos-revisao.
 * A tabela ja nasce com tenant_id (linhas de fabrica usam NULL); os
 * registros externos gravam com o tenant preenchido e o indice unico
 * parcial {@code uq_report_definitions_tenant} impede colisao no mesmo
 * tenant. As specs de colunas/filtros vao em jsonb pela classe Json do
 * core. O {@link ReportRegistry} carrega builtins do codigo + externos
 * persistidos do tenant a partir desta leitura.
 */
public class JdbcExternalDefinitionStore implements ExternalDefinitionStore {

    private final EntityManager entityManager;

    public JdbcExternalDefinitionStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void save(UUID tenantId, ReportDefinition definition) {
        entityManager.createNativeQuery(
                        "INSERT INTO report_definitions (id, tenant_id, code, title, origin, view_name, "
                                + "required_permission, columns_spec, filters_spec, default_sort, max_rows, active) "
                                + "VALUES (:id, :tenant, :code, :title, :origin, :view, :perm, "
                                + "CAST(:cols AS jsonb), CAST(:filters AS jsonb), :sort, :max, TRUE)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenant", tenantId)
                .setParameter("code", definition.code())
                .setParameter("title", definition.title())
                .setParameter("origin", definition.origin().name())
                .setParameter("view", definition.viewName())
                .setParameter("perm", definition.requiredPermission())
                .setParameter("cols", columnsJson(definition))
                .setParameter("filters", filtersJson(definition))
                .setParameter("sort", definition.defaultSortColumn())
                .setParameter("max", definition.maxRows())
                .executeUpdate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReportDefinition> loadByTenant(UUID tenantId) {
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT code, title, origin, view_name, required_permission, "
                                + "columns_spec::text, filters_spec::text, default_sort, max_rows "
                                + "FROM report_definitions WHERE tenant_id = :tenant AND active")
                .setParameter("tenant", tenantId)
                .getResultList();
        List<ReportDefinition> loaded = new ArrayList<>();
        for (Object[] row : rows) {
            loaded.add(new ReportDefinition(
                    (String) row[0], (String) row[1], ReportOrigin.valueOf((String) row[2]),
                    (String) row[3], (String) row[4],
                    parseColumns((String) row[5]), parseFilters((String) row[6]),
                    (String) row[7], ((Number) row[8]).intValue()));
        }
        return loaded;
    }

    private static String columnsJson(ReportDefinition definition) {
        return Json.write(definition.columns().stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", c.name());
            map.put("type", c.type().name());
            map.put("personal", c.personal());
            map.put("aggregable", c.aggregable());
            return map;
        }).toList());
    }

    private static String filtersJson(ReportDefinition definition) {
        return Json.write(definition.filters().stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", f.column());
            map.put("operator", f.operator().name());
            return map;
        }).toList());
    }

    private static List<ReportColumn> parseColumns(String json) {
        Object wrapped = Json.readMap("{\"cols\":" + json + "}").get("cols");
        List<ReportColumn> columns = new ArrayList<>();
        if (wrapped instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    boolean personal = Boolean.TRUE.equals(map.get("personal"));
                    Object aggregable = map.get("aggregable");
                    ReportColumnType type = ReportColumnType.valueOf(String.valueOf(map.get("type")));
                    String name = String.valueOf(map.get("name"));
                    columns.add(aggregable instanceof Boolean flag
                            ? new ReportColumn(name, type, personal, flag)
                            : new ReportColumn(name, type, personal));
                }
            }
        }
        return columns;
    }

    private static List<ReportFilter> parseFilters(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object wrapped = Json.readMap("{\"filters\":" + json + "}").get("filters");
        List<ReportFilter> filters = new ArrayList<>();
        if (wrapped instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    filters.add(new ReportFilter(String.valueOf(map.get("name")),
                            ReportFilter.Operator.EQ_OR_RANGE));
                }
            }
        }
        return filters;
    }
}
