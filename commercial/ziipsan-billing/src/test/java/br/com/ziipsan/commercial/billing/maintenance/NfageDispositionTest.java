package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** P2.20: destino da NFAg-e viva (vocabulario de eventos da P2.6). A homologacao SEFAZ e aceite externo. */
class NfageDispositionTest {

    private static final UUID DOC = UUID.fromString("00000000-0000-0000-0000-0000000000d0");
    private static final UUID NEW_BILL = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    @DisplayName("sem NFAg-e viva: NONE, sem evento")
    void noDocument() {
        assertThat(NfageDisposition.decide(null, BillOperation.RECTIFY_CONSUMPTION, NEW_BILL))
                .isEqualTo(NfageDisposition.Disposition.NONE);
        assertThat(NfageDisposition.eventType(NfageDisposition.Disposition.NONE)).isNull();
    }

    @Test
    @DisplayName("retificacao com NFAg-e viva e conta nova: SUBSTITUTION")
    void rectificationSubstitutes() {
        var d = NfageDisposition.decide(DOC, BillOperation.RECTIFY_CONSUMPTION, NEW_BILL);
        assertThat(d).isEqualTo(NfageDisposition.Disposition.SUBSTITUTION);
        assertThat(NfageDisposition.eventType(d)).isEqualTo("SUBSTITUTION");
    }

    @Test
    @DisplayName("cancelamento puro com NFAg-e viva: CANCELLATION")
    void cancellationCancels() {
        var d = NfageDisposition.decide(DOC, BillOperation.CANCEL, null);
        assertThat(d).isEqualTo(NfageDisposition.Disposition.CANCELLATION);
        assertThat(NfageDisposition.eventType(d)).isEqualTo("CANCELLATION");
    }
}
