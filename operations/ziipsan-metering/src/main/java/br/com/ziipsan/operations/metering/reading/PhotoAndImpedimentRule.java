package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.ReadingCycleParameters.PhotoRequiredOn;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

/**
 * Foto obrigatória e aceite do impedimento (ED-15), pelos parâmetros
 * vigentes do tenant:
 *
 * <ul>
 *   <li>foto obrigatória conforme {@code read.photo_required_on}:
 *       {@code IMPEDIMENT_ONLY} exige foto só no impedimento;
 *       {@code EVERY_READING} exige foto em toda leitura (com leitura
 *       ou com impedimento);</li>
 *   <li>impedimento só é aceito depois do número de tentativas de
 *       {@code read.impediment_attempts}: tentativa a menos devolve o
 *       leiturista ao imóvel, não registra impedimento.</li>
 * </ul>
 *
 * <p>A regra decide e devolve o motivo; ela não grava leitura nem
 * impedimento. Leitura comum sem foto, com o parâmetro em
 * {@code IMPEDIMENT_ONLY}, não é afetada pela exigência de foto nem
 * pelo número de tentativas.
 */
public final class PhotoAndImpedimentRule {

    private PhotoAndImpedimentRule() {
    }

    public enum Verdict {
        ACCEPTED,
        /** Foto exigida pelo parâmetro vigente e não enviada. */
        REJECTED_PHOTO_REQUIRED,
        /** Impedimento antes de completar as tentativas exigidas. */
        REJECTED_IMPEDIMENT_ATTEMPTS
    }

    /**
     * @param hasReading        houve leitura do mostrador nesta visita
     * @param isImpediment      a visita terminou em impedimento
     * @param hasPhoto          a foto foi enviada
     * @param attemptsMade      tentativas de acesso já feitas nesta visita (>= 0)
     * @param photoRequiredOn   valor vigente de {@code read.photo_required_on}
     * @param impedimentAttempts valor vigente de {@code read.impediment_attempts} (>= 1)
     */
    public static Verdict evaluate(boolean hasReading, boolean isImpediment, boolean hasPhoto,
                                   int attemptsMade, PhotoRequiredOn photoRequiredOn,
                                   int impedimentAttempts) {
        if (photoRequiredOn == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    ReadingCycleParameters.PHOTO_REQUIRED_ON_KEY + ": valor vigente é obrigatório");
        }
        ReadingCycleParameters.requireValidImpedimentAttempts(impedimentAttempts);
        if (attemptsMade < 0) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "tentativas feitas não podem ser negativas");
        }
        if (hasReading && isImpediment) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a visita não pode ter leitura e ser impedimento ao mesmo tempo");
        }
        if (!hasReading && !isImpediment) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "a visita precisa terminar em leitura ou em impedimento: "
                            + "visita sem leitura e sem impedimento é entrada incoerente");
        }
        boolean photoRequired = switch (photoRequiredOn) {
            case EVERY_READING -> true;
            case IMPEDIMENT_ONLY -> isImpediment;
        };
        if (photoRequired && !hasPhoto) {
            return Verdict.REJECTED_PHOTO_REQUIRED;
        }
        if (isImpediment && attemptsMade < impedimentAttempts) {
            return Verdict.REJECTED_IMPEDIMENT_ATTEMPTS;
        }
        return Verdict.ACCEPTED;
    }

    /** A foto é exigida para esta visita, pelo parâmetro vigente. */
    public static boolean photoRequired(boolean isImpediment, PhotoRequiredOn photoRequiredOn) {
        if (photoRequiredOn == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    ReadingCycleParameters.PHOTO_REQUIRED_ON_KEY + ": valor vigente é obrigatório");
        }
        return photoRequiredOn == PhotoRequiredOn.EVERY_READING || isImpediment;
    }
}
