package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectionStatusRepository extends JpaRepository<ConnectionStatus, UUID> {
    List<ConnectionStatus> findByTenantIdAndKindOrderByCode(UUID tenantId, ConnectionStatus.Kind kind);
}
