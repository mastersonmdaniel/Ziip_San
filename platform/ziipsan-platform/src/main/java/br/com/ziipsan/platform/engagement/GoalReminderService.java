package br.com.ziipsan.platform.engagement;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Lembretes da meta pelas notificacoes da ED-41 (aceite da ED-101): o
 * servico decide O QUE lembrar a partir do progresso/relatorio do proprio
 * usuario e despacha pela porta da ED-41, uma unica vez por
 * (unidade, tipo, periodo) — o {@link ReminderPorts.ReminderLog} e a
 * deduplicacao. Sem modulo ligado ou sem adesao ativa, nada e despachado
 * (o servico devolve {@code false} sem lancar: lembrete e efeito
 * secundario, nao a operacao principal de quem chamou).
 */
public final class GoalReminderService {

    private final ReminderPorts.ReminderPort reminderPort;
    private final ReminderPorts.ReminderLog reminderLog;
    private final GoalService goalService;

    public GoalReminderService(ReminderPorts.ReminderPort reminderPort,
                               ReminderPorts.ReminderLog reminderLog,
                               GoalService goalService) {
        this.reminderPort = Objects.requireNonNull(reminderPort, "porta de lembretes e obrigatoria");
        this.reminderLog = Objects.requireNonNull(reminderLog, "registro de lembretes e obrigatorio");
        this.goalService = Objects.requireNonNull(goalService, "servico de metas e obrigatorio");
    }

    private boolean eligible(UUID tenantId, UUID customerId, UUID unitId) {
        try {
            goalService.requireActiveEnrollment(tenantId, customerId, unitId);
            return true;
        } catch (EngagementValidationException e) {
            return false;
        }
    }

    private boolean dispatch(UUID tenantId, UUID customerId, GoalProgress progress,
                             ReminderPorts.ReminderKind kind, Map<String, String> variables) {
        if (!eligible(tenantId, customerId, progress.unitId())) {
            return false;
        }
        if (reminderLog.alreadySent(tenantId, progress.unitId(), kind, progress.period())) {
            return false;
        }
        reminderPort.requestReminder(tenantId, customerId, progress.unitId(), kind,
                progress.period(), variables);
        reminderLog.record(tenantId, progress.unitId(), kind, progress.period());
        return true;
    }

    /** Lembrete do desfecho da meta no periodo (atingida ou excedida). */
    public boolean notifyGoalOutcome(UUID tenantId, UUID customerId, GoalProgress progress) {
        Objects.requireNonNull(progress, "progresso e obrigatorio");
        if (progress.status() == GoalProgress.Status.NO_DATA) {
            return false;
        }
        ReminderPorts.ReminderKind kind = progress.status() == GoalProgress.Status.WITHIN_GOAL
                ? ReminderPorts.ReminderKind.GOAL_ACHIEVED
                : ReminderPorts.ReminderKind.GOAL_EXCEEDED;
        return dispatch(tenantId, customerId, progress, kind, Map.of(
                "period", progress.period().toString(),
                "targetM3", progress.targetM3().toPlainString(),
                "actualM3", progress.actualM3().toPlainString()));
    }

    /** Lembrete de que o relatorio de pares do periodo foi publicado. */
    public boolean notifyPeerReportAvailable(UUID tenantId, UUID customerId, PeerReport report) {
        Objects.requireNonNull(report, "relatorio e obrigatorio");
        if (!report.published()) {
            return false;
        }
        // As variaveis do modelo sao as do relatorio (agregados, sem terceiros).
        if (!eligible(tenantId, customerId, report.unitId())) {
            return false;
        }
        ReminderPorts.ReminderKind kind = ReminderPorts.ReminderKind.PEER_REPORT_AVAILABLE;
        if (reminderLog.alreadySent(tenantId, report.unitId(), kind, report.period())) {
            return false;
        }
        reminderPort.requestReminder(tenantId, customerId, report.unitId(), kind, report.period(), Map.of(
                "period", report.period().toString(),
                "peerAverageM3", report.cohortAverageM3().toPlainString(),
                "position", report.position()));
        reminderLog.record(tenantId, report.unitId(), kind, report.period());
        return true;
    }
}
