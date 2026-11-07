package br.com.ziipsan.commercial.accounting.report;

import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.JournalEntry;
import br.com.ziipsan.commercial.accounting.event.JournalEntryGenerator.Scope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Balancetes por contrato e por municipio (ED-32): mensal, trimestral e anual.
 * Correcao pos-revisao (M4): trimestral e anual NAO sao mais "agregacao pelo
 * chamador" — sao metodos reais desta classe (quarterly/annual/period), que
 * agregam os lancamentos de todos os meses da faixa, com teste. O que continua
 * fora (fiação de reporting, ED-46..49) e a FORMATACAO das demonstracoes
 * anuais (layout/pecas), nao a agregacao dos saldos.
 * Escopo regulatorio e gerencial nunca se misturam no mesmo balancete.
 */
public final class TrialBalance {

    public record Line(String accountCode, BigDecimal totalDebit, BigDecimal totalCredit) {
        public BigDecimal balance() { return totalDebit.subtract(totalCredit); }
    }

    /** month = primeiro mes do periodo; periodEnd = ultimo mes do periodo (iguais no mensal). */
    public record Balance(LocalDate month, LocalDate periodEnd, Scope scope, UUID contractId, UUID municipalityId, List<Line> lines) {
        public BigDecimal totalDebit() { return lines.stream().map(Line::totalDebit).reduce(BigDecimal.ZERO, BigDecimal::add); }
        public BigDecimal totalCredit() { return lines.stream().map(Line::totalCredit).reduce(BigDecimal.ZERO, BigDecimal::add); }
        /** Partida dobrada: todo balancete fecha debito == credito. */
        public boolean isBalanced() { return totalDebit().compareTo(totalCredit()) == 0; }
    }

    private TrialBalance() {}

    public static Balance monthly(List<JournalEntry> entries, LocalDate month, Scope scope, UUID contractId, UUID municipalityId) {
        requireMonth(month);
        return aggregate(entries, month, month, scope, contractId, municipalityId);
    }

    /** Balancete trimestral: agrega os 3 meses do trimestre (1..4) do exercicio. */
    public static Balance quarterly(List<JournalEntry> entries, int exercise, int quarter, Scope scope, UUID contractId, UUID municipalityId) {
        if (quarter < 1 || quarter > 4) throw new IllegalArgumentException("trimestre invalido (1..4): " + quarter);
        LocalDate from = LocalDate.of(exercise, (quarter - 1) * 3 + 1, 1);
        return aggregate(entries, from, from.plusMonths(2), scope, contractId, municipalityId);
    }

    /** Balancete anual (base das demonstracoes anuais): agrega os 12 meses do exercicio. */
    public static Balance annual(List<JournalEntry> entries, int exercise, Scope scope, UUID contractId, UUID municipalityId) {
        LocalDate from = LocalDate.of(exercise, 1, 1);
        return aggregate(entries, from, from.plusMonths(11), scope, contractId, municipalityId);
    }

    /** Balancete de um periodo arbitrario de meses (ambos dia 1, from <= to). */
    public static Balance period(List<JournalEntry> entries, LocalDate fromMonth, LocalDate toMonth, Scope scope, UUID contractId, UUID municipalityId) {
        requireMonth(fromMonth);
        requireMonth(toMonth);
        if (toMonth.isBefore(fromMonth)) throw new IllegalArgumentException("periodo invalido: " + fromMonth + " > " + toMonth);
        return aggregate(entries, fromMonth, toMonth, scope, contractId, municipalityId);
    }

    private static Balance aggregate(List<JournalEntry> entries, LocalDate fromMonth, LocalDate toMonth, Scope scope, UUID contractId, UUID municipalityId) {
        Objects.requireNonNull(entries, "lancamentos sao obrigatorios");
        Objects.requireNonNull(scope, "escopo e obrigatorio");
        Map<String, BigDecimal[]> byAccount = new LinkedHashMap<>();
        for (JournalEntry e : entries) {
            if (e.accountingMonth().isBefore(fromMonth) || e.accountingMonth().isAfter(toMonth) || e.scope() != scope) continue;
            if (contractId != null && !contractId.equals(e.contractId())) continue;
            if (municipalityId != null && !municipalityId.equals(e.municipalityId())) continue;
            byAccount.computeIfAbsent(e.debitAccountCode(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[0] =
                    byAccount.get(e.debitAccountCode())[0].add(e.amount());
            byAccount.computeIfAbsent(e.creditAccountCode(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[1] =
                    byAccount.get(e.creditAccountCode())[1].add(e.amount());
        }
        List<Line> lines = byAccount.entrySet().stream().map(en -> new Line(en.getKey(), en.getValue()[0], en.getValue()[1])).toList();
        return new Balance(fromMonth, toMonth, scope, contractId, municipalityId, lines);
    }

    private static void requireMonth(LocalDate month) {
        if (month == null || month.getDayOfMonth() != 1)
            throw new IllegalArgumentException("mes do balancete deve ser o dia 1: " + month);
    }
}
