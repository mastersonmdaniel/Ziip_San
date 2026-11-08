package br.com.ziipsan.commercial.fiscal.queue;

import java.util.Objects;
import java.util.Set;

/**
 * Regra da inutilização de numeração (aceite da P2.33): só faixa de numeração NÃO
 * USADA. Faixa válida tem início >= 1, fim >= início e interseção vazia com os
 * números usados da série (documentos em qualquer estado do dicionário, inclusive
 * VOIDED, e números reservados em {@code fiscal_series} — quem monta o conjunto é a
 * {@link FiscalDocumentReadPort}). Um único número usado recusa a faixa inteira:
 * inutilizar "quase tudo" apagaria o rastro do número usado.
 */
public final class VoidingRule {

    private VoidingRule() {
    }

    public static void requireVoidable(long numberFrom, long numberTo, Set<Long> usedNumbers) {
        Objects.requireNonNull(usedNumbers, "Conjunto de números usados da série é obrigatório");
        if (numberFrom < 1) {
            throw new IllegalArgumentException("Inutilização exige início de faixa >= 1");
        }
        if (numberTo < numberFrom) {
            throw new IllegalArgumentException("Inutilização exige fim de faixa >= início");
        }
        for (long number = numberFrom; number <= numberTo; number++) {
            if (usedNumbers.contains(number)) {
                throw new IllegalArgumentException(
                        "Faixa contém número já usado da série (" + number + "): inutilização recusada");
            }
        }
    }

    public static boolean isVoidable(long numberFrom, long numberTo, Set<Long> usedNumbers) {
        try {
            requireVoidable(numberFrom, numberTo, usedNumbers);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
