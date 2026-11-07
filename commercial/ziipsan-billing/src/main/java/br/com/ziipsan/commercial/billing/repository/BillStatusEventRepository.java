package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillStatusEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Eventos de situacao da conta: esta camada so insere e le (nenhum metodo
 * de alteracao/remocao e chamado). A garantia insert-only de verdade e do
 * BANCO (correcao M1 da revisao: a heranca de JpaRepository expoe delete*
 * a quem injetar a interface): a V4_16 revoga UPDATE/DELETE do papel da
 * aplicacao e instala gatilho que recusa UPDATE/DELETE de qualquer origem
 * em {@code bill_status_events} (e em {@code bill_field_changes}).
 */
@Repository
public interface BillStatusEventRepository extends JpaRepository<BillStatusEventEntity, UUID> {

    List<BillStatusEventEntity> findByTenantIdAndBillIdOrderByOccurredAtAsc(UUID tenantId, UUID billId);
}
