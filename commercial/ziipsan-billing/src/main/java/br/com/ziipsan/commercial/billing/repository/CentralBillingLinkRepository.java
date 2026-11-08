package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CentralBillingLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CentralBillingLinkRepository extends JpaRepository<CentralBillingLinkEntity, UUID> {

    List<CentralBillingLinkEntity> findByCentralizerCustomerId(UUID centralizerCustomerId);

    List<CentralBillingLinkEntity> findByUnitId(UUID unitId);
}
