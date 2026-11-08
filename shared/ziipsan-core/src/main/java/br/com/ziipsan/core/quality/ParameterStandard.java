package br.com.ziipsan.core.quality;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma linha do catalogo normativo de qualidade da agua: o padrao de UM
 * parametro, numa versao do catalogo, com vigencia.
 *
 * <p>REGRA DURA da ED-50: NENHUM limite nasce nesta classe nem em dado
 * de fabrica. Limite, unidade e referencia normativa entram SOMENTE
 * pela carga do catalogo, depois de revisados pelo responsavel tecnico
 * (aceite: "catalogo da Portaria GM/MS 888/2021 revisado por
 * responsavel tecnico"). Linha sem revisao registrada
 * ({@code reviewedByTechnicalResponsible = false}) existe no catalogo
 * como rascunho, mas NUNCA fundamenta conformidade — o avaliador a
 * trata como padrao ausente (ver {@link ConformityEvaluator}).
 *
 * <p>Comparadores:
 * <ul>
 *   <li>{@code MAX}: conforme se valor &le; {@code limitMax};</li>
 *   <li>{@code MIN}: conforme se valor &ge; {@code limitMin};</li>
 *   <li>{@code RANGE}: conforme se {@code limitMin} &le; valor &le; {@code limitMax};</li>
 *   <li>{@code QUALITATIVE_EQUALS}: conforme se o resultado qualitativo
 *       do laboratorio e igual a {@code qualitativeExpected} (ex.:
 *       ausencia/presenca — o TEXTO esperado vem da carga revisada,
 *       nunca desta classe).</li>
 * </ul>
 *
 * @param healthRiskRelevant marcacao DA CARGA REVISADA: parametro cuja
 *        nao conformidade representa risco a saude e dispara a
 *        comunicacao da ED-50 (JOR-22). Nenhum parametro e marcado
 *        por fabrica.
 */
public record ParameterStandard(
        String parameterKey,
        String displayName,
        String unit,
        Comparator comparator,
        BigDecimal limitMin,
        BigDecimal limitMax,
        String qualitativeExpected,
        String normReference,
        boolean healthRiskRelevant,
        boolean reviewedByTechnicalResponsible,
        String reviewerRef,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    public enum Comparator { MAX, MIN, RANGE, QUALITATIVE_EQUALS }

    public ParameterStandard {
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parameterKey e obrigatorio");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName e obrigatorio para " + parameterKey);
        if (comparator == null)
            throw new IllegalArgumentException("comparator e obrigatorio para " + parameterKey);
        if (normReference == null || normReference.isBlank())
            throw new IllegalArgumentException("normReference e obrigatoria para " + parameterKey
                    + ": padrao sem referencia normativa nomeada nao entra no catalogo");
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

    /** Texto da referencia/limite como reportado pelo catalogo (para o bloco da conta da P2.24). */
    public String referenceText() {
        return switch (comparator) {
            case MAX -> "<= " + limitMax.toPlainString();
            case MIN -> ">= " + limitMin.toPlainString();
            case RANGE -> limitMin.toPlainString() + " a " + limitMax.toPlainString();
            case QUALITATIVE_EQUALS -> qualitativeExpected;
        };
    }
}
