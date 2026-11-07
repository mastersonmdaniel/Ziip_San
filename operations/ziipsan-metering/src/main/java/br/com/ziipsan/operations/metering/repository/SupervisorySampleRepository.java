package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.SupervisorySample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupervisorySampleRepository extends JpaRepository<SupervisorySample, UUID> {

    /** A trava de aplicação da idempotência por amostra: o reenvio do agente não duplica. */
    boolean existsByTenantIdAndSampleKey(UUID tenantId, String sampleKey);

    /** Linhas de meses anteriores ao corte (a execução da retenção). */
    long deleteByTenantIdAndBucketMonthLessThan(UUID tenantId, java.time.LocalDate bucketMonth);
}
