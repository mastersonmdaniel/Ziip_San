package br.com.ziipsan.core.transparency;

/**
 * Prazos da LAI do tenant ({@code sac.lai_deadlines}, ED-99; CAD-77):
 * classe c — o tenant ajusta DENTRO do limite legal, nunca acima dele.
 * Cada Municipio regula a LAI por norma propria (Lei 12.527/2011, art. 45),
 * mas a regulacao local nao pode superar os tetos da lei federal fixados
 * na spec da ED-99:
 * <pre>
 *   resposta            20 dias (prorrogaveis por mais 10)
 *   prorrogacao         10 dias
 *   recurso             10 dias (prazo do cidadao para recorrer)
 *   decisao do recurso   5 dias
 * </pre>
 * A guarda e testavel e vive na construcao: qualquer componente acima do
 * teto, zero ou negativo e RECUSADO aqui — nao depende de o chamador ter
 * validado antes (mesmo padrao da correcao G1 da ED-35). Componente nulo
 * na fabrica de parametros usa o teto legal como padrao de fabrica.
 */
public record LaiDeadlines(int responseDays, int extensionDays,
                           int appealDays, int appealDecisionDays) {

    /** Tetos da Lei 12.527/2011 conforme a spec da ED-99 — limites legais, nao prazos do tenant. */
    public static final int RESPONSE_LEGAL_MAX_DAYS = 20;
    public static final int EXTENSION_LEGAL_MAX_DAYS = 10;
    public static final int APPEAL_LEGAL_MAX_DAYS = 10;
    public static final int APPEAL_DECISION_LEGAL_MAX_DAYS = 5;

    /** Padrao de fabrica: os proprios tetos legais (o tenant so reduz, classe c). */
    public static final LaiDeadlines FACTORY = new LaiDeadlines(
            RESPONSE_LEGAL_MAX_DAYS, EXTENSION_LEGAL_MAX_DAYS,
            APPEAL_LEGAL_MAX_DAYS, APPEAL_DECISION_LEGAL_MAX_DAYS);

    public LaiDeadlines {
        check("responseDays", responseDays, RESPONSE_LEGAL_MAX_DAYS);
        check("extensionDays", extensionDays, EXTENSION_LEGAL_MAX_DAYS);
        check("appealDays", appealDays, APPEAL_LEGAL_MAX_DAYS);
        check("appealDecisionDays", appealDecisionDays, APPEAL_DECISION_LEGAL_MAX_DAYS);
    }

    private static void check(String field, int days, int legalMax) {
        if (days <= 0) throw new IllegalArgumentException(
                "sac.lai_deadlines." + field + " deve ser maior que zero: " + days);
        if (days > legalMax) throw new IllegalArgumentException(
                "sac.lai_deadlines." + field + " acima do teto da Lei 12.527/2011 ("
                        + legalMax + " dias): " + days
                        + " — classe c, o tenant ajusta dentro do limite legal (art. 45)");
    }

    /**
     * Monta os prazos a partir dos valores crus do tenant: componente
     * {@code null} usa o padrao de fabrica (teto legal); componente
     * presente passa pela guarda do construtor compacto.
     */
    public static LaiDeadlines fromTenantValues(Integer responseDays, Integer extensionDays,
                                                Integer appealDays, Integer appealDecisionDays) {
        return new LaiDeadlines(
                responseDays == null ? RESPONSE_LEGAL_MAX_DAYS : responseDays,
                extensionDays == null ? EXTENSION_LEGAL_MAX_DAYS : extensionDays,
                appealDays == null ? APPEAL_LEGAL_MAX_DAYS : appealDays,
                appealDecisionDays == null ? APPEAL_DECISION_LEGAL_MAX_DAYS : appealDecisionDays);
    }
}
