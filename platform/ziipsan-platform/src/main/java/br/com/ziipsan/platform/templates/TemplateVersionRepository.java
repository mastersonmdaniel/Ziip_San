package br.com.ziipsan.platform.templates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Versões de modelo do tenant. A versão vigente de um modelo numa data é a
 * APPROVED cuja vigência [valid_from, valid_to] contém a data, com os dois
 * extremos inclusivos (o mesmo sentido do EXCLUDE da V1_12).
 */
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

    Optional<TemplateVersion> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TemplateVersion> findAllByTenantIdAndTemplateIdOrderByVersionNumberAsc(UUID tenantId, UUID templateId);

    Optional<TemplateVersion> findTopByTenantIdAndTemplateIdOrderByVersionNumberDesc(UUID tenantId, UUID templateId);

    /**
     * Versões APPROVED vigentes na data: valid_from &lt;= data e (valid_to
     * nulo ou &gt;= data). Não dá para derivar por nome (o valid_to nulo
     * precisa do OR explícito), por isso a JPQL.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT v FROM TemplateVersion v WHERE v.tenantId = :tenantId AND v.templateId = :templateId"
                    + " AND v.status = 'APPROVED' AND v.validFrom <= :date"
                    + " AND (v.validTo IS NULL OR v.validTo >= :date)")
    List<TemplateVersion> findApprovedEffectiveOn(UUID tenantId, UUID templateId, LocalDate date);
}
