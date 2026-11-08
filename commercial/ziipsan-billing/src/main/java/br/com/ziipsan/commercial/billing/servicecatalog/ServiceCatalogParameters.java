package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura tipada, SEMPRE pela classe {@code Json} do core, dos parâmetros/conteúdos do
 * catálogo de serviços (ED-34) gravados como JSON pelo tenant:
 * <pre>
 *   service_type.required_documents  ARRAY  [{"code":"RG","name":"...","validityMonths":null}]
 *   service_type.workflow            ARRAY  [{"code":"REQUEST","name":"...","dependsOn":[],"deadlineHours":72}]
 *   doc.signature_policy             OBJECT {"CERTIDAO_QUITACAO":"ADVANCED", ...}
 *   connection.new_requires_sewer_when_available  BOOLEAN  true
 * </pre>
 * Ausência é erro explícito ({@link MissingParameterException}), nunca padrão assumido —
 * inclusive o booleano de esgoto simultâneo: sem valor aceito e vigente, erro explícito.
 * (As chaves rev.homologation_required / rev.sharing_pct NAO sao parametros lidos aqui:
 * sao campos do item de preco, validados em {@link ServicePriceItem}.)
 */
public final class ServiceCatalogParameters {

    public static final String REQUIRED_DOCUMENTS = "service_type.required_documents";
    public static final String WORKFLOW = "service_type.workflow";
    public static final String SIGNATURE_POLICY = "doc.signature_policy";
    public static final String NEW_REQUIRES_SEWER = "connection.new_requires_sewer_when_available";

    private ServiceCatalogParameters() {}

    public static List<RequiredDocument> requiredDocuments(String json) {
        JsonNode root = tree(json, REQUIRED_DOCUMENTS);
        if (!root.isArray()) throw new IllegalStateException(REQUIRED_DOCUMENTS + " deve ser um array JSON");
        List<RequiredDocument> out = new ArrayList<>();
        for (JsonNode n : root) {
            Integer validity = n.hasNonNull("validityMonths") ? n.get("validityMonths").asInt() : null;
            out.add(new RequiredDocument(text(n, "code"), text(n, "name"), validity,
                    n.path("requiresNotarizedSignature").asBoolean(false),
                    n.path("requiresAuthenticatedCopy").asBoolean(false)));
        }
        return out;
    }

    public static WorkflowDefinition workflow(String json) {
        JsonNode root = tree(json, WORKFLOW);
        if (!root.isArray()) throw new IllegalStateException(WORKFLOW + " deve ser um array JSON");
        List<WorkflowDefinition.Step> steps = new ArrayList<>();
        for (JsonNode n : root) {
            List<String> deps = new ArrayList<>();
            if (n.has("dependsOn")) for (JsonNode d : n.get("dependsOn")) deps.add(d.asString());
            Integer deadline = n.hasNonNull("deadlineHours") ? n.get("deadlineHours").asInt() : null;
            steps.add(new WorkflowDefinition.Step(text(n, "code"), text(n, "name"), deps, deadline));
        }
        return new WorkflowDefinition(steps);
    }

    public static SignaturePolicy signaturePolicy(String json) {
        JsonNode root = tree(json, SIGNATURE_POLICY);
        if (!root.isObject()) throw new IllegalStateException(SIGNATURE_POLICY + " deve ser um objeto JSON");
        Map<String, SignaturePolicy.Level> map = new LinkedHashMap<>();
        root.properties().forEach(e -> map.put(e.getKey(), SignaturePolicy.Level.valueOf(e.getValue().asString())));
        return new SignaturePolicy(map);
    }

    /** Booleano obrigatório: chave ausente é erro explícito, como nos demais parâmetros. */
    public static boolean newRequiresSewerWhenAvailable(String json) {
        JsonNode root = tree(json, NEW_REQUIRES_SEWER);
        if (!root.isBoolean()) throw new IllegalStateException(NEW_REQUIRES_SEWER + " deve ser booleano JSON");
        return root.asBoolean();
    }

    private static JsonNode tree(String json, String key) {
        if (json == null || json.isBlank())
            throw new MissingParameterException(key, "conteudo JSON ausente para a ED-34");
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode()) throw new MissingParameterException(key, "conteudo JSON vazio");
        return root;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.asString().isBlank())
            throw new IllegalStateException("campo obrigatorio ausente no JSON do catalogo: " + field);
        return v.asString();
    }
}
