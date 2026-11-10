package br.com.ziipsan.grs.mtr.pgrs;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E4.
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WastePgrsRepository extends JpaRepository<WastePgrs, UUID> {

    Optional<WastePgrs> findByIdAndTenantId(UUID id, UUID tenantId);
}
