package br.com.ziipsan.compliance.lgpd.privacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prazo de 15 dias da declaração completa (LGPD, art. 19, II). Contas à mão:
 * abertura em 2026-11-07 + 15 dias corridos = 2026-11-22; janela de alerta de 3 dias
 * começa em 2026-11-19.
 */
class DsrDeadlineTest {

    private static final LocalDate OPENED = LocalDate.of(2026, 11, 7);
    private static final LocalDate DUE = LocalDate.of(2026, 11, 22);

    @Test
    @DisplayName("prazo é abertura + 15 dias (art. 19, II)")
    void deadlineIsOpeningPlus15Days() {
        DsrDeadline deadline = DsrDeadline.fromOpening(OPENED);
        assertThat(deadline.dueDate()).isEqualTo(DUE);
        assertThat(DsrDeadline.FULL_DECLARATION_DAYS).isEqualTo(15);
    }

    @Test
    @DisplayName("prazo diferente de 15 dias é recusado na construção")
    void nonLegalDeadlineRefused() {
        assertThatThrownBy(() -> new DsrDeadline(OPENED, OPENED.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("art. 19, II");
    }

    @Test
    @DisplayName("alerta antes do vencimento: dentro da janela, inclusive no dia do prazo")
    void alertBeforeDue() {
        DsrDeadline deadline = DsrDeadline.fromOpening(OPENED);
        assertThat(deadline.isAlerting(LocalDate.of(2026, 11, 18), 3, false)).isFalse();
        assertThat(deadline.isAlerting(LocalDate.of(2026, 11, 19), 3, false)).isTrue();
        assertThat(deadline.isAlerting(DUE, 3, false)).isTrue();
        assertThat(deadline.isAlerting(LocalDate.of(2026, 11, 23), 3, false)).isFalse();
        assertThat(deadline.isAlerting(LocalDate.of(2026, 11, 20), 3, true)).isFalse();
    }

    @Test
    @DisplayName("janela de alerta inválida é recusada (o plano não fixa a janela)")
    void invalidAlertWindowRefused() {
        DsrDeadline deadline = DsrDeadline.fromOpening(OPENED);
        assertThatThrownBy(() -> deadline.isAlerting(OPENED, 0, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> deadline.isAlerting(OPENED, 15, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("vencido só depois do prazo e sem conclusão")
    void overdueOnlyAfterDueAndUncompleted() {
        DsrDeadline deadline = DsrDeadline.fromOpening(OPENED);
        assertThat(deadline.isOverdue(DUE, false)).isFalse();
        assertThat(deadline.isOverdue(LocalDate.of(2026, 11, 23), false)).isTrue();
        assertThat(deadline.isOverdue(LocalDate.of(2026, 11, 23), true)).isFalse();
    }
}
