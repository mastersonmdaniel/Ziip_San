package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterMovementRepository extends JpaRepository<MeterMovement, UUID> {}
