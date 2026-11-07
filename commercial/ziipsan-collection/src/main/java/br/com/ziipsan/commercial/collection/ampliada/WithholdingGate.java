package br.com.ziipsan.commercial.collection.ampliada;

import br.com.ziipsan.commercial.collection.retorno.WithholdingRule;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Portao do parecer da retencao na fonte na ED-26 (PD-10 / RAS-27): a baixa com
 * retencao pela regra da P3.1 ({@link WithholdingRule}) so e aplicada DEPOIS do
 * parecer juridico que a condiciona. Sem parecer, NENHUMA retencao e aplicada -
 * nem com o parametro {@code pay.withholding_enabled} ligado: o parametro sozinho
 * nao substitui o parecer.
 *
 * <p>O parecer NAO e inventado nem presumido: {@code legalOpinionIssued} e fato
 * externo (registro do parecer PD-10/RAS-27) que a fiacao entrega; esta regra so
 * o exige. Retencao zero nao precisa de parecer (nao ha retencao a aplicar) e
 * segue a baixa normal da P3.1.
 *
 * <p><b>Separacao de fontes (B5 da revisao cruzada - exigencia da fiacao):</b>
 * {@code legalOpinionIssued} e {@code withholdingEnabled} sao fatos de origens
 * DISTINTAS e nao podem vir da mesma fonte: o primeiro vem EXCLUSIVAMENTE do
 * registro do parecer juridico (PD-10/RAS-27); o segundo, do parametro do
 * tenant {@code pay.withholding_enabled}. A integracao NAO pode alimentar
 * {@code legalOpinionIssued} de parametro do tenant, de configuracao ou de
 * entrada de usuario - se o booleano do parecer nascer da mesma fonte que liga
 * o parametro, este portao cai. Essa separacao e ponto obrigatorio da revisao
 * da fiacao/integracao.
 */
public final class WithholdingGate {

    private final WithholdingRule rule = new WithholdingRule();

    public WithholdingRule.WithholdingResult settle(boolean legalOpinionIssued, boolean withholdingEnabled,
                                                    boolean payerPublicNature, BigDecimal grossAmount,
                                                    BigDecimal withheldAmount) {
        Objects.requireNonNull(withheldAmount, "valor retido e obrigatorio (zero explicito quando nao ha retencao)");
        if (withheldAmount.signum() > 0 && !legalOpinionIssued) {
            throw new IllegalStateException("retencao de " + withheldAmount
                    + " nao aplicada: falta o parecer juridico que condiciona a baixa com retencao"
                    + " (PD-10/RAS-27) - sem parecer, sem retencao");
        }
        return rule.apply(withholdingEnabled, payerPublicNature, grossAmount, withheldAmount);
    }
}
