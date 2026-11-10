package br.com.ziipsan.commercial.billing.cobill;

import br.com.ziipsan.commercial.billing.composition.ChargeCalculationContext;
import br.com.ziipsan.commercial.billingrules.catalog.CalculationModel;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeDefinition;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeOwner;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeResolution;
import br.com.ziipsan.commercial.billingrules.catalog.ChargeVersion;
import br.com.ziipsan.commercial.billingrules.servicecatalog.RevenueNature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camada 3: INV-C3-06 — diferencial DIRECTO da porta contra o
 * {@code compose} vigente da ED-25, para a mesma UC/competencia/
 * contrato e os mesmos factos (tolerancia zero, igualdade exacta de
 * {@link BigDecimal}, escala incluida). Valores ficticios de fixture,
 * no molde da suite vigente {@code SolidWasteChargeCalculatorTest}.
 */
class CobillExternalValueProviderTest {

    private static final UUID UNIT = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final YearMonth COMP = YearMonth.of(2026, 4);
    private static final BigDecimal OWN_TOTAL = new BigDecimal("100.00");
    private static final BigDecimal MONTHLY = new BigDecimal("35.00");

    private static CobillParameters params(String mode, String enabled) {
        return CobillParameters.fromValues(Map.of(
                CobillParameters.ENABLED, enabled,
                CobillParameters.BILLING_MODE, mode,
                CobillParameters.NOTICE_MIN_DAYS, "60"));
    }

    private static CobillContract contract() {
        return new CobillContract(UUID.randomUUID(), CobillContract.ServiceKind.SOLID_WASTE,
                UUID.randomUUID(), "Coleta Urbana Ltda", "Coleta de residuos solidos",
                LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 2));
    }

    private static ChargeResolution resolution(CobillContract contract) {
        return resolution(contract, contract.providerId());
    }

    private static ChargeResolution resolution(CobillContract contract, UUID definitionProviderId) {
        ChargeDefinition def = new ChargeDefinition(UUID.randomUUID(), "COBILL-FIXTURE",
                "Fixture cofaturamento", ChargeOwner.THIRD_PARTY, RevenueNature.ANCILLARY,
                CalculationModel.EXTERNAL_VALUE, definitionProviderId, false, true, false, null, null);
        ChargeVersion version = new ChargeVersion(UUID.randomUUID(), def.id(), 1,
                LocalDate.of(2026, 1, 1), null, CalculationModel.EXTERNAL_VALUE, null, null,
                contract.id(), ChargeVersion.ChargeParameters.empty());
        return new ChargeResolution(def, version);
    }

    private static ChargeCalculationContext context(YearMonth competence) {
        return ChargeCalculationContext.builder()
                .unitRef(UNIT.toString()).competence(competence.atDay(1)).build();
    }

    private static CobillExternalValueProvider.CobillFacts facts(
            CobillParameters parameters, CobillContract contract, BigDecimal monthly,
            boolean exempt, boolean withdrawn, boolean firstCharge, YearMonth competence) {
        return new CobillExternalValueProvider.CobillFacts(parameters, contract, OWN_TOTAL, monthly,
                exempt, withdrawn, firstCharge,
                CobillBookletPlan.documentIdentifier(UNIT, competence, 1));
    }

    /** O diferencial: porta == compose vigente, com os mesmos factos. */
    private static void assertDifferential(CobillParameters parameters, CobillContract contract,
                                           YearMonth competence, BigDecimal monthly,
                                           boolean exempt, boolean withdrawn, boolean firstCharge) {
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                facts(parameters, contract, monthly, exempt, withdrawn, firstCharge, competence));
        BigDecimal expected = SolidWasteChargeCalculator.compose(parameters, contract, competence,
                OWN_TOTAL, monthly, exempt, withdrawn, firstCharge,
                CobillBookletPlan.documentIdentifier(UNIT, competence, 1)).cobillAmount();
        assertEquals(expected, provider.resolveValue(resolution(contract), context(competence)));
    }

    @Test
    @DisplayName("INV-C3-06: COBILLED normal — porta == compose (35.00)")
    void cobilledIgualAoCompose() {
        assertDifferential(params("COBILLED", "true"), contract(), COMP, MONTHLY, false, false, false);
    }

    @Test
    @DisplayName("INV-C3-06: desligado — zero, sem linha, igual ao compose")
    void desligadoZeroSemLinha() {
        CobillContract contract = contract();
        assertDifferential(params("COBILLED", "false"), contract, COMP, MONTHLY, false, false, false);
        var composition = new CobillExternalValueProvider(
                facts(params("COBILLED", "false"), contract, MONTHLY, false, false, false, COMP))
                .resolveComposition(resolution(contract), context(COMP));
        assertNull(composition.cobillLine());
        assertEquals(new BigDecimal("0.00"), composition.cobillAmount());
    }

    @Test
    @DisplayName("INV-C3-06: fora de vigencia — zero, igual ao compose")
    void foraDeVigenciaZero() {
        assertDifferential(params("COBILLED", "true"), contract(), YearMonth.of(2025, 12),
                MONTHLY, false, false, false);
    }

    @Test
    @DisplayName("INV-C3-06: competencia anterior ao inicio da cobranca — zero, igual ao compose")
    void antesDoInicioZero() {
        assertDifferential(params("COBILLED", "true"), contract(), YearMonth.of(2026, 2),
                MONTHLY, false, false, false);
    }

    @Test
    @DisplayName("INV-C3-06: isencao — zero COM linha zerada marcada, igual ao compose")
    void isencaoZeroComLinha() {
        CobillContract contract = contract();
        assertDifferential(params("COBILLED", "true"), contract, COMP, MONTHLY, true, false, false);
        var composition = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), contract, MONTHLY, true, false, false, COMP))
                .resolveComposition(resolution(contract), context(COMP));
        assertNotNull(composition.cobillLine());
        assertTrue(composition.cobillLine().exempt());
        assertEquals(new BigDecimal("0.00"), composition.cobillAmount());
    }

    @Test
    @DisplayName("INV-C3-06: retirada vigente — zero, sem linha, igual ao compose")
    void retiradaZeroSemLinha() {
        CobillContract contract = contract();
        assertDifferential(params("COBILLED", "true"), contract, COMP, MONTHLY, false, true, false);
        var composition = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), contract, MONTHLY, false, true, false, COMP))
                .resolveComposition(resolution(contract), context(COMP));
        assertNull(composition.cobillLine());
    }

    @Test
    @DisplayName("INV-C3-06: valor mensal zero — zero, sem linha, igual ao compose")
    void valorMensalZero() {
        assertDifferential(params("COBILLED", "true"), contract(), COMP,
                new BigDecimal("0.00"), false, false, false);
    }

    @Test
    @DisplayName("fronteira billing_mode: SEPARATE_INVOICE devolve o valor DISCRIMINADO, fora do total do documento")
    void faturaDesvinculadaDevolveDiscriminadoForaDoTotal() {
        CobillContract contract = contract();
        assertDifferential(params("SEPARATE_INVOICE", "true"), contract, COMP, MONTHLY, false, false, false);
        var composition = new CobillExternalValueProvider(
                facts(params("SEPARATE_INVOICE", "true"), contract, MONTHLY, false, false, false, COMP))
                .resolveComposition(resolution(contract), context(COMP));
        assertEquals(MONTHLY, composition.cobillAmount());
        assertFalse(composition.cobillLine().includedInBillTotal());
        assertEquals(OWN_TOTAL, composition.billDocumentTotal());
    }

    @Test
    @DisplayName("fronteira billing_mode: BOOKLET_MONTHLY/ANNUAL devolvem o discriminado, fora do total do documento")
    void carnesDevolvemDiscriminadoForaDoTotal() {
        assertDifferential(params("BOOKLET_MONTHLY", "true"), contract(), COMP, MONTHLY, false, false, false);
        assertDifferential(params("BOOKLET_ANNUAL", "true"), contract(), COMP, MONTHLY, false, false, false);
        CobillContract contract = contract();
        var composition = new CobillExternalValueProvider(
                facts(params("BOOKLET_ANNUAL", "true"), contract, MONTHLY, false, false, false, COMP))
                .resolveComposition(resolution(contract), context(COMP));
        assertFalse(composition.cobillLine().includedInBillTotal());
        assertEquals(OWN_TOTAL, composition.billDocumentTotal());
    }

    @Test
    @DisplayName("primeira cobranca sem antecedencia do aviso: o portao vigente RECUSA atraves da porta")
    void primeiraCobrancaSemAvisoRecusada() {
        CobillContract avisoTardio = new CobillContract(UUID.randomUUID(),
                CobillContract.ServiceKind.SOLID_WASTE, UUID.randomUUID(), "Coleta Urbana Ltda",
                "Coleta de residuos solidos", LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2));
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), avisoTardio, MONTHLY, false, false, true, COMP));
        assertThrows(IllegalStateException.class,
                () -> provider.resolveValue(resolution(avisoTardio), context(COMP)));
    }

    @Test
    @DisplayName("contrato dos factos diverge do externalContractId da versao: falha fechada")
    void contratoDivergenteFalhaFechado() {
        CobillContract daResolucao = contract();
        CobillContract dosFactos = contract();
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), dosFactos, MONTHLY, false, false, false, COMP));
        assertThrows(IllegalStateException.class,
                () -> provider.resolveValue(resolution(daResolucao), context(COMP)));
    }

    @Test
    @DisplayName("prestador da definicao diverge do prestador do contrato: falha fechada")
    void prestadorDivergenteFalhaFechado() {
        CobillContract contract = contract();
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), contract, MONTHLY, false, false, false, COMP));
        assertThrows(IllegalStateException.class,
                () -> provider.resolveValue(resolution(contract, UUID.randomUUID()), context(COMP)));
    }

    @Test
    @DisplayName("resolucao que nao e EXTERNAL_VALUE: recusada")
    void resolucaoDeOutroModeloRecusada() {
        CobillContract contract = contract();
        ChargeDefinition def = new ChargeDefinition(UUID.randomUUID(), "FIXA-FIXTURE",
                "Fixture fixa", ChargeOwner.OWN, RevenueNature.TARIFF,
                CalculationModel.FIXED_PER_UNIT, null, false, true, false, null, null);
        ChargeVersion version = new ChargeVersion(UUID.randomUUID(), def.id(), 1,
                LocalDate.of(2026, 1, 1), null, CalculationModel.FIXED_PER_UNIT, null, null, null,
                new ChargeVersion.ChargeParameters(null, new BigDecimal("10.00"), null));
        ChargeResolution outra = new ChargeResolution(def, version);
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                facts(params("COBILLED", "true"), contract, MONTHLY, false, false, false, COMP));
        assertThrows(IllegalArgumentException.class, () -> provider.resolveValue(outra, context(COMP)));
    }

    @Test
    @DisplayName("factos nao resolvidos pela porta de factos: falha fechada")
    void factosAusentesFalhamFechado() {
        CobillContract contract = contract();
        CobillExternalValueProvider provider = new CobillExternalValueProvider(
                (CobillExternalValueProvider.FactsResolver) (resolution, context) -> null);
        assertThrows(NullPointerException.class,
                () -> provider.resolveValue(resolution(contract), context(COMP)));
    }
}
