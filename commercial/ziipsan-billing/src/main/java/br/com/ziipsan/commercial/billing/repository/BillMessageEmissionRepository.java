package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillMessageEmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillMessageEmissionRepository extends JpaRepository<BillMessageEmissionEntity, UUID> {

    /** A prova de uma versao da conta, na ordem de impressao. */
    List<BillMessageEmissionEntity> findByBillIdAndBillVersionNoOrderByPrintPositionAsc(UUID billId,
                                                                                          int billVersionNo);
}
