package br.com.ziipsan.commercial.accounting.event;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JournalEntryGeneratorTest {
    private static final AccountingRule BILL = new AccountingRule(AccountingEventKey.BILL_ISSUED, "1.1.2", "3.1.1", "Faturamento {origem} de {valor}", LocalDate.of(2026,1,1), null);
    private static final AccountingRule RECOVERY = new AccountingRule(AccountingEventKey.CREDIT_RECOVERY, "1.1.2", "4.1.9", "Recuperacao de credito {origem}", LocalDate.of(2026,1,1), null);
    private static final LocalDate FACT_DAY = LocalDate.of(2026,3,5);

    @Test @DisplayName("fato de faturamento gera debito/credito da regra, com mes contabil da P2.5 e data do fato")
    void billIssued() {
        var e = JournalEntryGenerator.generate(new Fact(AccountingEventKey.BILL_ISSUED, new BigDecimal("250.00"), LocalDate.of(2026,3,1), FACT_DAY, Sphere.PARTICULAR, Scope.REGULATORY, UUID.randomUUID(), UUID.randomUUID(), "conta:123"), List.of(BILL));
        assertThat(e.debitAccountCode()).isEqualTo("1.1.2");
        assertThat(e.creditAccountCode()).isEqualTo("3.1.1");
        assertThat(e.accountingMonth()).isEqualTo(LocalDate.of(2026,3,1));
        assertThat(e.factDate()).isEqualTo(FACT_DAY);
        assertThat(e.history()).isEqualTo("Faturamento conta:123 de 250.00");
    }
    @Test @DisplayName("recuperacao de credito e evento proprio, com regra propria (debito, credito, valor, mes e historico afirmados)")
    void creditRecoveryIsOwnEvent() {
        var e = JournalEntryGenerator.generate(new Fact(AccountingEventKey.CREDIT_RECOVERY, new BigDecimal("80.00"), LocalDate.of(2026,3,1), FACT_DAY, Sphere.PUBLICO, Scope.REGULATORY, null, null, "pagamento:conta-prescrita"), List.of(BILL, RECOVERY));
        assertThat(e.eventKey()).isEqualTo(AccountingEventKey.CREDIT_RECOVERY);
        assertThat(e.debitAccountCode()).isEqualTo("1.1.2");
        assertThat(e.creditAccountCode()).isEqualTo("4.1.9");
        assertThat(e.amount()).isEqualByComparingTo("80.00");
        assertThat(e.accountingMonth()).isEqualTo(LocalDate.of(2026,3,1));
        assertThat(e.history()).isEqualTo("Recuperacao de credito pagamento:conta-prescrita");
    }
    @Test @DisplayName("accounting_month fora do dia 1 e sem regra vigente sao recusados")
    void rejects() {
        assertThatThrownBy(() -> new Fact(AccountingEventKey.BILL_ISSUED, new BigDecimal("1.00"), LocalDate.of(2026,3,15), FACT_DAY, Sphere.PARTICULAR, Scope.REGULATORY, null, null, "x"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JournalEntryGenerator.generate(new Fact(AccountingEventKey.PAYMENT_RECEIVED, new BigDecimal("1.00"), LocalDate.of(2026,3,1), FACT_DAY, Sphere.PARTICULAR, Scope.REGULATORY, null, null, "x"), List.of(BILL)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("fronteira de vigencia: regra expirada (validTo anterior ao mes contabil) nao se aplica")
    void expiredRuleDoesNotApply() {
        var expired = new AccountingRule(AccountingEventKey.BILL_ISSUED, "1.1.2", "3.1.1", "Faturamento {origem}", LocalDate.of(2025,1,1), LocalDate.of(2026,2,28));
        assertThatThrownBy(() -> JournalEntryGenerator.generate(new Fact(AccountingEventKey.BILL_ISSUED, new BigDecimal("10.00"), LocalDate.of(2026,3,1), FACT_DAY, Sphere.PARTICULAR, Scope.REGULATORY, null, null, "conta:9"), List.of(expired)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("regras vigentes sobrepostas para o mesmo evento sao ambiguidade recusada (nao vence a primeira da lista)")
    void overlappingRulesRefused() {
        var other = new AccountingRule(AccountingEventKey.BILL_ISSUED, "1.1.3", "3.1.2", "Outro {origem}", LocalDate.of(2026,1,1), null);
        assertThatThrownBy(() -> JournalEntryGenerator.generate(new Fact(AccountingEventKey.BILL_ISSUED, new BigDecimal("10.00"), LocalDate.of(2026,3,1), FACT_DAY, Sphere.PARTICULAR, Scope.REGULATORY, null, null, "conta:9"), List.of(BILL, other)))
            .isInstanceOf(IllegalStateException.class);
    }
}
