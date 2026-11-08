package br.com.ziipsan.core.inspection;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Fonte alternativa de agua nos criterios de fiscalizacao (aceite da
 * ED-24: "fonte alternativa de agua declarada no campo da P1.3 entra nos
 * criterios de fiscalizacao" — CAD-69 consumido da P1.3).
 *
 * <p>O campo da P1.3 e a tabela {@code unit_alternative_sources}: poco raso
 * ou profundo, captacao superficial, agua de chuva ou reuso, com
 * autorizacao do orgao gestor, medidor, volume captado e uso; e o parametro
 * {@code connection.alt_source_requires_authorization} (classe b) diz se a
 * autorizacao e exigida. Esta classe NAO redefine o cadastro: recebe o fato
 * ja declarado na P1.3 e decide o enquadramento nos criterios:
 * <ul>
 *   <li>toda fonte declarada poe a unidade no conjunto fiscalizavel E
 *       produz um {@link InspectionCriterion.CriterionHit} auditavel com
 *       o criterio {@link InspectionCriterion#ALTERNATIVE_SOURCE_DECLARED}
 *       (correcao D3 da revisao cruzada da ED-24: a entrada no conjunto
 *       nao e so um booleano do chamador — e um hit registravel na trilha
 *       de criterios, como os demais); esse hit, por si so, NAO abre OS
 *       (ver {@link Assessment#generatesInspectionOrder()});</li>
 *   <li>fonte declarada SEM autorizacao, quando o tenant a exige, gera hit
 *       proprio ({@link InspectionCriterion#ALTERNATIVE_SOURCE_UNAUTHORIZED}),
 *       que abre OS pelo {@link InspectionOrderPlanner};</li>
 *   <li>sem fonte declarada, nenhum criterio deste tipo se aplica.</li>
 * </ul>
 */
public final class AlternativeSourceCriterion {

    private AlternativeSourceCriterion() {}

    /** Tipos da fonte alternativa, espelhando o vocabulario da P1.3 (unit_alternative_sources). */
    public enum AlternativeSourceType {
        SHALLOW_WELL, DEEP_WELL, SURFACE_CAPTURE, RAINWATER, REUSE
    }

    /**
     * Fato declarado no campo da P1.3 para uma unidade. {@code hasAuthorization}
     * e fato do cadastro (autorizacao do orgao gestor registrada), nao
     * inferencia desta regra.
     */
    public record DeclaredAlternativeSource(UUID consumerUnitId, AlternativeSourceType sourceType,
                                            boolean hasAuthorization, boolean hasMeter,
                                            Long capturedVolumeM3, LocalDateTime declaredAt) {
        public DeclaredAlternativeSource {
            Objects.requireNonNull(consumerUnitId, "unidade e obrigatoria");
            Objects.requireNonNull(sourceType, "tipo da fonte alternativa e obrigatorio");
            if (capturedVolumeM3 != null && capturedVolumeM3 < 0)
                throw new IllegalArgumentException("volume captado nao pode ser negativo: " + capturedVolumeM3);
            Objects.requireNonNull(declaredAt, "data da declaracao (P1.3) e obrigatoria");
        }
    }

    /**
     * Resultado da avaliacao: {@code declaredHit} e o hit auditavel
     * {@code ALTERNATIVE_SOURCE_DECLARED} de toda fonte declarada (D3);
     * {@code unauthorizedHit} e o hit {@code ALTERNATIVE_SOURCE_UNAUTHORIZED},
     * unico que abre OS pelo planner.
     */
    public record Assessment(boolean entersInspectionCriteria,
                             InspectionCriterion.CriterionHit declaredHit,
                             InspectionCriterion.CriterionHit unauthorizedHit) {
        public boolean generatesInspectionOrder() {
            return unauthorizedHit != null;
        }
    }

    /**
     * @param declared                    fato da P1.3, ou {@code null} quando a unidade nao declarou fonte
     * @param authorizationRequiredByTenant valor de {@code connection.alt_source_requires_authorization} do tenant
     */
    public static Assessment assess(DeclaredAlternativeSource declared, boolean authorizationRequiredByTenant) {
        if (declared == null) {
            return new Assessment(false, null, null);
        }
        // D3: a fonte declarada se materializa como CriterionHit auditavel
        // ALTERNATIVE_SOURCE_DECLARED, nao apenas como o booleano
        // entersInspectionCriteria.
        InspectionCriterion.CriterionHit declaredHit = new InspectionCriterion.CriterionHit(
                InspectionCriterion.ALTERNATIVE_SOURCE_DECLARED, declared.consumerUnitId(),
                "fonte alternativa " + declared.sourceType() + " declarada no campo da P1.3"
                        + " (unit_alternative_sources, CAD-69)",
                declared.declaredAt());
        InspectionCriterion.CriterionHit hit = null;
        if (authorizationRequiredByTenant && !declared.hasAuthorization()) {
            hit = new InspectionCriterion.CriterionHit(
                    InspectionCriterion.ALTERNATIVE_SOURCE_UNAUTHORIZED, declared.consumerUnitId(),
                    "fonte alternativa " + declared.sourceType() + " declarada (P1.3) sem autorizacao do orgao gestor",
                    declared.declaredAt());
        }
        return new Assessment(true, declaredHit, hit);
    }
}
