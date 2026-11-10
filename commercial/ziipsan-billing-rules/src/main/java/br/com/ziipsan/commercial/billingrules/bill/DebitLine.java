package br.com.ziipsan.commercial.billingrules.bill;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parcela de débito cobrada na conta. O tipo é o item do catálogo {@code debit_types} (DAD-20):
 * a linha grava o id do item e a fotografia do código, como a conta fotografa a tarifa — uma
 * troca posterior de código no catálogo não reescreve contas já emitidas.
 */
public record DebitLine(UUID receivableId, UUID debitTypeId, String debitTypeCode, int installmentNo,
                        int installmentTotal, BigDecimal amount) {
}
