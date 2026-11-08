package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CobillContractEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CobillContractRepository extends JpaRepository<CobillContractEntity, UUID> {

    List<CobillContractEntity> findByServiceKind(String serviceKind);

    List<CobillContractEntity> findByProviderId(UUID providerId);
}
