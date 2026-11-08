package br.com.ziipsan.platform.notification.multichannel;

import br.com.ziipsan.platform.notification.NotificationPreference;

import java.util.List;

/**
 * Regra pura do opt-in por canal e finalidade (aceite da ED-41), lida do
 * registro único de preferências da P3.36:
 *
 * <ul>
 *   <li><b>categoria obrigatória</b> (piso da plataforma ∪ tenant, pela
 *       {@code NotificationPolicy} da P3.36): a preferência NÃO desliga —
 *       {@code ALLOWED_MANDATORY}, como no disparo da P3.5;</li>
 *   <li><b>demais categorias</b>: SMS, WhatsApp e push exigem opt-in
 *       POSITIVO — uma linha de preferência LIGADA para (sujeito,
 *       finalidade, categoria, canal). Sem linha nenhuma, não há opt-in
 *       registrado: {@code OPT_IN_MISSING} (diferente do e-mail da P3.5,
 *       onde a ausência vale o padrão do tenant — aqui o aceite pede
 *       opt-in, e opt-in não se presume);</li>
 *   <li>linha desligada presente vence linha ligada (a leitura mais
 *       restritiva, como na P3.5): {@code PREFERENCE_OFF}.</li>
 * </ul>
 */
public final class OptInRule {

    public enum OptInDecision { ALLOWED_MANDATORY, ALLOWED_OPT_IN, PREFERENCE_OFF, OPT_IN_MISSING }

    private OptInRule() {
    }

    public static OptInDecision decide(boolean mandatory, List<NotificationPreference> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("as linhas de preferência são obrigatórias (podem ser vazias, não nulas)");
        }
        if (mandatory) {
            return OptInDecision.ALLOWED_MANDATORY;
        }
        boolean anyEnabled = false;
        for (NotificationPreference row : rows) {
            if (!row.enabled()) {
                return OptInDecision.PREFERENCE_OFF;
            }
            anyEnabled = true;
        }
        return anyEnabled ? OptInDecision.ALLOWED_OPT_IN : OptInDecision.OPT_IN_MISSING;
    }
}
