package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.NfageEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfageEventRepository extends JpaRepository<NfageEventEntity, UUID> {

    List<NfageEventEntity> findByTenantIdAndFiscalDocumentId(UUID tenantId, UUID fiscalDocumentId);
}
