package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CashbackListEntryEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashbackListEntryRepository extends JpaRepository<CashbackListEntryEntity, UUID> {

    Optional<CashbackListEntryEntity> findByTenantIdAndDomicileKeyHashAndAssessmentMonth(
            UUID tenantId, String domicileKeyHash, LocalDate assessmentMonth);
}
