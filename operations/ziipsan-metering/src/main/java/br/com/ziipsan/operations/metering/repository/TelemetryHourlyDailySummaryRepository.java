package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.TelemetryHourlyDailySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryHourlyDailySummaryRepository extends JpaRepository<TelemetryHourlyDailySummary, UUID> {

    Optional<TelemetryHourlyDailySummary> findByTenantIdAndMeasuringPointIdAndChannelIdAndDay(
            UUID tenantId, UUID measuringPointId, UUID channelId, LocalDate day);
}
