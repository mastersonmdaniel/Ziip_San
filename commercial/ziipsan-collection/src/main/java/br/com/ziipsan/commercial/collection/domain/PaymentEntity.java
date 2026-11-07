package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Pagamento recebido (arrecadacao), no estado final definido pela P3.1 (V6_2).
 *
 * <p>As colunas proprias do leiaute FEBRABAN 150 ({@code capture_form}, {@code payment_form},
 * {@code collecting_branch}, {@code authentication}, {@code line_number}, {@code barcode})
 * NAO vivem aqui: sao do arquivo de retorno e ficam em {@link ReturnFileDetailEntity}.
 * Pix e cartao nunca passam pelo arquivo de retorno, logo nunca tem essas colunas.
 *
 * <p>{@code collectionMonth} e a referencia de arrecadacao (DAD-19): sempre dia 1 do mes
 * em que o valor foi recebido, independente da competencia de faturamento da conta.
 * {@code fiscalDocumentId} liga o pagamento ao documento fiscal (FK composta com o tenant
 * para {@code fiscal_documents}, V4_5 do modulo fiscal).
 *
 * <p>Valores: {@code grossAmount} e o valor da baixa (o que quita a conta);
 * {@code segregatedTaxAmount} e o tributo segregado na liquidacao (split payment);
 * {@code withheldAmount} e a retencao na fonte (pagador publico, IN RFB 1.234/2012);
 * {@code netAmount} e o liquido efetivamente recebido
 * ({@code gross - segregated - withheld}, conferido pela conciliacao).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class PaymentEntity extends TenantAwareEntity {

    /** Conta baixada; nulo enquanto o pagamento nao foi identificado/classificado. */
    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "unit_id")
    private UUID unitId;

    /** Canal de recebimento: BOLETO, PIX, CARTAO, DEBITO_AUTOMATICO, BALCAO. Sem FEBRABAN aqui. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "segregated_tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal segregatedTaxAmount = new BigDecimal("0.00");

    @Column(name = "withheld_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal withheldAmount = new BigDecimal("0.00");

    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    /** Referencia de arrecadacao (dia 1), independente da competencia da conta. */
    @Column(name = "collection_month", nullable = false)
    private LocalDate collectionMonth;

    @Column(name = "fiscal_document_id")
    private UUID fiscalDocumentId;

    /** CNPJ do pagador na forma normalizada validada pela P1.39 (aceita alfanumerico). */
    @Column(name = "payer_document", length = 14)
    private String payerDocument;

    /** Pagador de natureza publica (habilita a retencao da IN RFB 1.234/2012). */
    @Column(name = "payer_public_nature", nullable = false)
    private boolean payerPublicNature;

    /** RECEBIDO, CLASSIFICADO, BAIXADO, DUPLICIDADE, NAO_IDENTIFICADO, TRANSFERIDO. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "RECEBIDO";
}
