package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.TelemetryDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryDeviceRepository extends JpaRepository<TelemetryDevice, UUID> {

    Optional<TelemetryDevice> findByTenantIdAndSerialNumber(UUID tenantId, String serialNumber);
}
