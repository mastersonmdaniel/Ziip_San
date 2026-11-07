package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.OptionalCategoryEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OptionalCategoryEnrollmentRepository extends JpaRepository<OptionalCategoryEnrollmentEntity, UUID> {

    List<OptionalCategoryEnrollmentEntity> findByUnitId(UUID unitId);

    List<OptionalCategoryEnrollmentEntity> findByUnitIdAndCategoryCode(UUID unitId, String categoryCode);
}
