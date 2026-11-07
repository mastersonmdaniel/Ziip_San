package br.com.ziipsan.operations.metering.field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Comparacao OCR x digitado x historico (ED-18; read.ocr_mode, read.ocr_tolerance_digits). */
class OcrComparisonTest {

    @Test
    @DisplayName("modo desligado nao compara nada, mesmo com divergencia total")
    void offDisables() {
        assertThat(OcrComparison.evaluate(OcrMode.OFF, 0, 5, 12345, 99999, 10000))
                .isEqualTo(OcrVerdict.DISABLED);
    }

    @Test
    @DisplayName("OCR igual ao digitado e historico compativel: MATCH")
    void match() {
        assertThat(OcrComparison.evaluate(OcrMode.REQUIRE, 0, 5, 12345, 12345, 12000))
                .isEqualTo(OcrVerdict.MATCH);
    }

    @Test
    @DisplayName("um digito divergente dentro da tolerancia de 1: MATCH; fora dela, modo exigir vai a revisao")
    void toleranceBoundary() {
        assertThat(OcrComparison.evaluate(OcrMode.REQUIRE, 1, 5, 12345, 12346, 12000))
                .isEqualTo(OcrVerdict.MATCH);
        assertThat(OcrComparison.evaluate(OcrMode.REQUIRE, 0, 5, 12345, 12346, 12000))
                .isEqualTo(OcrVerdict.REVIEW);
    }

    @Test
    @DisplayName("divergencia no modo sugerir vira sugestao, nao revisao")
    void suggestMode() {
        assertThat(OcrComparison.evaluate(OcrMode.SUGGEST, 0, 5, 12345, 12945, 12000))
                .isEqualTo(OcrVerdict.SUGGESTION);
    }

    @Test
    @DisplayName("digitado menor que a leitura anterior sem virada vai a revisao mesmo com OCR igual")
    void historyDivergence() {
        assertThat(OcrComparison.evaluate(OcrMode.SUGGEST, 0, 5, 11000, 11000, 12000))
                .isEqualTo(OcrVerdict.REVIEW);
    }

    @Test
    @DisplayName("virada do mostrador (anterior perto do topo) nao e divergencia de historico")
    void rolloverIsNotDivergence() {
        assertThat(OcrComparison.evaluate(OcrMode.REQUIRE, 0, 5, 00012, 00012, 99990))
                .isEqualTo(OcrVerdict.MATCH);
    }

    @Test
    @DisplayName("sem valor de OCR no modo exigir vai a revisao; no modo sugerir, nao")
    void missingOcr() {
        assertThat(OcrComparison.evaluate(OcrMode.REQUIRE, 0, 5, 12345, null, 12000))
                .isEqualTo(OcrVerdict.REVIEW);
        assertThat(OcrComparison.evaluate(OcrMode.SUGGEST, 0, 5, 12345, null, 12000))
                .isEqualTo(OcrVerdict.MATCH);
    }

    @Test
    @DisplayName("contagem de digitos divergentes alinha pelos digitos do mostrador")
    void divergentDigitsAlignment() {
        assertThat(OcrComparison.divergentDigits(123, 23, 5)).isEqualTo(1);
        assertThat(OcrComparison.divergentDigits(12345, 12345, 5)).isZero();
    }
}
