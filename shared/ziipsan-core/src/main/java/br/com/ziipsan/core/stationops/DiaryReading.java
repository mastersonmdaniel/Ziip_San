package br.com.ziipsan.core.stationops;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma leitura lancada no diario de operacao (ED-87): nivel, vazao,
 * pressao ou dado de controle de processo (turbidez por filtro, cloro
 * e pH na saida — JOR-21). Toda leitura do diario e, por construcao,
 * uma leitura MANUAL: ela e gravada como serie na tabela do
 * supervisório da ED-17 com origem {@code MANUAL} pela porta
 * {@link StationOpsPorts.ManualSeriesPort} (ver
 * {@link StationOpsService}).
 *
 * <p>{@code tagCode} e a chave da serie na ED-17 (estacao + grandeza +
 * filtro, quando houver) — deterministica a partir dos campos, para a
 * fiacao nao redigitar nem renomear a serie.</p>
 *
 * <p>TURBIDITY exige a unidade filtrante, e a unidade filtrante precisa
 * pertencer a unidade do diario — a validacao cruzada com a unidade
 * vive em {@link ShiftLog#addReading}.</p>
 */
public record DiaryReading(
        Kind kind,
        String filterUnitCode,
        BigDecimal value,
        String unitOfMeasure,
        Instant measuredAt) {

    /** Grandezas do diario nomeadas no aceite da ED-87. */
    public enum Kind { LEVEL, FLOW, PRESSURE, TURBIDITY, CHLORINE, PH }

    public DiaryReading {
        if (kind == null) throw new IllegalArgumentException("grandeza da leitura e obrigatoria");
        if (value == null) throw new IllegalArgumentException("valor da leitura de " + kind + " e obrigatorio");
        if (unitOfMeasure == null || unitOfMeasure.isBlank())
            throw new IllegalArgumentException("unidade de medida da leitura de " + kind + " e obrigatoria");
        if (measuredAt == null) throw new IllegalArgumentException("instante da leitura de " + kind + " e obrigatorio");
        if (kind == Kind.TURBIDITY && (filterUnitCode == null || filterUnitCode.isBlank()))
            throw new IllegalArgumentException("turbidez e por unidade filtrante: filterUnitCode e obrigatorio");
        if (kind != Kind.TURBIDITY && filterUnitCode != null)
            throw new IllegalArgumentException("filterUnitCode so existe na turbidez por filtro");
    }

    /** Dado de controle de processo que alimenta a ED-50 (JOR-21): turbidez, cloro e pH. */
    public boolean isProcessControl() {
        return kind == Kind.TURBIDITY || kind == Kind.CHLORINE || kind == Kind.PH;
    }

    /**
     * Chave da serie na tabela da ED-17 e chave do parametro na ED-50.
     * Para o processo, a chave e a propria grandeza (TURBIDITY/CHLORINE/
     * PH sao chaves estruturais, nao limites — nenhum valor regulatorio
     * esta embutido nelas).
     */
    public String parameterKey() { return kind.name(); }

    /** Chave deterministica da serie (ED-17): {@code <unidade>:<grandeza>[:<filtro>]}. */
    public String seriesTag(String stationCode) {
        if (stationCode == null || stationCode.isBlank())
            throw new IllegalArgumentException("stationCode e obrigatorio para a chave da serie");
        return filterUnitCode == null
                ? stationCode + ":" + kind.name()
                : stationCode + ":" + kind.name() + ":" + filterUnitCode;
    }

    /** Chave de idempotencia da gravacao na serie (reenvio nao duplica). */
    public String seriesSampleKey(String stationCode) {
        return "MANUAL:" + seriesTag(stationCode) + ":" + measuredAt.toEpochMilli();
    }
}
