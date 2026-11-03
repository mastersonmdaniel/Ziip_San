package br.com.ziipsan.platform.vertical;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê um {@code vertical.json} para {@link VerticalManifest} e valida com
 * {@link VerticalManifestValidator} (as regras de {@code vertical.schema.json}).
 * O núcleo lê todos os manifestos presentes na imagem ao subir; manifesto inválido ou
 * versão do núcleo abaixo do mínimo impede a subida (PLATAFORMA-E-VERTICAIS §3) — a
 * amarração na subida é da P1.6 (console); aqui fica a leitura e a validação.
 */
public final class VerticalManifestLoader {

    private VerticalManifestLoader() {
    }

    public static VerticalManifest load(Path manifestPath) {
        String json;
        try {
            json = Files.readString(manifestPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel ler o manifesto: " + manifestPath, e);
        }
        JsonNode root = Json.readTree(json);
        if (root.isMissingNode() || !root.isObject()) {
            throw new IllegalStateException("O manifesto nao e um objeto JSON: " + manifestPath);
        }
        List<VerticalManifest.ModuleManifest> modules = new ArrayList<>();
        for (JsonNode module : array(root, "modules")) {
            List<VerticalManifest.MenuItem> menu = new ArrayList<>();
            for (JsonNode item : array(module, "menu")) {
                menu.add(new VerticalManifest.MenuItem(
                        text(item, "label"), text(item, "route"), text(item, "icon"),
                        optionalText(item, "permission")));
            }
            modules.add(new VerticalManifest.ModuleManifest(
                    text(module, "id"), text(module, "name"), text(module, "mavenModule"),
                    text(module, "status"), text(module, "originTask"),
                    optionalText(module, "permission"), optionalText(module, "engine"),
                    List.copyOf(menu), strings(module, "apiRoutes"), strings(module, "unitSheetTabs")));
        }
        List<VerticalManifest.GisDomain> domains = new ArrayList<>();
        JsonNode gis = root.path("gis");
        if (!gis.isMissingNode()) {
            for (JsonNode domain : array(gis, "domains")) {
                domains.add(new VerticalManifest.GisDomain(
                        text(domain, "key"), text(domain, "label"), text(domain, "color"),
                        domain.path("order").asInt(-1)));
            }
        }
        VerticalManifest manifest = new VerticalManifest(
                text(root, "id"), text(root, "version"), text(root, "minPlatformVersion"),
                text(root, "nameKey"), text(root, "descriptionKey"),
                List.copyOf(modules), List.copyOf(domains), strings(root, "permissions"));
        VerticalManifestValidator.validate(manifest);
        return manifest;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isTextual()) {
            throw new IllegalStateException("Campo obrigatorio ausente ou nao textual: " + field);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalStateException("Campo nao textual: " + field);
        }
        return value.asText();
    }

    private static List<JsonNode> array(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalStateException("Campo nao e array: " + field);
        }
        List<JsonNode> items = new ArrayList<>();
        value.forEach(items::add);
        return items;
    }

    private static List<String> strings(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : array(node, field)) {
            if (!item.isTextual()) {
                throw new IllegalStateException("Item nao textual em: " + field);
            }
            values.add(item.asText());
        }
        return List.copyOf(values);
    }
}
