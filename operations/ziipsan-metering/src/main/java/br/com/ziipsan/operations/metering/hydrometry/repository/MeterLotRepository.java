package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterLot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterLotRepository extends JpaRepository<MeterLot, UUID> {}
