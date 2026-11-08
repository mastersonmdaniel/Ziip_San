package br.com.ziipsan.core.certification.env14001;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A avaliacao de significancia de UM aspecto sob UM criterio do
 * tenant (aceite da ED-70): escores por fator declarados pelo
 * avaliador, total calculado (nunca declarado) e o veredito derivado
 * do limiar do criterio — {@code total >= threshold} e significativo.
 *
 * <p>Tra travas: escore para fator fora do criterio e recusado;
 * escore fora de [0, maximo do fator] e recusado; fator do criterio
 * sem escore e recusado (avaliacao parcial nao produz veredito). O
 * aspecto avaliado deve ser do mesmo tenant do criterio. Registro da
 * norma: a ativacao e COMPONENTE do registro, junto do criterio que
 * sustenta a avaliacao, e o CONSTRUTOR CANONICO confere ativacao
 * ligada, criterio coerente (id/tenant), total == soma dos escores,
 * total >= 0 e veredito derivado do limiar do criterio — total e
 * veredito declarados em divergencia sao recusados em qualquer
 * caminho de construcao, nao so na fabrica (correcao D1 da revisao
 * independente da ED-70, no padrao reforcado da ED-73/D1: fabrica
 * correta nao fecha construtor canonico aberto).</p>
 */
public record SignificanceEvaluation(Iso14001Activation activation, SignificanceCriteria criteria,
                                     UUID id, UUID aspectId, UUID criteriaId, UUID tenantId,
                                     Map<String, BigDecimal> scores, BigDecimal total,
                                     boolean significant, UUID evaluatedBy, LocalDate evaluatedOn) {

    public SignificanceEvaluation {
        Objects.requireNonNull(activation, "activation e obrigatoria (componente do registro)");
        activation.requireEnabled();
        Objects.requireNonNull(criteria, "criteria e obrigatorio (componente do registro: "
                + "e o criterio do tenant que sustenta total e veredito)");
        if (!activation.equals(criteria.activation())) {
            throw new IllegalArgumentException("activation da avaliacao diverge da activation "
                    + "do criterio: a avaliacao nasce sob a mesma ativacao do criterio");
        }
        Objects.requireNonNull(id, "id da avaliacao e obrigatorio");
        Objects.requireNonNull(aspectId, "aspectId da avaliacao e obrigatorio");
        Objects.requireNonNull(criteriaId, "criteriaId da avaliacao e obrigatorio");
        if (!criteriaId.equals(criteria.id())) {
            throw new IllegalArgumentException("criteriaId " + criteriaId + " diverge do criterio "
                    + "declarado (" + criteria.id() + ")");
        }
        Objects.requireNonNull(tenantId, "tenantId da avaliacao e obrigatorio");
        if (!tenantId.equals(criteria.tenantId())) {
            throw new IllegalArgumentException("tenantId da avaliacao diverge do tenant do "
                    + "criterio: a avaliacao de significancia e por tenant");
        }
        Objects.requireNonNull(scores, "scores da avaliacao sao obrigatorios");
        for (String key : scores.keySet()) {
            if (criteria.factor(key).isEmpty()) {
                throw new IllegalArgumentException("escore para fator fora do criterio: " + key);
            }
        }
        BigDecimal computedTotal = BigDecimal.ZERO;
        for (SignificanceCriteria.Factor factor : criteria.factors()) {
            BigDecimal score = scores.get(factor.name());
            if (score == null) {
                throw new IllegalArgumentException("fator do criterio sem escore: " + factor.name()
                        + " (avaliacao parcial nao produz veredito)");
            }
            if (score.signum() < 0 || score.compareTo(factor.maxScore()) > 0) {
                throw new IllegalArgumentException("escore " + score + " fora de [0, "
                        + factor.maxScore() + "] para o fator " + factor.name());
            }
            computedTotal = computedTotal.add(score);
        }
        Objects.requireNonNull(total, "total da avaliacao e obrigatorio");
        if (total.signum() < 0) {
            throw new IllegalArgumentException("total da avaliacao nao pode ser negativo: " + total);
        }
        if (total.compareTo(computedTotal) != 0) {
            throw new IllegalArgumentException("total declarado " + total + " diverge da soma "
                    + "dos escores " + computedTotal + ": o total e calculado, nunca declarado");
        }
        boolean expectedSignificant = total.compareTo(criteria.threshold()) >= 0;
        if (significant != expectedSignificant) {
            throw new IllegalArgumentException("veredito declarado (significant=" + significant
                    + ") diverge do limiar do criterio (" + criteria.threshold() + " para o total "
                    + total + "): o veredito e derivado do limiar, nunca declarado livremente");
        }
        Objects.requireNonNull(evaluatedBy, "evaluatedBy (avaliador) e obrigatorio");
        Objects.requireNonNull(evaluatedOn, "evaluatedOn e obrigatorio");
        scores = Map.copyOf(scores);
    }

    /** Avalia o aspecto sob o criterio, calculando total e veredito. */
    public static SignificanceEvaluation evaluate(EnvironmentalAspect aspect,
                                                  SignificanceCriteria criteria,
                                                  Map<String, BigDecimal> scores,
                                                  UUID evaluatedBy, LocalDate evaluatedOn) {
        Objects.requireNonNull(aspect, "aspecto avaliado e obrigatorio");
        Objects.requireNonNull(criteria, "criterio de significancia e obrigatorio");
        Objects.requireNonNull(scores, "scores sao obrigatorios (um por fator do criterio)");
        if (!aspect.tenantId().equals(criteria.tenantId())) {
            throw new IllegalArgumentException("aspecto e criterio de tenants diferentes: "
                    + "a avaliacao de significancia e por tenant");
        }
        for (String key : scores.keySet()) {
            if (criteria.factor(key).isEmpty()) {
                throw new IllegalArgumentException("escore para fator fora do criterio: " + key);
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SignificanceCriteria.Factor factor : criteria.factors()) {
            BigDecimal score = scores.get(factor.name());
            if (score == null) {
                throw new IllegalArgumentException("fator do criterio sem escore: " + factor.name()
                        + " (avaliacao parcial nao produz veredito)");
            }
            if (score.signum() < 0 || score.compareTo(factor.maxScore()) > 0) {
                throw new IllegalArgumentException("escore " + score + " fora de [0, "
                        + factor.maxScore() + "] para o fator " + factor.name());
            }
            total = total.add(score);
        }
        boolean significant = total.compareTo(criteria.threshold()) >= 0;
        return new SignificanceEvaluation(aspect.activation(), criteria, UUID.randomUUID(),
                aspect.id(), criteria.id(), aspect.tenantId(), scores, total, significant,
                evaluatedBy, evaluatedOn);
    }
}
