package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CollectionErrorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Erros de coleta do recadastramento (ED-16). */
public interface CollectionErrorRepository extends JpaRepository<CollectionErrorEntity, UUID> {

    List<CollectionErrorEntity> findByTenantIdAndBatchIdOrderByOccurredAtAsc(UUID tenantId, UUID batchId);
}
