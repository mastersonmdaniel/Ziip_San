package br.com.ziipsan.commercial.registry.quality;

import java.util.UUID;

/**
 * Item da fila de correcao da qualidade cadastral (P3.32): um achado
 * aguardando correcao, agrupado em OS de lote ou resolvido por ela.
 *
 * <p>Maquina de estados minima e travada:
 * <pre>
 *   OPEN --(agrupa em OS de lote)--&gt; BATCHED --(OS executada/encerrada)--&gt; RESOLVED
 *   OPEN --(triagem com motivo)--&gt; DISMISSED
 * </pre>
 * Nao ha transicao de correcao fora da OS: {@code RESOLVED} exige o
 * {@code serviceOrderId} da OS de lote que corrigiu o item, e o servico da
 * fila confere que e a mesma OS que agrupou o item. DISMISSED e triagem
 * (o operador declara, com motivo, que o achado nao procede) — nao e
 * correcao e nao toca o cadastro.
 */
public final class CorrectionQueueItem {

    public enum Status { OPEN, BATCHED, RESOLVED, DISMISSED }

    private final UUID id;
    private final String findingKey;
    private final QualityRuleCode ruleCode;
    private final QualitySeverity severity;
    private final UUID unitId;
    private final UUID customerId;
    private final String detail;
    private Status status;
    private UUID serviceOrderId;
    private String dismissReason;

    private CorrectionQueueItem(UUID id, String findingKey, QualityRuleCode ruleCode,
                                QualitySeverity severity, UUID unitId, UUID customerId,
                                String detail) {
        this.id = id;
        this.findingKey = findingKey;
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.unitId = unitId;
        this.customerId = customerId;
        this.detail = detail;
        this.status = Status.OPEN;
    }

    private CorrectionQueueItem(UUID id, QualityFinding finding) {
        this(id, finding.key(), finding.ruleCode(), finding.severity(),
                finding.unitId(), finding.customerId(), finding.detail());
    }

    public static CorrectionQueueItem fromFinding(QualityFinding finding) {
        if (finding == null) {
            throw new IllegalArgumentException("achado e obrigatorio para entrar na fila");
        }
        return new CorrectionQueueItem(UUID.randomUUID(), finding);
    }

    /**
     * Reconstroi o item persistido (fiacao JPA); o estado vem da linha, nao
     * e recalculado. A {@code findingKey} PERSISTIDA e a verdade e e usada
     * como esta — mas ela e conferida contra a chave derivada de
     * regra/unidade/cliente: divergencia e erro explicito, nunca substituicao
     * silenciosa (correcao D4 da revisao independente: a versao anterior
     * ignorava o parametro e recalculava a chave em silencio). Pela mesma
     * razao, {@code detail} nulo NAO e mascarado e estado inconsistente com
     * o CHECK do DDL (BATCHED/RESOLVED sem {@code serviceOrderId}) e recusado.
     */
    public static CorrectionQueueItem restore(UUID id, String findingKey, QualityRuleCode ruleCode,
                                              QualitySeverity severity, UUID unitId, UUID customerId,
                                              String detail, Status status, UUID serviceOrderId,
                                              String dismissReason) {
        if (id == null || findingKey == null || ruleCode == null || severity == null || status == null) {
            throw new IllegalArgumentException("item da fila incompleto para restaurar");
        }
        if (detail == null) {
            throw new IllegalArgumentException(
                    "item da fila " + id + " sem detalhe persistido: linha incompleta nao e mascarada");
        }
        String expectedKey = ruleCode.name() + "|"
                + (unitId == null ? "-" : unitId) + "|"
                + (customerId == null ? "-" : customerId);
        if (!expectedKey.equals(findingKey)) {
            throw new IllegalArgumentException("item da fila " + id + ": finding_key persistida '"
                    + findingKey + "' diverge da derivada de regra/unidade/cliente '"
                    + expectedKey + "'; restauracao recusada, sem recalculo silencioso");
        }
        if ((status == Status.BATCHED || status == Status.RESOLVED) && serviceOrderId == null) {
            throw new IllegalArgumentException("item da fila " + id + " em estado " + status
                    + " sem service_order_id: inconsistente com o CHECK do DDL");
        }
        CorrectionQueueItem item = new CorrectionQueueItem(id, findingKey, ruleCode, severity,
                unitId, customerId, detail);
        item.status = status;
        item.serviceOrderId = serviceOrderId;
        item.dismissReason = dismissReason;
        return item;
    }

    void markBatched(UUID orderId) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("item " + id + " nao esta OPEN para agrupar (esta " + status + ")");
        }
        if (orderId == null) {
            throw new IllegalArgumentException("OS de lote e obrigatoria para agrupar o item " + id);
        }
        this.status = Status.BATCHED;
        this.serviceOrderId = orderId;
    }

    void markResolvedBy(UUID orderId) {
        if (status != Status.BATCHED) {
            throw new IllegalStateException("item " + id + " nao esta BATCHED para resolver (esta "
                    + status + "); correcao so ocorre por OS de lote");
        }
        if (orderId == null || !orderId.equals(serviceOrderId)) {
            throw new IllegalStateException("item " + id + " so pode ser resolvido pela OS que o agrupou ("
                    + serviceOrderId + "), nao por " + orderId);
        }
        this.status = Status.RESOLVED;
    }

    void markDismissed(String reason) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("item " + id + " nao esta OPEN para triagem (esta " + status + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("triagem do item " + id + " exige motivo");
        }
        this.status = Status.DISMISSED;
        this.dismissReason = reason.strip();
    }

    public UUID id() { return id; }
    public String findingKey() { return findingKey; }
    public QualityRuleCode ruleCode() { return ruleCode; }
    public QualitySeverity severity() { return severity; }
    public UUID unitId() { return unitId; }
    public UUID customerId() { return customerId; }
    public String detail() { return detail; }
    public Status status() { return status; }
    public UUID serviceOrderId() { return serviceOrderId; }
    public String dismissReason() { return dismissReason; }
}
