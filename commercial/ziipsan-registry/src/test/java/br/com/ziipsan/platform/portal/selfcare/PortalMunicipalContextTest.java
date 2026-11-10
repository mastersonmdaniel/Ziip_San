package br.com.ziipsan.platform.portal.selfcare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation;
import br.com.ziipsan.core.parameters.MunicipalityScopedParameterRule;
import br.com.ziipsan.core.parameters.ParameterVersionRule;
import br.com.ziipsan.core.protocol.DeadlineCalculator;
import br.com.ziipsan.core.protocol.HolidayScope;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * MM-08 — contexto municipal do portal: UC de A recebe A, UC de B
 * recebe B, UC sem derivacao recebe exactamente o vigente.
 * IBGEs ficticios no formato de 7 digitos; datas de feriado sao massa
 * de teste (o registo real e dado do tenant, P0.19).
 */
class PortalMunicipalContextTest {

    private static final UUID TENANT = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final String MUN_A = "4205407";
    private static final String MUN_B = "4216602";
    private static final LocalDate AT = LocalDate.of(2026, 6, 15);
    private static final LocalDate NATIONAL = LocalDate.of(2026, 1, 1);
    private static final LocalDate HOL_A = LocalDate.of(2026, 3, 10);
    private static final LocalDate HOL_B = LocalDate.of(2026, 4, 15);
    private static final LocalDate HOL_STATE = LocalDate.of(2026, 5, 20);

    private static final UUID LOC_A = UUID.randomUUID();
    private static final UUID LOC_B = UUID.randomUUID();
    private static final UUID SEC_A = UUID.randomUUID();
    private static final UUID SEC_B = UUID.randomUUID();
    private static final UUID BLK_A = UUID.randomUUID();
    private static final UUID BLK_B = UUID.randomUUID();
    private static final UUID UNIT_A = UUID.randomUUID();
    private static final UUID UNIT_B = UUID.randomUUID();
    private static final UUID UNIT_NONE = UUID.randomUUID();

    /** Porta da empresa (ED-38) com os valores vigentes de teste. */
    private static final class CompanyParams implements SelfCareParameters {
        public boolean govbrEnabled(UUID t) { return false; }
        public Optional<String> govbrMinLevelRaw(UUID t) { return Optional.of("SILVER"); }
        public boolean peerComparisonEnabled(UUID t) { return false; }
        public Optional<Integer> peerMinUnits(UUID t) { return Optional.of(5); }
        public boolean passkeyEnabled(UUID t) { return true; }
    }

    private static MunicipalityScopedParameterRule.ScopedVersion v(String ibge, String json) {
        return new MunicipalityScopedParameterRule.ScopedVersion(ibge, json,
                new ParameterVersionRule.Validity(LocalDate.of(2026, 1, 1), null));
    }

    private static final Map<String, List<MunicipalityScopedParameterRule.ScopedVersion>> VERSIONS = Map.of(
            MunicipalSelfCareParameters.KEY_GOVBR_ENABLED,
                    List.of(v(null, "false"), v(MUN_A, "true")),
            MunicipalSelfCareParameters.KEY_GOVBR_MIN_LEVEL,
                    List.of(v(null, "\"SILVER\""), v(MUN_A, "\"GOLD\"")),
            MunicipalSelfCareParameters.KEY_PEER_COMPARISON,
                    List.of(v(null, "false")),
            MunicipalSelfCareParameters.KEY_PEER_MIN_UNITS,
                    List.of(v(null, "5"), v(MUN_A, "8")),
            MunicipalSelfCareParameters.KEY_PASSKEY_ENABLED,
                    List.of(v(null, "true")));

    private static PortalMunicipalContext context(SelfCareParameters company,
            Map<String, List<MunicipalityScopedParameterRule.ScopedVersion>> versions) {
        // Adaptador da porta de municipio: a derivacao REAL do MM-01.
        MunicipalityDerivation derivation = new MunicipalityDerivation(
                List.of(new MunicipalityDerivation.LocalityRef(TENANT, LOC_A, MUN_A),
                        new MunicipalityDerivation.LocalityRef(TENANT, LOC_B, MUN_B)),
                List.of(new MunicipalityDerivation.SectorRef(TENANT, SEC_A, LOC_A),
                        new MunicipalityDerivation.SectorRef(TENANT, SEC_B, LOC_B)),
                List.of(new MunicipalityDerivation.BlockRef(TENANT, BLK_A, SEC_A),
                        new MunicipalityDerivation.BlockRef(TENANT, BLK_B, SEC_B)));
        Map<UUID, MunicipalityDerivation.UnitRef> units = Map.of(
                UNIT_A, new MunicipalityDerivation.UnitRef(TENANT, UNIT_A, BLK_A),
                UNIT_B, new MunicipalityDerivation.UnitRef(TENANT, UNIT_B, BLK_B),
                UNIT_NONE, new MunicipalityDerivation.UnitRef(TENANT, UNIT_NONE, null));
        PortalMunicipalContext.UnitMunicipalityPort munPort =
                (tenant, unit) -> derivation.derive(units.get(unit));
        PortalMunicipalContext.HolidayRegisterPort holidays = tenant -> List.of(
                new DeadlineCalculator.HolidayEntry(NATIONAL,
                        DeadlineCalculator.HolidaySphere.NATIONAL, null, null, false),
                new DeadlineCalculator.HolidayEntry(HOL_A,
                        DeadlineCalculator.HolidaySphere.MUNICIPAL, null, MUN_A, false),
                new DeadlineCalculator.HolidayEntry(HOL_B,
                        DeadlineCalculator.HolidaySphere.MUNICIPAL, null, MUN_B, false),
                new DeadlineCalculator.HolidayEntry(HOL_STATE,
                        DeadlineCalculator.HolidaySphere.STATE, "SC", null, false));
        return new PortalMunicipalContext(munPort, holidays, company,
                (tenant, key) -> versions.getOrDefault(key, List.of()));
    }

    @Test
    void ucDeMunicipioARecebeContextoDeA() {
        var ctx = context(new CompanyParams(), VERSIONS).resolve(
                TENANT, UNIT_A, HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", AT);
        assertEquals(Optional.of(MUN_A), ctx.municipalityIbge());
        assertTrue(ctx.municipal());
        // Parametro municipal vence o da empresa (MM-02).
        assertTrue(ctx.parameters().govbrEnabled(TENANT));
        assertEquals(Optional.of("GOLD"), ctx.parameters().govbrMinLevelRaw(TENANT));
        assertEquals(Optional.of(8), ctx.parameters().peerMinUnits(TENANT));
        // Calendario: municipal de A presente; o de B, nao.
        assertTrue(ctx.isHoliday(HOL_A));
        assertFalse(ctx.isHoliday(HOL_B));
        assertTrue(ctx.isHoliday(NATIONAL));
        assertTrue(ctx.isHoliday(HOL_STATE));
    }

    @Test
    void ucDeMunicipioBRecebeContextoDeB() {
        var ctx = context(new CompanyParams(), VERSIONS).resolve(
                TENANT, UNIT_B, HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", AT);
        assertEquals(Optional.of(MUN_B), ctx.municipalityIbge());
        // B nao tem versoes municipais: resolve a empresa (sem vazamento de A).
        assertFalse(ctx.parameters().govbrEnabled(TENANT));
        assertEquals(Optional.of("SILVER"), ctx.parameters().govbrMinLevelRaw(TENANT));
        assertEquals(Optional.of(5), ctx.parameters().peerMinUnits(TENANT));
        assertTrue(ctx.isHoliday(HOL_B));
        assertFalse(ctx.isHoliday(HOL_A));
        assertTrue(ctx.isHoliday(NATIONAL));
    }

    @Test
    void parametroSemVersaoMunicipalCaiNaEmpresa() {
        var ctx = context(new CompanyParams(), VERSIONS).resolve(
                TENANT, UNIT_A, HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", AT);
        assertTrue(ctx.parameters().passkeyEnabled(TENANT));
        assertFalse(ctx.parameters().peerComparisonEnabled(TENANT));
    }

    @Test
    void ucSemDerivacaoRecebeExactamenteOVigente() {
        CompanyParams company = new CompanyParams();
        var ctx = context(company, VERSIONS).resolve(
                TENANT, UNIT_NONE, HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", AT);
        assertTrue(ctx.municipalityIbge().isEmpty());
        assertFalse(ctx.municipal());
        // A propria porta da empresa, sem decorador (identidade).
        assertSame(company, ctx.parameters());
        assertEquals(company.govbrEnabled(TENANT), ctx.parameters().govbrEnabled(TENANT));
        assertEquals(company.govbrMinLevelRaw(TENANT), ctx.parameters().govbrMinLevelRaw(TENANT));
        assertEquals(company.peerMinUnits(TENANT), ctx.parameters().peerMinUnits(TENANT));
        // Calendario vigente sem municipio: nacional/estadual sim, municipal nao.
        assertTrue(ctx.isHoliday(NATIONAL));
        assertTrue(ctx.isHoliday(HOL_STATE));
        assertFalse(ctx.isHoliday(HOL_A));
        assertFalse(ctx.isHoliday(HOL_B));
    }

    @Test
    void escopoNacionalDoTenantExcluiFeriadoMunicipal() {
        // O mecanismo vigente manda: scope NATIONAL nao admite MUNICIPAL,
        // mesmo com IBGE derivado — o MM-08 consome, nao contorna.
        var ctx = context(new CompanyParams(), VERSIONS).resolve(
                TENANT, UNIT_A, HolidayScope.NATIONAL, "SC", AT);
        assertFalse(ctx.isHoliday(HOL_A));
        assertTrue(ctx.isHoliday(NATIONAL));
    }

    @Test
    void valorMunicipalIlegivelRecusaComAChave() {
        var bad = Map.of(MunicipalSelfCareParameters.KEY_GOVBR_ENABLED,
                List.of(v(null, "false"), v(MUN_A, "\"yes\"")));
        var ctx = context(new CompanyParams(), bad).resolve(
                TENANT, UNIT_A, HolidayScope.NATIONAL_STATE_MUNICIPAL, "SC", AT);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ctx.parameters().govbrEnabled(TENANT));
        assertTrue(e.getMessage().contains(MunicipalSelfCareParameters.KEY_GOVBR_ENABLED));
    }
}
