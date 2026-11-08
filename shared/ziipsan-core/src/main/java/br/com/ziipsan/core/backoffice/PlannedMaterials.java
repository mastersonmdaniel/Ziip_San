package br.com.ziipsan.core.backoffice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Materiais da OS completa (ED-36): quantidade PREVISTA e USADA por item.
 * Esta entrega registra previsto x usado e a divergencia — a ligacao com a
 * requisicao com alcada, a reserva e a baixa no estoque e da ED-58
 * (ADR-0032) e NAO acontece aqui: nenhum movimento de estoque e gerado.
 *
 * <p>Cobranca do material ao usuario segue so.material_charge_policy
 * (ED-36/OS-20): NEVER = nada e cobrado por esta regra; PRICE_TABLE_ERI =
 * o valor dos itens USADOS precificado pelo chamador na tabela da ED-34
 * (PriceTable) — esta classe nao conhece preco, como a P1.31 manda.
 */
public final class PlannedMaterials {

    public record Line(String materialCode, BigDecimal plannedQty, BigDecimal usedQty) {
        public Line {
            if (materialCode == null || materialCode.isBlank()) throw new IllegalArgumentException("codigo do material e obrigatorio");
            Objects.requireNonNull(plannedQty, "quantidade prevista e obrigatoria");
            if (plannedQty.signum() <= 0) throw new IllegalArgumentException("quantidade prevista deve ser positiva: " + materialCode);
            if (usedQty != null && usedQty.signum() < 0) throw new IllegalArgumentException("quantidade usada nao pode ser negativa: " + materialCode);
        }
        public boolean recorded() { return usedQty != null; }
        public boolean overused() { return usedQty != null && usedQty.compareTo(plannedQty) > 0; }
    }

    private final Map<String, Line> byCode = new LinkedHashMap<>();

    public PlannedMaterials(List<Line> planned) {
        if (planned == null || planned.isEmpty()) throw new IllegalArgumentException("OS com materiais exige ao menos uma linha prevista");
        for (Line l : planned) {
            Objects.requireNonNull(l, "linha de material nula");
            if (l.recorded()) throw new IllegalArgumentException("previsto nao nasce com usado registrado: " + l.materialCode());
            if (byCode.put(l.materialCode(), l) != null) throw new IllegalArgumentException("material repetido no previsto: " + l.materialCode());
        }
    }

    /** Registra o usado de um item previsto; item fora do previsto e recusado (previsto e o teto de conferencia). */
    public void recordUsed(String materialCode, BigDecimal usedQty) {
        Line l = byCode.get(materialCode);
        if (l == null) throw new IllegalArgumentException("material usado fora do previsto da OS: " + materialCode
                + " (incluir no previsto antes; requisicao/estoque sao da ED-58)");
        Objects.requireNonNull(usedQty, "quantidade usada e obrigatoria");
        byCode.put(materialCode, new Line(l.materialCode(), l.plannedQty(), usedQty));
    }

    public List<Line> lines() { return List.copyOf(byCode.values()); }

    /** Linhas com usado acima do previsto — divergencia que o encerramento precisa enxergar, nao esconder. */
    public List<Line> overusedLines() { return byCode.values().stream().filter(Line::overused).toList(); }

    /** Linhas previstas sem usado registrado (encerramento incompleto se a OS foi executada). */
    public List<Line> unrecordedLines() { return byCode.values().stream().filter(l -> !l.recorded()).toList(); }

    public BigDecimal plannedTotalQty() { return byCode.values().stream().map(Line::plannedQty).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public BigDecimal usedTotalQty() {
        return byCode.values().stream().filter(Line::recorded).map(Line::usedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Valor cobravel do material pela politica do tenant; o preco vem da ED-34 pelo chamador. */
    public static BigDecimal chargeAmount(SchedParameters.MaterialChargePolicy policy, BigDecimal usedAmountFromPriceTable) {
        Objects.requireNonNull(policy, "so.material_charge_policy e obrigatoria");
        return switch (policy) {
            case NEVER -> BigDecimal.ZERO.setScale(2);
            case PRICE_TABLE_ERI -> Objects.requireNonNull(usedAmountFromPriceTable,
                    "politica PRICE_TABLE_ERI exige o valor do usado precificado na tabela da ED-34");
        };
    }

    public static List<Line> snapshot(List<Line> lines) { return new ArrayList<>(lines); }
}
