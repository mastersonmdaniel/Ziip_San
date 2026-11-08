package br.com.ziipsan.core.transparency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import br.com.ziipsan.core.transparency.LaiRequest.AnswerOutcome;
import br.com.ziipsan.core.transparency.LaiRequest.AppealOutcome;
import br.com.ziipsan.core.transparency.TransparencyParameters.LaiMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
class LaiRequestTest {
    private static final UUID PROTOCOL = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final LocalDate OPENED = LocalDate.of(2026, 1, 5); // segunda-feira

    private LaiRequest request(LaiMode mode, LaiDeadlines deadlines) {
        return new LaiRequest(PROTOCOL, ProtocolChannel.PORTAL, OPENED, mode, deadlines,
                DeadlineCountingRule.CALENDAR, Set.of());
    }

    @Test @DisplayName("resposta em 20 dias corridos pelo motor de prazos; prorrogacao unica de 10, justificada")
    void responseAndExtension() {
        var r = request(LaiMode.NATIVE_SIC, LaiDeadlines.FACTORY);
        assertThat(r.originalResponseDue()).isEqualTo(LocalDate.of(2026, 1, 25));
        assertThat(r.currentResponseDue()).isEqualTo(LocalDate.of(2026, 1, 25));
        assertThatThrownBy(() -> r.extend(" ", DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
        r.extend("informacao em levantamento junto a outro setor", DeadlineCountingRule.CALENDAR, Set.of());
        assertThat(r.currentResponseDue()).isEqualTo(LocalDate.of(2026, 2, 4));
        assertThat(r.extended()).isTrue();
        assertThatThrownBy(() -> r.extend("segunda vez", DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("contagem BUSINESS do motor: 20 dias uteis a partir de segunda 05/01/2026 caem em 02/02/2026 (sem feriados)")
    void businessCounting() {
        var r = new LaiRequest(PROTOCOL, ProtocolChannel.PORTAL, OPENED, LaiMode.NATIVE_SIC,
                LaiDeadlines.FACTORY, DeadlineCountingRule.BUSINESS, Set.of());
        assertThat(r.originalResponseDue()).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(r.originalResponseDue().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }
    @Test @DisplayName("pedido nativo recusado fora de NATIVE_SIC (EXTERNAL_LINK e NONE nao nascem no ZiipSan)")
    void nativeOnlyInNativeSic() {
        assertThatThrownBy(() -> request(LaiMode.EXTERNAL_LINK, LaiDeadlines.FACTORY))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request(LaiMode.NONE, LaiDeadlines.FACTORY))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("resposta, janela de recurso de 10 dias e decisao do recurso em 5 — tudo pelo motor de prazos")
    void appealFlow() {
        var r = request(LaiMode.NATIVE_SIC, LaiDeadlines.FACTORY);
        r.answer(LocalDate.of(2026, 1, 20), AnswerOutcome.DENIED);
        assertThat(r.answeredWithinDeadline()).isTrue();
        assertThat(r.appealDeadline(DeadlineCountingRule.CALENDAR, Set.of()))
            .isEqualTo(LocalDate.of(2026, 1, 30));
        // recurso no ultimo dia da janela
        r.appeal(LocalDate.of(2026, 1, 30), DeadlineCountingRule.CALENDAR, Set.of());
        assertThat(r.appealDecisionDue()).isEqualTo(LocalDate.of(2026, 2, 4));
        r.decideAppeal(LocalDate.of(2026, 2, 3), AppealOutcome.GRANTED);
        assertThat(r.appealDecidedWithinDeadline()).isTrue();
        assertThat(r.appealOutcome()).isEqualTo(AppealOutcome.GRANTED);
    }
    @Test @DisplayName("recurso fora da janela, sem resposta ou contra deferimento integral e recusado")
    void appealRefusals() {
        var late = request(LaiMode.NATIVE_SIC, LaiDeadlines.FACTORY);
        late.answer(LocalDate.of(2026, 1, 20), AnswerOutcome.DENIED);
        assertThatThrownBy(() -> late.appeal(LocalDate.of(2026, 1, 31), DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
        var unanswered = request(LaiMode.NATIVE_SIC, LaiDeadlines.FACTORY);
        assertThatThrownBy(() -> unanswered.appeal(LocalDate.of(2026, 1, 10), DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalStateException.class);
        var granted = request(LaiMode.NATIVE_SIC, LaiDeadlines.FACTORY);
        granted.answer(LocalDate.of(2026, 1, 20), AnswerOutcome.GRANTED);
        assertThatThrownBy(() -> granted.appeal(LocalDate.of(2026, 1, 21), DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("prazos reduzidos do tenant (dentro da lei) encurtam resposta e decisao; atraso e detectado")
    void reducedDeadlinesAndOverdue() {
        var r = request(LaiMode.NATIVE_SIC, new LaiDeadlines(10, 5, 5, 2));
        assertThat(r.currentResponseDue()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(r.responseOverdueOn(LocalDate.of(2026, 1, 16))).isTrue();
        assertThat(r.responseOverdueOn(LocalDate.of(2026, 1, 15))).isFalse();
        r.answer(LocalDate.of(2026, 1, 16), AnswerOutcome.PARTIALLY_GRANTED);
        assertThat(r.answeredWithinDeadline()).isFalse();
    }
}
