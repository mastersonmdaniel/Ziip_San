package br.com.ziipsan.core.parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Matriz de fallback e invariante de comportamento do MM-02. */
class MunicipalityScopedParameterRuleTest {

    private static final String MUN_A = "4205407";
    private static final String MUN_B = "4216602";
    private static final LocalDate D = LocalDate.of(2026, 6, 15);

    private static MunicipalityScopedParameterRule.ScopedVersion v(
            String ibge, String value, String from, String to) {
        return new MunicipalityScopedParameterRule.ScopedVersion(ibge, value,
                new ParameterVersionRule.Validity(LocalDate.parse(from),
                        to == null ? null : LocalDate.parse(to)));
    }

    @Test
    void municipalVenceSobreEmpresa() {
        var versions = List.of(
                v(null, "\"empresa\"", "2026-01-01", null),
                v(MUN_A, "\"municipal\"", "2026-01-01", null));
        var r = MunicipalityScopedParameterRule.resolve(versions, MUN_A, D);
        assertEquals("\"municipal\"", r.orElseThrow().valueJson());
    }

    @Test
    void ausenciaMunicipalCaiNaEmpresa() {
        var versions = List.of(v(null, "\"empresa\"", "2026-01-01", null));
        var r = MunicipalityScopedParameterRule.resolve(versions, MUN_A, D);
        assertEquals("\"empresa\"", r.orElseThrow().valueJson());
    }

    @Test
    void municipioDistintoNaoVazaCaiNaEmpresa() {
        var versions = List.of(
                v(null, "\"empresa\"", "2026-01-01", null),
                v(MUN_B, "\"outro\"", "2026-01-01", null));
        var r = MunicipalityScopedParameterRule.resolve(versions, MUN_A, D);
        assertEquals("\"empresa\"", r.orElseThrow().valueJson());
    }

    @Test
    void ausenciaTotalVazioComoNaVigente() {
        assertTrue(MunicipalityScopedParameterRule.resolve(List.of(), MUN_A, D).isEmpty());
        var fora = List.of(v(null, "\"x\"", "2026-01-01", "2026-01-31"));
        assertTrue(MunicipalityScopedParameterRule.resolve(fora, MUN_A,
                LocalDate.of(2026, 3, 1)).isEmpty());
    }

    @Test
    void municipalForaDeVigenciaCaiNaEmpresaVigente() {
        var versions = List.of(
                v(null, "\"empresa\"", "2026-01-01", null),
                v(MUN_A, "\"municipal\"", "2026-01-01", "2026-03-31"));
        var r = MunicipalityScopedParameterRule.resolve(versions, MUN_A,
                LocalDate.of(2026, 5, 1));
        assertEquals("\"empresa\"", r.orElseThrow().valueJson());
    }

    @Test
    void fronteirasDeVigenciaInclusivas() {
        var versions = List.of(
                v(null, "\"empresa\"", "2026-01-01", null),
                v(MUN_A, "\"municipal\"", "2026-03-10", "2026-03-20"));
        assertEquals("\"municipal\"", MunicipalityScopedParameterRule
                .resolve(versions, MUN_A, LocalDate.of(2026, 3, 10)).orElseThrow().valueJson());
        assertEquals("\"municipal\"", MunicipalityScopedParameterRule
                .resolve(versions, MUN_A, LocalDate.of(2026, 3, 20)).orElseThrow().valueJson());
        assertEquals("\"empresa\"", MunicipalityScopedParameterRule
                .resolve(versions, MUN_A, LocalDate.of(2026, 3, 9)).orElseThrow().valueJson());
        assertEquals("\"empresa\"", MunicipalityScopedParameterRule
                .resolve(versions, MUN_A, LocalDate.of(2026, 3, 21)).orElseThrow().valueJson());
    }

    @Test
    void semMunicipioExactamenteComoVigente() {
        var versions = List.of(
                v(null, "\"v1\"", "2026-01-01", "2026-06-30"),
                v(null, "\"v2\"", "2026-07-01", null),
                v(MUN_A, "\"municipal\"", "2026-01-01", null));
        for (LocalDate d = LocalDate.of(2025, 12, 1);
             !d.isAfter(LocalDate.of(2027, 1, 1)); d = d.plusDays(7)) {
            Optional<MunicipalityScopedParameterRule.ScopedVersion> scoped =
                    MunicipalityScopedParameterRule.resolveCompanyScope(versions, d);
            // Referencia vigente: a primeira versao empresa cuja Validity cobre d.
            final LocalDate day = d;
            Optional<MunicipalityScopedParameterRule.ScopedVersion> vigente = versions.stream()
                    .filter(x -> x.isCompanyScope() && x.validity().covers(day))
                    .findFirst();
            assertEquals(vigente.map(x -> x.valueJson()), scoped.map(x -> x.valueJson()),
                    "divergencia em " + d);
        }
    }

    @Test
    void invarianteSoEmpresaQualquerMunicipioResolveComoHoje() {
        var soEmpresa = List.of(
                v(null, "\"v1\"", "2026-01-01", "2026-06-30"),
                v(null, "\"v2\"", "2026-07-01", null));
        for (LocalDate d = LocalDate.of(2026, 1, 1);
             !d.isAfter(LocalDate.of(2026, 12, 31)); d = d.plusDays(13)) {
            var sem = MunicipalityScopedParameterRule.resolveCompanyScope(soEmpresa, d);
            assertEquals(sem, MunicipalityScopedParameterRule.resolve(soEmpresa, MUN_A, d));
            assertEquals(sem, MunicipalityScopedParameterRule.resolve(soEmpresa, MUN_B, d));
        }
    }

    @Test
    void sobreposicaoSoDentroDoMesmoEscopo() {
        var candEmpresa = v(null, "\"n\"", "2026-06-01", null);
        var candMunA = v(MUN_A, "\"n\"", "2026-06-01", null);
        var existentes = List.of(
                v(null, "\"e\"", "2026-01-01", null),
                v(MUN_A, "\"a\"", "2026-01-01", null),
                v(MUN_B, "\"b\"", "2026-01-01", null));
        assertThrows(IllegalArgumentException.class, () ->
                MunicipalityScopedParameterRule.validateNoOverlapInScope(candEmpresa, existentes));
        assertThrows(IllegalArgumentException.class, () ->
                MunicipalityScopedParameterRule.validateNoOverlapInScope(candMunA, existentes));
        // Empresa nova fora da vigencia da empresa existente + municipios nao colidem:
        var candFora = v(null, "\"n\"", "2027-01-01", null);
        var existentesFechados = List.of(v(null, "\"e\"", "2026-01-01", "2026-12-31"),
                v(MUN_A, "\"a\"", "2027-01-01", null));
        MunicipalityScopedParameterRule.validateNoOverlapInScope(candFora, existentesFechados);
    }

    @Test
    void ibgeInvalidoRecusado() {
        assertThrows(IllegalArgumentException.class, () -> v("123", "\"x\"", "2026-01-01", null));
        assertThrows(IllegalArgumentException.class, () ->
                MunicipalityScopedParameterRule.resolve(List.of(), "ABC", D));
    }
}
