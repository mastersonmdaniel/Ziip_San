package br.com.ziipsan.commercial.accounting.closing;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.JournalEntry;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fechamento mensal congelado (ED-32): uma instancia e o fechamento de UM mes
 * contabil (vinculado no construtor — correcao pos-revisao M1; antes o
 * congelamento era um booleano global da instancia, sem mes). Depois de
 * fechado, reprocessar o mes NAO apaga nem altera lancamentos — devolve
 * exatamente os mesmos — e lancamento novo no mes fechado e recusado.
 * Lancamento de outro mes nesta instancia tambem e recusado: congelar marco
 * nunca bloqueia abril, porque abril tem a sua propria instancia.
 *
 * Chave de idempotencia AMPLIADA (M1): identidade completa do lancamento
 * (evento, origem, mes, valor, escopo, esfera, contas, contrato, municipio e
 * historico). Justificativa: com a chave antiga (evento|origem|mes), dois
 * fatos DISTINTOS de mesma origem — ex.: o mesmo sourceReference em escopo
 * REGULATORY e MANAGERIAL, ou com valores diferentes — colidiam e o segundo
 * era descartado em silencio (perda de lancamento). Com a identidade
 * completa, so e duplicado o que e integralmente igual, que e exatamente a
 * definicao de reprocessamento do mesmo fato.
 */
public final class FrozenMonthClosing {

    private final LocalDate accountingMonth;
    private final Map<String, JournalEntry> byIdempotencyKey = new LinkedHashMap<>();
    private boolean frozen;

    public FrozenMonthClosing(LocalDate accountingMonth) {
        requireMonth(accountingMonth);
        this.accountingMonth = accountingMonth;
    }

    public LocalDate accountingMonth() { return accountingMonth; }

    private static String key(JournalEntry e) {
        return String.join("|",
                e.eventKey().name(),
                e.sourceReference(),
                String.valueOf(e.accountingMonth()),
                e.amount().toPlainString(),
                e.scope().name(),
                e.sphere().name(),
                e.debitAccountCode(),
                e.creditAccountCode(),
                String.valueOf(e.contractId()),
                String.valueOf(e.municipalityId()),
                e.history());
    }

    /** @return true se gravou; false se ja existia (reprocessamento nao duplica nem apaga) */
    public boolean add(JournalEntry entry) {
        Objects.requireNonNull(entry, "lancamento e obrigatorio");
        if (!accountingMonth.equals(entry.accountingMonth()))
            throw new IllegalArgumentException("lancamento de " + entry.accountingMonth() + " nao pertence ao fechamento de " + accountingMonth);
        if (frozen) throw new IllegalStateException("mes " + accountingMonth + " fechado e congelado: lancamento novo recusado");
        return byIdempotencyKey.putIfAbsent(key(entry), entry) == null;
    }

    public List<JournalEntry> entries() { return List.copyOf(byIdempotencyKey.values()); }

    public void freeze() { this.frozen = true; }
    public boolean isFrozen() { return frozen; }

    /** Reprocessamento de um mes congelado: no-op que preserva todos os lancamentos. */
    public List<JournalEntry> reprocess(List<JournalEntry> ignoredRecomputed) {
        if (!frozen) throw new IllegalStateException("reprocessamento so se aplica a mes ja fechado");
        return entries(); // os recomputados sao descartados; nada e apagado
    }

    public static void requireMonth(LocalDate accountingMonth) {
        if (accountingMonth == null || accountingMonth.getDayOfMonth() != 1)
            throw new IllegalArgumentException("mes contabil deve ser o dia 1: " + accountingMonth);
    }
}
