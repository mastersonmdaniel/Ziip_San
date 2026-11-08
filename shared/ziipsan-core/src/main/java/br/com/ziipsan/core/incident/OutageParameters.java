package br.com.ziipsan.core.incident;

import br.com.ziipsan.core.parameters.MissingParameterException;

import java.util.List;

/**
 * Parametros da ED-92, SEMPRE do tenant — nenhum SLA, severidade ou
 * causa de fabrica alem do que o aceite nomeia:
 * <pre>
 *   outage.cause_catalog               LISTA de codigos de causa do tenant (extensivel);
 *                                      a UNICA causa com fonte nesta entrega e ALAGAMENTO
 *                                      (aceite da ED-64: "tipo alagamento no catalogo de
 *                                      causas da ocorrencia de rede da ED-92") — ver
 *                                      {@link #SOURCED_CAUSES}. Catalogo ausente/vazio no
 *                                      encerramento = erro explicito, nunca causa presumida.
 *   outage.min_duration_hours          padrao 6 (NR 9/ANA I-04), SO REDUZ: 0 &lt; v &lt;= 6.
 *   outage.emergency_supply_after_hours padrao 24 (OPE-16), SO REDUZ: 0 &lt; v &lt;= 24.
 *   outage.credit_enabled              padrao DESLIGADO (RAS-29); regra de credito so
 *                                      existe como dados do tenant/perfil da agencia
 *                                      ({@link OutageCreditRule}) — nenhuma formula de fabrica.
 * </pre>
 */
public final class OutageParameters {

    public static final String CAUSE_CATALOG = "outage.cause_catalog";
    public static final String MIN_DURATION_HOURS = "outage.min_duration_hours";
    public static final String EMERGENCY_SUPPLY_AFTER_HOURS = "outage.emergency_supply_after_hours";
    public static final String CREDIT_ENABLED = "outage.credit_enabled";

    /** Causas com FONTE nas tarefas consumidas: so ALAGAMENTO (ED-64). Nenhuma outra e semeada. */
    public static final List<String> SOURCED_CAUSES = List.of("ALAGAMENTO");

    /** Padroes NOMEADOS no aceite (NR 9/ANA I-04; OPE-16) — teto, nao piso. */
    public static final int MIN_DURATION_DEFAULT_HOURS = 6;
    public static final int EMERGENCY_DEFAULT_HOURS = 24;

    private OutageParameters() {}

    public static List<String> causeCatalog(List<String> raw) {
        if (raw == null || raw.isEmpty()) throw new MissingParameterException(CAUSE_CATALOG,
                "catalogo de causas nao configurado para o tenant; encerramento impossivel sem ele");
        return List.copyOf(raw);
    }

    public static void requireCauseInCatalog(List<String> catalogRaw, String causeCode) {
        List<String> catalog = causeCatalog(catalogRaw);
        if (!catalog.contains(causeCode)) throw new IllegalArgumentException(
                "causa fora do catalogo do tenant (" + CAUSE_CATALOG + "): " + causeCode);
    }

    public static int minDurationHours(Integer raw) {
        if (raw == null) return MIN_DURATION_DEFAULT_HOURS;
        if (raw <= 0 || raw > MIN_DURATION_DEFAULT_HOURS) throw new IllegalArgumentException(
                MIN_DURATION_HOURS + " so reduz a partir de 6 (NR 9/ANA I-04): " + raw);
        return raw;
    }

    public static int emergencySupplyAfterHours(Integer raw) {
        if (raw == null) return EMERGENCY_DEFAULT_HOURS;
        if (raw <= 0 || raw > EMERGENCY_DEFAULT_HOURS) throw new IllegalArgumentException(
                EMERGENCY_SUPPLY_AFTER_HOURS + " so reduz a partir de 24 (OPE-16): " + raw);
        return raw;
    }

    public static boolean creditEnabled(Boolean raw) { return Boolean.TRUE.equals(raw); }
}
