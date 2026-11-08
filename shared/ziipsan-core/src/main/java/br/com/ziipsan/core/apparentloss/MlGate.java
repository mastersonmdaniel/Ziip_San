package br.com.ziipsan.core.apparentloss;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Portao do aprendizado de maquina (ADR-0042, aceite da ED-95):
 * "modelo de aprendizado de maquina so depois que as regras forem
 * medidas". Esta classe NAO implementa modelo nenhum e NAO habilita
 * nada: ela apenas AVALIA a prontidao e nomeia as lacunas. A decisao
 * de construir um modelo e ato posterior, sob o ADR-0042, com
 * referencia registrada — e mesmo "pronto para decisao" aqui
 * significa apenas que a conversa pode comecar, nao que ML exista.
 *
 * <p>Condicoes avaliadas (todas declaradas, nenhum numero inventado):
 * <ul>
 *   <li>cada regra HABILITADA em {@code fraud.rules} tem pelo menos
 *       uma inspecao concluida no relatorio de acerto (regra medida);
 *       o aceite nao fixa tamanho minimo de amostra — fixar um seria
 *       numero inventado, por isso a lacuna e por regra sem medicao
 *       alguma e o tamanho da amostra medido vai exposto no relatorio
 *       que acompanha o pedido de decisao;</li>
 *   <li>existe referencia registrada da decisao sob o ADR-0042
 *       (documento/ata do tenant) — sem ela, bloqueado.</li>
 * </ul>
 */
public final class MlGate {

    private MlGate() {
    }

    public enum Status {
        /** Regras nao medidas e/ou sem decisao ADR-0042: ML bloqueado. */
        BLOCKED,
        /** Medicao completa e decisao referenciada: elegivel a decisao de desenho sob o ADR-0042 (nenhum modelo criado). */
        READY_FOR_ADR_DECISION
    }

    public enum Gap {
        RULE_NOT_MEASURED, MISSING_ADR_DECISION_REFERENCE, NO_RULE_ENABLED
    }

    public record GapDetail(Gap gap, FraudRules.Rule rule) {
    }

    public record Assessment(Status status, List<GapDetail> gaps) {
        public boolean blocked() {
            return status == Status.BLOCKED;
        }
    }

    public static Assessment assess(Set<FraudRules.Rule> enabledRules,
                                    InspectionAccuracy.Report report,
                                    String adrDecisionReference) {
        List<GapDetail> gaps = new ArrayList<>();
        if (enabledRules == null || enabledRules.isEmpty()) {
            gaps.add(new GapDetail(Gap.NO_RULE_ENABLED, null));
        } else if (report != null) {
            for (FraudRules.Rule rule : enabledRules) {
                boolean measured = report.outcome(rule).map(InspectionAccuracy.RuleOutcome::measured).orElse(false);
                if (!measured) gaps.add(new GapDetail(Gap.RULE_NOT_MEASURED, rule));
            }
        } else {
            for (FraudRules.Rule rule : enabledRules) gaps.add(new GapDetail(Gap.RULE_NOT_MEASURED, rule));
        }
        if (adrDecisionReference == null || adrDecisionReference.isBlank())
            gaps.add(new GapDetail(Gap.MISSING_ADR_DECISION_REFERENCE, null));
        return new Assessment(gaps.isEmpty() ? Status.READY_FOR_ADR_DECISION : Status.BLOCKED,
                List.copyOf(gaps));
    }

    /** Regras habilitadas a partir dos limiares resolvidos do tenant. */
    public static Set<FraudRules.Rule> enabledRules(LossParameters.FraudRuleThresholds t) {
        java.util.EnumSet<FraudRules.Rule> out = java.util.EnumSet.noneOf(FraudRules.Rule.class);
        if (t.zeroConsumptionEnabled()) out.add(FraudRules.Rule.ZERO_CONSUMPTION_ACTIVE);
        if (t.abruptDropEnabled()) out.add(FraudRules.Rule.ABRUPT_DROP);
        if (t.perEconomyEnabled()) out.add(FraudRules.Rule.PER_ECONOMY);
        if (t.perAreaEnabled()) out.add(FraudRules.Rule.PER_AREA);
        if (t.stalledMeterEnabled()) out.add(FraudRules.Rule.STALLED_METER);
        if (t.undersizedMeterEnabled()) out.add(FraudRules.Rule.UNDERSIZED_METER);
        if (t.meterReplacementEnabled()) out.add(FraudRules.Rule.METER_REPLACEMENT_ANOMALY);
        if (t.repeatedReadingEnabled()) out.add(FraudRules.Rule.REPEATED_READING);
        return out;
    }
}
