package br.com.ziipsan.commercial.billing.leakmeter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeakReviewRepository extends JpaRepository<LeakReviewEntity, UUID> {

    Optional<LeakReviewEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<LeakReviewEntity> findByTenantIdAndBillIdAndStatus(UUID tenantId, UUID billId, String status);

    /** Pedidos da unidade num estado -- a base da contagem do limite de 12 meses (GRANTED). */
    List<LeakReviewEntity> findByTenantIdAndUnitIdAndStatus(UUID tenantId, UUID unitId, String status);
}
