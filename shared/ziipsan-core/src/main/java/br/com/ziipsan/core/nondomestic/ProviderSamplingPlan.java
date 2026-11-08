package br.com.ziipsan.core.nondomestic;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plano de amostragem DO PRESTADOR no ponto de lancamento do usuario
 * nao domestico (aceite da ED-83): as coletas vencem a cada
 * {@code effluent.sampling_interval_days} dias a partir da ancora
 * (inicio da autorizacao), no ponto de lancamento do cadastro.
 *
 * <p>O intervalo e o parametro do tenant validado por
 * {@link NonDomesticParameters#samplingIntervalDays} — nenhum
 * intervalo nasce de fabrica. Este plano nao fixa parametros nem
 * quantidades por coleta: o QUE se analisa em cada coleta e o
 * catalogo de padrao de lancamento do prestador (parametros com
 * linha vigente); o plano fixa QUANDO coletar e ONDE (o ponto).
 * A coleta em si e amostra da ED-50, com etiqueta e cadeia de
 * custodia da ED-50 (ver {@link ProviderSampling}).
 */
public record ProviderSamplingPlan(
        UUID dischargerId,
        String dischargePointCode,
        int intervalDays,
        LocalDate anchorDate) {

    public ProviderSamplingPlan {
        if (dischargerId == null) throw new IllegalArgumentException("o gerador do plano e obrigatorio");
        if (dischargePointCode == null || dischargePointCode.isBlank())
            throw new IllegalArgumentException("o ponto de lancamento do plano e obrigatorio");
        if (intervalDays <= 0) throw new IllegalArgumentException(
                "intervalo do plano deve ser maior que zero (effluent.sampling_interval_days): " + intervalDays);
        if (anchorDate == null) throw new IllegalArgumentException(
                "ancora do plano (inicio da autorizacao) e obrigatoria");
    }

    /** Primeira data de coleta: ancora + intervalo. */
    public LocalDate firstDueDate() {
        return anchorDate.plusDays(intervalDays);
    }

    /** Proxima coleta devida em ou depois de {@code onOrAfter} (ancora + k intervalos). */
    public LocalDate nextDueOnOrAfter(LocalDate onOrAfter) {
        if (onOrAfter == null) throw new IllegalArgumentException("data de referencia e obrigatoria");
        LocalDate due = firstDueDate();
        while (due.isBefore(onOrAfter)) due = due.plusDays(intervalDays);
        return due;
    }

    /** Todas as coletas devidas na janela [from, to], em ordem. */
    public List<LocalDate> dueDatesBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new IllegalArgumentException("janela do plano invalida");
        List<LocalDate> dues = new ArrayList<>();
        LocalDate due = nextDueOnOrAfter(from);
        while (!due.isAfter(to)) {
            dues.add(due);
            due = due.plusDays(intervalDays);
        }
        return List.copyOf(dues);
    }

    /**
     * Ha coleta vencida e nao realizada? {@code lastCollectionDate}
     * nulo significa nenhuma coleta ainda: vence na primeira data.
     */
    public boolean overdueOn(LocalDate today, LocalDate lastCollectionDate) {
        if (today == null) throw new IllegalArgumentException("data de referencia e obrigatoria");
        LocalDate due = lastCollectionDate == null
                ? firstDueDate()
                : lastCollectionDate.plusDays(intervalDays);
        return today.isAfter(due);
    }
}
