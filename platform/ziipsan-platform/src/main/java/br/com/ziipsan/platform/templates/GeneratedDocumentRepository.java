package br.com.ziipsan.platform.templates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Documentos gerados do tenant, em ordem de geração (o mais recente primeiro). */
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, UUID> {

    Optional<GeneratedDocument> findByIdAndTenantId(UUID id, UUID tenantId);

    List<GeneratedDocument> findAllByTenantIdAndTemplateIdOrderByGeneratedAtDesc(UUID tenantId, UUID templateId);
}
