package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Confronto do pedido de eliminação com a tabela de retenção (aceite da P3.40; LGPD,
 * art. 16): dado com guarda legal ou regulatória NÃO é eliminado no pedido — fica
 * BLOQUEADO e é eliminado ou anonimizado ao fim da guarda, com a justificativa na
 * resposta ao titular.
 *
 * <p>A fonte da guarda é a {@code lgpd_retention_schedule} da {@code V2_5}
 * ({@code entity_type}, {@code category}, {@code retention_days}, {@code legal_hold_reason},
 * {@code disposal_action}, {@code active}): os prazos são os que o controlador declarou —
 * a {@code V2_5} não semeia prazo nenhum e esta classe também não inventa nenhum. Quem
 * chama informa o fim da guarda já calculado da linha ativa correspondente; sem linha
 * de retenção, não há guarda e a eliminação segue.
 *
 * <p>PENDÊNCIA DECLARADA: a {@code V2_5} não fixa o domínio de {@code disposal_action}
 * (VARCHAR(30) sem CHECK). O desfecho pós-guarda lê a ação declarada pelo tenant:
 * contendo {@code ANONYM} vira anonimização; qualquer outra ação declarada vira
 * eliminação. Se um dia o domínio for fechado em catálogo, esta leitura some.
 *
 * <p>Bloqueio sem justificativa é impossível por construção: guarda vigente exige
 * {@code legalHoldReason} não vazio, porque a resposta ao titular precisa dela.
 */
public final class RetentionDecision {

    private RetentionDecision() {
    }

    /** Desfecho do confronto. */
    public enum Outcome {
        /** Sem guarda vigente: eliminar já. */
        ELIMINATE_NOW,
        /** Sem guarda vigente e a ação declarada é anonimizar: anonimizar já. */
        ANONYMIZE_NOW,
        /** Guarda vigente: bloquear até o fim da guarda e só então dispor. */
        BLOCKED_UNTIL_GUARD_END
    }

    /**
     * Linha de retenção aplicável ao dado do pedido, já filtrada (ativa e do tipo/categoria
     * do dado) por quem lê a {@code lgpd_retention_schedule}.
     *
     * @param guardEndsAt fim da guarda calculado da linha; {@code null} = linha sem guarda vigente
     */
    public record RetentionEntry(String entityType, String category, String disposalAction,
                                 String legalHoldReason, LocalDate guardEndsAt) {

        public RetentionEntry {
            if (entityType == null || entityType.isBlank()) {
                throw new IllegalArgumentException("entityType da retenção é obrigatório");
            }
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("category da retenção é obrigatória");
            }
            if (disposalAction == null || disposalAction.isBlank()) {
                throw new IllegalArgumentException("disposalAction da retenção é obrigatória: "
                        + "é ela que diz o que acontece ao fim da guarda");
            }
        }
    }

    /**
     * Decisão do confronto.
     *
     * @param justification justificativa que vai na resposta ao titular (obrigatória no bloqueio)
     * @param disposalDate  data em que a disposição acontece (hoje, ou o fim da guarda)
     * @param disposalAction ação declarada na retenção que governa o fim da guarda
     */
    public record Decision(Outcome outcome, String justification, LocalDate disposalDate,
                           String disposalAction) {
    }

    /**
     * Decide o pedido de eliminação contra as linhas de retenção aplicáveis.
     *
     * @param entries linhas ativas aplicáveis ao dado (vazia = sem retenção declarada)
     * @param today   data da decisão
     */
    public static Decision decide(List<RetentionEntry> entries, LocalDate today) {
        Objects.requireNonNull(entries, "entries é obrigatório (lista vazia = sem retenção)");
        Objects.requireNonNull(today, "today é obrigatório");

        RetentionEntry blocking = entries.stream()
                .filter(e -> e.guardEndsAt() != null && !e.guardEndsAt().isBefore(today))
                .max(Comparator.comparing(RetentionEntry::guardEndsAt))
                .orElse(null);
        if (blocking != null) {
            if (blocking.legalHoldReason() == null || blocking.legalHoldReason().isBlank()) {
                throw new IllegalStateException("bloqueio sem justificativa: a linha de "
                        + "retenção de " + blocking.entityType() + "/" + blocking.category()
                        + " está em guarda até " + blocking.guardEndsAt() + " mas não declara "
                        + "legal_hold_reason — a resposta ao titular exige a justificativa "
                        + "(LGPD, art. 16)");
            }
            return new Decision(Outcome.BLOCKED_UNTIL_GUARD_END,
                    "Dado mantido bloqueado até " + blocking.guardEndsAt()
                            + " por guarda legal ou regulatória (LGPD, art. 16): "
                            + blocking.legalHoldReason()
                            + ". Ao fim da guarda será " + disposalLabel(blocking.disposalAction())
                            + ", sem nova solicitação do titular.",
                    blocking.guardEndsAt(), blocking.disposalAction());
        }

        if (entries.isEmpty()) {
            return new Decision(Outcome.ELIMINATE_NOW,
                    "Sem linha de retenção aplicável: eliminação imediata.", today, "DELETE");
        }
        RetentionEntry governing = entries.stream()
                .max(Comparator.comparing(e -> e.guardEndsAt() == null
                        ? LocalDate.MIN : e.guardEndsAt()))
                .orElseThrow();
        Outcome outcome = governing.disposalAction().toUpperCase(java.util.Locale.ROOT)
                .contains("ANONYM")
                ? Outcome.ANONYMIZE_NOW : Outcome.ELIMINATE_NOW;
        return new Decision(outcome,
                "Guarda encerrada: disposição imediata pela ação declarada na retenção ("
                        + governing.disposalAction() + ").",
                today, governing.disposalAction());
    }

    private static String disposalLabel(String disposalAction) {
        return disposalAction.toUpperCase(java.util.Locale.ROOT).contains("ANONYM")
                ? "anonimizado" : "eliminado";
    }
}
