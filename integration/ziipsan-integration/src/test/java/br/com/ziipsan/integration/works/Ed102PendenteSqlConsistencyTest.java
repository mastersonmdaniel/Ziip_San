package br.com.ziipsan.integration.works;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

/** Consistencia do SQL pendente da ED-102 com os enums e travas do codigo. */
class Ed102PendenteSqlConsistencyTest {
    static String sql() throws Exception {
        var p = Path.of("src/main/resources/db/pendente/ED-102__works_funding_agreements.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("user.dir"),
            "entrega/integration/ziipsan-integration/src/main/resources/db/pendente/ED-102__works_funding_agreements.sql");
        if (!Files.exists(p)) p = Path.of(System.getProperty("user.dir"),
            "src/main/resources/db/pendente/ED-102__works_funding_agreements.sql");
        return Files.readString(p);
    }

    @Test void sqlIsPendenteSemFonteAndRegistersAllTables() throws Exception {
        String s = sql();
        assertTrue(s.contains("PENDENTE DE NUMERACAO"));
        assertTrue(s.contains("[SEM FONTE]"));
        for (String t : java.util.List.of("works_funding_agreements", "works_agreement_gis_links",
                "works_schedule_stages", "works_measurements")) {
            assertTrue(s.contains("CREATE TABLE IF NOT EXISTS " + t), t);
            assertTrue(s.contains("register_tenant_table('" + t + "')"), t);
            assertTrue(s.contains("tenant_id"), t);
        }
        assertTrue(s.contains("'works.agreements', 'OBJECT', 'd'"));
        assertFalse(s.contains("DROP TABLE"));
        assertFalse(s.contains("DELETE FROM"));
    }

    @Test void sqlKindAndStatusChecksMatchEnumsExactly() throws Exception {
        String s = sql();
        for (FundingAgreement.Kind k : FundingAgreement.Kind.values())
            assertTrue(s.contains("'" + k.name() + "'"), "kind ausente no SQL: " + k.name());
        for (FundingAgreement.Status st : FundingAgreement.Status.values())
            assertTrue(s.contains("'" + st.name() + "'"), "status ausente no SQL: " + st.name());
        for (Measurement.Status st : Measurement.Status.values())
            assertTrue(s.contains("'" + st.name() + "'"), "status de medicao ausente no SQL: " + st.name());
    }

    @Test void sqlCarriesThePerRowGuardsOfTheCode() throws Exception {
        String s = sql();
        assertTrue(s.contains("counterpart_value <= total_value"));
        assertTrue(s.contains("end_date >= start_date"));
        assertTrue(s.contains("physical_percent_period >= 0 AND physical_percent_period <= 100"));
        assertTrue(s.contains("planned_percent > 0 AND planned_percent <= 100"));
        assertTrue(s.contains("uq_works_measurement_sequence"));
        assertTrue(s.contains("uq_works_agreement_external_ref"));
        assertTrue(s.contains("btrim(external_ref) <> ''"));
        assertTrue(s.contains("btrim(project_ref) <> ''"));
    }
}
