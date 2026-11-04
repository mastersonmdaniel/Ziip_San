package br.com.ziipsan.app;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P2.30: o motor pela API — variável inexistente ou de tipo incompatível
 * recusa a versão; prévia com massa sintética antes da aprovação; autor e
 * proponente não aprovam a própria versão; documento gerado grava modelo e
 * versão e não muda quando o modelo muda; vigência sobreposta é recusada;
 * bloco travado não muda nem some na versão seguinte; fábrica é copiada,
 * nunca editada.
 *
 * <p>Usuários distintos por subject fixo no token: o autor cria e propõe, o
 * aprovador aprova, e um terceiro propõe para o teste de proponente que
 * tenta aprovar. Cada token novo sem subject fixo teria subject aleatório
 * (ver TestTokens), o que quebraria o teste de separação.
 */
class DocumentTemplateIT extends AppIntegrationTestBase {

    private static final String BASE = "/api/v1/templates";
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID APPROVER = UUID.randomUUID();
    private static final UUID PROPOSER = UUID.randomUUID();

    private final String author = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID,
            List.of("template:write", "template:read"), b -> b.subject(AUTHOR.toString())));
    private final String approver = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID,
            List.of("template:approve", "template:read"), b -> b.subject(APPROVER.toString())));
    private final String authorAsApprover = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID,
            List.of("template:approve"), b -> b.subject(AUTHOR.toString())));
    private final String proposer = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID,
            List.of("template:write"), b -> b.subject(PROPOSER.toString())));
    private final String proposerAsApprover = bearer(TestTokens.token(ProvisionedTenant.TENANT_ID,
            List.of("template:approve"), b -> b.subject(PROPOSER.toString())));

    private static String uniq(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
        var builder = request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON);
        if (json != null) {
            builder.content(json);
        }
        return mvc.perform(builder);
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String createTemplate(String token, String code) throws Exception {
        ResultActions result = send(post(BASE), token,
                "{\"code\":\"" + code + "\",\"title\":\"Aviso\",\"documentType\":\"CUT_NOTICE\",\"channel\":\"PDF\"}");
        result.andExpect(status().isCreated());
        return id(result);
    }

    private String createVersion(String token, String templateId, String validFrom, String validTo,
                                 String blocksJson, String variablesJson) throws Exception {
        String body = "{\"blocks\":" + blocksJson + ",\"variables\":" + variablesJson
                + ",\"validFrom\":\"" + validFrom + "\""
                + (validTo == null ? ",\"validTo\":null" : ",\"validTo\":\"" + validTo + "\"") + "}";
        ResultActions result = send(post(BASE + "/" + templateId + "/versions"), token, body);
        result.andExpect(status().isCreated());
        return id(result);
    }

    private static String blocks(String freeText, String lockedText) {
        StringBuilder blocks = new StringBuilder("[");
        if (lockedText != null) {
            blocks.append("{\"id\":\"legal\",\"kind\":\"LOCKED\",\"origin\":\"TENANT\",\"text\":")
                    .append("\"").append(lockedText).append("\"},");
        }
        blocks.append("{\"id\":\"corpo\",\"kind\":\"FREE\",\"origin\":\"TENANT\",\"text\":\"").append(freeText).append("\"}]");
        return blocks.toString();
    }

    private static final String VARS = "[{\"name\":\"customer_name\",\"type\":\"TEXT\"},"
            + "{\"name\":\"amount\",\"type\":\"MONEY\"},{\"name\":\"due_date\",\"type\":\"DATE\"}]";

    @Test
    @DisplayName("P2.30: fábricas listadas (14) e cópia cria modelo do tenant em rascunho")
    void factoryListAndCopy() throws Exception {
        send(get(BASE + "/factory"), author, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14))
                .andExpect(jsonPath("$[?(@.key == 'cut-notice')].channel").value("PDF"))
                .andExpect(jsonPath("$[?(@.key == 'service-order-print')].channel").value("THERMAL"));
        ResultActions copy = send(post(BASE + "/factory/cut-notice/copy"), author,
                "{\"code\":\"" + uniq("copia") + "\",\"title\":\"Aviso copiado\",\"validFrom\":\"2026-01-01\"}");
        copy.andExpect(status().isCreated())
                .andExpect(jsonPath("$.factoryKey").value("cut-notice"));
        send(post(BASE + "/factory/inexistente/copy"), author,
                "{\"code\":\"" + uniq("x") + "\",\"title\":\"X\",\"validFrom\":\"2026-01-01\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("P2.30: variável inexistente e tipo incompatível recusam a versão (422)")
    void unknownVariableAndWrongTypeAreRejected() throws Exception {
        String templateId = createTemplate(author, uniq("aviso"));
        String unknownBlocks = "[{\"id\":\"corpo\",\"kind\":\"FREE\",\"origin\":\"TENANT\","
                + "\"text\":\"Titular {{customer_name}} e {{fantasia}}.\"}]";
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + unknownBlocks + ",\"variables\":" + VARS
                        + ",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-06-30\"}")
                .andExpect(status().isUnprocessableEntity());
        String wrongTypeVars = "[{\"name\":\"amount\",\"type\":\"DATE\"}]";
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + blocks("Valor R$ {{amount}}.", null) + ",\"variables\":" + wrongTypeVars
                        + ",\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-06-30\"}")
                .andExpect(status().isUnprocessableEntity());
        // Bloco SYSTEM (conteúdo legal da P2.32) também é recusado nesta frente.
        String systemBlocks = "[{\"id\":\"legal\",\"kind\":\"LOCKED\",\"origin\":\"SYSTEM\",\"text\":\"Legal futuro.\"}]";
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + systemBlocks + ",\"variables\":[],\"validFrom\":\"2026-01-01\",\"validTo\":\"2026-06-30\"}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("P2.30: fluxo completo — prévia, proposta, aprovação por outra pessoa, geração e instantâneo")
    void fullFlowWithSnapshot() throws Exception {
        String templateId = createTemplate(author, uniq("aviso"));
        String v1 = createVersion(author, templateId, "2026-01-01", "2026-06-30",
                blocks("Titular {{customer_name}}, valor R$ {{amount}}.", "Texto travado."), VARS);
        // Aprovar sem prévia é recusado — mesmo por outra pessoa.
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/propose"), author, null)
                .andExpect(status().isOk());
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/approve"), approver, null)
                .andExpect(status().isConflict());
        // Prévia com massa sintética (nenhum dado real).
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/preview"), author, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendered").value(org.hamcrest.Matchers.containsString("NOME EXEMPLO")));
        // Autor não aprova o próprio modelo.
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/approve"), authorAsApprover, null)
                .andExpect(status().isForbidden());
        // Outra pessoa aprova.
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/approve"), approver, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        // Geração com a versão vigente: instantâneo do texto.
        ResultActions gen = send(post(BASE + "/" + templateId + "/generate"), author,
                "{\"date\":\"2026-03-01\",\"values\":{\"customer_name\":\"NOME TESTE\","
                        + "\"amount\":\"50.00\",\"due_date\":\"2026-03-10\"}}");
        gen.andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionId").value(v1))
                .andExpect(jsonPath("$.renderedBody").value("Texto travado.Titular NOME TESTE, valor R$ 50.00."));
        // Vigência sobreposta é recusada (409), mesmo em rascunho. O caso
        // preserva o bloco travado da v1 de propósito: sem ele, a única
        // violação aqui seria a sobreposição — com o bloco removido, a
        // validação de blocos travados (422) dispara antes de o EXCLUDE de
        // vigência ser alcançado, e o teste deixaria de isolar a sobreposição.
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + blocks("Outro.", "Texto travado.") + ",\"variables\":" + VARS
                        + ",\"validFrom\":\"2026-03-01\",\"validTo\":\"2026-03-31\"}")
                .andExpect(status().isConflict());
        // Bloco travado alterado ou removido na versão nova é recusado (422).
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + blocks("Novo corpo.", "Texto ALTERADO.") + ",\"variables\":" + VARS
                        + ",\"validFrom\":\"2026-07-01\",\"validTo\":null}")
                .andExpect(status().isUnprocessableEntity());
        send(post(BASE + "/" + templateId + "/versions"), author,
                "{\"blocks\":" + blocks("Novo corpo.", null) + ",\"variables\":" + VARS
                        + ",\"validFrom\":\"2026-07-01\",\"validTo\":null}")
                .andExpect(status().isUnprocessableEntity());
        // Bloco travado preservado, livre alterado: versão nova válida.
        String v2 = createVersion(author, templateId, "2026-07-01", null,
                blocks("Titular {{customer_name}}, valor NOVO R$ {{amount}}.", "Texto travado."), VARS);
        send(post(BASE + "/" + templateId + "/versions/" + v2 + "/preview"), author, null)
                .andExpect(status().isOk());
        send(post(BASE + "/" + templateId + "/versions/" + v2 + "/propose"), author, null)
                .andExpect(status().isOk());
        send(post(BASE + "/" + templateId + "/versions/" + v2 + "/approve"), approver, null)
                .andExpect(status().isOk());
        // O documento gerado antes NÃO mudou com a versão nova.
        send(get(BASE + "/" + templateId + "/generated"), author, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].renderedBody")
                        .value("Texto travado.Titular NOME TESTE, valor R$ 50.00."));
        // E a geração nova usa a versão nova.
        send(post(BASE + "/" + templateId + "/generate"), author,
                "{\"date\":\"2026-08-01\",\"values\":{\"customer_name\":\"NOME TESTE\","
                        + "\"amount\":\"70.00\",\"due_date\":\"2026-08-10\"}}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateVersionId").value(v2))
                .andExpect(jsonPath("$.renderedBody")
                        .value("Texto travado.Titular NOME TESTE, valor NOVO R$ 70.00."));
    }

    @Test
    @DisplayName("P2.30: proponente não aprova a própria proposta, e rejeição exige outra pessoa")
    void proposerCannotApprove() throws Exception {
        String templateId = createTemplate(author, uniq("aviso"));
        String v1 = createVersion(author, templateId, "2026-01-01", null,
                blocks("Titular {{customer_name}}.", null),
                "[{\"name\":\"customer_name\",\"type\":\"TEXT\"}]");
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/preview"), author, null)
                .andExpect(status().isOk());
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/propose"), proposer, null)
                .andExpect(status().isOk());
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/approve"), proposerAsApprover, null)
                .andExpect(status().isForbidden());
        send(post(BASE + "/" + templateId + "/versions/" + v1 + "/reject"), approver,
                "{\"reason\":\"Revisar o texto.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
