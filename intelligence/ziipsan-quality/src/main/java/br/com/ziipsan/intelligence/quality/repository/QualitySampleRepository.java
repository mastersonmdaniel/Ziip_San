package br.com.ziipsan.intelligence.quality.repository;

import br.com.ziipsan.intelligence.quality.domain.QualitySample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualitySampleRepository extends JpaRepository<QualitySample, UUID> {

    Optional<QualitySample> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<QualitySample> findBySamplingPointIdAndStatus(UUID samplingPointId,
                                                          QualitySample.SampleStatus status);
}