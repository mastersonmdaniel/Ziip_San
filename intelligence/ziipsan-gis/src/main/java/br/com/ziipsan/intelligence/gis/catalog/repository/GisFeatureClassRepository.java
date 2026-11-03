package br.com.ziipsan.intelligence.gis.catalog.repository;

import br.com.ziipsan.intelligence.gis.catalog.domain.GisFeatureClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GisFeatureClassRepository extends JpaRepository<GisFeatureClass, UUID> {

    List<GisFeatureClass> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<GisFeatureClass> findByIdAndTenantId(UUID id, UUID tenantId);
}
