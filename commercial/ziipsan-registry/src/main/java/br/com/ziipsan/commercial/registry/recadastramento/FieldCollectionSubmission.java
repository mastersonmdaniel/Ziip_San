package br.com.ziipsan.commercial.registry.recadastramento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Uma coleta de campo do recadastramento, como chega na sincronizacao
 * do app da ED-18 (ED-16): o valor de UM campo pre-definido, levantado
 * numa unidade do lote.
 *
 * <p>{@code taskId} e o identificador da tarefa gerado no servidor
 * quando o pacote do lote e montado e devolvido intacto pelo aparelho:
 * e a chave de idempotencia da sincronizacao (o reenvio da mesma tarefa
 * nao duplica a coleta), no mesmo desenho do
 * {@code FieldReadingSubmission} da ED-18. {@code photoRefs} sao
 * referencias (hash + nome) das fotos ja enviadas por upload separado.
 * {@code valueMasked} declara que o valor ja vem mascarado — exigido
 * para o campo de documento (ver
 * {@link RecadastramentoFieldCatalog#requiresMaskedValue}).
 *
 * <p>Este record NAO valida regra de negocio: quem valida e o
 * {@code RecadastramentoService}, que registra cada recusa como
 * {@link CollectionError} antes de lancar a excecao.
 */
public record FieldCollectionSubmission(
        UUID taskId,
        UUID batchId,
        UUID deviceId,
        UUID collectorId,
        UUID unitId,
        UUID customerId,
        String targetType,
        String field,
        String collectedValue,
        boolean valueMasked,
        List<String> photoRefs,
        OffsetDateTime capturedAt) {

    public FieldCollectionSubmission {
        photoRefs = photoRefs == null ? List.of() : List.copyOf(photoRefs);
    }

    /** Chave de idempotencia da coleta (tenant + lote + tarefa). */
    public String idempotencyKey() {
        return "recadastramento-task:" + taskId;
    }
}
