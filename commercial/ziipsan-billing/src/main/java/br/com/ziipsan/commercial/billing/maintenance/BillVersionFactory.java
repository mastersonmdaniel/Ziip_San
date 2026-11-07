package br.com.ziipsan.commercial.billing.maintenance;

import br.com.ziipsan.commercial.billing.bill.BillStatus;
import br.com.ziipsan.commercial.billing.domain.BillEntity;

import java.util.Objects;
import java.util.UUID;

/**
 * Nova versao da conta (DAD-14/DAD-16): a operacao de manutencao NUNCA
 * atualiza os valores da conta vigente -- monta uma linha nova, copia os
 * valores faturados da anterior (o chamador corrige os que a operacao muda
 * e recalcula o total pela formula do CHECK), liga a cadeia por
 * {@code origin_bill_id}/{@code version_no} e devolve tambem o status que a
 * versao anterior assume. A versao anterior nao e apagada: sai da vigencia
 * pelo status (o indice unico parcial de contas vivas libera o par
 * unidade/referencia), como no {@code ConsumptionRectification} da P2.5.
 */
public final class BillVersionFactory {

    /** Par (nova versao, status que a anterior assume quando a nova entra). */
    public record VersionedBill(BillEntity newVersion, BillStatus supersededStatus) {
    }

    private BillVersionFactory() {
    }

    /**
     * @param current     a versao vigente da conta (viva)
     * @param newStatus   status da nova versao (ex.: RECTIFIED; na alteracao
     *                    de vencimento, o mesmo status de cobranca da anterior)
     * @param serviceOrderId OS interna que executa a operacao (quando houver)
     * @param protocolId  protocolo da P1.36 quando o pedido veio do usuario;
     *                    null na alteracao interna do backoffice (ADR-0039, 7)
     */
    public static VersionedBill nextVersion(BillEntity current, BillStatus newStatus,
                                            UUID serviceOrderId, UUID protocolId) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(newStatus, "newStatus");
        BillStatus currentStatus = BillStatus.valueOf(current.getStatus());
        if (!currentStatus.live()) {
            throw new IllegalStateException("So a versao viva pode gerar nova versao; esta esta "
                    + current.getStatus());
        }
        BillEntity next = new BillEntity();
        next.setId(UUID.randomUUID());
        next.setTenantId(current.getTenantId());
        next.setUnitId(current.getUnitId());
        next.setReferenceMonth(current.getReferenceMonth());
        next.setStatus(newStatus.name());
        next.setPreviousStatus(current.getStatus());
        next.setPeriodFrom(current.getPeriodFrom());
        next.setPeriodTo(current.getPeriodTo());
        next.setWaterConsumption(current.getWaterConsumption());
        next.setSewerVolume(current.getSewerVolume());
        next.setSewerPct(current.getSewerPct());
        next.setCollectionPct(current.getCollectionPct());
        next.setWaterValue(current.getWaterValue());
        next.setSewerValue(current.getSewerValue());
        next.setFixedPartValue(current.getFixedPartValue());
        next.setDebitsValue(current.getDebitsValue());
        next.setCreditsValue(current.getCreditsValue());
        next.setTaxesValue(current.getTaxesValue());
        next.setTotalValue(current.getTotalValue());
        next.setDueDate(current.getDueDate());
        next.setOriginalDueDate(current.getOriginalDueDate());
        next.setValidUntil(current.getValidUntil());
        next.setIssuedOn(current.getIssuedOn());
        next.setRectificationCount(current.getRectificationCount() + 1);
        next.setDueChangeCount(current.getDueChangeCount());
        next.setLateFeeCharged(current.isLateFeeCharged());
        next.setReviewedOn(current.getReviewedOn());
        next.setOriginBillId(current.getId());
        next.setOriginReferenceMonth(current.getReferenceMonth());
        next.setTariffSnapshot(current.getTariffSnapshot());
        next.setParameterVersionIds(current.getParameterVersionIds());
        next.setBilledCustomerId(current.getBilledCustomerId());
        next.setBilledCustomerSnapshot(current.getBilledCustomerSnapshot());
        next.setCompleted(current.isCompleted());
        next.setTaxTreatment(current.getTaxTreatment());
        next.setAuditBatchId(current.getAuditBatchId());
        next.setFiscalDocumentId(null);
        next.setVersionNo(current.getVersionNo() + 1);
        next.setServiceOrderId(serviceOrderId);
        next.setProtocolId(protocolId);
        return new VersionedBill(next, BillStatus.CANCELLED_BY_RECTIFICATION);
    }
}
