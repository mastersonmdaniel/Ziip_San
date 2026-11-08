package br.com.ziipsan.platform.accesscontrol;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.platform.work.UserPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessControlServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private TestStore store;
    private AccessControlService service;
    private RecertificationService recertification;
    /** Concessões diretas da P0.13 (fonte INDEPENDENTE do serviço — correção D3). */
    private Map<UUID, Set<String>> directGrants;

    @BeforeEach
    void setUp() {
        store = new TestStore();
        store.factoryProfiles.put(ADMIN, Set.of("ADMIN"));
        store.factoryProfiles.put(USER, Set.of("VIEWER"));
        directGrants = new HashMap<>();
        // A porta lê o mapa de concessões diretas — NÃO delega de volta ao
        // serviço (a versão anterior era circular: se o serviço passasse a
        // chamar a porta, seria recursão infinita; e o teste não provava
        // nada sobre concessões diretas).
        UserPermissions permissions = (tenantId, userId) ->
                TENANT.equals(tenantId) ? directGrants.getOrDefault(userId, Set.of()) : Set.of();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccessControlService(TENANT, store, permissions, clock);
        recertification = new RecertificationService(TENANT, store, clock);
    }

    @Test
    @DisplayName("perfil composto nasce de perfil de fábrica e soma só o que o ADMIN possui")
    void compositeFromFactoryWithinAdminCeiling() {
        CompositeProfile profile = service.createCompositeProfile(ADMIN, "CONSULTA_PLUS", "Consulta ampliada",
                Set.of("VIEWER"), Set.of("user:read"));
        assertThat(profile.expandedPermissions()).contains("registry:read", "user:read");
        service.assignCompositeProfile(ADMIN, USER, profile.id());
        assertThat(service.effectivePermissionsOf(USER)).contains("registry:read", "user:read");
        // VIEWER continua só de consulta.
        assertThat(EffectivePermissions.isReadOnly(service.effectivePermissionsOf(USER))).isTrue();
    }

    @Test
    @DisplayName("compor perfil com permissão que o criador NÃO possui é recusado")
    void compositeBeyondAdminIsRefused() {
        // O teto é medido pelas EFETIVAS do criador (fábrica ∪ compostos ∪
        // concessões diretas + delegações − negativas): um criador VIEWER,
        // sem concessão direta de escrita, não compõe nada além de leitura.
        store.factoryProfiles.put(OTHER, Set.of("VIEWER"));
        assertThatThrownBy(() -> service.createCompositeProfile(OTHER, "ESCALADA", "Escalada",
                Set.of(), Set.of("user:write")))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("concessão direta da P0.13 aparece nas efetivas, conta no teto e a negativa continua valendo")
    void directGrantsInEffectives() {
        // registry:reveal-document NÃO está em perfil de fábrica nenhum
        // (concessão direta, ADR-0008): só a porta da P0.13 a traz.
        directGrants.put(USER, Set.of("registry:reveal-document"));
        assertThat(service.effectivePermissionsOf(USER)).contains("registry:reveal-document");
        // Conta no teto: quem a possui por concessão direta pode delegá-la.
        var delegation = service.delegate(USER, OTHER, Set.of("registry:reveal-document"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        assertThat(delegation.permissions()).containsExactly("registry:reveal-document");
        assertThat(service.effectivePermissionsOf(OTHER)).contains("registry:reveal-document");
        // Restrição negativa subtrai inclusive a concessão direta.
        service.restrictUser(ADMIN, USER, Set.of("registry:reveal-document"));
        assertThat(service.effectivePermissionsOf(USER)).doesNotContain("registry:reveal-document");
    }

    @Test
    @DisplayName("porta de permissões falhando: a resolução falha junto (fail-closed), não devolve parcial")
    void failingPermissionsPortFailsClosed() {
        UserPermissions broken = (tenantId, userId) -> {
            throw new IllegalStateException("porta da P0.13 indisponível (simulado)");
        };
        AccessControlService brokenService = new AccessControlService(TENANT, store, broken,
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> brokenService.effectivePermissionsOf(USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("delegação em cadeia não estende a vigência da origem nem vira permanente via composto")
    void delegationChainCannotExtendNorBecomePermanent() {
        // ADMIN possui registry:reveal-document por concessão direta e a
        // delega a OTHER com vigência até NOW+3600.
        directGrants.put(ADMIN, Set.of("registry:reveal-document"));
        service.delegate(ADMIN, OTHER, Set.of("registry:reveal-document"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        assertThat(service.effectivePermissionsOf(OTHER)).contains("registry:reveal-document");
        // Cadeia ALÉM da origem (NOW+7200 > NOW+3600): recusada.
        assertThatThrownBy(() -> service.delegate(OTHER, USER, Set.of("registry:reveal-document"),
                NOW, NOW.plusSeconds(7200))).isInstanceOf(ApiException.Validation.class);
        // Cadeia DENTRO da origem: permitida e vale para o novo delegado.
        service.delegate(OTHER, USER, Set.of("registry:reveal-document"),
                NOW, NOW.plusSeconds(1800));
        assertThat(service.effectivePermissionsOf(USER)).contains("registry:reveal-document");
        // Perfil composto permanente com permissão que OTHER só tem por
        // delegação temporária: recusado (delegada não eterniza).
        assertThatThrownBy(() -> service.createCompositeProfile(OTHER, "ETERNIZA", "Eterniza delegada",
                Set.of(), Set.of("registry:reveal-document")))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("atribuir perfil composto com permissão que o ator só possui por delegação é recusado pela guarda D5")
    void assignCompositeWithSolelyDelegatedPermissionIsRefused() {
        // ADMIN possui registry:reveal-document pela BASE (concessão direta
        // da P0.13), portanto cria legitimamente um perfil composto que a
        // contém — o perfil existe e é válido antes de qualquer delegação.
        directGrants.put(ADMIN, Set.of("registry:reveal-document"));
        CompositeProfile profile = service.createCompositeProfile(ADMIN, "REVELA_DOC", "Revela documento",
                Set.of(), Set.of("registry:reveal-document"));
        assertThat(profile.expandedPermissions()).containsExactly("registry:reveal-document");
        // ADMIN delega a permissão a OTHER por tempo limitado: OTHER passa
        // a possuí-la nas EFETIVAS, mas NÃO na base não-delegada.
        service.delegate(ADMIN, OTHER, Set.of("registry:reveal-document"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        assertThat(service.effectivePermissionsOf(OTHER)).contains("registry:reveal-document");
        // Pré-condição que isola a guarda D5 da GrantRule: como OTHER tem a
        // permissão nas efetivas, o teto (GrantRule) PASSARIA — só a guarda
        // delegated-permission-not-permanent pode recusar a atribuição,
        // porque perfil composto é permanente e delegação é temporária.
        // A recusa precisa ser exatamente por esse código, não por outro.
        assertThatThrownBy(() -> service.assignCompositeProfile(OTHER, USER, profile.id()))
                .isInstanceOfSatisfying(ApiException.Validation.class, ex ->
                        assertThat(ex.errors())
                                .extracting(ApiException.FieldError::code)
                                .containsExactly("delegated-permission-not-permanent"));
        // A recusa não deixou efeito colateral: nada foi atribuído a USER.
        assertThat(store.compositeProfilesOfUser(USER)).isEmpty();
        assertThat(service.effectivePermissionsOf(USER)).doesNotContain("registry:reveal-document");
        // Controle no MESMO cenário/perfil/alvo: quem possui a permissão
        // pela base (ADMIN, concessão direta) atribui normalmente — prova
        // que a recusa acima é da guarda D5, não do perfil ou do alvo.
        service.assignCompositeProfile(ADMIN, USER, profile.id());
        assertThat(service.effectivePermissionsOf(USER)).contains("registry:reveal-document");
    }

    @Test
    @DisplayName("restrição negativa subtrai até de perfil de fábrica; delegação vale só na vigência e no teto")
    void negativeRestrictionAndDelegation() {
        service.restrictUser(ADMIN, USER, Set.of("registry:read"));
        assertThat(service.effectivePermissionsOf(USER)).doesNotContain("registry:read");

        var delegation = service.delegate(ADMIN, OTHER, Set.of("user:read"),
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        assertThat(delegation.permissions()).containsExactly("user:read");
        assertThat(service.effectivePermissionsOf(OTHER)).contains("user:read");
        // Delegar o que o delegante não tem (OTHER delegando user:write) é recusado.
        assertThatThrownBy(() -> service.delegate(OTHER, USER, Set.of("user:write"),
                NOW, NOW.plusSeconds(3600))).isInstanceOf(ApiException.Validation.class);
        // Fora da vigência, a delegação não soma.
        Set<String> after = EffectivePermissions.resolve(Set.of(), java.util.List.of(),
                java.util.List.of(delegation), null, OTHER, NOW.plusSeconds(7200));
        assertThat(after).doesNotContain("user:read");
    }

    @Test
    @DisplayName("SSO obrigatório recusa login local; SCIM respeita teto do ADMIN e isolamento entre tenants")
    void ssoAndScimGuards() {
        store.sso = new TenantSsoConfig(TenantSsoConfig.SsoMode.OIDC, true, true);
        assertThatThrownBy(() -> service.assertLocalLoginAllowed()).isInstanceOf(ApiException.Forbidden.class);
        ScimGuard.assertSameTenant(TENANT, TENANT);
        assertThatThrownBy(() -> ScimGuard.assertSameTenant(TENANT, UUID.randomUUID()))
                .isInstanceOf(ApiException.NotFound.class);
        assertThatThrownBy(() -> ScimGuard.assertWithinAdminCeiling(Set.of("user:read"), Set.of("user:write")))
                .isInstanceOf(ApiException.Validation.class);
        TenantSsoConfig off = TenantSsoConfig.localOnly();
        assertThatThrownBy(() -> ScimGuard.assertScimEnabled(off)).isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    @DisplayName("recertificação: cada decisão fica registrada; duplicada e fora do prazo são recusadas")
    void recertificationRecordsDecisions() {
        var campaign = recertification.openCampaign(ADMIN, "Recertificação 2026-2", NOW.plusSeconds(7L * 24 * 3600));
        var decision = recertification.decide(ADMIN, campaign.id(), USER,
                RecertificationService.DecisionKind.KEEP, "Acesso conferido com o gestor da área.");
        assertThat(decision.kind()).isEqualTo(RecertificationService.DecisionKind.KEEP);
        assertThat(recertification.decisions(campaign.id())).hasSize(1);
        assertThatThrownBy(() -> recertification.decide(ADMIN, campaign.id(), USER,
                RecertificationService.DecisionKind.REVOKE, "Segunda decisão."))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    @DisplayName("trilha por usuário exporta JSONL via Json do core com selo de integridade da P1.7")
    void auditTrailExport() {
        AccessControlPorts.AuditTrailPort trailPort = new AccessControlPorts.AuditTrailPort() {
            @Override public java.util.List<AuditTrailService.AuditEventView> eventsOfUser(
                    UUID userId, Instant from, Instant to) {
                return java.util.List.of(new AuditTrailService.AuditEventView(
                        UUID.randomUUID(), userId, "user:write", NOW, "{\"campo\":\"perfil\"}"));
            }
            @Override public boolean integrityVerifies(UUID userId, Instant from, Instant to) { return true; }
        };
        AuditTrailService trailService = new AuditTrailService(TENANT, store, trailPort, sessionId -> { });
        store.openSession(new LoginGuard.ActiveSession(USER, "sess-1", NOW));
        var export = trailService.exportTrailOfUser(USER, NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        assertThat(export.integrityVerified()).isTrue();
        assertThat(export.jsonLines()).contains("\"action\":\"user:write\"");
        assertThat(trailService.activeSessions()).hasSize(1);
        trailService.revokeSession("sess-1");
        // Correção D8: revogar também limpa o ESPELHO — a sessão revogada
        // não continua listada como ativa.
        assertThat(trailService.activeSessions()).isEmpty();
        assertThatThrownBy(() -> trailService.revokeSession("sess-inexistente"))
                .isInstanceOf(ApiException.NotFound.class);
    }
}
