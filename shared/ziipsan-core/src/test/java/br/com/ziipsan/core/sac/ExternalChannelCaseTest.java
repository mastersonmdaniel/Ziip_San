package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
class ExternalChannelCaseTest {
    static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID P2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Map<ExternalOrigin,Integer> CH = Map.of(
        ExternalOrigin.CONSUMER_GOV_BR, 15, ExternalOrigin.PROCON_SINDEC, 10);
    @Test @DisplayName("prazo proprio por origem (teste do prazo)")
    void ownDeadline() {
        var a = new ExternalChannelCase(P1, ExternalOrigin.CONSUMER_GOV_BR, LocalDate.of(2026,3,2), CH, DeadlineCountingRule.CALENDAR, Set.of());
        var b = new ExternalChannelCase(P2, ExternalOrigin.PROCON_SINDEC, LocalDate.of(2026,3,2), CH, DeadlineCountingRule.CALENDAR, Set.of());
        assertThat(a.dueDate()).isEqualTo(LocalDate.of(2026,3,17));
        assertThat(b.dueDate()).isEqualTo(LocalDate.of(2026,3,12));
    }
    @Test @DisplayName("encerrar sem retorno ao canal de origem e recusado; com retorno, libera")
    void returnRequired() {
        var a = new ExternalChannelCase(P1, ExternalOrigin.CONSUMER_GOV_BR, LocalDate.of(2026,3,2), CH, DeadlineCountingRule.CALENDAR, Set.of());
        assertThatThrownBy(a::assertClosable).isInstanceOf(IllegalStateException.class);
        a.registerReturn(LocalDateTime.of(2026,3,10,10,0), "CG-998877");
        assertThatCode(a::assertClosable).doesNotThrowAnyException();
        assertThatThrownBy(() -> a.registerReturn(LocalDateTime.of(2026,3,11,10,0), "X"))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("M1: retorno anterior a abertura e recusado")
    void returnBeforeOpeningRefused() {
        var a = new ExternalChannelCase(P1, ExternalOrigin.CONSUMER_GOV_BR, LocalDate.of(2026,3,2), CH, DeadlineCountingRule.CALENDAR, Set.of());
        assertThatThrownBy(() -> a.registerReturn(LocalDateTime.of(2026,3,1,10,0), "CG-1"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(a.returned()).isFalse();
    }
    @Test @DisplayName("M1: close() encerra de fato \u2014 sem retorno recusa, antes do retorno recusa, segunda vez recusa")
    void closeOperation() {
        var a = new ExternalChannelCase(P1, ExternalOrigin.CONSUMER_GOV_BR, LocalDate.of(2026,3,2), CH, DeadlineCountingRule.CALENDAR, Set.of());
        assertThatThrownBy(() -> a.close(LocalDateTime.of(2026,3,10,11,0))).isInstanceOf(IllegalStateException.class);
        a.registerReturn(LocalDateTime.of(2026,3,10,10,0), "CG-998877");
        assertThatThrownBy(() -> a.close(LocalDateTime.of(2026,3,10,9,0))).isInstanceOf(IllegalArgumentException.class);
        a.close(LocalDateTime.of(2026,3,10,11,0));
        assertThat(a.closed()).isTrue();
        assertThat(a.closedAt()).isEqualTo(LocalDateTime.of(2026,3,10,11,0));
        assertThatThrownBy(() -> a.close(LocalDateTime.of(2026,3,10,12,0))).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("indicador por origem (base do RPT-102 da ED-46)")
    void indicators() {
        var ind = ExternalOriginReport.indicators(List.of(
            new ExternalOriginReport.CaseFact(ExternalOrigin.PROCON_SINDEC, LocalDate.of(2026,3,12), LocalDate.of(2026,3,10)),
            new ExternalOriginReport.CaseFact(ExternalOrigin.PROCON_SINDEC, LocalDate.of(2026,3,12), LocalDate.of(2026,3,20)),
            new ExternalOriginReport.CaseFact(ExternalOrigin.CONSUMER_GOV_BR, LocalDate.of(2026,3,17), null)));
        assertThat(ind.get(ExternalOrigin.PROCON_SINDEC).total()).isEqualTo(2);
        assertThat(ind.get(ExternalOrigin.PROCON_SINDEC).late()).isEqualTo(1);
        assertThat(ind.get(ExternalOrigin.PROCON_SINDEC).latePct()).isEqualByComparingTo("50.00");
        assertThat(ind.get(ExternalOrigin.CONSUMER_GOV_BR).closed()).isEqualTo(0);
    }
}
