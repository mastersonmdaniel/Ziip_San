package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0.10: OpenAPI gerada do código e conferida no CI.
 *
 * <p>O teste baixa a spec gerada pelo springdoc em runtime e compara com a
 * spec commitada em docs/openapi/openapi.yaml. Diferença não registrada
 * quebra o teste — e, portanto, o CI. Para regenerar a spec após mudar
 * uma rota, rode o teste com -Dopenapi.write=true e commite o resultado.
 *
 * <p>Comparação SEMÂNTICA (JsonNode), não string exata: a ordem das chaves
 * de um objeto JSON não tem significado, e o springdoc não estabiliza a
 * ordem das propriedades do {@code CsrfToken} (interface do Spring Security,
 * sem campos — a ordem vem da reflexão e varia entre execuções: numa
 * execução sai {@code parameterName, token, headerName}, noutra
 * {@code parameterName, headerName, token}). Comparar strings faria o CI
 * falhar de forma intermitente sem nenhuma mudança no código; a igualdade
 * de JsonNode ignora a ordem das chaves e mantém a garantia (mesmos paths,
 * mesmos schemas, mesmos valores).
 */
class OpenApiSpecTest extends AppIntegrationTestBase {

    @Test
    @DisplayName("a spec gerada é igual à spec commitada e cobre as rotas versionadas")
    void generatedSpecMatchesCommitted() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        String generated = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(generated).contains("/api/v1/me");
        assertThat(generated).contains("/api/v1/registry");

        Path committed = Path.of("..", "..", "docs", "openapi", "openapi.yaml").toAbsolutePath().normalize();
        if (Boolean.getBoolean("openapi.write")) {
            Files.createDirectories(committed.getParent());
            Files.writeString(committed, generated, StandardCharsets.UTF_8);
            return;
        }
        assertThat(committed).as("spec commitada ausente: regenere com -Dopenapi.write=true").exists();
        assertThat(br.com.ziipsan.core.json.Json.readTree(generated))
                .as("spec gerada difere da commitada (comparação semântica: ordem de chaves não importa)")
                .isEqualTo(br.com.ziipsan.core.json.Json.readTree(Files.readString(committed)));
    }
}
