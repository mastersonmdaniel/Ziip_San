package br.com.sigsan.compliance.lgpd.repository;

import br.com.sigsan.compliance.lgpd.domain.DpiaRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DpiaRecordRepository extends JpaRepository<DpiaRecord, UUID> {

    Optional<DpiaRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DpiaRecord> findByTenantIdAndStatusAndReviewDateBefore(UUID tenantId,
                                                               DpiaRecord.DpiaStatus status,
                                                               OffsetDateTime reviewDate);
}