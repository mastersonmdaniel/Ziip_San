package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.LargeUserContractEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LargeUserContractRepository extends JpaRepository<LargeUserContractEntity, UUID> {

    List<LargeUserContractEntity> findByUnitId(UUID unitId);
}
