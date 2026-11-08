package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.GroupedBillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Correcao pos-revisao (D5): o codigo do agrupador so e unico POR TENANT
 * (indice {@code idx_grouped_bills_code (tenant_id, code)}), e o padrao
 * dos repositorios de entidades de tenant da P2.5 (ex.:
 * {@code BillingGroupRepository}, {@code CycleActivityRepository}) e a
 * busca composta {@code findByTenantIdAndCode} - a busca sem tenant foi
 * removida em vez de apenas justificada.
 */
@Repository
public interface GroupedBillRepository extends JpaRepository<GroupedBillEntity, UUID> {

    Optional<GroupedBillEntity> findByTenantIdAndCode(UUID tenantId, String code);
}
