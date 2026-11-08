package br.com.ziipsan.platform.telephony;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
import java.time.Instant; import java.util.*;
class ScreenPopServiceTest {
    static final Instant NOW=Instant.parse("2026-03-10T12:00:00Z");
    TelephonyPorts.ContactDirectoryPort dir(List<TelephonyPorts.ContactEntry> l){ return t->l; }
    @Test void aberturaPelaChamadaApareceNoLogDeAcesso() {
        var log=new Ed44Support.MemLog();
        var svc=new ScreenPopService(dir(List.of(new TelephonyPorts.ContactEntry(Ed44Support.UC1,"+55 (47) 99999-0001"))),
            (t,u)->Optional.of(Ed44Support.view(u)), log);
        var r=svc.onIncomingCall(new TelephonyPorts.IncomingCall(Ed44Support.T, "call-1","47999990001",Ed44Support.ATT,NOW));
        assertThat(r.status()).isEqualTo(ScreenPopService.ScreenPopResult.Status.OPENED);
        assertThat(r.view()).isPresent();
        // TESTE OBRIGATORIO DO ACEITE: a abertura pela chamada esta no log
        assertThat(log.entries).hasSize(1);
        assertThat(log.entries.get(0).accessType()).isEqualTo(TelephonyAccessLog.AccessType.CALL_SCREEN_POP);
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.FOUND);
        assertThat(log.entries.get(0).consumerUnitId()).isEqualTo(Ed44Support.UC1);
        assertThat(log.entries.get(0).actorId()).isEqualTo(Ed44Support.ATT);
    }
    @Test void numeroDesconhecidoNaoAbreFichaEMasLoga() {
        var log=new Ed44Support.MemLog();
        var svc=new ScreenPopService(dir(List.of()), (t,u)->Optional.empty(), log);
        var r=svc.onIncomingCall(new TelephonyPorts.IncomingCall(Ed44Support.T,"c2","47888880000",Ed44Support.ATT,NOW));
        assertThat(r.status()).isEqualTo(ScreenPopService.ScreenPopResult.Status.NOT_FOUND);
        assertThat(log.entries).hasSize(1);
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.NOT_FOUND);
    }
    @Test void numeroCompartilhadoEhAmbiguoENaoAbreFichaErrada() {
        var log=new Ed44Support.MemLog();
        var svc=new ScreenPopService(dir(List.of(
            new TelephonyPorts.ContactEntry(Ed44Support.UC1,"47999990001"),
            new TelephonyPorts.ContactEntry(Ed44Support.UC2,"+5547999990001"))),
            (t,u)->Optional.of(Ed44Support.view(u)), log);
        var r=svc.onIncomingCall(new TelephonyPorts.IncomingCall(Ed44Support.T,"c3","47999990001",Ed44Support.ATT,NOW));
        assertThat(r.status()).isEqualTo(ScreenPopService.ScreenPopResult.Status.AMBIGUOUS);
        assertThat(r.candidateUnitIds()).containsExactlyInAnyOrder(Ed44Support.UC1,Ed44Support.UC2);
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.AMBIGUOUS);
    }
    @Test void numeroChamadorInvalidoOuOcultoNaoLancaExcecaoELogaNotFound() {
        // R1 (revisao ED-44): numero suprimido/invalido vindo do PABX nao pode
        // escapar como excecao nem furar o invariante "toda abertura e logada".
        var log=new Ed44Support.MemLog();
        var svc=new ScreenPopService(dir(List.of(new TelephonyPorts.ContactEntry(Ed44Support.UC1,"47999990001"))),
            (t,u)->Optional.of(Ed44Support.view(u)), log);
        var r=svc.onIncomingCall(new TelephonyPorts.IncomingCall(Ed44Support.T,"c-oculto","numero-oculto",Ed44Support.ATT,NOW));
        assertThat(r.status()).isEqualTo(ScreenPopService.ScreenPopResult.Status.NOT_FOUND);
        assertThat(r.view()).isEmpty();
        assertThat(r.callerNumberNormalized()).isNull();
        assertThat(log.entries).hasSize(1);
        assertThat(log.entries.get(0).accessType()).isEqualTo(TelephonyAccessLog.AccessType.CALL_SCREEN_POP);
        assertThat(log.entries.get(0).result()).isEqualTo(TelephonyAccessLog.Result.NOT_FOUND);
        assertThat(log.entries.get(0).callerNumberNormalized()).isNull();
    }
    @Test void normalizacaoIgnoraDdiEMascara() {
        assertThat(PhoneNumber.normalize("+55 (47) 99999-0001")).isEqualTo("47999990001");
        assertThat(PhoneNumber.matches("(47) 3333-0001","554733330001")).isTrue();
        assertThatThrownBy(()->PhoneNumber.normalize("123")).isInstanceOf(IllegalArgumentException.class);
    }
}
