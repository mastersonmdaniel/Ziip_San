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
 * Registro de pessoa da lista oficial importada (V4_2). So indices cegos
 * ({@code familyCodeHash}, {@code personDocumentHash}); nao ha coluna de CPF, NIS ou
 * renda. {@code matchStatus} guarda PENDING, MATCHED ou UNMATCHED; UNMATCHED e a lista
 * de busca ativa ({@code social.active_search}).
 *
 * <p>Nao usar {@code @Builder}: o Lombok descarta os campos herdados de {@code BaseEntity}.
 */
@Entity
@Table(name = "social_base_records")
@Getter
@Setter
public class SocialBaseRecordEntity extends TenantAwareEntity {

    @Column(name = "import_id", nullable = false)
    private UUID importId;

    @Column(name = "family_code_hash", nullable = false, length = 64)
    private String familyCodeHash;

    @Column(name = "person_document_hash", nullable = false, length = 64)
    private String personDocumentHash;

    @Column(name = "person_role", nullable = false, length = 10)
    private String personRole;

    @Column(name = "eligibility_basis", nullable = false, length = 20)
    private String eligibilityBasis;

    @Column(name = "cadunico_updated_on")
    private LocalDate cadunicoUpdatedOn;

    @Column(name = "matched_unit_id")
    private UUID matchedUnitId;

    @Column(name = "matched_enrollment_id")
    private UUID matchedEnrollmentId;

    @Column(name = "match_status", nullable = false, length = 20)
    private String matchStatus = "PENDING";
}
