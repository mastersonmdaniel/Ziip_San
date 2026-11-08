package br.com.ziipsan.commercial.billing.special;

import java.time.YearMonth;
import java.util.UUID;

/**
 * Registro de uma situacao especial de faturamento, conforme o dicionario
 * do 23b S3.17 (DAD-22) nomeado no acceptance da ED-22: unidade, dominio
 * (de faturamento ou de cobranca), tipo, motivo, base legal, meses de
 * inicio, fim e retirada, volumes fixos de agua e esgoto, OS de abertura
 * e de encerramento e lote.
 *
 * @param unitId           unidade consumidora
 * @param domain           dominio da situacao: faturamento ou cobranca
 * @param type             tipo (com os sete indicadores de {@link SpecialSituationType})
 * @param reason           motivo (texto do atendimento; obrigatorio)
 * @param legalBasis       base legal; obrigatoria quando o tipo a exige (isencao)
 * @param startMonth       primeira competencia em que vale
 * @param endMonth         ultima competencia prevista; nula = sem fim previsto
 * @param withdrawnMonth   competencia da retirada (OS REMOVE); nula = nao retirada
 * @param fixedWaterVolume volume fixo de agua em m3; so com tipo que fixa agua
 * @param fixedSewerVolume volume fixo de esgoto em m3; so com tipo que fixa esgoto
 * @param openingOsId      OS de abertura ({@code SPECIAL_SITUATION_SET})
 * @param closingOsId      OS de encerramento ({@code SPECIAL_SITUATION_REMOVE})
 * @param batchId          lote (OS-mae) quando a inclusao foi em lote; nulo = individual
 */
public record SpecialSituationRecord(
        UUID id,
        UUID unitId,
        Domain domain,
        SpecialSituationType type,
        String reason,
        String legalBasis,
        YearMonth startMonth,
        YearMonth endMonth,
        YearMonth withdrawnMonth,
        Integer fixedWaterVolume,
        Integer fixedSewerVolume,
        UUID openingOsId,
        UUID closingOsId,
        UUID batchId) {

    /** Dominio da situacao especial: faturamento ou cobranca (23b S3.17). */
    public enum Domain { BILLING, CHARGING }

    public static final String OS_SET = "SPECIAL_SITUATION_SET";
    public static final String OS_REMOVE = "SPECIAL_SITUATION_REMOVE";

    public SpecialSituationRecord {
        if (id == null || unitId == null || domain == null || type == null) {
            throw new IllegalArgumentException("situacao especial exige id, unidade, dominio e tipo");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("situacao especial exige motivo");
        }
        if (startMonth == null) {
            throw new IllegalArgumentException("situacao especial exige mes de inicio");
        }
        if (type.requiresLegalBasis() && (legalBasis == null || legalBasis.isBlank())) {
            throw new IllegalArgumentException(
                    "tipo " + type + " exige base legal (isencao sem base legal e recusada)");
        }
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new IllegalArgumentException("mes de fim anterior ao de inicio: " + startMonth + ".." + endMonth);
        }
        if (withdrawnMonth != null && withdrawnMonth.isBefore(startMonth)) {
            throw new IllegalArgumentException("mes de retirada anterior ao de inicio: " + withdrawnMonth);
        }
        if (fixedWaterVolume != null && fixedWaterVolume < 0) {
            throw new IllegalArgumentException("volume fixo de agua negativo: " + fixedWaterVolume);
        }
        if (fixedSewerVolume != null && fixedSewerVolume < 0) {
            throw new IllegalArgumentException("volume fixo de esgoto negativo: " + fixedSewerVolume);
        }
        // Correcao pos-revisao (D2): tipo que fixa volume (indicadores
        // fixesWaterVolume/fixesSewerVolume ligados) com os DOIS volumes
        // nulos nao fixa nada e geraria consumo SPECIAL_SITUATION_FIXED sem
        // volume - recusado. Ao menos um volume nao nulo e exigido; o CHECK
        // correspondente esta no SQL pendente.
        if ((type.fixesWaterVolume() || type.fixesSewerVolume())
                && fixedWaterVolume == null && fixedSewerVolume == null) {
            throw new IllegalArgumentException("tipo " + type
                    + " fixa volume, mas nenhum volume fixo foi informado (agua e esgoto nulos)");
        }
        if (fixedWaterVolume != null && !type.fixesWaterVolume()) {
            throw new IllegalArgumentException("tipo " + type + " nao fixa volume de agua");
        }
        if (fixedSewerVolume != null && !type.fixesSewerVolume()) {
            throw new IllegalArgumentException("tipo " + type + " nao fixa volume de esgoto");
        }
        if (type.requiresServiceOrder() && openingOsId == null) {
            throw new IllegalArgumentException(
                    "situacao especial so e incluida por OS " + OS_SET + " (OS de abertura obrigatoria)");
        }
        if (withdrawnMonth != null && closingOsId == null) {
            throw new IllegalArgumentException(
                    "retirada de situacao especial exige OS " + OS_REMOVE + " (OS de encerramento)");
        }
    }

    /**
     * Ultima competencia efetiva: a retirada antecipa o fim previsto.
     *
     * <p><b>INTERPRETACAO PENDENTE (correcao pos-revisao, D4):</b> hoje o
     * {@code withdrawnMonth} e tratado como o <b>ultimo mes valido</b>
     * (inclusivo), por analogia ao {@code end} inclusivo do
     * {@code SpecialSituation} do metering. NENHUMA fonte local (acceptance
     * da ED-22 nem o metering) fixa se o mes de retirada e inclusivo ou
     * exclusivo ("primeiro mes que nao vale"); se o dicionario 23b S3.17
     * definir o contrario, ha um mes de faturamento a corrigir aqui e no
     * teste. A escolha atual fica declarada como pendente de confirmacao
     * nominal no 23b - nao e fato de fonte.
     */
    public YearMonth effectiveEndMonth() {
        if (withdrawnMonth != null) {
            return withdrawnMonth;
        }
        return endMonth;
    }

    /** A situacao vale na competencia? Inicio e fim inclusive (semantica do metering). */
    public boolean appliesTo(YearMonth competence) {
        if (competence == null) {
            throw new IllegalArgumentException("competencia e obrigatoria");
        }
        YearMonth end = effectiveEndMonth();
        return !competence.isBefore(startMonth) && (end == null || !competence.isAfter(end));
    }

    /** Duracao em meses (inicio..fim efetivo, inclusive); aberta (sem fim) devolve -1. */
    public long durationMonths() {
        YearMonth end = effectiveEndMonth();
        if (end == null) {
            return -1;
        }
        return (end.getYear() - startMonth.getYear()) * 12L
                + (end.getMonthValue() - startMonth.getMonthValue()) + 1;
    }

    /** Retirada: devolve o registro encerrado pela OS REMOVE na competencia dada. */
    public SpecialSituationRecord withdrawn(YearMonth month, UUID closingOs) {
        if (closingOs == null) {
            throw new IllegalArgumentException("retirada exige a OS " + OS_REMOVE);
        }
        return new SpecialSituationRecord(id, unitId, domain, type, reason, legalBasis, startMonth,
                endMonth, month, fixedWaterVolume, fixedSewerVolume, openingOsId, closingOs, batchId);
    }
}
