package br.com.sigsan.operations.metering.repository;

import br.com.sigsan.operations.metering.domain.ReadingAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadingAnomalyRepository extends JpaRepository<ReadingAnomaly, UUID> {

    Optional<ReadingAnomaly> findByCode(String code);

    Optional<ReadingAnomaly> findBySystemRole(ReadingAnomaly.SystemRole systemRole);
}
