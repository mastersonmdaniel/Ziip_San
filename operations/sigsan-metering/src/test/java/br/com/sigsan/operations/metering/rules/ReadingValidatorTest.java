package br.com.sigsan.operations.metering.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validação do retorno de leitura (relatório 02, 2.4). Datas e valores FICTÍCIOS. A referência de
 * faturamento é out/2026 e a janela aceita leituras de set/2026 a nov/2026 (meses).
 */
class ReadingValidatorTest {

    private static final YearMonth REF = YearMonth.of(2026, 10);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate LATE_TODAY = LocalDate.of(2026, 12, 31);
    /** Janela de 1 mês para cada lado (o valor ilustrativo do relatório 02; em produção é do prestador). */
    private static final int WINDOW = 1;
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static ReadingSubmission submission(LocalDate date, Integer reading, String anomalyCode) {
        return new ReadingSubmission(UNIT, MeasurementKind.WATER, date, reading, anomalyCode);
    }

    private static ReadingContext context() {
        return new ReadingContext(true, true, true, 5, null);
    }

    private static ReadingValidationResult validate(ReadingSubmission s, ReadingContext c) {
        return ReadingValidator.validate(s, c, TODAY, REF, WINDOW);
    }

    @Test
    @DisplayName("linha correta: aceita, sem violações nem avisos")
    void validLine() {
        ReadingValidationResult r = validate(submission(LocalDate.of(2026, 10, 15), 1234, null), context());
        assertThat(r.accepted()).isTrue();
        assertThat(r.violations()).isEmpty();
        assertThat(r.warnings()).isEmpty();
    }

    @Test
    @DisplayName("leiturista inexistente")
    void unknownReader() {
        ReadingValidationResult r = validate(submission(LocalDate.of(2026, 10, 15), 1, null),
                new ReadingContext(false, true, true, 5, null));
        assertThat(r.violations()).containsExactly(ReadingViolation.READER_UNKNOWN);
    }

    @Test
    @DisplayName("unidade inexistente (sem id, ou id que o banco não conhece); sem unidade não há como checar o hidrômetro")
    void unknownUnit() {
        ReadingValidationResult noId = validate(new ReadingSubmission(null, MeasurementKind.WATER,
                LocalDate.of(2026, 10, 15), 1, null), context());
        assertThat(noId.violations()).containsExactly(ReadingViolation.UNIT_UNKNOWN);

        ReadingValidationResult notFound = validate(submission(LocalDate.of(2026, 10, 15), 1, null),
                new ReadingContext(true, false, false, null, null));
        assertThat(notFound.violations()).containsExactly(ReadingViolation.UNIT_UNKNOWN);
    }

    @Test
    @DisplayName("tipo de medição desconhecido")
    void unknownKind() {
        ReadingValidationResult r = validate(new ReadingSubmission(UNIT, null, LocalDate.of(2026, 10, 15), 1, null),
                context());
        assertThat(r.violations()).containsExactly(ReadingViolation.KIND_UNKNOWN);
    }

    @Test
    @DisplayName("data ausente")
    void missingDate() {
        assertThat(validate(submission(null, 1, null), context()).violations())
                .containsExactly(ReadingViolation.READING_DATE_MISSING);
    }

    @Test
    @DisplayName("data futura dentro da janela: só 'futura' (hoje 20/10, leitura em 25/10)")
    void futureDateInsideWindow() {
        assertThat(validate(submission(LocalDate.of(2026, 10, 25), 1, null), context()).violations())
                .containsExactly(ReadingViolation.READING_DATE_IN_FUTURE);
    }

    @Test
    @DisplayName("data futura e fora da janela: as duas violações aparecem, não só a primeira")
    void futureAndOutsideWindowReportsBoth() {
        assertThat(validate(submission(LocalDate.of(2026, 12, 5), 1, null), context()).violations())
                .containsExactly(ReadingViolation.READING_DATE_IN_FUTURE,
                        ReadingViolation.READING_DATE_OUTSIDE_WINDOW);
    }

    @ParameterizedTest(name = "leitura em {0}: fora da janela? {1}")
    @DisplayName("janela de referência - 1 a referência + 1 mês (granularidade de mês), referência out/2026")
    @CsvSource({
            "2026-08-31, true",   // ago: dois meses antes
            "2026-09-01, false",  // primeiro dia de set (ref - 1): dentro
            "2026-10-15, false",
            "2026-11-30, false",  // último dia de nov (ref + 1): dentro
            "2026-12-01, true",   // dez: dois meses depois
    })
    void windowBoundaries(LocalDate date, boolean outside) {
        // "hoje" = 31/12/2026 para que nenhuma data seja futura e só a janela seja testada
        ReadingValidationResult r = ReadingValidator.validate(submission(date, 1, null), context(), LATE_TODAY, REF, WINDOW);
        if (outside) {
            assertThat(r.violations()).containsExactly(ReadingViolation.READING_DATE_OUTSIDE_WINDOW);
        } else {
            assertThat(r.violations()).isEmpty();
        }
    }

    @Test
    @DisplayName("a janela é parâmetro: com 0 só o mês da referência vale; com 2, dois meses para cada lado")
    void windowIsAParameter() {
        ReadingSubmission september = submission(LocalDate.of(2026, 9, 15), 1, null);
        assertThat(ReadingValidator.validate(september, context(), LATE_TODAY, REF, 0).violations())
                .containsExactly(ReadingViolation.READING_DATE_OUTSIDE_WINDOW);
        ReadingSubmission august = submission(LocalDate.of(2026, 8, 15), 1, null);
        assertThat(ReadingValidator.validate(august, context(), LATE_TODAY, REF, 2).violations()).isEmpty();
        assertThat(ReadingValidator.validate(august, context(), LATE_TODAY, REF, 1).violations())
                .containsExactly(ReadingViolation.READING_DATE_OUTSIDE_WINDOW);
        assertThatThrownBy(() -> ReadingValidator.validate(august, context(), LATE_TODAY, REF, -1))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("leitura negativa")
    void negativeReading() {
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), -1, null), context()).violations())
                .containsExactly(ReadingViolation.READING_NEGATIVE);
    }

    @Test
    @DisplayName("leitura > 0 para ligação sem hidrômetro do tipo informado")
    void readingWithoutMeter() {
        ReadingContext noMeter = new ReadingContext(true, true, false, null, null);
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), 10, null), noMeter).violations())
                .containsExactly(ReadingViolation.READING_WITHOUT_METER);
        // mesmo com a anormalidade de "sem hidrômetro", leitura > 0 não cabe
        ReadingContext noMeterWithAnomaly = new ReadingContext(true, true, false, null, Fixtures.noMeterAnomaly("SH"));
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), 10, "SH"), noMeterWithAnomaly).violations())
                .containsExactly(ReadingViolation.READING_WITHOUT_METER);
    }

    @Test
    @DisplayName("tipo de medição informado sem hidrômetro instalado e sem a anormalidade de 'sem hidrômetro'")
    void meterNotInstalledForKind() {
        ReadingContext noMeter = new ReadingContext(true, true, false, null, null);
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), null, null), noMeter).violations())
                .containsExactly(ReadingViolation.METER_NOT_INSTALLED_FOR_KIND);
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), 0, null), noMeter).violations())
                .containsExactly(ReadingViolation.METER_NOT_INSTALLED_FOR_KIND);
    }

    @Test
    @DisplayName("a anormalidade 'sem hidrômetro' sem leitura é o jeito legítimo de reportar imóvel sem medidor")
    void noMeterAnomalyOnUnitWithoutMeterIsAccepted() {
        ReadingContext noMeter = new ReadingContext(true, true, false, null, Fixtures.noMeterAnomaly("SH"));
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), null, "SH"), noMeter).accepted()).isTrue();
    }

    @Test
    @DisplayName("anormalidade 'sem hidrômetro' lançada para ligação que tem hidrômetro")
    void noMeterAnomalyWithMeter() {
        ReadingContext withMeter = new ReadingContext(true, true, true, 5, Fixtures.noMeterAnomaly("SH"));
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), null, "SH"), withMeter).violations())
                .containsExactly(ReadingViolation.NO_METER_ANOMALY_WITH_METER);
    }

    @Test
    @DisplayName("código de anormalidade que não existe no catálogo; código existente de outro tipo é aceito")
    void anomalyCodes() {
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), 1, "ZZZ"), context()).violations())
                .containsExactly(ReadingViolation.ANOMALY_UNKNOWN);

        ReadingAnomalyRule known = Fixtures.anomaly("A1", ReadingEffect.PREVIOUS, ReadingEffect.INFORMED,
                ConsumptionEffect.AVERAGE, ConsumptionEffect.NORMAL, null, null);
        ReadingContext withKnown = new ReadingContext(true, true, true, 5, known);
        assertThat(validate(submission(LocalDate.of(2026, 10, 15), 1, "A1"), withKnown).accepted()).isTrue();
    }

    @Test
    @DisplayName("várias violações juntas são todas listadas, na ordem das regras")
    void allViolationsAreReported() {
        ReadingValidationResult r = validate(
                new ReadingSubmission(null, null, null, -5, "ZZZ"), new ReadingContext(false, false, false, null, null));
        assertThat(r.violations()).containsExactly(ReadingViolation.READER_UNKNOWN, ReadingViolation.UNIT_UNKNOWN,
                ReadingViolation.KIND_UNKNOWN, ReadingViolation.READING_DATE_MISSING,
                ReadingViolation.READING_NEGATIVE, ReadingViolation.ANOMALY_UNKNOWN);
        assertThat(r.accepted()).isFalse();
    }

    @Test
    @DisplayName("leitura acima da capacidade do mostrador não recusa: avisa (o cálculo a limita)")
    void readingAboveDialIsAWarningNotAnError() {
        ReadingValidationResult over = validate(submission(LocalDate.of(2026, 10, 15), 100_000, null), context());
        assertThat(over.accepted()).isTrue();
        assertThat(over.warnings()).extracting(CalculationWarning::code)
                .containsExactly(WarningCode.READING_EXCEEDS_DIAL_CAPACITY);

        ReadingValidationResult atLimit = validate(submission(LocalDate.of(2026, 10, 15), 99_999, null), context());
        assertThat(atLimit.warnings()).isEmpty();
    }

    @Test
    @DisplayName("entradas obrigatórias do validador")
    void requiredArguments() {
        assertThatThrownBy(() -> ReadingValidator.validate(null, context(), TODAY, REF, WINDOW))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> ReadingValidator.validate(submission(TODAY, 1, null), context(), null, REF, WINDOW))
                .isInstanceOf(MeteringInputException.class);
    }
}
