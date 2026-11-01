package br.com.ziipsan.intelligence.losses.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Balanço hídrico IWA/AWWA e Índice de Vazamentos da Infraestrutura (ILI), sem I/O.
 *
 * <p>Toda a aritmética fica aqui, isolada de repositório e de relógio, para que cada fórmula
 * tenha teste de valor conhecido calculado à mão. As unidades são explícitas:
 * <ul>
 *   <li>volumes do balanço: m³ no período;</li>
 *   <li>{@code periodDays}: duração do período em dias (pode ser fracionária);</li>
 *   <li>UARL: litros/dia, como na fórmula original da IWA; {@code uarlM3PerDay} é a conversão.</li>
 * </ul>
 *
 * <p>Estrutura do balanço (IWA):
 * <pre>
 *   Volume de entrada (SIV)
 *     = Consumo autorizado (faturado + não faturado)
 *     + Perdas de água (aparentes + reais)
 *   Água não faturada (NRW) = SIV − consumo autorizado faturado
 *                           = consumo autorizado não faturado + perdas aparentes + perdas reais
 *   Perdas reais = SIV − consumo autorizado − perdas aparentes
 * </pre>
 *
 * <p>Este calculador <b>não esconde balanço inconsistente</b>. Se as perdas reais saírem
 * negativas (as entradas medidas não fecham), o valor negativo é devolvido junto de um aviso, e
 * o ILI não é calculado. Zerar o valor, como a versão anterior fazia, produzia um balanço que
 * parecia fechado.
 *
 * <p>UARL (perdas reais anuais inevitáveis), em litros/dia:
 * {@code (18·Lm + 0,8·Nc + 25·Lp) · P}, com Lm em km de rede, Nc número de ligações, Lp km totais
 * de ramal privado (do limite do lote ao hidrômetro) e P pressão média em metros. A fórmula é
 * considerada pouco confiável com menos de 20 ligações por km de rede ou pressão média abaixo de
 * 25 m; nesses casos o resultado sai com aviso.
 */
public final class WaterBalanceCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal UARL_MAINS = BigDecimal.valueOf(18);
    private static final BigDecimal UARL_CONNECTIONS = new BigDecimal("0.8");
    private static final BigDecimal UARL_PRIVATE = BigDecimal.valueOf(25);
    private static final BigDecimal MIN_CONNECTION_DENSITY_PER_KM = BigDecimal.valueOf(20);
    private static final BigDecimal MIN_PRESSURE_M = BigDecimal.valueOf(25);

    private WaterBalanceCalculator() {
    }

    /** Entradas do balanço. Volumes em m³ no período. */
    public record Input(
            BigDecimal systemInputVolume,
            BigDecimal billedAuthorized,
            BigDecimal unbilledAuthorized,
            BigDecimal unauthorizedConsumption,
            BigDecimal meteringInaccuracies,
            BigDecimal periodDays,
            Network network) {
    }

    /**
     * Dados da rede para o UARL. Qualquer campo {@code null} significa "não informado": o
     * calculador não inventa valor, e sim deixa o UARL e o ILI como {@code null} com aviso.
     * A única exceção é {@code privatePipeLengthKm}, ver {@link #calculate}.
     */
    public record Network(
            BigDecimal mainsLengthKm,
            Integer serviceConnections,
            BigDecimal privatePipeLengthKm,
            BigDecimal averagePressureM) {

        public static Network unknown() {
            return new Network(null, null, null, null);
        }
    }

    public record Result(
            BigDecimal authorizedConsumption,
            BigDecimal waterLosses,
            BigDecimal apparentLosses,
            BigDecimal realLosses,
            BigDecimal nonRevenueWater,
            BigDecimal nonRevenueWaterPercent,
            BigDecimal uarlLitersPerDay,
            BigDecimal uarlCubicMetersPerDay,
            BigDecimal infrastructureLeakageIndex,
            boolean consistent,
            List<String> warnings) {
    }

    public static Result calculate(Input in) {
        require(in.systemInputVolume(), "systemInputVolume");
        require(in.billedAuthorized(), "billedAuthorized");
        require(in.unbilledAuthorized(), "unbilledAuthorized");
        require(in.unauthorizedConsumption(), "unauthorizedConsumption");
        require(in.meteringInaccuracies(), "meteringInaccuracies");
        require(in.periodDays(), "periodDays");
        if (in.periodDays().signum() <= 0) {
            throw new IllegalArgumentException("periodDays deve ser positivo: " + in.periodDays());
        }

        List<String> warnings = new ArrayList<>();
        BigDecimal siv = in.systemInputVolume();

        BigDecimal authorized = in.billedAuthorized().add(in.unbilledAuthorized());
        BigDecimal waterLosses = siv.subtract(authorized);
        BigDecimal apparent = in.unauthorizedConsumption().add(in.meteringInaccuracies());
        BigDecimal real = waterLosses.subtract(apparent);
        BigDecimal nrw = siv.subtract(in.billedAuthorized());

        BigDecimal nrwPercent = null;
        if (siv.signum() > 0) {
            nrwPercent = nrw.multiply(HUNDRED).divide(siv, 4, RoundingMode.HALF_UP);
        } else {
            warnings.add("Volume de entrada nulo ou negativo: percentual de água não faturada não calculado.");
        }

        boolean consistent = true;
        if (waterLosses.signum() < 0) {
            consistent = false;
            warnings.add("Consumo autorizado maior que o volume de entrada: as medições não fecham o balanço.");
        }
        if (real.signum() < 0) {
            consistent = false;
            warnings.add("Perdas reais negativas: volume de entrada menor que consumo autorizado mais "
                    + "perdas aparentes. Revise a macromedição, a micromedição e as estimativas de perdas aparentes.");
        }

        BigDecimal uarlLitersPerDay = null;
        BigDecimal uarlCubicMetersPerDay = null;
        BigDecimal ili = null;

        Network net = in.network() == null ? Network.unknown() : in.network();
        if (net.mainsLengthKm() == null || net.serviceConnections() == null || net.averagePressureM() == null) {
            warnings.add("Dados de rede incompletos (extensão, ligações ou pressão média): UARL e ILI não calculados.");
        } else if (net.mainsLengthKm().signum() <= 0 || net.averagePressureM().signum() <= 0) {
            warnings.add("Extensão de rede e pressão média devem ser positivas: UARL e ILI não calculados.");
        } else {
            BigDecimal lp = net.privatePipeLengthKm();
            if (lp == null) {
                lp = BigDecimal.ZERO;
                warnings.add("Extensão de ramal privado (Lp) não informada: assumido 0 km, o que vale se o "
                        + "hidrômetro fica no limite do lote. Caso contrário o UARL está subestimado e o ILI superestimado.");
            }

            BigDecimal connections = BigDecimal.valueOf(net.serviceConnections());
            BigDecimal density = connections.divide(net.mainsLengthKm(), 4, RoundingMode.HALF_UP);
            if (density.compareTo(MIN_CONNECTION_DENSITY_PER_KM) < 0) {
                warnings.add("Densidade de ligações abaixo de 20 por km de rede: a fórmula do UARL é pouco confiável.");
            }
            if (net.averagePressureM().compareTo(MIN_PRESSURE_M) < 0) {
                warnings.add("Pressão média abaixo de 25 m: a fórmula do UARL é pouco confiável.");
            }

            uarlLitersPerDay = UARL_MAINS.multiply(net.mainsLengthKm())
                    .add(UARL_CONNECTIONS.multiply(connections))
                    .add(UARL_PRIVATE.multiply(lp))
                    .multiply(net.averagePressureM());
            uarlCubicMetersPerDay = uarlLitersPerDay.divide(THOUSAND, 6, RoundingMode.HALF_UP);

            // ILI = perdas reais anuais correntes / perdas reais anuais inevitáveis. As duas
            // grandezas precisam estar na mesma base de tempo: aqui ambas por dia. Uma única
            // divisão no fim, sem arredondamento intermediário.
            if (real.signum() >= 0 && uarlLitersPerDay.signum() > 0) {
                BigDecimal unavoidableInPeriod = uarlLitersPerDay.multiply(in.periodDays())
                        .divide(THOUSAND, 10, RoundingMode.HALF_UP);
                ili = real.divide(unavoidableInPeriod, 4, RoundingMode.HALF_UP);
            } else if (real.signum() < 0) {
                warnings.add("ILI não calculado porque as perdas reais são negativas.");
            }
        }

        return new Result(authorized, waterLosses, apparent, real, nrw, nrwPercent,
                uarlLitersPerDay, uarlCubicMetersPerDay, ili, consistent, List.copyOf(warnings));
    }

    private static void require(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " é obrigatório");
        }
    }
}
