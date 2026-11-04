package br.com.ziipsan.operations.asset;

import br.com.ziipsan.core.api.ApiException;
import br.com.ziipsan.operations.asset.AssetService.AssetInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Formatos MCPSB e niveis (P1.35), sem banco: a validacao pura do
 * {@link AssetService#validateMcpsb} e do {@link AssetLevel}.
 */
class AssetValidationTest {

    private static AssetInput base() {
        return new AssetInput("AT-1", null, 1, null, "MUNICIPALITY", null, null,
                false, false, "1".repeat(25), "1234567", null, null, "12", null,
                "00", "00", "00", "00", "00", "INDIVIDUAL", null, false, false,
                null, null, null, null, null, null, null, null, "PLANNED", null);
    }

    private static AssetInput with(String cib, String loc, String ac, String up, String uar,
                                   String control, String unit, int level, String nts) {
        AssetInput b = base();
        return new AssetInput(b.code(), b.parentAssetId(), level, b.assetClass(), b.catalogTypeCode(),
                b.gisFeatureKind(), b.gisFeatureId(), b.isBuilding(), b.confinedSpace(),
                cib, loc, ac, nts, up, uar, "00", "00", "00", "00", "00",
                control, unit, b.bcp(), b.brv(), b.manufacturer(), b.model(), b.serialNumber(),
                b.manufactureYear(), b.powerKw(), b.capacity(), b.capacityUnit(),
                b.inServiceOn(), b.lifecycleStatus(), b.locationText());
    }

    @Test
    @DisplayName("UP aceita 2 e 3 digitos (manual divergente, ressalva V27_1) e recusa 1 e 4")
    void upAcceptsTwoOrThreeDigits() {
        assertThatCode(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, "12", null, "INDIVIDUAL", null, 1, null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, "123", null, "INDIVIDUAL", null, 1, null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, "1", null, "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, "1234", null, "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
    }

    @Test
    @DisplayName("CIB: 25 digitos so no bem individual; massa nao tem CIB e exige unidade")
    void cibRules() {
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(24), "1234567", null, null, null, "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, null, null, "MASS", "un", 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with(null, "1234567", null, null, null, "MASS", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> AssetService.validateMcpsb(
                with(null, "1234567", null, null, null, "MASS", "un", 1, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LOC 7, AC 6, UAR 7; NTS-SDS obrigatorio do nivel 3 em diante")
    void otherMcpsbFormats() {
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "123456", null, null, null, "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", "12345", null, null, "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, null, "123456", "INDIVIDUAL", null, 1, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatThrownBy(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, null, null, "INDIVIDUAL", null, 3, null)))
                .isInstanceOf(ApiException.Validation.class);
        assertThatCode(() -> AssetService.validateMcpsb(
                with("1".repeat(25), "1234567", null, null, null, "INDIVIDUAL", null, 3, "FIXTURE_NTS_01")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Niveis: componente exige pai equipamento; raiz nao tem pai")
    void levels() {
        org.junit.jupiter.api.Assertions.assertEquals(AssetLevel.EQUIPMENT,
                AssetLevel.COMPONENT.requiredParentLevel());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> AssetLevel.MUNICIPALITY.requiredParentLevel());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> AssetLevel.of(7));
        org.junit.jupiter.api.Assertions.assertEquals(6, AssetLevel.COMPONENT.level());
    }
}
