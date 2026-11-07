package br.com.ziipsan.reporting.export;

import br.com.ziipsan.core.audit.PersonalDataAccessLog;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Gravacao na tabela real do core (V1_6): access_type EXPORT, entity_type
 * REPORT, proposito com recurso/formato/linhas - nunca o documento em claro
 * (o log guarda no maximo o indice cego, e exportacao nao busca documento).
 */
public class PersonalDataAccessExportAuditRecorder implements ExportAuditRecorder {

    private final EntityManager entityManager;

    public PersonalDataAccessExportAuditRecorder(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void recordExport(UUID tenantId, UUID userId, String reportCode, String result, String purpose) {
        PersonalDataAccessLog entry = new PersonalDataAccessLog();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setAccessedAt(Instant.now());
        entry.setAccessType(PersonalDataAccessLog.AccessType.EXPORT.name());
        entry.setEntityType("REPORT:" + reportCode);
        entry.setPurpose(purpose == null ? null : purpose.substring(0, Math.min(500, purpose.length())));
        entry.setResult(result);
        entityManager.persist(entry);
    }
}
