package br.com.sigsan.commercial.billing.bill;

import java.math.BigDecimal;

/**
 * Decide se uma conta deve ser gerada (relatório 02, seção 5.5, versão nova). Regra pura, sem leitura
 * de dados: o chamador informa os fatos.
 *
 * <pre>
 *   suspenso (situação especial de paralisação cobre a referência)  → NÃO gera, qualquer que seja o resto
 *   semConsumo        = valor de água = 0 E valor de esgoto = 0
 *   situaçãoGera      = NÃO( semConsumo OU (sem água ligada E sem esgoto ligado E fora de condomínio) )
 *   débitosGeram      = há débito sem pagamento e sem revisão
 *                       E ( há crédito a realizar  OU  algum débito está marcado como "gera conta" )
 *   condomínioSemCons = semConsumo E sem água ligada E sem esgoto ligado E dentro de condomínio
 *   gera              = situaçãoGera OU débitosGeram OU condomínioSemCons
 * </pre>
 *
 * <p>Premissa documentada: a especificação não define "sem consumo" na versão nova; aqui é "valor de
 * água e de esgoto iguais a zero", como na versão antiga.
 */
public final class BillGenerationRule {

    /**
     * @param waterValue                valor de água calculado
     * @param sewerValue                valor de esgoto calculado
     * @param waterConnected            há ligação de água na situação de faturamento
     * @param sewerConnected            há ligação de esgoto
     * @param belongsToCondominium      imóvel pertence a condomínio (macro/micro)
     * @param billingSuspended          situação especial paralisa o faturamento na referência
     * @param hasUnpaidDebitNotInReview existe débito a cobrar sem pagamento e fora de revisão
     * @param hasPendingCredit          existe crédito a realizar com valor a conceder
     * @param hasDebitThatGeneratesBill algum dos débitos tem o indicador "gera conta"
     */
    public record Facts(BigDecimal waterValue, BigDecimal sewerValue, boolean waterConnected, boolean sewerConnected,
                        boolean belongsToCondominium, boolean billingSuspended, boolean hasUnpaidDebitNotInReview,
                        boolean hasPendingCredit, boolean hasDebitThatGeneratesBill) {
    }

    private BillGenerationRule() {
    }

    public static boolean shouldGenerate(Facts f) {
        if (f.billingSuspended()) {
            return false;
        }
        boolean noConsumption = f.waterValue().signum() == 0 && f.sewerValue().signum() == 0;
        boolean noConnection = !f.waterConnected() && !f.sewerConnected();
        boolean situationGenerates = !(noConsumption || (noConnection && !f.belongsToCondominium()));
        boolean debitsGenerate = f.hasUnpaidDebitNotInReview()
                && (f.hasPendingCredit() || f.hasDebitThatGeneratesBill());
        boolean condominiumWithoutConsumption = noConsumption && noConnection && f.belongsToCondominium();
        return situationGenerates || debitsGenerate || condominiumWithoutConsumption;
    }
}
