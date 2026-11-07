package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterBenchTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterBenchTestResultRepository extends JpaRepository<MeterBenchTestResult, UUID> {}
