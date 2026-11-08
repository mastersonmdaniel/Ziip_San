package br.com.ziipsan.intelligence.gis.webedit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GisFormSchemaTest {

    private static Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "required", List.of("diameter_mm"),
                "properties", Map.of(
                        "diameter_mm", Map.of(
                                "type", "integer", "title", "Diametro (mm)",
                                "minimum", 20, "maximum", 2000),
                        "material", Map.of(
                                "type", "string", "title", "Material",
                                "enum", List.of("PVC", "PEAD", "FOFO")),
                        "notes", Map.of("type", "string", "maxLength", 10)));
    }

    @Test
    void formIsGeneratedFromJsonSchema() {
        GisFormSchema.FormDescriptor form = GisFormSchema.formFromSchema(schema());
        assertEquals(3, form.fields().size());
        GisFormSchema.Field diameter = form.fields().stream()
                .filter(f -> f.name().equals("diameter_mm")).findFirst().orElseThrow();
        assertTrue(diameter.required());
        assertEquals("integer", diameter.type());
        assertEquals("Diametro (mm)", diameter.label());
        GisFormSchema.Field material = form.fields().stream()
                .filter(f -> f.name().equals("material")).findFirst().orElseThrow();
        assertEquals(List.of("PVC", "PEAD", "FOFO"), material.options());
    }

    @Test
    void emptySchemaProducesEmptyForm() {
        assertEquals(0, GisFormSchema.formFromSchema(Map.of()).fields().size());
        assertEquals(0, GisFormSchema.formFromSchema(null).fields().size());
    }

    @Test
    void validationCatchesRequiredTypeEnumRangeAndLength() {
        assertTrue(GisFormSchema.validate(schema(), Map.of()).stream()
                .anyMatch(p -> p.contains("obrigatorio")));
        assertTrue(GisFormSchema.validate(schema(),
                Map.of("diameter_mm", 10)).stream()
                .anyMatch(p -> p.contains("minimo")));
        assertTrue(GisFormSchema.validate(schema(),
                Map.of("diameter_mm", 100, "material", "COBRE")).stream()
                .anyMatch(p -> p.contains("lista")));
        assertTrue(GisFormSchema.validate(schema(),
                Map.of("diameter_mm", 100.5)).stream()
                .anyMatch(p -> p.contains("inteiro")));
        assertTrue(GisFormSchema.validate(schema(),
                Map.of("diameter_mm", 100, "notes", "0123456789X")).stream()
                .anyMatch(p -> p.contains("tamanho")));
        assertEquals(List.of(), GisFormSchema.validate(schema(),
                Map.of("diameter_mm", 100, "material", "PVC")));
    }
}
