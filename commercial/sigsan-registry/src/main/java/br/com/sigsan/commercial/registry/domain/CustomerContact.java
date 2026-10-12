package br.com.sigsan.commercial.registry.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * E-mail ou telefone do cliente. consentId liga ao consentimento LGPD quando essa for a base legal.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "customer_contacts")
@Getter
@Setter
public class CustomerContact extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "label", length = 50)
    private String label;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "consent_id")
    private UUID consentId;

    public enum Kind { EMAIL, PHONE }

}
