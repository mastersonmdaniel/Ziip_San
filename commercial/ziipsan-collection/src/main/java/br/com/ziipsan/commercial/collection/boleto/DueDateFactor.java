package br.com.ziipsan.commercial.collection.boleto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fator de vencimento do boleto de cobrança bancária (posições 06-09 do código de barras).
 *
 * <h3>Regra</h3>
 * <ul>
 *   <li><b>Ciclo legado</b>: fator = número de dias entre 07/10/1997 e o vencimento. O fator
 *       9999 cai em 21/02/2025. Ex.: 06/12/2000 = 1156; 15/12/2000 = 1165; 30/12/2000 = 1180
 *       (exemplos do manual de código de barras de cobrança do Banco Santander, v36).</li>
 *   <li><b>Ciclo atual</b>: a partir de 22/02/2025 o fator <b>volta a 1000</b> e soma 1 por dia:
 *       22/02/2025 = 1000, 23/02/2025 = 1001, 24/02/2025 = 1002 (mesmo manual). O fator 9999 do
 *       ciclo atual cai em 13/10/2049, e o ciclo seguinte (14/10/2049) ainda não tem regra
 *       publicada, por isso {@link #encode} recusa datas depois de 13/10/2049.</li>
 *   <li>Fator {@code 0000} significa "sem vencimento"; o manual do Santander proíbe as literais
 *       "contra apresentação" e "à vista", e este módulo não gera boleto sem vencimento.</li>
 * </ul>
 *
 * <h3>Ambiguidade na leitura</h3>
 * Os fatores 1000 a 9999 existem nos dois ciclos: o mesmo número é uma data entre 03/07/2000 e
 * 21/02/2025 (ciclo legado) ou uma data entre 22/02/2025 e 13/10/2049 (ciclo atual), 9.000 dias
 * (cerca de 24,6 anos) de distância uma da outra. O código de barras não diz qual. Por isso
 * {@link #decode} exige uma <b>data de referência</b> e uma <b>janela</b> de aceitação, e só
 * devolve a data se exatamente uma das duas candidatas cair na janela. A janela não tem valor
 * padrão: é política do chamador (por exemplo, tempo máximo que um boleto vencido ainda é aceito
 * e antecedência máxima de emissão). O documento primário da FEBRABAN sobre a janela de
 * decodificação não foi localizado (ver README).
 */
public final class DueDateFactor {

    public static final LocalDate LEGACY_BASE = LocalDate.of(1997, 10, 7);
    public static final LocalDate CURRENT_BASE = LocalDate.of(2025, 2, 22);
    public static final int CURRENT_BASE_FACTOR = 1000;
    public static final int MAX_FACTOR = 9999;
    /** Último vencimento do ciclo legado (fator 9999). */
    public static final LocalDate LEGACY_LAST_DATE = LEGACY_BASE.plusDays(MAX_FACTOR);
    /** Último vencimento do ciclo atual (fator 9999). */
    public static final LocalDate CURRENT_LAST_DATE = CURRENT_BASE.plusDays(MAX_FACTOR - CURRENT_BASE_FACTOR);

    private DueDateFactor() {
    }

    /** Fator do vencimento, de 4 posições, no ciclo em vigor para a data. */
    public static int encode(LocalDate dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Vencimento ausente");
        }
        if (!dueDate.isBefore(CURRENT_BASE)) {
            if (dueDate.isAfter(CURRENT_LAST_DATE)) {
                throw new IllegalArgumentException("Vencimento depois de " + CURRENT_LAST_DATE
                        + ": o fator do ciclo seguinte não tem regra publicada");
            }
            return CURRENT_BASE_FACTOR + (int) ChronoUnit.DAYS.between(CURRENT_BASE, dueDate);
        }
        long legacy = ChronoUnit.DAYS.between(LEGACY_BASE, dueDate);
        if (legacy < 1) {
            throw new IllegalArgumentException("Vencimento anterior ou igual a " + LEGACY_BASE
                    + " não é representável pelo fator");
        }
        return (int) legacy;
    }

    /** Datas que o fator pode representar: 0, 1 ou 2 candidatas (ciclo legado e ciclo atual). */
    public static List<LocalDate> candidates(int factor) {
        if (factor < 0 || factor > MAX_FACTOR) {
            throw new IllegalArgumentException("Fator fora de 0000-9999: " + factor);
        }
        List<LocalDate> out = new ArrayList<>(2);
        if (factor >= 1) {
            out.add(LEGACY_BASE.plusDays(factor)); // fator 1..9999 -> 08/10/1997..21/02/2025
        }
        if (factor >= CURRENT_BASE_FACTOR) {
            out.add(CURRENT_BASE.plusDays(factor - CURRENT_BASE_FACTOR));
        }
        return out;
    }

    /**
     * Resolve o fator em data de vencimento.
     *
     * @param factor        fator lido do código de barras
     * @param reference     data de referência (emissão, apresentação ou pagamento)
     * @param maxDaysBefore quantos dias antes da referência um vencimento ainda é aceito (&ge; 0)
     * @param maxDaysAfter  quantos dias depois da referência um vencimento ainda é aceito (&ge; 0)
     * @return a data; vazio se o fator for 0000 (sem vencimento)
     * @throws IllegalArgumentException se nenhuma candidata cair na janela, ou se as duas caírem
     */
    public static Optional<LocalDate> decode(int factor, LocalDate reference, int maxDaysBefore, int maxDaysAfter) {
        if (reference == null || maxDaysBefore < 0 || maxDaysAfter < 0) {
            throw new IllegalArgumentException("Referência e janela (não negativa) são obrigatórias");
        }
        if (factor == 0) {
            return Optional.empty();
        }
        LocalDate from = reference.minusDays(maxDaysBefore);
        LocalDate to = reference.plusDays(maxDaysAfter);
        List<LocalDate> inside = new ArrayList<>(2);
        for (LocalDate c : candidates(factor)) {
            if (!c.isBefore(from) && !c.isAfter(to)) {
                inside.add(c);
            }
        }
        if (inside.isEmpty()) {
            throw new IllegalArgumentException("Fator " + factor + " não corresponde a vencimento entre " + from
                    + " e " + to + " (candidatas: " + candidates(factor) + ")");
        }
        if (inside.size() > 1) {
            throw new IllegalArgumentException("Fator " + factor + " ambíguo na janela " + from + " a " + to
                    + ": " + inside + "; reduza a janela");
        }
        return Optional.of(inside.get(0));
    }
}
