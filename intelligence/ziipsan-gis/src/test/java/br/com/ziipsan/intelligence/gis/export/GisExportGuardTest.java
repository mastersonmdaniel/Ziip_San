package br.com.ziipsan.intelligence.gis.export;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GisExportGuardTest {

    private GisExportFeature personal(String id, String cell) {
        return new GisExportFeature(id, "properties", "PROPERTY", "POINT",
                List.of(List.of(-48.0, -15.0)), Map.of("code", id), true, false, false, cell, "IN_SERVICE");
    }
    private GisExportFeature network(String id) {
        return new GisExportFeature(id, "water-arcs", "PIPE", "LINESTRING",
                List.of(List.of(-48.0,-15.0), List.of(-48.1,-15.1)), Map.of("dn", 100), false, false, false, null, "IN_SERVICE");
    }

    @Test void pisoNuncaAbaixoDeCinco() {
        assertEquals(5, GisExportGuard.effectiveFloor(1));
        assertEquals(5, GisExportGuard.effectiveFloor(5));
        assertEquals(9, GisExportGuard.effectiveFloor(9));
    }

    @Test void celulaComQuatroImoveisNaoPublicaENemArredonda() {
        var out = GisExportGuard.publishableAggregates(List.of(
                new GisExportGuard.PersonalAggregate("quadra-1", 4),
                new GisExportGuard.PersonalAggregate("quadra-2", 5)), 5);
        assertEquals(1, out.size());
        assertEquals("quadra-2", out.get(0).cellKey());
    }

    @Test void pessoalForaDoBackofficeEIndividuoSaoRecusados() {
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.THIRD_PARTY, List.of(personal("p1","q1")), true, true));
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.PUBLIC, List.of(personal("p1","q1")), true, true));
    }

    @Test void pessoalNoBackofficeExigePermissaoERegistro() {
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.BACKOFFICE, List.of(personal("p1","q1")), false, true));
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.BACKOFFICE, List.of(personal("p1","q1")), true, false));
        assertDoesNotThrow(() -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.BACKOFFICE, List.of(personal("p1","q1")), true, true));
    }

    @Test void marcadorSensivelNuncaSaiEInfraCriticaNaoSaiParaTerceiro() {
        var sensitive = new GisExportFeature("s1","water-nodes","VALVE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, false, true, null, "IN_SERVICE");
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.BACKOFFICE, List.of(sensitive), true, true));
        var critical = new GisExportFeature("c1","water-nodes","INTAKE","POINT",
                List.of(List.of(-48.0,-15.0)), Map.of(), false, true, false, null, "IN_SERVICE");
        assertThrows(IllegalArgumentException.class, () -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.THIRD_PARTY, List.of(critical), true, true));
        assertDoesNotThrow(() -> GisExportGuard.checkIndividualExport(
                GisExportGuard.Audience.BACKOFFICE, List.of(network("n1")), false, false));
    }
}
