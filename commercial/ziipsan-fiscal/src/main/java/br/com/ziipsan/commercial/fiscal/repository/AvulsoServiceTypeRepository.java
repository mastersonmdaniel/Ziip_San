package br.com.ziipsan.commercial.fiscal.repository;

import br.com.ziipsan.commercial.fiscal.domain.AvulsoServiceTypeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvulsoServiceTypeRepository extends JpaRepository<AvulsoServiceTypeEntity, UUID> {

    Optional<AvulsoServiceTypeEntity> findByTenantIdAndCode(UUID tenantId, String code);
}
