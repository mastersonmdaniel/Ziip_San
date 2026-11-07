package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ReadingTask;
import br.com.ziipsan.operations.metering.rules.MeasurementKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadingTaskRepository extends JpaRepository<ReadingTask, UUID> {

    /** Tarefas da rota na referencia, na ordem de leitura (a ordem do arquivo de ida). */
    List<ReadingTask> findByTenantIdAndVendorIdAndRouteCodeAndReferenceMonthOrderBySequenceNoAsc(
            UUID tenantId, UUID vendorId, String routeCode, LocalDate referenceMonth);

    /** A tarefa de uma unidade e tipo numa rota e referencia: o destino de um registro da volta. */
    Optional<ReadingTask> findByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
            UUID tenantId, LocalDate referenceMonth, String routeCode, UUID unitId, MeasurementKind kind);

    boolean existsByTenantIdAndReferenceMonthAndRouteCodeAndUnitIdAndKind(
            UUID tenantId, LocalDate referenceMonth, String routeCode, UUID unitId, MeasurementKind kind);
}
