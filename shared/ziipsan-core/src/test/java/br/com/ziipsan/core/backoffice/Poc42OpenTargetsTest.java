package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderStatus;
import br.com.ziipsan.core.serviceorder.TargetType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** POC-42 (abrir): alvos multiplos e tipados da P1.31, incl. equipamento/componente da P1.35; desdobramento. */
class Poc42OpenTargetsTest {

    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID EQUIP = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID COMP = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final Set<TargetType> ACCEPTED = Set.of(TargetType.CONSUMER_UNIT, TargetType.EQUIPMENT,
            TargetType.COMPONENT, TargetType.METER, TargetType.LOCATION_ONLY);

    @Test
    void multipleTypedTargetsIncludingAssetClasses() {
        List<OrderTargets.Target> targets = OrderTargets.validate(List.of(
                OrderTargets.Target.entity(TargetType.CONSUMER_UNIT, UNIT),
                OrderTargets.Target.entity(TargetType.EQUIPMENT, EQUIP),
                OrderTargets.Target.entity(TargetType.COMPONENT, COMP)), ACCEPTED);
        assertEquals(3, targets.size());
        assertEquals(2, OrderTargets.assetTargets(targets).size());
    }

    @Test
    void locationOnlyRulesMirrorV91() {
        assertThrows(IllegalArgumentException.class, () -> new OrderTargets.Target(TargetType.LOCATION_ONLY, UNIT, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderTargets.Target(TargetType.CONSUMER_UNIT, UNIT, "Rua X"));
        assertEquals(TargetType.LOCATION_ONLY, OrderTargets.Target.location("Rua X, 10").type());
    }

    @Test
    void duplicateAndUnacceptedTargetsRefused() {
        assertThrows(IllegalArgumentException.class, () -> OrderTargets.validate(List.of(
                OrderTargets.Target.entity(TargetType.EQUIPMENT, EQUIP),
                OrderTargets.Target.entity(TargetType.EQUIPMENT, EQUIP)), ACCEPTED));
        assertThrows(IllegalArgumentException.class, () -> OrderTargets.validate(List.of(
                OrderTargets.Target.entity(TargetType.ZONE, UNIT)), ACCEPTED));
        assertThrows(IllegalArgumentException.class, () -> OrderTargets.validate(List.of(), ACCEPTED));
    }

    @Test
    void splitPartitionsAllTargets() {
        OrderTargets.Target t1 = OrderTargets.Target.entity(TargetType.CONSUMER_UNIT, UNIT);
        OrderTargets.Target t2 = OrderTargets.Target.entity(TargetType.EQUIPMENT, EQUIP);
        OrderTargets.Target t3 = OrderTargets.Target.entity(TargetType.COMPONENT, COMP);
        OrderSplitAndRework.SplitPlan plan = OrderSplitAndRework.split(UUID.randomUUID(), ServiceOrderStatus.OPEN,
                List.of(t1, t2, t3), List.of(List.of(t1), List.of(t2, t3)));
        assertEquals(2, plan.childTargets().size());
        assertThrows(IllegalArgumentException.class, () -> OrderSplitAndRework.split(UUID.randomUUID(), ServiceOrderStatus.OPEN,
                List.of(t1, t2, t3), List.of(List.of(t1), List.of(t2)))); // falta t3
        assertThrows(IllegalArgumentException.class, () -> OrderSplitAndRework.split(UUID.randomUUID(), ServiceOrderStatus.OPEN,
                List.of(t1, t2), List.of(List.of(t1, t2), List.of(t2)))); // t2 em duas filhas
        assertThrows(IllegalStateException.class, () -> OrderSplitAndRework.split(UUID.randomUUID(), ServiceOrderStatus.CLOSED_EXECUTED,
                List.of(t1, t2), List.of(List.of(t1), List.of(t2)))); // encerrada nao desdobra
    }
}
