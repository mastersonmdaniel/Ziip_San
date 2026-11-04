package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CadastralChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CadastralChangeRequestRepository extends JpaRepository<CadastralChangeRequest, UUID> {

    List<CadastralChangeRequest> findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtAsc(
            UUID tenantId, String targetType, UUID targetId);

}
