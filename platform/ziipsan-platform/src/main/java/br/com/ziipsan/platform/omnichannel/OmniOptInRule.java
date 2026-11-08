package br.com.ziipsan.platform.omnichannel;

import java.util.List;

/**
 * Regra pura do opt-in por canal do atendimento (aceite da ED-42: "opt-in
 * por canal", no mesmo modelo da ED-41). É a GENERALIZAÇÃO declarada
 * (extensão E-ED42-2) da {@code OptInRule} da ED-41 para os canais que o
 * {@code NotificationChannel} não alcança (Messenger, Instagram,
 * Telegram): a semântica é a MESMA, sem afrouxar —
 *
 * <ul>
 *   <li>canal externo exige opt-in POSITIVO: uma linha de opt-in LIGADA
 *       para (morador, canal). Sem linha nenhuma, não há opt-in
 *       registrado: {@code OPT_IN_MISSING} — opt-in não se presume;</li>
 *   <li>linha desligada/revogada presente vence linha ligada (a leitura
 *       mais restritiva, como na ED-41): {@code OPT_IN_REVOKED};</li>
 *   <li>o armazenamento é próprio ({@code omni_channel_opt_ins}) porque
 *       as linhas da P3.36 são chaveadas pelo enum de canais de
 *       notificação; a fiação pode projetar as linhas do WhatsApp/e-mail
 *       da P3.36 nesta leitura na integração (pendência nomeada).</li>
 * </ul>
 */
public final class OmniOptInRule {

    public enum OptInDecision { ALLOWED, OPT_IN_MISSING, OPT_IN_REVOKED }

    private OmniOptInRule() {
    }

    /** @param rows estados das linhas de opt-in do (morador, canal): {@code true} = ligado, {@code false} = revogado/desligado. */
    public static OptInDecision decide(List<Boolean> rows) {
        if (rows == null) {
            throw new IllegalArgumentException("as linhas de opt-in são obrigatórias (podem ser vazias, não nulas)");
        }
        boolean anyEnabled = false;
        for (Boolean row : rows) {
            if (row == null) {
                throw new IllegalArgumentException("linha de opt-in sem estado não é linha válida");
            }
            if (!row) {
                return OptInDecision.OPT_IN_REVOKED;
            }
            anyEnabled = true;
        }
        return anyEnabled ? OptInDecision.ALLOWED : OptInDecision.OPT_IN_MISSING;
    }
}
