package br.com.ziipsan.commercial.registry.municipality;

import br.com.ziipsan.commercial.registry.municipality.MunicipalityDerivation.UnitRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.IBGE_A;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.IBGE_B;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.TENANT;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.chain;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.id;
import static br.com.ziipsan.commercial.registry.municipality.RouteMunicipalityScopeTest.unit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MM-03 — Validador de pureza: classifica TODAS as rotas do tenant e o
 * relatorio de excecoes lista SOMENTE as mistas. A validacao NAO bloqueia:
 * rota mista devolve relatorio, nunca excecao de programacao (estudo §6).
 */
class RoutePurityValidatorTest {

    private RoutePurityValidator validator() {
        return new RoutePurityValidator(chain());
    }

    @Test
    void classificaTodasAsRotasERelataSoAsMistas() {
        UUID puraA = id(601), mista = id(602), vazia = id(603), puraB = id(604);
        RoutePurityReport report = validator().validate(TENANT, Map.of(
                puraA, List.of(unit(101, 21), unit(102, 21)),
                mista, List.of(unit(103, 21), unit(104, 22), unit(105, 22)),
                vazia, List.<UnitRef>of(),
                puraB, List.of(unit(106, 22))));

        assertEquals(4, report.totalRoutes());
        assertEquals(2, report.pureCount());
        assertEquals(1, report.mixedCount());
        assertEquals(1, report.emptyCount());

        // Excecoes: exactamente a rota mista, com contagens certas.
        assertEquals(1, report.exceptions().size());
        RouteMunicipalityScope ex = report.exceptions().get(0);
        assertEquals(mista, ex.routeId());
        assertEquals(Map.of(IBGE_A, 1, IBGE_B, 2), ex.unitsByMunicipality());
        assertEquals(3, ex.totalUnits());

        // Texto do relatorio: contem a mista e NAO contem as puras/vazia.
        String text = report.formatExceptions();
        assertTrue(text.contains(mista.toString()), text);
        assertTrue(text.contains(IBGE_A + "=1"), text);
        assertTrue(text.contains(IBGE_B + "=2"), text);
        assertFalse(text.contains(puraA.toString()), text);
        assertFalse(text.contains(puraB.toString()), text);
        assertFalse(text.contains(vazia.toString()), text);
    }

    @Test
    void tenantSemRotasMistasNaoTemExcecoes() {
        RoutePurityReport report = validator().validate(TENANT, Map.of(
                id(611), List.of(unit(101, 21)),
                id(612), List.of(unit(102, 22)),
                id(613), List.<UnitRef>of()));
        assertEquals(3, report.totalRoutes());
        assertEquals(2, report.pureCount());
        assertEquals(0, report.mixedCount());
        assertTrue(report.exceptions().isEmpty());
        assertEquals("", report.formatExceptions());
    }

    @Test
    void rotaMistaNaoLancaExcecaoDeProgramacao() {
        // A invariante e validacao com relatorio: validar um tenant COM
        // rota mista termina normalmente e a mista aparece como excecao
        // listada — nenhum bloqueio seco (disposicao do estudo §6).
        RoutePurityReport report = validator().validate(TENANT, Map.of(
                id(621), List.of(unit(101, 21), unit(102, 22))));
        assertEquals(1, report.mixedCount());
        assertEquals(id(621), report.exceptions().get(0).routeId());
    }
}
