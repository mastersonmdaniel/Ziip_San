package br.com.ziipsan.integration.hub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3.37: log com carga mascarada. As cadeias esperadas foram montadas à
 * mão na ordem das chaves da carga (o Json do core preserva a ordem de
 * inserção); se a forma canônica mudar, o teste quebra alto — é o
 * comportamento desejado, como no EvidenceKit da P3.15.
 */
class PayloadMaskerTest {

    @Test
    @DisplayName("segredos e documentos mascarados em qualquer profundidade; o resto passa")
    void masksSensitiveKeysDeep() {
        String payload = "{\"operation\":\"send\",\"api_key\":\"segredo\",\"nested\":{\"token\":\"abc\",\"valor\":10},"
                + "\"items\":[{\"payer_document\":\"123\",\"ok\":\"sim\"}],\"note\":\"Bearer xyz\"}";
        assertThat(PayloadMasker.maskForLog(payload)).isEqualTo(
                "{\"operation\":\"send\",\"api_key\":\"***\",\"nested\":{\"token\":\"***\",\"valor\":10},"
                        + "\"items\":[{\"payer_document\":\"***\",\"ok\":\"sim\"}],\"note\":\"***\"}");
    }

    @Test
    @DisplayName("chave que contém fragmento sensível também mascara (client_secret, user_password)")
    void masksFragmentKeys() {
        String payload = "{\"client_secret\":\"x\",\"user_password\":\"y\",\"name\":\"visivel\"}";
        assertThat(PayloadMasker.maskForLog(payload))
                .isEqualTo("{\"client_secret\":\"***\",\"user_password\":\"***\",\"name\":\"visivel\"}");
    }

    @Test
    @DisplayName("carga que não é objeto JSON é omitida por inteiro, com o tamanho")
    void nonObjectOmitted() {
        assertThat(PayloadMasker.maskForLog("[1,2]"))
                .isEqualTo("{\"_payload\":\"[omitida do log: carga não é objeto JSON, 5 caracteres]\"}");
        assertThat(PayloadMasker.maskForLog("texto puro"))
                .isEqualTo("{\"_payload\":\"[omitida do log: carga não é objeto JSON, 10 caracteres]\"}");
    }

    @Test
    @DisplayName("objeto vazio e carga nula têm forma explícita no log")
    void emptyAndNull() {
        assertThat(PayloadMasker.maskForLog("{}")).isEqualTo("{}");
        assertThat(PayloadMasker.maskForLog(null)).isEqualTo("{\"_payload\":\"[omitida: carga nula]\"}");
    }

    @Test
    @DisplayName("literal null NÃO vira {} no log (é omitido); objeto vazio com espaços segue {}")
    void nullLiteralOmittedNotDisguised() {
        assertThat(PayloadMasker.maskForLog("null"))
                .isEqualTo("{\"_payload\":\"[omitida do log: carga não é objeto JSON, 4 caracteres]\"}");
        assertThat(PayloadMasker.maskForLog("{ }")).isEqualTo("{}");
    }

    @Test
    @DisplayName("IntegrationCallLog.of mascara na construção e carrega o correlation_id")
    void callLogMasksOnBuild() {
        UUID tenant = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        IntegrationCallLog log = IntegrationCallLog.of(tenant, "psp-pix", "send", "corr-1",
                IntegrationMode.SANDBOX, "{\"token\":\"abc\",\"valor\":1}", "SUCCESS",
                Instant.parse("2026-11-07T12:00:00Z"));
        assertThat(log.maskedPayloadJson()).isEqualTo("{\"token\":\"***\",\"valor\":1}");
        assertThat(log.correlationId()).isEqualTo("corr-1");
        assertThat(log.maskedPayloadJson()).doesNotContain("abc");
    }
}
