package br.com.ziipsan.operations.metering.critique;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §4.1 da estimativa: cada regra dispara/nao dispara em fronteira (igualdade incluida). */
class CritiqueEngineTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final YearMonth REF = YearMonth.of(2026, 10);
    private final CritiqueEngine engine = new CritiqueEngine();
    private final CritiqueConfig config = CritiqueConfig.proofDefaults();

    private ReadingSubmission submission(Integer current, Integer previous, Integer measured, List<Integer> history) {
        return new ReadingSubmission(UNIT, REF, current, previous, measured, history);
    }

    private boolean fired(CritiqueResult result, CritiqueRuleKind kind) {
        return result.findings().stream().anyMatch(f -> f.kind() == kind);
    }

    @Test
    void leituraNormalNaoDisparaNada() {
        CritiqueResult r = engine.evaluate(submission(1120, 1000, 120, List.of(110, 120, 130)), config);
        assertTrue(r.valid(), "consumo 120 com media 120 e faixa [0,200] nao dispara");
    }

    @Test
    void leituraEmFaltaDisparaMissingEApenasMissingDasSemLimiar() {
        CritiqueResult r = engine.evaluate(submission(null, 1000, null, List.of(110, 120)), config);
        assertTrue(fired(r, CritiqueRuleKind.MISSING_READING));
        assertFalse(fired(r, CritiqueRuleKind.ZERO_CONSUMPTION));
        assertFalse(fired(r, CritiqueRuleKind.DEVIATION_ABOVE_HISTORY));
    }

    @Test
    void leituraRegressivaDisparaNegativa() {
        CritiqueResult r = engine.evaluate(submission(900, 1000, -100, List.of(110)), config);
        assertTrue(fired(r, CritiqueRuleKind.NEGATIVE_OR_REGRESSING_READING));
    }

    @Test
    void leituraIgualAnteriorNaoENegativa() {
        CritiqueResult r = engine.evaluate(submission(1000, 1000, 0, List.of(0)), config);
        assertFalse(fired(r, CritiqueRuleKind.NEGATIVE_OR_REGRESSING_READING));
        assertTrue(fired(r, CritiqueRuleKind.ZERO_CONSUMPTION), "consumo zero dispara a sua regra propria");
    }

    @Test
    void consumoZeroDisparaZeroEConsumoUmNao() {
        assertTrue(fired(engine.evaluate(submission(1000, 1000, 0, List.of(10)), config),
                CritiqueRuleKind.ZERO_CONSUMPTION));
        assertFalse(fired(engine.evaluate(submission(1001, 1000, 1, List.of(10)), config),
                CritiqueRuleKind.ZERO_CONSUMPTION));
    }

    @Test
    void desvioExactamenteNoLimiarNaoDispara() {
        // historico media 100; medido 150 = desvio +50% exacto; limiar de prova 50 -> NAO dispara (estrito)
        CritiqueResult r = engine.evaluate(submission(1150, 1000, 150, List.of(100, 100)), config);
        assertFalse(fired(r, CritiqueRuleKind.DEVIATION_ABOVE_HISTORY));
    }

    @Test
    void desvioAcimaDoLimiarDispara() {
        CritiqueResult r = engine.evaluate(submission(1151, 1000, 151, List.of(100, 100)), config);
        assertTrue(fired(r, CritiqueRuleKind.DEVIATION_ABOVE_HISTORY));
    }

    @Test
    void desvioAbaixoExactamenteNoLimiarNaoDisparaEAcimaDispara() {
        // medido 50 face a media 100 = -50% exacto -> nao dispara; 49 = -51% -> dispara
        assertFalse(fired(engine.evaluate(submission(1050, 1000, 50, List.of(100, 100)), config),
                CritiqueRuleKind.DEVIATION_BELOW_HISTORY));
        assertTrue(fired(engine.evaluate(submission(1049, 1000, 49, List.of(100, 100)), config),
                CritiqueRuleKind.DEVIATION_BELOW_HISTORY));
    }

    @Test
    void faixaExtremosNaoDisparamEForaDispara() {
        // faixa de prova [0, 200]: 200 nao dispara, 201 dispara
        assertFalse(fired(engine.evaluate(submission(1200, 1000, 200, List.of(190)), config),
                CritiqueRuleKind.OUTSIDE_ABSOLUTE_BAND));
        assertTrue(fired(engine.evaluate(submission(1201, 1000, 201, List.of(190)), config),
                CritiqueRuleKind.OUTSIDE_ABSOLUTE_BAND));
    }

    @Test
    void regraDesligadaNaoDispara() {
        CritiqueConfig off = new CritiqueConfig(List.of(
                CritiqueRule.simple("Z", CritiqueRuleKind.ZERO_CONSUMPTION).disabled()));
        assertTrue(engine.evaluate(submission(1000, 1000, 0, List.of(10)), off).valid());
    }

    @Test
    void desvioSemHistoricoOuMediaZeroNaoDispara() {
        assertFalse(fired(engine.evaluate(submission(1500, 1000, 500, List.of()), config),
                CritiqueRuleKind.DEVIATION_ABOVE_HISTORY));
        assertFalse(fired(engine.evaluate(submission(1500, 1000, 500, List.of(0, 0)), config),
                CritiqueRuleKind.DEVIATION_ABOVE_HISTORY));
    }

    @Test
    void determinismoMesmaEntradaMesmoResultado() {
        ReadingSubmission s = submission(1151, 1000, 151, List.of(100, 100));
        assertEquals(engine.evaluate(s, config), engine.evaluate(s, config));
    }

    @Test
    void regraDeDesvioSemLimiarERecusada() {
        assertThrows(IllegalArgumentException.class,
                () -> new CritiqueRule("X", CritiqueRuleKind.DEVIATION_ABOVE_HISTORY, true, null, null, null));
    }

    @Test
    void regraDeFaixaInvalidaERecusada() {
        assertThrows(IllegalArgumentException.class, () -> CritiqueRule.band("X", 50, 10));
    }
}
