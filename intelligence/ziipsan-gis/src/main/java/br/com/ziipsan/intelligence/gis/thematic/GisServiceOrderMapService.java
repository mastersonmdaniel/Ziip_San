package br.com.ziipsan.intelligence.gis.thematic;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.intelligence.gis.thematic.GisThematicDtos.ServiceOrderPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Camada de OS no mapa (SG-491, TR-534 e o temático de OS do
 * TR-1061): OS solicitadas e programadas com posição resolvida
 * pela view {@code gis_thematic_service_order} (V13_13), com
 * filtros por tipo de serviço, situação, equipe (usuário da
 * {@code service_order_crew}) e endereço (texto do alvo), e
 * exportação analítica em CSV (TR-1061).
 *
 * <p>A posição da OS pode ser a da unidade consumidora: a camada é
 * pessoal e exige {@code gis.view-personal} antes de qualquer
 * consulta, como as demais camadas pessoais (GIS-06/GIS-08). O CSV
 * não carrega nome de pessoa nem documento: equipe sai como ids de
 * usuário, que é o que a análise espacial precisa.
 */
@Service
@Transactional(readOnly = true)
public class GisServiceOrderMapService {

    static final int MAX_ORDERS = 20_000;

    private final JdbcTemplate jdbc;
    private final BooleanSupplier canViewPersonal;

    public GisServiceOrderMapService(JdbcTemplate jdbc) {
        this(jdbc, GisThematicContext::canViewPersonal);
    }

    GisServiceOrderMapService(JdbcTemplate jdbc, BooleanSupplier canViewPersonal) {
        this.jdbc = jdbc;
        this.canViewPersonal = canViewPersonal;
    }

    /** Filtros da camada de OS; lista vazia/nula = sem recorte naquele eixo. */
    public record OrderFilters(List<String> statuses, String serviceTypeCode,
                               UUID crewUserId, String addressQuery) {
    }

    public List<ServiceOrderPoint> orders(OrderFilters filters) {
        if (!canViewPersonal.getAsBoolean()) {
            throw new ApiException.Forbidden(
                    "Camada de OS: exige a permissão gis.view-personal.");
        }
        UUID tenantId = GisThematicContext.tenantId();
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT order_id, service_type_code, service_type_name, status,
                       priority, scheduled_for, created_at, address_text,
                       crew_user_ids,
                       ST_X(ST_Transform(geom, 4326)) AS lon,
                       ST_Y(ST_Transform(geom, 4326)) AS lat
                FROM gis_thematic_service_order
                WHERE tenant_id = ?
                """);
        args.add(tenantId);
        if (filters != null && filters.statuses() != null && !filters.statuses().isEmpty()) {
            sql.append(" AND status IN (");
            for (int i = 0; i < filters.statuses().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                args.add(filters.statuses().get(i));
            }
            sql.append(")");
        }
        if (filters != null && filters.serviceTypeCode() != null
                && !filters.serviceTypeCode().isBlank()) {
            sql.append(" AND service_type_code = ?");
            args.add(filters.serviceTypeCode());
        }
        if (filters != null && filters.crewUserId() != null) {
            sql.append(" AND ? = ANY(crew_user_ids)");
            args.add(filters.crewUserId());
        }
        if (filters != null && filters.addressQuery() != null
                && !filters.addressQuery().isBlank()) {
            sql.append(" AND address_text ILIKE ?");
            args.add("%" + filters.addressQuery().trim() + "%");
        }
        sql.append(" ORDER BY created_at, order_id LIMIT ?");
        args.add(MAX_ORDERS);
        return jdbc.query(sql.toString(), (rs, n) -> toPoint(rs), args.toArray());
    }

    /** Exportação analítica (TR-1061): o mesmo recorte dos filtros, em CSV. */
    public String exportCsv(OrderFilters filters) {
        List<ServiceOrderPoint> orders = orders(filters);
        StringBuilder csv = new StringBuilder(
                "order_id;service_type_code;service_type_name;status;priority;"
                        + "scheduled_for;created_at;address_text;crew_user_ids;lon;lat\n");
        for (ServiceOrderPoint o : orders) {
            csv.append(o.orderId()).append(';')
                    .append(csvField(o.serviceTypeCode())).append(';')
                    .append(csvField(o.serviceTypeName())).append(';')
                    .append(csvField(o.status())).append(';')
                    .append(csvField(o.priority())).append(';')
                    .append(o.scheduledFor() == null ? "" : o.scheduledFor().toString()).append(';')
                    .append(o.createdAt() == null ? "" : o.createdAt().toString()).append(';')
                    .append(csvField(o.addressText())).append(';')
                    .append(o.crewUserIds() == null ? "" : o.crewUserIds().toString()).append(';')
                    .append(o.lon()).append(';').append(o.lat()).append('\n');
        }
        return csv.toString();
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ');
        return cleaned.contains(";") || cleaned.contains("\"")
                ? "\"" + cleaned.replace("\"", "\"\"") + "\"" : cleaned;
    }

    private ServiceOrderPoint toPoint(ResultSet rs) throws SQLException {
        List<UUID> crew = List.of();
        Array array = rs.getArray("crew_user_ids");
        if (array != null) {
            Object[] raw = (Object[]) array.getArray();
            List<UUID> ids = new ArrayList<>();
            for (Object item : raw) {
                if (item != null) {
                    ids.add((UUID) item);
                }
            }
            crew = List.copyOf(ids);
        }
        return new ServiceOrderPoint(
                rs.getObject("order_id", UUID.class),
                rs.getString("service_type_code"), rs.getString("service_type_name"),
                rs.getString("status"), rs.getString("priority"),
                rs.getDouble("lon"), rs.getDouble("lat"),
                rs.getString("address_text"), crew,
                rs.getObject("scheduled_for", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
