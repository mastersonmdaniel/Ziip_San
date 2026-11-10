package br.com.ziipsan.core.regulatory.agenda;

import br.com.ziipsan.core.regulatory.export.ExportPackage;
import br.com.ziipsan.core.regulatory.export.SubmissionRecord;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-98 (GES-05): um item do calendario de obrigacoes — com responsavel,
 * evidencia, situacao e (via {@code reminderRef}) o lembrete da ED-07.
 *
 * <p>Evidencia de envio (RAS-08): so nasce de um {@link SubmissionRecord}
 * REAL da ED-97, do MESMO tenant, pelo {@link #linkSubmission} — o
 * {@code evidenceRef} gravado e exatamente o {@code evidenceRef()} da
 * ED-97. Item manual (RAS-17) NAO aceita evidencia de envio da ED-97:
 * conclui por {@link #completeManually} com nota do responsavel.
 * Conclusao de item nao manual exige evidencia ligada — situacao DONE
 * sem evidencia e recusada na construcao.</p>
 *
 * <p>Correcao D1 (REVISAO-ED-98): o vinculo confere, alem do tenant,
 * (a) o PERIODO — {@code periodRef} do envio igual ao do item (sonda
 * S1: evidencia de 2025 nao conclui obrigacao de 2027); (b) o ESTADO
 * DE ENVIO REAL — so {@code SUBMITTED_BY_TENANT} (sonda S2: registro
 * apenas {@code GENERATED_VALIDATED} e pacote gerado/validado, sem
 * envio declarado pelo tenant — nao conclui nada); (c) a
 * CORRESPONDENCIA DE TIPO — agencia+perfil do envio contra o tipo da
 * obrigacao ({@link ObligationKind#matchesSubmissionProfile}), de
 * modo que a mesma evidencia nao quita dois tipos distintos (sonda
 * S7; na persistencia, o indice unico parcial sobre
 * {@code evidence_ref} do SQL pendente repete a trava). Por
 * construcao, um {@code evidenceRef} presente tem de ser uma
 * referencia bem-formada da ED-97 cujo periodo embutido e o periodo
 * DESTE item.</p>
 *
 * <p>Correcao R8-ED98-1 (Rodada 8): o vinculo exige ainda o KIND do
 * pacote — so {@code AGGREGATE_PUBLIC} quita a obrigacao; um registro
 * {@code NOMINAL_RESTRICTED} e recusado em {@link #linkSubmission},
 * mesmo com tenant, periodo, estado e perfil correspondentes (a
 * evidencia nominal e restrita e nao cumpre a obrigacao agregada).</p>
 */
public record AgendaItem(UUID id, UUID tenantId, ObligationKind kind, String title,
                         String normCatalogKey, String periodRef,
                         LocalDate dueDate, String dueLabel,
                         UUID responsibleUserId, String evidenceRef,
                         Status status, boolean manual,
                         String occurrenceRef, String actionPlan,
                         String reminderRef, String completionNote) {

    public enum Status { PENDING, EVIDENCE_LINKED, DONE, OVERDUE }

    public AgendaItem {
        Objects.requireNonNull(id, "id do item e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant do item e obrigatorio");
        Objects.requireNonNull(kind, "tipo do item e obrigatorio");
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("item exige titulo");
        Objects.requireNonNull(periodRef, "item exige a referencia do periodo");
        Objects.requireNonNull(responsibleUserId,
                "cada item exige responsavel (aceite GES-05) — nenhum responsavel e inventado");
        Objects.requireNonNull(status, "item exige situacao");
        if (dueDate == null && (dueLabel == null || dueLabel.isBlank()))
            throw new IllegalArgumentException("item sem data de vencimento exige o rotulo [A DEFINIR] do vencimento");
        // D1 por construcao: evidencia presente tem de ser a referencia
        // bem-formada da ED-97 DESTE periodo (formato de evidenceRef()
        // da ED-97: ED97-EVIDENCE:<agencia>:<periodo>:<hash16>).
        if (evidenceRef != null) {
            if (evidenceRef.isBlank())
                throw new IllegalArgumentException("referencia de evidencia nao pode ser vazia (RAS-08)");
            String[] parts = evidenceRef.split(":", -1);
            if (parts.length != 4 || !"ED97-EVIDENCE".equals(parts[0]) || parts[1].isBlank()
                    || !parts[3].matches("[0-9a-f]{16}"))
                throw new IllegalArgumentException("referencia de evidencia fora do formato da ED-97: " + evidenceRef);
            if (!parts[2].equals(periodRef))
                throw new IllegalArgumentException("evidencia de outro periodo recusada na construcao: "
                        + evidenceRef + " (item do periodo " + periodRef + ")");
        }
        if (status == Status.DONE && !manual && evidenceRef == null)
            throw new IllegalArgumentException("item nao manual so conclui com evidencia ligada (RAS-08)");
        if (status == Status.DONE && manual && (completionNote == null || completionNote.isBlank()))
            throw new IllegalArgumentException("item manual so conclui com nota do responsavel (RAS-17)");
        if (kind == ObligationKind.EXTERNAL_INSPECTION_FOLLOWUP) {
            if (occurrenceRef == null || occurrenceRef.isBlank())
                throw new IllegalArgumentException("item de fiscalizacao exige a referencia da ocorrencia da P2.25 (JOR-24)");
            if (actionPlan == null || actionPlan.isBlank())
                throw new IllegalArgumentException("item de fiscalizacao exige plano de acao (JOR-24)");
        }
    }

    /**
     * Liga a evidencia de envio da ED-97 (RAS-08): mesmo tenant,
     * registro real — e, apos a correcao D1, mesmo PERIODO, ENVIO REAL
     * declarado pelo tenant e CORRESPONDENCIA DE TIPO (agencia+perfil
     * do envio x tipo desta obrigacao).
     */
    public AgendaItem linkSubmission(SubmissionRecord submission) {
        Objects.requireNonNull(submission, "registro de envio da ED-97 e obrigatorio");
        if (manual)
            throw new IllegalArgumentException("item manual (RAS-17) nao aceita evidencia de envio da ED-97");
        if (!submission.tenantId().equals(tenantId))
            throw new IllegalArgumentException("evidencia de outro tenant recusada: " + submission.evidenceRef());
        if (!submission.periodRef().equals(periodRef))
            throw new IllegalArgumentException("evidencia de outro periodo recusada: envio do periodo "
                    + submission.periodRef() + " nao quita o item do periodo " + periodRef
                    + " (" + submission.evidenceRef() + ")");
        if (submission.status() != SubmissionRecord.Status.SUBMITTED_BY_TENANT)
            throw new IllegalArgumentException("registro sem envio real nao e evidencia de cumprimento: "
                    + "estado " + submission.status() + " (o envio fisico e do tenant, ED-97/ADR-0004) — "
                    + submission.evidenceRef());
        if (submission.kind() != ExportPackage.Kind.AGGREGATE_PUBLIC)
            throw new IllegalArgumentException("evidencia de pacote nominal restrito recusada: kind "
                    + submission.kind() + " nao quita a obrigacao " + kind + " — so pacote agregado publico "
                    + "e evidencia de cumprimento (R8-ED98-1) (" + submission.evidenceRef() + ")");
        if (!kind.matchesSubmissionProfile(submission.agencyCode(), submission.profileKey()))
            throw new IllegalArgumentException("evidencia sem correspondencia de tipo recusada: perfil "
                    + submission.profileKey() + " (agencia " + submission.agencyCode()
                    + ") nao corresponde a obrigacao " + kind + " — a mesma evidencia nao quita "
                    + "obrigacoes distintas (" + submission.evidenceRef() + ")");
        return new AgendaItem(id, tenantId, kind, title, normCatalogKey, periodRef, dueDate, dueLabel,
                responsibleUserId, submission.evidenceRef(), Status.EVIDENCE_LINKED, manual,
                occurrenceRef, actionPlan, reminderRef, completionNote);
    }

    /** Conclui item com evidencia ligada. */
    public AgendaItem complete() {
        if (manual) throw new IllegalArgumentException("item manual conclui por completeManually (RAS-17)");
        if (evidenceRef == null)
            throw new IllegalArgumentException("conclusao exige evidencia de envio ligada (RAS-08)");
        return withStatus(Status.DONE);
    }

    /** Conclui item manual (RAS-17) com a nota do responsavel. */
    public AgendaItem completeManually(String note) {
        if (!manual) throw new IllegalArgumentException("conclusao manual so vale para o item manual (RAS-17)");
        if (note == null || note.isBlank())
            throw new IllegalArgumentException("conclusao manual exige nota do responsavel (RAS-17)");
        return new AgendaItem(id, tenantId, kind, title, normCatalogKey, periodRef, dueDate, dueLabel,
                responsibleUserId, evidenceRef, Status.DONE, manual,
                occurrenceRef, actionPlan, reminderRef, note);
    }

    /** Registra a referencia do lembrete criado pelos alertas da ED-07. */
    public AgendaItem withReminderRef(String ref) {
        if (ref == null || ref.isBlank())
            throw new IllegalArgumentException("referencia do lembrete (ED-07) nao pode ser vazia");
        return new AgendaItem(id, tenantId, kind, title, normCatalogKey, periodRef, dueDate, dueLabel,
                responsibleUserId, evidenceRef, status, manual,
                occurrenceRef, actionPlan, ref, completionNote);
    }

    public AgendaItem withStatus(Status newStatus) {
        return new AgendaItem(id, tenantId, kind, title, normCatalogKey, periodRef, dueDate, dueLabel,
                responsibleUserId, evidenceRef, newStatus, manual,
                occurrenceRef, actionPlan, reminderRef, completionNote);
    }
}
