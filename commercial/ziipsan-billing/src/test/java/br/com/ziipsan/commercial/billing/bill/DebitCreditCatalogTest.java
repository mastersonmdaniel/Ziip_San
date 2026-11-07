package br.com.ziipsan.commercial.billing.bill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.d;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.id;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.res;
import static br.com.ziipsan.commercial.billing.fixture.FictitiousTariffs.t1Schedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DAD-20 (P2.5, frente F3): débitos e créditos tipados pelos catálogos {@code debit_types} /
 * {@code credit_origins}, proveniência {@code source_type}/{@code source_id}, OS obrigatória no
 * débito de serviço, meses contábil/de arrecadação e prazo de financiamento — e a travessia de
 * meses de parcelas/resíduo com os lançamentos tipados, com os mesmos valores calculados à mão
 * dos casos C-*/D-* já existentes (que continuam valendo em {@link CreditApplierTest} e
 * {@link InstallmentsAndApportionmentTest}).
 *
 * <p>Os itens de catálogo "TESTE" e todos os valores são fictícios, como no restante da suíte:
 * o produto não semeia código de catálogo nenhum.
 */
class DebitCreditCatalogTest {

    private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
    private static final LocalDate FEV = LocalDate.of(2026, 2, 1);
    private static final LocalDate MAR = LocalDate.of(2026, 3, 1);

    /** Débito de serviço: tipo do catálogo + origem SERVICE_ORDER apontando para a OS. */
    private static DebitReceivable serviceDebit(String total, int installments, int done, java.util.UUID serviceOrderId) {
        return new DebitReceivable(id(1), id(900), "TESTE", true, d(total), installments, done,
                ReceivableSourceType.SERVICE_ORDER, serviceOrderId, null, serviceOrderId, JAN, JAN, null);
    }

    private static CreditReceivable manualCredit(int n, String total, int installments, int done, String residual) {
        return new CreditReceivable(id(n), id(901), "TESTE", d(total), installments, done, d(residual),
                ReceivableSourceType.MANUAL, null, null, null, JAN, null, null);
    }

    /** Reconstrói o crédito depois de um mês, como a persistência faria (parcelas e resíduo da linha). */
    private static CreditReceivable after(CreditReceivable before, CreditLine line, LocalDate collectionMonth) {
        return new CreditReceivable(before.id(), before.creditOriginId(), before.originCode(), before.total(),
                before.installments(), line.installmentsDoneAfter(), line.residualAfter(), before.sourceType(),
                before.sourceId(), before.legacyReference(), before.serviceOrderId(), before.accountingMonth(),
                collectionMonth, before.financingTerm());
    }

    // ----------------------------------------------------------------------------------------------
    // Recusas exigidas pelo aceite
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("DAD-20: débito de serviço sem OS é recusado (service_order_id obrigatório)")
    void serviceDebitWithoutServiceOrderIsRefused() {
        assertThatThrownBy(() -> serviceDebit("100.00", 1, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service_order_id");
    }

    @Test
    @DisplayName("DAD-20: débito de serviço com OS é aceito, e o generates_bill vem do tipo do catálogo")
    void serviceDebitWithServiceOrderIsAccepted() {
        DebitReceivable debit = serviceDebit("100.00", 3, 0, id(777));

        assertThat(debit.serviceOrderId()).isEqualTo(id(777));
        assertThat(debit.type()).isEqualTo(new DebitType(id(900), "TESTE", true));
        assertThat(debit.generatesBill()).isTrue();
    }

    @Test
    @DisplayName("DAD-20: source_type fora dos seis valores da spec é recusado; os seis são aceitos")
    void invalidSourceTypeIsRefused() {
        assertThat(ReceivableSourceType.parse("SERVICE_ORDER")).isEqualTo(ReceivableSourceType.SERVICE_ORDER);
        assertThat(ReceivableSourceType.parse("INSTALLMENT_AGREEMENT"))
                .isEqualTo(ReceivableSourceType.INSTALLMENT_AGREEMENT);
        assertThat(ReceivableSourceType.parse("INFRACTION")).isEqualTo(ReceivableSourceType.INFRACTION);
        assertThat(ReceivableSourceType.parse("PAYMENT_ADJUSTMENT"))
                .isEqualTo(ReceivableSourceType.PAYMENT_ADJUSTMENT);
        assertThat(ReceivableSourceType.parse("LEGACY_IMPORT")).isEqualTo(ReceivableSourceType.LEGACY_IMPORT);
        assertThat(ReceivableSourceType.parse("MANUAL")).isEqualTo(ReceivableSourceType.MANUAL);

        assertThatThrownBy(() -> ReceivableSourceType.parse("OUTRO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivableSourceType.parse("service_order"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivableSourceType.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceivableSourceType.parse("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DAD-20: origem inválida é recusada — crédito sem item do catálogo, código em branco, tipo sem id")
    void invalidCatalogOriginIsRefused() {
        assertThatThrownBy(() -> new CreditReceivable(id(1), null, "TESTE", d("10.00"), 1, 0, d("0.00"),
                ReceivableSourceType.MANUAL, null, null, null, JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditReceivable(id(1), id(901), " ", d("10.00"), 1, 0, d("0.00"),
                ReceivableSourceType.MANUAL, null, null, null, JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditOrigin(id(901), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebitType(null, "TESTE", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebitReceivable(id(1), null, "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.MANUAL, null, null, null, JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DAD-20: proveniência incoerente é recusada (acordo sem source_id, OS com outro source_type, legado sem referência)")
    void incoherentProvenanceIsRefused() {
        // parcelamento sem identificar o acordo
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.INSTALLMENT_AGREEMENT, null, null, null, JAN, null, 12))
                .isInstanceOf(IllegalArgumentException.class);
        // service_order_id com origem manual: contradição
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.MANUAL, null, null, id(777), JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        // importação do legado sem a referência do legado (a P2.10 confere o saldo por ela)
        assertThatThrownBy(() -> new CreditReceivable(id(1), id(901), "TESTE", d("10.00"), 1, 0, d("0.00"),
                ReceivableSourceType.LEGACY_IMPORT, null, null, null, JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DAD-20: prestação de parcelamento do legado (caso da P2.10) é aceita com legacy_reference e financing_term")
    void legacyInstallmentDebitIsAccepted() {
        DebitReceivable debit = new DebitReceivable(id(1), id(900), "TESTE", false, d("120.00"), 12, 0,
                ReceivableSourceType.INSTALLMENT_AGREEMENT, id(555), "ACORDO-LEGADO-123", null, JAN, FEV, 12);

        assertThat(debit.legacyReference()).isEqualTo("ACORDO-LEGADO-123");
        assertThat(debit.financingTerm()).isEqualTo(12);
        assertThat(debit.collectionMonth()).isEqualTo(FEV);
    }

    @Test
    @DisplayName("DAD-20: meses fora do primeiro dia, arrecadação antes da competência e prazo não positivo recusam")
    void invalidMonthsAndTermAreRefused() {
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.MANUAL, null, null, null, LocalDate.of(2026, 1, 15), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.MANUAL, null, null, null, FEV, JAN, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                ReceivableSourceType.MANUAL, null, null, null, JAN, null, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DebitReceivable(id(1), id(900), "TESTE", false, d("10.00"), 1, 0,
                null, null, null, null, JAN, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ----------------------------------------------------------------------------------------------
    // Travessia de meses: parcelas e resíduo com os lançamentos tipados (valores de C-01/C-02/D-01)
    // ----------------------------------------------------------------------------------------------

    @Test
    @DisplayName("D-01 tipado: débito de serviço de R$ 100,00 em 3 meses: 33,33 + 33,33 + 33,34 = 100,00; a linha carrega o tipo do catálogo")
    void serviceDebitInstallmentsAcrossMonths() {
        DebitReceivable debit = serviceDebit("100.00", 3, 0, id(777));
        List<BigDecimal> charged = new ArrayList<>();
        LocalDate[] collectionMonths = {FEV, MAR, LocalDate.of(2026, 4, 1)};
        for (int month = 0; month < 3; month++) {
            BillInput in = new BillInput(t1Schedule(), JAN, FEV, List.of(res(1)), null, null,
                    List.of(debit), List.of(), null);
            BillResult r = BillCalculator.calculate(in);
            charged.add(r.debitsValue());
            DebitLine line = r.debitLines().get(0);
            assertThat(line.debitTypeId()).isEqualTo(id(900));
            assertThat(line.debitTypeCode()).isEqualTo("TESTE");
            assertThat(line.installmentNo()).isEqualTo(month + 1);
            debit = new DebitReceivable(debit.id(), debit.debitTypeId(), debit.debitTypeCode(),
                    debit.generatesBill(), debit.total(), debit.installments(), debit.installmentsDone() + 1,
                    debit.sourceType(), debit.sourceId(), debit.legacyReference(), debit.serviceOrderId(),
                    debit.accountingMonth(), collectionMonths[month], debit.financingTerm());
        }

        assertThat(charged).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("33.33"), d("33.33"), d("33.34"));
        assertThat(charged.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(debit.hasInstallmentToCharge()).isFalse();
    }

    @Test
    @DisplayName("C-02/C-03 tipados: crédito de 150,00 em 1 parcela — mês 1 aplica 100,00 (resíduo 50,00); mês 2 realiza o resíduo de 50,00")
    void creditResidualAcrossMonths() {
        CreditReceivable credit = manualCredit(1, "150.00", 1, 0, "0.00");

        CreditApplier.Application first = CreditApplier.apply(d("100.00"), List.of(credit));
        assertThat(first.totalApplied()).isEqualByComparingTo("100.00");
        assertThat(first.lines().get(0).residualAfter()).isEqualByComparingTo("50.00");
        assertThat(first.lines().get(0).creditOriginId()).isEqualTo(id(901));
        assertThat(first.lines().get(0).originCode()).isEqualTo("TESTE");
        credit = after(credit, first.lines().get(0), FEV);
        assertThat(credit.collectionMonth()).isEqualTo(FEV);

        CreditApplier.Application second = CreditApplier.apply(d("80.00"), List.of(credit));
        assertThat(second.lines().get(0).applied()).isEqualByComparingTo("50.00");
        assertThat(second.lines().get(0).residualAfter()).isEqualByComparingTo("0.00");
        assertThat(second.lines().get(0).installmentNo()).as("só resíduo, nenhuma parcela nova").isNull();
        assertThat(d("80.00").subtract(second.totalApplied())).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("C-01 tipado: crédito de R$ 35,00 em 6 meses: 5,83 ×5 e 5,85; soma 35,00 e resíduo final 0,00")
    void creditInstallmentsAcrossMonths() {
        CreditReceivable credit = manualCredit(1, "35.00", 6, 0, "0.00");
        List<BigDecimal> applied = new ArrayList<>();
        for (int month = 0; month < 6; month++) {
            CreditApplier.Application a = CreditApplier.apply(d("1000.00"), List.of(credit));
            applied.add(a.totalApplied());
            assertThat(a.lines().get(0).creditOriginId()).isEqualTo(id(901));
            credit = after(credit, a.lines().get(0), JAN.plusMonths(month + 1L));
        }

        assertThat(applied).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.83"), d("5.85"));
        assertThat(applied.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("35.00");
        assertThat(credit.installmentsDone()).isEqualTo(6);
        assertThat(credit.residual()).isEqualByComparingTo("0.00");
        assertThat(credit.origin()).isEqualTo(new CreditOrigin(id(901), "TESTE"));
    }
}
