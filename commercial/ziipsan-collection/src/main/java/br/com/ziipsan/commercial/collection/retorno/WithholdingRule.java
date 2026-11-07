package br.com.ziipsan.commercial.collection.retorno;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Baixa com retencao na fonte para pagador de natureza publica (P3.1; IN RFB
 * 1.234/2012).
 *
 * <p>O recurso e ligado por parametro ({@code pay.withholding_enabled}, lido por
 * {@code CollectionParameters}) e so pode ser ligado depois do parecer juridico sobre
 * o alcance (PD-10). Comportamento:
 * <ul>
 *   <li>DESLIGADO: retencao informada maior que zero e erro explicito (o arquivo diz
 *       que reteve, mas o tenant nao esta habilitado a tratar retencao - aceitar em
 *       silencio baixaria a conta por valor menor sem registrar o credito);</li>
 *   <li>LIGADO + pagador publico: a baixa e pelo valor bruto; o retido vira credito
 *       do tenant ({@code WithholdingCreditEntity}) e o liquido e bruto - retido;</li>
 *   <li>LIGADO + pagador nao publico: retencao e erro explicito (a IN 1.234/2012
 *       alcanca orgaos e entidades publicas; reter de pagador privado nao tem base
 *       nesta regra).</li>
 * </ul>
 */
public final class WithholdingRule {

    /**
     * @param settledAmount    valor que baixa a conta (o bruto, quando ha retencao valida)
     * @param netReceived      liquido que entra em caixa
     * @param tenantCredit     credito do tenant a registrar (zero quando nao ha retencao)
     */
    public record WithholdingResult(BigDecimal settledAmount, BigDecimal netReceived, BigDecimal tenantCredit) {
    }

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public WithholdingResult apply(boolean withholdingEnabled, boolean payerPublicNature,
                                   BigDecimal grossAmount, BigDecimal withheldAmount) {
        Objects.requireNonNull(grossAmount, "valor bruto e obrigatorio");
        Objects.requireNonNull(withheldAmount, "valor retido e obrigatorio (zero explicito quando nao ha retencao)");
        if (grossAmount.signum() <= 0) {
            throw new IllegalArgumentException("valor bruto deve ser positivo: " + grossAmount);
        }
        if (withheldAmount.signum() < 0 || withheldAmount.compareTo(grossAmount) > 0) {
            throw new IllegalArgumentException("valor retido fora da faixa [0, bruto]: " + withheldAmount);
        }
        if (withheldAmount.signum() == 0) {
            return new WithholdingResult(grossAmount, grossAmount, ZERO);
        }
        if (!withholdingEnabled) {
            throw new IllegalStateException("retencao informada (" + withheldAmount
                    + ") com o recurso desligado (pay.withholding_enabled): habilitar exige o parecer PD-10");
        }
        if (!payerPublicNature) {
            throw new IllegalArgumentException("retencao na fonte (IN RFB 1.234/2012) so se aplica a pagador de natureza publica");
        }
        return new WithholdingResult(grossAmount, grossAmount.subtract(withheldAmount), withheldAmount);
    }
}
