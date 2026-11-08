package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.FiscalOperatorActionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiscalOperatorActionRepository extends JpaRepository<FiscalOperatorActionEntity, UUID> {

    Optional<FiscalOperatorActionEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<FiscalOperatorActionEntity> findByTenantIdAndFiscalDocumentIdAndActionTypeAndStatus(
            UUID tenantId, UUID fiscalDocumentId, String actionType, String status);

    List<FiscalOperatorActionEntity> findByTenantIdAndFiscalDocumentId(UUID tenantId, UUID fiscalDocumentId);
}
