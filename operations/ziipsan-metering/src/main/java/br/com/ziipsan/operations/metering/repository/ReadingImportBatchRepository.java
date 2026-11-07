package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ReadingImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadingImportBatchRepository extends JpaRepository<ReadingImportBatch, UUID> {

    /** O lote de um conteudo ja recebido: e a consulta da idempotencia de arquivo (aceite da P2.7). */
    Optional<ReadingImportBatch> findByTenantIdAndFileSha256(UUID tenantId, String fileSha256);

    Optional<ReadingImportBatch> findByTenantIdAndVendorIdAndExternalRef(UUID tenantId, UUID vendorId,
                                                                          String externalRef);
}
