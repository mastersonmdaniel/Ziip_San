package br.com.ziipsan.intelligence.gis.drain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plano de operação e manutenção da drenagem (aceite da ED-64):
 * periodicidade e critérios POR CLASSE DE ELEMENTO, conforme a NR
 * 12/ANA (Res. ANA 245/2025, arts. 11 e 12 — a norma exige o plano
 * com periodicidade por classe; ela NÃO fixa os meses neste pacote:
 * os intervalos são do tenant, no parâmetro
 * {@code drain.cleaning_interval_by_class}).
 *
 * <p><b>Nenhum intervalo de fábrica é semeado.</b> Sem o parâmetro do
 * tenant, elemento nenhum ganha data de limpeza — {@link #nextDue}
 * devolve vazio e o gerador de OS recorrentes pula a classe,
 * registrando o motivo. Inventar "limpar boca de lobo a cada N
 * meses" seria dado sem fonte, vedado pelo método do projeto.
 *
 * <p>Critérios além do calendário (o plano combina os dois, como a
 * NR pede "periodicidade E critérios"): obstrução acima do limiar do
 * plano e conservação POOR/CRITICAL antecipam a limpeza
 * ({@link #isDueByCriteria}) — os limiares também são do plano do
 * tenant, gravados na linha do plano, nunca constantes desta classe.
 *
 * <p>A OS em si é do núcleo de OS (ED-36): esta classe/serviço geram
 * o PEDIDO recorrente (gis_drain_cleaning_request); o
 * {@code service_order_id} é preenchido na integração, como o pedido
 * de limpeza da ED-65.
 */
public final class GisDrainMaintenance {

    /** Uma linha do plano: classe, intervalo (meses) e critérios. */
    public record CleaningPlanLine(String elementClass, Integer intervalMonths,
                                   Double obstructionThresholdPct,
                                   boolean includePoorOrCritical) {
        public CleaningPlanLine {
            if (elementClass == null
                    || !GisDrainCatalog.CLEANING_ELEMENT_CLASSES.contains(elementClass)) {
                throw new IllegalArgumentException(
                        "classe de elemento fora do plano de drenagem: " + elementClass);
            }
            if (intervalMonths != null && intervalMonths <= 0) {
                throw new IllegalArgumentException(
                        "intervalo de limpeza deve ser positivo (meses): " + elementClass);
            }
            if (obstructionThresholdPct != null) {
                GisDrainRules.ensureObstructionPct(obstructionThresholdPct);
            }
        }

        /** A classe tem periodicidade definida? (sem ela, sem OS recorrente) */
        public boolean hasInterval() {
            return intervalMonths != null;
        }
    }

    /** Estado de vencimento de um elemento frente ao plano. */
    public enum DueStatus { NOT_SCHEDULED, SCHEDULED, DUE, OVERDUE }

    private GisDrainMaintenance() {
    }

    /**
     * Lê o parâmetro {@code drain.cleaning_interval_by_class} (mapa
     * classe → meses, JSON do mecanismo de parâmetros) como linhas de
     * plano SEM critérios. Classe fora da lista da GIS-13 no mapa =
     * erro de configuração do tenant, acusado — nunca ignorado em
     * silêncio. Mapa vazio/nulo = plano sem periodicidade (válido:
     * a geração não produz OS e o relatório diz por quê).
     */
    public static List<CleaningPlanLine> planFromIntervalParameter(
            Map<String, Integer> intervalByClass) {
        if (intervalByClass == null || intervalByClass.isEmpty()) {
            return List.of();
        }
        return intervalByClass.entrySet().stream()
                .map(e -> new CleaningPlanLine(e.getKey(), e.getValue(), null, false))
                .toList();
    }

    /**
     * Próxima limpeza devida pelo calendário: última limpeza +
     * intervalo da classe. Sem última limpeza conhecida, a primeira
     * devida é a data-base do plano ({@code planStart}) + intervalo.
     * Sem intervalo para a classe: vazio (NOT_SCHEDULED).
     */
    public static Optional<LocalDate> nextDue(CleaningPlanLine line,
                                              LocalDate lastCleanedOn,
                                              LocalDate planStart) {
        if (!line.hasInterval()) {
            return Optional.empty();
        }
        LocalDate base = lastCleanedOn != null ? lastCleanedOn : planStart;
        if (base == null) {
            return Optional.empty();
        }
        return Optional.of(base.plusMonths(line.intervalMonths()));
    }

    /** Classifica o vencimento de um elemento numa data de referência. */
    public static DueStatus dueStatus(CleaningPlanLine line, LocalDate lastCleanedOn,
                                      LocalDate planStart, LocalDate referenceDate) {
        Optional<LocalDate> due = nextDue(line, lastCleanedOn, planStart);
        if (due.isEmpty()) {
            return DueStatus.NOT_SCHEDULED;
        }
        if (referenceDate.isBefore(due.get())) {
            return DueStatus.SCHEDULED;
        }
        return referenceDate.isEqual(due.get()) ? DueStatus.DUE : DueStatus.OVERDUE;
    }

    /**
     * Critério de antecipação do plano: obstrução ≥ limiar do plano,
     * ou conservação POOR/CRITICAL quando o plano inclui o critério.
     * Sem limiar e sem o critério ligados, nenhum elemento é devido
     * por critério — só pelo calendário.
     */
    public static boolean isDueByCriteria(CleaningPlanLine line, Double obstructionPct,
                                          String conditionRating) {
        if (line.obstructionThresholdPct() != null && obstructionPct != null
                && obstructionPct >= line.obstructionThresholdPct()) {
            return true;
        }
        return line.includePoorOrCritical()
                && ("POOR".equals(conditionRating) || "CRITICAL".equals(conditionRating));
    }

    // ------------------------------------------- indicadores (OPE-32)

    /**
     * Indicadores operacionais da drenagem (OPE-32): fórmulas puras
     * sobre contagens do período — o serviço lê as contagens do
     * banco e esta classe calcula, para o cálculo ser testável sem
     * banco. Denominador zero devolve {@code null} (indicador sem
     * base no período), nunca 0% ou 100% inventados.
     */
    public record DrainIndicators(
            long floodOccurrences,
            Double avgRainMmPerFloodPeriod,
            long inspectionsInPeriod,
            Double inspectedNetworkPct,
            long cleaningRequestsDue,
            long cleaningRequestsDone,
            Double cleaningCompletionPct,
            Double poorOrCriticalPct,
            Double avgObstructionPct) {
    }

    public static DrainIndicators indicators(
            long floodOccurrences, List<Double> rainPerFloodPeriodMm,
            long inspectionsInPeriod, long networkElementsInService,
            long inspectedElementsDistinct,
            long cleaningRequestsDue, long cleaningRequestsDone,
            long elementsWithCondition, long elementsPoorOrCritical,
            List<Double> obstructionSamplesPct) {
        Double avgRain = null;
        List<Double> rainKnown = rainPerFloodPeriodMm == null ? List.of()
                : rainPerFloodPeriodMm.stream().filter(java.util.Objects::nonNull).toList();
        if (!rainKnown.isEmpty()) {
            avgRain = rainKnown.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }
        Double inspectedPct = networkElementsInService <= 0 ? null
                : 100.0 * inspectedElementsDistinct / networkElementsInService;
        Double completionPct = cleaningRequestsDue <= 0 ? null
                : 100.0 * cleaningRequestsDone / cleaningRequestsDue;
        Double poorPct = elementsWithCondition <= 0 ? null
                : 100.0 * elementsPoorOrCritical / elementsWithCondition;
        Double avgObstruction = (obstructionSamplesPct == null || obstructionSamplesPct.isEmpty())
                ? null
                : obstructionSamplesPct.stream().mapToDouble(Double::doubleValue)
                        .average().orElse(0);
        return new DrainIndicators(floodOccurrences, avgRain, inspectionsInPeriod,
                inspectedPct, cleaningRequestsDue, cleaningRequestsDone, completionPct,
                poorPct, avgObstruction);
    }
}
