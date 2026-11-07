package br.com.ziipsan.commercial.billing.leakmeter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeterTestRepository extends JpaRepository<MeterTestEntity, UUID> {

    Optional<MeterTestEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<MeterTestEntity> findByTenantIdAndMeterIdAndStatus(UUID tenantId, UUID meterId, String status);
}
