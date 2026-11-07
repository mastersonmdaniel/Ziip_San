package br.com.ziipsan.commercial.collection.ampliada;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-26 - fechamento mensal congelado e depositos identificados/nao identificados.
 * Fechamento a mao: janeiro com 100,00 + 250,00 = 350,00 em 2 pagamentos.
 */
class MonthlyClosingAndDepositTest {

    @Test
    void fechamentoSomaSoOMesERetornaFotografiaCongelada() {
        var frozen = MonthlyClosing.close(YearMonth.of(2026, 1), List.of(
                new MonthlyClosing.ClosingPayment(LocalDate.of(2026, 1, 10), new BigDecimal("100.00")),
                new MonthlyClosing.ClosingPayment(LocalDate.of(2026, 1, 28), new BigDecimal("250.00"))));
        assertThat(frozen.totalNet()).isEqualByComparingTo("350.00");
        assertThat(frozen.paymentCount()).isEqualTo(2);
    }

    @Test
    void pagamentoEmMesCongeladoERecusado() {
        assertThatThrownBy(() -> MonthlyClosing.assertMonthOpen(
                LocalDate.of(2026, 1, 15), Set.of(YearMonth.of(2026, 1))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("congelado");
        // Mes ainda aberto nao levanta.
        MonthlyClosing.assertMonthOpen(LocalDate.of(2026, 2, 15), Set.of(YearMonth.of(2026, 1)));
    }

    @Test
    void fechamentoRecusaPagamentoDeOutroMes() {
        assertThatThrownBy(() -> MonthlyClosing.close(YearMonth.of(2026, 1), List.of(
                new MonthlyClosing.ClosingPayment(LocalDate.of(2026, 2, 1), new BigDecimal("10.00")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void depositoComReferenciaEValorConferentesEIdentificado() {
        UUID bill = UUID.randomUUID();
        var id = DepositIdentification.identify(
                new DepositIdentification.Deposit(new BigDecimal("130.00"), LocalDate.of(2026, 1, 12), bill),
                new DepositIdentification.OpenBill(bill, new BigDecimal("130.00")));
        assertThat(id.status()).isEqualTo(DepositIdentification.DepositStatus.IDENTIFIED);
    }

    @Test
    void depositoSemReferenciaOuComValorDivergenteNaoEIdentificado() {
        UUID bill = UUID.randomUUID();
        var semRef = DepositIdentification.identify(
                new DepositIdentification.Deposit(new BigDecimal("130.00"), LocalDate.of(2026, 1, 12), null),
                new DepositIdentification.OpenBill(bill, new BigDecimal("130.00")));
        assertThat(semRef.status()).isEqualTo(DepositIdentification.DepositStatus.UNIDENTIFIED);
        var divergente = DepositIdentification.identify(
                new DepositIdentification.Deposit(new BigDecimal("129.00"), LocalDate.of(2026, 1, 12), bill),
                new DepositIdentification.OpenBill(bill, new BigDecimal("130.00")));
        assertThat(divergente.status()).isEqualTo(DepositIdentification.DepositStatus.UNIDENTIFIED);
    }

    @Test
    void depositoComReferenciaDesconhecidaNaoEIdentificado() {
        // B2 (pos-revisao): ramo "referencia desconhecida" - a referencia nao
        // corresponde a nenhuma conta aberta candidata (outra conta ou nenhuma).
        UUID referencia = UUID.randomUUID();
        UUID outraConta = UUID.randomUUID();
        var outra = DepositIdentification.identify(
                new DepositIdentification.Deposit(new BigDecimal("130.00"), LocalDate.of(2026, 1, 12), referencia),
                new DepositIdentification.OpenBill(outraConta, new BigDecimal("130.00")));
        assertThat(outra.status()).isEqualTo(DepositIdentification.DepositStatus.UNIDENTIFIED);
        assertThat(outra.reason()).contains("nao corresponde");
        var nenhuma = DepositIdentification.identify(
                new DepositIdentification.Deposit(new BigDecimal("130.00"), LocalDate.of(2026, 1, 12), referencia),
                null);
        assertThat(nenhuma.status()).isEqualTo(DepositIdentification.DepositStatus.UNIDENTIFIED);
    }

    @Test
    void contaCandidataSemValorDevidoEErroExplicitoNaoNpe() {
        // B2 (pos-revisao): amountDue nulo era NPE na comparacao; agora IAE explicita.
        assertThatThrownBy(() -> new DepositIdentification.OpenBill(UUID.randomUUID(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor devido");
    }
}
