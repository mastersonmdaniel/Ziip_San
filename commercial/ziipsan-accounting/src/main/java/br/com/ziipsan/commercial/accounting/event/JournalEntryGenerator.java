package br.com.ziipsan.commercial.accounting.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lancamentos gerados por fato (ED-32). O mes contabil vem do fato (accounting_month
 * da P2.5, sempre dia 1) e nunca da data de pagamento/arrecadacao.
 * O fato tem tambem a sua DATA (factDate, o dia em que o fato ocorreu/foi gerado),
 * distinta do mes contabil: e ela que permite o recorte DIARIO da exportacao
 * (correcao pos-revisao G2 — antes o lancamento nao tinha data e o recorte
 * diario era impossivel).
 * Dado regulatorio e gerencial trafegam separados pelo campo scope.
 * Duas regras vigentes sobrepostas para o mesmo evento sao ambiguidade de
 * configuracao e sao recusadas (correcao pos-revisao M3 — antes resolvia
 * silenciosamente pela ordem da lista).
 */
public final class JournalEntryGenerator {

    public enum Sphere { PARTICULAR, PUBLICO }
    public enum Scope { REGULATORY, MANAGERIAL }

    /** Fato gerador: referencia de origem rastreavel (auditoria) + mes contabil da P2.5 + data do fato. */
    public record Fact(AccountingEventKey eventKey, BigDecimal amount, LocalDate accountingMonth, LocalDate factDate,
                       Sphere sphere, Scope scope, UUID contractId, UUID municipalityId, String sourceReference) {
        public Fact {
            Objects.requireNonNull(eventKey, "chave de evento e obrigatoria");
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("valor do fato deve ser positivo: " + amount);
            Objects.requireNonNull(accountingMonth, "accounting_month (P2.5) e obrigatorio");
            if (accountingMonth.getDayOfMonth() != 1) throw new IllegalArgumentException("accounting_month deve ser o dia 1 do mes: " + accountingMonth);
            Objects.requireNonNull(factDate, "data do fato e obrigatoria (recorte diario da exportacao)");
            Objects.requireNonNull(sphere, "esfera e obrigatoria");
            Objects.requireNonNull(scope, "escopo (regulatorio/gerencial) e obrigatorio");
            if (sourceReference == null || sourceReference.isBlank()) throw new IllegalArgumentException("referencia de origem e obrigatoria (rastreabilidade)");
        }
    }

    public record JournalEntry(AccountingEventKey eventKey, String debitAccountCode, String creditAccountCode,
                               BigDecimal amount, LocalDate accountingMonth, LocalDate factDate, String history,
                               Sphere sphere, Scope scope, UUID contractId, UUID municipalityId, String sourceReference) {
    }

    private JournalEntryGenerator() {}

    public static JournalEntry generate(Fact fact, List<AccountingRule> rules) {
        Objects.requireNonNull(fact, "fato e obrigatorio");
        Objects.requireNonNull(rules, "regras contabeis (acc.*) sao obrigatorias");
        List<AccountingRule> matches = rules.stream()
                .filter(r -> r.eventKey() == fact.eventKey() && r.appliesOn(fact.accountingMonth()))
                .toList();
        if (matches.isEmpty())
            throw new IllegalStateException("sem regra contabil vigente (acc.*) para o evento " + fact.eventKey() + " em " + fact.accountingMonth());
        if (matches.size() > 1)
            throw new IllegalStateException("regras contabeis vigentes sobrepostas (ambiguas) para o evento " + fact.eventKey() + " em " + fact.accountingMonth() + ": " + matches.size() + " regras");
        AccountingRule rule = matches.get(0);
        String history = rule.historyTemplate()
                .replace("{valor}", fact.amount().toPlainString())
                .replace("{origem}", fact.sourceReference());
        return new JournalEntry(fact.eventKey(), rule.debitAccountCode(), rule.creditAccountCode(), fact.amount(),
                fact.accountingMonth(), fact.factDate(), history, fact.sphere(), fact.scope(), fact.contractId(), fact.municipalityId(), fact.sourceReference());
    }
}
