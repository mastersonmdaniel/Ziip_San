package br.com.ziipsan.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Atribuições de perfil por usuário e tenant. */
public interface UserProfileRepository extends JpaRepository<UserProfileAssignment, UUID> {

    List<UserProfileAssignment> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);

    void deleteByUserIdAndTenantId(UUID userId, UUID tenantId);
}
