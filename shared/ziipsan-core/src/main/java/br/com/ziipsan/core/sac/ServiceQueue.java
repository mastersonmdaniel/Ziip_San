package br.com.ziipsan.core.sac;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de atendimento por prazo sobre o protocolo da P1.36 (ED-35), com o
 * atendimento prioritario e de acessibilidade do perfil da P1.3 entrando
 * ANTES do prazo na ordenacao (quem tem prioridade legal nao espera atras
 * de quem nao tem). Desempate: vencimento, depois abertura mais antiga.
 * O protocolo e referenciado pelo {@link UUID} da P1.36 (V8_1), convencao
 * do repo (correcao pos-reprovacao, M4).
 */
public final class ServiceQueue {

    /** priorityProfile = perfil prioritario/acessibilidade marcado na P1.3. */
    public record QueueItem(UUID protocolId, LocalDate dueDate, LocalDate openedDate,
                            boolean priorityProfile) {
        public QueueItem {
            Objects.requireNonNull(protocolId, "protocolo obrigatorio");
            Objects.requireNonNull(dueDate, "prazo obrigatorio");
            Objects.requireNonNull(openedDate, "abertura obrigatoria");
        }
        public boolean overdueOn(LocalDate date) { return date.isAfter(dueDate); }
    }

    private ServiceQueue() {}

    public static List<QueueItem> order(List<QueueItem> items) {
        return items.stream()
                .sorted(Comparator.comparing(QueueItem::priorityProfile).reversed()
                        .thenComparing(QueueItem::dueDate)
                        .thenComparing(QueueItem::openedDate)
                        .thenComparing(QueueItem::protocolId))
                .toList();
    }
}
