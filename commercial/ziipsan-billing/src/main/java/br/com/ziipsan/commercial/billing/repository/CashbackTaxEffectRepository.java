package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CashbackTaxEffectEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashbackTaxEffectRepository extends JpaRepository<CashbackTaxEffectEntity, UUID> {

    Optional<CashbackTaxEffectEntity> findByTenantIdAndCompetenceMonth(UUID tenantId, LocalDate competenceMonth);
}
