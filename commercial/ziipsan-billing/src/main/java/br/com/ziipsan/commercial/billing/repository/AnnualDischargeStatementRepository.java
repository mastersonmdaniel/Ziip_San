package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.AnnualDischargeStatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnnualDischargeStatementRepository
        extends JpaRepository<AnnualDischargeStatementEntity, UUID> {

    /** O ano ja foi declarado para a unidade? (a emissao e unica por ano) */
    boolean existsByTenantIdAndUnitIdAndReferenceYear(UUID tenantId, UUID unitId, int referenceYear);

    /** Os registros da unidade, por ano: a fonte do {@code alreadyIssuedYears} da avaliacao. */
    List<AnnualDischargeStatementEntity> findByTenantIdAndUnitIdOrderByReferenceYearAsc(UUID tenantId,
                                                                                        UUID unitId);
}
