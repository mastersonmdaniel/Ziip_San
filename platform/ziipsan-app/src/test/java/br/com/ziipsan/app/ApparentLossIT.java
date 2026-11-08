package br.com.ziipsan.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ED-95 (CAD-103, GES-25, OPE-37): perdas aparentes no PostgreSQL
 * real — as travas do aceite no banco: OS so com decisao humana
 * registrada, suspeita sem efeito automatico, cruzamento so de
 * backoffice e cadastro sem base legal/finalidade recusado.
 *
 * <p>NAO RODADA nesta VM como IT: pendencia nomeada P-ED95-03 —
 * prova da CI no PC do dono, contra a pilha real com as migrations
 * numeradas na integracao. Nesta VM os dois SQL pendentes foram
 * APLICADOS de verdade em PostgreSQL 18.6 descartavel com stubs
 * declarados e as cinco sondas negativas abaixo foram executadas
 * manualmente com o mesmo resultado esperado (harness
 * cascata-ed95, pg/apply-ed95.log, ver RELATORIO-ED-95).
 *
 * <p>Requer as variaveis de ambiente ED95_IT_JDBC_URL,
 * ED95_IT_JDBC_USER e ED95_IT_JDBC_PASSWORD apontando para um
 * banco descartavel com o SQL da ED-95 ja aplicado.
 */
class ApparentLossIT {

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                System.getenv("ED95_IT_JDBC_URL"),
                System.getenv("ED95_IT_JDBC_USER"),
                System.getenv("ED95_IT_JDBC_PASSWORD"));
    }

    @Test
    @DisplayName("banco recusa OS de cruzamento sem decisao humana registrada")
    void workOrderWithoutHumanDecisionIsRefusedByTheDatabase() throws Exception {
        try (Connection c = open(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO loss_cross_findings (id, tenant_id, finding_kind, evidence, suggested_action,"
                            + " evaluation_channel, detected_at, review_due_date, status, work_order_id)"
                            + " VALUES (gen_random_uuid(), gen_random_uuid(), 'USE_DIVERGENCE', 'ev', 'INSPECTION',"
                            + " 'BACKOFFICE', NOW(), CURRENT_DATE, 'DECIDED', gen_random_uuid())"));
        }
    }

    @Test
    @DisplayName("banco recusa suspeita com efeito automatico diferente de NONE")
    void suspicionWithAutomaticEffectIsRefusedByTheDatabase() throws Exception {
        try (Connection c = open(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO loss_suspicions (id, tenant_id, unit_id, rule_code, evidence, evaluated_at,"
                            + " recommendation, automatic_effect)"
                            + " VALUES (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), 'ABRUPT_DROP',"
                            + " 'ev', NOW(), 'HUMAN_INSPECTION_REVIEW', 'CUT')"));
        }
    }

    @Test
    @DisplayName("banco recusa cadastro imobiliario sem base legal")
    void registryWithoutLegalBasisIsRefusedByTheDatabase() throws Exception {
        try (Connection c = open(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.execute(
                    "INSERT INTO loss_property_registry (id, tenant_id, municipal_registration,"
                            + " built_area_square_meters, declared_use, legal_basis, sharing_purpose, source,"
                            + " source_batch_reference, imported_at)"
                            + " VALUES (gen_random_uuid(), gen_random_uuid(), 'INSC-IT', 10, 'RESIDENTIAL',"
                            + " ' ', 'finalidade', 'GIS_IMPORT', 'L1', NOW())"));
        }
    }
}
