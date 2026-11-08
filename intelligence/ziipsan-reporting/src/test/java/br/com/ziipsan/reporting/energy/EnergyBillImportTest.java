package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Importacao de fatura por arquivo (OPE-08): o leiaute NAO e inventado —
 * sem conferencia na implantacao, recusa; com o leiaute verificado, a
 * leitura e da porta e a fatura e conferida contra o registro.
 * Distribuidora, numero e leiaute aqui sao massa sintetica declarada.
 */
class EnergyBillImportTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final EnergyInstallation INSTALLATION = new EnergyInstallation(
            TENANT, UUID.randomUUID(), UUID.randomUUID(), OperationalUnitKind.ETA,
            "Distribuidora Sintetica", "UC-999", new BigDecimal("100"));

    private static EnergyBill importedBill(String layoutVersion) {
        return new EnergyBill(TENANT, INSTALLATION.id(), YearMonth.of(2026, 10),
                new BigDecimal("12000"), new BigDecimal("3000"),
                new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("0.95"),
                EnergyDataSource.IMPORTED_BILL, layoutVersion);
    }

    @Test
    void semLeiauteRegistradoRecusaAntesDeLerArquivo() {
        EnergyPorts.EnergyBillFilePort port = (key, file, content, inst) -> importedBill("L1");
        EnergyBillImportService service = new EnergyBillImportService();
        assertThatThrownBy(() -> service.importBill(INSTALLATION, "DIST-SINT", "fatura.bin",
                new byte[]{1}, Map.of(), port))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_LAYOUT_NOT_VERIFIED")
                .hasMessageContaining("[SEM FONTE]");
    }

    @Test
    void leiauteNaoVerificadoRecusa() {
        EnergyPorts.EnergyBillFilePort port = (key, file, content, inst) -> importedBill("L1");
        EnergyBillImportService service = new EnergyBillImportService();
        Map<String, DistributorLayout> layouts =
                Map.of("DIST-SINT", DistributorLayout.unverified("DIST-SINT"));
        assertThatThrownBy(() -> service.importBill(INSTALLATION, "DIST-SINT", "fatura.bin",
                new byte[]{1}, layouts, port))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_LAYOUT_NOT_VERIFIED");
    }

    @Test
    void leiauteVerificadoDelegaAPortaEConfereVersao() {
        EnergyPorts.EnergyBillFilePort port = (key, file, content, inst) -> importedBill("L1");
        EnergyBillImportService service = new EnergyBillImportService();
        Map<String, DistributorLayout> layouts = Map.of("DIST-SINT",
                DistributorLayout.verified("DIST-SINT", "L1", "conferido contra faturas sinteticas"));
        EnergyBillImportService.ImportResult result = service.importBill(
                INSTALLATION, "DIST-SINT", "fatura.bin", new byte[]{1}, layouts, port);
        assertThat(result.bill().totalKwh()).isEqualByComparingTo("12000");
        assertThat(result.layout().layoutVersion()).isEqualTo("L1");
    }

    @Test
    void leiauteRegistradoParaOutraDistribuidoraRecusaP15() {
        // Correcao D6 (REVISAO-ED-90, sonda P15): o mapa foi montado sob
        // a chave pedida, mas o REGISTRO e de outra distribuidora.
        EnergyPorts.EnergyBillFilePort port = (key, file, content, inst) -> importedBill("L1");
        EnergyBillImportService service = new EnergyBillImportService();
        Map<String, DistributorLayout> layouts = Map.of("DIST-SINT",
                DistributorLayout.verified("DIST-OUTRA", "L1", "conferido para outra"));
        assertThatThrownBy(() -> service.importBill(INSTALLATION, "DIST-SINT", "fatura.bin",
                new byte[]{1}, layouts, port))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_LAYOUT_DISTRIBUTOR_MISMATCH");
    }

    @Test
    void faturaComVersaoDivergenteDoLeiauteRegistradoRecusa() {
        EnergyPorts.EnergyBillFilePort port = (key, file, content, inst) -> importedBill("L2");
        EnergyBillImportService service = new EnergyBillImportService();
        Map<String, DistributorLayout> layouts = Map.of("DIST-SINT",
                DistributorLayout.verified("DIST-SINT", "L1", "conferido contra faturas sinteticas"));
        assertThatThrownBy(() -> service.importBill(INSTALLATION, "DIST-SINT", "fatura.bin",
                new byte[]{1}, layouts, port))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_IMPORT_MISMATCH");
    }

    @Test
    void faturaImportadaSemVersaoDeLeiauteRecusaNaConstrucao() {
        assertThatThrownBy(() -> new EnergyBill(TENANT, INSTALLATION.id(), YearMonth.of(2026, 10),
                new BigDecimal("100"), null, null, null, null,
                EnergyDataSource.IMPORTED_BILL, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_LAYOUT_VERSION_REQUIRED");
    }

    @Test
    void fatorDePotenciaForaDaFaixaRecusa() {
        assertThatThrownBy(() -> new EnergyBill(TENANT, INSTALLATION.id(), YearMonth.of(2026, 10),
                new BigDecimal("100"), null, null, null, new BigDecimal("1.5"),
                EnergyDataSource.TELEMETRY, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_POWER_FACTOR_INVALID");
    }

    @Test
    void pontaAcimaDoTotalRecusa() {
        assertThatThrownBy(() -> new EnergyBill(TENANT, INSTALLATION.id(), YearMonth.of(2026, 10),
                new BigDecimal("100"), new BigDecimal("101"), null, null, null,
                EnergyDataSource.MANUAL, null))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_BILL_PEAK_KWH_INVALID");
    }

    @Test
    void leiauteVerificadoSemVersaoOuSemNotaRecusa() {
        assertThatThrownBy(() -> DistributorLayout.verified("DIST-SINT", " ", "nota"))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_LAYOUT_VERSION_REQUIRED");
        assertThatThrownBy(() -> DistributorLayout.verified("DIST-SINT", "L1", " "))
                .isInstanceOf(IndicatorValidationException.class)
                .hasMessageContaining("ENERGY_LAYOUT_VERIFICATION_NOTE_REQUIRED");
    }
}
