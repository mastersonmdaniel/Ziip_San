package br.com.ziipsan.commercial.fiscal.nfage;

import br.com.ziipsan.commercial.billing.composition.BillChargeLine;
import br.com.ziipsan.commercial.billing.composition.BillComposition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Discriminacao fiscal do cofaturamento (Camada 3; desenho da Camada 0,
 * secao 3.1 item 6; INV-C3-08): o total PROPRIO da conta para a NFAg-e,
 * excluindo as linhas de terceiro — valor de terceiro NAO e receita
 * fiscal propria do prestador de saneamento (e valor em transito, no
 * desenho da segregacao da ED-25/ED-32).
 *
 * <p>Componente PURO e novo: recebe a composicao da conta na forma da
 * Camada 2 ({@link BillComposition}/{@link BillChargeLine}, por
 * referencia — nenhuma forma nova e inventada) e devolve
 * {@code total - linhas THIRD_PARTY}. Fronteiras (INV-C3-08):
 * <ul>
 *   <li>conta sem linha de terceiro: exclusao = IDENTIDADE (devolve o
 *       total da composicao);</li>
 *   <li>terceiro == total: devolve ZERO;</li>
 *   <li>o {@code thirdPartyTotal} declarado na composicao tem de ser a
 *       soma das linhas de terceiro dela — divergencia e composicao
 *       quebrada: falha fechada, nunca um total fiscal presumido;</li>
 *   <li>resultado negativo (terceiro maior que o total) e recusado.</li>
 * </ul>
 *
 * <p>{@link NfageEmissionService} e {@link NfageTotalRule} (P2.6) NAO
 * sao editados: a fiacao — compor esta exclusao ANTES da emissao, na
 * camada de aplicacao, alimentando os itens da NFAg-e so com o proprio
 * — e da INTEGRACAO.
 */
public final class NfageThirdPartyExclusion {

    private NfageThirdPartyExclusion() {
    }

    /** Soma das linhas de terceiro de uma lista de linhas da composicao. */
    public static BigDecimal thirdPartyTotal(List<BillChargeLine> lines) {
        Objects.requireNonNull(lines, "linhas da composicao sao obrigatorias");
        BigDecimal sum = BigDecimal.ZERO;
        for (BillChargeLine line : lines) {
            Objects.requireNonNull(line, "linha da composicao e obrigatoria");
            if (line.isThirdParty()) {
                sum = sum.add(line.amount());
            }
        }
        return sum;
    }

    /**
     * Total proprio de uma composicao da Camada 2:
     * {@code composition.total() - linhas THIRD_PARTY} (INV-C3-08).
     */
    public static BigDecimal ownTotal(BillComposition composition) {
        Objects.requireNonNull(composition, "composicao da conta e obrigatoria");
        BigDecimal fromLines = thirdPartyTotal(composition.lines());
        if (fromLines.compareTo(composition.thirdPartyTotal()) != 0) {
            throw new IllegalStateException("composicao inconsistente: thirdPartyTotal declarado ("
                    + composition.thirdPartyTotal() + ") diverge da soma das linhas de terceiro ("
                    + fromLines + ") — total fiscal nao e presumido");
        }
        return ownTotal(composition.total(), composition.lines());
    }

    /**
     * Total proprio a partir do total da conta e das suas linhas:
     * {@code total - linhas THIRD_PARTY} (INV-C3-08).
     */
    public static BigDecimal ownTotal(BigDecimal billTotal, List<BillChargeLine> lines) {
        Objects.requireNonNull(billTotal, "total da conta e obrigatorio");
        if (billTotal.signum() < 0) {
            throw new IllegalArgumentException("total da conta negativo: " + billTotal);
        }
        BigDecimal own = billTotal.subtract(thirdPartyTotal(lines));
        if (own.signum() < 0) {
            throw new IllegalArgumentException("linhas de terceiro (" + thirdPartyTotal(lines)
                    + ") maiores que o total da conta (" + billTotal + ")");
        }
        return own;
    }
}
