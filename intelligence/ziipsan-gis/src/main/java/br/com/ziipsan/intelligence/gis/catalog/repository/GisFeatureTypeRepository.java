package br.com.ziipsan.intelligence.gis.catalog.repository;

import br.com.ziipsan.intelligence.gis.catalog.domain.GisFeatureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GisFeatureTypeRepository extends JpaRepository<GisFeatureType, UUID> {

    List<GisFeatureType> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<GisFeatureType> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndClassIdAndCode(UUID tenantId, UUID classId, String code);

    long countByTenantIdAndOrigin(UUID tenantId, String origin);

    long countByTenantIdAndOriginAndApprovalStatus(UUID tenantId, String origin, String approvalStatus);

    long countByTenantIdAndOriginAndFactoryRetiredOnIsNotNull(UUID tenantId, String origin);
}
