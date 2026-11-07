package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillCancelReasonEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillCancelReasonRepository extends JpaRepository<BillCancelReasonEntity, UUID> {

    Optional<BillCancelReasonEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
