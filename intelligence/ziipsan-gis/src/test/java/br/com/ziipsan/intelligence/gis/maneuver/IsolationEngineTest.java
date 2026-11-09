package br.com.ziipsan.intelligence.gis.maneuver;

import br.com.ziipsan.intelligence.gis.maneuver.IsolationEngine.IsolationResult;
import br.com.ziipsan.intelligence.gis.maneuver.ManeuverModel.Target;
import org.junit.jupiter.api.Test;

import java.util.List;

import static br.com.ziipsan.intelligence.gis.maneuver.ManeuverFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Isolamento com resultado conhecido (aceite da GIS-11): válvulas a
 * fechar, válvula inoperável ATRAVESSADA, imóveis/economias afetados
 * e clientes sensíveis primeiro.
 */
class IsolationEngineTest {

    @Test
    void knownResultForArcA5() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A5));
        assertTrue(r.isolable());
        assertNull(r.failure());
        assertEquals(List.of("VA", "VB"),
                r.valvesToClose().stream().map(v -> v.code()).toList());
        assertEquals(List.of("VX"),
                r.inoperableTraversed().stream().map(v -> v.code()).toList());
        // Nós afetados ordenados pelo código do nó (ordem determinística do motor).
        assertEquals(List.of(N2, N3, N6, VX), r.affectedNodeIds());
    }

    @Test
    void knownResultAffectedContent() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A5));
        assertEquals(4, r.affectedNodeIds().size());
        assertTrue(r.affectedNodeIds().containsAll(List.of(N2, VX, N3, N6)));
        assertEquals(List.of(A4, A5, A9), r.affectedArcIds());
        // Sensível primeiro (C2: U2,U3), essencial depois (C3: U4), demais por fim (C1: U1).
        assertEquals(List.of(U2, U3, U4, U1), r.affectedUnitIds());
        assertEquals(6, r.totalEconomies());
        assertEquals(List.of("C3"), r.essentialConnections().stream().map(c -> c.code()).toList());
        // C4 (N5) está fora da área isolada.
        assertFalse(r.affectedUnitIds().contains(U5));
    }

    /**
     * Sonda D1 da revisão (GRAVE): com VA JÁ FECHADA, isolar A5
     * continua isolável fechando só VB — a válvula já fechada entra
     * no conjunto de barreiras da recomputação da área (antes o
     * componente a atravessava, alcançava a fonte e o motor devolvia
     * SOURCE_REACHED_WITHOUT_VALVE). Mesma área do resultado
     * conhecido.
     */
    @Test
    void alreadyClosedValveBarsAreaRecomputation() {
        IsolationResult r = IsolationEngine.isolate(withValveAClosed(), Target.arc(A5));
        assertTrue(r.isolable());
        assertNull(r.failure());
        assertEquals(List.of("VB"),
                r.valvesToClose().stream().map(v -> v.code()).toList());
        assertEquals(List.of("VX"),
                r.inoperableTraversed().stream().map(v -> v.code()).toList());
        assertEquals(List.of(N2, N3, N6, VX), r.affectedNodeIds());
        assertEquals(List.of(A4, A5, A9), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U1), r.affectedUnitIds());
        assertEquals(6, r.totalEconomies());
    }

    /**
     * Sonda D2 da revisão (GRAVE, silenciosa): o arco-alvo A3 tem a
     * válvula operável aberta VA na ponta node1. A área afetada é o
     * componente do arco removido (fechando VA e VB), NÃO o conjunto
     * vazio que nascia de semear o componente a partir da ponta
     * barrada — isolable=true com área vazia era o defeito.
     */
    @Test
    void targetArcWithValveAtNode1HasNonEmptyArea() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A3));
        assertTrue(r.isolable());
        assertNull(r.failure());
        assertEquals(List.of("VA", "VB"),
                r.valvesToClose().stream().map(v -> v.code()).toList());
        assertEquals(List.of("VX"),
                r.inoperableTraversed().stream().map(v -> v.code()).toList());
        assertEquals(List.of(N2, N3, N6, VX), r.affectedNodeIds());
        assertEquals(List.of(A4, A5, A9), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U1), r.affectedUnitIds());
        assertEquals(6, r.totalEconomies());
        assertEquals(List.of("C3"), r.essentialConnections().stream().map(c -> c.code()).toList());
    }

    /**
     * Controle da sonda D2: A6 tem a válvula VB na ponta node2 — a
     * área já saía correta antes da correção e continua igual
     * (a computação independe da orientação do arco-alvo).
     */
    @Test
    void targetArcWithValveAtNode2KeepsCorrectArea() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A6));
        assertTrue(r.isolable());
        assertEquals(List.of("VA", "VB"),
                r.valvesToClose().stream().map(v -> v.code()).toList());
        assertEquals(List.of(N2, N3, N6, VX), r.affectedNodeIds());
        assertEquals(List.of(A4, A5, A9), r.affectedArcIds());
        assertEquals(List.of(U2, U3, U4, U1), r.affectedUnitIds());
        assertEquals(6, r.totalEconomies());
    }

    /**
     * Sonda B3-M1 da auditoria (rodada 3, residual do D2): o arco-alvo
     * A12 tem válvula operável aberta nas DUAS pontas (VB e VC). Sem a
     * correção, as duas sementes caíam em barred e o motor devolvia
     * isolable=true com área VAZIA (nós/arcos/unidades vazios, 0
     * economias) — o mesmo modo de falha silenciosa do D2. Com a
     * correção, o trecho isolado é o próprio A12 com suas pontas:
     * fechar VB+VC isola exatamente esse arco, e a conexão C8
     * derivada dele entra na área.
     */
    @Test
    void targetArcWithValvesAtBothEndsIsolatesTargetArc() {
        IsolationResult r = IsolationEngine.isolate(withValveToValveArc(), Target.arc(A12));
        assertTrue(r.isolable());
        assertNull(r.failure());
        assertEquals(List.of("VB", "VC"),
                r.valvesToClose().stream().map(v -> v.code()).toList());
        assertTrue(r.inoperableTraversed().isEmpty());
        assertEquals(List.of(VB, VC), r.affectedNodeIds());
        assertEquals(List.of(A12), r.affectedArcIds());
        assertEquals(List.of(U8), r.affectedUnitIds());
        assertEquals(1, r.totalEconomies());
        assertTrue(r.essentialConnections().isEmpty());
    }

    /**
     * Variante da sonda B3-M1 com VB e VC JÁ FECHADAS: o isolamento
     * continua possível sem nenhuma válvula a fechar, com a mesma
     * área {VB, VC} / [A12] — a guarda vale também quando as pontas
     * barram por já estarem fechadas.
     */
    @Test
    void targetArcWithValvesAtBothEndsAlreadyClosedIsolatesTargetArc() {
        IsolationResult r = IsolationEngine.isolate(withValveToValveArcBothClosed(), Target.arc(A12));
        assertTrue(r.isolable());
        assertNull(r.failure());
        assertTrue(r.valvesToClose().isEmpty());
        assertTrue(r.inoperableTraversed().isEmpty());
        assertEquals(List.of(VB, VC), r.affectedNodeIds());
        assertEquals(List.of(A12), r.affectedArcIds());
        assertEquals(List.of(U8), r.affectedUnitIds());
        assertEquals(1, r.totalEconomies());
    }

    @Test
    void sourceReachedWithoutValveIsNotIsolable() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A1));
        assertFalse(r.isolable());
        assertEquals(IsolationEngine.Failure.SOURCE_REACHED_WITHOUT_VALVE, r.failure());
        assertTrue(r.affectedUnitIds().isEmpty());
    }

    @Test
    void valveNodeAsTargetIsRefused() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.node(VA));
        assertFalse(r.isolable());
        assertEquals(IsolationEngine.Failure.TARGET_IS_VALVE, r.failure());
    }

    @Test
    void retiredArcAsTargetIsRefused() {
        IsolationResult r = IsolationEngine.isolate(base(), Target.arc(A11));
        assertFalse(r.isolable());
        assertEquals(IsolationEngine.Failure.TARGET_NOT_IN_SERVICE, r.failure());
    }
}
