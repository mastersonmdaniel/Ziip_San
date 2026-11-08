package br.com.ziipsan.core.stationops;

import br.com.ziipsan.core.quality.AnalysisResult;
import br.com.ziipsan.core.quality.ConformityEvaluator;
import br.com.ziipsan.core.quality.MonthlyClosing;
import br.com.ziipsan.core.quality.NormativeCatalog;
import br.com.ziipsan.core.quality.Sample;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JOR-21: os dados de controle de processo do diario (turbidez por
 * filtro, cloro e pH na saida) alimentam a conformidade mensal da
 * ED-50 e o arquivo do SISAGUA SEM REDIGITACAO. Esta classe e a
 * conversao UNICA e deterministica diario -> ED-50: cada leitura de
 * processo vira uma amostra {@code PROCESS_CONTROL} da ED-50 (o tipo
 * que a ED-50 reserva exatamente para esta origem) com o resultado
 * numerico lancado, e a entrada do fechamento mensal correspondente.
 *
 * <p>Regras da conversao (declaradas, sem improviso):</p>
 * <ul>
 *   <li>a etiqueta da amostra e derivada da leitura
 *       ({@code ED87-<unidade>-<grandeza>[-<filtro>]-<epochMilli>}) —
 *       deterministica, nunca redigitada;</li>
 *   <li>a custodia da amostra de processo e registrada com o operador
 *       do diario como ator dos eventos e o recebimento pelo
 *       "laboratorio" e o recebimento do lancamento de processo —
 *       o portao JOR-37 da ED-50 (resultado so sobre amostra
 *       recebida) e atravessado pelo caminho normal, nao contornado;</li>
 *   <li>o resultado nasce validado por {@code validatorRef} explicito
 *       (o fechamento da ED-50 so recebe resultado validado);</li>
 *   <li>a avaliacao e a do {@link ConformityEvaluator} da ED-50
 *       contra o catalogo do tenant: sem padrao revisado, o veredito
 *       e INDETERMINATE — o diario nao cria conformidade presumida.</li>
 * </ul>
 */
public final class ProcessControlFeed {

    private ProcessControlFeed() {}

    /** Etiqueta deterministica da amostra de processo de uma leitura do diario. */
    public static String labelFor(ShiftLog log, DiaryReading reading) {
        String base = "ED87-" + log.unit().code() + "-" + reading.kind().name()
                + (reading.filterUnitCode() == null ? "" : "-" + reading.filterUnitCode())
                + "-" + reading.measuredAt().toEpochMilli();
        return base;
    }

    /**
     * Converte UMA leitura de processo na entrada do fechamento da
     * ED-50 (amostra + resultado validado + avaliacao).
     *
     * @param pointId ponto da ED-50 que representa a saida da unidade
     *                (o cadastro do ponto e da ED-50; a fiacao resolve
     *                unidade -> ponto)
     */
    public static MonthlyClosing.Entry toEntry(ShiftLog log, DiaryReading reading, UUID pointId,
                                               NormativeCatalog catalog, String validatorRef) {
        if (log == null || reading == null || pointId == null || catalog == null)
            throw new IllegalArgumentException("diario, leitura, ponto e catalogo sao obrigatorios");
        if (!reading.isProcessControl())
            throw new IllegalArgumentException("leitura " + reading.kind()
                    + " nao e de controle de processo: so turbidez, cloro e pH alimentam a ED-50 (JOR-21)");
        if (validatorRef == null || validatorRef.isBlank())
            throw new IllegalArgumentException("validatorRef e obrigatorio (o fechamento so recebe validado)");

        Sample sample = Sample.planned(UUID.randomUUID(), log.tenantId(), pointId,
                Sample.SampleType.PROCESS_CONTROL, labelFor(log, reading), null, null);
        java.time.Instant at = reading.measuredAt();
        sample.collect(log.openedByRef(), at);
        sample.sealAndDispatch(log.openedByRef(), at);
        sample.receiveByLab(log.openedByRef(), at);
        AnalysisResult result = AnalysisResult.firstVersion(UUID.randomUUID(), sample,
                reading.parameterKey(), reading.value(), null, reading.unitOfMeasure(),
                null, log.openedByRef(), at).validate(validatorRef);
        sample.markAnalyzed();
        sample.markValidated();
        ConformityEvaluator.Assessment assessment = ConformityEvaluator.evaluate(
                result, catalog, log.shiftDate());
        return new MonthlyClosing.Entry(sample, result, assessment);
    }

    /** Converte TODAS as leituras de processo do diario, na ordem lancada. */
    public static List<MonthlyClosing.Entry> toEntries(ShiftLog log, UUID pointId,
                                                       NormativeCatalog catalog, String validatorRef) {
        List<MonthlyClosing.Entry> out = new ArrayList<>();
        for (DiaryReading reading : log.processReadings()) {
            out.add(toEntry(log, reading, pointId, catalog, validatorRef));
        }
        return out;
    }
}
