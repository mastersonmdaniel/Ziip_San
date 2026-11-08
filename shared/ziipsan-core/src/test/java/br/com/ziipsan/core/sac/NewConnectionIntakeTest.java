package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import java.util.List;
import java.util.UUID;
class NewConnectionIntakeTest {
    static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    /** Duble da porta: devolve a cadeia documentada da ED-34 (a ligacao real e
     *  provada no teste do adaptador, no modulo comercial). */
    static final class FakePort implements NewConnectionWorkflowPort {
        boolean sewerSeen; boolean requiredSeen;
        public List<String> chainedStepCodes(boolean sewerAvailable, boolean requiresSewerWhenAvailable,
                                             Integer inspectionDeadlineHours, Integer executionDeadlineHours) {
            sewerSeen = sewerAvailable; requiredSeen = requiresSewerWhenAvailable;
            return sewerAvailable && requiresSewerWhenAvailable
                ? List.of("REQUEST","DOCUMENTS","FEASIBILITY","QUOTE","PAYMENT_GUIDE","INSPECTION",
                          "EXECUTION","SEWER_CONNECTION","ACTIVATION","CONTRACT_DELIVERY","CUSTOMER_LINK","FIRST_READING")
                : List.of("REQUEST","DOCUMENTS","FEASIBILITY","QUOTE","PAYMENT_GUIDE","INSPECTION",
                          "EXECUTION","ACTIVATION","CONTRACT_DELIVERY","CUSTOMER_LINK","FIRST_READING");
        }
    }
    @Test @DisplayName("G2: ligacao nova abre sobre o fluxo encadeado da ED-34 via porta, sem reimplementar etapas")
    void opensThroughEd34Port() {
        var fake = new FakePort();
        var intake = NewConnectionIntake.open(P1, ProtocolChannel.PORTAL, fake, true, true, 24, 72);
        assertThat(intake.protocolId()).isEqualTo(P1);
        assertThat(intake.channel()).isEqualTo(ProtocolChannel.PORTAL);
        assertThat(intake.stepCodes()).containsExactly("REQUEST","DOCUMENTS","FEASIBILITY","QUOTE",
            "PAYMENT_GUIDE","INSPECTION","EXECUTION","SEWER_CONNECTION","ACTIVATION",
            "CONTRACT_DELIVERY","CUSTOMER_LINK","FIRST_READING");
        assertThat(fake.sewerSeen).isTrue(); assertThat(fake.requiredSeen).isTrue();
        var semEsgoto = NewConnectionIntake.open(P1, ProtocolChannel.COUNTER, fake, false, true, 24, 72);
        assertThat(semEsgoto.stepCodes()).doesNotContain("SEWER_CONNECTION");
    }
    @Test @DisplayName("G2: porta ausente ou fluxo vazio sao recusados")
    void portRequired() {
        assertThatThrownBy(() -> NewConnectionIntake.open(P1, ProtocolChannel.PORTAL, null, false, false, null, null))
            .isInstanceOf(NullPointerException.class);
        NewConnectionWorkflowPort empty = (a,b,c,d) -> List.of();
        assertThatThrownBy(() -> NewConnectionIntake.open(P1, ProtocolChannel.PORTAL, empty, false, false, null, null))
            .isInstanceOf(IllegalStateException.class);
    }
}
