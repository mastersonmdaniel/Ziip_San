package br.com.ziipsan.core.sac;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
class PlannedOutageTest {
    static final UUID OUT1 = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID UC1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID UC2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID UC9 = UUID.fromString("00000000-0000-0000-0000-000000000009");
    static final UUID UC99 = UUID.fromString("00000000-0000-0000-0000-000000000099");
    static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID P2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID P3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID OP7 = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private PlannedOutage outage(boolean essential) {
        return new PlannedOutage(OUT1, new PlannedOutage.Area("Centro","S1","R7"),
            LocalDateTime.of(2026,3,10,8,0), LocalDateTime.of(2026,3,10,18,0), "troca de valvula",
            List.of(UC1,UC2),
            essential ? List.of(new PlannedOutage.EssentialUnit(UC2,"HOSPITAL")) : List.of());
    }
    static final class FakeNotifier implements OutageNotifier {
        final List<String> calls = new ArrayList<>();
        public void notifyAgency(PlannedOutage o) { calls.add("agency"); }
        public void notifyAffectedUsers(PlannedOutage o, List<UUID> ids) { calls.add("users:"+ids.size()); }
        public void requestEmergencySupply(PlannedOutage o, List<PlannedOutage.EssentialUnit> e) { calls.add("emergency:"+e.size()); }
    }
    @Test @DisplayName("teste do aviso: agencia + usuarios + emergencia so com unidade essencial (P3.5)")
    void communication() {
        var fake = new FakeNotifier();
        OutageCommunication.communicate(outage(true), fake);
        assertThat(fake.calls).containsExactly("agency","users:2","emergency:1");
        var fake2 = new FakeNotifier();
        OutageCommunication.communicate(outage(false), fake2);
        assertThat(fake2.calls).containsExactly("agency","users:2");
    }
    @Test @DisplayName("unidade essencial fora da area afetada e recusada")
    void essentialOutsideArea() {
        assertThatThrownBy(() -> new PlannedOutage(OUT1, new PlannedOutage.Area("Centro","S1","R7"),
            LocalDateTime.of(2026,3,10,8,0), LocalDateTime.of(2026,3,10,18,0), "x",
            List.of(UC1), List.of(new PlannedOutage.EssentialUnit(UC9,"HOSPITAL"))))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("M3: area sem setor ou sem rota e recusada no cadastro")
    void areaRequiresSectorAndRoute() {
        assertThatThrownBy(() -> new PlannedOutage.Area("Centro", null, "R7"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedOutage.Area("Centro", "S1", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedOutage.Area("Centro", " ", "R7"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlannedOutage.Area("Centro", "S1", " "))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test @DisplayName("agrupamento do mesmo evento + aviso ao atendente")
    void groupingAndNotice() {
        var g = OutageProtocolGroup.group(outage(true), List.of(
            new OutageProtocolGroup.ProtocolRef(P1,UC1),
            new OutageProtocolGroup.ProtocolRef(P2,UC2),
            new OutageProtocolGroup.ProtocolRef(P3,UC99)));
        assertThat(g.size()).isEqualTo(2);
        var n = g.attendantNotice();
        assertThat(n.groupedProtocols()).isEqualTo(2);
        assertThat(n.hasEssentialUnits()).isTrue();
        assertThat(n.message()).contains(OUT1.toString());
    }
    @Test @DisplayName("previsao de retorno so por operador identificado")
    void returnUpdate() {
        var o = outage(false);
        assertThatThrownBy(() -> o.updateExpectedReturn(LocalDateTime.of(2026,3,10,20,0), null))
            .isInstanceOf(NullPointerException.class);
        o.updateExpectedReturn(LocalDateTime.of(2026,3,10,20,0), OP7);
        assertThat(o.expectedReturn()).isEqualTo(LocalDateTime.of(2026,3,10,20,0));
        assertThat(o.lastReturnUpdateBy()).isEqualTo(OP7);
    }
    @Test @DisplayName("consulta publica sem login: HIDDEN vazio, SUMMARY sem motivo, FULL com motivo, busca por inscricao e bairro")
    void publicStatus() {
        var list = List.of(outage(true));
        var from = LocalDateTime.of(2026,3,1,0,0); var to = LocalDateTime.of(2026,4,1,0,0);
        var now = LocalDateTime.of(2026,3,9,0,0);
        assertThat(PublicOutageStatus.query(list, SacParameters.OutageVisibility.HIDDEN, "Centro", from, to, now)).isEmpty();
        var sum = PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_SUMMARY, "centro", from, to, now);
        assertThat(sum).hasSize(1); assertThat(sum.get(0).reasonOrNull()).isNull();
        var full = PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, UC1.toString(), from, to, now);
        assertThat(full).hasSize(1); assertThat(full.get(0).reasonOrNull()).isEqualTo("troca de valvula");
        assertThat(PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, "BairroLonge", from, to, now)).isEmpty();
    }
    @Test @DisplayName("M2: consulta filtra por periodo, distingue ATIVA de PROGRAMADA e some com interrupcao encerrada")
    void publicStatusPeriodAndPhase() {
        var list = List.of(outage(true));
        // periodo que nao alcanca a interrupcao (10/03) -> vazia
        assertThat(PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, "",
            LocalDateTime.of(2026,4,1,0,0), LocalDateTime.of(2026,5,1,0,0),
            LocalDateTime.of(2026,3,9,0,0))).isEmpty();
        // antes do inicio -> PROGRAMADA
        var sched = PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, "",
            LocalDateTime.of(2026,3,1,0,0), LocalDateTime.of(2026,4,1,0,0),
            LocalDateTime.of(2026,3,9,12,0));
        assertThat(sched).hasSize(1);
        assertThat(sched.get(0).phase()).isEqualTo(PublicOutageStatus.Phase.SCHEDULED);
        // dentro da janela -> ATIVA
        var act = PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, "",
            LocalDateTime.of(2026,3,1,0,0), LocalDateTime.of(2026,4,1,0,0),
            LocalDateTime.of(2026,3,10,12,0));
        assertThat(act).hasSize(1);
        assertThat(act.get(0).phase()).isEqualTo(PublicOutageStatus.Phase.ACTIVE);
        // depois do retorno previsto -> encerrada, nao aparece
        assertThat(PublicOutageStatus.query(list, SacParameters.OutageVisibility.PUBLIC_FULL, "",
            LocalDateTime.of(2026,3,1,0,0), LocalDateTime.of(2026,4,1,0,0),
            LocalDateTime.of(2026,3,11,0,0))).isEmpty();
    }
}
