package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Evento de pagamento para consumo de outras trilhas (P3.1). O caso normado aqui e o
 * {@code CREDIT_RECOVERY}: pagamento de conta prescrita e recebimento valido (CC,
 * art. 882), consumido pela contabilidade/recuperacao de credito da ED-32.
 * {@code payload} e JSON e so pode ser produzido pela classe {@code Json} do core.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "payment_events")
@Getter
@Setter
public class PaymentEventEntity extends TenantAwareEntity {

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;
}
