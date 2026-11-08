package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.transparency.LaiRequest.AnswerOutcome;
import br.com.ziipsan.core.transparency.LaiRequest.AppealOutcome;
import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
class LaiAnnualReportTest {
    private static LaiRequest opened(LocalDate date) {
        return new LaiRequest(UUID.randomUUID(), ProtocolChannel.PORTAL, date, LaiMode.NATIVE_SIC,
                LaiDeadlines.FACTORY, DeadlineCountingRule.CALENDAR, Set.of());
    }
    @Test @DisplayName("relatorio anual agrega so o ano, so numeros: prazos, desfechos, prorrogacao e recursos")
    void annualAggregation() {
        var granted = opened(LocalDate.of(2026, 3, 2));
        granted.answer(LocalDate.of(2026, 3, 10), AnswerOutcome.GRANTED);
        var deniedAppealed = opened(LocalDate.of(2026, 3, 2));
        deniedAppealed.extend("levantamento", DeadlineCountingRule.CALENDAR, Set.of());
        deniedAppealed.answer(LocalDate.of(2026, 4, 1), AnswerOutcome.DENIED); // prorrogado: venceu 01/04, no prazo
        deniedAppealed.appeal(LocalDate.of(2026, 4, 5), DeadlineCountingRule.CALENDAR, Set.of());
        deniedAppealed.decideAppeal(LocalDate.of(2026, 4, 20), AppealOutcome.DENIED); // decisao devia 10/04: fora do prazo
        var late = opened(LocalDate.of(2026, 5, 4));
        late.answer(LocalDate.of(2026, 6, 1), AnswerOutcome.PARTIALLY_GRANTED); // venceu 24/05: fora do prazo
        var pending = opened(LocalDate.of(2026, 5, 4)); // sem resposta, vencido na referencia
        var otherYear = opened(LocalDate.of(2025, 5, 4));
        otherYear.answer(LocalDate.of(2025, 5, 10), AnswerOutcome.GRANTED);

        var report = LaiAnnualReport.ofYear(2026,
                List.of(granted, deniedAppealed, late, pending, otherYear), LocalDate.of(2026, 7, 1));
        assertThat(report.received()).isEqualTo(4);
        assertThat(report.answered()).isEqualTo(3);
        assertThat(report.answeredWithinDeadline()).isEqualTo(2);
        assertThat(report.answeredLate()).isEqualTo(1);
        assertThat(report.pendingOverdue()).isEqualTo(1);
        assertThat(report.extensionsUsed()).isEqualTo(1);
        assertThat(report.granted()).isEqualTo(1);
        assertThat(report.partiallyGranted()).isEqualTo(1);
        assertThat(report.denied()).isEqualTo(1);
        assertThat(report.appealsFiled()).isEqualTo(1);
        assertThat(report.appealsDecided()).isEqualTo(1);
        assertThat(report.appealsDecidedWithinDeadline()).isEqualTo(0);
        assertThat(report.appealsPendingOverdue()).isEqualTo(0);
    }
    @Test @DisplayName("ano sem pedidos = relatorio zerado, nunca numero presumido")
    void emptyYear() {
        var report = LaiAnnualReport.ofYear(2026, List.of(), LocalDate.of(2026, 12, 31));
        assertThat(report.received()).isZero();
        assertThat(report.answered()).isZero();
    }
}
