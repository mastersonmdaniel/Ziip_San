package br.com.ziipsan.commercial.billing.printing;

import br.com.ziipsan.commercial.billing.domain.BillDeliveryReturnEntity;
import br.com.ziipsan.commercial.billing.repository.BillDeliveryReturnRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Implementacao do {@link UndeliveredBillService} (P2.8): converte o registro
 * validado para a entidade e le de volta SEM afrouxar o catalogo — chave
 * gravada fora do catalogo (dado legado ou escrita direta) e erro explicito
 * na leitura, nao motivo inventado.
 */
@Service
public class DefaultUndeliveredBillService implements UndeliveredBillService {

    private final BillDeliveryReturnRepository repository;

    public DefaultUndeliveredBillService(BillDeliveryReturnRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository e obrigatorio");
        }
        this.repository = repository;
    }

    @Override
    @Transactional
    public void register(UndeliveredBill undelivered) {
        if (undelivered == null) {
            throw new IllegalArgumentException("registro de nao entrega e obrigatorio");
        }
        repository.save(toEntity(undelivered));
    }

    @Override
    @Transactional
    public List<UndeliveredBill> importReturnFile(String content) {
        List<UndeliveredBill> parsed = ReturnFileParser.parse(content);
        for (UndeliveredBill undelivered : parsed) {
            repository.save(toEntity(undelivered));
        }
        return parsed;
    }

    @Override
    public List<UndeliveredBill> returnsForUnit(UUID unitId) {
        if (unitId == null) {
            throw new IllegalArgumentException("unitId e obrigatorio");
        }
        return repository.findByUnitIdOrderByReturnDateDesc(unitId).stream().map(this::toRecord).toList();
    }

    @Override
    public List<UndeliveredBill> returnsBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("janela de retornos invalida");
        }
        return repository.findByReturnDateBetweenOrderByReturnDateAsc(from, to).stream().map(this::toRecord).toList();
    }

    private BillDeliveryReturnEntity toEntity(UndeliveredBill undelivered) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("registro de nao entrega sem tenant no contexto");
        }
        BillDeliveryReturnEntity entity = new BillDeliveryReturnEntity();
        entity.setTenantId(tenantId);
        entity.setBillId(undelivered.billId());
        entity.setUnitId(undelivered.unitId());
        entity.setReasonKey(undelivered.reason().key());
        entity.setReturnDate(undelivered.returnDate());
        entity.setOriginKey(undelivered.origin().name());
        return entity;
    }

    private UndeliveredBill toRecord(BillDeliveryReturnEntity entity) {
        return new UndeliveredBill(entity.getBillId(), entity.getUnitId(),
                UndeliveredReason.fromKey(entity.getReasonKey()), entity.getReturnDate(),
                ReturnOrigin.fromKey(entity.getOriginKey()));
    }
}
