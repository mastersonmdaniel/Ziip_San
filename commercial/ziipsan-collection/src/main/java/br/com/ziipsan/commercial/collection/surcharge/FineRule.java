package br.com.ziipsan.commercial.collection.surcharge;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Multa moratória: percentual único, cobrado uma vez sobre o principal, sem capitalização.
 *
 * <p>O percentual é obrigatório e vem do tenant (contrato/regulador); o padrão de fábrica
 * é {@link LegalLimits#FACTORY_FINE_PERCENT} (2%). A trava de
 * {@link LegalLimits#MAX_FINE_PERCENT} (2%) vale se não houver teto próprio.
 *
 * <p>P0.20 (classe b, PARAMETROS G5): teto próprio ACIMA de 2% não vale mais com só uma
 * justificativa em texto livre — era o que esta classe aceitava antes. Acima de 2% exige,
 * juntos: norma citada, aprovação dupla (quem propõe ≠ quem aprova), aceite formal
 * registrado (referência do aceite em {@code parameter_acceptances}) e alerta de risco
 * reconhecido. Isso é {@link #withAboveLegalCap} / {@link FineCapApproval}; o construtor
 * direto com teto acima de 2% recusa e aponta para ela. O CHECK da V6_6 impõe o mesmo
 * no banco. Teto absoluto acima de 2%, se houver: a definir pelo dono (PARAMETROS §7,
 * P0) — nenhum teto absoluto é fixado aqui.
 *
 * @param ratePercent              percentual da multa (ex.: {@code 2.00} é 2%)
 * @param capOverridePercent       teto próprio do tenant, ou {@code null} para usar a trava legal
 * @param capOverrideJustification texto livre legado; obrigatória no V6_1, mas SOZINHA
 *                                 não autoriza mais teto acima de 2%
 * @param capApproval              aceite reforçado; obrigatório quando o teto próprio
 *                                 passa de 2% — ver {@link #withAboveLegalCap}
 */
public record FineRule(BigDecimal ratePercent, BigDecimal capOverridePercent, String capOverrideJustification,
                       FineCapApproval capApproval) {

    /** Construtor legado (sem aceite reforçado): teto acima de 2% é recusado. */
    public FineRule(BigDecimal ratePercent, BigDecimal capOverridePercent, String capOverrideJustification) {
        this(ratePercent, capOverridePercent, capOverrideJustification, null);
    }

    /**
     * Aceite reforçado de teto de multa acima de 2%: os quatro requisitos da classe b.
     * Os UUIDs são de usuários do tenant; {@code acceptanceReference} é a referência do
     * aceite formal (id da linha em {@code parameter_acceptances}).
     */
    public record FineCapApproval(String normCitation, UUID proposedBy, UUID approvedBy,
                                  String acceptanceReference, boolean riskAlertAcknowledged) {
        public FineCapApproval {
            if (normCitation == null || normCitation.isBlank()) {
                throw new IllegalArgumentException(
                        "Multa acima de 2% exige a norma citada que a permite");
            }
            if (proposedBy == null || approvedBy == null) {
                throw new IllegalArgumentException(
                        "Multa acima de 2% exige aprovação dupla: quem propõe e quem aprova");
            }
            if (approvedBy.equals(proposedBy)) {
                throw new IllegalArgumentException("Quem propõe não aprova o próprio teto de multa");
            }
            if (acceptanceReference == null || acceptanceReference.isBlank()) {
                throw new IllegalArgumentException(
                        "Multa acima de 2% exige aceite formal registrado");
            }
            if (!riskAlertAcknowledged) {
                throw new IllegalArgumentException(
                        "Multa acima de 2% exige alerta de risco exibido e reconhecido");
            }
        }
    }

    public FineRule {
        if (ratePercent == null || ratePercent.signum() < 0) {
            throw new IllegalArgumentException("O percentual de multa é obrigatório e não pode ser negativo");
        }
        if (capOverridePercent != null) {
            if (capOverridePercent.signum() < 0) {
                throw new IllegalArgumentException("Teto de multa negativo");
            }
            if (capOverridePercent.compareTo(LegalLimits.MAX_FINE_PERCENT) > 0 && capApproval == null) {
                throw new IllegalArgumentException(
                        "Teto de multa acima de " + LegalLimits.MAX_FINE_PERCENT
                                + "% exige norma citada, aprovação dupla, aceite formal registrado "
                                + "e alerta de risco (use FineRule.withAboveLegalCap); "
                                + "justificativa em texto livre, sozinha, não basta");
            }
        }
        BigDecimal cap = capOverridePercent != null ? capOverridePercent : LegalLimits.MAX_FINE_PERCENT;
        if (ratePercent.compareTo(cap) > 0) {
            throw new IllegalArgumentException("Multa de " + ratePercent.toPlainString()
                    + "% acima do teto de " + cap.toPlainString() + "%");
        }
    }

    /** Multa sob a trava legal de 2%. */
    public static FineRule of(BigDecimal ratePercent) {
        return new FineRule(ratePercent, null, null);
    }

    /** Multa sem cobrança (percentual zero), declarada explicitamente. */
    public static FineRule none() {
        return new FineRule(BigDecimal.ZERO, null, null);
    }

    /**
     * Multa com teto próprio ATÉ 2% (a justificativa, quando houver, fica registrada).
     * Para teto acima de 2%, use {@link #withAboveLegalCap}.
     */
    public static FineRule withCapOverride(BigDecimal ratePercent, BigDecimal capPercent, String justification) {
        return new FineRule(ratePercent, capPercent, justification);
    }

    /** Multa com teto próprio acima de 2%, com o aceite reforçado da classe b. */
    public static FineRule withAboveLegalCap(BigDecimal ratePercent, BigDecimal capPercent,
                                             FineCapApproval approval) {
        if (approval == null) {
            throw new IllegalArgumentException(
                    "Multa acima de 2% exige o aceite reforçado (FineCapApproval)");
        }
        if (capPercent == null || capPercent.compareTo(LegalLimits.MAX_FINE_PERCENT) <= 0) {
            throw new IllegalArgumentException(
                    "withAboveLegalCap é só para teto acima de " + LegalLimits.MAX_FINE_PERCENT + "%");
        }
        return new FineRule(ratePercent, capPercent, approval.normCitation(), approval);
    }
}
