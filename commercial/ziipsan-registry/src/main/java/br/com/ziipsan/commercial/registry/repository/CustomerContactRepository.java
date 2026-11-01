package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CustomerContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerContactRepository extends JpaRepository<CustomerContact, UUID> {
    List<CustomerContact> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
