package br.com.ziipsan.commercial.billing.relief;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatorio de contas provisorias (DESENHO-PA02 secao 4.4): a lista de
 * trabalho da regularizacao. Sem marcador no snapshot nao ha regularizacao
 * possivel — este relatorio le exactamente as contas marcadas.
 *
 * <p>Regularizacao (NT 5.3.3): compensacao das diferencas apuradas, em favor
 * do prestador ou dos usuarios. A forma operacional fina e [SEM FONTE]
 * (P-02-05 parcial) e nao se inventa aqui.
 */
public final class ProvisionalBillReport {

    /** Conta emitida pela via provisoria, lida do snapshot. */
    public record ProvisionalBill(String billReference, UUID programId, LocalDate issuedOn,
                                  ReliefSnapshotKeys snapshotKeys) {
        public ProvisionalBill {
            Objects.requireNonNull(snapshotKeys, "chaves do snapshot sao obrigatorias");
            if (!snapshotKeys.reliefProvisionalFallback()) {
                throw new IllegalArgumentException(
                        "Conta " + billReference + " nao esta marcada como provisoria");
            }
        }
    }

    private ProvisionalBillReport() {
    }

    /** Filtra, da massa dada, exactamente as provisorias do programa. */
    public static List<ProvisionalBill> ofProgram(List<ProvisionalBill> bills, UUID programId) {
        Objects.requireNonNull(bills, "massa de contas e obrigatoria");
        Objects.requireNonNull(programId, "programa e obrigatorio");
        return bills.stream().filter(b -> programId.equals(b.programId())).toList();
    }
}
