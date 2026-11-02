package br.com.ziipsan.commercial.registry.repository;

import br.com.ziipsan.commercial.registry.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    /** Enderecos de uma entidade polimorfica (ex.: entity_type='customers'), na ordem da lista. */
    List<Address> findByTenantIdAndEntityTypeAndEntityIdOrderByPositionAsc(UUID tenantId, String entityType,
                                                                           UUID entityId);
}
