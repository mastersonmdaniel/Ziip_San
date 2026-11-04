package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.UnitDueDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnitDueDayRepository extends JpaRepository<UnitDueDay, UUID> {

    List<UnitDueDay> findByTenantIdAndUnitIdOrderByValidFromDesc(UUID tenantId, UUID unitId);

}
