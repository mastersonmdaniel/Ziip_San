package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillFieldChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Trilha por campo: so insercao e leitura nesta camada; a garantia
 * insert-only e do banco (V4_16: REVOKE de UPDATE/DELETE para o papel da
 * aplicacao + gatilho anti-mutacao, como em bill_status_events).
 */
@Repository
public interface BillFieldChangeRepository extends JpaRepository<BillFieldChangeEntity, UUID> {

    List<BillFieldChangeEntity> findByTenantIdAndBillIdOrderByCreatedAtAsc(UUID tenantId, UUID billId);
}
