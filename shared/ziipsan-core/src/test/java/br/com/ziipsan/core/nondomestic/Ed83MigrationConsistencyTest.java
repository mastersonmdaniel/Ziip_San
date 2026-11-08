package br.com.ziipsan.core.nondomestic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coerencia do SQL pendente da ED-83 com o nucleo: tabelas, guardas
 * nomeadas no aceite e ausencia de seeds/valores inventados. O caminho
 * do SQL vem de -Ded83.sql.path (harness); sem a propriedade, o teste
 * localiza o arquivo a partir do diretorio de trabalho da entrega.
 */
class Ed83MigrationConsistencyTest {

    private static String sql() throws IOException {
        String prop = System.getProperty("ed83.sql.path");
        Path p = prop != null ? Path.of(prop)
                : Path.of("shared/ziipsan-core/src/main/resources/db/pendente/ED-83__nondomestic_discharger.sql");
        return Files.readString(p);
    }

    @Test
    void sqlDeclaresAllTablesWithTenantRegistrationAndNoSeeds() throws IOException {
        String sql = sql();
        for (String table : new String[]{
                "nondomestic_dischargers", "nondomestic_characterizations",
                "nondomestic_characterization_params", "nondomestic_user_catalog_versions",
                "nondomestic_user_standards", "nondomestic_sampling_collections",
                "nondomestic_self_monitoring_reports", "nondomestic_self_monitoring_results",
                "nondomestic_inspections", "nondomestic_nonconformities",
                "nondomestic_charge_components"}) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), table);
            assertTrue(sql.contains("register_tenant_table('" + table + "')"), table);
        }
        assertEquals(11, sql.lines().filter(l -> l.contains("CREATE TABLE IF NOT EXISTS")).count());
        assertFalse(sql.toUpperCase().contains("INSERT INTO"), "SQL nao semeia linha alguma");
        assertFalse(sql.contains("V102"), "V102 reservado, proibido");
        assertFalse(sql.contains("V103"), "V103 reservado, proibido");
        assertFalse(sql.contains("V6_4"), "V6_4 intocada, proibido referenciar");
    }

    @Test
    void sqlMirrorsCoreGuards() throws IOException {
        String sql = sql();
        // Marca na unidade + pre-tratamento as expensas do usuario.
        assertTrue(sql.contains("non_domestic_discharger"));
        assertTrue(sql.contains("pretreatment_at_user_expense = TRUE"));
        // Catalogo do prestador nasce so com norma publicada, como a ED-105.
        assertTrue(sql.contains("publication_status IN ('PUBLISHED')"));
        // Veredito de coleta exige recebimento pelo laboratorio.
        assertTrue(sql.contains("verdict IS NULL OR received_by_lab = TRUE"));
        // Carga so com previsao da agencia.
        assertTrue(sql.contains("charge_model <> 'POLLUTANT_LOAD' OR agency_foresees_load_billing = TRUE"));
        // Componente com codigo fixo do motor da P2.5.
        assertTrue(sql.contains("NONDOMESTIC_EFFLUENT"));
        // Parametros nomeados no aceite presentes como definitions comentadas.
        assertTrue(sql.contains("effluent.user_standard_catalog"));
        assertTrue(sql.contains("effluent.sampling_interval_days"));
        assertTrue(sql.contains("sewer.nondomestic_charge_model"));
    }
}
