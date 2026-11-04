package br.com.ziipsan.platform.user;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.core.json.Json;
import tools.jackson.databind.JsonNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Preferências do usuário no servidor (P1.18, sobre a V1_7; ADR-0017,
 * MARCA-E-TEMAS §4.4, PLT-25).
 *
 * <p>Como {@code TenantParameterService} (P1.6), cada método roda numa
 * transação própria com {@code app.current_tenant} local à transação: a
 * tabela do tenant tem RLS forçada, e sem a GUC a policy esconderia as
 * linhas. A GUC morre com a transação, nunca vaza para outra conexão do
 * pool.
 *
 * <p>Precedência do valor efetivo (testada em {@code UserPreferencesServiceTest}
 * e na IT): escolha do usuário, se permitida; senão o padrão do tenant;
 * senão o padrão de fábrica (aqua-blue) no modo do sistema. "Permitida" para o padrão é pertencer
 * à lista do tenant ({@code theme.allowed_palettes}); para o modo não há
 * restrição de tenant no plano — só o padrão é restrito. A escolha guardada
 * NUNCA é apagada porque saiu da lista: o efetivo cai para o padrão do
 * tenant, e se o padrão voltar à lista a escolha volta a valer.
 *
 * <p>Padrão do tenant: o console (P1.6) gerencia {@code theme.default_mode},
 * {@code theme.default_palette}, {@code theme.allowed_palettes} e
 * {@code ui.default_locale} como versões de parâmetro (V1_5); o desenho
 * antigo (MARCA-E-TEMAS §4.4) os põe em {@code public.tenant_settings}.
 * O serviço lê os dois, com a versão aceita vigente vencendo: assim o
 * tenant que muda o padrão pelo console vê o efeito sem nenhuma migração
 * nova, e o tenant que ainda usa {@code tenant_settings} continua valendo.
 * Valores do tenant fora do domínio (modo inválido, padrão fora da lista,
 * idioma não publicado) são IGNORADOS — o padrão do tenant é dado de
 * configuração, não entrada do usuário, e um valor estragado não pode
 * quebrar o login de todo o tenant; cai para o padrão de fábrica.
 *
 * <p>Domínios validados na escrita (422, nunca 500): modo em
 * {@code light, dark, system}; padrão em {@code KNOWN_PALETTES} — a cópia
 * no backend do registro do frontend ({@code frontend/src/theme/
 * palette-ids.json}, MARCA-E-TEMAS §4.3: "com que o backend valida
 * preferências"); padrão novo entra nos dois lugares juntos (§4.7).
 * Idioma: só {@code pt-BR} é publicado (PLT-25); outro idioma é recusado
 * até que a tradução exista, em vez de guardar uma preferência que a
 * interface não cumpre.
 *
 * <p>Atualização parcial: o PUT aceita qualquer subconjunto dos três
 * campos; campo ausente mantém o guardado, campo explicitamente nulo
 * LIMPA a escolha (volta ao padrão do tenant). É por isso que o update
 * carrega um flag "presente" por campo, e não só o valor.
 */
public class UserPreferencesService {

    /** Padrões do registro do frontend (MARCA-E-TEMAS §4.2/§4.3). */
    public static final Set<String> KNOWN_PALETTES = Set.of("aqua-blue", "aqua-green");

    /** Únicos idiomas publicados (PLT-25). */
    public static final Set<String> PUBLISHED_LOCALES = Set.of("pt-BR");

    public static final String FACTORY_MODE = "system";
    public static final String FACTORY_PALETTE = "aqua-blue";
    public static final String FACTORY_LOCALE = "pt-BR";

    private static final Set<String> MODES = Set.of("light", "dark", "system");

    /** Escolha guardada do usuário; null = sem escolha (vale o padrão do tenant). */
    public record StoredPreferences(String mode, String palette, String locale) {
    }

    /** Padrão e restrição do tenant; listas nulas = sem restrição/padrão definido. */
    public record TenantDefaults(String defaultMode, String defaultPalette,
                                 List<String> allowedPalettes, String defaultLocale) {
    }

    /** Visão completa do GET: o guardado, o efetivo e o padrão do tenant. */
    public record PreferencesView(StoredPreferences stored, String effectiveMode,
                                  String effectivePalette, String effectiveLocale,
                                  TenantDefaults tenantDefaults) {
    }

    /** Um campo do PUT: presente + valor (null presente = limpar a escolha). */
    public record FieldUpdate(boolean present, String value) {
        public static FieldUpdate absent() {
            return new FieldUpdate(false, null);
        }

        public static FieldUpdate of(String value) {
            return new FieldUpdate(true, value);
        }
    }

    private final DataSource dataSource;
    private final String schema;
    private final UUID tenantId;

    public UserPreferencesService(DataSource dataSource, String schema, UUID tenantId) {
        if (dataSource == null || schema == null || schema.isBlank() || tenantId == null) {
            throw new IllegalArgumentException("dataSource, schema e tenantId são obrigatórios");
        }
        this.dataSource = dataSource;
        this.schema = schema;
        this.tenantId = tenantId;
    }

    private String t(String table) {
        return "\"" + schema.replace("\"", "\"\"") + "\"." + table;
    }

    @FunctionalInterface
    private interface Tx<T> {
        T run(Connection connection) throws Exception;
    }

    private <T> T inTx(Tx<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_tenant', ?, true)")) {
                ps.setString(1, tenantId.toString());
                ps.executeQuery().close();
            }
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } catch (Exception e) {
                connection.rollback();
                throw new IllegalStateException("Operação de preferência falhou: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Operação de preferência falhou: " + e.getMessage(), e);
        }
    }

    /** GET: escolha guardada + efetivo resolvido + padrão do tenant. */
    public PreferencesView get(String userSubject) {
        requireSubject(userSubject);
        return inTx(c -> {
            StoredPreferences stored = load(c, userSubject);
            TenantDefaults defaults = tenantDefaults(c);
            return new PreferencesView(stored,
                    resolveMode(stored, defaults),
                    resolvePalette(stored, defaults),
                    resolveLocale(stored, defaults),
                    defaults);
        });
    }

    /** PUT parcial: só os campos presentes mudam; null presente limpa a escolha. */
    public PreferencesView save(String userSubject, FieldUpdate mode, FieldUpdate palette, FieldUpdate locale) {
        requireSubject(userSubject);
        if (mode == null || palette == null || locale == null) {
            throw validation("mode, palette e locale são obrigatórios (ausente = manter, null = limpar).");
        }
        validate(mode, "mode");
        validate(palette, "palette");
        validate(locale, "locale");
        return inTx(c -> {
            StoredPreferences current = load(c, userSubject);
            String nextMode = mode.present() ? mode.value() : current.mode();
            String nextPalette = palette.present() ? palette.value() : current.palette();
            String nextLocale = locale.present() ? locale.value() : current.locale();
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO " + t("user_preferences")
                            + " (tenant_id, user_subject, theme_mode, theme_palette, locale)"
                            + " VALUES (?, ?, ?, ?, ?)"
                            + " ON CONFLICT (tenant_id, user_subject) DO UPDATE"
                            + " SET theme_mode = EXCLUDED.theme_mode,"
                            + " theme_palette = EXCLUDED.theme_palette, locale = EXCLUDED.locale,"
                            + " updated_at = NOW()")) {
                ps.setObject(1, tenantId);
                ps.setString(2, userSubject);
                ps.setString(3, nextMode);
                ps.setString(4, nextPalette);
                ps.setString(5, nextLocale);
                ps.executeUpdate();
            }
            StoredPreferences stored = new StoredPreferences(nextMode, nextPalette, nextLocale);
            TenantDefaults defaults = tenantDefaults(c);
            return new PreferencesView(stored,
                    resolveMode(stored, defaults),
                    resolvePalette(stored, defaults),
                    resolveLocale(stored, defaults),
                    defaults);
        });
    }

    private void validate(FieldUpdate field, String name) {
        if (!field.present() || field.value() == null) {
            return;
        }
        String value = field.value();
        boolean ok = switch (name) {
            case "mode" -> MODES.contains(value);
            case "palette" -> KNOWN_PALETTES.contains(value);
            case "locale" -> PUBLISHED_LOCALES.contains(value);
            default -> false;
        };
        if (!ok) {
            String hint = switch (name) {
                case "mode" -> "modo precisa ser light, dark ou system";
                case "palette" -> "padrão desconhecido (registro: " + String.join(", ", KNOWN_PALETTES) + ")";
                default -> "só pt-BR é publicado (PLT-25)";
            };
            throw ApiException.Validation.of(
                    new ApiException.FieldError(name, "invalid", hint));
        }
    }

    private StoredPreferences load(Connection c, String userSubject) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT theme_mode, theme_palette, locale FROM " + t("user_preferences")
                        + " WHERE tenant_id = ? AND user_subject = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, userSubject);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new StoredPreferences(null, null, null);
                }
                return new StoredPreferences(rs.getString(1), rs.getString(2), rs.getString(3));
            }
        }
    }

    /**
     * Padrão do tenant: versão aceita vigente (P1.6) vence; sem ela, vale
     * {@code tenant_settings} (MARCA-E-TEMAS §4.4); sem nenhum, fábrica.
     * parameter_versions pode não existir num classpath sem o módulo de
     * console — por isso a leitura é tolerante a tabela ausente (a
     * exceção 42P01 é engolida e vira "sem versão").
     */
    private TenantDefaults tenantDefaults(Connection c) throws SQLException {
        String defaultMode = firstPresent(
                acceptedVersion(c, "theme.default_mode"), tenantSetting(c, "theme.default_mode"));
        String defaultPalette = firstPresent(
                acceptedVersion(c, "theme.default_palette"), tenantSetting(c, "theme.default_palette"));
        String allowedJson = firstPresent(
                acceptedVersion(c, "theme.allowed_palettes"), tenantSetting(c, "theme.allowed_palettes"));
        String defaultLocale = firstPresent(
                acceptedVersion(c, "ui.default_locale"), tenantSetting(c, "ui.default_locale"));
        return new TenantDefaults(
                textOrNull(defaultMode), textOrNull(defaultPalette),
                parseStringList(allowedJson), textOrNull(defaultLocale));
    }

    private String firstPresent(String first, String second) {
        return first != null ? first : second;
    }

    private String acceptedVersion(Connection c, String key) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT v.value::text FROM " + t("parameter_versions") + " v"
                        + " WHERE v.tenant_id = ? AND v.definition_key = ? AND v.status = 'APPROVED'"
                        + " AND v.valid_from <= CURRENT_DATE AND (v.valid_to IS NULL OR v.valid_to >= CURRENT_DATE)"
                        + " AND EXISTS (SELECT 1 FROM " + t("parameter_acceptances") + " a"
                        + " WHERE a.tenant_id = v.tenant_id AND a.version_id = v.id"
                        + " AND a.content_hash = v.content_hash)"
                        + " ORDER BY v.valid_from DESC LIMIT 1")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            // Tabela ausente (42P01) ou sem RLS neste classpath: sem versão, sem erro.
            if ("42P01".equals(e.getSQLState())) {
                return null;
            }
            throw new IllegalStateException("Leitura da versão aceita falhou: " + e.getMessage(), e);
        }
    }

    private String tenantSetting(Connection c, String key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT setting_value::text FROM public.tenant_settings"
                        + " WHERE tenant_id = ? AND setting_key = ?")) {
            ps.setObject(1, tenantId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Desembrulha um JSON escalar ("dark" -> dark); lista/objeto volta como está. */
    private String textOrNull(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = Json.readTree(json);
            return node.isTextual() ? node.asText() : json;
        } catch (Exception e) {
            return json;
        }
    }

    private List<String> parseStringList(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = Json.readTree(json);
            if (!node.isArray()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    out.add(item.asText());
                }
            }
            return out.isEmpty() ? null : List.copyOf(out);
        } catch (Exception e) {
            return null;
        }
    }

    /** Regra pura, testável sem banco (ver UserPreferencesServiceTest). */
    static String resolveMode(StoredPreferences stored, TenantDefaults defaults) {
        if (stored.mode() != null && MODES.contains(stored.mode())) {
            return stored.mode();
        }
        if (defaults.defaultMode() != null && MODES.contains(defaults.defaultMode())) {
            return defaults.defaultMode();
        }
        return FACTORY_MODE;
    }

    static String resolvePalette(StoredPreferences stored, TenantDefaults defaults) {
        List<String> allowed = defaults.allowedPalettes();
        // List.of().contains(null) lança NPE: o null é testado antes de consultar a lista.
        if (stored.palette() != null && KNOWN_PALETTES.contains(stored.palette())
                && (allowed == null || allowed.contains(stored.palette()))) {
            return stored.palette();
        }
        if (defaults.defaultPalette() != null && KNOWN_PALETTES.contains(defaults.defaultPalette())
                && (allowed == null || allowed.contains(defaults.defaultPalette()))) {
            return defaults.defaultPalette();
        }
        return FACTORY_PALETTE;
    }

    static String resolveLocale(StoredPreferences stored, TenantDefaults defaults) {
        if (stored.locale() != null && PUBLISHED_LOCALES.contains(stored.locale())) {
            return stored.locale();
        }
        if (defaults.defaultLocale() != null && PUBLISHED_LOCALES.contains(defaults.defaultLocale())) {
            return defaults.defaultLocale();
        }
        return FACTORY_LOCALE;
    }

    private void requireSubject(String userSubject) {
        if (userSubject == null || userSubject.isBlank()) {
            throw new ApiException.Forbidden("Esta rota exige um token que identifique o usuário.");
        }
    }

    private static ApiException.Validation validation(String message) {
        return new ApiException.Validation(message, List.of(), List.of());
    }
}
