package br.com.ziipsan.commercial.billing.field;

import br.com.ziipsan.commercial.billingrules.BatchSendPolicy;
import br.com.ziipsan.commercial.billingrules.thermal.ThermalDocumentType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldParametersAndMigrationTest {

    @Test
    void parametersParseAndValidate() {
        FieldEmissionParameters params = FieldEmissionParameters.from(Map.of(
                BatchSendPolicy.SEND_EVERY_N_KEY, "20",
                DivergenceApprovalService.DOUBLE_APPROVAL_LIMIT_KEY, "10.00",
                FieldEmissionParameters.PRINTER_MIN_BATTERY_KEY, "30"));
        assertThat(params.batchSendPolicy().sendEveryN()).isEqualTo(20);
        assertThat(params.doubleApprovalLimit()).isEqualByComparingTo("10.00");
        assertThat(params.printerMinBatteryPct()).isEqualTo(30);
    }

    @Test
    void missingParameterIsRefusedNeverAssumed() {
        FieldEmissionParameters params = FieldEmissionParameters.from(Map.of());
        assertThatThrownBy(params::batchSendPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BatchSendPolicy.SEND_EVERY_N_KEY);
        assertThatThrownBy(params::doubleApprovalLimit)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void contingencyPolicyRequiresMotive() {
        assertThat(NfageContingencyPolicy.situation(false, null))
                .isEqualTo(NfageContingencyPolicy.FiscalSituation.NORMAL);
        assertThat(NfageContingencyPolicy.situation(true, "SEFAZ indisponivel"))
                .isEqualTo(NfageContingencyPolicy.FiscalSituation.CONTINGENCY_PENDING_TRANSMISSION);
        assertThatThrownBy(() -> NfageContingencyPolicy.situation(true, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NfageContingencyPolicy.situation(false, "motivo solto"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gatesMirrorEd18AndP334Catalogs() {
        assertThat(FieldEmissionGates.OcrGate.fromEd18Verdict("REVIEW"))
                .isEqualTo(FieldEmissionGates.OcrGate.REVIEW);
        assertThat(FieldEmissionGates.ocrHolds(FieldEmissionGates.OcrGate.REVIEW)).isTrue();
        assertThat(FieldEmissionGates.ocrHolds(FieldEmissionGates.OcrGate.SUGGESTION)).isFalse();
        assertThatThrownBy(() -> FieldEmissionGates.OcrGate.fromEd18Verdict("INVENTADO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(FieldEmissionGates.signatureBlocks(
                FieldEmissionGates.SignatureGateLevel.QUALIFIED, false, null)).isTrue();
        assertThat(FieldEmissionGates.signatureBlocks(null, false, null)).isFalse();
        assertThat(FieldEmissionGates.signatureBlocks(
                FieldEmissionGates.SignatureGateLevel.ADVANCED, true,
                FieldEmissionGates.SignatureGateLevel.QUALIFIED)).isFalse();
    }

    @Test
    void migrationPendenteIsConsistentWithTheCode() throws Exception {
        String sql;
        try (InputStream in = getClass().getResourceAsStream(
                "/db/pendente/ED-19__field_bills_and_divergences.sql")) {
            assertThat(in).as("SQL pendente da ED-19 no classpath de recursos").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Unicidade por task_id: a garantia de banco da idempotencia.
        assertThat(sql).contains("UNIQUE (tenant_id, task_id)");
        // Situacoes do SQL = enum do codigo.
        for (FieldBillStatus status : FieldBillStatus.values()) {
            assertThat(sql).contains("'" + status.name() + "'");
        }
        // Tipos de documento do trabalho de impressao = enum termico (maiusculas).
        for (ThermalDocumentType type : ThermalDocumentType.values()) {
            assertThat(sql).contains("'" + type.name() + "'");
        }
        assertThat(sql).contains("'ZPL'").contains("'CPCL'");
        assertThat(sql).contains("field_bills").contains("field_bill_divergences")
                .contains("field_print_jobs");
    }
}
