package br.com.ziipsan.commercial.collection.pixauto;

/**
 * Trava da decisao do dono (ADR-0028): autorizacao NOVA de debito em conta so
 * nasce como Pix Automatico. O FEBRABAN 150 legado NAO recebe autorizacao nova
 * por nenhum caminho: ele so trata de autorizacoes legadas importadas, na
 * P3.24 (com optantes no piloto, sob o parecer RAS-06) e na ED-27.
 */
public final class AccountDebitAuthorizationGate {

    public enum DebitChannel { PIX_AUTOMATICO, FEBRABAN_150_LEGADO }

    /** Recusa de autorizacao nova fora do Pix Automatico. */
    public static final class LegacyDebitAuthorizationRejectedException extends RuntimeException {
        public LegacyDebitAuthorizationRejectedException(String message) {
            super(message);
        }
    }

    private AccountDebitAuthorizationGate() {
    }

    public static void requirePixAutomaticoForNewAuthorization(DebitChannel channel) {
        if (channel == null) {
            throw new LegacyDebitAuthorizationRejectedException(
                    "canal de debito obrigatorio: autorizacao nova de debito em conta so por Pix Automatico");
        }
        if (channel != DebitChannel.PIX_AUTOMATICO) {
            throw new LegacyDebitAuthorizationRejectedException(
                    "autorizacao nova de debito em conta so por Pix Automatico; o canal "
                            + channel + " so trata autorizacoes legadas importadas (P3.24/ED-27)");
        }
    }
}
