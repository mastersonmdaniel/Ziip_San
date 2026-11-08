package br.com.ziipsan.commercial.billingrules;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PixPayloadTest {

    @Test
    void crc16KnownAnswer() {
        // Vetor classico do CRC-16/CCITT-FALSE: "123456789" -> 0x29B1.
        assertThat(PixPayload.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void payloadStructureAndCrc() {
        String payload = PixPayload.build("pix@exemplo.com.br", new BigDecimal("27.90"),
                "Concessionaria Exemplo", "Camboriu", "ZIIP0000000000000000001");
        assertThat(payload).startsWith("000201");
        assertThat(payload).contains("0014br.gov.bcb.pix");
        assertThat(payload).contains("5303986");
        assertThat(payload).contains("540527.90");
        assertThat(payload).contains("5802BR");
        assertThat(payload).contains("5922CONCESSIONARIA EXEMPLO");
        assertThat(payload).contains("6008CAMBORIU");
        String withoutCrc = payload.substring(0, payload.length() - 4);
        assertThat(payload.substring(payload.length() - 4)).isEqualTo(PixPayload.crc16(withoutCrc));
    }

    @Test
    void payloadWithoutAmountOmitsField54() {
        String payload = PixPayload.build("pix@exemplo.com.br", null,
                "Concessionaria Exemplo", "Camboriu", "TXID1");
        assertThat(payload).doesNotContain("5405");
        assertThat(payload).contains("5303986");
    }

    @Test
    void invalidTxidIsRejected() {
        assertThatThrownBy(() -> PixPayload.build("pix@exemplo.com.br", null,
                "Nome", "Cidade", "***invalido***"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
