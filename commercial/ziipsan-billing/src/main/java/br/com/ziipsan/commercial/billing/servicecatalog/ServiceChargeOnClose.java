package br.com.ziipsan.commercial.billing.servicecatalog;

import br.com.ziipsan.commercial.billing.math.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Cobrança lançada no encerramento da OS (ED-34, teste do acceptance): quando a OS fecha
 * como executada ({@code CLOSED_EXECUTED}, máquina de estados da P1.31/V9_1), o preço
 * vigente na data do encerramento é resolvido pela {@link PriceTable} e o lançamento nasce
 * como débito de serviço (fonte {@code SERVICE_ORDER}, o mesmo sourceType dos débitos da
 * P2.5), com materiais usados somados e parcelamento opcional (POC-31).
 *
 * <p>Idempotência: a chave é OS + item de preço; a persistência (integração) deve ter
 * UNIQUE sobre ela — relançar a mesma OS/item é recusado pelo chamador ao conferir a chave
 * já lançada (contrato declarado no SQL pendente).
 */
public final class ServiceChargeOnClose {

    private ServiceChargeOnClose() {}

    /** Estado mínimo da OS no encerramento (espelho dos status da V9_1). */
    public record ClosedOrder(UUID orderId, String status, LocalDate closedOn, UUID consumerUnitId) {
        public ClosedOrder {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            Objects.requireNonNull(status, "status da OS e obrigatorio");
            Objects.requireNonNull(closedOn, "data de encerramento e obrigatoria");
            Objects.requireNonNull(consumerUnitId, "unidade consumidora e obrigatoria para lancar cobranca");
        }
        boolean executed() { return "CLOSED_EXECUTED".equals(status); }
    }

    public record MaterialUse(String materialCode, BigDecimal quantity) {
        public MaterialUse {
            if (materialCode == null || materialCode.isBlank()) throw new IllegalArgumentException("material e obrigatorio");
            if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantidade de material deve ser positiva");
        }
    }

    public record ChargePosting(String idempotencyKey, UUID serviceOrderId, UUID consumerUnitId,
                                String priceItemCode, RevenueNature revenueNature, BigDecimal serviceAmount,
                                BigDecimal materialsAmount, BigDecimal total,
                                ServiceInstallmentPlan installmentPlan, String sourceType) {}

    public static ChargePosting charge(PriceTable table, ClosedOrder order, String priceItemCode,
                                       BigDecimal quantity, List<MaterialUse> materials,
                                       Integer installments, BigDecimal limitMaxAmount, int limitMaxInstallments) {
        if (!order.executed())
            throw new IllegalStateException("cobranca so e lancada no encerramento executado da OS; status=" + order.status());
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("quantidade do servico deve ser positiva");
        ServicePriceItem item = table.service(priceItemCode, order.closedOn());
        if (!item.billable())
            throw new IllegalStateException("item " + priceItemCode + " exige homologacao regulatoria "
                    + "(rev.homologation_required) sem registro de homologacao: nao faturavel");
        BigDecimal serviceAmount = Money.roundHalfUp(item.unitPrice().multiply(quantity));
        BigDecimal materialsAmount = Money.ZERO;
        for (MaterialUse m : materials == null ? List.<MaterialUse>of() : materials) {
            MaterialPriceItem mp = table.material(m.materialCode(), order.closedOn());
            materialsAmount = materialsAmount.add(Money.roundHalfUp(mp.unitPrice().multiply(m.quantity())));
        }
        BigDecimal total = serviceAmount.add(materialsAmount);
        ServiceInstallmentPlan plan = installments == null ? null
                : ServiceInstallmentPlan.plan(total, installments, limitMaxAmount, limitMaxInstallments);
        return new ChargePosting(order.orderId() + ":" + priceItemCode, order.orderId(), order.consumerUnitId(),
                priceItemCode, item.revenueNature(), serviceAmount, Money.roundHalfUp(materialsAmount),
                Money.roundHalfUp(total), plan, "SERVICE_ORDER");
    }
}
