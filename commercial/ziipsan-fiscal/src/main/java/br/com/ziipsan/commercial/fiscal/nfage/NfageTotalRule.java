package br.com.ziipsan.commercial.fiscal.nfage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Regra de total do documento da NT 2026.004 (vetor de teste expresso do aceite da
 * P2.6): o total da NFAg-e é o <b>valor líquido</b>; a soma de IBS e CBS dos itens só
 * entra no total do documento para documentos emitidos <b>a partir de 2027</b>:
 *
 * <ul>
 *   <li>emissão até 31/12/2026: total = valor líquido (IBS/CBS destacados, fora do total);</li>
 *   <li>emissão de 01/01/2027 em diante: total = valor líquido + Σ IBS + Σ CBS.</li>
 * </ul>
 *
 * <p>Regra pura com fronteira de data explícita ({@link #INICIO_SOMA_IBS_CBS}). Os
 * valores de IBS/CBS vêm dos itens da conta (grupo por item da V4_7, NT 2026.002) —
 * nenhum valor é inventado aqui. Item sem valor de IBS/CBS declarado é erro explícito
 * quando a regra precisa dele (emissão de 2027 em diante); antes de 2027, item sem o
 * grupo é lido como grupo não aplicável àquela versão de leiaute e contribui zero —
 * leitura registrada aqui porque o grupo é condicional ao MOC (ver V4_7).
 *
 * <p>Esta regra é do <b>documento fiscal</b>. O total da <b>conta</b> continua o da
 * V4_6 (DAD-17, {@code chk_bills_total_with_withholding_tax}), que esta tarefa não toca.
 */
public final class NfageTotalRule {

    /** Fronteira da NT 2026.004: de 01/01/2027 em diante IBS e CBS somam no total. */
    public static final LocalDate INICIO_SOMA_IBS_CBS = LocalDate.of(2027, 1, 1);

    /**
     * Um item do documento para a regra de total.
     *
     * @param valorLiquido valor líquido do item (obrigatório, nunca negativo)
     * @param valorIbs     valor de IBS do item (grupo NT 2026.002); nulo = grupo ausente
     * @param valorCbs     valor de CBS do item (grupo NT 2026.002); nulo = grupo ausente
     */
    public record ItemTotal(BigDecimal valorLiquido, BigDecimal valorIbs, BigDecimal valorCbs) {
    }

    private NfageTotalRule() {
    }

    /** Verdadeiro para emissão na fronteira de 2027 ou depois. */
    public static boolean somaIbsCbsNoTotal(LocalDate dataEmissao) {
        Objects.requireNonNull(dataEmissao, "Data de emissão é obrigatória");
        return !dataEmissao.isBefore(INICIO_SOMA_IBS_CBS);
    }

    /**
     * Total do documento para a data de emissão e os itens dados.
     *
     * @throws IllegalArgumentException se não houver itens, se algum valor líquido for
     *                                  nulo/negativo, ou se — na regra de 2027 — algum
     *                                  item não trouxer valor de IBS ou de CBS (ausência
     *                                  de dado é erro, nunca zero presumido)
     */
    public static BigDecimal totalDocumento(LocalDate dataEmissao, List<ItemTotal> itens) {
        Objects.requireNonNull(dataEmissao, "Data de emissão é obrigatória");
        if (itens == null || itens.isEmpty()) {
            throw new IllegalArgumentException("Documento fiscal sem itens não tem total");
        }
        boolean somaTributos = somaIbsCbsNoTotal(dataEmissao);
        BigDecimal liquido = BigDecimal.ZERO;
        BigDecimal ibs = BigDecimal.ZERO;
        BigDecimal cbs = BigDecimal.ZERO;
        for (ItemTotal item : itens) {
            Objects.requireNonNull(item, "Item do documento é obrigatório");
            if (item.valorLiquido() == null || item.valorLiquido().signum() < 0) {
                throw new IllegalArgumentException("Item com valor líquido nulo ou negativo");
            }
            liquido = liquido.add(item.valorLiquido());
            // Valor de IBS/CBS presente é sempre validado, em qualquer ano
            // (correção da revisão cruzada da P2.6): antes de 2027 o grupo pode
            // estar AUSENTE (condicional ao MOC), mas presente-e-negativo é dado
            // quebrado, nunca um zero silencioso fora do total.
            if ((item.valorIbs() != null && item.valorIbs().signum() < 0)
                    || (item.valorCbs() != null && item.valorCbs().signum() < 0)) {
                throw new IllegalArgumentException("Item com valor de IBS/CBS negativo");
            }
            if (somaTributos) {
                if (item.valorIbs() == null || item.valorCbs() == null) {
                    throw new IllegalArgumentException(
                            "Na regra da NT 2026.004 (2027+), todo item precisa trazer valor de IBS e de CBS");
                }
                ibs = ibs.add(item.valorIbs());
                cbs = cbs.add(item.valorCbs());
            }
        }
        return somaTributos ? liquido.add(ibs).add(cbs) : liquido;
    }
}
