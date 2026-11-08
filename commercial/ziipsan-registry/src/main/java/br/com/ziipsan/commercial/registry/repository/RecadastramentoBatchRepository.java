package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.RecadastramentoBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Lotes de recadastramento (ED-16). */
public interface RecadastramentoBatchRepository extends JpaRepository<RecadastramentoBatchEntity, UUID> {

    Optional<RecadastramentoBatchEntity> findByTenantIdAndServiceOrderId(UUID tenantId, UUID serviceOrderId);
}
