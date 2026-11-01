package br.com.ziipsan.intelligence.quality.repository;

import br.com.ziipsan.intelligence.quality.lab.LabQcSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LabQcSampleRepository extends JpaRepository<LabQcSample, UUID> {

    List<LabQcSample> findByMethodIdAndAnalyzedAtAfter(UUID methodId, OffsetDateTime analyzedAt);

    @Query("SELECT q FROM LabQcSample q WHERE q.method.id = :methodId AND q.tenantId = :tenantId AND q.analyzedAt BETWEEN :from AND :to")
    List<LabQcSample> findByMethodIdAndAnalyzedAtBetween(@Param("methodId") UUID methodId,
                                                        @Param("tenantId") UUID tenantId,
                                                        @Param("from") OffsetDateTime from,
                                                        @Param("to") OffsetDateTime to);
}