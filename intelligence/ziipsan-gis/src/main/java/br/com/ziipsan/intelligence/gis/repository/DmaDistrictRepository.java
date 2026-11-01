package br.com.ziipsan.intelligence.gis.repository;

import br.com.ziipsan.intelligence.losses.domain.DmaDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side access to DMA districts for the GIS module.
 * The entity itself is owned by ziipsan-losses.
 */
@Repository
public interface DmaDistrictRepository extends JpaRepository<DmaDistrict, UUID> {

    Optional<DmaDistrict> findByIdAndTenantId(UUID id, UUID tenantId);
}