package br.com.ziipsan.intelligence.gis.webedit;

import br.com.ziipsan.core.json.Json;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC da porta de esquema (correção D2): lê
 * {@code gis_feature_type.attributes_schema} (JSONB da V13_1)
 * filtrando por tenant_id + id. O JSON é lido pela classe Json
 * do core — JSON inválido lança, nunca vira mapa vazio em
 * silêncio (o mesmo princípio anti-apagamento da classe).
 */
@Component
public class JdbcGisFeatureTypeSchemaPort implements GisFeatureTypeSchemaPort {

    private final JdbcTemplate jdbc;

    public JdbcGisFeatureTypeSchemaPort(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Map<String, Object>> attributesSchema(UUID tenantId,
                                                          UUID featureTypeId) {
        List<String> rows = jdbc.queryForList("""
                SELECT attributes_schema::text
                FROM gis_feature_type
                WHERE tenant_id = ? AND id = ?
                """, String.class, tenantId, featureTypeId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(Json.readMap(rows.get(0)));
    }
}
