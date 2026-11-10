package br.com.ziipsan.operations.metering.selfreading;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * A FRONTEIRA com a II-2 (II-7 -&gt; II-2): a leitura aceite na
 * conferencia da autoleitura, pronta para a critica. Os campos de
 * leitura espelham 1:1 os da {@code ReadingSubmission} da II-2
 * (unitId, reference, currentReading, previousReading,
 * measuredConsumption, history), para a fiacao na integracao ser uma
 * construcao directa desse tipo -- a critica em si (regras, fila de
 * excepcoes, VEE, vazamento) e TODA da II-2 e nao e tocada aqui.
 * Os campos extra sao a proveniencia da aceite: score, artefacto da
 * foto e distancia da prova geografica.
 *
 * @param measuredConsumption consumo derivado (leitura OCR - anterior); null na primeira leitura
 */
public record CritiqueHandoff(UUID submissionId, UUID unitId, YearMonth reference,
                              Integer currentReading, Integer previousReading,
                              Integer measuredConsumption, List<Integer> history,
                              BigDecimal scoreTotal, String photoArtifactRef,
                              Double geoDistanceMetres) {

    public CritiqueHandoff {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
