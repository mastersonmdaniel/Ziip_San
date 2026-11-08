package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.rules.MeteringInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContingencyBulletinTest {

    private static final UUID ROUTE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PENDING = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @Test
    @DisplayName("boletim fecha os contadores e registra a contingência com motivo")
    void bulletinClosesAndDeclaresContingency() {
        ContingencyBulletin bulletin = new ContingencyBulletin(
                ROUTE, DAY, 10, 7, 2, List.of(PENDING), "Rua interditada por obra da prefeitura");
        assertThat(bulletin.isContingency()).isTrue();
        assertThat(bulletin.pending()).isEqualTo(1);
    }

    @Test
    @DisplayName("boletim com pendência ou impedimento sem motivo é recusado")
    void bulletinWithoutReasonRejected() {
        assertThatThrownBy(() -> new ContingencyBulletin(ROUTE, DAY, 10, 7, 2, List.of(PENDING), " "))
                .isInstanceOf(MeteringInputException.class);
        assertThatThrownBy(() -> new ContingencyBulletin(ROUTE, DAY, 10, 8, 2, List.of(), null))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("boletim cujos contadores não fecham com o planejado é recusado")
    void bulletinWithOpenCountsRejected() {
        assertThatThrownBy(() -> new ContingencyBulletin(ROUTE, DAY, 10, 7, 1, List.of(PENDING), "motivo"))
                .isInstanceOf(MeteringInputException.class);
    }

    @Test
    @DisplayName("unidade pendente duplicada é recusada: a mesma pendência não conta duas vezes")
    void duplicatedPendingUnitRejected() {
        // planned/read/impeded fechariam se a duplicata contasse 2x — a recusa é pela duplicidade
        assertThatThrownBy(() -> new ContingencyBulletin(
                ROUTE, DAY, 4, 2, 0, List.of(PENDING, PENDING), "motivo"))
                .isInstanceOf(MeteringInputException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    @DisplayName("rota concluída sem impedimento nem pendência não é contingência e não exige motivo")
    void completedRouteIsNotContingency() {
        ContingencyBulletin bulletin = new ContingencyBulletin(ROUTE, DAY, 10, 10, 0, List.of(), null);
        assertThat(bulletin.isContingency()).isFalse();
    }
}
