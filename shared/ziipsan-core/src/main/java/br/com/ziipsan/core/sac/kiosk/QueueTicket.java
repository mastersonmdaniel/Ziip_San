package br.com.ziipsan.core.sac.kiosk;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Senha presencial da ED-40. Grava, POR SENHA, os tres instantes que o
 * acceptance exige: hora de EMISSAO, hora da CHAMADA e hora de INICIO do
 * atendimento (mais o encerramento, para fechar o ciclo). Transicoes sao
 * monotonicas e irreversiveis: chamada antes da emissao, inicio antes da
 * chamada, chamada dupla ou inicio duplo sao recusados — o relatorio de
 * espera (RAS-26) le estes instantes, entao eles nao podem ser reescritos.
 * O numero sequencial e por unidade de atendimento e por dia, atribuido
 * pelo chamador (sequencia do banco na fiacao); o codigo de exibicao
 * deriva dele e do perfil (prefixo P para prioridade legal, N comum) e e
 * o UNICO identificador que vai ao painel — nenhum nome vai ao painel.
 */
public final class QueueTicket {

    public enum Status { WAITING, CALLED, IN_SERVICE, COMPLETED, NO_SHOW, CANCELLED }

    private final UUID id;
    private final UUID serviceUnitId;
    private final int sequenceNumber;
    private final String serviceType;
    private final PriorityAttendanceProfile profile;
    private final LocalDateTime issuedAt;
    private Status status = Status.WAITING;
    private LocalDateTime calledAt;
    private String deskId;
    private LocalDateTime serviceStartedAt;
    private LocalDateTime finishedAt;

    public QueueTicket(UUID id, UUID serviceUnitId, int sequenceNumber, String serviceType,
                       PriorityAttendanceProfile profile, LocalDateTime issuedAt) {
        this.id = Objects.requireNonNull(id, "id da senha obrigatorio");
        this.serviceUnitId = Objects.requireNonNull(serviceUnitId, "unidade de atendimento obrigatoria");
        if (sequenceNumber <= 0) throw new IllegalArgumentException("sequencial da senha deve ser positivo");
        this.sequenceNumber = sequenceNumber;
        if (serviceType == null || serviceType.isBlank()) {
            throw new IllegalArgumentException("tipo de servico obrigatorio");
        }
        this.serviceType = serviceType.trim();
        this.profile = Objects.requireNonNull(profile, "perfil de atendimento obrigatorio");
        this.issuedAt = Objects.requireNonNull(issuedAt, "hora de emissao obrigatoria");
    }

    public String displayCode() {
        return (profile.legalPriority() ? "P" : "N") + String.format("%03d", sequenceNumber);
    }

    public void call(LocalDateTime at, String deskId) {
        Objects.requireNonNull(at, "hora da chamada obrigatoria");
        if (deskId == null || deskId.isBlank()) throw new IllegalArgumentException("guiche/mesa da chamada obrigatorio");
        if (status != Status.WAITING) throw new IllegalStateException("senha nao esta aguardando: " + status);
        if (at.isBefore(issuedAt)) throw new IllegalArgumentException("chamada anterior a emissao");
        this.calledAt = at;
        this.deskId = deskId.trim();
        this.status = Status.CALLED;
    }

    public void startService(LocalDateTime at) {
        Objects.requireNonNull(at, "hora de inicio do atendimento obrigatoria");
        if (status != Status.CALLED) throw new IllegalStateException("senha nao esta chamada: " + status);
        if (at.isBefore(calledAt)) throw new IllegalArgumentException("inicio anterior a chamada");
        this.serviceStartedAt = at;
        this.status = Status.IN_SERVICE;
    }

    public void complete(LocalDateTime at) {
        Objects.requireNonNull(at, "hora de encerramento obrigatoria");
        if (status != Status.IN_SERVICE) throw new IllegalStateException("senha nao esta em atendimento: " + status);
        if (at.isBefore(serviceStartedAt)) throw new IllegalArgumentException("encerramento anterior ao inicio");
        this.finishedAt = at;
        this.status = Status.COMPLETED;
    }

    public void markNoShow(LocalDateTime at) {
        Objects.requireNonNull(at, "instante obrigatorio");
        if (status != Status.CALLED) throw new IllegalStateException("so senha chamada pode ser marcada ausente: " + status);
        if (at.isBefore(calledAt)) throw new IllegalArgumentException("ausencia anterior a chamada");
        this.finishedAt = at;
        this.status = Status.NO_SHOW;
    }

    public void cancel(LocalDateTime at) {
        Objects.requireNonNull(at, "instante obrigatorio");
        if (status != Status.WAITING && status != Status.CALLED) {
            throw new IllegalStateException("senha nao pode ser cancelada neste estado: " + status);
        }
        if (at.isBefore(issuedAt)) throw new IllegalArgumentException("cancelamento anterior a emissao");
        this.finishedAt = at;
        this.status = Status.CANCELLED;
    }

    /** Espera ate a chamada (emissao -> chamada); null enquanto nao chamada. */
    public Duration waitUntilCall() {
        return calledAt == null ? null : Duration.between(issuedAt, calledAt);
    }

    /** Espera presencial do relatorio: emissao -> INICIO do atendimento; null enquanto nao iniciado. */
    public Duration waitingTime() {
        return serviceStartedAt == null ? null : Duration.between(issuedAt, serviceStartedAt);
    }

    public boolean serviceStarted() { return serviceStartedAt != null; }

    public UUID id() { return id; }
    public UUID serviceUnitId() { return serviceUnitId; }
    public int sequenceNumber() { return sequenceNumber; }
    public String serviceType() { return serviceType; }
    public PriorityAttendanceProfile profile() { return profile; }
    public LocalDateTime issuedAt() { return issuedAt; }
    public LocalDateTime calledAt() { return calledAt; }
    public String deskId() { return deskId; }
    public LocalDateTime serviceStartedAt() { return serviceStartedAt; }
    public LocalDateTime finishedAt() { return finishedAt; }
    public Status status() { return status; }
}
