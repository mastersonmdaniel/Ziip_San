package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.GroupedBillMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Membros da fatura agrupadora (correcao pos-revisao, D5).
 * {@code findByTenantIdAndBillId} expoe a unicidade
 * {@code (tenant_id, bill_id)} do SQL pendente na camada Java.
 */
@Repository
public interface GroupedBillMemberRepository extends JpaRepository<GroupedBillMemberEntity, UUID> {

    List<GroupedBillMemberEntity> findByGroupedBillId(UUID groupedBillId);

    Optional<GroupedBillMemberEntity> findByTenantIdAndBillId(UUID tenantId, UUID billId);
}
