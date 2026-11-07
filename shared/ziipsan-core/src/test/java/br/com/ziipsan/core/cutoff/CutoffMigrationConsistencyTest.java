package br.com.ziipsan.core.cutoff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consistencia da V9_2 com os enums e parametros Java da P3.4 (sem
 * banco, no padrao dos testes de consistencia do projeto): CHECKs,
 * tabelas registradas para RLS, chaves de parametro e a fronteira com
 * a V9_1 (OS) e a V8_1 (protocolo), que esta tarefa nao toca.
 */
class CutoffMigrationConsistencyTest {

    private static final String SQL = readSql();

    private static String readSql() {
        try (InputStream in = CutoffMigrationConsistencyTest.class
                .getResourceAsStream("/db/migration/tenant/V9_2__cutoff_reconnection.sql")) {
            assertThat(in).as("V9_2 no classpath do core").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("as seis tabelas da P3.4 existem e sao registradas para RLS")
    void tablesAndRls() {
        for (String table : new String[]{"cutoff_notices", "cutoff_non_generation_reasons",
                "cutoff_orders", "reconnection_orders", "cutoff_compensations",
                "cutoff_consumption_alerts"}) {
            assertThat(SQL).contains("CREATE TABLE IF NOT EXISTS " + table);
            assertThat(SQL).contains("register_tenant_table('" + table + "')");
        }
    }

    @Test
    @DisplayName("CHECKs da V9_2 espelham os enums Java, valor a valor")
    void checksMirrorEnums() {
        for (var reason : CutoffEnums.Reason.values()) {
            assertThat(SQL).contains("'" + reason.name() + "'");
        }
        for (var reason : CutoffEnums.NonGenerationReason.values()) {
            assertThat(SQL).contains("'" + reason.name() + "'");
        }
        for (var failure : CutoffEnums.RevalidationFailure.values()) {
            assertThat(SQL).contains("'" + failure.name() + "'");
        }
        for (var condition : CutoffEnums.ReleaseCondition.values()) {
            assertThat(SQL).contains("'" + condition.name() + "'");
        }
        for (var formula : CutoffEnums.UndueCompensationFormula.values()) {
            assertThat(SQL).contains("'" + formula.name() + "'");
        }
        for (var fee : CutoffEnums.FeeDecision.values()) {
            assertThat(SQL).contains("'" + fee.name() + "'");
        }
        for (var proof : CutoffEnums.DeliveryProofStatus.values()) {
            assertThat(SQL).contains("'" + proof.name() + "'");
        }
    }

    @Test
    @DisplayName("definicoes cut.*/rec.* novas registradas; as da V1_13 (P2.31) nao se repetem")
    void parameterDefinitions() {
        for (String key : new String[]{
                CutoffParameters.MIN_OVERDUE_DAYS, CutoffParameters.MIN_DEBT_AMOUNT,
                CutoffParameters.NOTICE_VALIDITY_DAYS, CutoffParameters.POST_CUT_NOTICE_HOURS,
                CutoffParameters.PROTECTED_CLASSES, CutoffParameters.SOCIAL_TARIFF_ALLOWED,
                CutoffParameters.SEWER_ALLOWED, CutoffParameters.REASONS,
                CutoffParameters.RELEASE_CONDITION, CutoffParameters.AUTO_ORDER_ON_PAYMENT,
                CutoffParameters.URGENT_FEE}) {
            assertThat(SQL).contains("('" + key + "'");
        }
        assertThat(SQL).contains("ON CONFLICT (definition_key) DO NOTHING");
        // Ja registradas pela V1_13 da P2.31: nao entram como definicao aqui.
        assertThat(SQL).doesNotContain("('" + CutoffParameters.MIN_NOTICE_DAYS + "', 'INT'");
        assertThat(SQL).doesNotContain("('" + CutoffParameters.MAX_DEBT_AGE_DAYS + "', 'INT'");
    }

    @Test
    @DisplayName("fronteira: a V9_2 nao cria nem altera as tabelas da V9_1 (OS) nem da V8_1 (protocolo)")
    void boundaryWithV91AndV81() {
        assertThat(SQL).doesNotContain("CREATE TABLE IF NOT EXISTS service_orders");
        assertThat(SQL).doesNotContain("CREATE TABLE IF NOT EXISTS service_protocols");
        assertThat(SQL).doesNotContain("ALTER TABLE service_orders");
        assertThat(SQL).doesNotContain("ALTER TABLE service_protocols");
        // O vinculo e por FK para o nucleo, nunca por copia do modelo.
        assertThat(SQL).contains("REFERENCES service_orders(tenant_id, id)");
        // Emergencia com justificativa e foto tambem e CHECK do banco.
        assertThat(SQL).contains("emergency_photo_document_id IS NOT NULL");
    }

    @Test
    @DisplayName("tipos de OS da P3.4 sao os declarados ao nucleo (codigos estaveis)")
    void orderTypeCodes() {
        assertThat(CutoffOrderTypes.CUTOFF_TYPE_CODE).isEqualTo("WATER_CUTOFF");
        assertThat(CutoffOrderTypes.RECONNECTION_TYPE_CODE).isEqualTo("WATER_RECONNECTION");
        assertThat(CutoffOrderTypes.INSPECTION_TYPE_CODE).isEqualTo("CONNECTION_INSPECTION");
        assertThat(CutoffOrderTypes.cutoffType().targetClasses())
                .containsExactly(br.com.ziipsan.core.serviceorder.TargetType.CONSUMER_UNIT);
        assertThat(CutoffOrderTypes.inspectionType().operational()).isTrue();
        assertThat(CutoffOrderTypes.inspectionType().effectCode()).isNull();
    }
}
