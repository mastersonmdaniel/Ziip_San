package br.com.ziipsan.commercial.billing.bill;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Validação da proveniência DAD-20, compartilhada por {@link DebitReceivable} e
 * {@link CreditReceivable} (as regras são as mesmas dos dois lados; o que muda é a palavra do
 * lançamento nas mensagens). As mesmas regras existem no banco como CHECKs nomeados no trecho F3
 * da V4_4 — domínio e banco recusam igual, como no restante do módulo.
 *
 * <ul>
 *   <li>{@code sourceType} é obrigatório (origem inválida/ausente recusa);</li>
 *   <li>tipos com objeto de origem ({@link ReceivableSourceType#requiresSourceId()}) exigem
 *       {@code sourceId};</li>
 *   <li>débito/crédito de serviço ({@code SERVICE_ORDER}) exige {@code serviceOrderId} — é a regra
 *       "service_order_id obrigatório para débito de serviço" do aceite;</li>
 *   <li>{@code serviceOrderId} informado com outro {@code sourceType} é contradição e recusa;</li>
 *   <li>{@code LEGACY_IMPORT} exige {@code legacyReference} (sem ela o lançamento importado não é
 *       conferível com o legado); {@code legacyReference} em branco, quando informada, recusa;</li>
 *   <li>{@code accountingMonth} é obrigatório e é o primeiro dia do mês; {@code collectionMonth},
 *       quando informado, também é primeiro dia e nunca é anterior ao mês contábil;</li>
 *   <li>{@code financingTerm}, quando informado, é positivo.</li>
 * </ul>
 */
final class ReceivableChecks {

    private ReceivableChecks() {
    }

    static void checkProvenance(String kind, ReceivableSourceType sourceType, UUID sourceId,
                                String legacyReference, UUID serviceOrderId,
                                LocalDate accountingMonth, LocalDate collectionMonth, Integer financingTerm) {
        if (sourceType == null) {
            throw new IllegalArgumentException(kind + " sem source_type (origem tipada é obrigatória, DAD-20)");
        }
        if (sourceType.requiresSourceId() && sourceId == null) {
            throw new IllegalArgumentException(kind + " de origem " + sourceType + " exige source_id");
        }
        if (sourceType == ReceivableSourceType.SERVICE_ORDER && serviceOrderId == null) {
            throw new IllegalArgumentException(kind + " de serviço (SERVICE_ORDER) exige service_order_id");
        }
        if (serviceOrderId != null && sourceType != ReceivableSourceType.SERVICE_ORDER) {
            throw new IllegalArgumentException(kind + " com service_order_id exige source_type SERVICE_ORDER, veio "
                    + sourceType);
        }
        if (legacyReference != null && legacyReference.isBlank()) {
            throw new IllegalArgumentException(kind + " com legacy_reference em branco");
        }
        if (sourceType == ReceivableSourceType.LEGACY_IMPORT
                && (legacyReference == null || legacyReference.isBlank())) {
            throw new IllegalArgumentException(kind + " importado do legado (LEGACY_IMPORT) exige legacy_reference");
        }
        if (accountingMonth == null || accountingMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(kind + " exige accounting_month no primeiro dia do mês: "
                    + accountingMonth);
        }
        if (collectionMonth != null) {
            if (collectionMonth.getDayOfMonth() != 1) {
                throw new IllegalArgumentException(kind + " com collection_month fora do primeiro dia do mês: "
                        + collectionMonth);
            }
            if (collectionMonth.isBefore(accountingMonth)) {
                throw new IllegalArgumentException(kind + " com collection_month " + collectionMonth
                        + " anterior ao accounting_month " + accountingMonth);
            }
        }
        if (financingTerm != null && financingTerm <= 0) {
            throw new IllegalArgumentException(kind + " com financing_term não positivo: " + financingTerm);
        }
    }
}
