package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.UnitCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UnitCustomerRepository extends JpaRepository<UnitCustomer, UUID> {
    /** Vinculos vigentes (sem data de fim) da unidade. */
    List<UnitCustomer> findByTenantIdAndUnitIdAndEndDateIsNull(UUID tenantId, UUID unitId);

    /** Historico completo da unidade, do mais recente ao mais antigo. */
    List<UnitCustomer> findByTenantIdAndUnitIdOrderByStartDateDesc(UUID tenantId, UUID unitId);
}
