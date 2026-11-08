package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Motor de alcada (ED-58), puro: decide aprovacao automatica e o papel
 * exigido para aprovacao humana, SOMENTE a partir dos parametros do
 * tenant ({@link InventoryParameters}) — nenhum valor vive aqui.
 *
 * <ul>
 *   <li>item CONTROLADO: nunca automatico, sempre humano (qualquer valor);</li>
 *   <li>automatica: total &lt;= inv.auto_approval_limit e nenhum controlado;</li>
 *   <li>humana: o papel e o da PRIMEIRA faixa de inv.approval_bands que
 *       cobre o total; total sem faixa que o cubra = recusa nomeada
 *       (faixas mal configuradas nao viram aprovacao livre).</li>
 * </ul>
 */
public final class ApprovalPolicy {
    private ApprovalPolicy() {}

    public static boolean autoApprovable(BigDecimal total, boolean anyControlled, BigDecimal autoLimit) {
        if (anyControlled) return false;
        return total.compareTo(autoLimit) <= 0;
    }

    public static String requiredRole(BigDecimal total, List<InventoryParameters.ApprovalBand> bands) {
        for (InventoryParameters.ApprovalBand b : bands) if (b.covers(total)) return b.requiredRole();
        throw new InventoryExceptions.ValidationException("APPROVAL_BAND_NOT_FOUND",
                "nenhuma faixa de " + InventoryParameters.APPROVAL_BANDS + " cobre o total " + total
                        + ": alcada nao configurada para este valor [A DEFINIR]");
    }

    public static Optional<String> requiredRoleOrEmpty(BigDecimal total, List<InventoryParameters.ApprovalBand> bands) {
        try { return Optional.of(requiredRole(total, bands)); } catch (InventoryExceptions.ValidationException e) { return Optional.empty(); }
    }
}
