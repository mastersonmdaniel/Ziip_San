package br.com.ziipsan.core.sewerraw;

import br.com.ziipsan.core.parameters.MissingParameterException;

/**
 * Parametros da ED-80 lidos do tenant — nenhum prazo de alerta nasce
 * de fabrica. O aceite manda "alerta de vencimento" de outorga e de
 * licenca, mas NENHUMA fonte fixa com quantos dias de antecedencia:
 * a janela e parametro obrigatorio do tenant.
 * <pre>
 *   sewerraw.expiry_alert_window_days  inteiro &gt; 0 (dias)
 * </pre>
 * Ausencia = {@link MissingParameterException} (erro explicito,
 * nunca fallback), no padrao da ED-105 ({@code EffluentParameters}).
 */
public final class SewerRawParameters {

    public static final String EXPIRY_ALERT_WINDOW_DAYS = "sewerraw.expiry_alert_window_days";

    private SewerRawParameters() {}

    public static int expiryAlertWindowDays(Integer raw) {
        if (raw == null) throw new MissingParameterException(EXPIRY_ALERT_WINDOW_DAYS,
                "janela de alerta de vencimento nao configurada para o tenant:"
                        + " sem ela nenhum prazo de alerta e presumido (aceite ED-80)");
        if (raw <= 0) throw new IllegalArgumentException(
                EXPIRY_ALERT_WINDOW_DAYS + " deve ser maior que zero: " + raw);
        return raw;
    }
}
