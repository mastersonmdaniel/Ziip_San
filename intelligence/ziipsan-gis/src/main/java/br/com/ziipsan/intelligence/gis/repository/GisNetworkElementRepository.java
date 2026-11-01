package br.com.ziipsan.intelligence.gis.repository;

import br.com.ziipsan.intelligence.gis.domain.GisNetworkElement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GisNetworkElementRepository extends JpaRepository<GisNetworkElement, UUID> {

    List<GisNetworkElement> findByDmaDistrictIdAndTenantId(UUID dmaDistrictId, UUID tenantId);
}