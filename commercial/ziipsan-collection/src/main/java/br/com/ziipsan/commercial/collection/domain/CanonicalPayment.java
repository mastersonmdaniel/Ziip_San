package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.commercial.collection.payment.PaymentClassifier.AdjustmentKind;
import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Pagamento recebido (arrecadação) — modelo CANÓNICO único de {@code payments}
 * (decisão 75, PD-1, Opção A). Funde os dois modelos anteriores, ambos RETIRADOS:
 * {@code Payment} (formato FEBRABAN 150, V6_1) e {@code PaymentEntity} (estado final
 * da P3.1, V6_2). Nenhum dos dois descrevia a tabela física — um híbrido (V6_1 +
 * V6_2 + V6_3 + V15_1 + V112) — e nenhum dos dois conseguia escrever nela
 * (DIAGNOSTICO-P-D72-V151-01 §§1–2).
 *
 * <p>Vocabulário canónico de {@code status} (PD-2, CHECK {@code chk_payments_status_unified}
 * da V112): os 15 valores FEBRABAN + os 6 valores P3.1 (21 no total). O campo é
 * {@code String} de propósito: nenhum dos dois enums existentes cobre o vocabulário
 * unificado — {@code PaymentStatus} é o vocabulário FEBRABAN de 15 (classificador e
 * trilha) e a P3.1 não tem enum de status. Os pares divergentes normalizam-se na
 * derivação da V112 ({@code CLASSIFIED→CLASSIFICADO}, {@code DUPLICATE→DUPLICIDADE},
 * {@code DOC_NOT_FOUND→NAO_IDENTIFICADO}); os restantes 12 valores FEBRABAN ficam
 * como valores legítimos do vocabulário unificado.
 *
 * <p>{@code previousStatus} é {@code String} (não o enum FEBRABAN): uma transição
 * P3.1 pode ter como estado anterior um literal P3.1 ({@code RECEBIDO}, ...), que o
 * enum não contém — mapear o enum aqui faria o Hibernate recusar a leitura da linha.
 *
 * <p>Colunas (PD-3: as FEBRABAN mantêm-se como colunas próprias; a equivalência
 * {@code amount ↔ gross_amount} NÃO está provada — nunca tratar os dois como
 * sinónimos): {@code amount} é o "valor pago" do retorno; {@code grossAmount} é o
 * valor da baixa (o que quita a conta); {@code netAmount = gross - segregated -
 * withheld}. As 6 colunas de detalhe FEBRABAN ({@code capture_form},
 * {@code payment_form}, {@code collecting_branch}, {@code authentication},
 * {@code line_number}, {@code barcode}) NÃO vivem aqui: são do arquivo de retorno e
 * ficam em {@link ReturnFileDetailEntity}. As 3 colunas Pix órfãs da V6_3
 * ({@code endToEndId}, {@code txid}, {@code hybridDocumentId}) vivem aqui — nenhum
 * dos dois modelos antigos as mapeava.
 *
 * <p>Obrigações por canal (PD-4, CHECKs da V112): {@code agreementId},
 * {@code amount}, {@code identified} e {@code idempotencyKey} são anuláveis e só
 * exigidos quando {@code channel} é BOLETO ou DEBITO_AUTOMATICO (regime integral
 * do retorno/convenio); PIX, CARTAO e BALCAO escrevem sem eles. {@code identified}
 * é {@code Boolean} (não primitivo) pelo mesmo motivo.
 *
 * <p>{@code collectionMonth} é a referência de arrecadação (DAD-19): sempre dia 1
 * do mês em que o valor foi recebido, independente da competência de faturamento
 * da conta. {@code fiscalDocumentId} liga o pagamento ao documento fiscal (FK
 * composta com o tenant para {@code fiscal_documents}, V4_5 do módulo fiscal).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
public class CanonicalPayment extends TenantAwareEntity {

    /** Convénio bancário (FEBRABAN/retorno). Exigido só para BOLETO e DEBITO_AUTOMATICO. */
    @Column(name = "agreement_id")
    private UUID agreementId;

    @Column(name = "return_file_id")
    private UUID returnFileId;

    /** Valor pago (retorno FEBRABAN). Exigido só para BOLETO e DEBITO_AUTOMATICO. */
    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "pending_excess", nullable = false, precision = 14, scale = 2)
    private BigDecimal pendingExcess = BigDecimal.ZERO;

    @Column(name = "excess_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal excessAmount = BigDecimal.ZERO;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "credited_on")
    private LocalDate creditedOn;

    /** Conta baixada; nulo enquanto o pagamento não foi identificado/classificado. */
    @Column(name = "bill_id")
    private UUID billId;

    @Column(name = "unit_id")
    private UUID unitId;

    /** Canal de recebimento: BOLETO, PIX, CARTAO, DEBITO_AUTOMATICO, BALCAO. */
    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    /** Valor da baixa (o que quita a conta). */
    @Column(name = "gross_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossAmount;

    /** Tributo segregado na liquidação (split payment). */
    @Column(name = "segregated_tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal segregatedTaxAmount = new BigDecimal("0.00");

    /** Retenção na fonte (pagador público, IN RFB 1.234/2012). */
    @Column(name = "withheld_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal withheldAmount = new BigDecimal("0.00");

    /** Líquido efetivamente recebido ({@code gross - segregated - withheld}). */
    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount;

    /** Referência de arrecadação (dia 1), independente da competência da conta. */
    @Column(name = "collection_month", nullable = false)
    private LocalDate collectionMonth;

    @Column(name = "fiscal_document_id")
    private UUID fiscalDocumentId;

    /** CNPJ do pagador na forma normalizada validada pela P1.39 (aceita alfanumérico). */
    @Column(name = "payer_document", length = 14)
    private String payerDocument;

    /** Pagador de natureza pública (habilita a retenção da IN RFB 1.234/2012). */
    @Column(name = "payer_public_nature", nullable = false)
    private boolean payerPublicNature;

    /**
     * Vocabulário unificado (21 valores, CHECK da V112): 15 FEBRABAN +
     * RECEBIDO, CLASSIFICADO, BAIXADO, DUPLICIDADE, NAO_IDENTIFICADO, TRANSFERIDO.
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status = "RECEBIDO";

    /** Situação anterior, no mesmo vocabulário unificado (sem CHECK no DDL). */
    @Column(name = "previous_status", length = 30)
    private String previousStatus;

    /** Exigido só para BOLETO e DEBITO_AUTOMATICO; o conceito vive no status/bill_id. */
    @Column(name = "identified")
    private Boolean identified;

    @Column(name = "document_type", length = 30)
    private String documentType;

    /** Documento polimórfico (fatura, guia, ...): sem FK de propósito. */
    @Column(name = "document_id")
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_kind", nullable = false, length = 20)
    private AdjustmentKind adjustmentKind = AdjustmentKind.NONE;

    @Column(name = "adjustment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal adjustmentAmount = BigDecimal.ZERO;

    @Column(name = "classified_at")
    private OffsetDateTime classifiedAt;

    /** Chave de idempotência da importação (ex.: arquivo + linha). Exigida só para BOLETO e DEBITO_AUTOMATICO. */
    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    /** Colunas Pix (V6_3), órfãs nos dois modelos antigos. */
    @Column(name = "end_to_end_id", length = 32)
    private String endToEndId;

    @Column(name = "txid", length = 35)
    private String txid;

    @Column(name = "hybrid_document_id")
    private UUID hybridDocumentId;
}
