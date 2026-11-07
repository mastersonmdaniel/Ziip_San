package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Instrucao de cobranca recorrente do Pix Automatico (P4.5), um ciclo por
 * linha, vinculada a autorizacao e a conta do ciclo. O
 * {@code chargeIdentifier} e unico por tenant: e a chave da conciliacao.
 * Tabela {@code pix_auto_debit_instructions} - migration pendente de numero
 * da sequencia global na integracao (ver a entidade da autorizacao).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "pix_auto_debit_instructions")
@Getter
@Setter
public class PixAutoDebitInstructionEntity extends TenantAwareEntity {

    @Column(name = "authorization_id", nullable = false)
    private UUID authorizationId;

    @Column(name = "charge_identifier", nullable = false, length = 80)
    private String chargeIdentifier;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "collection_month", nullable = false)
    private LocalDate collectionMonth;

    @Column(name = "principal", nullable = false, precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(name = "fine", nullable = false, precision = 14, scale = 2)
    private BigDecimal fine = BigDecimal.ZERO;

    @Column(name = "interest", nullable = false, precision = 14, scale = 2)
    private BigDecimal interest = BigDecimal.ZERO;

    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;

    /** AGENDADA, NOTIFICADA, ENVIADA, LIQUIDADA, FALHA_AGUARDANDO_RETENTATIVA, FALHA_DEFINITIVA, CANCELADA_PELO_PAGADOR, CANCELADA_PELO_RECEBEDOR. */
    @Column(name = "status", nullable = false, length = 32)
    private String status = "AGENDADA";

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "first_attempt_date")
    private LocalDate firstAttemptDate;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "end_to_end_id", length = 32)
    private String endToEndId;

    @Column(name = "settled_at")
    private Instant settledAt;
}
