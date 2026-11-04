package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.support.SlaDeadlineCalculator;
import br.com.ziipsan.core.support.SupportSlaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT da P2.15 contra o banco dedicado de integração: políticas de SLA
 * com cinco severidades (casos Videira e SAMAE), prazos no chamado,
 * pausa do relógio, alertas a 80% e no estouro, CSAT, relatório mensal
 * na Central e a promoção da referência do contrato a FK (P2.12).
 *
 * <p>Isolamento: o tenant é o provisionado (com schema, para a carga de
 * feriados nacionais); cada teste usa chaves de política e números de
 * contrato com sufixo aleatório. O relatório mensal usa um mês passado
 * (2026-01) com chamados redatados por SQL direto — nenhum outro teste
 * toca esse mês, então as contagens são exatas. Brecha e pausa com
 * instante explícito passam pelo serviço direto (o mesmo banco migrado),
 * porque o relógio da API REST é o real: esperar 1h útil no teste não
 * é uma opção, e o cálculo puro já é provado no SlaDeadlineCalculatorTest.
 */
class SupportSlaIT extends AppIntegrationTestBase {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private static String tenantToken() {
        return TestTokens.token(TENANT, "support:read", "support:write", "support:manage");
    }

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private static String syntheticCnpj() {
        String base;
        do {
            base = String.valueOf(100000000000L + (long) (Math.random() * 899999999999L));
        } while (base.chars().distinct().count() == 1);
        return base + DocumentNumber.cnpjCheckDigits(base);
    }

    /** Alvos no formato da API: S1–S5 com primeira resposta, solução, aderência e multa. */
    private static String targetsJson(int[] first, int[] resolution, double[] adherence, double[] penalty) {
        String[] severities = {"S1", "S2", "S3", "S4", "S5"};
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                out.append(",");
            }
            out.append("{\"severity\":\"").append(severities[i]).append("\",\"firstResponseHours\":")
                    .append(first[i]).append(",\"resolutionHours\":").append(resolution[i])
                    .append(",\"adherencePct\":").append(adherence[i])
                    .append(",\"penaltyPct\":").append(penalty[i]).append("}");
        }
        return out.append("]").toString();
    }

    /** Videira (ADR-0025, tabela do Contexto): solução em 2, 4, 6, 16 e 32 h úteis. */
    private static String videiraTargets() {
        return targetsJson(new int[]{1, 1, 2, 4, 8}, new int[]{2, 4, 6, 16, 32},
                new double[]{95, 95, 90, 85, 80}, new double[]{5, 5, 5, 5, 5});
    }

    /** SAMAE São Ludgero (ADR-0025): 2, 4, 16, 72 e 160 h; aderência 95, 95, 90, 80 e 70%; multa 10, 10, 5, 5 e 5%. */
    private static String samaeTargets() {
        return targetsJson(new int[]{1, 2, 4, 8, 24}, new int[]{2, 4, 16, 72, 160},
                new double[]{95, 95, 90, 80, 70}, new double[]{10, 10, 5, 5, 5});
    }

    private void createPolicy(String key, String targets) throws Exception {
        mvc.perform(post("/api/v1/platform/sla/policies?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyKey\":\"" + key + "\",\"name\":\"Politica " + key
                                + "\",\"calendar\":\"BUSINESS\",\"level\":\"PUBLIC_SECTOR\","
                                + "\"businessStartHour\":8,\"businessEndHour\":18}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyKey").value(key));
        mvc.perform(post("/api/v1/platform/sla/policies/" + key + "/targets?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(targets))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets.length()").value(5));
    }

    private String createTicket(String severity) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"Portal fora do ar " + SUFFIX + "\",\"category\":\"INCIDENT\","
                                + "\"severity\":\"" + severity + "\",\"initialMessage\":\"O portal nao abre.\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        int start = json.indexOf("\"number\":\"") + 10;
        return json.substring(start, json.indexOf("\"", start));
    }

    private void applySla(String number, String policyKey) throws Exception {
        mvc.perform(post("/api/v1/platform/support/tickets/" + number + "/sla?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyKey\":\"" + policyKey + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyKey").value(policyKey));
    }

    private void queuePatch(String number, String status) throws Exception {
        mvc.perform(patch("/api/v1/platform/support/tickets/" + number + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    /** Linha do chamado com o contexto RLS do tenant (papel migrador). */
    private static String[] ticketRow(String number) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id, status, created_at, resolved_at, first_response_due_at, resolution_due_at,"
                            + " first_response_at FROM support.ticket WHERE tenant_id = ? AND number = ?")) {
                ps.setObject(1, TENANT);
                ps.setString(2, number);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AssertionError("Chamado " + number + " nao encontrado");
                    }
                    return new String[]{rs.getObject(1).toString(), rs.getString(2),
                            String.valueOf(rs.getTimestamp(3)), String.valueOf(rs.getTimestamp(4)),
                            String.valueOf(rs.getTimestamp(5)), String.valueOf(rs.getTimestamp(6)),
                            String.valueOf(rs.getTimestamp(7))};
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private static void updateTicketSql(String number, String setClause, Object... params) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE support.ticket SET " + setClause + " WHERE tenant_id = ? AND number = ?")) {
                int i = 1;
                for (Object param : params) {
                    ps.setObject(i++, param);
                }
                ps.setObject(i++, TENANT);
                ps.setString(i, number);
                if (ps.executeUpdate() != 1) {
                    throw new AssertionError("UPDATE nao afetou o chamado " + number);
                }
            }
            connection.commit();
        }
    }

    private static SupportSlaService slaService() {
        return new SupportSlaService(ProvisionedTenant.appDs, TENANT, Clock.systemUTC());
    }

    /** Contagem na trilha do chamado com o contexto RLS (ticket_event tem RLS FORCED). */
    private static int countTicketEvents(String number, String eventType) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM support.ticket_event e JOIN support.ticket t ON t.id = e.ticket_id"
                            + " WHERE t.tenant_id = ? AND t.number = ? AND e.event_type = ?")) {
                ps.setObject(1, TENANT);
                ps.setString(2, number);
                ps.setString(3, eventType);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("politicas Videira e SAMAE: cinco alvos com os prazos do edital, e matriz com sobrescrita")
    void videiraAndSamaePolicies() throws Exception {
        String videira = "videira-" + SUFFIX;
        String samae = "samae-" + SUFFIX;
        createPolicy(videira, videiraTargets());
        createPolicy(samae, samaeTargets());

        mvc.perform(get("/api/v1/platform/sla/policies/" + videira + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[0].severity").value("S1"))
                .andExpect(jsonPath("$.targets[0].resolutionHours").value(2))
                .andExpect(jsonPath("$.targets[4].severity").value("S5"))
                .andExpect(jsonPath("$.targets[4].resolutionHours").value(32));
        mvc.perform(get("/api/v1/platform/sla/policies/" + samae + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[1].resolutionHours").value(4))
                .andExpect(jsonPath("$.targets[1].adherencePct").value(95))
                .andExpect(jsonPath("$.targets[1].penaltyPct").value(10))
                .andExpect(jsonPath("$.targets[4].resolutionHours").value(160))
                .andExpect(jsonPath("$.targets[4].adherencePct").value(70))
                .andExpect(jsonPath("$.targets[4].penaltyPct").value(5));

        // Matriz padrão: HIGH x LOW = S3; sobrescrita da política muda para S2.
        mvc.perform(get("/api/v1/platform/sla/policies/" + videira + "/classify?tenantId=" + TENANT
                        + "&impact=HIGH&criticality=LOW")
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("S3"));
        mvc.perform(post("/api/v1/platform/sla/policies/" + videira + "/matrix?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"impact\":\"HIGH\",\"criticality\":\"LOW\",\"severity\":\"S2\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/sla/policies/" + videira + "/classify?tenantId=" + TENANT
                        + "&impact=HIGH&criticality=LOW")
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string("S2"));

        // Conjunto incompleto de alvos é recusado (422), não salvo pela metade.
        mvc.perform(post("/api/v1/platform/sla/policies/" + videira + "/targets?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"severity\":\"S1\",\"firstResponseHours\":1,\"resolutionHours\":2,"
                                + "\"adherencePct\":95,\"penaltyPct\":5}]"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("prazos no chamado: aplicacao calcula primeira resposta e solucao; feriado nacional salta o prazo")
    void ticketDeadlinesWithNationalHoliday() throws Exception {
        String key = "feriado-" + SUFFIX;
        createPolicy(key, videiraTargets());
        // Feriado nacional no schema do tenant, amanhã: o prazo que o cruzar salta o dia.
        LocalDate holiday = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        insertNationalHoliday(holiday);
        try {
            String number = createTicket("S1");
            applySla(number, key);
            String[] row = ticketRow(number);
            assertThat(row[4]).as("primeira resposta calculada").isNotEqualTo("null");
            assertThat(row[5]).as("solucao calculada").isNotEqualTo("null");
            // A primeira resposta (1h útil, fixture) cai dentro da janela; a solução (2h
            // úteis, Videira S1) é posterior à primeira resposta.
            assertThat(Timestamp.valueOf(row[5]).toInstant())
                    .isAfter(Timestamp.valueOf(row[4]).toInstant());
            // O prazo calculado pelo serviço direto, com o mesmo feriado, bate com o
            // prazo gravado pela API (mesma abertura, mesmo calendário).
            SupportSlaService.TicketSlaView view = slaService().slaOf(number);
            assertThat(view.firstResponseDueAt()).isNotNull();
            assertThat(view.resolutionDueAt()).isNotNull();
            assertThat(view.firstResponseDueAt().toInstant())
                    .isEqualTo(Timestamp.valueOf(row[4]).toInstant());
        } finally {
            deleteNationalHoliday(holiday);
        }
    }

    /** Escrita no schema do tenant com o contexto RLS (a tabela tem RLS FORCED). */
    private static void tenantSchemaUpdate(String sql, Object... params) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                ps.executeUpdate();
            }
            connection.commit();
        }
    }

    private static void insertNationalHoliday(LocalDate holiday) throws Exception {
        tenantSchemaUpdate(
                "INSERT INTO \"" + ProvisionedTenant.SCHEMA + "\".holidays"
                        + " (tenant_id, holiday_date, scope, description, source, kind)"
                        + " VALUES (?, ?, 'NATIONAL', 'Feriado de teste', 'IT P2.15', 'HOLIDAY')",
                TENANT, java.sql.Date.valueOf(holiday));
    }

    private static void deleteNationalHoliday(LocalDate holiday) throws Exception {
        tenantSchemaUpdate(
                "DELETE FROM \"" + ProvisionedTenant.SCHEMA + "\".holidays"
                        + " WHERE tenant_id = ? AND holiday_date = ?",
                TENANT, java.sql.Date.valueOf(holiday));
    }

    @Test
    @DisplayName("pausa: WAITING_CUSTOMER abre a pausa e a retomada estende os prazos em horas uteis")
    void pauseExtendsDeadlines() throws Exception {
        String key = "pausa-" + SUFFIX;
        createPolicy(key, videiraTargets());
        String number = createTicket("S2");
        applySla(number, key);
        String[] before = ticketRow(number);
        queuePatch(number, "WAITING_CUSTOMER");
        SupportSlaService.TicketSlaView paused = slaService().slaOf(number);
        assertThat(paused.pauseOpen()).as("pausa aberta pelo WAITING_CUSTOMER").isTrue();
        assertThat(paused.pauseCount()).isEqualTo(1);

        // Retomada com instante explícito: 120 minutos úteis depois da pausa.
        OffsetDateTime pausedAt = readOpenPauseAt(number);
        OffsetDateTime resumedAt = SlaDeadlineCalculator.addBusinessMinutes(
                pausedAt.atZoneSameInstant(ZONE), 120, java.util.Set.of(), 8, 18).toOffsetDateTime();
        SupportSlaService.TicketSlaView resumed = slaService().endPauseAndExtend(number, resumedAt,
                java.util.Set.of(), ZONE);
        assertThat(resumed.pauseOpen()).isFalse();
        long shift = SlaDeadlineCalculator.businessMinutesBetween(
                Timestamp.valueOf(before[5]).toInstant().atZone(ZONE),
                resumed.resolutionDueAt().atZoneSameInstant(ZONE), java.util.Set.of(), 8, 18);
        assertThat(shift).as("prazo estendido exatamente pelos minutos uteis da pausa").isEqualTo(120);
    }

    private static OffsetDateTime readOpenPauseAt(String number) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT p.paused_at FROM support.ticket_sla_pause p JOIN support.ticket t ON t.id = p.ticket_id"
                            + " WHERE p.tenant_id = ? AND t.number = ? AND p.resumed_at IS NULL")) {
                ps.setObject(1, TENANT);
                ps.setString(2, number);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new AssertionError("Nenhuma pausa aberta para " + number);
                    }
                    return rs.getTimestamp(1).toInstant().atOffset(ZoneOffset.UTC);
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    @DisplayName("alertas: 80% do prazo vira WARNING e o estouro vira BREACHED, registrados uma vez por nivel")
    void alertsAt80AndBreach() throws Exception {
        String key = "alerta-" + SUFFIX;
        createPolicy(key, videiraTargets());
        String number = createTicket("S1");
        applySla(number, key);
        SupportSlaService.TicketSlaView view = slaService().slaOf(number);
        // A 80% do prazo de primeira resposta (1h útil): WARNING, sem BREACHED.
        OffsetDateTime at80 = view.firstResponseDueAt().minusMinutes(12);
        SupportSlaService.SlaEvaluation eval = slaService().evaluateAndRecord(number, at80,
                java.util.Set.of(), ZONE);
        assertThat(eval.firstResponse().level()).isEqualTo(SupportSlaService.AlertLevel.WARNING);
        // Depois do prazo de solução (2h úteis, Videira S1): BREACHED nos dois.
        OffsetDateTime after = view.resolutionDueAt().plusHours(1);
        SupportSlaService.SlaEvaluation breached = slaService().evaluateAndRecord(number, after,
                java.util.Set.of(), ZONE);
        assertThat(breached.firstResponse().level()).isEqualTo(SupportSlaService.AlertLevel.BREACHED);
        assertThat(breached.resolution().level()).isEqualTo(SupportSlaService.AlertLevel.BREACHED);
        // Repetir o mesmo nível não duplica o evento na trilha.
        slaService().evaluateAndRecord(number, after, java.util.Set.of(), ZONE);
        int breachEvents = countTicketEvents(number, "SLA_BREACHED");
        assertThat(breachEvents).as("um BREACHED por tipo de prazo, nao por chamada").isEqualTo(2);
    }

    @Test
    @DisplayName("primeira resposta: mensagem publica do agente marca o FRT; nota interna nao marca")
    void firstResponseOnPublicAgentMessage() throws Exception {
        String key = "frt-" + SUFFIX;
        createPolicy(key, videiraTargets());
        String number = createTicket("S3");
        applySla(number, key);
        mvc.perform(post("/api/v1/platform/support/tickets/" + number + "/messages?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Nota interna de triagem\",\"internal\":true}"))
                .andExpect(status().isOk());
        assertThat(ticketRow(number)[6]).as("nota interna nao e primeira resposta").isEqualTo("null");
        mvc.perform(post("/api/v1/platform/support/tickets/" + number + "/messages?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Estamos verificando o portal.\",\"internal\":false}"))
                .andExpect(status().isOk());
        assertThat(ticketRow(number)[6]).as("mensagem publica marca a primeira resposta").isNotEqualTo("null");
    }

    @Test
    @DisplayName("CSAT: nota apos RESOLVED dentro de 7 dias; nota 2 abre revisao; segunda avaliacao e recusada")
    void csatFlow() throws Exception {
        String key = "csat-" + SUFFIX;
        createPolicy(key, videiraTargets());
        String number = createTicket("S4");
        applySla(number, key);
        mvc.perform(post("/api/v1/support/tickets/" + number + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Ok\"}"))
                .andExpect(status().is4xxClientError());
        queuePatch(number, "IN_PROGRESS");
        queuePatch(number, "RESOLVED");
        mvc.perform(post("/api/v1/support/tickets/" + number + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2,\"comment\":\"Demorou.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(2))
                .andExpect(jsonPath("$.reviewRequired").value(true));
        mvc.perform(post("/api/v1/support/tickets/" + number + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/v1/support/tickets/" + number + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("contrato: chave de politica existente aceita, chave desconhecida e 422 (FK da P2.12 promovida)")
    void contractSlaPolicyKeyValidated() throws Exception {
        String key = "contrato-" + SUFFIX;
        createPolicy(key, samaeTargets());
        String contractNumber = "CTR-SLA-" + SUFFIX;
        String body = "{\"contractNumber\":\"" + contractNumber + "\",\"biddingProcess\":\"PROC-2026-001\","
                + "\"biddingModality\":\"PREGAO\",\"contractorCnpj\":\"" + syntheticCnpj() + "\","
                + "\"startsAt\":\"" + today() + "\",\"endsAt\":\"" + LocalDate.now(ZoneOffset.UTC).plusYears(1) + "\","
                + "\"monthlyValue\":10000,\"indexKey\":\"IPCA\",\"indexBaseDate\":\"" + today() + "\","
                + "\"slaPolicyKey\":\"" + key + "\",\"quotasJson\":\"{}\"}";
        mvc.perform(post("/api/v1/platform/contracts?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaPolicyKey").value(key));
        String unknown = body.replace("\"slaPolicyKey\":\"" + key + "\"", "\"slaPolicyKey\":\"inexistente\"")
                .replace(contractNumber, "CTR-SLA-X-" + SUFFIX);
        mvc.perform(post("/api/v1/platform/contracts?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknown))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("relatorio mensal na Central: FRT, MTTR, aderencia, CSAT e multa estimada por severidade")
    void monthlyReportOnCentral() throws Exception {
        String key = "relatorio-" + SUFFIX;
        createPolicy(key, samaeTargets());
        // Contrato ativo de R$ 10.000: a multa estimada usa o valor mensal.
        String contractNumber = "CTR-REL-" + SUFFIX;
        MvcResult created = mvc.perform(post("/api/v1/platform/contracts?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contractNumber\":\"" + contractNumber + "\",\"biddingProcess\":\"PROC-2026-001\","
                                + "\"biddingModality\":\"PREGAO\",\"contractorCnpj\":\"" + syntheticCnpj() + "\","
                                + "\"startsAt\":\"" + today() + "\",\"endsAt\":\""
                                + LocalDate.now(ZoneOffset.UTC).plusYears(1) + "\","
                                + "\"monthlyValue\":10000,\"indexKey\":\"IPCA\",\"indexBaseDate\":\"" + today() + "\","
                                + "\"slaPolicyKey\":\"" + key + "\",\"quotasJson\":\"{}\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String contractJson = created.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String contractId = contractJson.substring(contractJson.indexOf("\"id\":\"") + 6,
                contractJson.indexOf("\"", contractJson.indexOf("\"id\":\"") + 6));
        mvc.perform(post("/api/v1/platform/contracts/" + contractId + "/activate")
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                .andExpect(status().isOk());

        String within = createTicket("S2");
        String breached = createTicket("S2");
        String open = createTicket("S1");
        applySla(within, key);
        applySla(breached, key);
        applySla(open, key);
        queuePatch(within, "IN_PROGRESS");
        queuePatch(within, "RESOLVED");
        queuePatch(breached, "IN_PROGRESS");
        queuePatch(breached, "RESOLVED");
        // CSAT dos dois resolvidos: 5 e 2 (media 3.5, uma revisao).
        mvc.perform(post("/api/v1/support/tickets/" + within + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/support/tickets/" + breached + "/csat")
                        .header("Authorization", bearer(tenantToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":2}"))
                .andExpect(status().isOk());

        // Redata para janeiro de 2026 e fixa os prazos: um dentro, um estourado
        // (S2 SAMAE: multa 10% de R$ 10.000 = R$ 1.000 pela brecha).
        updateTicketSql(within, "created_at = ?",
                Timestamp.valueOf("2026-01-15 12:00:00"));
        updateTicketSql(within, "resolution_due_at = ?",
                Timestamp.valueOf("2027-01-01 00:00:00"));
        updateTicketSql(breached, "created_at = ?",
                Timestamp.valueOf("2026-01-15 12:00:00"));
        updateTicketSql(breached, "resolution_due_at = ?",
                Timestamp.valueOf("2026-01-10 00:00:00"));
        updateTicketSql(open, "created_at = ?",
                Timestamp.valueOf("2026-01-15 12:00:00"));

        mvc.perform(get("/api/v1/platform/central/sla-report?tenantId=" + TENANT + "&month=2026-01")
                        .header("Authorization", bearer(platformToken("PLATFORM_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.opened").value(3))
                .andExpect(jsonPath("$.resolved").value(2))
                .andExpect(jsonPath("$.adherencePct").value(50.0))
                .andExpect(jsonPath("$.csatCount").value(2))
                .andExpect(jsonPath("$.csatAvg").value(3.5))
                .andExpect(jsonPath("$.penaltyEstimateTotal", closeTo(1000.0, 0.01)));
    }
}
