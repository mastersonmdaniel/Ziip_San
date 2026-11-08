package br.com.ziipsan.commercial.billing.servicecatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova do G2 (correcao pos-reprovacao da ED-35): a porta do atendimento,
 * implementada por este adaptador, devolve EXATAMENTE as etapas do fluxo
 * encadeado real da ED-34 (NewConnectionWorkflow), na ordem \u2014 com e sem a
 * etapa de esgoto simultaneo.
 */
class NewConnectionWorkflowSacAdapterTest {

    private final NewConnectionWorkflowSacAdapter adapter = new NewConnectionWorkflowSacAdapter();

    @Test @DisplayName("sem rede de esgoto: fluxo da ED-34 sem SEWER_CONNECTION")
    void stepsWithoutSewer() {
        assertThat(adapter.chainedStepCodes(false, true, 24, 72)).containsExactly(
                "REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE",
                "INSPECTION", "EXECUTION", "ACTIVATION", "CONTRACT_DELIVERY",
                "CUSTOMER_LINK", "FIRST_READING");
    }

    @Test @DisplayName("com rede e parametro exigindo: SEWER_CONNECTION entra antes da ativacao")
    void stepsWithSewer() {
        assertThat(adapter.chainedStepCodes(true, true, 24, 72)).containsExactly(
                "REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE",
                "INSPECTION", "EXECUTION", "SEWER_CONNECTION", "ACTIVATION",
                "CONTRACT_DELIVERY", "CUSTOMER_LINK", "FIRST_READING");
    }
}
