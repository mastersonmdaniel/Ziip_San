package br.com.ziipsan.commercial.collection.dunning.installment;

/**
 * Vocabularios do parcelamento (P4.1, DAD-21 — dicionario 23b SS3.16).
 * Os CHECKs do SQL pendente espelham estes nomes.
 */
public final class InstallmentEnums {

    private InstallmentEnums() {
    }

    /** Metodo de juros do acordo ({@code interest_method}). */
    public enum InterestMethod {
        PRICE,
        COMPOUND,
        NONE
    }

    /** Situacao do acordo ({@code status}). */
    public enum Status {
        PENDING_CONFIRMATION,
        ACTIVE,
        UNDONE,
        CANCELED,
        REPLACED,
        CONCLUDED
    }

    /** Origem do acordo: perfil padrao, plano homologado ou campanha. */
    public enum Source {
        STANDARD,
        REPACTUATION_PLAN,
        REGULARIZATION_CAMPAIGN
    }
}
