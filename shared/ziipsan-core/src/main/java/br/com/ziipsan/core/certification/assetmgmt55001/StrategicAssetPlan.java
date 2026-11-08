package br.com.ziipsan.core.certification.assetmgmt55001;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Plano estratégico de gestão de ativos (ED-72): objetivos declarados pelo
 * tenant, critérios de decisão para os quatro desfechos do aceite —
 * RENOVAR, REPARAR, MANTER, DESCARTAR — e o REGISTRO das decisões tomadas
 * ao amparo desses critérios.
 *
 * <p><b>Nenhum critério de fábrica:</b> nenhuma fonte fixa limiar de
 * renovação/reparo/descarte para os ativos do tenant. Os critérios são
 * TEXTO E REGRA DECLARADOS pelo tenant (condição descrita em palavras do
 * tenant, podendo referenciar o risco LIDO da ED-56 e o ciclo de vida);
 * este pacote guarda a declaração e exige cobertura dos quatro desfechos
 * — plano estratégico sem critério para um desfecho não é plano completo.
 *
 * <p><b>Decisão registrada é imutável neste pacote:</b> não há operação de
 * exclusão nem de reescrita de decisão; decisão nova sobre o mesmo ativo
 * é um registro novo que referencia o critério vigente.
 */
public record StrategicAssetPlan(Iso55001Activation activation, String planName,
                                 List<String> objectives, List<DecisionCriterion> criteria,
                                 UUID approvedBy, LocalDate approvedOn) {

    /** Desfechos de decisão do aceite da ED-72. */
    public enum DecisionOutcome {
        RENEW, REPAIR, MAINTAIN, DISPOSE
    }

    /**
     * Critério de decisão declarado pelo tenant para um desfecho: a
     * condição é descrita pelo tenant (texto próprio); {@code criterionKey}
     * é a chave estável citada pelo registro de decisão.
     */
    public record DecisionCriterion(String criterionKey, DecisionOutcome outcome,
                                    String conditionDescription) {
        public DecisionCriterion {
            if (criterionKey == null || criterionKey.isBlank()) {
                throw new IllegalArgumentException("criterionKey é obrigatória");
            }
            criterionKey = criterionKey.trim();
            Objects.requireNonNull(outcome, "outcome do critério é obrigatório");
            if (conditionDescription == null || conditionDescription.isBlank()) {
                throw new IllegalArgumentException("conditionDescription é obrigatória: o critério "
                        + "é declarado pelo tenant em palavras próprias — nenhum limiar de "
                        + "fábrica é presumido neste pacote");
            }
            conditionDescription = conditionDescription.trim();
        }
    }

    /**
     * Registro de UMA decisão tomada: ativo, critério aplicado (o OBJETO
     * do plano, que carrega o desfecho), desfecho declarado, fundamentação,
     * quem decidiu e quando. Nasce amarrado ao critério que o autoriza.
     *
     * <p><b>Correção D1 (revisão da ED-72):</b> o construtor canônico
     * EXIGE o {@link DecisionCriterion} aplicado e recusa desfecho
     * diverso do desfecho do critério — a régua "decisão amarrada a
     * critério do plano E do mesmo desfecho" vive neste construtor, não
     * só em {@code decide()}: a construção direta inconsistente
     * (ex.: RENEW citando critério de DISPOSE) é recusada na construção.
     */
    public record DecisionRecord(UUID assetId, DecisionCriterion criterion,
                                 DecisionOutcome outcome, String rationale,
                                 UUID decidedBy, LocalDate decidedOn) {
        public DecisionRecord {
            Objects.requireNonNull(assetId, "assetId da decisão é obrigatório");
            Objects.requireNonNull(criterion, "criterion da decisão é obrigatório: "
                    + "decisão sem o critério do plano aplicado não é decisão "
                    + "do plano estratégico");
            Objects.requireNonNull(outcome, "outcome da decisão é obrigatório");
            if (criterion.outcome() != outcome) {
                throw new IllegalArgumentException("critério " + criterion.criterionKey()
                        + " é de " + criterion.outcome() + ", não de " + outcome
                        + ": o desfecho da decisão tem de ser o do critério aplicado");
            }
            if (rationale == null || rationale.isBlank()) {
                throw new IllegalArgumentException("rationale da decisão é obrigatória "
                        + "(fundamentação declarada por quem decidiu)");
            }
            rationale = rationale.trim();
            Objects.requireNonNull(decidedBy, "decidedBy é obrigatório");
            Objects.requireNonNull(decidedOn, "decidedOn é obrigatório");
        }

        /** Chave do critério aplicado (atalho para {@code criterion().criterionKey()}). */
        public String criterionKey() {
            return criterion.criterionKey();
        }
    }

    public StrategicAssetPlan {
        Objects.requireNonNull(activation, "activation é obrigatória");
        activation.requireEnabled();
        if (planName == null || planName.isBlank()) {
            throw new IllegalArgumentException("planName do plano estratégico é obrigatório");
        }
        planName = planName.trim();
        if (objectives == null || objectives.isEmpty()
                || objectives.stream().anyMatch(o -> o == null || o.isBlank())) {
            throw new IllegalArgumentException("objectives do plano estratégico: ao menos um "
                    + "objetivo declarado pelo tenant, nenhum em branco");
        }
        objectives = List.copyOf(objectives);
        if (criteria == null || criteria.isEmpty()) {
            throw new IllegalArgumentException("criteria do plano estratégico são obrigatórios");
        }
        criteria = List.copyOf(criteria);
        Set<DecisionOutcome> covered = EnumSet.noneOf(DecisionOutcome.class);
        Set<String> keys = new java.util.HashSet<>();
        for (DecisionCriterion c : criteria) {
            covered.add(c.outcome());
            if (!keys.add(c.criterionKey())) {
                throw new IllegalArgumentException("criterionKey duplicada no plano: "
                        + c.criterionKey());
            }
        }
        if (covered.size() != DecisionOutcome.values().length) {
            Set<DecisionOutcome> missing = EnumSet.allOf(DecisionOutcome.class);
            missing.removeAll(covered);
            throw new IllegalArgumentException("plano estratégico incompleto: faltam critérios "
                    + "de decisão para " + missing + " (o aceite exige renovar, reparar, "
                    + "manter e descartar)");
        }
        Objects.requireNonNull(approvedBy, "approvedBy do plano é obrigatório");
        Objects.requireNonNull(approvedOn, "approvedOn do plano é obrigatório");
    }

    /**
     * Registra uma decisão ao amparo deste plano: o critério citado tem
     * de existir no plano E ser do mesmo desfecho declarado — decisão
     * "renovar" citando critério de "descartar" é recusada.
     */
    public DecisionRecord decide(UUID assetId, DecisionOutcome outcome, String criterionKey,
                                 String rationale, UUID decidedBy, LocalDate decidedOn) {
        String key = criterionKey == null ? null : criterionKey.trim();
        DecisionCriterion found = criteria.stream()
                .filter(c -> c.criterionKey().equals(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "critério " + criterionKey + " não existe neste plano "
                                + "estratégico: decisão sem critério do plano é recusada"));
        // A coerência desfecho x critério é imposta pelo construtor
        // canônico de DecisionRecord (correção D1): decide() resolve o
        // critério do plano e delega a régua — sem régua paralela aqui.
        return new DecisionRecord(assetId, found, outcome, rationale, decidedBy, decidedOn);
    }
}
