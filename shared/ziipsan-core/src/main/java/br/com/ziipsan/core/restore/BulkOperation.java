package br.com.ziipsan.core.restore;

import br.com.ziipsan.core.json.Json;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Operacao em massa reversivel em duas etapas (P3.33): simulacao e
 * aplicacao, identificadas por {@code run_id}, com remocao POR EXECUCAO
 * e correcao do efeito de negocio por OS inversa (ADR-0039).
 *
 * <p>Ciclo de vida fechado: {@code SIMULATED -> APPLIED -> REVERTED},
 * mais {@code DISCARDED} a partir de {@code SIMULATED} (simulacao que
 * nao vira aplicacao). Nenhuma transicao fora desta lista existe:
 * aplicar sem simular, aplicar duas vezes, reverter simulacao ou
 * reverter duas vezes sao recusados com codigo estavel.
 *
 * <p>Duas camadas de desfazer, que nao se confundem:
 * <ul>
 *   <li><b>remocao por execucao</b> (dado): desfaz exatamente as linhas
 *       da execucao {@code runId}, pela imagem-anterior gravada na
 *       aplicacao (o roteiro/script guarda a imagem por {@code run_id};
 *       pedir a remocao de um {@code run_id} diverso do aplicado e
 *       recusado — {@code run-id-mismatch} — para uma execucao nunca
 *       desfazer outra);</li>
 *   <li><b>OS inversa</b> (negocio, ADR-0039): quando a operacao gerou
 *       efeito de negocio por OS (efeito aplicado no encerramento,
 *       {@code service_order_effects} da P1.31), a correcao NAO e
 *       remocao de linha: e uma OS inversa ligada a OS de origem, que
 *       aplica o efeito contrario no seu proprio encerramento. O
 *       {@link InverseOrderRequest} e o descritor dessa correcao.</li>
 * </ul>
 *
 * <p>Records imutaveis: cada transicao devolve uma copia no novo estado,
 * como o {@code AssistedConsent} da P3.17. JSON so pela classe
 * {@code Json} do core.
 */
public final class BulkOperation {

    private BulkOperation() {
    }

    /** Estados da execucao em massa. */
    public enum RunStatus {
        SIMULATED,
        APPLIED,
        REVERTED,
        DISCARDED
    }

    /** Recusa de transicao/regra da operacao em massa, com codigo estavel. */
    public static final class BulkRefusedException extends RuntimeException {
        private final String code;

        public BulkRefusedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Uma execucao (simulacao, aplicacao e eventual reversao) de operacao em massa. */
    public record BulkRun(
            UUID runId,
            UUID tenantId,
            String operation,
            long simulatedAffected,
            Instant simulatedAt,
            Long appliedAffected,
            Instant appliedAt,
            UUID appliedBy,
            Instant revertedAt,
            UUID revertedBy,
            RunStatus status) {

        public BulkRun {
            Objects.requireNonNull(runId, "runId e obrigatorio");
            Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operation e obrigatoria (tipo da operacao em massa)");
            }
            if (simulatedAffected < 0) {
                throw new IllegalArgumentException("simulatedAffected nao pode ser negativo");
            }
            Objects.requireNonNull(simulatedAt, "simulatedAt e obrigatorio");
            Objects.requireNonNull(status, "status e obrigatorio");
            if (status == RunStatus.APPLIED || status == RunStatus.REVERTED) {
                Objects.requireNonNull(appliedAt, "execucao aplicada exige appliedAt");
                Objects.requireNonNull(appliedBy, "execucao aplicada exige appliedBy");
                Objects.requireNonNull(appliedAffected, "execucao aplicada exige appliedAffected");
            }
            if (status == RunStatus.REVERTED) {
                Objects.requireNonNull(revertedAt, "execucao revertida exige revertedAt");
                Objects.requireNonNull(revertedBy, "execucao revertida exige revertedBy");
            }
            // O construtor canonico e publico: sem estas guardas, um
            // chamador montaria estados que as transicoes recusam
            // (SIMULATED ja "aplicada", APPLIED ja "revertida",
            // cronologia invertida) por fora da maquina de estados.
            if ((status == RunStatus.SIMULATED || status == RunStatus.DISCARDED)
                    && (appliedAt != null || appliedBy != null || appliedAffected != null
                        || revertedAt != null || revertedBy != null)) {
                throw new IllegalArgumentException(
                        "execucao nao aplicada (SIMULATED/DISCARDED) nao carrega applied*/reverted*");
            }
            if (status == RunStatus.APPLIED && (revertedAt != null || revertedBy != null)) {
                throw new IllegalArgumentException(
                        "execucao APPLIED nao carrega reverted*: a reversao muda o estado para REVERTED");
            }
            if ((revertedAt == null) != (revertedBy == null)) {
                throw new IllegalArgumentException("revertedAt e revertedBy andam juntos");
            }
            if (appliedAt != null && appliedAt.isBefore(simulatedAt)) {
                throw new IllegalArgumentException("appliedAt nao pode preceder simulatedAt");
            }
            if (revertedAt != null && appliedAt != null && revertedAt.isBefore(appliedAt)) {
                throw new IllegalArgumentException("revertedAt nao pode preceder appliedAt");
            }
        }

        /** Etapa 1 — simulacao: nasce a execucao, sem tocar em dado. */
        public static BulkRun simulate(UUID runId, UUID tenantId, String operation,
                                       long simulatedAffected, Instant at) {
            return new BulkRun(runId, tenantId, operation, simulatedAffected, at,
                    null, null, null, null, null, RunStatus.SIMULATED);
        }

        /** Etapa 2 — aplicacao: so a partir da simulacao desta mesma execucao. */
        public BulkRun apply(long appliedAffected, UUID by, Instant at) {
            Objects.requireNonNull(by, "appliedBy e obrigatorio");
            Objects.requireNonNull(at, "appliedAt e obrigatorio");
            if (appliedAffected < 0) {
                throw new IllegalArgumentException("appliedAffected nao pode ser negativo");
            }
            if (status != RunStatus.SIMULATED) {
                throw new BulkRefusedException("not-simulated",
                        "Aplicacao exige a etapa de simulacao desta execucao (estado atual: " + status + ").");
            }
            if (at.isBefore(simulatedAt)) {
                throw new BulkRefusedException("applied-before-simulation",
                        "A aplicacao nao pode preceder a simulacao.");
            }
            return new BulkRun(runId, tenantId, operation, simulatedAffected, simulatedAt,
                    appliedAffected, at, by, null, null, RunStatus.APPLIED);
        }

        /** Simulacao que nao vira aplicacao: descartada, sem efeito. */
        public BulkRun discard() {
            if (status != RunStatus.SIMULATED) {
                throw new BulkRefusedException("not-simulated",
                        "So uma simulacao pode ser descartada (estado atual: " + status + ").");
            }
            return new BulkRun(runId, tenantId, operation, simulatedAffected, simulatedAt,
                    null, null, null, null, null, RunStatus.DISCARDED);
        }

        /**
         * Remocao por execucao: reverte ESTA execucao. O
         * {@code requestedRunId} e conferido contra o {@code runId}
         * gravado — divergencia e recusada antes de olhar o estado,
         * para nenhuma remocao mirar a execucao errada.
         */
        public BulkRun revert(UUID requestedRunId, UUID by, Instant at) {
            Objects.requireNonNull(requestedRunId, "requestedRunId e obrigatorio");
            Objects.requireNonNull(by, "revertedBy e obrigatorio");
            Objects.requireNonNull(at, "revertedAt e obrigatorio");
            if (!runId.equals(requestedRunId)) {
                throw new BulkRefusedException("run-id-mismatch",
                        "A remocao e por execucao: o run_id pedido nao e o run_id desta execucao.");
            }
            if (status != RunStatus.APPLIED) {
                throw new BulkRefusedException("not-applied",
                        "So execucao aplicada pode ser revertida (estado atual: " + status + ").");
            }
            if (at.isBefore(appliedAt)) {
                throw new BulkRefusedException("reverted-before-application",
                        "A reversao nao pode preceder a aplicacao.");
            }
            return new BulkRun(runId, tenantId, operation, simulatedAffected, simulatedAt,
                    appliedAffected, appliedAt, appliedBy, at, by, RunStatus.REVERTED);
        }

        /** JSON da execucao para a trilha, via {@code Json} do core. */
        public String toJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("runId", runId.toString());
            map.put("tenantId", tenantId.toString());
            map.put("operation", operation);
            map.put("status", status.name());
            map.put("simulatedAffected", simulatedAffected);
            map.put("simulatedAt", simulatedAt.toString());
            map.put("appliedAffected", appliedAffected);
            map.put("appliedAt", appliedAt == null ? null : appliedAt.toString());
            map.put("appliedBy", appliedBy == null ? null : appliedBy.toString());
            map.put("revertedAt", revertedAt == null ? null : revertedAt.toString());
            map.put("revertedBy", revertedBy == null ? null : revertedBy.toString());
            return Json.write(map);
        }
    }

    /**
     * Correcao de efeito de negocio por OS inversa (ADR-0039): o
     * descritor que a camada de OS (P1.31) consome para abrir a OS
     * inversa ligada a OS de origem. A OS inversa tem o efeito contrario
     * da de origem e o aplica no SEU encerramento — nunca se edita nem
     * se apaga o efeito ja aplicado da OS de origem.
     */
    public record InverseOrderRequest(
            UUID tenantId,
            UUID originalOrderId,
            UUID bulkRunId,
            String reason,
            UUID requestedBy,
            Instant requestedAt) {

        public InverseOrderRequest {
            Objects.requireNonNull(tenantId, "tenantId e obrigatorio");
            Objects.requireNonNull(originalOrderId, "originalOrderId e obrigatorio: OS inversa sem OS de origem nao existe");
            Objects.requireNonNull(bulkRunId, "bulkRunId e obrigatorio: a correcao pertence a uma execucao");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason e obrigatoria: correcao de efeito de negocio exige justificativa");
            }
            Objects.requireNonNull(requestedBy, "requestedBy e obrigatorio");
            Objects.requireNonNull(requestedAt, "requestedAt e obrigatorio");
        }

        /** JSON do descritor, via {@code Json} do core. */
        public String toJson() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tenantId", tenantId.toString());
            map.put("originalOrderId", originalOrderId.toString());
            map.put("bulkRunId", bulkRunId.toString());
            map.put("reason", reason);
            map.put("requestedBy", requestedBy.toString());
            map.put("requestedAt", requestedAt.toString());
            map.put("kind", "INVERSE_SERVICE_ORDER (ADR-0039)");
            return Json.write(map);
        }
    }

    /**
     * Exige a OS inversa quando a execucao aplicada gerou efeito de
     * negocio por OS: nesse caso a remocao de linhas sozinha deixaria o
     * efeito de negocio de pe, e a reversao e recusada sem o descritor
     * da correcao. Sem efeito por OS ({@code businessEffectOrderId}
     * nulo), devolve {@code null} — a remocao por execucao basta.
     */
    public static InverseOrderRequest requireInverseOrderIfBusinessEffect(
            BulkRun run, UUID businessEffectOrderId, String reason, UUID requestedBy, Instant at) {
        Objects.requireNonNull(run, "run e obrigatorio");
        if (run.status() != RunStatus.APPLIED) {
            throw new BulkRefusedException("not-applied",
                    "Correcao por OS inversa exige execucao aplicada (estado atual: " + run.status() + ").");
        }
        if (businessEffectOrderId == null) {
            return null;
        }
        return new InverseOrderRequest(run.tenantId(), businessEffectOrderId, run.runId(),
                reason, requestedBy, at);
    }
}
