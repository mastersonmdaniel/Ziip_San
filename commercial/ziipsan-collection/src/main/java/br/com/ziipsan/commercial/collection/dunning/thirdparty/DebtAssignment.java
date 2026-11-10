package br.com.ziipsan.commercial.collection.dunning.thirdparty;

import java.time.LocalDate;
import java.util.UUID;

/**
 * E8 — Cessao de uma conta (divida/UC) a empresa terceirizada, sob um
 * contrato (E1). A cessao guarda o payload minimo (E5) que foi enviado —
 * e nada alem dele.
 *
 * <p>Estados: {@code ACTIVE} -> {@code WITHDRAWN} (terminal). A retirada
 * tem SEMPRE causa enumerada e data declaradas:
 * {@link WithdrawalCause#PAYMENT} (pagamento), {@link
 * WithdrawalCause#INSTALLMENT_AGREEMENT} (parcelamento) ou {@link
 * WithdrawalCause#WINDOW_END} (fim da janela do contrato). Uma cessao
 * retirada nao pode ser retirada outra vez nem reactivada por esta via.
 */
public record DebtAssignment(UUID id, UUID tenantId, UUID contractId, MinimalDebtorData minimalData,
                             Status status, WithdrawalCause withdrawalCause, LocalDate withdrawnOn) {

    public enum Status { ACTIVE, WITHDRAWN }

    public enum WithdrawalCause { PAYMENT, INSTALLMENT_AGREEMENT, WINDOW_END }

    public DebtAssignment {
        if (id == null)
            throw new ThirdPartyCollectionException("ASSIGNMENT_ID_REQUIRED", "cessao exige id");
        if (tenantId == null)
            throw new ThirdPartyCollectionException("TENANT_REQUIRED", "tenant obrigatorio");
        if (contractId == null)
            throw new ThirdPartyCollectionException("ASSIGNMENT_CONTRACT_REQUIRED", "cessao exige o contrato (E1)");
        if (minimalData == null)
            throw new ThirdPartyCollectionException("ASSIGNMENT_DATA_REQUIRED",
                    "cessao exige o payload minimo (E5) que foi enviado");
        if (status == null)
            throw new ThirdPartyCollectionException("ASSIGNMENT_STATUS_REQUIRED", "cessao exige estado");
        if (status == Status.ACTIVE && (withdrawalCause != null || withdrawnOn != null))
            throw new ThirdPartyCollectionException("ASSIGNMENT_ACTIVE_WITH_WITHDRAWAL",
                    "cessao ACTIVE nao pode carregar causa/data de retirada");
        if (status == Status.WITHDRAWN && (withdrawalCause == null || withdrawnOn == null))
            throw new ThirdPartyCollectionException("ASSIGNMENT_WITHDRAWAL_INCOMPLETE",
                    "cessao WITHDRAWN exige causa enumerada e data da retirada");
    }

    public static DebtAssignment active(UUID id, UUID tenantId, UUID contractId, MinimalDebtorData data) {
        return new DebtAssignment(id, tenantId, contractId, data, Status.ACTIVE, null, null);
    }

    public DebtAssignment withdrawn(WithdrawalCause cause, LocalDate on) {
        if (status != Status.ACTIVE)
            throw new ThirdPartyCollectionException("ASSIGNMENT_ALREADY_WITHDRAWN",
                    "cessao ja retirada (terminal): " + id);
        if (cause == null || on == null)
            throw new ThirdPartyCollectionException("ASSIGNMENT_WITHDRAWAL_INCOMPLETE",
                    "retirada exige causa enumerada e data declaradas");
        return new DebtAssignment(id, tenantId, contractId, minimalData, Status.WITHDRAWN, cause, on);
    }

    public UUID debtId() {
        return minimalData.debtId();
    }
}
