package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatus;
import br.com.ziipsan.commercial.registry.domain.ServiceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceConnectionRepository extends JpaRepository<ServiceConnection, UUID> {

    Optional<ServiceConnection> findByTenantIdAndPropertyUnitIdAndKindAndEndedOnIsNull(
            UUID tenantId, UUID propertyUnitId, ConnectionStatus.Kind kind);

    List<ServiceConnection> findByTenantIdAndPropertyUnitIdOrderByKind(UUID tenantId, UUID propertyUnitId);
}
