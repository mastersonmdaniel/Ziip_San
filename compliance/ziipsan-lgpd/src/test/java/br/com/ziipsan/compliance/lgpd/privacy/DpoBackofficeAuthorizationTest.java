package br.com.ziipsan.compliance.lgpd.privacy;

import br.com.ziipsan.compliance.lgpd.privacy.web.DpoBackofficeController;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Autorização do backoffice do encarregado (correção RG-LOTE2/S3 da revisão
 * geral de 08/11/2026): a {@code SecurityConfig} do app composto (main, HEAD
 * 19f5890) não tem matcher para {@code /privacidade/**} em nenhuma das três
 * cadeias, então a proteção passa a ser declarada no próprio controller, no
 * padrão dominante do projeto — {@code @PreAuthorize("isAuthenticated()")}
 * na classe, cobrindo os três GETs, como na correção análoga da P3.34.
 *
 * <p>Este teste prova nos dois níveis que o harness do pacote permite:
 * (1) estrutural — a anotação está na classe, com a expressão exata, e os
 * três mapeamentos continuam sob {@code /privacidade}; (2) comportamental —
 * a expressão REAL anotada é avaliada pelo machinery REAL do Spring
 * Security ({@link DefaultMethodSecurityExpressionHandler} +
 * {@code MethodSecurityExpressionRoot}) contra contextos anônimo,
 * não autenticado e autenticado: os dois primeiros são NEGADOS, o terceiro
 * passa. A prova de ponta a ponta (filtro + método no contexto composto,
 * 401/403 HTTP) é da CI total, quando o módulo lgpd entrar no scan da
 * aplicação — registrado no relatório da correção.
 *
 * <p>Limitação que este teste documenta de propósito: como o catálogo de
 * permissões da P0.13 (conferido na main) não tem permissão de
 * privacidade/LGPD e o perfil {@code ENCARREGADO_DADOS} só carrega
 * {@code registry:read}/{@code user:read}, HOJE qualquer usuário
 * autenticado passa no portão — o teste do usuário autenticado usa uma
 * autoridade qualquer justamente para deixar esse fato asseverado, e não
 * escondido. Restringir ao encarregado exige a permissão no catálogo
 * (decisão da integração), não um nome inventado aqui.
 */
class DpoBackofficeAuthorizationTest {

    private static String classLevelExpression() {
        PreAuthorize annotation = DpoBackofficeController.class.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("@PreAuthorize de classe no DpoBackofficeController").isNotNull();
        return annotation.value();
    }

    /** Avalia a expressão anotada com o machinery real de method security do Spring Security. */
    private static boolean expressionAllows(Authentication authentication, Method method) throws Exception {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        PrivacyFacade facade = mock(PrivacyFacade.class);
        DpoBackofficeController target = new DpoBackofficeController(facade);
        // MethodInvocation mínimo (aopalliance): o handler só lê método, alvo e
        // argumentos para montar a raiz de expressão — proceed() nunca é chamado.
        MethodInvocation invocation = new MethodInvocation() {
            @Override
            public Object proceed() {
                throw new UnsupportedOperationException("invocação de avaliação — proceed() não é chamado");
            }

            @Override
            public Object getThis() {
                return target;
            }

            @Override
            public java.lang.reflect.AccessibleObject getStaticPart() {
                return method;
            }

            @Override
            public Method getMethod() {
                return method;
            }

            @Override
            public Object[] getArguments() {
                return new Object[0];
            }
        };
        EvaluationContext context = handler.createEvaluationContext(authentication, invocation);
        return Boolean.TRUE.equals(handler.getExpressionParser()
                .parseExpression(classLevelExpression()).getValue(context, Boolean.class));
    }

    @Test
    @DisplayName("a classe declara @PreAuthorize(\"isAuthenticated()\") e os três GETs seguem sob /privacidade")
    void classDeclaresIsAuthenticatedAndKeepsTheThreeMappings() throws Exception {
        assertThat(classLevelExpression()).isEqualTo("isAuthenticated()");
        assertThat(DpoBackofficeController.class.getAnnotation(RequestMapping.class).path())
                .containsExactly("/privacidade");
        assertThat(DpoBackofficeController.class.getMethod("report")
                .getAnnotation(GetMapping.class).value()).containsExactly("/relatorio");
        assertThat(DpoBackofficeController.class.getMethod("coverageGaps")
                .getAnnotation(GetMapping.class).value()).containsExactly("/pendencias-cobertura");
        assertThat(DpoBackofficeController.class.getMethod("processingRecords")
                .getAnnotation(GetMapping.class).value()).containsExactly("/operacoes");
    }

    @Test
    @DisplayName("anônimo e não autenticado são NEGADOS pela expressão anotada, nos três endpoints")
    void anonymousAndUnauthenticatedAreDenied() throws Exception {
        Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        Authentication unauthenticated = new TestingAuthenticationToken("usuario", "credencial");
        assertThat(unauthenticated.isAuthenticated()).isFalse();

        for (String methodName : List.of("report", "coverageGaps", "processingRecords")) {
            Method method = DpoBackofficeController.class.getMethod(methodName);
            assertThat(expressionAllows(anonymous, method))
                    .as("anônimo em " + methodName).isFalse();
            assertThat(expressionAllows(unauthenticated, method))
                    .as("não autenticado em " + methodName).isFalse();
        }
    }

    @Test
    @DisplayName("usuário autenticado passa (limitação registrada: hoje basta autenticar — sem permissão de DPO no catálogo)")
    void authenticatedUserIsAllowed() throws Exception {
        Authentication authenticated = new TestingAuthenticationToken("encarregado", "credencial",
                List.of(new SimpleGrantedAuthority("registry:read")));
        assertThat(authenticated.isAuthenticated()).isTrue();

        for (String methodName : List.of("report", "coverageGaps", "processingRecords")) {
            Method method = DpoBackofficeController.class.getMethod(methodName);
            assertThat(expressionAllows(authenticated, method))
                    .as("autenticado em " + methodName).isTrue();
        }
    }

    @Test
    @DisplayName("o controller continua fino: delega os três relatórios à facade, sem mudar o comportamento")
    void controllerStillDelegatesToTheFacade() {
        PrivacyFacade facade = mock(PrivacyFacade.class);
        when(facade.dpoReportJson()).thenReturn("{\"relatorio\":true}");
        when(facade.coverageGapsJson()).thenReturn("{\"pendencias\":[]}");
        when(facade.processingRecordsJson()).thenReturn("{\"operacoes\":[]}");
        DpoBackofficeController controller = new DpoBackofficeController(facade);

        assertThat(controller.report()).isEqualTo("{\"relatorio\":true}");
        assertThat(controller.coverageGaps()).isEqualTo("{\"pendencias\":[]}");
        assertThat(controller.processingRecords()).isEqualTo("{\"operacoes\":[]}");
    }
}
