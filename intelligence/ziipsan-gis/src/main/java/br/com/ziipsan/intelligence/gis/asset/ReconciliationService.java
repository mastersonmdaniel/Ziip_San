package br.com.ziipsan.intelligence.gis.asset;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Conciliacao GIS x patrimonio exportada a contabilidade (GES-14),
 * com o de-para do MCRSB da ED-32: cada ativo do inventario com
 * codigo MCPSB e confrontado com (a) a existencia do de-para
 * vigente na ED-32 para a sua conta regulatoria e (b) o custo
 * historico registrado. Divergencia NUNCA e corrigida em silencio:
 * ela sai nomeada na exportacao (sem de-para, sem custo, codigo
 * patrimonial ausente, bem reversivel sem origem).
 *
 * <p>A "conta regulatoria" de um ativo e derivada do seu par
 * UP/UAR na fiacao (a tabela do MCPSB liga UP/UAR a conta); aqui o
 * chamador entrega o codigo ja resolvido por ativo — nenhum codigo
 * de conta e inventado nesta classe.
 */
public final class ReconciliationService {

    public enum Divergence {
        MISSING_PATRIMONIAL_CODE, MISSING_MCPSB_UP_UAR, NO_DEPARA_IN_ED32,
        MISSING_HISTORICAL_COST, REVERSIBLE_WITHOUT_ORIGIN
    }

    /**
     * Entrada da conciliacao por ativo. <b>Correcao D5 (revisao
     * ED-56):</b> o input passa a carregar {@code investmentOrigin}
     * (a origem do investimento do bem reversivel, o mesmo fato que o
     * {@code AssetRecord} exige) — sem ela a divergencia
     * {@code REVERSIBLE_WITHOUT_ORIGIN} era codigo morto, impossivel
     * de emitir nesta superficie.
     */
    public record AssetAccountingInput(UUID assetId, String patrimonialCode,
                                       String up, String uar, String regulatoryAccountCode,
                                       BigDecimal historicalCost, boolean reversible,
                                       String investmentOrigin) {
    }

    public record ReconciliationLine(UUID assetId, String patrimonialCode,
                                     String regulatoryAccountCode, String tenantAccountCode,
                                     List<Divergence> divergences) {
        public boolean clean() { return divergences.isEmpty(); }
    }

    public record ReconciliationExport(UUID tenantId, int exercise, String reference,
                                       List<ReconciliationLine> lines) {
        public long divergentCount() { return lines.stream().filter(l -> !l.clean()).count(); }
    }

    private final AssetPorts.AccountingPort accounting;

    public ReconciliationService(AssetPorts.AccountingPort accounting) {
        this.accounting = Objects.requireNonNull(accounting, "porta da ED-32 e obrigatoria");
    }

    public ReconciliationExport reconcile(UUID tenantId, int exercise, List<AssetAccountingInput> inputs) {
        Objects.requireNonNull(tenantId, "conciliacao sem tenant");
        List<ReconciliationLine> lines = inputs.stream().map(in -> {
            java.util.List<Divergence> div = new java.util.ArrayList<>();
            if (in.patrimonialCode() == null || in.patrimonialCode().isBlank())
                div.add(Divergence.MISSING_PATRIMONIAL_CODE);
            boolean hasPair = in.up() != null && in.uar() != null;
            if (!hasPair) div.add(Divergence.MISSING_MCPSB_UP_UAR);
            String tenantAccount = null;
            if (hasPair && in.regulatoryAccountCode() != null) {
                var resolved = accounting.resolveTenantAccount(in.regulatoryAccountCode(), exercise);
                if (resolved.isEmpty()) div.add(Divergence.NO_DEPARA_IN_ED32);
                else tenantAccount = resolved.get();
            } else if (hasPair) {
                div.add(Divergence.NO_DEPARA_IN_ED32);
            }
            if (in.historicalCost() == null) div.add(Divergence.MISSING_HISTORICAL_COST);
            // D5: bem reversivel sem origem do investimento (NR 3/2023) — emitido de fato.
            if (in.reversible() && (in.investmentOrigin() == null || in.investmentOrigin().isBlank()))
                div.add(Divergence.REVERSIBLE_WITHOUT_ORIGIN);
            return new ReconciliationLine(in.assetId(), in.patrimonialCode(),
                    in.regulatoryAccountCode(), tenantAccount, List.copyOf(div));
        }).toList();
        String reference = "GIS19-RECON-" + exercise + "-" + lines.size();
        var export = new ReconciliationExport(tenantId, exercise, reference, lines);
        accounting.receiveReconciliationExport(tenantId, reference, exercise);
        return export;
    }
}
