package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Selecao minima do aviso (e do documento de cobranca) da P3.4: um
 * teste por motivo de nao geracao e o caso ADASA calculado a mao
 * (minimo de 60 dias e teto de 120 dias de idade do debito; avaliacao
 * em 16/11/2026).
 */
class NoticeSelectionRuleTest {

    private static final LocalDate EVAL = LocalDate.of(2026, 11, 16);
    private static final UUID UNIT = UUID.randomUUID();

    private static CutoffParameters.Values params(Map<String, String> extra) {
        return CutoffParameters.parse(extra);
    }

    private static NoticeSelectionRule.Debt debt(String due, String amount) {
        return new NoticeSelectionRule.Debt(LocalDate.parse(due), new BigDecimal(amount),
                NoticeSelectionRule.DebtStatus.OPEN);
    }

    private static NoticeSelectionRule.UnitFacts facts(List<NoticeSelectionRule.Debt> debts) {
        return new NoticeSelectionRule.UnitFacts(UNIT, CutoffEnums.ConnectionStatus.ACTIVE,
                Set.of(), false, debts);
    }

    @Test
    @DisplayName("seleciona: debito vencido dentro das idades, total somado, mais antigo primeiro")
    void selects() {
        var result = NoticeSelectionRule.evaluate(
                facts(List.of(debt("2026-10-01", "100.00"), debt("2026-09-01", "50.00"))),
                params(Map.of()), EVAL);
        assertThat(result.selected()).isTrue();
        assertThat(result.totalAmount()).isEqualByComparingTo("150.00");
        assertThat(result.motivatingDebts().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.oldestDebtAgeDays()).isEqualTo(76L); // 01/09 -> 16/11
    }

    @Test
    @DisplayName("sem debito vencido em aberto: NO_OVERDUE_DEBT (quitado, parcelado e a vencer nao motivam)")
    void noOverdueDebt() {
        var settled = new NoticeSelectionRule.Debt(LocalDate.of(2026, 9, 1), new BigDecimal("10"),
                NoticeSelectionRule.DebtStatus.SETTLED);
        var agreed = new NoticeSelectionRule.Debt(LocalDate.of(2026, 9, 1), new BigDecimal("10"),
                NoticeSelectionRule.DebtStatus.IN_INSTALLMENT_AGREEMENT);
        var notDue = debt("2026-12-01", "10");
        var result = NoticeSelectionRule.evaluate(facts(List.of(settled, agreed, notDue)),
                params(Map.of()), EVAL);
        assertThat(result.selected()).isFalse();
        assertThat(result.nonGenerationReason()).isEqualTo(CutoffEnums.NonGenerationReason.NO_OVERDUE_DEBT);
    }

    @Test
    @DisplayName("ligacao nao ativa: CONNECTION_NOT_ACTIVE")
    void connectionNotActive() {
        var cut = new NoticeSelectionRule.UnitFacts(UNIT, CutoffEnums.ConnectionStatus.CUT,
                Set.of(), false, List.of(debt("2026-09-01", "10")));
        var result = NoticeSelectionRule.evaluate(cut, params(Map.of()), EVAL);
        assertThat(result.nonGenerationReason())
                .isEqualTo(CutoffEnums.NonGenerationReason.CONNECTION_NOT_ACTIVE);
    }

    @Test
    @DisplayName("conta em revisao ou contestada: BILL_UNDER_REVIEW_OR_CONTESTED")
    void billUnderReview() {
        var underReview = new NoticeSelectionRule.UnitFacts(UNIT, CutoffEnums.ConnectionStatus.ACTIVE,
                Set.of(), true, List.of(debt("2026-09-01", "10")));
        var result = NoticeSelectionRule.evaluate(underReview, params(Map.of()), EVAL);
        assertThat(result.nonGenerationReason())
                .isEqualTo(CutoffEnums.NonGenerationReason.BILL_UNDER_REVIEW_OR_CONTESTED);
    }

    @Test
    @DisplayName("usuario essencial e dependente de saude: PROTECTED_CLASS")
    void protectedClasses() {
        for (var protectedClass : List.of(CutoffEnums.ProtectedClass.ESSENTIAL_USER,
                CutoffEnums.ProtectedClass.HEALTH_DEPENDENT)) {
            var unit = new NoticeSelectionRule.UnitFacts(UNIT, CutoffEnums.ConnectionStatus.ACTIVE,
                    Set.of(protectedClass), false, List.of(debt("2026-09-01", "10")));
            var result = NoticeSelectionRule.evaluate(unit, params(Map.of()), EVAL);
            assertThat(result.nonGenerationReason())
                    .isEqualTo(CutoffEnums.NonGenerationReason.PROTECTED_CLASS);
        }
    }

    @Test
    @DisplayName("tarifa social: vedada sem o parametro da ERI; permitida com cut.social_tariff_allowed")
    void socialTariff() {
        var unit = new NoticeSelectionRule.UnitFacts(UNIT, CutoffEnums.ConnectionStatus.ACTIVE,
                Set.of(CutoffEnums.ProtectedClass.SOCIAL_TARIFF), false, List.of(debt("2026-09-01", "10")));
        var blocked = NoticeSelectionRule.evaluate(unit, params(Map.of()), EVAL);
        assertThat(blocked.nonGenerationReason())
                .isEqualTo(CutoffEnums.NonGenerationReason.SOCIAL_TARIFF_PROTECTED);
        var allowed = NoticeSelectionRule.evaluate(unit,
                params(Map.of("cut.social_tariff_allowed", "true")), EVAL);
        assertThat(allowed.selected()).isTrue();
    }

    @Test
    @DisplayName("valor abaixo do minimo: DEBT_BELOW_MINIMUM; no minimo exato, seleciona")
    void belowMinimumAmount() {
        var p = params(Map.of("cut.min_debt_amount", "100.00"));
        var below = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-09-01", "99.99"))), p, EVAL);
        assertThat(below.nonGenerationReason()).isEqualTo(CutoffEnums.NonGenerationReason.DEBT_BELOW_MINIMUM);
        var exact = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-09-01", "100.00"))), p, EVAL);
        assertThat(exact.selected()).isTrue();
    }

    @Test
    @DisplayName("caso ADASA: 59 dias e cedo demais, 60 e 120 selecionam, 121 e preterito demais")
    void adasaCase() {
        var adasa = params(Map.of("cut.min_overdue_days", "60", "cut.max_debt_age_days", "120"));
        // 18/09/2026 -> 59 dias em 16/11/2026.
        var tooRecent = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-09-18", "10"))), adasa, EVAL);
        assertThat(tooRecent.nonGenerationReason()).isEqualTo(CutoffEnums.NonGenerationReason.DEBT_TOO_RECENT);
        // 17/09/2026 -> exatos 60 dias.
        var sixty = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-09-17", "10"))), adasa, EVAL);
        assertThat(sixty.selected()).isTrue();
        // 19/07/2026 -> exatos 120 dias (o teto inclui o limite).
        var ceiling = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-07-19", "10"))), adasa, EVAL);
        assertThat(ceiling.selected()).isTrue();
        // 18/07/2026 -> 121 dias: debito preterito fora da selecao.
        var tooOld = NoticeSelectionRule.evaluate(facts(List.of(debt("2026-07-18", "10"))), adasa, EVAL);
        assertThat(tooOld.nonGenerationReason()).isEqualTo(CutoffEnums.NonGenerationReason.DEBT_TOO_OLD);
    }

    @Test
    @DisplayName("teto ARIS de 120 dias: debito preterito e excluido, o restante motiva o aviso")
    void arisCeilingExcludesOldDebt() {
        var aris = params(Map.of("cut.max_debt_age_days", "120"));
        var result = NoticeSelectionRule.evaluate(
                facts(List.of(debt("2026-07-18", "999.00"), debt("2026-10-10", "40.00"))), aris, EVAL);
        assertThat(result.selected()).isTrue();
        assertThat(result.motivatingDebts()).hasSize(1);
        assertThat(result.totalAmount()).isEqualByComparingTo("40.00");
    }
}
