package br.com.ziipsan.core.effluent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do catalogo de padrao de efluente: o padrao de UM
 * parametro, numa esfera (federal ou estadual), com vigencia —
 * inclusive vigencia FUTURA (a versao nova convive carregada com a
 * vigente ate a data de efeito; ver {@link EffluentCatalog}).
 *
 * <p>REGRA DURA 1 — nenhum limite nasce aqui: limite, unidade e
 * referencia normativa entram SOMENTE pela carga do catalogo de
 * normas, revisada pelo responsavel tecnico, espelhando a disciplina
 * da ED-50. Linha nao revisada existe como carga, mas NUNCA
 * fundamenta conformidade.
 *
 * <p>REGRA DURA 2 — norma nao publicada nao entra: a CONAMA 430/2011
 * esta em revisao (nova resolucao aprovada em 02/09/2026, SEM
 * publicacao, fato dado pelo aceite). Linha com
 * {@code publicationStatus = UNPUBLISHED} e RECUSADA na construcao:
 * os limites novos so entram no catalogo DEPOIS da publicacao, como
 * nova versao do catalogo com data de efeito — nunca como constante,
 * nunca como linha vigente antecipada.
 */
public record EffluentStandard(
        String parameterKey,
        String displayName,
        String unit,
        Comparator comparator,
        BigDecimal limitMin,
        BigDecimal limitMax,
        String qualitativeExpected,
        String normReference,
        Jurisdiction jurisdiction,
        String stateCode,
        PublicationStatus publicationStatus,
        boolean reviewedByTechnicalResponsible,
        String reviewerRef,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    public enum Comparator { MAX, MIN, RANGE, QUALITATIVE_EQUALS }

    /** Esfera do padrao: os padroes federais e os estaduais convivem no mesmo catalogo. */
    public enum Jurisdiction { FEDERAL, STATE }

    /** Situacao de publicacao da norma-fonte da linha. */
    public enum PublicationStatus { PUBLISHED, UNPUBLISHED }

    public EffluentStandard {
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName e obrigatorio para " + parameterKey);
        if (comparator == null)
            throw new IllegalArgumentException("comparator e obrigatorio para " + parameterKey);
        if (normReference == null || normReference.isBlank())
            throw new IllegalArgumentException("normReference e obrigatoria para " + parameterKey
                    + ": padrao sem referencia normativa nomeada nao entra no catalogo");
        if (jurisdiction == null)
            throw new IllegalArgumentException("jurisdiction e obrigatoria para " + parameterKey);
        if (jurisdiction == Jurisdiction.STATE && (stateCode == null || stateCode.isBlank()))
            throw new IllegalArgumentException("padrao estadual exige stateCode para " + parameterKey);
        if (jurisdiction == Jurisdiction.FEDERAL && stateCode != null)
            throw new IllegalArgumentException("padrao federal nao admite stateCode para " + parameterKey);
        if (publicationStatus == null)
            throw new IllegalArgumentException("publicationStatus e obrigatorio para " + parameterKey);
        if (publicationStatus == PublicationStatus.UNPUBLISHED)
            throw new IllegalArgumentException("padrao de " + parameterKey
                    + " vem de norma aprovada e NAO publicada (" + normReference + "):"
                    + " nao entra no catalogo — os limites novos so entram depois da"
                    + " publicacao, como nova versao com data de efeito (aceite da ED-105)");
        switch (comparator) {
            case MAX -> {
                if (limitMax == null) throw new IllegalArgumentException(
                        "padrao MAX exige limitMax (da carga revisada) para " + parameterKey);
                if (limitMin != null || qualitativeExpected != null) throw new IllegalArgumentException(
                        "padrao MAX nao admite limitMin nem qualitativeExpected para " + parameterKey);
            }
            case MIN -> {
                if (limitMin == null) throw new IllegalArgumentException(
                        "padrao MIN exige limitMin (da carga revisada) para " + parameterKey);
                if (limitMax != null || qualitativeExpected != null) throw new IllegalArgumentException(
                        "padrao MIN nao admite limitMax nem qualitativeExpected para " + parameterKey);
            }
            case RANGE -> {
                if (limitMin == null || limitMax == null) throw new IllegalArgumentException(
                        "padrao RANGE exige limitMin e limitMax (da carga revisada) para " + parameterKey);
                if (limitMin.compareTo(limitMax) > 0) throw new IllegalArgumentException(
                        "padrao RANGE com limitMin maior que limitMax para " + parameterKey);
                if (qualitativeExpected != null) throw new IllegalArgumentException(
                        "padrao RANGE nao admite qualitativeExpected para " + parameterKey);
            }
            case QUALITATIVE_EQUALS -> {
                if (qualitativeExpected == null || qualitativeExpected.isBlank())
                    throw new IllegalArgumentException("padrao QUALITATIVE_EQUALS exige qualitativeExpected"
                            + " (da carga revisada) para " + parameterKey);
                if (limitMin != null || limitMax != null) throw new IllegalArgumentException(
                        "padrao QUALITATIVE_EQUALS nao admite limites numericos para " + parameterKey);
            }
        }
        if (reviewedByTechnicalResponsible && (reviewerRef == null || reviewerRef.isBlank()))
            throw new IllegalArgumentException("revisao do catalogo exige o responsavel tecnico"
                    + " identificado (reviewerRef) para " + parameterKey);
        if (effectiveFrom == null)
            throw new IllegalArgumentException("effectiveFrom e obrigatorio para " + parameterKey);
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom))
            throw new IllegalArgumentException("effectiveTo anterior a effectiveFrom para " + parameterKey);
    }

    public boolean effectiveOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    /** Chave da esfera para unicidade/sobreposicao: FEDERAL ou STATE:&lt;UF&gt;. */
    public String jurisdictionKey() {
        return jurisdiction == Jurisdiction.FEDERAL ? "FEDERAL" : "STATE:" + stateCode;
    }
}
