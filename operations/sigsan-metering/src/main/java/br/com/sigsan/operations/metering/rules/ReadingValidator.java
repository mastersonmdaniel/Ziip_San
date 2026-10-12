package br.com.sigsan.operations.metering.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Validação do retorno de leitura (relatório 02, 2.4) e faixa de leitura esperada (2.5).
 *
 * <h2>Validação do retorno</h2>
 * Recusa a linha (e lista <b>todos</b> os motivos) quando: o leiturista não existe; a unidade não
 * existe; o tipo de medição é desconhecido; a data é ausente, futura, ou fora de
 * {@code [referência - N, referência + N]} <b>meses</b> (granularidade de mês: o relatório diz
 * "meses", não dias; o legado tem N = 1 fixo no código, aqui N é parâmetro do prestador); a leitura é negativa; há leitura &gt; 0 sem hidrômetro do tipo informado; a
 * anormalidade "sem hidrômetro" foi lançada para ligação com hidrômetro; o código de anormalidade
 * não existe; ou o tipo de medição (água/poço) foi informado sem hidrômetro instalado.
 *
 * <p>Pontos que o relatório deixa vagos e como foram resolvidos:
 * <ul>
 *   <li>"leitura &gt; 0 para ligação sem hidrômetro" e "água/poço informado sem hidrômetro
 *       instalado" se sobrepõem. A primeira vale quando há leitura &gt; 0; a segunda, quando não há
 *       leitura &gt; 0 e a linha também não traz a anormalidade "sem hidrômetro" (a única maneira
 *       legítima de reportar um imóvel sem medidor).</li>
 *   <li>"Indicador de confirmação inválido" não existe: é um booleano tipado.</li>
 *   <li>Leitura acima da capacidade do mostrador <b>não recusa</b> a linha (o cálculo a limita ao
 *       maior valor do mostrador); gera aviso.</li>
 * </ul>
 *
 * <h2>Faixa esperada</h2>
 * Ver {@link #expectedRange}. <b>Arredondamento:</b> cada ponta é arredondada para inteiro com
 * {@link java.math.RoundingMode#HALF_UP} (12,5 vira 13), sobre fatores {@link BigDecimal} exatos.
 */
public final class ReadingValidator {

    private ReadingValidator() {
    }

    public static ReadingValidationResult validate(ReadingSubmission submission, ReadingContext context,
                                                   LocalDate today, YearMonth billingReference,
                                                   int windowMonthsEachSide) {
        MeteringInputException.notNull(submission, "submission");
        MeteringInputException.notNull(context, "context");
        MeteringInputException.notNull(today, "today");
        MeteringInputException.notNull(billingReference, "billingReference");
        MeteringInputException.require(windowMonthsEachSide >= 0, MeteringErrorCode.INVALID_INPUT,
                "windowMonthsEachSide negativo");

        List<ReadingViolation> violations = new ArrayList<>();
        List<CalculationWarning> warnings = new ArrayList<>();

        if (!context.readerKnown()) {
            violations.add(ReadingViolation.READER_UNKNOWN);
        }
        boolean unitKnown = submission.unitId() != null && context.unitKnown();
        if (!unitKnown) {
            violations.add(ReadingViolation.UNIT_UNKNOWN);
        }
        if (submission.kind() == null) {
            violations.add(ReadingViolation.KIND_UNKNOWN);
        }

        LocalDate date = submission.readingDate();
        if (date == null) {
            violations.add(ReadingViolation.READING_DATE_MISSING);
        } else {
            if (date.isAfter(today)) {
                violations.add(ReadingViolation.READING_DATE_IN_FUTURE);
            }
            YearMonth month = YearMonth.from(date);
            if (month.isBefore(billingReference.minusMonths(windowMonthsEachSide))
                    || month.isAfter(billingReference.plusMonths(windowMonthsEachSide))) {
                violations.add(ReadingViolation.READING_DATE_OUTSIDE_WINDOW);
            }
        }

        Integer reading = submission.reading();
        if (reading != null && reading < 0) {
            violations.add(ReadingViolation.READING_NEGATIVE);
        }

        ReadingAnomalyRule anomaly = null;
        if (submission.anomalyCode() != null) {
            anomaly = context.anomaly();
            if (anomaly == null) {
                violations.add(ReadingViolation.ANOMALY_UNKNOWN);
            }
        }

        if (unitKnown && submission.kind() != null) {
            boolean noMeterAnomaly = anomaly != null && anomaly.forUnitWithoutMeter();
            if (!context.meterInstalledForKind()) {
                if (reading != null && reading > 0) {
                    violations.add(ReadingViolation.READING_WITHOUT_METER);
                } else if (!noMeterAnomaly) {
                    violations.add(ReadingViolation.METER_NOT_INSTALLED_FOR_KIND);
                }
            } else if (noMeterAnomaly) {
                violations.add(ReadingViolation.NO_METER_ANOMALY_WITH_METER);
            }
        }

        if (reading != null && context.meterDigits() != null && context.meterDigits() > 0
                && reading > dialMaximum(context.meterDigits())) {
            warnings.add(new CalculationWarning(WarningCode.READING_EXCEEDS_DIAL_CAPACITY,
                    "Leitura " + reading + " excede a capacidade do mostrador (" + context.meterDigits()
                            + " dígitos); o cálculo a limitará a " + dialMaximum(context.meterDigits())));
        }
        return new ReadingValidationResult(violations, warnings);
    }

    /**
     * Faixa de leitura esperada (UC0086 do legado, escrito do zero).
     *
     * <p>Cada ponta é calculada em {@link BigDecimal} exato, arredondada HALF_UP para inteiro e
     * levada para dentro do mostrador por módulo {@code 10^dígitos} (o relatório subtrai
     * {@code 10^dígitos} uma vez; o módulo é igual para valores abaixo de {@code 2 * 10^dígitos} e
     * correto acima disso).
     *
     * @param average         consumo médio em m³ (>= 0)
     * @param previousReading leitura anterior faturada (0 a 10^dígitos - 1)
     * @param digits          dígitos do mostrador (1 a 9)
     * @param policy          faixas do prestador
     */
    public static ExpectedReadingRange expectedRange(int average, int previousReading, int digits,
                                                     ExpectedRangePolicy policy) {
        MeteringInputException.require(average >= 0, MeteringErrorCode.INVALID_INPUT, "média negativa");
        MeteringInputException.require(digits >= 1 && digits <= 9, MeteringErrorCode.INVALID_INPUT,
                "dígitos do mostrador deve estar entre 1 e 9");
        MeteringInputException.require(previousReading >= 0 && previousReading <= dialMaximum(digits),
                MeteringErrorCode.INVALID_INPUT, "leitura anterior fora do mostrador de " + digits + " dígitos");
        MeteringInputException.notNull(policy, "policy");

        ExpectedRangePolicy.Band band = policy.bandFor(average);
        BigDecimal avg = BigDecimal.valueOf(average);
        BigDecimal low = BigDecimal.valueOf(previousReading).add(band.lowFactor().multiply(avg))
                .add(BigDecimal.valueOf(band.lowAddend()));
        BigDecimal high = BigDecimal.valueOf(previousReading).add(band.highFactor().multiply(avg))
                .add(BigDecimal.valueOf(band.highAddend()));
        return new ExpectedReadingRange(wrap(low, digits), wrap(high, digits), digits);
    }

    private static int wrap(BigDecimal value, int digits) {
        long rounded = value.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
        return (int) Math.floorMod(rounded, dialModulus(digits));
    }

    /** {@code 10^dígitos}: quantos valores o mostrador tem. */
    static long dialModulus(int digits) {
        long modulus = 1;
        for (int i = 0; i < digits; i++) {
            modulus *= 10;
        }
        return modulus;
    }

    /** Maior leitura que o mostrador mostra: {@code 10^dígitos - 1}. */
    static long dialMaximum(int digits) {
        return dialModulus(digits) - 1;
    }
}
