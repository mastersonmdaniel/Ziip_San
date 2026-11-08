package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CollectedFieldEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Campos coletados do recadastramento (ED-16). */
public interface CollectedFieldRepository extends JpaRepository<CollectedFieldEntity, UUID> {

    List<CollectedFieldEntity> findByTenantIdAndBatchIdOrderByCreatedAtAsc(UUID tenantId, UUID batchId);

    Optional<CollectedFieldEntity> findByTenantIdAndBatchIdAndTaskId(UUID tenantId, UUID batchId, UUID taskId);

    List<CollectedFieldEntity> findByTenantIdAndBatchIdAndStatus(UUID tenantId, UUID batchId, String status);
}
