package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterModel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterModelRepository extends JpaRepository<MeterModel, UUID> {}
