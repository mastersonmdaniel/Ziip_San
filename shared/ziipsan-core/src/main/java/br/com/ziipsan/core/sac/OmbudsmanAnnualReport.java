package br.com.ziipsan.core.sac;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatorio anual da ouvidoria (ED-35; NR 11, art. 52, §2º): agregacao dos
 * casos do ano — total, respondidos no prazo, fora do prazo, pendentes e
 * prorrogacoes usadas. Sem nome de manifestante: o relatorio e agregado,
 * coerente com a preservacao de identidade.
 */
public final class OmbudsmanAnnualReport {

    public record CaseFact(LocalDate openedDate, LocalDate dueDate, LocalDate answeredDate,
                           boolean extended) {}

    public record Report(int year, long total, long answeredOnTime, long answeredLate,
                         long pending, long extensionsUsed) {}

    private OmbudsmanAnnualReport() {}

    public static Report ofYear(int year, List<CaseFact> facts) {
        List<CaseFact> mine = facts.stream().filter(f -> f.openedDate().getYear() == year).toList();
        long onTime = mine.stream().filter(f -> f.answeredDate() != null
                && !f.answeredDate().isAfter(f.dueDate())).count();
        long late = mine.stream().filter(f -> f.answeredDate() != null
                && f.answeredDate().isAfter(f.dueDate())).count();
        long pending = mine.stream().filter(f -> f.answeredDate() == null).count();
        long ext = mine.stream().filter(CaseFact::extended).count();
        return new Report(year, mine.size(), onTime, late, pending, ext);
    }
}
