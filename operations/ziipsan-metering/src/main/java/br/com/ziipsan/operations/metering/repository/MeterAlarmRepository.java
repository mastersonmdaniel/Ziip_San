package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.MeterAlarm;
import br.com.ziipsan.operations.metering.telemetry.MeterAlarmKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MeterAlarmRepository extends JpaRepository<MeterAlarm, UUID> {

    /** Um alarme aberto do mesmo tipo no ponto não é duplicado pela próxima detecção. */
    boolean existsByTenantIdAndMeasuringPointIdAndKindAndStatus(UUID tenantId, UUID measuringPointId,
                                                                MeterAlarmKind kind, MeterAlarm.Status status);

    List<MeterAlarm> findByTenantIdAndUnitIdOrderByRaisedAtDesc(UUID tenantId, UUID unitId);
}
