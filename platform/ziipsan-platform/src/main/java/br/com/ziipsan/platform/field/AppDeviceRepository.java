package br.com.ziipsan.platform.field;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppDeviceRepository extends JpaRepository<AppDevice, UUID> {

    Optional<AppDevice> findByTenantIdAndDeviceId(UUID tenantId, UUID deviceId);

    List<AppDevice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
