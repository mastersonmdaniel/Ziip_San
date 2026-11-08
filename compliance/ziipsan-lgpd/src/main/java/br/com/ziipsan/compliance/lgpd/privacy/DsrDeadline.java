package br.com.ziipsan.compliance.lgpd.privacy;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Prazo do pedido do titular: 15 dias para a declaração completa — LGPD, art. 19, II.
 *
 * <p>É a CORREÇÃO DE REFERÊNCIA que o aceite da P3.40 pede: o prazo não é do art. 18
 * (que lista os direitos, ver {@link DataSubjectRight}); quem fixa os 15 dias é o
 * art. 19, II. A {@code V23_1} grava essa referência em comentário na coluna
 * {@code lgpd_data_subject_request.deadline_at}; referências antigas a "art. 18" como
 * fonte do prazo, onde existirem no código do módulo, são pendência nomeada do relatório.
 *
 * <p>A contagem é a do motor de prazos da P1.36 ({@code service_protocols}: datas de
 * abertura, prazo original e prazo corrente, com prorrogação como evento sem apagar o
 * original). Esta classe é a régua pura dos 15 dias sobre a data de abertura; o motor da
 * P1.36 continua dono da contagem no protocolo (dias corridos ou úteis por
 * {@code sac.deadline_counting_rule}) e é ele que grava {@code original_due_date} /
 * {@code current_due_date} — divergência entre os dois é defeito de integração, não
 * duas regras: 15 dias corridos a partir da abertura.
 *
 * <p>O alerta antes do vencimento usa uma janela INFORMADA por quem chama
 * ({@code alertWindowDays}): o plano não fixa o número de dias do alerta, e fixar um
 * aqui seria inventar política. Janela ausente, zerada ou maior/igual ao prazo é
 * recusada — alerta que nasce vencido ou que cobre o prazo inteiro não alerta nada.
 */
public record DsrDeadline(LocalDate openedDate, LocalDate dueDate) {

    /** Prazo da declaração completa (LGPD, art. 19, II). */
    public static final int FULL_DECLARATION_DAYS = 15;

    public DsrDeadline {
        Objects.requireNonNull(openedDate, "openedDate é obrigatória (abertura do protocolo)");
        Objects.requireNonNull(dueDate, "dueDate é obrigatória");
        if (!dueDate.equals(openedDate.plusDays(FULL_DECLARATION_DAYS))) {
            throw new IllegalArgumentException("dueDate deve ser a abertura + "
                    + FULL_DECLARATION_DAYS + " dias (LGPD, art. 19, II)");
        }
    }

    /** Prazo a partir da abertura do protocolo da P1.36. */
    public static DsrDeadline fromOpening(LocalDate openedDate) {
        Objects.requireNonNull(openedDate, "openedDate é obrigatória (abertura do protocolo)");
        return new DsrDeadline(openedDate, openedDate.plusDays(FULL_DECLARATION_DAYS));
    }

    /** Vencido: hoje estritamente depois do prazo e o pedido ainda não concluído. */
    public boolean isOverdue(LocalDate today, boolean completed) {
        Objects.requireNonNull(today, "today é obrigatório");
        return !completed && today.isAfter(dueDate);
    }

    /**
     * Alerta antes do vencimento: dentro da janela que antecede o prazo e ainda não
     * vencido nem concluído. No dia do prazo o pedido está "vencendo hoje" — o alerta
     * cobre esse dia (quem só descobre no dia seguinte descobriu tarde).
     */
    public boolean isAlerting(LocalDate today, int alertWindowDays, boolean completed) {
        Objects.requireNonNull(today, "today é obrigatório");
        if (alertWindowDays <= 0 || alertWindowDays >= FULL_DECLARATION_DAYS) {
            throw new IllegalArgumentException("alertWindowDays deve estar entre 1 e "
                    + (FULL_DECLARATION_DAYS - 1) + ": o plano não fixa a janela e uma janela "
                    + "fora dessa faixa não alerta antes do vencimento");
        }
        if (completed || today.isAfter(dueDate)) {
            return false;
        }
        return !today.isBefore(dueDate.minusDays(alertWindowDays));
    }
}
