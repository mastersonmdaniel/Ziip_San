package br.com.ziipsan.commercial.accounting.closing;

import br.com.ziipsan.commercial.accounting.event.*;
import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FrozenMonthClosingTest {
    private static final LocalDate MAR = LocalDate.of(2026,3,1);
    private static final LocalDate MAR_DAY = LocalDate.of(2026,3,5);

    private static JournalEntry entry(String ref) {
        return entry(ref, "100.00", Scope.REGULATORY, MAR);
    }
    private static JournalEntry entry(String ref, String amount, Scope scope, LocalDate month) {
        return new JournalEntry(AccountingEventKey.BILL_ISSUED, "1.1.2", "3.1.1", new BigDecimal(amount), month, MAR_DAY, "h", Sphere.PARTICULAR, scope, null, null, ref);
    }
    @Test @DisplayName("fechamento congelado: reprocessamento com lista recomputada DIVERGENTE preserva integro o conteudo original")
    void frozenReprocessKeepsEntries() {
        var closing = new FrozenMonthClosing(MAR);
        assertThat(closing.add(entry("conta:1"))).isTrue();
        assertThat(closing.add(entry("conta:1"))).isFalse(); // idempotente antes do fechamento
        closing.freeze();
        // recomputados divergentes: outro valor e outra referencia — nada pode apagar nem substituir o original
        var divergent = List.of(entry("conta:99", "777.00", Scope.REGULATORY, MAR), entry("conta:1", "555.00", Scope.REGULATORY, MAR));
        var after = closing.reprocess(divergent);
        assertThat(after).hasSize(1);
        assertThat(after.get(0).sourceReference()).isEqualTo("conta:1");
        assertThat(after.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(closing.entries()).hasSize(1);
        assertThatThrownBy(() -> closing.add(entry("conta:2"))).isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("fechamento e vinculado ao mes: lancamento de outro mes recusado; congelar marco nao e a instancia de abril")
    void closingBoundToItsMonth() {
        var closing = new FrozenMonthClosing(MAR);
        assertThat(closing.accountingMonth()).isEqualTo(MAR);
        assertThatThrownBy(() -> closing.add(entry("conta:abril", "100.00", Scope.REGULATORY, LocalDate.of(2026,4,1))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FrozenMonthClosing(LocalDate.of(2026,3,15))).isInstanceOf(IllegalArgumentException.class);
        var april = new FrozenMonthClosing(LocalDate.of(2026,4,1));
        closing.freeze();
        assertThat(april.isFrozen()).isFalse();
        assertThat(april.add(entry("conta:abril", "100.00", Scope.REGULATORY, LocalDate.of(2026,4,1)))).isTrue();
    }
    @Test @DisplayName("fatos distintos com a mesma origem NAO sao descartados: escopo diferente (REGULATORY x MANAGERIAL) ou valor diferente geram 2 lancamentos")
    void distinctFactsWithSameReferenceAreKept() {
        var closing = new FrozenMonthClosing(MAR);
        assertThat(closing.add(entry("conta:1", "100.00", Scope.REGULATORY, MAR))).isTrue();
        assertThat(closing.add(entry("conta:1", "100.00", Scope.MANAGERIAL, MAR))).isTrue();
        assertThat(closing.add(entry("conta:1", "250.00", Scope.REGULATORY, MAR))).isTrue();
        assertThat(closing.entries()).hasSize(3);
    }
}
