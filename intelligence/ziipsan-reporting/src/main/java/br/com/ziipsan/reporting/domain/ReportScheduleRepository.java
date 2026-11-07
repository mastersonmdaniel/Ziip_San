package br.com.ziipsan.reporting.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, UUID> {

    List<ReportSchedule> findByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAsc(OffsetDateTime now);
}
