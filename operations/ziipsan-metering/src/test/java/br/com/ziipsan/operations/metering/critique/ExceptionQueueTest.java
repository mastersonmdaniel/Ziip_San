package br.com.ziipsan.operations.metering.critique;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** §4.2 da estimativa: transicoes validas e recusa tipificada das invalidas; ordenacao deterministica. */
class ExceptionQueueTest {

    private static RuleFinding finding(String code) {
        return new RuleFinding(code, CritiqueRuleKind.ZERO_CONSUMPTION, BigDecimal.ZERO, null, "fixture");
    }

    private static CritiqueException exception(UUID unit, YearMonth ref, String code) {
        return new CritiqueException(unit, ref, List.of(finding(code)));
    }

    @Test
    void transicoesValidasAPartirDePendente() {
        UUID u = UUID.randomUUID();
        CritiqueException a = exception(u, YearMonth.of(2026, 10), "R1");
        a.accept();
        assertEquals(ExceptionStatus.ACCEPTED, a.status());

        CritiqueException b = exception(u, YearMonth.of(2026, 10), "R1");
        b.correct(1234);
        assertEquals(ExceptionStatus.CORRECTED, b.status());
        assertEquals(1234, b.correctedReading());

        CritiqueException c = exception(u, YearMonth.of(2026, 10), "R1");
        c.requestRereading();
        assertEquals(ExceptionStatus.REREADING_REQUESTED, c.status());
        c.correct(1200);
        assertEquals(ExceptionStatus.CORRECTED, c.status());
    }

    @Test
    void estadosTerminaisRecusamQualquerTransicao() {
        UUID u = UUID.randomUUID();
        CritiqueException accepted = exception(u, YearMonth.of(2026, 10), "R1");
        accepted.accept();
        assertThrows(CritiqueException.InvalidTransitionException.class, accepted::accept);
        assertThrows(CritiqueException.InvalidTransitionException.class, accepted::requestRereading);
        assertThrows(CritiqueException.InvalidTransitionException.class, () -> accepted.correct(1));

        CritiqueException corrected = exception(u, YearMonth.of(2026, 10), "R1");
        corrected.correct(10);
        assertThrows(CritiqueException.InvalidTransitionException.class, corrected::accept);
    }

    @Test
    void releituraSoAPartirDePendente() {
        CritiqueException e = exception(UUID.randomUUID(), YearMonth.of(2026, 10), "R1");
        e.requestRereading();
        assertThrows(CritiqueException.InvalidTransitionException.class, e::requestRereading);
    }

    @Test
    void correccaoComLeituraNegativaERecusada() {
        CritiqueException e = exception(UUID.randomUUID(), YearMonth.of(2026, 10), "R1");
        assertThrows(IllegalArgumentException.class, () -> e.correct(-1));
        assertEquals(ExceptionStatus.PENDING, e.status(), "recusa nao muda o estado");
    }

    @Test
    void resultadoValidoNaoEntraNaFila() {
        ExceptionQueue queue = new ExceptionQueue();
        CritiqueResult valid = new CritiqueResult(UUID.randomUUID(), YearMonth.of(2026, 10), List.of());
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(valid));
    }

    @Test
    void ordenacaoDeterministicaDaFila() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ExceptionQueue queue = new ExceptionQueue();
        queue.enqueue(new CritiqueResult(u2, YearMonth.of(2026, 11), List.of(finding("RB"))));
        queue.enqueue(new CritiqueResult(u2, YearMonth.of(2026, 10), List.of(finding("RA"))));
        queue.enqueue(new CritiqueResult(u1, YearMonth.of(2026, 10), List.of(finding("RZ"))));
        List<CritiqueException> ordered = queue.ordered();
        assertEquals(u1, ordered.get(0).unitId());
        assertEquals(u2, ordered.get(1).unitId());
        assertEquals(YearMonth.of(2026, 11), ordered.get(2).reference());
        assertEquals(ordered, queue.ordered(), "ordenacao estavel entre chamadas");
    }
}
