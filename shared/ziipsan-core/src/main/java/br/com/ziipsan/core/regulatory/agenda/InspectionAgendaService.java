package br.com.ziipsan.core.regulatory.agenda;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-98 (JOR-24): fiscalizacao recebida — da agencia, do Tribunal de
 * Contas ou do orgao ambiental — registrada como OCORRENCIA da P2.25
 * (pela {@link AgendaPorts.OccurrencePort}; a P2.25 nao tem entrega
 * local consumivel, fiacao = P-ED98-02), com plano de acao e lembrete
 * NESTA agenda (item {@link ObligationKind#EXTERNAL_INSPECTION_FOLLOWUP}
 * com o lembrete disparado pela porta da ED-07).
 */
public final class InspectionAgendaService {

    private final AgendaPorts.OccurrencePort occurrences;
    private final AgendaPorts.ReminderPort reminders;

    public InspectionAgendaService(AgendaPorts.OccurrencePort occurrences,
                                   AgendaPorts.ReminderPort reminders) {
        this.occurrences = Objects.requireNonNull(occurrences, "porta de ocorrencias da P2.25 e obrigatoria");
        this.reminders = Objects.requireNonNull(reminders, "porta de lembretes da ED-07 e obrigatoria");
    }

    /**
     * Registra a fiscalizacao: (1) ocorrencia na P2.25; (2) item nesta
     * agenda com plano de acao, prazo e responsavel; (3) lembrete do
     * item pelos alertas da ED-07. Sem referencia devolvida pela porta
     * da P2.25, NADA e criado — ocorrencia nao se finge.
     */
    public AgendaItem registerInspection(TenantObligationProfile profile,
                                         AgendaPorts.InspectionSource source, String subject,
                                         LocalDate receivedAt, LocalDate deadline, String actionPlan) {
        Objects.requireNonNull(profile, "perfil do tenant e obrigatorio");
        UUID responsible = profile.responsibleFor(ObligationKind.EXTERNAL_INSPECTION_FOLLOWUP);
        if (responsible == null)
            throw new IllegalArgumentException("fiscalizacao sem responsavel no perfil — nenhum responsavel e inventado");
        String occurrenceRef = occurrences.registerOccurrence(new AgendaPorts.OccurrenceRequest(
                profile.tenantId(), source, subject, receivedAt, deadline, actionPlan));
        if (occurrenceRef == null || occurrenceRef.isBlank())
            throw new IllegalStateException("porta da P2.25 nao devolveu referencia de ocorrencia — item nao criado");
        AgendaItem item = new AgendaItem(UUID.randomUUID(), profile.tenantId(),
                ObligationKind.EXTERNAL_INSPECTION_FOLLOWUP,
                "Fiscalizacao " + source.name() + ": " + subject,
                null, receivedAt.toString(), deadline,
                deadline == null ? "[A DEFINIR] prazo da fiscalizacao sem fonte" : null,
                responsible, null, AgendaItem.Status.PENDING, false,
                occurrenceRef, actionPlan, null, null);
        String reminderRef = reminders.remind(new AgendaPorts.ReminderRequest(
                item.tenantId(), item.id(), item.responsibleUserId(),
                "ED98-AGENDA-ITEM:" + item.id(),
                "agenda.inspection.reminder.title", "agenda.inspection.reminder.body",
                item.dueDate()));
        return item.withReminderRef(reminderRef);
    }
}
