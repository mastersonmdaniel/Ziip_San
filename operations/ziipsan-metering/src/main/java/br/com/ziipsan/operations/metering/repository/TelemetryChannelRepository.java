package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.TelemetryChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryChannelRepository extends JpaRepository<TelemetryChannel, UUID> {

    Optional<TelemetryChannel> findByTenantIdAndId(UUID tenantId, UUID id);

    /** O canal pelo código dentro do ponto (o código que o arquivo AMR traz). */
    Optional<TelemetryChannel> findByTenantIdAndMeasuringPointIdAndCode(UUID tenantId, UUID measuringPointId,
                                                                         String code);
}
