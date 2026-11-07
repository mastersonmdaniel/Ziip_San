package br.com.ziipsan.commercial.accounting.report;

import br.com.ziipsan.commercial.accounting.event.*;
import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TrialBalanceTest {
    private static JournalEntry entry(String amount, LocalDate month, Scope scope, UUID contract, UUID muni, String ref) {
        return new JournalEntry(AccountingEventKey.BILL_ISSUED, "1.1.2", "3.1.1", new BigDecimal(amount), month, month.withDayOfMonth(5), "h", Sphere.PARTICULAR, scope, contract, muni, ref);
    }
    @Test @DisplayName("balancete separa escopo regulatorio do gerencial e EXCLUI outro contrato/municipio; linhas e totalCredit afirmados")
    void trialBalance() {
        UUID contract = UUID.randomUUID(), muni = UUID.randomUUID();
        UUID otherContract = UUID.randomUUID(), otherMuni = UUID.randomUUID();
        LocalDate mar = LocalDate.of(2026,3,1);
        var e1 = entry("250.00", mar, Scope.REGULATORY, contract, muni, "c:1");
        var e2 = entry("999.00", mar, Scope.MANAGERIAL, contract, muni, "c:2");
        var e3 = entry("700.00", mar, Scope.REGULATORY, otherContract, otherMuni, "c:3");
        var b = TrialBalance.monthly(List.of(e1, e2, e3), mar, Scope.REGULATORY, contract, muni);
        assertThat(b.isBalanced()).isTrue();
        assertThat(b.totalDebit()).isEqualByComparingTo("250.00");
        assertThat(b.totalCredit()).isEqualByComparingTo("250.00");
        assertThat(b.lines()).hasSize(2);
        var debitLine = b.lines().stream().filter(l -> l.accountCode().equals("1.1.2")).findFirst().orElseThrow();
        assertThat(debitLine.totalDebit()).isEqualByComparingTo("250.00");
        assertThat(debitLine.totalCredit()).isEqualByComparingTo("0");
        var creditLine = b.lines().stream().filter(l -> l.accountCode().equals("3.1.1")).findFirst().orElseThrow();
        assertThat(creditLine.totalDebit()).isEqualByComparingTo("0");
        assertThat(creditLine.totalCredit()).isEqualByComparingTo("250.00");
        // sem filtro de contrato/municipio, o regulatorio soma os dois contratos: 250 + 700 = 950
        var all = TrialBalance.monthly(List.of(e1, e2, e3), mar, Scope.REGULATORY, null, null);
        assertThat(all.totalDebit()).isEqualByComparingTo("950.00");
        assertThat(all.totalCredit()).isEqualByComparingTo("950.00");
    }
    @Test @DisplayName("trimestral e anual sao agregacoes reais: Q1 = jan+fev+mar = 600; ano = 1000 (com abr); mes fora da faixa excluido")
    void quarterlyAndAnnual() {
        UUID contract = UUID.randomUUID(), muni = UUID.randomUUID();
        var jan = entry("100.00", LocalDate.of(2026,1,1), Scope.REGULATORY, contract, muni, "c:jan");
        var feb = entry("200.00", LocalDate.of(2026,2,1), Scope.REGULATORY, contract, muni, "c:fev");
        var mar = entry("300.00", LocalDate.of(2026,3,1), Scope.REGULATORY, contract, muni, "c:mar");
        var apr = entry("400.00", LocalDate.of(2026,4,1), Scope.REGULATORY, contract, muni, "c:abr");
        var all = List.of(jan, feb, mar, apr);
        var q1 = TrialBalance.quarterly(all, 2026, 1, Scope.REGULATORY, contract, muni);
        assertThat(q1.totalDebit()).isEqualByComparingTo("600.00");
        assertThat(q1.totalCredit()).isEqualByComparingTo("600.00");
        assertThat(q1.month()).isEqualTo(LocalDate.of(2026,1,1));
        assertThat(q1.periodEnd()).isEqualTo(LocalDate.of(2026,3,1));
        var q2 = TrialBalance.quarterly(all, 2026, 2, Scope.REGULATORY, contract, muni);
        assertThat(q2.totalDebit()).isEqualByComparingTo("400.00");
        var year = TrialBalance.annual(all, 2026, Scope.REGULATORY, contract, muni);
        assertThat(year.totalDebit()).isEqualByComparingTo("1000.00");
        assertThat(year.totalCredit()).isEqualByComparingTo("1000.00");
        assertThat(year.isBalanced()).isTrue();
    }
}
