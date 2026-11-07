package br.com.ziipsan.reporting.export;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Escrita direta em generated_files (V3_11) por SQL nativo: o nucleo de
 * relatorios nao depende do modulo registry (dono da entidade) - a tabela e
 * o contrato compartilhado da P1.12, com o conteudo JA mascarado.
 * Instanciada pela ReportingConfiguration (correcao R1).
 */
public class JdbcGeneratedFileDelivery implements GeneratedFileDelivery {

    private final EntityManager entityManager;

    public JdbcGeneratedFileDelivery(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public UUID enqueue(UUID tenantId, UUID userId, String resource, String fileName) {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery(
                        "INSERT INTO generated_files (id, tenant_id, user_id, resource, status, file_name) "
                                + "VALUES (:id, :tenant, :user, :resource, 'QUEUED', :name)")
                .setParameter("id", id)
                .setParameter("tenant", tenantId)
                .setParameter("user", userId)
                .setParameter("resource", resource)
                .setParameter("name", fileName)
                .executeUpdate();
        return id;
    }

    @Override
    @Transactional
    public void complete(UUID generatedFileId, long rowCount, byte[] content) {
        entityManager.createNativeQuery(
                        "UPDATE generated_files SET status = 'READY', row_count = :rows, content = :content, "
                                + "ready_at = NOW(), updated_at = NOW() WHERE id = :id")
                .setParameter("rows", rowCount)
                .setParameter("content", content)
                .setParameter("id", generatedFileId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void fail(UUID generatedFileId, String error) {
        entityManager.createNativeQuery(
                        "UPDATE generated_files SET status = 'FAILED', error = :error, updated_at = NOW() WHERE id = :id")
                .setParameter("error", error == null ? null : error.substring(0, Math.min(500, error.length())))
                .setParameter("id", generatedFileId)
                .executeUpdate();
    }
}
