package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.RetentionSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RetentionScheduleRepository extends JpaRepository<RetentionSchedule, UUID> {

    List<RetentionSchedule> findByTenantIdAndActiveTrue(UUID tenantId);

    List<RetentionSchedule> findByTenantIdAndActiveTrueAndNextRunAtBefore(UUID tenantId, OffsetDateTime nextRunAt);
}