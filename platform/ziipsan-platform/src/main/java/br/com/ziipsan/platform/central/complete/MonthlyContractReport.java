package br.com.ziipsan.platform.central.complete;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * P3.15 (ADR-0025): relatório mensal POR CONTRATO da Central —
 * disponibilidade e aderência por severidade, com o cálculo da multa.
 *
 * <h2>Reconciliação com a P2.15 (correção pós-revisão, achado B1)</h2>
 * Esta classe <b>NÃO cria fórmula nova</b>: ela agrega, por severidade e
 * por contrato, a semântica JÁ ENTREGUE da P2.15 em
 * {@code SupportSlaService.monthlyReport} (core, {@code V10_2}):
 * <ul>
 *   <li><b>aderência</b> = {@code resolvedWithinDue / (resolvedWithinDue +
 *       breachedResolution) × 100} (2 casas, HALF_UP) — o denominador só
 *       conta resolvidos <b>COM prazo aplicado</b>; chamado resolvido sem
 *       prazo ({@code resolvedWithoutDue}) fica FORA do denominador,
 *       exatamente como na P2.15. Denominador zero → aderência INDEFINIDA
 *       ({@link Optional#empty()}), nunca 100% presumido;</li>
 *   <li><b>multa</b> = soma <b>por chamado estourado</b>: cada resolução
 *       fora do prazo soma {@code mensalidade × penalty_pct / 100}
 *       (centavos, HALF_UP por chamado, como o {@code penaltyEstimate} da
 *       P2.15 soma por chamado). A multa NÃO é "1× por severidade abaixo
 *       da meta": ela nasce de cada estouro, mesmo quando a aderência da
 *       severidade está exatamente na meta (ex.: 49 no prazo + 1 estouro
 *       em 50 com prazo → aderência 98,00% = meta, e ainda assim 1
 *       chamado estourado gera multa). A comparação com a meta
 *       ({@code belowTarget}) é informativa; quem gera multa é o estouro.</li>
 * </ul>
 * A versão anterior desta classe divergia da P2.15 nos dois pontos
 * (denominador = todos os resolvidos; multa = 1× por severidade abaixo
 * da meta) e foi corrigida — ver "Correções pós-revisão" no
 * RELATORIO-P3.15.md.
 *
 * <h2>Disponibilidade</h2>
 * Meta de <b>99,9%</b> no nível Setor Público ({@code PUBLIC_SECTOR} da
 * core {@code V10_2}), medida <b>a partir do lançamento</b>: o período
 * medido começa no maior entre o dia 1 do mês e a data de lançamento do
 * contrato; mês inteiro anterior ao lançamento é erro explícito (não
 * existe disponibilidade "100%" de um serviço que não existia).
 * Fórmula declarada: {@code (minutosMedidos − indisponibilidadeNãoPlanejada)
 * / minutosMedidos × 100}, 4 casas, HALF_UP, onde
 * {@code minutosMedidos = diasMedidos × 1440 − manutençãoPlanejada} — o
 * denominador <b>exclui a manutenção planejada</b> (campo explícito
 * {@link Report#availabilityDenominatorNote()}). Premissa registrada e
 * pendente de conferência contra o contrato-modelo do nível Setor
 * Público (P3.9/ED-05), ver RELATORIO-P3.15.md. No nível
 * {@code STANDARD} não há meta de 99,9% no aceite: a disponibilidade é
 * calculada e exibida, sem veredito de meta nem multa de disponibilidade
 * (a multa desta tarefa é a de aderência/estouro, acima — nenhum
 * percentual de disponibilidade foi inventado para o STANDARD).
 *
 * <p>Nenhum teto de multa é aplicado: nenhum teto consta das fontes (se
 * o contrato tiver teto, ele entra como dado do contrato — pendência
 * declarada).
 *
 * <p>Casos calculados à mão estão nos testes
 * ({@code MonthlyContractReportTest}) e repetidos no javadoc de cada
 * cálculo.
 */
public final class MonthlyContractReport {

    /** Meta de disponibilidade do nível Setor Público (ADR-0025). */
    public static final BigDecimal PUBLIC_SECTOR_AVAILABILITY_TARGET = new BigDecimal("99.9");
    public static final List<String> SEVERITIES = List.of("S1", "S2", "S3", "S4", "S5");
    /** Nota explícita (M4): o denominador da disponibilidade exclui a manutenção planejada. */
    public static final String AVAILABILITY_DENOMINATOR_NOTE =
            "Denominador da disponibilidade EXCLUI a manutenção planejada: "
                    + "minutosMedidos = diasMedidos × 1440 − plannedMaintenanceMinutes.";

    /** Alvo de uma severidade, vindo de {@code support.sla_policy_target} (contrato, P2.15). */
    public record SeverityTarget(String severity, BigDecimal adherenceTargetPct, BigDecimal penaltyPct) {
        public SeverityTarget {
            if (!SEVERITIES.contains(severity)) {
                throw new IllegalArgumentException("Severidade inválida: " + severity);
            }
            Objects.requireNonNull(adherenceTargetPct, "meta de aderência é obrigatória (" + severity + ")");
            Objects.requireNonNull(penaltyPct, "percentual de multa é obrigatório (" + severity + ")");
            if (adherenceTargetPct.signum() < 0 || adherenceTargetPct.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("Meta de aderência fora de [0,100] (" + severity + "): " + adherenceTargetPct);
            }
            if (penaltyPct.signum() < 0) {
                throw new IllegalArgumentException("Percentual de multa negativo (" + severity + "): " + penaltyPct);
            }
        }
    }

    /**
     * Desfecho de uma severidade no mês, nos termos da P2.15: resolvidos
     * dentro do prazo de solução ({@code resolvedWithinDue}), resolvidos
     * FORA do prazo ({@code breachedResolution}) e resolvidos SEM prazo
     * aplicado ({@code resolvedWithoutDue} — fora do denominador da
     * aderência e sem multa, como na P2.15, que só conta quem tem
     * {@code resolution_due_at}).
     */
    public record SeverityOutcome(String severity, long resolvedWithinDueCount,
                                  long breachedResolutionCount, long resolvedWithoutDueCount) {
        public SeverityOutcome {
            if (!SEVERITIES.contains(severity)) {
                throw new IllegalArgumentException("Severidade inválida: " + severity);
            }
            if (resolvedWithinDueCount < 0 || breachedResolutionCount < 0 || resolvedWithoutDueCount < 0) {
                throw new IllegalArgumentException("Contagens negativas em " + severity);
            }
        }

        /** Resolvidos COM prazo aplicado: o denominador da aderência da P2.15. */
        public long resolvedWithDueCount() {
            return resolvedWithinDueCount + breachedResolutionCount;
        }
    }

    /**
     * Linha do relatório por severidade: aderência (vazia = indefinida,
     * denominador zero), veredito informativo contra a meta, estouros e
     * multa em centavos (soma por chamado estourado, semântica P2.15).
     */
    public record SeverityLine(String severity, Optional<BigDecimal> adherencePct,
                               BigDecimal adherenceTargetPct, boolean belowTarget,
                               long breachedResolutionCount, long penaltyCents) {
    }

    public record Report(UUID contractId, UUID tenantId, YearMonth month, String level,
                         LocalDate measuredFrom, long measuredMinutes, long unplannedDowntimeMinutes,
                         long plannedMaintenanceMinutes, BigDecimal availabilityPct,
                         Optional<Boolean> availabilityTargetMet,
                         Map<String, SeverityLine> bySeverity, long totalPenaltyCents,
                         long monthlyFeeCents, String availabilityDenominatorNote) {
    }

    /**
     * Calcula o relatório do mês.
     *
     * @param level                 {@code PUBLIC_SECTOR} ou {@code STANDARD} (valores da V10_2)
     * @param launchDate            lançamento do contrato (a medição começa aqui, se dentro do mês)
     * @param targets               exatamente os cinco alvos S1–S5 da política do contrato
     * @param outcomes              exatamente os cinco desfechos S1–S5 do mês (zero inclusive)
     */
    public Report generate(UUID contractId, UUID tenantId, YearMonth month, String level,
                           LocalDate launchDate, long unplannedDowntimeMinutes, long plannedMaintenanceMinutes,
                           long monthlyFeeCents, List<SeverityTarget> targets, List<SeverityOutcome> outcomes) {
        Objects.requireNonNull(contractId, "contrato é obrigatório");
        Objects.requireNonNull(tenantId, "tenant é obrigatório");
        Objects.requireNonNull(month, "mês é obrigatório");
        Objects.requireNonNull(launchDate, "data de lançamento é obrigatória (a medição começa nela)");
        if (!"PUBLIC_SECTOR".equals(level) && !"STANDARD".equals(level)) {
            throw new IllegalArgumentException("Nível do contrato inválido (V10_2): " + level);
        }
        if (unplannedDowntimeMinutes < 0 || plannedMaintenanceMinutes < 0 || monthlyFeeCents < 0) {
            throw new IllegalArgumentException("Minutos e mensalidade não podem ser negativos");
        }
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        if (launchDate.isAfter(monthEnd)) {
            throw new IllegalArgumentException("Mês " + month + " anterior ao lançamento (" + launchDate
                    + "): disponibilidade só é medida a partir do lançamento (ADR-0025)");
        }
        LocalDate measuredFrom = launchDate.isAfter(monthStart) ? launchDate : monthStart;
        long measuredDays = monthEnd.toEpochDay() - measuredFrom.toEpochDay() + 1;
        // Denominador EXCLUI a manutenção planejada (nota explícita no Report).
        long measuredMinutes = measuredDays * 24 * 60 - plannedMaintenanceMinutes;
        if (measuredMinutes <= 0) {
            throw new IllegalArgumentException("Período medido sem minutos (manutenção planejada cobre tudo?): " + measuredMinutes);
        }
        if (unplannedDowntimeMinutes > measuredMinutes) {
            throw new IllegalArgumentException("Indisponibilidade (" + unplannedDowntimeMinutes
                    + " min) maior que o período medido (" + measuredMinutes + " min)");
        }
        // Disponibilidade calculada à mão (teste): mês de 30 dias = 43.200 min;
        // 60 min de indisponibilidade → 43.140/43.200 = 99,8611% (abaixo de 99,9);
        // 43 min → 99,9005% (acima); 44 min → 99,8981% (abaixo). Orçamento: 43,2 min.
        // Com manutenção planejada (M4): 43.200 − 600 = 42.600 de denominador;
        // 44 min não planejados → 42.556/42.600 = 99,8967% (abaixo de 99,9).
        BigDecimal availability = BigDecimal.valueOf(measuredMinutes - unplannedDowntimeMinutes)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(measuredMinutes), 4, RoundingMode.HALF_UP);
        Optional<Boolean> targetMet = "PUBLIC_SECTOR".equals(level)
                ? Optional.of(availability.compareTo(PUBLIC_SECTOR_AVAILABILITY_TARGET) >= 0)
                : Optional.empty();

        Map<String, SeverityTarget> targetBySeverity = indexTargets(targets);
        Map<String, SeverityOutcome> outcomeBySeverity = indexOutcomes(outcomes);
        Map<String, SeverityLine> lines = new LinkedHashMap<>();
        long totalPenalty = 0;
        for (String severity : SEVERITIES) {
            SeverityTarget target = targetBySeverity.get(severity);
            SeverityOutcome outcome = outcomeBySeverity.get(severity);
            // Multa da P2.15, calculada à mão (teste): CADA chamado estourado
            // soma mensalidade × penalty_pct/100. Ex.: 1 estouro a 10% sobre
            // 1.000.000 de centavos (R$ 10.000,00) → 100.000 centavos;
            // 10 estouros a 5% → 10 × 50.000 = 500.000 centavos (R$ 5.000,00).
            // A multa independe de a aderência estar abaixo da meta.
            long penaltyPerBreach = BigDecimal.valueOf(monthlyFeeCents).multiply(target.penaltyPct())
                    .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP).longValueExact();
            long penalty = Math.multiplyExact(penaltyPerBreach, outcome.breachedResolutionCount());
            totalPenalty += penalty;
            if (outcome.resolvedWithDueCount() == 0) {
                // Sem resolvido COM prazo: aderência indefinida (P2.15 devolve
                // null), nunca 100% presumido. Estouros = 0 por construção
                // (denominador zero ⇒ breached = 0), logo multa = 0.
                lines.put(severity, new SeverityLine(severity, Optional.empty(),
                        target.adherenceTargetPct(), false, outcome.breachedResolutionCount(), penalty));
                continue;
            }
            // Aderência da P2.15, calculada à mão (teste): 19 no prazo e 1
            // estouro → denominador 20 → 95,00%. 49 no prazo e 1 estouro →
            // 98,00% (= meta de 98%), MAS a multa do estouro é 50.000
            // centavos — é aqui que a fórmula antiga divergia da P2.15.
            BigDecimal adherence = BigDecimal.valueOf(outcome.resolvedWithinDueCount())
                    .multiply(new BigDecimal("100"))
                    .divide(BigDecimal.valueOf(outcome.resolvedWithDueCount()), 2, RoundingMode.HALF_UP);
            boolean below = adherence.compareTo(target.adherenceTargetPct()) < 0;
            lines.put(severity, new SeverityLine(severity, Optional.of(adherence),
                    target.adherenceTargetPct(), below, outcome.breachedResolutionCount(), penalty));
        }
        return new Report(contractId, tenantId, month, level, measuredFrom, measuredMinutes,
                unplannedDowntimeMinutes, plannedMaintenanceMinutes, availability, targetMet,
                // b3: cópia em LinkedHashMap NÃO modificável — preserva a ordem
                // S1–S5 (Map.copyOf perderia a ordem de iteração).
                Collections.unmodifiableMap(new LinkedHashMap<>(lines)), totalPenalty, monthlyFeeCents,
                AVAILABILITY_DENOMINATOR_NOTE);
    }

    private static Map<String, SeverityTarget> indexTargets(List<SeverityTarget> targets) {
        if (targets == null || targets.size() != SEVERITIES.size()) {
            throw new IllegalArgumentException("A política do contrato deve declarar exatamente os cinco alvos S1–S5 (V10_2)");
        }
        Map<String, SeverityTarget> map = new LinkedHashMap<>();
        for (SeverityTarget t : targets) {
            if (map.put(t.severity(), t) != null) {
                throw new IllegalArgumentException("Alvo duplicado para " + t.severity());
            }
        }
        if (!map.keySet().equals(Set.copyOf(SEVERITIES))) {
            throw new IllegalArgumentException("Alvos devem cobrir S1–S5 exatamente: " + map.keySet());
        }
        return map;
    }

    private static Map<String, SeverityOutcome> indexOutcomes(List<SeverityOutcome> outcomes) {
        if (outcomes == null || outcomes.size() != SEVERITIES.size()) {
            throw new IllegalArgumentException("O mês deve declarar desfecho para as cinco severidades (zero inclusive)");
        }
        Map<String, SeverityOutcome> map = new LinkedHashMap<>();
        for (SeverityOutcome o : outcomes) {
            if (map.put(o.severity(), o) != null) {
                throw new IllegalArgumentException("Desfecho duplicado para " + o.severity());
            }
        }
        if (!map.keySet().equals(Set.copyOf(SEVERITIES))) {
            throw new IllegalArgumentException("Desfechos devem cobrir S1–S5 exatamente: " + map.keySet());
        }
        return map;
    }
}
