package br.com.ziipsan.core.nondomestic;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros da ED-83 nomeados no aceite (CAD-70, OPE-13), lidos do
 * tenant — nenhum padrao de lancamento, intervalo, modelo de cobranca
 * ou valor nasce de fabrica nesta classe:
 * <pre>
 *   effluent.user_standard_catalog   TEXT versao do catalogo de padrao
 *       de lancamento DO PRESTADOR para o efluente do usuario, construido
 *       SOBRE o catalogo de padrao de efluente da ED-105
 *       ({@code EffluentCatalog}). A chave nomeia a versao vigente do
 *       catalogo do prestador; os LIMITES sao linhas desse catalogo,
 *       carregadas e revisadas — nunca constantes. Ausente, toda
 *       avaliacao de conformidade do usuario fica sem base e recusa.
 *   effluent.sampling_interval_days  INTEGER intervalo, em dias, do
 *       plano de amostragem do prestador no ponto de lancamento do
 *       usuario. Nenhum intervalo de fabrica consta das fontes:
 *       ausente ou nao positivo, o plano recusa.
 *   sewer.nondomestic_charge_model   TEXT modelo da cobranca
 *       complementar do esgoto nao domestico (classe d):
 *       VOLUME_PERCENT, POLLUTANT_LOAD ou FIXED_BY_CLASS — ver
 *       {@link NonDomesticChargeModel}. Ausente, nenhuma cobranca
 *       complementar e calculada.
 * </pre>
 * O chamador (fiacao) le o valor do tenant e entrega o valor tipado;
 * esta classe valida, no padrao da ED-50/ED-105.
 */
public final class NonDomesticParameters {

    public static final String USER_STANDARD_CATALOG = "effluent.user_standard_catalog";
    public static final String SAMPLING_INTERVAL_DAYS = "effluent.sampling_interval_days";
    public static final String NONDOMESTIC_CHARGE_MODEL = "sewer.nondomestic_charge_model";

    private NonDomesticParameters() {}

    /** Versao vigente do catalogo de padrao de lancamento do prestador (sobre a ED-105). */
    public static String userStandardCatalogVersion(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(USER_STANDARD_CATALOG,
                "catalogo de padrao de lancamento do prestador (sobre o catalogo da ED-105)"
                        + " nao configurado para o tenant: sem ele nenhum padrao de usuario"
                        + " e presumido (CAD-70)");
        return raw.trim();
    }

    /** Intervalo do plano de amostragem do prestador no ponto de lancamento, em dias. */
    public static int samplingIntervalDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(SAMPLING_INTERVAL_DAYS,
                "intervalo de amostragem no ponto de lancamento do usuario nao configurado"
                        + " para o tenant: nenhum intervalo nasce de fabrica (OPE-13)");
        if (raw <= 0) throw new IllegalArgumentException(
                SAMPLING_INTERVAL_DAYS + " deve ser maior que zero: " + raw);
        return raw;
    }

    /** Modelo da cobranca complementar (classe d), declarado pelo tenant/agencia. */
    public static NonDomesticChargeModel chargeModel(String raw) {
        if (raw == null || raw.isBlank()) throw new MissingParameterException(NONDOMESTIC_CHARGE_MODEL,
                "modelo de cobranca complementar do esgoto nao domestico nao configurado"
                        + " para o tenant: sem modelo declarado nenhuma cobranca nasce (classe d)");
        try {
            return NonDomesticChargeModel.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(NONDOMESTIC_CHARGE_MODEL
                    + " com valor desconhecido '" + raw + "': modelos validos sao"
                    + " VOLUME_PERCENT, POLLUTANT_LOAD e FIXED_BY_CLASS (classe d)");
        }
    }
}
