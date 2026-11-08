package br.com.ziipsan.commercial.billing.cobill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Carne mensal ou anual e o IDENTIFICADOR UNICO do documento de
 * cobranca (ED-25): cada documento - conta cofaturada, fatura
 * desvinculada ou parcela do carne - tem um identificador unico por
 * tenant/unidade/competencia, e duplicidade e recusada.
 *
 * <p>Formato do identificador (proposto nesta entrega, declarado no
 * relatorio): {@code COBILL-<UUID completo da unidade>-<AAAAMM>-<seq 3>}.
 * A unicidade vale POR CONSTRUCAO (correcao pos-reprovacao D1): o
 * identificador carrega o UUID inteiro da unidade (128 bits) mais a
 * competencia e a sequencia dentro dela - duas unidades distintas
 * nunca geram o mesmo identificador, e a mesma unidade so repete
 * identificador se repetir competencia E sequencia. O indice unico do
 * SQL pendente ({@code cobill_charges.document_identifier}) e a
 * segunda barreira (recusa persistencia duplicada), nao a garantia
 * primaria; em memoria, {@link #requireUnique(List)} recusa duplicado
 * dentro de um lote. (A versao original usava so os 8 primeiros
 * caracteres hex do UUID - 32 bits - e colidia entre unidades
 * distintas: defeito D1 da revisao cruzada, corrigido aqui.)
 *
 * <p>Carne ANUAL: o valor anual e dividido em 12 parcelas mensais com
 * arredondamento DOWN e o residuo de centavos na ultima parcela - o
 * mesmo desenho de parcelas da P2.5 ({@code Installments}), reescrito
 * aqui sem dependencia para a regra ficar pura e testavel isolada.
 * Carne MENSAL: uma parcela por competencia, no valor mensal cheio.
 * Dia de vencimento e entregue pelo chamador (vem da opcao de
 * vencimento da unidade na P2.5) - nenhum dia e presumido aqui.
 */
public final class CobillBookletPlan {

    private CobillBookletPlan() {
    }

    /** Uma parcela/documento do carne. */
    public record Slip(String documentIdentifier, UUID unitId, YearMonth competence,
                       LocalDate dueDate, BigDecimal amount) {
        public Slip {
            if (documentIdentifier == null || documentIdentifier.isBlank()) {
                throw new IllegalArgumentException("identificador unico do documento e obrigatorio");
            }
            Objects.requireNonNull(unitId, "unidade e obrigatoria");
            Objects.requireNonNull(competence, "competencia e obrigatoria");
            Objects.requireNonNull(dueDate, "vencimento e obrigatorio");
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("parcela deve ser positiva: " + amount);
            }
            if (YearMonth.from(dueDate).isBefore(competence)) {
                throw new IllegalArgumentException("vencimento anterior a competencia: " + dueDate);
            }
        }
    }

    /** Identificador unico do documento de uma competencia (seq comeca em 1). */
    public static String documentIdentifier(UUID unitId, YearMonth competence, int sequence) {
        Objects.requireNonNull(unitId, "unidade e obrigatoria");
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        if (sequence < 1 || sequence > 999) {
            throw new IllegalArgumentException("sequencia do documento fora de 1..999: " + sequence);
        }
        // UUID COMPLETO da unidade: unicidade por construcao (D1).
        return "COBILL-" + unitId + "-"
                + String.format("%04d%02d", competence.getYear(), competence.getMonthValue())
                + "-" + String.format("%03d", sequence);
    }

    /** Unicidade dos identificadores de um lote de documentos: duplicado e recusado. */
    public static void requireUnique(List<Slip> slips) {
        Objects.requireNonNull(slips, "lista de documentos e obrigatoria");
        Set<String> seen = new HashSet<>();
        for (Slip slip : slips) {
            if (!seen.add(slip.documentIdentifier())) {
                throw new IllegalArgumentException(
                        "identificador de documento duplicado: " + slip.documentIdentifier());
            }
        }
    }

    /** Carne mensal: 12 documentos mensais (um por competencia do ano), no valor mensal. */
    public static List<Slip> monthlyBooklet(UUID unitId, int year, BigDecimal monthlyAmount, int dueDay) {
        requireAmount(monthlyAmount);
        requireDueDay(dueDay);
        List<Slip> slips = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth competence = YearMonth.of(year, month);
            slips.add(new Slip(documentIdentifier(unitId, competence, 1), unitId, competence,
                    dueDate(competence, dueDay), monthlyAmount));
        }
        requireUnique(slips);
        return List.copyOf(slips);
    }

    /**
     * Carne anual: o valor ANUAL em 12 parcelas mensais; a soma das
     * parcelas e exatamente o valor anual (residuo na ultima).
     */
    public static List<Slip> annualBooklet(UUID unitId, int year, BigDecimal annualAmount, int dueDay) {
        requireAmount(annualAmount);
        requireDueDay(dueDay);
        BigDecimal base = annualAmount.divide(new BigDecimal("12"), 2, RoundingMode.DOWN);
        if (base.signum() <= 0) {
            throw new IllegalArgumentException("valor anual nao comporta 12 parcelas positivas: " + annualAmount);
        }
        List<Slip> slips = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            YearMonth competence = YearMonth.of(year, month);
            BigDecimal amount = month < 12 ? base : annualAmount.subtract(accumulated);
            accumulated = accumulated.add(amount);
            slips.add(new Slip(documentIdentifier(unitId, competence, 1), unitId, competence,
                    dueDate(competence, dueDay), amount));
        }
        requireUnique(slips);
        return List.copyOf(slips);
    }

    private static LocalDate dueDate(YearMonth competence, int dueDay) {
        return competence.atDay(Math.min(dueDay, competence.lengthOfMonth()));
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("valor do carne deve ser positivo: " + amount);
        }
    }

    private static void requireDueDay(int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException("dia de vencimento fora de 1..31: " + dueDay);
        }
    }
}
