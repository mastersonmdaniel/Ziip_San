package br.com.ziipsan.commercial.fiscal.nfage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reflexo do cashback na NFAg-e (P2.34 sobre a P2.6): DTO condicional ao
 * MOC definitivo — sem campo de leiaute inventado, ligado à versão do
 * leiaute, com a condicionalidade declarada no payload interno.
 * Valores à mão: CBS 30,00 + IBS 2,00 = 32,00.
 */
class NfageCashbackReflectionTest {

    @Test
    @DisplayName("sem MOC definir os campos: reflexo NÃO é aplicável ao leiaute e o payload declara a condicionalidade")
    void conditionalUntilMoc() {
        var reflexo = new NfageCashbackReflection.ReflexoCashback(UUID.randomUUID(),
                new BigDecimal("30.00"), new BigDecimal("2.00"), new BigDecimal("32.00"),
                "MOC-pendente", false);

        assertThat(reflexo.aplicavelAoLeiaute()).isFalse();
        String payload = reflexo.payloadInternoJson();
        assertThat(payload).contains("CONDICIONAL AO MOC DEFINITIVO");
        assertThat(payload).contains("\"totalDevolvido\":\"32.00\"");
        assertThat(payload).contains("\"aplicavelAoLeiaute\":false");
    }

    @Test
    @DisplayName("com o MOC definindo os campos, o mesmo DTO passa a ser aplicável (a fiação é da integração)")
    void applicableWhenMocDefines() {
        var reflexo = new NfageCashbackReflection.ReflexoCashback(UUID.randomUUID(),
                new BigDecimal("30.00"), new BigDecimal("2.00"), new BigDecimal("32.00"),
                "versao-futura-do-moc", true);
        assertThat(reflexo.aplicavelAoLeiaute()).isTrue();
    }

    @Test
    @DisplayName("total diferente de CBS + IBS e versão de leiaute ausente são erros explícitos")
    void inconsistentReflectionIsAnError() {
        UUID conta = UUID.randomUUID();
        assertThatThrownBy(() -> new NfageCashbackReflection.ReflexoCashback(conta,
                new BigDecimal("30.00"), new BigDecimal("2.00"), new BigDecimal("31.00"),
                "MOC-pendente", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NfageCashbackReflection.ReflexoCashback(conta,
                new BigDecimal("30.00"), new BigDecimal("2.00"), new BigDecimal("32.00"),
                " ", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
