package br.com.ziipsan.core.exit;

import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.DeadlineCountingRule;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Regras puras de prazo e de portao da saida de tenant (P3.16;
 * ADR-0018, ADR-0015, ADR-0025, RNF-42). Sem banco e sem relogio:
 * quem chama passa as datas e o conjunto de feriados do tenant.
 *
 * <p>Prazos do aceite, com a conta conferida a mao (contrato com fim
 * em 2026-12-31, quinta-feira, sem feriados cadastrados):
 * <ul>
 *   <li>exportacao: fim do contrato + 15 dias corridos = 2027-01-15
 *       (nivel STANDARD); no nivel PUBLIC_SECTOR, 10 dias uteis pelo
 *       motor da P1.36 ({@link DeadlineCalculator}, dia da abertura
 *       nunca conta) = 2027-01-14;</li>
 *   <li>consulta somente leitura (so PUBLIC_SECTOR): comeca no fim do
 *       contrato e dura o prazo do contrato (fim - inicio), no maximo
 *       120 dias: contrato de 305 dias -&gt; consulta ate 2027-04-30
 *       (fim + 120); contrato de 60 dias -&gt; ate 2027-03-01
 *       (fim + 60);</li>
 *   <li>carencia: 30 dias. STANDARD: do fim do contrato (ate
 *       2027-01-30). PUBLIC_SECTOR: do fim da consulta;</li>
 *   <li>contestacao: registrada na carencia; resposta do Ziip em ate
 *       15 dias corridos da registrada (o aceite so marca "uteis" na
 *       exportacao — nos demais prazos, dias corridos);</li>
 *   <li>certificado: ate 30 dias apos o maior entre o fim da carencia
 *       e a resposta a ultima contestacao, com teto contado do inicio
 *       da carencia ({@code graceAnchor}): 60 dias sem contestacao e
 *       75 com contestacao. As duas contas fecham exatamente: sem
 *       contestacao, carencia (30) + 30 = 60; contestacao no ultimo
 *       dia da carencia, resposta em +15 e certificado em +30 =
 *       30 + 15 + 30 = 75. No nivel PUBLIC_SECTOR, a ancora e o fim
 *       da consulta (de onde a carencia conta), nao o fim do
 *       contrato — leitura declarada: sem ela, o teto de 75 dias
 *       seria inalcancavel por construcao sempre que houvesse
 *       consulta, e o aceite manda a carencia contar do fim dela.</li>
 * </ul>
 *
 * <p>Alerta da Central da contestacao: o aceite exige alerta "antes
 * do vencimento", sem numero. A antecedencia de
 * {@value #CONTESTATION_ALERT_LEAD_DAYS} dias e convencao desta
 * frente (como a janela 8-18 da P2.15), registrada aqui e pendente de
 * confirmacao do dono — nao e numero do plano nem do aceite.
 */
public final class TenantExitCalculator {

    /** Exportacao no nivel STANDARD: dias corridos apos o fim do contrato. */
    public static final int EXPORT_CALENDAR_DAYS = 15;

    /** Exportacao no nivel PUBLIC_SECTOR: dias uteis apos o fim do contrato. */
    public static final int EXPORT_BUSINESS_DAYS_PUBLIC_SECTOR = 10;

    /** Carencia: dias corridos (do fim do contrato ou do fim da consulta). */
    public static final int GRACE_DAYS = 30;

    /** Consulta somente leitura: teto em dias corridos. */
    public static final int READONLY_MAX_DAYS = 120;

    /** Resposta do Ziip a contestacao: dias corridos da registrada. */
    public static final int CONTESTATION_RESPONSE_DAYS = 15;

    /** Antecedencia do alerta da Central (convencao desta frente, ver Javadoc). */
    public static final int CONTESTATION_ALERT_LEAD_DAYS = 3;

    /** Certificado: dias apos o fim da carencia ou a ultima resposta. */
    public static final int CERTIFICATE_AFTER_DAYS = 30;

    /** Teto do certificado sem contestacao, contado da ancora da carencia. */
    public static final int CERTIFICATE_CAP_WITHOUT_CONTESTATION_DAYS = 60;

    /** Teto do certificado com contestacao, contado da ancora da carencia. */
    public static final int CERTIFICATE_CAP_WITH_CONTESTATION_DAYS = 75;

    /** Papel unico que agenda a eliminacao (aceite; console da P1.6). */
    public static final String ROLE_PLATFORM_OWNER = "PLATFORM_OWNER";

    private TenantExitCalculator() {
    }

    /** Nivel do processo de saida (vocabulario de {@code support.sla_policy.level}, V10_2). */
    public enum Level {
        STANDARD,
        PUBLIC_SECTOR
    }

    /** Recusa de regra da saida, com o motivo em codigo estavel. */
    public static final class ExitRefusedException extends RuntimeException {
        private final String code;

        public ExitRefusedException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    // ------------------------------------------------------------------
    // Exportacao
    // ------------------------------------------------------------------

    /**
     * Vencimento da exportacao. STANDARD: fim do contrato + 15 dias
     * corridos. PUBLIC_SECTOR: 10 dias uteis pelo motor de prazos da
     * P1.36, com os feriados do tenant (nenhum feriado embutido: o que
     * nao esta no conjunto e dia util).
     */
    public static LocalDate exportDueDate(Level level, LocalDate contractEnd, Set<LocalDate> holidays) {
        if (level == null || contractEnd == null) {
            throw new IllegalArgumentException("level e contractEnd sao obrigatorios");
        }
        if (level == Level.PUBLIC_SECTOR) {
            return DeadlineCalculator.computeDueDate(contractEnd, EXPORT_BUSINESS_DAYS_PUBLIC_SECTOR,
                    DeadlineCountingRule.BUSINESS, holidays == null ? Set.of() : holidays);
        }
        return contractEnd.plusDays(EXPORT_CALENDAR_DAYS);
    }

    // ------------------------------------------------------------------
    // Consulta somente leitura (PUBLIC_SECTOR)
    // ------------------------------------------------------------------

    /**
     * Duracao da consulta: o prazo do contrato (fim - inicio, em
     * dias), limitado ao teto de {@value #READONLY_MAX_DAYS} dias.
     * Contrato sem prazo positivo e erro de dado, nao consulta zero.
     */
    public static long readonlyDurationDays(LocalDate contractStart, LocalDate contractEnd) {
        if (contractStart == null || contractEnd == null) {
            throw new IllegalArgumentException("contractStart e contractEnd sao obrigatorios");
        }
        long term = ChronoUnit.DAYS.between(contractStart, contractEnd);
        if (term <= 0) {
            throw new IllegalArgumentException("Prazo do contrato deve ser positivo: "
                    + contractStart + " a " + contractEnd);
        }
        return Math.min(term, READONLY_MAX_DAYS);
    }

    /** Fim da consulta: fim do contrato + duracao (ver {@link #readonlyDurationDays}). */
    public static OffsetDateTime readonlyEndsAt(LocalDate contractStart, OffsetDateTime contractEndAt) {
        if (contractEndAt == null) {
            throw new IllegalArgumentException("contractEndAt e obrigatorio");
        }
        return contractEndAt.plusDays(readonlyDurationDays(contractStart, contractEndAt.toLocalDate()));
    }

    /** Verdadeiro se o instante esta dentro de um periodo de consulta ativo [inicio, fim). */
    public static boolean inReadonlyPeriod(OffsetDateTime startsAt, OffsetDateTime endsAt, OffsetDateTime now) {
        if (startsAt == null || endsAt == null || now == null) {
            throw new IllegalArgumentException("startsAt, endsAt e now sao obrigatorios");
        }
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    /**
     * Portao da consulta: durante o periodo, TODA escrita do tenant e
     * recusada (o aceite exige o teste de escrita recusada). Fora do
     * periodo, este portao nao decide (valem as regras do ciclo de
     * vida vigente).
     */
    public static void requireWriteAllowed(OffsetDateTime readonlyStartsAt,
                                           OffsetDateTime readonlyEndsAt, OffsetDateTime now) {
        if (readonlyStartsAt != null && readonlyEndsAt != null
                && inReadonlyPeriod(readonlyStartsAt, readonlyEndsAt, now)) {
            throw new ExitRefusedException("readonly_consultation",
                    "Periodo de consulta somente leitura pos-contrato: escrita recusada.");
        }
    }

    // ------------------------------------------------------------------
    // Carencia
    // ------------------------------------------------------------------

    /**
     * Inicio da carencia: no nivel STANDARD, o fim do contrato; no
     * PUBLIC_SECTOR, o fim da consulta (a carencia conta do fim da
     * consulta, nao do contrato).
     */
    public static OffsetDateTime graceStartsAt(Level level, OffsetDateTime contractEndAt,
                                               OffsetDateTime readonlyEndsAt) {
        if (level == null || contractEndAt == null) {
            throw new IllegalArgumentException("level e contractEndAt sao obrigatorios");
        }
        if (level == Level.PUBLIC_SECTOR) {
            if (readonlyEndsAt == null) {
                throw new IllegalArgumentException(
                        "Nivel PUBLIC_SECTOR exige o fim da consulta para contar a carencia");
            }
            return readonlyEndsAt;
        }
        return contractEndAt;
    }

    /** Fim da carencia: inicio + {@value #GRACE_DAYS} dias. */
    public static OffsetDateTime graceEndsAt(OffsetDateTime graceStartsAt) {
        if (graceStartsAt == null) {
            throw new IllegalArgumentException("graceStartsAt e obrigatorio");
        }
        return graceStartsAt.plusDays(GRACE_DAYS);
    }

    /** Verdadeiro se o instante esta na carencia [inicio, fim]. */
    public static boolean inGrace(OffsetDateTime graceStartsAt, OffsetDateTime graceEndsAt,
                                  OffsetDateTime now) {
        if (graceStartsAt == null || graceEndsAt == null || now == null) {
            throw new IllegalArgumentException("graceStartsAt, graceEndsAt e now sao obrigatorios");
        }
        return !now.isBefore(graceStartsAt) && !now.isAfter(graceEndsAt);
    }

    // ------------------------------------------------------------------
    // Agendamento, cancelamento e execucao
    // ------------------------------------------------------------------

    /**
     * Agendamento da eliminacao: so {@code PLATFORM_OWNER} e so com
     * MFA (step-up) no ato. Qualquer outro papel — inclusive
     * PLATFORM_FINANCE, que na P1.6 recebe 403 ao pedir eliminacao —
     * ou a ausencia de MFA recusa.
     */
    public static void requireSchedulingAllowed(String actorRole, boolean mfaVerified) {
        if (!ROLE_PLATFORM_OWNER.equals(actorRole)) {
            throw new ExitRefusedException("forbidden_role",
                    "Eliminacao so pode ser agendada por PLATFORM_OWNER; papel recebido: " + actorRole);
        }
        if (!mfaVerified) {
            throw new ExitRefusedException("mfa_required",
                    "Agendamento da eliminacao exige MFA (step-up) no ato.");
        }
    }

    /** Cancelamento do agendamento: so durante a carencia [inicio, fim]. */
    public static void requireCancellationAllowed(OffsetDateTime graceStartsAt,
                                                  OffsetDateTime graceEndsAt, OffsetDateTime now) {
        if (graceStartsAt == null || graceEndsAt == null || now == null) {
            throw new IllegalArgumentException("graceStartsAt, graceEndsAt e now sao obrigatorios");
        }
        if (now.isBefore(graceStartsAt)) {
            throw new ExitRefusedException("grace_not_started",
                    "Cancelamento so durante a carencia; carencia comeca em " + graceStartsAt);
        }
        if (now.isAfter(graceEndsAt)) {
            throw new ExitRefusedException("grace_elapsed",
                    "Cancelamento so durante a carencia; carencia encerrada em " + graceEndsAt);
        }
    }

    /**
     * Estado exigido para cancelar ou executar: a eliminacao precisa
     * estar AGENDADA (por {@code PLATFORM_OWNER} com MFA). Sem esta
     * guarda, o cancelamento nao teria efeito (uma execucao posterior
     * ignoraria o status {@code CANCELLED}) e a execucao direta
     * furaria o portao de papel/MFA do agendamento.
     */
    public static void requireEliminationScheduled(String status) {
        if (!"ELIMINATION_SCHEDULED".equals(status)) {
            throw new ExitRefusedException("elimination_not_scheduled",
                    "Eliminacao nao esta agendada (status atual: " + status + ").");
        }
    }

    /** Agendar de novo um tenant ja eliminado e recusado: ELIMINATED e terminal. */
    public static void requireSchedulingStateAllowed(String currentStatus) {
        if ("ELIMINATED".equals(currentStatus)) {
            throw new ExitRefusedException("already_eliminated",
                    "Tenant ja eliminado, com certificado emitido: novo agendamento recusado.");
        }
    }

    /** Suspensoes vigentes da execucao (o estado que o servico le das tabelas da V10_5). */
    public record Holds(boolean contestationOpen, boolean courtOrderActive) {
        public boolean any() {
            return contestationOpen || courtOrderActive;
        }
    }

    /**
     * Portao da execucao: antes do fim da carencia, recusa; com
     * contestacao sem resposta ou ordem judicial vigente, recusa. E o
     * teste "execucao antes do fim da carencia recusada" e os testes
     * de suspensao do aceite, como predicado puro.
     */
    public static void requireExecutionAllowed(OffsetDateTime graceEndsAt, OffsetDateTime now, Holds holds) {
        if (graceEndsAt == null || now == null || holds == null) {
            throw new IllegalArgumentException("graceEndsAt, now e holds sao obrigatorios");
        }
        if (!now.isAfter(graceEndsAt)) {
            throw new ExitRefusedException("grace_not_elapsed",
                    "Execucao recusada antes do fim da carencia (" + graceEndsAt + ").");
        }
        if (holds.contestationOpen()) {
            throw new ExitRefusedException("held_by_contestation",
                    "Execucao suspensa: contestacao registrada na carencia sem resposta do Ziip.");
        }
        if (holds.courtOrderActive()) {
            throw new ExitRefusedException("held_by_court_order",
                    "Execucao suspensa: ordem judicial vigente.");
        }
    }

    // ------------------------------------------------------------------
    // Contestacao
    // ------------------------------------------------------------------

    /** Contestacao so vale se registrada na carencia (inicio e fim contam). */
    public static void requireContestationInGrace(OffsetDateTime graceStartsAt,
                                                  OffsetDateTime graceEndsAt,
                                                  OffsetDateTime registeredAt) {
        if (!inGrace(graceStartsAt, graceEndsAt, registeredAt)) {
            throw new ExitRefusedException("contestation_outside_grace",
                    "Contestacao so pode ser registrada durante a carencia.");
        }
    }

    /** Resposta do Ziip: registrada + {@value #CONTESTATION_RESPONSE_DAYS} dias. */
    public static OffsetDateTime contestationResponseDueAt(OffsetDateTime registeredAt) {
        if (registeredAt == null) {
            throw new IllegalArgumentException("registeredAt e obrigatorio");
        }
        return registeredAt.plusDays(CONTESTATION_RESPONSE_DAYS);
    }

    /** Instante em que o alerta da Central deve sair: vencimento - antecedencia. */
    public static OffsetDateTime contestationAlertAt(OffsetDateTime responseDueAt) {
        if (responseDueAt == null) {
            throw new IllegalArgumentException("responseDueAt e obrigatorio");
        }
        return responseDueAt.minusDays(CONTESTATION_ALERT_LEAD_DAYS);
    }

    /** Alerta devido: agora >= instante do alerta e a resposta ainda nao saiu. */
    public static boolean contestationAlertDue(OffsetDateTime now, OffsetDateTime responseDueAt,
                                               boolean responded) {
        if (now == null || responseDueAt == null) {
            throw new IllegalArgumentException("now e responseDueAt sao obrigatorios");
        }
        return !responded && !now.isBefore(contestationAlertAt(responseDueAt));
    }

    /** Resposta vencida: passou do vencimento sem resposta (a Central escala). */
    public static boolean contestationOverdue(OffsetDateTime now, OffsetDateTime responseDueAt,
                                              boolean responded) {
        if (now == null || responseDueAt == null) {
            throw new IllegalArgumentException("now e responseDueAt sao obrigatorios");
        }
        return !responded && now.isAfter(responseDueAt);
    }

    // ------------------------------------------------------------------
    // Certificado
    // ------------------------------------------------------------------

    /**
     * Vencimento do certificado: 30 dias apos o maior entre o fim da
     * carencia e a resposta a ultima contestacao, limitado ao teto da
     * ancora da carencia (60 dias sem contestacao; 75 com). Resposta
     * atrasada alem do prazo de 15 dias faz o teto apertar — o
     * certificado nao desliza com o atraso.
     */
    public static OffsetDateTime certificateDueAt(OffsetDateTime graceAnchor,
                                                  OffsetDateTime graceEndsAt,
                                                  OffsetDateTime lastContestationResponseAt) {
        if (graceAnchor == null || graceEndsAt == null) {
            throw new IllegalArgumentException("graceAnchor e graceEndsAt sao obrigatorios");
        }
        OffsetDateTime base = graceEndsAt;
        boolean contested = lastContestationResponseAt != null;
        if (contested && lastContestationResponseAt.isAfter(base)) {
            base = lastContestationResponseAt;
        }
        OffsetDateTime byRule = base.plusDays(CERTIFICATE_AFTER_DAYS);
        long capDays = contested ? CERTIFICATE_CAP_WITH_CONTESTATION_DAYS
                : CERTIFICATE_CAP_WITHOUT_CONTESTATION_DAYS;
        OffsetDateTime cap = graceAnchor.plusDays(capDays);
        return byRule.isAfter(cap) ? cap : byRule;
    }
}
