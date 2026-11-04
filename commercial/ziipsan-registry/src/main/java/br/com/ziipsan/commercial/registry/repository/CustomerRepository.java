package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    /** Cliente ativo com o documento (pelo indice cego), para detectar duplicidade sem decifrar. */
    Optional<Customer> findByTenantIdAndDocumentHashAndDeletedAtIsNull(UUID tenantId, String documentHash);

    /** Candidatos à fusão sem documento: mesmo nome e mesma data de nascimento, ativos. */
    java.util.List<Customer> findByTenantIdAndNameIgnoreCaseAndBirthDateAndDeletedAtIsNull(
            UUID tenantId, String name, java.time.LocalDate birthDate);

    /** Clientes com documento cifrado (inclui excluídos: a máscara gravada também é recalculada). */
    java.util.List<Customer> findByTenantIdAndDocumentCiphertextIsNotNull(UUID tenantId);
}
