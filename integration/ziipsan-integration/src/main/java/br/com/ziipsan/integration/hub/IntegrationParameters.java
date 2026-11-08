package br.com.ziipsan.integration.hub;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * P3.37: parâmetros do tenant consumidos pelo hub de integrações, no
 * mesmo padrão de {@code PixParameters} (P3.2) e {@code SinisaParameters}
 * (P4.2): o chamador resolve os valores VIGENTES E ACEITOS das versões de
 * parâmetro (P0.19/P1.6) e entrega o JSON de cada chave; esta classe só
 * interpreta, sempre pela classe {@link Json} do core, e ausência é
 * {@link MissingParameterException} — nunca modo ou credencial presumidos.
 *
 * <p>Chaves (classe d, por integração {@code <name>}):
 * <pre>
 *   integration.&lt;name&gt;.mode           TEXTO  "SIMULATED" | "SANDBOX" | "PRODUCTION"
 *   integration.&lt;name&gt;.credential_ref TEXTO  "vault:..." (REFERÊNCIA no cofre da P0.45, nunca o segredo)
 * </pre>
 *
 * <p>O nome da integração entra na chave do parâmetro, por isso é
 * validado: minúsculas, dígitos e hífen ({@code [a-z0-9][a-z0-9-]*}),
 * o mesmo alfabeto dos nomes de conector do catálogo.
 *
 * <p><b>Produção só com aceite:</b> {@link #operableMode} recusa
 * {@code PRODUCTION} quando o aceite do tenant para aquela integração
 * não foi registrado ({@code productionAccepted == false}). Como os
 * valores entregues a esta classe já são os aceitos das versões de
 * parâmetro, o aceite aqui é o registro específico de entrada em
 * produção da integração, conferido pela fiação (tela do tenant) — o
 * hub não presume aceite a partir do modo gravado.
 */
public final class IntegrationParameters {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");

    private final Map<String, String> jsonByKey;

    private IntegrationParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    /** @param jsonByKey chave do parâmetro -> JSON vigente e aceito (texto da coluna {@code value}) */
    public static IntegrationParameters fromValues(Map<String, String> jsonByKey) {
        Objects.requireNonNull(jsonByKey, "Mapa de parâmetros é obrigatório");
        return new IntegrationParameters(Map.copyOf(jsonByKey));
    }

    public static String modeKey(String integrationName) {
        return "integration." + requireValidName(integrationName) + ".mode";
    }

    public static String credentialRefKey(String integrationName) {
        return "integration." + requireValidName(integrationName) + ".credential_ref";
    }

    public static String requireValidName(String integrationName) {
        if (integrationName == null || !NAME_PATTERN.matcher(integrationName).matches()) {
            throw new IllegalArgumentException(
                    "Nome de integração inválido (esperado [a-z0-9][a-z0-9-]*): " + integrationName);
        }
        return integrationName;
    }

    /** Modo declarado da integração para o tenant. Ausência é erro explícito. */
    public IntegrationMode mode(String integrationName) {
        String key = modeKey(integrationName);
        JsonNode node = required(key);
        if (!node.isTextual()) {
            throw new IllegalStateException(key + ": esperado texto SIMULATED, SANDBOX ou PRODUCTION, veio "
                    + node.getNodeType());
        }
        try {
            return IntegrationMode.valueOf(node.textValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(key + ": modo fora do catálogo: " + node.textValue(), e);
        }
    }

    /**
     * Modo OPERÁVEL: igual a {@link #mode}, mas {@code PRODUCTION} sem o
     * aceite registrado do tenant é recusado — o aceite é do tenant para
     * aquela integração, não decorre do valor do parâmetro.
     */
    public IntegrationMode operableMode(String integrationName, boolean productionAccepted) {
        IntegrationMode mode = mode(integrationName);
        if (mode == IntegrationMode.PRODUCTION && !productionAccepted) {
            throw new IllegalStateException("integration." + integrationName
                    + ".mode é PRODUCTION, mas não há aceite do tenant registrado para produção"
                    + " nesta integração: produção só com aceite (P3.37)");
        }
        return mode;
    }

    /** Referência da credencial no cofre da P0.45 (nunca o segredo). Ausência é erro explícito. */
    public String credentialRef(String integrationName) {
        String key = credentialRefKey(integrationName);
        JsonNode node = required(key);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalStateException(key + ": esperado texto não vazio com a referência no cofre");
        }
        return node.textValue();
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) {
            throw new MissingParameterException(key, "nenhum valor entregue ao hub de integrações");
        }
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) {
            throw new MissingParameterException(key, "valor JSON é nulo");
        }
        return node;
    }
}
