package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.40: a validação por linha — tipo, domínio, FK, duplicidade no arquivo
 * e no banco — sem banco de verdade (DbChecks falso). Uma linha só é aceita
 * sem nenhuma recusa; a mensagem nunca ecoa o valor recusado.
 */
class ImportValidatorTest {

    private static final ImportSchema SCHEMA = new ImportSchema() {
        @Override
        public String code() {
            return "test.fake";
        }

        @Override
        public String label() {
            return "Falso";
        }

        @Override
        public List<ImportColumn> columns() {
            return List.of(
                    ImportColumn.key("code", "X1"),
                    ImportColumn.text("name", true, "Nome"),
                    new ImportColumn("kind", ImportColumn.ColumnType.TEXT, true,
                            List.of("A", "B"), "A", null, false),
                    new ImportColumn("parent_code", ImportColumn.ColumnType.TEXT, false,
                            List.of(), "P1",
                            new ImportColumn.ForeignKey("parents", "code"), false),
                    new ImportColumn("amount", ImportColumn.ColumnType.INTEGER, false,
                            List.of(), "10", null, false),
                    new ImportColumn("active", ImportColumn.ColumnType.BOOLEAN, false,
                            List.of(), "sim", null, false),
                    new ImportColumn("day", ImportColumn.ColumnType.DATE, false,
                            List.of(), "2026-11-03", null, false));
        }

        @Override
        public ImportApplier applier() {
            return new ImportApplier() {
                @Override
                public boolean existsByKey(Connection c, String s, UUID t, Map<String, String> v) {
                    return false;
                }

                @Override
                public Applied apply(Connection c, String s, UUID t, UUID a, Map<String, String> v) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void reverse(Connection c, String s, UUID t, UUID a, Applied applied) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    };

    private static ImportValidator.DbChecks checks(Set<String> existingParents, Set<String> existingKeys) {
        return new ImportValidator.DbChecks() {
            @Override
            public boolean foreignKeyExists(String table, String column, String value) {
                return table.equals("parents") && column.equals("code") && existingParents.contains(value);
            }

            @Override
            public boolean existsInDatabase(Map<String, String> values) {
                return existingKeys.contains(values.get("code"));
            }
        };
    }

    private static ImportCsvParser.ParsedCsv csv(String... lines) {
        StringBuilder content = new StringBuilder("code,name,kind,parent_code,amount,active,day\n");
        for (String line : lines) {
            content.append(line).append("\n");
        }
        return ImportCsvParser.parse(content.toString());
    }

    @Test
    @DisplayName("linha limpa é aceita; sim/não valem como booleano e ISO como data")
    void cleanRowAccepted() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv("X1,Nome,A,P1,10,sim,2026-11-03"), checks(Set.of("P1"), Set.of()));
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejections()).isEmpty();
    }

    @Test
    @DisplayName("obrigatório, tipo, domínio e FK geram uma recusa por campo, sem ecoar o valor")
    void fieldRejections() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv("X1,,C,NOPE,dez,talvez,03/11/2026"), checks(Set.of("P1"), Set.of()));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .extracting(ImportValidator.Rejection::reasonCode)
                .containsExactlyInAnyOrder("REQUIRED", "INVALID_DOMAIN", "FOREIGN_KEY_NOT_FOUND",
                        "INVALID_TYPE", "INVALID_TYPE", "INVALID_TYPE");
        assertThat(result.rejections()).allSatisfy(rejection ->
                assertThat(rejection.message()).doesNotContain("dez").doesNotContain("talvez"));
        // Campos certos: name, kind, parent_code, amount, active, day.
        assertThat(result.rejections())
                .extracting(ImportValidator.Rejection::field)
                .containsExactlyInAnyOrder("name", "kind", "parent_code", "amount", "active", "day");
    }

    @Test
    @DisplayName("duplicidade no arquivo aponta a primeira ocorrência; no banco, recusa própria")
    void duplicates() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv("X1,Nome,A,,,,\nX1,Outro,A,,,,\nX2,NoBanco,A,,,,"),
                checks(Set.of(), Set.of("X2")));
        // A primeira ocorrência da chave é aceita; só a repetição é recusada
        // (a mensagem da recusa cita a linha 1 como referência).
        assertThat(result.accepted()).extracting(row -> row.number()).containsExactly(1);
        ImportValidator.Rejection inFile = result.rejections().stream()
                .filter(r -> r.reasonCode().equals("DUPLICATE_IN_FILE")).findFirst().orElseThrow();
        assertThat(inFile.rowNumber()).isEqualTo(2);
        assertThat(inFile.message()).contains("linha 1");
        assertThat(result.rejections()).filteredOn(r -> r.reasonCode().equals("DUPLICATE_IN_DATABASE"))
                .extracting(ImportValidator.Rejection::rowNumber).containsExactly(3);
    }

    @Test
    @DisplayName("linha com qualquer recusa fica fora do lote; linha boa do mesmo arquivo entra")
    void rejectedRowsExcludedAcceptedRowsKept() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv("X1,Bom,A,,,,\nX2,,A,,,,"),
                checks(Set.of(), Set.of()));
        assertThat(result.accepted()).extracting(row -> row.number()).containsExactly(1);
        assertThat(result.rejections()).extracting(ImportValidator.Rejection::rowNumber).containsExactly(2);
    }

    @Test
    @DisplayName("coluna-chave em branco não gera duplicidade fantasma entre linhas")
    void blankKeyIsNotAFileDuplicate() {
        ImportValidator.ValidationResult result = ImportValidator.validate(SCHEMA,
                csv(",SemCodigo,A,,,,\n,OutroSemCodigo,A,,,,"),
                checks(Set.of(), Set.of()));
        assertThat(result.rejections()).noneMatch(r -> r.reasonCode().equals("DUPLICATE_IN_FILE"));
        assertThat(result.rejections()).allMatch(r -> r.reasonCode().equals("REQUIRED"));
    }
}
