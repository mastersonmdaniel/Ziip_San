package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.collection.parameters.ArrecadacaoParameters.PartialSettlementOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ED-26 - cascata da fatura agrupadora do orgao centralizador.
 * Contas calculadas a mao: unidade A 100,00 (venc. 10/01), unidade B 200,00
 * (venc. 05/01), unidade C 50,00 (venc. 20/01); total 350,00.
 */
class CascadeSettlementTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private List<CascadeSettlement.GroupedBill> bills() {
        return List.of(
                new CascadeSettlement.GroupedBill(A, UUID.randomUUID(), LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 2), new BigDecimal("100.00")),
                new CascadeSettlement.GroupedBill(B, UUID.randomUUID(), LocalDate.of(2026, 1, 5),
                        LocalDate.of(2026, 1, 2), new BigDecimal("200.00")),
                new CascadeSettlement.GroupedBill(C, UUID.randomUUID(), LocalDate.of(2026, 1, 20),
                        LocalDate.of(2026, 1, 2), new BigDecimal("50.00")));
    }

    @Test
    void pagaAMenorSegueAOrdemDeVencimentoDoParametro() {
        // Pago 250,00: B (05/01) quita 200,00; A recebe 50,00 e resta 50,00 aberto; C intocada.
        var r = CascadeSettlement.settle(bills(), new BigDecimal("250.00"),
                PartialSettlementOrder.DUE_DATE_ASC);
        assertThat(r.allocations()).extracting(CascadeSettlement.Allocation::billId)
                .containsExactly(B, A, C);
        assertThat(r.allocations().get(0).settledAmount()).isEqualByComparingTo("200.00");
        assertThat(r.allocations().get(0).fullySettled()).isTrue();
        assertThat(r.allocations().get(1).settledAmount()).isEqualByComparingTo("50.00");
        assertThat(r.allocations().get(1).remainingOpen()).isEqualByComparingTo("50.00");
        assertThat(r.allocations().get(2).settledAmount()).isEqualByComparingTo("0.00");
        assertThat(r.totalSettled()).isEqualByComparingTo("250.00");
        assertThat(r.excessAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void ordemPorMenorValorMudaASequenciaDaCascata() {
        // Ordem AMOUNT_ASC: C 50,00, A 100,00, B 200,00. Pago 120,00: C quita, A recebe 70,00.
        var r = CascadeSettlement.settle(bills(), new BigDecimal("120.00"),
                PartialSettlementOrder.AMOUNT_ASC);
        assertThat(r.allocations()).extracting(CascadeSettlement.Allocation::billId)
                .containsExactly(C, A, B);
        assertThat(r.allocations().get(1).remainingOpen()).isEqualByComparingTo("30.00");
    }

    @Test
    void pagamentoExatoQuitaTodasSemExcedente() {
        var r = CascadeSettlement.settle(bills(), new BigDecimal("350.00"),
                PartialSettlementOrder.DUE_DATE_ASC);
        assertThat(r.allocations()).allMatch(CascadeSettlement.Allocation::fullySettled);
        assertThat(r.excessAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void pagamentoMaiorQueOTotalDevolveExcedenteExplicito() {
        var r = CascadeSettlement.settle(bills(), new BigDecimal("400.00"),
                PartialSettlementOrder.DUE_DATE_ASC);
        assertThat(r.totalSettled()).isEqualByComparingTo("350.00");
        assertThat(r.excessAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void agrupadorVazioOuPagamentoZeradoSaoRecusados() {
        assertThatThrownBy(() -> CascadeSettlement.settle(List.of(), new BigDecimal("10.00"),
                PartialSettlementOrder.DUE_DATE_ASC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CascadeSettlement.settle(bills(), new BigDecimal("0.00"),
                PartialSettlementOrder.DUE_DATE_ASC)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contaAgrupadaSemUnidadeERecusada() {
        // B1 (pos-revisao): conta agrupada e, por definicao, de uma unidade.
        assertThatThrownBy(() -> new CascadeSettlement.GroupedBill(A, null,
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 2), new BigDecimal("100.00")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("unidade");
    }
}
