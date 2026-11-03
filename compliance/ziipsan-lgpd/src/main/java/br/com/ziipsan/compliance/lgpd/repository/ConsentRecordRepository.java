package br.com.ziipsan.compliance.lgpd.repository;

import br.com.ziipsan.compliance.lgpd.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    Optional<ConsentRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    // P1.15: todas as leituras por filtro excluem registros excluídos
    // (deleted_at IS NULL, coluna da V2_11). A carga por id continua sem o filtro:
    // quem carrega por id (revogação) verifica deletedAt explicitamente no serviço,
    // para devolver o mesmo "não encontrado" de um id inexistente.
    Optional<ConsentRecord> findByTenantIdAndSubjectIdAndPurposeAndStatusAndDeletedAtIsNull(UUID tenantId,
                                                                                           UUID subjectId,
                                                                                           String purpose,
                                                                                           ConsentRecord.ConsentStatus status);

    List<ConsentRecord> findByTenantIdAndSubjectIdAndDeletedAtIsNull(UUID tenantId, UUID subjectId);

    List<ConsentRecord> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ConsentRecord.ConsentStatus status);

    List<ConsentRecord> findByTenantIdAndStatusAndExpiresAtBeforeAndDeletedAtIsNull(UUID tenantId,
                                                                                   ConsentRecord.ConsentStatus status,
                                                                                   OffsetDateTime expiresAt);
}
