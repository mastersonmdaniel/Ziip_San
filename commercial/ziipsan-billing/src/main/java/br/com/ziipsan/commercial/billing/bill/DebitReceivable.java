package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Débito a cobrar: valor devido (serviço, parcelamento, acréscimo...) que entra na conta em
 * parcelas. O chamador seleciona os que entram no mês (não cancelado, não em revisão, ainda com
 * parcelas); o cálculo da parcela está em {@link Installments}.
 *
 * <p>DAD-20 (P2.5): o tipo deixou de ser texto livre — é o item do catálogo {@code debit_types}
 * ({@link DebitType}, já resolvido pelo chamador), e o lançamento carrega a sua proveniência:
 *
 * @param debitTypeId    item do catálogo {@code debit_types}
 * @param debitTypeCode  código do tipo (fotografia do catálogo, para a linha da conta)
 * @param generatesBill  "gera conta" resolvido do catálogo ({@link DebitType#generatesBill()})
 * @param sourceType     tipo da origem ({@link ReceivableSourceType}); nunca texto livre
 * @param sourceId       objeto de origem (OS, acordo, infração...); obrigatório nos tipos de
 *                       {@link ReceivableSourceType#requiresSourceId()}
 * @param legacyReference referência do lançamento no sistema de origem; obrigatória em
 *                       {@code LEGACY_IMPORT} (é o que a P2.10 usa para conferir o saldo migrado)
 * @param serviceOrderId ordem de serviço que gerou o débito; <b>obrigatório para débito de
 *                       serviço</b> ({@code sourceType = SERVICE_ORDER}) e proibido nos demais tipos
 * @param accountingMonth mês contábil (competência) do lançamento, primeiro dia do mês
 * @param collectionMonth mês de arrecadação (mês em que a cobrança entra na conta), primeiro dia
 *                       do mês; {@code null} = ainda não programado; nunca anterior ao contábil
 * @param financingTerm  prazo de financiamento em meses, quando o débito é financiado
 *                       (parcelamento); {@code null} = sem financiamento; se informado, é positivo
 */
public record DebitReceivable(UUID id, UUID debitTypeId, String debitTypeCode, boolean generatesBill,
                              BigDecimal total, int installments, int installmentsDone,
                              ReceivableSourceType sourceType, UUID sourceId, String legacyReference,
                              UUID serviceOrderId, LocalDate accountingMonth, LocalDate collectionMonth,
                              Integer financingTerm) {

    public DebitReceivable {
        if (id == null || debitTypeId == null || debitTypeCode == null || debitTypeCode.isBlank()) {
            throw new IllegalArgumentException("Débito sem identificação ou tipo (catálogo debit_types)");
        }
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Valor do débito deve ser positivo: " + total);
        }
        if (installments < 1 || installmentsDone < 0 || installmentsDone > installments) {
            throw new IllegalArgumentException("Parcelas inconsistentes: " + installmentsDone + "/" + installments);
        }
        ReceivableChecks.checkProvenance("Débito", sourceType, sourceId, legacyReference, serviceOrderId,
                accountingMonth, collectionMonth, financingTerm);
    }

    /** O tipo do catálogo, recomposto a partir dos campos resolvidos. */
    public DebitType type() {
        return new DebitType(debitTypeId, debitTypeCode, generatesBill);
    }

    public boolean hasInstallmentToCharge() {
        return installmentsDone < installments;
    }
}
