package br.com.ziipsan.security.auth;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Catálogo de permissões {@code recurso:ação} do produto (P0.13, ADR-0040).
 *
 * <p>Formato único: um recurso, dois-pontos, uma ação, sem ponto ({@code registry:reveal-document},
 * nunca {@code registry.reveal-document}). A negação é o padrão: um papel ou permissão
 * desconhecida não concede nada ({@link #authoritiesForRole(String)} devolve vazio).
 *
 * <p>O Keycloak autentica e entrega identidade, tenant e perfis; a permissão fina é resolvida
 * na aplicação, a partir daqui. Um token pode carregar um perfil de fábrica (ex.: {@code OPERATOR},
 * expandido para as permissões do perfil) ou uma permissão direta do catálogo (ex.:
 * {@code registry:reveal-document}, que nenhum perfil de fábrica inclui de propósito: quem
 * cadastra não precisa, em regra, ver o documento depois de gravado).
 *
 * <p>Permissão nova entra aqui junto da rota que a usa; o documento em
 * {@code docs/seguranca/CATALOGO-PERMISSOES.md} é conferido contra esta lista no CI.
 */
public final class PermissionCatalog {

    /** Uma permissão: nome {@code recurso:ação}, módulo, recurso, ação e tarefa de origem. */
    public record Permission(String name, String module, String resource, String action, String originTask) {
    }

    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    public static final List<Permission> ALL = List.of(
            new Permission("registry:read", "cadastro", "registry", "read", "P0.13"),
            new Permission("registry:write", "cadastro", "registry", "write", "P0.13"),
            new Permission("registry:reveal-document", "cadastro", "registry", "reveal-document", "P0.13"),
            new Permission("registry:search-document", "cadastro", "registry", "search-document", "P0.11"),
            new Permission("user:read", "plataforma", "user", "read", "P0.13"),
            new Permission("user:write", "plataforma", "user", "write", "P0.13"),
            // P0.43: leitura do registro de implantação (platform.deployment), rota
            // GET /api/v1/platform/deployments. Papéis de plataforma que a têm:
            // PLATFORM_OWNER e PLATFORM_OPS (ver PlatformRole); SUPPORT e FINANCE, não.
            new Permission("deployment:read", "plataforma", "deployment", "read", "P0.43"));

    /** Perfis mínimos de fábrica da P0.13, compostos do catálogo (perfis por função: P1.37). */
    public enum FactoryProfile {
        /**
         * Todas as permissões do catálogo, exceto {@code registry:reveal-document} e
         * {@code registry:search-document}: revelar o documento em claro e buscar por
         * documento continuam exigindo concessão direta, como no modelo anterior — nenhum
         * perfil de fábrica as inclui, nem o ADMIN (P0.11, ADR-0008: a busca por documento
         * é um papel próprio, separado de leitura e de revelação).
         */
        ADMIN(Set.of("registry:read", "registry:write", "user:read", "user:write")),
        /** Cadastro (leitura e escrita) e leitura de usuários; não gerencia usuários. */
        MANAGER(Set.of("registry:read", "registry:write", "user:read")),
        /** Cadastro (leitura e escrita); sem acesso à gestão de usuários. */
        OPERATOR(Set.of("registry:read", "registry:write")),
        /** Só leitura do cadastro. */
        VIEWER(Set.of("registry:read"));

        private final Set<String> permissions;

        FactoryProfile(Set<String> permissions) {
            this.permissions = Set.copyOf(permissions);
        }

        public Set<String> permissions() {
            return permissions;
        }
    }

    private static final Map<String, Set<String>> BY_PROFILE = Map.of(
            "ADMIN", FactoryProfile.ADMIN.permissions(),
            "MANAGER", FactoryProfile.MANAGER.permissions(),
            "OPERATOR", FactoryProfile.OPERATOR.permissions(),
            "VIEWER", FactoryProfile.VIEWER.permissions());

    private PermissionCatalog() {
    }

    /** Nomes de todas as permissões do catálogo. */
    public static Set<String> allNames() {
        return ALL.stream().map(Permission::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Verdadeiro se o nome está no catálogo e no formato {@code recurso:ação}. */
    public static boolean isPermission(String name) {
        return name != null && NAME.matcher(name).matches() && allNames().contains(name);
    }

    /**
     * Permissões concedidas por um papel do token: a expansão do perfil de fábrica, a própria
     * permissão quando o papel já é um nome do catálogo, ou vazio (negação por padrão) quando
     * o papel não é nem perfil nem permissão.
     */
    public static Set<String> authoritiesForRole(String role) {
        if (role == null) {
            return Set.of();
        }
        Set<String> profile = BY_PROFILE.get(role);
        if (profile != null) {
            return profile;
        }
        return isPermission(role) ? Set.of(role) : Set.of();
    }
}
