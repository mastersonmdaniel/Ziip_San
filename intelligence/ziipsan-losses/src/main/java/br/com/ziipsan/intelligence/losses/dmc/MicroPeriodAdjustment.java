package br.com.ziipsan.intelligence.losses.dmc;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Ajuste do volume micromedido ao periodo do macromedido (ED-85;
 * JOR-34): quando as rotas de micromedicao sao lidas em datas
 * diferentes do periodo do macromedidor, o volume micromedido e
 * trazido ao periodo macro pela media diaria do periodo micro
 * (PRO_RATA_DAILY_AVERAGE). O metodo aplicado e DECLARADO e GRAVADO
 * no balanco ({@code loss_monthly_balances.adjustment_method}) —
 * periodo coincidente registra NONE, sem fator escondido.
 *
 * <p>Convencao de periodo: intervalos [inicio, fim) em dias civis.
 */
public final class MicroPeriodAdjustment {

    public enum Method { NONE, PRO_RATA_DAILY_AVERAGE }

    public record Result(Method method, double originalM3, double adjustedM3,
                         long microDays, long macroDays) {}

    private MicroPeriodAdjustment() {}

    public static Result adjust(double microVolumeM3, LocalDate microStart, LocalDate microEndExclusive,
                                LocalDate macroStart, LocalDate macroEndExclusive) {
        Objects.requireNonNull(microStart, "inicio do periodo micro obrigatorio");
        Objects.requireNonNull(microEndExclusive, "fim do periodo micro obrigatorio");
        Objects.requireNonNull(macroStart, "inicio do periodo macro obrigatorio");
        Objects.requireNonNull(macroEndExclusive, "fim do periodo macro obrigatorio");
        if (microVolumeM3 < 0 || Double.isNaN(microVolumeM3))
            throw new IllegalArgumentException("volume micromedido negativo: " + microVolumeM3);
        long microDays = ChronoUnit.DAYS.between(microStart, microEndExclusive);
        long macroDays = ChronoUnit.DAYS.between(macroStart, macroEndExclusive);
        if (microDays <= 0) throw new IllegalArgumentException("periodo micro vazio ou invertido");
        if (macroDays <= 0) throw new IllegalArgumentException("periodo macro vazio ou invertido");
        if (microStart.equals(macroStart) && microEndExclusive.equals(macroEndExclusive))
            return new Result(Method.NONE, microVolumeM3, microVolumeM3, microDays, macroDays);
        double adjusted = microVolumeM3 / microDays * macroDays;
        return new Result(Method.PRO_RATA_DAILY_AVERAGE, microVolumeM3, adjusted, microDays, macroDays);
    }
}
