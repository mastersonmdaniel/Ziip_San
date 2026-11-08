package br.com.ziipsan.platform.engagement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marcos da gamificacao da ED-101, definidos pelo TENANT no parametro
 * proposto {@code eng.gamification_milestones} — SEM valor de fabrica:
 * nenhuma fonte da tarefa fixa limiar, codigo de marco nem ponto, e este
 * codigo nao inventa nenhum. Sem o parametro, nao ha marco concedido.
 *
 * <p>Formato PROPOSTO (a confirmar na integracao, como as chaves novas da
 * ED-41): pares {@code fracao=CODIGO} separados por {@code ;}, onde a
 * fracao e a parcela da meta (0 &lt; fracao &le; 1) e o codigo identifica o
 * marco nas telas do tenant. Ex.: {@code "0.90=ABAIXO_DA_META;1.00=META_CUMPRIDA"}.
 * Semantica: com o periodo fechado, o marco de fracao F e atingido quando
 * o consumo realizado ficou em ate F x meta ({@code ratio <= F}) — marcos
 * da meta do PROPRIO usuario, como manda o aceite.
 */
public final class GamificationMilestones {

    private GamificationMilestones() {
    }

    public record MilestoneDefinition(BigDecimal fractionOfTarget, String code) {
        public MilestoneDefinition {
            if (fractionOfTarget == null || fractionOfTarget.signum() <= 0
                    || fractionOfTarget.compareTo(BigDecimal.ONE) > 0) {
                throw new EngagementValidationException("MILESTONE_FRACTION_INVALID",
                        "fracao do marco deve estar em (0, 1]: " + fractionOfTarget);
            }
            if (code == null || !code.matches("[A-Z0-9_]{1,40}")) {
                throw new EngagementValidationException("MILESTONE_CODE_INVALID",
                        "codigo do marco deve ser [A-Z0-9_]{1,40}: " + code);
            }
        }
    }

    /** Le o valor cru de {@code eng.gamification_milestones}. */
    public static List<MilestoneDefinition> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Map<String, MilestoneDefinition> byCode = new LinkedHashMap<>();
        List<BigDecimal> fractions = new ArrayList<>();
        for (String part : raw.split(";")) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            String[] kv = item.split("=", 2);
            if (kv.length != 2) {
                throw new EngagementValidationException("MILESTONES_FORMAT_INVALID",
                        "item de eng.gamification_milestones sem '=': " + item);
            }
            BigDecimal fraction;
            try {
                fraction = new BigDecimal(kv[0].trim());
            } catch (NumberFormatException e) {
                throw new EngagementValidationException("MILESTONES_FORMAT_INVALID",
                        "fracao nao decimal em eng.gamification_milestones: " + kv[0]);
            }
            MilestoneDefinition def = new MilestoneDefinition(fraction, kv[1].trim());
            if (byCode.putIfAbsent(def.code(), def) != null) {
                throw new EngagementValidationException("MILESTONE_DUPLICATED",
                        "codigo de marco repetido: " + def.code());
            }
            if (fractions.contains(fraction)) {
                throw new EngagementValidationException("MILESTONE_DUPLICATED",
                        "fracao de marco repetida: " + fraction);
            }
            fractions.add(fraction);
        }
        return List.copyOf(byCode.values());
    }
}
