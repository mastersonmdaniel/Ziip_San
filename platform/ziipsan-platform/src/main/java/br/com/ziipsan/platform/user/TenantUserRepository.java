package br.com.ziipsan.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Usuários do tenant. Todo acesso filtra por {@code tenantId} do contexto, além da RLS:
 * um id que existe em outro tenant devolve vazio aqui (404), nunca o registro alheio.
 */
public interface TenantUserRepository extends JpaRepository<TenantUser, UUID> {

    Optional<TenantUser> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<TenantUser> findByKeycloakSubAndTenantId(String keycloakSub, UUID tenantId);

    List<TenantUser> findAllByTenantId(UUID tenantId);
}
