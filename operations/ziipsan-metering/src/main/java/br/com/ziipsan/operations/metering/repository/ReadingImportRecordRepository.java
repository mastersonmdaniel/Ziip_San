package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ReadingImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadingImportRecordRepository extends JpaRepository<ReadingImportRecord, UUID> {

    /** O registro ja importado de uma chave: e a consulta da idempotencia de registro (aceite da P2.7). */
    Optional<ReadingImportRecord> findByTenantIdAndRecordKeyAndStatus(UUID tenantId, String recordKey,
                                                                       ReadingImportRecord.Status status);
}
