package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillTemplateVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillTemplateVersionRepository extends JpaRepository<BillTemplateVersionEntity, UUID> {

    /** Versao vigente do modelo numa data (a restricao de exclusao garante no maximo uma). */
    @Query("select v from BillTemplateVersionEntity v where v.tenantId = :tenantId"
            + " and v.templateId = :templateId"
            + " and v.validFrom <= :date and (v.validTo is null or v.validTo >= :date)")
    Optional<BillTemplateVersionEntity> findEffectiveOn(@Param("tenantId") UUID tenantId,
                                                        @Param("templateId") UUID templateId,
                                                        @Param("date") LocalDate date);
}
