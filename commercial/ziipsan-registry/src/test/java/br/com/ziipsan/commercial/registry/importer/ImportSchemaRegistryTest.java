package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P1.40: o registro de esquemas — conhecido resolve, desconhecido e branco recusam. */
class ImportSchemaRegistryTest {

    @Test
    @DisplayName("esquemas embutidos resolvem; desconhecido e branco recusam sem 500")
    void registryResolvesBuiltIns() {
        ImportSchemaRegistry registry = new ImportSchemaRegistry(
                List.of(new CategoryImportSchema(), new SubcategoryImportSchema()));
        assertThat(registry.require("registry.categories").label()).isEqualTo("Categorias");
        assertThat(registry.require("registry.subcategories").columns())
                .extracting(ImportColumn::name)
                .containsExactly("category_code", "code", "name", "abbreviation",
                        "inspection_factor", "seasonal_supply", "active");
        assertThat(registry.schemas()).hasSize(2);
        assertThatThrownBy(() -> registry.require("registry.nao-existe"))
                .isInstanceOf(ImportSchemaRegistry.UnknownSchemaException.class);
        assertThatThrownBy(() -> registry.require("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FK da subcategoria aponta para categories(code); chave é code nos dois esquemas")
    void subcategoryForeignKeyAndKeys() {
        ImportColumn fk = new SubcategoryImportSchema().columns().get(0);
        assertThat(fk.foreignKey()).isEqualTo(new ImportColumn.ForeignKey("categories", "code"));
        assertThat(new CategoryImportSchema().columns().stream()
                .filter(ImportColumn::uniqueKey).map(ImportColumn::name)).containsExactly("code");
        assertThat(new SubcategoryImportSchema().columns().stream()
                .filter(ImportColumn::uniqueKey).map(ImportColumn::name)).containsExactly("code");
    }
}
