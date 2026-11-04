package br.com.ziipsan.commercial.registry.legacy;

import br.com.ziipsan.commercial.registry.rules.Matricula;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * Aplicador JDBC da v0 (P1.2): ver {@link LegacyUnitApplier} para o limite
 * do que a v0 grava e o que fica para a P1.3.
 *
 * <p>Cada unidade é a sua transação (o lote é grande e um erro de
 * referência não pode desfazer as unidades já criadas); a reaplicação
 * reaproveita a unidade pela matrícula única. O contexto de tenant é
 * fixado por conexão, como no motor da P1.31 — sem ele, a policy RLS
 * FORÇADA recusaria a escrita.
 */
public final class JdbcLegacyUnitApplier implements LegacyUnitApplier {

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public JdbcLegacyUnitApplier(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios.");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    private String t(String table) {
        return schema + "." + table;
    }

    @Override
    public Outcome apply(StagedUnit unit, UUID actorId) {
        List<LegacyIssue> blocking = new ArrayList<>();
        if (unit.matricula() == null || unit.matricula().isBlank()) {
            blocking.add(LegacyIssue.error("matricula", "MATRICULA_REQUIRED",
                    "Unidade sem matrícula: não aplicável."));
        } else if (!unit.matricula().chars().allMatch(Character::isDigit)) {
            blocking.add(LegacyIssue.error("matricula", "MATRICULA_DIGITS",
                    "Matrícula precisa ter só dígitos."));
        }
        String dv = null;
        if (blocking.isEmpty()) {
            if (unit.matriculaDv() != null) {
                if (Matricula.isValid(unit.matricula() + unit.matriculaDv())) {
                    dv = unit.matriculaDv();
                } else {
                    blocking.add(LegacyIssue.error("matriculaDv", "DV_MISMATCH",
                            "Dígito verificador do legado não confere com a matrícula."));
                }
            } else {
                dv = String.valueOf(Matricula.checkDigit(unit.matricula()));
            }
        }
        if (!blocking.isEmpty()) {
            return new Outcome(null, blocking);
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                UUID existing = findUnitByMatricula(connection, unit.matricula());
                if (existing != null) {
                    connection.commit();
                    return new Outcome(existing, List.of());
                }
                UUID routeId = resolveRoute(connection, unit);
                UUID blockId = resolveBlock(connection, unit);
                if (blockId != null && (unit.lot() == null || unit.sublot() == null)) {
                    connection.rollback();
                    return new Outcome(null, List.of(LegacyIssue.error("lot", "INSCRIPTION_INCOMPLETE",
                            "Quadra resolvida sem lote/sublote: o banco recusa a inscrição incompleta.")));
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + t("consumer_units")
                                + " (id, tenant_id, matricula, dv, status, block_id, lot, sublot,"
                                + " reading_route_id, reading_sequence, created_by, updated_by)"
                                + " VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setString(++i, unit.matricula());
                    ps.setString(++i, dv);
                    setUuid(ps, ++i, blockId);
                    setLong(ps, ++i, unit.lot());
                    setLong(ps, ++i, unit.sublot());
                    setUuid(ps, ++i, routeId);
                    setLong(ps, ++i, unit.readingSequence());
                    setUuid(ps, ++i, actorId);
                    setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                connection.commit();
                return new Outcome(id, List.of());
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Falha ao aplicar a unidade: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao aplicar a unidade: " + e.getMessage(), e);
        }
    }

    private UUID findUnitByMatricula(Connection connection, String matricula) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + t("consumer_units") + " WHERE tenant_id = ? AND matricula = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, matricula);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject("id") : null;
            }
        }
    }

    /** Rota por código (único por tenant). Irresolúvel é aviso implícito: volta null, sem bloquear. */
    private UUID resolveRoute(Connection connection, StagedUnit unit) throws SQLException {
        Integer code = parseCode(unit.routeCode());
        if (code == null) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM " + t("reading_routes") + " WHERE tenant_id = ? AND code = ?")) {
            ps.setObject(1, tenantId);
            ps.setInt(2, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject("id") : null;
            }
        }
    }

    /** Quadra por localidade + setor + quadra. Qualquer código ausente ou irresolúvel volta null. */
    private UUID resolveBlock(Connection connection, StagedUnit unit) throws SQLException {
        Integer locality = parseCode(unit.localityCode());
        Integer sector = parseCode(unit.sectorCode());
        Integer block = parseCode(unit.blockCode());
        if (locality == null || sector == null || block == null) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT b.id FROM " + t("blocks") + " b"
                        + " JOIN " + t("commercial_sectors") + " s"
                        + "   ON s.tenant_id = b.tenant_id AND s.id = b.sector_id"
                        + " JOIN " + t("localities") + " l"
                        + "   ON l.tenant_id = s.tenant_id AND l.id = s.locality_id"
                        + " WHERE b.tenant_id = ? AND l.code = ? AND s.code = ? AND b.code = ?")) {
            ps.setObject(1, tenantId);
            ps.setInt(2, locality);
            ps.setInt(3, sector);
            ps.setInt(4, block);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? (UUID) rs.getObject("id") : null;
            }
        }
    }

    private static Integer parseCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }
}
