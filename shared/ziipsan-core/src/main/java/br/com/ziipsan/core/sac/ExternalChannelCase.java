package br.com.ziipsan.core.sac;

import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Protocolo de origem externa (ED-35): prazo PROPRIO da origem em
 * {@code sac.external_channels}, e o encerramento exige o RETORNO ao canal
 * de origem registrado (data/hora + referencia do canal). O retorno nao
 * pode ser anterior a abertura do protocolo, e o encerramento e uma
 * operacao de fato ({@link #close(LocalDateTime)}), nao so uma consulta
 * (correcao pos-reprovacao, M1).
 */
public final class ExternalChannelCase {

    private final UUID protocolId;
    private final ExternalOrigin origin;
    private final LocalDate openedDate;
    private final LocalDate dueDate;
    private LocalDateTime returnedAt;
    private String returnReference;
    private LocalDateTime closedAt;

    public ExternalChannelCase(UUID protocolId, ExternalOrigin origin, LocalDate openedDate,
                               Map<ExternalOrigin, Integer> externalChannels,
                               DeadlineCountingRule rule, Set<LocalDate> holidays) {
        this.protocolId = Objects.requireNonNull(protocolId, "protocolo obrigatorio");
        this.origin = Objects.requireNonNull(origin, "origem obrigatoria");
        this.openedDate = Objects.requireNonNull(openedDate, "abertura obrigatoria");
        int days = SacParameters.externalChannelDays(externalChannels, origin);
        this.dueDate = DeadlineCalculator.computeDueDate(openedDate, days, rule, holidays);
    }

    public void registerReturn(LocalDateTime when, String reference) {
        Objects.requireNonNull(when, "data/hora do retorno obrigatoria");
        if (reference == null || reference.isBlank())
            throw new IllegalArgumentException("retorno ao canal de origem exige a referencia do canal");
        if (returnedAt != null) throw new IllegalStateException("retorno ja registrado para este protocolo");
        // M1: retorno anterior a abertura e temporalmente impossivel \u2014 recusado.
        if (when.toLocalDate().isBefore(openedDate))
            throw new IllegalArgumentException("retorno ao canal de origem anterior a abertura do protocolo");
        this.returnedAt = when;
        this.returnReference = reference;
    }

    /** Encerrar protocolo externo sem retorno ao canal de origem e recusado. */
    public void assertClosable() {
        if (returnedAt == null) throw new IllegalStateException(
                "protocolo de origem " + origin + " so encerra com retorno ao canal de origem registrado");
    }

    /**
     * M1: encerramento de fato do protocolo externo. Exige o retorno ao
     * canal de origem ja registrado ({@link #assertClosable()}), nao aceita
     * data/hora anterior ao retorno e nao encerra duas vezes.
     */
    public void close(LocalDateTime when) {
        Objects.requireNonNull(when, "data/hora do encerramento obrigatoria");
        assertClosable();
        if (closedAt != null) throw new IllegalStateException("protocolo externo ja encerrado");
        if (when.isBefore(returnedAt))
            throw new IllegalArgumentException("encerramento anterior ao retorno ao canal de origem");
        this.closedAt = when;
    }

    public UUID protocolId() { return protocolId; }
    public ExternalOrigin origin() { return origin; }
    public LocalDate openedDate() { return openedDate; }
    public LocalDate dueDate() { return dueDate; }
    public boolean returned() { return returnedAt != null; }
    public LocalDateTime returnedAt() { return returnedAt; }
    public String returnReference() { return returnReference; }
    public boolean closed() { return closedAt != null; }
    public LocalDateTime closedAt() { return closedAt; }
    public boolean overdueOn(LocalDate date) { return date.isAfter(dueDate); }
}
