package br.com.ziipsan.commercial.registry.legacyxml;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Porta de leitura dos dados importados pela P2.10 (histórico mensal
 * aplicado da V14_2: {@code legacy_import_history}), da qual o PDF das
 * contas antigas sai sob demanda (P2.19).
 *
 * <p>Os campos espelham um a um os do histórico da P2.10
 * ({@code V1HistoryRecord}: unidade pela chave legada, competência,
 * consumo, valor faturado e vencimento — mais a matrícula, que a P2.10
 * conhece pela unidade encenada/aplicada). A integração liga esta porta à
 * leitura JDBC da V14_2; sem ela ligada, não há PDF (a porta é obrigatória
 * no construtor do serviço) — dado de conta antiga nunca é presumido.
 */
public interface LegacyHistoryDataPort {

    /** Uma conta antiga importada: um mês do histórico de uma unidade. */
    record ImportedBill(String unitLegacyKey, String matricula, YearMonth competence,
                        BigDecimal consumptionM3, Long billedAmountCents, LocalDate dueDate) {
    }

    /** Contas importadas da unidade, em qualquer ordem (o PDF ordena por competência). */
    List<ImportedBill> billsOfUnit(UUID tenantId, String unitLegacyKey);
}
