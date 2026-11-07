package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CycleScheduleItemEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CycleScheduleItemRepository extends JpaRepository<CycleScheduleItemEntity, UUID> {

    Optional<CycleScheduleItemEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Cronograma de um grupo numa referencia, na ordem das ocorrencias. */
    List<CycleScheduleItemEntity> findByTenantIdAndReferenceIdAndGroupIdOrderByOccurrenceNoAsc(
            UUID tenantId, UUID referenceId, UUID groupId);

    /** Todos os itens da referencia (todos os grupos), para o fechamento. */
    List<CycleScheduleItemEntity> findByTenantIdAndReferenceId(UUID tenantId, UUID referenceId);
}
