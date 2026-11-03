package br.com.ziipsan.platform.vertical;

import br.com.ziipsan.core.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test of the work-order sections of the sanitation manifest
 * (P1.31; ADR-0045 item 3: the package declares its service types and
 * target classes in the manifest, and the declaration enters the
 * manifest contract test).
 *
 * <p>Rules enforced here, not just documented:
 * <ul>
 *   <li>service type codes are unique, and every type carries approval,
 *       effect validity, target classes and an effect code;</li>
 *   <li>a type marked declarableNow may only accept classes that have a
 *       verifier today (CONSUMER_UNIT, engine built-in, and LOCATION_ONLY,
 *       which needs none); any other class forces declarableNow=false, so
 *       the engine will refuse to register it until its owning module
 *       registers a verifier;</li>
 *   <li>the 23b §3.2 map is fully present: contact, customer data,
 *       ownership, economies, profile change/update, due day, delivery
 *       address, inscription, and the five connection-status orders;</li>
 *   <li>target classes are exactly the 14 of the catalog, each with a
 *       source of truth and a verifier status.</li>
 * </ul>
 */
class ServiceOrderManifestTest {

    private static final Set<String> TARGET_CLASSES = Set.of(
            "CONSUMER_UNIT", "WATER_CONNECTION", "SEWER_CONNECTION", "METER",
            "GIS_LINEAR", "GIS_POINT", "OPERATIONAL_SITE", "EQUIPMENT", "COMPONENT",
            "BUILDING", "FLEET_ASSET", "NETWORK_INCIDENT", "ZONE", "LOCATION_ONLY");

    private static final Set<String> VERIFIED_TODAY = Set.of("CONSUMER_UNIT", "LOCATION_ONLY");

    private static final Set<String> MAP_23B = Set.of(
            "CUSTOMER_CONTACT_UPDATE", "CUSTOMER_DATA_UPDATE", "OWNERSHIP_TRANSFER",
            "ECONOMIES_CATEGORY_CHANGE", "UNIT_PROFILE_CHANGE", "UNIT_PROFILE_UPDATE",
            "ALTERNATIVE_DUE_DAY", "DELIVERY_ADDRESS_CHANGE", "INSCRIPTION_CHANGE",
            "CONNECTION_INSTALL", "DISCONNECTION_CUT", "RECONNECTION", "SUPPRESSION", "PLUGGING");

    private static JsonNode manifest() throws IOException {
        String json = Files.readString(
                VerticalManifestTest.repoRoot().resolve("packages/sanitation/vertical.json"),
                StandardCharsets.UTF_8);
        return Json.readTree(json);
    }

    @Test
    @DisplayName("tipos de servico do manifesto: codigos unicos, campos completos, mapa 23b presente")
    void serviceTypesAreCompleteAndUnique() throws IOException {
        JsonNode types = manifest().path("serviceTypes");
        assertTrue(types.isArray() && !types.isEmpty(), "serviceTypes precisa existir e nao estar vazio");
        Set<String> codes = new HashSet<>();
        for (JsonNode type : types) {
            String code = type.path("code").asText();
            assertTrue(codes.add(code), "codigo de tipo duplicado: " + code);
            assertTrue(type.path("approvalByAnotherPerson").isBoolean(),
                    "approvalByAnotherPerson ausente em " + code);
            assertTrue(!type.path("effectValidity").asText().isBlank(),
                    "effectValidity ausente em " + code);
            assertTrue(type.path("targetClasses").isArray() && !type.path("targetClasses").isEmpty(),
                    "targetClasses ausente em " + code);
            for (JsonNode target : type.path("targetClasses")) {
                assertTrue(TARGET_CLASSES.contains(target.asText()),
                        "classe de alvo desconhecida em " + code + ": " + target.asText());
            }
            assertTrue(!type.path("effectCode").asText().isBlank(),
                    "effectCode ausente em " + code);
            assertTrue(type.path("declarableNow").isBoolean(),
                    "declarableNow ausente em " + code);
        }
        assertTrue(codes.containsAll(MAP_23B),
                "mapa 23b §3.2 incompleto; faltam: " + MAP_23B.stream().filter(c -> !codes.contains(c)).toList());
    }

    @Test
    @DisplayName("declarableNow=true so para classes com verificador hoje; resto e declaracao futura")
    void declarableNowMatchesVerifierStatus() throws IOException {
        for (JsonNode type : manifest().path("serviceTypes")) {
            Set<String> targets = new HashSet<>();
            type.path("targetClasses").forEach(t -> targets.add(t.asText()));
            if (type.path("declarableNow").asBoolean()) {
                assertTrue(VERIFIED_TODAY.containsAll(targets),
                        "tipo " + type.path("code").asText() + " marcado declarableNow mas aceita classe sem verificador: " + targets);
            } else {
                assertTrue(targets.stream().anyMatch(t -> !VERIFIED_TODAY.contains(t)),
                        "tipo " + type.path("code").asText() + " marcado declarableNow=false mas so aceita classes verificadas");
            }
        }
    }

    @Test
    @DisplayName("alvos da OS: exatamente as 14 classes do catalogo, com fonte e status do verificador")
    void targetClassesAreExactlyTheCatalog() throws IOException {
        JsonNode targets = manifest().path("serviceOrderTargets");
        assertTrue(targets.isArray(), "serviceOrderTargets precisa existir");
        Set<String> classes = new HashSet<>();
        for (JsonNode target : targets) {
            classes.add(target.path("class").asText());
            assertTrue(!target.path("sourceOfTruth").asText().isBlank(),
                    "sourceOfTruth ausente em " + target.path("class").asText());
            assertTrue(!target.path("verifier").asText().isBlank(),
                    "verifier ausente em " + target.path("class").asText());
        }
        assertEquals(TARGET_CLASSES, classes);
    }

    @Test
    @DisplayName("tipos operacionais: sem efeito comercial, origens validas, aba de OS no manifesto")
    void operationalTypesAndUnitTab() throws IOException {
        JsonNode root = manifest();
        JsonNode operational = root.path("operationalTypes");
        assertTrue(operational.path("effectCode").isNull(),
                "tipo operacional nao tem efeito comercial");
        Set<String> origins = Set.of("NETWORK_INCIDENT", "PREVENTIVE_PLAN", "INSPECTION", "COMPLAINT");
        for (JsonNode origin : operational.path("origins")) {
            assertTrue(origins.contains(origin.asText()), "origem operacional invalida: " + origin.asText());
        }
        assertEquals("so.operational_types_enabled", operational.path("enabledBy").asText());
        boolean hasTab = false;
        for (JsonNode module : root.path("modules")) {
            if ("service-order".equals(module.path("id").asText())) {
                for (JsonNode tab : module.path("unitSheetTabs")) {
                    hasTab |= "service-orders".equals(tab.asText());
                }
            }
        }
        assertTrue(hasTab, "modulo service-order precisa expor a aba service-orders na ficha da unidade");
    }
}
