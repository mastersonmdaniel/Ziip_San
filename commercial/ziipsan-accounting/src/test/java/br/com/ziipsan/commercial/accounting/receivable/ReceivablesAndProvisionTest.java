package br.com.ziipsan.commercial.accounting.receivable;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.Sphere;
import br.com.ziipsan.commercial.accounting.receivable.DoubtfulProvision.*;
import br.com.ziipsan.commercial.accounting.receivable.ReceivablesMonthlyBalance.Term;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ReceivablesAndProvisionTest {
    private static BigDecimal d(String v){ return new BigDecimal(v); }
    @Test @DisplayName("FIN-05 a mao: 1000 + 500 - 200 - 100 - 50 - 30 - 20 = 1100")
    void fin05() {
        var b = new ReceivablesMonthlyBalance(Sphere.PARTICULAR, Term.SHORT, d("1000.00"), d("500.00"), d("200.00"), d("100.00"), d("50.00"), d("30.00"), d("20.00"));
        assertThat(b.current()).isEqualByComparingTo("1100.00");
    }
    @Test @DisplayName("FIN-05 PUBLICO x LONG a mao: 5000 + 1200 - 300 - 0 - 100 - 0 - 50 = 5750")
    void fin05PublicLong() {
        var b = new ReceivablesMonthlyBalance(Sphere.PUBLICO, Term.LONG, d("5000.00"), d("1200.00"), d("300.00"), d("0.00"), d("100.00"), d("0.00"), d("50.00"));
        assertThat(b.current()).isEqualByComparingTo("5750.00");
    }
    @Test @DisplayName("FIN-05: saldo negativo e inconsistencia recusada (decisao L3), nao devolvido em silencio")
    void fin05NegativeRefused() {
        var b = new ReceivablesMonthlyBalance(Sphere.PUBLICO, Term.SHORT, d("0.00"), d("100.00"), d("200.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"));
        assertThatThrownBy(b::current).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("FIN-05 agregacao por esfera x prazo: soma componente a componente; mistura de esfera/prazo recusada")
    void fin05Aggregate() {
        var b1 = new ReceivablesMonthlyBalance(Sphere.PARTICULAR, Term.SHORT, d("1000.00"), d("500.00"), d("200.00"), d("100.00"), d("50.00"), d("30.00"), d("20.00"));
        var b2 = new ReceivablesMonthlyBalance(Sphere.PARTICULAR, Term.SHORT, d("0.00"), d("100.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"));
        // agregado a mao: 1000 + 600 - 200 - 100 - 50 - 30 - 20 = 1200
        assertThat(ReceivablesMonthlyBalance.aggregate(List.of(b1, b2)).current()).isEqualByComparingTo("1200.00");
        var other = new ReceivablesMonthlyBalance(Sphere.PUBLICO, Term.SHORT, d("0.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"), d("0.00"));
        assertThatThrownBy(() -> ReceivablesMonthlyBalance.aggregate(List.of(b1, other))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("provisao TARGET_WRITE_OFF (oficial): provisao = alvo de baixa do contador; alvo acima do saldo recusado")
    void doubtfulTargetWriteOff() {
        assertThat(DoubtfulProvision.provision(Method.TARGET_WRITE_OFF, d("150.00"), d("1100.00"), null, null)).isEqualByComparingTo("150.00");
        assertThat(DoubtfulProvision.targetWriteOff(d("1100.00"), d("1100.00"))).isEqualByComparingTo("1100.00");
        assertThatThrownBy(() -> DoubtfulProvision.targetWriteOff(d("1200.00"), d("1100.00"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("provisao AGING_MATRIX (oficial) a mao: 1000x50% + 200x10% = 500 + 20 = 520,00")
    void doubtfulAgingMatrix() {
        assertThat(DoubtfulProvision.provision(Method.AGING_MATRIX, null, null,
            List.of(new AgingBucket(d("1000.00"), d("0.50")), new AgingBucket(d("200.00"), d("0.10"))), null)).isEqualByComparingTo("520.00");
    }
    @Test @DisplayName("provisao EXPECTED_LOSS (oficial, CPC 48) a mao: curva 2000x2% + 1000x10% + 500x40% = 40 + 100 + 200 = 340,00")
    void doubtfulExpectedLoss() {
        var curve = List.of(
            new LossCurveSegment("ATE_30D", d("2000.00"), d("0.02")),
            new LossCurveSegment("31_A_90D", d("1000.00"), d("0.10")),
            new LossCurveSegment("ACIMA_90D", d("500.00"), d("0.40")));
        assertThat(DoubtfulProvision.provision(Method.EXPECTED_LOSS, null, null, null, curve)).isEqualByComparingTo("340.00");
        assertThat(DoubtfulProvision.expectedLoss(curve)).isEqualByComparingTo("340.00");
    }
}
