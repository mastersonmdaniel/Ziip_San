package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillDebitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Linhas de debito da conta (a P2.5 nao entregou este repositorio; a P2.20 o usa no acerto do cancelamento). */
@Repository
public interface BillDebitRepository extends JpaRepository<BillDebitEntity, UUID> {

    List<BillDebitEntity> findByTenantIdAndBillId(UUID tenantId, UUID billId);
}
