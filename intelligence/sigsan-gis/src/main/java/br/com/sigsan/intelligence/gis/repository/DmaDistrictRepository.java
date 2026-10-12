package br.com.sigsan.intelligence.gis.repository;

import br.com.sigsan.intelligence.losses.domain.DmaDistrict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-side access to DMA districts for the GIS module.
 * The entity itself is owned by sigsan-losses.
 */
@Repository
public interface DmaDistrictRepository extends JpaRepository<DmaDistrict, UUID> {

    Optional<DmaDistrict> findByIdAndTenantId(UUID id, UUID tenantId);
}