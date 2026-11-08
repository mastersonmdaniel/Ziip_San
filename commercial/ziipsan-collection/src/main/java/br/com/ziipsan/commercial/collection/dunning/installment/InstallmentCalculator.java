package br.com.ziipsan.commercial.collection.dunning.installment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculo da agenda de parcelas (P4.1), puro e deterministico.
 *
 * <p>Metodos ({@code interest_method} de DAD-21), sobre o valor
 * FINANCIADO (total − abatimentos − entrada):
 * <ul>
 *   <li>{@code NONE}: sem juros (a taxa deve ser zero); divisao igual;</li>
 *   <li>{@code PRICE}: prestacao fixa pela formula da tabela Price,
 *       {@code PMT = P * i / (1 − (1+i)^−n)}; com taxa zero, divisao igual;</li>
 *   <li>{@code COMPOUND}: juros compostos sobre o financiado,
 *       {@code total = P * (1+i)^n}, dividido em parcelas iguais.</li>
 * </ul>
 *
 * <p>Arredondamento: HALF_UP em 2 casas (padrao monetario do projeto);
 * a ULTIMA parcela absorve o residuo, de modo que a soma da agenda e
 * exatamente o total calculado — nunca se cria nem se some centavo.
 */
public final class InstallmentCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;

    private InstallmentCalculator() {
    }

    /** Uma parcela da agenda. */
    public record Installment(int number, LocalDate dueDate, BigDecimal amount) {
        public Installment {
            if (number < 1 || dueDate == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("parcela exige numero, vencimento e valor positivo");
            }
        }
    }

    /** Agenda calculada: parcelas e o total exato que a soma delas reproduz. */
    public record Schedule(List<Installment> installments, BigDecimal financedAmount, BigDecimal totalAmount) {
        public Schedule {
            installments = List.copyOf(installments);
        }
    }

    public static Schedule schedule(BigDecimal financed, InstallmentEnums.InterestMethod method,
                                    BigDecimal monthlyRate, int count, LocalDate firstDueDate) {
        if (financed == null || financed.signum() <= 0) {
            throw new IllegalArgumentException("valor financiado deve ser positivo");
        }
        if (method == null || monthlyRate == null || count < 1 || firstDueDate == null) {
            throw new IllegalArgumentException("metodo, taxa, numero de parcelas e primeiro vencimento sao obrigatorios");
        }
        if (monthlyRate.signum() < 0) {
            throw new IllegalArgumentException("taxa de juros nao pode ser negativa");
        }
        BigDecimal totalExact = switch (method) {
            case NONE -> {
                if (monthlyRate.signum() != 0) {
                    throw new IllegalArgumentException("interest_method NONE exige taxa zero");
                }
                yield financed;
            }
            case PRICE -> {
                if (monthlyRate.signum() == 0) {
                    yield financed;
                }
                BigDecimal onePlusI = BigDecimal.ONE.add(monthlyRate);
                BigDecimal discount = BigDecimal.ONE.subtract(
                        BigDecimal.ONE.divide(onePlusI.pow(count, MC), MC), MC);
                BigDecimal pmt = financed.multiply(monthlyRate, MC).divide(discount, MC);
                yield pmt.multiply(BigDecimal.valueOf(count), MC);
            }
            case COMPOUND -> financed.multiply(BigDecimal.ONE.add(monthlyRate).pow(count, MC), MC);
        };
        BigDecimal total = totalExact.setScale(2, RoundingMode.HALF_UP);
        BigDecimal each = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        List<Installment> installments = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int n = 1; n <= count; n++) {
            BigDecimal amount;
            if (n < count) {
                amount = each;
                accumulated = accumulated.add(amount);
            } else {
                amount = total.subtract(accumulated);
            }
            installments.add(new Installment(n, firstDueDate.plusMonths(n - 1L), amount));
        }
        return new Schedule(installments, financed, total);
    }
}
