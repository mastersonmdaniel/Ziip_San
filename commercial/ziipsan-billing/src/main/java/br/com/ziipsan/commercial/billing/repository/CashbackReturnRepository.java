package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CashbackReturnEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashbackReturnRepository extends JpaRepository<CashbackReturnEntity, UUID> {

    Optional<CashbackReturnEntity> findByTenantIdAndDomicileKeyHashAndAssessmentMonth(
            UUID tenantId, String domicileKeyHash, LocalDate assessmentMonth);

    List<CashbackReturnEntity> findByTenantIdAndBillReferenceMonth(UUID tenantId, LocalDate billReferenceMonth);
}
