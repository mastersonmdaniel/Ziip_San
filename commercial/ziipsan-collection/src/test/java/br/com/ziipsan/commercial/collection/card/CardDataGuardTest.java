package br.com.ziipsan.commercial.collection.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A guarda de dado de cartao: detecta PAN (Luhn, com ou sem separador)
 * e campos proibidos, e NAO sinaliza numeros legitimos do fluxo
 * (valores, datas, referencias, identificadores longos).
 * Os numeros de cartao usados sao os numeros de TESTE publicos das
 * bandeiras (ex.: 4111111111111111), nunca cartoes reais.
 */
class CardDataGuardTest {

    @Test
    @DisplayName("PAN de teste puro, com espacos e com hifens e detectado (Luhn)")
    void detectsPanVariants() {
        assertThat(CardDataGuard.containsCardData("pagamento " + CardTestSupport.TEST_PAN)).isTrue();
        assertThat(CardDataGuard.containsCardData("pagamento " + CardTestSupport.TEST_PAN_SPACED)).isTrue();
        assertThat(CardDataGuard.containsCardData("4111-1111-1111-1111")).isTrue();
        assertThat(CardDataGuard.containsCardData("5500005555555559")).isTrue();
    }

    @Test
    @DisplayName("sequencia de 16 digitos que NAO passa em Luhn nao e PAN")
    void nonLuhnSequenceIsNotPan() {
        assertThat(CardDataGuard.containsCardData("ref 1234567812345678")).isFalse();
    }

    @Test
    @DisplayName("identificador numerico de 20 digitos (longo demais p/ PAN) nao e sinalizado")
    void longNumericIdIsNotPan() {
        assertThat(CardDataGuard.containsCardData("tid 12345678901234567890")).isFalse();
    }

    @Test
    @DisplayName("campos proibidos em JSON sao detectados mesmo sem PAN")
    void detectsForbiddenFields() {
        assertThat(CardDataGuard.containsCardData("{\"cvv\": \"123\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"cardNumber\": \"x\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"numero_cartao\": \"x\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"pan\": \"x\"}")).isTrue();
    }

    @Test
    @DisplayName("payload legitimo do fluxo (referencia, valor, status) passa limpo")
    void legitPayloadPasses() {
        String payload = "{\"eventId\":\"evt-1\",\"acquirerTransactionId\":\"tx-987\","
                + "\"sessionReference\":\"9b7f2c1e-0000-4000-8000-000000000001\","
                + "\"status\":\"APPROVED\",\"grossAmount\":106.61,\"feeAmount\":3.20}";
        assertThat(CardDataGuard.containsCardData(payload)).isFalse();
    }

    @Test
    @DisplayName("assertNoCardData recusa com a excecao da guarda")
    void assertRejects() {
        assertThatThrownBy(() -> CardDataGuard.assertNoCardData("x " + CardTestSupport.TEST_PAN, "teste"))
                .isInstanceOf(CardDataGuard.CardDataDetectedException.class);
    }

    @Test
    @DisplayName("sanitizeForLog apaga sequencia longa de digitos e preserva o resto")
    void sanitizeRedactsLongDigits() {
        String out = CardDataGuard.sanitizeForLog("tx=abc valor 1234567890123 fim");
        assertThat(out).doesNotContain("1234567890123").contains("[REDACTED]").contains("tx=abc");
    }

    @Test
    @DisplayName("D3: PAN com PONTOS (4111.1111.1111.1111) e detectado e o sanitize o apaga")
    void detectsDottedPan() {
        assertThat(CardDataGuard.containsCardData("4111.1111.1111.1111")).isTrue();
        assertThat(CardDataGuard.containsPan("4111.1111.1111.1111")).isTrue();
        assertThat(CardDataGuard.sanitizeForLog("tx 4111.1111.1111.1111 fim"))
                .doesNotContain("4111").contains("[REDACTED]").contains("tx").contains("fim");
    }

    @Test
    @DisplayName("D3: campos da sonda da revisao — validade, expiry, securityCode, cardholder, holderName, pin")
    void detectsReviewerFieldFormats() {
        assertThat(CardDataGuard.containsCardData("{\"validade\":\"12/28\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"expiry\":\"12/28\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"securityCode\":\"123\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"cardholder\":\"FULANO DE TAL\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"holderName\":\"FULANO DE TAL\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"pin\":\"1234\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"nome_impresso\":\"FULANO DE TAL\"}")).isTrue();
        assertThat(CardDataGuard.containsCardData("{\"creditCard\":\"x\"}")).isTrue();
    }

    @Test
    @DisplayName("D3: campo proibido em TEXTO LIVRE, sem dois-pontos nem igual (cvv 123)")
    void detectsFreeTextField() {
        assertThat(CardDataGuard.containsCardData("retorno trouxe cvv 123 no corpo")).isTrue();
        assertThat(CardDataGuard.sanitizeForLog("retorno trouxe cvv 123 no corpo"))
                .doesNotContain("123").contains("[REDACTED]");
    }

    @Test
    @DisplayName("D3: PAN escapado em \\uXXXX dentro do JSON e detectado e o sanitize o apaga")
    void detectsUnicodeEscapedPan() {
        // "4111111111111111" com cada digito escapado: 4='4', 1='1'
        String escaped = "{\"tid\":\"\\u0034\\u0031\\u0031\\u0031\\u0031\\u0031\\u0031\\u0031"
                + "\\u0031\\u0031\\u0031\\u0031\\u0031\\u0031\\u0031\\u0031\"}";
        assertThat(CardDataGuard.containsCardData(escaped)).isTrue();
        assertThat(CardDataGuard.sanitizeForLog(escaped)).doesNotContain("4111").contains("[REDACTED]");
    }

    @Test
    @DisplayName("D3: sanitize apaga o VALOR de campo proibido (validade/securityCode) e preserva o resto")
    void sanitizeRedactsForbiddenFieldValues() {
        String out = CardDataGuard.sanitizeForLog("{\"validade\":\"12/28\",\"status\":\"APPROVED\"}");
        assertThat(out).doesNotContain("12/28").contains("[REDACTED]").contains("APPROVED");
    }

    @Test
    @DisplayName("Luhn: digito correto passa, digito trocado falha")
    void luhnItself() {
        assertThat(CardDataGuard.passesLuhn("4111111111111111")).isTrue();
        assertThat(CardDataGuard.passesLuhn("4111111111111112")).isFalse();
    }
}
