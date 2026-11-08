package br.com.ziipsan.commercial.collection.dunning.scoring;

import br.com.ziipsan.commercial.collection.dunning.DunningParameters;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Escore de propensao de atraso (GES-24), sob o ADR-0042: ele SO ordena o
 * contato e adapta canal, horario e oferta de parcelamento. Desligado por
 * padrao em {@code ai.collection_scoring_enabled}. Sem dado do Open
 * Finance: o escore chega calculado de fora (porta de quem o calcula) e
 * esta tarefa nao le nenhuma fonte financeira externa.
 *
 * <p>O titular tem direito a EXPLICACAO (fatores do escore) e a OPOSICAO
 * (LGPD, art. 20): quem se opoe sai da ordenacao por escore por inteiro
 * (conserva a posicao neutra de entrada) e nao e adaptado. E, por
 * desenho, NENHUM portao de protecao DESTE modulo consome este tipo —
 * ver {@code CollectionGates} e o teste de arquitetura
 * {@code ScoringIsolationArchitectureTest}: corte, negativacao e
 * protesto nunca leem o escore. A tarifa social nao vive neste modulo
 * (fronteira declarada no teste de arquitetura, D2).
 */
public final class CollectionScoring {

    private CollectionScoring() {
    }

    /** Escore de um contato (0..1), com os fatores que o explicam. */
    public record Score(UUID unitId, double value, List<String> factors) {
        public Score {
            if (unitId == null || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("escore exige unidade e valor entre 0 e 1");
            }
            factors = factors == null ? List.of() : List.copyOf(factors);
        }
    }

    /** Contato da fila de cobranca. */
    public record Contact(UUID unitId, String channel, String preferredHourBand, boolean installmentOffer) {
        public Contact {
            if (unitId == null) {
                throw new IllegalArgumentException("contato exige unidade");
            }
        }
    }

    /** Explicacao devolvida ao titular que a pede (LGPD, art. 20). */
    public record Explanation(UUID unitId, double value, List<String> factors, boolean scoringEnabled) {
    }

    /**
     * Ordena a fila de contato pelo escore (maior propensao primeiro) e
     * adapta canal/horario/oferta. Com o escore DESLIGADO, a ordem e a de
     * entrada (neutra) e nenhuma adaptacao e feita.
     *
     * <p>OPOSICAO do titular (correcao D3 da revisao cruzada): quem se
     * opoe sai da ordenacao por escore POR INTEIRO — conserva a POSICAO
     * que tinha na fila de entrada e nao e adaptado; o escore dele nao o
     * promove nem o rebaixa. Os demais sao ordenados pelo escore apenas
     * nas posicoes restantes. (A versao anterior dava chave 0,0 ao oposto
     * e o REBAIXAVA atras de qualquer escore positivo: a sonda do revisor
     * [B oposto 0,95, A 0,30] saia [A, B].)
     */
    public static List<Contact> orderContacts(List<Contact> contacts, List<Score> scores,
                                              Set<UUID> opposedUnitIds, DunningParameters params) {
        if (!params.collectionScoringEnabled()) {
            return List.copyOf(contacts);
        }
        java.util.Map<UUID, Score> byUnit = new java.util.HashMap<>();
        for (Score score : scores) {
            byUnit.put(score.unitId(), score);
        }
        Set<UUID> opposed = opposedUnitIds == null ? Set.of() : opposedUnitIds;
        List<Contact> orderable = contacts.stream()
                .filter(c -> !opposed.contains(c.unitId()))
                .sorted(Comparator.comparingDouble((Contact c) -> {
                    Score score = byUnit.get(c.unitId());
                    return score == null ? 0.0 : -score.value();
                }))
                .map(c -> adapt(c, byUnit.get(c.unitId()), false))
                .toList();
        Contact[] result = new Contact[contacts.size()];
        int next = 0;
        for (int i = 0; i < contacts.size(); i++) {
            if (opposed.contains(contacts.get(i).unitId())) {
                result[i] = contacts.get(i);
            } else {
                result[i] = orderable.get(next++);
            }
        }
        return List.of(result);
    }

    /**
     * Adaptacao por faixa de escore: apenas canal, horario e oferta de
     * parcelamento — nunca uma decisao de corte, negativacao, protesto
     * ou tarifa. As faixas sao a regra desta tarefa sobre o escore ja
     * calculado; sem escore ou com oposicao, nada muda.
     */
    private static Contact adapt(Contact contact, Score score, boolean opposed) {
        if (score == null || opposed) {
            return contact;
        }
        if (score.value() >= 0.7) {
            return new Contact(contact.unitId(), "WHATSAPP", "EVENING", true);
        }
        if (score.value() >= 0.4) {
            return new Contact(contact.unitId(), contact.channel(), "MORNING", true);
        }
        return contact;
    }

    /** Explicacao ao titular: fatores registrados do escore e o estado do interruptor. */
    public static Explanation explain(UUID unitId, List<Score> scores, DunningParameters params) {
        boolean enabled = params.collectionScoringEnabled();
        Score score = scores.stream().filter(s -> s.unitId().equals(unitId)).findFirst().orElse(null);
        if (!enabled || score == null) {
            return new Explanation(unitId, 0.0, List.of(), enabled);
        }
        return new Explanation(unitId, score.value(), score.factors(), true);
    }
}
