package br.com.ziipsan.reporting.export;

import br.com.ziipsan.core.audit.PersonalDataAccessLog;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Gravacao na tabela real do core (V1_6): access_type EXPORT, entity_type
 * REPORT, proposito com recurso/formato/linhas - nunca o documento em claro
 * (o log guarda no maximo o indice cego, e exportacao nao busca documento).
 *
 * <p>Correcao RG-Lote3 (08/11/2026): o carimbo {@code accessed_at} do log de
 * auditoria vem do {@link Clock} injetado, no padrao do projeto (construtor
 * de conveniencia com {@code Clock.systemUTC()}, como na P2.31; testes usam
 * {@code Clock.fixed}). Antes era {@code Instant.now()} direto, dependente
 * do relogio/TZ do processo. O construtor de um argumento continua existindo
 * e a fiacao da {@code ReportingConfiguration} nao muda.
 */
public class PersonalDataAccessExportAuditRecorder implements ExportAuditRecorder {

    private final EntityManager entityManager;
    private final Clock clock;

    public PersonalDataAccessExportAuditRecorder(EntityManager entityManager) {
        this(entityManager, Clock.systemUTC());
    }

    public PersonalDataAccessExportAuditRecorder(EntityManager entityManager, Clock clock) {
        this.entityManager = entityManager;
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
    }

    @Override
    @Transactional
    public void recordExport(UUID tenantId, UUID userId, String reportCode, String result, String purpose) {
        PersonalDataAccessLog entry = new PersonalDataAccessLog();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(tenantId);
        entry.setUserId(userId);
        entry.setAccessedAt(clock.instant());
        entry.setAccessType(PersonalDataAccessLog.AccessType.EXPORT.name());
        entry.setEntityType("REPORT:" + reportCode);
        entry.setPurpose(purpose == null ? null : purpose.substring(0, Math.min(500, purpose.length())));
        entry.setResult(result);
        entityManager.persist(entry);
    }
}
