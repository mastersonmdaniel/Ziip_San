package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Conciliacao pelo extrato da credenciadora: casa baixa x extrato pela transacao. */
class CardStatementReconcilerTest {

    private final CardStatementReconciler reconciler = new CardStatementReconciler();
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private CardStatementReconciler.ExpectedSettlement expected(String tx, String gross, String fee) {
        BigDecimal g = new BigDecimal(gross);
        BigDecimal f = new BigDecimal(fee);
        return new CardStatementReconciler.ExpectedSettlement(tx, "ref-" + tx, g, f, g.subtract(f));
    }

    private CardPorts.AcquirerPort.StatementLine line(String tx, String gross, String fee) {
        BigDecimal g = new BigDecimal(gross);
        BigDecimal f = new BigDecimal(fee);
        return new CardPorts.AcquirerPort.StatementLine(tx, "ref-" + tx, g, f, g.subtract(f),
                LocalDate.of(2026, 11, 7));
    }

    @Test
    @DisplayName("baixa e extrato iguais: CONCILIADO e totais batem")
    void exactMatchReconciles() {
        var result = reconciler.reconcile(
                java.util.List.of(expected("tx-1", "106.61", "3.20")),
                java.util.List.of(line("tx-1", "106.61", "3.20")), TOLERANCE);
        assertThat(result.reconciledCount()).isEqualTo(1);
        assertThat(result.items()).extracting(CardStatementReconciler.Item::status)
                .containsExactly(CardStatementReconciler.ItemStatus.CONCILIADO);
        assertThat(result.expectedNetTotal()).isEqualByComparingTo(result.statementNetTotal());
    }

    @Test
    @DisplayName("liquido fora da tolerancia: DIVERGENTE com a diferenca no item")
    void netBeyondToleranceDiverges() {
        var result = reconciler.reconcile(
                java.util.List.of(expected("tx-1", "106.61", "3.20")),
                java.util.List.of(line("tx-1", "106.61", "4.20")), TOLERANCE);
        assertThat(result.items().get(0).status()).isEqualTo(CardStatementReconciler.ItemStatus.DIVERGENTE);
        assertThat(result.items().get(0).netDifference()).isEqualByComparingTo("-1.00");
    }

    @Test
    @DisplayName("bruto diferente no extrato: DIVERGENTE mesmo com liquido proximo")
    void grossMismatchDiverges() {
        var result = reconciler.reconcile(
                java.util.List.of(expected("tx-1", "106.61", "3.20")),
                java.util.List.of(line("tx-1", "100.00", "3.20")), TOLERANCE);
        assertThat(result.items().get(0).status()).isEqualTo(CardStatementReconciler.ItemStatus.DIVERGENTE);
    }

    @Test
    @DisplayName("baixa sem linha no extrato: AUSENTE_NO_EXTRATO")
    void missingInStatement() {
        var result = reconciler.reconcile(
                java.util.List.of(expected("tx-1", "106.61", "3.20")),
                java.util.List.of(), TOLERANCE);
        assertThat(result.items()).extracting(CardStatementReconciler.Item::status)
                .containsExactly(CardStatementReconciler.ItemStatus.AUSENTE_NO_EXTRATO);
        assertThat(result.reconciledCount()).isZero();
    }

    @Test
    @DisplayName("linha do extrato sem baixa: EXTRATO_SEM_BAIXA")
    void statementWithoutSettlement() {
        var result = reconciler.reconcile(java.util.List.of(),
                java.util.List.of(line("tx-9", "50.00", "1.50")), TOLERANCE);
        assertThat(result.items()).extracting(CardStatementReconciler.Item::status)
                .containsExactly(CardStatementReconciler.ItemStatus.EXTRATO_SEM_BAIXA);
    }

    @Test
    @DisplayName("mesma transacao duas vezes no extrato: DUPLICADO_NO_EXTRATO e o total conta uma vez")
    void duplicatedInStatementCountsOnce() {
        var result = reconciler.reconcile(
                java.util.List.of(expected("tx-1", "106.61", "3.20")),
                java.util.List.of(line("tx-1", "106.61", "3.20"), line("tx-1", "106.61", "3.20")),
                TOLERANCE);
        assertThat(result.items().get(0).status())
                .isEqualTo(CardStatementReconciler.ItemStatus.DUPLICADO_NO_EXTRATO);
        assertThat(result.statementNetTotal()).isEqualByComparingTo("103.41");
    }
}
