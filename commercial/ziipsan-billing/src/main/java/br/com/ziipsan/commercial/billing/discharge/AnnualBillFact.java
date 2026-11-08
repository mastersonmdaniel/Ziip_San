package br.com.ziipsan.commercial.billing.discharge;

import br.com.ziipsan.commercial.billing.bill.BillStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * O fato de uma conta que a regra da declaracao anual de quitacao (P2.24,
 * Lei 12.007/2009) precisa conhecer: a competencia, a situacao no
 * vocabulario da P2.5 ({@link BillStatus}, o mesmo do CHECK da V4_1) e a
 * data em que a conta foi quitada.
 *
 * <p>Quem monta os fatos le as contas da unidade ({@code bills} da P2.5) e
 * as baixas da arrecadacao (P3.1: pagamento classificado que baixou a
 * conta). A data de quitacao NAO e inventada a partir da situacao: conta
 * {@code PAID} sem data de quitacao e erro explicito do chamador, porque a
 * regra do mes seguinte a quitacao tardia depende dessa data; e data de
 * quitacao em conta nao paga e contradicao, tambem recusada aqui.
 *
 * @param billId         a conta (P2.5)
 * @param referenceMonth competencia da conta (primeiro dia do mes)
 * @param status         situacao da conta ({@link BillStatus})
 * @param settledOn      data da quitacao; obrigatoria se, e somente se,
 *                       {@code status == PAID}
 */
public record AnnualBillFact(
        UUID billId,
        LocalDate referenceMonth,
        BillStatus status,
        LocalDate settledOn) {

    public AnnualBillFact {
        if (billId == null) {
            throw new IllegalArgumentException("billId e obrigatorio");
        }
        if (referenceMonth == null || referenceMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException(
                    "referenceMonth deve ser o primeiro dia do mes de competencia: " + referenceMonth);
        }
        if (status == null) {
            throw new IllegalArgumentException("status e obrigatorio para a conta " + billId);
        }
        if (status == BillStatus.PAID && settledOn == null) {
            throw new IllegalArgumentException("conta " + billId + " esta PAID sem data de quitacao:"
                    + " a regra da declaracao anual exige a data real da baixa (P3.1)");
        }
        if (status != BillStatus.PAID && settledOn != null) {
            throw new IllegalArgumentException("conta " + billId + " tem data de quitacao mas nao esta"
                    + " PAID (status " + status + "): fato contraditorio nao alimenta a regra");
        }
    }

    /** A conta ocupa o ano? (situacao nao viva libera o par e nao conta no ano: BillStatus.live). */
    public boolean countsForYear() {
        return status.live();
    }

    /** A conta esta quitada? */
    public boolean settled() {
        return status == BillStatus.PAID;
    }
}
