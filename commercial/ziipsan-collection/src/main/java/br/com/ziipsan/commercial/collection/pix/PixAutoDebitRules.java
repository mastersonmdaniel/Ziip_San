package br.com.ziipsan.commercial.collection.pix;

import java.time.LocalTime;

/**
 * Regras operacionais do Pix Automatico REGISTRADAS (P3.2) - nao executadas.
 *
 * <p>A spec da P3.2 manda registrar as regras na interface de PSP e nos
 * parametros {@code pay.auto_debit.*} (classe d), com UM TESTE POR REGRA
 * adiado para a P4.5 e a ED-27. Esta classe e o registro unico dessas regras,
 * com validacao dos limites normativos; quem executa jornada, agendamento,
 * retentativa e cancelamento e a P4.5 - nenhum metodo aqui agenda, cobra ou
 * cancela nada.
 *
 * <p>Regras (Manual de Fluxos do Pix 2.2 / regras do Pix Automatico):
 * jornadas de autorizacao 1 a 4 (inclusive QR Code de autorizacao e primeiro
 * pagamento imediato); agendamento de 2 a 10 dias antes da liquidacao;
 * notificacao ao pagador; retentativas no maximo 3 em 7 dias, e somente se
 * previstas na autorizacao; cancelamento pelo pagador ate 23h59 da vespera e
 * pelo recebedor ate 22h do dia da liquidacao; multa e juros entram na
 * instrucao do ciclo seguinte; conciliacao por identificador de cobranca;
 * devolucao de debito indevido em 24 horas pelo banco do pagador.
 */
public final class PixAutoDebitRules {

    public static final int MIN_AUTHORIZATION_JOURNEY = 1;
    public static final int MAX_AUTHORIZATION_JOURNEY = 4;
    public static final int MIN_SCHEDULE_DAYS_BEFORE = 2;
    public static final int MAX_SCHEDULE_DAYS_BEFORE = 10;
    public static final int MAX_RETRIES = 3;
    public static final int RETRY_WINDOW_DAYS = 7;
    public static final LocalTime PAYER_CANCEL_DEADLINE = LocalTime.of(23, 59);
    public static final LocalTime RECEIVER_CANCEL_DEADLINE = LocalTime.of(22, 0);
    public static final int UNDUE_DEBIT_REFUND_HOURS = 24;

    private PixAutoDebitRules() {
    }

    /** Politica de retentativa vinda de pay.auto_debit.retry_policy, validada contra o teto normativo. */
    public record RetryPolicy(int maxRetries, int windowDays, boolean foreseenInAuthorization) {
        public RetryPolicy {
            if (maxRetries < 0 || maxRetries > MAX_RETRIES) {
                throw new IllegalStateException("pay.auto_debit.retry_policy: retentativas fora de 0.."
                        + MAX_RETRIES + ": " + maxRetries);
            }
            if (windowDays < 1 || windowDays > RETRY_WINDOW_DAYS) {
                throw new IllegalStateException("pay.auto_debit.retry_policy: janela fora de 1.."
                        + RETRY_WINDOW_DAYS + " dias: " + windowDays);
            }
            if (maxRetries > 0 && !foreseenInAuthorization) {
                throw new IllegalStateException("pay.auto_debit.retry_policy: retentativa so existe "
                        + "se prevista na autorizacao");
            }
        }
    }

    public static boolean isValidAuthorizationJourney(int journey) {
        return journey >= MIN_AUTHORIZATION_JOURNEY && journey <= MAX_AUTHORIZATION_JOURNEY;
    }

    public static boolean isValidScheduleLeadDays(int daysBeforeSettlement) {
        return daysBeforeSettlement >= MIN_SCHEDULE_DAYS_BEFORE
                && daysBeforeSettlement <= MAX_SCHEDULE_DAYS_BEFORE;
    }
}
