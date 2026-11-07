package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CycleActivityEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CycleActivityRepository extends JpaRepository<CycleActivityEntity, UUID> {

    Optional<CycleActivityEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CycleActivityEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<CycleActivityEntity> findByTenantIdOrderBySequenceOrderAsc(UUID tenantId);
}
