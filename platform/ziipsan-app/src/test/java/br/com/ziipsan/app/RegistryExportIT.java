package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.12: exportação de listas de ponta a ponta — permissão {@code registry:export},
 * CSV com a máscara da tela (nunca o documento em claro), registro EXPORT em
 * personal_data_access_log (FOUND e DENIED) e o item Arquivos gerados.
 *
 * <p>O tenant é o compartilhado de {@link ProvisionedTenant}; o cliente criado
 * aqui tem nome único por execução, e a exportação o filtra por esse nome, para
 * não depender (nem poluir) os dados dos outros testes. CPF de exemplo com
 * dígitos calculados, não é de ninguém.
 */
class RegistryExportIT extends AppIntegrationTestBase {

    private static final UUID TENANT_A = ProvisionedTenant.TENANT_ID;
    /** CPF válido de exemplo (111.444.777-35); máscara de fábrica: ***.***.777-**. */
    private static final String CPF = "111.444.777-35";
    private static final String CPF_DIGITS = "11144477735";
    private static final String MASKED = "***.***.777-**";

    private final String operator = bearer(TestTokens.token(TENANT_A, "OPERATOR"));
    private final String viewer = bearer(TestTokens.token(TENANT_A, "VIEWER"));
    private final String exporter = bearer(TestTokens.token(TENANT_A, "VIEWER", "registry:export"));

    // Igual ao DocumentSearchGateTest: a tabela tem RLS forçada com
    // USING (tenant_id = current_tenant_id()), e current_tenant_id() lê o GUC
    // app.current_tenant. Sem o set_config, a consulta via JDBC não enxerga
    // nenhuma linha (o teste via appDs sem GUC contava sempre 0, mesmo com a
    // linha DENIED gravada — foi o que quebrou a primeira execução deste IT).
    private long countLog(String accessType, String result) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT count(*) FROM " + ProvisionedTenant.SCHEMA + ".personal_data_access_log"
                                + " WHERE tenant_id = ? AND access_type = ? AND result = ?")) {
                    ps.setObject(1, TENANT_A);
                    ps.setString(2, accessType);
                    ps.setString(3, result);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return rs.getLong(1);
                    }
                }
            }
        }
    }

    private String latestExportPurpose() throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT set_config('app.current_tenant', '" + TENANT_A + "', true)");
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT purpose FROM " + ProvisionedTenant.SCHEMA + ".personal_data_access_log"
                                + " WHERE tenant_id = ? AND access_type = 'EXPORT' AND result = 'FOUND'"
                                + " ORDER BY accessed_at DESC LIMIT 1")) {
                    ps.setObject(1, TENANT_A);
                    try (ResultSet rs = ps.executeQuery()) {
                        return rs.next() ? rs.getString(1) : null;
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("exportação: /me expõe registry:export, sem ela 403 + DENIED, com ela CSV mascarado + FOUND")
    void exportFlow() throws Exception {
        String token = "ZzxExportP112" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String name = "TESTE " + token;

        // /me da P1.12: permissões expandidas, que alimentam o gating do frontend.
        mvc.perform(get("/api/v1/me").header("Authorization", exporter))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("registry:export")))
                .andExpect(jsonPath("$.permissions").value(org.hamcrest.Matchers.hasItem("registry:read")));

        // Cliente próprio deste teste.
        mvc.perform(post("/api/v1/registry/customers").header("Authorization", operator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personType": "PF", "name": "%s", "document": "%s",
                                 "billing": {"dueDay": 10}}""".formatted(name, CPF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentMasked").value(MASKED));

        // Sem registry:export, 403 — e a negação fica registrada.
        long deniedBefore = countLog("EXPORT", "DENIED");
        mvc.perform(post("/api/v1/registry/exports").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"customers\", \"filters\": {\"q\": \"%s\"}}".formatted(token)))
                .andExpect(status().isForbidden());
        assertThat(countLog("EXPORT", "DENIED")).isEqualTo(deniedBefore + 1);

        // Com a permissão: CSV síncrono com a máscara da tela, nunca o documento.
        MvcResult exported = mvc.perform(post("/api/v1/registry/exports").header("Authorization", exporter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"customers\", \"filters\": {\"q\": \"%s\"}}".formatted(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("clientes-")))
                .andExpect(content().string(containsString("Nome,Tipo de pessoa,Documento,Imóveis,Excluído")))
                .andExpect(content().string(containsString(name)))
                .andExpect(content().string(containsString(MASKED)))
                .andExpect(content().string(not(containsString(CPF_DIGITS))))
                .andExpect(content().string(not(containsString(CPF))))
                .andReturn();
        assertThat(exported.getResponse().getContentType()).contains("text/csv");
        assertThat(latestExportPurpose()).contains("export:customers rows=1");

        // Recurso desconhecido: 400, sem registro de exportação concluída.
        mvc.perform(post("/api/v1/registry/exports").header("Authorization", exporter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"fiscal\", \"filters\": {}}"))
                .andExpect(status().isBadRequest());

        // Arquivos gerados: a exportação síncrona não cria arquivo; download desconhecido é 404.
        mvc.perform(get("/api/v1/registry/generated-files").header("Authorization", viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/v1/registry/generated-files/" + UUID.randomUUID() + "/download")
                        .header("Authorization", exporter))
                .andExpect(status().isNotFound());

        // Exportação de imóveis (filtro vazio) também volta CSV síncrono.
        mvc.perform(post("/api/v1/registry/exports").header("Authorization", exporter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resource\": \"consumer-units\", \"filters\": {}}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Matrícula,Inscrição,Titular")));
    }
}
