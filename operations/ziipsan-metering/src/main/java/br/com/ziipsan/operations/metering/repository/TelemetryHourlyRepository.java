package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.TelemetryHourly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TelemetryHourlyRepository extends JpaRepository<TelemetryHourly, UUID> {

    boolean existsByTenantIdAndMeasuringPointIdAndChannelIdAndHourStart(UUID tenantId, UUID measuringPointId,
                                                                        UUID channelId, OffsetDateTime hourStart);

    /** A curva horária de um ponto num intervalo, em ordem de tempo — a consulta que o índice por ponto e tempo serve. */
    List<TelemetryHourly> findByTenantIdAndMeasuringPointIdAndHourStartBetweenOrderByHourStartAsc(
            UUID tenantId, UUID measuringPointId, OffsetDateTime from, OffsetDateTime to);

    /** Linhas da curva de meses anteriores ao corte (a execução da retenção por resolução). */
    long deleteByTenantIdAndBucketMonthLessThan(UUID tenantId, java.time.LocalDate bucketMonth);
}
