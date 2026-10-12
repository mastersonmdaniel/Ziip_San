package br.com.sigsan.commercial.registry.repository;

import br.com.sigsan.commercial.registry.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    /** Cliente ativo com o documento (pelo indice cego), para detectar duplicidade sem decifrar. */
    Optional<Customer> findByTenantIdAndDocumentHashAndDeletedAtIsNull(UUID tenantId, String documentHash);
}
