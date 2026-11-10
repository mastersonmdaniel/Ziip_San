package br.com.ziipsan.core.slaos;

import java.util.List;
import java.util.Objects;

/**
 * Configuracao de SLA por tipo de OS (II-4, sobre a P1.31).
 *
 * <p>Os dois prazos (interno e do regulador) sao CONFIGURACAO por tipo:
 * os valores reais sao {@code [A DEFINIR]} (pendencia P-II4-PRAZOS —
 * contrato de concessao/norma do regulador do tenant) e por isso nascem
 * anulaveis: {@code null} significa "prazo ainda nao definido pela fonte",
 * NUNCA um prazo inventado por esta classe. A avaliacao de um prazo nao
 * configurado devolve {@code NOT_CONFIGURED} (ver {@link DualDeadlineClock}),
 * no mesmo espirito do {@code OrderMonitoring} da ED-36, que recusa
 * monitorar OS sem prazo da P1.31 em vez de inventar um.
 *
 * <p>O limiar de risco e os degraus de escalonamento tambem sao
 * configuracao explicita: nenhum valor de fabrica e assumido aqui.
 */
public record TypeSlaConfig(
        String typeCode,
        Integer internalDeadlineHours,
        Integer regulatorDeadlineHours,
        int riskThresholdPercent,
        List<Integer> escalationThresholdsPercent) {

    public TypeSlaConfig {
        Objects.requireNonNull(typeCode, "tipo de OS e obrigatorio");
        if (typeCode.isBlank()) throw new IllegalArgumentException("tipo de OS nao pode ser vazio");
        if (internalDeadlineHours != null && internalDeadlineHours <= 0)
            throw new IllegalArgumentException("prazo interno deve ser positivo quando definido");
        if (regulatorDeadlineHours != null && regulatorDeadlineHours <= 0)
            throw new IllegalArgumentException("prazo do regulador deve ser positivo quando definido");
        if (riskThresholdPercent < 1 || riskThresholdPercent > 99)
            throw new IllegalArgumentException("limiar de risco deve estar entre 1 e 99 (% do prazo)");
        Objects.requireNonNull(escalationThresholdsPercent, "degraus de escalonamento sao obrigatorios (lista pode ser vazia)");
        escalationThresholdsPercent = List.copyOf(escalationThresholdsPercent);
        for (int i = 0; i < escalationThresholdsPercent.size(); i++) {
            int v = escalationThresholdsPercent.get(i);
            if (v <= 0) throw new IllegalArgumentException("degrau de escalonamento deve ser positivo: " + v);
            if (i > 0 && v <= escalationThresholdsPercent.get(i - 1))
                throw new IllegalArgumentException("degraus de escalonamento devem ser estritamente crescentes");
        }
    }

    public boolean internalConfigured() { return internalDeadlineHours != null; }
    public boolean regulatorConfigured() { return regulatorDeadlineHours != null; }
}
