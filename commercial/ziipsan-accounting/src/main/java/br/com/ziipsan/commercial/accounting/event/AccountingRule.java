package br.com.ziipsan.commercial.accounting.event;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Regra contabil por chave de evento, configuravel no grupo acc.* com vigencia:
 * conta debito, conta credito e historico (modelo com {valor} e {origem}).
 */
public record AccountingRule(AccountingEventKey eventKey, String debitAccountCode, String creditAccountCode,
                             String historyTemplate, LocalDate validFrom, LocalDate validTo) {
    public AccountingRule {
        Objects.requireNonNull(eventKey, "chave de evento e obrigatoria");
        if (debitAccountCode == null || debitAccountCode.isBlank()) throw new IllegalArgumentException("conta debito e obrigatoria");
        if (creditAccountCode == null || creditAccountCode.isBlank()) throw new IllegalArgumentException("conta credito e obrigatoria");
        if (debitAccountCode.equals(creditAccountCode)) throw new IllegalArgumentException("debito e credito nao podem ser a mesma conta");
        if (historyTemplate == null || historyTemplate.isBlank()) throw new IllegalArgumentException("historico e obrigatorio");
        Objects.requireNonNull(validFrom, "inicio de vigencia e obrigatorio");
        if (validTo != null && validTo.isBefore(validFrom)) throw new IllegalArgumentException("vigencia invalida");
    }
    public boolean appliesOn(LocalDate date) {
        return !date.isBefore(validFrom) && (validTo == null || !date.isAfter(validTo));
    }
}
