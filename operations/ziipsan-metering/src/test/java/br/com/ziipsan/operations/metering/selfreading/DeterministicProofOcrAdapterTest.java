package br.com.ziipsan.operations.metering.selfreading;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * O adaptador de prova deterministico (§4.4 da estimativa, pela
 * positiva): tabela explicita por hash; fora da tabela, NO_READING --
 * nunca um numero inventado. Nao e um motor OCR real (P-II7-OCR).
 */
class DeterministicProofOcrAdapterTest {

    private static final String HASH = "d".repeat(64);
    private final PhotoArtifact photo = new PhotoArtifact("foto/9", HASH, 1200, 1000);

    @Test
    void hashConhecidoExtraiEDeterministico() {
        DeterministicProofOcrAdapter adapter = new DeterministicProofOcrAdapter(
                Map.of(HASH, OcrExtraction.extracted(4321, new BigDecimal("0.87"), "qualquer")));
        OcrExtraction a = adapter.extract(photo);
        OcrExtraction b = adapter.extract(photo);
        assertEquals(a, b);
        assertEquals(OcrStatus.EXTRACTED, a.status());
        assertEquals(Integer.valueOf(4321), a.reading());
        assertEquals(0, new BigDecimal("0.87").compareTo(a.confidence()));
        // o carimbo e sempre o do adaptador de prova, nunca um motor fingido
        assertEquals(DeterministicProofOcrAdapter.ENGINE_ID, a.engineId());
    }

    @Test
    void hashDesconhecidoSemLeitura() {
        DeterministicProofOcrAdapter adapter = new DeterministicProofOcrAdapter(Map.of());
        OcrExtraction out = adapter.extract(photo);
        assertEquals(OcrStatus.NO_READING, out.status());
        assertNull(out.reading());
        assertNull(out.confidence());
    }

    @Test
    void artefactoComHashInvalidoRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoArtifact("foto/x", "nao-e-um-hash", 100, 100));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoArtifact("foto/x", HASH, 0, 100));
    }

    @Test
    void extraccaoInvalidaRecusada() {
        // EXTRACTED sem leitura / NO_READING com leitura sao contradicoes tipificadas
        assertThrows(NullPointerException.class,
                () -> new OcrExtraction(OcrStatus.EXTRACTED, null, BigDecimal.ONE, "e"));
        assertThrows(IllegalArgumentException.class,
                () -> new OcrExtraction(OcrStatus.NO_READING, 10, null, "e"));
        assertThrows(IllegalArgumentException.class,
                () -> OcrExtraction.extracted(10, new BigDecimal("1.5"), "e"));
    }
}
