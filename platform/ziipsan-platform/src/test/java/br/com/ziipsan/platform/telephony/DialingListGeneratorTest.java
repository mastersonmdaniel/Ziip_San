package br.com.ziipsan.platform.telephony;
import org.junit.jupiter.api.Test; import static org.assertj.core.api.Assertions.*;
import java.time.Instant; import java.util.*;
class DialingListGeneratorTest {
    static final Instant NOW=Instant.parse("2026-03-10T12:00:00Z");
    @Test void listaMaterializaSelecaoDaReguaComDedupEMotivos() {
        var log=new Ed44Support.MemLog();
        var gen=new DialingListGenerator(t->List.of(
            new TelephonyPorts.DialingCandidate(Ed44Support.UC1,"+55 47 99999-0001","D7_MSG","COBRANCA"),
            new TelephonyPorts.DialingCandidate(Ed44Support.UC2,"47999990001","D7_MSG","COBRANCA"),
            new TelephonyPorts.DialingCandidate(Ed44Support.UC2,"sem-numero","FILA_SAC","ATENDIMENTO")), log);
        var list=gen.generate(Ed44Support.T, Ed44Support.ATT, NOW);
        assertThat(list.items()).hasSize(1);
        assertThat(list.items().get(0).sourceRuleCode()).isEqualTo("D7_MSG");
        assertThat(list.excluded()).extracting(DialingListGenerator.Excluded::reason)
            .containsExactly("DUPLICATE_NUMBER","INVALID_OR_MISSING_PHONE");
        assertThat(log.entries.get(0).accessType()).isEqualTo(TelephonyAccessLog.AccessType.DIALING_LIST_GENERATED);
    }
    @Test void sourceKindForaDoConjuntoDoCheckEhRecusadoNoCodigo() {
        // R2 (revisao ED-44): o codigo valida o mesmo dominio do CHECK do SQL
        // (source_kind IN ('COBRANCA','ATENDIMENTO')) — "XYZ" nao passa.
        assertThatThrownBy(()->new TelephonyPorts.DialingCandidate(Ed44Support.UC1,"47999990001","D7_MSG","XYZ"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(()->new TelephonyPorts.DialingCandidate(Ed44Support.UC1,"47999990001","D7_MSG","COBRANCA"))
            .doesNotThrowAnyException();
        assertThatCode(()->new TelephonyPorts.DialingCandidate(Ed44Support.UC1,"47999990001","FILA_SAC","ATENDIMENTO"))
            .doesNotThrowAnyException();
    }
    @Test void selecaoVaziaGeraListaVaziaSemInventarCandidatos() {
        var gen=new DialingListGenerator(t->List.of(), new Ed44Support.MemLog());
        assertThat(gen.generate(Ed44Support.T, Ed44Support.ATT, NOW).items()).isEmpty();
    }
}
