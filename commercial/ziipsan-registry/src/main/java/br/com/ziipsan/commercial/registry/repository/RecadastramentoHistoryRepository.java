package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.RecadastramentoHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Historico de aplicacoes do recadastramento (ED-16). */
public interface RecadastramentoHistoryRepository extends JpaRepository<RecadastramentoHistoryEntity, UUID> {

    List<RecadastramentoHistoryEntity> findByTenantIdAndBatchIdOrderByAppliedAtAsc(UUID tenantId, UUID batchId);

    List<RecadastramentoHistoryEntity> findByTenantIdAndTargetTypeAndTargetIdOrderByAppliedAtAsc(
            UUID tenantId, String targetType, UUID targetId);
}
