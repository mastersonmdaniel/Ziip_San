package br.com.ziipsan.core.certification.env14001;

import br.com.ziipsan.core.sanitation.SanitationAspectCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignificanceTest {

    private SignificanceCriteria criteria() {
        return new SignificanceCriteria(TestSupport.enabledTarget(), TestSupport.id(),
                TestSupport.TENANT, "SYN-criterio do tenant",
                List.of(new SignificanceCriteria.Factor("SYN-severidade", new BigDecimal("5")),
                        new SignificanceCriteria.Factor("SYN-frequencia", new BigDecimal("5"))),
                new BigDecimal("7"));
    }

    private EnvironmentalAspect aspect() {
        return EnvironmentalAspect.fromTemplate(TestSupport.enabledTarget(), TestSupport.id(),
                TestSupport.TENANT, "NETWORK-WATER-LOSS", EnvironmentalAspect.Situation.NORMAL);
    }

    @Test
    void tenantConfiguredCriteriaComputesTotalAndVerdict() {
        SignificanceEvaluation ev = SignificanceEvaluation.evaluate(aspect(), criteria(),
                Map.of("SYN-severidade", new BigDecimal("4"), "SYN-frequencia", new BigDecimal("3")),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(ev.total()).isEqualByComparingTo("7");
        assertThat(ev.significant()).isTrue();

        SignificanceEvaluation below = SignificanceEvaluation.evaluate(aspect(), criteria(),
                Map.of("SYN-severidade", new BigDecimal("2"), "SYN-frequencia", new BigDecimal("2")),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(below.total()).isEqualByComparingTo("4");
        assertThat(below.significant()).isFalse();
    }

    @Test
    void noFactoryCriteriaExists() {
        assertThatThrownBy(() -> new SignificanceCriteria(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "SYN-vazio", List.of(), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nenhum fator de fabrica");
        assertThatThrownBy(() -> new SignificanceCriteria(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.TENANT, "SYN-limiar-fora",
                List.of(new SignificanceCriteria.Factor("SYN-f", new BigDecimal("5"))),
                new BigDecimal("6")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void partialOrOutOfRangeScoresRefused() {
        assertThatThrownBy(() -> SignificanceEvaluation.evaluate(aspect(), criteria(),
                Map.of("SYN-severidade", new BigDecimal("4")),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem escore");
        assertThatThrownBy(() -> SignificanceEvaluation.evaluate(aspect(), criteria(),
                Map.of("SYN-severidade", new BigDecimal("6"), "SYN-frequencia", BigDecimal.ONE),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SignificanceEvaluation.evaluate(aspect(), criteria(),
                Map.of("SYN-severidade", BigDecimal.ONE, "SYN-frequencia", BigDecimal.ONE,
                        "SYN-desconhecido", BigDecimal.ONE),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do criterio");
    }

    @Test
    void crossTenantEvaluationRefusedAndDisabledNormRefusesCriteria() {
        EnvironmentalAspect foreign = new EnvironmentalAspect(TestSupport.enabledTarget(),
                TestSupport.id(), TestSupport.OTHER_TENANT, null,
                SanitationAspectCatalog.AspectArea.WTP, "SYN-a", "SYN-i",
                EnvironmentalAspect.Situation.NORMAL, false);
        assertThatThrownBy(() -> SignificanceEvaluation.evaluate(foreign, criteria(),
                Map.of("SYN-severidade", BigDecimal.ONE, "SYN-frequencia", BigDecimal.ONE),
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SignificanceCriteria(TestSupport.disabled(),
                TestSupport.id(), TestSupport.TENANT, "SYN-x",
                List.of(new SignificanceCriteria.Factor("SYN-f", BigDecimal.ONE)), BigDecimal.ONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void directConstructionWithDeclaredTotalOrFreeVerdictRefused() {
        // Correcao D1 da revisao da ED-70: o construtor canonico confere
        // total/veredito contra o criterio — os casos do probe do revisor
        // (total arbitrario 999, total -5, escore sem criterio) nascem
        // recusados por construcao direta, nao so pela fabrica evaluate.
        SignificanceCriteria criteria = criteria();
        EnvironmentalAspect aspect = aspect();
        Map<String, BigDecimal> scores = Map.of("SYN-severidade", BigDecimal.ONE,
                "SYN-frequencia", BigDecimal.ONE);
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT, scores,
                new BigDecimal("999"), true, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nunca declarado");
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT, scores,
                new BigDecimal("-5"), false, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT,
                Map.of("SYN-desconhecido", BigDecimal.ONE), BigDecimal.ONE, false,
                TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do criterio");
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT,
                Map.of(), BigDecimal.ZERO, false, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem escore");
        // Veredito livre: soma 7 atinge o limiar 7, logo significant=false e incoerente.
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT,
                Map.of("SYN-severidade", new BigDecimal("4"),
                        "SYN-frequencia", new BigDecimal("3")),
                new BigDecimal("7"), false, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("veredito");
    }

    @Test
    void directConstructionWithoutEnabledNormOrCoherentCriteriaRefused() {
        // Correcao D1: sem ativacao ligada nao nasce nem pelo construtor
        // canonico; criteriaId/tenant divergentes do criterio componente
        // tambem nao. A construcao direta COERENTE segue valida.
        SignificanceCriteria criteria = criteria();
        EnvironmentalAspect aspect = aspect();
        Map<String, BigDecimal> scores = Map.of("SYN-severidade", new BigDecimal("4"),
                "SYN-frequencia", new BigDecimal("3"));
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.disabled(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT, scores,
                new BigDecimal("7"), true, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), TestSupport.id(), TestSupport.TENANT, scores,
                new BigDecimal("7"), true, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("criteriaId");
        assertThatThrownBy(() -> new SignificanceEvaluation(TestSupport.enabledTarget(), criteria,
                TestSupport.id(), aspect.id(), criteria.id(), TestSupport.OTHER_TENANT, scores,
                new BigDecimal("7"), true, TestSupport.ACTOR, LocalDate.of(2026, 1, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant");
        SignificanceEvaluation direct = new SignificanceEvaluation(TestSupport.enabledTarget(),
                criteria, TestSupport.id(), aspect.id(), criteria.id(), TestSupport.TENANT,
                scores, new BigDecimal("7"), true, TestSupport.ACTOR, LocalDate.of(2026, 1, 10));
        assertThat(direct.total()).isEqualByComparingTo("7");
        assertThat(direct.significant()).isTrue();
        assertThat(direct.criteria()).isEqualTo(criteria);
        assertThat(direct.activation().enabled()).isTrue();
    }
}
