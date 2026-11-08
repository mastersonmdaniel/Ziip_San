package br.com.ziipsan.commercial.collection.cutoff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Planejamento diario das OS de corte (ED-29): ordena as candidatas
 * pela politica de prioridade do tenant (TR-409/SG-393) e aplica a
 * cota diaria (TR-410/SG-394, {@code cut.daily_order_limit}).
 *
 * <p>Quem entra como candidata ja passou pela selecao minima e pelos
 * bloqueios da P3.4 e pelo portao de geracao — esta classe NAO
 * seleciona nem libera corte: ela ordena e corta a fila na cota. As
 * excedentes nao sao descartadas: voltam como adiadas, com o motivo
 * registrado (a geracao do dia seguinte as reavalia pelas regras da
 * P3.4, inclusive revalidacao).
 *
 * <p>Equipes especiais (TR-529/SG-486; 18f-ENT-04): candidata marcada
 * {@code specialCrewOnly} so pode ser atribuida a equipe especial —
 * {@link #assertCrewAllowed} e o portao; a atribuicao em si e do
 * agendamento da ED-36.
 */
public final class CutoffDailyPlanner {

    private CutoffDailyPlanner() {
    }

    /**
     * Candidata a OS de corte do dia. Os campos sao os criterios de
     * TR-409: localizacao (sequencia na rota declarada pelo cadastro),
     * vencimento (a divida motivadora mais antiga), valor total, tarifa,
     * categoria e perfil do devedor.
     */
    public record Candidate(
            UUID unitId,
            CutoffLevel level,
            int routeSequence,
            LocalDate oldestDueDate,
            BigDecimal totalAmount,
            String tariff,
            String category,
            String debtorProfile,
            boolean specialCrewOnly) {
        public Candidate {
            if (unitId == null || level == null || oldestDueDate == null || totalAmount == null) {
                throw new IllegalArgumentException(
                        "candidata exige unidade, nivel, vencimento mais antigo e valor total");
            }
            if (totalAmount.signum() < 0) {
                throw new IllegalArgumentException("valor total da candidata nao pode ser negativo");
            }
        }
    }

    /** Candidata planejada, na posicao da fila do dia (1 = primeira). */
    public record Planned(Candidate candidate, int position) {
    }

    /** Candidata adiada pela cota, com o motivo registrado. */
    public record Deferred(Candidate candidate, String reason) {
    }

    public record DailyPlan(List<Planned> planned, List<Deferred> deferred, LocalDate date, int dailyLimit) {
    }

    /** Comparador da politica de prioridade declarada (lexicografico sobre a ordem do tenant). */
    public static Comparator<Candidate> priorityComparator(CutoffExtParameters.PriorityPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("politica de prioridade e obrigatoria");
        }
        Comparator<Candidate> result = null;
        for (CutoffExtParameters.PriorityFactor factor : policy.order()) {
            Comparator<Candidate> byFactor = switch (factor) {
                case LOCATION -> Comparator.comparingInt(Candidate::routeSequence);
                case DUE_DATE -> Comparator.comparing(Candidate::oldestDueDate);
                case AMOUNT -> Comparator.comparing(Candidate::totalAmount).reversed();
                case TARIFF -> Comparator.comparingInt(c -> policy.rankOf(factor, c.tariff()));
                case CATEGORY -> Comparator.comparingInt(c -> policy.rankOf(factor, c.category()));
                case DEBTOR_PROFILE -> Comparator.comparingInt(c -> policy.rankOf(factor, c.debtorProfile()));
            };
            result = result == null ? byFactor : result.thenComparing(byFactor);
        }
        // Desempate final deterministico (declarado): unidade, para a
        // fila nao depender da ordem de entrada quando tudo empata.
        return result.thenComparing(c -> c.unitId().toString());
    }

    /** Ordena e aplica a cota diaria; excedentes voltam adiadas com motivo. */
    public static DailyPlan plan(List<Candidate> candidates, CutoffExtParameters extParams, LocalDate date) {
        if (candidates == null || extParams == null || date == null) {
            throw new IllegalArgumentException("candidatas, parametros e data sao obrigatorios");
        }
        int limit = extParams.dailyOrderLimit();
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(priorityComparator(extParams.priorityPolicy()));
        List<Planned> planned = new ArrayList<>();
        List<Deferred> deferred = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (i < limit) {
                planned.add(new Planned(sorted.get(i), i + 1));
            } else {
                deferred.add(new Deferred(sorted.get(i), "DAILY_QUOTA_EXCEEDED"));
            }
        }
        return new DailyPlan(List.copyOf(planned), List.copyOf(deferred), date, limit);
    }

    /**
     * Portao de equipe especial (TR-529/SG-486): ordem de cliente
     * restrito atribuida a equipe comum recusa; os demais casos passam.
     */
    public static void assertCrewAllowed(Candidate candidate, boolean crewIsSpecial) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidata e obrigatoria");
        }
        if (candidate.specialCrewOnly() && !crewIsSpecial) {
            throw new IllegalStateException(
                    "OS de corte desta unidade so pode ser atribuida a equipe especial (TR-529/SG-486)");
        }
    }
}
