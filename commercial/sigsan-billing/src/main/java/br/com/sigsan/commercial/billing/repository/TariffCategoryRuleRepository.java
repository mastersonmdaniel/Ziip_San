package br.com.sigsan.commercial.billing.repository;

import br.com.sigsan.commercial.billing.domain.TariffCategoryRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TariffCategoryRuleRepository extends JpaRepository<TariffCategoryRuleEntity, UUID> {}
