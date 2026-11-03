package br.com.ziipsan.commercial.registry.service;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.audit.PersonalDataAccessLog;
import br.com.ziipsan.core.audit.PersonalDataAccessLogRepository;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0.11: busca por documento com papel, registro e cota — sem banco (repositório mockado).
 * O comportamento contra o PostgreSQL real está em DocumentSearchGateTest (ziipsan-app).
 */
class PersonalDataAccessServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String HASH = "a".repeat(64);

    private PersonalDataAccessLogRepository logRepository;
    private PersonalDataAccessService service;

    @BeforeEach
    void setUp() {
        logRepository = mock(PersonalDataAccessLogRepository.class);
        service = new PersonalDataAccessService(logRepository, 3);
        TenantContextHolder.setTenant("tenant_teste");
        TenantContextHolder.setTenantId(TENANT);
        TenantContextHolder.setUserId(USER);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(String... authorities) {
        var auth = new UsernamePasswordAuthenticationToken("usuario.teste", null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("sem a permissão própria, a busca por documento é 403 e fica registrada como DENIED")
    void withoutPermissionIsForbiddenAndLogged() {
        authenticate("registry:read");

        assertThatThrownBy(() -> service.authorizeDocumentSearch(HASH))
                .isInstanceOf(ApiException.Forbidden.class);
        ArgumentCaptor<PersonalDataAccessLog> captor = ArgumentCaptor.forClass(PersonalDataAccessLog.class);
        verify(logRepository).save(captor.capture());
        PersonalDataAccessLog saved = captor.getValue();
        assertThat(saved.getAccessType()).isEqualTo("DOCUMENT_SEARCH");
        assertThat(saved.getResult()).isEqualTo("DENIED");
        assertThat(saved.getDocumentHash()).isEqualTo(HASH);
        assertThat(saved.getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getUserId()).isEqualTo(USER);
    }

    @Test
    @DisplayName("com a permissão e dentro da cota, autorizar não registra nada (o resultado vem depois)")
    void withinQuotaAuthorizesWithoutLogging() {
        authenticate("registry:read", "registry:search-document");
        when(logRepository.countDocumentSearchesInWindow(any(), any(), any())).thenReturn(2L);

        service.authorizeDocumentSearch(HASH);

        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("cota excedida: 429, evento QUOTA_EXCEEDED registrado e alerta no log")
    void quotaExceededIsTooManyRequestsAndLogged() {
        authenticate("registry:search-document");
        when(logRepository.countDocumentSearchesInWindow(any(), any(), any())).thenReturn(3L);

        assertThatThrownBy(() -> service.authorizeDocumentSearch(HASH))
                .isInstanceOf(ApiException.TooManyRequests.class);
        ArgumentCaptor<PersonalDataAccessLog> captor = ArgumentCaptor.forClass(PersonalDataAccessLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("busca concluída, leitura e revelação registram FOUND sem o documento em claro")
    void recordsSearchReadAndReveal() {
        service.recordDocumentSearch(HASH, true);
        service.recordDocumentSearch(HASH, false);
        service.recordPersonalRead("customers", UUID.randomUUID());
        service.recordDocumentReveal("customers", UUID.randomUUID(), HASH, "atendimento");

        ArgumentCaptor<PersonalDataAccessLog> captor = ArgumentCaptor.forClass(PersonalDataAccessLog.class);
        verify(logRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        List<PersonalDataAccessLog> rows = captor.getAllValues();
        assertThat(rows).extracting(PersonalDataAccessLog::getAccessType)
                .containsExactly("DOCUMENT_SEARCH", "DOCUMENT_SEARCH", "PERSONAL_READ", "DOCUMENT_REVEAL");
        assertThat(rows).extracting(PersonalDataAccessLog::getResult)
                .containsExactly("FOUND", "NOT_FOUND", "FOUND", "FOUND");
        assertThat(rows.get(3).getPurpose()).isEqualTo("atendimento");
        assertThat(rows).allSatisfy(row -> assertThat(row.getDocumentHash()).isNotEqualTo("52998224725"));
    }

    @Test
    @DisplayName("gancho da P3.10: estouros de cota recentes são consultáveis por tenant")
    void quotaExceededHookDelegatesToRepository() {
        Instant since = Instant.now().minusSeconds(3600);
        when(logRepository.countQuotaExceeded(TENANT, since)).thenReturn(5L);
        assertThat(service.quotaExceededSince(TENANT, since)).isEqualTo(5L);
    }
}
