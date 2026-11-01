package br.com.ziipsan.grs.mtr.repository;

import br.com.ziipsan.grs.mtr.domain.WasteTransporter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteTransporterRepository extends JpaRepository<WasteTransporter, UUID> {

    Optional<WasteTransporter> findByIdAndTenantId(UUID id, UUID tenantId);
}