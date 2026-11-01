package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Debito a cobrar: valor devido que entra na conta em parcelas. {@code generatesBill} nao tem padrao: e decisao do tipo de debito.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "debit_receivables")
@Getter
@Setter
public class DebitReceivableEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "type_code", nullable = false, length = 40)
    private String typeCode;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "installments", nullable = false)
    private int installments;

    @Column(name = "installments_done", nullable = false)
    private int installmentsDone;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "NORMAL";

    @Column(name = "under_review", nullable = false)
    private boolean underReview;

    @Column(name = "generates_bill", nullable = false)
    private Boolean generatesBill;
}
