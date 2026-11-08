package br.com.ziipsan.core.backoffice;

import br.com.ziipsan.core.serviceorder.ServiceOrderEnums;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Baixa posterior pelo backoffice (ED-36): para equipe sem app e para
 * empreiteira, o encerramento e registrado DEPOIS da execucao, com data e
 * hora da EXECUCAO (nao do registro), executor, materiais usados e foto.
 *
 * <p>Regras:
 * <ul>
 *   <li>a execucao nao pode estar no futuro nem o registro exceder
 *       {@code so.retroactive_close_days} dias apos ela (OS-12);</li>
 *   <li>modo INTERNAL nao tem baixa posterior (e trabalho de balcao, sem
 *       deslocamento): so FIELD e CONTRACTOR, e CONTRACTOR exige a
 *       referencia da empreiteira;</li>
 *   <li>os prazos de religacao e de compensacao contam da HORA DE EXECUCAO
 *       REGISTRADA: {@link #deadlineFromExecution} soma as horas do SLA
 *       (rec.sla_hours da P3.4, resolvido pelo chamador) sobre executedAt —
 *       nunca sobre registeredAt. E o teste exigido pelo acceptance.</li>
 *   <li>a FOTO da baixa so vale com a varredura CLEARED: a validacao que
 *       recebe o anexo ({@link #validate(Close, int, OrderAttachment.Attachment)})
 *       exige que o anexo seja a propria foto declarada na baixa, do tipo
 *       PHOTO e liberado por {@link OrderAttachment#requireLinkable}
 *       (ADR-0020) — correcao D4 da revisao cruzada (antes o
 *       {@code photoAttachmentId} era um UUID solto, sem composicao
 *       anexo×baixa).</li>
 * </ul>
 */
public final class RetroactiveClose {

    public record Close(UUID orderId, ServiceOrderEnums.ExecutionMode executionMode, String contractorRefOrNull,
                        UUID executorId, LocalDateTime executedAt, LocalDateTime registeredAt,
                        List<PlannedMaterials.Line> materialsUsed, UUID photoAttachmentId) {
        public Close {
            Objects.requireNonNull(orderId, "OS e obrigatoria");
            Objects.requireNonNull(executionMode, "modo de execucao e obrigatorio");
            Objects.requireNonNull(executorId, "executor da baixa posterior e obrigatorio");
            Objects.requireNonNull(executedAt, "data e hora da execucao sao obrigatorias");
            Objects.requireNonNull(registeredAt, "data e hora do registro sao obrigatorias");
            Objects.requireNonNull(materialsUsed, "materiais usados sao obrigatorios (lista pode ser vazia se nada foi usado)");
            Objects.requireNonNull(photoAttachmentId, "foto da execucao e obrigatoria na baixa posterior (documento da P1.20)");
            materialsUsed = List.copyOf(materialsUsed);
        }
    }

    private RetroactiveClose() {}

    public static Close validate(Close close, int retroactiveCloseDays) {
        Objects.requireNonNull(close, "baixa posterior e obrigatoria");
        if (retroactiveCloseDays <= 0) throw new IllegalArgumentException(SchedParameters.RETROACTIVE_CLOSE_DAYS + " deve ser positivo");
        if (close.executionMode() == ServiceOrderEnums.ExecutionMode.INTERNAL)
            throw new IllegalArgumentException("baixa posterior nao se aplica a OS interna (sem deslocamento)");
        if (close.executionMode() == ServiceOrderEnums.ExecutionMode.CONTRACTOR
                && (close.contractorRefOrNull() == null || close.contractorRefOrNull().isBlank()))
            throw new IllegalArgumentException("baixa posterior de empreiteira exige a referencia da empreiteira");
        if (close.executedAt().isAfter(close.registeredAt()))
            throw new IllegalArgumentException("execucao no futuro: executada em " + close.executedAt() + ", registrada em " + close.registeredAt());
        long days = ChronoUnit.DAYS.between(close.executedAt().toLocalDate(), close.registeredAt().toLocalDate());
        if (days > retroactiveCloseDays)
            throw new IllegalArgumentException("baixa posterior fora da janela de " + SchedParameters.RETROACTIVE_CLOSE_DAYS
                    + " (" + retroactiveCloseDays + " dias): execucao ha " + days + " dias");
        for (PlannedMaterials.Line l : close.materialsUsed())
            if (!l.recorded()) throw new IllegalArgumentException("material da baixa posterior sem quantidade usada: " + l.materialCode());
        return close;
    }

    /**
     * Validacao composta da baixa posterior COM a foto (correcao D4): alem das
     * regras de {@link #validate(Close, int)}, o anexo precisa ser a foto
     * declarada na baixa (mesma OS, mesmo documento de {@code photoAttachmentId}),
     * do tipo PHOTO e com varredura CLEARED ({@link OrderAttachment#requireLinkable}).
     * Foto em QUARANTINE ou INFECTED recusa a baixa — evidencia nao liberada
     * nao sustenta encerramento posterior.
     */
    public static Close validate(Close close, int retroactiveCloseDays, OrderAttachment.Attachment photo) {
        Close valid = validate(close, retroactiveCloseDays);
        Objects.requireNonNull(photo, "anexo da foto da baixa posterior e obrigatorio");
        if (!photo.orderId().equals(close.orderId()))
            throw new IllegalArgumentException("foto da baixa posterior pertence a outra OS: " + photo.orderId());
        if (!photo.documentId().equals(close.photoAttachmentId()))
            throw new IllegalArgumentException("anexo " + photo.documentId() + " nao e a foto declarada na baixa ("
                    + close.photoAttachmentId() + ")");
        if (photo.kind() != OrderAttachment.Kind.PHOTO)
            throw new IllegalArgumentException("anexo da baixa posterior deve ser PHOTO, nao " + photo.kind());
        OrderAttachment.requireLinkable(photo);
        return valid;
    }

    /** Prazo (religacao/compensacao) contado da hora de execucao registrada. */
    public static LocalDateTime deadlineFromExecution(Close close, int slaHours) {
        if (slaHours <= 0) throw new IllegalArgumentException("rec.sla_hours deve ser positivo (P3.4)");
        return close.executedAt().plusHours(slaHours);
    }

    /** Estourou = conclusao (ex.: religacao) depois do prazo contado da execucao. */
    public static boolean missedDeadline(Close close, LocalDateTime completedAt, int slaHours) {
        Objects.requireNonNull(completedAt, "instante da conclusao e obrigatorio");
        return completedAt.isAfter(deadlineFromExecution(close, slaHours));
    }
}
