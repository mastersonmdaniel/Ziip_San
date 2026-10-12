package br.com.sigsan.intelligence.quality.repository;

import br.com.sigsan.intelligence.quality.domain.QualityAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QualityAnalysisResultRepository extends JpaRepository<QualityAnalysisResult, UUID> {

    Optional<QualityAnalysisResult> findBySampleIdAndParameterId(UUID sampleId, UUID parameterId);

    List<QualityAnalysisResult> findBySampleIdAndTenantId(UUID sampleId, UUID tenantId);
}