package br.com.ziipsan.commercial.billingrules;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Conta de campo calculada (ED-19). Invariante:
 * {@code total = fixed + water + sewer + debits - creditsApplied}, com
 * {@code creditsApplied <= fixed + water + sewer + debits} (credito alem das
 * cobrancas nao abate: volta em {@code creditsNotApplied} para o chamador
 * tratar no ciclo, nunca some).
 *
 * @param consumptionM3  consumo medido (leitura atual - anterior, com virada)
 * @param billedM3       consumo faturado (medido, no minimo minimo x economias)
 * @param waterValue     parcela variavel de agua (faixas sobre o excedente
 *                       por economia)
 * @param fixedValue     parcela fixa (fixa por economia x economias)
 * @param sewerValue     esgoto (percentual sobre agua variavel + fixa)
 * @param debitsValue    soma dos debitos
 * @param creditsApplied creditos efetivamente abatidos
 * @param creditsNotApplied creditos alem das cobrancas (nao abatidos)
 * @param total          total da conta
 * @param fingerprint    SHA-256 da forma canonica (entrada + resultado):
 *                       e ele que o servidor compara — igualdade de
 *                       fingerprint dispensa comparar campo a campo
 */
public record FieldBillResult(UUID taskId, int consumptionM3, int billedM3,
                              BigDecimal waterValue, BigDecimal fixedValue, BigDecimal sewerValue,
                              BigDecimal debitsValue, BigDecimal creditsApplied,
                              BigDecimal creditsNotApplied, BigDecimal total,
                              String fingerprint) {

    public FieldBillResult {
        Objects.requireNonNull(taskId, "task_id do resultado e obrigatorio");
        Objects.requireNonNull(waterValue, "valor de agua e obrigatorio");
        Objects.requireNonNull(fixedValue, "valor fixo e obrigatorio");
        Objects.requireNonNull(sewerValue, "valor de esgoto e obrigatorio");
        Objects.requireNonNull(debitsValue, "valor de debitos e obrigatorio");
        Objects.requireNonNull(creditsApplied, "creditos aplicados sao obrigatorios");
        Objects.requireNonNull(creditsNotApplied, "creditos nao aplicados sao obrigatorios");
        Objects.requireNonNull(total, "total e obrigatorio");
        Objects.requireNonNull(fingerprint, "fingerprint e obrigatorio");
    }

    /** Cobrancas antes de creditos: fixa + agua + esgoto + debitos. */
    public BigDecimal charges() {
        return fixedValue.add(waterValue).add(sewerValue).add(debitsValue);
    }
}
