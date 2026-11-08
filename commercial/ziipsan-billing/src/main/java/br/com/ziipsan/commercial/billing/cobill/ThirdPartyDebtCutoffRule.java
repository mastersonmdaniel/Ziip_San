package br.com.ziipsan.commercial.billing.cobill;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Inadimplencia do servico cofaturado SEM efeito de corte de agua
 * (ED-25; parametro {@code cut.third_party_debt_blocks} nomeado no
 * acceptance): a divida de terceiro, sozinha, nao autoriza corte quando
 * o parametro esta desligado - e o desenho esperado da NR 13, porque o
 * usuario nao pode perder a agua por divida de outro servico.
 *
 * <p>Regras (cada uma com teste):
 * <ul>
 *   <li>parametro DESLIGADO: so o devido PROPRIO vencido conta para a
 *       elegibilidade de corte; o de terceiro e ignorado por completo,
 *       qualquer que seja o valor;</li>
 *   <li>parametro LIGADO (decisao expressa do tenant): proprio + terceiro
 *       contam somados;</li>
 *   <li>parametro AUSENTE: erro explicito
 *       ({@link CobillParameters.MissingCobillParameterException}) -
 *       decisao de corte nunca sai sobre parametro presumido
 *       (precedente P3.4).</li>
 * </ul>
 *
 * <p>Esta regra decide apenas QUANTO da divida conta; janela legal,
 * aviso, niveis e demais portoes do corte sao da P3.4/ED-29, que
 * consomem este valor elegivel.
 */
public final class ThirdPartyDebtCutoffRule {

    private ThirdPartyDebtCutoffRule() {
    }

    /** O valor da divida que conta para corte, separados os componentes. */
    public static BigDecimal eligibleDebt(CobillParameters parameters, BigDecimal ownOverdue,
                                          BigDecimal thirdPartyOverdue) {
        Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        requireNonNegative(ownOverdue, "devido proprio vencido");
        requireNonNegative(thirdPartyOverdue, "devido de terceiro vencido");
        if (!parameters.thirdPartyDebtBlocks()) {
            return ownOverdue;
        }
        return ownOverdue.add(thirdPartyOverdue);
    }

    /** Divida SO de terceiro autoriza corte? (a pergunta direta do acceptance) */
    public static boolean thirdPartyOnlyDebtBlocks(CobillParameters parameters, BigDecimal thirdPartyOverdue) {
        return eligibleDebt(parameters, BigDecimal.ZERO, thirdPartyOverdue).signum() > 0;
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " invalido: " + value);
        }
    }
}
