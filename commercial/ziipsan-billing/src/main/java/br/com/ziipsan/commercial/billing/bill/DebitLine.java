package br.com.ziipsan.commercial.billing.bill;

import java.math.BigDecimal;
import java.util.UUID;

/** Parcela de débito cobrada na conta. */
public record DebitLine(UUID receivableId, String typeCode, int installmentNo, int installmentTotal,
                        BigDecimal amount) {
}
