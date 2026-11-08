package br.com.ziipsan.intelligence.gis.expandedimport;

import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.MatchMethod;
import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.MatchResult;
import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.MatchStatus;
import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.ReconciliationOutcome;
import br.com.ziipsan.intelligence.gis.expandedimport.GisUnitMatching.UnitCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** GIS-18 (ED-55): casamento SG-1349 e conciliacao SG-1356. */
class GisUnitMatchingTest {

    private static final List<UnitCandidate> CANDIDATES = List.of(
            new UnitCandidate("u1", "MAT-001", "Rua das Flores, 123 - Centro"),
            new UnitCandidate("u2", "MAT-002", "Rua das Flores, 124 - Centro"),
            new UnitCandidate("u3", "MAT-003", "Avenida Central, 900"));

    @Test
    @DisplayName("matricula exata unica casa; duplicada vira AMBIGUOUS")
    void matchByRegistration() {
        MatchResult r = GisUnitMatching.match("MAT-002", null, CANDIDATES);
        assertEquals(MatchStatus.MATCHED, r.status());
        assertEquals(MatchMethod.REGISTRATION, r.method());
        assertEquals("u2", r.candidateUnitId());

        List<UnitCandidate> dup = List.of(
                new UnitCandidate("x1", "MAT-009", "Rua A, 1"),
                new UnitCandidate("x2", "MAT-009", "Rua B, 2"));
        assertEquals(MatchStatus.AMBIGUOUS,
                GisUnitMatching.match("MAT-009", null, dup).status());
    }

    @Test
    @DisplayName("endereco normalizado (acento/caixa/pontuacao) casa exato")
    void matchByNormalizedAddress() {
        MatchResult r = GisUnitMatching.match(null,
                "RUA DAS FLORES 123 CENTRO", CANDIDATES);
        assertEquals(MatchStatus.MATCHED, r.status());
        assertEquals(MatchMethod.ADDRESS_EXACT, r.method());
        assertEquals("u1", r.candidateUnitId());
        assertEquals("RUA DAS FLORES 123 CENTRO",
                GisUnitMatching.normalizeAddress("Rua das Flôres, 123 - Centro"));
    }

    @Test
    @DisplayName("prefixo nao casa sozinho: vai para revisao com o candidato")
    void prefixGoesToReview() {
        MatchResult r = GisUnitMatching.match(null,
                "Avenida Central", CANDIDATES);
        assertEquals(MatchStatus.PENDING_REVIEW, r.status());
        assertEquals(MatchMethod.ADDRESS_PREFIX, r.method());
        assertEquals("u3", r.candidateUnitId());
    }

    @Test
    @DisplayName("sem candidato: NONE para a fila, nunca um chute")
    void noCandidateGoesToQueue() {
        MatchResult r = GisUnitMatching.match("MAT-999",
                "Travessa Inexistente, 1", CANDIDATES);
        assertEquals(MatchStatus.PENDING_REVIEW, r.status());
        assertEquals(MatchMethod.NONE, r.method());
        assertEquals(null, r.candidateUnitId());
    }

    @Test
    @DisplayName("AMBIGUOUS -> MATCHED: a decisao informa o candidato escolhido entre os avaliados")
    void ambiguousCanBeMatchedByHumanDecision() {
        // O item ambiguo nasce sem candidato (match() devolve null);
        // a decisao humana escolhe um dos avaliados e ele vira o
        // candidato do item (correcao D4).
        List<String> evaluated = List.of("x1", "x2");
        assertEquals("x2", GisUnitMatching.resolveDecisionCandidate(
                null, "x2", evaluated));
        // Escolhido fora do conjunto avaliado: recusado.
        assertThrows(IllegalArgumentException.class, () ->
                GisUnitMatching.resolveDecisionCandidate(null, "x9", evaluated));
        // Sem escolhido e sem candidato registrado: MATCHED recusado.
        assertThrows(IllegalArgumentException.class, () ->
                GisUnitMatching.resolveDecisionCandidate(null, null, evaluated));
        assertThrows(IllegalArgumentException.class, () ->
                GisUnitMatching.resolveDecisionCandidate(null, " ", evaluated));
    }

    @Test
    @DisplayName("PENDING_REVIEW -> MATCHED confirma o candidato registrado; AMBIGUOUS -> rejeitado nao exige candidato")
    void pendingReviewDecisionPaths() {
        // Prefixo (PENDING_REVIEW com candidato unico): a decisao
        // MATCHED sem informar candidato confirma o registrado.
        assertEquals("u3", GisUnitMatching.resolveDecisionCandidate(
                "u3", null, List.of("u1", "u2", "u3")));
        // E pode trocar por outro do conjunto avaliado.
        assertEquals("u1", GisUnitMatching.resolveDecisionCandidate(
                "u3", "u1", List.of("u1", "u2", "u3")));
        // AMBIGUOUS -> REJECTED nao passa pelo resolvedor (o servico
        // so o chama para MATCHED): aqui fica provado que rejeitar
        // nao depende de candidato — o item ambiguo de
        // matchByRegistration (candidato null) e decidivel como
        // REJECTED sem nenhum candidato envolvido.
        MatchResult ambiguous = GisUnitMatching.match("MAT-009", null, List.of(
                new UnitCandidate("x1", "MAT-009", "Rua A, 1"),
                new UnitCandidate("x2", "MAT-009", "Rua B, 2")));
        assertEquals(MatchStatus.AMBIGUOUS, ambiguous.status());
        assertEquals(null, ambiguous.candidateUnitId());
    }

    @Test
    @DisplayName("conciliacao: so na base, so no desenho, geometria, atributo, acordo")
    void reconciliationOutcomes() {
        assertEquals(ReconciliationOutcome.ONLY_IN_BASE,
                GisUnitMatching.reconcile(true, false, "POINT(0 0)", null, "a", null));
        assertEquals(ReconciliationOutcome.ONLY_IN_DRAWING,
                GisUnitMatching.reconcile(false, true, null, "POINT(0 0)", null, "a"));
        assertEquals(ReconciliationOutcome.GEOMETRY_DIVERGENT,
                GisUnitMatching.reconcile(true, true,
                        "POINT(0 0)", "POINT(1 1)", "a", "a"));
        assertEquals(ReconciliationOutcome.ATTRIBUTE_DIVERGENT,
                GisUnitMatching.reconcile(true, true,
                        "POINT(0 0)", "point(0  0)", "a", "b"));
        assertEquals(ReconciliationOutcome.AGREED,
                GisUnitMatching.reconcile(true, true,
                        "POINT(0 0)", "POINT(0 0)", "a", "a"));
    }
}
