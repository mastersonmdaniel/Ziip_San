package br.com.ziipsan.intelligence.quality.repository;

import br.com.ziipsan.intelligence.quality.domain.QualityParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityParameterRepository extends JpaRepository<QualityParameter, UUID> {

    Optional<QualityParameter> findByIdAndTenantId(UUID id, UUID tenantId);
}