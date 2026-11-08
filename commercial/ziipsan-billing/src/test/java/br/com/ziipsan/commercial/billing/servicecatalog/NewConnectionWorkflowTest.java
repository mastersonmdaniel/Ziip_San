package br.com.ziipsan.commercial.billing.servicecatalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewConnectionWorkflowTest {

    @Test @DisplayName("fluxo completo da ligacao nova, na ordem, sem pular etapa")
    void fullFlow() {
        var def = NewConnectionWorkflow.definition(false, true, 72, 120);
        var p = new WorkflowDefinition.Progress(def);
        assertThatThrownBy(() -> p.complete("DOCUMENTS")).isInstanceOf(IllegalStateException.class);
        for (String step : List.of("REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE",
                "INSPECTION", "EXECUTION", "ACTIVATION", "CONTRACT_DELIVERY", "CUSTOMER_LINK", "FIRST_READING"))
            p.complete(step);
        assertThat(p.finished()).isTrue();
    }

    @Test @DisplayName("esgoto simultaneo: com rede e parametro ligado a etapa entra e trava a ativacao; sem rede nao entra")
    void sewerSimultaneous() {
        var withSewer = NewConnectionWorkflow.definition(true, true, null, null);
        assertThat(withSewer.steps()).extracting(WorkflowDefinition.Step::code).contains("SEWER_CONNECTION");
        var p = new WorkflowDefinition.Progress(withSewer);
        for (String s : List.of("REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE", "INSPECTION", "EXECUTION"))
            p.complete(s);
        assertThat(p.canComplete("ACTIVATION")).isFalse();
        assertThat(NewConnectionWorkflow.definition(false, true, null, null).steps())
                .extracting(WorkflowDefinition.Step::code).doesNotContain("SEWER_CONNECTION");
        assertThat(NewConnectionWorkflow.definition(true, false, null, null).steps())
                .extracting(WorkflowDefinition.Step::code).doesNotContain("SEWER_CONNECTION");
    }

    @Test @DisplayName("orcamento soma itens resolvidos por bitola/pavimento/categoria na tabela vigente")
    void quoteSums() {
        var table = new PriceTable(List.of(
                new ServicePriceItem("LIGACAO_20MM_ASFALTO_RES", "Ligacao 20mm asfalto residencial", RevenueNature.TARIFF,
                        new BigDecimal("800.00"), LocalDate.of(2026, 1, 1), null, false, null, null),
                new ServicePriceItem("LIGACAO_ESGOTO_RES", "Ligacao esgoto residencial", RevenueNature.TARIFF,
                        new BigDecimal("600.00"), LocalDate.of(2026, 1, 1), null, false, null, null)),
                List.of(new MaterialPriceItem("HIDROMETRO_20MM", "Hidrometro 20mm", "UN", new BigDecimal("250.00"),
                        LocalDate.of(2026, 1, 1), null)));
        var q = NewConnectionWorkflow.quote(table, LocalDate.of(2026, 3, 10),
                List.of(new ServiceChargeOnClose.MaterialUse("LIGACAO_20MM_ASFALTO_RES", BigDecimal.ONE),
                        new ServiceChargeOnClose.MaterialUse("LIGACAO_ESGOTO_RES", BigDecimal.ONE)),
                List.of(new ServiceChargeOnClose.MaterialUse("HIDROMETRO_20MM", BigDecimal.ONE)));
        assertThat(q.total()).isEqualByComparingTo("1650.00");
        assertThat(q.lines()).hasSize(3);
    }

    @Test @DisplayName("TR-300: dias minimos de cobranca prevalecem sobre o periodo real curto")
    void minimumBillableDays() {
        assertThat(NewConnectionWorkflow.billableDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 6), 10)).isEqualTo(10);
        assertThat(NewConnectionWorkflow.billableDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 21), 10)).isEqualTo(20);
    }

    @Test @DisplayName("fluxo generico: ciclo e dependencia inexistente recusados")
    void workflowValidation() {
        assertThatThrownBy(() -> new WorkflowDefinition(List.of(
                new WorkflowDefinition.Step("A", "a", List.of("B"), null),
                new WorkflowDefinition.Step("B", "b", List.of("A"), null)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowDefinition(List.of(
                new WorkflowDefinition.Step("A", "a", List.of("ZZ"), null)))).isInstanceOf(IllegalArgumentException.class);
    }
}
