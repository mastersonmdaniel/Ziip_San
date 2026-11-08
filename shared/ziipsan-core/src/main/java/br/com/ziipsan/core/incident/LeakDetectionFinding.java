package br.com.ziipsan.core.incident;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Achado de pesquisa de vazamento (DAD-27): o {@code leak_detection}
 * PERDE o ciclo proprio de reparo — o achado liga-se a ocorrencia
 * (ED-92) e a OS, tem codigo unico no tenant, e o REPARO e
 * registrado SO na OS. Por isso esta classe deliberadamente NAO tem
 * estado de reparo, data de reparo nem responsavel pelo reparo: a
 * disposicao e da PESQUISA (PENDING -> CONFIRMED_LINKED |
 * NOT_A_LEAK), nao do conserto.
 */
public final class LeakDetectionFinding {

    public enum Disposition { PENDING, CONFIRMED_LINKED, NOT_A_LEAK }

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final LocalDateTime foundAt;
    private final String locationText;
    private Disposition disposition = Disposition.PENDING;
    private UUID networkIncidentId;
    private UUID workOrderId;

    public LeakDetectionFinding(UUID id, UUID tenantId, String code, LocalDateTime foundAt, String locationText) {
        this.id = Objects.requireNonNull(id, "achado sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "achado sem tenant");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo do achado obrigatorio (unico no tenant)");
        this.code = code;
        this.foundAt = Objects.requireNonNull(foundAt, "data do achado obrigatoria");
        if (locationText == null || locationText.isBlank()) throw new IllegalArgumentException("local do achado obrigatorio");
        this.locationText = locationText;
    }

    /** Confirma o vazamento ligando ocorrencia + OS (o reparo vive na OS — DAD-27). */
    public void confirmLinked(UUID incidentId, UUID workOrderId) {
        if (disposition != Disposition.PENDING) throw new IllegalStateException("achado ja disposto: " + disposition);
        this.networkIncidentId = Objects.requireNonNull(incidentId, "confirmacao exige a ocorrencia");
        this.workOrderId = Objects.requireNonNull(workOrderId, "confirmacao exige a OS (o reparo e so dela)");
        disposition = Disposition.CONFIRMED_LINKED;
    }

    public void markNotALeak() {
        if (disposition != Disposition.PENDING) throw new IllegalStateException("achado ja disposto: " + disposition);
        disposition = Disposition.NOT_A_LEAK;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public LocalDateTime foundAt() { return foundAt; }
    public String locationText() { return locationText; }
    public Disposition disposition() { return disposition; }
    public UUID networkIncidentId() { return networkIncidentId; }
    public UUID workOrderId() { return workOrderId; }
}
