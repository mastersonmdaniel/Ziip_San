package br.com.sigsan.intelligence.losses.repository;

import br.com.sigsan.intelligence.losses.domain.LeakDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeakDetectionRepository extends JpaRepository<LeakDetection, UUID> {

    Optional<LeakDetection> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("SELECT l FROM LeakDetection l WHERE l.dmaDistrict.id = :dmaId AND l.tenantId = :tenantId AND l.status = :status")
    List<LeakDetection> findByDmaDistrictIdAndStatus(@Param("dmaId") UUID dmaId,
                                                   @Param("tenantId") UUID tenantId,
                                                   @Param("status") LeakDetection.LeakStatus status);

    List<LeakDetection> findByDmaDistrictIdAndTenantId(UUID dmaDistrictId, UUID tenantId);

    List<LeakDetection> findByTenantIdAndPriorityOrderByDetectedAtDesc(UUID tenantId, String priority);

    long countByTenantId(UUID tenantId);
}