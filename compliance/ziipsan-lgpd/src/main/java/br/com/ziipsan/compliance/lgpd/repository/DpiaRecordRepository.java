package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.DpiaRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DpiaRecordRepository extends JpaRepository<DpiaRecord, UUID> {

    Optional<DpiaRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    // P1.15: registros excluídos (V2_11) não aparecem nas leituras.
    List<DpiaRecord> findByTenantIdAndStatusAndReviewDateBeforeAndDeletedAtIsNull(UUID tenantId,
                                                                                  DpiaRecord.DpiaStatus status,
                                                                                  OffsetDateTime reviewDate);
}
