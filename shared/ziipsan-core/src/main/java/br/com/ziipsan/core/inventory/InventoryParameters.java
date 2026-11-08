package br.com.ziipsan.core.inventory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parametros do grupo de almoxarifado (ED-58), SEMPRE lidos do tenant —
 * nenhum valor de alcada, limite ou prazo e constante nesta classe:
 * <pre>
 *   inv.auto_approval_limit          teto (R$) da aprovacao automatica;
 *                                    abaixo dele (e sem item controlado) a
 *                                    requisicao e aprovada automaticamente
 *                                    COM REGISTRO. Sem fabrica: [A DEFINIR].
 *   inv.approval_bands               faixas de valor -> papel aprovador.
 *                                    Sem fabrica: [A DEFINIR].
 *   inv.negative_regularization_days prazo (dias) para regularizar saldo
 *                                    negativo; alem dele, alerta reforcado.
 *                                    Sem fabrica: [A DEFINIR].
 *   inv.lot_expiry_alert_days        janela (dias) do alerta de validade de
 *                                    lote — chave NOMEADA pelo acceptance.
 *                                    Sem fabrica: [A DEFINIR].
 * </pre>
 * Ausencia de qualquer um = a operacao que dele depende RECUSA com motivo
 * nomeado (nunca valor presumido) — regra da cascata e das entregas
 * vizinhas (ED-20/ED-36). Os 14 valores da P3.9 NAO sao tocados por esta
 * entrega: as definitions vao no SQL pendente com factory_default NULL.
 */
public interface InventoryParameters {

    String AUTO_APPROVAL_LIMIT = "inv.auto_approval_limit";
    String APPROVAL_BANDS = "inv.approval_bands";
    String NEGATIVE_REGULARIZATION_DAYS = "inv.negative_regularization_days";
    String LOT_EXPIRY_ALERT_DAYS = "inv.lot_expiry_alert_days";

    /** Faixa de alcada: total ate maxTotal (null = sem teto) exige o papel. */
    record ApprovalBand(BigDecimal maxTotal, String requiredRole) {
        public ApprovalBand {
            if (maxTotal != null && maxTotal.signum() < 0) throw new IllegalArgumentException("faixa de alcada com teto negativo");
            if (requiredRole == null || requiredRole.isBlank()) throw new IllegalArgumentException("faixa de alcada exige o papel aprovador");
        }
        public boolean covers(BigDecimal total) { return maxTotal == null || total.compareTo(maxTotal) <= 0; }
    }

    Optional<BigDecimal> autoApprovalLimit(UUID tenantId);
    Optional<List<ApprovalBand>> approvalBands(UUID tenantId);
    Optional<Integer> negativeRegularizationDays(UUID tenantId);
    Optional<Integer> lotExpiryAlertDays(UUID tenantId);

    static BigDecimal requireAutoLimit(Optional<BigDecimal> v) {
        BigDecimal x = v == null ? null : v.orElse(null);
        if (x == null) throw new InventoryExceptions.ValidationException("PARAM_MISSING",
                AUTO_APPROVAL_LIMIT + " nao configurado no tenant [A DEFINIR]: aprovacao automatica nao opera sem limite declarado");
        if (x.signum() < 0) throw new IllegalArgumentException(AUTO_APPROVAL_LIMIT + " nao pode ser negativo");
        return x;
    }

    static List<ApprovalBand> requireBands(Optional<List<ApprovalBand>> v) {
        List<ApprovalBand> x = v == null ? null : v.orElse(null);
        if (x == null || x.isEmpty()) throw new InventoryExceptions.ValidationException("PARAM_MISSING",
                APPROVAL_BANDS + " nao configurado no tenant [A DEFINIR]: aprovacao humana por alcada nao opera sem faixas declaradas");
        return List.copyOf(x);
    }

    static int requireRegularizationDays(Optional<Integer> v) {
        Integer x = v == null ? null : v.orElse(null);
        if (x == null) throw new InventoryExceptions.ValidationException("PARAM_MISSING",
                NEGATIVE_REGULARIZATION_DAYS + " nao configurado no tenant [A DEFINIR]: prazo de regularizacao de negativos nao opera");
        if (x <= 0) throw new IllegalArgumentException(NEGATIVE_REGULARIZATION_DAYS + " deve ser positivo");
        return x;
    }

    static int requireLotExpiryAlertDays(Optional<Integer> v) {
        Integer x = v == null ? null : v.orElse(null);
        if (x == null) throw new InventoryExceptions.ValidationException("PARAM_MISSING",
                LOT_EXPIRY_ALERT_DAYS + " nao configurado no tenant [A DEFINIR]: alerta de validade de lote nao opera");
        if (x < 0) throw new IllegalArgumentException(LOT_EXPIRY_ALERT_DAYS + " nao pode ser negativo");
        return x;
    }
}
