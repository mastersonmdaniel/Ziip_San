package br.com.ziipsan.platform.templates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Modelos do tenant. Todo acesso filtra por {@code tenantId} do contexto,
 * além da RLS: um id de outro tenant devolve vazio (404), nunca o modelo
 * alheio — o mesmo padrão de {@code SavedViewRepository}.
 */
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID> {

    Optional<DocumentTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DocumentTemplate> findByTenantIdAndCode(UUID tenantId, String code);

    List<DocumentTemplate> findAllByTenantIdOrderByCodeAsc(UUID tenantId);
}
