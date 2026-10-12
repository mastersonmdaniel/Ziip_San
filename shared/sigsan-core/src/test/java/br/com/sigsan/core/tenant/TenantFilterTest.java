package br.com.sigsan.core.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantFilterTest {

    private TenantFilter filter;
    private MockHttpServletResponse response;
    private final java.util.concurrent.atomic.AtomicReference<String> seenTenant =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicReference<java.util.UUID> seenTenantId =
            new java.util.concurrent.atomic.AtomicReference<>();

    @BeforeEach
    void setUp() {
        filter = new TenantFilter();
        response = new MockHttpServletResponse();
        seenTenant.set(null);
        seenTenantId.set(null);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private FilterChain recordingChain(AtomicBoolean invoked, AtomicBoolean contextPresent) {
        return new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                invoked.set(true);
                contextPresent.set(TenantContextHolder.hasTenant());
                seenTenant.set(TenantContextHolder.getTenant());
                seenTenantId.set(TenantContextHolder.getTenantId());
            }
        };
    }

    @Test
    @DisplayName("header X-Tenant-ID válido popula o contexto durante a requisição")
    void validTenantHeaderPopulatesContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant_alpha");

        AtomicBoolean chainInvoked = new AtomicBoolean();
        AtomicBoolean contextPresent = new AtomicBoolean();

        filter.doFilter(request, response, recordingChain(chainInvoked, contextPresent));

        assertThat(chainInvoked).isTrue();
        assertThat(contextPresent.get())
                .as("o filtro precisa popular o contexto ANTES de chamar a cadeia")
                .isTrue();
        assertThat(seenTenant.get())
                .as("o tenant é válido apenas durante a cadeia; depois o finally limpa")
                .isEqualTo("tenant_alpha");
        assertThat(TenantContextHolder.getTenant())
                .as("fora da cadeia o contexto já foi limpo")
                .isNull();
    }

    @Test
    @DisplayName("contexto é limpo depois da requisição, mesmo em sucesso")
    void contextClearedAfterSuccessfulRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant_alpha");

        filter.doFilter(request, response, recordingChain(new AtomicBoolean(), new AtomicBoolean()));

        assertThat(TenantContextHolder.hasTenant())
                .as("contexto residual em thread pool causaria vazamento de tenant entre requisições")
                .isFalse();
    }

    @Test
    @DisplayName("contexto é limpo mesmo quando a cadeia lança exceção")
    void contextClearedWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant_alpha");

        FilterChain exploding = new FilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("falha no downstream");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, exploding))
                .isInstanceOf(IllegalStateException.class);

        assertThat(TenantContextHolder.hasTenant())
                .as("sem o finally, o tenant vazaria para a próxima requisição na mesma thread")
                .isFalse();
    }

    @Test
    @DisplayName("requisição sem header não define contexto, mas segue a cadeia")
    void missingHeaderLeavesContextEmptyButContinuesChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        AtomicBoolean chainInvoked = new AtomicBoolean();
        AtomicBoolean contextPresent = new AtomicBoolean();

        filter.doFilter(request, response, recordingChain(chainInvoked, contextPresent));

        assertThat(chainInvoked)
                .as("a ausência de header não deve interromper a requisição neste estágio")
                .isTrue();
        assertThat(contextPresent.get()).isFalse();
        assertThat(TenantContextHolder.getTenant()).isNull();
    }

    @Test
    @DisplayName("header em branco é tratado como ausente")
    void blankHeaderTreatedAsAbsent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "   ");

        AtomicBoolean contextPresent = new AtomicBoolean();

        filter.doFilter(request, response, recordingChain(new AtomicBoolean(), contextPresent));

        assertThat(contextPresent.get()).isFalse();
        assertThat(TenantContextHolder.getTenant()).isNull();
    }

    @Test
    @DisplayName("header UUID deriva o tenantId sem quebrar quando não é UUID")
    void uuidHeaderDerivesTenantId() throws ServletException, IOException {
        UUID tenantId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", tenantId.toString());

        filter.doFilter(request, response, recordingChain(new AtomicBoolean(), new AtomicBoolean()));

        assertThat(seenTenantId.get())
                .as("quando o header principal é um UUID, ele é o próprio tenantId")
                .isEqualTo(tenantId);
    }

    @Test
    @DisplayName("schema não-UUID não causa exceção: tenantId fica nulo, não estoura")
    void nonUuidSchemaDoesNotThrow() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", "tenant_alpha");

        assertThatCode(() -> filter.doFilter(request, response,
                recordingChain(new AtomicBoolean(), new AtomicBoolean())))
                .as("o bug anterior era UUID.fromString() estourar com nome de schema")
                .doesNotThrowAnyException();

        assertThat(seenTenant.get()).isEqualTo("tenant_alpha");
        assertThat(seenTenantId.get())
                .as("sem header de UUID, o id fica para ser resolvido pelo catálogo")
                .isNull();
    }

    @Test
    @DisplayName("header com formato de schema inválido é rejeitado")
    void malformedSchemaHeaderIsRejected() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Tentativa de injeção via search_path: o header alimenta o schema do Hibernate.
        request.addHeader("X-Tenant-ID", "public; DROP TABLE users--");

        filter.doFilter(request, response, recordingChain(new AtomicBoolean(), new AtomicBoolean()));

        assertThat(seenTenant.get())
                .as("formato inválido precisa ser descartado, não propagado ao banco")
                .isNull();
        assertThat(TenantContextHolder.hasTenant()).isFalse();
    }

    @Test
    @DisplayName("UUID explícito em header separado tem precedência")
    void explicitUuidHeaderTakesPrecedence() throws ServletException, IOException {
        UUID schemaUuid = UUID.randomUUID();
        UUID explicitId = UUID.randomUUID();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-ID", schemaUuid.toString());
        request.addHeader("X-Tenant-UUID", explicitId.toString());

        filter.doFilter(request, response, recordingChain(new AtomicBoolean(), new AtomicBoolean()));

        assertThat(seenTenantId.get()).isEqualTo(explicitId);
    }
}
