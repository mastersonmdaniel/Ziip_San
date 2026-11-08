package br.com.ziipsan.reporting.sinisa;

import java.math.BigDecimal;
import java.util.List;

/**
 * Secao gerencial do painel da ED-48: os valores lidos da ED-32 pela
 * {@link ManagerialCostPort}. Campo {@code null} = a fonte nao tinha o
 * dado, e o nome do campo aparece em {@code pendingFields} — pendencia
 * nomeada no painel, nunca zero nem valor presumido.
 */
public record ManagerialPanelSection(BigDecimal costPerCubicMeter, BigDecimal costPerConnection,
                                     List<ManagerialCostPort.CategoryMargin> marginsByCategory,
                                     BigDecimal serviceOrderTotalCost,
                                     List<String> pendingFields) {

    public ManagerialPanelSection {
        marginsByCategory = marginsByCategory == null ? List.of() : List.copyOf(marginsByCategory);
        pendingFields = pendingFields == null ? List.of() : List.copyOf(pendingFields);
    }

    /** Verdadeiro quando a ED-32 entregou todos os campos pedidos. */
    public boolean complete() {
        return pendingFields.isEmpty();
    }
}
