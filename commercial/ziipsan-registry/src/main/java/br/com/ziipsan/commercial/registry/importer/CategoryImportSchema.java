package br.com.ziipsan.commercial.registry.importer;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.BOOLEAN;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.INTEGER;

/**
 * P1.40: esquema embutido {@code registry.categories} — prova do framework
 * contra a tabela real de categorias (ED-12: catálogos auxiliares pelo
 * importador, com OS de lote). Colunas exatamente as da tabela (V3_1 + V3_7);
 * a chave é {@code code} (UNIQUE por tenant). O inverso desativa a categoria
 * inserida: catálogo não tem DELETE.
 */
@Component
public class CategoryImportSchema implements ImportSchema {

    public static final String CODE = "registry.categories";

    private static final List<ImportColumn> COLUMNS = List.of(
            ImportColumn.key("code", "RESIDENCIAL"),
            ImportColumn.text("name", true, "Residencial"),
            ImportColumn.text("abbreviation", false, "RES"),
            new ImportColumn("minimum_consumption", INTEGER, false, List.of(), "10", null, false),
            new ImportColumn("economies_soft_limit", INTEGER, false, List.of(), "5", null, false),
            new ImportColumn("requires_special_permission", BOOLEAN, false, List.of(), "nao", null, false),
            new ImportColumn("public_entity", BOOLEAN, false, List.of(), "nao", null, false),
            new ImportColumn("active", BOOLEAN, false, List.of(), "sim", null, false));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Categorias";
    }

    @Override
    public List<ImportColumn> columns() {
        return COLUMNS;
    }

    @Override
    public ImportApplier applier() {
        return new ImportApplier() {
            @Override
            public boolean existsByKey(Connection connection, String schema, UUID tenantId,
                                       Map<String, String> values) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM " + schema + ".categories WHERE tenant_id = ? AND code = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, values.get("code"));
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next();
                    }
                }
            }

            @Override
            public Applied apply(Connection connection, String schema, UUID tenantId, UUID actorId,
                                 Map<String, String> values) throws SQLException {
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + schema + ".categories (id, tenant_id, code, name, abbreviation,"
                                + " minimum_consumption, economies_soft_limit, requires_special_permission,"
                                + " public_entity, active, created_by, updated_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setString(++i, values.get("code"));
                    ps.setString(++i, values.get("name"));
                    setTextOrNull(ps, ++i, values.get("abbreviation"));
                    setIntOrNull(ps, ++i, values.get("minimum_consumption"));
                    setIntOrNull(ps, ++i, values.get("economies_soft_limit"));
                    ps.setBoolean(++i, ImportValidator.parseBoolean(values.getOrDefault(
                            "requires_special_permission", "nao")));
                    ps.setBoolean(++i, ImportValidator.parseBoolean(values.getOrDefault(
                            "public_entity", "nao")));
                    ps.setBoolean(++i, blankOrTrue(values.get("active")));
                    setUuid(ps, ++i, actorId);
                    setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                return new Applied("categories", id, values.get("code"));
            }

            @Override
            public void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                                Applied applied) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + schema + ".categories SET active = FALSE, updated_by = ?"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    setUuid(ps, 1, actorId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, applied.targetId());
                    ps.executeUpdate();
                }
            }
        };
    }

    static boolean blankOrTrue(String value) {
        return value == null || value.isBlank() || ImportValidator.parseBoolean(value);
    }

    static void setTextOrNull(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    static void setIntOrNull(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, Integer.parseInt(value));
        }
    }

    static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }
}
