package br.com.ziipsan.operations.metering.selfreading;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.1 da estimativa: score com casos fixture calculados A MAO (exactos)
 * e fronteira do limiar. Pesos de prova: 0.40/0.30/0.15/0.15, limiar 70.
 */
class ReadingScoreCalculatorTest {

    private final SelfReadingScoreConfig config = SelfReadingScoreConfig.proofDefaults();
    private final GeoProofResult geoOk = new GeoProofResult(0.0, true, true, true);
    private final PhotoArtifact photoFull = new PhotoArtifact("foto/1", "a".repeat(64), 1200, 1000);

    private static void assertBd(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "esperado " + expected + " mas foi " + actual);
    }

    @Test
    void casoACalculadoAMao96() {
        // ocr 0.90*0.40=0.36; consumo 1012-1000=12, media (10+12+14)/3=12 -> coerencia 1*0.30=0.30;
        // foto lado menor 1000 -> 1*0.15=0.15; geo dentro -> 1*0.15=0.15. Total 0.96*100 = 96.00
        ReadingScore s = ReadingScoreCalculator.score(new BigDecimal("0.90"), 1012, null, 1000,
                List.of(10, 12, 14), photoFull, geoOk, config);
        assertBd("0.90", s.ocrComponent());
        assertBd("1", s.coherenceComponent());
        assertBd("1", s.photoComponent());
        assertBd("1", s.geoComponent());
        assertBd("96.00", s.total());
    }

    @Test
    void casoBCalculadoAMao5750() {
        // ocr 0.50*0.40=0.20; consumo 18, media 12 -> 1-6/12=0.5*0.30=0.15;
        // foto lado menor 500 -> 0.5*0.15=0.075; geo 1*0.15=0.15. Total 0.575*100 = 57.50
        PhotoArtifact photo500 = new PhotoArtifact("foto/2", "b".repeat(64), 500, 800);
        ReadingScore s = ReadingScoreCalculator.score(new BigDecimal("0.50"), 1018, null, 1000,
                List.of(10, 12, 14), photo500, geoOk, config);
        assertBd("0.5", s.coherenceComponent());
        assertBd("0.5", s.photoComponent());
        assertBd("57.50", s.total());
    }

    @Test
    void fronteiraScoreExactamente70() {
        // ocr 1*0.40=0.40; consumo 24, media 12 -> razao 1 -> coerencia 0; foto 0.15; geo 0.15
        // Total 0.70*100 = 70.00 (a fronteira do limiar prova-se no servico: igual aceita)
        ReadingScore s = ReadingScoreCalculator.score(BigDecimal.ONE, 1024, null, 1000,
                List.of(10, 12, 14), photoFull, geoOk, config);
        assertBd("0", s.coherenceComponent());
        assertBd("70.00", s.total());
        assertTrue(s.total().compareTo(config.acceptThreshold()) == 0);
    }

    @Test
    void coerenciaParcialExacta075() {
        // consumo 15, media 12 -> 1 - 3/12 = 0.75 exacto
        assertBd("0.75", ReadingScoreCalculator.coherence(1015, null, 1000, List.of(10, 12, 14)));
    }

    @Test
    void coerenciaDivergenciaDeclaradaAnula() {
        // sem divergencia seria 1 (consumo 12 = media); declarada 9999 != OCR 1012 -> 0
        assertBd("0", ReadingScoreCalculator.coherence(1012, 9999, 1000, List.of(10, 12, 14)));
        assertBd("1", ReadingScoreCalculator.coherence(1012, 1012, 1000, List.of(10, 12, 14)));
    }

    @Test
    void coerenciaSemEvidenciaZeroECasosDeMediaZero() {
        assertBd("0", ReadingScoreCalculator.coherence(1012, null, null, List.of(10, 12, 14)));
        assertBd("0", ReadingScoreCalculator.coherence(1012, null, 1000, List.of()));
        assertBd("0", ReadingScoreCalculator.coherence(990, null, 1000, List.of(10, 12, 14)));
        assertBd("1", ReadingScoreCalculator.coherence(1000, null, 1000, List.of(0, 0, 0)));
        assertBd("0", ReadingScoreCalculator.coherence(1005, null, 1000, List.of(0, 0, 0)));
    }

    @Test
    void componenteFoto() {
        assertBd("0", ReadingScoreCalculator.photoComponent(null));
        assertBd("0.5", ReadingScoreCalculator.photoComponent(
                new PhotoArtifact("f", "c".repeat(64), 500, 800)));
        assertBd("1", ReadingScoreCalculator.photoComponent(
                new PhotoArtifact("f", "c".repeat(64), 2000, 1500)));
    }

    @Test
    void componenteGeoDecaiLinearmente() {
        // maximo 100: distancia 150 -> 1 - 50/100 = 0.5; distancia 250 -> negativo -> 0
        assertBd("0", ReadingScoreCalculator.geoComponent(GeoProofResult.absent(), config));
        assertBd("0", ReadingScoreCalculator.geoComponent(
                new GeoProofResult(0.0, true, false, true), config));
        assertBd("1", ReadingScoreCalculator.geoComponent(
                new GeoProofResult(80.0, true, true, true), config));
        assertBd("0.5", ReadingScoreCalculator.geoComponent(
                new GeoProofResult(150.0, false, true, true), config));
        assertBd("0", ReadingScoreCalculator.geoComponent(
                new GeoProofResult(250.0, false, true, true), config));
    }

    @Test
    void determinismoMesmaEntradaMesmoScore() {
        ReadingScore a = ReadingScoreCalculator.score(new BigDecimal("0.90"), 1012, null, 1000,
                List.of(10, 12, 14), photoFull, geoOk, config);
        ReadingScore b = ReadingScoreCalculator.score(new BigDecimal("0.90"), 1012, null, 1000,
                List.of(10, 12, 14), photoFull, geoOk, config);
        assertEquals(a, b);
    }

    @Test
    void configuracaoInvalidaRecusada() {
        // pesos que nao somam 1
        assertThrows(IllegalArgumentException.class, () -> new SelfReadingScoreConfig(
                new BigDecimal("0.40"), new BigDecimal("0.30"),
                new BigDecimal("0.15"), new BigDecimal("0.14"),
                new BigDecimal("70"), 100.0, 50.0));
        // limiar fora de [0, 100]
        assertThrows(IllegalArgumentException.class, () -> new SelfReadingScoreConfig(
                new BigDecimal("0.40"), new BigDecimal("0.30"),
                new BigDecimal("0.15"), new BigDecimal("0.15"),
                new BigDecimal("101"), 100.0, 50.0));
    }

    @Test
    void confiancaForaDeIntervaloRecusada() {
        assertThrows(IllegalArgumentException.class, () ->
                ReadingScoreCalculator.score(new BigDecimal("1.5"), 1012, null, 1000,
                        List.of(10, 12, 14), photoFull, geoOk, config));
    }
}
