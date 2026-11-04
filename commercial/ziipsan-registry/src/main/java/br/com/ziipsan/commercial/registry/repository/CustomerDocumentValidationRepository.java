package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.CustomerDocumentValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerDocumentValidationRepository extends JpaRepository<CustomerDocumentValidation, UUID> {

    List<CustomerDocumentValidation> findByTenantIdAndCustomerIdOrderByValidatedAtDesc(UUID tenantId, UUID customerId);

    List<CustomerDocumentValidation> findByTenantIdAndCustomerIdAndResultOrderByValidatedAtDesc(
            UUID tenantId, UUID customerId, String result);
}
