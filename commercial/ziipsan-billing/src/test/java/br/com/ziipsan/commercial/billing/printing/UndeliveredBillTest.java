package br.com.ziipsan.commercial.billing.printing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Registro de conta nao entregue (P2.8, JOR-35): retorno de devolucao vira
 * registro completo (motivo de catalogo, data, origem), alimenta a qualidade
 * cadastral da P3.32 e so marca o endereco como nao verificado quando o
 * motivo e endereco nao localizado.
 */
class UndeliveredBillTest {

    private static final UUID BILL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UNIT = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private static String returnLine(String reason, String origin) {
        return "RET|CONTA=" + BILL + "|UNIDADE=" + UNIT + "|MOTIVO=" + reason
                + "|DATA=2026-05-18|ORIGEM=" + origin;
    }

    @Test
    @DisplayName("retorno de devolucao: registro completo com motivo, data e origem")
    void devolutionReturnBecomesCompleteRecord() {
        List<UndeliveredBill> parsed = ReturnFileParser.parse(returnLine("DEVOLUCAO", "CORREIOS") + "\n");
        assertThat(parsed).hasSize(1);
        UndeliveredBill record = parsed.get(0);
        assertThat(record.billId()).isEqualTo(BILL);
        assertThat(record.unitId()).isEqualTo(UNIT);
        assertThat(record.reason()).isEqualTo(UndeliveredReason.DEVOLUCAO);
        assertThat(record.returnDate()).isEqualTo(LocalDate.of(2026, 5, 18));
        assertThat(record.origin()).isEqualTo(ReturnOrigin.CORREIOS);
        // Alimenta a P3.32; a devolucao, sozinha, nao marca o endereco.
        assertThat(record.feedsCadastralQuality()).isTrue();
        assertThat(record.marksAddressUnverified()).isFalse();
    }

    @Test
    @DisplayName("endereco nao localizado marca o endereco como nao verificado (contato verificado)")
    void addressNotFoundMarksAddressUnverified() {
        List<UndeliveredBill> parsed = ReturnFileParser.parse(
                returnLine("ENDERECO_NAO_LOCALIZADO", "ENTREGA_EM_CAMPO"));
        assertThat(parsed.get(0).reason()).isEqualTo(UndeliveredReason.ENDERECO_NAO_LOCALIZADO);
        assertThat(parsed.get(0).marksAddressUnverified()).isTrue();
        assertThat(parsed.get(0).feedsCadastralQuality()).isTrue();
    }

    @Test
    @DisplayName("motivo fora do catalogo, origem fora do catalogo e campo faltante recusam o arquivo nomeando a linha")
    void invalidReturnFileRejects() {
        assertThatThrownBy(() -> ReturnFileParser.parse(returnLine("EXTRAVIADO", "CORREIOS")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Linha 1")
                .hasMessageContaining("EXTRAVIADO");
        assertThatThrownBy(() -> ReturnFileParser.parse(returnLine("DEVOLUCAO", "POMBO_CORREIO")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("POMBO_CORREIO");
        assertThatThrownBy(() -> ReturnFileParser.parse(
                "RET|CONTA=" + BILL + "|UNIDADE=" + UNIT + "|MOTIVO=DEVOLUCAO|DATA=2026-05-18"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ORIGEM");
        assertThatThrownBy(() -> ReturnFileParser.parse(
                returnLine("DEVOLUCAO", "GRAFICA") + "\n" + "XXX|CONTA=" + BILL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Linha 2");
        assertThatThrownBy(() -> ReturnFileParser.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("campo duplicado na linha do retorno recusa o arquivo nomeando a linha")
    void duplicatedFieldInReturnLineRejects() {
        // [Teste acrescentado na revisao cruzada] Sem a trava, o ultimo
        // MOTIVO vencia em silencio e o registro podia nascer com o motivo
        // errado alimentando a qualidade cadastral da P3.32.
        assertThatThrownBy(() -> ReturnFileParser.parse(
                returnLine("DEVOLUCAO", "CORREIOS") + "|MOTIVO=ENDERECO_NAO_LOCALIZADO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Linha 1")
                .hasMessageContaining("MOTIVO");
    }

    @Test
    @DisplayName("registro sem motivo, data ou origem nao se monta")
    void incompleteRecordRejects() {
        assertThatThrownBy(() -> new UndeliveredBill(BILL, UNIT, null, LocalDate.of(2026, 5, 18), ReturnOrigin.GRAFICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> new UndeliveredBill(BILL, UNIT, UndeliveredReason.DEVOLUCAO, null, ReturnOrigin.GRAFICA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");
        assertThatThrownBy(() -> UndeliveredReason.fromKey("RECUSADO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalogo");
    }
}
