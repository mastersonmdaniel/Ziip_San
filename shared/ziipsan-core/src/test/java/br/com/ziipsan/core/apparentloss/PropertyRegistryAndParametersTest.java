package br.com.ziipsan.core.apparentloss;

import br.com.ziipsan.core.parameters.MissingParameterException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static br.com.ziipsan.core.apparentloss.LossFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PropertyRegistryAndParametersTest {

    @Test
    void completeRecordIsAcceptedWithLegalBasisAndPurpose() {
        PropertyRegistryRecord r = property(UUID.randomUUID(), "120", PropertyRegistryRecord.PropertyUse.RESIDENTIAL);
        assertTrue(r.built());
        assertEquals("base legal declarada pelo ente (massa sintetica de teste)", r.legalBasis());
    }

    @Test
    void recordWithoutLegalBasisIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                new PropertyRegistryRecord(UUID.randomUUID(), TENANT, "INSC-1", bd("10"),
                        PropertyRegistryRecord.PropertyUse.RESIDENTIAL, null, null,
                        "  ", "finalidade declarada",
                        PropertyRegistryRecord.RegistrySource.MUNICIPAL_CONNECTOR, "L1", NOW));
        assertTrue(e.getMessage().contains("base legal"));
    }

    @Test
    void recordWithoutSharingPurposeIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                new PropertyRegistryRecord(UUID.randomUUID(), TENANT, "INSC-1", bd("10"),
                        PropertyRegistryRecord.PropertyUse.RESIDENTIAL, null, null,
                        "base legal declarada", null,
                        PropertyRegistryRecord.RegistrySource.GIS_IMPORT, "L1", NOW));
        assertTrue(e.getMessage().contains("finalidade"));
    }

    @Test
    void recordWithoutRegistrationIsRefusedAndVacantLotIsNotBuilt() {
        assertThrows(IllegalArgumentException.class, () ->
                new PropertyRegistryRecord(UUID.randomUUID(), TENANT, " ", bd("10"),
                        PropertyRegistryRecord.PropertyUse.RESIDENTIAL, null, null,
                        "base", "finalidade",
                        PropertyRegistryRecord.RegistrySource.GIS_IMPORT, "L1", NOW));
        assertFalse(property(UUID.randomUUID(), "0", PropertyRegistryRecord.PropertyUse.OTHER).built());
    }

    @Test
    void syncModeIsRequiredAndReturnedVerbatim() {
        assertThrows(MissingParameterException.class, () -> LossParameters.propertyRegistrySyncMode(null));
        assertThrows(MissingParameterException.class, () -> LossParameters.propertyRegistrySyncMode(" "));
        assertEquals("MODO-DECLARADO-PELO-ENTE", LossParameters.propertyRegistrySyncMode("  MODO-DECLARADO-PELO-ENTE "));
    }

    @Test
    void reviewDaysAreRequiredAndPositive() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> LossParameters.unregisteredBuildingReviewDays(null));
        assertEquals(LossParameters.UNREGISTERED_BUILDING_REVIEW_DAYS, e.definitionKey());
        assertThrows(IllegalArgumentException.class, () -> LossParameters.unregisteredBuildingReviewDays(0));
        assertEquals(15, LossParameters.unregisteredBuildingReviewDays(15));
    }

    @Test
    void fraudRulesAbsentIsExplicitErrorNeverDefault() {
        MissingParameterException e = assertThrows(MissingParameterException.class,
                () -> LossParameters.FraudRuleThresholds.parse(null));
        assertEquals(LossParameters.FRAUD_RULES, e.definitionKey());
        assertThrows(MissingParameterException.class, () -> LossParameters.FraudRuleThresholds.parse("  "));
    }

    @Test
    void fraudRulesUnknownKeyAndNestedValueAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> LossParameters.FraudRuleThresholds.parse("{\"regraInventada.enabled\": true}"));
        assertThrows(IllegalArgumentException.class,
                () -> LossParameters.FraudRuleThresholds.parse("{\"zeroConsumption.enabled\": {\"x\": 1}}"));
    }

    @Test
    void fraudRulesTrailingCommaIsRefused() {
        // Chave CONHECIDA: a recusa so pode vir do parser, nao da checagem de chave desconhecida.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LossParameters.FraudRuleThresholds.parse("{\"zeroConsumption.enabled\": true,}"));
        assertTrue(e.getMessage().contains("virgula final"), "mensagem deve nomear a virgula final: " + e.getMessage());
    }

    @Test
    void fraudRulesTrailingCommaWithSpaceBeforeItIsRefused() {
        // Chave CONHECIDA: a recusa so pode vir do parser, nao da checagem de chave desconhecida.
        assertThrows(IllegalArgumentException.class,
                () -> LossParameters.FraudRuleThresholds.parse("{\"zeroConsumption.enabled\": true ,}"));
    }

    @Test
    void enabledRuleWithoutThresholdIsExplicitError() {
        LossParameters.FraudRuleThresholds t = LossParameters.FraudRuleThresholds.parse(
                "{\"zeroConsumption.enabled\": true}");
        MissingParameterException e = assertThrows(MissingParameterException.class, t::requireThresholds);
        assertTrue(e.getMessage().contains("zeroConsumption.minConsecutiveZeroCycles"));
    }

    @Test
    void selfReportOptionIsDeclaredFactNeverPresumed() {
        assertNull(LossParameters.declaredSelfReportFineWaiverEnabled(null));
        assertEquals(Boolean.TRUE, LossParameters.declaredSelfReportFineWaiverEnabled("true"));
        assertEquals(Boolean.FALSE, LossParameters.declaredSelfReportFineWaiverEnabled("false"));
        assertThrows(IllegalArgumentException.class,
                () -> LossParameters.declaredSelfReportFineWaiverEnabled("sim"));
    }
}
