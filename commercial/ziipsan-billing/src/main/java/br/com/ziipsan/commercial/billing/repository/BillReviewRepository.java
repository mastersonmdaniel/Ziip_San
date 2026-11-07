package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillReviewRepository extends JpaRepository<BillReviewEntity, UUID> {

    Optional<BillReviewEntity> findByTenantIdAndBillIdAndStatus(UUID tenantId, UUID billId, String status);
}
