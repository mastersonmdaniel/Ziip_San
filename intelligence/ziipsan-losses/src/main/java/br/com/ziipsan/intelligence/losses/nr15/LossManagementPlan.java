package br.com.ziipsan.intelligence.losses.nr15;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano de Gestao de Reducao e Controle de Perdas de Agua (ED-78;
 * NR 15, arts. 6o a 9o): instrumento formal do prestador, POR
 * MUNICIPIO (art. 8o), submetido a entidade reguladora
 * infranacional. O conteudo minimo e o do art. 9o:
 * <ul>
 *   <li>I - diagnostico (a declaracao anual: {@code declarationId} +
 *       ano-base);</li>
 *   <li>II - acompanhamento de perdas aparentes e reais com o
 *       diagnostico como LINHA DE BASE ({@code baseline*} +
 *       descricao e periodicidade DECLARADAS pelo prestador - a NR
 *       remete a periodicidade de revisao ao ato da ERI, art. 9o,
 *       §2o; nenhum periodo e presumido aqui);</li>
 *   <li>III - acompanhamento da evolucao da hidrometacao e da
 *       macromedicao ({@code meteringEvolution}: serie medida e
 *       planejada, por ano);</li>
 *   <li>IV - acoes com cronograma fisico-financeiro e justificativa
 *       tecnico-economica ({@link PlanAction});</li>
 *   <li>V - indicadores complementares declarados;</li>
 *   <li>VI - estrutura operacional e estimativa de custos.</li>
 * </ul>
 *
 * <p>Metas/resultados pactuados NAO sao inventados: quando a ERI os
 * fixa em ato proprio, entram como {@link DeclaredTarget} declarado
 * (dado, com o ano); sem alvo declarado, a convergencia do
 * acompanhamento e DESCONHECIDA (ver {@link Nr15PlanService}) -
 * nenhum numero da Res. ANA 275/2025 e usado como meta ([SEM
 * FONTE] para limiares: a NR 15 nao fixa indice-alvo no texto lido).
 *
 * <p>Revisao (art. 9o, §1o): acompanhamentos anuais sem convergencia
 * exigem ajuste do Plano - {@link Nr15PlanService#revise} cria a
 * versao seguinte ligada a esta ({@code revisionOfId}), com motivo.
 *
 * <p><b>Gap conhecido e registrado (correcao ED-78-CORR-1, ressalva
 * R4 da revisao):</b> diferente da declaracao anual
 * ({@link AnnualDeclaration}, que tem maquina DRAFT -&gt; SUBMITTED
 * -&gt; PUBLISHED com canal e referencia obrigatorios), este record
 * e ABERTO quanto ao {@link Status}: um Plano pode ser construido
 * ja com {@code status = PUBLISHED}, sem transicao guardada nem
 * rastro de publicacao, e a tabela {@code nr15_management_plans}
 * nao tem colunas nem CHECK de publicacao do plano. NENHUM ciclo
 * novo e inventado nesta correcao: o gap e o tratamento esperado
 * (na fiacao, ou o Plano ganha o mesmo portao de publicacao da
 * declaracao, ou o status dele fica restrito a DRAFT/SUBMITTED ate
 * la) estao registrados na pendencia P-ED78-05 (adendo de
 * 08/11/2026 no RELATORIO-ED-78).
 */
public record LossManagementPlan(
        UUID id,
        UUID tenantId,
        String municipalityCode,
        int version,
        int baseYear,
        UUID declarationId,
        String agencyCode,
        double baselineTotalLossesLPerConnDay,
        Double baselineRealLossesLPerConnDayOrNull,
        Double baselineApparentLossesLPerConnDayOrNull,
        String monitoringDescription,
        String monitoringPeriodicityDeclared,
        List<MeteringEvolutionPoint> meteringEvolution,
        List<PlanAction> actions,
        List<String> complementaryIndicators,
        List<DeclaredTarget> declaredTargets,
        String operationalStructure,
        double operationalCostEstimate,
        Status status,
        UUID revisionOfIdOrNull,
        String revisionReasonOrNull) {

    public enum Status { DRAFT, SUBMITTED, PUBLISHED }

    /** Foco de uma acao do Plano (art. 7o, p.u.: foco definido pelo prestador). */
    public enum ActionFocus { REAL, APPARENT, BOTH }

    /** Acao do Plano (art. 9o, IV): cronograma fisico-financeiro + justificativa. */
    public record PlanAction(String description, ActionFocus focus,
                             LocalDate startDate, LocalDate endDate,
                             double estimatedCost, String technicalEconomicJustification) {
        public PlanAction {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("acao do Plano sem descricao");
            }
            Objects.requireNonNull(focus, "foco da acao obrigatorio (perdas reais, aparentes ou ambas)");
            Objects.requireNonNull(startDate, "inicio do cronograma da acao obrigatorio (art. 9o, IV, a)");
            Objects.requireNonNull(endDate, "fim do cronograma da acao obrigatorio (art. 9o, IV, a)");
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException("cronograma da acao invertido: " + startDate + " > " + endDate);
            }
            if (estimatedCost < 0 || Double.isNaN(estimatedCost)) {
                throw new IllegalArgumentException("custo estimado da acao invalido (cronograma financeiro, art. 9o, IV, a)");
            }
            if (technicalEconomicJustification == null || technicalEconomicJustification.isBlank()) {
                throw new IllegalArgumentException(
                        "acao sem justificativa tecnico-economica (art. 9o, IV, b): recusa nomeada");
            }
        }
    }

    /** Ponto da serie de evolucao da medicao (art. 9o, III). */
    public record MeteringEvolutionPoint(int year, Kind kind,
                                         Double hydroMeteringPercentOrNull,
                                         Double macroMeteringPercentOrNull) {
        public enum Kind { MEASURED, PLANNED }

        public MeteringEvolutionPoint {
            Objects.requireNonNull(kind, "natureza do ponto de evolucao obrigatoria (medido ou planejado)");
            if (hydroMeteringPercentOrNull == null && macroMeteringPercentOrNull == null) {
                throw new IllegalArgumentException("ponto de evolucao sem nenhum indice de medicao");
            }
            checkPercent(hydroMeteringPercentOrNull, "hidrometacao");
            checkPercent(macroMeteringPercentOrNull, "macromedicao");
        }

        private static void checkPercent(Double value, String what) {
            if (value != null && (value < 0 || value > 100 || Double.isNaN(value))) {
                throw new IllegalArgumentException("indice de " + what + " fora de [0,100]: " + value);
            }
        }
    }

    /** Alvo declarado por ato da ERI/prestador para um ano (dado, nao norma). */
    public record DeclaredTarget(int year, double realLossesLPerConnDayTarget) {
        public DeclaredTarget {
            if (realLossesLPerConnDayTarget <= 0 || Double.isNaN(realLossesLPerConnDayTarget)) {
                throw new IllegalArgumentException("alvo declarado de perdas reais deve ser positivo");
            }
        }
    }

    /**
     * Entrada de acompanhamento de um periodo do Plano (art. 9o, §1o e
     * art. 12): os indicadores observados, lidos da saida da NR 15 do
     * periodo (mes 0 = agregado anual).
     */
    public record MonitoringEntry(int year, int month, Double realLossesLPerConnDayOrNull,
                                  Double apparentLossesLPerConnDayOrNull, Double iliOrNull,
                                  Double hydroMeteringPercentOrNull, Double macroMeteringPercentOrNull) {
        public MonitoringEntry {
            if (month < 0 || month > 12) throw new IllegalArgumentException("mes do acompanhamento invalido: " + month);
        }
    }

    public LossManagementPlan {
        Objects.requireNonNull(id, "Plano sem id");
        Objects.requireNonNull(tenantId, "Plano sem tenant");
        if (municipalityCode == null || municipalityCode.isBlank()) {
            throw new IllegalArgumentException("Plano e por municipio (art. 8o): codigo do municipio obrigatorio");
        }
        if (version < 1) throw new IllegalArgumentException("versao do Plano deve ser >= 1");
        Objects.requireNonNull(declarationId, "Plano sem o diagnostico (declaracao anual, art. 9o, I)");
        if (agencyCode == null || agencyCode.isBlank()) {
            throw new IllegalArgumentException("Plano sem a agencia reguladora destinataria");
        }
        if (baselineTotalLossesLPerConnDay < 0 || Double.isNaN(baselineTotalLossesLPerConnDay)) {
            throw new IllegalArgumentException("linha de base de perdas totais invalida");
        }
        if (monitoringDescription == null || monitoringDescription.isBlank()) {
            throw new IllegalArgumentException("Plano sem a definicao do acompanhamento (art. 9o, II)");
        }
        if (monitoringPeriodicityDeclared == null || monitoringPeriodicityDeclared.isBlank()) {
            throw new IllegalArgumentException("periodicidade do acompanhamento nao declarada: a NR 15 a remete"
                    + " ao ato da ERI (art. 9o, §2o) - nenhum periodo e presumido nesta entrega");
        }
        meteringEvolution = meteringEvolution == null ? List.of() : List.copyOf(meteringEvolution);
        actions = actions == null ? List.of() : List.copyOf(actions);
        complementaryIndicators = complementaryIndicators == null ? List.of() : List.copyOf(complementaryIndicators);
        declaredTargets = declaredTargets == null ? List.of() : List.copyOf(declaredTargets);
        if (operationalCostEstimate < 0 || Double.isNaN(operationalCostEstimate)) {
            throw new IllegalArgumentException("estimativa de custo da estrutura invalida (art. 9o, VI)");
        }
        Objects.requireNonNull(status, "situacao do Plano obrigatoria");
        if (version > 1 && (revisionOfIdOrNull == null || revisionReasonOrNull == null
                || revisionReasonOrNull.isBlank())) {
            throw new IllegalArgumentException("versao " + version + " do Plano exige a versao anterior"
                    + " e o motivo da revisao (art. 9o, §1o)");
        }
        if (version == 1 && revisionOfIdOrNull != null) {
            throw new IllegalArgumentException("versao 1 do Plano nao revisa Plano anterior");
        }
    }

    /** Custo total estimado (art. 9o, VI): estrutura + soma das acoes. */
    public double totalEstimatedCost() {
        return operationalCostEstimate + actions.stream().mapToDouble(PlanAction::estimatedCost).sum();
    }

    /** Alvo declarado para um ano, se houver. */
    public java.util.Optional<DeclaredTarget> targetFor(int year) {
        return declaredTargets.stream().filter(t -> t.year() == year).findFirst();
    }
}
