package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.sac.AssignmentChange;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Parametros da ED-36 + redistribuicao pela ED-35 + ligacao nova pelo fluxo da ED-34. */
class SchedParametersAndLinksTest {

    @Test
    void missingParametersAreExplicitErrors() {
        assertThrows(MissingParameterException.class, () -> SchedParameters.customerWindows(null));
        assertThrows(MissingParameterException.class, () -> SchedParameters.customerWindows(List.of()));
        assertThrows(MissingParameterException.class, () -> SchedParameters.retroactiveCloseDays(null));
        assertThrows(MissingParameterException.class, () -> SchedParameters.materialChargePolicy(null));
        assertThrows(MissingParameterException.class, () -> SchedParameters.softWeights(null, 1.0));
        assertThrows(MissingParameterException.class, () -> SchedParameters.replanOnUrgency(null));
        assertEquals(3, SchedParameters.retroactiveCloseDays(3));
        assertEquals(SchedParameters.MaterialChargePolicy.NEVER, SchedParameters.materialChargePolicy("NEVER"));
    }

    @Test
    void hardConstraintsCannotDropSafetyOrDeadline() {
        assertThrows(IllegalArgumentException.class, () -> SchedParameters.hardConstraints(
                EnumSet.of(SchedParameters.HardConstraint.ORDER_DEADLINE)));
        assertThrows(IllegalArgumentException.class, () -> SchedParameters.hardConstraints(
                EnumSet.of(SchedParameters.HardConstraint.SAFETY_QUALIFICATION)));
        assertEquals(3, SchedParameters.hardConstraints(EnumSet.of(
                SchedParameters.HardConstraint.SAFETY_QUALIFICATION,
                SchedParameters.HardConstraint.ORDER_DEADLINE,
                SchedParameters.HardConstraint.CUSTOMER_WINDOW)).size());
    }

    @Test
    void windowValidation() {
        assertThrows(IllegalArgumentException.class, () -> new SchedParameters.CustomerWindow(
                "X", Set.of(DayOfWeek.MONDAY), LocalTime.of(12, 0), LocalTime.of(8, 0), 1)); // fim antes do inicio
        assertThrows(IllegalArgumentException.class, () -> new SchedParameters.CustomerWindow(
                "X", Set.of(), LocalTime.of(8, 0), LocalTime.of(12, 0), 1)); // sem dia
        SchedParameters.CustomerWindow w = new SchedParameters.CustomerWindow(
                "MANHA", Set.of(DayOfWeek.MONDAY), LocalTime.of(8, 0), LocalTime.of(12, 0), 2);
        assertTrue(w.covers(DayOfWeek.MONDAY, LocalTime.of(9, 0)));
        assertFalse(w.covers(DayOfWeek.MONDAY, LocalTime.of(12, 0))); // fim exclusivo
        assertFalse(w.covers(DayOfWeek.TUESDAY, LocalTime.of(9, 0)));
    }

    @Test
    void redistributionDelegatesToEd35Rule() {
        UUID order = UUID.randomUUID(), from = UUID.randomUUID(), to = UUID.randomUUID(), by = UUID.randomUUID();
        LocalDateTime when = LocalDateTime.of(2026, 11, 9, 9, 0);
        AssignmentChange.TrailEntry entry = OrderRedistribution.redistributeOrder(order, from, to, "equipe da regiao", by, when);
        assertEquals(AssignmentChange.TargetType.SERVICE_ORDER, entry.targetType());
        assertEquals(order, entry.targetId());
        assertThrows(IllegalArgumentException.class, () -> OrderRedistribution.redistributeOrder(order, from, from, "x", by, when));
        assertThrows(IllegalArgumentException.class, () -> OrderRedistribution.redistributeOrder(order, from, to, " ", by, when));
    }

    @Test
    void newConnectionExecutionFollowsEd34Flow() {
        List<String> stepsNoSewer = List.of("REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE",
                "INSPECTION", "EXECUTION", "ACTIVATION", "CONTRACT_DELIVERY", "CUSTOMER_LINK", "FIRST_READING");
        NewConnectionExecution exec = new NewConnectionExecution(
                (sewer, req, i, e) -> stepsNoSewer, false, false, null, null);
        assertFalse(exec.hasSewerStep());
        exec.assertCompletable("EXECUTION", Set.of("INSPECTION"));
        assertThrows(IllegalStateException.class, () -> exec.assertCompletable("EXECUTION", Set.of()));
        assertThrows(IllegalStateException.class, () -> exec.assertCompletable("ACTIVATION", Set.of("INSPECTION")));
        exec.assertCompletable("ACTIVATION", Set.of("EXECUTION"));

        List<String> stepsSewer = List.of("REQUEST", "DOCUMENTS", "FEASIBILITY", "QUOTE", "PAYMENT_GUIDE",
                "INSPECTION", "EXECUTION", "SEWER_CONNECTION", "ACTIVATION", "CONTRACT_DELIVERY", "CUSTOMER_LINK", "FIRST_READING");
        NewConnectionExecution execSewer = new NewConnectionExecution(
                (sewer, req, i, e) -> stepsSewer, true, true, null, null);
        assertTrue(execSewer.hasSewerStep());
        // com esgoto simultaneo, a ativacao exige EXECUTION e SEWER_CONNECTION
        assertThrows(IllegalStateException.class, () -> execSewer.assertCompletable("ACTIVATION", Set.of("EXECUTION")));
        execSewer.assertCompletable("ACTIVATION", Set.of("EXECUTION", "SEWER_CONNECTION"));
    }
}
