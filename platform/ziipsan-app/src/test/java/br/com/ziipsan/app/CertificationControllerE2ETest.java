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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.32 pela API que a tela usa: catálogo, ativação com escopo/edição/
 * aceite, mapa de cláusulas, vínculo de evidência e desligamento — com as
 * quatro permissões {@code cert:*} como concessão direta.
 *
 * <p>O autor (write) e o aprovador (approve) são tokens distintos; o autor
 * tentando aprovar é 403 pela permissão e, com a permissão, pela regra
 * proponente ≠ aprovador do serviço. A visão de auditoria do vínculo
 * exige {@code cert:audit}: leitura comum é 403 nela.
 */
class CertificationControllerE2ETest extends AppIntegrationTestBase {

    private static final String BASE = "/api/v1/certification";

    private final String writer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:write"));
    private final String approver = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:approve"));
    private final String reader = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:read"));
    private final String auditor = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "cert:audit"));
    private final String viewer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER"));

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
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
                ps.setString(1, "5.1");
                ps.setString(2, "Liderança em palavras próprias E2E");
                ps.setString(3, "Resumo próprio E2E.");
                ps.setInt(4, 2);
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
    @DisplayName("ativação E2E: propõe, aprova por outra pessoa, aceita, mapa, evidência M:N, auditoria e desligamento")
    void activationLifecycleE2E() throws Exception {
        String code = uniq("SYN-E2E");
        try {
            insertStandard(code);

            mvc.perform(get(BASE + "/standards/" + code).header("Authorization", reader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(code))
                    .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("não certifica")));
            mvc.perform(get(BASE + "/standards/" + code).header("Authorization", viewer))
                    .andExpect(status().isForbidden());

            String activationId = id(send(post(BASE + "/activations"), writer,
                    "{\"standardCode\": \"%s\", \"scopeStatement\": \"Escopo E2E — unidade sintética\", \"edition\": \"ED-TESTE\"}"
                            .formatted(code))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PROPOSED"))
                    .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("não certifica"))));

            // Quem escreve não aprova (falta cert:approve); quem aprova, aprova.
            send(post(BASE + "/activations/" + activationId + "/approve"), writer, "")
                    .andExpect(status().isForbidden());
            send(post(BASE + "/activations/" + activationId + "/approve"), approver, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APPROVED"));
            send(post(BASE + "/activations/" + activationId + "/accept"), approver, "")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.acceptanceHash").isNotEmpty());

            String mapBody = mvc.perform(get(BASE + "/activations/" + activationId + "/clause-map")
                            .header("Authorization", reader))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            java.util.List<java.util.Map<String, Object>> map = JsonPath.read(mapBody, "$");
            org.assertj.core.api.Assertions.assertThat(map).hasSize(2);
            String mapId1 = map.get(0).get("id").toString();
            String mapId2 = map.get(1).get("id").toString();

            String evidenceId = UUID.randomUUID().toString();
            send(post(BASE + "/clause-map/" + mapId1 + "/evidence"), writer,
                    "{\"evidenceKind\": \"DOCUMENT\", \"evidenceId\": \"%s\", \"evidenceRef\": \"ref própria E2E\"}"
                            .formatted(evidenceId))
                    .andExpect(status().isCreated());
            send(post(BASE + "/clause-map/" + mapId2 + "/evidence"), writer,
                    "{\"evidenceKind\": \"DOCUMENT\", \"evidenceId\": \"%s\", \"evidenceRef\": \"ref própria E2E\"}"
                            .formatted(evidenceId))
                    .andExpect(status().isCreated());

            // Visão de auditoria: só cert:audit; leitura comum é 403.
            mvc.perform(get(BASE + "/evidence/" + evidenceId + "/clauses").header("Authorization", reader))
                    .andExpect(status().isForbidden());
            String auditBody = mvc.perform(get(BASE + "/evidence/" + evidenceId + "/clauses")
                            .header("Authorization", auditor))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat((java.util.List<?>) JsonPath.read(auditBody, "$")).hasSize(2);

            send(post(BASE + "/activations/" + activationId + "/deactivate"), writer, "{\"reason\": \"motivo E2E\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
            String frozenBody = mvc.perform(get(BASE + "/activations/" + activationId + "/clause-map")
                            .header("Authorization", reader))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            java.util.List<java.util.Map<String, Object>> frozen = JsonPath.read(frozenBody, "$");
            org.assertj.core.api.Assertions.assertThat(frozen).extracting(m -> m.get("mapStatus"))
                    .containsOnly("FROZEN");
        } finally {
            deleteStandard(code);
        }
    }
}
