package br.com.ziipsan.commercial.billingrules;

import java.util.Objects;

/**
 * Uma tarefa do pacote da rota (ED-19): tudo que a emissao simultanea daquela
 * unidade precisa, MENOS a leitura atual (unico dado que nasce em campo).
 * A entrada de calculo e montada por {@link #toInput(int)} quando a leitura
 * chega — no aparelho e, na recalculacao, no servidor, com o mesmo codigo.
 *
 * @param inputBase    entrada sem a leitura atual e representada pela
 *                     {@code previousReading} como marcador: o
 *                     {@link FieldBillInput} base carrega currentReading =
 *                     previousReading (consumo zero) e {@link #toInput(int)}
 *                     devolve a entrada real. O marcador e validado no
 *                     construtor para nunca ser confundido com leitura real.
 * @param barcodeValue valor do codigo de barras da conta (resolvido no
 *                     pacote: digitos da cobranca hibrida da P3.2); pode ser
 *                     {@code null} quando a tarefa ainda nao tem cobranca
 *                     resolvida — a impressao sai sem barras, nunca com
 *                     digitos inventados
 */
public record RouteTask(FieldBillInput inputBase, String barcodeValue) {

    public RouteTask {
        Objects.requireNonNull(inputBase, "entrada base da tarefa e obrigatoria");
        if (inputBase.currentReading() != inputBase.previousReading()) {
            throw new IllegalArgumentException(
                    "A entrada base da tarefa carrega currentReading = previousReading como marcador; "
                            + "a leitura atual so entra por toInput(int)");
        }
    }

    /** Entrada real do calculo com a leitura feita em campo. */
    public FieldBillInput toInput(int currentReading) {
        return new FieldBillInput(inputBase.taskId(), inputBase.unitId(), inputBase.installationId(),
                inputBase.previousReading(), currentReading, inputBase.meterDigits(),
                inputBase.economies(), inputBase.snapshot(), inputBase.debits(),
                inputBase.credits(), inputBase.dueDate());
    }
}
