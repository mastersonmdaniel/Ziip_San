package br.com.ziipsan.commercial.collection.dunning.installment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O acordo de parcelamento (P4.1; tabelas conforme o dicionario do
 * 23b SS3.16, DAD-21): {@code unit_id} e {@code customer_id} com o
 * TITULAR como devedor, {@code policy_version_id} do perfil versionado,
 * {@code protocol_id} e {@code service_order_id} (o parcelamento nasce
 * de protocolo e OS — ADR-0039), {@code included_bills} com o valor
 * ORIGINAL de cada conta e {@code legacy_reference} para a carga da
 * P2.10.
 */
public record InstallmentAgreement(
        UUID id,
        UUID tenantId,
        UUID unitId,
        UUID customerId,
        UUID policyVersionId,
        UUID protocolId,
        UUID serviceOrderId,
        List<IncludedBill> includedBills,
        BigDecimal totalDebt,
        Discounts discounts,
        BigDecimal downPayment,
        LocalDate downPaymentDueDate,
        List<InstallmentCalculator.Installment> schedule,
        InstallmentEnums.InterestMethod interestMethod,
        BigDecimal interestRate,
        InstallmentEnums.Status status,
        UUID replacesAgreementId,
        String legacyReference,
        InstallmentEnums.Source source,
        String sourceReference,
        String statementTemplateCode,
        String statementTemplateVersion,
        String statementDocumentRef,
        String termTemplateCode,
        String termTemplateVersion,
        String termDocumentRef,
        MinimumWaiver downPaymentWaiver,
        String signatureLevelName,
        String signatureReference) {

    /** Minimos da entrada que uma permissao pode dispensar, um a um (D5). */
    public enum WaivedMinimum {
        DOWN_PAYMENT_AMOUNT,
        DOWN_PAYMENT_DUE_DATE
    }

    /**
     * Permissao REGISTRADA para entrada abaixo do minimo (correcao D5 da
     * revisao cruzada): quem permitiu, quando e a referencia do registro,
     * e QUAIS minimos foram dispensados — nao e um boolean solto. Uma
     * permissao sem proveniencia completa nem se constroi, e ela so
     * dispensa os minimos nomeados nela.
     */
    public record MinimumWaiver(UUID grantedBy, Instant grantedAt, String reference,
                                Set<WaivedMinimum> waived) {
        public MinimumWaiver {
            if (grantedBy == null || grantedAt == null
                    || reference == null || reference.isBlank()
                    || waived == null || waived.isEmpty()) {
                throw new IllegalArgumentException(
                        "permissao registrada exige quem permitiu, quando, a referencia do registro "
                                + "e os minimos dispensados (proveniencia, D5)");
            }
            waived = Set.copyOf(waived);
        }

        public boolean waives(WaivedMinimum minimum) {
            return waived.contains(minimum);
        }
    }

    /** Conta incluida no acordo, com o seu valor ORIGINAL (DAD-21). */
    public record IncludedBill(UUID billId, BigDecimal originalAmount) {
        public IncludedBill {
            if (billId == null || originalAmount == null || originalAmount.signum() <= 0) {
                throw new IllegalArgumentException("conta incluida exige id e valor original positivo");
            }
        }
    }

    /** Abatimentos por componente (multa, juros, correcao) e do principal. */
    public record Discounts(BigDecimal fine, BigDecimal interest, BigDecimal correction, BigDecimal principal) {
        public Discounts {
            fine = fine == null ? BigDecimal.ZERO : fine;
            interest = interest == null ? BigDecimal.ZERO : interest;
            correction = correction == null ? BigDecimal.ZERO : correction;
            principal = principal == null ? BigDecimal.ZERO : principal;
            if (fine.signum() < 0 || interest.signum() < 0 || correction.signum() < 0 || principal.signum() < 0) {
                throw new IllegalArgumentException("abatimento nao pode ser negativo");
            }
        }

        public BigDecimal total() {
            return fine.add(interest).add(correction).add(principal);
        }

        public static Discounts none() {
            return new Discounts(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public InstallmentAgreement {
        if (id == null || tenantId == null || unitId == null || customerId == null
                || policyVersionId == null || protocolId == null || serviceOrderId == null) {
            throw new IllegalArgumentException(
                    "acordo exige id, tenant, unidade, titular (devedor), perfil versionado, protocolo e OS (ADR-0039)");
        }
        if (includedBills == null || includedBills.isEmpty()) {
            throw new IllegalArgumentException("acordo exige ao menos uma conta incluida");
        }
        includedBills = List.copyOf(includedBills);
        schedule = schedule == null ? List.of() : List.copyOf(schedule);
        if (totalDebt == null || totalDebt.signum() <= 0 || downPayment == null || downPayment.signum() < 0
                || downPaymentDueDate == null || interestMethod == null || interestRate == null
                || status == null || source == null) {
            throw new IllegalArgumentException("acordo exige total, entrada, vencimento da entrada, juros e estado");
        }
        if (statementDocumentRef == null || statementDocumentRef.isBlank()
                || termDocumentRef == null || termDocumentRef.isBlank()) {
            throw new IllegalArgumentException(
                    "acordo exige a referencia dos documentos GERADOS pelo motor de modelos (P2.30): "
                            + "e o termo gerado que se assina, nao o codigo do modelo (D1)");
        }
    }

    /** Soma dos valores originais das contas incluidas. */
    public BigDecimal includedTotal() {
        return includedBills.stream().map(IncludedBill::originalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Valor financiado: total apos abatimentos, menos a entrada. */
    public BigDecimal financedAmount() {
        return totalDebt.subtract(discounts.total()).subtract(downPayment);
    }

    /** Copia com novo estado (o acordo e imutavel; transicoes devolvem copia). */
    public InstallmentAgreement withStatus(InstallmentEnums.Status newStatus) {
        return new InstallmentAgreement(id, tenantId, unitId, customerId, policyVersionId, protocolId,
                serviceOrderId, includedBills, totalDebt, discounts, downPayment, downPaymentDueDate,
                schedule, interestMethod, interestRate, newStatus, replacesAgreementId, legacyReference,
                source, sourceReference, statementTemplateCode, statementTemplateVersion, statementDocumentRef,
                termTemplateCode, termTemplateVersion, termDocumentRef, downPaymentWaiver,
                signatureLevelName, signatureReference);
    }

    /** Copia confirmada com a referencia da assinatura (P3.34). */
    public InstallmentAgreement confirmedWith(String levelName, String ref) {
        return new InstallmentAgreement(id, tenantId, unitId, customerId, policyVersionId, protocolId,
                serviceOrderId, includedBills, totalDebt, discounts, downPayment, downPaymentDueDate,
                schedule, interestMethod, interestRate, InstallmentEnums.Status.ACTIVE, replacesAgreementId,
                legacyReference, source, sourceReference, statementTemplateCode, statementTemplateVersion,
                statementDocumentRef, termTemplateCode, termTemplateVersion, termDocumentRef,
                downPaymentWaiver, levelName, ref);
    }
}
