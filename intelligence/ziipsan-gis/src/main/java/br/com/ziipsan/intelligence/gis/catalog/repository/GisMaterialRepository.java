package br.com.ziipsan.intelligence.gis.catalog.repository;

import br.com.ziipsan.intelligence.gis.catalog.domain.GisMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GisMaterialRepository extends JpaRepository<GisMaterial, UUID> {

    List<GisMaterial> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<GisMaterial> findByIdAndTenantId(UUID id, UUID tenantId);
}
