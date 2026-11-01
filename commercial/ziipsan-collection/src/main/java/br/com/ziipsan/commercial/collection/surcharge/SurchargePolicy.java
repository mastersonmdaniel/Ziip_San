package br.com.ziipsan.commercial.collection.surcharge;

/**
 * Parâmetros de acréscimos por impontualidade de um tenant (vigência e norma-fonte ficam na tabela
 * {@code collection_policies}). Nenhum campo tem valor padrão: ausência de regra é declarada
 * ({@link FineRule#none()}, {@link InterestRule#none()}, {@link CorrectionRule#none()}).
 *
 * @param graceBusinessDays          carência em dias úteis após o vencimento sem acréscimo algum
 *                                   (0 = sem carência). Dia útil segue o {@link BusinessCalendar}
 *                                   do município da unidade
 * @param dueDateRollsToBusinessDay  se {@code true}, vencimento em dia não útil é tratado como o
 *                                   próximo dia útil (o consumidor não paga mora por pagar no
 *                                   primeiro dia útil), e todos os prazos contam dele
 * @param fine                       multa moratória
 * @param interest                   juros de mora
 * @param correction                 correção monetária
 */
public record SurchargePolicy(int graceBusinessDays, boolean dueDateRollsToBusinessDay, FineRule fine,
                              InterestRule interest, CorrectionRule correction) {

    public SurchargePolicy {
        if (graceBusinessDays < 0) {
            throw new IllegalArgumentException("A carência não pode ser negativa");
        }
        if (fine == null || interest == null || correction == null) {
            throw new IllegalArgumentException(
                    "Multa, juros e correção são obrigatórios (use none() para declarar ausência)");
        }
    }
}
