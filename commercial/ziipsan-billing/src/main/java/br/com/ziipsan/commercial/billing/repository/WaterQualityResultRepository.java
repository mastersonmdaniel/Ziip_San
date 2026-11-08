package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.WaterQualityResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface WaterQualityResultRepository extends JpaRepository<WaterQualityResultEntity, UUID> {

    /** Os resultados de um mes/sistema, na ordem de impressao do bloco (chave do parametro). */
    List<WaterQualityResultEntity> findByTenantIdAndReferenceMonthAndSupplySystemOrderByParameterKeyAsc(
            UUID tenantId, LocalDate referenceMonth, String supplySystem);

    /** A linha ja existe? (a mesma chave composta da unicidade da tabela e do esquema da P1.40) */
    boolean existsByTenantIdAndReferenceMonthAndSupplySystemAndParameterKeyAndSource(
            UUID tenantId, LocalDate referenceMonth, String supplySystem, String parameterKey, String source);
}
