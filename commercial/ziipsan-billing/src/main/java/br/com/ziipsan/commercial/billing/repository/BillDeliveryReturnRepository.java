package br.com.ziipsan.commercial.billing.repository;

import br.com.ziipsan.commercial.billing.domain.BillDeliveryReturnEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BillDeliveryReturnRepository extends JpaRepository<BillDeliveryReturnEntity, UUID> {

    /** Retornos de uma conta (a P3.32 e o atendimento leem por conta/unidade). */
    List<BillDeliveryReturnEntity> findByBillIdOrderByReturnDateDesc(UUID billId);

    /** Historico de nao entrega da unidade, mais recente primeiro (JOR-35). */
    List<BillDeliveryReturnEntity> findByUnitIdOrderByReturnDateDesc(UUID unitId);

    /** Janela de retornos para a varredura de qualidade cadastral da P3.32. */
    List<BillDeliveryReturnEntity> findByReturnDateBetweenOrderByReturnDateAsc(LocalDate from, LocalDate to);

    /** Retornos por motivo de catalogo (indicadores da P3.32 por motivo). */
    List<BillDeliveryReturnEntity> findByReasonKeyOrderByReturnDateDesc(String reasonKey);
}
