package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.NfseAvulsaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NfseAvulsaRepository extends JpaRepository<NfseAvulsaEntity, UUID> {

    List<NfseAvulsaEntity> findByTenantIdAndServiceOrderId(UUID tenantId, UUID serviceOrderId);
}
