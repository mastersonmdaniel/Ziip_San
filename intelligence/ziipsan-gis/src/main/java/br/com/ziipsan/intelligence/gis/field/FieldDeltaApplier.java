package br.com.ziipsan.intelligence.gis.field;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aplicacao de deltas de campo no servidor (ED-66 / GIS-15, §8 + §6.3):
 * os deltas entram num pacote de edicao FIELD (gis_change_set) — esta
 * classe e a regra PURA de aceite de cada delta sobre o estado atual
 * da feicao; a escrita no pacote e da integracao.
 *
 * <ul>
 *   <li><b>Idempotencia:</b> client_change_id ja visto devolve
 *       DUPLICATE e nada muda — o reenvio do aparelho nao duplica
 *       (mesmo desenho do task_id da ED-18).</li>
 *   <li><b>Base atual:</b> versao de base igual a versao atual aplica
 *       os campos alterados sobre o estado atual (APPLIED).</li>
 *   <li><b>Base desatualizada:</b> o conflito e POR ATRIBUTO — campo
 *       cujo valor atual difere do valor de base que o aparelho viu e
 *       CONFLITO (vai para a fila, resolucao na web); campo que ninguem
 *       mexeu desde a base APLICA mesmo com a versao defasada.</li>
 *   <li><b>As-built nos DOIS verbos (correcao M1 da revisao):</b> a
 *       decisao nº 5 vale para INSERT e UPDATE igualmente — todo delta
 *       {@code asBuilt} passa por {@code GisFieldRules.asBuiltRefusals}
 *       com a meta da classe resolvida no servidor
 *       (gis.accuracy_targets): sem meta, REJECTED nomeado
 *       AS_BUILT_TARGET_MISSING; fora da meta, REJECTED com as recusas
 *       do portao. Antes, INSERT nem chegava a este aplicador.</li>
 *   <li><b>Validade do pacote (correcao M2 da revisao):</b> delta de
 *       pacote vencido (avaliado depois de valid_until) devolve
 *       REJECTED nomeado PACKAGE_EXPIRED, sem tocar no estado — o
 *       espelho servidor da recusa do aparelho (builders/prepareUpload).</li>
 * </ul>
 */
public final class FieldDeltaApplier {

    public enum Outcome { APPLIED, DUPLICATE, CONFLICT, REJECTED }

    public enum Operation { INSERT, UPDATE }

    /** Estado atual da feicao no servidor (versao + atributos correntes). */
    public record FeatureState(long version, Map<String, Object> attributes) {
        public FeatureState {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    /**
     * Delta como chega do aparelho (espelho de GisDelta, campo/src/gis).
     *
     * <p>INSERT de coleta nova tem {@code baseVersion == null} e
     * {@code operation == INSERT} (correcao M1: antes o construtor
     * rejeitava base nula e nenhum INSERT passava por aqui). UPDATE
     * exige a versao de base. Delta {@code asBuilt} carrega o fix e a
     * meta resolvida da classe ({@code target}); sem meta, o aplicador
     * recusa pelo nome. {@code packageValidUntil}/{@code evaluatedAt}
     * alimentam a checagem de validade (M2): ambos presentes e
     * avaliacao apos a validade = REJECTED.
     */
    public record Delta(String clientChangeId, Long baseVersion,
                        Map<String, Object> changedAttributes, Map<String, Object> baseAttributes,
                        Operation operation, boolean asBuilt,
                        GisFieldRules.GnssFix fix, GisFieldRules.AccuracyTarget target,
                        Instant packageValidUntil, Instant evaluatedAt) {
        public Delta {
            Objects.requireNonNull(clientChangeId, "client_change_id e obrigatorio");
            Objects.requireNonNull(operation, "operacao do delta (INSERT/UPDATE) e obrigatoria");
            changedAttributes = changedAttributes == null ? Map.of() : Map.copyOf(changedAttributes);
            baseAttributes = baseAttributes == null ? Map.of() : Map.copyOf(baseAttributes);
            if (operation == Operation.UPDATE && baseVersion == null) throw new IllegalArgumentException(
                    "delta de edicao (UPDATE) exige a versao de base");
            if (operation == Operation.INSERT && baseVersion != null) throw new IllegalArgumentException(
                    "delta de coleta nova (INSERT) nao tem versao de base");
        }

        /** Compatibilidade: delta de EDICAO simples (UPDATE, sem as-built, sem validade). */
        public Delta(String clientChangeId, Long baseVersion,
                     Map<String, Object> changedAttributes, Map<String, Object> baseAttributes) {
            this(clientChangeId, baseVersion, changedAttributes, baseAttributes,
                    Operation.UPDATE, false, null, null, null, null);
        }

        /** Delta de COLETA NOVA (INSERT): sem versao de base, com fix/meta/validade. */
        public static Delta insert(String clientChangeId, Map<String, Object> attributes,
                                   boolean asBuilt, GisFieldRules.GnssFix fix,
                                   GisFieldRules.AccuracyTarget target,
                                   Instant packageValidUntil, Instant evaluatedAt) {
            return new Delta(clientChangeId, null, attributes, Map.of(),
                    Operation.INSERT, asBuilt, fix, target, packageValidUntil, evaluatedAt);
        }

        /** Delta de EDICAO (UPDATE) com as-built e/ou validade do pacote. */
        public static Delta update(String clientChangeId, long baseVersion,
                                   Map<String, Object> changedAttributes, Map<String, Object> baseAttributes,
                                   boolean asBuilt, GisFieldRules.GnssFix fix,
                                   GisFieldRules.AccuracyTarget target,
                                   Instant packageValidUntil, Instant evaluatedAt) {
            return new Delta(clientChangeId, baseVersion, changedAttributes, baseAttributes,
                    Operation.UPDATE, asBuilt, fix, target, packageValidUntil, evaluatedAt);
        }
    }

    /**
     * Resultado da aplicacao.
     *
     * @param receiptClientChangeId correcao m2 da revisao: o id que o
     *        chamador DEVE gravar no recibo (gis_field_delta_receipt)
     *        na MESMA transacao da aplicacao — presente em todo desfecho
     *        terminal (APPLIED/CONFLICT/REJECTED) e null em DUPLICATE
     *        (o recibo ja existe). O UNIQUE do SQL segue como anteparo.
     * @param rejectionReasons recusas nomeadas quando REJECTED (M1/M2).
     */
    public record Result(Outcome outcome, FeatureState newState, List<String> conflictFields,
                         String receiptClientChangeId, List<String> rejectionReasons) {
        public Result {
            conflictFields = conflictFields == null ? List.of() : List.copyOf(conflictFields);
            rejectionReasons = rejectionReasons == null ? List.of() : List.copyOf(rejectionReasons);
        }

        /** Compatibilidade com o contrato de 3 componentes. */
        public Result(Outcome outcome, FeatureState newState, List<String> conflictFields) {
            this(outcome, newState, conflictFields, null, List.of());
        }
    }

    private FieldDeltaApplier() {}

    private static boolean sameValue(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * Fecha o ciclo da idempotencia NESTA porta (correcao m2): registra
     * o id no conjunto de vistos e o devolve como id do recibo a gravar.
     * O conjunto DEVE ser mutavel — conjunto imutavel no desfecho
     * terminal e erro nomeado do chamador, nunca ciclo aberto em silencio.
     */
    private static String registerReceipt(Set<String> seenClientChangeIds, String clientChangeId) {
        try {
            seenClientChangeIds.add(clientChangeId);
        } catch (UnsupportedOperationException e) {
            throw new IllegalArgumentException(
                    "conjunto de ids vistos deve ser MUTAVEL: apply registra nele o client_change_id "
                            + "do recibo (ciclo da idempotencia, correcao m2)", e);
        }
        return clientChangeId;
    }

    private static Result rejected(FeatureState current, Delta delta, Set<String> seen, List<String> reasons) {
        String receiptId = registerReceipt(seen, delta.clientChangeId());
        return new Result(Outcome.REJECTED, current, List.of(), receiptId, reasons);
    }

    /**
     * Aplica um delta (INSERT ou UPDATE) sobre o estado atual.
     *
     * @param current estado atual da feicao; em INSERT pode ser null
     *        (a feicao ainda nao existe no servidor).
     * @param seenClientChangeIds ids com recibo ja persistido; DEVE ser
     *        mutavel — em desfecho terminal o {@link #apply} REGISTRA o
     *        id do delta nele e o devolve em
     *        {@code Result.receiptClientChangeId} para o chamador gravar
     *        o recibo na mesma transacao (correcao m2; o UNIQUE de
     *        gis_field_delta_receipt segue como anteparo).
     */
    public static Result apply(FeatureState current, Delta delta, Set<String> seenClientChangeIds) {
        Objects.requireNonNull(delta, "delta e obrigatorio");
        Objects.requireNonNull(seenClientChangeIds, "conjunto de ids vistos e obrigatorio");
        FeatureState state = current == null ? new FeatureState(0, Map.of()) : current;
        if (seenClientChangeIds.contains(delta.clientChangeId())) {
            return new Result(Outcome.DUPLICATE, state, List.of());
        }
        // M2 (servidor): validade do pacote de origem do delta.
        if (delta.packageValidUntil() != null && delta.evaluatedAt() != null
                && delta.evaluatedAt().isAfter(delta.packageValidUntil())) {
            return rejected(state, delta, seenClientChangeIds, List.of("PACKAGE_EXPIRED"));
        }
        // M1: portao do as-built nos DOIS verbos, com a meta do servidor.
        if (delta.asBuilt()) {
            if (delta.target() == null) {
                return rejected(state, delta, seenClientChangeIds, List.of("AS_BUILT_TARGET_MISSING"));
            }
            if (delta.fix() == null) {
                return rejected(state, delta, seenClientChangeIds, List.of("AS_BUILT_FIX_REFUSED"));
            }
            List<String> refusals = GisFieldRules.asBuiltRefusals(delta.fix(), delta.target());
            if (!refusals.isEmpty()) {
                return rejected(state, delta, seenClientChangeIds, refusals);
            }
        }
        if (delta.operation() == Operation.INSERT) {
            if (current != null) throw new IllegalArgumentException(
                    "delta INSERT exige feicao inexistente no servidor (estado atual deve ser null)");
            Map<String, Object> created = new LinkedHashMap<>(delta.changedAttributes());
            String receiptId = registerReceipt(seenClientChangeIds, delta.clientChangeId());
            return new Result(Outcome.APPLIED, new FeatureState(1, created), List.of(), receiptId, List.of());
        }
        Objects.requireNonNull(current, "estado atual da feicao e obrigatorio para UPDATE");
        Map<String, Object> next = new LinkedHashMap<>(current.attributes());
        if (delta.baseVersion() == current.version()) {
            next.putAll(delta.changedAttributes());
            String receiptId = registerReceipt(seenClientChangeIds, delta.clientChangeId());
            return new Result(Outcome.APPLIED, new FeatureState(current.version() + 1, next),
                    List.of(), receiptId, List.of());
        }
        // Base desatualizada: conflito por atributo.
        List<String> conflicts = new ArrayList<>();
        for (String field : delta.changedAttributes().keySet()) {
            Object baseValue = delta.baseAttributes().get(field);
            Object currentValue = current.attributes().get(field);
            if (!sameValue(baseValue, currentValue)) conflicts.add(field);
        }
        if (!conflicts.isEmpty()) {
            String receiptId = registerReceipt(seenClientChangeIds, delta.clientChangeId());
            return new Result(Outcome.CONFLICT, current, conflicts, receiptId, List.of());
        }
        next.putAll(delta.changedAttributes());
        String receiptId = registerReceipt(seenClientChangeIds, delta.clientChangeId());
        return new Result(Outcome.APPLIED, new FeatureState(current.version() + 1, next),
                List.of(), receiptId, List.of());
    }
}
