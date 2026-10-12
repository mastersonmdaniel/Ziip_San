package br.com.sigsan.intelligence.quality.repository;

import br.com.sigsan.intelligence.quality.lab.LabInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LabInstrumentRepository extends JpaRepository<LabInstrument, UUID> {

    Optional<LabInstrument> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LabInstrument> findByTenantIdAndCalibrationDueBeforeAndStatus(UUID tenantId,
                                                                    OffsetDateTime calibrationDue,
                                                                    String status);
}