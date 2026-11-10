package br.com.ziipsan.commercial.billing.composition;

import java.util.Objects;

/**
 * Porta de gravacao do snapshot, invocada PELA CAMADA DE APLICACAO no
 * acto da emissao (desenho da Camada 0, secao 2.1 item 14; regra de
 * fronteira (vi)). A implementacao (persistencia na coluna do
 * documento, ao lado das colunas vigentes de {@code bills}, que nao
 * sao alteradas) e da camada de aplicacao/integracao; nesta camada
 * vive o contrato e a guarda de emissao.
 *
 * <p><b>INV-C2-07:</b> emitir sem snapshot e falha, nunca emissao
 * silenciosa sem registo — {@link #requireAndWrite} e o ponto unico
 * que a emissao deve chamar: porta ausente = excecao.
 */
public interface ChargeSnapshotWriter {

    /** Grava o documento do snapshot da conta emitida. */
    void write(ChargeSnapshotDocument document);

    /**
     * Guarda de emissao (INV-C2-07): exige a porta e grava o snapshot.
     *
     * @throws IllegalStateException se a porta for {@code null}
     *         (emissao sem snapshot) ou o documento for {@code null}
     */
    static void requireAndWrite(ChargeSnapshotWriter writer, ChargeSnapshotDocument document) {
        if (writer == null) {
            throw new IllegalStateException(
                    "emissao sem snapshot recusada: ChargeSnapshotWriter ausente (INV-C2-07)");
        }
        Objects.requireNonNull(document, "documento de snapshot e obrigatorio na emissao");
        writer.write(document);
    }
}
