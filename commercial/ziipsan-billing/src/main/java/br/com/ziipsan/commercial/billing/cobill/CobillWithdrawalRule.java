package br.com.ziipsan.commercial.billing.cobill;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Retirada a pedido do usuario (ED-25; NR 13, arts. 46 a 56): o usuario
 * pode pedir a saida do cofaturamento; a partir da competencia de
 * efetivacao, nada mais e cobrado dele por este servico.
 *
 * <p>O que esta regra fixa (e testa): o pedido exige protocolo (a prova
 * do pedido); a efetivacao nao retroage (competencia efetiva nunca
 * anterior a do pedido); cobrado antes da efetivacao permanece devido
 * (a retirada nao apaga debito passado - a inadimplencia dele segue a
 * regra de {@link ThirdPartyDebtCutoffRule}).
 *
 * <p>O que esta regra NAO fixa: prazo legal entre pedido e efetivacao -
 * o texto da NR 13 nao esta nesta VM e nenhum prazo foi inventado; a
 * competencia efetiva e decidida pelo atendimento/contrato e entregue
 * pronta a esta regra, que so a valida.
 */
public final class CobillWithdrawalRule {

    private CobillWithdrawalRule() {
    }

    /** Pedido de retirada registrado (protocolo = prova do pedido). */
    public record WithdrawalRequest(UUID unitId, String protocol, LocalDate requestedOn,
                                    YearMonth effectiveFrom) {
        public WithdrawalRequest {
            Objects.requireNonNull(unitId, "unidade do pedido e obrigatoria");
            if (protocol == null || protocol.isBlank()) {
                throw new IllegalArgumentException("retirada a pedido exige protocolo (prova do pedido)");
            }
            Objects.requireNonNull(requestedOn, "data do pedido e obrigatoria");
            Objects.requireNonNull(effectiveFrom, "competencia de efetivacao e obrigatoria");
            if (effectiveFrom.isBefore(YearMonth.from(requestedOn))) {
                throw new IllegalArgumentException("efetivacao retroativa recusada: pedido em "
                        + requestedOn + ", efetivacao em " + effectiveFrom);
            }
        }
    }

    /** A retirada esta vigente nesta competencia? (efetivacao inclusive). */
    public static boolean withdrawnIn(WithdrawalRequest request, YearMonth competence) {
        if (request == null) {
            return false;
        }
        Objects.requireNonNull(competence, "competencia e obrigatoria");
        return !competence.isBefore(request.effectiveFrom());
    }
}
