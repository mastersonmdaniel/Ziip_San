package br.com.sigsan.commercial.registry.domain;

import br.com.sigsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Cliente (pessoa fisica ou juridica). O documento NUNCA e guardado em claro: ciphertext (AES-256-GCM por tenant), hash (indice cego) e mascara.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false)
    private PersonType personType;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "document_ciphertext", columnDefinition = "text")
    private String documentCiphertext;

    @Column(name = "document_hash", length = 64)
    private String documentHash;

    @Column(name = "document_masked", length = 30)
    private String documentMasked;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "due_day")
    private Short dueDay;

    @Column(name = "deleted_at")
    private java.time.OffsetDateTime deletedAt;

    @Column(name = "deleted_reason", length = 200)
    private String deletedReason;

    public enum PersonType { PF, PJ }

}
