package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Biblioteca de regras de suspeita (GES-25, aceite da ED-95) sobre
 * consumo e cadastro, com limiares por tenant de
 * {@code fraud.rules} (ver {@link LossParameters.FraudRuleThresholds}
 * — nenhum limiar tem valor de fabrica; regra nao habilitada pelo
 * tenant nao dispara, regra habilitada sem limiar e erro explicito).
 *
 * <p><b>A saida e RECOMENDACAO a humano</b> (ADR-0042): {@link Suspicion}
 * nao carrega valor, debito, corte nem infracao — carrega regra,
 * evidencia e a recomendacao fixa de inspecao. Nenhum metodo desta
 * classe (nem do servico) lanca debito, ordena corte ou emite auto:
 * o auto de infracao e da ED-24, com defesa e decisao antes do
 * debito ({@code infraction.charge_timing}). O teste nomeado do
 * aceite ("nenhuma regra gera debito nem corte sem decisao humana
 * registrada") esta em {@code NoAutomaticDebitOrCutTest}.
 *
 * <p>Regras (as oito do aceite):
 * <ul>
 *   <li>ZERO_CONSUMPTION_ACTIVE — consumo zero com ligacao ativa por
 *       N ciclos consecutivos;</li>
 *   <li>ABRUPT_DROP — queda abrupta: ultimo ciclo cai pelo menos X%
 *       contra a media dos ciclos anteriores;</li>
 *   <li>PER_ECONOMY — consumo por economia acima do teto;</li>
 *   <li>PER_AREA — consumo por m2 de area construida acima do teto;</li>
 *   <li>STALLED_METER — medidor parado: telemetria (ED-17) registra
 *       fluxo no periodo e a leitura do hidrometro nao avancou nas
 *       ultimas N leituras (sem telemetria, a regra nao dispara);</li>
 *   <li>UNDERSIZED_METER — medidor subdimensionado: vazao de pico
 *       observada atinge pelo menos X% da vazao nominal (fato da
 *       hidrometria, ED-14);</li>
 *   <li>METER_REPLACEMENT_ANOMALY — troca de hidrometro (ED-14)
 *       recente com queda de consumo depois da troca de pelo menos
 *       X%;</li>
 *   <li>REPEATED_READING — leitura repetida: as ultimas N leituras
 *       absolutas sao identicas (leitura nao avancou ciclo apos
 *       ciclo, sem telemetria envolvida).</li>
 * </ul>
 */
public final class FraudRules {

    private FraudRules() {
    }

    public enum Rule {
        ZERO_CONSUMPTION_ACTIVE, ABRUPT_DROP, PER_ECONOMY, PER_AREA,
        STALLED_METER, UNDERSIZED_METER, METER_REPLACEMENT_ANOMALY, REPEATED_READING
    }

    /** Unico efeito automatico possivel de uma suspeita: nenhum. */
    public enum AutomaticEffect {
        NONE
    }

    /** Recomendacao fixa da biblioteca: inspecao decidida por humano. */
    public enum Recommendation {
        HUMAN_INSPECTION_REVIEW
    }

    /**
     * Suspeita = recomendacao a humano. Por construcao, o record NAO
     * tem campo de valor, debito, corte ou infracao.
     *
     * <p>Correcao D1 (revisao da ED-95): a suspeita carrega o estado da
     * sua decisao — {@code decidedBy}/{@code decidedAt}/
     * {@code inspectionWorkOrderId}, as tres colunas que o SQL ja
     * previa em {@code loss_suspicions}. Ou as tres sao nulas
     * (pendente de decisao) ou as tres estao presentes (decidida, com
     * a OS de fiscalizacao que a decisao gerou); estado parcial e
     * recusado. A transicao {@link #decidedWithInspection} recusa a
     * segunda decisao, espelhando a guarda de
     * {@code ApparentLossService.decideFinding}.
     */
    public record Suspicion(
            UUID id, UUID tenantId, UUID unitId, Rule rule, String evidence,
            LocalDateTime evaluatedAt, Recommendation recommendation, AutomaticEffect automaticEffect,
            UUID decidedBy, LocalDateTime decidedAt, UUID inspectionWorkOrderId) {

        public Suspicion {
            Objects.requireNonNull(id, "id da suspeita e obrigatorio");
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            Objects.requireNonNull(unitId, "unidade e obrigatoria");
            Objects.requireNonNull(rule, "regra e obrigatoria");
            if (evidence == null || evidence.isBlank())
                throw new IllegalArgumentException("suspeita sem evidencia nao se registra (GES-25)");
            Objects.requireNonNull(evaluatedAt, "avaliacao exige data/hora");
            if (recommendation != Recommendation.HUMAN_INSPECTION_REVIEW)
                throw new IllegalArgumentException("a unica saida da biblioteca e recomendacao a humano (ADR-0042)");
            if (automaticEffect != AutomaticEffect.NONE)
                throw new IllegalArgumentException("suspeita nao tem efeito automatico (ADR-0042)");
            boolean anyDecision = decidedBy != null || decidedAt != null || inspectionWorkOrderId != null;
            boolean fullDecision = decidedBy != null && decidedAt != null && inspectionWorkOrderId != null;
            if (anyDecision && !fullDecision)
                throw new IllegalArgumentException("decisao sobre a suspeita e integral ou ausente: "
                        + "decidedBy, decidedAt e inspectionWorkOrderId andam juntos");
        }

        /** Construtor pendente (pre-D1): suspeita ainda sem decisao registrada. */
        public Suspicion(UUID id, UUID tenantId, UUID unitId, Rule rule, String evidence,
                         LocalDateTime evaluatedAt, Recommendation recommendation,
                         AutomaticEffect automaticEffect) {
            this(id, tenantId, unitId, rule, evidence, evaluatedAt, recommendation, automaticEffect,
                    null, null, null);
        }

        /** Verdadeiro quando a decisao humana sobre esta suspeita ja foi registrada. */
        public boolean decided() {
            return inspectionWorkOrderId != null;
        }

        /**
         * Transicao unica para decidida: registra quem decidiu, quando e
         * a OS de fiscalizacao gerada. Segunda decisao e recusada.
         */
        public Suspicion decidedWithInspection(CrossMatch.HumanDecision decision, UUID generatedWorkOrderId) {
            Objects.requireNonNull(decision, "decisao humana registrada e obrigatoria");
            Objects.requireNonNull(generatedWorkOrderId, "OS de fiscalizacao gerada pela decisao e obrigatoria");
            if (decided())
                throw new IllegalStateException("suspeita ja decidida (OS " + inspectionWorkOrderId
                        + "): segunda decisao recusada: " + id);
            return new Suspicion(id, tenantId, unitId, rule, evidence, evaluatedAt,
                    recommendation, automaticEffect,
                    decision.decidedBy(), decision.decidedAt(), generatedWorkOrderId);
        }

        static Suspicion of(UUID tenantId, UUID unitId, Rule rule, String evidence, LocalDateTime at) {
            return new Suspicion(UUID.randomUUID(), tenantId, unitId, rule, evidence, at,
                    Recommendation.HUMAN_INSPECTION_REVIEW, AutomaticEffect.NONE, null, null, null);
        }
    }

    /** Avalia TODAS as regras habilitadas sobre o retrato da unidade. */
    public static List<Suspicion> evaluate(UnitFacts facts, LossParameters.FraudRuleThresholds t,
                                           LocalDateTime evaluatedAt) {
        Objects.requireNonNull(facts, "retrato da unidade e obrigatorio");
        Objects.requireNonNull(t, "limiares fraud.rules sao obrigatorios");
        Objects.requireNonNull(evaluatedAt, "data/hora da avaliacao e obrigatoria");
        t.requireThresholds();
        List<Suspicion> out = new ArrayList<>();
        List<BigDecimal> consumptions = facts.monthlyConsumptionsM3();
        List<BigDecimal> readings = facts.meterReadings();

        if (t.zeroConsumptionEnabled()
                && facts.connectionStatus() == UnitFacts.ConnectionStatus.ACTIVE
                && consumptions.size() >= t.zeroMinConsecutiveCycles()) {
            List<BigDecimal> tail = tail(consumptions, t.zeroMinConsecutiveCycles());
            if (tail.stream().allMatch(c -> c.signum() == 0)) {
                out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.ZERO_CONSUMPTION_ACTIVE,
                        "consumo zero nos ultimos " + t.zeroMinConsecutiveCycles()
                                + " ciclos com ligacao ativa", evaluatedAt));
            }
        }

        if (t.abruptDropEnabled() && consumptions.size() >= 2) {
            BigDecimal last = consumptions.get(consumptions.size() - 1);
            BigDecimal baseline = average(consumptions.subList(0, consumptions.size() - 1));
            if (baseline.signum() > 0) {
                BigDecimal dropPct = baseline.subtract(last).multiply(BigDecimal.valueOf(100))
                        .divide(baseline, 4, RoundingMode.HALF_UP);
                if (dropPct.compareTo(t.abruptDropMinPercent()) >= 0) {
                    out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.ABRUPT_DROP,
                            "queda de " + dropPct + "% do ultimo ciclo (" + last
                                    + " m3) contra a media anterior (" + baseline + " m3); limiar do tenant "
                                    + t.abruptDropMinPercent() + "%", evaluatedAt));
                }
            }
        }

        facts.lastConsumptionM3().ifPresent(last -> {
            if (t.perEconomyEnabled()) {
                BigDecimal perEconomy = last.divide(BigDecimal.valueOf(facts.economies()), 4, RoundingMode.HALF_UP);
                if (perEconomy.compareTo(t.perEconomyMaxM3()) > 0) {
                    out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.PER_ECONOMY,
                            "consumo por economia " + perEconomy + " m3 acima do teto do tenant "
                                    + t.perEconomyMaxM3() + " m3 (ciclo " + last + " m3, "
                                    + facts.economies() + " economias)", evaluatedAt));
                }
            }
            if (t.perAreaEnabled() && facts.declaredBuiltAreaSquareMeters() != null
                    && facts.declaredBuiltAreaSquareMeters().signum() > 0) {
                BigDecimal perArea = last.divide(facts.declaredBuiltAreaSquareMeters(), 6, RoundingMode.HALF_UP);
                if (perArea.compareTo(t.perAreaMaxM3PerSquareMeter()) > 0) {
                    out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.PER_AREA,
                            "consumo por area " + perArea + " m3/m2 acima do teto do tenant "
                                    + t.perAreaMaxM3PerSquareMeter() + " m3/m2 (ciclo " + last + " m3, area "
                                    + facts.declaredBuiltAreaSquareMeters() + " m2)", evaluatedAt));
                }
            }
        });

        if (t.stalledMeterEnabled() && facts.telemetryAvailable()
                && facts.telemetryPeriodFlowM3() != null && facts.telemetryPeriodFlowM3().signum() > 0
                && readings.size() >= t.stalledMinIdenticalReadings()) {
            List<BigDecimal> tail = tail(readings, t.stalledMinIdenticalReadings());
            if (allEqual(tail)) {
                out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.STALLED_METER,
                        "telemetria da ED-17 registrou fluxo de " + facts.telemetryPeriodFlowM3()
                                + " m3 no periodo e a leitura do hidrometro nao avancou nas ultimas "
                                + t.stalledMinIdenticalReadings() + " leituras (" + tail.get(0) + ")",
                        evaluatedAt));
            }
        }

        if (t.undersizedMeterEnabled() && facts.meterNominalFlowM3PerHour() != null
                && facts.meterNominalFlowM3PerHour().signum() > 0
                && facts.observedPeakFlowM3PerHour() != null) {
            BigDecimal pct = facts.observedPeakFlowM3PerHour().multiply(BigDecimal.valueOf(100))
                    .divide(facts.meterNominalFlowM3PerHour(), 4, RoundingMode.HALF_UP);
            if (pct.compareTo(t.undersizedMinPeakPercent()) >= 0) {
                out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.UNDERSIZED_METER,
                        "vazao de pico observada " + facts.observedPeakFlowM3PerHour()
                                + " m3/h atinge " + pct + "% da vazao nominal do hidrometro "
                                + facts.meterNominalFlowM3PerHour() + " m3/h; limiar do tenant "
                                + t.undersizedMinPeakPercent() + "% (dimensionamento da ED-14)", evaluatedAt));
            }
        }

        if (t.meterReplacementEnabled() && facts.cyclesSinceMeterReplacement() != null
                && facts.cyclesSinceMeterReplacement() <= t.replacementMaxCyclesAgo()
                && facts.avgConsumptionBeforeReplacementM3() != null
                && facts.avgConsumptionBeforeReplacementM3().signum() > 0
                && facts.avgConsumptionAfterReplacementM3() != null) {
            BigDecimal dropPct = facts.avgConsumptionBeforeReplacementM3()
                    .subtract(facts.avgConsumptionAfterReplacementM3()).multiply(BigDecimal.valueOf(100))
                    .divide(facts.avgConsumptionBeforeReplacementM3(), 4, RoundingMode.HALF_UP);
            if (dropPct.compareTo(t.replacementMinDropPercent()) >= 0) {
                out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.METER_REPLACEMENT_ANOMALY,
                        "troca de hidrometro ha " + facts.cyclesSinceMeterReplacement()
                                + " ciclos (ED-14) com queda de " + dropPct + "% do consumo medio (de "
                                + facts.avgConsumptionBeforeReplacementM3() + " para "
                                + facts.avgConsumptionAfterReplacementM3() + " m3); limiar do tenant "
                                + t.replacementMinDropPercent() + "%", evaluatedAt));
            }
        }

        if (t.repeatedReadingEnabled() && readings.size() >= t.repeatedMinConsecutiveReadings()) {
            List<BigDecimal> tail = tail(readings, t.repeatedMinConsecutiveReadings());
            if (allEqual(tail)) {
                out.add(Suspicion.of(facts.tenantId(), facts.unitId(), Rule.REPEATED_READING,
                        "leitura absoluta repetida nas ultimas " + t.repeatedMinConsecutiveReadings()
                                + " leituras (" + tail.get(0) + ") — leitura nao avancou ciclo apos ciclo",
                        evaluatedAt));
            }
        }

        return List.copyOf(out);
    }

    private static List<BigDecimal> tail(List<BigDecimal> values, int n) {
        return values.subList(values.size() - n, values.size());
    }

    private static boolean allEqual(List<BigDecimal> values) {
        return values.stream().allMatch(v -> v.compareTo(values.get(0)) == 0);
    }

    private static BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }
}
