package br.com.ziipsan.core.sac;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.Map;

/**
 * Parametros da ED-35, SEMPRE lidos do tenant — nenhum prazo e constante de negocio:
 * <pre>
 *   sac.ombudsman_days              INTEGER dias da ouvidoria (Lei 13.460/2017, art. 16:
 *                                   teto legal de 30, classe c "so reduz" no catalogo
 *                                   PARAMETROS-DO-TENANT 20d §5, ATD-21; padrao de fabrica
 *                                   30 registrado na definition — aqui, valor ausente e
 *                                   erro explicito, nunca fallback silencioso)
 *   sac.external_channels           OBJECT  {"CONSUMER_GOV_BR": dias, ...} — prazo
 *                                   proprio por origem externa; origem sem prazo
 *                                   configurado e erro explicito
 *   portal.outage_status_visibility ENUM    PUBLIC_FULL | PUBLIC_SUMMARY | HIDDEN
 * </pre>
 * O chamador (fiacao) le o JSON com a classe {@code Json} do core e entrega os
 * valores tipados; esta classe valida. JSON nao e parseado aqui.
 */
public final class SacParameters {

    public static final String OMBUDSMAN_DAYS = "sac.ombudsman_days";
    public static final String EXTERNAL_CHANNELS = "sac.external_channels";
    public static final String OUTAGE_VISIBILITY = "portal.outage_status_visibility";

    /** Teto da Lei 13.460/2017, art. 16 — limite legal, nao prazo do tenant. */
    public static final int OMBUDSMAN_LEGAL_MAX_DAYS = 30;

    private SacParameters() {}

    public static int ombudsmanDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(OMBUDSMAN_DAYS,
                "prazo da ouvidoria nao configurado para o tenant");
        if (raw <= 0) throw new IllegalArgumentException(OMBUDSMAN_DAYS + " deve ser maior que zero");
        if (raw > OMBUDSMAN_LEGAL_MAX_DAYS) throw new IllegalArgumentException(
                OMBUDSMAN_DAYS + " acima do teto da Lei 13.460/2017, art. 16 (30 dias): " + raw);
        return raw;
    }

    public static int externalChannelDays(Map<ExternalOrigin, Integer> raw, ExternalOrigin origin) {
        if (origin == null) throw new IllegalArgumentException("origem externa obrigatoria");
        if (raw == null) throw new MissingParameterException(EXTERNAL_CHANNELS,
                "prazos dos canais externos nao configurados para o tenant");
        Integer days = raw.get(origin);
        if (days == null) throw new MissingParameterException(EXTERNAL_CHANNELS,
                "prazo nao configurado para a origem " + origin);
        if (days <= 0) throw new IllegalArgumentException(
                EXTERNAL_CHANNELS + " deve ser maior que zero para " + origin);
        return days;
    }

    public enum OutageVisibility { PUBLIC_FULL, PUBLIC_SUMMARY, HIDDEN }

    public static OutageVisibility outageVisibility(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(OUTAGE_VISIBILITY,
                "visibilidade da situacao do abastecimento nao configurada para o tenant");
        try {
            return OutageVisibility.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(OUTAGE_VISIBILITY + " desconhecido: " + raw);
        }
    }
}
