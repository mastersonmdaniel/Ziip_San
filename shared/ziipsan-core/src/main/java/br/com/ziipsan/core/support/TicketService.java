package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Chamados de suporte v0 (P1.23; ADR-0020; AJUDA-SUPORTE-SOBRE §4–§6).
 *
 * <p>O único escritor do estado do chamado, para um tenant: numeração
 * {@code SUP-AAAA-NNNNNN}, conversa, estados, eventos e registros de anexo.
 * Toda operação roda numa transação com o contexto RLS do tenant
 * ({@code app.current_tenant}), como o motor de OS: fora do tenant, a linha
 * não existe — a API devolve 404, nunca 403, para número de outro tenant.
 *
 * <p>Limites deliberados desta frente (não omissões silenciosas):
 * <ul>
 *   <li>Termo: "chamado de suporte" (pedido do tenant ao Ziip). O "protocolo
 *       de atendimento" (pedido do consumidor ao tenant, NR 11) é a P1.36,
 *       com outras tabelas e outra numeração — nunca se misturam;</li>
 *   <li>Estados: {@code NEW → IN_PROGRESS → WAITING_CUSTOMER → RESOLVED →
 *       CLOSED}, mais {@code CANCELLED} só a partir de {@code NEW}, pelo
 *       solicitante, antes do atendimento. Mensagem do solicitante num
 *       chamado {@code RESOLVED} reabre para {@code IN_PROGRESS} (AJUDA §5);
 *       {@code CLOSED} é terminal — fecha-se outro chamado vinculado;</li>
 *   <li>SLA (prazos, alerta de 80%, estouro) e CSAT são P2.15: aqui não há
 *       prazo calculado nem nota; {@code waitingSince} só registra a entrada
 *       em {@code WAITING_CUSTOMER} para o cálculo futuro;</li>
 *   <li>Transições automáticas por tempo (7 dias em WAITING_CUSTOMER ou em
 *       RESOLVED) não existem sem job agendado: a mudança é feita pela API
 *       da Central; o job é pendência declarada, não comportamento fingido;</li>
 *   <li>Nota interna ({@code INTERNAL}) só existe para autor AGENT/SYSTEM e
 *       nunca é devolvida à API do tenant (o filtro é no SQL, não no
 *       controlador);</li>
 *   <li>Dado pessoal: assunto ou corpo com CPF, CNPJ, e-mail ou telefone
 *       MARCA {@code contains_personal_data} (alerta, não recusa — ADR-0020)
 *       e as listas devolvem o texto mascarado; o detalhe mostra o texto
 *       integral só a quem pode ver o chamado;</li>
 *   <li>Anexos: este serviço só REGISTRA o anexo e muda o seu estado de
 *       varredura. Bytes, detecção de MIME e varredura vivem no
 *       {@code TicketAttachmentService} da plataforma (pipeline da P1.20);
 *       download só é servido com {@code CLEAN}.</li>
 * </ul>
 */
public final class TicketService {

    /** Categorias do chamado (AJUDA §4, em inglês pelo plano §8). */
    public static final Set<String> CATEGORIES = Set.of(
            "QUESTION", "INCIDENT", "REQUEST", "IMPROVEMENT", "SECURITY", "PRIVACY");
    /** Severidades (ADR-0025). */
    public static final Set<String> SEVERITIES = Set.of("S1", "S2", "S3", "S4", "S5");
    /** Estados do chamado. */
    public static final Set<String> STATUSES = Set.of(
            "NEW", "IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CLOSED", "CANCELLED");
    /** Níveis de atendimento (N1, N2, engenharia). */
    public static final Set<String> LEVELS = Set.of("N1", "N2", "ENGINEERING");
    /** Estados de varredura do anexo (P1.20). */
    public static final Set<String> SCAN_STATUSES = Set.of("QUARANTINED", "CLEAN", "INFECTED", "NOT_SCANNED");
    /** Tipos de MIME aceitos em anexo (AJUDA §11, detectados pelo conteúdo). */
    public static final Set<String> ALLOWED_MIMES = Set.of(
            "image/png", "image/jpeg", "image/webp", "application/pdf",
            "text/plain", "text/csv", "application/xml");
    /** Limites de anexo (AJUDA §11). */
    public static final int MAX_ATTACHMENTS_PER_MESSAGE = 5;
    public static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;

    private static final Pattern CPF_LIKE = Pattern.compile("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    private static final Pattern CNPJ_LIKE = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern EMAIL_LIKE = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_LIKE = Pattern.compile("\\(?\\d{2}\\)?\\s?9?\\d{4}-\\d{4}");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    /** Transições de estado permitidas ao agente (Central); o solicitante só cancela de NEW. */
    private static final java.util.Map<String, Set<String>> TRANSITIONS = java.util.Map.of(
            "NEW", Set.of("IN_PROGRESS", "WAITING_CUSTOMER", "RESOLVED", "CANCELLED"),
            "IN_PROGRESS", Set.of("WAITING_CUSTOMER", "RESOLVED", "CANCELLED"),
            "WAITING_CUSTOMER", Set.of("IN_PROGRESS", "RESOLVED"),
            "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
            "CLOSED", Set.of(),
            "CANCELLED", Set.of());

    public record Ticket(UUID id, String number, UUID requesterId, String subject, String category,
                         String module, String originRoute, String appVersion, String correlationId,
                         String problemCode, String severity, String status, String level,
                         UUID assigneeId, boolean containsPersonalData, int reopenCount,
                         OffsetDateTime waitingSince, OffsetDateTime resolvedAt,
                         OffsetDateTime closedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }

    public record TicketMessage(UUID id, UUID ticketId, String authorType, UUID authorId,
                                String visibility, String body, boolean containsPersonalData,
                                OffsetDateTime createdAt) {
    }

    public record TicketAttachment(UUID id, UUID ticketId, UUID messageId, String fileName,
                                   String detectedMime, long sizeBytes, String sha256,
                                   String objectKey, String scanStatus, String threat,
                                   OffsetDateTime createdAt) {
    }

    public record TicketEvent(UUID id, UUID ticketId, String eventType, String actorType,
                              UUID actorId, String fromStatus, String toStatus, String detail,
                              OffsetDateTime createdAt) {
    }

    /** Entrada de criação: assunto e mensagem inicial são obrigatórios (@NotBlank no controlador). */
    public record CreateInput(String subject, String category, String module, String originRoute,
                              String appVersion, String correlationId, String problemCode,
                              String severity, String initialMessage) {
    }

    private final DataSource dataSource;
    private final UUID tenantId;
    private final Clock clock;

    public TicketService(DataSource dataSource, UUID tenantId, Clock clock) {
        if (dataSource == null || tenantId == null || clock == null) {
            throw new IllegalArgumentException("dataSource, tenantId e clock sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    /** Tenant ao qual esta instância está vinculada (compõe a chave do objeto do anexo). */
    public UUID tenantId() {
        return tenantId;
    }

    // ------------------------------------------------------------------
    // Criação e leitura (lado do tenant)
    // ------------------------------------------------------------------

    /** Cria o chamado com a mensagem inicial, numa transação; devolve o chamado. */
    public Ticket create(UUID requesterId, CreateInput in) {
        requireRequester(requesterId);
        String subject = requireText(in.subject(), "subject", 200);
        String body = requireText(in.initialMessage(), "initialMessage", 10000);
        String category = in.category() == null ? "QUESTION" : in.category();
        if (!CATEGORIES.contains(category)) {
            throw ApiException.Validation.of(new ApiException.FieldError("category", "invalid", "Categoria desconhecida."));
        }
        String severity = in.severity() == null ? "S4" : in.severity();
        if (!SEVERITIES.contains(severity)) {
            throw ApiException.Validation.of(new ApiException.FieldError("severity", "invalid", "Severidade desconhecida."));
        }
        return inTx(connection -> {
            String number = nextNumber(connection);
            UUID ticketId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.ticket (tenant_id, number, requester_id, subject, category, module,"
                            + " origin_route, app_version, correlation_id, problem_code, severity,"
                            + " contains_personal_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
                ps.setObject(1, tenantId);
                ps.setString(2, number);
                ps.setObject(3, requesterId);
                ps.setString(4, subject);
                ps.setString(5, category);
                setText(ps, 6, in.module());
                setText(ps, 7, in.originRoute());
                setText(ps, 8, in.appVersion());
                setText(ps, 9, in.correlationId());
                setText(ps, 10, in.problemCode());
                ps.setString(11, severity);
                ps.setBoolean(12, containsPersonalData(subject) || containsPersonalData(body));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ticketId = (UUID) rs.getObject(1);
                }
            }
            insertMessage(connection, ticketId, "REQUESTER", requesterId, "PUBLIC", body);
            insertEvent(connection, ticketId, "CREATED", "REQUESTER", requesterId, null, "NEW", null);
            return findById(connection, ticketId);
        });
    }

    /** Lista do tenant: com {@code canSeeAll}, todos; sem, só os do solicitante. Assunto mascarado. */
    public List<Ticket> list(UUID requesterId, boolean canSeeAll, String statusFilter) {
        requireRequester(requesterId);
        if (statusFilter != null && !STATUSES.contains(statusFilter)) {
            throw ApiException.Validation.of(new ApiException.FieldError("status", "invalid", "Estado desconhecido."));
        }
        return inTx(connection -> {
            String sql = "SELECT id, number, requester_id, subject, category, module, origin_route,"
                    + " app_version, correlation_id, problem_code, severity, status, level, assignee_id,"
                    + " contains_personal_data, reopen_count, waiting_since, resolved_at, closed_at,"
                    + " created_at, updated_at FROM support.ticket WHERE tenant_id = ?"
                    + (canSeeAll ? "" : " AND requester_id = ?")
                    + (statusFilter == null ? "" : " AND status = ?")
                    + " ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, tenantId);
                if (!canSeeAll) {
                    ps.setObject(i++, requesterId);
                }
                if (statusFilter != null) {
                    ps.setString(i++, statusFilter);
                }
                List<Ticket> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mask(readTicket(rs)));
                    }
                }
                return out;
            }
        });
    }

    /** Detalhe do chamado; nota interna nunca sai daqui. 404 quando não é do tenant nem do solicitante. */
    public TicketDetail get(String number, UUID requesterId, boolean canSeeAll) {
        requireRequester(requesterId);
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null || (!canSeeAll && !ticket.requesterId().equals(requesterId))) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            List<TicketMessage> messages = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, ticket_id, author_type, author_id, visibility, body, contains_personal_data,"
                            + " created_at FROM support.ticket_message WHERE tenant_id = ? AND ticket_id = ?"
                            + " AND visibility = 'PUBLIC' ORDER BY created_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(readMessage(rs));
                    }
                }
            }
            List<TicketAttachment> attachments = listAttachments(connection, ticket.id());
            return new TicketDetail(ticket, messages, attachments, listEvents(connection, ticket.id()));
        });
    }

    public record TicketDetail(Ticket ticket, List<TicketMessage> messages,
                               List<TicketAttachment> attachments, List<TicketEvent> events) {
    }

    /** Mensagem do solicitante; em RESOLVED, reabre para IN_PROGRESS (AJUDA §5). */
    public TicketMessage addRequesterMessage(String number, UUID requesterId, String rawBody) {
        requireRequester(requesterId);
        String body = requireText(rawBody, "body", 10000);
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null || !ticket.requesterId().equals(requesterId)) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            if (ticket.status().equals("CLOSED") || ticket.status().equals("CANCELLED")) {
                throw new ApiException.Conflict("data-conflict",
                        "O chamado está encerrado; abra um novo chamado vinculado.");
            }
            TicketMessage message = insertMessage(connection, ticket.id(), "REQUESTER", requesterId, "PUBLIC", body);
            insertEvent(connection, ticket.id(), "MESSAGE", "REQUESTER", requesterId, null, null, null);
            if (ticket.status().equals("RESOLVED")) {
                changeStatus(connection, ticket, "IN_PROGRESS", "REQUESTER", requesterId);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE support.ticket SET reopen_count = reopen_count + 1, updated_at = NOW()"
                                + " WHERE id = ?")) {
                    ps.setObject(1, ticket.id());
                    ps.executeUpdate();
                }
                insertEvent(connection, ticket.id(), "REOPENED", "REQUESTER", requesterId,
                        "RESOLVED", "IN_PROGRESS", null);
            }
            return message;
        });
    }

    /** Cancelamento pelo solicitante, só antes do atendimento (a partir de NEW). */
    public Ticket cancel(String number, UUID requesterId) {
        requireRequester(requesterId);
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null || !ticket.requesterId().equals(requesterId)) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            if (!ticket.status().equals("NEW")) {
                throw new ApiException.Conflict("data-conflict",
                        "Só é possível cancelar antes do atendimento.");
            }
            Ticket updated = changeStatus(connection, ticket, "CANCELLED", "REQUESTER", requesterId);
            insertEvent(connection, ticket.id(), "CANCELLED", "REQUESTER", requesterId, "NEW", "CANCELLED", null);
            return updated;
        });
    }

    // ------------------------------------------------------------------
    // Lado do operador (Central): fila e atendimento
    // ------------------------------------------------------------------

    /** Fila da Central para este tenant, com filtros de estado e severidade. */
    public List<Ticket> queue(String statusFilter, String severityFilter) {
        if (statusFilter != null && !STATUSES.contains(statusFilter)) {
            throw ApiException.Validation.of(new ApiException.FieldError("status", "invalid", "Estado desconhecido."));
        }
        if (severityFilter != null && !SEVERITIES.contains(severityFilter)) {
            throw ApiException.Validation.of(new ApiException.FieldError("severity", "invalid", "Severidade desconhecida."));
        }
        return inTx(connection -> {
            String sql = "SELECT id, number, requester_id, subject, category, module, origin_route,"
                    + " app_version, correlation_id, problem_code, severity, status, level, assignee_id,"
                    + " contains_personal_data, reopen_count, waiting_since, resolved_at, closed_at,"
                    + " created_at, updated_at FROM support.ticket WHERE tenant_id = ?"
                    + (statusFilter == null ? "" : " AND status = ?")
                    + (severityFilter == null ? "" : " AND severity = ?")
                    + " ORDER BY created_at DESC";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int i = 1;
                ps.setObject(i++, tenantId);
                if (statusFilter != null) {
                    ps.setString(i++, statusFilter);
                }
                if (severityFilter != null) {
                    ps.setString(i++, severityFilter);
                }
                List<Ticket> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(readTicket(rs));
                    }
                }
                return out;
            }
        });
    }

    /** Detalhe do operador: inclui notas internas. */
    public TicketDetail queueGet(String number) {
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            List<TicketMessage> messages = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, ticket_id, author_type, author_id, visibility, body, contains_personal_data,"
                            + " created_at FROM support.ticket_message WHERE tenant_id = ? AND ticket_id = ?"
                            + " ORDER BY created_at")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(readMessage(rs));
                    }
                }
            }
            return new TicketDetail(ticket, messages, listAttachments(connection, ticket.id()),
                    listEvents(connection, ticket.id()));
        });
    }

    /** Atendimento do agente: estado, severidade, nível e responsável (sem reiniciar relógio). */
    public Ticket agentUpdate(String number, UUID agentId, String toStatus, String severity,
                              String level, UUID assigneeId) {
        if (agentId == null) {
            throw new ApiException.Forbidden("O token precisa identificar o agente.");
        }
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            Ticket current = ticket;
            if (toStatus != null && !toStatus.equals(current.status())) {
                if (!TRANSITIONS.getOrDefault(current.status(), Set.of()).contains(toStatus)) {
                    throw new ApiException.Conflict("data-conflict",
                            "Transição inválida: " + current.status() + " → " + toStatus + ".");
                }
                current = changeStatus(connection, current, toStatus, "AGENT", agentId);
                insertEvent(connection, current.id(), "STATUS_CHANGED", "AGENT", agentId,
                        ticket.status(), toStatus, null);
            }
            if (severity != null && !severity.equals(current.severity())) {
                if (!SEVERITIES.contains(severity)) {
                    throw ApiException.Validation.of(new ApiException.FieldError("severity", "invalid", "Severidade desconhecida."));
                }
                insertEvent(connection, current.id(), "SEVERITY_CHANGED", "AGENT", agentId,
                        null, null, "severity " + current.severity() + " → " + severity);
                current = updateField(connection, current, "severity", severity);
            }
            if (level != null && !level.equals(current.level())) {
                if (!LEVELS.contains(level)) {
                    throw ApiException.Validation.of(new ApiException.FieldError("level", "invalid", "Nível desconhecido."));
                }
                current = updateField(connection, current, "level", level);
            }
            if (assigneeId != null && !assigneeId.equals(current.assigneeId())) {
                insertEvent(connection, current.id(), "ASSIGNED", "AGENT", agentId,
                        null, null, "assignee " + assigneeId);
                current = updateAssignee(connection, current, assigneeId);
            }
            return current;
        });
    }

    /** Mensagem do agente, pública ou nota interna (nunca visível ao tenant). */
    public TicketMessage addAgentMessage(String number, UUID agentId, String rawBody, boolean internal) {
        if (agentId == null) {
            throw new ApiException.Forbidden("O token precisa identificar o agente.");
        }
        String body = requireText(rawBody, "body", 10000);
        return inTx(connection -> {
            Ticket ticket = findByNumber(connection, number);
            if (ticket == null) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            if (ticket.status().equals("CLOSED") || ticket.status().equals("CANCELLED")) {
                throw new ApiException.Conflict("data-conflict", "O chamado está encerrado.");
            }
            TicketMessage message = insertMessage(connection, ticket.id(), "AGENT", agentId,
                    internal ? "INTERNAL" : "PUBLIC", body);
            insertEvent(connection, ticket.id(), "MESSAGE", "AGENT", agentId, null, null,
                    internal ? "internal" : "public");
            return message;
        });
    }

    // ------------------------------------------------------------------
    // Anexos (registro; bytes e varredura vivem no TicketAttachmentService)
    // ------------------------------------------------------------------

    /** Registra o anexo já em quarentena; recusa tipo fora da lista e o 6º anexo da mensagem. */
    public TicketAttachment registerAttachment(UUID ticketId, UUID messageId, String fileName,
                                               String detectedMime, long sizeBytes, String sha256,
                                               String objectKey) {
        return registerAttachmentWithId(UUID.randomUUID(), ticketId, messageId, fileName,
                detectedMime, sizeBytes, sha256, objectKey);
    }

    /**
     * Mesmo registro, com id explícito: o pipeline precisa do id antes de
     * gravar o objeto, para a chave ser definitiva desde o início (sem chave
     * provisória que viraria lixo se o registro falhasse).
     */
    public TicketAttachment registerAttachmentWithId(UUID attachmentId, UUID ticketId, UUID messageId,
                                                     String fileName, String detectedMime, long sizeBytes,
                                                     String sha256, String objectKey) {
        if (attachmentId == null || ticketId == null || messageId == null) {
            throw new IllegalArgumentException("attachmentId, ticketId e messageId sao obrigatorios");
        }
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            throw ApiException.Validation.of(new ApiException.FieldError("fileName", "invalid", "Nome de arquivo inválido."));
        }
        if (!ALLOWED_MIMES.contains(detectedMime)) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "invalid",
                    "Tipo de arquivo não aceito para chamado."));
        }
        if (sizeBytes <= 0 || sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw ApiException.Validation.of(new ApiException.FieldError("file", "invalid",
                    "Anexo maior que 10 MB."));
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 invalido");
        }
        return inTx(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT ticket_id FROM support.ticket_message WHERE tenant_id = ? AND id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !ticketId.equals(rs.getObject(1))) {
                        throw new ApiException.NotFound("A mensagem não existe neste chamado.");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM support.ticket_attachment WHERE tenant_id = ? AND message_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, messageId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getLong(1) >= MAX_ATTACHMENTS_PER_MESSAGE) {
                        throw ApiException.Validation.of(new ApiException.FieldError("file", "invalid",
                                "Limite de 5 anexos por mensagem."));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.ticket_attachment (id, tenant_id, ticket_id, message_id, file_name,"
                            + " detected_mime, size_bytes, sha256, object_key)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING created_at")) {
                ps.setObject(1, attachmentId);
                ps.setObject(2, tenantId);
                ps.setObject(3, ticketId);
                ps.setObject(4, messageId);
                ps.setString(5, fileName.strip());
                ps.setString(6, detectedMime);
                ps.setLong(7, sizeBytes);
                ps.setString(8, sha256);
                ps.setString(9, objectKey);
                OffsetDateTime createdAt;
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    createdAt = toOffset(rs.getTimestamp(1));
                }
                insertEvent(connection, ticketId, "SCAN", "SYSTEM", null, null, null, "QUARANTINED " + attachmentId);
                return new TicketAttachment(attachmentId, ticketId, messageId, fileName.strip(), detectedMime,
                        sizeBytes, sha256, objectKey, "QUARANTINED", null, createdAt);
            }
        });
    }

    /** Marca o resultado da varredura; INFECTED exige o nome da ameaça. Download só com CLEAN. */
    public TicketAttachment markScan(UUID attachmentId, String scanStatus, String threat) {
        if (!SCAN_STATUSES.contains(scanStatus)) {
            throw ApiException.Validation.of(new ApiException.FieldError("scanStatus", "invalid", "Estado de varredura desconhecido."));
        }
        if (scanStatus.equals("INFECTED") && (threat == null || threat.isBlank())) {
            throw new IllegalArgumentException("INFECTED exige o nome da ameaca");
        }
        return inTx(connection -> {
            TicketAttachment current = findAttachment(connection, attachmentId);
            if (current == null) {
                throw new ApiException.NotFound("O anexo não existe.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket_attachment SET scan_status = ?, threat = ? WHERE id = ?")) {
                ps.setString(1, scanStatus);
                setText(ps, 2, threat);
                ps.setObject(3, attachmentId);
                ps.executeUpdate();
            }
            insertEvent(connection, current.ticketId(), "SCAN", "SYSTEM", null, null, null,
                    scanStatus + " " + attachmentId);
            return new TicketAttachment(current.id(), current.ticketId(), current.messageId(),
                    current.fileName(), current.detectedMime(), current.sizeBytes(), current.sha256(),
                    current.objectKey(), scanStatus, threat, current.createdAt());
        });
    }

    /** Anexo para download: recusa tudo que não está CLEAN (fail-closed, P1.20). */
    public TicketAttachment attachmentForDownload(UUID attachmentId) {
        return inTx(connection -> {
            TicketAttachment current = findAttachment(connection, attachmentId);
            if (current == null) {
                throw new ApiException.NotFound("O anexo não existe.");
            }
            if (!current.scanStatus().equals("CLEAN")) {
                throw new ApiException.Conflict("data-conflict",
                        "O anexo ainda não foi liberado pela varredura.");
            }
            return current;
        });
    }

    /** Troca a chave do objeto após a liberação (quarentena → liberados). */
    public void moveAttachmentKey(UUID attachmentId, String releasedKey) {
        if (attachmentId == null || releasedKey == null || releasedKey.isBlank()) {
            throw new IllegalArgumentException("attachmentId e releasedKey sao obrigatorios");
        }
        inTx(connection -> {
            if (findAttachment(connection, attachmentId) == null) {
                throw new ApiException.NotFound("O anexo não existe.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket_attachment SET object_key = ? WHERE id = ?")) {
                ps.setString(1, releasedKey);
                ps.setObject(2, attachmentId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Chamado dono do anexo (para conferir visibilidade antes do download). */
    public Ticket ticketOfAttachment(UUID attachmentId) {
        return inTx(connection -> {
            TicketAttachment current = findAttachment(connection, attachmentId);
            if (current == null) {
                throw new ApiException.NotFound("O anexo não existe.");
            }
            return findById(connection, current.ticketId());
        });
    }

    public TicketAttachment findAttachmentById(UUID attachmentId) {
        return inTx(connection -> {
            TicketAttachment current = findAttachment(connection, attachmentId);
            if (current == null) {
                throw new ApiException.NotFound("O anexo não existe.");
            }
            return current;
        });
    }

    // ------------------------------------------------------------------
    // Utilitários públicos (formulário e listas)
    // ------------------------------------------------------------------

    /** Verdadeiro quando o texto contém CPF, CNPJ, e-mail ou telefone (ADR-0020). */
    public static boolean containsPersonalData(String text) {
        if (text == null) {
            return false;
        }
        return CPF_LIKE.matcher(text).find() || CNPJ_LIKE.matcher(text).find()
                || EMAIL_LIKE.matcher(text).find() || PHONE_LIKE.matcher(text).find();
    }

    /** Mascara dado pessoal para exibição em listas (AJUDA §11). */
    public static String mask(String text) {
        if (text == null) {
            return null;
        }
        String out = CPF_LIKE.matcher(text).replaceAll("***.***.***-**");
        out = CNPJ_LIKE.matcher(out).replaceAll("**.***.***/****-**");
        out = EMAIL_LIKE.matcher(out).replaceAll("***@***");
        out = PHONE_LIKE.matcher(out).replaceAll("*****-****");
        return out;
    }

    /** Sanitiza o corpo: sem tags HTML, sem espaços nas pontas (AJUDA §11). */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        return TAG.matcher(raw).replaceAll("").strip();
    }

    // ------------------------------------------------------------------
    // Infra interna
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Ticket operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Ticket operation failed: " + e.getMessage(), e);
        }
    }

    /** Próximo número da sequência, com a linha do contador travada na transação. */
    private String nextNumber(Connection connection) throws SQLException {
        int year = OffsetDateTime.now(clock).getYear();
        int next;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_number FROM support.ticket_number_counter"
                        + " WHERE tenant_id = ? AND year = ? FOR UPDATE")) {
            ps.setObject(1, tenantId);
            ps.setInt(2, year);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    next = rs.getInt(1) + 1;
                    try (PreparedStatement up = connection.prepareStatement(
                            "UPDATE support.ticket_number_counter SET last_number = ?"
                                    + " WHERE tenant_id = ? AND year = ?")) {
                        up.setInt(1, next);
                        up.setObject(2, tenantId);
                        up.setInt(3, year);
                        up.executeUpdate();
                    }
                } else {
                    next = 1;
                    try (PreparedStatement ins = connection.prepareStatement(
                            "INSERT INTO support.ticket_number_counter (tenant_id, year, last_number)"
                                    + " VALUES (?, ?, ?)")) {
                        ins.setObject(1, tenantId);
                        ins.setInt(2, year);
                        ins.setInt(3, next);
                        ins.executeUpdate();
                    }
                }
            }
        }
        return "SUP-" + year + "-" + String.format("%06d", next);
    }

    private TicketMessage insertMessage(Connection connection, UUID ticketId, String authorType,
                                        UUID authorId, String visibility, String rawBody) throws SQLException {
        String body = sanitize(rawBody);
        if (body == null || body.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError("body", "invalid", "Mensagem vazia."));
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO support.ticket_message (tenant_id, ticket_id, author_type, author_id,"
                        + " visibility, body, contains_personal_data)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            ps.setString(3, authorType);
            setUuid(ps, 4, authorId);
            ps.setString(5, visibility);
            ps.setString(6, body);
            ps.setBoolean(7, containsPersonalData(body));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new TicketMessage((UUID) rs.getObject(1), ticketId, authorType, authorId,
                        visibility, body, containsPersonalData(body), toOffset(rs.getTimestamp(2)));
            }
        }
    }

    private void insertEvent(Connection connection, UUID ticketId, String eventType, String actorType,
                             UUID actorId, String fromStatus, String toStatus, String detail) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO support.ticket_event (tenant_id, ticket_id, event_type, actor_type,"
                        + " actor_id, from_status, to_status, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            ps.setString(3, eventType);
            ps.setString(4, actorType);
            setUuid(ps, 5, actorId);
            setText(ps, 6, fromStatus);
            setText(ps, 7, toStatus);
            setText(ps, 8, detail);
            ps.executeUpdate();
        }
    }

    private Ticket changeStatus(Connection connection, Ticket ticket, String toStatus,
                                String actorType, UUID actorId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE support.ticket SET status = ?, updated_at = NOW(),"
                        + " waiting_since = CASE WHEN ? = 'WAITING_CUSTOMER' THEN NOW() ELSE waiting_since END,"
                        + " resolved_at = CASE WHEN ? = 'RESOLVED' THEN NOW() ELSE resolved_at END,"
                        + " closed_at = CASE WHEN ? = 'CLOSED' THEN NOW() ELSE closed_at END"
                        + " WHERE id = ?")) {
            ps.setString(1, toStatus);
            ps.setString(2, toStatus);
            ps.setString(3, toStatus);
            ps.setString(4, toStatus);
            ps.setObject(5, ticket.id());
            ps.executeUpdate();
        }
        return new Ticket(ticket.id(), ticket.number(), ticket.requesterId(), ticket.subject(),
                ticket.category(), ticket.module(), ticket.originRoute(), ticket.appVersion(),
                ticket.correlationId(), ticket.problemCode(), ticket.severity(), toStatus,
                ticket.level(), ticket.assigneeId(), ticket.containsPersonalData(),
                ticket.reopenCount(), toStatus.equals("WAITING_CUSTOMER") ? OffsetDateTime.now(clock) : ticket.waitingSince(),
                toStatus.equals("RESOLVED") ? OffsetDateTime.now(clock) : ticket.resolvedAt(),
                toStatus.equals("CLOSED") ? OffsetDateTime.now(clock) : ticket.closedAt(),
                ticket.createdAt(), OffsetDateTime.now(clock));
    }

    private Ticket updateField(Connection connection, Ticket ticket, String column, String value) throws SQLException {
        if (!Set.of("severity", "level").contains(column)) {
            throw new IllegalArgumentException("coluna invalida: " + column);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE support.ticket SET " + column + " = ?, updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, value);
            ps.setObject(2, ticket.id());
            ps.executeUpdate();
        }
        return findById(connection, ticket.id());
    }

    private Ticket updateAssignee(Connection connection, Ticket ticket, UUID assigneeId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE support.ticket SET assignee_id = ?, updated_at = NOW() WHERE id = ?")) {
            setUuid(ps, 1, assigneeId);
            ps.setObject(2, ticket.id());
            ps.executeUpdate();
        }
        return findById(connection, ticket.id());
    }

    private Ticket findByNumber(Connection connection, String number) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, number, requester_id, subject, category, module, origin_route, app_version,"
                        + " correlation_id, problem_code, severity, status, level, assignee_id,"
                        + " contains_personal_data, reopen_count, waiting_since, resolved_at, closed_at,"
                        + " created_at, updated_at FROM support.ticket WHERE tenant_id = ? AND number = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readTicket(rs) : null;
            }
        }
    }

    private Ticket findById(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, number, requester_id, subject, category, module, origin_route, app_version,"
                        + " correlation_id, problem_code, severity, status, level, assignee_id,"
                        + " contains_personal_data, reopen_count, waiting_since, resolved_at, closed_at,"
                        + " created_at, updated_at FROM support.ticket WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O chamado não existe.");
                }
                return readTicket(rs);
            }
        }
    }

    private TicketAttachment findAttachment(Connection connection, UUID id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, ticket_id, message_id, file_name, detected_mime, size_bytes, sha256,"
                        + " object_key, scan_status, threat, created_at FROM support.ticket_attachment"
                        + " WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readAttachment(rs) : null;
            }
        }
    }

    private List<TicketEvent> listEvents(Connection connection, UUID ticketId) throws SQLException {
        List<TicketEvent> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, ticket_id, event_type, actor_type, actor_id, from_status, to_status, detail,"
                        + " created_at FROM support.ticket_event WHERE tenant_id = ? AND ticket_id = ?"
                        + " ORDER BY created_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readEvent(rs));
                }
            }
        }
        return out;
    }

    private List<TicketAttachment> listAttachments(Connection connection, UUID ticketId) throws SQLException {
        List<TicketAttachment> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, ticket_id, message_id, file_name, detected_mime, size_bytes, sha256,"
                        + " object_key, scan_status, threat, created_at FROM support.ticket_attachment"
                        + " WHERE tenant_id = ? AND ticket_id = ? ORDER BY created_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(readAttachment(rs));
                }
            }
        }
        return out;
    }

    private static Ticket readTicket(ResultSet rs) throws SQLException {
        return new Ticket((UUID) rs.getObject("id"), rs.getString("number"),
                (UUID) rs.getObject("requester_id"), rs.getString("subject"),
                rs.getString("category"), rs.getString("module"), rs.getString("origin_route"),
                rs.getString("app_version"), rs.getString("correlation_id"), rs.getString("problem_code"),
                rs.getString("severity"), rs.getString("status"), rs.getString("level"),
                (UUID) rs.getObject("assignee_id"), rs.getBoolean("contains_personal_data"),
                rs.getInt("reopen_count"), toOffset(rs.getTimestamp("waiting_since")),
                toOffset(rs.getTimestamp("resolved_at")), toOffset(rs.getTimestamp("closed_at")),
                toOffset(rs.getTimestamp("created_at")), toOffset(rs.getTimestamp("updated_at")));
    }

    private static TicketMessage readMessage(ResultSet rs) throws SQLException {
        return new TicketMessage((UUID) rs.getObject("id"), (UUID) rs.getObject("ticket_id"),
                rs.getString("author_type"), (UUID) rs.getObject("author_id"),
                rs.getString("visibility"), rs.getString("body"),
                rs.getBoolean("contains_personal_data"), toOffset(rs.getTimestamp("created_at")));
    }

    private static TicketAttachment readAttachment(ResultSet rs) throws SQLException {
        return new TicketAttachment((UUID) rs.getObject("id"), (UUID) rs.getObject("ticket_id"),
                (UUID) rs.getObject("message_id"), rs.getString("file_name"),
                rs.getString("detected_mime"), rs.getLong("size_bytes"), rs.getString("sha256"),
                rs.getString("object_key"), rs.getString("scan_status"), rs.getString("threat"),
                toOffset(rs.getTimestamp("created_at")));
    }

    private static TicketEvent readEvent(ResultSet rs) throws SQLException {
        return new TicketEvent((UUID) rs.getObject("id"), (UUID) rs.getObject("ticket_id"),
                rs.getString("event_type"), rs.getString("actor_type"),
                (UUID) rs.getObject("actor_id"),
                rs.getString("from_status"), rs.getString("to_status"), rs.getString("detail"),
                toOffset(rs.getTimestamp("created_at")));
    }

    private static Ticket mask(Ticket ticket) {
        return new Ticket(ticket.id(), ticket.number(), ticket.requesterId(), mask(ticket.subject()),
                ticket.category(), ticket.module(), ticket.originRoute(), ticket.appVersion(),
                ticket.correlationId(), ticket.problemCode(), ticket.severity(), ticket.status(),
                ticket.level(), ticket.assigneeId(), ticket.containsPersonalData(),
                ticket.reopenCount(), ticket.waitingSince(), ticket.resolvedAt(), ticket.closedAt(),
                ticket.createdAt(), ticket.updatedAt());
    }

    private static void requireRequester(UUID requesterId) {
        if (requesterId == null) {
            throw new ApiException.Forbidden("O token precisa identificar o usuário.");
        }
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "required", "Campo obrigatório."));
        }
        String clean = sanitize(value);
        if (clean.isBlank() || clean.length() > max) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "invalid", "Texto inválido ou longo demais."));
        }
        return clean;
    }

    private static void setText(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setObject(index, value);
        }
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
