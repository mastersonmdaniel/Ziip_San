package br.com.ziipsan.core.parameters;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Limites travados do §2.3 (P0.20) e regras de coerência do P0.19, por chave.
 *
 * <p>Cada método é nomeado pela chave que valida e recusa valor fora do limite com
 * {@link IllegalArgumentException} que cita a norma. O que é trava do produto está
 * aqui; o que é teto de agência chega nos perfis da P2.31 e não é inventado aqui:
 * <ul>
 *   <li>{@code meter.*} é classe d (livre, PARAMETROS G1): a trava é de coerência
 *       (inteiro positivo) e a referência convergente de SC (6 meses, 3 ciclos) é
 *       sugestão documentada, não teto;</li>
 *   <li>{@code fee.fine_pct} acima de 2% tem a regra completa no módulo collection
 *       ({@code FineRule} + CHECK da V6_6); aqui fica só o piso/teto numérico, para
 *       não duplicar a regra de aceite em dois módulos;</li>
 *   <li>{@code fee.interest_mode}: só juros simples; {@code CALENDAR_MONTH} (mês cheio,
 *       modo do GSAN) é recusado porque não está implementado — decisão pendente do
 *       dono, registrada no catalogo do core V3.</li>
 * </ul>
 */
public final class ParameterLimits {

    /** Piso de retenção do histórico de leitura e cobrança (NR 11, art. 22). */
    public static final int BILLING_HISTORY_FLOOR_MONTHS = 120;

    /** Mínimo de dias de vencimento para concessionária (Lei 8.987, art. 7º-A). */
    public static final int DUE_DATE_OPTIONS_MIN_FOR_CONCESSIONAIRE = 6;

    /** Modos de juros suportados nesta versão (juros simples, sem capitalização). */
    public static final Set<String> SUPPORTED_INTEREST_MODES = Set.of(
            "DAILY_RATE_WITH_CAP", "DAILY_RATE", "MONTHLY_PRO_RATA");

    private ParameterLimits() {
    }

    /**
     * {@code bill.due_date_options}: dias 1..31, distintos; para concessionária, ao menos
     * 6 (Lei 8.987, art. 7º-A) e só amplia — reduzir abaixo da lista aceita é recusado.
     * Para autarquia o mínimo está a confirmar (pendência registrada no core V3).
     */
    public static void validateDueDateOptions(List<Integer> days, boolean concessionaire,
                                              Integer previousAcceptedCount) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("bill.due_date_options é obrigatório: informe ao menos um dia");
        }
        Set<Integer> seen = new HashSet<>();
        for (Integer day : days) {
            if (day == null || day < 1 || day > 31) {
                throw new IllegalArgumentException("bill.due_date_options: dia fora de 1..31: " + day);
            }
            if (!seen.add(day)) {
                throw new IllegalArgumentException("bill.due_date_options: dia duplicado: " + day);
            }
        }
        if (concessionaire && days.size() < DUE_DATE_OPTIONS_MIN_FOR_CONCESSIONAIRE) {
            throw new IllegalArgumentException(
                    "bill.due_date_options: concessionária precisa ofertar ao menos 6 dias "
                            + "(Lei 8.987/1995, art. 7º-A); recebidos: " + days.size());
        }
        if (previousAcceptedCount != null && days.size() < previousAcceptedCount) {
            throw new IllegalArgumentException(
                    "bill.due_date_options: só amplia — a lista aceita tinha "
                            + previousAcceptedCount + " dias e a nova tem " + days.size());
        }
    }

    /** {@code meter.average_months} (NR 11, art. 35): inteiro positivo; referência SC: 6. */
    public static void validateAverageMonths(int months) {
        if (months < 1) {
            throw new IllegalArgumentException("meter.average_months precisa ser ao menos 1 (NR 11, art. 35)");
        }
    }

    /** {@code meter.max_estimated_cycles} (NR 11, art. 35): inteiro positivo; referência SC: 3. */
    public static void validateMaxEstimatedCycles(int cycles) {
        if (cycles < 1) {
            throw new IllegalArgumentException(
                    "meter.max_estimated_cycles precisa ser ao menos 1 (NR 11, art. 35)");
        }
    }

    /**
     * {@code ret.billing_history_months} (NR 11, art. 22): piso 120, só amplia.
     * Reduzir abaixo do piso ou abaixo do valor já aceito é recusado.
     */
    public static void validateBillingHistoryMonths(int months, Integer previousAcceptedMonths) {
        if (months < BILLING_HISTORY_FLOOR_MONTHS) {
            throw new IllegalArgumentException(
                    "ret.billing_history_months: piso 120 meses (NR 11, art. 22); recebidos: " + months);
        }
        if (previousAcceptedMonths != null && months < previousAcceptedMonths) {
            throw new IllegalArgumentException(
                    "ret.billing_history_months: só amplia — o valor aceito era "
                            + previousAcceptedMonths + " e o novo é " + months);
        }
    }

    /**
     * {@code bill.invoice_template}: o bloco do fornecedor é travado na cobrança
     * (CDC, art. 42-A). A emissão com o bloco é da P2.32; aqui fica a trava, para que
     * nenhum modelo sem o bloco seja aceito como válido.
     */
    public static void validateInvoiceHasSupplierBlock(boolean hasSupplierBlock) {
        if (!hasSupplierBlock) {
            throw new IllegalArgumentException(
                    "bill.invoice_template: o bloco do fornecedor é travado na cobrança (CDC, art. 42-A)");
        }
    }

    /** {@code fee.interest_mode}: só juros simples; mês cheio (GSAN) não implementado. */
    public static void validateInterestMode(String mode) {
        if (mode == null || !SUPPORTED_INTEREST_MODES.contains(mode)) {
            throw new IllegalArgumentException(
                    "fee.interest_mode '" + mode + "' fora do catálogo P0.20 "
                            + "(juros simples: DAILY_RATE_WITH_CAP, DAILY_RATE, MONTHLY_PRO_RATA; "
                            + "CALENDAR_MONTH não implementado, decisão pendente do dono)");
        }
    }

    /** {@code fee.fine_pct}: percentual não negativo (o teto e o aceite vivem em FineRule). */
    public static void validateFinePercent(BigDecimal percent) {
        if (percent == null || percent.signum() < 0) {
            throw new IllegalArgumentException("fee.fine_pct é obrigatório e não pode ser negativo");
        }
    }
}
