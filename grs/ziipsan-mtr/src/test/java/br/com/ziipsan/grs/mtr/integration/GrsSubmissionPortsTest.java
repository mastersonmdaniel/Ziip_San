package br.com.ziipsan.grs.mtr.integration;

// ED-104 (produtor, sessão 6d762d43-e1a9-4984-b50d-571ba0643b52) — E13, teste 6 (portas).
import br.com.ziipsan.grs.mtr.domain.WasteMtr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrsSubmissionPortsTest {

    private WasteMtr mtr() {
        WasteMtr m = new WasteMtr();
        m.setMtrNumber("2026.00000001.TENA.01");
        m.setQuantityKg(new BigDecimal("100"));
        m.setStatus(WasteMtr.MtrStatus.DESTINED);
        m.setSinirSyncStatus(WasteMtr.SinirSyncStatus.NOT_SUBMITTED);
        return m;
    }

    @Test
    @DisplayName("sem adapter, IMA e SINIR recusam ruidosamente e nada fica 'sincronizado'")
    void noAdapterRefusesLoudly() {
        var service = new GrsSubmissionService(new GrsSubmissionPorts.AdapterRegistry());
        WasteMtr m = mtr();
        assertThatThrownBy(() -> service.submitToIma(m))
                .isInstanceOf(GrsSubmissionPorts.SubmissionRefusedException.class)
                .hasMessageContaining("nenhum adapter");
        assertThatThrownBy(() -> service.syncWithSinir(m))
                .isInstanceOf(GrsSubmissionPorts.SubmissionRefusedException.class)
                .hasMessageContaining("P0.25");
        assertThat(m.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.NOT_SUBMITTED);
        assertThat(m.getOfficialMtrNumber()).isNull();
        assertThat(m.getSinirProtocol()).isNull();
        assertThat(m.getSinirSyncAt()).isNull();
    }

    @Test
    @DisplayName("só um adapter real, com protocolo do emissor, pode produzir SYNCED e número oficial")
    void onlyRealReceiptProducesSynced() {
        var registry = new GrsSubmissionPorts.AdapterRegistry();
        registry.registerSinir(payload -> new GrsSubmissionPorts.SubmissionReceipt(
                "SINIR", "0001234567", "PROTO-2026-1"));
        var service = new GrsSubmissionService(registry);
        WasteMtr m = mtr();
        var receipt = service.syncWithSinir(m);
        assertThat(receipt.officialNumber()).isEqualTo("0001234567");
        assertThat(m.getSinirSyncStatus()).isEqualTo(WasteMtr.SinirSyncStatus.SYNCED);
        assertThat(m.getOfficialMtrNumber()).isEqualTo("0001234567");
        assertThat(m.getSinirProtocol()).isEqualTo("PROTO-2026-1");
        // recibo sem protocolo não existe: o record recusa
        assertThatThrownBy(() -> new GrsSubmissionPorts.SubmissionReceipt("SINIR", "1", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("regra interestadual de SC (IMA 009/2026 art. 6º): fluxo interestadual sinaliza MTR duplo")
    void interstateFlowRequiresDoubleMtr() {
        assertThat(GrsSubmissionPorts.requiredSystemsForFlow("SC", "SC")).isEqualTo(List.of("IMA"));
        assertThat(GrsSubmissionPorts.requiredSystemsForFlow("SC", "PR"))
                .containsExactly("IMA", "SINIR_OR_DESTINATION_STATE");
        assertThat(GrsSubmissionPorts.requiredSystemsForFlow("PR", "SC"))
                .containsExactly("ORIGIN_STATE_OR_SINIR", "IMA");
        assertThat(GrsSubmissionPorts.requiredSystemsForFlow("PR", "PR")).isEqualTo(List.of("SINIR"));
    }

    @Test
    @DisplayName("o payload declara o leiaute [SEM FONTE] — projecção interna, não leiaute oficial")
    void payloadDeclaresNoOfficialLayout() {
        var service = new GrsSubmissionService(new GrsSubmissionPorts.AdapterRegistry());
        var payload = service.payloadOf(mtr());
        assertThat(String.valueOf(payload.fields().get("officialLayout"))).contains("[SEM FONTE]");
    }
}
