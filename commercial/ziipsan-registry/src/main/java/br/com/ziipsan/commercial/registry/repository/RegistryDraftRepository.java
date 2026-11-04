package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.RegistryDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RegistryDraftRepository extends JpaRepository<RegistryDraft, UUID> {

    List<RegistryDraft> findByTenantIdAndResourceAndTargetId(UUID tenantId, String resource, UUID targetId);

}
