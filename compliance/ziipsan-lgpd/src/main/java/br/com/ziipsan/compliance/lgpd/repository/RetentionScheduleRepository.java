package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.RetentionSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionScheduleRepository extends JpaRepository<RetentionSchedule, UUID> {

    // P1.15: registros excluídos (V2_11) não aparecem nas leituras.
    List<RetentionSchedule> findByTenantIdAndActiveTrueAndDeletedAtIsNull(UUID tenantId);

    List<RetentionSchedule> findByTenantIdAndActiveTrueAndNextRunAtBeforeAndDeletedAtIsNull(UUID tenantId, OffsetDateTime nextRunAt);
}
