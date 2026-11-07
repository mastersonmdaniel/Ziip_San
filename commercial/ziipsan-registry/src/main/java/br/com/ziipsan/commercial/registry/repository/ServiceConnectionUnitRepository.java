package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ServiceConnectionUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceConnectionUnitRepository extends JpaRepository<ServiceConnectionUnit, UUID> {

    List<ServiceConnectionUnit> findByTenantIdAndConnectionIdOrderByUnitId(UUID tenantId, UUID connectionId);

    List<ServiceConnectionUnit> findByTenantIdAndUnitIdOrderByConnectionId(UUID tenantId, UUID unitId);

    Optional<ServiceConnectionUnit> findByTenantIdAndConnectionIdAndUnitId(
            UUID tenantId, UUID connectionId, UUID unitId);
}
