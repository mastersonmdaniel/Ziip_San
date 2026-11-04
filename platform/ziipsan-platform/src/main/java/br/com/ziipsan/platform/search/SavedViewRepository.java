package br.com.ziipsan.platform.search;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Visões salvas do tenant. Todo acesso filtra por {@code tenantId} do contexto, além da RLS:
 * um id que existe em outro tenant devolve vazio aqui (404), nunca a visão alheia. O filtro
 * por dono vive no serviço: a listagem devolve só as visões do usuário autenticado, e a
 * leitura por id exige o dono — até a P3.30 definir o escopo por unidade organizacional.
 */
public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {

    Optional<SavedView> findByIdAndTenantId(UUID id, UUID tenantId);

    List<SavedView> findAllByTenantIdAndOwnerUserIdOrderByTitleAsc(UUID tenantId, UUID ownerUserId);

    Optional<SavedView> findByTenantIdAndOwnerUserIdAndTitle(UUID tenantId, UUID ownerUserId, String title);

    List<SavedView> findAllByTenantIdAndOwnerUserIdAndResourceAndIsDefaultTrue(UUID tenantId, UUID ownerUserId,
                                                                               String resource);
}
