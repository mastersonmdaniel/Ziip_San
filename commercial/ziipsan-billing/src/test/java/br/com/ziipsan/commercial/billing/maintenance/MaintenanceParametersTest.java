package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** P2.20: uma prova por chave de parametro da manutencao, inclusive o padrao declarado e o erro explicito. */
class MaintenanceParametersTest {

    @Test
    @DisplayName("bill.review_effects ausente: padrao SUSPEND_ALL do aceite (todos os efeitos)")
    void reviewEffectsDefault() {
        MaintenanceParameters p = MaintenanceParameters.fromValues(Map.of());
        assertThat(p.reviewEffects()).containsExactlyInAnyOrderElementsOf(
                java.util.EnumSet.allOf(MaintenanceParameters.ReviewEffect.class));
    }

    @Test
    @DisplayName("bill.review_effects como lista: so os efeitos listados")
    void reviewEffectsList() {
        MaintenanceParameters p = MaintenanceParameters.fromValues(
                Map.of(MaintenanceParameters.REVIEW_EFFECTS, "[\"COLLECTION\", \"AUTO_DEBIT\"]"));
        assertThat(p.reviewEffects()).containsExactlyInAnyOrder(
                MaintenanceParameters.ReviewEffect.COLLECTION, MaintenanceParameters.ReviewEffect.AUTO_DEBIT);
    }

    @Test
    @DisplayName("bill.review_effects com efeito fora do catalogo: erro explicito")
    void reviewEffectsUnknown() {
        MaintenanceParameters p = MaintenanceParameters.fromValues(
                Map.of(MaintenanceParameters.REVIEW_EFFECTS, "[\"FREEZE\"]"));
        assertThatThrownBy(p::reviewEffects).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bill.review_effects");
    }

    @Test
    @DisplayName("bill.review_max_days ausente: MissingParameterException (sem padrao inventado)")
    void reviewMaxDaysMissing() {
        MaintenanceParameters p = MaintenanceParameters.fromValues(Map.of());
        assertThatThrownBy(p::reviewMaxDays).isInstanceOf(MissingParameterException.class);
    }

    @Test
    @DisplayName("bill.due_change_*: ausentes sao erro explicito; presentes leem o inteiro")
    void dueChangeParams() {
        MaintenanceParameters empty = MaintenanceParameters.fromValues(Map.of());
        assertThatThrownBy(empty::dueChangeMaxPerYear).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(empty::dueChangeMinDaysBeforeDue).isInstanceOf(MissingParameterException.class);
        MaintenanceParameters p = MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.DUE_CHANGE_MAX_PER_YEAR, "2",
                MaintenanceParameters.DUE_CHANGE_MIN_DAYS_BEFORE_DUE, "5"));
        assertThat(p.dueChangeMaxPerYear()).isEqualTo(2);
        assertThat(p.dueChangeMinDaysBeforeDue()).isEqualTo(5);
    }

    @Test
    @DisplayName("service_type.internal_approval: REQUIRED/NONE vencem a fabrica; sem entrada vale a fabrica")
    void internalApproval() {
        MaintenanceParameters p = MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.INTERNAL_APPROVAL,
                "{\"BILL_CANCELLATION\": \"REQUIRED\", \"BILL_REVIEW\": \"NONE\"}"));
        assertThat(p.approvalRequired(BillOrderType.BILL_CANCELLATION, false)).isTrue();
        assertThat(p.approvalRequired(BillOrderType.BILL_REVIEW, true)).isFalse();
        assertThat(p.approvalRequired(BillOrderType.BILL_RECTIFICATION, true)).isTrue();
        assertThat(p.approvalRequired(BillOrderType.BILL_RECTIFICATION, false)).isFalse();
        MaintenanceParameters none = MaintenanceParameters.fromValues(Map.of());
        assertThat(none.approvalRequired(BillOrderType.BILL_CANCELLATION, true)).isTrue();
    }

    @Test
    @DisplayName("bill.rectification_limits_against_user: objeto sem teto devolve null; com teto, o numero")
    void rectificationLimits() {
        assertThat(MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, "{}"))
                .rectificationMaxPerUnitAndYear()).isNull();
        assertThat(MaintenanceParameters.fromValues(Map.of(
                MaintenanceParameters.RECTIFICATION_LIMITS_AGAINST_USER, "{\"maxPerUnitAndYear\": 3}"))
                .rectificationMaxPerUnitAndYear()).isEqualTo(3);
        assertThatThrownBy(() -> MaintenanceParameters.fromValues(Map.of()).rectificationMaxPerUnitAndYear())
                .isInstanceOf(MissingParameterException.class);
    }
}
