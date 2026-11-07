package br.com.ziipsan.operations.metering.field;

/**
 * Parametros de leitura de campo vigentes do tenant, lidos do catalogo de parametros
 * (nunca fixados no codigo): os dois de OCR da ED-18 e os digitos do mostrador da
 * unidade, que a comparacao precisa para alinhar os valores.
 */
public record FieldReadingParameters(OcrMode ocrMode, int ocrToleranceDigits) {

    public FieldReadingParameters {
        if (ocrMode == null) {
            throw new IllegalArgumentException("read.ocr_mode vigente e obrigatorio");
        }
        if (ocrToleranceDigits < 0) {
            throw new IllegalArgumentException("read.ocr_tolerance_digits negativo");
        }
    }
}
