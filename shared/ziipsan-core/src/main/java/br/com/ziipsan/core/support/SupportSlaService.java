package br.com.ziipsan.core.support;

import br.com.ziipsan.core.api.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

/**
 * SLA de suporte por contrato e CSAT (P2.15; ADR-0020, ADR-0025).
 *
 * <p>O único escritor das tabelas de SLA do tenant, no mesmo padrão do
 * {@link TicketService}: uma transação com o contexto RLS do tenant
 * ({@code app.current_tenant}). Fora do tenant, a política não existe —
 * a API devolve 404, nunca 403.
 *
 * <p>O que este serviço faz:
 * <ul>
 *   <li>política de SLA por tenant (calendário BUSINESS ou ROUND_THE_CLOCK,
 *       nível STANDARD ou PUBLIC_SECTOR) com exatamente os cinco alvos
 *       S1–S5 (primeira resposta, solução, aderência, multa);</li>
 *   <li>matriz impacto × criticidade → severidade: o padrão abaixo, com
 *       sobrescrita opcional por política e por célula;</li>
 *   <li>prazos no chamado: a aplicação calcula primeira resposta e
 *       solução a partir da abertura, no calendário da política e nos
 *       feriados passados por quem chama (nenhum feriado embutido);</li>
 *   <li>pausa: entrar em WAITING_CUSTOMER pausa o relógio; a retomada
 *       estende os prazos só pelos minutos úteis da pausa;</li>
 *   <li>alertas: 80% do prazo (WARNING) e estouro (BREACHED), calculados
 *       na leitura e registrados uma vez por nível na trilha do chamado;</li>
 *   <li>CSAT: uma avaliação por chamado, nota 1–5, só após RESOLVED e
 *       dentro de 7 dias; nota 1 ou 2 abre revisão;</li>
 *   <li>relatório mensal por severidade (FRT, MTTR, aderência, CSAT,
 *       multa estimada) — a aba da Central o serve por tenant e mês.</li>
 * </ul>
 *
 * <p>Limites deliberados (não omissões silenciosas):
 * <ul>
 *   <li>A aplicação automática do SLA na abertura do chamado ainda não
 *       existe: o operador aplica a política pela fila da Central. A
 *       abertura sem política deixa os prazos nulos, como a P1.23.</li>
 *   <li>O fuso usado no cálculo é o passado por quem chama (o controlador
 *       usa o padrão de fábrica {@code America/Sao_Paulo}, P0.19): ler o
 *       {@code tenant.timezone} vigente do tenant é pendência declarada,
 *       não um fuso inventado por chamado.</li>
 *   <li>Os feriados vêm de quem chama: o controlador da fila carrega só
 *       os nacionais do schema do tenant (sem localidade conhecida, só
 *       eles contam, a convenção do DeadlineCalculator); o feriado
 *       municipal é provado no cálculo com o conjunto explícito, e o
 *       carregamento com localidade (UF/IBGE do tenant) é pendência
 *       declarada.</li>
 *   <li>A multa estimada do relatório é {@code brechas × multa% × valor
 *       mensal do contrato} quando o chamador informa o valor mensal; sem
 *       ele, o relatório traz só a contagem e o percentual. A base da
 *       multa (valor mensal) é pendência declarada do dono: o ADR-0025
 *       fixa o percentual, não a base.</li>
 *   <li>O envio do alerta (e-mail ao responsável/coordenador) não existe
 *       aqui: o nível é calculado e registrado na trilha; o consumidor
 *       de e-mail da outbox é a mesma pendência declarada da P1.23.</li>
 *   <li>FRT e MTTR do relatório são minutos de relógio (MTTR desconta
 *       as pausas em minutos de relógio); a aderência, não a média, é
 *       que usa o prazo em horas úteis.</li>
 * </ul>
 */
public final class SupportSlaService {

    /** Severidades, na ordem do ADR-0025. */
    public static final List<String> SEVERITIES = List.of("S1", "S2", "S3", "S4", "S5");
    /** Impactos e criticidades da matriz. */
    public static final Set<String> MATRIX_AXES = Set.of("HIGH", "MEDIUM", "LOW");
    /** Calendários da política. */
    public static final Set<String> CALENDARS = Set.of("BUSINESS", "ROUND_THE_CLOCK");
    /** Níveis da política. */
    public static final Set<String> LEVELS = Set.of("STANDARD", "PUBLIC_SECTOR");
    /** Janela de 7 dias para avaliar após RESOLVED (AJUDA §5). */
    public static final int CSAT_WINDOW_DAYS = 7;
    /** Limiar do alerta de 80% do prazo (AJUDA §5). */
    public static final double ALERT_FRACTION = 0.8;

    /**
     * Matriz padrão impacto × criticidade → severidade (AJUDA §5, lida
     * como matriz: impacto alto com criticidade alta para tudo; impacto
     * baixo com criticidade baixa vira solicitação/melhoria).
     */
    private static final Map<String, String> DEFAULT_MATRIX = Map.of(
            "HIGH:HIGH", "S1",
            "HIGH:MEDIUM", "S2",
            "HIGH:LOW", "S3",
            "MEDIUM:HIGH", "S2",
            "MEDIUM:MEDIUM", "S3",
            "MEDIUM:LOW", "S4",
            "LOW:HIGH", "S3",
            "LOW:MEDIUM", "S4",
            "LOW:LOW", "S5");

    /** Classificação padrão, sem política (matriz acima). */
    public static String classify(String impact, String criticality) {
        requireAxis(impact, "impact");
        requireAxis(criticality, "criticality");
        return DEFAULT_MATRIX.get(impact + ":" + criticality);
    }

    private static void requireAxis(String value, String field) {
        if (value == null || !MATRIX_AXES.contains(value)) {
            throw ApiException.Validation.of(new ApiException.FieldError(field, "invalid",
                    "Impacto/criticidade desconhecidos (HIGH, MEDIUM ou LOW)."));
        }
    }

    public record PolicyInput(String policyKey, String name, String calendar, String level,
                              int businessStartHour, int businessEndHour) {
    }

    public record TargetInput(String severity, int firstResponseHours, int resolutionHours,
                              BigDecimal adherencePct, BigDecimal penaltyPct) {
    }

    public record PolicyView(UUID id, String policyKey, String name, String calendar, String level,
                             int businessStartHour, int businessEndHour,
                             List<TargetView> targets, Map<String, String> matrixOverrides) {
    }

    public record TargetView(String severity, int firstResponseHours, int resolutionHours,
                             BigDecimal adherencePct, BigDecimal penaltyPct) {
    }

    public record TicketSlaView(UUID ticketId, String number, String severity, String status,
                                String policyKey, OffsetDateTime firstResponseDueAt,
                                OffsetDateTime resolutionDueAt, OffsetDateTime firstResponseAt,
                                OffsetDateTime createdAt, OffsetDateTime resolvedAt,
                                int pauseCount, boolean pauseOpen) {
    }

    /** Nível de um prazo: dentro (OK), a 80% (WARNING), estourado (BREACHED) ou cumprido (MET). */
    public enum AlertLevel {
        OK, WARNING, BREACHED, MET, NONE
    }

    public record DueEvaluation(OffsetDateTime dueAt, OffsetDateTime doneAt, AlertLevel level,
                                double elapsedFraction) {
    }

    public record SlaEvaluation(TicketSlaView ticket, DueEvaluation firstResponse,
                                DueEvaluation resolution) {
    }

    public record CsatView(UUID id, UUID ticketId, String ticketNumber, int rating, String comment,
                           boolean reviewRequired, OffsetDateTime respondedAt) {
    }

    public record SeverityReport(String severity, int opened, int resolved, int resolvedWithinDue,
                                 int breachedResolution, Double adherencePct, Double avgFrtMinutes,
                                 Double avgMttrMinutes, Double csatAvg, int csatCount,
                                 int csatReviewCount, BigDecimal penaltyEstimate) {
    }

    public record MonthlyReport(UUID tenantId, LocalDate month, int opened, int resolved,
                                Double adherencePct, Double csatAvg, int csatCount,
                                BigDecimal penaltyEstimateTotal, List<SeverityReport> bySeverity) {
    }

    private final DataSource dataSource;
    private final UUID tenantId;
    private final Clock clock;

    public SupportSlaService(DataSource dataSource, UUID tenantId, Clock clock) {
        if (dataSource == null || tenantId == null || clock == null) {
            throw new IllegalArgumentException("dataSource, tenantId e clock sao obrigatorios");
        }
        this.dataSource = dataSource;
        this.tenantId = tenantId;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Políticas
    // ------------------------------------------------------------------

    /** Cria a política (sem alvos: {@link #setTargets} os exige em seguida). */
    public PolicyView createPolicy(UUID actorId, String actorRole, PolicyInput in) {
        if (in == null || in.policyKey() == null || !in.policyKey().matches("^[a-z0-9][a-z0-9_-]{0,99}$")) {
            throw ApiException.Validation.of(new ApiException.FieldError("policyKey", "invalid",
                    "Chave da politica invalida (minusculas, numeros, _ ou -, ate 100)."));
        }
        if (in.name() == null || in.name().isBlank() || in.name().length() > 200) {
            throw ApiException.Validation.of(new ApiException.FieldError("name", "invalid",
                    "Nome da politica obrigatorio, ate 200 caracteres."));
        }
        String calendar = in.calendar() == null ? "BUSINESS" : in.calendar();
        if (!CALENDARS.contains(calendar)) {
            throw ApiException.Validation.of(new ApiException.FieldError("calendar", "invalid",
                    "Calendario desconhecido (BUSINESS ou ROUND_THE_CLOCK)."));
        }
        String level = in.level() == null ? "STANDARD" : in.level();
        if (!LEVELS.contains(level)) {
            throw ApiException.Validation.of(new ApiException.FieldError("level", "invalid",
                    "Nivel desconhecido (STANDARD ou PUBLIC_SECTOR)."));
        }
        try {
            SlaDeadlineCalculator.requireWindow(in.businessStartHour(), in.businessEndHour());
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError("businessEndHour", "invalid", e.getMessage()));
        }
        return inTx(connection -> {
            if (findPolicyRow(connection, in.policyKey()) != null) {
                throw new ApiException.Conflict("data-conflict",
                        "Ja existe uma politica com esta chave neste tenant.");
            }
            UUID id = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.sla_policy (id, tenant_id, policy_key, name, calendar, level,"
                            + " business_start_hour, business_end_hour) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setString(3, in.policyKey());
                ps.setString(4, in.name().strip());
                ps.setString(5, calendar);
                ps.setString(6, level);
                ps.setInt(7, in.businessStartHour());
                ps.setInt(8, in.businessEndHour());
                ps.executeUpdate();
            }
            audit(connection, actorId, actorRole, "SLA_POLICY_CREATED",
                    Map.of("policy_key", in.policyKey()));
            return policy(connection, in.policyKey());
        });
    }

    /** Define (substituindo) os cinco alvos da política; recusa conjunto incompleto. */
    public PolicyView setTargets(String policyKey, List<TargetInput> targets, UUID actorId, String actorRole) {
        if (targets == null || targets.size() != 5) {
            throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                    "A politica exige exatamente os cinco alvos S1–S5."));
        }
        Map<String, TargetInput> bySeverity = new HashMap<>();
        for (TargetInput target : targets) {
            if (target == null || !SEVERITIES.contains(target.severity())) {
                throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                        "Alvo com severidade desconhecida."));
            }
            if (target.firstResponseHours() <= 0 || target.resolutionHours() <= 0) {
                throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                        "Prazos em horas devem ser maiores que zero."));
            }
            if (target.adherencePct() == null
                    || target.adherencePct().compareTo(BigDecimal.ZERO) < 0
                    || target.adherencePct().compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                        "Aderencia deve estar entre 0 e 100."));
            }
            if (target.penaltyPct() == null || target.penaltyPct().compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                        "Multa nao pode ser negativa."));
            }
            if (bySeverity.put(target.severity(), target) != null) {
                throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                        "Severidade duplicada nos alvos."));
            }
        }
        if (!bySeverity.keySet().containsAll(SEVERITIES)) {
            throw ApiException.Validation.of(new ApiException.FieldError("targets", "invalid",
                    "Faltam severidades nos alvos (exigem-se S1–S5)."));
        }
        return inTx(connection -> {
            UUID policyId = requirePolicyId(connection, policyKey);
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM support.sla_policy_target WHERE tenant_id = ? AND policy_id = ?")) {
                del.setObject(1, tenantId);
                del.setObject(2, policyId);
                del.executeUpdate();
            }
            for (String severity : SEVERITIES) {
                TargetInput target = bySeverity.get(severity);
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO support.sla_policy_target (tenant_id, policy_id, severity,"
                                + " first_response_hours, resolution_hours, adherence_pct, penalty_pct)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, tenantId);
                    ps.setObject(2, policyId);
                    ps.setString(3, severity);
                    ps.setInt(4, target.firstResponseHours());
                    ps.setInt(5, target.resolutionHours());
                    ps.setBigDecimal(6, target.adherencePct());
                    ps.setBigDecimal(7, target.penaltyPct());
                    ps.executeUpdate();
                }
            }
            audit(connection, actorId, actorRole, "SLA_POLICY_TARGETS_SET",
                    Map.of("policy_key", policyKey));
            return policy(connection, policyKey);
        });
    }

    /** Sobrescreve uma célula da matriz para a política (sem linha, vale o padrão). */
    public PolicyView setMatrixCell(String policyKey, String impact, String criticality,
                                    String severity, UUID actorId, String actorRole) {
        requireAxis(impact, "impact");
        requireAxis(criticality, "criticality");
        if (severity == null || !SEVERITIES.contains(severity)) {
            throw ApiException.Validation.of(new ApiException.FieldError("severity", "invalid",
                    "Severidade desconhecida."));
        }
        return inTx(connection -> {
            UUID policyId = requirePolicyId(connection, policyKey);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.sla_severity_matrix (tenant_id, policy_id, impact, criticality, severity)"
                            + " VALUES (?, ?, ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, policy_id, impact, criticality) DO UPDATE SET severity = EXCLUDED.severity")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, policyId);
                ps.setString(3, impact);
                ps.setString(4, criticality);
                ps.setString(5, severity);
                ps.executeUpdate();
            }
            audit(connection, actorId, actorRole, "SLA_POLICY_MATRIX_SET",
                    Map.of("policy_key", policyKey, "cell", impact + ":" + criticality));
            return policy(connection, policyKey);
        });
    }

    /** Política com alvos e sobrescritas; 404 quando não é do tenant. */
    public PolicyView policy(String policyKey) {
        return inTx(connection -> policy(connection, policyKey));
    }

    /** Todas as políticas do tenant, em ordem de chave. */
    public List<PolicyView> policies() {
        return inTx(connection -> {
            List<PolicyView> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT policy_key FROM support.sla_policy WHERE tenant_id = ? ORDER BY policy_key")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(policy(connection, rs.getString(1)));
                    }
                }
            }
            return out;
        });
    }

    /** Severidade pela matriz da política (sobrescrita) ou pelo padrão. */
    public String classifyForPolicy(String policyKey, String impact, String criticality) {
        requireAxis(impact, "impact");
        requireAxis(criticality, "criticality");
        return inTx(connection -> {
            UUID policyId = requirePolicyId(connection, policyKey);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT severity FROM support.sla_severity_matrix"
                            + " WHERE tenant_id = ? AND policy_id = ? AND impact = ? AND criticality = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, policyId);
                ps.setString(3, impact);
                ps.setString(4, criticality);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                }
            }
            return classify(impact, criticality);
        });
    }

    // ------------------------------------------------------------------
    // Prazos no chamado
    // ------------------------------------------------------------------

    /**
     * Aplica a política ao chamado: calcula primeira resposta e solução a
     * partir da abertura, no calendário da política. Reaplicar (ex.:
     * mudança de severidade) recalcula a partir da abertura e mantém a
     * extensão das pausas já registradas.
     */
    public TicketSlaView applyToTicket(String number, String policyKey,
                                       Set<LocalDate> holidays, ZoneId zone) {
        if (zone == null) {
            throw new IllegalArgumentException("O fuso do tenant e obrigatorio");
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            PolicyRow policy = requirePolicyRow(connection, policyKey);
            Map<String, TargetRow> targets = loadTargets(connection, policy.id());
            if (targets.size() != 5) {
                throw new ApiException.Conflict("data-conflict",
                        "A politica ainda nao tem os cinco alvos S1–S5.");
            }
            TargetRow target = targets.get(ticket.severity());
            SlaDeadlineCalculator.Calendar calendar = SlaDeadlineCalculator.Calendar.valueOf(policy.calendar());
            ZonedDateTime created = toZoned(ticket.createdAt(), zone);
            ZonedDateTime firstDue = SlaDeadlineCalculator.addHours(created, target.firstResponseHours(),
                    calendar, holidays, policy.businessStartHour(), policy.businessEndHour());
            ZonedDateTime resolutionDue = SlaDeadlineCalculator.addHours(created, target.resolutionHours(),
                    calendar, holidays, policy.businessStartHour(), policy.businessEndHour());
            long pausedBusiness = pausedBusinessMinutes(connection, ticket.id(), null, policy, holidays, zone);
            if (pausedBusiness > 0) {
                firstDue = SlaDeadlineCalculator.addBusinessMinutes(firstDue, pausedBusiness,
                        holidays, policy.businessStartHour(), policy.businessEndHour());
                resolutionDue = SlaDeadlineCalculator.addBusinessMinutes(resolutionDue, pausedBusiness,
                        holidays, policy.businessStartHour(), policy.businessEndHour());
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket SET sla_policy_id = ?, first_response_due_at = ?,"
                            + " resolution_due_at = ?, updated_at = NOW() WHERE id = ?")) {
                ps.setObject(1, policy.id());
                ps.setTimestamp(2, Timestamp.from(firstDue.toInstant()));
                ps.setTimestamp(3, Timestamp.from(resolutionDue.toInstant()));
                ps.setObject(4, ticket.id());
                ps.executeUpdate();
            }
            insertEvent(connection, ticket.id(), "SLA_APPLIED", "SYSTEM", null, null, null,
                    "policy " + policyKey + " severity " + ticket.severity());
            return ticketSla(connection, ticket.id());
        });
    }

    /** Prazos, pausas e primeira resposta do chamado; 404 quando não é do tenant. */
    public TicketSlaView slaOf(String number) {
        return inTx(connection -> ticketSla(connection, requireTicket(connection, number).id()));
    }

    /** Nível dos dois prazos, no instante dado (sem gravar nada). */
    public SlaEvaluation evaluate(String number, OffsetDateTime now, Set<LocalDate> holidays, ZoneId zone) {
        if (now == null || zone == null) {
            throw new IllegalArgumentException("O instante e o fuso sao obrigatorios");
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            TicketSlaView view = ticketSla(connection, ticket.id());
            if (view.policyKey() == null || view.firstResponseDueAt() == null) {
                return new SlaEvaluation(view,
                        new DueEvaluation(null, view.firstResponseAt(), AlertLevel.NONE, 0),
                        new DueEvaluation(null, view.resolvedAt(), AlertLevel.NONE, 0));
            }
            PolicyRow policy = requirePolicyRowById(connection, ticket.slaPolicyId());
            Map<String, TargetRow> targets = loadTargets(connection, policy.id());
            TargetRow target = targets.get(ticket.severity());
            DueEvaluation first = evaluateDue(view.firstResponseDueAt(), view.firstResponseAt(), null,
                    ticket, target.firstResponseHours(), policy, holidays, zone, now, connection);
            DueEvaluation resolution = evaluateDue(view.resolutionDueAt(), view.resolvedAt(), ticket.status(),
                    ticket, target.resolutionHours(), policy, holidays, zone, now, connection);
            return new SlaEvaluation(view, first, resolution);
        });
    }

    /** Como {@link #evaluate}, mas registra WARNING/BREACHED na trilha quando o nível muda. */
    public SlaEvaluation evaluateAndRecord(String number, OffsetDateTime now,
                                           Set<LocalDate> holidays, ZoneId zone) {
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            TicketSlaView view = ticketSla(connection, ticket.id());
            if (view.policyKey() == null || view.firstResponseDueAt() == null) {
                return new SlaEvaluation(view,
                        new DueEvaluation(null, view.firstResponseAt(), AlertLevel.NONE, 0),
                        new DueEvaluation(null, view.resolvedAt(), AlertLevel.NONE, 0));
            }
            PolicyRow policy = requirePolicyRowById(connection, ticket.slaPolicyId());
            Map<String, TargetRow> targets = loadTargets(connection, policy.id());
            TargetRow target = targets.get(ticket.severity());
            DueEvaluation first = evaluateDue(view.firstResponseDueAt(), view.firstResponseAt(), null,
                    ticket, target.firstResponseHours(), policy, holidays, zone, now, connection);
            DueEvaluation resolution = evaluateDue(view.resolutionDueAt(), view.resolvedAt(), ticket.status(),
                    ticket, target.resolutionHours(), policy, holidays, zone, now, connection);
            recordAlert(connection, ticket.id(), "FIRST_RESPONSE", first.level());
            recordAlert(connection, ticket.id(), "RESOLUTION", resolution.level());
            return new SlaEvaluation(view, first, resolution);
        });
    }

    private DueEvaluation evaluateDue(OffsetDateTime dueAt, OffsetDateTime doneAt, String status,
                                      TicketRow ticket, int targetHours, PolicyRow policy,
                                      Set<LocalDate> holidays, ZoneId zone, OffsetDateTime now,
                                      Connection connection) throws SQLException {
        if (dueAt == null) {
            return new DueEvaluation(null, doneAt, AlertLevel.NONE, 0);
        }
        if (doneAt != null) {
            return new DueEvaluation(dueAt, doneAt,
                    doneAt.isAfter(dueAt) ? AlertLevel.BREACHED : AlertLevel.MET, 1);
        }
        if ("CANCELLED".equals(status)) {
            return new DueEvaluation(dueAt, null, AlertLevel.NONE, 0);
        }
        ZonedDateTime created = toZoned(ticket.createdAt(), zone);
        ZonedDateTime at = now.atZoneSameInstant(zone);
        long elapsed;
        if (policy.calendar().equals("ROUND_THE_CLOCK")) {
            elapsed = Duration.between(created, at).toMinutes()
                    - pausedWallMinutes(connection, ticket.id(), at.toOffsetDateTime());
        } else {
            elapsed = SlaDeadlineCalculator.businessMinutesBetween(created, at, holidays,
                    policy.businessStartHour(), policy.businessEndHour())
                    - pausedBusinessMinutes(connection, ticket.id(), at.toOffsetDateTime(), policy, holidays, zone);
        }
        if (elapsed < 0) {
            elapsed = 0;
        }
        double fraction = targetHours <= 0 ? 0 : (double) elapsed / (targetHours * 60L);
        AlertLevel level = !now.isBefore(dueAt) ? AlertLevel.BREACHED
                : fraction >= ALERT_FRACTION ? AlertLevel.WARNING : AlertLevel.OK;
        return new DueEvaluation(dueAt, null, level, fraction);
    }

    private void recordAlert(Connection connection, UUID ticketId, String kind, AlertLevel level) throws SQLException {
        String eventType = switch (level) {
            case WARNING -> "SLA_WARNING";
            case BREACHED -> "SLA_BREACHED";
            default -> null;
        };
        if (eventType == null) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT event_type FROM support.ticket_event WHERE tenant_id = ? AND ticket_id = ?"
                        + " AND event_type IN ('SLA_WARNING', 'SLA_BREACHED') AND detail = ?"
                        + " ORDER BY created_at DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            ps.setString(3, kind);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && eventType.equals(rs.getString(1))) {
                    return;
                }
            }
        }
        insertEvent(connection, ticketId, eventType, "SYSTEM", null, null, null, kind);
    }

    /** Primeira resposta do agente: só a primeira conta (COALESCE). */
    public TicketSlaView markFirstResponse(String number, OffsetDateTime at) {
        if (at == null) {
            throw new IllegalArgumentException("O instante da primeira resposta e obrigatorio");
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket SET first_response_at = COALESCE(first_response_at, ?)"
                            + " WHERE id = ?")) {
                ps.setTimestamp(1, Timestamp.from(at.toInstant()));
                ps.setObject(2, ticket.id());
                ps.executeUpdate();
            }
            if (ticket.firstResponseAt() == null) {
                insertEvent(connection, ticket.id(), "FIRST_RESPONSE", "AGENT", null, null, null, null);
            }
            return ticketSla(connection, ticket.id());
        });
    }

    // ------------------------------------------------------------------
    // Pausa
    // ------------------------------------------------------------------

    /** Abre a pausa do relógio (entrada em WAITING_CUSTOMER); recusa pausa sobre pausa. */
    public TicketSlaView startPause(String number, OffsetDateTime at) {
        if (at == null) {
            throw new IllegalArgumentException("O instante da pausa e obrigatorio");
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            if ("CLOSED".equals(ticket.status()) || "CANCELLED".equals(ticket.status())) {
                throw new ApiException.Conflict("data-conflict",
                        "Chamado encerrado nao pausa o relogio.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM support.ticket_sla_pause WHERE tenant_id = ? AND ticket_id = ?"
                            + " AND resumed_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ApiException.Conflict("data-conflict",
                                "O relogio deste chamado ja esta pausado.");
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.ticket_sla_pause (tenant_id, ticket_id, paused_at) VALUES (?, ?, ?)")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                ps.setTimestamp(3, Timestamp.from(at.toInstant()));
                ps.executeUpdate();
            }
            insertEvent(connection, ticket.id(), "SLA_PAUSED", "SYSTEM", null, null, null, null);
            return ticketSla(connection, ticket.id());
        });
    }

    /**
     * Fecha a pausa e estende os prazos pelos minutos úteis dela (sem
     * prazo aplicado, só fecha a pausa). Recusa quando não há pausa aberta.
     */
    public TicketSlaView endPauseAndExtend(String number, OffsetDateTime at,
                                           Set<LocalDate> holidays, ZoneId zone) {
        if (at == null || zone == null) {
            throw new IllegalArgumentException("O instante e o fuso da retomada sao obrigatorios");
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            UUID pauseId;
            OffsetDateTime pausedAt;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, paused_at FROM support.ticket_sla_pause WHERE tenant_id = ? AND ticket_id = ?"
                            + " AND resumed_at IS NULL ORDER BY paused_at LIMIT 1")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new ApiException.Conflict("data-conflict",
                                "Nao ha pausa aberta neste chamado.");
                    }
                    pauseId = (UUID) rs.getObject(1);
                    pausedAt = toOffset(rs.getTimestamp(2));
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket_sla_pause SET resumed_at = ? WHERE id = ?")) {
                ps.setTimestamp(1, Timestamp.from(at.toInstant()));
                ps.setObject(2, pauseId);
                ps.executeUpdate();
            }
            if (ticket.slaPolicyId() != null && ticket.firstResponseDueAt() != null) {
                PolicyRow policy = requirePolicyRowById(connection, ticket.slaPolicyId());
                long business;
                if (policy.calendar().equals("ROUND_THE_CLOCK")) {
                    business = Duration.between(pausedAt, at).toMinutes();
                } else {
                    business = SlaDeadlineCalculator.businessMinutesBetween(
                            pausedAt.atZoneSameInstant(zone), at.atZoneSameInstant(zone),
                            holidays, policy.businessStartHour(), policy.businessEndHour());
                }
                if (business > 0) {
                    ZonedDateTime firstDue = toZoned(ticket.firstResponseDueAt(), zone);
                    ZonedDateTime resolutionDue = toZoned(ticket.resolutionDueAt(), zone);
                    if (policy.calendar().equals("ROUND_THE_CLOCK")) {
                        firstDue = firstDue.plusMinutes(business);
                        resolutionDue = resolutionDue.plusMinutes(business);
                    } else {
                        firstDue = SlaDeadlineCalculator.addBusinessMinutes(firstDue, business,
                                holidays, policy.businessStartHour(), policy.businessEndHour());
                        resolutionDue = SlaDeadlineCalculator.addBusinessMinutes(resolutionDue, business,
                                holidays, policy.businessStartHour(), policy.businessEndHour());
                    }
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE support.ticket SET first_response_due_at = ?, resolution_due_at = ?,"
                                    + " updated_at = NOW() WHERE id = ?")) {
                        ps.setTimestamp(1, Timestamp.from(firstDue.toInstant()));
                        ps.setTimestamp(2, Timestamp.from(resolutionDue.toInstant()));
                        ps.setObject(3, ticket.id());
                        ps.executeUpdate();
                    }
                }
            }
            insertEvent(connection, ticket.id(), "SLA_RESUMED", "SYSTEM", null, null, null, null);
            return ticketSla(connection, ticket.id());
        });
    }

    // ------------------------------------------------------------------
    // CSAT
    // ------------------------------------------------------------------

    /** Avaliação do solicitante; nota 1 ou 2 abre revisão. */
    public CsatView submitCsat(String number, UUID requesterId, boolean canSeeAll,
                               int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw ApiException.Validation.of(new ApiException.FieldError("rating", "invalid",
                    "A nota vai de 1 a 5."));
        }
        if (comment != null && comment.length() > 1000) {
            throw ApiException.Validation.of(new ApiException.FieldError("comment", "invalid",
                    "Comentario de ate 1000 caracteres."));
        }
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            if (!canSeeAll && (requesterId == null || !requesterId.equals(ticket.requesterId()))) {
                throw new ApiException.NotFound("O chamado não existe.");
            }
            if (!"RESOLVED".equals(ticket.status()) && !"CLOSED".equals(ticket.status())) {
                throw new ApiException.Conflict("data-conflict",
                        "So chamado resolvido pode ser avaliado.");
            }
            if (ticket.resolvedAt() == null
                    || OffsetDateTime.now(clock).isAfter(ticket.resolvedAt().plusDays(CSAT_WINDOW_DAYS))) {
                throw new ApiException.Conflict("data-conflict",
                        "O prazo de 7 dias para avaliar expirou.");
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM support.csat_response WHERE tenant_id = ? AND ticket_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new ApiException.Conflict("data-conflict",
                                "Este chamado ja foi avaliado.");
                    }
                }
            }
            UUID id = UUID.randomUUID();
            boolean reviewRequired = rating <= 2;
            OffsetDateTime respondedAt = OffsetDateTime.now(clock);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO support.csat_response (id, tenant_id, ticket_id, rating, comment,"
                            + " review_required, responded_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setObject(1, id);
                ps.setObject(2, tenantId);
                ps.setObject(3, ticket.id());
                ps.setInt(4, rating);
                setText(ps, 5, comment == null ? null : comment.strip());
                ps.setBoolean(6, reviewRequired);
                ps.setTimestamp(7, Timestamp.from(respondedAt.toInstant()));
                ps.executeUpdate();
            }
            insertEvent(connection, ticket.id(), "CSAT_RECEIVED", "REQUESTER", requesterId,
                    null, null, "rating " + rating);
            return new CsatView(id, ticket.id(), ticket.number(), rating,
                    comment == null ? null : comment.strip(), reviewRequired, respondedAt);
        });
    }

    /** Avaliação do chamado, ou null quando ainda não avaliado. */
    public CsatView csatOf(String number) {
        return inTx(connection -> {
            TicketRow ticket = requireTicket(connection, number);
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, rating, comment, review_required, responded_at FROM support.csat_response"
                            + " WHERE tenant_id = ? AND ticket_id = ?")) {
                ps.setObject(1, tenantId);
                ps.setObject(2, ticket.id());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    return new CsatView((UUID) rs.getObject(1), ticket.id(), ticket.number(),
                            rs.getInt(2), rs.getString(3), rs.getBoolean(4),
                            toOffset(rs.getTimestamp(5)));
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Relatório mensal
    // ------------------------------------------------------------------

    /**
     * Relatório do mês (coorte: chamados ABERTOS no mês), por severidade.
     * Aderência = resolvidos dentro do prazo de solução / resolvidos com
     * prazo aplicado; multa estimada = brechas × multa% × valor mensal,
     * só quando {@code monthlyValue} é informado.
     */
    public MonthlyReport monthlyReport(LocalDate month, BigDecimal monthlyValue) {
        if (month == null) {
            throw new IllegalArgumentException("O mes do relatorio e obrigatorio");
        }
        LocalDate start = month.withDayOfMonth(1);
        LocalDate end = start.plusMonths(1);
        return inTx(connection -> {
            Map<String, Acc> acc = new LinkedHashMap<>();
            for (String severity : SEVERITIES) {
                acc.put(severity, new Acc());
            }
            int opened = 0;
            int resolved = 0;
            List<Double> csatAll = new ArrayList<>();
            int csatCount = 0;
            BigDecimal penaltyTotal = BigDecimal.ZERO;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT t.id, t.severity, t.status, t.created_at, t.resolved_at, t.first_response_at,"
                            + " t.resolution_due_at, t.sla_policy_id, c.rating, c.review_required,"
                            + " tgt.penalty_pct FROM support.ticket t"
                            + " LEFT JOIN support.csat_response c ON c.tenant_id = t.tenant_id AND c.ticket_id = t.id"
                            + " LEFT JOIN support.sla_policy_target tgt ON tgt.tenant_id = t.tenant_id"
                            + " AND tgt.policy_id = t.sla_policy_id AND tgt.severity = t.severity"
                            + " WHERE t.tenant_id = ? AND t.created_at >= ? AND t.created_at < ?"
                            + " ORDER BY t.created_at")) {
                ps.setObject(1, tenantId);
                ps.setTimestamp(2, Timestamp.from(start.atStartOfDay(ZoneOffset.UTC).toInstant()));
                ps.setTimestamp(3, Timestamp.from(end.atStartOfDay(ZoneOffset.UTC).toInstant()));
                Map<UUID, Row> rows = new LinkedHashMap<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID id = (UUID) rs.getObject(1);
                        Object ratingRaw = rs.getObject(9);
                        rows.put(id, new Row(id, rs.getString(2), rs.getString(3),
                                toOffset(rs.getTimestamp(4)), toOffset(rs.getTimestamp(5)),
                                toOffset(rs.getTimestamp(6)), toOffset(rs.getTimestamp(7)),
                                ratingRaw == null ? null : ((Number) ratingRaw).intValue(),
                                rs.getBoolean(10), rs.getBigDecimal(11)));
                    }
                }
                Map<UUID, Long> paused = pausedWallBeforeResolution(connection, rows);
                for (Row row : rows.values()) {
                    Acc a = acc.get(row.severity());
                    if (a == null) {
                        continue;
                    }
                    opened++;
                    a.opened++;
                    boolean isResolved = "RESOLVED".equals(row.status()) || "CLOSED".equals(row.status());
                    if (isResolved && row.resolvedAt() != null) {
                        resolved++;
                        a.resolved++;
                        if (row.resolutionDueAt() != null) {
                            a.resolvedWithDue++;
                            if (!row.resolvedAt().isAfter(row.resolutionDueAt())) {
                                a.resolvedWithinDue++;
                            } else {
                                a.breachedResolution++;
                                if (monthlyValue != null && row.penaltyPct() != null) {
                                    BigDecimal estimate = monthlyValue.multiply(row.penaltyPct())
                                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                                    a.penaltyEstimate = a.penaltyEstimate.add(estimate);
                                    penaltyTotal = penaltyTotal.add(estimate);
                                }
                            }
                        }
                        long mttr = Duration.between(row.createdAt(), row.resolvedAt()).toMinutes()
                                - paused.getOrDefault(row.id(), 0L);
                        a.mttr.add((double) Math.max(mttr, 0));
                    }
                    if (row.firstResponseAt() != null) {
                        a.frt.add((double) Duration.between(row.createdAt(), row.firstResponseAt()).toMinutes());
                    }
                    if (row.rating() != null) {
                        a.csat.add(row.rating().doubleValue());
                        csatAll.add(row.rating().doubleValue());
                        csatCount++;
                        if (row.reviewRequired()) {
                            a.csatReviewCount++;
                        }
                    }
                }
            }
            List<SeverityReport> bySeverity = new ArrayList<>();
            for (String severity : SEVERITIES) {
                Acc a = acc.get(severity);
                bySeverity.add(new SeverityReport(severity, a.opened, a.resolved, a.resolvedWithinDue,
                        a.breachedResolution,
                        a.resolvedWithDue == 0 ? null : (double) a.resolvedWithinDue * 100 / a.resolvedWithDue,
                        avg(a.frt), avg(a.mttr), avg(a.csat), a.csat.size(), a.csatReviewCount,
                        monthlyValue == null ? null : a.penaltyEstimate));
            }
            Double adherence = null;
            int withDue = bySeverity.stream().mapToInt(s -> s.resolvedWithinDue() + s.breachedResolution()).sum();
            int within = bySeverity.stream().mapToInt(SeverityReport::resolvedWithinDue).sum();
            if (withDue > 0) {
                adherence = (double) within * 100 / withDue;
            }
            return new MonthlyReport(tenantId, start, opened, resolved, adherence,
                    avg(csatAll), csatCount,
                    monthlyValue == null ? null : penaltyTotal, bySeverity);
        });
    }

    private static Double avg(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static final class Acc {
        int opened;
        int resolved;
        int resolvedWithinDue;
        int resolvedWithDue;
        int breachedResolution;
        List<Double> frt = new ArrayList<>();
        List<Double> mttr = new ArrayList<>();
        List<Double> csat = new ArrayList<>();
        int csatReviewCount;
        BigDecimal penaltyEstimate = BigDecimal.ZERO;
    }

    private record Row(UUID id, String severity, String status, OffsetDateTime createdAt,
                       OffsetDateTime resolvedAt, OffsetDateTime firstResponseAt,
                       OffsetDateTime resolutionDueAt, Integer rating, boolean reviewRequired,
                       BigDecimal penaltyPct) {
    }

    // ------------------------------------------------------------------
    // Infra interna
    // ------------------------------------------------------------------

    private record PolicyRow(UUID id, String policyKey, String name, String calendar, String level,
                             int businessStartHour, int businessEndHour) {
    }

    private record TargetRow(String severity, int firstResponseHours, int resolutionHours,
                             BigDecimal adherencePct, BigDecimal penaltyPct) {
    }

    private record TicketRow(UUID id, String number, UUID requesterId, String severity, String status,
                             UUID slaPolicyId, OffsetDateTime createdAt, OffsetDateTime resolvedAt,
                             OffsetDateTime firstResponseAt, OffsetDateTime firstResponseDueAt,
                             OffsetDateTime resolutionDueAt) {
    }

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
                throw new IllegalStateException("SLA operation failed: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("SLA operation failed: " + e.getMessage(), e);
        }
    }

    private PolicyView policy(Connection connection, String policyKey) throws SQLException {
        PolicyRow row = requirePolicyRow(connection, policyKey);
        Map<String, TargetRow> targets = loadTargets(connection, row.id());
        List<TargetView> views = new ArrayList<>();
        for (String severity : SEVERITIES) {
            TargetRow target = targets.get(severity);
            if (target != null) {
                views.add(new TargetView(severity, target.firstResponseHours(), target.resolutionHours(),
                        target.adherencePct(), target.penaltyPct()));
            }
        }
        Map<String, String> overrides = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT impact, criticality, severity FROM support.sla_severity_matrix"
                        + " WHERE tenant_id = ? AND policy_id = ? ORDER BY impact, criticality")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, row.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    overrides.put(rs.getString(1) + ":" + rs.getString(2), rs.getString(3));
                }
            }
        }
        return new PolicyView(row.id(), row.policyKey(), row.name(), row.calendar(), row.level(),
                row.businessStartHour(), row.businessEndHour(), views, overrides);
    }

    private PolicyRow findPolicyRow(Connection connection, String policyKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, policy_key, name, calendar, level, business_start_hour, business_end_hour"
                        + " FROM support.sla_policy WHERE tenant_id = ? AND policy_key = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, policyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new PolicyRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7));
            }
        }
    }

    private PolicyRow requirePolicyRow(Connection connection, String policyKey) throws SQLException {
        PolicyRow row = policyKey == null ? null : findPolicyRow(connection, policyKey);
        if (row == null) {
            throw new ApiException.NotFound("A politica de SLA nao existe neste tenant.");
        }
        return row;
    }

    private PolicyRow requirePolicyRowById(Connection connection, UUID policyId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, policy_key, name, calendar, level, business_start_hour, business_end_hour"
                        + " FROM support.sla_policy WHERE tenant_id = ? AND id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("A politica de SLA nao existe neste tenant.");
                }
                return new PolicyRow((UUID) rs.getObject(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getInt(6), rs.getInt(7));
            }
        }
    }

    private UUID requirePolicyId(Connection connection, String policyKey) throws SQLException {
        return requirePolicyRow(connection, policyKey).id();
    }

    private Map<String, TargetRow> loadTargets(Connection connection, UUID policyId) throws SQLException {
        Map<String, TargetRow> out = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT severity, first_response_hours, resolution_hours, adherence_pct, penalty_pct"
                        + " FROM support.sla_policy_target WHERE tenant_id = ? AND policy_id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, policyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), new TargetRow(rs.getString(1), rs.getInt(2), rs.getInt(3),
                            rs.getBigDecimal(4), rs.getBigDecimal(5)));
                }
            }
        }
        return out;
    }

    private TicketRow requireTicket(Connection connection, String number) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, number, requester_id, severity, status, sla_policy_id, created_at, resolved_at,"
                        + " first_response_at, first_response_due_at, resolution_due_at"
                        + " FROM support.ticket WHERE tenant_id = ? AND number = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, number);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException.NotFound("O chamado não existe.");
                }
                return new TicketRow((UUID) rs.getObject(1), rs.getString(2), (UUID) rs.getObject(3),
                        rs.getString(4), rs.getString(5), (UUID) rs.getObject(6),
                        toOffset(rs.getTimestamp(7)), toOffset(rs.getTimestamp(8)),
                        toOffset(rs.getTimestamp(9)), toOffset(rs.getTimestamp(10)),
                        toOffset(rs.getTimestamp(11)));
            }
        }
    }

    private TicketSlaView ticketSla(Connection connection, UUID ticketId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT t.number, t.severity, t.status, p.policy_key, t.first_response_due_at,"
                        + " t.resolution_due_at, t.first_response_at, t.created_at, t.resolved_at,"
                        + " (SELECT COUNT(*) FROM support.ticket_sla_pause WHERE ticket_id = t.id),"
                        + " EXISTS (SELECT 1 FROM support.ticket_sla_pause WHERE ticket_id = t.id"
                        + " AND resumed_at IS NULL) FROM support.ticket t"
                        + " LEFT JOIN support.sla_policy p ON p.id = t.sla_policy_id"
                        + " WHERE t.tenant_id = ? AND t.id = ?")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new TicketSlaView(ticketId, rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), toOffset(rs.getTimestamp(5)), toOffset(rs.getTimestamp(6)),
                        toOffset(rs.getTimestamp(7)), toOffset(rs.getTimestamp(8)),
                        toOffset(rs.getTimestamp(9)), rs.getInt(10), rs.getBoolean(11));
            }
        }
    }

    /** Minutos úteis de todas as pausas até {@code until} (null = até agora, pelo relógio). */
    private long pausedBusinessMinutes(Connection connection, UUID ticketId, OffsetDateTime until,
                                       PolicyRow policy, Set<LocalDate> holidays, ZoneId zone) throws SQLException {
        if (policy.calendar().equals("ROUND_THE_CLOCK")) {
            return pausedWallMinutes(connection, ticketId, until);
        }
        long total = 0;
        OffsetDateTime end = until == null ? OffsetDateTime.now(clock) : until;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT paused_at, resumed_at FROM support.ticket_sla_pause"
                        + " WHERE tenant_id = ? AND ticket_id = ? ORDER BY paused_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OffsetDateTime pausedAt = toOffset(rs.getTimestamp(1));
                    OffsetDateTime resumedAt = toOffset(rs.getTimestamp(2));
                    OffsetDateTime stop = resumedAt == null || resumedAt.isAfter(end) ? end : resumedAt;
                    if (stop.isAfter(pausedAt)) {
                        total += SlaDeadlineCalculator.businessMinutesBetween(
                                pausedAt.atZoneSameInstant(zone), stop.atZoneSameInstant(zone),
                                holidays, policy.businessStartHour(), policy.businessEndHour());
                    }
                }
            }
        }
        return total;
    }

    /** Minutos de relógio de todas as pausas até {@code until} (null = até agora). */
    private long pausedWallMinutes(Connection connection, UUID ticketId, OffsetDateTime until) throws SQLException {
        long total = 0;
        OffsetDateTime end = until == null ? OffsetDateTime.now(clock) : until;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT paused_at, resumed_at FROM support.ticket_sla_pause"
                        + " WHERE tenant_id = ? AND ticket_id = ? ORDER BY paused_at")) {
            ps.setObject(1, tenantId);
            ps.setObject(2, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OffsetDateTime pausedAt = toOffset(rs.getTimestamp(1));
                    OffsetDateTime resumedAt = toOffset(rs.getTimestamp(2));
                    OffsetDateTime stop = resumedAt == null || resumedAt.isAfter(end) ? end : resumedAt;
                    if (stop.isAfter(pausedAt)) {
                        total += Duration.between(pausedAt, stop).toMinutes();
                    }
                }
            }
        }
        return total;
    }

    /**
     * Minutos de relógio pausados antes da resolução, por chamado: a pausa
     * aberta na resolução conta até ela; pausa após a resolução não desconta
     * nada do MTTR. Chamados sem resolução não entram no MTTR e ficam fora.
     */
    private Map<UUID, Long> pausedWallBeforeResolution(Connection connection,
                                                       Map<UUID, Row> rows) throws SQLException {
        Map<UUID, Long> out = new HashMap<>();
        List<UUID> ticketIds = new ArrayList<>(rows.keySet());
        if (ticketIds.isEmpty()) {
            return out;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT ticket_id, paused_at, resumed_at FROM support.ticket_sla_pause"
                        + " WHERE tenant_id = ? AND ticket_id IN (");
        for (int i = 0; i < ticketIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") ORDER BY paused_at");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setObject(1, tenantId);
            for (int i = 0; i < ticketIds.size(); i++) {
                ps.setObject(i + 2, ticketIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID ticketId = (UUID) rs.getObject(1);
                    Row row = rows.get(ticketId);
                    if (row == null || row.resolvedAt() == null) {
                        continue;
                    }
                    OffsetDateTime pausedAt = toOffset(rs.getTimestamp(2));
                    OffsetDateTime resumedAt = toOffset(rs.getTimestamp(3));
                    OffsetDateTime stop = resumedAt == null || resumedAt.isAfter(row.resolvedAt())
                            ? row.resolvedAt() : resumedAt;
                    if (stop.isAfter(pausedAt)) {
                        out.merge(ticketId, Duration.between(pausedAt, stop).toMinutes(), Long::sum);
                    }
                }
            }
        }
        return out;
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

    private void audit(Connection connection, UUID actorId, String actorRole, String action,
                       Map<String, String> details) throws SQLException {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
        }
        json.append("}");
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO platform.audit_event (tenant_id, actor_id, actor_role, action, details)"
                        + " VALUES (?, ?, ?, ?, ?::jsonb)")) {
            ps.setObject(1, tenantId);
            setUuid(ps, 2, actorId);
            setText(ps, 3, actorRole);
            ps.setString(4, action);
            ps.setString(5, json.toString());
            ps.executeUpdate();
        }
    }

    private static ZonedDateTime toZoned(OffsetDateTime value, ZoneId zone) {
        return value == null ? null : value.atZoneSameInstant(zone);
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
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
}
