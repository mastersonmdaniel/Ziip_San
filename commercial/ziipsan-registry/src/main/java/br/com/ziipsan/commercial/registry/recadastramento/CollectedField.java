package br.com.ziipsan.commercial.registry.recadastramento;

import br.com.ziipsan.core.json.Json;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Um campo coletado em campo para o recadastramento (ED-16): o valor que
 * o recadastrador levantou no app da ED-18 para UMA unidade (e, nos
 * campos de cliente, para o cliente vinculado), junto do valor vigente
 * no cadastro no instante da coleta.
 *
 * <p>Maquina de estados travada — a aprovacao e CAMPO A CAMPO e a
 * aplicacao e efeito da OS de lote:
 * <pre>
 *   COLLECTED --(aprova)--&gt; APPROVED --(aplica como efeito da OS)--&gt; APPLIED
 *   COLLECTED --(rejeita, com motivo)--&gt; REJECTED
 *   APPROVED  --(cadastro mudou desde a coleta)--&gt; STALE
 *   STALE     --(nova coleta do mesmo campo no lote)--&gt; SUPERSEDED
 * </pre>
 * {@code STALE} e a ocorrencia de recadastramento incorreto
 * (TR-686/SG-912): aplicar por cima de um cadastro que mudou depois da
 * coleta sobrescreveria uma alteracao legitima, entao a aplicacao e
 * recusada, o campo fica marcado e o lote so fecha depois de nova
 * coleta daquele campo. {@code REJECTED} guarda o motivo da decisao:
 * rejeitar sem motivo nao existe.
 *
 * <p>{@code valueAtCollection} pode ser nulo (campo vazio no cadastro).
 * Valores de documento chegam e ficam mascarados (catalogo
 * {@link RecadastramentoFieldCatalog}).
 *
 * <p>{@code valueAtApplication} tambem pode ser nulo num campo
 * {@code STALE}, e isso e legitimo: a porta de leitura
 * ({@link CadastralFieldReader}) devolve {@code null} para campo sem
 * valor, logo um campo ESVAZIADO no cadastro depois da coleta diverge
 * do valor da coleta e marca STALE com {@code null} — nesse caso o
 * {@code null} gravado E o valor vigente na aplicacao (o esvaziamento),
 * nao ausencia de leitura. Por isso o SQL pendente NAO exige
 * {@code value_at_application IS NOT NULL} para STALE (ver o comentario
 * na tabela {@code recadastramento_collected_fields}).
 */
public final class CollectedField {

    public enum Status { COLLECTED, APPROVED, REJECTED, APPLIED, STALE, SUPERSEDED }

    private final UUID id;
    private final UUID batchId;
    private final UUID taskId;
    private final UUID unitId;
    private final UUID customerId;
    private final String targetType;
    private final String field;
    private final String valueAtCollection;
    private final String collectedValue;
    private final UUID collectorId;
    private final List<String> photoRefs;
    private final OffsetDateTime capturedAt;
    private Status status;
    private UUID decidedBy;
    private String decisionReason;
    private OffsetDateTime decidedAt;
    private UUID appliedChangeRequestId;
    private OffsetDateTime appliedAt;
    private String valueAtApplication;

    private CollectedField(UUID id, UUID batchId, UUID taskId, UUID unitId, UUID customerId,
                           String targetType, String field, String valueAtCollection,
                           String collectedValue, UUID collectorId, List<String> photoRefs,
                           OffsetDateTime capturedAt) {
        this.id = id;
        this.batchId = batchId;
        this.taskId = taskId;
        this.unitId = unitId;
        this.customerId = customerId;
        this.targetType = targetType;
        this.field = field;
        this.valueAtCollection = valueAtCollection;
        this.collectedValue = collectedValue;
        this.collectorId = collectorId;
        this.photoRefs = photoRefs == null ? List.of() : List.copyOf(photoRefs);
        this.capturedAt = capturedAt;
        this.status = Status.COLLECTED;
    }

    /**
     * Nasce uma coleta valida. As recusas de negocio (campo fora do
     * catalogo, valor vazio, alvo incompativel) sao conferidas ANTES pelo
     * servico, que as registra como erro de coleta; aqui elas sao o
     * backstop que impede um CollectedField invalido de existir.
     */
    public static CollectedField collect(UUID batchId, UUID taskId, UUID unitId, UUID customerId,
                                         String targetType, String field, String valueAtCollection,
                                         String collectedValue, UUID collectorId,
                                         List<String> photoRefs, OffsetDateTime capturedAt) {
        if (batchId == null || taskId == null || unitId == null || collectorId == null) {
            throw new IllegalArgumentException("lote, tarefa, unidade e recadastrador sao obrigatorios");
        }
        RecadastramentoFieldCatalog.osTypeFor(targetType, field);
        if (RecadastramentoFieldCatalog.TARGET_CUSTOMER.equals(targetType) && customerId == null) {
            throw new IllegalArgumentException("campo de cliente coletado sem o cliente: " + field);
        }
        if (collectedValue == null || collectedValue.isBlank()) {
            throw new IllegalArgumentException("valor coletado vazio para o campo " + field);
        }
        return new CollectedField(UUID.randomUUID(), batchId, taskId, unitId, customerId,
                targetType, field, valueAtCollection, collectedValue.strip(), collectorId,
                photoRefs, capturedAt);
    }

    /** Reconstroi o campo persistido (fiacao JPA); o estado vem da linha. */
    public static CollectedField restore(UUID id, UUID batchId, UUID taskId, UUID unitId, UUID customerId,
                                         String targetType, String field, String valueAtCollection,
                                         String collectedValue, UUID collectorId, List<String> photoRefs,
                                         OffsetDateTime capturedAt, Status status, UUID decidedBy,
                                         String decisionReason, OffsetDateTime decidedAt,
                                         UUID appliedChangeRequestId, OffsetDateTime appliedAt,
                                         String valueAtApplication) {
        if (id == null || status == null) {
            throw new IllegalArgumentException("campo coletado incompleto para restaurar");
        }
        CollectedField restored = new CollectedField(id, batchId, taskId, unitId, customerId,
                targetType, field, valueAtCollection, collectedValue, collectorId, photoRefs, capturedAt);
        restored.status = status;
        restored.decidedBy = decidedBy;
        restored.decisionReason = decisionReason;
        restored.decidedAt = decidedAt;
        restored.appliedChangeRequestId = appliedChangeRequestId;
        restored.appliedAt = appliedAt;
        restored.valueAtApplication = valueAtApplication;
        return restored;
    }

    /** O id do alvo do campo: o cliente nos campos CUSTOMER, a unidade nos demais. */
    public UUID targetId() {
        return RecadastramentoFieldCatalog.TARGET_CUSTOMER.equals(targetType) ? customerId : unitId;
    }

    /** Ha diferenca entre o coletado e o vigente na coleta? (relatorio SG-910) */
    public boolean hasDifference() {
        return !Objects.equals(normalize(valueAtCollection), normalize(collectedValue));
    }

    /**
     * Impressao da carga para a idempotencia por task_id (padrao ED-18):
     * o reenvio da MESMA coleta devolve o recibo original; o reenvio do
     * mesmo task_id com carga DIFERENTE e conflito (erro de coleta).
     * Serializada pela classe Json do core, nunca por concatenacao.
     */
    public String payloadFingerprint() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", String.valueOf(batchId));
        payload.put("unitId", String.valueOf(unitId));
        payload.put("customerId", customerId == null ? null : String.valueOf(customerId));
        payload.put("targetType", targetType);
        payload.put("field", field);
        payload.put("collectedValue", collectedValue);
        return Json.write(payload);
    }

    /** Impressao de uma submissao ainda nao aceita, para comparar com a gravada. */
    public static String fingerprintOf(UUID batchId, UUID unitId, UUID customerId,
                                       String targetType, String field, String collectedValue) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", String.valueOf(batchId));
        payload.put("unitId", String.valueOf(unitId));
        payload.put("customerId", customerId == null ? null : String.valueOf(customerId));
        payload.put("targetType", targetType);
        payload.put("field", field);
        payload.put("collectedValue", collectedValue == null ? null : collectedValue.strip());
        return Json.write(payload);
    }

    public void approve(UUID approverId, OffsetDateTime at) {
        requireCollected("aprovar");
        if (approverId == null) {
            throw new IllegalArgumentException("aprovador e obrigatorio para decidir o campo " + field);
        }
        this.status = Status.APPROVED;
        this.decidedBy = approverId;
        this.decidedAt = at;
        this.decisionReason = null;
    }

    public void reject(UUID approverId, String reason, OffsetDateTime at) {
        requireCollected("rejeitar");
        if (approverId == null) {
            throw new IllegalArgumentException("aprovador e obrigatorio para decidir o campo " + field);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("rejeicao do campo " + field + " exige motivo");
        }
        if (reason.strip().length() > 300) {
            throw new IllegalArgumentException("motivo da rejeicao do campo " + field
                    + " excede 300 caracteres (limite do decision_reason VARCHAR(300))");
        }
        this.status = Status.REJECTED;
        this.decidedBy = approverId;
        this.decidedAt = at;
        this.decisionReason = reason.strip();
    }

    /**
     * Marca a aplicacao como efeito da OS de lote. Com diferenca, exige o
     * id do registro no historico ({@code cadastral_change_requests}):
     * alteracao aprovada sem historico nao existe. Sem diferenca, nao ha
     * alteracao a registrar e o id e nulo.
     */
    public void markApplied(UUID changeRequestId, OffsetDateTime at) {
        if (status != Status.APPROVED) {
            throw new IllegalStateException("campo " + field + " nao esta APPROVED para aplicar (esta "
                    + status + "); so alteracao aprovada e aplicada");
        }
        if (hasDifference() && changeRequestId == null) {
            throw new IllegalArgumentException(
                    "aplicacao com diferenca exige o registro no historico do cadastro: " + field);
        }
        this.status = Status.APPLIED;
        this.appliedChangeRequestId = changeRequestId;
        this.appliedAt = at;
    }

    /**
     * O cadastro mudou depois da coleta: a aplicacao e recusada
     * (ocorrencia incorreta). {@code currentValueAtApplication} pode ser
     * {@code null}: e o caso do campo esvaziado no cadastro depois da
     * coleta, que a porta de leitura devolve como {@code null} — o
     * esvaziamento tambem e divergencia e tambem marca STALE, com o
     * {@code null} gravado como o valor vigente.
     */
    public void markStale(String currentValueAtApplication) {
        if (status != Status.APPROVED) {
            throw new IllegalStateException("campo " + field + " nao esta APPROVED para ficar STALE (esta "
                    + status + ")");
        }
        this.status = Status.STALE;
        this.valueAtApplication = currentValueAtApplication;
    }

    /** Uma nova coleta do mesmo campo no lote supersede esta (que fica como ocorrencia). */
    public void markSuperseded() {
        if (status != Status.STALE) {
            throw new IllegalStateException("so campo STALE e supersedido por nova coleta (esta "
                    + status + "): " + field);
        }
        this.status = Status.SUPERSEDED;
    }

    private void requireCollected(String verb) {
        if (status != Status.COLLECTED) {
            throw new IllegalStateException("campo " + field + " nao esta COLLECTED para " + verb
                    + " (esta " + status + "); a decisao e campo a campo, uma unica vez");
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }

    public UUID id() { return id; }
    public UUID batchId() { return batchId; }
    public UUID taskId() { return taskId; }
    public UUID unitId() { return unitId; }
    public UUID customerId() { return customerId; }
    public String targetType() { return targetType; }
    public String field() { return field; }
    public String valueAtCollection() { return valueAtCollection; }
    public String collectedValue() { return collectedValue; }
    public UUID collectorId() { return collectorId; }
    public List<String> photoRefs() { return photoRefs; }
    public OffsetDateTime capturedAt() { return capturedAt; }
    public Status status() { return status; }
    public UUID decidedBy() { return decidedBy; }
    public String decisionReason() { return decisionReason; }
    public OffsetDateTime decidedAt() { return decidedAt; }
    public UUID appliedChangeRequestId() { return appliedChangeRequestId; }
    public OffsetDateTime appliedAt() { return appliedAt; }
    public String valueAtApplication() { return valueAtApplication; }
}
