package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterBrand;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterBrandRepository extends JpaRepository<MeterBrand, UUID> {}
