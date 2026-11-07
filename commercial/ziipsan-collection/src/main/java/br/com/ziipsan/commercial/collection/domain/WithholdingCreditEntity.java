package br.com.ziipsan.commercial.collection.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Credito do tenant nascido de retencao na fonte (P3.1, IN RFB 1.234/2012): quando o
 * pagador de natureza publica retem tributos na baixa, o valor retido nao e receita
 * perdida - e credito do tenant a compensar/recuperar, registrado aqui ligado ao
 * pagamento de origem. O recurso so existe com o parametro de retencao ligado
 * (apos o parecer juridico PD-10); desligado, nenhum registro nasce.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "withholding_credits")
@Getter
@Setter
public class WithholdingCreditEntity extends TenantAwareEntity {

    @Column(name = "source_payment_id", nullable = false)
    private UUID sourcePaymentId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** Origem normativa da retencao. Hoje: IN_RFB_1234_2012. */
    @Column(name = "reason", nullable = false, length = 30)
    private String reason = "IN_RFB_1234_2012";

    /** REGISTRADO ou COMPENSADO. */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "REGISTRADO";
}
