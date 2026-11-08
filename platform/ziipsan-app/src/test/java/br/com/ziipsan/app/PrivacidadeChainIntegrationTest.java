package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-G2 no app composto (correção da rodada 2, 08/11/2026): a cadeia
 * {@code apiSecurity} negava {@code /privacidade/**} a todos
 * ({@code anyRequest().denyAll()}), e o {@code @PreAuthorize} do
 * {@code DpoBackofficeController} nunca era avaliado. Com a regra
 * {@code .requestMatchers("/privacidade/**").authenticated()}, o anônimo
 * recebe 401 e o autenticado atravessa a cadeia.
 *
 * <p>O esperado para o autenticado HOJE é <b>404, e não 403</b>: o módulo
 * LGPD não está no scan de componentes da {@code ZiipsanApplication} (o
 * javadoc da classe diz de propósito que LGPD fica fora do scan até ganhar
 * serviço de aplicação e API), então não há handler mapeado para
 * {@code /privacidade/**} no contexto composto — a cadeia já não nega, e o
 * DispatcherServlet responde 404. O 200 de ponta a ponta pertence ao passo
 * de integração que escanear o módulo (fora deste pacote); quando ele
 * acontecer, este teste DEVE ser atualizado para esperar 200. O que este
 * teste prova — e o que fecha o R2-G2 no nível do app composto — é que a
 * cadeia não nega mais: 401 para anônimo, não-403 para autenticado.
 */
class PrivacidadeChainIntegrationTest extends AppIntegrationTestBase {

    private static final String PROBLEM = "application/problem+json";

    @Test
    @DisplayName("R2-G2: anônimo em /privacidade/relatorio recebe 401 em problem+json")
    void anonymousGets401() throws Exception {
        mvc.perform(get("/privacidade/relatorio"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.type").value("urn:ziipsan:problem:unauthenticated"));
    }

    @Test
    @DisplayName("R2-G2: Bearer válido em /privacidade/relatorio atravessa a cadeia (404 hoje — sem handler, LGPD fora do scan — e NÃO 403)")
    void authenticatedRelatorioIsNot403() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/privacidade/relatorio").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R2-G2: Bearer válido em /privacidade/pendencias-cobertura atravessa a cadeia (404 hoje, NÃO 403)")
    void authenticatedPendenciasIsNot403() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/privacidade/pendencias-cobertura").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("R2-G2: Bearer válido em /privacidade/operacoes atravessa a cadeia (404 hoje, NÃO 403)")
    void authenticatedOperacoesIsNot403() throws Exception {
        String token = TestTokens.token(ProvisionedTenant.TENANT_ID, "VIEWER");
        mvc.perform(get("/privacidade/operacoes").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }
}
