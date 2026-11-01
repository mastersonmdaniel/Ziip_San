package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Débito a cobrar: valor devido (serviço, parcelamento, acréscimo...) que entra na conta em
 * parcelas. O chamador seleciona os que entram no mês (não cancelado, não em revisão, ainda com
 * parcelas); o cálculo da parcela está em {@link Installments}.
 */
public record DebitReceivable(UUID id, String typeCode, BigDecimal total, int installments, int installmentsDone) {

    public DebitReceivable {
        if (id == null || typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("Débito sem identificação ou tipo");
        }
        if (total == null || total.signum() <= 0) {
            throw new IllegalArgumentException("Valor do débito deve ser positivo: " + total);
        }
        if (installments < 1 || installmentsDone < 0 || installmentsDone > installments) {
            throw new IllegalArgumentException("Parcelas inconsistentes: " + installmentsDone + "/" + installments);
        }
    }

    public boolean hasInstallmentToCharge() {
        return installmentsDone < installments;
    }
}
