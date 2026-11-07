package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.NfageDetailEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfageDetailRepository extends JpaRepository<NfageDetailEntity, UUID> {

    Optional<NfageDetailEntity> findByTenantIdAndFiscalDocumentId(UUID tenantId, UUID fiscalDocumentId);
}
