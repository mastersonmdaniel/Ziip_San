package br.com.ziipsan.commercial.registry.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2.24: o esquema da planilha mensal do laboratorio declarado ao
 * importador generico da P1.40 — modelo gerado do esquema, validacao por
 * linha (tipo, obrigatoriedade, duplicidade) e a recusa da planilha que o
 * aceite pede ("planilha do laboratorio recusada na validacao"). Sem
 * banco de verdade: as checagens de banco sao o DbChecks falso, como nos
 * demais testes do importador.
 */
class WaterQualityLabImportSchemaTest {

    private static final WaterQualityLabImportSchema SCHEMA = new WaterQualityLabImportSchema();

    private static final String HEADER =
            "reference_month,supply_system,parameter_key,parameter_name,unit,result_text,reference_text";

    private static String row(String month, String system, String key, String result) {
        return month + "," + system + "," + key + ",Nome de " + key + ",," + result + ",";
    }

    private static ImportCsvParser.ParsedCsv csv(String... lines) {
        StringBuilder content = new StringBuilder(HEADER).append("\n");
        for (String line : lines) {
            content.append(line).append("\n");
        }
        return ImportCsvParser.parse(content.toString());
    }

    private static ImportValidator.DbChecks checks(boolean existsInDatabase) {
        return new ImportValidator.DbChecks() {
            @Override
            public boolean foreignKeyExists(String table, String column, String value) {
                return true;
            }

            @Override
            public boolean existsInDatabase(Map<String, String> values) {
                return existsInDatabase;
            }
        };
    }

    @Test
    @DisplayName("o esquema resolve no registro e o modelo sai do esquema, na ordem declarada")
    void schemaResolvesAndTemplateFollowsDeclaration() {
        ImportSchemaRegistry registry = new ImportSchemaRegistry(List.<ImportSchema>of(SCHEMA));
        assertThat(registry.require("billing.water_quality_lab").label())
                .isEqualTo("Qualidade da agua - planilha mensal do laboratorio");
        assertThat(SCHEMA.columns()).extracting(ImportColumn::name)
                .containsExactly("reference_month", "supply_system", "parameter_key", "parameter_name",
                        "unit", "result_text", "reference_text");
        String template = ImportTemplate.csv(SCHEMA);
        assertThat(template.lines().findFirst().orElseThrow()).isEqualTo(HEADER);
        // A linha de exemplo do modelo e uma linha valida do proprio esquema.
        ImportValidator.ValidationResult result = SCHEMA.validate(
                ImportCsvParser.parse(template), checks(false));
        assertThat(result.rejections()).isEmpty();
        assertThat(result.accepted()).hasSize(1);
    }

    @Test
    @DisplayName("planilha valida: as linhas aceitas seguem para a aplicacao por OS de lote")
    void validSpreadsheetRowsAreAccepted() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                row("2026-05-01", "ETA_CENTRAL", "param_a", "1.0"),
                row("2026-05-01", "ETA_CENTRAL", "param_b", "conforme")), checks(false));
        assertThat(result.rejections()).isEmpty();
        assertThat(result.accepted()).hasSize(2);
    }

    @Test
    @DisplayName("planilha recusada na validacao: data fora do formato ISO e recusada por tipo, sem aceitar a linha")
    void spreadsheetWithInvalidDateIsRejected() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                row("15/05/2026", "ETA_CENTRAL", "param_a", "1.0")), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections()).hasSize(1);
        assertThat(result.rejections().get(0).field()).isEqualTo("reference_month");
        assertThat(result.rejections().get(0).reasonCode()).isEqualTo("INVALID_TYPE");
    }

    @Test
    @DisplayName("planilha recusada na validacao: resultado ausente e recusa de obrigatoriedade")
    void spreadsheetWithMissingResultIsRejected() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                "2026-05-01,ETA_CENTRAL,param_a,Nome de param_a,,,"), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .anyMatch(r -> r.field().equals("result_text") && r.reasonCode().equals("REQUIRED"));
    }

    @Test
    @DisplayName("planilha recusada na validacao: mesmo mes, sistema e parametro duas vezes e duplicidade no arquivo")
    void duplicatedRowInFileIsRejected() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                row("2026-05-01", "ETA_CENTRAL", "param_a", "1.0"),
                row("2026-05-01", "ETA_CENTRAL", "param_a", "2.0")), checks(false));
        assertThat(result.accepted()).hasSize(1);
        assertThat(result.rejections())
                .anyMatch(r -> r.reasonCode().equals("DUPLICATE_IN_FILE"));
    }

    @Test
    @DisplayName("reenvio da mesma planilha nao duplica: o que ja esta no banco e recusado como duplicidade")
    void resendIsRejectedAsDuplicateInDatabase() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                row("2026-05-01", "ETA_CENTRAL", "param_a", "1.0")), checks(true));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .anyMatch(r -> r.reasonCode().equals("DUPLICATE_IN_DATABASE"));
    }

    @Test
    @DisplayName("mes fora do primeiro dia: recusado na validacao do esquema e barrado de novo no aplicador")
    void monthNotAtStartIsRejectedAndBlockedAtApplier() {
        ImportValidator.ValidationResult result = SCHEMA.validate(csv(
                row("2026-05-15", "ETA_CENTRAL", "param_a", "1.0")), checks(false));
        assertThat(result.accepted()).isEmpty();
        assertThat(result.rejections())
                .anyMatch(r -> r.field().equals("reference_month") && r.reasonCode().equals("INVALID_DOMAIN"));

        assertThatThrownBy(() -> WaterQualityLabImportSchema.requireMonthStart(
                Map.of("reference_month", "2026-05-15")))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("primeiro dia do mes");
    }
}
