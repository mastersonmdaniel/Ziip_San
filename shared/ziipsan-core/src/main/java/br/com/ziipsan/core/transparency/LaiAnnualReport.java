package br.com.ziipsan.core.transparency;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Relatorio estatistico anual da LAI (ED-99; CAD-77): agrega os pedidos
 * do ano pela data de abertura — SOMENTE numeros. Nenhum nome, documento
 * ou conteudo de pedido entra no relatorio: a estatistica e agregada por
 * construcao (a lista de {@link LaiRequest} nao carrega identidade).
 *
 * <p>Contagens derivadas apenas do estado registrado nos pedidos:
 * recebidos, respondidos (no prazo / fora do prazo), pendentes vencidos
 * na data de referencia, prorrogacoes usadas, desfechos da resposta,
 * recursos e decisoes de recurso (no prazo / fora do prazo) e recursos
 * pendentes vencidos. Nenhum numero e estimado: pedido sem resposta nao
 * conta como respondido, em nenhum sentido.
 */
public record LaiAnnualReport(int year,
                              long received,
                              long answered,
                              long answeredWithinDeadline,
                              long answeredLate,
                              long pendingOverdue,
                              long extensionsUsed,
                              long granted,
                              long partiallyGranted,
                              long denied,
                              long appealsFiled,
                              long appealsDecided,
                              long appealsDecidedWithinDeadline,
                              long appealsPendingOverdue) {

    public static LaiAnnualReport ofYear(int year, List<LaiRequest> requests, LocalDate referenceDate) {
        Objects.requireNonNull(requests, "pedidos obrigatorios");
        Objects.requireNonNull(referenceDate, "data de referencia obrigatoria");
        long received = 0, answered = 0, within = 0, late = 0, pendingOverdue = 0, extensions = 0;
        long granted = 0, partial = 0, denied = 0;
        long appealsFiled = 0, appealsDecided = 0, appealsWithin = 0, appealsPendingOverdue = 0;
        for (LaiRequest r : requests) {
            if (r.openedDate().getYear() != year) continue;
            received++;
            if (r.extended()) extensions++;
            if (r.answeredDate() != null) {
                answered++;
                if (r.answeredWithinDeadline()) within++; else late++;
                switch (r.answerOutcome()) {
                    case GRANTED -> granted++;
                    case PARTIALLY_GRANTED -> partial++;
                    case DENIED -> denied++;
                }
            } else if (r.responseOverdueOn(referenceDate)) {
                pendingOverdue++;
            }
            if (r.appealDate() != null) {
                appealsFiled++;
                if (r.appealDecidedDate() != null) {
                    appealsDecided++;
                    if (r.appealDecidedWithinDeadline()) appealsWithin++;
                } else if (r.appealDecisionOverdueOn(referenceDate)) {
                    appealsPendingOverdue++;
                }
            }
        }
        return new LaiAnnualReport(year, received, answered, within, late, pendingOverdue,
                extensions, granted, partial, denied,
                appealsFiled, appealsDecided, appealsWithin, appealsPendingOverdue);
    }
}
