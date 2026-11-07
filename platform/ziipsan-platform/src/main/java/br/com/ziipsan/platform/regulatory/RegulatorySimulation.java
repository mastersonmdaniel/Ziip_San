package br.com.ziipsan.platform.regulatory;

import br.com.ziipsan.core.json.Json;
import br.com.ziipsan.platform.regulatory.RegulatoryProfileDef.ProfileValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Simulação pré-ativação de um perfil regulatório (P2.31), sobre a massa
 * sintética: mostra a diferença de cada chave entre o valor vigente do
 * tenant e o valor do perfil, o efeito na conta-tipo (multa, juros, média
 * e ajuste por dias) e as divergências e chaves pendentes do tenant.
 *
 * <p>SEM GRAVAÇÃO, por construção: esta classe não conhece
 * {@code DataSource}, conexão nem repositório — ela recebe valores e
 * devolve um resultado. Quem persiste é só a ativação, pelo envelope da
 * P0.19 ({@code RegulatoryProfileActivationService}), depois de proposta,
 * aprovação por outra pessoa e aceite formal.
 *
 * <p><b>ESTIMATIVA, não prévia fiel da P2.5</b> (limitação declarada,
 * correção pós-revisão): os efeitos numéricos abaixo são uma
 * REIMPLEMENTAÇÃO simplificada no platform — multa + juros simples sobre
 * a base, média aritmética e fator de dias {@code dias/referência}
 * (4 casas HALF_UP). Eles NÃO reproduzem o cálculo autoritativo do
 * billing: o {@code PartialPeriodRule} real distingue primeira/final de
 * ciclo regular e normaliza o consumo medido de ciclo regular fora da
 * faixa pela média diária; os encargos reais consideram
 * {@code fee.interest_mode} e o índice de correção. Reusar as classes
 * reais exigiria a dependência platform → billing, que a arquitetura
 * não admite (o platform é módulo de fundação; o billing, comercial,
 * depende do core — a regra pura de período parcial vive no billing e
 * não há módulo comum abaixo dos dois onde ela possa morar hoje sem
 * mover a P2.5). O resultado carrega o rótulo em
 * {@link SimulationResult#notice()}: a tela deve exibi-lo como
 * estimativa. O cálculo autoritativo continua sendo o do billing.
 */
public final class RegulatorySimulation {

    /** Faixa de referência do ajuste por dias quando o perfil não a confirma (P2.5). */
    public static final int REFERENCE_BAND_MIN_DAYS = 27;
    public static final int REFERENCE_BAND_MAX_DAYS = 33;

    private RegulatorySimulation() {
    }

    /** Conta-tipo sintética da simulação (nenhum dado real de usuário). */
    public record SyntheticAccount(
            BigDecimal baseAmount,
            int daysLate,
            List<Integer> lastConsumptions,
            int estimatedCyclesUsed,
            int periodDays,
            int referenceDays) {

        public SyntheticAccount {
            if (baseAmount == null || baseAmount.signum() < 0) {
                throw new IllegalArgumentException("baseAmount é obrigatório e não pode ser negativo");
            }
            if (daysLate < 0) {
                throw new IllegalArgumentException("daysLate negativo: " + daysLate);
            }
            lastConsumptions = lastConsumptions == null ? List.of() : List.copyOf(lastConsumptions);
            if (estimatedCyclesUsed < 0) {
                throw new IllegalArgumentException("estimatedCyclesUsed negativo: " + estimatedCyclesUsed);
            }
            if (periodDays < 1 || referenceDays < 1) {
                throw new IllegalArgumentException("periodDays/referenceDays precisam ser positivos");
            }
        }
    }

    /** Diferença de uma chave entre o vigente e o perfil. */
    public record KeyDiff(String definitionKey, String currentValueJson, String profileValueJson,
                          String sourceAct, String sourceArticle,
                          RegulatoryProfileDef.Confirmation confirmation) {
    }

    /** Rótulo obrigatório do resultado: estimativa simplificada, não o cálculo do billing. */
    public static final String ESTIMATE_NOTICE =
            "ESTIMATIVA simplificada do platform (multa + juros simples, média aritmética e "
                    + "fator de dias): o cálculo autoritativo é o do billing da P2.5 "
                    + "(PartialPeriodRule distingue primeira/final de ciclo regular, normaliza "
                    + "o consumo fora da faixa e considera fee.interest_mode e correção).";

    /** Resultado completo da simulação (nada disto é gravado). */
    public record SimulationResult(
            String profileKey,
            List<KeyDiff> diffs,
            List<RegulatoryProfileDef.ProfileDivergence> divergences,
            List<String> pendingTenantKeys,
            BigDecimal currentCharges,
            BigDecimal profileCharges,
            BigDecimal currentAverageConsumption,
            BigDecimal profileAverageConsumption,
            boolean profileAfterCapTriggered,
            BigDecimal dayFactor,
            String notice) {
    }

    /**
     * @param currentValues valores vigentes do tenant por chave (JSON), como
     *                      viriam de {@code parameter_versions} aceitas; pode
     *                      não ter a chave (ausência = sem valor vigente)
     * @param profile       o perfil candidato
     * @param account       a conta-tipo sintética
     */
    public static SimulationResult simulate(Map<String, String> currentValues,
                                            RegulatoryProfileDef profile,
                                            SyntheticAccount account) {
        if (profile == null || account == null) {
            throw new IllegalArgumentException("profile e account são obrigatórios");
        }
        Map<String, String> current = currentValues == null ? Map.of() : currentValues;

        List<KeyDiff> diffs = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (ProfileValue value : profile.values()) {
            diffs.add(new KeyDiff(value.definitionKey(), current.get(value.definitionKey()),
                    value.valueJson(), value.sourceAct(), value.sourceArticle(), value.confirmation()));
            if (value.confirmation() == RegulatoryProfileDef.Confirmation.REQUIRES_TENANT_VALUE) {
                pending.add(value.definitionKey());
            } else if (value.confirmation() == RegulatoryProfileDef.Confirmation.CONFIRMED
                    && RegulatoryLoadGuard.TENANT_COMPLETED_KEYS.contains(value.definitionKey())) {
                // Confirmado só o requisito (mínimo de datas): os dias
                // concretos são entrada do tenant na ativação — pendência
                // dele também, exibida como tal (o diff mostra o requisito
                // {"minCount":N} do catálogo, não uma lista de dias).
                pending.add(value.definitionKey());
            }
        }

        BigDecimal profileCharges = charges(
                effective(current, profile, "fee.fine_pct"),
                effective(current, profile, "fee.interest_daily_pct"),
                account);
        BigDecimal currentCharges = charges(
                current.get("fee.fine_pct"), current.get("fee.interest_daily_pct"), account);

        BigDecimal profileAverage = average(
                effectiveInt(current, profile, "meter.average_months"), account.lastConsumptions());
        BigDecimal currentAverage = average(
                intOrNull(current.get("meter.average_months")), account.lastConsumptions());

        Integer cap = effectiveInt(current, profile, "meter.max_estimated_cycles");
        boolean afterCap = cap != null && account.estimatedCyclesUsed() >= cap;

        int[] band = bandOf(profile);
        BigDecimal dayFactor = account.periodDays() >= band[0] && account.periodDays() <= band[1]
                ? new BigDecimal("1.0000")
                : BigDecimal.valueOf(account.periodDays())
                        .divide(BigDecimal.valueOf(account.referenceDays()), 4, RoundingMode.HALF_UP);

        return new SimulationResult(profile.profileKey(), List.copyOf(diffs),
                profile.divergences(), List.copyOf(pending),
                currentCharges, profileCharges, currentAverage, profileAverage, afterCap, dayFactor,
                ESTIMATE_NOTICE);
    }

    /** Valor efetivo da chave na simulação: o do perfil; se o perfil exige o tenant, o vigente. */
    private static String effective(Map<String, String> current, RegulatoryProfileDef profile, String key) {
        ProfileValue value = profile.value(key);
        if (value != null && value.hasFactoryValue()) {
            return value.valueJson();
        }
        return current.get(key);
    }

    private static Integer effectiveInt(Map<String, String> current, RegulatoryProfileDef profile, String key) {
        return intOrNull(effective(current, profile, key));
    }

    private static Integer intOrNull(String json) {
        if (json == null) {
            return null;
        }
        JsonNode node = Json.readTree(json);
        return node.isIntegralNumber() ? node.asInt() : null;
    }

    /** Multa (uma vez) + juros simples pro rata die, HALF_UP em centavos. Sem capitalização. */
    private static BigDecimal charges(String fineJson, String dailyJson, SyntheticAccount account) {
        BigDecimal total = BigDecimal.ZERO;
        if (fineJson != null) {
            JsonNode fine = Json.readTree(fineJson);
            if (fine.isNumber()) {
                total = total.add(account.baseAmount().multiply(fine.decimalValue())
                        .movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
            }
        }
        if (dailyJson != null) {
            JsonNode daily = Json.readTree(dailyJson);
            if (daily.isNumber()) {
                total = total.add(account.baseAmount().multiply(daily.decimalValue())
                        .movePointLeft(2).multiply(BigDecimal.valueOf(account.daysLate()))
                        .setScale(2, RoundingMode.HALF_UP));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** Média aritmética dos últimos {@code months} consumos (os que houver), 4 casas HALF_UP. */
    private static BigDecimal average(Integer months, List<Integer> consumptions) {
        if (months == null || consumptions.isEmpty()) {
            return null;
        }
        int from = Math.max(0, consumptions.size() - months);
        List<Integer> window = consumptions.subList(from, consumptions.size());
        BigDecimal sum = BigDecimal.ZERO;
        for (Integer consumption : window) {
            sum = sum.add(BigDecimal.valueOf(consumption));
        }
        return sum.divide(BigDecimal.valueOf(window.size()), 4, RoundingMode.HALF_UP);
    }

    private static int[] bandOf(RegulatoryProfileDef profile) {
        ProfileValue cycle = profile.value("meter.reading_cycle_days");
        if (cycle != null && cycle.hasFactoryValue()) {
            return RegulatoryLoadGuard.readingCycleBand(profile.profileKey(), cycle.valueJson());
        }
        return new int[]{REFERENCE_BAND_MIN_DAYS, REFERENCE_BAND_MAX_DAYS};
    }
}
