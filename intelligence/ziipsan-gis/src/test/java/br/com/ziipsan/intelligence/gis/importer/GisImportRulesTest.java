package br.com.ziipsan.intelligence.gis.importer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GIS-07 (P3.26): regras puras do importador — conversão assistida, SRS
 * e grade do IBGE, anonimização (ADR-0033) e máquina de estados do
 * lote. Amostras sintéticas dos perfis: as camadas Corsan Anexo A do
 * §9.2 são exercitadas contra os conjuntos daqui e da V13_11.
 */
class GisImportRulesTest {

    private static final String HASH_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String HASH_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    @DisplayName("DWG só com DXF do ODA e os dois hashes")
    void dwgRequiresOdaConversion() {
        assertThatCode(() -> GisImportRules.ensureConversionRecorded(
                "DWG", "DXF", HASH_A, HASH_B, "ODA_FILE_CONVERTER"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GisImportRules.ensureConversionRecorded(
                "DWG", null, HASH_A, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisImportRules.ensureConversionRecorded(
                "DWG", "DXF", HASH_A, HASH_B, "ASSISTED_EXTERNAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("DGN v8 só depois da conversão assistida para DWG ou DXF")
    void dgnV8RequiresAssistedConversion() {
        assertThatCode(() -> GisImportRules.ensureConversionRecorded(
                "DGN_V8", "DXF", HASH_A, HASH_B, "ASSISTED_EXTERNAL"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GisImportRules.ensureConversionRecorded(
                "DGN_V8", null, HASH_A, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisImportRules.ensureConversionRecorded(
                "DGN_V7", "DXF", HASH_A, HASH_B, "ASSISTED_EXTERNAL"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("hash precisa ser SHA-256 hexadecimal de 64 caracteres")
    void hashMustBeSha256() {
        assertThat(GisImportRules.isSha256(HASH_A)).isTrue();
        assertThat(GisImportRules.isSha256("abc")).isFalse();
        assertThat(GisImportRules.isSha256(null)).isFalse();
        assertThatThrownBy(() -> GisImportRules.ensureConversionRecorded(
                "DXF", null, "não-é-hash", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sem SRS recusa; SAD69 e Córrego Alegre só pela grade do IBGE")
    void srsRules() {
        assertThatThrownBy(() -> GisImportRules.ensureSrsReady(
                null, null, false, "NONE", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisImportRules.ensureSrsReady(
                4618, "DETECTED", false, "NONE", null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisImportRules.ensureSrsReady(
                4225, "DETECTED", false, "IBGE_GRID", " ", false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> GisImportRules.ensureSrsReady(
                4618, "DETECTED", false, "IBGE_GRID", "grade declarada no lote", false))
                .doesNotThrowAnyException();
        assertThatCode(() -> GisImportRules.ensureSrsReady(
                4674, "DETECTED", false, "NONE", null, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> GisImportRules.ensureSrsReady(
                4674, "OPERATOR_DECLARED", false, "NONE", null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("camada pessoal sem método de anonimização é recusada (ADR-0033)")
    void personalLayerRequiresAnonymizationMethod() {
        assertThatThrownBy(() -> GisImportRules.ensureAnonymization(true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GisImportRules.ensureAnonymization(true, "APAGAR"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> GisImportRules.ensureAnonymization(
                true, "SHIFTED_WITHIN_BLOCK")).doesNotThrowAnyException();
        assertThatCode(() -> GisImportRules.ensureAnonymization(
                true, "AGGREGATED_BY_BLOCK")).doesNotThrowAnyException();
        assertThatCode(() -> GisImportRules.ensureAnonymization(false, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("máquina de estados do lote é a da V13_11")
    void batchTransitions() {
        assertThat(GisImportRules.canTransitionBatch("RECEIVED", "STAGED")).isTrue();
        assertThat(GisImportRules.canTransitionBatch("STAGED", "MAPPED")).isTrue();
        assertThat(GisImportRules.canTransitionBatch("MAPPED", "CLEANED")).isTrue();
        assertThat(GisImportRules.canTransitionBatch("CLEANED", "QUALITY_REPORTED")).isTrue();
        assertThat(GisImportRules.canTransitionBatch("QUALITY_REPORTED", "PUBLISHED")).isTrue();
        assertThat(GisImportRules.canTransitionBatch("RECEIVED", "PUBLISHED")).isFalse();
        assertThat(GisImportRules.canTransitionBatch("PUBLISHED", "STAGED")).isFalse();
    }
}
