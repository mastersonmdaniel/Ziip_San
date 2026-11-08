package br.com.ziipsan.core.effluent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leitura do lodo para o aspecto ambiental da ED-70 (pacote ISO
 * 14001): a ED-70 LE estes totais — geracao e destinacao registradas
 * aqui valem com a norma ligada ou desligada (a obrigacao e
 * REGULATORY, ver {@link RegulatoryObligation}) — e nenhuma geracao
 * ou destinacao e registrada pela ED-70. Este resumo e a fronteira
 * de leitura: totais por tipo de residuo na unidade da quantidade
 * de cada grupo (grupos de unidade diferente nao se somam — a
 * chave inclui a unidade), saidas realizadas e registros ainda sem
 * destinacao, nomeados.
 */
public record SludgeAspectSummary(
        UUID tenantId,
        Map<String, BigDecimal> generatedByKindAndUnit,
        long dispatchedCount,
        long awaitingDestinationCount) {

    public SludgeAspectSummary {
        generatedByKindAndUnit = Map.copyOf(generatedByKindAndUnit);
    }

    public static SludgeAspectSummary summarize(UUID tenantId, List<SludgeRecord> records) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId e obrigatorio");
        if (records == null) throw new IllegalArgumentException("registros sao obrigatorios");
        Map<String, BigDecimal> totals = new java.util.LinkedHashMap<>();
        long dispatched = 0;
        long awaiting = 0;
        for (SludgeRecord r : records) {
            if (!r.tenantId().equals(tenantId)) continue;
            String key = r.residueKind() + "|" + r.quantityUnit();
            totals.merge(key, r.quantity(), BigDecimal::add);
            if (r.status() == SludgeRecord.Status.DISPATCHED || r.status() == SludgeRecord.Status.CLOSED) {
                dispatched++;
            } else {
                awaiting++;
            }
        }
        return new SludgeAspectSummary(tenantId, totals, dispatched, awaiting);
    }
}
