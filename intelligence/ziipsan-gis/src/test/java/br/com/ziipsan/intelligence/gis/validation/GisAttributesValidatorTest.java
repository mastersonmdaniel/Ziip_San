package br.com.ziipsan.intelligence.gis.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GisAttributesValidator: o lado Java da validação do §3.3. O lado do banco
 * (gatilho gis_validate_attributes) é provado pela IT Gis01MigrationTest, por
 * SQL direto; os dois lados precisam continuar iguais.
 */
class GisAttributesValidatorTest {

    private static final String SCHEMA = """
            {
              "required": ["turns"],
              "properties": {
                "turns": {"type": "integer"},
                "actuator": {"type": "string", "enum": ["MANUAL", "ELECTRIC"]},
                "cover": {"$ref": "gis_value_list:cover_load_class"}
              },
              "additionalProperties": false
            }
            """;

    private static Set<String> lists(String code) {
        return switch (code) {
            case "cover_load_class" -> Set.of("A15", "D400");
            default -> Set.of();
        };
    }

    @Test
    @DisplayName("attributes dentro do schema passam")
    void validAttributesPass() {
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": 12, \"actuator\": \"MANUAL\", \"cover\": \"D400\"}", SCHEMA, GisAttributesValidatorTest::lists))
                .isEmpty();
    }

    @Test
    @DisplayName("attributes nulos valem {} e falham no required")
    void nullAttributesFailRequired() {
        assertThat(GisAttributesValidator.validate(null, SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("turns");
    }

    @Test
    @DisplayName("chave obrigatoria ausente e recusada por campo")
    void missingRequiredIsReportedByField() {
        assertThat(GisAttributesValidator.validate(
                "{\"actuator\": \"MANUAL\"}", SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("turns");
    }

    @Test
    @DisplayName("tipo errado e recusado (turns como texto)")
    void wrongTypeIsRejected() {
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": \"doze\"}", SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("turns");
    }

    @Test
    @DisplayName("valor fora do enum e recusado")
    void enumViolationIsRejected() {
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": 3, \"actuator\": \"HYDRAULIC\"}", SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("actuator");
    }

    @Test
    @DisplayName("codigo fora da lista de valores e recusado")
    void unknownListCodeIsRejected() {
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": 3, \"cover\": \"F900\"}", SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("cover");
    }

    @Test
    @DisplayName("chave nao declarada e recusada com additionalProperties false")
    void undeclaredKeyIsRejectedWhenAdditionalPropertiesFalse() {
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": 3, \"surprise\": true}", SCHEMA, GisAttributesValidatorTest::lists))
                .extracting(GisAttributesValidator.FieldError::field)
                .containsExactly("surprise");
    }

    @Test
    @DisplayName("sem additionalProperties false, chave extra passa (padrao JSON Schema)")
    void extraKeyPassesWithoutAdditionalPropertiesFalse() {
        String schema = "{\"properties\": {\"turns\": {\"type\": \"integer\"}}}";
        assertThat(GisAttributesValidator.validate(
                "{\"turns\": 3, \"surprise\": true}", schema, GisAttributesValidatorTest::lists))
                .isEmpty();
    }

    @Test
    @DisplayName("schema ausente e erro de programacao, nao de dado")
    void missingSchemaThrows() {
        assertThatThrownBy(() -> GisAttributesValidator.validate("{}", null, GisAttributesValidatorTest::lists))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("varios erros voltam juntos, um por campo")
    void multipleErrorsAreAllReported() {
        List<GisAttributesValidator.FieldError> errors = GisAttributesValidator.validate(
                "{\"actuator\": \"HYDRAULIC\", \"cover\": \"F900\", \"surprise\": 1}", SCHEMA,
                GisAttributesValidatorTest::lists);
        assertThat(errors).extracting(GisAttributesValidator.FieldError::field)
                .containsExactlyInAnyOrder("turns", "actuator", "cover", "surprise");
    }
}
