package br.com.sigsan.operations.metering.repository;

import br.com.sigsan.operations.metering.domain.MeterReading;
import br.com.sigsan.operations.metering.rules.MeasurementKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MeterReadingRepository extends JpaRepository<MeterReading, UUID> {

    /** Leituras da unidade no mes (referencia = dia 1), da mais recente para a mais antiga. */
    List<MeterReading> findByUnitIdAndKindAndReferenceOrderByReadOnDesc(UUID unitId, MeasurementKind kind,
                                                                          LocalDate reference);
}
