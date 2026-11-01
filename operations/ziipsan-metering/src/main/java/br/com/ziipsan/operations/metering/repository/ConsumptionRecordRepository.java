package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ConsumptionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumptionRecordRepository extends JpaRepository<ConsumptionRecord, UUID> {

    /** Idempotencia: a unica linha da unidade, servico e referencia (dia 1), se existir. */
    Optional<ConsumptionRecord> findByUnitIdAndServiceKindAndReference(UUID unitId,
                                                                       ConsumptionRecord.ServiceKind serviceKind,
                                                                       LocalDate reference);

    /** Historico de uma unidade e servico, do mais recente para o mais antigo (entrada da media). */
    List<ConsumptionRecord> findByUnitIdAndServiceKindAndReferenceBeforeOrderByReferenceDesc(
            UUID unitId, ConsumptionRecord.ServiceKind serviceKind, LocalDate before);
}
