package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.RouteReordering.RouteStop;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RouteReorderingTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID X = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private static List<RouteStop> route() {
        return List.of(new RouteStop(A, 1), new RouteStop(B, 2), new RouteStop(C, 3));
    }

    @Test
    @DisplayName("reordenação aplica a permutação e renumera de 1 a N sem buracos")
    void reorderAppliesPermutation() {
        List<RouteStop> result = RouteReordering.reorder(route(), List.of(C, A, B));
        assertThat(result).containsExactly(new RouteStop(C, 1), new RouteStop(A, 2), new RouteStop(B, 3));
    }

    @Test
    @DisplayName("reordenação recusa unidade estranha, duplicada ou faltante — nunca cria nem some parada")
    void reorderRejectsNonPermutation() {
        assertThatThrownBy(() -> RouteReordering.reorder(route(), List.of(C, A, X)))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> RouteReordering.reorder(route(), List.of(C, A, A)))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> RouteReordering.reorder(route(), List.of(C, A)))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("mover uma parada desloca as demais e mantém a permutação exata")
    void moveShiftsOthers() {
        List<RouteStop> result = RouteReordering.move(route(), A, 3);
        assertThat(result).containsExactly(new RouteStop(B, 1), new RouteStop(C, 2), new RouteStop(A, 3));
        assertThatThrownBy(() -> RouteReordering.move(route(), A, 4))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> RouteReordering.move(route(), X, 1))
                .isInstanceOf(MeteringInputException.class);
    }
}
