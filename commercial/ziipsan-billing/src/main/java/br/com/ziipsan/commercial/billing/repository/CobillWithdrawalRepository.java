package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.CobillWithdrawalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositorio da retirada a pedido (ED-25). Criado na correcao
 * pos-reprovacao D2: {@code cobill_withdrawals} era a unica tabela do
 * pacote sem entidade/repositorio. O protocolo e unico POR TENANT
 * (indice {@code idx_cobill_withdrawals_protocol (tenant_id, protocol)}),
 * por isso a busca e composta com tenant, no padrao da ED-22.
 */
@Repository
public interface CobillWithdrawalRepository extends JpaRepository<CobillWithdrawalEntity, UUID> {

    Optional<CobillWithdrawalEntity> findByTenantIdAndProtocol(UUID tenantId, String protocol);

    List<CobillWithdrawalEntity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);
}
