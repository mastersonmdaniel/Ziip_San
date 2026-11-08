package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.SpecialSituationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SpecialSituationRepository extends JpaRepository<SpecialSituationEntity, UUID> {

    List<SpecialSituationEntity> findByUnitId(UUID unitId);

    List<SpecialSituationEntity> findByBatchId(UUID batchId);
}
