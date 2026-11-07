package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Motivo de revisao do catalogo {@code bill_review_reasons} (P2.20, DAD-16).
 * Substitui o texto livre de {@code bills.review_reason}. O catalogo comeca
 * vazio: motivos sao politica do prestador, nenhum e semeado.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados.
 */
@Entity
@Table(name = "bill_review_reasons")
@Getter
@Setter
public class BillReviewReasonEntity extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
