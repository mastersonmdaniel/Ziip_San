package br.com.sigsan.commercial.billing.bill;

import java.util.Collection;

/**
 * Decisão de idempotência do faturamento de uma unidade numa referência. A garantia final está no
 * banco (índice único parcial sobre as contas "vivas"); esta regra é a mesma, no domínio, para o
 * faturamento decidir antes de calcular, e para o teste provar que reexecutar não duplica.
 */
public final class BillIdempotency {

    public enum Decision {
        /** Nenhuma conta viva: pode criar. */
        CREATE,
        /** Há só uma conta pré-faturada (impressão simultânea): reprocessa e substitui, sem criar outra. */
        REPLACE_PRE_BILLED,
        /** Já existe conta viva: não cria outra. */
        ALREADY_BILLED
    }

    private BillIdempotency() {
    }

    public static Decision decide(Collection<BillStatus> existingForUnitAndReference) {
        long live = existingForUnitAndReference.stream().filter(BillStatus::live).count();
        if (live == 0) {
            return Decision.CREATE;
        }
        if (live == 1 && existingForUnitAndReference.contains(BillStatus.PRE_BILLED)) {
            return Decision.REPLACE_PRE_BILLED;
        }
        return Decision.ALREADY_BILLED;
    }
}
