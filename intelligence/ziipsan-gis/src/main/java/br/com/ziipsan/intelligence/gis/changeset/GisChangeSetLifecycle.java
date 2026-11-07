package br.com.ziipsan.intelligence.gis.changeset;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Regras puras da GIS-04 (§3.7 e §6.1/§6.2), espelho legível das guardas
 * do banco na V13_9 — o banco é a autoridade (recusa inclusive por SQL
 * direto); esta classe existe para a api falhar cedo, com o erro certo, e
 * para as regras serem testáveis sem banco.
 */
public final class GisChangeSetLifecycle {

    /** Estados do pacote (§6.1). */
    public static final Set<String> CHANGE_SET_STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "IN_REVIEW", "APPROVED", "APPLIED",
            "RETURNED", "REJECTED", "CANCELLED");

    /** Transições do pacote (§6.1), idênticas às da guarda do banco. */
    private static final Map<String, Set<String>> CHANGE_SET_TRANSITIONS = Map.of(
            "DRAFT", Set.of("SUBMITTED", "CANCELLED"),
            "SUBMITTED", Set.of("IN_REVIEW", "RETURNED", "CANCELLED"),
            "IN_REVIEW", Set.of("APPROVED", "APPLIED", "RETURNED", "REJECTED", "CANCELLED"),
            "RETURNED", Set.of("SUBMITTED", "CANCELLED"),
            "APPROVED", Set.of("APPLIED"),
            "APPLIED", Set.of(),
            "REJECTED", Set.of(),
            "CANCELLED", Set.of());

    /** Transições de ciclo de vida da feição (§3.7), sem a permanência. */
    private static final Map<String, Set<String>> FEATURE_TRANSITIONS = Map.of(
            "PLANNED", Set.of("UNDER_CONSTRUCTION", "IN_SERVICE", "ABANDONED"),
            "UNDER_CONSTRUCTION", Set.of("IN_SERVICE"),
            "IN_SERVICE", Set.of("ABANDONED"),
            "ABANDONED", Set.of());

    /** Tabelas de feição que um item de pacote pode referenciar. */
    public static final Set<String> FEATURE_TABLES = Set.of(
            "gis_node", "gis_arc", "gis_connection", "gis_structure",
            "gis_zone", "gis_poi");

    /** Tipos de pacote do §6.1 (os mesmos do CHECK da V13_9). */
    public static final Set<String> CHANGE_SET_TYPES = Set.of(
            "PROJECT", "AS_BUILT", "CORRECTION", "IMPORT", "FIELD", "QGIS",
            "SERVICE_ORDER", "AUTO_SERVICE_LINE");

    /** Canais de pacote (os mesmos do CHECK da V13_9). */
    public static final Set<String> CHANGE_SET_CHANNELS = Set.of(
            "WEB", "QGIS", "FIELD", "IMPORT", "API", "SERVICE_ORDER", "AUTO");

    private GisChangeSetLifecycle() {
    }

    public static boolean canTransitionChangeSet(String from, String to) {
        return CHANGE_SET_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean canTransitionFeature(String from, String to) {
        return from.equals(to)
                || FEATURE_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /** Feição em operação: fora de traçado/indicadores quando não está aqui. */
    public static boolean isOperational(String lifecycleStatus) {
        return "IN_SERVICE".equals(lifecycleStatus)
                || "UNDER_CONSTRUCTION".equals(lifecycleStatus);
    }

    /** Revisão por outra pessoa (§6.2): autor nunca revisa o próprio pacote. */
    public static void ensureReviewerIsNotAuthor(UUID authorId, UUID reviewerId) {
        if (authorId != null && authorId.equals(reviewerId)) {
            throw new SelfReviewException();
        }
    }

    /** 403 da api para a auto-revisão; o banco recusa com a mesma regra. */
    public static final class SelfReviewException extends RuntimeException {
        public SelfReviewException() {
            super("autor não revisa o próprio pacote (revisão por outra pessoa, GIS-04)");
        }
    }
}
