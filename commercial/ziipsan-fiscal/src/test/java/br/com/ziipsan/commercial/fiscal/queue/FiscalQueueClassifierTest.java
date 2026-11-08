package br.com.ziipsan.commercial.fiscal.queue;

import br.com.ziipsan.commercial.fiscal.nfage.NfageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classificação da fila (P2.33) sobre os estados do dicionário da P2.6. */
class FiscalQueueClassifierTest {

    @Test
    @DisplayName("autorizada, rejeitada, cancelada e substituída classificam pelo estado do dicionário")
    void terminalAndAuthorizedStates() {
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.AUTHORIZED)))
                .isEqualTo(FiscalQueueState.AUTHORIZED);
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(
                NfageStatus.REJECTED, false, false, "204", java.time.YearMonth.of(2026, 12),
                FiscalQueueTestSupport.GROUP))).isEqualTo(FiscalQueueState.REJECTED);
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.CANCELLED)))
                .isEqualTo(FiscalQueueState.CANCELLED);
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.REPLACED)))
                .isEqualTo(FiscalQueueState.REPLACED);
    }

    @Test
    @DisplayName("autorizada com cancelamento pendente classifica como CANCELLATION_PENDING (pendência da V4_11)")
    void authorizedWithPendingCancellation() {
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(
                NfageStatus.AUTHORIZED, false, true, null, java.time.YearMonth.of(2026, 12),
                FiscalQueueTestSupport.GROUP))).isEqualTo(FiscalQueueState.CANCELLATION_PENDING);
    }

    @Test
    @DisplayName("contingência não transmitida classifica CONTINGENCY; trânsito normal sem contingência não é item da fila")
    void contingencyAndNormalTransit() {
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(
                NfageStatus.RESERVED, true, false, null, java.time.YearMonth.of(2026, 12),
                FiscalQueueTestSupport.GROUP))).isEqualTo(FiscalQueueState.CONTINGENCY);
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.RESERVED))).isNull();
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.TRANSMITTED))).isNull();
    }

    @Test
    @DisplayName("VOIDED não é item da fila: classificar é erro explícito, nunca pendência fingida")
    void voidedIsError() {
        assertThatThrownBy(() -> FiscalQueueClassifier.classify(FiscalQueueTestSupport.nfage(NfageStatus.VOIDED)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("VOIDED");
    }

    @Test
    @DisplayName("NFS-e avulsa não transmitida é contingência da fila; transmitida NÃO é presumida autorizada")
    void avulsaClassification() {
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.avulsa(false)))
                .isEqualTo(FiscalQueueState.CONTINGENCY);
        assertThat(FiscalQueueClassifier.classify(FiscalQueueTestSupport.avulsa(true))).isNull();
    }

    @Test
    @DisplayName("só rejeição, contingência e cancelamento pendente geram item de tarefas")
    void workItemStates() {
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.REJECTED)).isTrue();
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.CONTINGENCY)).isTrue();
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.CANCELLATION_PENDING)).isTrue();
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.AUTHORIZED)).isFalse();
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.CANCELLED)).isFalse();
        assertThat(FiscalQueueClassifier.generatesWorkItem(FiscalQueueState.REPLACED)).isFalse();
    }
}
