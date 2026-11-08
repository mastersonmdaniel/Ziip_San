package br.com.ziipsan.core.apparentloss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Relatorio de acerto das inspecoes (aceite da ED-95): taxa de
 * confirmacao POR REGRA — quantas inspecoes decididas/executadas a
 * partir das suspeitas de cada regra confirmaram a irregularidade.
 * E a medicao que o ADR-0042 exige antes de qualquer conversa sobre
 * modelo de aprendizado (ver {@link MlGate}): sem inspecao concluida,
 * a taxa da regra e INDETERMINADA (nunca zero fingido).
 *
 * <p>Os desfechos chegam da fiscalizacao (ED-24): a ED-95 nao executa
 * inspecao — registra o desfecho declarado (confirmou / nao
 * confirmou) e calcula a taxa.
 */
public final class InspectionAccuracy {

    private InspectionAccuracy() {
    }

    /** Desfecho agregado de inspecoes de uma regra (contagens declaradas da fiscalizacao). */
    public record RuleOutcome(FraudRules.Rule rule, int inspected, int confirmed) {
        public RuleOutcome {
            Objects.requireNonNull(rule, "regra do desfecho e obrigatoria");
            if (inspected < 0 || confirmed < 0)
                throw new IllegalArgumentException("contagens de inspecao nao podem ser negativas");
            if (confirmed > inspected)
                throw new IllegalArgumentException("confirmadas (" + confirmed
                        + ") nao podem exceder inspecionadas (" + inspected + ")");
        }

        /** Taxa de confirmacao; vazia quando nada foi inspecionado (indeterminada). */
        public Optional<BigDecimal> confirmationRate() {
            if (inspected == 0) return Optional.empty();
            return Optional.of(BigDecimal.valueOf(confirmed)
                    .divide(BigDecimal.valueOf(inspected), 4, RoundingMode.HALF_UP));
        }

        public boolean measured() {
            return inspected > 0;
        }
    }

    /** Relatorio deterministico: por regra e no total. */
    public record Report(Map<FraudRules.Rule, RuleOutcome> byRule, int totalInspected, int totalConfirmed) {

        public Report {
            byRule = Map.copyOf(Objects.requireNonNull(byRule, "desfechos por regra sao obrigatorios"));
        }

        public Optional<BigDecimal> overallRate() {
            if (totalInspected == 0) return Optional.empty();
            return Optional.of(BigDecimal.valueOf(totalConfirmed)
                    .divide(BigDecimal.valueOf(totalInspected), 4, RoundingMode.HALF_UP));
        }

        public Optional<RuleOutcome> outcome(FraudRules.Rule rule) {
            return Optional.ofNullable(byRule.get(rule));
        }

        /** Regras com pelo menos uma inspecao concluida (medidas, no sentido do ADR-0042). */
        public List<FraudRules.Rule> measuredRules() {
            return byRule.values().stream().filter(RuleOutcome::measured).map(RuleOutcome::rule).toList();
        }
    }

    public static Report build(List<RuleOutcome> outcomes) {
        Objects.requireNonNull(outcomes, "desfechos sao obrigatorios");
        Map<FraudRules.Rule, RuleOutcome> byRule = new LinkedHashMap<>();
        int inspected = 0;
        int confirmed = 0;
        for (RuleOutcome o : outcomes) {
            if (byRule.put(o.rule(), o) != null)
                throw new IllegalArgumentException("desfecho duplicado para a regra " + o.rule());
            inspected += o.inspected();
            confirmed += o.confirmed();
        }
        return new Report(byRule, inspected, confirmed);
    }
}
