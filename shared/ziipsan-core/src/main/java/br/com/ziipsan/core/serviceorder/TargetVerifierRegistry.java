package br.com.ziipsan.core.serviceorder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Existence verifiers for target classes (ADR-0045 item 3: the target is a
 * typed contract, and validation is enabled when the entity exists).
 *
 * <p>Each module that creates a target entity registers its verifier here;
 * the engine refuses to declare a service type for a class with no
 * verifier (except {@link TargetType#LOCATION_ONLY}, which carries a
 * location instead of an entity). The registry is instance-scoped, never
 * static: a service instance is bound to one tenant schema, and a static
 * registry would mix tenants.
 *
 * <p>The engine itself registers only the root-entity verifier
 * ({@link TargetType#CONSUMER_UNIT}, the table the engine's own history
 * FKs already reference). GIS, asset, fleet and incident verifiers are
 * registered by their owning modules; until then those classes are not
 * declarable, and the refusal is tested, not silently skipped.
 */
public final class TargetVerifierRegistry {

    /** A verifier answers: does this entity exist for this tenant? */
    @FunctionalInterface
    public interface TargetExistenceVerifier {
        boolean exists(Connection connection, UUID tenantId, UUID targetId) throws SQLException;
    }

    private static final Pattern TABLE_NAME = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final Map<TargetType, TargetExistenceVerifier> verifiers = new ConcurrentHashMap<>();

    public void register(TargetType type, TargetExistenceVerifier verifier) {
        if (type == null || verifier == null) {
            throw new IllegalArgumentException("type and verifier are required");
        }
        verifiers.put(type, verifier);
    }

    public boolean hasVerifier(TargetType type) {
        return type != null && (type.isLocationOnly() || verifiers.containsKey(type));
    }

    public TargetExistenceVerifier verifier(TargetType type) {
        return verifiers.get(type);
    }

    /** All classes the engine may currently declare (plus LOCATION_ONLY, always). */
    public Set<TargetType> declarableClasses() {
        var out = new java.util.HashSet<>(verifiers.keySet());
        out.add(TargetType.LOCATION_ONLY);
        return Set.copyOf(out);
    }

    /**
     * Table-backed verifier: the entity exists when a row with this id
     * exists for the tenant in the given schema-qualified table. The table
     * name is validated against a strict pattern because it is
     * interpolated into SQL; the id is always a bind parameter.
     */
    public static TargetExistenceVerifier sqlVerifier(String schema, String table) {
        if (schema == null || !TABLE_NAME.matcher(schema).matches()) {
            throw new IllegalArgumentException("invalid schema for target verifier: " + schema);
        }
        if (table == null || !TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("invalid table for target verifier: " + table);
        }
        String sql = "SELECT 1 FROM " + schema + "." + table + " WHERE tenant_id = ? AND id = ?";
        return (connection, tenantId, targetId) -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, targetId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        };
    }

    /** Snapshot of the registered verifiers, for diagnostics and tests. */
    Map<TargetType, TargetExistenceVerifier> snapshot() {
        var copy = new EnumMap<TargetType, TargetExistenceVerifier>(TargetType.class);
        copy.putAll(verifiers);
        return copy;
    }
}
