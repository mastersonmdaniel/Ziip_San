package br.com.ziipsan.operations.asset;

import br.com.ziipsan.core.serviceorder.ServiceOrderService;
import br.com.ziipsan.core.serviceorder.TargetType;
import br.com.ziipsan.core.serviceorder.TargetVerifierRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Verificadores de existencia dos alvos de ativo (P1.35), registrados no
 * ponto de extensao do motor da P1.31 ({@link TargetVerifierRegistry}) —
 * o motor nao e reescrito: o modulo dono do ativo registra, e o motor
 * passa a aceitar a classe em tipos de servico (e a recusar alvo
 * inexistente ou de outro tenant no {@code addTarget}).
 *
 * <p>Correspondencia classe -> nivel, seguindo ALVOS §3:
 * {@code OPERATIONAL_SITE} e instalacao (nivel 4, nao edificacao);
 * {@code EQUIPMENT} e equipamento (nivel 5); {@code COMPONENT} e
 * componente (nivel 6); {@code BUILDING} e edificacao (nivel 4 com
 * {@code is_building}). Uma OS com alvo de ativo inexistente ou de outro
 * tenant e recusada pelo verificador (filtro por {@code tenant_id}).
 */
public final class AssetTargetVerifiers {

    private AssetTargetVerifiers() {
    }

    /** Registra os quatro verificadores no motor da P1.31. */
    public static void registerAll(ServiceOrderService engine, String schema) {
        registerAll(engine.verifiers(), schema);
    }

    /** Variante direta sobre o registro (para testes sem motor completo). */
    public static void registerAll(TargetVerifierRegistry verifiers, String schema) {
        verifiers.register(TargetType.OPERATIONAL_SITE,
                levelVerifier(schema, 4, " AND NOT is_building"));
        verifiers.register(TargetType.EQUIPMENT, levelVerifier(schema, 5, ""));
        verifiers.register(TargetType.COMPONENT, levelVerifier(schema, 6, ""));
        verifiers.register(TargetType.BUILDING,
                levelVerifier(schema, 4, " AND is_building"));
    }

    private static TargetVerifierRegistry.TargetExistenceVerifier levelVerifier(
            String schema, int level, String extra) {
        String sql = "SELECT 1 FROM " + schema + ".asset"
                + " WHERE tenant_id = ? AND id = ? AND hierarchy_level = ?" + extra;
        return (Connection connection, UUID tenantId, UUID targetId) -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, targetId);
                ps.setInt(3, level);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Asset target verification failed: " + e.getMessage(), e);
            }
        };
    }
}
