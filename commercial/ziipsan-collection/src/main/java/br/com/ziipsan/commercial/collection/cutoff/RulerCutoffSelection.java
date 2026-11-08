package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.commercial.collection.dunning.ruler.DunningEngine;
import br.com.ziipsan.commercial.collection.dunning.ruler.DunningRuler;
import br.com.ziipsan.commercial.collection.dunning.scoring.CollectionScoring;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Selecao para corte pela REGUA DE COBRANCA da P4.1 (CAD-100): a
 * unidade so entra na fila de corte quando o proximo passo devido da
 * regua, decidido por {@link DunningEngine#nextStep}, e o passo de
 * corte declarado pelo tenant ({@code cut.ruler_cutoff_step_code}).
 * Promessa de pagamento vigente suspende (a regua da P4.1 decide);
 * promessa quebrada reinicia a regua (idem) — esta classe nao
 * reimplementa a regua.
 *
 * <p><b>Regua adaptativa (GES-24):</b> com
 * {@code ai.collection_scoring_enabled} LIGADO, o escore da P4.1 apenas
 * ORDENA a fila ja selecionada e adapta canal/horario/oferta, via
 * {@link CollectionScoring#orderContacts} — sob o ADR-0042 ele NUNCA
 * decide corte: unidade fora da selecao da regua nao entra por escore
 * alto, e unidade selecionada nao sai por escore baixo. Com o
 * interruptor desligado (padrao), a ordem e a de entrada. Quem se opoe
 * ao escore (LGPD, art. 20) conserva a posicao, pela regra da P4.1.
 */
public final class RulerCutoffSelection {

    private RulerCutoffSelection() {
    }

    /** Unidade selecionada pela regua para o passo de corte. */
    public record Selected(UUID unitId, String rulerStepCode, boolean rulerRestartedByBrokenPromise) {
    }

    /**
     * Seleciona, na ordem da regua, as unidades cujo proximo passo
     * devido e o passo de corte. Estados com promessa vigente ou sem
     * passo devido ficam fora (a decisao e da P4.1).
     */
    public static List<Selected> selectForCutoff(DunningRuler ruler, List<DunningEngine.CaseState> states,
                                                 LocalDate today, CutoffExtParameters extParams) {
        if (ruler == null || states == null || today == null || extParams == null) {
            throw new IllegalArgumentException("regua, estados, data e parametros sao obrigatorios");
        }
        String cutoffStepCode = extParams.rulerCutoffStepCode();
        List<Selected> selected = new ArrayList<>();
        for (DunningEngine.CaseState state : states) {
            DunningEngine.Decision decision = DunningEngine.nextStep(ruler, state, today);
            if (decision.kind() == DunningEngine.DecisionKind.EXECUTE_STEP
                    && decision.step() != null
                    && cutoffStepCode.equals(decision.step().code())) {
                selected.add(new Selected(state.unitId(), decision.step().code(),
                        decision.rulerRestartedByBrokenPromise()));
            }
        }
        return List.copyOf(selected);
    }

    /**
     * Ordenacao adaptativa da fila JA selecionada: delega a
     * {@link CollectionScoring#orderContacts} da P4.1. A lista de
     * contatos deve cobrir exatamente as unidades selecionadas — contato
     * de unidade nao selecionada e erro do chamador, nao selecao nova.
     */
    public static List<CollectionScoring.Contact> orderAdaptive(List<Selected> selected,
                                                                  List<CollectionScoring.Contact> contacts,
                                                                  List<CollectionScoring.Score> scores,
                                                                  Set<UUID> opposedUnitIds,
                                                                  DunningParameters dunningParams) {
        if (selected == null || contacts == null || dunningParams == null) {
            throw new IllegalArgumentException("selecionadas, contatos e parametros da P4.1 sao obrigatorios");
        }
        Set<UUID> selectedIds = selected.stream().map(Selected::unitId)
                .collect(java.util.stream.Collectors.toSet());
        for (CollectionScoring.Contact contact : contacts) {
            if (!selectedIds.contains(contact.unitId())) {
                throw new IllegalArgumentException(
                        "contato de unidade fora da selecao da regua: o escore nao seleciona para corte (ADR-0042)");
            }
        }
        // Cobertura EXATA, como o javadoc promete (correcao D2 da revisao
        // cruzada): unidade selecionada SEM contato e erro explicito do
        // chamador — ela nao pode sumir da fila ordenada em silencio.
        Set<UUID> contactIds = contacts.stream().map(CollectionScoring.Contact::unitId)
                .collect(java.util.stream.Collectors.toSet());
        List<UUID> missing = selectedIds.stream().filter(id -> !contactIds.contains(id)).sorted().toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "contatos nao cobrem exatamente as unidades selecionadas; sem contato: " + missing);
        }
        return CollectionScoring.orderContacts(contacts,
                scores == null ? List.of() : scores, opposedUnitIds, dunningParams);
    }
}
