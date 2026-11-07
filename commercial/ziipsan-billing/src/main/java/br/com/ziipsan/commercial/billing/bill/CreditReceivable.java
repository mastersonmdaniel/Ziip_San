package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Crédito a realizar: valor a favor do cliente abatido da conta em parcelas.
 *
 * <p>DAD-20 (P2.5): a origem deixou de ser texto livre — é o item do catálogo {@code credit_origins}
 * (classe a; {@link CreditOrigin}, já resolvido pelo chamador), e o lançamento carrega a mesma
 * proveniência dos débitos. Origem fora do catálogo (id nulo ou código em branco) e
 * {@code source_type} fora dos seis valores da spec são recusados na construção.
 *
 * @param creditOriginId item do catálogo {@code credit_origins}
 * @param originCode     código da origem (fotografia do catálogo, para a linha da conta)
 * @param residual       parte já "concedida" em meses anteriores que a conta não comportou e que
 *                       passa ao mês seguinte (um crédito nunca excede a conta)
 * @param sourceType     tipo da origem ({@link ReceivableSourceType}); nunca texto livre
 * @param sourceId       objeto de origem (acordo, ajuste de pagamento...); obrigatório nos tipos
 *                       de {@link ReceivableSourceType#requiresSourceId()}
 * @param legacyReference referência do lançamento no sistema de origem; obrigatória em
 *                       {@code LEGACY_IMPORT}
 * @param serviceOrderId ordem de serviço ligada ao crédito, quando houver; com
 *                       {@code sourceType = SERVICE_ORDER} ela é exigida também aqui (a regra do
 *                       aceite nomeia o débito de serviço, e a proveniência tipada é a mesma dos
 *                       dois lados — ver {@link ReceivableChecks})
 * @param accountingMonth mês contábil (competência) do lançamento, primeiro dia do mês
 * @param collectionMonth mês de arrecadação/realização programado, primeiro dia do mês;
 *                       {@code null} = ainda não programado; nunca anterior ao contábil
 * @param financingTerm  prazo de financiamento em meses, quando o crédito nasce de financiamento;
 *                       {@code null} = sem financiamento; se informado, é positivo
 */
public record CreditReceivable(UUID id, UUID creditOriginId, String originCode,
                               BigDecimal total, int installments, int installmentsDone,
                               BigDecimal residual,
                               ReceivableSourceType sourceType, UUID sourceId, String legacyReference,
                               UUID serviceOrderId, LocalDate accountingMonth, LocalDate collectionMonth,
                               Integer financingTerm) {

    public CreditReceivable {
        if (id == null || creditOriginId == null || originCode == null || originCode.isBlank()) {
            throw new IllegalArgumentException("Crédito sem identificação ou origem (catálogo credit_origins)");
        }
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Valor do crédito deve ser positivo: " + total);
        }
        if (installments < 1 || installmentsDone < 0 || installmentsDone > installments) {
            throw new IllegalArgumentException("Parcelas inconsistentes: " + installmentsDone + "/" + installments);
        }
        if (residual == null || residual.signum() < 0) {
            throw new IllegalArgumentException("Resíduo do crédito deve ser informado e não negativo: " + residual);
        }
        ReceivableChecks.checkProvenance("Crédito", sourceType, sourceId, legacyReference, serviceOrderId,
                accountingMonth, collectionMonth, financingTerm);
    }

    /** A origem do catálogo, recomposta a partir dos campos resolvidos. */
    public CreditOrigin origin() {
        return new CreditOrigin(creditOriginId, originCode);
    }
}
