package br.com.ziipsan.commercial.billing.cycle;

import br.com.ziipsan.commercial.billing.bill.BillIdempotency;
import br.com.ziipsan.commercial.billing.bill.BillStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Decisoes puras do ciclo (P2.5/F1), sem leitura de dados: o servico monta o retrato
 * (catalogo, itens do cronograma, referencia aberta ou fechada) e esta regra decide.
 * Nenhuma ausencia vira permissao: item inexistente, entrada de catalogo inexistente
 * e cronograma vazio sao decisoes de recusa explicitas.
 *
 * <pre>
 *   comando de atividade:
 *     referencia fechada                                   -> REFERENCE_CLOSED
 *     atividade fora do catalogo do retrato                -> CATALOG_ENTRY_MISSING
 *     sem instancia da atividade no grupo/referencia       -> NO_SUCH_INSTANCE
 *     ultima ocorrencia DONE e atividade nao repetivel     -> ALREADY_DONE_NOT_REPEATABLE
 *     ultima ocorrencia DONE e atividade repetivel         -> NEW_OCCURRENCE_ALLOWED
 *     ultima ocorrencia COMMANDED                          -> ALREADY_COMMANDED
 *     ultima ocorrencia CANCELLED                          -> INSTANCE_CANCELLED
 *     precedente sem ocorrencia DONE:
 *       faturamento cuja precedente e a transmissao        -> ROUTE_NOT_TRANSMITTED
 *       qualquer outra                                     -> PREDECESSOR_NOT_DONE
 *     precedente DONE (ou inexistente: primeira da cadeia) -> ALLOWED
 *   fechamento da referencia: so ALLOWED quando, para cada grupo do cronograma e
 *   cada atividade obrigatoria do catalogo, a ultima ocorrencia esta DONE.
 *   geracao de conta: referencia fechada recusa antes de tudo; aberta, quem decide
 *   e BillIdempotency sobre as contas existentes da unidade na referencia.
 * </pre>
 */
public final class CycleCommandRule {

    /** Atividade do catalogo, como a regra a ve. */
    public record ActivityView(CycleActivityCode code, boolean mandatory, boolean repeatable,
                               CycleActivityCode predecessor) {
    }

    /** Uma ocorrencia de atividade de um grupo numa referencia. */
    public record ItemView(UUID groupId, CycleActivityCode activity, ScheduleItemStatus status, int occurrenceNo) {
    }

    /** Retrato do ciclo: catalogo e itens de uma referencia (um grupo ou todos). */
    public record Snapshot(boolean referenceClosed, List<ActivityView> catalog, List<ItemView> items) {
    }

    public enum CommandDecision {
        ALLOWED,
        NEW_OCCURRENCE_ALLOWED,
        REFERENCE_CLOSED,
        CATALOG_ENTRY_MISSING,
        NO_SUCH_INSTANCE,
        PREDECESSOR_NOT_DONE,
        ROUTE_NOT_TRANSMITTED,
        ALREADY_COMMANDED,
        ALREADY_DONE_NOT_REPEATABLE,
        INSTANCE_CANCELLED
    }

    public enum CloseDecision {
        ALLOWED,
        ALREADY_CLOSED,
        MANDATORY_PENDING
    }

    /** Resultado do fechamento: a decisao e, na recusa, o que falta (grupo + atividade). */
    public record CloseEvaluation(CloseDecision decision, List<String> pending) {
    }

    public enum GenerationDecision {
        CREATE,
        REPLACE_PRE_BILLED,
        ALREADY_BILLED,
        REFERENCE_CLOSED
    }

    private CycleCommandRule() {
    }

    public static CommandDecision evaluateCommand(Snapshot snapshot, UUID groupId, CycleActivityCode target) {
        if (snapshot.referenceClosed()) {
            return CommandDecision.REFERENCE_CLOSED;
        }
        ActivityView activity = null;
        for (ActivityView view : snapshot.catalog()) {
            if (view.code() == target) {
                activity = view;
                break;
            }
        }
        if (activity == null) {
            return CommandDecision.CATALOG_ENTRY_MISSING;
        }
        ItemView latest = latest(snapshot, groupId, target);
        if (latest == null) {
            return CommandDecision.NO_SUCH_INSTANCE;
        }
        switch (latest.status()) {
            case DONE:
                return activity.repeatable()
                        ? CommandDecision.NEW_OCCURRENCE_ALLOWED
                        : CommandDecision.ALREADY_DONE_NOT_REPEATABLE;
            case COMMANDED:
                return CommandDecision.ALREADY_COMMANDED;
            case CANCELLED:
                return CommandDecision.INSTANCE_CANCELLED;
            case PENDING:
                break;
        }
        if (activity.predecessor() == null) {
            return CommandDecision.ALLOWED;
        }
        ItemView predecessor = latest(snapshot, groupId, activity.predecessor());
        if (predecessor != null && predecessor.status() == ScheduleItemStatus.DONE) {
            return CommandDecision.ALLOWED;
        }
        if (target == CycleActivityCode.BILLING && activity.predecessor() == CycleActivityCode.TRANSMISSION) {
            return CommandDecision.ROUTE_NOT_TRANSMITTED;
        }
        return CommandDecision.PREDECESSOR_NOT_DONE;
    }

    public static CloseEvaluation evaluateClose(Snapshot snapshot) {
        if (snapshot.referenceClosed()) {
            return new CloseEvaluation(CloseDecision.ALREADY_CLOSED, List.of());
        }
        Set<UUID> groups = new LinkedHashSet<>();
        for (ItemView item : snapshot.items()) {
            groups.add(item.groupId());
        }
        List<String> pending = new ArrayList<>();
        if (groups.isEmpty()) {
            pending.add("NO_SCHEDULE_ITEMS");
        }
        for (UUID groupId : groups) {
            for (ActivityView activity : snapshot.catalog()) {
                if (!activity.mandatory()) {
                    continue;
                }
                ItemView latest = latest(snapshot, groupId, activity.code());
                if (latest == null || latest.status() != ScheduleItemStatus.DONE) {
                    pending.add("group=" + groupId + " activity=" + activity.code());
                }
            }
        }
        return pending.isEmpty()
                ? new CloseEvaluation(CloseDecision.ALLOWED, List.of())
                : new CloseEvaluation(CloseDecision.MANDATORY_PENDING, List.copyOf(pending));
    }

    /**
     * Geracao de conta numa referencia: fechada, recusa sempre; aberta, a decisao e a
     * de {@link BillIdempotency} sobre as contas existentes da unidade na referencia
     * (ciclo repetido nao gera segunda conta; a garantia final e o indice unico
     * parcial de contas vivas da V4_1).
     */
    public static GenerationDecision evaluateGeneration(boolean referenceClosed,
                                                        Collection<BillStatus> existingForUnitAndReference) {
        if (referenceClosed) {
            return GenerationDecision.REFERENCE_CLOSED;
        }
        return switch (BillIdempotency.decide(existingForUnitAndReference)) {
            case CREATE -> GenerationDecision.CREATE;
            case REPLACE_PRE_BILLED -> GenerationDecision.REPLACE_PRE_BILLED;
            case ALREADY_BILLED -> GenerationDecision.ALREADY_BILLED;
        };
    }

    /** A ocorrencia mais recente de uma atividade de um grupo, ou nula se nao ha nenhuma. */
    private static ItemView latest(Snapshot snapshot, UUID groupId, CycleActivityCode activity) {
        ItemView found = null;
        for (ItemView item : snapshot.items()) {
            if (item.groupId().equals(groupId) && item.activity() == activity
                    && (found == null || item.occurrenceNo() > found.occurrenceNo())) {
                found = item;
            }
        }
        return found;
    }
}
