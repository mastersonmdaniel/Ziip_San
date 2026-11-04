package br.com.ziipsan.commercial.registry.importer;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.importer.CategoryImportSchema.blankOrTrue;
import static br.com.ziipsan.commercial.registry.importer.CategoryImportSchema.setIntOrNull;
import static br.com.ziipsan.commercial.registry.importer.CategoryImportSchema.setTextOrNull;
import static br.com.ziipsan.commercial.registry.importer.CategoryImportSchema.setUuid;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.BOOLEAN;
import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.INTEGER;

/**
 * P1.40: esquema embutido {@code registry.subcategories} — o segundo esquema
 * prova que o framework é genérico (não é um importador de categorias), e
 * exercita a validação de chave estrangeira: {@code category_code} tem de
 * existir em {@code categories(code)} no tenant, ou a linha é recusada com
 * FOREIGN_KEY_NOT_FOUND e fica fora do lote. A chave é {@code code} (UNIQUE
 * por tenant); o inverso desativa a subcategoria inserida.
 */
@Component
public class SubcategoryImportSchema implements ImportSchema {

    public static final String CODE = "registry.subcategories";

    private static final List<ImportColumn> COLUMNS = List.of(
            new ImportColumn("category_code", ImportColumn.ColumnType.TEXT, true, List.of(), "RESIDENCIAL",
                    new ImportColumn.ForeignKey("categories", "code"), false),
            ImportColumn.key("code", "RES-1"),
            ImportColumn.text("name", true, "Residencial unifamiliar"),
            ImportColumn.text("abbreviation", false, "RES1"),
            new ImportColumn("inspection_factor", INTEGER, false, List.of(), "1", null, false),
            new ImportColumn("seasonal_supply", BOOLEAN, false, List.of(), "nao", null, false),
            new ImportColumn("active", BOOLEAN, false, List.of(), "sim", null, false));

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String label() {
        return "Subcategorias";
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
                        "SELECT 1 FROM " + schema + ".subcategories WHERE tenant_id = ? AND code = ?")) {
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
                UUID categoryId;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT id FROM " + schema + ".categories WHERE tenant_id = ? AND code = ?")) {
                    ps.setObject(1, tenantId);
                    ps.setString(2, values.get("category_code"));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // A validação já recusou FK inexistente; chegar aqui é corrida
                            // (a categoria sumiu entre validar e aplicar). Falhar o lote
                            // inteiro é melhor que aplicar metade sem a referência.
                            throw new IllegalStateException("Categoria referenciada nao existe mais: "
                                    + values.get("category_code"));
                        }
                        categoryId = (UUID) rs.getObject(1);
                    }
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + schema + ".subcategories (id, tenant_id, category_id, code, name,"
                                + " abbreviation, inspection_factor, seasonal_supply, active, created_by, updated_by)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setObject(++i, categoryId);
                    ps.setString(++i, values.get("code"));
                    ps.setString(++i, values.get("name"));
                    setTextOrNull(ps, ++i, values.get("abbreviation"));
                    setIntOrNull(ps, ++i, values.get("inspection_factor"));
                    ps.setBoolean(++i, ImportValidator.parseBoolean(values.getOrDefault(
                            "seasonal_supply", "nao")));
                    ps.setBoolean(++i, blankOrTrue(values.get("active")));
                    setUuid(ps, ++i, actorId);
                    setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                return new Applied("subcategories", id, values.get("code"));
            }

            @Override
            public void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                                Applied applied) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + schema + ".subcategories SET active = FALSE, updated_by = ?"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    setUuid(ps, 1, actorId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, applied.targetId());
                    ps.executeUpdate();
                }
            }
        };
    }
}
