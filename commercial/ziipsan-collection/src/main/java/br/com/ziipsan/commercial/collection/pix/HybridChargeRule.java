package br.com.ziipsan.commercial.collection.pix;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Elegibilidade da cobranca hibrida (P3.2) pela Res. BCB 587/2026, vigente a
 * partir de 01/02/2027: Pix Cobranca COM vencimento + boleto, no mesmo
 * documento, com baixa unica.
 *
 * <p>Condicoes cumulativas (cada recusa tem o seu motivo e o seu teste):
 * <ul>
 *   <li>regra vigente: emissao antes de 01/02/2027 e recusada;</li>
 *   <li>so Pix Cobranca com vencimento (Pix imediato ou sem vencimento, nao);</li>
 *   <li>boleto COMUM ou DINAMICO, nunca vinculado a ativo financeiro;</li>
 *   <li>boleto de proposta e boleto de deposito sao recusados;</li>
 *   <li>emissor do boleto = PSP do recebedor (nunca terceiro);</li>
 *   <li>oferta facultativa por tenant: {@code pay.hybrid_offer_enabled}
 *       desligado = sem hibrido para aquele tenant;</li>
 *   <li>valor e vencimento do Pix e do boleto sao os do mesmo documento.</li>
 * </ul>
 */
public final class HybridChargeRule {

    /** Vigencia da Res. BCB 587/2026 para a cobranca hibrida. */
    public static final LocalDate EFFECTIVE_FROM = LocalDate.of(2027, 2, 1);

    public enum SlipKind { COMUM, DINAMICO, PROPOSTA, DEPOSITO }

    public enum Refusal {
        RULE_NOT_YET_EFFECTIVE, PIX_WITHOUT_DUE_DATE, SLIP_KIND_FORBIDDEN,
        SLIP_LINKED_TO_FINANCIAL_ASSET, SLIP_ISSUER_NOT_RECEIVER_PSP,
        TENANT_DOES_NOT_OFFER, AMOUNT_OR_DUE_DATE_DIVERGE
    }

    public static final class HybridRefusedException extends RuntimeException {
        private final Refusal refusal;

        public HybridRefusedException(Refusal refusal, String detail) {
            super(refusal + ": " + detail);
            this.refusal = refusal;
        }

        public Refusal refusal() {
            return refusal;
        }
    }

    /**
     * @param issueDate              data de emissao da cobranca hibrida
     * @param pixHasDueDate          o Pix e Cobranca com vencimento
     * @param slipKind               tipo do boleto
     * @param slipLinkedToAsset      boleto vinculado a ativo financeiro
     * @param slipIssuerPsp          emissor do boleto
     * @param receiverPsp            PSP do recebedor
     * @param tenantOffersHybrid     {@code pay.hybrid_offer_enabled} do tenant
     * @param pixAmountCents         valor do Pix, em centavos (comparacao exata)
     * @param slipAmountCents        valor do boleto, em centavos
     * @param pixDueDate             vencimento do Pix
     * @param slipDueDate            vencimento do boleto
     */
    public record HybridRequest(LocalDate issueDate, boolean pixHasDueDate, SlipKind slipKind,
                                boolean slipLinkedToAsset, String slipIssuerPsp, String receiverPsp,
                                boolean tenantOffersHybrid, long pixAmountCents, long slipAmountCents,
                                LocalDate pixDueDate, LocalDate slipDueDate) {
    }

    private HybridChargeRule() {
    }

    /** Valida e devolve o proprio pedido; recusa lanca {@link HybridRefusedException}. */
    public static HybridRequest requireEligible(HybridRequest req) {
        Objects.requireNonNull(req, "pedido de cobranca hibrida e obrigatorio");
        Objects.requireNonNull(req.issueDate(), "data de emissao e obrigatoria");
        if (req.issueDate().isBefore(EFFECTIVE_FROM)) {
            throw new HybridRefusedException(Refusal.RULE_NOT_YET_EFFECTIVE,
                    "cobranca hibrida so a partir de " + EFFECTIVE_FROM + " (Res. BCB 587/2026)");
        }
        if (!req.pixHasDueDate()) {
            throw new HybridRefusedException(Refusal.PIX_WITHOUT_DUE_DATE,
                    "hibrido exige Pix Cobranca com vencimento");
        }
        Objects.requireNonNull(req.slipKind(), "tipo do boleto e obrigatorio");
        if (req.slipKind() == SlipKind.PROPOSTA || req.slipKind() == SlipKind.DEPOSITO) {
            throw new HybridRefusedException(Refusal.SLIP_KIND_FORBIDDEN,
                    "boleto de " + req.slipKind() + " nao compoe cobranca hibrida");
        }
        if (req.slipLinkedToAsset()) {
            throw new HybridRefusedException(Refusal.SLIP_LINKED_TO_FINANCIAL_ASSET,
                    "boleto vinculado a ativo financeiro nao compoe cobranca hibrida");
        }
        if (req.slipIssuerPsp() == null || !req.slipIssuerPsp().equals(req.receiverPsp())) {
            throw new HybridRefusedException(Refusal.SLIP_ISSUER_NOT_RECEIVER_PSP,
                    "emissor do boleto (" + req.slipIssuerPsp() + ") deve ser o PSP do recebedor ("
                            + req.receiverPsp() + ")");
        }
        if (!req.tenantOffersHybrid()) {
            throw new HybridRefusedException(Refusal.TENANT_DOES_NOT_OFFER,
                    "oferta da cobranca hibrida e facultativa e esta desligada para o tenant");
        }
        if (req.pixAmountCents() <= 0 || req.pixAmountCents() != req.slipAmountCents()
                || req.pixDueDate() == null || !req.pixDueDate().equals(req.slipDueDate())) {
            throw new HybridRefusedException(Refusal.AMOUNT_OR_DUE_DATE_DIVERGE,
                    "Pix e boleto do mesmo documento tem o mesmo valor e o mesmo vencimento");
        }
        return req;
    }
}
