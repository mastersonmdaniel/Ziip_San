package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    Optional<ConsentRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ConsentRecord> findByTenantIdAndSubjectIdAndPurposeAndStatus(UUID tenantId,
                                                                       UUID subjectId,
                                                                       String purpose,
                                                                       ConsentRecord.ConsentStatus status);

    List<ConsentRecord> findByTenantIdAndSubjectId(UUID tenantId, UUID subjectId);

    List<ConsentRecord> findByTenantIdAndStatus(UUID tenantId, ConsentRecord.ConsentStatus status);

    List<ConsentRecord> findByTenantIdAndStatusAndExpiresAtBefore(UUID tenantId,
                                                                  ConsentRecord.ConsentStatus status,
                                                                  OffsetDateTime expiresAt);
}