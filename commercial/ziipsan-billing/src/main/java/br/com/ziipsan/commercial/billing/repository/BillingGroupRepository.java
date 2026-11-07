package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillingGroupEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillingGroupRepository extends JpaRepository<BillingGroupEntity, UUID> {

    Optional<BillingGroupEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<BillingGroupEntity> findByTenantIdAndCode(UUID tenantId, String code);

    List<BillingGroupEntity> findByTenantIdAndActiveTrueOrderByCodeAsc(UUID tenantId);
}
