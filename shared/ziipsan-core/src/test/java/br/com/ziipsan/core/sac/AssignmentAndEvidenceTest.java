package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;
class AssignmentAndEvidenceTest {
    static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID P2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID OS1 = UUID.fromString("00000000-0000-0000-0000-000000000051");
    static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID BRUNO = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID GESTOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    @Test @DisplayName("redistribuicao de protocolo e de OS: motivo obrigatorio e trilha (teste do acceptance)")
    void assignment() {
        var t = AssignmentChange.redistribute(AssignmentChange.TargetType.PROTOCOL, P1, ANA, BRUNO,
            "ferias da responsavel", GESTOR, LocalDateTime.of(2026,3,10,9,0));
        assertThat(t.fromPersonId()).isEqualTo(ANA); assertThat(t.toPersonId()).isEqualTo(BRUNO);
        assertThat(t.reason()).isEqualTo("ferias da responsavel");
        assertThatThrownBy(() -> AssignmentChange.redistribute(AssignmentChange.TargetType.SERVICE_ORDER,
            OS1, ANA, BRUNO, " ", GESTOR, LocalDateTime.of(2026,3,10,9,0)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AssignmentChange.redistribute(AssignmentChange.TargetType.PROTOCOL,
            P1, ANA, ANA, "motivo", GESTOR, LocalDateTime.of(2026,3,10,9,0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("evidencia ISO: pesquisa so com resposta registrada; nota 1..5")
    void evidence() {
        var e = new CommunicationEvidence(P1, "agua turva na rua X", null);
        assertThatThrownBy(() -> e.recordSatisfaction(new CommunicationEvidence.Satisfaction(5, null, LocalDateTime.of(2026,3,10,10,0))))
            .isInstanceOf(IllegalStateException.class);
        var e2 = new CommunicationEvidence(P2, "falta de agua", "equipe enviada; retorno as 18h");
        e2.recordSatisfaction(new CommunicationEvidence.Satisfaction(4, "ok", LocalDateTime.of(2026,3,11,10,0)));
        assertThat(e2.complete()).isTrue();
        assertThatThrownBy(() -> new CommunicationEvidence.Satisfaction(6, null, LocalDateTime.of(2026,3,10,10,0)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
