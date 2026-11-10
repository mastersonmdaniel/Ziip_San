package br.com.ziipsan.operations.metering.selfreading;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * O servico de intake da autoleitura (II-7, E2+E3+E4): submissao com
 * foto -&gt; OCR pela porta -&gt; score deterministico -&gt; conferencia
 * em DUAS ETAPAS -&gt; prova geografica como porta de aceite.
 *
 * <p>Roteamento declarado (nenhum caminho silencioso):
 * <ol>
 *   <li>sem adaptador na porta, ou adaptador indisponivel
 *       ({@link OcrUnavailableException}) =&gt; {@code PENDING_OCR} --
 *       comportamento TIPIFICADO; nenhuma leitura e inventada e a
 *       submissao nao e aceite "por OCR" sem OCR;</li>
 *   <li>sem foto =&gt; {@code PENDING_HUMAN_REVIEW} (neste fluxo a foto
 *       e o objecto do OCR);</li>
 *   <li>OCR sem leitura na foto =&gt; {@code PENDING_HUMAN_REVIEW},
 *       sem score fabricado;</li>
 *   <li>distancia a ligacao ALEM do maximo configurado =&gt;
 *       {@code PENDING_HUMAN_REVIEW} com {@code GEO_BEYOND_MAX_DISTANCE}
 *       -- sinalizada, nunca aceite em silencio, mesmo com score alto
 *       (o filtro geografico e porta de aceite, nao so componente);</li>
 *   <li>score &gt;= limiar (fronteira INCLUSIVA: igual ao limiar
 *       aceita) =&gt; {@code ACCEPTED_FOR_CRITIQUE}, com
 *       {@link CritiqueHandoff} para a critica da II-2;</li>
 *   <li>score &lt; limiar =&gt; {@code PENDING_HUMAN_REVIEW} com
 *       {@code SCORE_BELOW_THRESHOLD}, para a fila de revisao humana
 *       ({@link HumanReviewQueue}) -- ESTADO, sem tela (decisao 57).</li>
 * </ol>
 *
 * <p>Fronteiras: a critica da leitura aceite e da II-2 (este servico so
 * entrega o handoff); a critica simples da ED-15
 * ({@code SelfReadingService} do pacote {@code reading}) nao e alterada
 * nem substituida -- este fluxo e o da autoleitura COM foto/OCR da
 * Onda II, aditivo sobre ela.
 */
public final class SelfReadingIntakeService {

    private final OcrPort ocrPort;

    /** @param ocrPort adaptador ligado a porta; null = sem adaptador (P-II7-OCR) */
    public SelfReadingIntakeService(OcrPort ocrPort) {
        this.ocrPort = ocrPort;
    }

    /** Servico sem nenhum adaptador OCR: toda a submissao fica PENDING_OCR. */
    public static SelfReadingIntakeService withoutOcrAdapter() {
        return new SelfReadingIntakeService(null);
    }

    public SelfReadingOutcome evaluate(SelfReadingSubmission submission,
                                       SelfReadingScoreConfig config) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(config, "config");
        GeoProofResult geo = GeoProofEvaluator.evaluate(
                submission.geoProof(), submission.connectionPosition(), config);

        if (submission.photo() == null) {
            return outcome(submission, SelfReadingStatus.PENDING_HUMAN_REVIEW,
                    List.of(SelfReadingReason.PHOTO_MISSING), null, null, geo, null);
        }
        if (ocrPort == null) {
            return outcome(submission, SelfReadingStatus.PENDING_OCR,
                    List.of(SelfReadingReason.NO_OCR_ADAPTER), null, null, geo, null);
        }
        OcrExtraction extraction;
        try {
            extraction = ocrPort.extract(submission.photo());
        } catch (OcrUnavailableException unavailable) {
            return outcome(submission, SelfReadingStatus.PENDING_OCR,
                    List.of(SelfReadingReason.OCR_UNAVAILABLE), null, null, geo, null);
        }
        if (extraction == null) {
            // Adaptador que devolve null viola o contrato da porta: trata-se
            // como indisponivel, nunca como leitura.
            return outcome(submission, SelfReadingStatus.PENDING_OCR,
                    List.of(SelfReadingReason.OCR_UNAVAILABLE), null, null, geo, null);
        }
        if (extraction.status() == OcrStatus.NO_READING) {
            return outcome(submission, SelfReadingStatus.PENDING_HUMAN_REVIEW,
                    List.of(SelfReadingReason.OCR_NO_READING), null, null, geo, null);
        }

        ReadingScore score = ReadingScoreCalculator.score(
                extraction.confidence(), extraction.reading(), submission.declaredReading(),
                submission.previousReading(), submission.history(), submission.photo(),
                geo, config);

        List<SelfReadingReason> notes = new ArrayList<>();
        if (!geo.present()) {
            notes.add(SelfReadingReason.GEO_PROOF_MISSING);
        } else if (!geo.accuracySufficient()) {
            notes.add(SelfReadingReason.GEO_ACCURACY_INSUFFICIENT);
        }
        if (submission.declaredReading() != null
                && submission.declaredReading().intValue() != extraction.reading()) {
            notes.add(SelfReadingReason.DECLARED_READING_DIVERGES);
        }

        if (geo.present() && !geo.withinMaxDistance()) {
            List<SelfReadingReason> reasons = new ArrayList<>(notes);
            reasons.add(SelfReadingReason.GEO_BEYOND_MAX_DISTANCE);
            return outcome(submission, SelfReadingStatus.PENDING_HUMAN_REVIEW,
                    reasons, score, extraction.reading(), geo, null);
        }
        if (score.total().compareTo(config.acceptThreshold()) >= 0) {
            Integer measured = submission.previousReading() == null
                    ? null : extraction.reading() - submission.previousReading();
            CritiqueHandoff handoff = new CritiqueHandoff(
                    submission.id(), submission.unitId(), submission.reference(),
                    extraction.reading(), submission.previousReading(), measured,
                    submission.history(), score.total(), submission.photo().artifactRef(),
                    geo.distanceMetres());
            List<SelfReadingReason> reasons = new ArrayList<>(notes);
            reasons.add(SelfReadingReason.ACCEPTED);
            return outcome(submission, SelfReadingStatus.ACCEPTED_FOR_CRITIQUE,
                    reasons, score, extraction.reading(), geo, handoff);
        }
        List<SelfReadingReason> reasons = new ArrayList<>(notes);
        reasons.add(SelfReadingReason.SCORE_BELOW_THRESHOLD);
        return outcome(submission, SelfReadingStatus.PENDING_HUMAN_REVIEW,
                reasons, score, extraction.reading(), geo, null);
    }

    private SelfReadingOutcome outcome(SelfReadingSubmission s, SelfReadingStatus status,
                                       List<SelfReadingReason> reasons, ReadingScore score,
                                       Integer ocrReading, GeoProofResult geo,
                                       CritiqueHandoff handoff) {
        return new SelfReadingOutcome(s.id(), s.unitId(), s.reference(), status,
                reasons, score, ocrReading, geo, handoff);
    }
}
