package br.com.ziipsan.commercial.registry.importer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.importer.ImportColumn.ColumnType.BOOLEAN;

/**
 * ED-12: esquema de importação genérico dos catálogos simples do cadastro
 * (SAMAE 2.8.10) — tabelas do padrão da V3_12 ({@code id, tenant_id, code}
 * único por tenant, {@code name, active}, auditoria) declaradas em
 * {@link RegistryCatalogImportSchemas}.
 *
 * <p>Um único aplicador serve a todos esses catálogos porque a forma da
 * tabela é a mesma; o que muda por catálogo é a lista de colunas extras
 * tipadas (ex.: {@code applies_to} de {@code customer_types}). A tabela
 * NUNCA vem do arquivo nem da requisição: vem da declaração em código,
 * conferida contra a allowlist abaixo — a mesma fronteira de
 * {@code CatalogLookup} e de {@code RegistryCatalogService}. Tabela fora da
 * allowlist é erro de programação e falha na construção do bean, não em
 * tempo de importação.
 *
 * <p>Aplicação por OS de lote é a do framework da P1.40 (o
 * {@code ImportService} abre a OS {@code IMPORT_BATCH} e a correção é a OS
 * inversa): este esquema só grava e desfaz linhas. O inverso desativa o item
 * inserido — catálogo não tem DELETE, como nos esquemas embutidos de
 * categorias e subcategorias.
 */
public final class RegistryCatalogImportSchema implements ImportSchema {

    /**
     * Tabelas que este aplicador sabe gravar: exatamente as de código+nome da
     * V3_12. {@code block_faces} (exige {@code block_id}), {@code street_names}
     * e {@code neighborhoods} (sem coluna {@code code}) NÃO entram: a forma
     * delas não é a forma simples e a escrita delas pertence às telas
     * próprias (ver {@code RegistryCatalogService}).
     */
    public static final Set<String> TABLES = Set.of(
            "customer_types", "property_profiles", "relation_roles", "document_exemption_reasons",
            "customer_delete_reasons", "unit_link_end_reasons", "sewer_discharges", "consent_purposes",
            "ibge_municipalities");

    /** Coluna extra tipada de um catálogo (além de code, name e active). */
    public record Extra(String column, ImportColumn.ColumnType type, boolean required,
                        List<String> domain, String example) {
        public Extra {
            if (column == null || !column.matches("[a-z][a-z0-9_]*")) {
                throw new IllegalArgumentException("coluna extra invalida: " + column);
            }
            if (type == null) {
                throw new IllegalArgumentException("tipo da coluna extra e obrigatorio: " + column);
            }
            domain = domain == null ? List.of() : List.copyOf(domain);
            if (example == null) {
                example = "";
            }
        }

        ImportColumn toImportColumn() {
            return new ImportColumn(column, type, required, domain, example, null, false);
        }
    }

    private final String code;
    private final String label;
    private final String table;
    private final List<Extra> extras;
    private final List<ImportColumn> columns;

    public RegistryCatalogImportSchema(String code, String label, String table, List<Extra> extras) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("codigo do esquema e obrigatorio");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("rotulo do esquema e obrigatorio");
        }
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("tabela fora da allowlist de catalogos importaveis: " + table);
        }
        this.code = code;
        this.label = label;
        this.table = table;
        this.extras = extras == null ? List.of() : List.copyOf(extras);
        List<ImportColumn> cols = new ArrayList<>();
        cols.add(ImportColumn.key("code", "CODIGO"));
        cols.add(ImportColumn.text("name", true, "Nome do item"));
        for (Extra extra : this.extras) {
            cols.add(extra.toImportColumn());
        }
        cols.add(new ImportColumn("active", BOOLEAN, false, List.of(), "sim", null, false));
        this.columns = List.copyOf(cols);
    }

    public String table() {
        return table;
    }

    public List<Extra> extras() {
        return extras;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public List<ImportColumn> columns() {
        return columns;
    }

    @Override
    public ImportApplier applier() {
        return new ImportApplier() {
            @Override
            public boolean existsByKey(Connection connection, String schema, UUID tenantId,
                                       Map<String, String> values) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM " + schema + "." + table + " WHERE tenant_id = ? AND code = ?")) {
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
                // Extra opcional em branco e OMITIDO da insercao, nunca NULL:
                // as colunas extras da V3_12 sao NOT NULL com DEFAULT do
                // prestador (applies_to='BOTH', provisional_only=false,
                // requires_permission=true, protetivo) — inserir NULL
                // quebraria a constraint, e omitir faz valer o DEFAULT.
                List<Extra> present = new ArrayList<>();
                for (Extra extra : extras) {
                    String value = values.get(extra.column());
                    if (value == null || value.isBlank()) {
                        if (extra.required()) {
                            // A validacao generica ja recusou a linha (REQUIRED);
                            // chegar aqui e corrida ou chamada fora do importador.
                            throw new IllegalStateException(
                                    "coluna obrigatoria vazia na aplicacao: " + extra.column());
                        }
                        continue;
                    }
                    present.add(extra);
                }
                List<String> names = new ArrayList<>(List.of("id", "tenant_id", "code", "name"));
                for (Extra extra : present) {
                    names.add(extra.column());
                }
                names.addAll(List.of("active", "created_by", "updated_by"));
                String placeholders = String.join(", ", names.stream().map(n -> "?").toList());
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO " + schema + "." + table + " (" + String.join(", ", names)
                                + ") VALUES (" + placeholders + ")")) {
                    int i = 0;
                    ps.setObject(++i, id);
                    ps.setObject(++i, tenantId);
                    ps.setString(++i, values.get("code"));
                    ps.setString(++i, values.get("name"));
                    for (Extra extra : present) {
                        setValue(ps, ++i, extra, values.get(extra.column()));
                    }
                    ps.setBoolean(++i, CategoryImportSchema.blankOrTrue(values.get("active")));
                    CategoryImportSchema.setUuid(ps, ++i, actorId);
                    CategoryImportSchema.setUuid(ps, ++i, actorId);
                    ps.executeUpdate();
                }
                return new Applied(table, id, values.get("code"));
            }

            @Override
            public void reverse(Connection connection, String schema, UUID tenantId, UUID actorId,
                                Applied applied) throws SQLException {
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + schema + "." + table + " SET active = FALSE, updated_by = ?"
                                + " WHERE tenant_id = ? AND id = ?")) {
                    CategoryImportSchema.setUuid(ps, 1, actorId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, applied.targetId());
                    ps.executeUpdate();
                }
            }
        };
    }

    private static void setValue(PreparedStatement ps, int index, Extra extra, String value)
            throws SQLException {
        // O apply() só chama com valor presente (branco opcional é omitido da
        // inserção; branco obrigatório já falhou antes). O ramo de nulo é só
        // defesa para chamada direta futura.
        if (value == null || value.isBlank()) {
            switch (extra.type()) {
                case INTEGER -> ps.setNull(index, Types.INTEGER);
                case DECIMAL -> ps.setNull(index, Types.DECIMAL);
                case BOOLEAN -> ps.setNull(index, Types.BOOLEAN);
                case DATE -> ps.setNull(index, Types.DATE);
                case TEXT -> ps.setNull(index, Types.VARCHAR);
            }
            return;
        }
        switch (extra.type()) {
            case INTEGER -> ps.setInt(index, Integer.parseInt(value));
            case DECIMAL -> ps.setBigDecimal(index, new java.math.BigDecimal(value));
            case BOOLEAN -> ps.setBoolean(index, ImportValidator.parseBoolean(value));
            case DATE -> ps.setObject(index, java.time.LocalDate.parse(value));
            case TEXT -> ps.setString(index, value);
        }
    }
}
