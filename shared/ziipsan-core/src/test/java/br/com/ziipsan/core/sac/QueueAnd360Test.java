package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
class QueueAnd360Test {
    static final UUID N1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID N2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PR = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID UC1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    @Test @DisplayName("fila por prazo, com prioridade da P1.3 antes do prazo")
    void queueOrder() {
        var normalEarly = new ServiceQueue.QueueItem(N1, LocalDate.of(2026,3,10), LocalDate.of(2026,3,1), false);
        var normalLate = new ServiceQueue.QueueItem(N2, LocalDate.of(2026,3,20), LocalDate.of(2026,3,1), false);
        var priority = new ServiceQueue.QueueItem(PR, LocalDate.of(2026,3,25), LocalDate.of(2026,3,2), true);
        var ordered = ServiceQueue.order(List.of(normalLate, normalEarly, priority));
        assertThat(ordered).extracting(ServiceQueue.QueueItem::protocolId).containsExactly(PR,N1,N2);
    }
    @Test @DisplayName("visao 360: faturas abertas, vencidas e total")
    void view360() {
        var v = new Customer360View(UC1,
            List.of(new Customer360View.BillSummary("B1", LocalDate.of(2026,3,5), new BigDecimal("100.00"), false),
                    new Customer360View.BillSummary("B2", LocalDate.of(2026,4,5), new BigDecimal("50.00"), false),
                    new Customer360View.BillSummary("B3", LocalDate.of(2026,2,5), new BigDecimal("70.00"), true)),
            List.of(new Customer360View.ConsumptionSummary("2026-02", 12)),
            List.of(new Customer360View.ServiceSummary("2026/001", "IN_PROGRESS")),
            List.of(new Customer360View.WaterAnalysisSummary(LocalDate.of(2026,2,1), "POTAVEL")));
        assertThat(v.openBills()).isEqualTo(2);
        assertThat(v.overdueBillsOn(LocalDate.of(2026,3,10))).isEqualTo(1);
        assertThat(v.openAmount()).isEqualByComparingTo("150.00");
    }
}
