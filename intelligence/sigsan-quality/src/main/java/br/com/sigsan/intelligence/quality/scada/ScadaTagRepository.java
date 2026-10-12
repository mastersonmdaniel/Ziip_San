package br.com.sigsan.intelligence.quality.scada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScadaTagRepository extends JpaRepository<ScadaTag, UUID> {

    List<ScadaTag> findByTenantIdAndStatus(UUID tenantId, String status);
}