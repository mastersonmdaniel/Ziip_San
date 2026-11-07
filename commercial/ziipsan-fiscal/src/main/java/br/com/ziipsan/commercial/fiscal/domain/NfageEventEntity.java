package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Evento da NFAg-e que muda o documento de fora do fluxo de emissão (V4_7):
 * {@code CANCELLATION} (evento de cancelamento — o documento vai a CANCELLED no
 * dicionário), {@code SUBSTITUTION} (o documento vai a REPLACED e
 * {@code relatedDocumentId} é o substituto) e {@code VOIDING} (inutilização de
 * número reservado — o documento vai a VOIDED). O estado em si continua gravado em
 * {@code fiscal_documents}; aqui fica o evento, com protocolo e motivo.
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_nfage_events")
@Getter
@Setter
public class NfageEventEntity extends TenantAwareEntity {

    @Column(name = "fiscal_document_id", nullable = false)
    private UUID fiscalDocumentId;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    @Column(name = "protocol", length = 100)
    private String protocol;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "related_document_id")
    private UUID relatedDocumentId;
}
