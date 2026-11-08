package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CadastralQualityFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Achados/fila de correcao da qualidade cadastral (P3.32). */
public interface CadastralQualityFindingRepository
        extends JpaRepository<CadastralQualityFindingEntity, UUID> {

    List<CadastralQualityFindingEntity> findByTenantIdAndFindingKey(UUID tenantId, String findingKey);

    List<CadastralQualityFindingEntity> findByTenantIdAndServiceOrderId(UUID tenantId, UUID serviceOrderId);

    List<CadastralQualityFindingEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
