package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ConsumptionAnomaly;
import br.com.ziipsan.operations.metering.rules.ConsumptionAnomalyKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumptionAnomalyRepository extends JpaRepository<ConsumptionAnomaly, UUID> {

    Optional<ConsumptionAnomaly> findByKind(ConsumptionAnomalyKind kind);
}
