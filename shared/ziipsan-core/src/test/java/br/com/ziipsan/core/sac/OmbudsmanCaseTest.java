package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import br.com.ziipsan.core.parameters.MissingParameterException;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;
import br.com.ziipsan.core.protocol.ProtocolChannel;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
class OmbudsmanCaseTest {
    static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private OmbudsmanCase kase(Integer days) {
        return new OmbudsmanCase(P1, ProtocolChannel.OMBUDSMAN, LocalDate.of(2026,3,2), days,
            DeadlineCountingRule.CALENDAR, Set.of(), OmbudsmanCase.IdentityMode.PRESERVED, "Maria Sigilosa");
    }
    @Test @DisplayName("prazo da ouvidoria = abertura + sac.ombudsman_days (teste do prazo)")
    void deadline() {
        assertThat(kase(30).originalDueDate()).isEqualTo(LocalDate.of(2026,4,1));
        assertThat(kase(10).originalDueDate()).isEqualTo(LocalDate.of(2026,3,12));
    }
    @Test @DisplayName("G1: caso com 45 dias e RECUSADO na construcao (teto da Lei 13.460 imposto pelo proprio caso)")
    void legalCeilingEnforcedByCase() {
        assertThatThrownBy(() -> kase(45)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Lei 13.460/2017");
        assertThatThrownBy(() -> kase(31)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kase(null)).isInstanceOf(MissingParameterException.class);
        assertThatThrownBy(() -> kase(0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("uma prorrogacao por igual periodo; segunda recusada; sem justificativa recusada")
    void extensionOnce() {
        var c = kase(30);
        assertThatThrownBy(() -> c.extend(" ", DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalArgumentException.class);
        c.extend("aguardando pericia", DeadlineCountingRule.CALENDAR, Set.of());
        assertThat(c.currentDueDate()).isEqualTo(LocalDate.of(2026,5,1));
        assertThat(c.extended()).isTrue();
        assertThatThrownBy(() -> c.extend("de novo", DeadlineCountingRule.CALENDAR, Set.of()))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test @DisplayName("identidade preservada nao expoe o nome na fila")
    void identityPreserved() {
        var c = kase(30);
        assertThat(c.displayName()).isEqualTo("Identidade preservada");
        assertThat(c.identityExposed()).isFalse();
    }
    @Test @DisplayName("M6: manifestante ANONYMOUS nao tem o nome retido em memoria; canal e o ProtocolChannel da P1.36")
    void anonymousNameNotRetained() {
        var anon = new OmbudsmanCase(P1, ProtocolChannel.OMBUDSMAN, LocalDate.of(2026,3,2), 30,
            DeadlineCountingRule.CALENDAR, Set.of(), OmbudsmanCase.IdentityMode.ANONYMOUS, "Nome Secreto");
        assertThat(anon.requesterName()).isNull();
        assertThat(anon.displayName()).isEqualTo("Identidade preservada");
        assertThat(anon.channel()).isEqualTo(ProtocolChannel.OMBUDSMAN);
        var identified = new OmbudsmanCase(P1, ProtocolChannel.COUNTER, LocalDate.of(2026,3,2), 30,
            DeadlineCountingRule.CALENDAR, Set.of(), OmbudsmanCase.IdentityMode.IDENTIFIED, "Joao");
        assertThat(identified.requesterName()).isEqualTo("Joao");
        assertThat(identified.displayName()).isEqualTo("Joao");
    }
    @Test @DisplayName("relatorio anual: no prazo, fora do prazo, pendente, prorrogacoes")
    void annualReport() {
        var r = OmbudsmanAnnualReport.ofYear(2026, java.util.List.of(
            new OmbudsmanAnnualReport.CaseFact(LocalDate.of(2026,1,5), LocalDate.of(2026,2,4), LocalDate.of(2026,2,1), false),
            new OmbudsmanAnnualReport.CaseFact(LocalDate.of(2026,2,1), LocalDate.of(2026,3,3), LocalDate.of(2026,3,10), true),
            new OmbudsmanAnnualReport.CaseFact(LocalDate.of(2026,3,1), LocalDate.of(2026,3,31), null, false),
            new OmbudsmanAnnualReport.CaseFact(LocalDate.of(2025,3,1), LocalDate.of(2025,3,31), LocalDate.of(2025,3,20), false)));
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.answeredOnTime()).isEqualTo(1);
        assertThat(r.answeredLate()).isEqualTo(1);
        assertThat(r.pending()).isEqualTo(1);
        assertThat(r.extensionsUsed()).isEqualTo(1);
    }
}
