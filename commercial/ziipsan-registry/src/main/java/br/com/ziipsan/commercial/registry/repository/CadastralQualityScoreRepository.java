package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CadastralQualityScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Pontuacoes por grupo e varredura da qualidade cadastral (P3.32). */
public interface CadastralQualityScoreRepository
        extends JpaRepository<CadastralQualityScoreEntity, UUID> {

    List<CadastralQualityScoreEntity> findByTenantIdAndRunId(UUID tenantId, UUID runId);

    List<CadastralQualityScoreEntity> findByTenantIdAndGroupTypeAndGroupId(
            UUID tenantId, String groupType, UUID groupId);
}
