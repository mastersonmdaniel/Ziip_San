package br.com.ziipsan.intelligence.gis.catalog.repository;

import br.com.ziipsan.intelligence.gis.catalog.domain.GisCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GisCatalogItemRepository extends JpaRepository<GisCatalogItem, UUID> {

    List<GisCatalogItem> findByTenantIdOrderByFeatureTypeIdAsc(UUID tenantId);

    List<GisCatalogItem> findByTenantIdAndFeatureTypeIdOrderByDnMmAsc(UUID tenantId, UUID featureTypeId);

    Optional<GisCatalogItem> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Duplicata exata (comparação null-safe): o tenant não cria o mesmo item duas vezes. */
    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END FROM GisCatalogItem i
            WHERE i.tenantId = :tenantId AND i.featureTypeId = :featureTypeId
              AND i.materialId IS NOT DISTINCT FROM :materialId
              AND i.dnMm IS NOT DISTINCT FROM :dnMm
              AND i.dn2Mm IS NOT DISTINCT FROM :dn2Mm
              AND i.angleDeg IS NOT DISTINCT FROM :angleDeg
              AND i.pressureClass IS NOT DISTINCT FROM :pressureClass
              AND i.jointType IS NOT DISTINCT FROM :jointType
              AND i.sectionShape IS NOT DISTINCT FROM :sectionShape
              AND i.geom1 IS NOT DISTINCT FROM :geom1
              AND i.geom2 IS NOT DISTINCT FROM :geom2
              AND i.approvalStatus IS DISTINCT FROM 'REJECTED'
            """)
    boolean existsDuplicate(UUID tenantId, UUID featureTypeId, UUID materialId, Integer dnMm,
                            Integer dn2Mm, BigDecimal angleDeg, String pressureClass, String jointType,
                            String sectionShape, BigDecimal geom1, BigDecimal geom2);

    long countByTenantIdAndOriginAndReferenceStatus(UUID tenantId, String origin, String referenceStatus);

    long countByTenantIdAndOriginAndApprovalStatus(UUID tenantId, String origin, String approvalStatus);

    long countByTenantIdAndOrigin(UUID tenantId, String origin);

    long countByTenantIdAndOriginAndFactoryRetiredOnIsNotNull(UUID tenantId, String origin);
}
