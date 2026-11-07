package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillRepository extends JpaRepository<BillEntity, UUID> {

    /**
     * P2.5/F1: contas de uma unidade numa referencia, para a guarda de geracao do
     * ciclo ({@code CycleService.assertNewBillAllowed}) decidir com
     * {@code BillIdempotency} antes de calcular: ciclo repetido nao gera segunda
     * conta. A garantia final continua no banco (indice unico parcial de contas
     * vivas da V4_1); esta leitura e a mesma regra, no dominio, um passo antes.
     */
    List<BillEntity> findByTenantIdAndUnitIdAndReferenceMonth(UUID tenantId, UUID unitId, LocalDate referenceMonth);
}
