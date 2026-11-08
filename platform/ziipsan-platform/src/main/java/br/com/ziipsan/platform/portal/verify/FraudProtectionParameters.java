package br.com.ziipsan.platform.portal.verify;

import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parametros da protecao contra cobranca falsa (P3.35), no mesmo padrao de
 * {@code PortalParameters} (P3.3): JSON lido SOMENTE pela classe {@link Json}
 * do core.
 *
 * <pre>
 *   portal.verify_enabled     classe d  booleano   fabrica: true (padrao ligado, do aceite)
 *   portal.official_channels  classe d  array de {type, label, value} a partir de branding.contact
 *   portal.scam_alert         classe d  objeto {enabled, text} — alerta de golpe configuravel do portal
 * </pre>
 *
 * <p>{@code verify_enabled}: a ausencia devolve o padrao de fabrica do aceite
 * (ligado) — classe d significa exatamente que a fabrica fixa o padrao e o
 * tenant pode desligar; nao e valor presumido por esta classe. Valor presente
 * que nao seja booleano e erro explicito.
 *
 * <p>{@code official_channels} ausente = tenant ainda nao publicou canais
 * (lista vazia honesta, nada inventado); a conferencia contra
 * {@code branding.contact} e de {@link OfficialChannels}.
 *
 * <p>{@code scam_alert} ausente ou {@code enabled=false} = sem faixa de
 * alerta customizada; o texto padrao anti-golpe de {@link FraudProtectionTexts}
 * independe deste parametro.
 *
 * <p>O cadastro destas chaves em {@code public.parameter_definitions} NAO
 * entra como migration versionada: o mapa do plano (secao 3) reserva para a
 * P3.35 apenas a tenant {@code V8_3}, sem versao de core. O SQL de cadastro
 * acompanha a entrega em {@code db/pendente/} (ver RELATORIO-P3.35).
 */
public final class FraudProtectionParameters {

    public static final String VERIFY_ENABLED = "portal.verify_enabled";
    public static final String OFFICIAL_CHANNELS = "portal.official_channels";
    public static final String SCAM_ALERT = "portal.scam_alert";

    /** Alerta de golpe configuravel exibido no portal. */
    public record ScamAlert(boolean enabled, String text) {
        public ScamAlert {
            if (enabled && (text == null || text.isBlank())) {
                throw new IllegalArgumentException("portal.scam_alert: alerta ligado exige o texto exibido");
            }
            text = text == null ? null : text.strip();
        }

        public static ScamAlert disabled() {
            return new ScamAlert(false, null);
        }
    }

    private final Map<String, String> jsonByKey;

    private FraudProtectionParameters(Map<String, String> jsonByKey) {
        this.jsonByKey = jsonByKey;
    }

    public static FraudProtectionParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) {
            throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        }
        return new FraudProtectionParameters(Map.copyOf(jsonByKey));
    }

    /** Classe d, padrao de fabrica LIGADO (aceite da P3.35). */
    public boolean verifyEnabled() {
        String json = jsonByKey.get(VERIFY_ENABLED);
        if (json == null || json.isBlank()) {
            return true;
        }
        JsonNode node = Json.readTree(json);
        if (!node.isBoolean()) {
            throw new IllegalStateException(VERIFY_ENABLED + ": esperado booleano, veio " + node.getNodeType());
        }
        return node.booleanValue();
    }

    /** Canais declarados pelo tenant (a conferencia contra branding.contact e de OfficialChannels). */
    public List<OfficialChannel> declaredOfficialChannels() {
        String json = jsonByKey.get(OFFICIAL_CHANNELS);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode node = Json.readTree(json);
        if (!node.isArray()) {
            throw new IllegalStateException(OFFICIAL_CHANNELS + ": esperado array, veio " + node.getNodeType());
        }
        List<OfficialChannel> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new IllegalStateException(OFFICIAL_CHANNELS + ": cada canal deve ser objeto {type, label, value}");
            }
            String type = requiredText(item, "type");
            String label = requiredText(item, "label");
            String value = requiredText(item, "value");
            try {
                out.add(new OfficialChannel(OfficialChannel.ChannelType.valueOf(type), label, value));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(OFFICIAL_CHANNELS + ": tipo de canal desconhecido: " + type, e);
            }
        }
        return List.copyOf(out);
    }

    public ScamAlert scamAlert() {
        String json = jsonByKey.get(SCAM_ALERT);
        if (json == null || json.isBlank()) {
            return ScamAlert.disabled();
        }
        JsonNode node = Json.readTree(json);
        if (!node.isObject()) {
            throw new IllegalStateException(SCAM_ALERT + ": esperado objeto {enabled, text}, veio " + node.getNodeType());
        }
        JsonNode enabled = node.get("enabled");
        if (enabled == null || !enabled.isBoolean()) {
            throw new IllegalStateException(SCAM_ALERT + ": campo booleano obrigatorio ausente: enabled");
        }
        JsonNode text = node.get("text");
        String textValue = text == null || text.isNull() ? null : text.isTextual() ? text.textValue() : null;
        if (text != null && !text.isNull() && textValue == null) {
            throw new IllegalStateException(SCAM_ALERT + ": text deve ser textual");
        }
        return new ScamAlert(enabled.booleanValue(), textValue);
    }

    private static String requiredText(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException(OFFICIAL_CHANNELS + ": campo textual obrigatorio ausente: " + field);
        }
        return value.textValue();
    }
}
