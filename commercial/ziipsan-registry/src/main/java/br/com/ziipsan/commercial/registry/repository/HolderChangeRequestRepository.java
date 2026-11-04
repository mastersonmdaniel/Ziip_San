package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.HolderChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HolderChangeRequestRepository extends JpaRepository<HolderChangeRequest, UUID> {

    List<HolderChangeRequest> findByTenantIdAndTargetTypeAndTargetIdOrderByCreatedAtDesc(
            UUID tenantId, String targetType, UUID targetId);
}
