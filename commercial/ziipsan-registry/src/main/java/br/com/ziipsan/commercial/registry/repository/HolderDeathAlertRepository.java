package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.HolderDeathAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HolderDeathAlertRepository extends JpaRepository<HolderDeathAlert, UUID> {

    List<HolderDeathAlert> findByTenantIdAndStatusOrderByDetectedAtDesc(UUID tenantId, String status);

    List<HolderDeathAlert> findByTenantIdAndUnitIdOrderByDetectedAtDesc(UUID tenantId, UUID unitId);

    List<HolderDeathAlert> findByTenantIdAndCustomerIdOrderByDetectedAtDesc(UUID tenantId, UUID customerId);
}
