package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.StationOutage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StationOutageRepository extends JpaRepository<StationOutage, UUID> {

    Optional<StationOutage> findByTenantIdAndId(UUID tenantId, UUID id);

    /** A parada ainda aberta da estação (sem {@code endedAt}): uma estação não tem duas paradas abertas. */
    Optional<StationOutage> findByTenantIdAndStationCodeAndEndedAtIsNull(UUID tenantId, String stationCode);

    List<StationOutage> findByTenantIdAndStationCodeOrderByStartedAtDesc(UUID tenantId, String stationCode);
}
