package br.com.ziipsan.commercial.billing.domain;

import br.com.ziipsan.core.domain.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Inscricao de tarifa social (V4_2). So indices cegos: {@code familyCodeHash} e o hash
 * do codigo familiar, nunca o codigo em claro; nao ha coluna de CPF, NIS, renda nem de
 * adimplencia nesta entidade, por desenho (Lei 14.898/2024).
 *
 * <p>{@code serviceOrderId} é NOT NULL desde a P1.3 (V4_13) e tem FK para
 * service_orders desde a V9_1 (fk_social_enroll_order) — a correção R1
 * ("com a FK") já estava satisfeita pela V9_1; a P1.3 entregou o NOT NULL
 * que faltava (a V3_6 reservada no plano já existia, P0.16, e a V9_2 do
 * tenant é da P3.4). Antes da P1.3 ele era NULL admitido; o plano reservava
 * a obrigatoriedade para a P1.3, quando todo registro direto passa a rotear
 * por OS interna. {@code status} guarda ACTIVE, LOSS_NOTICE, GRACE ou ENDED.
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "social_tariff_enrollments")
@Getter
@Setter
public class SocialTariffEnrollmentEntity extends TenantAwareEntity {

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "holder_customer_id", nullable = false)
    private UUID holderCustomerId;

    @Column(name = "family_code_hash", nullable = false, length = 64)
    private String familyCodeHash;

    @Column(name = "eligibility_basis", nullable = false, length = 20)
    private String eligibilityBasis;

    @Column(name = "enrollment_source", nullable = false, length = 20)
    private String enrollmentSource;

    @Column(name = "cadunico_updated_on")
    private LocalDate cadunicoUpdatedOn;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "loss_notice_started_on")
    private LocalDate lossNoticeStartedOn;

    @Column(name = "grace_started_on")
    private LocalDate graceStartedOn;

    @Column(name = "end_reason_code", length = 40)
    private String endReasonCode;

    @Column(name = "legal_basis", nullable = false, length = 200)
    private String legalBasis;

    @Column(name = "social_economies", nullable = false)
    private int socialEconomies;

    /** P1.3 (V4_13): NOT NULL no banco; FK desde a V9_1 (fk_social_enroll_order). */
    @Column(name = "service_order_id", nullable = false)
    private UUID serviceOrderId;
}
