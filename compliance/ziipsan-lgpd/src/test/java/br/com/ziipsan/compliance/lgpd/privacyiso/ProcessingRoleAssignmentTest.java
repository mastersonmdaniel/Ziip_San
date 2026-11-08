package br.com.ziipsan.compliance.lgpd.privacyiso;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Aceite da ED-74: papel de controlador ou operador por tratamento, só com a norma ligada. */
class ProcessingRoleAssignmentTest {

    private static Iso27701Activation on() {
        return Iso27701Activation.enabled("2025", Iso27701Activation.EditionUse.TARGET,
                "escopo", "sgpi");
    }

    @Test
    @DisplayName("controlador registrado atrai a audiência de controlador do mapa")
    void controllerRole() {
        var a = ProcessingRoleAssignment.register(on(), "FATURAMENTO",
                ProcessingRoleAssignment.PrivacyRole.CONTROLLER, "");
        assertThat(a.audience()).isEqualTo(PrivacyClauseAssessment.Audience.CONTROLLER);
    }

    @Test
    @DisplayName("operador registrado atrai a audiência de operador do mapa")
    void processorRole() {
        var a = ProcessingRoleAssignment.register(on(), "LEITURA-TERCEIRIZADA",
                ProcessingRoleAssignment.PrivacyRole.PROCESSOR, "controlador: tenant contratante");
        assertThat(a.audience()).isEqualTo(PrivacyClauseAssessment.Audience.PROCESSOR);
    }

    @Test
    @DisplayName("com a norma desligada, nenhum papel nasce")
    void disabledRefuses() {
        assertThatThrownBy(() -> ProcessingRoleAssignment.register(
                Iso27701Activation.disabled(), "FATURAMENTO",
                ProcessingRoleAssignment.PrivacyRole.CONTROLLER, ""))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("código do registro é obrigatório e respeita o teto da V23_1 (60)")
    void codeValidation() {
        assertThatThrownBy(() -> ProcessingRoleAssignment.register(on(), " ",
                ProcessingRoleAssignment.PrivacyRole.CONTROLLER, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessingRoleAssignment.register(on(), "X".repeat(61),
                ProcessingRoleAssignment.PrivacyRole.CONTROLLER, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem papel declarado, o registro não nasce")
    void roleRequired() {
        assertThatThrownBy(() -> ProcessingRoleAssignment.register(on(), "FATURAMENTO", null, ""))
                .isInstanceOf(NullPointerException.class);
    }
}
