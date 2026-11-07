package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.NfagePaymentLinkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfagePaymentLinkRepository extends JpaRepository<NfagePaymentLinkEntity, UUID> {

    List<NfagePaymentLinkEntity> findByTenantIdAndNfageDetailId(UUID tenantId, UUID nfageDetailId);

    List<NfagePaymentLinkEntity> findByTenantIdAndPaymentId(UUID tenantId, UUID paymentId);
}
