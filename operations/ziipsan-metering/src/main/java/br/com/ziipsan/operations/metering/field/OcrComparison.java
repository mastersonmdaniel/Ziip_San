package br.com.ziipsan.operations.metering.field;

/**
 * Comparacao da leitura por foto do visor (ED-18; OPE-34), regra pura e testavel sem aparelho:
 * o OCR roda NO aparelho e so os digitos reconhecidos chegam aqui — a imagem do visor nunca
 * sai do aparelho (ver {@code OnDeviceOcrEngine} no app e o teste de contrato).
 *
 * <h2>Comparacoes</h2>
 * <ol>
 *   <li><b>OCR x digitado:</b> os dois valores sao comparados digito a digito, alinhados a
 *       direita e completados com zeros a esquerda ate o numero de digitos do mostrador.
 *       Divergencia = quantidade de posicoes diferentes. Ate
 *       {@code read.ocr_tolerance_digits} posicoes diferentes e tolerado.</li>
 *   <li><b>Digitado x historico:</b> digitado menor que a leitura anterior e divergencia,
 *       salvo virada do mostrador (digitada compativel com {@code anterior + consumo}
 *       alem de {@code 10^digitos}); a virada segue a mesma ideia de
 *       {@code RolloverPolicy} do modulo, sem duplicar a cascata de consumo.</li>
 * </ol>
 *
 * <p>Sem valor de OCR (foto nao tirada ou OCR sem leitura): no modo REQUIRE a leitura vai
 * a revisao; nos demais modos, so a comparacao com o historico decide.
 */
public final class OcrComparison {

    private OcrComparison() {
    }

    public static OcrVerdict evaluate(OcrMode mode, int toleranceDigits, int meterDigits,
                                      Integer typedValue, Integer ocrValue,
                                      Integer previousReading) {
        if (mode == null || mode == OcrMode.OFF) {
            return OcrVerdict.DISABLED;
        }
        if (typedValue == null) {
            // Sem valor digitado nao ha leitura a conferir: quem valida a presenca e o
            // ReadingValidator; aqui, exigir OCR sem digitado nao e divergencia de OCR.
            return OcrVerdict.MATCH;
        }
        if (toleranceDigits < 0) {
            throw new IllegalArgumentException("read.ocr_tolerance_digits negativo");
        }
        if (historyDiverges(typedValue, previousReading, meterDigits)) {
            return OcrVerdict.REVIEW;
        }
        if (ocrValue == null) {
            return mode == OcrMode.REQUIRE ? OcrVerdict.REVIEW : OcrVerdict.MATCH;
        }
        int divergent = divergentDigits(typedValue, ocrValue, meterDigits);
        if (divergent <= toleranceDigits) {
            return OcrVerdict.MATCH;
        }
        return mode == OcrMode.REQUIRE ? OcrVerdict.REVIEW : OcrVerdict.SUGGESTION;
    }

    /** Posicoes diferentes entre os dois valores, alinhados pelos digitos do mostrador. */
    public static int divergentDigits(int a, int b, int meterDigits) {
        // Largura COMUM aos dois valores: padroes separados faziam charAt estourar
        // quando um valor passava dos digitos do mostrador e o outro nao.
        int width = Math.max(meterDigits,
                Math.max(Integer.toString(a).length(), Integer.toString(b).length()));
        String sa = pad(a, width);
        String sb = pad(b, width);
        int count = 0;
        for (int i = 0; i < sa.length(); i++) {
            if (sa.charAt(i) != sb.charAt(i)) {
                count++;
            }
        }
        return count;
    }

    private static String pad(int value, int digits) {
        String s = Integer.toString(value);
        int width = Math.max(digits, s.length());
        return "0".repeat(width - s.length()) + s;
    }

    private static boolean historyDiverges(int typed, Integer previous, int meterDigits) {
        if (previous == null || typed >= previous) {
            return false;
        }
        // Virada do mostrador: o mostrador passou de 10^digitos - 1 para o inicio; um
        // digitado menor so e compativel se a leitura anterior estava perto do topo.
        long capacity = 1L;
        for (int i = 0; i < meterDigits; i++) {
            capacity *= 10L;
        }
        return previous * 10 < capacity * 9;
    }
}
