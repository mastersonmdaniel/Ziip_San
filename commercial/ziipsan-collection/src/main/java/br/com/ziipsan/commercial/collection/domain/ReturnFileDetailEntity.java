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
 * Linha do arquivo de retorno (detalhe FEBRABAN 150). E AQUI - e nao em {@code payments} -
 * que vivem as colunas proprias do leiaute: {@code captureForm} (forma de captura),
 * {@code paymentForm} (forma de pagamento), {@code collectingBranch} (agencia arrecadadora),
 * {@code authentication} (autenticacao bancaria), {@code lineNumber} (numero da linha no
 * arquivo) e {@code barcode} (codigo de barras). Pagamento por Pix ou cartao nao gera
 * detalhe de retorno, portanto nunca usa essas colunas.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "return_file_details")
@Getter
@Setter
public class ReturnFileDetailEntity extends TenantAwareEntity {

    @Column(name = "return_file_id", nullable = false)
    private UUID returnFileId;

    /** Pagamento gerado por esta linha; nulo enquanto nao identificado. */
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "barcode", length = 60)
    private String barcode;

    @Column(name = "capture_form", length = 30)
    private String captureForm;

    @Column(name = "payment_form", length = 30)
    private String paymentForm;

    @Column(name = "collecting_branch", length = 20)
    private String collectingBranch;

    @Column(name = "authentication", length = 60)
    private String authentication;

    /** CNPJ do pagador informado na linha, normalizado e validado pela P1.39. */
    @Column(name = "payer_document", length = 14)
    private String payerDocument;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;
}
