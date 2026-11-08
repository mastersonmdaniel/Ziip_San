package br.com.ziipsan.platform.orgscope;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Loja JDBC da P3.30 (V1_8) e das leituras vizinhas (território, âncoras e
 * OS do protocolo), no mesmo padrão transacional do WorkInboxService da
 * P2.28: cada método roda em transação própria com
 * {@code app.current_tenant} local (as tabelas têm RLS forçada) e o schema
 * do tenant é explícito no nome da tabela; o {@code tenant_id} também vai
 * em cada predicado (defesa em profundidade, como no restante do produto).
 *
 * <p>Atomicidade e transações compostas (correções D1/D6 da revisão
 * independente): o trâmite ({@link #transferAssignmentAndRecord}) executa
 * o upsert da atribuição e a inserção da trilha na MESMA transação, sobre
 * a mesma conexão — se a trilha falhar, a atribuição desfaz junto. As
 * âncoras compostas ({@code anchorForProtocol}, {@code anchorForServiceOrder}
 * e {@code anchorForBill}) também rodam inteiras numa transação só, com um
 * JOIN único ou várias consultas sobre a mesma conexão. O que continua em
 * transações separadas, por desenho: as leituras simples de um método só
 * e as chamadas DISTINTAS que o serviço faz a portas diferentes (ex.:
 * território e OS durante a expansão/trâmite) — são leituras sem escrita,
 * e a consistência exigida pelo aceite é a da escrita do trâmite.
 *
 * <p>Âncoras (a cadeia é a do cadastro): unidade consumidora → quadra →
 * setor comercial → localidade, e unidade consumidora → rota de leitura.
 * Conta resolve pela sua unidade ({@code bills.unit_id}); protocolo, pela
 * unidade consumidora dele (sem unidade, âncora vazia presente); OS, pelo
 * protocolo dela e, sem protocolo, pelo primeiro alvo CONSUMER_UNIT.
 * Tabela vizinha ausente num classpath parcial devolve "não existe"
 * (42P01 tratado) — a montagem completa tem todas; presumir âncora seria
 * o erro oposto.
 */
public class JdbcOrgScopeStore implements OrgScopePorts.OrgScopeStore, OrgScopePorts.TerritoryPort,
        OrgScopePorts.AnchorPort, OrgScopePorts.OrderPort {

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public JdbcOrgScopeStore(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Operação de escopo organizacional falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de escopo organizacional falhou: " + e.getMessage(), e);
        }
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private static boolean isUndefinedTable(SQLException e) {
        return "42P01".equals(e.getSQLState());
    }

    // ------------------------------------------------------------------
    // OrgScopeStore
    // ------------------------------------------------------------------

    @Override
    public Optional<OrgUnit> findUnit(UUID unitId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, parent_id, code, name, active, created_at FROM " + t("org_units")
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, unitId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapUnit(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<OrgUnit> listUnits() {
        return inTx(c -> {
            List<OrgUnit> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, parent_id, code, name, active, created_at FROM " + t("org_units")
                            + " WHERE tenant_id = ? ORDER BY code")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapUnit(rs));
                    }
                }
            }
            return result;
        });
    }

    private static OrgUnit mapUnit(ResultSet rs) throws SQLException {
        return new OrgUnit(uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "parent_id"),
                rs.getString("code"), rs.getString("name"), rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant());
    }

    @Override
    public void insertUnit(OrgUnit unit) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("org_units")
                            + " (id, tenant_id, parent_id, code, name, active, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, unit.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, unit.parentId());
                ps.setString(4, unit.code());
                ps.setString(5, unit.name());
                ps.setBoolean(6, unit.active());
                ps.setTimestamp(7, Timestamp.from(unit.createdAt()));
                ps.setTimestamp(8, Timestamp.from(unit.createdAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void updateUnit(OrgUnit unit) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE " + t("org_units") + " SET parent_id = ?, code = ?, name = ?, active = ?,"
                            + " updated_at = NOW() WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, unit.parentId());
                ps.setString(2, unit.code());
                ps.setString(3, unit.name());
                ps.setBoolean(4, unit.active());
                ps.setObject(5, tenantId);
                ps.setObject(6, unit.id());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<OrgAreaLink> areasOfUnit(UUID unitId) {
        return areasOfUnits(Set.of(unitId));
    }

    @Override
    public List<OrgAreaLink> areasOfUnits(Set<UUID> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return List.of();
        }
        return inTx(c -> {
            List<OrgAreaLink> result = new ArrayList<>();
            String placeholders = String.join(", ", unitIds.stream().map(id -> "?").toList());
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT org_unit_id, area_type, area_id FROM " + t("org_unit_areas")
                            + " WHERE tenant_id = ? AND org_unit_id IN (" + placeholders + ")")) {
                ps.setObject(1, tenantId);
                int i = 2;
                for (UUID unitId : unitIds) {
                    ps.setObject(i++, unitId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new OrgAreaLink(uuid(rs, "org_unit_id"),
                                OrgAreaType.valueOf(rs.getString("area_type")), uuid(rs, "area_id")));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public void insertAreaLink(OrgAreaLink link) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("org_unit_areas")
                            + " (tenant_id, org_unit_id, area_type, area_id) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, link.orgUnitId());
                ps.setString(3, link.areaType().name());
                ps.setObject(4, link.areaId());
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<UserScopeGrant> grantsOfUser(UUID userId) {
        return inTx(c -> {
            List<UserScopeGrant> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, user_id, org_unit_id, area_type, area_id, unrestricted,"
                            + " granted_by, created_at FROM " + t("user_org_scopes")
                            + " WHERE tenant_id = ? AND user_id = ? ORDER BY created_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapGrant(rs));
                    }
                }
            }
            return result;
        });
    }

    @Override
    public Optional<UserScopeGrant> findGrant(UUID grantId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, tenant_id, user_id, org_unit_id, area_type, area_id, unrestricted,"
                            + " granted_by, created_at FROM " + t("user_org_scopes")
                            + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, grantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(mapGrant(rs)) : Optional.empty();
                }
            }
        });
    }

    private static UserScopeGrant mapGrant(ResultSet rs) throws SQLException {
        String areaType = rs.getString("area_type");
        return new UserScopeGrant(uuid(rs, "id"), uuid(rs, "tenant_id"), uuid(rs, "user_id"),
                uuid(rs, "org_unit_id"), areaType == null ? null : OrgAreaType.valueOf(areaType),
                uuid(rs, "area_id"), rs.getBoolean("unrestricted"), uuid(rs, "granted_by"),
                rs.getTimestamp("created_at").toInstant());
    }

    @Override
    public void insertGrant(UserScopeGrant grant) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("user_org_scopes")
                            + " (id, tenant_id, user_id, org_unit_id, area_type, area_id, unrestricted,"
                            + " granted_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, grant.id());
                ps.setObject(2, tenantId);
                ps.setObject(3, grant.userId());
                ps.setObject(4, grant.orgUnitId());
                ps.setString(5, grant.areaType() == null ? null : grant.areaType().name());
                ps.setObject(6, grant.areaId());
                ps.setBoolean(7, grant.unrestricted());
                ps.setObject(8, grant.grantedBy());
                ps.setTimestamp(9, Timestamp.from(grant.createdAt()));
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public void deleteGrant(UUID grantId) {
        inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM " + t("user_org_scopes") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, grantId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public Optional<UUID> protocolOrgUnit(UUID protocolId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT org_unit_id FROM " + t("protocol_org_assignments")
                            + " WHERE tenant_id = ? AND protocol_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, protocolId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(uuid(rs, "org_unit_id")) : Optional.empty();
                }
            }
        });
    }

    private void upsertAssignment(Connection c, UUID protocolId, UUID orgUnitId, UUID actorId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + t("protocol_org_assignments")
                        + " (tenant_id, protocol_id, org_unit_id, assigned_by)"
                        + " VALUES (?, ?, ?, ?)"
                        + " ON CONFLICT (tenant_id, protocol_id) DO UPDATE"
                        + " SET org_unit_id = EXCLUDED.org_unit_id, assigned_by = EXCLUDED.assigned_by,"
                        + " updated_at = NOW()")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, protocolId);
            ps.setObject(3, orgUnitId);
            ps.setObject(4, actorId);
            ps.executeUpdate();
        }
    }

    @Override
    public void upsertProtocolAssignment(UUID protocolId, UUID orgUnitId, UUID actorId) {
        inTx(c -> {
            upsertAssignment(c, protocolId, orgUnitId, actorId);
            return null;
        });
    }

    private void insertTransfer(Connection c, ProtocolTransfer transfer) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + t("protocol_transfers")
                        + " (id, tenant_id, protocol_id, from_org_unit_id, to_org_unit_id, moved_by,"
                        + " reason, moved_order_ids, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)")) {
            ps.setObject(1, transfer.id());
            ps.setObject(2, tenantId);
            ps.setObject(3, transfer.protocolId());
            ps.setObject(4, transfer.fromOrgUnitId());
            ps.setObject(5, transfer.toOrgUnitId());
            ps.setObject(6, transfer.movedBy());
            ps.setString(7, transfer.reason());
            // JSON SEMPRE pela classe Json do core (regra do projeto).
            ps.setString(8, Json.write(transfer.movedOrderIds().stream().map(UUID::toString).toList()));
            ps.setTimestamp(9, Timestamp.from(transfer.createdAt()));
            ps.executeUpdate();
        }
    }

    @Override
    public void insertTransfer(ProtocolTransfer transfer) {
        inTx(c -> {
            insertTransfer(c, transfer);
            return null;
        });
    }

    /**
     * O trâmite ATÔMICO (correção D1): upsert da atribuição + inserção da
     * trilha sobre a MESMA conexão, dentro da única transação do
     * {@link #inTx} — qualquer falha desfaz as duas escritas.
     */
    @Override
    public void transferAssignmentAndRecord(ProtocolTransfer transfer) {
        inTx(c -> {
            upsertAssignment(c, transfer.protocolId(), transfer.toOrgUnitId(), transfer.movedBy());
            insertTransfer(c, transfer);
            return null;
        });
    }

    // ------------------------------------------------------------------
    // TerritoryPort
    // ------------------------------------------------------------------

    @Override
    public boolean areaExists(OrgAreaType type, UUID areaId) {
        String table = switch (type) {
            case LOCALITY -> "localities";
            case SECTOR -> "commercial_sectors";
            case ROUTE -> "reading_routes";
        };
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM " + t(table) + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, areaId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    return false;
                }
                throw e;
            }
        });
    }

    private Set<UUID> idSet(String sql, UUID key) {
        return inTx(c -> {
            Set<UUID> result = new LinkedHashSet<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(uuid(rs, "id"));
                    }
                }
            } catch (SQLException e) {
                if (!isUndefinedTable(e)) {
                    throw e;
                }
            }
            return result;
        });
    }

    @Override
    public Set<UUID> sectorIdsOfLocality(UUID localityId) {
        return idSet("SELECT id FROM " + t("commercial_sectors")
                + " WHERE tenant_id = ? AND locality_id = ?", localityId);
    }

    @Override
    public Set<UUID> routeIdsOfLocality(UUID localityId) {
        return idSet("SELECT id FROM " + t("reading_routes")
                + " WHERE tenant_id = ? AND locality_id = ?", localityId);
    }

    @Override
    public Set<UUID> routeIdsOfSector(UUID sectorId) {
        return idSet("SELECT id FROM " + t("reading_routes")
                + " WHERE tenant_id = ? AND sector_id = ?", sectorId);
    }

    // ------------------------------------------------------------------
    // AnchorPort
    // ------------------------------------------------------------------

    /** A cadeia da unidade consumidora sobre uma conexão dada (um elo das âncoras compostas). */
    private Optional<AreaAnchor> consumerUnitAnchor(Connection c, UUID consumerUnitId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT cu.reading_route_id AS route_id, b.sector_id AS sector_id,"
                        + " cs.locality_id AS locality_id FROM " + t("consumer_units") + " cu"
                        + " LEFT JOIN " + t("blocks") + " b"
                        + " ON b.tenant_id = cu.tenant_id AND b.id = cu.block_id"
                        + " LEFT JOIN " + t("commercial_sectors") + " cs"
                        + " ON cs.tenant_id = cu.tenant_id AND cs.id = b.sector_id"
                        + " WHERE cu.tenant_id = ? AND cu.id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, consumerUnitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AreaAnchor(uuid(rs, "locality_id"),
                        uuid(rs, "sector_id"), uuid(rs, "route_id")));
            }
        } catch (SQLException e) {
            if (isUndefinedTable(e)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public Optional<AreaAnchor> anchorForConsumerUnit(UUID consumerUnitId) {
        return inTx(c -> consumerUnitAnchor(c, consumerUnitId));
    }

    /**
     * Âncora do protocolo numa transação e num JOIN só (correção D6):
     * protocolo → unidade consumidora → quadra → setor → localidade/rota.
     * Sem linha = protocolo inexistente (vazio); linha sem unidade = o
     * protocolo existe sem âncora ({@link AreaAnchor#EMPTY} presente);
     * unidade consumidora sumida idem — nunca "protocolo não encontrado"
     * para um protocolo que existe.
     */
    @Override
    public Optional<AreaAnchor> anchorForProtocol(UUID protocolId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT sp.consumer_unit_id AS consumer_unit_id,"
                            + " cu.reading_route_id AS route_id, b.sector_id AS sector_id,"
                            + " cs.locality_id AS locality_id FROM " + t("service_protocols") + " sp"
                            + " LEFT JOIN " + t("consumer_units") + " cu"
                            + " ON cu.tenant_id = sp.tenant_id AND cu.id = sp.consumer_unit_id"
                            + " LEFT JOIN " + t("blocks") + " b"
                            + " ON b.tenant_id = cu.tenant_id AND b.id = cu.block_id"
                            + " LEFT JOIN " + t("commercial_sectors") + " cs"
                            + " ON cs.tenant_id = cu.tenant_id AND cs.id = b.sector_id"
                            + " WHERE sp.tenant_id = ? AND sp.id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, protocolId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    if (uuid(rs, "consumer_unit_id") == null) {
                        return Optional.of(AreaAnchor.EMPTY);
                    }
                    return Optional.of(new AreaAnchor(uuid(rs, "locality_id"),
                            uuid(rs, "sector_id"), uuid(rs, "route_id")));
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    /**
     * Âncora da OS numa transação só (correção D6): existência, protocolo e
     * a cadeia dele num JOIN; sem âncora pelo protocolo, o alvo
     * CONSUMER_UNIT é consultado sobre a MESMA conexão e resolvido nela.
     */
    @Override
    public Optional<AreaAnchor> anchorForServiceOrder(UUID serviceOrderId) {
        return inTx(c -> {
            UUID protocolId;
            AreaAnchor byProtocol = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT so.protocol_id AS protocol_id,"
                            + " cu.reading_route_id AS route_id, b.sector_id AS sector_id,"
                            + " cs.locality_id AS locality_id FROM " + t("service_orders") + " so"
                            + " LEFT JOIN " + t("service_protocols") + " sp"
                            + " ON sp.tenant_id = so.tenant_id AND sp.id = so.protocol_id"
                            + " LEFT JOIN " + t("consumer_units") + " cu"
                            + " ON cu.tenant_id = sp.tenant_id AND cu.id = sp.consumer_unit_id"
                            + " LEFT JOIN " + t("blocks") + " b"
                            + " ON b.tenant_id = cu.tenant_id AND b.id = cu.block_id"
                            + " LEFT JOIN " + t("commercial_sectors") + " cs"
                            + " ON cs.tenant_id = cu.tenant_id AND cs.id = b.sector_id"
                            + " WHERE so.tenant_id = ? AND so.id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, serviceOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    protocolId = uuid(rs, "protocol_id");
                    byProtocol = new AreaAnchor(uuid(rs, "locality_id"),
                            uuid(rs, "sector_id"), uuid(rs, "route_id"));
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    return Optional.empty();
                }
                throw e;
            }
            if (protocolId != null && byProtocol.hasAny()) {
                return Optional.of(byProtocol);
            }
            Optional<UUID> targetUnit;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT target_id FROM " + t("service_order_targets")
                            + " WHERE tenant_id = ? AND order_id = ? AND target_type = 'CONSUMER_UNIT'"
                            + " ORDER BY created_at LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, serviceOrderId);
                try (ResultSet rs = ps.executeQuery()) {
                    targetUnit = rs.next()
                            ? Optional.ofNullable(uuid(rs, "target_id")) : Optional.<UUID>empty();
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    targetUnit = Optional.empty();
                } else {
                    throw e;
                }
            }
            if (targetUnit.isEmpty()) {
                return Optional.of(AreaAnchor.EMPTY);
            }
            // Alvo existe mas a unidade sumiu: a OS existe — âncora vazia
            // presente (só o irrestrito vê), nunca "OS não encontrada".
            return Optional.of(consumerUnitAnchor(c, targetUnit.get()).orElse(AreaAnchor.EMPTY));
        });
    }

    /**
     * Âncora da conta numa transação só (correção D6): conta → unidade →
     * cadeia, num JOIN. Sem linha = conta inexistente (vazio); unidade
     * consumidora sumida = conta existe sem âncora resolvível
     * ({@link AreaAnchor#EMPTY} presente) — o chamador responde "fora de
     * escopo", não "conta não encontrada" (a imprecisão apontada na D6).
     */
    @Override
    public Optional<AreaAnchor> anchorForBill(UUID billId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT bl.unit_id AS unit_id,"
                            + " cu.reading_route_id AS route_id, b.sector_id AS sector_id,"
                            + " cs.locality_id AS locality_id FROM " + t("bills") + " bl"
                            + " LEFT JOIN " + t("consumer_units") + " cu"
                            + " ON cu.tenant_id = bl.tenant_id AND cu.id = bl.unit_id"
                            + " LEFT JOIN " + t("blocks") + " b"
                            + " ON b.tenant_id = cu.tenant_id AND b.id = cu.block_id"
                            + " LEFT JOIN " + t("commercial_sectors") + " cs"
                            + " ON cs.tenant_id = cu.tenant_id AND cs.id = b.sector_id"
                            + " WHERE bl.tenant_id = ? AND bl.id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, billId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    // unit_id é NOT NULL na V4_1; os JOINs podem vir nulos
                    // se a unidade consumidora sumiu — a conta existe.
                    return Optional.of(new AreaAnchor(uuid(rs, "locality_id"),
                            uuid(rs, "sector_id"), uuid(rs, "route_id")));
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    @Override
    public Optional<AreaAnchor> anchorForWorkObject(String objectType, UUID objectId) {
        if (objectType == null || objectId == null) {
            throw new IllegalArgumentException("tipo e id do objeto são obrigatórios");
        }
        return switch (objectType) {
            case "SERVICE_ORDER" -> anchorForServiceOrder(objectId);
            case "PROTOCOL" -> anchorForProtocol(objectId);
            case "CONSUMER_UNIT" -> anchorForConsumerUnit(objectId);
            case "BILL" -> anchorForBill(objectId);
            // Tipo sem resolução territorial conhecida: o item existe (quem o
            // registrou sabe), mas não há âncora — âncora vazia presente, e o
            // ScopeRule só deixa o irrestrito ver (fail-closed).
            default -> Optional.of(AreaAnchor.EMPTY);
        };
    }

    // ------------------------------------------------------------------
    // OrderPort
    // ------------------------------------------------------------------

    @Override
    public List<OrgScopePorts.OrderRef> ordersOfProtocol(UUID protocolId) {
        return inTx(c -> {
            List<OrgScopePorts.OrderRef> result = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, status FROM " + t("service_orders")
                            + " WHERE tenant_id = ? AND protocol_id = ? ORDER BY created_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, protocolId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new OrgScopePorts.OrderRef(uuid(rs, "id"), rs.getString("status")));
                    }
                }
            } catch (SQLException e) {
                if (!isUndefinedTable(e)) {
                    throw e;
                }
            }
            return result;
        });
    }

    @Override
    public boolean protocolExists(UUID protocolId) {
        return inTx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM " + t("service_protocols") + " WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, protocolId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                if (isUndefinedTable(e)) {
                    return false;
                }
                throw e;
            }
        });
    }

    /** Leitura da trilha em JSON (uso da integração/diagnóstico): ids das OS de um trâmite. */
    public static List<UUID> parseMovedOrderIds(String movedOrderIdsJson) {
        JsonNode node = Json.readTree(movedOrderIdsJson);
        List<UUID> result = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode element : node) {
                result.add(UUID.fromString(element.asString()));
            }
        }
        return result;
    }
}
