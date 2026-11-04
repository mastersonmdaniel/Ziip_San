package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ConsumerUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerUnitRepository extends JpaRepository<ConsumerUnit, UUID> {
    /** P1.3 (V3_12): propriedade renomeada de matricula para registrationNumber. */
    Optional<ConsumerUnit> findByTenantIdAndRegistrationNumber(UUID tenantId, String registrationNumber);

    /** Unidade ativa com a mesma inscricao (quadra + lote + sublote). */
    Optional<ConsumerUnit> findByTenantIdAndBlockIdAndLotAndSublotAndDeletedAtIsNull(
            UUID tenantId, UUID blockId, Integer lot, Integer sublot);
}
