package br.com.sigsan.operations.metering.repository;

import br.com.sigsan.operations.metering.domain.Meter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterRepository extends JpaRepository<Meter, UUID> {

    Optional<Meter> findByMeterNumber(String meterNumber);
}
