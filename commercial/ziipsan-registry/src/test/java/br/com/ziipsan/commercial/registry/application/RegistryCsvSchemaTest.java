package br.com.ziipsan.commercial.registry.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * P1.3: a frente é DONA da declaração do esquema CSV do cadastro (a execução da importação é da
 * P1.40). O esquema é só declaração: cada coluna casa com o mapa campo → OS, nenhuma coluna
 * carrega dado sensível da LGPD art. 11 (pessoa dependente de abastecimento contínuo fica fora
 * do CSV, só a pedido do titular) e o nome CSV é snake_case.
 */
class RegistryCsvSchemaTest {

    @Test
    @DisplayName("declarações casam com o mapa campo → OS (nenhuma divergência)")
    void declarationsMatchOsTypeMap() {
        assertThatCode(RegistryCsvSchema::validateDeclarations).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cliente e unidade têm colunas declaradas, sem duplicata e em snake_case")
    void columnsAreNonEmptyUniqueAndSnakeCase() {
        for (List<RegistryCsvSchema.Column> columns : List.of(
                RegistryCsvSchema.CUSTOMER_COLUMNS, RegistryCsvSchema.UNIT_COLUMNS)) {
            assertThat(columns).isNotEmpty();
            Set<String> seen = new HashSet<>();
            for (RegistryCsvSchema.Column column : columns) {
                assertThat(column.csv()).matches("[a-z0-9_]+");
                assertThat(column.apiField()).isNotBlank();
                assertThat(column.target()).isIn("CUSTOMER", "CONSUMER_UNIT");
                assertThat(seen.add(column.csv())).as("coluna CSV duplicada: %s", column.csv()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("nenhuma coluna de dado sensível LGPD art. 11 no CSV")
    void noSensitiveArt11Columns() {
        List<String> forbidden = List.of("vulnerab", "dependent", "health", "deficien", "disease", "medical");
        for (List<RegistryCsvSchema.Column> columns : List.of(
                RegistryCsvSchema.CUSTOMER_COLUMNS, RegistryCsvSchema.UNIT_COLUMNS)) {
            for (RegistryCsvSchema.Column column : columns) {
                String haystack = (column.csv() + " " + column.apiField()).toLowerCase();
                assertThat(forbidden.stream().noneMatch(haystack::contains))
                        .as("coluna sensível no CSV: %s", column.csv()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("toda coluna com OS declarada tem tipo de OS preenchido")
    void everyDeclaredOsTypeIsFilled() {
        for (List<RegistryCsvSchema.Column> columns : List.of(
                RegistryCsvSchema.CUSTOMER_COLUMNS, RegistryCsvSchema.UNIT_COLUMNS)) {
            for (RegistryCsvSchema.Column column : columns) {
                if (column.osType() != null) {
                    assertThat(column.osType()).isNotBlank();
                }
            }
        }
    }
}
