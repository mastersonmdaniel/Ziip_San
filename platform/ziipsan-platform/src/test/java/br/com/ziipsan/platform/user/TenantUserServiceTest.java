package br.com.ziipsan.platform.user;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.tenant.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P0.13, sem banco: quem concede um perfil só concede o que possui. Um ator com
 * {@code user:write} e {@code registry:read}, mas sem {@code registry:write}, não pode
 * conceder OPERATOR (que exige as duas permissões de cadastro).
 */
class TenantUserServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private TenantUserRepository users;
    private UserProfileRepository profiles;
    private TenantUserService service;

    @BeforeEach
    void setUp() {
        users = mock(TenantUserRepository.class);
        profiles = mock(UserProfileRepository.class);
        service = new TenantUserService(users, profiles);
        TenantContextHolder.setTenant("it_teste");
        TenantContextHolder.setTenantId(TENANT);
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(users.findByKeycloakSubAndTenantId(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("conceder perfil além das próprias permissões é 403")
    void cannotGrantBeyondOwnPermissions() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ator", null, List.of(
                        new SimpleGrantedAuthority("user:write"),
                        new SimpleGrantedAuthority("registry:read"))));
        assertThatThrownBy(() -> service.create(new TenantUserService.CreateRequest(
                        "sub-1", "Ator", null, List.of("OPERATOR"))))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ator com todas as permissões do perfil concede normalmente")
    void canGrantWithinOwnPermissions() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(
                        new SimpleGrantedAuthority("user:write"),
                        new SimpleGrantedAuthority("registry:read"),
                        new SimpleGrantedAuthority("registry:write"))));
        UserProfileAssignment assignment = new UserProfileAssignment();
        assignment.setProfile("OPERATOR");
        assignment.setValidFrom(java.time.OffsetDateTime.now().minusHours(1));
        assignment.setValidUntil(null);
        when(profiles.findAllByUserIdAndTenantId(any(), any())).thenReturn(List.of(assignment));
        TenantUserService.UserView view = service.create(new TenantUserService.CreateRequest(
                "sub-1", "Novo", null, List.of("OPERATOR")));
        assertThat(view.keycloakSub()).isEqualTo("sub-1");
        assertThat(view.profiles()).containsExactly("OPERATOR");
    }

    @Test
    @DisplayName("P1.37: perfil de fábrica por função é atribuível por quem tem suas permissões")
    void canGrantFactoryProfileByFunction() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(
                        new SimpleGrantedAuthority("user:write"),
                        new SimpleGrantedAuthority("registry:read"),
                        new SimpleGrantedAuthority("registry:write"))));
        UserProfileAssignment assignment = new UserProfileAssignment();
        assignment.setProfile("ATENDENTE");
        assignment.setValidFrom(java.time.OffsetDateTime.now().minusHours(1));
        assignment.setValidUntil(null);
        when(profiles.findAllByUserIdAndTenantId(any(), any())).thenReturn(List.of(assignment));
        TenantUserService.UserView view = service.create(new TenantUserService.CreateRequest(
                "sub-2", "Atendente", null, List.of("ATENDENTE")));
        assertThat(view.profiles()).containsExactly("ATENDENTE");
    }

    @Test
    @DisplayName("P1.37: perfil desligado (auditor externo, regulador) é recusado até a ED-75")
    void cannotGrantDisabledProfile() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(
                        new SimpleGrantedAuthority("user:write"),
                        new SimpleGrantedAuthority("registry:read"),
                        new SimpleGrantedAuthority("gis:catalog-read"))));
        assertThatThrownBy(() -> service.create(new TenantUserService.CreateRequest(
                        "sub-3", "Externo", null, List.of("AUDITOR_EXTERNO"))))
                .isInstanceOfSatisfying(ApiException.Validation.class, v ->
                        assertThat(v.errors()).anySatisfy(e -> assertThat(e.message()).contains("ED-75")));
        assertThatThrownBy(() -> service.create(new TenantUserService.CreateRequest(
                        "sub-4", "Regulador", null, List.of("REGULADOR"))))
                .isInstanceOf(ApiException.Validation.class);
    }
}
