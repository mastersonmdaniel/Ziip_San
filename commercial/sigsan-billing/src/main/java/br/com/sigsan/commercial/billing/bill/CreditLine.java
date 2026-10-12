package br.com.sigsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Crédito realizado na conta.
 *
 * @param installmentNo     número da parcela do mês; {@code null} quando só se está realizando resíduo
 *                          de parcelas já vencidas
 * @param applied           valor abatido da conta
 * @param residualAfter     resíduo que passa ao mês seguinte
 * @param installmentsDoneAfter parcelas cumpridas depois deste mês (para atualizar o crédito)
 */
public record CreditLine(UUID receivableId, Integer installmentNo, BigDecimal applied, BigDecimal residualAfter,
                         int installmentsDoneAfter) {
}
