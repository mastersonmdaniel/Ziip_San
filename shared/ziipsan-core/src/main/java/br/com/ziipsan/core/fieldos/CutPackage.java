package br.com.ziipsan.core.fieldos;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pacote de campo de uma OS de CORTE (ED-20): para cada OS de corte, o
 * pacote que desce ao aparelho leva o estado do debito, a contestacao, o
 * aviso e as vedacoes — o retrato (snapshot) na emissao e os fatos que a
 * revalidacao confere, no aparelho (offline, contra o retrato) e no
 * servidor (na sincronizacao, contra os fatos ATUAIS da porta).
 */
public final class CutPackage {

    public enum DebitState { OPEN, PAID, NONE }

    public enum Prohibition {
        /** Debito contestado pelo usuario: corte vedado enquanto a contestacao estiver aberta. */
        CONTESTATION_OPEN,
        /** Aviso previo de corte nao comprovado: corte vedado. */
        NOTICE_NOT_GIVEN,
        /** Vedacao legal/regulatoria registrada para a unidade (ex.: unidade essencial). */
        LEGAL_PROHIBITION
    }

    /**
     * Fatos de corte num instante: usados como retrato da emissao E como
     * leitura atual do servidor — a mesma forma, para a mesma regra
     * ({@link CutRevalidation}) valer nos dois lados.
     */
    public record Facts(DebitState debitState, boolean contestationOpen, boolean noticeGiven,
                        Set<Prohibition> prohibitions) {
        public Facts {
            Objects.requireNonNull(debitState, "estado do debito e obrigatorio");
            prohibitions = prohibitions == null ? Set.of() : Set.copyOf(prohibitions);
            if (contestationOpen && !prohibitions.contains(Prohibition.CONTESTATION_OPEN)) {
                var merged = new java.util.HashSet<>(prohibitions);
                merged.add(Prohibition.CONTESTATION_OPEN);
                prohibitions = Set.copyOf(merged);
            }
            if (!noticeGiven && !prohibitions.contains(Prohibition.NOTICE_NOT_GIVEN)) {
                var merged = new java.util.HashSet<>(prohibitions);
                merged.add(Prohibition.NOTICE_NOT_GIVEN);
                prohibitions = Set.copyOf(merged);
            }
        }
    }

    /** Retrato emitido ao aparelho: OS, unidade, tarefa (task_id da ED-18) e fatos na emissao. */
    public record Snapshot(UUID orderId, UUID unitId, String taskId, Instant issuedAt, Facts factsAtIssue) {
        public Snapshot {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            Objects.requireNonNull(unitId, "unidade e obrigatoria");
            if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("task_id da tarefa de campo e obrigatorio");
            Objects.requireNonNull(issuedAt, "instante de emissao do pacote e obrigatorio");
            Objects.requireNonNull(factsAtIssue, "fatos na emissao sao obrigatorios");
        }
    }

    private CutPackage() {}
}
