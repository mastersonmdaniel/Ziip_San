package br.com.ziipsan.commercial.registry.legacy;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/**
 * P2.10: {@link UnitSituationGateway} sobre JDBC — grava a
 * {@code occupancy_status} da P1.41 (CAD-68) na unidade aplicada
 * ({@code consumer_units}, a tabela referenciada por
 * {@code debit_receivables}). Unidade inexistente para o tenant é erro
 * explícito: situação aplicada em unidade errada é pior que nenhuma.
 */
public final class JdbcUnitSituationGateway implements UnitSituationGateway {

    private static final Set<String> STATUSES = Set.of("OCCUPIED", "VACANT", "UNKNOWN");

    private final DataSource dataSource;
    private final String schema;

    public JdbcUnitSituationGateway(DataSource dataSource, String schema) {
        if (dataSource == null || schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("dataSource e schema são obrigatórios.");
        }
        this.dataSource = dataSource;
        this.schema = schema;
    }

    @Override
    public void applyOccupancyStatus(UUID tenantId, UUID unitId, String occupancyStatus, UUID actorId) {
        if (!STATUSES.contains(occupancyStatus)) {
            throw new IllegalArgumentException("occupancy_status inválido: " + occupancyStatus
                    + ". Válidos: " + STATUSES);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + schema + ".consumer_units SET occupancy_status = ?, updated_by = ?"
                             + " WHERE tenant_id = ? AND id = ?")) {
            ps.setString(1, occupancyStatus);
            ps.setObject(2, actorId);
            ps.setObject(3, tenantId);
            ps.setObject(4, unitId);
            if (ps.executeUpdate() != 1) {
                throw new IllegalStateException("Unidade não encontrada para aplicar a situação"
                        + " de ocupação (CAD-68): " + unitId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao aplicar a situação de ocupação (CAD-68)"
                    + " na unidade " + unitId, e);
        }
    }
}
