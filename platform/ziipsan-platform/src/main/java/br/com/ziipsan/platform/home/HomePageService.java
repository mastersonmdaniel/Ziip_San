package br.com.ziipsan.platform.home;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.security.auth.PermissionCatalog;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Composicao da pagina inicial por perfil de fabrica (P3.39). Le das portas
 * das tarefas donas e NAO tem regra propria alem das do aceite:
 *
 * <ul>
 *   <li>perfil = perfil de fabrica da P1.37 ({@link PermissionCatalog.FactoryProfile});
 *       codigo fora do catalogo e recusa nomeada 422 — nao existe pagina para
 *       perfil inventado;</li>
 *   <li>atalhos = os do catalogo {@link HomeShortcut#FACTORY} cuja permissao
 *       o perfil tem, mais os universais — derivado do catalogo, nunca de
 *       uma tabela digitada por perfil;</li>
 *   <li>tarefas = a caixa do usuario como a P2.28 a filtra pelo escopo dele
 *       (a pagina nao refiltra nem amplia); vencidas contadas sobre o
 *       devolvido, com o relogio injetado;</li>
 *   <li>avisos = a central da P3.36 do usuario (lista entregue + nao lidos);</li>
 *   <li>indicadores = os do papel no registro da P3.41, cortados em
 *       {@link HomePage#MAX_INDICATORS} na ordem do registro, com total e
 *       truncamento declarados;</li>
 *   <li>alertas do tenant = a porta da ED-07: sem fonte, a secao nasce
 *       indisponivel (available=false), nunca uma lista fingida;</li>
 *   <li>perfil desligado (P1.37: auditor externo e regulador ate a ED-75)
 *       nao compoe conteudo: secoes vazias e {@code profileEnabled=false},
 *       sem nem consultar as portas.</li>
 * </ul>
 *
 * <p>Um usuario com MAIS de um perfil: o aceite fala da pagina POR PERFIL e
 * a P1.37 atribui uma pagina por perfil; compor a UNIAO de perfis e regra
 * que nenhuma fonte fixa — esta servico compoe o perfil pedido e a escolha
 * do perfil ativo (quando houver varios) e da fiacao/sessao na integracao
 * (pendencia declarada no relatorio), nao uma fusao inventada aqui.
 */
public final class HomePageService {

    /** Limites de exibicao pedidos pela pagina as portas (a caixa/central ja paginam nelas). */
    public static final int WORK_ITEMS_LIMIT = 10;
    public static final int NOTIFICATIONS_LIMIT = 10;

    private final HomePorts.WorkItems workItems;
    private final HomePorts.Notifications notifications;
    private final HomePorts.Indicators indicators;
    private final HomePorts.TenantAlerts tenantAlerts;
    private final Clock clock;

    public HomePageService(HomePorts.WorkItems workItems, HomePorts.Notifications notifications,
                           HomePorts.Indicators indicators, HomePorts.TenantAlerts tenantAlerts,
                           Clock clock) {
        if (workItems == null || notifications == null || indicators == null
                || tenantAlerts == null || clock == null) {
            throw new IllegalArgumentException("portas de tarefas, avisos, indicadores, alertas e relogio sao obrigatorias");
        }
        this.workItems = workItems;
        this.notifications = notifications;
        this.indicators = indicators;
        this.tenantAlerts = tenantAlerts;
        this.clock = clock;
    }

    /** Compoe a pagina inicial do usuario no perfil de fabrica pedido. */
    public HomePage compose(UUID tenantId, UUID userId, String profileCode) {
        if (tenantId == null || userId == null) {
            throw new IllegalArgumentException("tenant e usuario sao obrigatorios");
        }
        PermissionCatalog.FactoryProfile profile = factoryProfile(profileCode);
        if (!profile.enabled()) {
            return new HomePage(profile.name(), profile.label(), false, profile.homePage(),
                    List.of(), List.of(), 0, List.of(), 0,
                    HomePorts.AlertsResult.unavailable(), List.of(), 0, false);
        }
        Set<String> permissions = profile.permissions();
        List<HomeShortcut> shortcuts = HomeShortcut.FACTORY.stream()
                .filter(s -> s.isUniversal() || permissions.contains(s.requiredPermission()))
                .toList();
        List<br.com.ziipsan.platform.work.WorkItem> inbox = workItems.inboxOf(userId, WORK_ITEMS_LIMIT);
        Instant now = clock.instant();
        long overdue = inbox.stream()
                .filter(i -> i.dueAt() != null && i.dueAt().isBefore(now) && i.status().isOpen())
                .count();
        List<br.com.ziipsan.platform.notification.NotificationCenterService.NotificationView> recent =
                notifications.recentOf(userId, NOTIFICATIONS_LIMIT);
        long unread = notifications.unreadCountOf(userId);
        List<HomeIndicator> all = indicators.forProfile(tenantId, profile.name());
        List<HomeIndicator> shown = all.stream().limit(HomePage.MAX_INDICATORS).toList();
        return new HomePage(profile.name(), profile.label(), true, profile.homePage(),
                shortcuts, inbox, overdue, recent, unread,
                tenantAlerts.alertsOf(tenantId), shown, all.size(), all.size() > shown.size());
    }

    private static PermissionCatalog.FactoryProfile factoryProfile(String profileCode) {
        if (profileCode == null || profileCode.isBlank()) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "profile", "profile-required", "Informe o perfil de fabrica do usuario."));
        }
        try {
            return PermissionCatalog.FactoryProfile.valueOf(profileCode);
        } catch (IllegalArgumentException e) {
            throw ApiException.Validation.of(new ApiException.FieldError(
                    "profile", "unknown-factory-profile",
                    "Perfil fora do catalogo de fabrica da P1.37: " + profileCode));
        }
    }
}
