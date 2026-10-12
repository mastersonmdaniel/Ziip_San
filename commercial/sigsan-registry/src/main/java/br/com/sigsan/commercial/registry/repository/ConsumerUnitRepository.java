package br.com.sigsan.commercial.registry.repository;

import br.com.sigsan.commercial.registry.domain.ConsumerUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerUnitRepository extends JpaRepository<ConsumerUnit, UUID> {
    Optional<ConsumerUnit> findByTenantIdAndMatricula(UUID tenantId, String matricula);

    /** Unidade ativa com a mesma inscricao (quadra + lote + sublote). */
    Optional<ConsumerUnit> findByTenantIdAndBlockIdAndLotAndSublotAndDeletedAtIsNull(
            UUID tenantId, UUID blockId, Integer lot, Integer sublot);
}
