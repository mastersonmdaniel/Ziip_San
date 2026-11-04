package br.com.ziipsan.commercial.registry.legacy;

import br.com.ziipsan.core.json.Json;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mapeamento declarativo versionado de um legado (P1.2, PLT-16).
 *
 * <p>Um legado novo entra como um arquivo destes mais testes, sem importador
 * novo: o motor ({@link LegacyImportService}) só conhece alvos, transformações
 * e validações deste vocabulário. O arquivo diz, por campo: de onde vem
 * ({@code source}), para onde vai ({@code target}), como transforma
 * ({@code transform}) e o que valida ({@code validation}).
 *
 * <p>Formato (JSON):
 * <pre>
 * {"name": "piloto-v1", "version": "1", "sourceSystem": "PILOTO",
 *  "delimiter": ";",
 *  "fields": [
 *    {"source": "matricula", "target": "MATRICULA", "transform": "TRIM", "required": true},
 *    {"source": "dt_ligacao", "target": "CONNECTED_ON", "transform": "DATE:dd/MM/yyyy"},
 *    {"source": "cpf_cnpj", "target": "HOLDER_DOCUMENT", "transform": "DOCUMENT",
 *     "validation": "DOCUMENT_VALID"}],
 *  "installments": {"fields": [
 *    {"source": "matricula", "target": "UNIT_LEGACY_KEY", "required": true},
 *    {"source": "acordo", "target": "AGREEMENT_REF", "required": true},
 *    {"source": "parcela", "target": "INSTALLMENT_NO", "transform": "INTEGER"},
 *    {"source": "vencimento", "target": "DUE_DATE", "transform": "DATE:dd/MM/yyyy"},
 *    {"source": "valor", "target": "AMOUNT_CENTS", "transform": "DECIMAL_CENTS"}]}}
 * </pre>
 *
 * <p>Regras de parse (falha rápida, sem adivinhar): nome, versão e sistema de
 * origem são obrigatórios e não vazios; alvo desconhecido, transformação
 * desconhecida, validação desconhecida e alvo duplicado na mesma seção são
 * erros; a seção de unidades não pode ser vazia. A transformação
 * {@code DOCUMENT} só vale para o alvo {@code HOLDER_DOCUMENT}: o documento
 * é validado em memória e jamais persistido bruto (ver
 * {@link LegacyTransform.DocumentValue}).
 */
public record LegacyMapping(
        @NotBlank String name,
        @NotBlank String version,
        @NotBlank String sourceSystem,
        char delimiter,
        List<FieldMapping> fields,
        InstallmentMapping installments) {

    /** Alvos da seção de unidades. Só estes existem na v0; alvo novo exige decisão, não improviso. */
    public enum UnitTarget {
        LEGACY_KEY, MATRICULA, MATRICULA_DV,
        LOCALITY_CODE, SECTOR_CODE, BLOCK_CODE, ROUTE_CODE, CATEGORY_CODE,
        LOT, SUBLOT, READING_SEQUENCE,
        BALANCE_CENTS, CONNECTED_ON,
        HOLDER_NAME, HOLDER_DOCUMENT;

        static UnitTarget fromName(String raw) {
            try {
                return UnitTarget.valueOf(raw);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("Alvo de unidade desconhecido: " + raw
                        + ". Alvos válidos: " + List.of(UnitTarget.values()));
            }
        }
    }

    /** Alvos da seção de prestações (encenadas para a P2.10, nunca aplicadas na v0). */
    public enum InstallmentTarget {
        UNIT_LEGACY_KEY, AGREEMENT_REF, INSTALLMENT_NO, DUE_DATE, AMOUNT_CENTS;

        static InstallmentTarget fromName(String raw) {
            try {
                return InstallmentTarget.valueOf(raw);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException("Alvo de prestação desconhecido: " + raw
                        + ". Alvos válidos: " + List.of(InstallmentTarget.values()));
            }
        }
    }

    /** Transformação: o nome, e o argumento depois do primeiro ':' (só DATE usa). */
    public record Transform(String name, String argument) {
        static Transform parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new Transform("NONE", null);
            }
            String text = raw.trim();
            int colon = text.indexOf(':');
            if (colon < 0) {
                return new Transform(text, null);
            }
            return new Transform(text.substring(0, colon), text.substring(colon + 1));
        }
    }

    public record FieldMapping(
            @NotBlank String source,
            String target,
            Transform transform,
            String validation,
            boolean required) {
    }

    public record InstallmentMapping(List<FieldMapping> fields) {
    }

    private static final Set<String> TRANSFORMS = Set.of(
            "NONE", "TRIM", "UPPER", "INTEGER", "DECIMAL_CENTS", "DATE", "DOCUMENT");
    private static final Set<String> VALIDATIONS = Set.of(
            "NONE", "DOCUMENT_VALID", "NON_NEGATIVE", "MATRICULA_DIGITS");

    /** Seção de prestações presente (o mapeamento pode não ter prestações). */
    public boolean hasInstallments() {
        return installments != null && !installments.fields().isEmpty();
    }

    /**
     * Faz o parse estrito de um arquivo de mapeamento. Qualquer desvio do
     * vocabulário acima é erro — um mapeamento que o motor não entende não
     * pode carregar dado pela metade.
     */
    public static LegacyMapping parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("O arquivo de mapeamento está vazio.");
        }
        JsonNode root = Json.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("O arquivo de mapeamento precisa ser um objeto JSON.");
        }
        String name = requiredText(root, "name");
        String version = requiredText(root, "version");
        String sourceSystem = requiredText(root, "sourceSystem");
        char delimiter = ',';
        JsonNode delim = root.path("delimiter");
        if (!delim.isMissingNode() && !delim.isNull()) {
            String d = delim.asText();
            if (d.length() != 1) {
                throw new IllegalArgumentException("delimiter precisa ter exatamente 1 caractere: " + d);
            }
            delimiter = d.charAt(0);
        }
        List<FieldMapping> fields = parseFields(root.path("fields"), true);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("O mapeamento precisa de ao menos um campo na seção de unidades.");
        }
        InstallmentMapping installments = null;
        JsonNode inst = root.path("installments");
        if (!inst.isMissingNode() && !inst.isNull()) {
            if (!inst.isObject()) {
                throw new IllegalArgumentException("installments precisa ser um objeto com fields.");
            }
            List<FieldMapping> instFields = parseFields(inst.path("fields"), false);
            if (!instFields.isEmpty()) {
                installments = new InstallmentMapping(instFields);
            }
        }
        return new LegacyMapping(name, version, sourceSystem, delimiter, fields, installments);
    }

    private static List<FieldMapping> parseFields(JsonNode node, boolean unitSection) {
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("fields precisa ser um array.");
        }
        List<FieldMapping> out = new ArrayList<>();
        Map<String, String> seenTargets = new LinkedHashMap<>();
        for (JsonNode field : node) {
            if (!field.isObject()) {
                throw new IllegalArgumentException("Cada campo do mapeamento precisa ser um objeto.");
            }
            String source = requiredText(field, "source");
            String target = requiredText(field, "target");
            if (unitSection) {
                UnitTarget.fromName(target);
            } else {
                InstallmentTarget.fromName(target);
            }
            if (seenTargets.put(target, source) != null) {
                throw new IllegalArgumentException("Alvo duplicado no mapeamento: " + target);
            }
            Transform transform = Transform.parse(optionalText(field, "transform"));
            if (!TRANSFORMS.contains(transform.name())) {
                throw new IllegalArgumentException("Transformação desconhecida: " + transform.name()
                        + ". Válidas: " + TRANSFORMS);
            }
            if (transform.name().equals("DOCUMENT") && !(unitSection && target.equals("HOLDER_DOCUMENT"))) {
                throw new IllegalArgumentException(
                        "A transformação DOCUMENT só vale para o alvo HOLDER_DOCUMENT: " + source);
            }
            if (unitSection && target.equals("HOLDER_DOCUMENT") && !transform.name().equals("DOCUMENT")) {
                throw new IllegalArgumentException(
                        "O alvo HOLDER_DOCUMENT exige a transformação DOCUMENT (sem ela o documento "
                                + "chegaria bruto ao staging): " + source);
            }
            String validation = optionalText(field, "validation");
            if (validation == null || validation.isBlank()) {
                validation = "NONE";
            }
            if (!VALIDATIONS.contains(validation)) {
                throw new IllegalArgumentException("Validação desconhecida: " + validation
                        + ". Válidas: " + VALIDATIONS);
            }
            JsonNode required = field.path("required");
            boolean isRequired = !required.isMissingNode() && !required.isNull() && required.asBoolean(false);
            out.add(new FieldMapping(source, target, transform, validation, isRequired));
        }
        return List.copyOf(out);
    }

    private static String requiredText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório do mapeamento ausente ou vazio: " + key);
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
