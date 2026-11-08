package br.com.ziipsan.platform.training;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Documentação das customizações do cliente (ED-11): o registro que diz,
 * por tenant, tudo o que nele diverge do produto padrão — para o suporte,
 * a próxima implantação e a reversão (ED-04) saberem o que existe de
 * específico antes de precisar descobrir em produção.
 *
 * <p>As categorias são os pontos de variação que o próprio plano prevê —
 * nenhum ponto de customização foi inventado:
 * <ul>
 *   <li>{@code PARAMETER} — valor de parâmetro do tenant divergente do
 *       padrão de fábrica (P0.19), sempre por versão aceita;</li>
 *   <li>{@code BRAND_THEME} — marca, temas e marca no portal/e-mails
 *       (P1.19, P0.34–P0.38, P3.21);</li>
 *   <li>{@code ACCESS_PROFILE} — perfil de acesso próprio composto pelo
 *       administrador do tenant (ED-06 sobre a P0.13/P1.37);</li>
 *   <li>{@code LEGACY_MAPPING} — arquivo de mapeamento do legado do
 *       cliente no framework declarativo (P1.2/PLT-16);</li>
 *   <li>{@code DOCUMENT_TEMPLATE} — modelo de documento ou mensagem do
 *       tenant no motor único (P2.30, P2.32);</li>
 *   <li>{@code INTEGRATION} — conector/integração específica ligada no hub
 *       (P2.26, P3.37);</li>
 *   <li>{@code VERTICAL_MANIFEST} — conteúdo do manifesto do pacote de
 *       vertical do tenant (P0.47, ADR-0037).</li>
 * </ul>
 *
 * <p>Documentação completa, para um item, é: registrado, <b>aceito</b>
 * pelo tenant (quem decidiu e quando) e com a <b>nota de reversão</b> —
 * como aquilo se desfaz. Item pendente de aceite pode existir no registro
 * (ele é o retrato fiel do tenant), mas a documentação só está completa
 * sem nenhum. Registro vazio só é documentação completa com a declaração
 * explícita de que o tenant não tem customizações — silêncio não é
 * declaração.
 *
 * <p>O registro viaja em JSON montado e lido exclusivamente pela classe
 * {@code Json} do core, como o pacote de parâmetros da ED-09.
 */
public final class CustomizationDoc {

    private CustomizationDoc() {
    }

    public enum Category {
        PARAMETER, BRAND_THEME, ACCESS_PROFILE, LEGACY_MAPPING,
        DOCUMENT_TEMPLATE, INTEGRATION, VERTICAL_MANIFEST
    }

    public enum Status { RECORDED, PENDING_ACCEPTANCE, ACCEPTED }

    public record Customization(String key, Category category, String title,
                                String description, String sourceRef, Status status,
                                String decidedBy, LocalDate decidedOn,
                                String rollbackNote) {
        public Customization {
            if (key == null || key.isBlank() || category == null
                    || title == null || title.isBlank()
                    || description == null || description.isBlank()
                    || sourceRef == null || sourceRef.isBlank() || status == null) {
                throw new IllegalArgumentException(
                        "Customização exige chave, categoria, título, descrição, "
                                + "origem e situação.");
            }
        }
    }

    /**
     * @param declaredNone declaração explícita de que o tenant não tem
     *                     customização nenhuma (só vale com a lista vazia)
     */
    public record Registry(List<Customization> items, boolean declaredNone) {
        public Registry {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record Result(boolean complete, List<String> failures) {
    }

    public static Result completeness(Registry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("O registro de customizações é obrigatório.");
        }
        List<String> failures = new ArrayList<>();
        if (registry.items().isEmpty() && !registry.declaredNone()) {
            failures.add("Registro vazio sem a declaração explícita de que o tenant "
                    + "não tem customizações.");
        }
        if (!registry.items().isEmpty() && registry.declaredNone()) {
            failures.add("O registro declara inexistência mas lista "
                    + registry.items().size() + " customização(ões).");
        }
        List<String> seen = new ArrayList<>();
        for (Customization c : registry.items()) {
            if (seen.contains(c.key())) {
                failures.add("Customização repetida no registro: " + c.key());
            }
            seen.add(c.key());
            if (c.status() != Status.ACCEPTED) {
                failures.add("Customização sem aceite do tenant: " + c.key());
            } else {
                if (c.decidedBy() == null || c.decidedBy().isBlank() || c.decidedOn() == null) {
                    failures.add("Customização aceita sem quem decidiu ou sem a data: "
                            + c.key());
                }
                if (c.rollbackNote() == null || c.rollbackNote().isBlank()) {
                    failures.add("Customização aceita sem a nota de como se desfaz: "
                            + c.key());
                }
            }
        }
        return new Result(failures.isEmpty(), List.copyOf(failures));
    }

    /** Serializa o registro (JSON só pela classe {@code Json} do core). */
    public static String toJson(Registry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("O registro é obrigatório.");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("declaredNone", registry.declaredNone());
        List<Object> items = new ArrayList<>();
        for (Customization c : registry.items()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", c.key());
            m.put("category", c.category().name());
            m.put("title", c.title());
            m.put("description", c.description());
            m.put("sourceRef", c.sourceRef());
            m.put("status", c.status().name());
            m.put("decidedBy", c.decidedBy());
            m.put("decidedOn", c.decidedOn() == null ? null : c.decidedOn().toString());
            m.put("rollbackNote", c.rollbackNote());
            items.add(m);
        }
        root.put("items", items);
        return Json.write(root);
    }

    /** Lê um registro serializado por {@link #toJson}; conteúdo inválido recusa nomeando. */
    public static Registry importRegistry(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("O registro de customizações está vazio.");
        }
        JsonNode root = Json.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("O registro precisa ser um objeto JSON.");
        }
        boolean declaredNone = root.path("declaredNone").asBoolean(false);
        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray()) {
            throw new IllegalArgumentException("O registro não tem a lista items.");
        }
        List<Customization> items = new ArrayList<>();
        for (JsonNode n : itemsNode) {
            String key = text(n, "key");
            Category category;
            Status status;
            try {
                category = Category.valueOf(text(n, "category"));
                status = Status.valueOf(text(n, "status"));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Categoria ou situação fora do domínio no item: " + key, e);
            }
            String decidedOnText = optionalText(n, "decidedOn");
            items.add(new Customization(key, category, text(n, "title"),
                    text(n, "description"), text(n, "sourceRef"), status,
                    optionalText(n, "decidedBy"),
                    decidedOnText == null ? null : LocalDate.parse(decidedOnText),
                    optionalText(n, "rollbackNote")));
        }
        return new Registry(items, declaredNone);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isTextual() || v.asText().isBlank()) {
            throw new IllegalArgumentException("Campo textual obrigatório ausente: " + field);
        }
        return v.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText();
    }
}
