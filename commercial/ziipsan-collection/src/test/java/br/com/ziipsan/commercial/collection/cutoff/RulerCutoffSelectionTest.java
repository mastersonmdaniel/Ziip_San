package br.com.ziipsan.commercial.collection.cutoff;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;
import br.com.ziipsan.commercial.collection.dunning.ruler.DunningEngine;
import br.com.ziipsan.commercial.collection.dunning.ruler.DunningRuler;
import br.com.ziipsan.commercial.collection.dunning.scoring.CollectionScoring;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Selecao pela regua da P4.1 (CAD-100): so entra quem a regua manda
 * para o passo de corte; a adaptativa (GES-24 ligada) so ORDENA.
 * Regua-fixture: D30_MSG aos 30 dias e D60_CORTE aos 60, vencimento
 * 01/09/2026; avaliacao em 16/11/2026 (76 dias).
 */
class RulerCutoffSelectionTest {

    private static final LocalDate DUE = LocalDate.of(2026, 9, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static DunningRuler ruler() {
        var segment = new DunningRuler.Segment("TARIFF", null, null, List.of(), List.of());
        return new DunningRuler(List.of(
                new DunningRuler.Step("D30_MSG", 30, "WHATSAPP", segment, "DUNNING_D30"),
                new DunningRuler.Step("D60_CORTE", 60, "FIELD", segment, null)));
    }

    private static DunningEngine.CaseState state(UUID unit, List<String> executed,
                                                 DunningEngine.PaymentPromise promise) {
        return new DunningEngine.CaseState(unit, UUID.randomUUID(), "TARIFF", "RESIDENTIAL", null,
                DUE, executed, promise);
    }

    private static DunningParameters dunningParams(boolean scoringEnabled) {
        return DunningParameters.fromValues(Map.of(
                DunningParameters.COLLECTION_SCORING_ENABLED, Boolean.toString(scoringEnabled),
                DunningParameters.RULER, "{\"steps\":[]}"));
    }

    @Test
    @DisplayName("regua: quem ja executou a mensagem e alcancou o passo de corte e selecionado")
    void rulerSelectsCutoffStep() {
        var unit = UUID.randomUUID();
        var selected = RulerCutoffSelection.selectForCutoff(ruler(),
                List.of(state(unit, List.of("D30_MSG"), null)), TODAY, CutoffExtTestSupport.extParams());
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).unitId()).isEqualTo(unit);
        assertThat(selected.get(0).rulerStepCode()).isEqualTo("D60_CORTE");
    }

    @Test
    @DisplayName("regua: passo devido ainda e a mensagem — nao seleciona para corte; promessa vigente suspende")
    void rulerDoesNotSelectEarlyOrSuspended() {
        var early = UUID.randomUUID();
        var selectedEarly = RulerCutoffSelection.selectForCutoff(ruler(),
                List.of(state(early, List.of(), null)), TODAY, CutoffExtTestSupport.extParams());
        assertThat(selectedEarly).isEmpty();
        var promised = UUID.randomUUID();
        var promise = new DunningEngine.PaymentPromise(LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 20), false);
        var selectedPromised = RulerCutoffSelection.selectForCutoff(ruler(),
                List.of(state(promised, List.of("D30_MSG"), promise)), TODAY, CutoffExtTestSupport.extParams());
        assertThat(selectedPromised).isEmpty();
    }

    @Test
    @DisplayName("adaptativa LIGADA: ordena a fila selecionada pelo escore; desligada: ordem de entrada")
    void adaptiveOrderingWhenEnabled() {
        var low = UUID.randomUUID();
        var high = UUID.randomUUID();
        var selected = List.of(new RulerCutoffSelection.Selected(low, "D60_CORTE", false),
                new RulerCutoffSelection.Selected(high, "D60_CORTE", false));
        var contacts = List.of(new CollectionScoring.Contact(low, "LETTER", null, false),
                new CollectionScoring.Contact(high, "LETTER", null, false));
        var scores = List.of(new CollectionScoring.Score(low, 0.2, List.of("atraso recente")),
                new CollectionScoring.Score(high, 0.9, List.of("atrasos repetidos")));
        var ordered = RulerCutoffSelection.orderAdaptive(selected, contacts, scores, Set.of(),
                dunningParams(true));
        assertThat(ordered).extracting(CollectionScoring.Contact::unitId).containsExactly(high, low);
        assertThat(ordered.get(0).channel()).isEqualTo("WHATSAPP");
        var neutral = RulerCutoffSelection.orderAdaptive(selected, contacts, scores, Set.of(),
                dunningParams(false));
        assertThat(neutral).extracting(CollectionScoring.Contact::unitId).containsExactly(low, high);
    }

    @Test
    @DisplayName("D2: selecionadas {A,B} com contato so de A: erro explicito — B nao some da fila em silencio")
    void adaptiveMissingContactIsExplicitError() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var selected = List.of(new RulerCutoffSelection.Selected(a, "D60_CORTE", false),
                new RulerCutoffSelection.Selected(b, "D60_CORTE", false));
        var contacts = List.of(new CollectionScoring.Contact(a, "LETTER", null, false));
        assertThatThrownBy(() -> RulerCutoffSelection.orderAdaptive(selected, contacts,
                List.of(new CollectionScoring.Score(a, 0.5, List.of())), Set.of(), dunningParams(true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(b.toString());
    }

    @Test
    @DisplayName("ADR-0042: escore alto NAO seleciona — contato fora da selecao da regua e erro, nao entrada")
    void scoringNeverSelects() {
        var inside = UUID.randomUUID();
        var outside = UUID.randomUUID();
        var selected = List.of(new RulerCutoffSelection.Selected(inside, "D60_CORTE", false));
        var contacts = List.of(new CollectionScoring.Contact(outside, "LETTER", null, false));
        assertThatThrownBy(() -> RulerCutoffSelection.orderAdaptive(selected, contacts,
                List.of(new CollectionScoring.Score(outside, 1.0, List.of())), Set.of(), dunningParams(true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
