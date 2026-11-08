package br.com.ziipsan.core.incident;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ocorrencia de rede como ENTIDADE (ED-92; OPE-16): a tabela
 * {@code network_incidents} e esta classe sao a fonte unica da
 * ocorrencia — ED-54 (manobras consomem ocorrencia), ED-35
 * (interrupcao programada como SUBTIPO: {@code planned=true} +
 * origem {@code PLANNED} + vinculo ao cadastro dela), ED-38 (relato
 * sem login cria ocorrencia pelo fluxo dela, JOR-14) e ED-64
 * (alagamento) passam a apontar para ca.
 *
 * <p>Ciclo de vida = o do aceite, sem maquina paralela:
 * {@code OPEN -> CONTROLLED -> RESTORED -> CLOSED} (OPEN pode ir
 * direto a RESTORED quando nao houve fase de controle registrada;
 * CLOSED e terminal). Causa do catalogo {@code outage.cause_catalog}
 * OBRIGATORIA no encerramento (CLOSED) — validada contra o catalogo
 * vigente do tenant NA ENTIDADE ({@link #close(ClosureData, List)});
 * causa nula/em branco no fechamento tambem e recusada.
 *
 * <p>Retrato do inicio ({@link AffectedSnapshot}): unidades e
 * economias afetadas CALCULADAS pelo isolamento da ED-54 no inicio
 * e CONGELADAS — mudanca posterior da rede nao reescreve o retrato.
 * Extensao de rede no esgoto (km) alimenta o denominador do
 * indicador de extravasamento (I-05).
 *
 * <p>Volume perdido estimado e tempo de reparo sao gravados NO
 * ENCERRAMENTO a partir do fechamento da OS de reparo
 * ({@link ClosureData}) — entrada das perdas reais da ED-85 e do
 * relatorio por causa (JOR-33). Nenhum valor e presumido: sem OS de
 * reparo encerrada, o fechamento recusa os dois campos como
 * desconhecidos em vez de gravar zero.
 */
public final class NetworkIncident {

    /** Tipos do aceite (e SOMENTE os do aceite). */
    public enum Type { INTERRUPTION, RUPTURE, OVERFLOW, OBSTRUCTION, POWER_OUTAGE, FLOODING }

    /** Origens do aceite. */
    public enum Origin { COMPLAINTS, TELEMETRY, CREW, PLANNED }

    /** Situacao do aceite — nenhuma outra maquina de estados e criada. */
    public enum Status { OPEN, CONTROLLED, RESTORED, CLOSED }

    /** Retrato congelado do inicio: unidades, economias e extensao de esgoto afetada. */
    public record AffectedSnapshot(List<UUID> affectedUnitIds, int totalEconomies,
                                   Double sewerNetworkKmOrNull) {
        public AffectedSnapshot {
            affectedUnitIds = affectedUnitIds == null ? List.of() : List.copyOf(affectedUnitIds);
            if (totalEconomies < 0) throw new IllegalArgumentException("economias afetadas negativas");
            if (sewerNetworkKmOrNull != null && sewerNetworkKmOrNull < 0)
                throw new IllegalArgumentException("extensao de rede de esgoto negativa");
        }
        public static AffectedSnapshot empty() { return new AffectedSnapshot(List.of(), 0, null); }
    }

    /** Dados do encerramento vindos da OS de reparo (JOR-33/ED-85). */
    public record ClosureData(String causeCode, Double lostVolumeM3OrNull,
                              Long repairMinutesOrNull, UUID repairWorkOrderId) {
        public ClosureData {
            if (causeCode == null || causeCode.isBlank())
                throw new IllegalArgumentException("causa obrigatoria no encerramento (outage.cause_catalog)");
            Objects.requireNonNull(repairWorkOrderId, "encerramento exige a OS de reparo que o produziu");
            if (lostVolumeM3OrNull != null && lostVolumeM3OrNull < 0)
                throw new IllegalArgumentException("volume perdido negativo");
            if (repairMinutesOrNull != null && repairMinutesOrNull < 0)
                throw new IllegalArgumentException("tempo de reparo negativo");
        }
    }

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final Type type;
    private final boolean planned;
    private final Origin origin;
    private final LocalDateTime startedAt;
    private final AffectedSnapshot startSnapshot;
    private final UUID plannedOutageIdOrNull;
    private Status status = Status.OPEN;
    private LocalDateTime endedAt;
    private LocalDateTime agencyNotifiedAt;
    private LocalDateTime usersNotifiedAt;
    private LocalDateTime emergencySupplyStartedAt;
    private final List<UUID> workOrderIds = new ArrayList<>();
    private ClosureData closure;

    public NetworkIncident(UUID id, UUID tenantId, String code, Type type, boolean planned,
                           Origin origin, LocalDateTime startedAt, AffectedSnapshot startSnapshot,
                           UUID plannedOutageIdOrNull) {
        this.id = Objects.requireNonNull(id, "ocorrencia sem id");
        this.tenantId = Objects.requireNonNull(tenantId, "ocorrencia sem tenant");
        if (code == null || code.isBlank()) throw new IllegalArgumentException("codigo da ocorrencia obrigatorio (unico por tenant)");
        this.code = code;
        this.type = Objects.requireNonNull(type, "tipo da ocorrencia obrigatorio");
        this.origin = Objects.requireNonNull(origin, "origem da ocorrencia obrigatoria");
        this.startedAt = Objects.requireNonNull(startedAt, "inicio da ocorrencia obrigatorio");
        this.startSnapshot = startSnapshot == null ? AffectedSnapshot.empty() : startSnapshot;
        // Bicondicional do subtipo programada (correcao M1 da revisao da ED-92):
        // planned <=> origem PLANNED <=> vinculo com a ED-35 presente. Os dois
        // sentidos sao validados — origem PLANNED ou vinculo sem planned tambem recusam.
        if (planned && origin != Origin.PLANNED)
            throw new IllegalArgumentException("ocorrencia programada exige origem PLANNED");
        if (!planned && origin == Origin.PLANNED)
            throw new IllegalArgumentException("origem PLANNED so existe no subtipo programada (planned=true + vinculo ED-35)");
        if (planned && plannedOutageIdOrNull == null)
            throw new IllegalArgumentException("subtipo programada exige o vinculo com a interrupcao programada da ED-35");
        if (!planned && plannedOutageIdOrNull != null)
            throw new IllegalArgumentException("vinculo com a ED-35 so existe no subtipo programada");
        this.planned = planned;
        this.plannedOutageIdOrNull = plannedOutageIdOrNull;
    }

    public void markControlled() {
        if (status != Status.OPEN) throw new IllegalStateException("CONTROLLED so a partir de OPEN: " + status);
        status = Status.CONTROLLED;
    }

    public void markRestored(LocalDateTime when) {
        Objects.requireNonNull(when, "instante da restauracao obrigatorio");
        if (status != Status.OPEN && status != Status.CONTROLLED)
            throw new IllegalStateException("RESTORED so a partir de OPEN/CONTROLLED: " + status);
        if (when.isBefore(startedAt)) throw new IllegalArgumentException("restauracao anterior ao inicio");
        this.endedAt = when;
        status = Status.RESTORED;
    }

    /**
     * Encerramento: a causa e validada AQUI, na entidade, contra o
     * catalogo vigente do tenant ({@code outage.cause_catalog}) —
     * correcao m2 da revisao da ED-92: a validacao nao vive mais so
     * no servico, nenhum chamador fecha com causa fora do catalogo.
     */
    public void close(ClosureData data, List<String> causeCatalogRaw) {
        Objects.requireNonNull(data, "dados de encerramento obrigatorios");
        OutageParameters.requireCauseInCatalog(causeCatalogRaw, data.causeCode());
        if (status != Status.RESTORED) throw new IllegalStateException("CLOSED so a partir de RESTORED: " + status);
        if (!workOrderIds.contains(data.repairWorkOrderId()))
            throw new IllegalArgumentException("OS de reparo do encerramento nao esta vinculada a ocorrencia");
        this.closure = data;
        status = Status.CLOSED;
    }

    public void recordAgencyNotice(LocalDateTime when) { this.agencyNotifiedAt = checked(when, "aviso a agencia"); }
    public void recordUsersNotice(LocalDateTime when) { this.usersNotifiedAt = checked(when, "aviso aos usuarios"); }
    public void recordEmergencySupplyStart(LocalDateTime when) { this.emergencySupplyStartedAt = checked(when, "inicio do fornecimento de emergencia"); }
    private LocalDateTime checked(LocalDateTime when, String what) {
        Objects.requireNonNull(when, what + ": instante obrigatorio");
        // Correcao m2: CLOSED e terminal TAMBEM para as datas de aviso/emergencia.
        if (status == Status.CLOSED) throw new IllegalStateException(
                "ocorrencia encerrada nao recebe " + what);
        if (when.isBefore(startedAt)) throw new IllegalArgumentException(what + " anterior ao inicio da ocorrencia");
        return when;
    }

    public void linkWorkOrder(UUID workOrderId) {
        Objects.requireNonNull(workOrderId, "OS vinculada obrigatoria");
        if (status == Status.CLOSED) throw new IllegalStateException("ocorrencia encerrada nao recebe OS nova");
        if (!workOrderIds.contains(workOrderId)) workOrderIds.add(workOrderId);
    }

    /** Duracao ate o fim (ou ate {@code now} se ainda aberta) — base de I-04 e da emergencia. */
    public Duration durationAt(LocalDateTime now) {
        LocalDateTime end = endedAt != null ? endedAt : Objects.requireNonNull(now, "instante de referencia");
        return Duration.between(startedAt, end);
    }

    /** Fornecimento de emergencia devido (NR 11 art. 41 / OPE-16): duracao alem do limiar do tenant. */
    public boolean emergencySupplyDue(LocalDateTime now, int afterHours) {
        if (type != Type.INTERRUPTION && type != Type.POWER_OUTAGE) return false;
        return durationAt(now).compareTo(Duration.ofHours(afterHours)) > 0;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public Type type() { return type; }
    public boolean planned() { return planned; }
    public Origin origin() { return origin; }
    public Status status() { return status; }
    public LocalDateTime startedAt() { return startedAt; }
    public LocalDateTime endedAt() { return endedAt; }
    public AffectedSnapshot startSnapshot() { return startSnapshot; }
    public UUID plannedOutageIdOrNull() { return plannedOutageIdOrNull; }
    public LocalDateTime agencyNotifiedAt() { return agencyNotifiedAt; }
    public LocalDateTime usersNotifiedAt() { return usersNotifiedAt; }
    public LocalDateTime emergencySupplyStartedAt() { return emergencySupplyStartedAt; }
    public List<UUID> workOrderIds() { return List.copyOf(workOrderIds); }
    public ClosureData closure() { return closure; }
}
