package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillingReferenceEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingReferenceRepository extends JpaRepository<BillingReferenceEntity, UUID> {

    Optional<BillingReferenceEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<BillingReferenceEntity> findByTenantIdAndReferenceMonth(UUID tenantId, LocalDate referenceMonth);
}
