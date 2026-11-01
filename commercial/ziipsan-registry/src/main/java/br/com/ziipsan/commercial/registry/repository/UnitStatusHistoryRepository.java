package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.UnitStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitStatusHistoryRepository extends JpaRepository<UnitStatusHistory, UUID> {
    List<UnitStatusHistory> findByTenantIdAndUnitIdOrderByChangedAtDesc(UUID tenantId, UUID unitId);
}
