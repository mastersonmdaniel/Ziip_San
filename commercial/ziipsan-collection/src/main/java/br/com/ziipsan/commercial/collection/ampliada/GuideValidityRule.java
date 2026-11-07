package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.billing.bill.LateFeeCalculator;
import br.com.ziipsan.commercial.billing.bill.LateFeePolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Guia (inclusive a do usuario avulso) e extrato com validade (ED-26): o valor
 * impresso vale ate a data de validade; pago DEPOIS dela, o valor devido e
 * RECALCULADO pela data real do pagamento.
 *
 * <p><b>Paridade com o billing (correcao M1 da revisao cruzada):</b> o recalculo
 * NAO tem formula propria - ele DELEGA ao {@link LateFeeCalculator} da P2.5 com a
 * {@link LateFeePolicy} do tenant. Guia e conta usam, portanto, a MESMA regra:
 * carencia em dias uteis, modo da multa {@code FLAT} ou {@code PER_DAY} com teto,
 * periodo dos juros parametrizado (nada de 30 dias fixos embutidos), teto de
 * juros, multa ja cobrada nao cobrada de novo, multas embutidas fora da base da
 * multa e isencao de debito automatico. A validade da guia faz, nesta delegacao,
 * o papel do vencimento da conta.
 *
 * <p><b>Fiacao de integracao (declarada, nao simulada):</b>
 * <ul>
 *   <li>a {@link LateFeePolicy} e montada pela fiacao a partir dos parametros
 *       {@code fee.*} do tenant (o modo vem de {@code fee.fine_mode}, lido por
 *       {@code BillingParameters} da P2.5; percentuais, periodo, tetos e carencia
 *       sao campos da politica, SEM padrao em nenhum dos dois pacotes) - esta
 *       classe nao le parametros nem inventa chaves novas;</li>
 *   <li>o calendario de dias uteis ({@code businessDay}) e o do municipio do
 *       tenant, entregue pela fiacao - nenhum calendario e presumido aqui;</li>
 *   <li>o {@code pom} do modulo {@code ziipsan-collection} precisa declarar a
 *       dependencia do {@code ziipsan-billing} (onde vivem
 *       {@code LateFeeCalculator}/{@code LateFeePolicy}) - ou a politica ser
 *       promovida ao core; a decisao e da integracao, registrada no relatorio.</li>
 * </ul>
 */
public final class GuideValidityRule {

    /** Guia de usuario avulso: sem conta e sem unidade vinculada; exige descricao,
     *  valor e validade - e o nome de quem paga, para o comprovante. */
    public record AvulsoGuide(String payerName, String description, BigDecimal amount,
                              LocalDate issuedOn, LocalDate validUntil) {
        public AvulsoGuide {
            if (payerName == null || payerName.isBlank()) {
                throw new IllegalArgumentException("guia avulsa exige o nome de quem paga");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("guia avulsa exige a descricao do que se paga");
            }
            if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("guia avulsa exige valor positivo: " + amount);
            }
            Objects.requireNonNull(issuedOn, "emissao da guia e obrigatoria");
            Objects.requireNonNull(validUntil, "validade da guia e obrigatoria");
            if (validUntil.isBefore(issuedOn)) {
                throw new IllegalArgumentException("validade anterior a emissao: " + validUntil + " < " + issuedOn);
            }
        }
    }

    /** @param daysLate dias de atraso contra a validade (0 quando pago no prazo) */
    public record RecalculatedAmount(BigDecimal principal, BigDecimal fine, BigDecimal interest,
                                     BigDecimal totalDue, long daysLate) {
    }

    private GuideValidityRule() {
    }

    /**
     * Valor devido da guia paga em {@code paymentDate}, recalculado pela politica
     * de acrescimos do tenant via {@link LateFeeCalculator} (billing, P2.5).
     *
     * @param principal             valor impresso na guia (base do recalculo)
     * @param validUntil            validade da guia (papel de vencimento na delegacao)
     * @param paymentDate           data real do pagamento
     * @param policy                politica {@code fee.*} do tenant montada pela fiacao
     * @param businessDay           calendario de dias uteis do municipio (fiacao)
     * @param fineAlreadyCharged    a multa desta guia ja foi cobrada: nao se cobra de novo
     * @param finesIncludedInAmount multas que ja fazem parte do principal (0 se nenhuma);
     *                              saem da base da multa, como no billing
     * @param directDebit           pagamento por debito automatico: sem acrescimo
     */
    public static RecalculatedAmount amountDueOn(BigDecimal principal, LocalDate validUntil,
                                                 LocalDate paymentDate, LateFeePolicy policy,
                                                 Predicate<LocalDate> businessDay,
                                                 boolean fineAlreadyCharged,
                                                 BigDecimal finesIncludedInAmount,
                                                 boolean directDebit) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("principal deve ser positivo: " + principal);
        }
        Objects.requireNonNull(validUntil, "validade e obrigatoria");
        Objects.requireNonNull(paymentDate, "data real do pagamento e obrigatoria");
        Objects.requireNonNull(policy, "politica de acrescimos do tenant (fee.*) e obrigatoria");
        Objects.requireNonNull(businessDay, "calendario de dias uteis e obrigatorio");
        // finesIncludedInAmount nulo/negativo e recusado pelo proprio LateFeeCalculator.
        LateFeeCalculator.Result charges = LateFeeCalculator.calculate(principal, fineAlreadyCharged,
                finesIncludedInAmount, validUntil, paymentDate, policy, businessDay, directDebit);
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(validUntil, paymentDate));
        return new RecalculatedAmount(principal, charges.fine(), charges.interest(),
                principal.add(charges.total()), daysLate);
    }
}
