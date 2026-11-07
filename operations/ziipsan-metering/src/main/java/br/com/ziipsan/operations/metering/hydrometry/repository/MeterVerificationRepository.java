package br.com.ziipsan.operations.metering.hydrometry.repository;

import br.com.ziipsan.operations.metering.hydrometry.domain.MeterVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MeterVerificationRepository extends JpaRepository<MeterVerification, UUID> {}
