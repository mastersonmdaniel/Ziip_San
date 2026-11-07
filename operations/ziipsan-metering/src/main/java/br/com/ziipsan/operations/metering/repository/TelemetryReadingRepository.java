package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.TelemetryReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {

    Optional<TelemetryReading> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * A trava de aplicação da idempotência, no mesmo escopo do índice único
     * do SQL (tenant, mês, chave) e do padrão da P2.3: a mesma referência
     * externa no mesmo mês não entra duas vezes; em outro mês, é outra leitura.
     */
    boolean existsByTenantIdAndReferenceMonthAndIdempotencyKey(UUID tenantId, java.time.LocalDate referenceMonth,
                                                              String idempotencyKey);

    /** Linhas da série bruta de meses anteriores ao corte (a execução da retenção). */
    long deleteByTenantIdAndReferenceMonthLessThan(UUID tenantId, java.time.LocalDate referenceMonth);
}
