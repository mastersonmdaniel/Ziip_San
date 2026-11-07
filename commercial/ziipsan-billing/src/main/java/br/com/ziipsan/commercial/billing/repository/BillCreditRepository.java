package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillCreditEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Linhas de credito realizado da conta (idem BillDebitRepository). */
@Repository
public interface BillCreditRepository extends JpaRepository<BillCreditEntity, UUID> {

    List<BillCreditEntity> findByTenantIdAndBillId(UUID tenantId, UUID billId);
}
