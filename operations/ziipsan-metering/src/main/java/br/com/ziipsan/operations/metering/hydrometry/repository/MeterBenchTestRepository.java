package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterBenchTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterBenchTestRepository extends JpaRepository<MeterBenchTest, UUID> {}
