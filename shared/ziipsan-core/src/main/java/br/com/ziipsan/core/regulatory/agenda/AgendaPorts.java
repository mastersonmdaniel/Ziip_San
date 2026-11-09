package br.com.ziipsan.core.regulatory.agenda;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ED-98: as portas de integracao da agenda.
 *
 * <ul>
 *   <li>{@link NormCatalogPort} — leitura do catalogo de normas da
 *       P3.15 ({@code RegulatoryLibrary}/{@code NormEntry}, que por sua
 *       vez projeta o catalogo global da P1.34). A projecao
 *       {@link NormRuleView} espelha os campos da {@code NormEntry} real
 *       (catalogKey, title, status, effectiveFrom): a agenda vive no
 *       core e a P3.15 vive na plataforma (que depende do core) — a
 *       fiacao injeta a biblioteca real na borda, sem inverter a
 *       dependencia entre modulos. A data de efeito e a DA NORMA,
 *       anulavel, como na V9_8/P1.34.</li>
 *   <li>{@link ReminderPort} — lembretes pelos alertas da ED-07: o
 *       pedido espelha o {@code TenantAlert} real da ED-07 (tenant,
 *       responsavel, referencia da origem, codigos de titulo/corpo —
 *       codigos do catalogo de mensagens, nunca texto livre, como na
 *       ED-07). A ligacao ao {@code TenantAlertService} real exige o
 *       evento declarado no catalogo de eventos da P1.38/P3.36 —
 *       pendencia P-ED98-03.</li>
 *   <li>{@link OccurrencePort} — ocorrencias da P2.25 (JOR-24): NAO ha
 *       entrega local consumivel da P2.25 (tarefa pre-retomada); a porta
 *       segue o precedente da ED-97 ({@code RegulatorExternalScope}:
 *       nomear a integracao sobre o mecanismo sem inventar o
 *       comportamento alheio). Fiacao = pendencia P-ED98-02.</li>
 * </ul>
 */
public final class AgendaPorts {

    private AgendaPorts() { }

    // ------------------------------------------------------- P3.15/P1.34
    /** Projecao de leitura de uma norma do catalogo (espelha a NormEntry da P3.15). */
    public record NormRuleView(String catalogKey, String title, String status,
                               LocalDate effectiveFrom) {
        public NormRuleView {
            Objects.requireNonNull(catalogKey, "catalogKey da norma e obrigatoria");
            Objects.requireNonNull(title, "titulo da norma e obrigatorio");
            Objects.requireNonNull(status, "situacao da norma e obrigatoria");
            // effectiveFrom ANULAVEL de proposito: norma em vigencia
            // futura sem data permanece VIGENCIA_FUTURA (regra da P1.34).
        }

        /**
         * Casa o token da regra (chave ou titulo)? Correcao D4
         * (REVISAO-ED-98): o casamento dobra ACENTOS e caixa dos dois
         * lados ({@link TextFolding}) — titulo realista acentuado
         * ("Declaração anual de quitação") casa o token QUITACAO
         * (sonda S4 do revisor: sem a dobra, nao casava). Sem
         * casamento, a regra segue para {@code unresolvedKinds} —
         * nada e inventado (risco residual nomeado na P-ED98-06).
         */
        public boolean matchesToken(String token) {
            String t = TextFolding.fold(token);
            return TextFolding.fold(catalogKey).contains(t) || TextFolding.fold(title).contains(t);
        }

        /**
         * Alerta antecipado (P1.34): norma em VIGENCIA_FUTURA com data de
         * efeito na janela (hoje, hoje+dias] — a mesma janela do
         * {@code NormEffectivenessService.upcomingEffective} da P1.34.
         * Sem data, nao ha o que antecipar.
         */
        public boolean isUpcomingAt(LocalDate today, int daysAhead) {
            Objects.requireNonNull(today, "data de referencia e obrigatoria");
            return "VIGENCIA_FUTURA".equals(status) && effectiveFrom != null
                    && effectiveFrom.isAfter(today) && !effectiveFrom.isAfter(today.plusDays(daysAhead));
        }
    }

    /** Leitura do catalogo de normas da P3.15 (a fiacao injeta a RegulatoryLibrary real). */
    public interface NormCatalogPort {
        List<NormRuleView> listNorms();
    }

    // ------------------------------------------------------------- ED-07
    /** Pedido de lembrete (espelha o TenantAlert da ED-07: codigos, nunca texto livre). */
    public record ReminderRequest(UUID tenantId, UUID agendaItemId, UUID responsibleUserId,
                                  String sourceRef, String titleCode, String bodyCode,
                                  LocalDate dueDate) {
        public ReminderRequest {
            Objects.requireNonNull(tenantId, "tenant do lembrete e obrigatorio");
            Objects.requireNonNull(agendaItemId, "item da agenda do lembrete e obrigatorio");
            Objects.requireNonNull(responsibleUserId,
                    "lembrete exige o usuario responsavel (como o TenantAlert da ED-07)");
            if (sourceRef == null || sourceRef.isBlank())
                throw new IllegalArgumentException("lembrete exige a referencia da origem (item da agenda)");
            if (titleCode == null || titleCode.isBlank() || bodyCode == null || bodyCode.isBlank())
                throw new IllegalArgumentException("lembrete exige os codigos de titulo e corpo (convencao da ED-07)");
        }
    }

    /** Lembretes pelos alertas da ED-07 (fiacao ao TenantAlertService: P-ED98-03). */
    public interface ReminderPort {
        /** Devolve a referencia do alerta criado (id/ref da ED-07). */
        String remind(ReminderRequest request);
    }

    // ------------------------------------------------------------ P2.25
    /** Origem da fiscalizacao recebida (aceite, JOR-24). */
    public enum InspectionSource { AGENCY, COURT_OF_ACCOUNTS, ENVIRONMENTAL_BODY }

    /** Pedido de registro de ocorrencia na P2.25 (JOR-24). */
    public record OccurrenceRequest(UUID tenantId, InspectionSource source, String subject,
                                    LocalDate receivedAt, LocalDate deadline, String actionPlan) {
        public OccurrenceRequest {
            Objects.requireNonNull(tenantId, "tenant da ocorrencia e obrigatorio");
            Objects.requireNonNull(source, "origem da fiscalizacao e obrigatoria");
            if (subject == null || subject.isBlank())
                throw new IllegalArgumentException("ocorrencia exige o assunto da fiscalizacao");
            Objects.requireNonNull(receivedAt, "ocorrencia exige a data de recebimento");
            if (actionPlan == null || actionPlan.isBlank())
                throw new IllegalArgumentException("ocorrencia de fiscalizacao exige plano de acao (JOR-24)");
        }
    }

    /** Ocorrencias da P2.25 — porta (sem entrega local consumivel; fiacao: P-ED98-02). */
    public interface OccurrencePort {
        /** Registra a ocorrencia e devolve a referencia dela na P2.25. */
        String registerOccurrence(OccurrenceRequest request);
    }
}
