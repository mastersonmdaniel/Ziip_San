package br.com.ziipsan.core.nondomestic;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Caso de nao conformidade do lancamento do usuario nao domestico
 * (aceite da ED-83): nasce de um veredito {@code NON_CONFORMING} —
 * da amostragem do prestador ou do automonitoramento avaliado — e
 * segue a ordem do aceite: primeiro a NOTIFICACAO ao usuario, so
 * depois a PENALIDADE.
 *
 * <p>Maquina de estados: {@code DETECTED -> NOTIFIED -> PENALIZED},
 * com {@code RESOLVED} a partir de NOTIFIED ou PENALIZED, mediante
 * evidencia de correcao E vistoria por OS de conferencia.
 *
 * <p>Regras duras:
 * <ul>
 *   <li>penalidade sem notificacao previa registrada e RECUSADA —
 *       a ordem notificacao -> penalidade nao se inverte;</li>
 *   <li>o VALOR da penalidade e a sua base sao DADOS do processo
 *       (tabela de penalidades do prestador/agencia): nenhum valor,
 *       percentual ou texto nasce aqui [SEM FONTE] — penalidade sem
 *       valor positivo e sem referencia da base recusa;</li>
 *   <li>a resolucao exige a referencia da evidencia de correcao e o
 *       id da OS de vistoria que a conferiu — resolucao presumida
 *       nao existe.</li>
 * </ul>
 */
public final class NonConformityCase {

    /** Origem do veredito que abriu o caso. */
    public enum Origin { PROVIDER_SAMPLING, SELF_MONITORING, INSPECTION }

    public enum Status { DETECTED, NOTIFIED, PENALIZED, RESOLVED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID dischargerId;
    private final Origin origin;
    private final String parameterKey;
    private final String detail;
    private final Instant detectedAt;
    private Status status = Status.DETECTED;
    private String notificationRef;
    private Instant notifiedAt;
    private BigDecimal penaltyAmount;
    private String penaltyBasisRef;
    private Instant penalizedAt;
    private String correctionEvidenceRef;
    private UUID reinspectionOrderId;
    private Instant resolvedAt;

    public NonConformityCase(UUID id, UUID tenantId, UUID dischargerId, Origin origin,
                             String parameterKey, String detail, Instant detectedAt) {
        if (id == null) throw new IllegalArgumentException("id do caso e obrigatorio");
        if (tenantId == null) throw new IllegalArgumentException("tenantId do caso e obrigatorio");
        if (dischargerId == null) throw new IllegalArgumentException("gerador do caso e obrigatorio");
        if (origin == null) throw new IllegalArgumentException("origem do caso e obrigatoria");
        if (parameterKey == null || parameterKey.isBlank())
            throw new IllegalArgumentException("parametro da nao conformidade e obrigatorio");
        if (detail == null || detail.isBlank())
            throw new IllegalArgumentException("detalhe da nao conformidade e obrigatorio (veredito e referencia)");
        if (detectedAt == null) throw new IllegalArgumentException("instante da deteccao e obrigatorio");
        this.id = id;
        this.tenantId = tenantId;
        this.dischargerId = dischargerId;
        this.origin = origin;
        this.parameterKey = parameterKey;
        this.detail = detail;
        this.detectedAt = detectedAt;
    }

    /** Registra a notificacao ao usuario (a referencia e a prova do envio pelo canal vigente). */
    public void recordNotification(String notificationRef, Instant at) {
        if (status != Status.DETECTED)
            throw new IllegalStateException("notificacao exige caso DETECTED, estado atual " + status);
        if (notificationRef == null || notificationRef.isBlank())
            throw new IllegalArgumentException("notificacao exige a referencia do envio (prova)");
        if (at == null || at.isBefore(detectedAt))
            throw new IllegalArgumentException("notificacao nao pode ser anterior a deteccao");
        this.notificationRef = notificationRef;
        this.notifiedAt = at;
        this.status = Status.NOTIFIED;
    }

    /**
     * Aplica a penalidade. Portoes: caso NOTIFIED (a notificacao vem
     * primeiro, pelo aceite), valor positivo e referencia da base da
     * penalidade — valor e base sao do processo [SEM FONTE aqui].
     */
    public void applyPenalty(BigDecimal amount, String basisRef, Instant at) {
        if (status != Status.NOTIFIED)
            throw new IllegalStateException("penalidade exige o caso NOTIFIED antes"
                    + " (notificacao precede a penalidade), estado atual " + status);
        if (amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("penalidade exige valor positivo declarado"
                    + " na tabela do prestador/agencia: nenhum valor nasce aqui [SEM FONTE]");
        if (basisRef == null || basisRef.isBlank())
            throw new IllegalArgumentException("penalidade exige a referencia da sua base"
                    + " (ato/tabela do prestador ou da agencia) [SEM FONTE]");
        if (at == null || at.isBefore(notifiedAt))
            throw new IllegalArgumentException("penalidade nao pode ser anterior a notificacao");
        this.penaltyAmount = amount;
        this.penaltyBasisRef = basisRef;
        this.penalizedAt = at;
        this.status = Status.PENALIZED;
    }

    /** Resolve o caso com evidencia de correcao conferida em vistoria por OS. */
    public void resolve(String correctionEvidenceRef, UUID reinspectionOrderId, Instant at) {
        if (status != Status.NOTIFIED && status != Status.PENALIZED)
            throw new IllegalStateException("resolucao exige caso NOTIFIED ou PENALIZED, estado atual " + status);
        if (correctionEvidenceRef == null || correctionEvidenceRef.isBlank())
            throw new IllegalArgumentException("resolucao exige a evidencia de correcao referenciada");
        if (reinspectionOrderId == null)
            throw new IllegalArgumentException("resolucao exige a OS de vistoria que conferiu a correcao");
        if (at == null || at.isBefore(notifiedAt))
            throw new IllegalArgumentException("resolucao nao pode ser anterior a notificacao");
        this.correctionEvidenceRef = correctionEvidenceRef;
        this.reinspectionOrderId = reinspectionOrderId;
        this.resolvedAt = at;
        this.status = Status.RESOLVED;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID dischargerId() { return dischargerId; }
    public Origin origin() { return origin; }
    public String parameterKey() { return parameterKey; }
    public String detail() { return detail; }
    public Instant detectedAt() { return detectedAt; }
    public Status status() { return status; }
    public String notificationRef() { return notificationRef; }
    public Instant notifiedAt() { return notifiedAt; }
    public BigDecimal penaltyAmount() { return penaltyAmount; }
    public String penaltyBasisRef() { return penaltyBasisRef; }
    public Instant penalizedAt() { return penalizedAt; }
    public String correctionEvidenceRef() { return correctionEvidenceRef; }
    public UUID reinspectionOrderId() { return reinspectionOrderId; }
    public Instant resolvedAt() { return resolvedAt; }
}
