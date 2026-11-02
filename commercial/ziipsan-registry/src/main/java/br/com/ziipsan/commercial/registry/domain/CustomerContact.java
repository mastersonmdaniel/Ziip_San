package br.com.ziipsan.commercial.registry.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
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

    /** P0.16 opcao 1: ramal (telefone) e nome do contato. label continua sendo o tipo. */
    @Column(name = "extension", length = 10)
    private String extension;

    /** Nome de terceiro: so se necessario (dica do formulario); redigido na auditoria. */
    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "primary_contact", nullable = false)
    private boolean primaryContact;

    @Column(name = "consent_id")
    private UUID consentId;

    public enum Kind { EMAIL, PHONE }

}
