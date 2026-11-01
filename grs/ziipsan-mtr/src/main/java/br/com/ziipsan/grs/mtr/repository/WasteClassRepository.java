package br.com.ziipsan.grs.mtr.repository;

import br.com.ziipsan.grs.mtr.domain.WasteClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WasteClassRepository extends JpaRepository<WasteClass, UUID> {

    Optional<WasteClass> findByIdAndTenantId(UUID id, UUID tenantId);
}