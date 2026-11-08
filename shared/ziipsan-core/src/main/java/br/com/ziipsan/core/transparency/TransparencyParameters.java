package br.com.ziipsan.core.transparency;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.Locale;
import java.util.Set;

/**
 * Parametros da ED-99 (transparencia ativa, LAI e dados abertos), SEMPRE
 * lidos do tenant — nenhum comportamento e presumido alem dos padroes que
 * a propria spec fixa:
 * <pre>
 *   pub.lai_mode            ENUM  NONE | EXTERNAL_LINK | NATIVE_SIC
 *                           Padrao por natureza juridica (ver abaixo):
 *                           o link para o sistema de acesso do municipio
 *                           (EXTERNAL_LINK) e o padrao quando o modulo
 *                           esta ligado; a adesao ao Fala.BR e voluntaria
 *                           (GES-06).
 *   pub.lai_external_url    URL   obrigatoria quando EXTERNAL_LINK.
 *   pub.falabr_opt_in       BOOL  adesao voluntaria ao Fala.BR; padrao false.
 *   pub.open_data_enabled   BOOL  dados abertos do tenant; padrao false
 *                           quando ausente (publicar e ato do tenant).
 *   sac.lai_deadlines       OBJ   prazos da LAI (classe c, nunca acima da
 *                           lei — guarda em {@link LaiDeadlines}).
 * </pre>
 *
 * Modulo opcional por natureza juridica do prestador: LIGADO por padrao
 * para autarquia ({@code AUTARCHY}) e empresa publica
 * ({@code PUBLIC_COMPANY}) — Lei 12.527/2011, art. 1º, paragrafo unico,
 * II, conforme a spec da ED-99 — e DESLIGADO por padrao nas demais
 * naturezas do dominio real de {@code reg.legal_nature} (core V9):
 * {@code PRIVATE} e {@code SEM}. Natureza fora do dominio ou ausente =
 * desligado (falha fechada, mesmo criterio de {@code GovBrPolicy} da
 * P3.3). Tenant de natureza desligada por padrao pode ligar o modulo
 * voluntariamente configurando {@code pub.lai_mode} — a LAI nao o obriga,
 * mas o produto nao o impede.
 *
 * <p>O chamador (fiacao) le o JSON com a classe {@code Json} do core e
 * entrega os valores tipados; esta classe valida — JSON nao e parseado
 * aqui (mesmo padrao de {@code SacParameters} da ED-35).
 */
public final class TransparencyParameters {

    public static final String LAI_MODE = "pub.lai_mode";
    public static final String LAI_EXTERNAL_URL = "pub.lai_external_url";
    public static final String FALABR_OPT_IN = "pub.falabr_opt_in";
    public static final String OPEN_DATA_ENABLED = "pub.open_data_enabled";
    public static final String LAI_DEADLINES = "sac.lai_deadlines";

    /** Dominio real de {@code reg.legal_nature} (core V9; literais da P3.3 — nao inventar outros). */
    public static final Set<String> LEGAL_NATURE_DOMAIN =
            Set.of("PRIVATE", "AUTARCHY", "PUBLIC_COMPANY", "SEM");

    /** Naturezas com o modulo LIGADO por padrao (spec da ED-99; Lei 12.527/2011, art. 1º, paragrafo unico, II). */
    public static final Set<String> DEFAULT_ENABLED_NATURES =
            Set.of("AUTARCHY", "PUBLIC_COMPANY");

    /** Modo de atendimento do pedido de acesso a informacao (GES-06). */
    public enum LaiMode { NONE, EXTERNAL_LINK, NATIVE_SIC }

    private TransparencyParameters() {}

    /** Normaliza a natureza juridica; fora do dominio real = {@code null} (desligado por padrao). */
    public static String normalizeLegalNature(String legalNature) {
        if (legalNature == null) return null;
        String v = legalNature.trim().toUpperCase(Locale.ROOT);
        return LEGAL_NATURE_DOMAIN.contains(v) ? v : null;
    }

    /** O modulo nasce ligado para este tenant? (so AUTARCHY e PUBLIC_COMPANY, pela spec). */
    public static boolean moduleEnabledByDefault(String legalNature) {
        String nature = normalizeLegalNature(legalNature);
        return nature != null && DEFAULT_ENABLED_NATURES.contains(nature);
    }

    /** Modo padrao: EXTERNAL_LINK (link do municipio) quando ligado por padrao; NONE nas demais. */
    public static LaiMode defaultLaiMode(String legalNature) {
        return moduleEnabledByDefault(legalNature) ? LaiMode.EXTERNAL_LINK : LaiMode.NONE;
    }

    /**
     * Modo vigente: valor cru {@code null}/em branco usa o padrao da
     * natureza; valor presente e parseado estrito — desconhecido e
     * recusado, nunca interpretado em silencio.
     */
    public static LaiMode laiMode(String legalNature, String rawMode) {
        if (rawMode == null || rawMode.isBlank()) return defaultLaiMode(legalNature);
        try {
            return LaiMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(LAI_MODE + " desconhecido: " + rawMode);
        }
    }

    /** O modulo esta operante para o tenant (modo diferente de NONE). */
    public static boolean moduleEnabled(String legalNature, String rawMode) {
        return laiMode(legalNature, rawMode) != LaiMode.NONE;
    }

    /**
     * URL do sistema de acesso do municipio: obrigatoria (erro explicito)
     * quando o modo e EXTERNAL_LINK — link padrao nao pode faltar.
     */
    public static String externalUrl(String rawUrl, LaiMode mode) {
        if (mode != LaiMode.EXTERNAL_LINK) return rawUrl;
        if (rawUrl == null || rawUrl.isBlank()) throw new MissingParameterException(
                LAI_EXTERNAL_URL, "pub.lai_mode=EXTERNAL_LINK exige o link do sistema de acesso do municipio");
        return rawUrl.trim();
    }

    /** Adesao ao Fala.BR: voluntaria (GES-06); ausente = nao aderiu. */
    public static boolean falaBrOptIn(Boolean raw) {
        return raw != null && raw;
    }

    /**
     * Dados abertos do tenant: ausente = desligado (padrao seguro — a
     * publicacao e ato expresso do tenant); presente vale como veio.
     */
    public static boolean openDataEnabled(Boolean raw) {
        return raw != null && raw;
    }
}
