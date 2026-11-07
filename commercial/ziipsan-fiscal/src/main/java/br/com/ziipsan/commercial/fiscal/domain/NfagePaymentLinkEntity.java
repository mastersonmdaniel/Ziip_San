package br.com.ziipsan.commercial.fiscal.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Vínculo entre a NFAg-e e um pagamento (NT 2026.001): a conta paga aponta para o
 * documento que a formaliza. {@code paymentId} é o id do pagamento no módulo de
 * arrecadação — vínculo por id, sem FK entre módulos (ver V4_7).
 *
 * <p>Não usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "fiscal_nfage_payments")
@Getter
@Setter
public class NfagePaymentLinkEntity extends TenantAwareEntity {

    @Column(name = "nfage_detail_id", nullable = false)
    private UUID nfageDetailId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;
}
