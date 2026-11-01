package br.com.ziipsan.app;

import br.com.ziipsan.app.security.BffLoginSuccessHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** P0.14: o caminho de volta do login BFF nunca vira open redirect. */
class BffLoginSuccessHandlerTest {

    private final BffLoginSuccessHandler handler = new BffLoginSuccessHandler();

    @Test
    @DisplayName("returnTo interno guardado na sessão vira o redirect; atributo some da sessão")
    void internalReturnToRedirects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(BffLoginSuccessHandler.RETURN_TO_SESSION_ATTRIBUTE, "/cadastro/clientes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, null);

        assertThat(response.getRedirectedUrl()).isEqualTo("/cadastro/clientes");
        assertThat(request.getSession(false).getAttribute(BffLoginSuccessHandler.RETURN_TO_SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("sem returnTo, volta para /")
    void noReturnToGoesHome() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, null);
        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("returnTo externo ou para o próprio login vira / (nunca sai do app)")
    void unsafeReturnToGoesHome() throws Exception {
        for (String unsafe : new String[]{"https://evil.example/x", "//evil.example/x", "/bff/login", "/login",
                "/auth/callback", "/\\evil.example"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.getSession(true).setAttribute(BffLoginSuccessHandler.RETURN_TO_SESSION_ATTRIBUTE, unsafe);
            MockHttpServletResponse response = new MockHttpServletResponse();
            handler.onAuthenticationSuccess(request, response, null);
            assertThat(response.getRedirectedUrl()).as("returnTo %s", unsafe).isEqualTo("/");
        }
    }
}
