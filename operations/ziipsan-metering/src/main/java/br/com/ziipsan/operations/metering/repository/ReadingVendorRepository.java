package br.com.ziipsan.operations.metering.repository;

import br.com.ziipsan.operations.metering.domain.ReadingVendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReadingVendorRepository extends JpaRepository<ReadingVendor, UUID> {

    Optional<ReadingVendor> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ReadingVendor> findByTenantIdAndCode(UUID tenantId, String code);
}
