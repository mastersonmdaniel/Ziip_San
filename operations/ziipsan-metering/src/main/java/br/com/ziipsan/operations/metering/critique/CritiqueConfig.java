package br.com.ziipsan.operations.metering.critique;

import java.util.List;
import java.util.Objects;

/**
 * O conjunto ordenado de regras de critica de um tenant (II-2). A ordem da
 * lista e a ordem dos disparos no resultado -- configuracao e dado, o motor
 * nao tem regras embutidas.
 *
 * <p>{@link #proofDefaults()} e CONSTRUCAO DE PROVA: os limiares 50% / faixa
 * [0, 200] existem so para os testes fixture exercitarem o mecanismo. NAO
 * sao valores operacionais de nenhum tenant nem de nenhum regulador --
 * pendencia P-II2-LIMIARES: os limiares reais sao {@code [A DEFINIR]} pelo
 * tenant/regulador e entram por configuracao.
 */
public record CritiqueConfig(List<CritiqueRule> rules) {

    public CritiqueConfig {
        Objects.requireNonNull(rules, "rules");
        rules = List.copyOf(rules);
    }

    /** Configuracao de prova (ver cabecalho): NAO e omissao operacional. */
    public static CritiqueConfig proofDefaults() {
        return new CritiqueConfig(List.of(
                CritiqueRule.simple("PROVA-AUSENTE", CritiqueRuleKind.MISSING_READING),
                CritiqueRule.simple("PROVA-NEGATIVA", CritiqueRuleKind.NEGATIVE_OR_REGRESSING_READING),
                CritiqueRule.simple("PROVA-ZERO", CritiqueRuleKind.ZERO_CONSUMPTION),
                CritiqueRule.deviationAbove("PROVA-DESVIO-ALTA", "50"),
                CritiqueRule.deviationBelow("PROVA-DESVIO-BAIXA", "50"),
                CritiqueRule.band("PROVA-FAIXA", 0, 200)));
    }
}
