package br.com.ziipsan.commercial.billing.discharge;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.content.LegalMessageCatalog;
import br.com.ziipsan.commercial.billing.content.LegalMessageEngine;
import br.com.ziipsan.commercial.billing.domain.AnnualDischargeStatementEntity;
import br.com.ziipsan.commercial.billing.parameters.StatementParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Servico da declaracao anual (P2.24): o disparo calculado aqui entra no
 * contexto da P2.32 SOMENTE no canal de espaco na fatura; nos demais
 * canais a declaracao nao ocupa o espaco da fatura. A prova de que a
 * mensagem legal dispara e feita com o motor real da P2.32.
 */
class DefaultAnnualDischargeServiceTest {

    private final AnnualDischargeService service = new DefaultAnnualDischargeService();

    private static List<AnnualBillFact> settledYear2025() {
        List<AnnualBillFact> facts = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate ref = LocalDate.of(2025, month, 1);
            facts.add(new AnnualBillFact(UUID.randomUUID(), ref, BillStatus.PAID,
                    ref.plusMonths(1).withDayOfMonth(10)));
        }
        return facts;
    }

    private static AnnualDischargeService.DischargeRequest request(List<AnnualBillFact> history,
                                                                   Map<String, String> params) {
        return new AnnualDischargeService.DischargeRequest(
                UUID.randomUUID(), LocalDate.of(2026, 5, 1), history, Set.of(), params);
    }

    private static LegalMessageEngine.EmissionContext baseContext() {
        return new LegalMessageEngine.EmissionContext(
                LocalDate.of(2026, 5, 10), null, null, 3, false, true, null, null, Set.of());
    }

    @Test
    @DisplayName("espaco na fatura: contexto marcado dispara a mensagem legal da P2.32 e preserva os demais campos")
    void invoiceSpaceMarksContextAndLegalMessageFires() {
        AnnualDischargeService.DischargeRequest req = request(settledYear2025(), Map.of());
        assertThat(service.dueStatements(req)).hasSize(1);
        assertThat(service.channel(req)).isEqualTo(StatementParameters.StatementChannel.INVOICE_SPACE);

        LegalMessageEngine.EmissionContext enriched = service.contextWithDischarge(baseContext(), req);
        assertThat(enriched.annualDischargeDue()).isTrue();
        // Os demais campos sao os do contexto base, sem alteracao.
        assertThat(enriched.issueDate()).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat(enriched.waterQualityAvailable()).isTrue();

        assertThat(LegalMessageEngine.select(enriched))
                .extracting(s -> s.definition().messageKey())
                .contains(LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION,
                        LegalMessageCatalog.WATER_QUALITY_BLOCK);
    }

    @Test
    @DisplayName("sem declaracao devida (debito em aberto), o contexto nao e marcado")
    void noDueStatementLeavesContextUnmarked() {
        List<AnnualBillFact> facts = settledYear2025();
        facts.set(5, new AnnualBillFact(UUID.randomUUID(), LocalDate.of(2025, 6, 1),
                BillStatus.NORMAL, null));
        AnnualDischargeService.DischargeRequest req = request(facts, Map.of());
        assertThat(service.dueStatements(req)).isEmpty();
        assertThat(service.contextWithDischarge(baseContext(), req).annualDischargeDue()).isFalse();
    }

    @Test
    @DisplayName("canal de documento anexo: a declaracao vence, mas NAO ocupa o espaco na fatura")
    void annexChannelDoesNotMarkBillContext() {
        AnnualDischargeService.DischargeRequest req = request(settledYear2025(),
                Map.of(StatementParameters.STATEMENT_CHANNEL, "\"ANNEX_DOCUMENT\""));
        assertThat(service.channel(req)).isEqualTo(StatementParameters.StatementChannel.ANNEX_DOCUMENT);
        assertThat(service.dueStatements(req)).hasSize(1);
        LegalMessageEngine.EmissionContext enriched = service.contextWithDischarge(baseContext(), req);
        assertThat(enriched.annualDischargeDue()).isFalse();
        assertThat(LegalMessageEngine.select(enriched))
                .extracting(s -> s.definition().messageKey())
                .doesNotContain(LegalMessageCatalog.ANNUAL_DISCHARGE_DECLARATION);
    }

    @Test
    @DisplayName("registro da emissao: espaco na fatura exige a conta; portal admite registro sem conta")
    void statementRecordRules() {
        AnnualDischargeRule.DischargeDecision decision = service.dueStatements(
                request(settledYear2025(), Map.of())).get(0);
        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID billId = UUID.randomUUID();

        assertThatThrownBy(() -> service.newStatementRecord(tenantId, unitId, decision,
                StatementParameters.StatementChannel.INVOICE_SPACE, null, LocalDate.of(2026, 5, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billId");

        AnnualDischargeStatementEntity onBill = service.newStatementRecord(tenantId, unitId, decision,
                StatementParameters.StatementChannel.INVOICE_SPACE, billId, LocalDate.of(2026, 5, 10));
        assertThat(onBill.getReferenceYear()).isEqualTo(2025);
        assertThat(onBill.getChannel()).isEqualTo("INVOICE_SPACE");
        assertThat(onBill.getEmissionTrigger()).isEqualTo("MAY_CYCLE");
        assertThat(onBill.getIssuedBillId()).isEqualTo(billId);
        assertThat(onBill.getUnitId()).isEqualTo(unitId);
        assertThat(onBill.getTenantId()).isEqualTo(tenantId);

        AnnualDischargeStatementEntity onPortal = service.newStatementRecord(tenantId, unitId, decision,
                StatementParameters.StatementChannel.PORTAL, null, LocalDate.of(2026, 5, 10));
        assertThat(onPortal.getChannel()).isEqualTo("PORTAL");
        assertThat(onPortal.getIssuedBillId()).isNull();
    }
}
