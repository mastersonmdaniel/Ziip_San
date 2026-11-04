package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByTenantIdAndCustomerIdOrderByValidFromAsc(UUID tenantId, UUID customerId);

}
