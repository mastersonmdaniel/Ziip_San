package br.com.ziipsan.commercial.collection.dunning.negativation;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.core.parameters.MissingParameterException;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parametros da negativacao e do protesto por arquivo (ED-30; v2.15), no
 * padrao de {@code DunningParameters} (P4.1) e {@code ActiveDebtParameters}
 * (ED-31): JSON sempre pela classe {@code Json} do core; ausencia de
 * parametro sem padrao na spec e erro explicito, nunca valor assumido.
 *
 * <ul>
 *   <li>{@code neg.channels} — canais de remessa (biro e central de
 *       protesto) do tenant: array JSON de codigos de canal. Os CODIGOS sao
 *       configuracao do tenant; nenhum biro/central e nomeado neste codigo
 *       (sem fonte para a lista) e o LAYOUT de arquivo de cada canal e
 *       [SEM FONTE] — ver {@code NegativationPorts};</li>
 *   <li>{@code neg.prior_notice_by} — meio da carta de aviso previo
 *       (codigo texto do tenant, ex.: o codigo do modelo/meio registrado);
 *       obrigatorio: sem ele nao ha como cumprir o aviso previo;</li>
 *   <li>{@code neg.notice_min_days} — intervalo minimo (dias) entre a
 *       ENTREGA registrada do aviso previo e a inclusao; obrigatorio;</li>
 *   <li>{@code neg.excluded_profiles} — perfis excluidos da negativacao:
 *       array JSON de codigos de perfil do tenant; obrigatorio (pode ser
 *       array vazio declarado, nunca ausencia silenciosa).</li>
 * </ul>
 *
 * <p>VALORES TRAVADOS do aceite (NAO sao parametros e NAO podem ser
 * alterados por configuracao): exclusao enviada em no maximo
 * {@link #EXCLUSION_SEND_MAX_BUSINESS_DAYS} dias uteis apos o pagamento e
 * expiracao da inclusao em {@link #LISTING_EXPIRY_YEARS} anos. Vivem como
 * constantes nomeadas aqui e sao conferidos por teste de consistencia
 * contra o SQL pendente.
 */
public final class NegativationParameters {

    public static final String CHANNELS = "neg.channels";
    public static final String PRIOR_NOTICE_BY = "neg.prior_notice_by";
    public static final String NOTICE_MIN_DAYS = "neg.notice_min_days";
    public static final String EXCLUDED_PROFILES = "neg.excluded_profiles";

    /** TRAVADO pelo aceite da ED-30: exclusao enviada em no maximo 5 dias uteis. */
    public static final int EXCLUSION_SEND_MAX_BUSINESS_DAYS = 5;
    /** TRAVADO pelo aceite da ED-30: a inclusao expira em 5 anos. */
    public static final int LISTING_EXPIRY_YEARS = 5;
    /** Tipo de OS de exclusao a pedido, nomeado pelo aceite da ED-30. */
    public static final String EXCLUSION_SERVICE_ORDER_TYPE = "NEGATIVE_LISTING_EXCLUSION";

    private final Map<String, String> jsonByKey;

    private NegativationParameters(Map<String, String> jsonByKey) { this.jsonByKey = jsonByKey; }

    public static NegativationParameters fromValues(Map<String, String> jsonByKey) {
        if (jsonByKey == null) throw new IllegalArgumentException("Mapa de parametros e obrigatorio");
        return new NegativationParameters(Map.copyOf(jsonByKey));
    }

    /** Canais de remessa do tenant (codigos); ao menos um, todos nao vazios. */
    public List<String> channels() {
        List<String> channels = requiredTextList(CHANNELS);
        if (channels.isEmpty()) throw new IllegalStateException(CHANNELS + ": ao menos um canal e obrigatorio");
        return channels;
    }

    /** Meio da carta de aviso previo (codigo do tenant). */
    public String priorNoticeBy() { return requiredText(PRIOR_NOTICE_BY); }

    /** Intervalo minimo entre a entrega do aviso previo e a inclusao (dias, >= 1). */
    public int noticeMinDays() {
        int days = requiredInt(NOTICE_MIN_DAYS);
        if (days < 1) throw new IllegalStateException(NOTICE_MIN_DAYS + ": deve ser ao menos 1, veio " + days);
        return days;
    }

    /** Perfis excluidos (codigos do tenant); array vazio declarado e valido. */
    public List<String> excludedProfiles() { return requiredTextList(EXCLUDED_PROFILES); }

    private List<String> requiredTextList(String key) {
        JsonNode node = required(key);
        if (!node.isArray()) throw new IllegalStateException(key + ": esperado array, veio " + node.getNodeType());
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isString() || item.stringValue().isBlank())
                throw new IllegalStateException(key + ": item deve ser texto nao vazio");
            out.add(item.stringValue());
        }
        return List.copyOf(out);
    }

    private String requiredText(String key) {
        JsonNode node = required(key);
        if (!node.isTextual() || node.asText().isBlank())
            throw new IllegalStateException(key + ": esperado texto nao vazio");
        return node.asText();
    }

    private int requiredInt(String key) {
        JsonNode node = required(key);
        if (!node.isIntegralNumber()) throw new IllegalStateException(key + ": esperado inteiro, veio " + node.getNodeType());
        long v = node.longValue();
        if (v < 0 || v > Integer.MAX_VALUE) throw new IllegalStateException(key + ": inteiro fora da faixa: " + v);
        return (int) v;
    }

    private JsonNode required(String key) {
        String json = jsonByKey.get(key);
        if (json == null || json.isBlank()) throw new MissingParameterException(key, "nenhum valor entregue a negativacao");
        JsonNode node = Json.readTree(json);
        if (node.isMissingNode() || node.isNull()) throw new MissingParameterException(key, "valor JSON e nulo");
        return node;
    }
}
