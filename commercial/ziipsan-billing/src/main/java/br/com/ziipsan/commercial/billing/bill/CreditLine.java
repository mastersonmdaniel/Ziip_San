package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Crédito realizado na conta. A origem é o item do catálogo {@code credit_origins} (DAD-20):
 * a linha grava o id do item e a fotografia do código, como em {@link DebitLine}.
 *
 * @param installmentNo     número da parcela do mês; {@code null} quando só se está realizando resíduo
 *                          de parcelas já vencidas
 * @param applied           valor abatido da conta
 * @param residualAfter     resíduo que passa ao mês seguinte
 * @param installmentsDoneAfter parcelas cumpridas depois deste mês (para atualizar o crédito)
 */
public record CreditLine(UUID receivableId, UUID creditOriginId, String originCode, Integer installmentNo,
                         BigDecimal applied, BigDecimal residualAfter, int installmentsDoneAfter) {
}
