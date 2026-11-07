package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.ConnectionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConnectionStatusHistoryRepository extends JpaRepository<ConnectionStatusHistory, UUID> {

    List<ConnectionStatusHistory> findByTenantIdAndConnectionIdOrderByChangedAtDesc(
            UUID tenantId, UUID connectionId);

    List<ConnectionStatusHistory> findByTenantIdAndConsumerUnitIdOrderByChangedAtDesc(
            UUID tenantId, UUID consumerUnitId);
}
