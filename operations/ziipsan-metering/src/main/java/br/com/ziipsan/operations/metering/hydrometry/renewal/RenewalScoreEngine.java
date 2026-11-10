package br.com.ziipsan.operations.metering.hydrometry.renewal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;

/**
 * Score DETERMINÍSTICO de prioridade de troca de cada hidrómetro (II-5,
 * critério B-4 do PDCA-01): soma ponderada de quatro componentes em
 * [0,1], com pesos e referências vindos de {@link RenewalScoreConfig}
 * (parâmetros — P-II5-PARAM). É a camada de PRIORIZAÇÃO que faltava à
 * ED-14: a {@code ReplacementRule} da ED-14 decide o disparo binário
 * (idade OU volume) e o {@code RenewalPlanCalculator} ordena por
 * custo×benefício; nenhum dos dois é alterado nem duplicado — este motor
 * pontua e o {@link AnnualRenewalPlan} selecciona.
 *
 * <p>Componentes (cada um em [0,1], divisão com escala 6, HALF_UP —
 * regra declarada, reproduzível à mão):
 * <ul>
 *   <li><b>AGE</b> — anos completos desde a data-base
 *       ({@link MeterRenewalSnapshot#ageBaseDate()}, mesma contagem de
 *       {@code ReplacementRule.ageYears} da ED-14) ÷
 *       {@code ageReferenceYears}, com teto 1;</li>
 *   <li><b>VOLUME</b> — volume totalizado ÷ limiar da classe
 *       ({@code meter.replace_by_volume_m3_by_class} da ED-14), teto 1;</li>
 *   <li><b>CLASS_TECHNOLOGY</b> — pontuação da chave de tecnologia/classe
 *       no mapa parametrizado;</li>
 *   <li><b>WATER_QUALITY</b> — agressividade [0,1] do retrato; quando a
 *       base não a expõe (hoje: não expõe por hidrómetro), componente 0
 *       e {@code waterQualityApplied=false} no resultado — o peso
 *       continua declarado na configuração, o critério fica inerte e
 *       VISÍVEL, nunca simulado.</li>
 * </ul>
 *
 * <p>Critério dominante = maior contribuição ponderada; empate resolve
 * pela ordem fixa AGE → VOLUME → CLASS_TECHNOLOGY → WATER_QUALITY
 * (declarada). Ordenação de scores iguais: total desc, depois
 * {@code meterNumber} asc, depois {@code meterId} asc — determinística
 * em qualquer JVM, sem relógio nem aleatoriedade.
 */
public final class RenewalScoreEngine {
    private RenewalScoreEngine() {}

    /** Critérios do score, na ordem fixa de desempate do dominante. */
    public enum Criterion { AGE, VOLUME, CLASS_TECHNOLOGY, WATER_QUALITY }

    /** Decomposição auditável do score: componentes crus e contribuições ponderadas. */
    public record ScoreBreakdown(BigDecimal ageComponent, BigDecimal volumeComponent,
                                 BigDecimal technologyComponent, BigDecimal waterQualityComponent,
                                 boolean waterQualityApplied,
                                 BigDecimal ageContribution, BigDecimal volumeContribution,
                                 BigDecimal technologyContribution, BigDecimal waterQualityContribution) {}

    /** Hidrômetro pontuado: retrato + decomposição + total + critério dominante. */
    public record ScoredMeter(MeterRenewalSnapshot snapshot, ScoreBreakdown breakdown,
                              BigDecimal totalScore, Criterion dominantCriterion) {}

    /** Ordem determinística de scores (sem relógio): total desc, número asc, id asc. */
    public static final Comparator<ScoredMeter> SCORE_ORDER =
            Comparator.comparing(ScoredMeter::totalScore).reversed()
                    .thenComparing(s -> s.snapshot().meterNumber())
                    .thenComparing(s -> s.snapshot().meterId());

    private static final int COMPONENT_SCALE = 6;

    public static ScoredMeter score(MeterRenewalSnapshot snapshot, RenewalScoreConfig config, LocalDate reference) {
        if (snapshot == null) throw new IllegalArgumentException("Retrato do hidrômetro é obrigatório");
        if (config == null) throw new IllegalArgumentException("Configuração do score é obrigatória");
        if (reference == null) throw new IllegalArgumentException("Data de referência do score é obrigatória");

        // AGE — anos completos sobre a referência parametrizada, teto 1.
        LocalDate base = snapshot.ageBaseDate();
        if (base == null) throw new IllegalArgumentException(
            "Hidrômetro " + snapshot.meterNumber() + " sem installedOn nem manufactureYear: idade não calculável");
        if (reference.isBefore(base)) throw new IllegalArgumentException(
            "Data de referência " + reference + " anterior à base de idade " + base
            + " do hidrômetro " + snapshot.meterNumber());
        int fullYears = Period.between(base, reference).getYears();
        BigDecimal ageComponent = ratio(BigDecimal.valueOf(fullYears),
                BigDecimal.valueOf(config.ageReferenceYears()));

        // VOLUME — totalizado sobre o limiar da classe (parâmetro da ED-14), teto 1.
        BigDecimal volumeComponent = ratio(BigDecimal.valueOf(snapshot.totalizedVolumeM3()),
                BigDecimal.valueOf(config.volumeThresholdM3(snapshot.accuracyClass())));

        // CLASS_TECHNOLOGY — mapa parametrizado; chave sem pontuação é erro explícito.
        BigDecimal technologyComponent = config.technologyScore(snapshot.technologyKey());

        // WATER_QUALITY — inerte (0, aplicado=false) enquanto a base não a expuser.
        boolean waterApplied = snapshot.waterQualityAggressiveness() != null;
        BigDecimal waterComponent = waterApplied ? snapshot.waterQualityAggressiveness() : BigDecimal.ZERO;

        RenewalScoreConfig.RenewalWeights w = config.weights();
        BigDecimal ageContribution = w.age().multiply(ageComponent);
        BigDecimal volumeContribution = w.volume().multiply(volumeComponent);
        BigDecimal technologyContribution = w.classTechnology().multiply(technologyComponent);
        BigDecimal waterContribution = w.waterQuality().multiply(waterComponent);
        BigDecimal total = ageContribution.add(volumeContribution)
                .add(technologyContribution).add(waterContribution);

        Criterion dominant = Criterion.AGE;
        BigDecimal best = ageContribution;
        if (volumeContribution.compareTo(best) > 0) { dominant = Criterion.VOLUME; best = volumeContribution; }
        if (technologyContribution.compareTo(best) > 0) { dominant = Criterion.CLASS_TECHNOLOGY; best = technologyContribution; }
        if (waterContribution.compareTo(best) > 0) { dominant = Criterion.WATER_QUALITY; }

        return new ScoredMeter(snapshot,
                new ScoreBreakdown(ageComponent, volumeComponent, technologyComponent, waterComponent,
                        waterApplied, ageContribution, volumeContribution, technologyContribution, waterContribution),
                total, dominant);
    }

    /** Razão com teto 1 e escala declarada (6, HALF_UP) — a única divisão do motor. */
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        BigDecimal r = numerator.divide(denominator, COMPONENT_SCALE, RoundingMode.HALF_UP);
        return r.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : r;
    }
}
