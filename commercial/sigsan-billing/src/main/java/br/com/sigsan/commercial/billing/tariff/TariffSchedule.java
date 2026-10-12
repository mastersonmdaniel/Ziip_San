package br.com.sigsan.commercial.billing.tariff;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Todas as versões de uma tarifa, ordenadas, sem sobreposição de vigência (o banco impõe o mesmo
 * por uma restrição de exclusão).
 */
public record TariffSchedule(List<TariffVersion> versions) {

    /** Trecho de um período coberto por uma única versão (datas inclusivas). */
    public record PeriodSlice(TariffVersion version, LocalDate from, LocalDate to) {
    }

    public TariffSchedule {
        if (versions == null || versions.isEmpty()) {
            throw new TariffConfigurationException("Tarifa sem nenhuma versão");
        }
        versions = versions.stream().sorted(Comparator.comparing(TariffVersion::validFrom)).toList();
        for (int i = 1; i < versions.size(); i++) {
            TariffVersion previous = versions.get(i - 1);
            TariffVersion current = versions.get(i);
            if (previous.validTo() == null || !previous.validTo().isBefore(current.validFrom())) {
                throw new TariffConfigurationException("Vigências se sobrepõem: versão " + previous.id()
                        + " e versão " + current.id() + " (feche a vigência anterior antes de abrir a nova)");
            }
        }
    }

    /**
     * Divide {@code [from, to]} (inclusivo) pelas versões que o cobrem. Equivale à regra do legado:
     * a versão vigente na data inicial mais todas as que começam dentro do período.
     *
     * @throws TariffConfigurationException se algum dia do período não tiver versão vigente
     */
    public List<PeriodSlice> slices(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Período invertido: " + from + " > " + to);
        }
        List<PeriodSlice> slices = new ArrayList<>();
        LocalDate cursor = from;
        for (TariffVersion v : versions) {
            LocalDate end = v.validTo() == null ? LocalDate.MAX : v.validTo();
            if (end.isBefore(cursor) || v.validFrom().isAfter(to)) {
                continue;
            }
            if (v.validFrom().isAfter(cursor)) {
                break; // buraco: o dia 'cursor' não tem versão
            }
            LocalDate sliceEnd = end.isBefore(to) ? end : to;
            slices.add(new PeriodSlice(v, cursor, sliceEnd));
            if (!sliceEnd.isBefore(to)) {
                return slices;
            }
            cursor = sliceEnd.plusDays(1);
        }
        throw new TariffConfigurationException("Nenhuma tarifa vigente em " + cursor
                + " (período pedido: " + from + " a " + to + ")");
    }
}
