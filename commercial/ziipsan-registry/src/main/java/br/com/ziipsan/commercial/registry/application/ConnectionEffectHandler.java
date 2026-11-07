package br.com.ziipsan.commercial.registry.application;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.serviceorder.EffectHandlerRegistry;
import br.com.ziipsan.core.serviceorder.TargetType;
import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Executor dos efeitos de ligação da ED-13 sobre a base da P2.23: o
 * handler que o módulo de cadastro registra no
 * {@code EffectHandlerRegistry} do núcleo de OS (P1.31, ADR-0039/
 * ADR-0045) para os seis códigos de {@link ConnectionOperation} — o
 * registro completo (handlers + verificadores de alvo + declarações de
 * tipo) é {@link ConnectionServiceOrderIntegration}.
 *
 * <p>Roda na transação do motor (a {@code Connection} do
 * {@code EffectContext} — nunca commita): lê a ligação e os papéis do
 * catálogo único pela mesma conexão, valida a transição com
 * {@link ConnectionOperationRules}, atualiza {@code service_connections}
 * e grava {@code connection_status_history} com o
 * {@code service_order_id} do contexto (coluna NOT NULL da V3_14) e os
 * {@code parameterVersionIds} da carga, quando presentes. Falha em
 * qualquer ponto desfaz o efeito inteiro no motor.
 *
 * <p>Bifurcações REAIS do catálogo (ADR-0038), consumidas aqui:
 * <ul>
 *   <li>SUPPRESS (qualquer tipo): o portão
 *     {@code connection.voluntary_suppression_with_debt} decide sobre o
 *     débito em aberto consultado nas contas das unidades atendidas
 *     (ver {@link #hasOpenDebt}); a decisão é aplicada de fato — BLOCK
 *     e ALLOW_WITH_PERMISSION sem permissão recusam o efeito. A taxa
 *     da supressão é da ED-34; a DECISÃO é desta tarefa.</li>
 *   <li>CUT/SUPPRESS da ÁGUA: {@code sewer.out_of_use_on_water_cut} =
 *     AUTOMATIC aplica, NA MESMA OS, o efeito correspondente na
 *     ligação de esgoto do mesmo imóvel (situação-alvo declarada em
 *     {@code sewerStatusCode}); SERVICE_ORDER_ONLY e NONE não cascateiam.</li>
 * </ul>
 *
 * <p>Carga do efeito (validada também pelo {@code effect_schema} do
 * tipo de serviço declarado em {@link ConnectionServiceOrderIntegration}):
 * {@code statusCode} (obrigatório — o código, no catálogo do tenant, da
 * situação-alvo), {@code reason} (opcional, até 200 caracteres — a
 * coluna do histórico), {@code sewerStatusCode} (obrigatório só na
 * cascata AUTOMATIC com ligação de esgoto vigente),
 * {@code suppressionPermissionGranted} (booleano, na supressão),
 * {@code hasOpenDebt} (booleano; só usado quando a tabela de contas
 * não existe no schema — sem ela e sem a declaração, a supressão é
 * recusada em vez de presumir ausência de débito) e
 * {@code parameterVersionIds} (opcional, lista de UUIDs).
 *
 * <p>Ligação ENCERRADA ({@code ended_on} preenchido) não recebe
 * operação: pela regra 1:1 ela já pode ter sido substituída por outra.
 *
 * <p>A projeção legada {@code consumer_units.water_status_id /
 * sewer_status_id} é atualizada aqui como o serviço da P2.23 fazia
 * (mesmos ids do catálogo único): ela continua sendo projeção — a
 * fonte é a ligação, e o ciclo da ED-13 lê a ligação (ver
 * {@code ConnectionCycleRule} no billing).
 */
public final class ConnectionEffectHandler implements EffectHandlerRegistry.EffectHandler {

    /** Tamanho da coluna reason do histórico (V3_14: VARCHAR(200)). */
    static final int REASON_MAX_LENGTH = 200;

    private final ConnectionOperation operation;

    public ConnectionEffectHandler(ConnectionOperation operation) {
        this.operation = operation;
    }

    /** Registra os seis efeitos de ligação no registry de uma instância do motor. */
    public static void registerAll(EffectHandlerRegistry registry) {
        for (ConnectionOperation op : ConnectionOperation.values()) {
            registry.register(op.effectCode(), new ConnectionEffectHandler(op));
        }
    }

    @Override
    public void apply(EffectHandlerRegistry.EffectContext context) throws Exception {
        JsonNode payload = Json.readTree(context.effectPayloadJson());
        if (payload == null || !payload.isObject() || !payload.hasNonNull("statusCode")) {
            throw new ApiException.BadRequest("efeito-de-ligacao-sem-situacao-alvo",
                    "A carga do efeito " + operation.effectCode()
                            + " exige statusCode (codigo da situacao-alvo no catalogo do tenant).");
        }
        if (!payload.path("statusCode").isTextual()) {
            throw new ApiException.BadRequest("efeito-de-ligacao-sem-situacao-alvo",
                    "statusCode da carga do efeito " + operation.effectCode()
                            + " precisa ser texto (codigo da situacao-alvo).");
        }
        String statusCode = payload.path("statusCode").asText();
        String reason = readReason(payload);
        List<UUID> parameterVersionIds = readParameterVersionIds(payload);
        Connection conn = context.connection();
        String schema = context.schema();

        Row current = readConnection(conn, schema, context.tenantId(), context.targetId());
        if (current.endedOn() != null) {
            throw new ApiException.Conflict("ligacao-encerrada",
                    "Ligacao " + current.id() + " encerrada em " + current.endedOn()
                            + ": operacao de OS nao se aplica a ligacao encerrada.");
        }
        TargetType expected = current.kind == ConnectionStatus.Kind.WATER
                ? TargetType.WATER_CONNECTION : TargetType.SEWER_CONNECTION;
        if (context.targetType() != expected) {
            throw new ApiException.BadRequest("alvo-de-ligacao-incompativel",
                    "Ligacao " + current.kind + " exige alvo " + expected
                            + "; a OS traz " + context.targetType() + ".");
        }
        ConnectionStatus currentStatus = readStatus(conn, schema, context.tenantId(), current.statusId);
        ConnectionStatus target = readStatusByCode(conn, schema, context.tenantId(),
                current.kind, statusCode);
        ConnectionOperationRules.requireTransition(operation, current.kind,
                currentStatus.getSystemRole(), target);

        ConnectionForkParameters.Values forks = null;
        if (operation == ConnectionOperation.SUPPRESS
                || (current.kind == ConnectionStatus.Kind.WATER
                && operation.triggersSewerOutOfUseCascade())) {
            forks = ConnectionForkParameters.parse(
                    readAcceptedParameters(conn, schema, context.tenantId()));
        }
        if (operation == ConnectionOperation.SUPPRESS) {
            boolean hasDebt = hasOpenDebt(conn, schema, context.tenantId(), current.id(), payload);
            boolean permission = readBooleanFlag(payload, "suppressionPermissionGranted");
            ConnectionForkParameters.requireSuppressionAllowed(forks, hasDebt, permission);
        }

        applyChange(conn, schema, context, current, target, reason, parameterVersionIds);

        // Cascata do esgoto 'fora de uso' (AUTOMATIC = mesma OS).
        if (forks != null && current.kind == ConnectionStatus.Kind.WATER
                && operation.triggersSewerOutOfUseCascade()
                && forks.sewerCascadeOnWaterCutOrSuppression()
                == ConnectionForkParameters.SewerCascadeDecision.APPLY_IN_SAME_ORDER) {
            applySewerCascade(conn, schema, context, current, payload, reason, parameterVersionIds);
        }
    }

    /**
     * AUTOMATIC: a ligação de esgoto vigente do mesmo imóvel recebe, na
     * mesma transação/OS, a operação correspondente (CUT->CUT,
     * SUPPRESS->SUPPRESS) para a situação declarada em
     * {@code sewerStatusCode}. Sem ligação de esgoto vigente, não há o
     * que cascatear. Situação não declarada ou transição inválida do
     * esgoto recusam o efeito INTEIRO (a OS é uma só).
     */
    private void applySewerCascade(Connection conn, String schema,
                                   EffectHandlerRegistry.EffectContext context,
                                   Row water, JsonNode payload, String reason,
                                   List<UUID> parameterVersionIds) throws SQLException {
        Row sewer = readSewerConnection(conn, schema, context.tenantId(), water.propertyUnitId());
        if (sewer == null) {
            return;
        }
        if (!payload.hasNonNull("sewerStatusCode") || !payload.path("sewerStatusCode").isTextual()) {
            throw new ApiException.BadRequest("cascata-esgoto-sem-situacao-alvo",
                    "A bifurcacao " + ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT
                            + "=AUTOMATIC exige sewerStatusCode na carga (situacao 'fora de uso' "
                            + "do esgoto no catalogo do tenant).");
        }
        ConnectionStatus sewerCurrent = readStatus(conn, schema, context.tenantId(), sewer.statusId);
        ConnectionStatus sewerTarget = readStatusByCode(conn, schema, context.tenantId(),
                ConnectionStatus.Kind.SEWER, payload.path("sewerStatusCode").asText());
        ConnectionOperationRules.requireTransition(operation, ConnectionStatus.Kind.SEWER,
                sewerCurrent.getSystemRole(), sewerTarget);
        applyChange(conn, schema, context, sewer, sewerTarget, reason, parameterVersionIds);
    }

    // ------------------------------------------------------------ carga

    private static String readReason(JsonNode payload) {
        if (!payload.hasNonNull("reason")) {
            return null;
        }
        if (!payload.path("reason").isTextual()) {
            throw new ApiException.BadRequest("motivo-do-efeito-invalido",
                    "reason da carga do efeito precisa ser texto.");
        }
        String reason = payload.path("reason").asText();
        if (reason.length() > REASON_MAX_LENGTH) {
            throw new ApiException.BadRequest("motivo-do-efeito-longo",
                    "reason excede " + REASON_MAX_LENGTH + " caracteres (coluna do historico): "
                            + reason.length() + ".");
        }
        return reason;
    }

    private static List<UUID> readParameterVersionIds(JsonNode payload) {
        List<UUID> ids = new ArrayList<>();
        if (!payload.hasNonNull("parameterVersionIds")) {
            return ids;
        }
        JsonNode node = payload.path("parameterVersionIds");
        if (!node.isArray()) {
            throw new ApiException.BadRequest("versoes-de-parametro-invalidas",
                    "parameterVersionIds da carga do efeito precisa ser uma lista de UUIDs.");
        }
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                throw new ApiException.BadRequest("versoes-de-parametro-invalidas",
                        "parameterVersionIds so aceita UUIDs em texto.");
            }
            try {
                ids.add(UUID.fromString(item.asText()));
            } catch (IllegalArgumentException e) {
                throw new ApiException.BadRequest("versoes-de-parametro-invalidas",
                        "parameterVersionIds com valor que nao e UUID: " + item.asText());
            }
        }
        return ids;
    }

    private static boolean readBooleanFlag(JsonNode payload, String field) {
        if (!payload.hasNonNull(field)) {
            return false;
        }
        if (!payload.path(field).isBoolean()) {
            throw new ApiException.BadRequest("carga-do-efeito-invalida",
                    field + " da carga do efeito precisa ser booleano.");
        }
        return payload.path(field).booleanValue();
    }

    // ------------------------------------------------------------ débito

    /**
     * Débito em aberto das unidades atendidas pela ligação: conta
     * completa, vencida, com valor, em situação exigível (fora PAID e
     * os estados mortos da V4_1) na tabela {@code bills} do mesmo
     * schema do tenant (leitura — o domínio da conta é do billing).
     * Sem a tabela no schema (instalação sem o billing), vale a
     * declaração {@code hasOpenDebt} da carga; sem nenhum dos dois, a
     * supressão é recusada: ausência de consulta nunca vira "sem
     * débito" presumido.
     */
    private static boolean hasOpenDebt(Connection conn, String schema, UUID tenantId,
                                       UUID connectionId, JsonNode payload) throws SQLException {
        boolean billsExists;
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, schema + ".bills");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                billsExists = rs.getBoolean(1);
            }
        }
        if (!billsExists) {
            if (payload.hasNonNull("hasOpenDebt")) {
                return readBooleanFlag(payload, "hasOpenDebt");
            }
            throw new ApiException.BadRequest("supressao-exige-consulta-de-debito",
                    "Sem a tabela de contas no schema, a supressao exige hasOpenDebt "
                            + "declarado na carga do efeito.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM " + schema + ".bills b"
                        + " JOIN " + schema + ".service_connection_units scu"
                        + " ON scu.tenant_id = b.tenant_id AND scu.unit_id = b.unit_id"
                        + " WHERE b.tenant_id = ? AND scu.connection_id = ? AND b.completed"
                        + " AND b.total_value > 0 AND b.due_date < CURRENT_DATE"
                        + " AND b.status NOT IN ('PAID', 'CANCELLED', 'CANCELLED_BY_RECTIFICATION',"
                        + " 'PROCESSING_ERROR', 'PRESCRIBED_DEBT'))")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, connectionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    // ------------------------------------------------------------ SQL

    private record Row(UUID id, ConnectionStatus.Kind kind, UUID statusId,
                       LocalDate disconnectedOn, LocalDate endedOn, UUID propertyUnitId) {
    }

    private static Row readConnection(Connection conn, String schema, UUID tenantId, UUID connectionId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, kind, status_id, disconnected_on, ended_on, property_unit_id FROM " + schema
                        + ".service_connections WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, connectionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Ligacao nao encontrada: " + connectionId);
                }
                return row(rs);
            }
        }
    }

    private static Row readSewerConnection(Connection conn, String schema, UUID tenantId,
                                           UUID propertyUnitId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, kind, status_id, disconnected_on, ended_on, property_unit_id FROM " + schema
                        + ".service_connections WHERE tenant_id = ? AND property_unit_id = ?"
                        + " AND kind = 'SEWER' AND ended_on IS NULL")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, propertyUnitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return row(rs);
            }
        }
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject(1, UUID.class),
                ConnectionStatus.Kind.valueOf(rs.getString(2)),
                rs.getObject(3, UUID.class),
                rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalDate.class),
                rs.getObject(6, UUID.class));
    }

    private static ConnectionStatus readStatus(Connection conn, String schema, UUID tenantId, UUID statusId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, kind, code, system_role, active FROM " + schema
                        + ".connection_statuses WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("Situacao da ligacao nao encontrada: " + statusId);
                }
                return status(rs);
            }
        }
    }

    private static ConnectionStatus readStatusByCode(Connection conn, String schema, UUID tenantId,
                                                     ConnectionStatus.Kind kind, String code)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, kind, code, system_role, active FROM " + schema
                        + ".connection_statuses WHERE tenant_id = ? AND kind = ? AND code = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, kind.name());
            ps.setString(3, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound(
                            "Situacao " + code + " nao existe no catalogo de " + kind + " do tenant.");
                }
                return status(rs);
            }
        }
    }

    private static ConnectionStatus status(ResultSet rs) throws SQLException {
        ConnectionStatus s = new ConnectionStatus();
        s.setId(rs.getObject(1, UUID.class));
        s.setKind(ConnectionStatus.Kind.valueOf(rs.getString(2)));
        s.setCode(rs.getString(3));
        s.setSystemRole(ConnectionStatus.SystemRole.valueOf(rs.getString(4)));
        s.setActive(rs.getBoolean(5));
        return s;
    }

    /** Valores aceitos e vigentes das bifurcações (mesma leitura do motor de OS, P0.19). */
    private static Map<String, String> readAcceptedParameters(Connection conn, String schema, UUID tenantId)
            throws SQLException {
        Map<String, String> raw = new HashMap<>();
        for (String key : List.of(ConnectionForkParameters.VOLUNTARY_SUPPRESSION_WITH_DEBT,
                ConnectionForkParameters.SEWER_OUT_OF_USE_ON_WATER_CUT,
                ConnectionForkParameters.SEWER_ALTERNATIVE_PCT_ENABLED)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT CAST(v.value AS text) FROM " + schema + ".parameter_versions v"
                            + " WHERE v.tenant_id = ? AND v.definition_key = ? AND v.status = 'APPROVED'"
                            + " AND v.valid_from <= CURRENT_DATE"
                            + " AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)"
                            + " AND EXISTS (SELECT 1 FROM " + schema + ".parameter_acceptances a"
                            + " WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id"
                            + " AND a.content_hash = v.content_hash)"
                            + " ORDER BY v.valid_from DESC LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        raw.put(key, rs.getString(1));
                    }
                }
            }
        }
        return raw;
    }

    private static void applyChange(Connection conn, String schema,
                                    EffectHandlerRegistry.EffectContext context,
                                    Row current, ConnectionStatus target, String reason,
                                    List<UUID> parameterVersionIds) throws SQLException {
        boolean disconnects = target.getSystemRole() == ConnectionStatus.SystemRole.DISCONNECTED
                || target.getSystemRole() == ConnectionStatus.SystemRole.NO_CONNECTION;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + schema + ".service_connections SET status_id = ?,"
                        + " disconnected_on = CASE WHEN ? AND disconnected_on IS NULL"
                        + " THEN CURRENT_DATE ELSE disconnected_on END,"
                        + " updated_by = ?, updated_at = NOW(), version = version + 1"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, target.getId());
            ps.setBoolean(2, disconnects);
            ps.setObject(3, context.actorId());
            ps.setObject(4, context.tenantId());
            ps.setObject(5, current.id());
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + schema + ".connection_status_history"
                        + " (id, tenant_id, connection_id, consumer_unit_id, kind, from_status_id,"
                        + " to_status_id, changed_at, reason, service_order_id, parameter_version_ids,"
                        + " created_by, updated_by)"
                        + " SELECT uuid_generate_v4(), ?, sc.id, sc.property_unit_id, sc.kind, ?, ?,"
                        + " NOW(), ?, ?, CAST(? AS jsonb), ?, ?"
                        + " FROM " + schema + ".service_connections sc"
                        + " WHERE sc.tenant_id = ? AND sc.id = ?")) {
            int i = 0;
            ps.setObject(++i, context.tenantId());
            ps.setObject(++i, current.statusId());
            ps.setObject(++i, target.getId());
            ps.setString(++i, reason);
            ps.setObject(++i, context.orderId());
            ps.setString(++i, parameterVersionIds.isEmpty()
                    ? null : Json.write(parameterVersionIds.stream().map(UUID::toString).toList()));
            ps.setObject(++i, context.actorId());
            ps.setObject(++i, context.actorId());
            ps.setObject(++i, context.tenantId());
            ps.setObject(++i, current.id());
            ps.executeUpdate();
        }
        // Projeção legada nas unidades atendidas (mesmos ids do catálogo
        // único, como na P2.23): projeção, nunca fonte.
        String column = current.kind() == ConnectionStatus.Kind.WATER ? "water_status_id" : "sewer_status_id";
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + schema + ".consumer_units SET " + column + " = ?, updated_by = ?,"
                        + " updated_at = NOW(), version = version + 1 WHERE tenant_id = ? AND id IN"
                        + " (SELECT unit_id FROM " + schema + ".service_connection_units"
                        + " WHERE tenant_id = ? AND connection_id = ?)")) {
            ps.setObject(1, target.getId());
            ps.setObject(2, context.actorId());
            ps.setObject(3, context.tenantId());
            ps.setObject(4, context.tenantId());
            ps.setObject(5, current.id());
            ps.executeUpdate();
        }
    }
}
