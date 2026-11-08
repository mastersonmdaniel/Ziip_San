package br.com.ziipsan.core.chemicals;

import br.com.ziipsan.core.inventory.NfeEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Recebimento de insumo quimico por nota fiscal com conferencia
 * (ED-89, OPE-06), sobre o parser/conferencia REAIS da ED-58
 * ({@link NfeEntry}): a ED-89 nao reimplementa leitura de NF-e.
 *
 * <p>Regras desta entrega, alem da conferencia da ED-58:
 * <ul>
 *   <li>cada linha recebida exige LOTE e VALIDADE (o aceite pede lote e
 *       validade no cadastro; lote vencido na data do recebimento e
 *       recusado — receber produto vencido nao e ato silencioso);</li>
 *   <li>cada lote exige o LAUDO DO FORNECEDOR (referencia obrigatoria);</li>
 *   <li>divergencia de conferencia NAO posta entrada (espelho ED-58:
 *       posta-se tudo conferido ou nada);</li>
 *   <li>idempotente pela chave de acesso da NF-e: reenvio devolve
 *       {@code duplicate=true} e nao posta duas vezes.</li>
 * </ul>
 */
public final class ChemReceipt {
    private ChemReceipt() {}

    /** Lote recebido: lote, validade e laudo do fornecedor, por item. */
    public record LotReceipt(UUID tenantId, String itemCode, String locationCode, String lotCode,
                             LocalDate expiryDate, String supplierCertificateRef,
                             BigDecimal qty, Instant receivedAt) {
        public LotReceipt {
            Objects.requireNonNull(tenantId, "tenant e obrigatorio");
            if (itemCode == null || itemCode.isBlank()) throw new IllegalArgumentException("item do lote e obrigatorio");
            if (lotCode == null || lotCode.isBlank())
                throw new ChemExceptions.ValidationException("LOT_REQUIRED", "recebimento de quimico exige o codigo do lote: " + itemCode);
            Objects.requireNonNull(expiryDate, "validade do lote e obrigatoria: " + itemCode);
            if (supplierCertificateRef == null || supplierCertificateRef.isBlank())
                throw new ChemExceptions.ValidationException("SUPPLIER_CERTIFICATE_REQUIRED",
                        "recebimento de quimico exige a referencia do laudo do fornecedor: " + itemCode + " lote " + lotCode);
            if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("quantidade do lote deve ser positiva");
        }
        public boolean expiredOn(LocalDate day) { return expiryDate.isBefore(day); }
    }

    public record Receipt(UUID tenantId, String nfeAccessKey, String supplierDocument,
                          List<LotReceipt> lots, Instant receivedAt) {
        public Receipt { lots = lots == null ? List.of() : List.copyOf(lots); }
    }

    public record ReceiptResult(boolean duplicate, Receipt receipt, NfeEntry.Conference conference) {}

    /**
     * Recebe a NF-e: confere contra o esperado, valida lote/validade/laudo
     * de cada linha e, so se tudo conferir, posta as entradas na ED-58
     * pela porta e grava os lotes.
     *
     * @param lotInfo por codigo de produto da NF-e: lote, validade e laudo
     *        declarados no recebimento fisico (o {@code <rastro>} da NF-e,
     *        quando presente, e conferido pela fiacao; aqui o dado
     *        autoritativo e o declarado e conferido pelo operador).
     */
    public static ReceiptResult receive(ChemStore store, ChemPorts.StockEntryPort stock,
                                        UUID tenantId, String nfeXml,
                                        java.util.Map<String, BigDecimal> expectedByCode,
                                        java.util.Map<String, LotReceipt> lotInfoByProductCode,
                                        Instant at) {
        Objects.requireNonNull(store, "store e obrigatorio");
        Objects.requireNonNull(stock, "porta de estoque (ED-58) e obrigatoria");
        NfeEntry.ParsedNfe nfe = NfeEntry.parse(nfeXml);
        if (store.receiptProcessed(tenantId, nfe.accessKey())) {
            Receipt previous = store.receipts(tenantId).stream()
                    .filter(r -> r.nfeAccessKey().equals(nfe.accessKey())).findFirst().orElse(null);
            return new ReceiptResult(true, previous, null);
        }
        NfeEntry.Conference conference = NfeEntry.check(nfe, expectedByCode);
        if (!conference.confered()) {
            throw new ChemExceptions.ValidationException("NFE_DIVERGENCE",
                    "conferencia da NF-e divergiu e NADA foi postado: " + conference.divergences());
        }
        LocalDate today = LocalDate.ofInstant(at, java.time.ZoneOffset.UTC);
        List<LotReceipt> lots = new java.util.ArrayList<>();
        for (NfeEntry.NfeItem line : nfe.items()) {
            LotReceipt declared = lotInfoByProductCode == null ? null : lotInfoByProductCode.get(line.productCode());
            if (declared == null)
                throw new ChemExceptions.ValidationException("LOT_REQUIRED",
                        "linha da NF-e sem lote/validade/laudo declarados no recebimento: " + line.productCode());
            if (!declared.itemCode().equals(line.productCode()))
                throw new ChemExceptions.ValidationException("LOT_ITEM_MISMATCH",
                        "lote declarado para " + declared.itemCode() + " nao corresponde a linha " + line.productCode());
            if (declared.qty().compareTo(line.qty()) != 0)
                throw new ChemExceptions.ValidationException("LOT_QTY_MISMATCH",
                        "quantidade do lote " + declared.lotCode() + " (" + declared.qty() + ") difere da NF-e (" + line.qty() + ")");
            if (declared.expiredOn(today))
                throw new ChemExceptions.ValidationException("LOT_EXPIRED_ON_RECEIPT",
                        "lote " + declared.lotCode() + " vencido em " + declared.expiryDate() + ": recebimento recusado");
            // Local POR LOTE (correcao R3 da revisao da ED-89): vale o
            // locationCode declarado neste LotReceipt — NF-e multi-local
            // posta cada lote no seu local; local nao declarado recusa.
            if (declared.locationCode() == null || declared.locationCode().isBlank())
                throw new ChemExceptions.ValidationException("LOT_LOCATION_REQUIRED",
                        "lote sem local de estoque declarado no recebimento: " + declared.itemCode()
                                + " lote " + declared.lotCode());
            lots.add(new LotReceipt(tenantId, declared.itemCode(), declared.locationCode(), declared.lotCode(),
                    declared.expiryDate(), declared.supplierCertificateRef(), declared.qty(), at));
        }
        for (LotReceipt lot : lots) {
            stock.postEntry(tenantId, lot.itemCode(), lot.locationCode(), lot.qty(), unitCostOf(nfe, lot.itemCode()),
                    "NFE " + nfe.accessKey() + " lote " + lot.lotCode());
            store.saveLotReceipt(lot);
        }
        Receipt receipt = new Receipt(tenantId, nfe.accessKey(), nfe.supplierDocument(), lots, at);
        store.saveReceipt(receipt);
        store.markReceiptProcessed(tenantId, nfe.accessKey());
        return new ReceiptResult(false, receipt, conference);
    }

    private static BigDecimal unitCostOf(NfeEntry.ParsedNfe nfe, String productCode) {
        return nfe.items().stream().filter(i -> i.productCode().equals(productCode))
                .map(NfeEntry.NfeItem::unitValue).findFirst().orElse(null);
    }
}
