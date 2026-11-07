package br.com.ziipsan.compliance.lgpd.incident;

/**
 * Parâmetros do incidente LGPD (P3.10), nos moldes de {@code ParameterLimits} do core:
 * a trava fica nomeada pela chave e recusa valor fora da regra citando a fonte.
 *
 * <ul>
 *   <li>{@code lgpd.operator_notice_hours} — classe c, <b>só reduz</b>: o tenant pode encurtar
 *       o prazo do aviso do operador, nunca alongá-lo além do teto fixado no contrato
 *       (minuta da P3.9). A Res. CD/ANPD 15/2024 não fixa número para o aviso do operador;
 *       a ANPD recomenda fixá-lo em contrato — por isso o teto é parâmetro desta validação
 *       e NÃO um valor inventado aqui.</li>
 *   <li>{@code ret.incident_record_years} — guarda do registro do incidente por 5 anos
 *       (spec da P3.10).</li>
 * </ul>
 */
public final class LgpdIncidentParameters {

    public static final String OPERATOR_NOTICE_HOURS_KEY = "lgpd.operator_notice_hours";
    public static final String INCIDENT_RECORD_YEARS_KEY = "ret.incident_record_years";

    /** Guarda do registro do incidente fixada pela spec da P3.10. */
    public static final int INCIDENT_RECORD_YEARS = 5;

    private LgpdIncidentParameters() {
    }

    /**
     * Valida {@code lgpd.operator_notice_hours} (classe c, só reduz).
     *
     * @param hours              valor pretendido pelo tenant
     * @param contractualCeiling teto do contrato vigente (P3.9); {@code null} = contrato sem
     *                           teto registrado — nesse caso a validação RECUSA, porque sem
     *                           teto não há como provar a regra "só reduz" (pendência P3.9
     *                           registrada no relatório da P3.10)
     * @param previousAccepted   valor já aceito para o tenant; reduzir é permitido, aumentar
     *                           acima dele é recusado (só reduz)
     */
    public static void validateOperatorNoticeHours(int hours, Integer contractualCeiling,
                                                   Integer previousAccepted) {
        if (hours < 1) {
            throw new IllegalArgumentException(
                    OPERATOR_NOTICE_HOURS_KEY + " precisa ser ao menos 1 hora; recebido: " + hours);
        }
        if (contractualCeiling == null) {
            throw new IllegalArgumentException(
                    OPERATOR_NOTICE_HOURS_KEY + ": teto contratual não registrado (P3.9); "
                            + "sem o teto não é possível validar a regra de só reduzir");
        }
        if (hours > contractualCeiling) {
            throw new IllegalArgumentException(
                    OPERATOR_NOTICE_HOURS_KEY + " é classe c (só reduz): " + hours
                            + " h excede o teto contratual de " + contractualCeiling + " h");
        }
        if (previousAccepted != null && hours > previousAccepted) {
            throw new IllegalArgumentException(
                    OPERATOR_NOTICE_HOURS_KEY + " é classe c (só reduz): o valor aceito era "
                            + previousAccepted + " h e o novo é " + hours + " h");
        }
    }

    /** Valida {@code ret.incident_record_years}: a spec fixa 5 anos; valor diferente é recusado. */
    public static void validateIncidentRecordYears(int years) {
        if (years != INCIDENT_RECORD_YEARS) {
            throw new IllegalArgumentException(
                    INCIDENT_RECORD_YEARS_KEY + " está fixado em " + INCIDENT_RECORD_YEARS
                            + " anos pela P3.10; recebido: " + years);
        }
    }
}
