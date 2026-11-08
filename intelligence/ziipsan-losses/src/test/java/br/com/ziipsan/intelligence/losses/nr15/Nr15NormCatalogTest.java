package br.com.ziipsan.intelligence.losses.nr15;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class Nr15NormCatalogTest {

    @Test
    @DisplayName("catalogo: NR15 liga ao cert_standard da P1.34 (ana-nr-15-res-275-2025) e a Res. ANA 275/2025")
    void catalogLinkage() {
        assertThat(Nr15NormCatalog.NR_KEY).isEqualTo("NR15");
        assertThat(Nr15NormCatalog.CATALOG_CODE).isEqualTo("ana-nr-15-res-275-2025");
        assertThat(Nr15NormCatalog.RESOLUTION).isEqualTo("Res. ANA 275/2025");
        assertThat(Nr15NormCatalog.PUBLISHED_ON).isEqualTo(LocalDate.of(2025, 12, 19));
    }

    @Test
    @DisplayName("data de norma do aceite: verificacao da adocao a partir de maio de 2028 = 20/05/2028 (art. 14, p.u.)")
    void verificationDateIsNormDate() {
        assertThat(Nr15NormCatalog.VERIFICATION_START).isEqualTo(LocalDate.of(2028, 5, 20));
        assertThat(Nr15NormCatalog.verificationStartsInMay2028()).isTrue();
    }

    @Test
    @DisplayName("matriz do Anexo I: apresentacao em mil m3 (180.000 m3 -> 180,0)")
    void matrixUnit() {
        assertThat(Nr15NormCatalog.toMatrixUnit(180_000.0)).isEqualTo(180.0);
    }

    @Test
    @DisplayName("fechamento SINISA do Anexo I: as 5 equacoes fecham sobre valores declarados consistentes")
    void closureEquationsClose() {
        Map<String, Double> gta = consistentGta();
        var residuals = SinisaClosureEquations.verify(gta);
        assertThat(residuals).hasSize(5);
        assertThat(residuals).allMatch(SinisaClosureEquations.Residual::closed);
        assertThat(SinisaClosureEquations.allClosed(gta)).isTrue();
        // GTA1218 = 10000 - 6000 - 200 - 300 - 500 = 3000
        assertThat(residuals.get(4).computed()).isEqualTo(3000.0);
    }

    @Test
    @DisplayName("fechamento: valor adulterado aparece no residuo; variavel ausente recusa nomeando-a")
    void closureDetectsAndRefuses() {
        Map<String, Double> tampered = consistentGta();
        tampered.put("GTA1218", 3100.0);
        assertThat(SinisaClosureEquations.allClosed(tampered)).isFalse();
        assertThat(SinisaClosureEquations.verify(tampered).get(4).residual())
                .isCloseTo(100.0, within(1e-9));
        Map<String, Double> missing = consistentGta();
        missing.remove("GTA1203");
        assertThatThrownBy(() -> SinisaClosureEquations.verify(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GTA1203");
    }

    private static Map<String, Double> consistentGta() {
        Map<String, Double> gta = new HashMap<>();
        gta.put("GTA1001", 8000.0);
        gta.put("GTA1009", 2000.0);
        gta.put("GTA1014", 10000.0);
        gta.put("GT1209", 5500.0);
        gta.put("GTA1210", 500.0);
        gta.put("GTA1211", 6000.0);
        gta.put("GTA1204", 100.0);
        gta.put("GTA1205", 100.0);
        gta.put("GTA1206", 100.0);
        gta.put("GTA1207", 300.0);
        gta.put("GTA1215", 200.0);
        gta.put("GTA1216", 300.0);
        gta.put("GTA1217", 500.0);
        gta.put("GTA1203", 200.0);
        gta.put("GTA1218", 3000.0);
        return gta;
    }
}
