package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;
import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** POC-45 (programar e desprogramar) + janelas sched.customer_windows + sugestao explicavel + portao pgRouting. */
class Poc45SchedulePlannerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 10, 0); // sabado
    private static final LocalDateTime MON_0800 = LocalDateTime.of(2026, 11, 9, 8, 0); // segunda
    private static final LocalDateTime MON_1200 = LocalDateTime.of(2026, 11, 9, 12, 0);
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000d0");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final SchedParameters.CustomerWindow MORNING = new SchedParameters.CustomerWindow(
            "MANHA", Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(12, 0), 2);

    @Test
    void scheduleInsideWindowAndConfirm() {
        OrderScheduling.Booking b = OrderScheduling.schedule(ServiceOrderStatus.OPEN, ORDER, MORNING,
                LocalDateTime.of(2026, 11, 9, 9, 0), 0, ACTOR, NOW);
        assertFalse(b.customerConfirmed());
        assertTrue(OrderScheduling.confirm(b).customerConfirmed());
        assertThrows(IllegalStateException.class, () -> OrderScheduling.confirm(OrderScheduling.confirm(b)));
    }

    @Test
    void scheduleOutsideWindowFullOrTerminalRefused() {
        assertThrows(IllegalArgumentException.class, () -> OrderScheduling.schedule(ServiceOrderStatus.OPEN, ORDER, MORNING,
                LocalDateTime.of(2026, 11, 9, 13, 0), 0, ACTOR, NOW)); // fora da janela
        assertThrows(IllegalStateException.class, () -> OrderScheduling.schedule(ServiceOrderStatus.OPEN, ORDER, MORNING,
                LocalDateTime.of(2026, 11, 9, 9, 0), 2, ACTOR, NOW)); // lotada (capacidade 2)
        assertThrows(IllegalStateException.class, () -> OrderScheduling.schedule(ServiceOrderStatus.CLOSED_EXECUTED, ORDER, MORNING,
                LocalDateTime.of(2026, 11, 9, 9, 0), 0, ACTOR, NOW)); // terminal nao programa
    }

    @Test
    void rescheduleNeedsReasonAndKeepsTrail() {
        OrderScheduling.Booking b = OrderScheduling.schedule(ServiceOrderStatus.OPEN, ORDER, MORNING,
                LocalDateTime.of(2026, 11, 9, 9, 0), 0, ACTOR, NOW);
        assertThrows(IllegalArgumentException.class, () -> OrderScheduling.reschedule(b, MORNING,
                LocalDateTime.of(2026, 11, 9, 10, 0), 0, " ", ACTOR, NOW));
        // Reagendamento por OUTRA pessoa, em instante posterior (prova da correcao D7).
        UUID rescheduler = UUID.fromString("00000000-0000-0000-0000-0000000000d9");
        LocalDateTime rescheduledAt = LocalDateTime.of(2026, 11, 7, 11, 30);
        OrderScheduling.RescheduleResult r = OrderScheduling.reschedule(b, MORNING,
                LocalDateTime.of(2026, 11, 9, 10, 0), 0, "usuario pediu mais tarde", rescheduler, rescheduledAt);
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), r.trail().previousStart());
        assertEquals(LocalDateTime.of(2026, 11, 9, 10, 0), r.booking().start());
        assertFalse(r.booking().customerConfirmed()); // nova janela volta a exigir confirmacao
        // D7: o Booking novo carrega autor/instante do REAGENDAMENTO, nao os originais.
        assertEquals(rescheduler, r.booking().scheduledBy());
        assertEquals(rescheduledAt, r.booking().scheduledAt());
        assertEquals(rescheduler, r.trail().actorId());
        assertEquals(rescheduledAt, r.trail().at());
    }

    @Test
    void unscheduleFollowsEnginePath() {
        assertFalse(ServiceOrderStatus.SCHEDULED.canMoveTo(ServiceOrderStatus.OPEN)); // sem aresta direta no motor
        assertEquals(List.of(ServiceOrderStatus.WAITING_DEPENDENCY, ServiceOrderStatus.OPEN),
                OrderScheduling.unschedulePath(ServiceOrderStatus.SCHEDULED));
        assertThrows(IllegalStateException.class, () -> OrderScheduling.unschedulePath(ServiceOrderStatus.OPEN));
    }

    /** Restricoes duras configuradas padrao dos testes (correcao D1: propose recebe o conjunto). */
    private static final Set<SchedParameters.HardConstraint> HARD = Set.of(
            SchedParameters.HardConstraint.SAFETY_QUALIFICATION,
            SchedParameters.HardConstraint.ORDER_DEADLINE,
            SchedParameters.HardConstraint.CUSTOMER_WINDOW);
    /** Prazo DISTINTO do fim da janela (14:00 x 12:00) — ver nota da fixture no helper cand. */
    private static final LocalDateTime DUE_1400 = LocalDateTime.of(2026, 11, 9, 14, 0);

    private static SchedulePlanner.Candidate cand(String suffix, ServiceOrderEnums.Priority p, String region,
                                                  boolean qualified, LocalDateTime windowStart) {
        return cand(suffix, p, region, qualified, windowStart, DUE_1400);
    }

    private static SchedulePlanner.Candidate cand(String suffix, ServiceOrderEnums.Priority p, String region,
                                                  boolean qualified, LocalDateTime windowStart, LocalDateTime dueAt) {
        // FIXTURE CORRIGIDA (D2): a versao original usava dueAt == windowEnd (12:00) para
        // TODAS as candidatas, o que mascarava o defeito D2 (prazo conferido contra o
        // inicio da janela). Agora dueAt != windowEnd por desenho; nenhuma assercao foi
        // afrouxada — as expectativas de inclusao/exclusao seguem as mesmas.
        return new SchedulePlanner.Candidate(UUID.fromString("00000000-0000-0000-0000-0000000000" + suffix),
                p, dueAt, windowStart, MON_1200, 60, region, qualified, true);
    }

    @Test
    void plannerHardFiltersAndExplainableProposal() {
        UUID crewA = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID crewB = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        SchedulePlanner.Proposal proposal = SchedulePlanner.propose(List.of(
                cand("f1", ServiceOrderEnums.Priority.URGENT, "NORTE", true, MON_0800),
                cand("f2", ServiceOrderEnums.Priority.NORMAL, "NORTE", true, MON_0800),
                cand("f3", ServiceOrderEnums.Priority.NORMAL, "SUL", true, MON_0800),
                cand("f4", ServiceOrderEnums.Priority.NORMAL, "SUL", false, MON_0800), // sem habilitacao SST
                cand("f5", ServiceOrderEnums.Priority.NORMAL, "SUL", true, LocalDateTime.of(2026, 11, 9, 11, 30),
                        LocalDateTime.of(2026, 11, 9, 12, 15)) // 60min a partir de 11:30 estoura o prazo 12:15 (e a janela)
        ), List.of(crewA, crewB), Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        assertEquals(3, proposal.ranked().size());
        assertEquals(2, proposal.excluded().size());
        assertEquals(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), proposal.ranked().get(0).orderId()); // urgente no topo
        assertEquals(crewA, proposal.ranked().get(0).crewId());
        assertEquals(crewB, proposal.ranked().get(1).crewId()); // equilibrio de carga
        assertFalse(proposal.ranked().get(0).reasons().isEmpty()); // explicavel
        assertFalse(proposal.confirmed()); // nasce nao confirmada
        assertTrue(proposal.confirm(ACTOR).confirmed()); // so vale confirmada por pessoa
        assertTrue(proposal.excluded().stream().anyMatch(e -> e.reasons().toString().contains("SST")));
        assertTrue(proposal.excluded().stream().anyMatch(e -> e.reasons().toString().contains("ORDER_DEADLINE")));
    }

    @Test
    void regionGroupingWithinSamePriority() {
        UUID crew = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
        SchedulePlanner.Proposal proposal = SchedulePlanner.propose(List.of(
                new SchedulePlanner.Candidate(UUID.fromString("00000000-0000-0000-0000-0000000000f6"),
                        ServiceOrderEnums.Priority.NORMAL, LocalDateTime.of(2026, 11, 9, 10, 0), MON_0800, MON_1200, 30, "NORTE", true, true),
                new SchedulePlanner.Candidate(UUID.fromString("00000000-0000-0000-0000-0000000000f7"),
                        ServiceOrderEnums.Priority.NORMAL, LocalDateTime.of(2026, 11, 9, 9, 0), MON_0800, MON_1200, 30, "SUL", true, true),
                new SchedulePlanner.Candidate(UUID.fromString("00000000-0000-0000-0000-0000000000f8"),
                        ServiceOrderEnums.Priority.NORMAL, LocalDateTime.of(2026, 11, 9, 11, 0), MON_0800, MON_1200, 30, "NORTE", true, true)
        ), List.of(crew), Map.of(), new SchedParameters.SoftWeights(1.0, 0.5), HARD);
        // prazo ordenaria SUL(9h), NORTE(10h), NORTE(11h); o agrupamento mantem as duas NORTE juntas no fim da banda
        assertEquals(List.of("00000000-0000-0000-0000-0000000000f7", "00000000-0000-0000-0000-0000000000f6", "00000000-0000-0000-0000-0000000000f8"),
                proposal.ranked().stream().map(r -> r.orderId().toString()).toList());
        assertTrue(proposal.ranked().get(2).reasons().toString().contains("agrupada"));
    }

    // ---------- Correcao D1: pesos brandas quantitativos + duras configuradas ----------

    @Test
    void loadBalanceZeroIgnoresCrewLoad() {
        UUID crewA = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID crewB = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        SchedulePlanner.Candidate only = cand("a1", ServiceOrderEnums.Priority.NORMAL, "NORTE", true, MON_0800);
        // Peso de carga 0: nem 600 min de diferenca mudam a escolha — e trocar as cargas
        // de equipe tambem nao (a escolha e a primeira da lista, por desempate deterministico).
        SchedulePlanner.Proposal p1 = SchedulePlanner.propose(List.of(only), List.of(crewA, crewB),
                Map.of(crewA, 600, crewB, 0), new SchedParameters.SoftWeights(1.0, 0.0), HARD);
        SchedulePlanner.Proposal p2 = SchedulePlanner.propose(List.of(only), List.of(crewA, crewB),
                Map.of(crewA, 0, crewB, 600), new SchedParameters.SoftWeights(1.0, 0.0), HARD);
        assertEquals(crewA, p1.ranked().get(0).crewId());
        assertEquals(crewA, p2.ranked().get(0).crewId());
        // Controle: com peso de carga positivo, a MESMA diferenca de carga muda a escolha.
        SchedulePlanner.Proposal p3 = SchedulePlanner.propose(List.of(only), List.of(crewA, crewB),
                Map.of(crewA, 600, crewB, 0), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        assertEquals(crewB, p3.ranked().get(0).crewId());
    }

    @Test
    void highLoadBalanceBeatsRegionGroupingForCrew() {
        UUID crewA = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID crewB = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        LocalDateTime we1800 = LocalDateTime.of(2026, 11, 9, 18, 0);
        SchedulePlanner.Candidate first = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000a2"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 16, 0), MON_0800, we1800, 120, "NORTE", true, true);
        SchedulePlanner.Candidate second = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000a3"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 17, 0), MON_0800, we1800, 60, "NORTE", true, true);
        // Peso de carga baixo (0.1): o bonus de continuidade regional (30 min-equiv) vence
        // a carga de 120 min da equipe A — a segunda OS NORTE fica com A.
        SchedulePlanner.Proposal low = SchedulePlanner.propose(List.of(first, second), List.of(crewA, crewB),
                Map.of(), new SchedParameters.SoftWeights(1.0, 0.1), HARD);
        assertEquals(crewA, low.ranked().get(0).crewId());
        assertEquals(crewA, low.ranked().get(1).crewId());
        // Peso de carga alto (5.0): a equipe menos carregada (B) vence MESMO contra o agrupamento.
        SchedulePlanner.Proposal high = SchedulePlanner.propose(List.of(first, second), List.of(crewA, crewB),
                Map.of(), new SchedParameters.SoftWeights(1.0, 5.0), HARD);
        assertEquals(crewA, high.ranked().get(0).crewId());
        assertEquals(crewB, high.ranked().get(1).crewId());
    }

    @Test
    void regionGroupingWeightScalesOrderingInfluence() {
        UUID crew = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
        SchedulePlanner.Candidate n1 = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000b1"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 9, 0), MON_0800, MON_1200, 30, "NORTE", true, true);
        SchedulePlanner.Candidate s1 = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000b2"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 10, 0), MON_0800, MON_1200, 30, "SUL", true, true);
        SchedulePlanner.Candidate n2 = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000b3"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 11, 0), MON_0800, MON_1200, 30, "NORTE", true, true);
        // Peso pequeno (0.2 = desconto de 6 min): nao puxa a NORTE das 11:00 — ordem pura por prazo.
        SchedulePlanner.Proposal small = SchedulePlanner.propose(List.of(n1, s1, n2), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(0.2, 1.0), HARD);
        assertEquals(List.of(n1.orderId(), s1.orderId(), n2.orderId()),
                small.ranked().stream().map(SchedulePlanner.Ranked::orderId).toList());
        // Peso grande (5.0 = desconto de 150 min): puxa a NORTE das 11:00 para junto da primeira.
        SchedulePlanner.Proposal large = SchedulePlanner.propose(List.of(n1, s1, n2), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(5.0, 1.0), HARD);
        assertEquals(List.of(n1.orderId(), n2.orderId(), s1.orderId()),
                large.ranked().stream().map(SchedulePlanner.Ranked::orderId).toList());
    }

    @Test
    void configuredHardConstraintsFilterCandidates() {
        UUID crew = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
        Set<SchedParameters.HardConstraint> withEquipment = Set.of(
                SchedParameters.HardConstraint.SAFETY_QUALIFICATION,
                SchedParameters.HardConstraint.ORDER_DEADLINE,
                SchedParameters.HardConstraint.CUSTOMER_WINDOW,
                SchedParameters.HardConstraint.EQUIPMENT_WHEN_ED59);
        SchedulePlanner.Candidate noEquipment = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000c1"), ServiceOrderEnums.Priority.NORMAL,
                DUE_1400, MON_0800, MON_1200, 60, "NORTE", true, false);
        // EQUIPMENT_WHEN_ED59 CONFIGURADA: candidata sem equipamento e filtrada.
        SchedulePlanner.Proposal filtered = SchedulePlanner.propose(List.of(noEquipment), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), withEquipment);
        assertEquals(0, filtered.ranked().size());
        assertEquals(1, filtered.excluded().size());
        assertTrue(filtered.excluded().get(0).reasons().toString().contains("EQUIPMENT_WHEN_ED59"));
        // SEM a restricao no conjunto: a mesma candidata entra (a dura configurada e quem filtra).
        SchedulePlanner.Proposal allowed = SchedulePlanner.propose(List.of(noEquipment), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        assertEquals(1, allowed.ranked().size());
        // CUSTOMER_WINDOW configurada filtra duracao que nao cabe na janela; sem ela, nao filtra.
        SchedulePlanner.Candidate tooLongForWindow = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000c2"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 18, 0), LocalDateTime.of(2026, 11, 9, 11, 30), MON_1200, 60, "NORTE", true, true);
        SchedulePlanner.Proposal windowFiltered = SchedulePlanner.propose(List.of(tooLongForWindow), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        assertEquals(0, windowFiltered.ranked().size());
        assertTrue(windowFiltered.excluded().get(0).reasons().toString().contains("CUSTOMER_WINDOW"));
        Set<SchedParameters.HardConstraint> noWindow = Set.of(
                SchedParameters.HardConstraint.SAFETY_QUALIFICATION,
                SchedParameters.HardConstraint.ORDER_DEADLINE);
        SchedulePlanner.Proposal windowAllowed = SchedulePlanner.propose(List.of(tooLongForWindow), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), noWindow);
        assertEquals(1, windowAllowed.ranked().size());
        // E o conjunto nao pode afrouxar as normativas: remover ORDER_DEADLINE e recusado.
        assertThrows(IllegalArgumentException.class, () -> SchedulePlanner.propose(List.of(noEquipment), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0),
                Set.of(SchedParameters.HardConstraint.SAFETY_QUALIFICATION)));
    }

    // ---------- Correcao D2: prazo contra o inicio EFETIVO (apos a fila) ----------

    @Test
    void deadlineCheckedAgainstEffectiveStartAfterQueue() {
        UUID crew = UUID.fromString("00000000-0000-0000-0000-0000000000e9");
        SchedulePlanner.Candidate first = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000d5"), ServiceOrderEnums.Priority.URGENT,
                LocalDateTime.of(2026, 11, 9, 18, 0), MON_0800, MON_1200, 120, "NORTE", true, true);
        // Janela 08:00–12:00 CABE (60 min a partir das 08:00 terminaria 09:00, antes do prazo
        // 10:30) — mas atras da primeira OS o inicio EFETIVO e 10:00 e o fim, 11:00 > 10:30.
        SchedulePlanner.Candidate queued = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000d6"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 10, 30), MON_0800, MON_1200, 60, "SUL", true, true);
        SchedulePlanner.Proposal proposal = SchedulePlanner.propose(List.of(first, queued), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        // Nunca nasce afirmando atendimento: e EXCLUIDA por ORDER_DEADLINE contra o inicio efetivo.
        assertEquals(1, proposal.ranked().size());
        assertEquals(first.orderId(), proposal.ranked().get(0).orderId());
        assertEquals(1, proposal.excluded().size());
        assertEquals(queued.orderId(), proposal.excluded().get(0).orderId());
        assertTrue(proposal.excluded().get(0).reasons().toString().contains("ORDER_DEADLINE"));
        assertTrue(proposal.excluded().get(0).reasons().toString().contains("inicio efetivo"));
        // Controle: sozinha (sem fila), a mesma OS e proposta com fim 09:00 e prazo atendido de verdade.
        SchedulePlanner.Proposal alone = SchedulePlanner.propose(List.of(queued), List.of(crew),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        assertEquals(1, alone.ranked().size());
        assertEquals(MON_0800, alone.ranked().get(0).proposedStart());
        assertTrue(alone.ranked().get(0).reasons().toString().contains("atendido"));
    }

    @Test
    void deadlineMetByFreeCrewWhenQueuedCrewWouldMiss() {
        UUID crewA = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID crewB = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        SchedulePlanner.Candidate first = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000d7"), ServiceOrderEnums.Priority.URGENT,
                LocalDateTime.of(2026, 11, 9, 18, 0), MON_0800, MON_1200, 120, "NORTE", true, true);
        SchedulePlanner.Candidate second = new SchedulePlanner.Candidate(
                UUID.fromString("00000000-0000-0000-0000-0000000000d8"), ServiceOrderEnums.Priority.NORMAL,
                LocalDateTime.of(2026, 11, 9, 10, 30), MON_0800, MON_1200, 60, "SUL", true, true);
        SchedulePlanner.Proposal proposal = SchedulePlanner.propose(List.of(first, second), List.of(crewA, crewB),
                Map.of(), new SchedParameters.SoftWeights(1.0, 1.0), HARD);
        // Na equipe A o fim efetivo seria 11:00 (apos o prazo 10:30); na B, livre, 09:00. Vai para a B.
        assertEquals(2, proposal.ranked().size());
        assertEquals(0, proposal.excluded().size());
        assertEquals(crewB, proposal.ranked().get(1).crewId());
        assertEquals(MON_0800, proposal.ranked().get(1).proposedStart());
    }

    @Test
    void routingSolverBlockedWithoutAdr() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                RoutingGate.requireSolver(new RoutingGate.AdrStatus(false, null), null));
        assertTrue(e.getMessage().contains("ADR-0010"));
        assertTrue(e.getMessage().contains("29/10/2026"));
        // mesmo com ADR aceito declarado, sem solver de codigo aberto ligado continua bloqueado (fiacao pendente)
        assertThrows(IllegalStateException.class, () ->
                RoutingGate.requireSolver(new RoutingGate.AdrStatus(true, "ADR-XXXX"), null));
    }
}
