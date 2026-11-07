package br.com.ziipsan.core.cutoff;

import br.com.ziipsan.core.api.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parametros cut.*/rec.* da P3.4: um teste por regra de validacao e por
 * opcao, incluindo os formatos REAIS que os perfis da P2.31 carregam.
 */
class CutoffParametersTest {

    @Test
    @DisplayName("cut.min_notice_days: abaixo de 30 e recusado (piso legal); 30 e acima passam; ausente vale 30")
    void minNoticeFloor() {
        assertThatThrownBy(() -> CutoffParameters.parse(Map.of("cut.min_notice_days", "29")))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(CutoffParameters.parse(Map.of("cut.min_notice_days", "30")).effectiveMinNoticeDays())
                .isEqualTo(30);
        assertThat(CutoffParameters.parse(Map.of("cut.min_notice_days", "45")).effectiveMinNoticeDays())
                .isEqualTo(45);
        assertThat(CutoffParameters.parse(Map.of()).effectiveMinNoticeDays()).isEqualTo(30);
    }

    @Test
    @DisplayName("cut.protected_classes: remover classe da lista minima travada e recusado (classe c)")
    void protectedClassesOnlyAdd() {
        assertThatThrownBy(() -> CutoffParameters.parse(Map.of(
                "cut.protected_classes", "[\"ESSENTIAL_USER\",\"HEALTH_DEPENDENT\"]")))
                .isInstanceOf(ApiException.Validation.class);
        var full = CutoffParameters.parse(Map.of(
                "cut.protected_classes", "[\"ESSENTIAL_USER\",\"HEALTH_DEPENDENT\",\"SOCIAL_TARIFF\"]"));
        assertThat(full.protectedClasses()).containsExactlyInAnyOrder(
                CutoffEnums.ProtectedClass.ESSENTIAL_USER, CutoffEnums.ProtectedClass.HEALTH_DEPENDENT,
                CutoffEnums.ProtectedClass.SOCIAL_TARIFF);
        // Ausente: vale a lista minima travada.
        assertThat(CutoffParameters.parse(Map.of()).protectedClasses())
                .isEqualTo(CutoffParameters.PROTECTED_CLASSES_MINIMUM);
    }

    @Test
    @DisplayName("cut.reasons: inadimplencia nunca sai das vedacoes; outro motivo exige norma citada")
    void reasonRules() {
        assertThatThrownBy(() -> CutoffParameters.parse(Map.of("cut.reasons",
                "{\"DEFAULT\":{\"followsTemporalBans\":false,\"normCitation\":\"X\"}}")))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> CutoffParameters.parse(Map.of("cut.reasons",
                "{\"FRAUD\":{\"followsTemporalBans\":false}}")))
                .isInstanceOf(ApiException.Validation.class);
        var values = CutoffParameters.parse(Map.of("cut.reasons",
                "{\"FRAUD\":{\"followsTemporalBans\":false,\"normCitation\":\"RN da ERI\"}}"));
        assertThat(values.reasonFollowsTemporalBans(CutoffEnums.Reason.FRAUD)).isFalse();
        assertThat(values.reasonFollowsTemporalBans(CutoffEnums.Reason.DEFAULT)).isTrue();
        // Padrao de fabrica: todos seguem, exceto a emergencia tecnica.
        var factory = CutoffParameters.parse(Map.of());
        assertThat(factory.reasonFollowsTemporalBans(CutoffEnums.Reason.RISK)).isTrue();
        assertThat(factory.reasonFollowsTemporalBans(CutoffEnums.Reason.TECHNICAL_EMERGENCY)).isFalse();
    }

    @Test
    @DisplayName("rec.sla_hours no formato dos perfis da P2.31 (ARIS/ARESC/AGIR) e no formato nao mapeado da AGR")
    void slaFormatsOfProfiles() {
        var aris = CutoffParameters.parse(Map.of("rec.sla_hours",
                "{\"normalHours\":48,\"urgentHours\":4,\"urgentOptional\":true,\"undueHours\":4}"));
        assertThat(aris.slaHours().normalHours()).isEqualTo(48);
        assertThat(aris.slaHours().hoursFor(false, false)).isEqualTo(48);
        assertThat(aris.slaHours().hoursFor(true, false)).isEqualTo(4);
        assertThat(aris.slaHours().hoursFor(false, true)).isEqualTo(4);
        // AGR-Tubarao: {reconnectionHours:24} sem discriminacao -- nao se
        // adivinha o mapeamento; a hora pedida e erro explicito.
        var agr = CutoffParameters.parse(Map.of("rec.sla_hours", "{\"reconnectionHours\":24}"));
        assertThatThrownBy(() -> agr.slaHours().hoursFor(false, false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rec.undue_cut_compensation: valores reais dos perfis (ARIS, ARESC, AGIR) e o padrao sem perfil")
    void undueFormulaFormatsOfProfiles() {
        assertThat(CutoffParameters.parse(Map.of(
                "rec.undue_cut_compensation", "\"RECONNECTION_4H_NO_CHARGE\"")).undueCompensationFormula())
                .isEqualTo(CutoffEnums.UndueCompensationFormula.RECONNECTION_4H_NO_CHARGE);
        assertThat(CutoffParameters.parse(Map.of("rec.undue_cut_compensation",
                "\"GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION\""))
                .undueCompensationFormula())
                .isEqualTo(CutoffEnums.UndueCompensationFormula
                        .GREATER_OF_2X_URGENT_FEE_AND_20PCT_FIRST_INVOICE_AFTER_RECONNECTION);
        assertThat(CutoffParameters.parse(Map.of("rec.undue_cut_compensation",
                "\"GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE\"")).undueCompensationFormula())
                .isEqualTo(CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE);
        // Sem perfil: a formula ARESC/AGIR (PARAMETROS-DO-TENANT).
        assertThat(CutoffParameters.parse(Map.of()).undueCompensationFormula())
                .isEqualTo(CutoffEnums.UndueCompensationFormula.GREATER_OF_2X_URGENT_FEE_AND_20PCT_LAST_INVOICE);
    }

    @Test
    @DisplayName("rec.release_condition: as tres opcoes e o padrao MOTIVATING_DEBT_PAID_OR_AGREED")
    void releaseConditionOptions() {
        for (var option : CutoffEnums.ReleaseCondition.values()) {
            var values = CutoffParameters.parse(Map.of(
                    "rec.release_condition", "\"" + option.name() + "\""));
            assertThat(values.releaseCondition()).isEqualTo(option);
        }
        assertThat(CutoffParameters.parse(Map.of()).releaseCondition())
                .isEqualTo(CutoffEnums.ReleaseCondition.MOTIVATING_DEBT_PAID_OR_AGREED);
        assertThatThrownBy(() -> CutoffParameters.parse(Map.of(
                "rec.release_condition", "\"ANY_PAYMENT\"")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("rec.auto_order_on_payment: padrao sim; nao aceito desliga")
    void autoOrderOnPayment() {
        assertThat(CutoffParameters.parse(Map.of()).autoOrderOnPayment()).isTrue();
        assertThat(CutoffParameters.parse(Map.of("rec.auto_order_on_payment", "false"))
                .autoOrderOnPayment()).isFalse();
    }

    @Test
    @DisplayName("cut.social_tariff_allowed ausente NAO permite; cut.sewer_allowed ausente vale nao")
    void absentBooleansAreExplicit() {
        var values = CutoffParameters.parse(Map.of());
        assertThat(values.socialTariffCutAllowed()).isFalse();
        assertThat(values.sewerAllowed()).isFalse();
        assertThat(CutoffParameters.parse(Map.of("cut.social_tariff_allowed", "true"))
                .socialTariffCutAllowed()).isTrue();
    }
}
