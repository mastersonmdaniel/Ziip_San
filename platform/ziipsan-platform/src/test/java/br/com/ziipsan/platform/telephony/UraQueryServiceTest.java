package br.com.ziipsan.platform.telephony;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
import java.time.Instant; import java.util.*;
class UraQueryServiceTest {
    static final Instant NOW=Instant.parse("2026-03-10T12:00:00Z");
    UraQueryService svc(Ed44Support.MemLog log){
        return new UraQueryService((t,c)->Optional.of(new TelephonyPorts.UraClient("ura-x","vault://telephony/ura-x",true)),
            ref->Optional.of("segredo-de-teste"),
            t->List.of(new TelephonyPorts.ContactEntry(Ed44Support.UC1,"47999990001")),
            (t,u)->Optional.of(Ed44Support.view(u)), log); }
    @Test void consultaAutenticadaIdentificaEFaturasAbertas() {
        var log=new Ed44Support.MemLog(); var a=svc(log).queryByCallerNumber(Ed44Support.T,"ura-x","segredo-de-teste","47999990001",NOW);
        assertThat(a.identified()).isTrue(); assertThat(a.consumerUnitId()).isEqualTo(Ed44Support.UC1); assertThat(a.openBills()).isEqualTo(1);
        assertThat(log.entries.get(0).accessType()).isEqualTo(TelephonyAccessLog.AccessType.URA_QUERY);
    }
    @Test void segredoErradoEhDeniedFailClosedELogado() {
        var log=new Ed44Support.MemLog(); var a=svc(log).queryByCallerNumber(Ed44Support.T,"ura-x","errado","47999990001",NOW);
        assertThat(a.denied()).isTrue(); assertThat(a.identified()).isFalse();
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.DENIED);
    }
    @Test void semView360NaoIdentificaELogaNotFound() {
        // R3 (revisao ED-44): contato casado mas ficha 360 ausente = NAO
        // identificado, log NOT_FOUND — nunca openBills=0 como fato.
        var log=new Ed44Support.MemLog();
        var s=new UraQueryService((t,c)->Optional.of(new TelephonyPorts.UraClient("ura-x","vault://telephony/ura-x",true)),
            ref->Optional.of("segredo-de-teste"),
            t->List.of(new TelephonyPorts.ContactEntry(Ed44Support.UC1,"47999990001")),
            (t,u)->Optional.empty(), log);
        var a=s.queryByCallerNumber(Ed44Support.T,"ura-x","segredo-de-teste","47999990001",NOW);
        assertThat(a.identified()).isFalse(); assertThat(a.denied()).isFalse();
        assertThat(a.consumerUnitId()).isNull();
        assertThat(log.entries).hasSize(1);
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.NOT_FOUND);
    }
    @Test void clienteDesconhecidoEhDenied() {
        var log=new Ed44Support.MemLog();
        var s=new UraQueryService((t,c)->Optional.empty(), ref->Optional.of("x"), t->List.of(), (t,u)->Optional.empty(), log);
        assertThat(s.queryByCallerNumber(Ed44Support.T,"zzz","x","47999990001",NOW).denied()).isTrue();
    }
}
