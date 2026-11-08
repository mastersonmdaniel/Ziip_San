package br.com.ziipsan.commercial.billingrules;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entrada completa do calculo da conta de campo (ED-19). Tudo ja resolvido:
 * o aparelho recebe estes valores no pacote da rota (leituras anteriores,
 * economias, retrato de tarifa) e so acrescenta a leitura atual; o servidor
 * remonta a MESMA entrada a partir da submissao + do pacote que ele mesmo
 * emitiu e recalcula. Nenhum dos dois lados busca dado fora da entrada.
 *
 * @param taskId         identificador da tarefa de campo (a conta de campo e
 *                       UNICA por task_id — ver o servico de submissao)
 * @param unitId         unidade consumidora
 * @param installationId instalacao/hidrometro da leitura
 * @param previousReading leitura anterior (do pacote)
 * @param currentReading  leitura atual (de campo)
 * @param meterDigits     digitos do mostrador
 * @param economies       economias da unidade na categoria da tarefa
 * @param snapshot        retrato de tarifa/parametros da tarefa
 * @param debits          debitos selecionados para esta emissao
 * @param credits         creditos selecionados para esta emissao
 * @param dueDate         vencimento resolvido no pacote (regra da P2.5)
 */
public record FieldBillInput(UUID taskId, UUID unitId, UUID installationId,
                             int previousReading, int currentReading, int meterDigits,
                             int economies, FieldTariffSnapshot snapshot,
                             List<AmountLine> debits, List<AmountLine> credits,
                             LocalDate dueDate) {

    public FieldBillInput {
        Objects.requireNonNull(taskId, "task_id e obrigatorio (a conta de campo e unica por task_id)");
        Objects.requireNonNull(unitId, "unidade e obrigatoria");
        Objects.requireNonNull(installationId, "instalacao e obrigatoria");
        Objects.requireNonNull(snapshot, "retrato de tarifa e obrigatorio");
        Objects.requireNonNull(dueDate, "vencimento e obrigatorio");
        if (economies < 1) {
            throw new IllegalArgumentException("Economias deve ser pelo menos 1: " + economies);
        }
        debits = debits == null ? List.of() : List.copyOf(debits);
        credits = credits == null ? List.of() : List.copyOf(credits);
    }
}
