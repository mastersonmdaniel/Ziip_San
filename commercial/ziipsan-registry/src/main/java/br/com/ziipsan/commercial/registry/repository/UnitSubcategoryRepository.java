package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.UnitSubcategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitSubcategoryRepository extends JpaRepository<UnitSubcategory, UUID> {
    List<UnitSubcategory> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);
}
