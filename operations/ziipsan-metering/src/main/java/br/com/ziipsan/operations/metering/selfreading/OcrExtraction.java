package br.com.ziipsan.operations.metering.selfreading;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * O resultado de uma extraccao OCR sobre a foto (II-7). Invariantes:
 * {@code EXTRACTED} exige leitura (&gt;= 0) e confianca em [0, 1];
 * {@code NO_READING} nao carrega leitura nem confianca -- um motor que
 * nao leu nao finge um numero nem uma confianca.
 *
 * @param status     desfecho tipificado
 * @param reading    leitura lida do mostrador; so em EXTRACTED
 * @param confidence confianca do motor em [0, 1]; so em EXTRACTED
 * @param engineId   identificador do motor/adaptador que extraiu (nao vazio)
 */
public record OcrExtraction(OcrStatus status, Integer reading, BigDecimal confidence,
                            String engineId) {

    public OcrExtraction {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(engineId, "engineId");
        if (engineId.isBlank()) {
            throw new IllegalArgumentException("engineId nao pode ser vazio");
        }
        if (status == OcrStatus.EXTRACTED) {
            Objects.requireNonNull(reading, "leitura e obrigatoria em EXTRACTED");
            Objects.requireNonNull(confidence, "confianca e obrigatoria em EXTRACTED");
            if (reading < 0) {
                throw new IllegalArgumentException("leitura OCR nao pode ser negativa: " + reading);
            }
            if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("confianca OCR fora de [0, 1]: " + confidence);
            }
        } else {
            if (reading != null || confidence != null) {
                throw new IllegalArgumentException(
                        "NO_READING nao carrega leitura nem confianca (nada e inventado)");
            }
        }
    }

    public static OcrExtraction extracted(int reading, BigDecimal confidence, String engineId) {
        return new OcrExtraction(OcrStatus.EXTRACTED, reading, confidence, engineId);
    }

    public static OcrExtraction noReading(String engineId) {
        return new OcrExtraction(OcrStatus.NO_READING, null, null, engineId);
    }
}
