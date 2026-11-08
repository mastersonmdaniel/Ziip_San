package br.com.ziipsan.operations.metering.reading;

import br.com.ziipsan.operations.metering.reading.ReadingCycleParameters.PhotoRequiredOn;
import br.com.ziipsan.operations.metering.rules.ExpectedReadingRange;
import br.com.ziipsan.operations.metering.rules.MeteringErrorCode;
import br.com.ziipsan.operations.metering.rules.MeteringInputException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Autoleitura pelo portal, com crítica (ED-15, sobre o portal da P3.3).
 *
 * <p>O consumidor informa a leitura do seu hidrômetro pelo portal; a
 * <b>crítica</b> é a conferência automática antes de a leitura valer:
 * <ul>
 *   <li>leitura ausente ou negativa é recusada;</li>
 *   <li>foto faltante, quando o parâmetro {@code read.photo_required_on}
 *       a exige ({@code EVERY_READING}), é recusada — a mesma regra de
 *       campo da {@link PhotoAndImpedimentRule};</li>
 *   <li>leitura <b>fora da faixa esperada</b> (a faixa da P2.5,
 *       {@link ExpectedReadingRange}) não é aceita em silêncio nem
 *       recusada: sai como {@link Verdict#ACCEPTED_WITH_CRITIQUE}, para
 *       revisão/confirmação antes de faturar — é a crítica da autoleitura.</li>
 * </ul>
 *
 * <p>Esta classe não autentica o consumidor nem grava a leitura: vínculo
 * e sessão são do portal (P3.3) e a gravação é da integração; aqui fica
 * só a crítica, determinística e testável.
 */
public final class SelfReadingService {

    private SelfReadingService() {
    }

    public enum Verdict {
        ACCEPTED,
        ACCEPTED_WITH_CRITIQUE,
        REJECTED
    }

    public enum Critique {
        READING_MISSING,
        READING_NEGATIVE,
        PHOTO_REQUIRED,
        OUT_OF_EXPECTED_RANGE
    }

    /** Resultado da crítica: o veredito e TODAS as críticas encontradas. */
    public record Outcome(Verdict verdict, List<Critique> critiques) {
        public Outcome {
            critiques = critiques == null ? List.of() : List.copyOf(critiques);
        }
    }

    /**
     * @param unitId          unidade da autoleitura (vínculo conferido pelo portal)
     * @param reading         leitura informada pelo consumidor; nula = não informou
     * @param expectedRange   faixa esperada da unidade neste ciclo (P2.5)
     * @param hasPhoto        a foto do mostrador foi enviada
     * @param photoRequiredOn valor vigente de {@code read.photo_required_on}
     */
    public static Outcome evaluate(UUID unitId, Integer reading, ExpectedReadingRange expectedRange,
                                   boolean hasPhoto, PhotoRequiredOn photoRequiredOn) {
        if (unitId == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "autoleitura sem unidade");
        }
        if (expectedRange == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    "autoleitura sem a faixa esperada da unidade (crítica exige a faixa da P2.5)");
        }
        if (photoRequiredOn == null) {
            throw new MeteringInputException(MeteringErrorCode.INVALID_INPUT,
                    ReadingCycleParameters.PHOTO_REQUIRED_ON_KEY + ": valor vigente é obrigatório");
        }
        List<Critique> critiques = new ArrayList<>();
        if (reading == null) {
            critiques.add(Critique.READING_MISSING);
        } else if (reading < 0) {
            critiques.add(Critique.READING_NEGATIVE);
        }
        // Autoleitura não é impedimento: a foto só é exigida em EVERY_READING.
        if (PhotoAndImpedimentRule.photoRequired(false, photoRequiredOn) && !hasPhoto) {
            critiques.add(Critique.PHOTO_REQUIRED);
        }
        if (!critiques.isEmpty()) {
            return new Outcome(Verdict.REJECTED, critiques);
        }
        if (expectedRange.isOutOfRange(reading)) {
            return new Outcome(Verdict.ACCEPTED_WITH_CRITIQUE,
                    List.of(Critique.OUT_OF_EXPECTED_RANGE));
        }
        return new Outcome(Verdict.ACCEPTED, List.of());
    }
}
