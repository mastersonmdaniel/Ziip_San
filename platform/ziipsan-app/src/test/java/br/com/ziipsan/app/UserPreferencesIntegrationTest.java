package br.com.ziipsan.app;

import br.com.ziipsan.platform.console.TenantParameterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1.18 na cadeia real: GET/PUT /api/v1/me/preferences, precedência
 * usuário → tenant → fábrica, escolha guardada que sobrevive à saída do
 * padrão da lista, e isolamento por usuário (o `sub` do token é a chave).
 *
 * <p>Isolamento: o tenant é o compartilhado da IT; cada teste usa um
 * `sub` aleatório (nenhum outro teste toca essa linha) e apaga no
 * finally o que semeou em tenant_settings / parameter_versions. As
 * chaves de parâmetro usadas são as da P1.18; a versão aceita vigente é
 * criada pelo fluxo real do console (propor → aprovar → aceitar), não
 * por INSERT direto.
 */
class UserPreferencesIntegrationTest extends AppIntegrationTestBase {

    private static String tokenFor(String subject) {
        return TestTokens.token(ProvisionedTenant.TENANT_ID, List.of("VIEWER"),
                b -> b.subject(subject));
    }

    private static String url() {
        return "/api/v1/me/preferences";
    }

    /** Escreve/apaga em tabelas com RLS forçada: GUC na mesma conexão. */
    private static void asTenant(String sql) throws Exception {
        try (Connection c = ProvisionedTenant.migratorDs.getConnection();
             Statement st = c.createStatement()) {
            st.execute("SELECT set_config('app.current_tenant', '" + ProvisionedTenant.TENANT_ID + "', false)");
            st.execute(sql);
        }
    }

    private static void setTenantSetting(String key, String valueJson) throws Exception {
        asTenant("INSERT INTO public.tenant_settings (tenant_id, setting_key, setting_value)"
                + " VALUES ('" + ProvisionedTenant.TENANT_ID + "', '" + key + "', '" + valueJson + "'::jsonb)"
                + " ON CONFLICT (tenant_id, setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value");
    }

    private static void deleteTenantSetting(String key) throws Exception {
        asTenant("DELETE FROM public.tenant_settings WHERE tenant_id = '"
                + ProvisionedTenant.TENANT_ID + "' AND setting_key = '" + key + "'");
    }

    private static void deleteParameterVersions(String key) throws Exception {
        asTenant("DELETE FROM " + ProvisionedTenant.SCHEMA + ".parameter_acceptances WHERE tenant_id = '"
                + ProvisionedTenant.TENANT_ID + "' AND version_id IN (SELECT id FROM "
                + ProvisionedTenant.SCHEMA + ".parameter_versions WHERE tenant_id = '"
                + ProvisionedTenant.TENANT_ID + "' AND definition_key = '" + key + "')");
        asTenant("DELETE FROM " + ProvisionedTenant.SCHEMA + ".parameter_versions WHERE tenant_id = '"
                + ProvisionedTenant.TENANT_ID + "' AND definition_key = '" + key + "'");
    }

    /** Versão aceita vigente via fluxo do console (propor → aprovar → aceitar). */
    private static void acceptedVersion(String key, String valueJson) {
        var service = new TenantParameterService(ProvisionedTenant.appDs,
                ProvisionedTenant.SCHEMA, ProvisionedTenant.TENANT_ID);
        UUID proposer = UUID.randomUUID();
        UUID approver = UUID.randomUUID();
        var proposed = service.propose(key, valueJson, LocalDate.now(), null, proposer);
        service.approve(proposed.id(), approver);
        service.accept(proposed.id(), approver, "Aceitante IT", "ADMIN", false);
    }

    @Test
    @DisplayName("sem escolha e sem padrão do tenant: fábrica (system, aqua-blue, pt-BR)")
    void factoryDefaultsWithoutAnyChoice() throws Exception {
        String subject = UUID.randomUUID().toString();
        mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.mode").isEmpty())
                .andExpect(jsonPath("$.effectiveMode").value("system"))
                .andExpect(jsonPath("$.effectivePalette").value("aqua-blue"))
                .andExpect(jsonPath("$.effectiveLocale").value("pt-BR"));
    }

    @Test
    @DisplayName("PUT guarda a escolha e o GET a devolve como efetivo; outro usuário não a vê")
    void putPersistsAndIsIsolatedPerUser() throws Exception {
        String subject = UUID.randomUUID().toString();
        String other = UUID.randomUUID().toString();
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"dark\",\"palette\":\"aqua-green\",\"locale\":\"pt-BR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.mode").value("dark"))
                .andExpect(jsonPath("$.effectivePalette").value("aqua-green"));
        mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.palette").value("aqua-green"));
        mvc.perform(get(url()).header("Authorization", bearer(tokenFor(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.mode").isEmpty())
                .andExpect(jsonPath("$.effectiveMode").value("system"));
    }

    @Test
    @DisplayName("PUT parcial mantém o resto; null explícito limpa só aquele campo")
    void partialPutAndExplicitNullClear() throws Exception {
        String subject = UUID.randomUUID().toString();
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"dark\",\"palette\":\"aqua-green\"}"))
                .andExpect(status().isOk());
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.mode").isEmpty())
                .andExpect(jsonPath("$.stored.palette").value("aqua-green"))
                .andExpect(jsonPath("$.effectiveMode").value("system"));
    }

    @Test
    @DisplayName("modo, padrão e idioma fora do domínio são 422 e nada é guardado")
    void invalidValuesAre422() throws Exception {
        String subject = UUID.randomUUID().toString();
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"banana\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"palette\":\"rosa\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"locale\":\"en-US\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stored.mode").isEmpty());
    }

    @Test
    @DisplayName("padrão do tenant via tenant_settings vale sem escolha; lista restrita derruba o efetivo sem apagar a escolha")
    void tenantSettingsDefaultAndRestrictedList() throws Exception {
        String subject = UUID.randomUUID().toString();
        try {
            setTenantSetting("theme.default_mode", "\"dark\"");
            setTenantSetting("theme.default_palette", "\"aqua-green\"");
            setTenantSetting("theme.allowed_palettes", "[\"aqua-blue\", \"aqua-green\"]");
            mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveMode").value("dark"))
                    .andExpect(jsonPath("$.effectivePalette").value("aqua-green"));
            // Usuário escolhe aqua-green; o tenant restringe para só aqua-blue:
            // o efetivo cai, a escolha guardada continua aqua-green.
            mvc.perform(put(url()).header("Authorization", bearer(tokenFor(subject)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"palette\":\"aqua-green\"}"))
                    .andExpect(status().isOk());
            setTenantSetting("theme.allowed_palettes", "[\"aqua-blue\"]");
            setTenantSetting("theme.default_palette", "\"aqua-blue\"");
            mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stored.palette").value("aqua-green"))
                    .andExpect(jsonPath("$.effectivePalette").value("aqua-blue"));
            // A lista volta a incluir aqua-green: a escolha guardada volta a valer.
            setTenantSetting("theme.allowed_palettes", "[\"aqua-blue\", \"aqua-green\"]");
            mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectivePalette").value("aqua-green"));
        } finally {
            deleteTenantSetting("theme.default_mode");
            deleteTenantSetting("theme.default_palette");
            deleteTenantSetting("theme.allowed_palettes");
        }
    }

    @Test
    @DisplayName("versão aceita vigente do console vence tenant_settings")
    void acceptedVersionBeatsTenantSettings() throws Exception {
        String subject = UUID.randomUUID().toString();
        try {
            // Resíduo de uma execução anterior interrompida antes do finally
            // travaria o propose com 409 (EXCLUDE de vigência): limpa antes.
            deleteParameterVersions("theme.default_mode");
            setTenantSetting("theme.default_mode", "\"dark\"");
            mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveMode").value("dark"));
            // O tenant muda o padrão pelo console (V9_8 registra a chave no
            // catálogo): a versão aceita vigente vence o tenant_settings.
            acceptedVersion("theme.default_mode", "\"light\"");
            mvc.perform(get(url()).header("Authorization", bearer(tokenFor(subject))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.effectiveMode").value("light"));
        } finally {
            deleteTenantSetting("theme.default_mode");
            deleteParameterVersions("theme.default_mode");
        }
    }
}
