package br.com.sigsan.intelligence.quality.repository;

import br.com.sigsan.intelligence.quality.lab.LabAnalysisMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabAnalysisMethodRepository extends JpaRepository<LabAnalysisMethod, UUID> {

    List<LabAnalysisMethod> findByParameterIdAndTenantId(UUID parameterId, UUID tenantId);
}