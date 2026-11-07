package br.com.ziipsan.commercial.billing.maintenance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.20/JOR-09: fila de analise -- desvio contra a media e um teste por acao em lote. */
class AnalysisQueueRuleTest {

    private static final UUID READING = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private static AnalysisQueueRule.QueueItem item(Integer current, Integer average) {
        return new AnalysisQueueRule.QueueItem(READING, BILL, UNIT, current, average,
                List.of(90, 100, 110), "foto://leitura/1", "CONSUMO_ALTO", "ANOMALIA", "LEITURA_RELEITURA");
    }

    @Test
    @DisplayName("desvio: atual 150 x media 100 = +50,00%; atual 80 = -20,00%; media 0 ou ausente = sem desvio")
    void deviation() {
        assertThat(item(150, 100).deviationPercent()).isEqualByComparingTo("50.00");
        assertThat(item(80, 100).deviationPercent()).isEqualByComparingTo("-20.00");
        assertThat(item(10, 0).deviationPercent()).isNull();
        assertThat(item(10, null).deviationPercent()).isNull();
    }

    @Test
    @DisplayName("acao RELEASE: planeja sem OS")
    void release() {
        var planned = AnalysisQueueRule.plan(List.of(item(150, 100)), AnalysisQueueRule.QueueAction.RELEASE);
        assertThat(planned).hasSize(1);
        assertThat(planned.get(0).requiredOrderType()).isNull();
    }

    @Test
    @DisplayName("acao KEEP_HELD: planeja sem OS (so registra a decisao)")
    void keepHeld() {
        var planned = AnalysisQueueRule.plan(List.of(item(150, 100)), AnalysisQueueRule.QueueAction.KEEP_HELD);
        assertThat(planned.get(0).action()).isEqualTo(AnalysisQueueRule.QueueAction.KEEP_HELD);
    }

    @Test
    @DisplayName("acao OPEN_REVIEW: exige conta e carrega o tipo BILL_REVIEW; item sem conta recusa o lote")
    void openReview() {
        var planned = AnalysisQueueRule.plan(List.of(item(150, 100)), AnalysisQueueRule.QueueAction.OPEN_REVIEW);
        assertThat(planned.get(0).requiredOrderType()).isEqualTo(BillOrderType.BILL_REVIEW);
        var noBill = new AnalysisQueueRule.QueueItem(READING, null, UNIT, 10, 10, List.of(), null, null, "RETIDA", null);
        assertThatThrownBy(() -> AnalysisQueueRule.plan(List.of(noBill), AnalysisQueueRule.QueueAction.OPEN_REVIEW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("acao REQUEST_REREADING: exige leitura; item sem leitura recusa o lote; lote vazio/duplicado recusa")
    void requestRereading() {
        var planned = AnalysisQueueRule.plan(List.of(item(150, 100)), AnalysisQueueRule.QueueAction.REQUEST_REREADING);
        assertThat(planned.get(0).action()).isEqualTo(AnalysisQueueRule.QueueAction.REQUEST_REREADING);
        var noReading = new AnalysisQueueRule.QueueItem(null, BILL, UNIT, 10, 10, List.of(), null, null, "RETIDA",
                "LEITURA_RELEITURA");
        assertThatThrownBy(() -> AnalysisQueueRule.plan(List.of(noReading),
                AnalysisQueueRule.QueueAction.REQUEST_REREADING)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnalysisQueueRule.plan(List.of(), AnalysisQueueRule.QueueAction.RELEASE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnalysisQueueRule.plan(List.of(item(1, 1), item(1, 1)),
                AnalysisQueueRule.QueueAction.RELEASE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("acao REQUEST_REREADING: ocorrencia sem tipo de OS no catalogo de anormalidades recusa o lote")
    void requestRereadingWithoutDeclaredType() {
        var noType = new AnalysisQueueRule.QueueItem(READING, BILL, UNIT, 10, 10, List.of(), null,
                "CONSUMO_ALTO", "ANOMALIA", null);
        assertThatThrownBy(() -> AnalysisQueueRule.plan(List.of(noType),
                AnalysisQueueRule.QueueAction.REQUEST_REREADING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service_order_type_code");
    }
}
