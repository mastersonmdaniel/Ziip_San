package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.UnitSubcategoryActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitSubcategoryActivityRepository extends JpaRepository<UnitSubcategoryActivity, UUID> {
    List<UnitSubcategoryActivity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);
}
