package br.com.ziipsan.core.contractor;

import br.com.ziipsan.core.certification.governance.GovernanceValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Medicao de empreiteira (ED-37; SG-495/SG-496): o valor devido no periodo e
 * a soma das OS EXECUTADAS pela empreiteira (ED-36: OS do motor da P1.31
 * executadas em modo CONTRACTOR, inclusive as de baixa posterior da ED-36),
 * precificadas pelos itens declarados na construcao da medicao. Nenhuma OS
 * entra duas vezes: OS ja medida em medicao nao rejeitada e recusada.
 */
public final class Measurement {
    private Measurement() {}

    /** OS executada pela empreiteira, como a fiacao a le da ED-36/P1.31. */
    public record ExecutedOrder(UUID orderId, UUID tenantId, UUID contractorSupplierId,
                                LocalDateTime executedAt, String description) {
        public ExecutedOrder {
            if (orderId == null) throw new GovernanceValidationException("MEASUREMENT_ORDER_REQUIRED", "OS executada exige id");
            if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
            if (contractorSupplierId == null) throw new GovernanceValidationException("CONTRACTOR_SUPPLIER_REQUIRED", "OS executada exige a empreiteira executora");
            if (executedAt == null) throw new GovernanceValidationException("MEASUREMENT_EXECUTED_AT_REQUIRED", "OS executada exige data/hora da execucao (ED-36)");
            if (description == null || description.isBlank()) throw new GovernanceValidationException("MEASUREMENT_ORDER_DESCRIPTION_REQUIRED", "OS executada exige descricao");
        }
    }

    /** Porta de leitura das OS executadas (fiacao sobre ED-36/P1.31). */
    public interface ExecutedOrdersPort {
        List<ExecutedOrder> ordersExecutedBy(UUID tenantId, UUID contractorSupplierId, LocalDate from, LocalDate to);
    }

    /** Item da medicao: UMA OS executada, com quantidade e preco declarados. */
    public record MeasurementItem(UUID orderId, String description, BigDecimal quantity, BigDecimal unitPrice) {
        public MeasurementItem {
            if (orderId == null) throw new GovernanceValidationException("MEASUREMENT_ORDER_REQUIRED", "item de medicao exige a OS");
            if (description == null || description.isBlank()) throw new GovernanceValidationException("MEASUREMENT_ITEM_DESCRIPTION_REQUIRED", "item de medicao exige descricao");
            if (quantity == null || quantity.signum() <= 0) throw new GovernanceValidationException("MEASUREMENT_QUANTITY_INVALID", "quantidade do item deve ser positiva");
            if (unitPrice == null || unitPrice.signum() < 0) throw new GovernanceValidationException("MEASUREMENT_PRICE_INVALID", "preco unitario do item nao pode ser nulo/negativo");
        }
        public BigDecimal amount() {
            return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        }
    }

    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED }

    /** Medicao de um contrato num periodo. Totais sao derivados dos itens e das penalidades vinculadas. */
    public record ContractorMeasurement(UUID id, UUID tenantId, UUID contractId, UUID contractorSupplierId,
                                        LocalDate periodStart, LocalDate periodEnd,
                                        List<MeasurementItem> items, Status status) {
        public ContractorMeasurement {
            if (id == null) throw new GovernanceValidationException("MEASUREMENT_ID_REQUIRED", "medicao exige id");
            if (tenantId == null) throw new GovernanceValidationException("TENANT_REQUIRED", "tenant obrigatorio");
            if (contractId == null) throw new GovernanceValidationException("MEASUREMENT_CONTRACT_REQUIRED", "medicao exige o contrato");
            if (contractorSupplierId == null) throw new GovernanceValidationException("CONTRACTOR_SUPPLIER_REQUIRED", "medicao exige a empreiteira");
            if (periodStart == null || periodEnd == null) throw new GovernanceValidationException("MEASUREMENT_PERIOD_REQUIRED", "medicao exige o periodo");
            if (periodEnd.isBefore(periodStart)) throw new GovernanceValidationException("MEASUREMENT_PERIOD_INVERTED", "fim do periodo anterior ao inicio");
            if (items == null || items.isEmpty()) throw new GovernanceValidationException("MEASUREMENT_EMPTY", "medicao sem OS executada nao existe");
            items = List.copyOf(items);
            if (status == null) throw new GovernanceValidationException("MEASUREMENT_STATUS_REQUIRED", "medicao exige estado");
        }

        public BigDecimal grossAmount() {
            return items.stream().map(MeasurementItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        /** Liquido = bruto − penalidades APLICADAS vinculadas a esta medicao. */
        public BigDecimal netAmount(List<Penalties.PenaltyApplication> applications) {
            BigDecimal deductions = applications.stream()
                    .filter(a -> a.status() == Penalties.PenaltyApplication.Status.APPLIED
                            && id.equals(a.measurementIdOrNull()))
                    .map(Penalties.PenaltyApplication::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return grossAmount().subtract(deductions).setScale(2, RoundingMode.HALF_UP);
        }

        public ContractorMeasurement withStatus(Status newStatus) {
            return new ContractorMeasurement(id, tenantId, contractId, contractorSupplierId, periodStart, periodEnd, items, newStatus);
        }
    }
}
