package br.com.ziipsan.reporting.energy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Coerencia estatica entre o SQL pendente da ED-90 e as classes. */
class Ed90MigrationConsistencyTest {

    private String sql() throws Exception {
        Path p = Path.of("intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-90__energy_esg.sql");
        if (!Files.exists(p)) {
            p = Path.of(System.getProperty("ed90.sql.path", ""));
        }
        return Files.readString(p);
    }

    private String paramsSql() throws Exception {
        Path p = Path.of("intelligence/ziipsan-reporting/src/main/resources/db/pendente/ED-90__core_energy_esg_parameters.sql");
        if (!Files.exists(p)) {
            p = Path.of(System.getProperty("ed90.params.path", ""));
        }
        return Files.readString(p);
    }

    @Test
    void fkCompostaTemUniqueNoPaiLicaoC3() throws Exception {
        String sql = sql();
        assertTrue(sql.contains("UNIQUE (tenant_id, id)"),
                "pai da FK composta sem UNIQUE (tenant_id, id) - licao C3");
        assertTrue(sql.contains("FOREIGN KEY (tenant_id, installation_id)"));
        assertTrue(sql.contains("REFERENCES energy_installations (tenant_id, id)"));
    }

    @Test
    void catalogoDeFatoresNasceVazioENenhumaTabelaTenantSemeada() throws Exception {
        String sql = sql();
        assertFalse(sql.contains("INSERT INTO energy_"), "SQL tenant da ED-90 nao semeia dado");
        assertFalse(sql.contains("INSERT INTO esg_"), "catalogo de fatores nasce vazio");
        assertTrue(sql.contains("register_tenant_table('energy_installations'"));
        assertTrue(sql.contains("register_tenant_table('energy_bills'"));
        assertTrue(sql.contains("register_tenant_table('esg_emission_inventory_lines'"));
    }

    @Test
    void parametrosNomeadosNoAceiteSemFabrica() throws Exception {
        String params = paramsSql();
        for (String key : new String[]{"energy.demand_alert_pct", "energy.pf_min",
                "esg.emission_factors_version", "esg.report_profile"}) {
            assertTrue(params.contains("'" + key + "'"), "parametro ausente: " + key);
        }
        assertTrue(params.contains("'energy.demand_alert_pct', 'DECIMAL', 'b', NULL"),
                "demand_alert_pct deve nascer sem factory_default");
        assertTrue(params.contains("'energy.pf_min', 'DECIMAL', 'b', NULL"),
                "pf_min deve nascer sem factory_default");
        assertTrue(params.contains("ON CONFLICT (definition_key) DO NOTHING"));
    }
}
