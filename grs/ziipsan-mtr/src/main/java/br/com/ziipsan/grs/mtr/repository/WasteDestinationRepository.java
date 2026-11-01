package br.com.ziipsan.grs.mtr.repository;

import br.com.ziipsan.grs.mtr.domain.WasteDestination;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteDestinationRepository extends JpaRepository<WasteDestination, UUID> {

    Optional<WasteDestination> findByIdAndTenantId(UUID id, UUID tenantId);
}