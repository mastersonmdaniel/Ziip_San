package br.com.ziipsan.operations.metering.field;

import br.com.ziipsan.operations.metering.rules.MeasurementKind;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Uma tarefa de leitura do pacote de campo (ED-18). {@code taskId} e o identificador da
 * tarefa gerado no servidor quando o pacote da rota e montado e devolvido intacto pelo
 * aparelho: e a chave de idempotencia da sincronizacao (o reenvio da mesma tarefa nao
 * duplica a leitura — no banco, a mesma chave alimenta o {@code idempotency_key} da
 * V5_4 sobre {@code meter_readings}, por tenant e mes de referencia).
 *
 * <p>{@code photoRefs} sao referencias (hash + nome) das fotos ja enviadas por upload
 * separado; a foto do visor usada pelo OCR NAO esta entre elas: ela nao sai do aparelho.
 * {@code ocrValue} sao so os digitos que o OCR on-device reconheceu.
 */
public record FieldReadingSubmission(UUID taskId, UUID unitId, UUID installationId, MeasurementKind kind,
                                     YearMonth reference, LocalDate readOn, Integer reading,
                                     String occurrenceCode, List<String> photoRefs,
                                     Integer ocrValue, OffsetDateTime capturedAt) {

    public FieldReadingSubmission {
        photoRefs = photoRefs == null ? List.of() : List.copyOf(photoRefs);
    }

    /** Chave de idempotencia gravada em meter_readings.idempotency_key (V5_4, teto de 120). */
    public String idempotencyKey() {
        return "field-task:" + taskId;
    }
}
