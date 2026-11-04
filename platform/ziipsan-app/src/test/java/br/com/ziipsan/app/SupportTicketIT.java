package br.com.ziipsan.app;

import br.com.ziipsan.core.document.DocumentNumber;
import br.com.ziipsan.core.support.TicketService;
import br.com.ziipsan.platform.documents.TicketAttachmentService;
import br.com.ziipsan.platform.infra.scan.FileScanner;
import br.com.ziipsan.platform.infra.storage.LocalFileObjectStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Year;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT da P1.23 contra o banco dedicado de integração (porta própria via
 * run-single-it.sh; IT em /var/tmp, apagado ao fim): numeração SUP-AAAA-NNNNNN
 * sem duplicata, conversa e estados, 404 para outro tenant, marca de dado
 * pessoal com máscara na lista, fila da Central, anexos pela pipeline da
 * P1.20 (quarentena → varredura → liberado/bloqueado) e eventos na outbox
 * sem conteúdo nem anexo na carga.
 *
 * <p>O varredor real (clamd) não existe neste sandbox — o CLEAN/INFECTED é
 * provado com um varredor falso da interface (como o DocumentPipelineIT da
 * P1.20); pela API REST, o varredor do ambiente é "indisponível" e o anexo
 * fica NOT_SCANNED (fail-closed). O que falta para provar contra um clamd
 * real está no javadoc de ClamAvScanner.
 */
class SupportTicketIT extends AppIntegrationTestBase {

    private static final UUID TENANT = ProvisionedTenant.TENANT_ID;
    private static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    private static final byte[] CLEAN_PDF = "%PDF-1.7\nconteudo limpo sintético".getBytes(StandardCharsets.UTF_8);
    private static final byte[] EICAR = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(StandardCharsets.US_ASCII);

    private static Path storageRoot;

    private static String tenantToken() {
        return TestTokens.token(TENANT, "support:read", "support:write", "support:manage");
    }

    private static String otherUserToken() {
        return TestTokens.token(TENANT, "support:read", "support:write");
    }

    private static String platformToken(String role) {
        return TestTokens.token(null, List.of(role), b -> b.claim("amr", List.of("otp")));
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private MvcResult createTicket(String token, String subject, String body) throws Exception {
        return mvc.perform(post("/api/v1/support/tickets")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"%s","category":"INCIDENT","severity":"S3","initialMessage":"%s"}"""
                                .formatted(subject, body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String numberOf(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        int start = json.indexOf("\"number\":\"") + 10;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    /** Linhas da outbox do tenant, lidas como migrador com o contexto RLS do tenant. */
    private static List<String[]> outboxRows(String type) throws Exception {
        try (Connection connection = ProvisionedTenant.migratorDs.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement set = connection.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, true)")) {
                set.setString(1, TENANT.toString());
                set.execute();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT type, payload::text FROM public.outbox WHERE tenant_id = ? AND type = ?")) {
                ps.setObject(1, TENANT);
                ps.setString(2, type);
                List<String[]> rows = new java.util.ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{rs.getString(1), rs.getString(2)});
                    }
                }
                return rows;
            } finally {
                connection.rollback();
            }
        }
    }

    private static UUID newCatalogTenant() {
        UUID id = UUID.randomUUID();
        String schema = "support_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        // CNPJ sintético válido (nunca dado real), como o PlatformConsoleTest.
        String base;
        do {
            base = String.valueOf(100000000000L + (long) (Math.random() * 899999999999L));
        } while (base.chars().distinct().count() == 1);
        new JdbcTemplate(ProvisionedTenant.migratorDs).update(
                "INSERT INTO public.tenant_catalog (id, schema_name, name, cnpj, email, status, plan, settings)"
                        + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'PRO', '{}')",
                id, schema, "Tenant Suporte IT " + SUFFIX, base + DocumentNumber.cnpjCheckDigits(base),
                "suporte-" + SUFFIX + "@teste.com.br");
        return id;
    }

    @AfterAll
    static void cleanupStorage() throws IOException {
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (Stream<Path> walk = Files.walk(storageRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // melhor esforço: /var/tmp é descartável
                    }
                });
            }
        }
    }

    @Test
    @DisplayName("cria numerado SUP-AAAA-NNNNNN em sequência, sem duplicata")
    void createsNumberedTicketsInSequence() throws Exception {
        String token = tenantToken();
        String first = numberOf(createTicket(token, "Portal fora do ar " + SUFFIX, "O portal nao abre."));
        String second = numberOf(createTicket(token, "Segundo chamado " + SUFFIX, "Outro problema."));
        assertThat(first).matches("SUP-" + Year.now().getValue() + "-\\d{6}");
        assertThat(second).matches("SUP-" + Year.now().getValue() + "-\\d{6}");
        assertThat(first).isNotEqualTo(second);
        int n1 = Integer.parseInt(first.substring(first.lastIndexOf('-') + 1));
        int n2 = Integer.parseInt(second.substring(second.lastIndexOf('-') + 1));
        assertThat(n2).isEqualTo(n1 + 1);
    }

    @Test
    @DisplayName("dado pessoal marca o chamado e a lista mascara o assunto")
    void personalDataFlaggedAndMaskedInList() throws Exception {
        String token = tenantToken();
        String subject = "Falha para fulano@teste.com.br " + SUFFIX;
        MvcResult created = createTicket(token, subject, "CPF 123.456.789-09 na mensagem.");
        String number = numberOf(created);
        mvc.perform(get("/api/v1/support/tickets/" + number)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.containsPersonalData").value(true));
        mvc.perform(get("/api/v1/support/tickets")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.number=='" + number + "')].subject")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("fulano@teste.com.br")))));
    }

    @Test
    @DisplayName("usuário de outro tenant recebe 404 no chamado alheio; outro usuário do tenant também")
    void otherTenantGets404() throws Exception {
        String token = tenantToken();
        String number = numberOf(createTicket(token, "Chamado alheio " + SUFFIX, "Conteudo."));
        UUID otherTenant = newCatalogTenant();
        try {
            String otherTenantToken = TestTokens.token(otherTenant, "support:read", "support:write");
            mvc.perform(get("/api/v1/support/tickets/" + number)
                            .header("Authorization", bearer(otherTenantToken)))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/support/tickets/" + number)
                            .header("Authorization", bearer(otherUserToken())))
                    .andExpect(status().isNotFound());
        } finally {
            new JdbcTemplate(ProvisionedTenant.migratorDs)
                    .update("DELETE FROM public.tenant_catalog WHERE id = ?", otherTenant);
        }
    }

    @Test
    @DisplayName("conversa, fila da Central e nota interna invisível ao tenant")
    void conversationAndCentralQueue() throws Exception {
        String token = tenantToken();
        String number = numberOf(createTicket(token, "Fila da Central " + SUFFIX, "Primeira mensagem."));
        mvc.perform(post("/api/v1/support/tickets/" + number + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Resposta do solicitante.\"}"))
                .andExpect(status().isOk());
        String platform = platformToken("PLATFORM_SUPPORT");
        mvc.perform(patch("/api/v1/platform/support/tickets/" + number + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/v1/platform/support/tickets/" + number + "/messages?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Nota interna do agente.\",\"internal\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform/support/queue?tenantId=" + TENANT + "&status=IN_PROGRESS")
                        .header("Authorization", bearer(platform)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.number=='" + number + "')]").exists());
        MvcResult tenantDetail = mvc.perform(get("/api/v1/support/tickets/" + number)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String tenantJson = tenantDetail.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(tenantJson).doesNotContain("Nota interna do agente.");
        assertThat(tenantJson).contains("Resposta do solicitante.");
        MvcResult queueDetail = mvc.perform(get("/api/v1/platform/support/tickets/" + number + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(queueDetail.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("Nota interna do agente.");
        // FINANCE não tem support:queue.
        mvc.perform(get("/api/v1/platform/support/queue?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_FINANCE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancelar só antes do atendimento; mensagem em RESOLVED reabre")
    void cancelAndReopenRules() throws Exception {
        String token = tenantToken();
        String platform = platformToken("PLATFORM_SUPPORT");
        String cancellable = numberOf(createTicket(token, "Cancelavel " + SUFFIX, "Antes do atendimento."));
        mvc.perform(post("/api/v1/support/tickets/" + cancellable + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/support/tickets/" + cancellable + "/cancel")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        String reopenable = numberOf(createTicket(token, "Reabrivel " + SUFFIX, "Primeira."));
        mvc.perform(patch("/api/v1/platform/support/tickets/" + reopenable + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/platform/support/tickets/" + reopenable + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/support/tickets/" + reopenable + "/messages")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Ainda nao resolveu.\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/support/tickets/" + reopenable)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.ticket.reopenCount").value(1));
        // Transição inválida do agente: NEW → CLOSED direto não existe.
        String fresh = numberOf(createTicket(token, "Transicao invalida " + SUFFIX, "Texto."));
        mvc.perform(patch("/api/v1/platform/support/tickets/" + fresh + "?tenantId=" + TENANT)
                        .header("Authorization", bearer(platform))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("outbox leva só identificadores e códigos, sem corpo nem anexo")
    void outboxCarriesNoContent() throws Exception {
        String token = tenantToken();
        String secretBody = "corpo-secreto-" + SUFFIX + " que nao pode vazar";
        String number = numberOf(createTicket(token, "Outbox " + SUFFIX, secretBody));
        List<String[]> rows = outboxRows("support.ticket.created@v1");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row[1]).contains(number);
            assertThat(row[1]).doesNotContain(secretBody);
            assertThat(row[1]).doesNotContain("corpo-secreto");
        });
    }

    @Test
    @DisplayName("anexo: quarentena, varredura falsa CLEAN libera, EICAR bloqueia, download só CLEAN")
    void attachmentPipeline() throws Exception {
        storageRoot = Files.createTempDirectory("ziip-san-tickets-it-");
        LocalFileObjectStorage storage = new LocalFileObjectStorage(storageRoot);
        TicketAttachmentService pipeline = new TicketAttachmentService(storage, "ziipsan");
        TicketService tickets = new TicketService(ProvisionedTenant.appDs, TENANT, Clock.systemUTC());
        UUID requester = UUID.randomUUID();
        TicketService.Ticket created = tickets.create(requester, new TicketService.CreateInput(
                "Anexo " + SUFFIX, "INCIDENT", null, null, null, null, null, "S3", "Mensagem com anexo."));
        String number = created.number();
        TicketService.TicketDetail detail = tickets.get(number, requester, false);
        TicketService.TicketMessage message = detail.messages().get(0);

        FileScanner cleanScanner = new FileScanner() {
            @Override
            public ScanResult scan(InputStream content) {
                return ScanResult.clean("fake", Optional.empty());
            }

            @Override
            public Optional<java.time.Instant> signatureUpdatedAt() {
                return Optional.empty();
            }
        };
        TicketService.TicketAttachment clean = pipeline.upload(tickets, detail.ticket().id(), message.id(),
                number, "limpo.pdf", CLEAN_PDF);
        assertThat(clean.scanStatus()).isEqualTo("QUARANTINED");
        assertThatThrownBy(() -> tickets.attachmentForDownload(clean.id()))
                .isInstanceOf(br.com.ziipsan.core.api.ApiException.Conflict.class);
        TicketService.TicketAttachment released = pipeline.scan(tickets, clean.id(), number, cleanScanner);
        assertThat(released.scanStatus()).isEqualTo("CLEAN");
        TicketService.TicketAttachment gated = tickets.attachmentForDownload(clean.id());
        try (InputStream in = pipeline.download(gated)) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("conteudo limpo");
        }

        FileScanner infectedScanner = new FileScanner() {
            @Override
            public ScanResult scan(InputStream content) {
                return ScanResult.infected("EICAR-Test-Signature", "fake", Optional.empty());
            }

            @Override
            public Optional<java.time.Instant> signatureUpdatedAt() {
                return Optional.empty();
            }
        };
        TicketService.TicketAttachment infected = pipeline.upload(tickets, detail.ticket().id(), message.id(),
                number, "eicar.pdf", EICAR);
        TicketService.TicketAttachment blocked = pipeline.scan(tickets, infected.id(), number, infectedScanner);
        assertThat(blocked.scanStatus()).isEqualTo("INFECTED");
        assertThat(blocked.threat()).isEqualTo("EICAR-Test-Signature");
        assertThatThrownBy(() -> tickets.attachmentForDownload(infected.id()))
                .isInstanceOf(br.com.ziipsan.core.api.ApiException.Conflict.class);

        // Tipo pelo conteúdo, não pela extensão: ZIP com nome .pdf é recusado.
        byte[] zip = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00};
        assertThatThrownBy(() -> pipeline.upload(tickets, detail.ticket().id(), message.id(),
                number, "falso.pdf", zip))
                .isInstanceOf(br.com.ziipsan.core.api.ApiException.Validation.class);
    }

    @Test
    @DisplayName("anexo pela API REST: quarentena, varredor indisponível deixa NOT_SCANNED, download recusado")
    void attachmentViaRestStaysQuarantinedWithoutClamd() throws Exception {
        String token = tenantToken();
        String number = numberOf(createTicket(token, "Anexo REST " + SUFFIX, "Mensagem."));
        MvcResult detailResult = mvc.perform(get("/api/v1/support/tickets/" + number)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String detailJson = detailResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        // Primeira mensagem do chamado (o "id" inicial do JSON é o do chamado).
        int idStart = detailJson.indexOf("\"messages\":[{\"id\":\"") + "\"messages\":[{\"id\":\"".length();
        assertThat(idStart).isGreaterThan("\"messages\":[{\"id\":\"".length());
        String messageId = detailJson.substring(idStart, detailJson.indexOf("\"", idStart));
        MvcResult upload = mvc.perform(post("/api/v1/support/tickets/" + number + "/attachments")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messageId\":\"" + messageId + "\",\"fileName\":\"limpo.pdf\",\"contentBase64\":\""
                                + base64(CLEAN_PDF) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("QUARANTINED"))
                .andReturn();
        String uploadJson = upload.getResponse().getContentAsString(StandardCharsets.UTF_8);
        int attStart = uploadJson.indexOf("\"id\":\"") + 6;
        String attachmentId = uploadJson.substring(attStart, uploadJson.indexOf("\"", attStart));
        mvc.perform(post("/api/v1/platform/support/attachments/" + attachmentId + "/scan?tenantId=" + TENANT)
                        .header("Authorization", bearer(platformToken("PLATFORM_SUPPORT"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanStatus").value("NOT_SCANNED"));
        mvc.perform(get("/api/v1/support/tickets/attachments/" + attachmentId + "/download")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }
}
