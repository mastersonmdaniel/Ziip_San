package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2.25 pela API que a tela usa: documento controlado com revisão e
 * ciência, ocorrência única com fechamento exigente sob norma ativa,
 * fiscalização recebida com prazo pelo calendário do tenant, pontes e
 * o contrato de itens de trabalho da P2.28 — com as permissões
 * {@code cert:*} como concessão direta.
 *
 * <p>O revisor do documento é um token de aprovação com subject fixo
 * (o revisor indicado na revisão); outro aprovador é 403 no serviço.
 * Quem só lê (cert:read) não escreve: POST sem cert:write é 403.
 */
class DocumentedInformationControllerE2ETest extends AppIntegrationTestBase {

    private static final String BASE = "/api/v1/certification";

    private final String writer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:write"));
    private final String approver = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:approve"));
    private final String reader = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:read"));
    private final String viewer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String hash(char fill) {
        return String.valueOf(fill).repeat(64);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        return mvc.perform(request.header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static void insertStandard(String code) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard (catalog_key, code, title, standard_class,"
                            + " verification_seal, status) VALUES (?, ?, ?, 'VOLUNTARY', 'NAO_CONFIRMADA', 'VIGENTE')")) {
                ps.setString(1, "syn-" + code.toLowerCase());
                ps.setString(2, code);
                ps.setString(3, "Norma sintética E2E — sistema de gestão genérico (" + code + ")");
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_standard_edition (standard_id, edition)"
                            + " SELECT id, 'ED-TESTE' FROM platform.cert_standard WHERE code = ?")) {
                ps.setString(1, code);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO platform.cert_clause (edition_id, clause_number, title, summary, sort_order)"
                            + " SELECT e.id, ?, ?, ?, ? FROM platform.cert_standard_edition e"
                            + " JOIN platform.cert_standard s ON s.id = e.standard_id WHERE s.code = ?")) {
                ps.setString(1, "4.1");
                ps.setString(2, "Contexto em palavras próprias E2E");
                ps.setString(3, "Resumo próprio E2E.");
                ps.setInt(4, 1);
                ps.setString(5, code);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private static void deleteStandard(String code) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            c.setAutoCommit(false);
            st.execute("DELETE FROM platform.cert_standard WHERE code = '" + code + "'");
            c.commit();
        }
    }

    @Test
    @DisplayName("documento E2E: cria, revisa por outra pessoa, aprova só o revisor, publica, ciência, nova versão")
    void documentCycleE2E() throws Exception {
        UUID reviewerId = UUID.randomUUID();
        String reviewerToken = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("cert:approve"),
                b -> b.subject(reviewerId.toString())));
        String code = uniq("SYN-DOC-E2E");
        String docId = id(send(post(BASE + "/documents"), writer,
                """
                        {"code": "%s", "title": "Procedimento sintético E2E", "docType": "PROCEDIMENTO",
                         "retentionClass": "GUARDA_5_ANOS", "confidentiality": "INTERNAL",
                         "docVersion": "1", "contentHash": "%s"}
                        """.formatted(code, hash('e')))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("não certifica"))));
        send(post(BASE + "/documents"), viewer,
                """
                        {"code": "%s", "title": "Procedimento sintético E2E", "docType": "PROCEDIMENTO",
                         "retentionClass": "GUARDA_5_ANOS", "confidentiality": "INTERNAL",
                         "docVersion": "1", "contentHash": "%s"}
                        """.formatted(uniq("SYN-DOC-E2E"), hash('e')))
                .andExpect(status().isForbidden());
        send(post(BASE + "/documents/" + docId + "/submit-review"), writer,
                "{\"reviewerId\": \"%s\"}".formatted(reviewerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
        send(post(BASE + "/documents/" + docId + "/approve"), approver, "")
                .andExpect(status().isForbidden());
        send(post(BASE + "/documents/" + docId + "/approve"), reviewerToken, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        send(post(BASE + "/documents/" + docId + "/publish"), reviewerToken,
                "{\"reviewDueDate\": \"2028-02-01\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        send(post(BASE + "/documents/" + docId + "/acknowledge"), writer, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").value(hash('e')));
        send(post(BASE + "/documents/" + docId + "/acknowledge"), writer, "")
                .andExpect(status().isOk());
        String ackBody = mvc.perform(get(BASE + "/documents/" + docId + "/acknowledgements")
                        .header("Authorization", reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((List<?>) JsonPath.read(ackBody, "$")).hasSize(1);
        String v2Id = id(send(post(BASE + "/documents/" + docId + "/versions"), writer,
                """
                        {"docVersion": "2", "contentHash": "%s"}
                        """.formatted(hash('f')))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.docVersion").value("2")));
        mvc.perform(get(BASE + "/documents/" + docId).header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPERSEDED"));
        mvc.perform(get(BASE + "/documents/" + v2Id).header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentHash").value(hash('f')));
    }

    @Test
    @DisplayName("ocorrência E2E sob norma ativa: fechamento 422 até causa, ação concluída e eficácia; depois CLOSED")
    void occurrenceWithActiveLinkE2E() throws Exception {
        String code = uniq("SYN-OCC-E2E");
        try {
            insertStandard(code);
            String activationId = id(send(post(BASE + "/activations"), writer,
                    "{\"standardCode\": \"%s\", \"scopeStatement\": \"Escopo E2E — unidade sintética\", \"edition\": \"ED-TESTE\"}"
                            .formatted(code))
                    .andExpect(status().isCreated()));
            send(post(BASE + "/activations/" + activationId + "/approve"), approver, "")
                    .andExpect(status().isOk());
            send(post(BASE + "/activations/" + activationId + "/accept"), approver, "")
                    .andExpect(status().isOk());
            String mapBody = mvc.perform(get(BASE + "/activations/" + activationId + "/clause-map")
                            .header("Authorization", reader))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> map = JsonPath.read(mapBody, "$");
            org.assertj.core.api.Assertions.assertThat(map).hasSize(1);
            String mapId = map.get(0).get("id").toString();

            String occurrenceId = id(send(post(BASE + "/occurrences"), writer,
                    """
                            {"kind": "NONCONFORMITY", "title": "Não conformidade sintética E2E",
                             "description": "Descrição sintética E2E", "origin": "AUDIT"}
                            """)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("OPEN")));
            send(post(BASE + "/occurrences/" + occurrenceId + "/clauses"), writer,
                    "{\"clauseMapId\": \"%s\"}".formatted(mapId))
                    .andExpect(status().isCreated());
            send(post(BASE + "/occurrences/" + occurrenceId + "/close"), approver, "")
                    .andExpect(status().isUnprocessableEntity());
            send(post(BASE + "/occurrences/" + occurrenceId + "/root-cause"), writer,
                    "{\"rootCause\": \"Causa sintética E2E\"}")
                    .andExpect(status().isOk());
            String actionId = id(send(post(BASE + "/occurrences/" + occurrenceId + "/actions"), writer,
                    "{\"title\": \"Ação sintética E2E\", \"responsibleId\": \"%s\", \"dueDate\": \"2027-03-01\"}"
                            .formatted(UUID.randomUUID()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("OPEN")));
            send(post(BASE + "/occurrences/" + occurrenceId + "/actions/" + actionId + "/done"), writer, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DONE"));
            send(post(BASE + "/occurrences/" + occurrenceId + "/close"), approver, "")
                    .andExpect(status().isUnprocessableEntity());
            send(post(BASE + "/occurrences/" + occurrenceId + "/verify-effectiveness"), approver,
                    "{\"result\": \"Eficaz na verificação sintética E2E\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("EFFECTIVENESS_CHECK"));
            send(post(BASE + "/occurrences/" + occurrenceId + "/close"), approver, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        } finally {
            deleteStandard(code);
        }
    }

    @Test
    @DisplayName("fiscalização E2E: registra com prazo em dias corridos, atende constatação, plano e evidência, fecha")
    void inspectionE2E() throws Exception {
        ResultActions created = send(post(BASE + "/inspections"), writer,
                """
                        {"kind": "NONCONFORMITY", "title": "Ocorrência de fiscalização sintética E2E",
                         "description": "Descrição sintética E2E", "authorityOrigin": "ENVIRONMENTAL_AGENCY",
                         "termNumber": "TERMO-SIN-E2E", "receivedDate": "2027-02-01", "responseDays": 10,
                         "countingRule": "CALENDAR", "holidayScope": "NATIONAL",
                         "findings": ["Constatação sintética E2E"]}
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responseDueDate").value("2027-02-11"))
                .andExpect(jsonPath("$.authorityOrigin").value("ENVIRONMENTAL_AGENCY"));
        String inspectionId = id(created);
        String occurrenceId = JsonPath.read(
                created.andReturn().getResponse().getContentAsString(), "$.occurrenceId");
        String inspectionBody = mvc.perform(get(BASE + "/inspections/" + inspectionId + "/findings")
                        .header("Authorization", reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> findings = JsonPath.read(inspectionBody, "$");
        org.assertj.core.api.Assertions.assertThat(findings).hasSize(1);
        String findingId = findings.get(0).get("id").toString();
        send(post(BASE + "/occurrences/" + occurrenceId + "/close"), approver, "")
                .andExpect(status().isUnprocessableEntity());
        send(post(BASE + "/inspections/" + inspectionId + "/findings/" + findingId + "/address"), writer,
                "{\"evidenceRef\": \"Evidência sintética E2E\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADDRESSED"));
        send(post(BASE + "/inspections/" + inspectionId + "/action-plan"), writer,
                "{\"actionPlan\": \"Plano sintético E2E\"}")
                .andExpect(status().isOk());
        send(post(BASE + "/inspections/" + inspectionId + "/attendance-evidence"), writer,
                "{\"evidenceKind\": \"RECORD\", \"evidenceRef\": \"Protocolo sintético E2E\"}")
                .andExpect(status().isOk());
        send(post(BASE + "/occurrences/" + occurrenceId + "/close"), approver, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get(BASE + "/occurrences/" + occurrenceId + "/inspection").header("Authorization", reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termNumber").value("TERMO-SIN-E2E"));
    }

    @Test
    @DisplayName("itens de trabalho E2E: ação aberta aparece com responsável e prazo no contrato da P2.28")
    void workItemsE2E() throws Exception {
        UUID responsible = UUID.randomUUID();
        String occurrenceId = id(send(post(BASE + "/occurrences"), writer,
                """
                        {"kind": "IMPROVEMENT", "title": "Melhoria sintética E2E",
                         "description": "Descrição sintética E2E", "origin": "INTERNAL"}
                        """)
                .andExpect(status().isCreated()));
        String actionId = id(send(post(BASE + "/occurrences/" + occurrenceId + "/actions"), writer,
                "{\"title\": \"Ação sintética de tarefa E2E\", \"responsibleId\": \"%s\", \"dueDate\": \"2027-04-01\"}"
                        .formatted(responsible))
                .andExpect(status().isCreated()));
        String body = mvc.perform(get(BASE + "/work-items").header("Authorization", reader))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(body, "$");
        org.assertj.core.api.Assertions.assertThat(items)
                .filteredOn(i -> "CERT_ACTION".equals(i.get("sourceKind")) && actionId.equals(i.get("sourceId")))
                .singleElement()
                .satisfies(i -> {
                    org.assertj.core.api.Assertions.assertThat(i.get("responsibleId")).isEqualTo(responsible.toString());
                    org.assertj.core.api.Assertions.assertThat(i.get("dueDate")).isEqualTo("2027-04-01");
                });
    }

    @Test
    @DisplayName("baixa lógica do vínculo de evidência E2E: desativa sem apagar e some da visão de auditoria")
    void evidenceDeactivationE2E() throws Exception {
        String code = uniq("SYN-EVI-E2E");
        try {
            insertStandard(code);
            String activationId = id(send(post(BASE + "/activations"), writer,
                    "{\"standardCode\": \"%s\", \"scopeStatement\": \"Escopo E2E — unidade sintética\", \"edition\": \"ED-TESTE\"}"
                            .formatted(code))
                    .andExpect(status().isCreated()));
            send(post(BASE + "/activations/" + activationId + "/approve"), approver, "")
                    .andExpect(status().isOk());
            send(post(BASE + "/activations/" + activationId + "/accept"), approver, "")
                    .andExpect(status().isOk());
            String mapBody = mvc.perform(get(BASE + "/activations/" + activationId + "/clause-map")
                            .header("Authorization", reader))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> map = JsonPath.read(mapBody, "$");
            String mapId = map.get(0).get("id").toString();
            String evidenceId = UUID.randomUUID().toString();
            String linkId = id(send(post(BASE + "/clause-map/" + mapId + "/evidence"), writer,
                    "{\"evidenceKind\": \"OTHER\", \"evidenceId\": \"%s\", \"evidenceRef\": \"ref própria E2E\"}"
                            .formatted(evidenceId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.active").value(true)));
            send(post(BASE + "/evidence/" + linkId + "/deactivate"), writer, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
            String auditBody = mvc.perform(get(BASE + "/evidence/" + evidenceId + "/clauses")
                            .header("Authorization", bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:audit"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat((List<?>) JsonPath.read(auditBody, "$")).isEmpty();
        } finally {
            deleteStandard(code);
        }
    }
}
