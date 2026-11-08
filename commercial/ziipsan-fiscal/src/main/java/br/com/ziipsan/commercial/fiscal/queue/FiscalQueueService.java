package br.com.ziipsan.commercial.fiscal.queue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A fila de pendências do operador (aceite da P2.33): lê os documentos do tenant pela
 * porta da P2.6, classifica cada um por {@link FiscalQueueClassifier}, aplica os
 * filtros (estado, competência, grupo) e monta o item da tela — rejeitada com a
 * explicação do catálogo da P2.16 e contingência não transmitida com o tempo
 * decorrido medido pelo {@link Clock} injetado. Documentos VOIDED e documentos em
 * trânsito normal (sem classificação no aceite) não entram na fila.
 */
public final class FiscalQueueService {

    private final Clock clock;
    private final FiscalDocumentReadPort readPort;

    public FiscalQueueService(Clock clock, FiscalDocumentReadPort readPort) {
        this.clock = Objects.requireNonNull(clock, "Clock é obrigatório (relógio injetável)");
        this.readPort = Objects.requireNonNull(readPort, "Porta de leitura dos documentos é obrigatória");
    }

    public List<FiscalQueueItem> queue(UUID tenantId, FiscalQueueQuery query) {
        Objects.requireNonNull(tenantId, "Tenant é obrigatório");
        Objects.requireNonNull(query, "Filtro da fila é obrigatório");
        return readPort.listByTenant(tenantId).stream()
                .filter(doc -> doc.tenantId().equals(tenantId))
                .filter(doc -> query.competence() == null || query.competence().equals(doc.competence()))
                .filter(doc -> query.billingGroupId() == null
                        || query.billingGroupId().equals(doc.billingGroupId()))
                .map(this::toItem)
                .flatMap(Optional::stream)
                .filter(item -> query.state() == null || query.state() == item.queueState())
                .toList();
    }

    private Optional<FiscalQueueItem> toItem(FiscalDocumentView doc) {
        FiscalQueueState state;
        try {
            state = FiscalQueueClassifier.classify(doc);
        } catch (IllegalStateException voided) {
            return Optional.empty();
        }
        if (state == null) {
            return Optional.empty();
        }
        Optional<FiscalRejectionExplainer.Explanation> explanation = state == FiscalQueueState.REJECTED
                ? FiscalRejectionExplainer.explain(doc.kind(), doc.rejectionCode())
                : Optional.empty();
        // A avulsa não transmitida é CONTINGENCY pela pendência de transmissão, sem
        // início de contingência na projeção (o record só exige o instante quando a
        // flag de contingência está marcada, e o classificador ignora a flag para a
        // avulsa): sem instante, o item entra na fila sem tempo decorrido — nunca NPE.
        Optional<Duration> elapsed = state == FiscalQueueState.CONTINGENCY && doc.contingencyStartedAt() != null
                ? Optional.of(Duration.between(doc.contingencyStartedAt(), clock.instant()
                        .atOffset(doc.contingencyStartedAt().getOffset())))
                : Optional.empty();
        return Optional.of(new FiscalQueueItem(doc, state, explanation, elapsed));
    }
}
