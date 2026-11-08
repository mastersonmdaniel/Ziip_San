package br.com.ziipsan.commercial.billing.central;

import br.com.ziipsan.commercial.billing.special.SpecialSituationParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ED-22: agrupadora com codigo unico, vinculo por OS, modos fiscais e usuario avulso (POC-51). */
class CentralBillingAndCasualTest {

    private static final UUID CENTRALIZER = UUID.randomUUID();
    private static final UUID U1 = UUID.randomUUID();
    private static final UUID U2 = UUID.randomUUID();
    private static final UUID U3 = UUID.randomUUID();

    private static SpecialSituationParameters params(String mode) {
        return SpecialSituationParameters.fromValues(
                Map.of(SpecialSituationParameters.CENTRAL_INVOICE_MODE, "\"" + mode + "\""));
    }

    private static CentralBilling.CentralizerLink link(UUID unit, YearMonth start, YearMonth end) {
        return new CentralBilling.CentralizerLink(UUID.randomUUID(), CENTRALIZER, unit,
                UUID.randomUUID(), start, end);
    }

    @Test
    @DisplayName("vinculo sem OS CENTRAL_BILLING_LINK e recusado")
    void linkNeedsOs() {
        assertThatThrownBy(() -> new CentralBilling.CentralizerLink(UUID.randomUUID(), CENTRALIZER, U1,
                null, YearMonth.of(2026, 1), null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CENTRAL_BILLING_LINK");
    }

    @Test
    @DisplayName("agrupadora: so unidades vinculadas na competencia; total 100+200=300,00; unidade nao vinculada fica fora")
    void groupedTotalAndFilter() {
        List<CentralBilling.CentralizerLink> links = List.of(
                link(U1, YearMonth.of(2026, 1), null), link(U2, YearMonth.of(2026, 1), null),
                link(U3, YearMonth.of(2026, 7), null));
        List<CentralBilling.MemberBill> bills = List.of(
                new CentralBilling.MemberBill(UUID.randomUUID(), U1, new BigDecimal("100.00")),
                new CentralBilling.MemberBill(UUID.randomUUID(), U2, new BigDecimal("200.00")),
                new CentralBilling.MemberBill(UUID.randomUUID(), U3, new BigDecimal("50.00")));
        CentralBilling.GroupedBill grouped = CentralBilling.group(
                CentralBilling.groupedCode(CENTRALIZER, YearMonth.of(2026, 6), 1),
                CENTRALIZER, YearMonth.of(2026, 6), links, bills, params("SINGLE"));
        assertThat(grouped.total()).isEqualByComparingTo("300.00");
        assertThat(grouped.members()).hasSize(2);
        assertThat(grouped.code()).startsWith("AGR-2026-06-");
        assertThat(grouped.fiscalDocumentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("documento fiscal por modo: SINGLE=1, PER_UNIT=n, NONE=0 (2 membros)")
    void fiscalModes() {
        List<CentralBilling.CentralizerLink> links = List.of(
                link(U1, YearMonth.of(2026, 1), null), link(U2, YearMonth.of(2026, 1), null));
        List<CentralBilling.MemberBill> bills = List.of(
                new CentralBilling.MemberBill(UUID.randomUUID(), U1, new BigDecimal("10.00")),
                new CentralBilling.MemberBill(UUID.randomUUID(), U2, new BigDecimal("20.00")));
        assertThat(CentralBilling.group("AGR-X1", CENTRALIZER, YearMonth.of(2026, 6), links, bills,
                params("PER_UNIT")).fiscalDocumentCount()).isEqualTo(2);
        assertThat(CentralBilling.group("AGR-X2", CENTRALIZER, YearMonth.of(2026, 6), links, bills,
                params("NONE")).fiscalDocumentCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("D1: GroupedBill direto recusa total nulo, total divergente da soma e fiscalDocumentCount incoerente")
    void groupedBillValidatesTotalAndFiscalCount() {
        List<CentralBilling.MemberBill> members = List.of(
                new CentralBilling.MemberBill(UUID.randomUUID(), U1, new BigDecimal("100.00")),
                new CentralBilling.MemberBill(UUID.randomUUID(), U2, new BigDecimal("200.00")));
        // Construcao valida direta (fora da fabrica): 100,00 + 200,00 = 300,00, SINGLE = 1 documento.
        CentralBilling.GroupedBill direct = new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, new BigDecimal("300.00"),
                SpecialSituationParameters.CentralBillingInvoiceMode.SINGLE, 1);
        assertThat(direct.total()).isEqualByComparingTo("300.00");
        assertThat(direct.fiscalDocumentCount()).isEqualTo(1);
        // Total nulo e recusado.
        assertThatThrownBy(() -> new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, null,
                SpecialSituationParameters.CentralBillingInvoiceMode.SINGLE, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("total");
        // Total divergente da soma dos membros (299,00 != 300,00) e recusado.
        assertThatThrownBy(() -> new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, new BigDecimal("299.00"),
                SpecialSituationParameters.CentralBillingInvoiceMode.SINGLE, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("diverge da soma");
        // fiscalDocumentCount incoerente com o modo: SINGLE com 2, PER_UNIT com 1 (2 membros), NONE com 1 e negativo.
        assertThatThrownBy(() -> new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, new BigDecimal("300.00"),
                SpecialSituationParameters.CentralBillingInvoiceMode.SINGLE, 2))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fiscalDocumentCount");
        assertThatThrownBy(() -> new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, new BigDecimal("300.00"),
                SpecialSituationParameters.CentralBillingInvoiceMode.PER_UNIT, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fiscalDocumentCount");
        assertThatThrownBy(() -> new CentralBilling.GroupedBill("AGR-D1", CENTRALIZER,
                YearMonth.of(2026, 6), members, new BigDecimal("300.00"),
                SpecialSituationParameters.CentralBillingInvoiceMode.NONE, -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fiscalDocumentCount");
    }

    @Test
    @DisplayName("codigo unico: sequencias diferentes geram codigos diferentes")
    void uniqueCode() {
        assertThat(CentralBilling.groupedCode(CENTRALIZER, YearMonth.of(2026, 6), 1))
                .isNotEqualTo(CentralBilling.groupedCode(CENTRALIZER, YearMonth.of(2026, 6), 2));
    }

    @Test
    @DisplayName("POC-51/avulso: tipo CASUAL nao recebe fatura regular; REGULAR recebe (ADR-0039)")
    void casualHasNoRegularBill() {
        assertThat(CasualUserUnit.canReceiveRegularBill(CasualUserUnit.UnitKind.CASUAL)).isFalse();
        assertThat(CasualUserUnit.canReceiveRegularBill(CasualUserUnit.UnitKind.REGULAR)).isTrue();
        assertThatThrownBy(() -> CasualUserUnit.requireRegularBillAllowed(CasualUserUnit.UnitKind.CASUAL))
                .isInstanceOf(IllegalArgumentException.class);
        CasualUserUnit.CasualUnit casual = new CasualUserUnit.CasualUnit(U1,
                CasualUserUnit.UnitKind.CASUAL, "Pagador avulso identificado");
        assertThat(casual.kind()).isEqualTo(CasualUserUnit.UnitKind.CASUAL);
        assertThatThrownBy(() -> new CasualUserUnit.CasualUnit(U1, CasualUserUnit.UnitKind.REGULAR, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
