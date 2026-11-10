package br.com.ziipsan.operations.metering.selfreading;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.1 (fronteira do limiar), §4.2 (roteamento sem caminho silencioso),
 * §4.3 (distancia alem do limiar sinalizada) e §4.4 (sem adaptador OCR:
 * comportamento tipificado) da estimativa.
 */
class SelfReadingIntakeServiceTest {

    private static final UUID UNIT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final YearMonth REF = YearMonth.of(2026, 11);
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    private final SelfReadingScoreConfig config = SelfReadingScoreConfig.proofDefaults();
    private final PhotoArtifact photoFull = new PhotoArtifact("foto/1", HASH, 1200, 1000);
    private final GeoPoint connection = new GeoPoint(0, 0);
    private final GeoProof proofHere = new GeoProof(new GeoPoint(0, 0), 10, NOW);

    private OcrPort adapterWith(OcrExtraction extraction) {
        return new DeterministicProofOcrAdapter(Map.of(HASH, extraction));
    }

    private SelfReadingSubmission submission(Integer declared, PhotoArtifact photo,
                                             GeoProof geo, Integer previous) {
        return new SelfReadingSubmission(UUID.randomUUID(), UNIT, REF, declared, photo,
                geo, connection, previous, List.of(10, 12, 14));
    }

    @Test
    void scoreAltoAceitaParaCriticaComHandoff() {
        // score 96.00 (calculado a mao no teste do calculador)
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1012, new BigDecimal("0.90"), "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.ACCEPTED_FOR_CRITIQUE, out.status());
        assertEquals(0, new BigDecimal("96.00").compareTo(out.score().total()));
        assertEquals(Integer.valueOf(1012), out.ocrReading());
        assertTrue(out.reasons().contains(SelfReadingReason.ACCEPTED));
        // fronteira II-2: o handoff espelha a ReadingSubmission da critica
        assertEquals(Integer.valueOf(1012), out.handoff().currentReading());
        assertEquals(Integer.valueOf(1000), out.handoff().previousReading());
        assertEquals(Integer.valueOf(12), out.handoff().measuredConsumption());
        assertEquals(List.of(10, 12, 14), out.handoff().history());
        assertEquals("foto/1", out.handoff().photoArtifactRef());
        assertEquals(0.0, out.handoff().geoDistanceMetres(), 0.000001);
    }

    @Test
    void fronteiraIgualAoLimiarAceita() {
        // score exactamente 70.00 -> aceite (regra declarada: >= limiar)
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1024, BigDecimal.ONE, "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, proofHere, 1000), config);
        assertEquals(0, new BigDecimal("70.00").compareTo(out.score().total()));
        assertEquals(SelfReadingStatus.ACCEPTED_FOR_CRITIQUE, out.status());
    }

    @Test
    void scoreBaixoVaiParaRevisaoHumana() {
        // score 57.50 < 70
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1018, new BigDecimal("0.50"), "x")));
        PhotoArtifact photo500 = new PhotoArtifact("foto/2", HASH, 500, 800);
        SelfReadingOutcome out = service.evaluate(
                submission(null, photo500, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_HUMAN_REVIEW, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.SCORE_BELOW_THRESHOLD));
        assertEquals(0, new BigDecimal("57.50").compareTo(out.score().total()));
        assertNull(out.handoff());
    }

    @Test
    void semAdaptadorOcrPendenteTipificadoSemLeituraInventada() {
        SelfReadingOutcome out = SelfReadingIntakeService.withoutOcrAdapter()
                .evaluate(submission(null, photoFull, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_OCR, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.NO_OCR_ADAPTER));
        assertNull(out.ocrReading());
        assertNull(out.score());
        assertNull(out.handoff());
    }

    @Test
    void adaptadorIndisponivelPendenteTipificado() {
        OcrPort down = photo -> {
            throw new OcrUnavailableException("motor em baixo (fixture)");
        };
        SelfReadingOutcome out = new SelfReadingIntakeService(down)
                .evaluate(submission(null, photoFull, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_OCR, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.OCR_UNAVAILABLE));
        assertNull(out.ocrReading());
    }

    @Test
    void adaptadorQueDevolveNullTratadoComoIndisponivel() {
        OcrPort broken = photo -> null;
        SelfReadingOutcome out = new SelfReadingIntakeService(broken)
                .evaluate(submission(null, photoFull, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_OCR, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.OCR_UNAVAILABLE));
    }

    @Test
    void ocrSemLeituraNaFotoVaiParaRevisaoSemScoreFabricado() {
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                new DeterministicProofOcrAdapter(Map.of()));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_HUMAN_REVIEW, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.OCR_NO_READING));
        assertNull(out.score());
        assertNull(out.ocrReading());
    }

    @Test
    void semFotoVaiParaRevisao() {
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1012, new BigDecimal("0.90"), "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, null, proofHere, 1000), config);
        assertEquals(SelfReadingStatus.PENDING_HUMAN_REVIEW, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.PHOTO_MISSING));
    }

    @Test
    void distanciaAlemDoLimiarNaoAceitaEmSilencioMesmoComScoreAlto() {
        // prova a ~222 m da ligacao (maximo 100 m): o componente geo cai a 0
        // e o score seria 81.00 (>= 70), mas o filtro geografico e porta:
        // vai para revisao, sinalizada, sem handoff.
        GeoProof far = new GeoProof(new GeoPoint(0, 0.002), 10, NOW);
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1012, new BigDecimal("0.90"), "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, far, 1000), config);
        assertEquals(0, new BigDecimal("81.00").compareTo(out.score().total()));
        assertEquals(SelfReadingStatus.PENDING_HUMAN_REVIEW, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.GEO_BEYOND_MAX_DISTANCE));
        assertNull(out.handoff());
    }

    @Test
    void declaradaDivergenteAnulaCoerenciaEMandaParaRevisao() {
        // coerencia 0 -> total 0.36+0+0.15+0.15 = 66.00 < 70
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1012, new BigDecimal("0.90"), "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(9999, photoFull, proofHere, 1000), config);
        assertEquals(0, new BigDecimal("66.00").compareTo(out.score().total()));
        assertEquals(SelfReadingStatus.PENDING_HUMAN_REVIEW, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.DECLARED_READING_DIVERGES));
        assertTrue(out.reasons().contains(SelfReadingReason.SCORE_BELOW_THRESHOLD));
    }

    @Test
    void primeiraLeituraHandoffSemConsumoDerivado() {
        // sem anterior: coerencia 0; 1*0.40 + 0 + 0.15 + 0.15 = 70.00 -> aceite na fronteira
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(500, BigDecimal.ONE, "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, proofHere, null), config);
        assertEquals(SelfReadingStatus.ACCEPTED_FOR_CRITIQUE, out.status());
        assertNull(out.handoff().measuredConsumption());
        assertNull(out.handoff().previousReading());
    }

    @Test
    void precisaoInsuficienteRegistaMotivoEZeraComponenteGeo() {
        // declarado: precisao nao e porta dura; zera o componente geo e fica registada.
        // total = 0.36 + 0.30 + 0.15 + 0 = 81.00 -> aceite COM o motivo visivel.
        GeoProof sloppy = new GeoProof(new GeoPoint(0, 0), 80, NOW);
        SelfReadingIntakeService service = new SelfReadingIntakeService(
                adapterWith(OcrExtraction.extracted(1012, new BigDecimal("0.90"), "x")));
        SelfReadingOutcome out = service.evaluate(
                submission(null, photoFull, sloppy, 1000), config);
        assertEquals(SelfReadingStatus.ACCEPTED_FOR_CRITIQUE, out.status());
        assertTrue(out.reasons().contains(SelfReadingReason.GEO_ACCURACY_INSUFFICIENT));
        assertEquals(0, BigDecimal.ZERO.compareTo(out.score().geoComponent()));
    }
}
