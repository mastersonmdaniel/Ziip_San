package br.com.ziipsan.commercial.billing.social;

import java.time.LocalDate;

/**
 * Ciclo de vida da inscricao de tarifa social (P1.5; Lei 14.898/2024, art. 3, paragrafo
 * unico; relatorio 08, R-14). Prazos minimos, todos "so ampliam" (catalogo):
 * <ul>
 *   <li>perda do beneficio so depois de aviso na fatura por no minimo 3 meses
 *       ({@code social.loss_notice_months}, 3): a situacao LOSS_NOTICE comeca na data do
 *       aviso e o fim so vale a partir de aviso + 3 meses;</li>
 *   <li>permanencia apos deixar de ser elegivel por no minimo 3 meses
 *       ({@code social.grace_months}, 3; GRACE_3M do plano R3): a situacao GRACE comeca
 *       quando a elegibilidade cessa e o fim so vale a partir de graca + 3 meses.</li>
 * </ul>
 * <p>Esta classe so valida datas; a transicao de situacao em si (e o texto do aviso, que
 * sai pelo motor de mensagens legais da P2.32) pertence ao servico de cadastro, fora
 * deste modulo de dominio puro. Nenhum metodo aqui recebe situacao de pagamento.
 */
public final class SocialEnrollmentLifecycle {

    private SocialEnrollmentLifecycle() {
    }

    /**
     * O fim apos aviso de perda so vale se {@code endDate} for, no minimo,
     * {@code lossNoticeStartedOn + lossNoticeMonths}.
     */
    public static boolean lossNoticePeriodSatisfied(LocalDate lossNoticeStartedOn,
            LocalDate endDate, int lossNoticeMonths) {
        if (lossNoticeStartedOn == null || endDate == null) {
            throw new IllegalArgumentException("Datas de aviso e de fim sao obrigatorias");
        }
        if (lossNoticeMonths < 0) {
            throw new IllegalArgumentException("Prazo de aviso nao pode ser negativo");
        }
        return !endDate.isBefore(lossNoticeStartedOn.plusMonths(lossNoticeMonths));
    }

    /** Ultimo dia de graca: {@code graceStartedOn + graceMonths} (o fim vale a partir dele). */
    public static LocalDate graceDeadline(LocalDate graceStartedOn, int graceMonths) {
        if (graceStartedOn == null) {
            throw new IllegalArgumentException("Data de inicio da graca e obrigatoria");
        }
        if (graceMonths < 0) {
            throw new IllegalArgumentException("Prazo de graca nao pode ser negativo");
        }
        return graceStartedOn.plusMonths(graceMonths);
    }

    /** O fim apos a graca so vale se {@code endDate} nao for anterior ao fim da graca. */
    public static boolean gracePeriodSatisfied(LocalDate graceStartedOn, LocalDate endDate, int graceMonths) {
        return !endDate.isBefore(graceDeadline(graceStartedOn, graceMonths));
    }
}
