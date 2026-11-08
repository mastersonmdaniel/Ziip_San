package br.com.ziipsan.intelligence.gis.ogc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Leitura real de {@code gis.public_aggregation_min_units} no
 * catálogo de parâmetros (core V9_6), com a MESMA consulta que a
 * P3.27 usa em {@code GisThematicService.readMinUnitsParameter}:
 * versão {@code APPROVED}, vigente na data e com aceite cujo hash
 * confere. JDBC puro sobre o {@code DataSource} da aplicação — sem
 * dependência nova. Devolve {@code null} quando o tenant não
 * configurou o parâmetro (vale o piso 5 da classe c, §3.10).
 */
public final class GisTenantParameterReader implements GisTenantParameterPort {

    private static final String SQL = """
            SELECT CAST(v.value AS text) FROM parameter_versions v
            WHERE v.tenant_id = ? AND v.definition_key = 'gis.public_aggregation_min_units'
              AND v.status = 'APPROVED'
              AND v.valid_from <= CURRENT_DATE
              AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)
              AND EXISTS (SELECT 1 FROM parameter_acceptances a
                          WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id
                            AND a.content_hash = v.content_hash)
            ORDER BY v.valid_from DESC LIMIT 1
            """;

    private final DataSource dataSource;

    public GisTenantParameterReader(DataSource dataSource){ this.dataSource = dataSource; }

    @Override
    public Integer aggregationMinUnits(UUID tenantId) {
        if (dataSource == null || tenantId == null) return null;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String v = rs.getString(1);
                return v == null ? null : Integer.valueOf(v.trim());
            }
        } catch (java.sql.SQLException | NumberFormatException e) {
            throw new IllegalStateException("falha ao ler gis.public_aggregation_min_units do tenant", e);
        }
    }
}
