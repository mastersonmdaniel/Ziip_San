package br.com.ziipsan.commercial.billing.maintenance;

import java.util.UUID;

/**
 * Destino da NFAg-e viva quando a conta e retificada ou cancelada (P2.6,
 * DAD-18): o vocabulario e o dos eventos de {@code fiscal_nfage_events}
 * ({@code CANCELLATION}, {@code SUBSTITUTION}) e a maquina de estados da
 * P2.6 so sai de AUTHORIZED por um desses dois eventos.
 *
 * <p>Esta regra decide <i>qual</i> evento o servico deve pedir ao modulo
 * fiscal; a transmissao do evento a SEFAZ e a prova em homologacao sao
 * aceite externo pendente (ver RELATORIO-P2.20) -- nenhum codigo aqui finge
 * protocolo da SEFAZ.
 */
public final class NfageDisposition {

    public enum Disposition {
        /** Conta sem NFAg-e viva: nada a fazer no fiscal. */
        NONE,
        /** Retificacao: a NFAg-e da conta velha e substituida pela da nova. */
        SUBSTITUTION,
        /** Cancelamento sem conta nova cobrando: a NFAg-e e cancelada. */
        CANCELLATION
    }

    private NfageDisposition() {
    }

    /**
     * @param liveFiscalDocumentId NFAg-e viva da conta (bills.fiscal_document_id); null = nenhuma
     * @param operation            operacao de manutencao em curso
     * @param newBillId            conta nova gerada (retificacao); null no cancelamento puro
     */
    public static Disposition decide(UUID liveFiscalDocumentId, BillOperation operation, UUID newBillId) {
        if (liveFiscalDocumentId == null) {
            return Disposition.NONE;
        }
        if (operation == BillOperation.CANCEL && newBillId == null) {
            return Disposition.CANCELLATION;
        }
        if (newBillId != null) {
            return Disposition.SUBSTITUTION;
        }
        return Disposition.NONE;
    }

    /** Tipo do evento em fiscal_nfage_events.event_type; null quando NONE. */
    public static String eventType(Disposition disposition) {
        return switch (disposition) {
            case NONE -> null;
            case SUBSTITUTION -> "SUBSTITUTION";
            case CANCELLATION -> "CANCELLATION";
        };
    }
}
