package br.com.ziipsan.grs.mtr.repository;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E1.
import br.com.ziipsan.grs.mtr.domain.WasteTemporaryStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteTemporaryStorageRepository extends JpaRepository<WasteTemporaryStorage, UUID> {

    Optional<WasteTemporaryStorage> findByIdAndTenantId(UUID id, UUID tenantId);
}
