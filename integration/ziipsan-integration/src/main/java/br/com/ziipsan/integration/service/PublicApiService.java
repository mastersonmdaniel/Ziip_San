package br.com.ziipsan.integration.service;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.integration.sandbox.SandboxFixtures;
import br.com.ziipsan.integration.security.ApiClientAuthentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Recursos da primeira entrega da API pública (P2.26): leitura de
 * properties (unidades consumidoras), bills, payments e service-orders,
 * mais a criação de service-order em DRAFT.
 *
 * <p>Segurança por construção (OWASP API Security Top 10):
 * <ul>
 *   <li>autorização por objeto: toda consulta filtra por
 *       {@code tenant_id} do token e o detalhe por id que não é do
 *       tenant é 404 — o mesmo 404 de id inexistente, sem oráculo;</li>
 *   <li>autorização por propriedade: só os campos da lista branca
 *       abaixo saem. Nome de titular, documento, endereço e telefone
 *       NUNCA saem pela API pública v1 (não há campo para eles);</li>
 *   <li>paginação limitada (máximo 100 por página) e filtros só por
 *       colunas com índice, sem texto livre;</li>
 *   <li>escrita: só DRAFT, só tipo de OS existente no tenant, só alvo
 *       unidade existente no tenant; o resto do ciclo de vida da OS
 *       continua no backoffice (o motor da P1.31 rege transições).</li>
 * </ul>
 * Em sandbox, nada disto toca o banco: voltam as fixtures sintéticas.
 */
@Service
public class PublicApiService {

    public record Page<T>(List<T> items, int page, int size, long total) {
    }

    private final DataSource dataSource;

    public PublicApiService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    private static boolean sandbox() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof ApiClientAuthentication api && api.isSandbox();
    }

    private static UUID actorClientId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ApiClientAuthentication api) {
            return api.clientUuid();
        }
        return null;
    }

    // ------------------------------------------------------------- properties
    public Page<Map<String, Object>> properties(int page, int size, String status, String registrationNumber) {
        int[] paging = paging(page, size);
        if (sandbox()) {
            return sandboxPage(SandboxFixtures.properties(), paging[0], paging[1]);
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            StringBuilder where = new StringBuilder("WHERE tenant_id = ? AND deleted_at IS NULL");
            List<Object> params = new ArrayList<>();
            params.add(IntegrationContext.tenantId());
            if (status != null) {
                where.append(" AND status = ?");
                params.add(status);
            }
            if (registrationNumber != null) {
                where.append(" AND registration_number = ?");
                params.add(registrationNumber);
            }
            long total;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM consumer_units " + where)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, registration_number, check_digit, status, lot, sublot, built_area_m2, due_day"
                            + " FROM consumer_units " + where
                            + " ORDER BY registration_number LIMIT ? OFFSET ?")) {
                bind(ps, params);
                ps.setInt(params.size() + 1, paging[1]);
                ps.setInt(params.size() + 2, paging[0] * paging[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", rs.getObject(1).toString());
                        item.put("registrationNumber", rs.getString(2));
                        item.put("checkDigit", rs.getString(3));
                        item.put("status", rs.getString(4));
                        item.put("lot", rs.getObject(5));
                        item.put("sublot", rs.getObject(6));
                        item.put("builtAreaM2", rs.getObject(7));
                        item.put("dueDay", rs.getObject(8));
                        items.add(item);
                    }
                }
            }
            return new Page<>(items, paging[0], paging[1], total);
        });
    }

    public Map<String, Object> property(UUID id) {
        if (sandbox()) {
            return SandboxFixtures.properties().stream()
                    .filter(p -> p.get("id").equals(id.toString()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException.NotFound("O imóvel não existe neste tenant."));
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, registration_number, check_digit, status, lot, sublot, built_area_m2, due_day"
                            + " FROM consumer_units WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("O imóvel não existe neste tenant.");
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getObject(1).toString());
                    item.put("registrationNumber", rs.getString(2));
                    item.put("checkDigit", rs.getString(3));
                    item.put("status", rs.getString(4));
                    item.put("lot", rs.getObject(5));
                    item.put("sublot", rs.getObject(6));
                    item.put("builtAreaM2", rs.getObject(7));
                    item.put("dueDay", rs.getObject(8));
                    return item;
                }
            }
        });
    }

    // ------------------------------------------------------------------ bills
    public Page<Map<String, Object>> bills(int page, int size, UUID unitId, String status) {
        int[] paging = paging(page, size);
        if (sandbox()) {
            return sandboxPage(SandboxFixtures.bills(), paging[0], paging[1]);
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(IntegrationContext.tenantId());
            if (unitId != null) {
                where.append(" AND unit_id = ?");
                params.add(unitId);
            }
            if (status != null) {
                where.append(" AND status = ?");
                params.add(status);
            }
            long total;
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM bills " + where)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, unit_id, reference_month, status, total_value, due_date, issued_on"
                            + " FROM bills " + where
                            + " ORDER BY reference_month DESC LIMIT ? OFFSET ?")) {
                bind(ps, params);
                ps.setInt(params.size() + 1, paging[1]);
                ps.setInt(params.size() + 2, paging[0] * paging[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(billView(rs));
                    }
                }
            }
            return new Page<>(items, paging[0], paging[1], total);
        });
    }

    public Map<String, Object> bill(UUID id) {
        if (sandbox()) {
            return SandboxFixtures.bills().stream()
                    .filter(b -> b.get("id").equals(id.toString()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException.NotFound("A conta não existe neste tenant."));
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, unit_id, reference_month, status, total_value, due_date, issued_on"
                            + " FROM bills WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("A conta não existe neste tenant.");
                    }
                    return billView(rs);
                }
            }
        });
    }

    private static Map<String, Object> billView(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getObject(1).toString());
        item.put("unitId", rs.getObject(2).toString());
        item.put("referenceMonth", dateString(rs.getDate(3)));
        item.put("status", rs.getString(4));
        item.put("totalValue", rs.getObject(5, BigDecimal.class));
        item.put("dueDate", dateString(rs.getDate(6)));
        item.put("issuedOn", dateString(rs.getDate(7)));
        return item;
    }

    // --------------------------------------------------------------- payments
    public Page<Map<String, Object>> payments(int page, int size, UUID unitId, String status) {
        int[] paging = paging(page, size);
        if (sandbox()) {
            return sandboxPage(SandboxFixtures.payments(), paging[0], paging[1]);
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            StringBuilder where = new StringBuilder("WHERE tenant_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(IntegrationContext.tenantId());
            if (unitId != null) {
                where.append(" AND unit_id = ?");
                params.add(unitId);
            }
            if (status != null) {
                // Convergência V112 (decisão 75, PD-2/PD-3): o filtro chega no vocabulário
                // FEBRABAN do contrato e casa os literais canónicos correspondentes — os
                // dois membros do par quando há normalização (ex.: CLASSIFIED casa
                // CLASSIFIED e CLASSIFICADO). O contrato não muda.
                List<String> canonicalStatuses = PaymentStatusTranslation.canonicalStatusesForApiFilter(status);
                where.append(" AND status IN (");
                for (int i = 0; i < canonicalStatuses.size(); i++) {
                    if (i > 0) {
                        where.append(", ");
                    }
                    where.append("?");
                    params.add(canonicalStatuses.get(i));
                }
                where.append(")");
            }
            long total;
            try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM payments " + where)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, unit_id, document_type, document_id, amount, paid_on, status"
                            + " FROM payments " + where
                            + " ORDER BY paid_on DESC LIMIT ? OFFSET ?")) {
                bind(ps, params);
                ps.setInt(params.size() + 1, paging[1]);
                ps.setInt(params.size() + 2, paging[0] * paging[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(paymentView(rs));
                    }
                }
            }
            return new Page<>(items, paging[0], paging[1], total);
        });
    }

    public Map<String, Object> payment(UUID id) {
        if (sandbox()) {
            return SandboxFixtures.payments().stream()
                    .filter(p -> p.get("id").equals(id.toString()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException.NotFound("O pagamento não existe neste tenant."));
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, unit_id, document_type, document_id, amount, paid_on, status"
                            + " FROM payments WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("O pagamento não existe neste tenant.");
                    }
                    return paymentView(rs);
                }
            }
        });
    }

    private static Map<String, Object> paymentView(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getObject(1).toString());
        Object unitId = rs.getObject(2);
        item.put("unitId", unitId == null ? null : unitId.toString());
        item.put("documentType", rs.getString(3));
        Object documentId = rs.getObject(4);
        item.put("documentId", documentId == null ? null : documentId.toString());
        item.put("amount", rs.getObject(5, BigDecimal.class));
        item.put("paidOn", dateString(rs.getDate(6)));
        // Convergência V112 (decisão 75, PD-2/PD-3): o status gravado é canónico;
        // expõe-se o literal FEBRABAN do contrato (os pares normalizam-se de volta;
        // BAIXADO/RECEBIDO/TRANSFERIDO passam tal como estão — sem tradução inventada).
        item.put("status", PaymentStatusTranslation.apiStatusForCanonical(rs.getString(7)));
        return item;
    }

    // ----------------------------------------------------------- service orders
    public Page<Map<String, Object>> serviceOrders(int page, int size, String status) {
        int[] paging = paging(page, size);
        if (sandbox()) {
            return sandboxPage(SandboxFixtures.serviceOrders(), paging[0], paging[1]);
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            StringBuilder where = new StringBuilder("WHERE so.tenant_id = ?");
            List<Object> params = new ArrayList<>();
            params.add(IntegrationContext.tenantId());
            if (status != null) {
                where.append(" AND so.status = ?");
                params.add(status);
            }
            long total;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT count(*) FROM service_orders so " + where)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT so.id, st.code, so.status, so.origin, so.execution_mode, so.created_at"
                            + " FROM service_orders so LEFT JOIN service_types st"
                            + " ON st.id = so.service_type_id AND st.tenant_id = so.tenant_id "
                            + where + " ORDER BY so.created_at DESC LIMIT ? OFFSET ?")) {
                bind(ps, params);
                ps.setInt(params.size() + 1, paging[1]);
                ps.setInt(params.size() + 2, paging[0] * paging[1]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(serviceOrderView(rs));
                    }
                }
            }
            return new Page<>(items, paging[0], paging[1], total);
        });
    }

    public Map<String, Object> serviceOrder(UUID id) {
        if (sandbox()) {
            return SandboxFixtures.serviceOrders().stream()
                    .filter(o -> o.get("id").equals(id.toString()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException.NotFound("A ordem de serviço não existe neste tenant."));
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT so.id, st.code, so.status, so.origin, so.execution_mode, so.created_at"
                            + " FROM service_orders so LEFT JOIN service_types st"
                            + " ON st.id = so.service_type_id AND st.tenant_id = so.tenant_id"
                            + " WHERE so.tenant_id = ? AND so.id = ?")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setObject(2, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.NotFound("A ordem de serviço não existe neste tenant.");
                    }
                    return serviceOrderView(rs);
                }
            }
        });
    }

    private static Map<String, Object> serviceOrderView(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", rs.getObject(1).toString());
        item.put("serviceTypeCode", rs.getString(2));
        item.put("status", rs.getString(3));
        item.put("origin", rs.getString(4));
        item.put("executionMode", rs.getString(5));
        Timestamp created = rs.getTimestamp(6);
        item.put("createdAt", created == null ? null : created.toInstant().toString());
        return item;
    }

    /** Cria uma OS em DRAFT, interna, com tipo e alvo validados no tenant. */
    public Map<String, Object> createServiceOrder(String serviceTypeCode, UUID unitId) {
        if (serviceTypeCode == null || serviceTypeCode.isBlank()) {
            throw new ApiException.Validation("O tipo da ordem de serviço é obrigatório.",
                    List.of(new ApiException.FieldError("serviceTypeCode", "required",
                            "Informe o código do tipo de OS.")), List.of());
        }
        if (sandbox()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", UUID.randomUUID().toString());
            item.put("serviceTypeCode", serviceTypeCode);
            item.put("status", "DRAFT");
            item.put("origin", "INTERNAL");
            item.put("executionMode", "INTERNAL");
            item.put("sandbox", true);
            return item;
        }
        UUID actor = actorClientId();
        if (actor == null) {
            throw new ApiException.Forbidden("A criação de OS pela API pública exige um cliente autenticado.");
        }
        return TenantJdbc.inTenantTransaction(dataSource, c -> {
            UUID typeId;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id FROM service_types WHERE tenant_id = ? AND code = ? AND active = true")) {
                ps.setObject(1, IntegrationContext.tenantId());
                ps.setString(2, serviceTypeCode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.Validation("O tipo de OS não existe ou está inativo neste tenant.",
                                List.of(new ApiException.FieldError("serviceTypeCode", "not-found",
                                        "Tipo de OS desconhecido neste tenant.")), List.of());
                    }
                    typeId = (UUID) rs.getObject(1);
                }
            }
            if (unitId != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM consumer_units WHERE tenant_id = ? AND id = ? AND deleted_at IS NULL")) {
                    ps.setObject(1, IntegrationContext.tenantId());
                    ps.setObject(2, unitId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new ApiException.Validation("A unidade alvo não existe neste tenant.",
                                    List.of(new ApiException.FieldError("unitId", "not-found",
                                            "Unidade desconhecida neste tenant.")), List.of());
                        }
                    }
                }
            }
            UUID orderId = UUID.randomUUID();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO service_orders (id, tenant_id, service_type_id, origin, execution_mode,"
                            + " status, created_by) VALUES (?, ?, ?, 'INTERNAL', 'INTERNAL', 'DRAFT', ?)")) {
                ps.setObject(1, orderId);
                ps.setObject(2, IntegrationContext.tenantId());
                ps.setObject(3, typeId);
                ps.setObject(4, actor);
                ps.executeUpdate();
            }
            if (unitId != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO service_order_targets (id, tenant_id, order_id, target_type, target_id, created_by)"
                                + " VALUES (?, ?, ?, 'CONSUMER_UNIT', ?, ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, IntegrationContext.tenantId());
                    ps.setObject(3, orderId);
                    ps.setObject(4, unitId);
                    ps.setObject(5, actor);
                    ps.executeUpdate();
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", orderId.toString());
            item.put("serviceTypeCode", serviceTypeCode);
            item.put("status", "DRAFT");
            item.put("origin", "INTERNAL");
            item.put("executionMode", "INTERNAL");
            return item;
        });
    }

    // ------------------------------------------------------------------ utils
    private static int[] paging(int page, int size) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        if (page < 0) {
            errors.add(new ApiException.FieldError("page", "invalid", "A página começa em 0."));
        }
        if (size < 1 || size > 100) {
            errors.add(new ApiException.FieldError("size", "invalid", "O tamanho da página é de 1 a 100."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException.Validation("Há campos inválidos.", errors, List.of());
        }
        return new int[]{page, size};
    }

    private static <T> Page<T> sandboxPage(List<T> all, int page, int size) {
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new Page<>(all.subList(from, to), page, size, all.size());
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            if (param instanceof UUID uuid) {
                ps.setObject(i + 1, uuid);
            } else {
                ps.setString(i + 1, String.valueOf(param));
            }
        }
    }

    private static String dateString(java.sql.Date date) {
        return date == null ? null : date.toLocalDate().toString();
    }
}
