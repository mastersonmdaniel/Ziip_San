package br.com.ziipsan.core.effluent;

import java.time.Instant;
import java.util.UUID;

/**
 * Vinculo entre um registro de lodo/residuo e o MTR do gerador
 * (RAS-17). Enquanto a ED-104 (emissao automatica) nao for
 * liberada, a emissao e MANUAL pela agenda da ED-98: o vinculo
 * nasce {@code PENDING_MANUAL} com a referencia do item da agenda,
 * e a emissao manual registra o numero do MTR emitido fora do
 * sistema. Quando a ED-104 for liberada, o mesmo vinculo recebe a
 * ligacao automatica ({@link #linkAutomatic}) — nenhum registro de
 * lodo e reescrito, so o vinculo avanca.
 *
 * <p>Origem isenta ({@code waste.mtr_exempt_origins}): o vinculo
 * nasce {@code EXEMPT}, sem item de agenda e sem numero — a isencao
 * e fato declarado no parametro, nao omissao.
 */
public final class MtrLink {

    public enum Status { PENDING_MANUAL, EMITTED_MANUALLY, LINKED_AUTOMATIC, EXEMPT }

    public enum Source { MANUAL_ED98, AUTOMATIC_ED104, EXEMPTION }

    private final UUID id;
    private final UUID tenantId;
    private final UUID sludgeRecordId;
    private Status status;
    private Source source;
    private String agendaItemRef;
    private String mtrNumber;
    private Instant emittedAt;

    private MtrLink(UUID id, UUID tenantId, UUID sludgeRecordId, Status status, Source source,
                    String agendaItemRef) {
        if (id == null || tenantId == null || sludgeRecordId == null)
            throw new IllegalArgumentException("id, tenant e registro de lodo sao obrigatorios no vinculo de MTR");
        this.id = id;
        this.tenantId = tenantId;
        this.sludgeRecordId = sludgeRecordId;
        this.status = status;
        this.source = source;
        this.agendaItemRef = agendaItemRef;
    }

    /** Nascimento do caminho manual (RAS-17): item da agenda da ED-98 ja criado pela porta. */
    public static MtrLink pendingManual(UUID id, UUID tenantId, UUID sludgeRecordId, String agendaItemRef) {
        if (agendaItemRef == null || agendaItemRef.isBlank())
            throw new IllegalArgumentException("vinculo manual exige a referencia do item da agenda da ED-98");
        return new MtrLink(id, tenantId, sludgeRecordId, Status.PENDING_MANUAL, Source.MANUAL_ED98,
                agendaItemRef.trim());
    }

    /** Nascimento do caminho isento: origem declarada em waste.mtr_exempt_origins. */
    public static MtrLink exempt(UUID id, UUID tenantId, UUID sludgeRecordId) {
        return new MtrLink(id, tenantId, sludgeRecordId, Status.EXEMPT, Source.EXEMPTION, null);
    }

    /** A emissao manual aconteceu (na agenda da ED-98): o numero do MTR e registrado. */
    public void recordManualEmission(String mtrNumber, Instant at) {
        if (status != Status.PENDING_MANUAL) throw new IllegalStateException(
                "vinculo " + id + " em " + status + " nao recebe emissao manual");
        if (mtrNumber == null || mtrNumber.isBlank())
            throw new IllegalArgumentException("numero do MTR emitido manualmente e obrigatorio");
        if (at == null) throw new IllegalArgumentException("instante da emissao manual e obrigatorio");
        this.mtrNumber = mtrNumber.trim();
        this.emittedAt = at;
        this.status = Status.EMITTED_MANUALLY;
    }

    /**
     * Ligacao automatica quando a ED-104 for liberada: aceita o
     * vinculo pendente (a ED-104 emite por ele) ou o ja emitido
     * manualmente (a ED-104 passa a referencia-lo). Isento e
     * recusado: isencao nao vira emissao por esta via.
     */
    public void linkAutomatic(String mtrNumber, Instant at) {
        if (status == Status.EXEMPT) throw new IllegalStateException(
                "vinculo " + id + " e EXEMPT (origem isenta): nao ha MTR a ligar pela ED-104");
        if (status == Status.LINKED_AUTOMATIC) throw new IllegalStateException(
                "vinculo " + id + " ja esta ligado automaticamente");
        if (mtrNumber == null || mtrNumber.isBlank())
            throw new IllegalArgumentException("numero do MTR da ligacao automatica e obrigatorio");
        if (at == null) throw new IllegalArgumentException("instante da ligacao automatica e obrigatorio");
        if (this.mtrNumber != null && !this.mtrNumber.equals(mtrNumber.trim()))
            throw new IllegalArgumentException("MTR automatico " + mtrNumber
                    + " diverge do MTR ja registrado " + this.mtrNumber + " no vinculo " + id);
        this.mtrNumber = mtrNumber.trim();
        this.emittedAt = at;
        this.source = Source.AUTOMATIC_ED104;
        this.status = Status.LINKED_AUTOMATIC;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID sludgeRecordId() { return sludgeRecordId; }
    public Status status() { return status; }
    public Source source() { return source; }
    public String agendaItemRef() { return agendaItemRef; }
    public String mtrNumber() { return mtrNumber; }
    public Instant emittedAt() { return emittedAt; }
}
