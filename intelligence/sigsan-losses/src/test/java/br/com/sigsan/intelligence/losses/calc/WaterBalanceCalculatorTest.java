package br.com.sigsan.intelligence.losses.calc;

import br.com.sigsan.intelligence.losses.calc.WaterBalanceCalculator.Input;
import br.com.sigsan.intelligence.losses.calc.WaterBalanceCalculator.Network;
import br.com.sigsan.intelligence.losses.calc.WaterBalanceCalculator.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valores esperados calculados à mão, nunca a partir do próprio código sob teste.
 *
 * <p>Cenário-base (volumes em m³ no período):
 * SIV 10.000; faturado 7.000; não faturado autorizado 200; consumo não autorizado 100; imprecisão
 * de hidrômetros 140 (2% do faturado).
 * <pre>
 *   consumo autorizado = 7.000 + 200            = 7.200
 *   perdas de água     = 10.000 − 7.200         = 2.800
 *   perdas aparentes   = 100 + 140              =   240
 *   perdas reais       = 2.800 − 240            = 2.560
 *   água não faturada  = 10.000 − 7.000         = 3.000   (30,0000%)
 * </pre>
 */
class WaterBalanceCalculatorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Input base(Network network, String days) {
        return new Input(bd("10000"), bd("7000"), bd("200"), bd("100"), bd("140"), bd(days), network);
    }

    /** Rede-exemplo: Lm 10 km, 1.000 ligações, Lp 0 km, pressão 30 m. */
    private static Network network(String lm, int nc, String lp, String p) {
        return new Network(bd(lm), nc, lp == null ? null : bd(lp), bd(p));
    }

    @Test
    @DisplayName("balanço IWA: consumo autorizado, perdas aparentes, perdas reais e água não faturada")
    void iwaBalanceStructure() {
        Result r = WaterBalanceCalculator.calculate(base(Network.unknown(), "30"));

        assertThat(r.authorizedConsumption()).isEqualByComparingTo("7200");
        assertThat(r.waterLosses()).isEqualByComparingTo("2800");
        assertThat(r.apparentLosses()).isEqualByComparingTo("240");
        assertThat(r.realLosses()).isEqualByComparingTo("2560");
        assertThat(r.consistent()).isTrue();
    }

    @Test
    @DisplayName("água não faturada inclui o consumo autorizado não faturado")
    void nonRevenueWaterIncludesUnbilledAuthorizedConsumption() {
        Result r = WaterBalanceCalculator.calculate(base(Network.unknown(), "30"));

        // NRW = SIV − faturado = 3.000. A versão anterior somava só perdas aparentes e reais
        // (240 + 2.560 = 2.800), que é "perdas de água" na nomenclatura IWA, e omitia os 200 m³
        // de consumo autorizado não faturado.
        assertThat(r.nonRevenueWater()).isEqualByComparingTo("3000");
        assertThat(r.nonRevenueWaterPercent()).isEqualByComparingTo("30.0000");
        assertThat(r.nonRevenueWater())
                .as("NRW = não faturado autorizado + perdas aparentes + perdas reais")
                .isEqualByComparingTo(r.apparentLosses().add(r.realLosses()).add(bd("200")));
    }

    @Test
    @DisplayName("percentual de água não faturada arredonda HALF_UP em 4 casas")
    void nrwPercentRounding() {
        // SIV 3, faturado 2: NRW 1 → 1/3 = 33,3333...%
        Input in = new Input(bd("3"), bd("2"), bd("0"), bd("0"), bd("0"), bd("30"), Network.unknown());
        assertThat(WaterBalanceCalculator.calculate(in).nonRevenueWaterPercent())
                .isEqualByComparingTo("33.3333");

        // SIV 6, faturado 1: NRW 5 → 5/6 = 83,3333...%
        in = new Input(bd("6"), bd("1"), bd("0"), bd("0"), bd("0"), bd("30"), Network.unknown());
        assertThat(WaterBalanceCalculator.calculate(in).nonRevenueWaterPercent())
                .isEqualByComparingTo("83.3333");
    }

    @Test
    @DisplayName("UARL em litros/dia: (18·Lm + 0,8·Nc + 25·Lp) · P")
    void uarlFormula() {
        // Lm 10, Nc 1.000, Lp 0, P 30: (180 + 800 + 0) · 30 = 29.400 L/dia = 29,4 m³/dia
        Result semRamal = WaterBalanceCalculator.calculate(
                base(network("10", 1000, "0", "30"), "30"));
        assertThat(semRamal.uarlLitersPerDay()).isEqualByComparingTo("29400");
        assertThat(semRamal.uarlCubicMetersPerDay()).isEqualByComparingTo("29.4");

        // Lp 5: (180 + 800 + 125) · 30 = 1.105 · 30 = 33.150 L/dia
        Result comRamal = WaterBalanceCalculator.calculate(
                base(network("10", 1000, "5", "30"), "30"));
        assertThat(comRamal.uarlLitersPerDay()).isEqualByComparingTo("33150");
    }

    @Test
    @DisplayName("ILI usa perdas reais e UARL na mesma base de tempo")
    void iliUsesTheSameTimeBase() {
        // Perdas reais 2.560 m³ em 30 dias; UARL 29.400 L/dia = 882 m³ em 30 dias.
        // ILI = 2.560 / 882 = 2,902494... → 2,9025
        Result r = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "30"), "30"));
        assertThat(r.infrastructureLeakageIndex()).isEqualByComparingTo("2.9025");
    }

    @Test
    @DisplayName("regressão: dividir o volume do período pelo UARL diário superestimava o ILI pelo número de dias")
    void iliIsNotInflatedByThePeriodLength() {
        // Mesmas perdas reais num período de 1 dia: ILI = 2.560 / 29,4 = 87,0748...
        Result umDia = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "30"), "1"));
        assertThat(umDia.infrastructureLeakageIndex()).isEqualByComparingTo("87.0748");

        // O mesmo volume de perdas espalhado em 30 dias é 30 vezes menos grave por dia. A versão
        // anterior devolvia 87,0748 nos dois casos, porque ignorava a duração do período.
        Result trintaDias = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "30"), "30"));
        assertThat(trintaDias.infrastructureLeakageIndex()).isLessThan(umDia.infrastructureLeakageIndex());
    }

    @Test
    @DisplayName("balanço que não fecha devolve perdas reais negativas, aviso e nenhum ILI")
    void inconsistentBalanceIsReportedNotHidden() {
        // SIV 5.000; faturado 4.800; não faturado 300; não autorizado 100; imprecisão 100
        //   autorizado 5.100 > SIV → perdas de água −100; aparentes 200; reais −300; NRW 200
        Input in = new Input(bd("5000"), bd("4800"), bd("300"), bd("100"), bd("100"), bd("30"),
                network("10", 1000, "0", "30"));
        Result r = WaterBalanceCalculator.calculate(in);

        assertThat(r.waterLosses()).isEqualByComparingTo("-100");
        assertThat(r.realLosses()).isEqualByComparingTo("-300");
        assertThat(r.nonRevenueWater()).isEqualByComparingTo("200");
        assertThat(r.consistent()).isFalse();
        assertThat(r.infrastructureLeakageIndex())
                .as("ILI sobre perdas reais negativas não tem significado").isNull();
        assertThat(r.warnings()).anyMatch(w -> w.contains("Perdas reais negativas"));
    }

    @Test
    @DisplayName("sem dados de rede o balanço sai, mas UARL e ILI ficam nulos com aviso")
    void missingNetworkDataIsNotInvented() {
        Result r = WaterBalanceCalculator.calculate(base(Network.unknown(), "30"));

        assertThat(r.realLosses()).isEqualByComparingTo("2560");
        assertThat(r.uarlLitersPerDay()).isNull();
        assertThat(r.infrastructureLeakageIndex()).isNull();
        assertThat(r.warnings()).anyMatch(w -> w.contains("Dados de rede incompletos"));
    }

    @Test
    @DisplayName("rede nula equivale a rede desconhecida")
    void nullNetworkIsUnknown() {
        Result r = WaterBalanceCalculator.calculate(base(null, "30"));
        assertThat(r.infrastructureLeakageIndex()).isNull();
        assertThat(r.warnings()).anyMatch(w -> w.contains("Dados de rede incompletos"));
    }

    @Test
    @DisplayName("ramal privado não informado assume 0 km e avisa")
    void unknownPrivatePipeLengthIsExplicit() {
        Result r = WaterBalanceCalculator.calculate(base(network("10", 1000, null, "30"), "30"));

        assertThat(r.uarlLitersPerDay()).isEqualByComparingTo("29400");
        assertThat(r.warnings()).anyMatch(w -> w.contains("Lp") && w.contains("assumido 0 km"));
    }

    @Test
    @DisplayName("densidade de ligações abaixo de 20/km avisa que o UARL é pouco confiável")
    void lowConnectionDensityWarns() {
        // 1.000 ligações em 100 km = 10/km
        Result r = WaterBalanceCalculator.calculate(base(network("100", 1000, "0", "30"), "30"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("Densidade de ligações"));

        // 1.000 ligações em 10 km = 100/km: sem aviso de densidade
        Result ok = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "30"), "30"));
        assertThat(ok.warnings()).noneMatch(w -> w.contains("Densidade de ligações"));
    }

    @Test
    @DisplayName("pressão média abaixo de 25 m avisa que o UARL é pouco confiável")
    void lowPressureWarns() {
        Result r = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "20"), "30"));
        assertThat(r.warnings()).anyMatch(w -> w.contains("Pressão média abaixo de 25 m"));
        // (180 + 800) · 20 = 19.600 L/dia: o valor sai mesmo assim, com o aviso
        assertThat(r.uarlLitersPerDay()).isEqualByComparingTo("19600");
    }

    @Test
    @DisplayName("volume de entrada zero não divide por zero")
    void zeroSystemInputVolume() {
        Input in = new Input(bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), bd("30"), Network.unknown());
        Result r = WaterBalanceCalculator.calculate(in);

        assertThat(r.nonRevenueWater()).isEqualByComparingTo("0");
        assertThat(r.nonRevenueWaterPercent()).isNull();
        assertThat(r.warnings()).anyMatch(w -> w.contains("Volume de entrada nulo"));
    }

    @Test
    @DisplayName("entradas obrigatórias ausentes ou período inválido são rejeitados")
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> WaterBalanceCalculator.calculate(
                new Input(null, bd("1"), bd("0"), bd("0"), bd("0"), bd("30"), Network.unknown())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("systemInputVolume");

        assertThatThrownBy(() -> WaterBalanceCalculator.calculate(base(Network.unknown(), "0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodDays");

        assertThatThrownBy(() -> WaterBalanceCalculator.calculate(base(Network.unknown(), "-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("período fracionário em dias é aceito")
    void fractionalPeriod() {
        // 12 horas = 0,5 dia. UARL no período = 29,4 · 0,5 = 14,7 m³. Perdas reais 2.560.
        // ILI = 2.560 / 14,7 = 174,1497...
        Result r = WaterBalanceCalculator.calculate(base(network("10", 1000, "0", "30"), "0.5"));
        assertThat(r.infrastructureLeakageIndex()).isEqualByComparingTo("174.1497");
    }
}
