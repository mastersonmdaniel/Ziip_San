package br.com.ziipsan.core.certification.assetmgmt55001;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano de investimento e manutenção POR ATIVO (ED-72): cada linha amarra
 * uma ação planejada ao seu custo APROPRIADO lido das fontes — custo da OS
 * da ED-36 ({@code WorkOrderCost.Breakdown}, material + mão de obra + frota
 * + terceiros já apropriados pela régua da ED-32/ED-36) e custo dos
 * materiais da ED-58 (quantidade × custo médio do {@code StockBalance}).
 *
 * <p>Nenhum custo é estimado aqui: a linha recebe os valores JÁ
 * apropriados pelas donas (ED-36 e ED-58) e só soma. Linha sem OS e sem
 * material (ação sem custo apropriado) precisa declarar o motivo — plano
 * de investimento não esconde ação de custo zero presumido.
 *
 * <p><b>Horizonte sem fábrica:</b> o período do plano (início/fim) é
 * declarado pelo tenant; nenhum horizonte padrão (anual, plurianual) é
 * presumido por este pacote.
 */
public record AssetInvestmentPlan(Iso55001Activation activation, UUID assetId,
                                  LocalDate periodFrom, LocalDate periodTo,
                                  List<PlanLine> lines) {

    /** Natureza da ação planejada (vocabulário do produto, não da norma). */
    public enum ActionKind {
        INVESTMENT, PREVENTIVE_MAINTENANCE, CORRECTIVE_MAINTENANCE, CONDITION_MAINTENANCE
    }

    /**
     * Uma ação do plano com o custo apropriado decomposto como lido:
     * {@code workOrderCostTotal} é o total do {@code Breakdown} da ED-36
     * para a OS de referência; {@code materialsTotal} é quantidade × custo
     * médio da ED-58. Ambos nulos = ação sem custo apropriado, que exige
     * {@code noCostReason}.
     */
    public record PlanLine(ActionKind kind, String description, String workOrderRef,
                           BigDecimal workOrderCostTotal, String materialItemCode,
                           BigDecimal materialQuantity, BigDecimal materialUnitAverageCost,
                           String noCostReason) {
        public PlanLine {
            Objects.requireNonNull(kind, "kind da linha do plano é obrigatório");
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description da ação planejada é obrigatória");
            }
            description = description.trim();
            workOrderRef = blankToNull(workOrderRef);
            materialItemCode = blankToNull(materialItemCode);
            noCostReason = blankToNull(noCostReason);
            if (workOrderCostTotal != null && workOrderCostTotal.signum() < 0) {
                throw new IllegalArgumentException("custo de OS apropriado (ED-36) negativo");
            }
            if (workOrderCostTotal != null && workOrderRef == null) {
                throw new IllegalArgumentException("custo de OS sem workOrderRef: o valor tem "
                        + "de apontar para a OS da ED-36 de onde foi apropriado");
            }
            if ((materialQuantity == null) != (materialUnitAverageCost == null)
                    || (materialQuantity == null) != (materialItemCode == null)) {
                throw new IllegalArgumentException("material da linha exige item, quantidade e "
                        + "custo médio (ED-58) juntos — ou nenhum dos três");
            }
            if (materialQuantity != null && materialQuantity.signum() <= 0) {
                throw new IllegalArgumentException("quantidade de material deve ser positiva");
            }
            if (materialUnitAverageCost != null && materialUnitAverageCost.signum() < 0) {
                throw new IllegalArgumentException("custo médio do material (ED-58) negativo");
            }
            if (workOrderCostTotal == null && materialItemCode == null && noCostReason == null) {
                throw new IllegalArgumentException("linha sem custo apropriado da ED-36 nem da "
                        + "ED-58 exige noCostReason: custo zero nunca é presumido");
            }
        }

        /** Custo dos materiais da linha: quantidade × custo médio LIDO da ED-58. */
        public BigDecimal materialsTotal() {
            if (materialQuantity == null) return null;
            return materialQuantity.multiply(materialUnitAverageCost)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        /** Total apropriado da linha (soma do que foi lido; nunca estimativa). */
        public BigDecimal lineTotal() {
            BigDecimal total = BigDecimal.ZERO;
            if (workOrderCostTotal != null) total = total.add(workOrderCostTotal);
            BigDecimal m = materialsTotal();
            if (m != null) total = total.add(m);
            return total.setScale(2, RoundingMode.HALF_UP);
        }

        private static String blankToNull(String v) {
            return v == null || v.isBlank() ? null : v.trim();
        }
    }

    public AssetInvestmentPlan {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        Objects.requireNonNull(assetId, "assetId do plano por ativo é obrigatório");
        Objects.requireNonNull(periodFrom, "periodFrom (horizonte declarado pelo tenant) "
                + "é obrigatório — nenhum horizonte de fábrica é presumido");
        Objects.requireNonNull(periodTo, "periodTo (horizonte declarado pelo tenant) "
                + "é obrigatório");
        if (periodTo.isBefore(periodFrom)) {
            throw new IllegalArgumentException("periodTo anterior a periodFrom");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("plano por ativo sem nenhuma ação não é plano");
        }
        lines = List.copyOf(lines);
    }

    /** Total do plano: soma dos totais apropriados das linhas (ED-36 + ED-58). */
    public BigDecimal planTotal() {
        return lines.stream().map(PlanLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
