package br.com.ziipsan.reporting.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportExecutionRepository extends JpaRepository<ReportExecution, UUID> {

    List<ReportExecution> findByStatusOrderByCreatedAtAsc(ReportExecution.Status status);

    /** Fila ad-hoc do worker (correcao B5): execucoes enfileiradas por
     *  exportacao acima do limite sincrono, ainda sem entrega. */
    List<ReportExecution> findByStatusAndScheduledFalseOrderByCreatedAtAsc(ReportExecution.Status status);
}
