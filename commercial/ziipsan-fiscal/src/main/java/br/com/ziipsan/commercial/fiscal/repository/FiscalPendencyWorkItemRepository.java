package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.FiscalPendencyWorkItemEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FiscalPendencyWorkItemRepository extends JpaRepository<FiscalPendencyWorkItemEntity, UUID> {

    List<FiscalPendencyWorkItemEntity> findByTenantIdAndFiscalDocumentIdAndClosedAtIsNull(
            UUID tenantId, UUID fiscalDocumentId);
}
