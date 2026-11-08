package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.security.auth.PermissionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aceite da P3.39: "um teste por perfil de fabrica com a composicao
 * esperada" — o teste parametrizado percorre TODOS os perfis de fabrica da
 * P1.37 (26) e confere a composicao de cada um a partir das regras do
 * aceite, nao de uma tabela copiada: atalhos derivados das permissoes do
 * perfil, teto de quatro indicadores marcados para o perfil, tarefas e
 * avisos das portas donas, perfil desligado sem conteudo.
 */
class HomePageCompositionTest {

    @ParameterizedTest(name = "perfil {0}")
    @EnumSource(PermissionCatalog.FactoryProfile.class)
    void compositionPerFactoryProfile(PermissionCatalog.FactoryProfile profile) {
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        ports.inbox = List.of(HomeFixtures.item(HomeFixtures.NOW.minusSeconds(3600),
                br.com.ziipsan.platform.work.WorkItemStatus.OPEN));
        ports.notices = List.of(HomeFixtures.notice(false));
        ports.unread = 1;
        // O registro da P3.41 devolve, para ESTE perfil, seis marcados:
        // a pagina exibe quatro e declara o truncamento.
        ports.indicators = List.of(
                HomeFixtures.indicator("IND-A"), HomeFixtures.indicator("IND-B"),
                HomeFixtures.indicator("IND-C"), HomeFixtures.indicator("IND-D"),
                HomeFixtures.indicator("IND-E"), HomeFixtures.indicator("IND-F"));

        HomePage page = ports.service().compose(HomeFixtures.TENANT, HomeFixtures.USER, profile.name());

        assertThat(page.profileCode()).isEqualTo(profile.name());
        assertThat(page.profileLabel()).isEqualTo(profile.label());
        assertThat(page.homeRoute())
                .isEqualTo(PermissionCatalog.HOME_PAGE)
                .isEqualTo(profile.homePage());
        if (!profile.enabled()) {
            // Auditor externo e regulador nascem desligados (P1.37/ED-75):
            // nenhuma secao composta e nenhuma porta consultada.
            assertThat(page.profileEnabled()).isFalse();
            assertThat(page.shortcuts()).isEmpty();
            assertThat(page.workItems()).isEmpty();
            assertThat(page.notifications()).isEmpty();
            assertThat(page.indicators()).isEmpty();
            assertThat(page.tenantAlerts().available()).isFalse();
            assertThat(ports.inboxCalls).isZero();
            assertThat(ports.indicatorCalls).isZero();
            return;
        }
        assertThat(page.profileEnabled()).isTrue();
        // Atalhos: exatamente os universais + os cuja permissao o perfil tem.
        assertThat(page.shortcuts()).allSatisfy(s ->
                assertThat(s.isUniversal() || profile.permissions().contains(s.requiredPermission()))
                        .isTrue());
        assertThat(page.shortcuts().stream().filter(HomeShortcut::isUniversal).count())
                .isEqualTo(HomeShortcut.FACTORY.stream().filter(HomeShortcut::isUniversal).count());
        assertThat(page.shortcuts().stream().map(HomeShortcut::code))
                .contains("minhas-tarefas", "notificacoes", "agenda");
        // Tarefas e avisos: os das portas, sem refiltro da pagina.
        assertThat(page.workItems()).hasSize(1);
        assertThat(page.workItemsOverdue()).isEqualTo(1);
        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notificationsUnread()).isEqualTo(1);
        // Indicadores: teto de quatro, na ordem do registro, com total declarado.
        assertThat(page.indicators()).extracting(HomeIndicator::code)
                .containsExactly("IND-A", "IND-B", "IND-C", "IND-D");
        assertThat(page.indicatorsTotal()).isEqualTo(6);
        assertThat(page.indicatorsTruncated()).isTrue();
        assertThat(ports.lastProfileAsked).isEqualTo(profile.name());
        // Alertas do tenant: sem a ED-07, a secao declara indisponibilidade.
        assertThat(page.tenantAlerts().available()).isFalse();
        assertThat(page.tenantAlerts().alerts()).isEmpty();
    }

    @Test
    void adminSeesPermissionGatedShortcutsViewerDoesNot() {
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        HomePage admin = ports.service().compose(
                HomeFixtures.TENANT, HomeFixtures.USER, "ADMIN");
        HomePage viewer = ports.service().compose(
                HomeFixtures.TENANT, HomeFixtures.USER, "VIEWER");
        assertThat(admin.shortcuts().stream().map(HomeShortcut::code))
                .contains("usuarios", "parametros", "marca", "integracoes",
                        "cadastro-tecnico", "modelos", "meu-contrato");
        assertThat(viewer.shortcuts().stream().map(HomeShortcut::code))
                .containsExactly("minhas-tarefas", "notificacoes", "agenda", "cadastro");
    }

    @Test
    void profileWithoutCataloguedPermissionsGetsOnlyUniversalShortcuts() {
        // ALMOXARIFE e OPERADOR_ESTACAO nao tem permissao catalogada nesta
        // branch da P1.37: a pagina deles e so o universal — nada inventado.
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        for (String code : List.of("ALMOXARIFE", "OPERADOR_ESTACAO")) {
            HomePage page = ports.service().compose(HomeFixtures.TENANT, HomeFixtures.USER, code);
            assertThat(page.shortcuts().stream().map(HomeShortcut::code))
                    .containsExactly("minhas-tarefas", "notificacoes", "agenda");
        }
    }

    @Test
    void fourOrFewerIndicatorsAreNotTruncated() {
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        ports.indicators = List.of(HomeFixtures.indicator("IND-A"), HomeFixtures.indicator("IND-B"));
        HomePage page = ports.service().compose(HomeFixtures.TENANT, HomeFixtures.USER, "GESTOR");
        assertThat(page.indicators()).hasSize(2);
        assertThat(page.indicatorsTotal()).isEqualTo(2);
        assertThat(page.indicatorsTruncated()).isFalse();
    }

    @Test
    void unknownProfileIsRefusedByName() {
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        assertThatThrownBy(() -> ports.service().compose(
                HomeFixtures.TENANT, HomeFixtures.USER, "SUPERUSER"))
                .isInstanceOf(ApiException.Validation.class);
        assertThat(ports.inboxCalls).isZero();
    }

    @Test
    void everyFactoryShortcutPermissionExistsInCatalog() {
        // Atalho com permissao fora do catalogo da P0.13 seria destino morto
        // para todo perfil: o catalogo de atalhos so usa permissao real.
        for (HomeShortcut s : HomeShortcut.FACTORY) {
            if (!s.isUniversal()) {
                assertThat(PermissionCatalog.isPermission(s.requiredPermission()))
                        .as("atalho %s", s.code()).isTrue();
            }
        }
    }

    @Test
    void homeJsonCarriesCompositionThroughCoreJson() {
        HomeFixtures.FakePorts ports = new HomeFixtures.FakePorts();
        ports.inbox = List.of(HomeFixtures.item(null,
                br.com.ziipsan.platform.work.WorkItemStatus.OPEN));
        ports.indicators = List.of(HomeFixtures.indicator("IND-A"));
        HomePage page = ports.service().compose(HomeFixtures.TENANT, HomeFixtures.USER, "FATURISTA");
        String json = page.toJson();
        var parsed = br.com.ziipsan.core.json.Json.readMap(json);
        assertThat(parsed.get("profileCode")).isEqualTo("FATURISTA");
        assertThat(parsed.get("indicatorsTotal")).isEqualTo(1);
    }
}
