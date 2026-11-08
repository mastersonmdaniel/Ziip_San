package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CadastralQualityRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Varreduras de qualidade cadastral (P3.32), mais recente primeiro na leitura da tendencia. */
public interface CadastralQualityRunRepository extends JpaRepository<CadastralQualityRunEntity, UUID> {

    List<CadastralQualityRunEntity> findByTenantIdOrderByReferenceDateDesc(UUID tenantId);
}
