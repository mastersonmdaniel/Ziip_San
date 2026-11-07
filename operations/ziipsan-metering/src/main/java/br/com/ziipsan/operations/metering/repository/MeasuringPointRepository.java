package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.MeasuringPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeasuringPointRepository extends JpaRepository<MeasuringPoint, UUID> {

    Optional<MeasuringPoint> findByTenantIdAndId(UUID tenantId, UUID id);

    /** O ponto pelo código estável que os arquivos AMR e os dispositivos usam. */
    Optional<MeasuringPoint> findByTenantIdAndCode(UUID tenantId, String code);
}
