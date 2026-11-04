package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CustomerRepresentation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerRepresentationRepository extends JpaRepository<CustomerRepresentation, UUID> {

    List<CustomerRepresentation> findByTenantIdAndRepresentedIdOrderByValidFromDesc(UUID tenantId, UUID representedId);

    List<CustomerRepresentation> findByTenantIdAndRepresentativeIdOrderByValidFromDesc(UUID tenantId, UUID representativeId);

    List<CustomerRepresentation> findByTenantIdAndUnitIdAndStatus(UUID tenantId, UUID unitId, String status);
}
